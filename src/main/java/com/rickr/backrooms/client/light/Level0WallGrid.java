package com.rickr.backrooms.client.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.world.layout.LevelLayout;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import java.nio.IntBuffer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * Level 0's wall map for the light shaders: which blocks around you are solid, seen from above.
 *
 * <p>Every Level 0 wall is a whole column of blocks, so a lamp's shadow only needs a map seen
 * from above: one texel per block column for the {@value #SIZE} x {@value #SIZE} columns around
 * the camera, each holding 32 bits, one per block from y{@value #BASE_Y} up to y71 (the carpet,
 * the three-high room, the ceiling and the tall atrium and shafts above it). The shader
 * (pinwheel/shaders/include/level0_grid.glsl) walks the columns between a pixel and a lamp and
 * only tests the blocks at the heights the line passes, so half walls, window holes and the
 * atrium's balcony throw the right shadows too.
 *
 * <p>The map wraps around: column (x, z) lives in texel (x & 127, z & 127). Walking only means the
 * chunks that come into the window get read (up to {@value #BUILDS_PER_TICK} per tick, nearest
 * first), and one chunk per tick is re-read round the window so changed blocks (a hidden door,
 * the Pillar Hall's shifts) show up within a few seconds. Only chunks whose bits really changed
 * are sent to the graphics card (a 16 x 16 patch, 1 KB).
 *
 * <p>Solid = a full opaque block, or any block you can't walk into at all (a full collision box:
 * the wall blocks with drawings or holes on them, which are see-through to the renderer, still
 * stop light). Thin things (signs, lamps, tape, half-wall caps, furniture) let light through.
 */
public final class Level0WallGrid {
    static final int SIZE = 128;
    private static final int CHUNKS = SIZE / 16;
    static final int BASE_Y = LevelLayout.FLOOR_Y;
    private static final int LEVELS = 32;
    /** New chunks read per tick: a whole row of the window when you cross a chunk border. */
    private static final int BUILDS_PER_TICK = CHUNKS;
    private static final long NONE = Long.MIN_VALUE;

    private static final int[] DATA = new int[SIZE * SIZE];
    /** Which chunk each of the 8 x 8 window slots holds (ChunkPos.asLong), NONE = not read yet. */
    private static final long[] BUILT = new long[CHUNKS * CHUNKS];
    /** False while a slot's chunk wasn't loaded when it was read (read again later). */
    private static final boolean[] COMPLETE = new boolean[CHUNKS * CHUNKS];
    /** Slots in nearest-first order around the middle of the window. */
    private static final int[] ORDER = new int[CHUNKS * CHUNKS];
    private static final int[] PATCH = new int[256];
    /** Block state -> 1 solid, 2 open (0 = not looked at yet). */
    private static final Reference2ByteOpenHashMap<BlockState> SOLID = new Reference2ByteOpenHashMap<>();

    private static int texture;
    private static IntBuffer upload;
    private static int originX;
    private static int originZ;
    private static int refresh;
    private static boolean ready;

    static {
        java.util.Arrays.fill(BUILT, NONE);
        Integer[] order = new Integer[CHUNKS * CHUNKS];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        double mid = CHUNKS / 2.0 - 0.5;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(
                Math.max(Math.abs(a % CHUNKS - mid), Math.abs(a / CHUNKS - mid)),
                Math.max(Math.abs(b % CHUNKS - mid), Math.abs(b / CHUNKS - mid))));
        for (int i = 0; i < order.length; i++) {
            ORDER[i] = order[i];
        }
    }

    private Level0WallGrid() {}

    /** The GL texture (R32UI), 0 before the first tick on Level 0. */
    static int texture() {
        return texture;
    }

    /** World x of the window's corner. */
    static int originX() {
        return originX;
    }

    /** World z of the window's corner. */
    static int originZ() {
        return originZ;
    }

    /** True once the chunks around you have been read at least once. */
    static boolean ready() {
        return ready;
    }

    /** Keeps the map centred on the camera. Client tick, Level 0 only (render thread). */
    static void tick(ClientLevel level, double camX, double camZ) {
        RenderSystem.assertOnRenderThread();
        if (texture == 0) {
            create();
        }
        int camChunkX = Math.floorDiv((int) Math.floor(camX), 16);
        int camChunkZ = Math.floorDiv((int) Math.floor(camZ), 16);
        int minChunkX = camChunkX - CHUNKS / 2;
        int minChunkZ = camChunkZ - CHUNKS / 2;
        originX = minChunkX * 16;
        originZ = minChunkZ * 16;
        int builds = 0;
        boolean all = true;
        for (int index : ORDER) {
            int cx = minChunkX + index % CHUNKS;
            int cz = minChunkZ + index / CHUNKS;
            int slot = Math.floorMod(cx, CHUNKS) + Math.floorMod(cz, CHUNKS) * CHUNKS;
            long want = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
            if (BUILT[slot] == want && COMPLETE[slot]) {
                continue;
            }
            all = false;
            if (builds++ < BUILDS_PER_TICK) {
                read(level, cx, cz, slot, want);
            }
        }
        ready |= all;
        // Re-read one chunk per tick, round the window, to catch changed blocks.
        refresh = (refresh + 1) % ORDER.length;
        int index = ORDER[refresh];
        int cx = minChunkX + index % CHUNKS;
        int cz = minChunkZ + index / CHUNKS;
        read(level, cx, cz, Math.floorMod(cx, CHUNKS) + Math.floorMod(cz, CHUNKS) * CHUNKS, net.minecraft.world.level.ChunkPos.asLong(cx, cz));
    }

    /** New level: forget everything (the texture is kept and overwritten). */
    static void reset() {
        java.util.Arrays.fill(BUILT, NONE);
        java.util.Arrays.fill(COMPLETE, false);
        ready = false;
    }

    private static void read(ClientLevel level, int cx, int cz, int slot, long want) {
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        boolean loaded = chunk != null && !(chunk instanceof EmptyLevelChunk);
        java.util.Arrays.fill(PATCH, 0);
        if (loaded) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int bit = 0; bit < LEVELS; bit++) {
                int y = BASE_Y + bit;
                int sectionIndex = chunk.getSectionIndex(y);
                if (sectionIndex < 0 || sectionIndex >= sections.length) {
                    continue;
                }
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int ly = y & 15;
                int mask = 1 << bit;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (solid(section.getBlockState(lx, ly, lz))) {
                            PATCH[lz * 16 + lx] |= mask;
                        }
                    }
                }
            }
        }
        boolean changed = BUILT[slot] != want;
        int tx = Math.floorMod(cx, CHUNKS) * 16;
        int tz = Math.floorMod(cz, CHUNKS) * 16;
        for (int lz = 0; lz < 16 && !changed; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                if (DATA[(tz + lz) * SIZE + tx + lx] != PATCH[lz * 16 + lx]) {
                    changed = true;
                    break;
                }
            }
        }
        BUILT[slot] = want;
        COMPLETE[slot] = loaded;
        if (!changed) {
            return;
        }
        upload.clear();
        for (int lz = 0; lz < 16; lz++) {
            System.arraycopy(PATCH, lz * 16, DATA, (tz + lz) * SIZE + tx, 16);
        }
        upload.put(PATCH).flip();
        GlStateManager._bindTexture(texture);
        unpackDefaults();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, tx, tz, 16, 16, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, upload);
    }

    private static boolean solid(BlockState state) {
        byte known = SOLID.getByte(state);
        if (known == 0) {
            boolean solid = !state.isAir() && (state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    || state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            known = solid ? (byte) 1 : (byte) 2;
            SOLID.put(state, known);
        }
        return known == 1;
    }

    private static void create() {
        texture = GlStateManager._genTexture();
        upload = MemoryUtil.memAllocInt(256);
        GlStateManager._bindTexture(texture);
        // Integer textures must be read exactly (no filtering, no mipmaps).
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        unpackDefaults();
        IntBuffer zeros = MemoryUtil.memCallocInt(SIZE * SIZE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32UI, SIZE, SIZE, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, zeros);
        MemoryUtil.memFree(zeros);
        java.util.Arrays.fill(DATA, 0);
    }

    /** Minecraft's texture uploads leave row-length/skip settings behind; reset them for ours. */
    static void unpackDefaults() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
}
