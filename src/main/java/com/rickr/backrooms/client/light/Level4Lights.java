package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.OfficeWindowBlock;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.Level4Atmosphere;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 4's Veil lights: the light of the office comes from its windows, plus a few lit troffers.
 *
 * <ul>
 *   <li><b>Window light.</b> Every run of {@code office_window} panes (up to {@value #MAX_SPAN}
 *       blocks, cut at the window bays' edges when the panes say where they are) gets one soft area
 *       light the size of its glass, just in front of it, tipped a little toward the floor. It is
 *       brightest on the floor, sills and columns near the window and falls off over
 *       {@value #WINDOW_DISTANCE} blocks into the floor plate, and with shadows on, columns,
 *       partitions and desks throw soft-edged shadows away from the windows. The wall around the
 *       window lies behind the light's plane, so it never lights its own wall (no clipped band).
 *       The storm's lightning flares these lights with the lightmap flash.</li>
 *   <li><b>Troffer downlights.</b> A lit panel ({@code office_panel}, not dead) is a small area
 *       light the size of its lens, facing down: a soft pool on the carpet under it that fades
 *       out at the louvre's cut-off angle, and no hot spot on the ceiling tiles around it (a point
 *       light half a block under the ceiling used to light the tiles beside it nearly white). They
 *       follow the panel's power, flicker and blackout (BackroomsLights keeps that state), and are
 *       left out of the usual point-light set on Level 4.</li>
 * </ul>
 * How many of each come from the QUALITY setting (ClientSettings.Quality#maxL4Windows and
 * #maxL4Troffers), nearest first within the quality's light range, fading out over the last third
 * of it so nothing pops on at the edge. Nothing is allocated per tick: window runs are found when
 * chunks load (like SunBeams) and grouped only when you have walked a few blocks; Veil lights are
 * made only when one comes into range.
 *
 * <p>Only active while Veil's lights are ({@link BackroomsLights#isVeilLightingActive()}); under
 * Iris the windows' and troffers' block light alone lights the level.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level4Lights {
    // --- Window light: the knobs -----------------------------------------------------------------
    /** Window light colour: a cool, slightly blue overcast grey (matches Level4Atmosphere's fill tint). */
    private static final float WINDOW_R = 0.80f;
    private static final float WINDOW_G = 0.87f;
    private static final float WINDOW_B = 1.0f;
    /**
     * Strength of a window run's light: the main knob for "brighter/darker by the windows". In
     * game (2026-11, first test) 1.4 over 18 blocks was barely visible on the carpet, so it is
     * stronger and shorter now: a soft cool pool in front of the windows that has faded out by
     * ~10-12 blocks ((1 - d/16)^3: 0.67 at 2 blocks, 0.42 at 4, 0.13 at 8, 0.02 at 12). The
     * highlight shoulder (AtmosphereGrade ROLLOFF_4) keeps white walls facing a window off white.
     */
    private static final float WINDOW_BRIGHTNESS = 3.0f;
    /** How far a window's light reaches (it fades as (1 - d/reach)^3): how deep the gradient runs into the floor. */
    private static final float WINDOW_DISTANCE = 16.0f;
    /**
     * Spread of the light, radians from straight out of the glass (Veil fades it smoothly to 0 at
     * this angle). Wide: the glow also reaches the floor right under the sill and along the wall.
     */
    private static final float WINDOW_ANGLE = 1.35f;
    /** Tipped this many degrees toward the floor, so the carpet in front gets more than the walls and ceiling. */
    private static final float WINDOW_TILT_DEG = 18.0f;
    /** The light's rectangle is this much smaller than the glass on each side (keeps its edge off the piers). */
    private static final float WINDOW_INSET = 0.1f;
    /** And sits this far in front of the glass (on it, the shadow test would start inside the window block). */
    private static final float FACE_GAP = 0.05f;
    /** Longest window light (blocks): three 3-block bays. Longer runs are split into several lights. */
    private static final int MAX_SPAN = 9;
    /** Window bays are this wide (the panes' left/mid/right): splits fall on a bay edge where possible. */
    private static final int BAY = 3;
    /** Window lights reach this times the quality's light range (Veil's shadows only cover ~32 blocks). */
    private static final float WINDOW_RANGE_SCALE = 1.0f;
    /** How much the lightning adds to a window's light at a full flash (1.5 = 2.5 times as bright). */
    private static final float WINDOW_FLASH_BOOST = 1.5f;

    // --- Troffer downlights: the knobs ----------------------------------------------------------
    /** Cool office white (~4500 K), the panel's fixture colour. */
    private static final float TROFFER_R = 0.93f;
    private static final float TROFFER_G = 0.96f;
    private static final float TROFFER_B = 1.0f;
    /**
     * Strength of a lit panel. Estimate: the carpet right under it (~4 blocks down) ~0.3 before the
     * grade, a wall 2 blocks to the side ~0.2, gone about 4 blocks out: a lit island, not a flood.
     */
    private static final float TROFFER_BRIGHTNESS = 2.2f;
    /** Reach of a panel's light (blocks). The floor is 4 below the ceiling. */
    private static final float TROFFER_DISTANCE = 11.0f;
    /** The louvre's cut-off (radians from straight down): a parabolic (CAT2) troffer shields above ~60 degrees. */
    private static final float TROFFER_ANGLE = 1.15f;
    /** The lens is this much smaller than the panel on each side (the frame). */
    private static final float TROFFER_INSET = 0.12f;
    /** The light sits this far under the panel's face. */
    private static final float TROFFER_GAP = 0.02f;
    /**
     * A panel lying in the floor, lens up (the Inside-Out Room): its light shines up into the room.
     * Stronger and wider than a ceiling troffer, because the room around it is lined with dark
     * carpet and it is the only light there: dim and wrong, but never black.
     */
    private static final float UPWARD_BRIGHTNESS = 4.0f;
    private static final float UPWARD_DISTANCE = 10.0f;
    private static final float UPWARD_ANGLE = 1.5f;

    /** Lights fade out from this share of their range to the range itself. */
    private static final float FADE_FROM = 0.65f;
    /** Rebuild the window runs after walking this far, or when windows were loaded/unloaded. */
    private static final int REBUILD_MOVE = 6;
    /** Windows are grouped this far around you (a bit beyond the longest light range). */
    private static final int BUILD_RANGE = ClientSettings.Quality.HIGH.lightRange + MAX_SPAN + 8;

    private static final float TILT_SIN = (float) Math.sin(Math.toRadians(WINDOW_TILT_DEG));
    private static final float TILT_COS = (float) Math.cos(Math.toRadians(WINDOW_TILT_DEG));

    // Pane codes kept per window block (bits 2-3; bits 0-1 are its facing's 2D value).
    private static final int PANE_MID = 0;
    private static final int PANE_LEFT = 1;
    private static final int PANE_RIGHT = 2;
    private static final int PANE_SINGLE = 3;

    /** One run (or piece of a long run) of window glass with its light. */
    private static final class WindowRun {
        final long key;
        /** Middle of the glass face. */
        final double x;
        final double y;
        final double z;
        final Direction facing;
        final float halfAcross;
        final float halfUp;
        double distSq;
        @Nullable
        LightRenderHandle<AreaLightData> handle;
        float lastBrightness = -1.0f;

        WindowRun(long key, double x, double y, double z, Direction facing, float halfAcross, float halfUp) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
            this.halfAcross = halfAcross;
            this.halfUp = halfUp;
        }

        boolean samePlace(WindowRun o) {
            return o.x == x && o.y == y && o.z == z && o.facing == facing && o.halfAcross == halfAcross && o.halfUp == halfUp;
        }

        void free() {
            if (handle != null) {
                handle.free();
                handle = null;
            }
            lastBrightness = -1.0f;
        }
    }

    /** A slot for one troffer downlight (fixed pool, reused). */
    private static final class Troffer {
        long key = Long.MIN_VALUE;
        double x;
        double y;
        double z;
        boolean seen;
        /** A panel in the floor shining up (decided when the slot is taken). */
        boolean upward;
        @Nullable
        LightRenderHandle<AreaLightData> handle;
        float lastBrightness = -1.0f;

        void free() {
            if (handle != null) {
                handle.free();
                handle = null;
            }
            key = Long.MIN_VALUE;
            lastBrightness = -1.0f;
        }
    }

    /** The lower panes of every office window in the loaded chunks, per chunk. */
    private static final Long2ObjectOpenHashMap<long[]> BY_CHUNK = new Long2ObjectOpenHashMap<>();
    /** Lower pane -> facing (bits 0-1, 2D value) and pane (bits 2-3); -1 = not a window. */
    private static final Long2ByteOpenHashMap INFO = new Long2ByteOpenHashMap();
    private static final LongOpenHashSet VISITED = new LongOpenHashSet();
    private static final Long2ObjectOpenHashMap<WindowRun> OLD_RUNS = new Long2ObjectOpenHashMap<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Troffer[] TROFFERS;

    static {
        INFO.defaultReturnValue((byte) -1);
        int most = 0;
        for (ClientSettings.Quality q : ClientSettings.Quality.values()) {
            most = Math.max(most, q.maxL4Troffers);
        }
        TROFFERS = new Troffer[most];
        for (int i = 0; i < most; i++) {
            TROFFERS[i] = new Troffer();
        }
    }

    @Nullable
    private static ClientLevel boundLevel;
    /** The window runs around you; runs[0..runCount) sorted nearest first every tick. */
    private static WindowRun[] runs = new WindowRun[0];
    private static int runCount;
    private static int revision;
    private static int builtRevision = -1;
    private static int builtX;
    private static int builtZ;

    private static boolean activeIn(@Nullable BackroomsLevel floor) {
        return floor == BackroomsLevel.LEVEL_4;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != boundLevel) {
            bind(level);
        }
        BackroomsLevel floor = level == null ? null : BackroomsLevel.fromDimension(level.dimension());
        if (!activeIn(floor) || mc.player == null || !BackroomsLights.isVeilLightingActive()) {
            freeLights();
            return;
        }
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        BlockPos pos = mc.player.blockPosition();
        if (revision != builtRevision || Math.abs(pos.getX() - builtX) > REBUILD_MOVE || Math.abs(pos.getZ() - builtZ) > REBUILD_MOVE) {
            build(pos.getX(), pos.getZ());
            builtRevision = revision;
            builtX = pos.getX();
            builtZ = pos.getZ();
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float scale = CameraClientState.battery <= 0.0f ? 0.25f : 1.0f;
        ClientSettings.Quality quality = ClientSettings.quality();
        LightRenderer lights = renderer.getLightRenderer();
        syncWindows(lights, eye, quality, scale);
        syncTroffers(lights, quality, scale);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            if (chunk.getLevel() != boundLevel && chunk.getLevel() instanceof ClientLevel level) {
                bind(level);
            }
            scan(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            remove(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        bind(null);
    }

    private static void bind(@Nullable ClientLevel level) {
        boundLevel = level;
        freeLights();
        BY_CHUNK.clear();
        INFO.clear();
        runs = new WindowRun[0];
        runCount = 0;
        revision++;
        Minecraft mc = Minecraft.getInstance();
        if (level != null && mc.player != null && mc.player.level() == level) {
            int cx = SectionPos.blockToSectionCoord(mc.player.getBlockX());
            int cz = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
            int r = BUILD_RANGE / 16 + 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk != null) {
                        scan(chunk);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Index of the window panes (on chunk load/unload only)
    // ------------------------------------------------------------------------------------------

    private static boolean isWindow(BlockState state) {
        return state.is(ModBlocks.OFFICE_WINDOW.get());
    }

    private static void scan(LevelChunk chunk) {
        if (boundLevel == null || chunk.getLevel() != boundLevel || !activeIn(BackroomsLevel.fromDimension(boundLevel.dimension()))) {
            return;
        }
        LongArrayList found = null;
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(Level4Lights::isWindow)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        // Only the lower pane is kept: a window light covers both panes above it.
                        if (!isWindow(state) || state.getValue(OfficeWindowBlock.HALF) != DoubleBlockHalf.LOWER) {
                            continue;
                        }
                        if (found == null) {
                            found = new LongArrayList();
                        }
                        long pos = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                        found.add(pos);
                        INFO.put(pos, (byte) (state.getValue(OfficeWindowBlock.FACING).get2DDataValue() | paneOf(state) << 2));
                    }
                }
            }
        }
        long key = chunk.getPos().toLong();
        remove(key);
        if (found != null) {
            BY_CHUNK.put(key, found.toLongArray());
            revision++;
        }
    }

    /**
     * Which part of its bay a pane is (the {@code pane} property: single/left/mid/right). Read by
     * name, so windows from before the property existed simply count as "mid" (no bay edges known).
     */
    private static int paneOf(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("pane");
        if (property == null) {
            return PANE_MID;
        }
        Object value = state.getValue(property);
        String name = value instanceof StringRepresentable named ? named.getSerializedName() : value.toString();
        return switch (name) {
            case "left" -> PANE_LEFT;
            case "right" -> PANE_RIGHT;
            case "single" -> PANE_SINGLE;
            default -> PANE_MID;
        };
    }

    private static void remove(long chunkKey) {
        long[] old = BY_CHUNK.remove(chunkKey);
        if (old != null) {
            for (long pos : old) {
                INFO.remove(pos);
            }
            revision++;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Panes -> window runs (only after walking a few blocks or when windows load)
    // ------------------------------------------------------------------------------------------

    /** True when {@code pos} is a lower pane facing the same way as {@code info} and not yet in a run. */
    private static boolean continues(long pos, int info) {
        byte other = INFO.get(pos);
        return other != -1 && (other & 3) == (info & 3) && !VISITED.contains(pos);
    }

    private static void build(int centerX, int centerZ) {
        OLD_RUNS.clear();
        for (int i = 0; i < runCount; i++) {
            OLD_RUNS.put(runs[i].key, runs[i]);
        }
        List<WindowRun> out = new ArrayList<>();
        VISITED.clear();
        int minCx = SectionPos.blockToSectionCoord(centerX - BUILD_RANGE);
        int maxCx = SectionPos.blockToSectionCoord(centerX + BUILD_RANGE);
        int minCz = SectionPos.blockToSectionCoord(centerZ - BUILD_RANGE);
        int maxCz = SectionPos.blockToSectionCoord(centerZ + BUILD_RANGE);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long[] positions = BY_CHUNK.get(ChunkPos.asLong(cx, cz));
                if (positions == null) {
                    continue;
                }
                for (long start : positions) {
                    if (!VISITED.contains(start)) {
                        walkRun(start, out);
                    }
                }
            }
        }
        // Keep the Veil lights of runs that are still exactly where they were (no flicker on rebuild).
        WindowRun[] next = out.toArray(new WindowRun[0]);
        for (WindowRun run : next) {
            WindowRun old = OLD_RUNS.remove(run.key);
            if (old != null && old.samePlace(run)) {
                run.handle = old.handle;
                run.lastBrightness = old.lastBrightness;
                old.handle = null;
            }
        }
        for (WindowRun old : OLD_RUNS.values()) {
            old.free();
        }
        OLD_RUNS.clear();
        runs = next;
        runCount = next.length;
    }

    /**
     * One straight run of panes along a wall (same facing, same height), cut into pieces of at
     * most {@link #MAX_SPAN} blocks; where the panes know their bay, cuts fall on a bay's edge.
     */
    private static void walkRun(long seed, List<WindowRun> out) {
        int info = INFO.get(seed);
        Direction facing = Direction.from2DDataValue(info & 3);
        Direction along = facing.getClockWise();
        int ax = along.getStepX();
        int az = along.getStepZ();
        long start = seed;
        while (true) {
            long prev = BlockPos.offset(start, -ax, 0, -az);
            if (!continues(prev, info)) {
                break;
            }
            start = prev;
        }
        long first = start;
        long pos = start;
        int length = 0;
        while (true) {
            VISITED.add(pos);
            length++;
            int pane = (INFO.get(pos) >> 2) & 3;
            long next = BlockPos.offset(pos, ax, 0, az);
            boolean more = continues(next, info);
            boolean bayEnd = pane == PANE_RIGHT || pane == PANE_SINGLE;
            if (!more || length >= MAX_SPAN || (bayEnd && length + BAY > MAX_SPAN)) {
                out.add(makeRun(first, pos, facing, length));
                first = next;
                length = 0;
            }
            if (!more) {
                return;
            }
            pos = next;
        }
    }

    private static WindowRun makeRun(long first, long last, Direction facing, int length) {
        int x0 = Math.min(BlockPos.getX(first), BlockPos.getX(last));
        int x1 = Math.max(BlockPos.getX(first), BlockPos.getX(last));
        int z0 = Math.min(BlockPos.getZ(first), BlockPos.getZ(last));
        int z1 = Math.max(BlockPos.getZ(first), BlockPos.getZ(last));
        int y = BlockPos.getY(first);
        // Two stacked panes make one tall window (the usual case); a lone lower pane is half as tall.
        boolean tall = false;
        if (boundLevel != null) {
            BlockState above = boundLevel.getBlockState(CURSOR.set(x0, y + 1, z0));
            tall = isWindow(above) && above.getValue(OfficeWindowBlock.HALF) == DoubleBlockHalf.UPPER;
        }
        double cx = (x0 + x1 + 1) / 2.0;
        double cz = (z0 + z1 + 1) / 2.0;
        switch (facing) {
            case NORTH -> cz = z0;
            case SOUTH -> cz = z1 + 1;
            case WEST -> cx = x0;
            case EAST -> cx = x1 + 1;
            default -> { }
        }
        float half = tall ? 1.0f : 0.5f;
        return new WindowRun(BlockPos.asLong(x0, y, z0), cx, y + half, cz, facing,
                Math.max(length / 2.0f - WINDOW_INSET, 0.2f), half - WINDOW_INSET);
    }

    // ------------------------------------------------------------------------------------------
    // Veil lights (every tick, nothing allocated unless a light comes into range)
    // ------------------------------------------------------------------------------------------

    private static void syncWindows(LightRenderer lights, Vec3 eye, ClientSettings.Quality quality, float scale) {
        WindowRun[] list = runs;
        int n = runCount;
        for (int i = 0; i < n; i++) {
            WindowRun run = list[i];
            double dx = run.x - eye.x;
            double dy = run.y - eye.y;
            double dz = run.z - eye.z;
            run.distSq = dx * dx + dy * dy + dz * dz;
        }
        // Insertion sort, nearest first: the order barely changes from tick to tick, so this is
        // nearly linear (and unlike List.sort it allocates nothing).
        for (int i = 1; i < n; i++) {
            WindowRun run = list[i];
            int j = i - 1;
            while (j >= 0 && list[j].distSq > run.distSq) {
                list[j + 1] = list[j];
                j--;
            }
            list[j + 1] = run;
        }
        double range = quality.lightRange * WINDOW_RANGE_SCALE;
        double rangeSq = range * range;
        int budget = quality.maxL4Windows;
        float flash = 1.0f + WINDOW_FLASH_BOOST * Level4Atmosphere.flash(System.currentTimeMillis());
        int used = 0;
        for (int i = 0; i < n; i++) {
            WindowRun run = list[i];
            if (used >= budget || run.distSq > rangeSq) {
                run.free(); // nearest first: everything from here on is out
                continue;
            }
            used++;
            float brightness = WINDOW_BRIGHTNESS * scale * flash * fade(run.distSq, range);
            if (run.handle != null && !run.handle.isValid()) {
                run.handle = null; // Veil threw it away (resource reload): make it again
            }
            if (run.handle == null) {
                run.handle = lights.addLight(newWindow(run, brightness));
                run.lastBrightness = brightness;
            } else if (brightness != run.lastBrightness) {
                run.handle.getLightData().setBrightness(brightness);
                run.lastBrightness = brightness;
            }
        }
    }

    private static void syncTroffers(LightRenderer lights, ClientSettings.Quality quality, float scale) {
        List<FixtureLight> fixtures = BackroomsLights.fixtures();
        double range = quality.lightRange;
        double rangeSq = range * range;
        int budget = Math.min(quality.maxL4Troffers, TROFFERS.length);
        boolean blackedOut = FixturePower.mainsPower() <= 0.0f;
        for (Troffer slot : TROFFERS) {
            slot.seen = false;
        }
        // Pass 1: which slots still show one of the nearest lit panels.
        int picked = 0;
        for (int i = 0; i < fixtures.size() && picked < budget; i++) {
            FixtureLight light = fixtures.get(i);
            if (light.distSq > rangeSq) {
                break; // nearest first
            }
            if (!isTroffer(light, blackedOut)) {
                continue;
            }
            picked++;
            Troffer slot = slotOf(light.key);
            if (slot != null && slot.x == light.x && slot.y == light.y && slot.z == light.z) {
                slot.seen = true;
            }
        }
        for (Troffer slot : TROFFERS) {
            if (!slot.seen) {
                slot.free();
            }
        }
        // Pass 2: new panels take free slots; every shown panel follows its power and flicker.
        picked = 0;
        for (int i = 0; i < fixtures.size() && picked < budget; i++) {
            FixtureLight light = fixtures.get(i);
            if (light.distSq > rangeSq) {
                break;
            }
            if (!isTroffer(light, blackedOut)) {
                continue;
            }
            picked++;
            Troffer slot = slotOf(light.key);
            if (slot == null) {
                slot = slotOf(Long.MIN_VALUE);
                if (slot == null) {
                    continue;
                }
                slot.key = light.key;
                slot.x = light.x;
                slot.y = light.y;
                slot.z = light.z;
                slot.seen = true;
                slot.upward = lensUp(light);
            }
            float base = slot.upward ? UPWARD_BRIGHTNESS : TROFFER_BRIGHTNESS;
            float brightness = base * light.level * scale * fade(light.distSq, range);
            if (slot.handle != null && !slot.handle.isValid()) {
                slot.handle = null;
            }
            if (slot.handle == null) {
                slot.handle = lights.addLight(newTroffer(light, slot.upward, brightness));
                slot.lastBrightness = brightness;
            } else if (brightness != slot.lastBrightness) {
                slot.handle.getLightData().setBrightness(brightness);
                slot.lastBrightness = brightness;
            }
        }
    }

    /** A lit Level 4 panel worth a light (in a full blackout a dead one would only cost GPU time). */
    private static boolean isTroffer(FixtureLight light, boolean blackedOut) {
        return light.type == FixtureType.OFFICE_PANEL && !(blackedOut && light.level <= 0.0f);
    }

    @Nullable
    private static Troffer slotOf(long key) {
        for (Troffer slot : TROFFERS) {
            if (slot.key == key) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 1 inside {@link #FADE_FROM} of the range, easing to 0 at the range. Rounded to sixteenths so
     * a light that is fading doesn't push a new brightness to Veil every tick.
     */
    private static float fade(double distSq, double range) {
        double d = Math.sqrt(distSq);
        double from = range * FADE_FROM;
        if (d <= from) {
            return 1.0f;
        }
        double t = Math.max(0.0, (range - d) / (range - from));
        float smooth = (float) (t * t * (3.0 - 2.0 * t));
        return Math.round(smooth * 16.0f) / 16.0f;
    }

    /** The window's light: the size of its glass, just in front of it, tipped toward the floor. */
    private static AreaLightData newWindow(WindowRun run, float brightness) {
        AreaLightData light = new AreaLightData()
                .setSize(run.halfAcross, run.halfUp)
                .setAngle(WINDOW_ANGLE)
                .setDistance(WINDOW_DISTANCE)
                .setColor(WINDOW_R, WINDOW_G, WINDOW_B)
                .setBrightness(brightness)
                .setOcclusionEnabled(true);
        Direction out = run.facing;
        // Tipping the rectangle pushes its lower edge back toward the glass; moving it out by that
        // much keeps the whole rectangle in the room (behind the glass the shadow test would fail).
        double gap = FACE_GAP + run.halfUp * TILT_SIN;
        light.getPositionMutable().set(run.x + out.getStepX() * gap, run.y, run.z + out.getStepZ() * gap);
        // Area lights shine along their local +Z; lookAlong turns the given direction to -Z, so pass
        // the reverse of where it shines (out of the glass and a little down).
        float sx = out.getStepX() * TILT_COS;
        float sz = out.getStepZ() * TILT_COS;
        light.getOrientationMutable().identity().lookAlong(-sx, TILT_SIN, -sz, 0.0f, 1.0f, 0.0f);
        return light;
    }

    /**
     * True for a panel lying in the floor with its lens up (the Inside-Out Room): solid under it,
     * air over it. Ceiling troffers have air under them.
     */
    private static boolean lensUp(FixtureLight fixture) {
        if (boundLevel == null || fixture.blocks == null || fixture.blocks.length == 0) {
            return false;
        }
        long pos = fixture.blocks[0];
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        return !boundLevel.getBlockState(CURSOR.set(x, y - 1, z)).isAir()
                && boundLevel.getBlockState(CURSOR.set(x, y + 1, z)).isAir();
    }

    /** A lit panel's light: the size of its lens, just under it facing down (or on top facing up, see {@link #lensUp}). */
    private static AreaLightData newTroffer(FixtureLight fixture, boolean upward, float brightness) {
        long[] blocks = fixture.blocks;
        double cx = fixture.x;
        double cz = fixture.z;
        // The panel's bottom face (FixtureClusters puts the light one block under the panel's middle).
        double faceY = fixture.soundY - 0.5;
        float halfX = 0.5f;
        float halfZ = 0.5f;
        if (blocks != null && blocks.length > 0) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long pos : blocks) {
                minX = Math.min(minX, BlockPos.getX(pos));
                maxX = Math.max(maxX, BlockPos.getX(pos));
                minZ = Math.min(minZ, BlockPos.getZ(pos));
                maxZ = Math.max(maxZ, BlockPos.getZ(pos));
            }
            cx = (minX + maxX + 1) / 2.0;
            cz = (minZ + maxZ + 1) / 2.0;
            faceY = BlockPos.getY(blocks[0]);
            halfX = (maxX - minX + 1) / 2.0f;
            halfZ = (maxZ - minZ + 1) / 2.0f;
        }
        AreaLightData light = new AreaLightData()
                .setSize(halfX - TROFFER_INSET, halfZ - TROFFER_INSET)
                .setAngle(upward ? UPWARD_ANGLE : TROFFER_ANGLE)
                .setDistance(upward ? UPWARD_DISTANCE : TROFFER_DISTANCE)
                .setColor(TROFFER_R, TROFFER_G, TROFFER_B)
                .setBrightness(brightness)
                .setOcclusionEnabled(true);
        light.getPositionMutable().set(cx, upward ? faceY + 1.0 + TROFFER_GAP : faceY - TROFFER_GAP, cz);
        // Area lights shine along local +Z; lookAlong turns the given direction to -Z, so it gets
        // the reverse of where the light goes. The "up" hint lays the rectangle's second side
        // along Z (like SunBeams' skylights).
        light.getOrientationMutable().identity().lookAlong(0.0f, upward ? -1.0f : 1.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return light;
    }

    private static void freeLights() {
        for (int i = 0; i < runCount; i++) {
            runs[i].free();
        }
        for (Troffer slot : TROFFERS) {
            slot.free();
        }
    }

    private Level4Lights() {}
}
