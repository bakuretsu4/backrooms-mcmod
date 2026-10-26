package com.rickr.backrooms.client.water;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Flooding;
import com.rickr.backrooms.registry.ModBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Finds every water surface around the camera, for {@link WaterRenderer} to draw.
 *
 * <p>A surface is:
 * <ul>
 *   <li>the top of a water block with no water above it (and nothing solid on it), at the
 *       water's own height (8/9 of a block for still water);</li>
 *   <li>a {@code shallow_water} block, or a block standing in the shallows (Level 37's
 *       {@link Flooding#isFlooded flooded} pieces), at {@link #SHALLOW_SURFACE} above its bottom.
 *       A water block directly under such a cell gets no surface of its own: Level 37's deep
 *       pools run under the shallows, and one continuous surface covers both (no ledge).</li>
 * </ul>
 *
 * <p>Work is cached per chunk section (16x16x16): each section is scanned once, its surface
 * cells are merged into as few rectangles as possible ("greedy meshing": a whole flat pool
 * becomes one or two quads), and it is only scanned again when something in it changes
 * (chunk (re)loaded, a block changed, or the slow safety re-check). Scans run on the client
 * tick with a time budget, never while drawing a frame.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class WaterSurfaces {
    /** Height of the shallow-water surface above the bottom of its cell (contract with the level layouts). */
    public static final float SHALLOW_SURFACE = 0.375f;
    /** Water this far from the camera (blocks, horizontally) is scanned and drawn. */
    static final int RANGE = 64;
    /** Scanned sections farther than this are forgotten. */
    private static final int FORGET_RANGE = 96;
    /** Time the scan may take per client tick (1.5 ms). */
    private static final long SCAN_BUDGET_NS = 1_500_000L;
    /** Every few seconds the sections right around you are scanned again, just in case an update was missed. */
    private static final int RECHECK_TICKS = 100;
    private static final int DISCOVER_TICKS = 10;

    /** Quads are stored as 6 ints: x0, z0, x1, z1 (block corners), y (block), kind. */
    static final int INTS_PER_QUAD = 6;
    /** Kind of a shallow-water surface; kinds 1..8 are vanilla water amounts (height = amount / 9). */
    private static final int SHALLOW_KIND = 10;
    private static final int[] NO_QUADS = new int[0];

    /** Section key ({@link SectionPos#asLong}) -> its quads (NO_QUADS = scanned, no water). */
    private static final Long2ObjectOpenHashMap<int[]> SECTIONS = new Long2ObjectOpenHashMap<>();
    /** Sections waiting for a (re)scan, oldest first. */
    private static final LongLinkedOpenHashSet DIRTY = new LongLinkedOpenHashSet();

    // Scratch space for scanning, reused for every section (no garbage per scan).
    private static final int[] KINDS = new int[16 * 16];
    private static final IntArrayList QUADS = new IntArrayList();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    @Nullable
    private static ClientLevel level;
    @Nullable
    private static Block shallowBlock;
    /** Bumped whenever any section's quads change: the renderer rebuilds its mesh when it sees a new value. */
    private static int revision;
    private static int ticks;
    private static long lastCameraSection = Long.MIN_VALUE;

    /** Called once per client tick by {@link WaterRenderer}; {@code active} = we draw water in this level. */
    static void tick(Minecraft mc, boolean active) {
        ClientLevel current = active ? mc.level : null;
        if (current != level) {
            reset(current);
        }
        if (current == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        int camX = Mth.floor(camera.x);
        int camZ = Mth.floor(camera.z);
        long cameraSection = ChunkPos.asLong(camX >> 4, camZ >> 4);
        ticks++;
        if (cameraSection != lastCameraSection || ticks % DISCOVER_TICKS == 0) {
            lastCameraSection = cameraSection;
            discover(current, camX, camZ);
        }
        if (ticks % RECHECK_TICKS == 0) {
            recheckAround(current, camX >> 4, camZ >> 4);
        }
        scanDirty(current);
    }

    /** Changes every time the set of water quads changes. */
    static int revision() {
        return revision;
    }

    /** All scanned sections' quads (see {@link #INTS_PER_QUAD}); only read on the render thread. */
    static Collection<int[]> sections() {
        return SECTIONS.values();
    }

    /** Height of a surface of this kind above the bottom of its block. */
    static float height(int kind) {
        return kind == SHALLOW_KIND ? SHALLOW_SURFACE : kind / 9.0f;
    }

    /** From the {@code LevelRenderer.blockChanged} mixin: rescan the section(s) a block change can affect. */
    public static void onBlockChanged(BlockGetter getter, BlockPos pos, BlockState oldState, BlockState newState) {
        if (level == null) {
            return; // not in a pool level: nothing to track (this runs for every block change everywhere)
        }
        boolean relevant = isWatery(oldState) || isWatery(newState)
                || isWatery(getter.getBlockState(CURSOR.setWithOffset(pos, 0, -1, 0)));
        if (!relevant) {
            return;
        }
        int sx = pos.getX() >> 4;
        int sy = pos.getY() >> 4;
        int sz = pos.getZ() >> 4;
        DIRTY.add(SectionPos.asLong(sx, sy, sz));
        if ((pos.getY() & 15) == 0) {
            DIRTY.add(SectionPos.asLong(sx, sy - 1, sz)); // the water just below may have been covered/uncovered
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (level == null || event.getLevel() != level || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            long key = SectionPos.asLong(pos.x, chunk.getSectionYFromSectionIndex(i), pos.z);
            DIRTY.add(key); // (re)loaded: scan again, even if we knew it before
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (level == null || event.getLevel() != level) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        for (int sy = level.getMinSection(); sy < level.getMaxSection(); sy++) {
            long key = SectionPos.asLong(pos.x, sy, pos.z);
            DIRTY.remove(key);
            int[] old = SECTIONS.remove(key);
            if (old != null && old.length > 0) {
                revision++;
            }
        }
    }

    private static void reset(@Nullable ClientLevel newLevel) {
        level = newLevel;
        SECTIONS.clear();
        DIRTY.clear();
        lastCameraSection = Long.MIN_VALUE;
        revision++;
        shallowBlock = newLevel == null ? null : ModBlocks.SHALLOW_WATER.get();
    }

    /** Queues every not-yet-scanned section in range; forgets sections that are now far away. */
    private static void discover(ClientLevel current, int camX, int camZ) {
        int chunkRange = (RANGE >> 4) + 1;
        int camCX = camX >> 4;
        int camCZ = camZ >> 4;
        for (int cx = camCX - chunkRange; cx <= camCX + chunkRange; cx++) {
            for (int cz = camCZ - chunkRange; cz <= camCZ + chunkRange; cz++) {
                if (current.getChunkSource().getChunk(cx, cz, false) == null) {
                    continue;
                }
                for (int sy = current.getMinSection(); sy < current.getMaxSection(); sy++) {
                    long key = SectionPos.asLong(cx, sy, cz);
                    if (!SECTIONS.containsKey(key)) {
                        DIRTY.add(key);
                    }
                }
            }
        }
        ObjectIterator<Long2ObjectMap.Entry<int[]>> it = SECTIONS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<int[]> entry = it.next();
            long key = entry.getLongKey();
            int dx = Math.abs((SectionPos.x(key) << 4) + 8 - camX);
            int dz = Math.abs((SectionPos.z(key) << 4) + 8 - camZ);
            if (dx > FORGET_RANGE || dz > FORGET_RANGE) {
                if (entry.getValue().length > 0) {
                    revision++;
                }
                it.remove();
            }
        }
    }

    /** Safety net: rescan the 3x3 chunks around the camera now and then. */
    private static void recheckAround(ClientLevel current, int camCX, int camCZ) {
        for (int cx = camCX - 1; cx <= camCX + 1; cx++) {
            for (int cz = camCZ - 1; cz <= camCZ + 1; cz++) {
                for (int sy = current.getMinSection(); sy < current.getMaxSection(); sy++) {
                    long key = SectionPos.asLong(cx, sy, cz);
                    int[] quads = SECTIONS.get(key);
                    if (quads != null && quads.length > 0) {
                        DIRTY.add(key); // only sections that have water are worth checking
                    }
                }
            }
        }
    }

    private static void scanDirty(ClientLevel current) {
        if (DIRTY.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + SCAN_BUDGET_NS;
        LongIterator it = DIRTY.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            it.remove();
            scanSection(current, key);
            if (System.nanoTime() > deadline) {
                break; // the rest waits for the next tick
            }
        }
    }

    private static void scanSection(ClientLevel current, long key) {
        int cx = SectionPos.x(key);
        int sy = SectionPos.y(key);
        int cz = SectionPos.z(key);
        LevelChunk chunk = current.getChunkSource().getChunk(cx, cz, false);
        int index = current.getSectionIndexFromSectionY(sy);
        if (chunk == null || index < 0 || index >= chunk.getSectionsCount()) {
            int[] old = SECTIONS.remove(key);
            if (old != null && old.length > 0) {
                revision++;
            }
            return;
        }
        LevelChunkSection section = chunk.getSection(index);
        int[] quads = section.hasOnlyAir() || !section.maybeHas(WaterSurfaces::isWatery)
                ? NO_QUADS
                : mesh(current, chunk, index, cx, sy, cz);
        int[] old = SECTIONS.put(key, quads);
        if (old == null ? quads.length > 0 : !Arrays.equals(old, quads)) {
            revision++;
        }
    }

    /** Surface cells of one section, merged into rectangles. */
    private static int[] mesh(ClientLevel current, LevelChunk chunk, int index, int cx, int sy, int cz) {
        LevelChunkSection section = chunk.getSection(index);
        LevelChunkSection sectionAbove = index + 1 < chunk.getSectionsCount() ? chunk.getSection(index + 1) : null;
        int baseX = cx << 4;
        int baseY = sy << 4;
        int baseZ = cz << 4;
        QUADS.clear();
        for (int y = 0; y < 16; y++) {
            boolean any = false;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int kind = 0;
                    if (isWatery(state)) {
                        BlockState above = y < 15 ? section.getBlockState(x, y + 1, z)
                                : sectionAbove != null ? sectionAbove.getBlockState(x, 0, z) : Blocks.AIR.defaultBlockState();
                        kind = surfaceKind(current, state, above, baseX + x, baseY + y, baseZ + z);
                    }
                    KINDS[(z << 4) | x] = kind;
                    any |= kind != 0;
                }
            }
            if (any) {
                mergeLayer(baseX, baseY + y, baseZ);
            }
        }
        return QUADS.toIntArray();
    }

    /** 0 = no surface on top of this cell; else the surface kind (see {@link #height}). */
    private static int surfaceKind(ClientLevel current, BlockState state, BlockState above, int x, int y, int z) {
        if (isShallow(state)) {
            return isShallow(above) ? 0 : SHALLOW_KIND;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.getType().isSame(Fluids.WATER)) {
            return 0;
        }
        if (above.getFluidState().getType().isSame(Fluids.WATER) || isShallow(above)) {
            return 0; // more water (or the shallows' surface) above: this is not the top
        }
        if (above.isSolidRender(current, CURSOR.set(x, y + 1, z))) {
            return 0; // capped by a solid block: nobody can see this surface
        }
        return Mth.clamp(fluid.getAmount(), 1, 8);
    }

    /**
     * Greedy meshing of one 16x16 layer of {@link #KINDS}: grow a run along x, then grow it along
     * z while whole rows match, emit the rectangle and clear its cells; repeat.
     */
    private static void mergeLayer(int baseX, int worldY, int baseZ) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int kind = KINDS[(z << 4) | x];
                if (kind == 0) {
                    continue;
                }
                int w = 1;
                while (x + w < 16 && KINDS[(z << 4) | (x + w)] == kind) {
                    w++;
                }
                int d = 1;
                grow:
                while (z + d < 16) {
                    for (int i = 0; i < w; i++) {
                        if (KINDS[((z + d) << 4) | (x + i)] != kind) {
                            break grow;
                        }
                    }
                    d++;
                }
                for (int dz = 0; dz < d; dz++) {
                    for (int dx = 0; dx < w; dx++) {
                        KINDS[((z + dz) << 4) | (x + dx)] = 0;
                    }
                }
                QUADS.add(baseX + x);
                QUADS.add(baseZ + z);
                QUADS.add(baseX + x + w);
                QUADS.add(baseZ + z + d);
                QUADS.add(worldY);
                QUADS.add(kind);
            }
        }
    }

    /** Ankle-deep water: the shallow-water marker block, or a block standing in the shallows. */
    private static boolean isShallow(BlockState state) {
        return (shallowBlock != null && state.is(shallowBlock)) || Flooding.isFlooded(state);
    }

    /** Anything that can carry a water surface. */
    static boolean isWatery(BlockState state) {
        return state.getFluidState().getType().isSame(Fluids.WATER) || isShallow(state);
    }

    private WaterSurfaces() {}
}
