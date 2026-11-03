package com.rickr.backrooms.world;

import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.network.ArrivalCardPayload;
import com.rickr.backrooms.network.IntroCutscenePayload;
import com.rickr.backrooms.network.EscapePayload;
import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level227Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Moving players between reality and the Backrooms floors.
 *
 * <p>Every move from one floor to another is filmed the same way, whatever the exit looks like:
 * the picture breaks up like a video stream that lost its keyframes ("datamoshing",
 * client/intro/NoclipTransition). We don't move the player at once: first their client freezes
 * and crumbles the picture for {@link #MOSH_TICKS}, then we move them and the client smears the
 * old picture into the new place. Only escaping back to reality has its own ending ("STOP").
 *
 * <p>Arrival points are computed from the floor plan (no chunk loading needed) by spiralling out
 * from a start column to the nearest walkable spot. Friends who use the same exit start the search
 * from the same exit centre, so they land in the same spot.
 */
public final class Travel {
    private static final int SEARCH_RADIUS = 96;
    /** How long the picture breaks up before the move (must match NoclipTransition's LAG_MS: 0.5 s). */
    public static final int MOSH_TICKS = 10;

    /** Players whose picture is breaking up: ticks left, and the move to make then. */
    private static final Map<UUID, PendingMove> PENDING = new HashMap<>();

    private static final class PendingMove {
        int ticksLeft = MOSH_TICKS;
        final Consumer<ServerPlayer> move;

        PendingMove(Consumer<ServerPlayer> move) {
            this.move = move;
        }
    }

    /**
     * Noclip out of reality into Level 0 (every way in ends here: suffocating in a block, pushing
     * into a wall in the dark, filming the dark, lying down in a bed; see {@link RealWorldEntries}).
     */
    public static void enterBackrooms(ServerPlayer player) {
        moshTo(player, p -> {
            if (p.isSleeping()) {
                p.stopSleepInBed(true, false); // through the bed: you arrive on your feet
            }
            sendToQuietly(p, BackroomsLevel.LEVEL_0, p.getBlockX(), p.getBlockZ());
            showArrival(p, BackroomsLevel.LEVEL_0, "message.backrooms.noclip");
        });
    }

    /**
     * Starts the datamosh; {@code move} runs when the picture has broken up. Returns false (and
     * does nothing) if this player is already on the way somewhere: exits that you stand in, fall
     * through or swim down are seen again every tick until you're gone.
     */
    public static boolean moshTo(ServerPlayer player, Consumer<ServerPlayer> move) {
        if (PENDING.putIfAbsent(player.getUUID(), new PendingMove(move)) != null) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.FREEZE));
        return true;
    }

    /** True while this player's picture is breaking up before a move. */
    public static boolean isMoving(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    /** Called every server tick for each player: makes the move once the picture has broken up. */
    public static void tick(ServerPlayer player) {
        PendingMove pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!player.isAlive()) {
            PENDING.remove(player.getUUID()); // died on the way: you respawn instead (the client gives the picture back)
            return;
        }
        if (pending.ticksLeft-- > 0) {
            return;
        }
        PENDING.remove(player.getUUID());
        pending.move.accept(player);
        // Hold the broken frame while the new place loads, then mosh it in.
        PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.ARRIVED));
    }

    /** Player left mid-move: forget it (they stay where they were). */
    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /**
     * Sends the player to {@code target}, searching for floor near (x, z), through the datamosh,
     * and shows the level's arrival card once they are there.
     */
    public static void sendTo(ServerPlayer player, BackroomsLevel target, int x, int z) {
        sendTo(player, target, x, z, null);
    }

    /**
     * As {@link #sendTo(ServerPlayer, BackroomsLevel, int, int)}, with {@code noteKey} (a lang key,
     * or null) as the arrival card's "how you got here" line, e.g. the Level 9 foxhole's.
     */
    public static void sendTo(ServerPlayer player, BackroomsLevel target, int x, int z, @Nullable String noteKey) {
        moshTo(player, p -> {
            if (sendToQuietly(p, target, x, z)) {
                showArrival(p, target, noteKey);
            }
        });
    }

    /**
     * The camcorder arrival card for {@code level} (client/hud/ArrivalCard): its name and one-line
     * description ({@code level.backrooms.<level>}, {@code message.backrooms.arrive.<level>}), plus
     * an optional note ({@code noteKey}, a lang key, or null) such as "You clipped through reality."
     * The card replaces the old chat lines; nothing goes to chat.
     */
    public static void showArrival(ServerPlayer player, BackroomsLevel level, @Nullable String noteKey) {
        showArrival(player, level, noteKey, 0);
    }

    /** As above, coming up {@code delayMs} later: for arrivals with their own fade (Level 37's openings). */
    public static void showArrival(ServerPlayer player, BackroomsLevel level, @Nullable String noteKey, int delayMs) {
        PacketDistributor.sendToPlayer(player,
                new ArrivalCardPayload(level.getSerializedName(), noteKey == null ? "" : noteKey, delayMs));
    }

    /** Teleport only, no transition effect: the caller brings its own. Returns false if the level is missing. */
    public static boolean sendToQuietly(ServerPlayer player, BackroomsLevel target, int x, int z) {
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) {
            return false;
        }
        long seed = BackroomsSeeds.layoutSeed(level, target);
        BackroomsLevel from = BackroomsLevel.fromDimension(player.level().dimension());
        // Level 227 puts every arrival on a branch far from everyone else (canon: wanderers are
        // always separated); every other level picks the spot from the floor plan.
        BlockPos separated = target == BackroomsLevel.LEVEL_227 ? Level227Layout.separatedArrival(level, player) : null;
        Arrival arrival = separated != null
                ? new Arrival(separated, true, false)
                : arrival(target.layout(), seed, x, z, from, from != target);
        BlockPos spot = arrival.spot();
        // Feet exactly on the surface: Level 9's sidewalks are half slabs (feet at y + 0.5).
        double feet = arrival.exact() ? spot.getY() : target.layout().feetY(seed, spot.getX(), spot.getZ());
        CameraService.startTape(player);
        teleport(player, level, new Vec3(spot.getX() + 0.5, feet, spot.getZ() + 0.5));
        return true;
    }

    /**
     * Where an arrival lands: the block the feet are in; whether its y is exactly the feet (a
     * level's own preferred spot) rather than a column to stand in; and whether no safe column was
     * found, so the spot is the level's arrival hint, unchecked.
     */
    public record Arrival(BlockPos spot, boolean exact, boolean fallback) {}

    /** Extra blocks beyond the clearance when pushing an arrival away, for the walkable-spot search. */
    private static final int CLEARANCE_SLACK = 24;
    /** Directions tried, evenly around the circle, when pushing straight away is not enough. */
    private static final int CLEARANCE_DIRECTIONS = 16;
    /** Tries at a landing spot far enough from the exits before taking the farthest one found. */
    private static final int CLEARANCE_ATTEMPTS = 5;

    /**
     * Where someone sent to (x, z) of this level lands. Some floors pick the spot themselves
     * depending on where you come from (Level 39: Level 37's pits and the Poolrooms' archway bring
     * you up in its tunnels; Level 369: Level 94's dark doors drop you through a skylight); otherwise the nearest safe column.
     * With {@code keepClear} (arriving from another level) the spot keeps the level's
     * {@link LevelLayout#arrivalExitClearance} from the nearest exit: the start point is pushed away
     * first, and since the level's own pick can wander off it, a spot that still lands too close
     * is pushed again, a little further each time. Depends only on (x, z), so friends using the
     * same exit land together.
     */
    public static Arrival arrival(LevelLayout layout, long seed, int x, int z, @Nullable BackroomsLevel from, boolean keepClear) {
        int min = layout.arrivalExitClearance();
        boolean clear = keepClear && min > 0 && layout.hasExits();
        Arrival best = null;
        double bestDistance = -1;
        int sx = x;
        int sz = z;
        for (int attempt = 0; attempt < CLEARANCE_ATTEMPTS; attempt++) {
            if (clear) {
                int[] start = clearOfExits(layout, seed, sx, sz, CLEARANCE_SLACK * (1 + 3 * attempt));
                sx = start[0];
                sz = start[1];
            }
            BlockPos preferred = layout.preferredArrival(seed, sx, sz, from);
            Arrival a = preferred != null ? new Arrival(preferred, true, false) : safeArrival(layout, seed, sx, sz);
            if (!clear) {
                return a;
            }
            int[] exit = layout.exitPosition(seed, a.spot().getX(), a.spot().getZ());
            double d = Math.hypot(a.spot().getX() - exit[0], a.spot().getZ() - exit[1]);
            if (d >= min) {
                return a;
            }
            if (d > bestDistance) {
                best = a;
                bestDistance = d;
            }
            sx = a.spot().getX();
            sz = a.spot().getZ();
        }
        return best;
    }

    /**
     * (x, z), or the nearest point to it whose nearest exit is at least the level's clearance plus
     * {@code slack} away. First pushes straight away from the nearest exit, a few times (a push can
     * bring a neighbouring exit close); failing that, tries rings of points around (x, z).
     */
    private static int[] clearOfExits(LevelLayout layout, long seed, int x, int z, int slack) {
        int min = layout.arrivalExitClearance();
        double want = min + slack;
        int px = x;
        int pz = z;
        for (int i = 0; i < 4; i++) {
            int[] exit = layout.exitPosition(seed, px, pz);
            double dx = px - exit[0];
            double dz = pz - exit[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= want) {
                return new int[] {px, pz};
            }
            if (d < 1) {
                dx = 1;
                dz = 0;
                d = 1;
            }
            px = exit[0] + (int) Math.round(dx / d * want);
            pz = exit[1] + (int) Math.round(dz / d * want);
        }
        for (int r = min; r <= 4 * min; r += min / 2) {
            for (int k = 0; k < CLEARANCE_DIRECTIONS; k++) {
                double a = 2 * Math.PI * k / CLEARANCE_DIRECTIONS;
                int cx = x + (int) Math.round(Math.cos(a) * r);
                int cz = z + (int) Math.round(Math.sin(a) * r);
                int[] exit = layout.exitPosition(seed, cx, cz);
                double ex = cx - exit[0];
                double ez = cz - exit[1];
                if (ex * ex + ez * ez >= want * want) {
                    return new int[] {cx, cz};
                }
            }
        }
        return new int[] {px, pz};
    }

    /** How high above the town a gondola rider appears in Level 39 (under the rendered cloud ceiling). */
    private static final double LEVEL39_SKY_Y = 250.0;
    /** Slow Falling for the drift down through the clouds: about 25 seconds. */
    private static final int LEVEL39_SKY_FALL_TICKS = 25 * 20;

    /**
     * The side entrance to Level 39: the gondola in Level 9's Crimson Field carries you up into the
     * whiteout, and you noclip through the ceiling of the superstructure. You appear high above the
     * town near (x, z) and drift down on Slow Falling; {@link Level39Rules} makes sure the landing
     * never hurts. Returns false if Level 39 isn't loaded.
     */
    public static boolean sendToLevel39Sky(ServerPlayer player, int x, int z) {
        BackroomsLevel target = BackroomsLevel.LEVEL_39;
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) {
            return false;
        }
        Checkpoints.clear(player); // a move between floors: the old checkpoint no longer applies
        // Far east lies the bigger-inside copy region, not the town: come down over an ordinary town.
        int townX = Level39AnomalyPlan.isCopy(x) ? Level39Layout.local(x) : x;
        BlockPos ground = findSafeSpot(level, target, townX, z);
        // Let go of the chair high over the field: hang in the air while the picture breaks up.
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, MOSH_TICKS + 20, 0, false, false, false));
        moshTo(player, p -> {
            CameraService.startTape(p);
            Level39Rules.markSkyArrival(p);
            teleport(p, level, new Vec3(ground.getX() + 0.5, LEVEL39_SKY_Y, ground.getZ() + 0.5));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LEVEL39_SKY_FALL_TICKS, 0, false, false, true));
            showArrival(p, target, null);
        });
        return true;
    }

    /**
     * Used the exit of {@code from} (a door, a stairwell, the drain, a dive...): on to the next
     * floor of the path, or escape if it was the last one. Leaving a floor forgets its checkpoint
     * (you can't respawn back up there).
     */
    public static void useExit(ServerPlayer player, BackroomsLevel from) {
        if (isMoving(player)) {
            return; // already on the way
        }
        Checkpoints.clear(player);
        BackroomsLevel next = from.next();
        if (next == null) {
            escape(player);
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), from);
        int[] exit = from.layout().exits().nearest(seed, player.getBlockX(), player.getBlockZ());
        sendTo(player, next, exit[0], exit[1]);
    }

    /** Back to reality at world spawn. "...or did you?" */
    public static void escape(ServerPlayer player) {
        Checkpoints.clear(player); // you left the Backrooms: checkpoints no longer apply
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        teleport(player, overworld, Vec3.atBottomCenterOf(top));
        PacketDistributor.sendToPlayer(player, new EscapePayload());
        player.playNotifySound(ModSounds.ESCAPE.get(), SoundSource.MASTER, 1.0f, 1.0f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.backrooms.escaped")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.backrooms.escaped.subtitle")));
    }

    /** Everyone within {@code radius} of {@code origin} in the same level goes through together. */
    public static void useExitAsGroup(ServerPlayer opener, BackroomsLevel from, double radius) {
        List<ServerPlayer> group = opener.serverLevel().getPlayers(p -> p == opener || p.distanceTo(opener) <= radius);
        for (ServerPlayer member : group) {
            useExit(member, from);
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        // Travel often runs inside the player's tick (falling out of a level, anomalies).
        Teleports.settle(player);
    }

    /** Nearest safe column to (x, z) (or to the level's hint for it), searched in growing squares. */
    public static BlockPos findSafeSpot(ServerLevel level, BackroomsLevel target, int x, int z) {
        return safeArrival(target.layout(), BackroomsSeeds.layoutSeed(level, target), x, z).spot();
    }

    private static Arrival safeArrival(LevelLayout layout, long seed, int x, int z) {
        int[] hint = layout.arrivalHint(seed, x, z);
        BlockPos found = searchWalkable(layout, seed, hint[0], hint[1], SEARCH_RADIUS);
        return found != null
                ? new Arrival(found, false, false)
                : new Arrival(new BlockPos(hint[0], layout.standY(seed, hint[0], hint[1]), hint[1]), false, true);
    }

    /**
     * Spiral search for a safe arrival column. Most levels also want its four neighbours safe (so
     * you never land squeezed against a wall or at the edge of a pool); Level 9 picks a spot on a
     * narrow sidewalk, where that can't hold ({@link LevelLayout#arrivalNeedsSpace()}). Returns the
     * block the player's feet are in, which on the Poolrooms' raised walkways is one block higher.
     */
    @Nullable
    public static BlockPos searchWalkable(LevelLayout layout, long seed, int x, int z, int radius) {
        boolean needsSpace = layout.arrivalNeedsSpace();
        for (int r = 0; r <= radius; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) {
                        continue;
                    }
                    int cx = x + ox;
                    int cz = z + oz;
                    if (layout.isSafeArrival(seed, cx, cz) && (!needsSpace
                            || layout.isSafeArrival(seed, cx + 1, cz) && layout.isSafeArrival(seed, cx - 1, cz)
                            && layout.isSafeArrival(seed, cx, cz + 1) && layout.isSafeArrival(seed, cx, cz - 1))) {
                        return new BlockPos(cx, layout.standY(seed, cx, cz), cz);
                    }
                }
            }
        }
        return null;
    }

    public static boolean inBackrooms(Level level) {
        return BackroomsLevel.fromDimension(level.dimension()) != null;
    }

    private Travel() {}
}
