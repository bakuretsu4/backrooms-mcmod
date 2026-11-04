package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level228StatePayload;
import com.rickr.backrooms.world.layout.Level228Layout;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server rules of Level 228 v2, "Hall of Mirrors" (the maze: world/layout/Level228Plan). All the
 * mechanics that matter are decided here, for everyone at once:
 * <ul>
 *   <li><b>Arrival</b> (from Level 94, a late joiner, a respawn): you step out of the foyer's
 *       arrival mirror ({@code Level228Layout#preferredArrival}), facing the facade.</li>
 *   <li><b>Shifting passages.</b> Now and then (every 3-6 minutes) one shifting class of a room
 *       turns mirror -> empty frame or back, the whole orbit at once so the room stays
 *       symmetric; only in a room nobody is within 8 blocks of, so nobody sees it happen and no
 *       pane ever appears where someone stands. Every state keeps the way through (tested).</li>
 *   <li><b>Secret panels.</b> Pushing against the hidden passage's panel for a moment opens it
 *       for everyone, for good.</li>
 *   <li><b>The final chamber.</b> Walking into the accurate mirror takes only you into the exit
 *       hallway; a wrong one quietly returns only you to the start of the Glass Labyrinth (you
 *       step out of its side mirror), with nothing but a faint glass hum.</li>
 *   <li><b>Group exit.</b> Standing in the vestibule behind the hallway's door makes you ready;
 *       when every active player in the level (online here, alive, not spectating) is ready, all
 *       go through to Level 369 in the same tick. A solo player goes at once. Leaving, logging out
 *       or dying simply drops you from the count.</li>
 * </ul>
 * The ShiftState lives in the level's saved data (the blocks it changed are saved with the
 * chunks), is sent to every client in the level on each change, and chunks saved under an older
 * state are brought up to date when they load.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level228Rules {
    /** Shifts happen this often (ticks), when some room is free of players. */
    private static final int SHIFT_MIN = 3600;
    private static final int SHIFT_SPREAD = 3600;
    /** No shift while any player is this close to the room (blocks). */
    private static final int SHIFT_CLEAR = 8;
    /** Pressing against an exit mirror this long (ticks) goes through it. */
    private static final int MIRROR_PRESS = 8;
    /** ... and against a secret panel this long opens it. */
    private static final int SECRET_PRESS = 16;
    /** How close (blocks) the body must be to a pane's plane to press it (the pane is 1/16 thick). */
    private static final double TOUCH = 0.22;
    /** Cosine of the largest angle off the pane's normal you can push it at (~60 degrees). */
    private static final double FACING = 0.5;

    private static final Map<UUID, int[]> PRESS = new HashMap<>();
    private static final ConcurrentLinkedQueue<ChunkPos> LOADED = new ConcurrentLinkedQueue<>();
    private static long nextShift = -1;
    private static final Level228Plan.Mirror MIRROR = new Level228Plan.Mirror();

    // ==========================================================================================
    // State
    // ==========================================================================================

    /** The current shared shift state of Level 228 (server thread). */
    public static Level228Plan.ShiftState shift(ServerLevel level) {
        Data d = data(level);
        if (d.state == null) {
            d.state = new Level228Plan.ShiftState(d.bits, d.version);
        }
        return d.state;
    }

    private static void setShift(ServerLevel level, Level228Plan.ShiftState next) {
        Data d = data(level);
        Level228Plan.ShiftState old = shift(level);
        if (next.bits == old.bits) {
            return;
        }
        d.state = next;
        d.bits = next.bits;
        d.version = next.version;
        d.setDirty();
        Level228Layout.liveBits = next.bits;
        long seed = seed(level);
        long changed = old.bits ^ next.bits;
        for (int b = 0; b < Level228Plan.bitCount(); b++) {
            if ((changed >>> b & 1L) != 0) {
                apply(level, seed, next.bits, Level228Plan.columnsOfBit(seed, b));
            }
        }
        for (ServerPlayer p : level.players()) {
            sendState(p);
        }
    }

    /** Writes the blocks of these columns for state {@code bits}, in loaded chunks (the rest on load). */
    private static void apply(ServerLevel level, long seed, long bits, int[] columns) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int packed : columns) {
            int x = packed >> 16;
            int z = (short) packed;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            for (int y = Level228Plan.FEET_Y; y <= Level228Plan.BAND_Y; y++) {
                BlockState want = Level228Layout.liveBlock(seed, bits, x, y, z);
                at.set(x, y, z);
                if (level.getBlockState(at) != want) {
                    level.setBlock(at, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static long seed(ServerLevel level) {
        return BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_228);
    }

    private static boolean isLevel228(ServerLevel level) {
        return BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_228;
    }

    // ==========================================================================================
    // Level tick: chunk catch-up, the shift scheduler, the group exit
    // ==========================================================================================

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && isLevel228(level)) {
            LOADED.add(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isLevel228(level)) {
            return;
        }
        long seed = seed(level);
        Level228Plan.ShiftState s = shift(level);
        Level228Layout.liveBits = s.bits;
        catchUpChunks(level, seed, s.bits);
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (nextShift < 0) {
            nextShift = now + SHIFT_MIN + level.random.nextInt(SHIFT_SPREAD);
        }
        if (now >= nextShift) {
            nextShift = tryShift(level, false) >= 0 ? now + SHIFT_MIN + level.random.nextInt(SHIFT_SPREAD) : now + 600;
        }
        groupExit(level, seed, players, now);
    }

    /** Brings newly loaded chunks' shifting columns up to the current state. */
    private static void catchUpChunks(ServerLevel level, long seed, long bits) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (ChunkPos c; (c = LOADED.poll()) != null; ) {
            if (!level.hasChunk(c.x, c.z)) {
                continue;
            }
            for (int x = c.getMinBlockX(); x <= c.getMaxBlockX(); x++) {
                for (int z = c.getMinBlockZ(); z <= c.getMaxBlockZ(); z++) {
                    if (!Level228Plan.isDynamic(seed, x, z)) {
                        continue;
                    }
                    for (int y = Level228Plan.FEET_Y; y <= Level228Plan.BAND_Y; y++) {
                        BlockState want = Level228Layout.liveBlock(seed, bits, x, y, z);
                        at.set(x, y, z);
                        if (level.getBlockState(at) != want) {
                            level.setBlock(at, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        }
    }

    /**
     * Toggles one shifting class of a room no player is near (any room if {@code forced}, as
     * long as no pane would appear inside someone). Returns the bit, or -1 if none could move.
     */
    public static int tryShift(ServerLevel level, boolean forced) {
        long seed = seed(level);
        List<ServerPlayer> players = level.players();
        int bits = 0;
        int[] candidates = new int[Level228Plan.bitCount()];
        for (int b = 0; b < Level228Plan.bitCount(); b++) {
            if (Level228Plan.isShiftBit(b) && (forced ? !paneOnPlayer(level, seed, b, players) : roomClear(seed, b, players))) {
                candidates[bits++] = b;
            }
        }
        if (bits == 0) {
            return -1;
        }
        int b = candidates[level.random.nextInt(bits)];
        Level228Plan.ShiftState s = shift(level);
        setShift(level, s.with(b, !s.on(b)));
        return b;
    }

    private static boolean roomClear(long seed, int bit, List<ServerPlayer> players) {
        int[] r = Level228Plan.bitRoomBounds(seed, bit);
        for (ServerPlayer p : players) {
            if (p.getX() > r[0] - SHIFT_CLEAR && p.getX() < r[2] + 1 + SHIFT_CLEAR
                    && p.getZ() > r[1] - SHIFT_CLEAR && p.getZ() < r[3] + 1 + SHIFT_CLEAR) {
                return false;
            }
        }
        return true;
    }

    /** True if any of the bit's columns is within reach of a player's body (a pane could close on them). */
    private static boolean paneOnPlayer(ServerLevel level, long seed, int bit, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            AABB box = p.getBoundingBox().inflate(1.0);
            for (int packed : Level228Plan.columnsOfBit(seed, bit)) {
                int x = packed >> 16;
                int z = (short) packed;
                if (box.maxX > x && box.minX < x + 1 && box.maxZ > z && box.minZ < z + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One player's standing for the group exit (pure, so the rule can be tested). */
    public record Member(boolean alive, boolean spectator, boolean ready) {
        boolean active() {
            return alive && !spectator;
        }
    }

    /**
     * How many active members are still not ready, or 0 when the group goes now (everyone active
     * is ready and at least one is). -1 when nobody active is ready (no one is waiting).
     */
    public static int stillMissing(List<Member> members) {
        int active = 0;
        int ready = 0;
        for (Member m : members) {
            if (m.active()) {
                active++;
                if (m.ready()) {
                    ready++;
                }
            }
        }
        return stillMissing(active, ready);
    }

    /** The rule itself, on counts: -1 nobody ready, 0 go now, else how many active are not ready. */
    static int stillMissing(int active, int ready) {
        return ready == 0 ? -1 : active - ready;
    }

    /**
     * The group exit over time: everyone active must have been ready for {@link #READY_HOLD}
     * consecutive level ticks, so one tick's stale position (a teleport not yet settled, a body
     * brushing the vestibule's edge) can never send anyone. Pure, for the tests.
     */
    public static final class GroupGate {
        /** Ticks (0.5 s) the whole group must have stood ready. */
        public static final int READY_HOLD = 10;
        private int readyTicks;

        /** One level tick: true when the ready players go now; else {@link #missing} says why not. */
        boolean step(int active, int ready) {
            missing = stillMissing(active, ready);
            if (missing != 0) {
                readyTicks = 0;
                return false;
            }
            if (++readyTicks < READY_HOLD) {
                return false;
            }
            readyTicks = 0;
            return true;
        }

        /** After {@link #step}: -1 nobody ready, 0 everyone ready, else how many still missing. */
        int missing;
    }

    private static final GroupGate GATE = new GroupGate();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static void groupExit(ServerLevel level, long seed, List<ServerPlayer> players, long now) {
        int active = 0;
        int ready = 0;
        for (ServerPlayer p : players) {
            // Everyone alive and not spectating counts, also while their picture breaks up for
            // some other move: only standing in the vestibule makes you ready.
            if (new Member(p.isAlive(), p.isSpectator(), false).active()) {
                active++;
                if (ready(seed, p)) {
                    ready++;
                }
            }
        }
        boolean go = GATE.step(active, ready);
        int missing = GATE.missing;
        if (missing < 0) {
            return;
        }
        if (go) {
            // Copy first: useExit starts each move; the list is the level's own.
            ServerPlayer[] group = players.toArray(new ServerPlayer[0]);
            for (ServerPlayer p : group) {
                if (p.isAlive() && !p.isSpectator() && !Travel.isMoving(p) && ready(seed, p)) {
                    LOGGER.info("Level 228: {} goes on with the group from {}", p.getGameProfile().getName(), p.position());
                    Travel.useExit(p, BackroomsLevel.LEVEL_228);
                }
            }
            return;
        }
        if (missing > 0 && now % 20 == 0) {
            Component msg = missing == 1
                    ? Component.translatable("message.backrooms.level228.waiting_one")
                    : Component.translatable("message.backrooms.level228.waiting", missing);
            for (ServerPlayer p : players) {
                if (ready(seed, p)) {
                    p.displayClientMessage(msg, true);
                }
            }
        }
    }

    private static boolean ready(long seed, ServerPlayer p) {
        return Level228Plan.inVestibule(seed, p.getX(), p.getZ());
    }

    // ==========================================================================================
    // Player tick: the chamber's mirrors, the secret panels
    // ==========================================================================================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()
                || !isLevel228(player.serverLevel()) || Travel.isMoving(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = seed(level);
        Level228Plan.ShiftState s = shift(level);
        int[] press = PRESS.computeIfAbsent(player.getUUID(), u -> new int[2]);
        int target = pressedTarget(seed, s, player);
        if (target == Integer.MIN_VALUE || target != press[0]) {
            press[0] = target;
            press[1] = 0;
            return;
        }
        press[1]++;
        if (target >= 0 && target < 3 && press[1] >= MIRROR_PRESS) {
            press[1] = 0;
            throughExitMirror(player, seed, target);
        } else if (target >= 3 && press[1] >= SECRET_PRESS) {
            press[1] = 0;
            openSecret(level, target - 3, player);
        }
    }

    /**
     * What the player is pushing against right now: chamber mirror 0..2, a secret panel as
     * 3 + its bit, or MIN_VALUE for nothing.
     */
    static int pressedTarget(long seed, Level228Plan.ShiftState s, ServerPlayer player) {
        AABB box = player.getBoundingBox();
        Vec3 look = player.getLookAngle();
        if (Level228Plan.zoneAt(seed, player.getBlockX(), player.getBlockZ()) == Level228Plan.Zone.CHAMBER) {
            for (int m = 0; m < 3; m++) {
                if (Level228Plan.mirror(seed, s, Level228Plan.exitMirrorId(m), MIRROR)
                        && pushes(box, look, MIRROR.axis, MIRROR.plane, MIRROR.min, MIRROR.max)) {
                    return m;
                }
            }
            return Integer.MIN_VALUE;
        }
        int bx = player.getBlockX();
        int bz = player.getBlockZ();
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                int bit = Level228Plan.secretAt(seed, s, x, z);
                if (bit < 0) {
                    continue;
                }
                boolean planeX = Math.floorMod(x, 3) == 0;
                int first = planeX ? z - (Math.floorMod(z, 3) - 1) : x - (Math.floorMod(x, 3) - 1);
                if (pushes(box, look, planeX ? 0 : 1, (planeX ? x : z) + 0.5, first, first + 2)) {
                    return 3 + bit;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The body is against the pane (within {@link #TOUCH} of its plane, overlapping its span by
     * more than a sliver) and faces into it.
     */
    static boolean pushes(AABB box, Vec3 look, int axis, double plane, double min, double max) {
        double lo = axis == 0 ? box.minX : box.minZ;
        double hi = axis == 0 ? box.maxX : box.maxZ;
        double aLo = axis == 0 ? box.minZ : box.minX;
        double aHi = axis == 0 ? box.maxZ : box.maxX;
        if (aHi <= min + 0.15 || aLo >= max - 0.15) {
            return false;
        }
        double centre = (lo + hi) * 0.5;
        double toward = plane > centre ? 1.0 : -1.0;
        double gap = toward > 0 ? plane - hi : lo - plane;
        return gap < TOUCH && gap > -0.3 && (axis == 0 ? look.x : look.z) * toward >= FACING;
    }

    /**
     * Through a chamber mirror: the accurate one lets you out into the hallway; the others put you
     * back at the start of the Glass Labyrinth. Only you move; nothing announces it.
     */
    static void throughExitMirror(ServerPlayer player, long seed, int which) {
        boolean correct = which == Level228Plan.correctExitMirror(seed);
        double[] to = correct ? Level228Plan.hallEntry(seed) : Level228Plan.checkpoint(seed);
        player.teleportTo(player.serverLevel(), to[0], to[1], to[2], (float) to[3], player.getXRot());
        player.resetFallDistance();
        // A faint glass hum, for you alone.
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.18f, 0.55f);
    }

    private static void openSecret(ServerLevel level, int bit, ServerPlayer by) {
        Level228Plan.ShiftState s = shift(level);
        if (s.on(bit)) {
            return;
        }
        setShift(level, s.with(bit, true));
        level.playSound(null, by.blockPosition(), SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 0.6f);
    }

    // ==========================================================================================
    // Sync and arrival
    // ==========================================================================================

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTo() == BackroomsLevel.LEVEL_228.dimension()) {
            sendState(player);
            faceOutOfArrivalMirror(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLevel228(player.serverLevel())) {
            // A respawn in the level comes out of the arrival mirror like every arrival.
            double[] a = Level228Plan.arrival(seed(player.serverLevel()));
            player.teleportTo(player.serverLevel(), a[0] - 0.5, a[1], a[2], (float) a[3], 0f);
            sendState(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLevel228(player.serverLevel())) {
            sendState(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PRESS.remove(event.getEntity().getUUID());
    }

    /** Someone who just came out of the arrival mirror looks away from it, at the facade. */
    private static void faceOutOfArrivalMirror(ServerPlayer player) {
        double[] a = Level228Plan.arrival(seed(player.serverLevel()));
        if (Math.abs(player.getX() - a[0]) < 1.5 && Math.abs(player.getZ() - a[2]) < 1.5) {
            player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), (float) a[3], 0f);
        }
    }

    private static void sendState(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long seed = seed(level);
        Level228Plan.ShiftState s = isLevel228(level) ? shift(level) : Level228Plan.ShiftState.INITIAL;
        PacketDistributor.sendToPlayer(player, new Level228StatePayload(seed, s.bits, s.version));
    }

    // ==========================================================================================
    // Saved data
    // ==========================================================================================

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Data::new, Data::load, null), "backrooms_level228");
    }

    private static final class Data extends SavedData {
        long bits;
        int version;
        Level228Plan.ShiftState state;

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.bits = tag.getLong("shift");
            d.version = tag.getInt("version");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("shift", bits);
            tag.putInt("version", version);
            return tag;
        }
    }

    private Level228Rules() {}
}
