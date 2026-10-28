package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.FixtureOutages;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyService;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 0's localised light events: small things the lights do to one person, instead of the
 * level-wide blackout. The lights themselves go out through world/FixtureOutages (synced to the
 * clients, which also play the ballast pops and the far-off contactor clunk). Three kinds:
 * <ul>
 *   <li>{@link Kind#ROOM}: you step through a doorway into a room and its lights stutter and die
 *       around you. They come back on the moment you leave the room (or after two minutes).</li>
 *   <li>{@link Kind#CORRIDOR}: you're walking down a corridor and the tubes behind you go out one
 *       by one, the farthest first, the dark creeping up behind you, stopping a few blocks short
 *       of you. Half a minute later they come back.</li>
 *   <li>{@link Kind#DISTRICT}: you're standing in a lit spot, looking down a long sightline, and a
 *       whole district far down it goes dark in a rolling wave while you watch. It comes back
 *       after 40-80 seconds.</li>
 * </ul>
 *
 * <p><b>Rare and fair.</b> At most one event per player at a time, and never more than two in the
 * level at once. The first one can come three minutes after you arrive; after each one the next
 * waits 4-8 minutes. Never inside an anomalous area, a dark wing, or while a blackout or another
 * outage already has the lights at your spot. The waits and the choice of kind come from the world
 * seed and the player, so they are repeatable; each kind waits up to 90 s for the right moment
 * (a doorway, a straight corridor, a long view) and otherwise a different kind gets its turn.
 * Nothing ever happens to you: the lights just go out.
 */
public final class Level0LocalEvents {
    public enum Kind { ROOM, CORRIDOR, DISTRICT }

    private static final int SECOND = 20;
    private static final int FIRST_WAIT = 3 * 60 * SECOND;
    private static final int COOLDOWN_MIN = 4 * 60 * SECOND;
    private static final int COOLDOWN_SPREAD = 4 * 60 * SECOND;
    /** A chosen kind waits this long for the right moment, then another kind is picked. */
    private static final int GIVE_UP = 90 * SECOND;
    private static final int MAX_IN_LEVEL = 2;

    /** The room: flood-filled from you up to this far (blocks, square), and at most this many columns. */
    private static final int ROOM_REACH = 8;
    private static final int ROOM_MAX_COLUMNS = 260;
    private static final int ROOM_LONGEST = 120 * SECOND;
    /**
     * A column is "narrow" (a doorway or a corridor) when there's wall within this many blocks on
     * both sides along one axis. A room is what a flood fill reaches without crossing narrow
     * columns, so the room ends at its doorways.
     */
    private static final int NARROW_REACH = 3;
    /** Fewer columns than this is a closet or a nook, not a room. */
    private static final int ROOM_SMALLEST = 16;

    /** The corridor: tubes 3..18 blocks behind you, within this far either side of your path. */
    private static final int CORRIDOR_BACK = 18;
    private static final int CORRIDOR_NEAREST = 3;
    private static final double CORRIDOR_SIDE = 3.0;
    private static final int CORRIDOR_STEP_MS = 650;
    private static final int CORRIDOR_DARK = 35 * SECOND;
    /** Walking: at least this far each second, four seconds running, in a steady direction. */
    private static final double WALK_STEP = 2.0;
    private static final int TRAIL = 5;

    /** The district: you can see at least this far, it's dark around this point at this radius. */
    private static final int SIGHT_AT_LEAST = 28;
    private static final int SIGHT_FURTHEST = 48;
    private static final float DISTRICT_RADIUS = 12.0f;
    private static final int DISTRICT_DARK_MIN = 40 * SECOND;
    private static final int DISTRICT_DARK_SPREAD = 40 * SECOND;

    /** What we know about one player. */
    private static final class Watch {
        long nextTry;
        int count;
        @Nullable Kind wanted;
        long wantedSince;
        /** Where they were the last few seconds (newest last). */
        final double[] trailX = new double[TRAIL];
        final double[] trailZ = new double[TRAIL];
        int trailLength;
        // The event running now (outage < 0: none).
        int outage = -1;
        @Nullable ServerLevel outageLevel;
        @Nullable Kind active;
        long endAt;
        @Nullable LongOpenHashSet room;
        int outsideRoom;
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private Level0LocalEvents() {}

    /** Hooks the player tick and logout (called from Level0Anomalies.register). */
    static void register() {
        NeoForge.EVENT_BUS.addListener(Level0LocalEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(Level0LocalEvents::onLogout);
    }

    /**
     * Testing: let {@code kind} happen to this player as soon as the moment is right (go stand in a
     * doorway, walk a corridor, or look down a long view from a lit spot). Returns what to do.
     */
    public static String force(ServerPlayer player, Kind kind) {
        Watch watch = WATCHES.computeIfAbsent(player.getUUID(), id -> new Watch());
        if (watch.outage >= 0) {
            finish(watch, player.serverLevel().getGameTime());
        }
        watch.nextTry = 1;
        watch.wanted = kind;
        watch.wantedSince = player.serverLevel().getGameTime() + 10L * 60 * SECOND; // no giving up while testing
        return switch (kind) {
            case ROOM -> "Walk through a doorway into a lit room.";
            case CORRIDOR -> "Walk steadily down a lit corridor for a few seconds.";
            case DISTRICT -> "Stand still in a lit spot and look down a long view (28+ blocks) of lit maze.";
        } + " (" + kind.name().toLowerCase(Locale.ROOT) + ")";
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Watch watch = WATCHES.remove(event.getEntity().getUUID());
        if (watch != null && watch.outage >= 0 && watch.outageLevel != null) {
            FixtureOutages.end(watch.outageLevel, watch.outage);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SECOND != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Watch watch = WATCHES.computeIfAbsent(player.getUUID(), id -> new Watch());
        boolean inLevel0 = BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_0 && !player.isSpectator();
        if (watch.outage >= 0) {
            if (!inLevel0 || watch.outageLevel != level) {
                finish(watch, now);
            } else {
                follow(watch, player, now);
            }
        }
        if (!inLevel0) {
            watch.trailLength = 0;
            return;
        }
        if (watch.nextTry == 0) {
            watch.nextTry = now + FIRST_WAIT;
        }
        remember(watch, player);
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        if (watch.outage < 0 && now >= watch.nextTry && allowed(level, player, layout)) {
            long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_0);
            if (watch.wanted == null || now - watch.wantedSince > GIVE_UP) {
                watch.wanted = pick(seed, player.getUUID(), watch.count, now);
                watch.wantedSince = now;
            }
            boolean started = switch (watch.wanted) {
                case ROOM -> justCameIn(watch) && startRoom(watch, level, player, layout, now);
                case CORRIDOR -> narrow(level, player.getBlockX(), player.getBlockZ(), layout.floorY())
                        && walkingSteadily(watch) && startCorridor(watch, level, player, layout, now);
                case DISTRICT -> standingStill(watch) && startDistrict(watch, level, player, layout, seed, now);
            };
            if (started) {
                watch.active = watch.wanted;
                watch.wanted = null;
                watch.outageLevel = level;
            }
        }
    }

    /** Not in an anomaly or a dark wing, the lights here are on, and the level isn't already busy. */
    private static boolean allowed(ServerLevel level, ServerPlayer player, LevelLayout layout) {
        if (AnomalyService.currentSite(player) != null || FixtureOutages.activeCount(level) >= MAX_IN_LEVEL
                || FixtureOutages.isOut(level, player.blockPosition())) {
            return false;
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_0);
        return !(layout instanceof Level0Layout l0) || !l0.isUnlitZone(seed, player.getBlockX(), player.getBlockZ());
    }

    /** The kind for this player's next event: about 45% rooms, 35% corridors, 20% districts. */
    private static Kind pick(long seed, UUID id, int count, long now) {
        double r = Hash.unit(seed ^ id.getMostSignificantBits(), (int) id.getLeastSignificantBits(), count, (int) (now / GIVE_UP));
        return r < 0.45 ? Kind.ROOM : r < 0.80 ? Kind.CORRIDOR : Kind.DISTRICT;
    }

    // ==========================================================================================
    // While an event runs
    // ==========================================================================================

    private static void follow(Watch watch, ServerPlayer player, long now) {
        if (watch.active == Kind.ROOM && watch.room != null) {
            // Back on as soon as you've left the room (two seconds running, so a step back doesn't count).
            boolean outside = !watch.room.contains(column(player.getBlockX(), player.getBlockZ()));
            watch.outsideRoom = outside ? watch.outsideRoom + 1 : 0;
            if (watch.outsideRoom >= 2) {
                finish(watch, now);
                return;
            }
        }
        if (now >= watch.endAt) {
            finish(watch, now);
        }
    }

    private static void finish(Watch watch, long now) {
        if (watch.outageLevel != null && watch.outage >= 0) {
            FixtureOutages.end(watch.outageLevel, watch.outage);
        }
        long seed = watch.outageLevel == null ? 0 : BackroomsSeeds.layoutSeed(watch.outageLevel, BackroomsLevel.LEVEL_0);
        watch.nextTry = now + COOLDOWN_MIN + Hash.range(Hash.mix(seed + 31L * watch.count + 7), COOLDOWN_SPREAD);
        watch.count++;
        watch.outage = -1;
        watch.outageLevel = null;
        watch.active = null;
        watch.room = null;
        watch.outsideRoom = 0;
    }

    // ==========================================================================================
    // Starting each kind
    // ==========================================================================================

    /** The lights of the room you just walked into: flood-filled from your feet, stopping at walls and doorways. */
    private static boolean startRoom(Watch watch, ServerLevel level, ServerPlayer player, LevelLayout layout, long now) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        if (narrow(level, px, pz, floor)) {
            return false; // still in the doorway
        }
        LongOpenHashSet room = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        room.add(column(px, pz));
        queue.enqueue(column(px, pz));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            if (room.size() >= ROOM_MAX_COLUMNS) {
                return false; // no walls in reach: an open hall, not a room
            }
            long c = queue.dequeueLong();
            int x = columnX(c);
            int z = columnZ(c);
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0);
                int nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                long n = column(nx, nz);
                if (Math.abs(nx - px) > ROOM_REACH || Math.abs(nz - pz) > ROOM_REACH || room.contains(n)
                        || !open(level, pos.set(nx, floor + 2, nz)) || narrow(level, nx, nz, floor)) {
                    continue;
                }
                room.add(n);
                queue.enqueue(n);
            }
        }
        // You only "came in" if a second ago you were outside it (in the doorway or the room before).
        int last = watch.trailLength - 2;
        if (room.size() < ROOM_SMALLEST
                || room.contains(column((int) Math.floor(watch.trailX[last]), (int) Math.floor(watch.trailZ[last])))) {
            return false;
        }
        LongArrayList lights = new LongArrayList();
        List<Integer> delays = new ArrayList<>();
        for (long c : room) {
            int x = columnX(c);
            int z = columnZ(c);
            if (lit(level, pos.set(x, ceiling, z))) {
                lights.add(pos.asLong());
                // Not all at once: the nearest first, each with a little stutter of its own.
                double d = Math.hypot(x - px, z - pz);
                delays.add(150 + (int) (d * 60) + Hash.range(Hash.mix(pos.asLong()), 250));
            }
        }
        if (lights.size() < 2) {
            return false;
        }
        int id = FixtureOutages.startList(level, lights.toLongArray(), toArray(delays), 0, FixtureOutages.Pattern.DIE_STUTTER);
        if (id < 0) {
            return false;
        }
        watch.outage = id;
        watch.room = room;
        watch.outsideRoom = 0;
        watch.endAt = now + ROOM_LONGEST;
        return true;
    }

    /** The tubes behind you along the corridor, going out one by one, the farthest first. */
    private static boolean startCorridor(Watch watch, ServerLevel level, ServerPlayer player, LevelLayout layout, long now) {
        int ceiling = layout.ceilingY();
        int last = watch.trailLength - 1;
        double hx = watch.trailX[last] - watch.trailX[last - 1];
        double hz = watch.trailZ[last] - watch.trailZ[last - 1];
        double len = Math.hypot(hx, hz);
        hx /= len;
        hz /= len;
        double px = player.getX();
        double pz = player.getZ();
        Vec3 eye = player.getEyePosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<double[]> found = new ArrayList<>(); // {behind, block}
        for (int dx = -CORRIDOR_BACK; dx <= CORRIDOR_BACK; dx++) {
            for (int dz = -CORRIDOR_BACK; dz <= CORRIDOR_BACK; dz++) {
                int x = player.getBlockX() + dx;
                int z = player.getBlockZ() + dz;
                double vx = x + 0.5 - px;
                double vz = z + 0.5 - pz;
                double behind = -(vx * hx + vz * hz);
                double side = Math.abs(vx * hz - vz * hx);
                if (behind < CORRIDOR_NEAREST || behind > CORRIDOR_BACK || side > CORRIDOR_SIDE || !lit(level, pos.set(x, ceiling, z))) {
                    continue;
                }
                // Only the corridor you came down: you must be able to see the tube from where you are.
                if (!Observation.lineOfSight(level, eye, new Vec3(x + 0.5, ceiling - 0.05, z + 0.5))) {
                    continue;
                }
                found.add(new double[] {behind, pos.asLong()});
            }
        }
        if (found.size() < 3) {
            return false;
        }
        found.sort((a, b) -> Double.compare(b[0], a[0]));
        long[] lights = new long[found.size()];
        int[] delays = new int[found.size()];
        // Tubes of one fixture (a strip's three blocks, a panel pair) go together: one step per metre or so.
        double previous = Double.NaN;
        int step = -1;
        for (int k = 0; k < lights.length; k++) {
            double behind = found.get(k)[0];
            if (Double.isNaN(previous) || previous - behind > 1.5) {
                step++;
                previous = behind;
            }
            lights[k] = (long) found.get(k)[1];
            delays[k] = step * CORRIDOR_STEP_MS;
        }
        int id = FixtureOutages.startList(level, lights, delays, 0, FixtureOutages.Pattern.DIE_STUTTER);
        if (id < 0) {
            return false;
        }
        watch.outage = id;
        watch.endAt = now + CORRIDOR_DARK + (long) step * CORRIDOR_STEP_MS / 50;
        return true;
    }

    /** A lit district far down your line of sight going dark in a rolling wave. */
    private static boolean startDistrict(Watch watch, ServerLevel level, ServerPlayer player, LevelLayout layout, long seed, long now) {
        if (Math.abs(player.getXRot()) > 35.0f) {
            return false; // looking at the floor or the ceiling, not down the hall
        }
        int ceiling = layout.ceilingY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (litWithin(level, player.getBlockX(), player.getBlockZ(), ceiling, 5, pos) < 1) {
            return false; // you're watching from a lit spot
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
        Vec3 far = eye.add(flat.scale(64.0));
        HitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        double sight = hit.getType() == HitResult.Type.MISS ? 64.0 : hit.getLocation().distanceTo(eye);
        if (sight < SIGHT_AT_LEAST) {
            return false;
        }
        Vec3 target = eye.add(flat.scale(Math.min(sight - 3.0, SIGHT_FURTHEST)));
        int tx = (int) Math.floor(target.x);
        int tz = (int) Math.floor(target.z);
        if (litWithin(level, tx, tz, ceiling, (int) DISTRICT_RADIUS, pos) < 4) {
            return false;
        }
        int id = FixtureOutages.start(level, new BlockPos(tx, ceiling - 1, tz), DISTRICT_RADIUS, 0, FixtureOutages.Pattern.WAVE);
        if (id < 0) {
            return false;
        }
        watch.outage = id;
        watch.endAt = now + DISTRICT_DARK_MIN + Hash.range(Hash.mix(seed ^ (31L * watch.count + tx * 7L + tz)), DISTRICT_DARK_SPREAD);
        return true;
    }

    // ==========================================================================================
    // Helpers
    // ==========================================================================================

    private static void remember(Watch watch, ServerPlayer player) {
        if (watch.trailLength == TRAIL) {
            System.arraycopy(watch.trailX, 1, watch.trailX, 0, TRAIL - 1);
            System.arraycopy(watch.trailZ, 1, watch.trailZ, 0, TRAIL - 1);
            watch.trailLength--;
        }
        watch.trailX[watch.trailLength] = player.getX();
        watch.trailZ[watch.trailLength] = player.getZ();
        watch.trailLength++;
    }

    /** Four seconds running at walking pace, never turning more than about 30 degrees between seconds. */
    private static boolean walkingSteadily(Watch watch) {
        if (watch.trailLength < TRAIL) {
            return false;
        }
        double lastX = 0;
        double lastZ = 0;
        for (int k = 1; k < TRAIL; k++) {
            double dx = watch.trailX[k] - watch.trailX[k - 1];
            double dz = watch.trailZ[k] - watch.trailZ[k - 1];
            double len = Math.hypot(dx, dz);
            if (len < WALK_STEP || len > 12.0) {
                return false; // standing, or teleported
            }
            dx /= len;
            dz /= len;
            if (k > 1 && dx * lastX + dz * lastZ < 0.85) {
                return false;
            }
            lastX = dx;
            lastZ = dz;
        }
        return true;
    }

    /** Hardly moved for the last two seconds: standing and looking. */
    private static boolean standingStill(Watch watch) {
        if (watch.trailLength < 3) {
            return false;
        }
        int last = watch.trailLength - 1;
        return Math.hypot(watch.trailX[last] - watch.trailX[last - 2], watch.trailZ[last] - watch.trailZ[last - 2]) < 1.5;
    }

    /** Moved at least a block and a half in the last second (so a doorway may just have been crossed). */
    private static boolean justCameIn(Watch watch) {
        if (watch.trailLength < 2) {
            return false;
        }
        int last = watch.trailLength - 1;
        double moved = Math.hypot(watch.trailX[last] - watch.trailX[last - 1], watch.trailZ[last] - watch.trailZ[last - 1]);
        return moved >= 1.5 && moved <= 12.0;
    }

    /** A doorway or corridor column: wall within NARROW_REACH on both sides along x, or along z. */
    private static boolean narrow(ServerLevel level, int x, int z, int floor) {
        return wallWithin(level, x, z, floor, 1, 0) && wallWithin(level, x, z, floor, -1, 0)
                || wallWithin(level, x, z, floor, 0, 1) && wallWithin(level, x, z, floor, 0, -1);
    }

    private static boolean wallWithin(ServerLevel level, int x, int z, int floor, int dx, int dz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 1; k <= NARROW_REACH; k++) {
            if (!open(level, pos.set(x + dx * k, floor + 2, z + dz * k))) {
                return true;
            }
        }
        return false;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    /** A Level 0 ceiling tube that is on right now (a panel or a strip, not a dead one, not already out). */
    private static boolean lit(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return (state.is(ModBlocks.FLUORESCENT_LIGHT.get()) || state.is(Level0Blocks.TROFFER_STRIP.get()))
                && !FixtureOutages.isOut(level, pos);
    }

    private static int litWithin(ServerLevel level, int x, int z, int ceiling, int radius, BlockPos.MutableBlockPos pos) {
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius && lit(level, pos.set(x + dx, ceiling, z + dz))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int columnX(long c) {
        return (int) (c >> 32);
    }

    private static int columnZ(long c) {
        return (int) c;
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = list.get(k);
        }
        return out;
    }
}
