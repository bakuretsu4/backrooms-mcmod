package com.rickr.backrooms.world.layout;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One Level 39 house, finished and packed small for world generation: its blocks as a byte per
 * cell (an index into the house's own palette of block states), only for the heights the house
 * actually uses. {@link #fillColumn} copies one column out of it without allocating anything.
 *
 * <p>Measured over a whole town (486 houses): about 2.9 KB of cells per house on average and 8 KB
 * for the biggest (merged and turreted ones), plus a palette of a few dozen states. Plans are
 * cached (shared by all world-generation threads) and rebuilt if the cache was cleared.
 */
final class Level39HousesPlan {
    final long seed;
    final long hash;
    final int x0;
    final int z0;
    final int w;
    final int d;
    final int ox;
    final int oz;
    final int rx;
    final int rz;
    final int bx;
    final int bz;
    /** Lowest y stored, and how many heights. */
    final int y0;
    final int height;
    /** Palette index per cell, ((u * d) + v) * height + (y - y0); 0 = leave the town's block. */
    final byte[] vox;
    final BlockState[] palette;

    // ---- what the anomaly code and the bigger interior need to know ---------------------------
    /** Interior doors, four ints each: x, y of the lower half, z, 2D facing. */
    final int[] doors;
    /** Front door column (local u), front wall (local v), ground floor y, storey pitch. */
    final int doorU;
    final int frontV;
    final int floorY;
    final int pitch;
    /** Wallpaper of the ground floor and the main floor kind (for the bigger interior). */
    final int paper;
    final int floorKind;
    final boolean old;
    /** Number of knockable wall sections. */
    final int knockWalls;

    Level39HousesPlan(long seed, Level39HouseSpec h, Level39HousesBuilder b) {
        this.seed = seed;
        this.hash = h.hash();
        this.x0 = h.x0();
        this.z0 = h.z0();
        this.w = b.W;
        this.d = b.D;
        this.ox = b.ox;
        this.oz = b.oz;
        this.rx = b.R.getStepX();
        this.rz = b.R.getStepZ();
        this.bx = b.B.getStepX();
        this.bz = b.B.getStepZ();
        int ch = Level39HousesBuilder.CH;
        // Only keep the heights something was drawn at.
        int lo = ch;
        int hi = -1;
        for (int i = 0; i < b.c.length; i++) {
            if (b.c[i] != Level39HousesBlocks.EMPTY) {
                int y = i % ch;
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        }
        if (hi < lo) {
            lo = 0;
            hi = 0;
        }
        this.y0 = Level39HousesBuilder.Y0 + lo;
        this.height = hi - lo + 1;
        this.vox = new byte[w * d * height];
        Map<Integer, Integer> index = new java.util.HashMap<>();
        Map<BlockState, Integer> byState = new IdentityHashMap<>();
        BlockState[] pal = new BlockState[256];
        int size = 1;
        int doorCount = 0;
        int[] doorTmp = new int[64 * 4];
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                for (int y = lo; y <= hi; y++) {
                    int code = b.c[((u * d) + v) * ch + y];
                    if (code == Level39HousesBlocks.EMPTY) continue;
                    Integer p = index.get(code);
                    if (p == null) {
                        BlockState state = Level39HousesBlocks.resolve(code);
                        p = byState.get(state);
                        if (p == null) {
                            if (size >= 255) {
                                p = 0; // palette full (never seen): leave the cell to the town
                            } else {
                                p = size;
                                pal[size++] = state;
                                byState.put(state, p);
                            }
                        }
                        index.put(code, p);
                    }
                    vox[((u * d) + v) * height + (y - lo)] = (byte) (int) p;
                    if (Level39HousesBlocks.kind(code) == Level39HousesBlocks.INT_DOOR
                            && (code & Level39HousesBlocks.SECOND) == 0 && doorCount + 4 <= doorTmp.length) {
                        doorTmp[doorCount++] = b.worldX(u, v);
                        doorTmp[doorCount++] = Level39HousesBuilder.Y0 + y;
                        doorTmp[doorCount++] = b.worldZ(u, v);
                        doorTmp[doorCount++] = Level39HousesBlocks.facing(code).get2DDataValue();
                    }
                }
            }
        }
        this.palette = Arrays.copyOf(pal, size);
        this.doors = Arrays.copyOf(doorTmp, doorCount);
        this.doorU = b.du;
        this.frontV = b.fv;
        this.floorY = b.F0;
        this.pitch = b.P;
        this.paper = b.paperGround;
        this.floorKind = b.floorMain;
        this.old = b.old;
        this.knockWalls = b.knockWalls.size();
    }

    int u(int x, int z) {
        return (x - ox) * rx + (z - oz) * rz;
    }

    int v(int x, int z) {
        return (x - ox) * bx + (z - oz) * bz;
    }

    int worldX(int u, int v) {
        return ox + rx * u + bx * v;
    }

    int worldZ(int u, int v) {
        return oz + rz * u + bz * v;
    }

    /** Writes the house's blocks of column (x, z); allocation-free. */
    void fillColumn(int x, int z, LevelLayout.Column out) {
        int u = u(x, z);
        int v = v(x, z);
        if (u < 0 || u >= w || v < 0 || v >= d) return;
        int base = ((u * d) + v) * height;
        for (int i = 0; i < height; i++) {
            int p = vox[base + i] & 0xFF;
            if (p != 0) out.set(y0 + i, palette[p]);
        }
    }

    /** Bytes this plan keeps (for the report and the debug command). */
    int memoryBytes() {
        return vox.length + palette.length * 8 + doors.length * 4 + 96;
    }

    Direction inward() {
        return Direction.fromDelta(bx, 0, bz);
    }

    // ---- cache ---------------------------------------------------------------------------------

    private static final ConcurrentHashMap<Long, Level39HousesPlan> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Level39HousesPlan> LAST = new ThreadLocal<>();
    /** About 400 houses: a few MB, more than the houses around every loaded player. */
    private static final int MAX_CACHED = 400;

    /** The plan of house h (built the first time it is asked for). */
    static Level39HousesPlan of(long seed, Level39HouseSpec h) {
        Level39HousesPlan p = LAST.get();
        if (p != null && p.seed == seed && p.hash == h.hash() && p.x0 == h.x0() && p.z0 == h.z0()) {
            return p;
        }
        long key = h.hash() ^ (seed * 0x9E3779B97F4A7C15L) ^ ((long) h.x0() << 32) ^ h.z0();
        p = CACHE.get(key);
        if (p == null || p.seed != seed || p.hash != h.hash() || p.x0 != h.x0() || p.z0 != h.z0()) {
            if (CACHE.size() > MAX_CACHED) {
                CACHE.clear();
            }
            // Built inside computeIfAbsent so two threads never build the same house twice.
            p = CACHE.computeIfAbsent(key, k -> Level39HousesBuilder.build(seed, h));
            if (p.seed != seed || p.hash != h.hash() || p.x0 != h.x0() || p.z0 != h.z0()) {
                // Another house had the same key (vanishingly rare): build this one without caching.
                p = Level39HousesBuilder.build(seed, h);
            }
        }
        LAST.set(p);
        return p;
    }
}
