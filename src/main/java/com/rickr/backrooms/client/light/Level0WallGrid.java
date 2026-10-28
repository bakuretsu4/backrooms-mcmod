package com.rickr.backrooms.client.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.registry.Level2Blocks;
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
 *
 * <p>Level 2 uses the same map for its lamps' shadows ({@link #tick} with {@code level2}): there it
 * starts 4 blocks lower ({@value #LEVEL2_BASE_Y}, so the trench under the floor grating is in it)
 * and it also keeps a second texture with the GLOSS class of every block (client/light/Level2Gloss,
 * two bits per block: one bit plane in each of its red and green channels), which the Level 2
 * lamp shader reads for the wet and metal highlights. Level 0 never builds the gloss texture.
 */
public final class Level0WallGrid {
    static final int SIZE = 128;
    private static final int CHUNKS = SIZE / 16;
    static final int BASE_Y = LevelLayout.FLOOR_Y;
    /** Level 2: the trench floor (floor - 3) is the lowest thing a corridor lamp lights. */
    static final int LEVEL2_BASE_Y = LevelLayout.FLOOR_Y - 4;
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
    /** Level 2 only: the gloss classes, low bit and high bit planes (see Level2Gloss). */
    private static final int[] GLOSS_LO = new int[SIZE * SIZE];
    private static final int[] GLOSS_HI = new int[SIZE * SIZE];
    private static final int[] PATCH_LO = new int[256];
    private static final int[] PATCH_HI = new int[256];
    /** Block state -> 1 solid, 2 open (0 = not looked at yet). */
    private static final Reference2ByteOpenHashMap<BlockState> SOLID = new Reference2ByteOpenHashMap<>();

    private static int texture;
    private static int glossTexture;
    private static IntBuffer upload;
    private static IntBuffer glossUpload;
    private static int originX;
    private static int originZ;
    private static int refresh;
    private static boolean ready;
    /** Which level the map is built for: false = Level 0 (base y40, no gloss), true = Level 2. */
    private static boolean level2;
    private static int baseY = BASE_Y;

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

    /** The GL texture (R32UI), 0 before the first tick on Level 0 or Level 2. */
    static int texture() {
        return texture;
    }

    /** Level 2's gloss texture (RG32UI), 0 until the map has been built for Level 2 once. */
    static int glossTexture() {
        return glossTexture;
    }

    /** World y of bit 0 (the lowest block in the map). */
    static int baseY() {
        return baseY;
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

    /**
     * Keeps the map centred on the camera. Client tick, Level 0 or Level 2 only (render thread).
     * {@code forLevel2} switches the map to Level 2's layout (lower base, gloss texture).
     */
    static void tick(ClientLevel level, double camX, double camZ, boolean forLevel2) {
        RenderSystem.assertOnRenderThread();
        if (texture == 0) {
            create();
        }
        if (forLevel2 != level2) {
            // Another level's map is in the window: start over with the new layout.
            level2 = forLevel2;
            baseY = forLevel2 ? LEVEL2_BASE_Y : BASE_Y;
            reset();
        }
        if (level2 && glossTexture == 0) {
            createGloss();
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
        boolean gloss = level2;
        java.util.Arrays.fill(PATCH, 0);
        if (gloss) {
            java.util.Arrays.fill(PATCH_LO, 0);
            java.util.Arrays.fill(PATCH_HI, 0);
        }
        if (loaded) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int bit = 0; bit < LEVELS; bit++) {
                int y = baseY + bit;
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
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (solid(state) && !(gloss && lightPasses(state))) {
                            PATCH[lz * 16 + lx] |= mask;
                        }
                        if (gloss) {
                            byte g = Level2Gloss.classOf(state);
                            if ((g & 1) != 0) {
                                PATCH_LO[lz * 16 + lx] |= mask;
                            }
                            if ((g & 2) != 0) {
                                PATCH_HI[lz * 16 + lx] |= mask;
                            }
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
                int at = (tz + lz) * SIZE + tx + lx;
                int in = lz * 16 + lx;
                if (DATA[at] != PATCH[in] || (gloss && (GLOSS_LO[at] != PATCH_LO[in] || GLOSS_HI[at] != PATCH_HI[in]))) {
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
        if (gloss) {
            // Red = low bit, green = high bit of each block's class, interleaved per texel.
            glossUpload.clear();
            for (int lz = 0; lz < 16; lz++) {
                System.arraycopy(PATCH_LO, lz * 16, GLOSS_LO, (tz + lz) * SIZE + tx, 16);
                System.arraycopy(PATCH_HI, lz * 16, GLOSS_HI, (tz + lz) * SIZE + tx, 16);
            }
            for (int i = 0; i < 256; i++) {
                glossUpload.put(PATCH_LO[i]).put(PATCH_HI[i]);
            }
            glossUpload.flip();
            GlStateManager._bindTexture(glossTexture);
            unpackDefaults();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, tx, tz, 16, 16, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, glossUpload);
        }
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

    /**
     * Level 2 only: the walkable bar grating over the trench has a full collision box but lets
     * light through: the trench lamps shine up through the bars and the corridor lamps down into
     * the trench.
     */
    private static boolean lightPasses(BlockState state) {
        return state.is(Level2Blocks.GRATING.get());
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

    /** Level 2's gloss texture: same window and wrapping as the wall map, two bit planes. */
    private static void createGloss() {
        glossTexture = GlStateManager._genTexture();
        glossUpload = MemoryUtil.memAllocInt(512);
        GlStateManager._bindTexture(glossTexture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        unpackDefaults();
        IntBuffer zeros = MemoryUtil.memCallocInt(SIZE * SIZE * 2);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG32UI, SIZE, SIZE, 0, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, zeros);
        MemoryUtil.memFree(zeros);
        java.util.Arrays.fill(GLOSS_LO, 0);
        java.util.Arrays.fill(GLOSS_HI, 0);
    }

    /** Minecraft's texture uploads leave row-length/skip settings behind; reset them for ours. */
    static void unpackDefaults() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
}
