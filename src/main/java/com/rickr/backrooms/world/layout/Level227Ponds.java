package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.layout.Level227Tile.Cave;
import com.rickr.backrooms.world.layout.Level227Tree.Beam;
import com.rickr.backrooms.world.layout.Level227Tree.Span;
import java.util.Arrays;

/**
 * The forest pools of Level 227: which puddle dips of the forest floor ({@link Level227Tree#puddleStrength})
 * hold water, at what height, and how deep.
 *
 * <p>The water lies where the old one-block puddles were: its surface is the top block of the
 * forest floor in the dip, at the dip's lowest level round about ({@link #LEVEL_REACH} blocks), so
 * a whole stretch of dip shares one flat surface (where the floor is a block higher, that block is
 * dug out). Below the surface the pool is a bowl. Each rule below is there so the water stays still
 * (vanilla water flows the moment it touches air) and is safe to swim in:
 * <ul>
 *   <li><b>One flat surface, sealed sides.</b> Two water columns side by side always share their
 *       surface (a column whose neighbour could hold water at another height stays dry). Every
 *       dry column next to the water has its floor at the surface or one block above it, solid
 *       all the way down: no water ever has air beside it. That shore is then dug level with the
 *       water ({@link #shore}) and kept clear, so you swim to the edge and step straight out.</li>
 *   <li><b>A bowl you can walk out of.</b> The depth wanted in a column ({@link #BOWL}) grows with
 *       how far inside the dip it is, but the real depth is limited to 1 + the distance (in
 *       blocks, along x and z) to the nearest column that is dry or shallower. So the rim is 1
 *       deep and the bottom never drops more than 1 per block: a staircase out from anywhere.
 *       Small pools stay 1-2 deep because nothing in them is far from the rim.</li>
 *   <li><b>Never through a branch.</b> At least {@link #FLOOR_UNDER} solid blocks stay under the
 *       deepest water (the branch's underside is {@link Span#bot}); too thin, and the depth is capped.</li>
 *   <li><b>Open above.</b> No other piece of the tree comes within {@link #AIR_ABOVE} + 1 blocks
 *       of the forest floor over the water, so there is always room to surface (Level227Layout
 *       keeps the forest's own things off and away from the water).</li>
 *   <li><b>Nothing carved beside it.</b> No pool near a bear cave, a hollow knot or a sunbeam
 *       clearing (which burns the ground to ash).</li>
 * </ul>
 *
 * <p>Everything is a pure function of the column, like the rest of the level: a chunk works out
 * its neighbours' facts for itself, so two chunks always agree on where a pool's water and banks
 * are. Per-thread caches keep that cheap (a pool column looks at columns up to 6 blocks away).
 */
final class Level227Ponds {
    /** The deepest any pool gets (blocks of water). */
    static final int MAX_DEPTH = 4;
    /** Blocks of clear air kept over every water block. */
    static final int AIR_ABOVE = 3;
    /** Blocks of clear air kept over a pool's shore (the dry columns right beside the water). */
    static final int SHORE_AIR = 2;
    /** Solid blocks always left under the deepest water, so a pool never opens into the void under a branch. */
    static final int FLOOR_UNDER = 2;
    /**
     * How fast the wanted depth grows with {@link Level227Tree#puddleStrength}: 1 + strength * this.
     * Tuned by sampling: the middles of most pools over 64 columns reach 3-4.
     */
    static final double BOWL = 12.0;
    /**
     * A pool's surface is the lowest floor of the dip within this many blocks (a square). Sampled
     * 1..6: 2 floods the most of the dips (wider reaches leave more dry seams where two levels meet).
     */
    static final int LEVEL_REACH = 2;
    /**
     * The most {@link Level227Tree#puddleStrength} changes per block (along x plus along z): its
     * noises change by at most 3 x their frequency per block, the steeper one is 0.06 and is divided by 0.48.
     */
    private static final double DIP_SLOPE = 3 * 0.06 / 0.48;
    /** Only fairly flat forest floor holds a pool (half-way out from a branch's ridge line). */
    private static final double MAX_ND = 0.45;
    /** Pieces of the tree past this (lateral distance / half-width) have grooves cut in their flanks. */
    private static final double GROOVED_ND = 0.72;
    /** Blocks kept between a pool and a sunbeam clearing's ash (which reaches radius + 5). */
    private static final double BEAM_CLEAR = 6;
    private static final int NONE = Integer.MIN_VALUE;

    private Level227Ponds() {}

    /** Blocks of water at column (x, z): 0 for none, else 1..{@link #MAX_DEPTH} from {@link #surface} down. */
    static int depth(int x, int z) {
        Cache c = CACHE.get();
        int best = c.wanted(x, z);
        // The bowl: never more than 1 + the distance to a column that is shallower (or dry).
        for (int d = 1; d < best; d++) {
            for (int i = 0; i < d; i++) {
                int j = d - i;
                best = Math.min(best, d + Math.min(Math.min(c.wanted(x + i, z + j), c.wanted(x + j, z - i)),
                        Math.min(c.wanted(x - i, z - j), c.wanted(x - j, z + i))));
                if (best <= d) {
                    return best;
                }
            }
        }
        return best;
    }

    /** The Y of the top water block at (x, z) (only meaningful where {@link #depth} is above 0). */
    static int surface(int x, int z) {
        return CACHE.get().level(x, z);
    }

    /**
     * The Y of the top block of the forest floor at (x, z) (Integer.MIN_VALUE where there is no
     * clean floor): where {@link #depth} is above 0 this is {@link #surface} or one block above it,
     * and the blocks between are dug out.
     */
    static int floor(int x, int z) {
        Cache c = CACHE.get();
        return c.top[c.facts(x, z)];
    }

    /**
     * The shore: for a dry column right beside pool water (along x or z), the Y its forest floor's
     * top block should have: the water's surface Y. Its floor is never below that (see
     * computeWanted), and where it lies one block above, that block is dug out, so from the water
     * you step straight out onto ground level with the surface instead of climbing a bank.
     * (Beside two pools at different levels the floor stays at the higher one, which is never dug
     * below, so the higher pool keeps its solid side.) Integer.MIN_VALUE for any other column.
     */
    static int shore(int x, int z) {
        if (depth(x, z) > 0) {
            return NONE;
        }
        int water = NONE;
        if (depth(x + 1, z) > 0) {
            water = Math.max(water, surface(x + 1, z));
        }
        if (depth(x - 1, z) > 0) {
            water = Math.max(water, surface(x - 1, z));
        }
        if (depth(x, z + 1) > 0) {
            water = Math.max(water, surface(x, z + 1));
        }
        if (depth(x, z - 1) > 0) {
            water = Math.max(water, surface(x, z - 1));
        }
        if (water == NONE) {
            return NONE;
        }
        int floor = floor(x, z);
        return floor == water + 1 ? water : floor;
    }

    /** True when there is pool water at (x, z) or within {@code r} blocks of it (in a square). */
    static boolean near(int x, int z, int r) {
        // Far enough outside a dip, no column within r blocks can be in one: one test, not (2r+1)^2.
        if (Level227Tree.puddleStrength(x + 0.5, z + 0.5) <= -DIP_SLOPE * 2 * r) {
            return false;
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                // Water only ever lies in a puddle dip: the cheap noise test first.
                if (Level227Tree.puddleStrength(x + dx + 0.5, z + dz + 0.5) > 0 && depth(x + dx, z + dz) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when any column within {@code r} blocks (a disc) of column (x, z) lies in a puddle dip,
     * i.e. might hold water. For planning the forest's trees and logs, which must never stand in a
     * pool: water only ever lies in a dip, so this never misses one.
     */
    static boolean dipWithin(int x, int z, double r) {
        int n = (int) Math.ceil(r);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                if (dx * dx + dz * dz <= r * r && Level227Tree.puddleStrength(x + dx + 0.5, z + dz + 0.5) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** No pool near a bear cave or hollow knot (their tunnels and hollows are carved air) or a sunbeam's ash. */
    private static boolean blocked(double px, double pz) {
        for (Beam b : Level227Tree.beams()) {
            double reach = b.radius() + BEAM_CLEAR;
            if (Math.abs(b.x() - px) < reach && Math.abs(b.z() - pz) < reach && Math.hypot(b.x() - px, b.z() - pz) < reach) {
                return true;
            }
        }
        return caveNear(Level227Tile.CAVE, Level227Tile.CAVE_CELL, px, pz) || caveNear(Level227Tile.KNOT, Level227Tile.KNOT_CELL, px, pz);
    }

    /**
     * Whether a cave of this kind carves (or lines) the ground at (px, pz) or right beside it: the
     * reach Level227Layout carves them within (the body, and a bear cave's tunnel a little past its
     * mouth), plus one block for the neighbours.
     */
    private static boolean caveNear(int kind, int cell, double px, double pz) {
        int reach = Level227Tile.CAVE_REACH;
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        for (int cx = Math.floorDiv(bx - reach, cell); cx <= Math.floorDiv(bx + reach, cell); cx++) {
            for (int cz = Math.floorDiv(bz - reach, cell); cz <= Math.floorDiv(bz + reach, cell); cz++) {
                Cave c = Level227Tile.caveAt(kind, cx, cz);
                if (c == null) {
                    continue;
                }
                double body = c.rx() + 5;
                if (Math.abs(px - c.x()) < body && Math.abs(pz - c.z()) < body) {
                    return true;
                }
                if (kind == Level227Tile.CAVE) {
                    // The tunnel runs on a little past its mouth (t up to 1.15).
                    double ex = c.x() + (c.ex() - c.x()) * 1.15, ez = c.z() + (c.ez() - c.z()) * 1.15;
                    if (distToSegment(px, pz, c.x(), c.z(), ex, ez) < c.tunnelR() + 4) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double distToSegment(double x, double z, double x0, double z0, double x1, double z1) {
        double tx = x1 - x0, tz = z1 - z0;
        double len2 = tx * tx + tz * tz;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - x0) * tx + (z - z0) * tz) / len2));
        return Math.hypot(x - (x0 + tx * t), z - (z0 + tz * t));
    }

    // ------------------------------------------------------------------------------------------
    // Per-thread cache of column facts
    // ------------------------------------------------------------------------------------------

    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    /**
     * What a pool needs to know about a column, worked out once per thread and kept in small
     * direct-mapped tables (a 64x64 window of columns), so generating a chunk allocates nothing.
     */
    private static final class Cache {
        static final int BITS = 6;
        static final int MASK = (1 << BITS) - 1;
        static final int SIZE = 1 << (2 * BITS);

        final Span span = new Span();
        // The forest floor: top block Y (NONE if no clean floor), lowest solid block of its piece, its nd.
        final long[] factKey = new long[SIZE];
        final int[] top = new int[SIZE];
        final int[] lo = new int[SIZE];
        final double[] nd = new double[SIZE];
        // The water level a column would have (NONE: it can't hold water).
        final long[] levelKey = new long[SIZE];
        final int[] levels = new int[SIZE];
        // The depth wanted before the bowl rule (0 = no water).
        final long[] wantKey = new long[SIZE];
        final byte[] want = new byte[SIZE];

        Cache() {
            Arrays.fill(factKey, Long.MIN_VALUE);
            Arrays.fill(levelKey, Long.MIN_VALUE);
            Arrays.fill(wantKey, Long.MIN_VALUE);
        }

        private static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        private static int slot(int x, int z) {
            return (x & MASK) << BITS | (z & MASK);
        }

        /** The table slot holding column (x, z)'s floor facts, filled in if needed. */
        int facts(int x, int z) {
            int i = slot(x, z);
            long key = key(x, z);
            if (factKey[i] != key) {
                factKey[i] = key;
                fill(i, x + 0.5, z + 0.5);
            }
            return i;
        }

        /**
         * The forest floor on the highest branch top here (as Level227Tile.ground finds it), but
         * only if it is "clean": the top of the column's solid wood is exactly that floor (no other
         * piece over it within AIR_ABOVE + 1 blocks), no broken tip's cut face (its growth rings
         * would replace the water), the piece lies flat enough to have no grooves, and it is among
         * the first pieces Level227Layout reads for the column (it reads at most MAX_SPANS).
         */
        private void fill(int i, double px, double pz) {
            top[i] = NONE;
            int feet = Level227Tile.ground(px, pz, span);
            if (feet == Integer.MIN_VALUE || span.nd >= GROOVED_ND) {
                return;
            }
            int floor = feet - 1;
            int bottom = (int) Math.ceil(span.bot);
            double flat = span.nd;
            int best = span.id;
            boolean listed = false;
            int hits = 0;
            for (int id : Level227Tree.cellAt(px, pz)) {
                if (!Level227Tree.span(id, px, pz, span)) {
                    continue;
                }
                int kLo = (int) Math.ceil(span.bot), kHi = (int) Math.floor(span.top);
                if (kHi < kLo) {
                    continue;
                }
                if (id == best && hits < Level227Layout.MAX_SPANS) {
                    listed = true;
                }
                hits++;
                if (kHi > floor && kLo <= floor + AIR_ABOVE + 1 || span.cut < 1.0) {
                    return;
                }
            }
            if (listed) {
                top[i] = floor;
                lo[i] = bottom;
                nd[i] = flat;
            }
        }

        /** A dip column with a clean, flat-enough floor: could be under water. */
        private boolean dipFloor(int x, int z) {
            if (Level227Tree.puddleStrength(x + 0.5, z + 0.5) <= 0) {
                return false;
            }
            int i = facts(x, z);
            return top[i] != NONE && nd[i] < MAX_ND;
        }

        /**
         * The water level column (x, z) would have: the lowest dip floor within LEVEL_REACH
         * blocks. NONE when the column itself is no dip floor, or its floor is more than one block
         * above that level (it would need more than its top block dug out), or the piece is too
         * thin to keep FLOOR_UNDER blocks under even one block of water.
         */
        int level(int x, int z) {
            int i = slot(x, z);
            long key = key(x, z);
            if (levelKey[i] != key) {
                levelKey[i] = key;
                levels[i] = computeLevel(x, z);
            }
            return levels[i];
        }

        private int computeLevel(int x, int z) {
            if (!dipFloor(x, z)) {
                return NONE;
            }
            int i = facts(x, z);
            int floor = top[i], bottom = lo[i];
            int level = floor;
            for (int dx = -LEVEL_REACH; dx <= LEVEL_REACH; dx++) {
                for (int dz = -LEVEL_REACH; dz <= LEVEL_REACH; dz++) {
                    if ((dx != 0 || dz != 0) && dipFloor(x + dx, z + dz)) {
                        level = Math.min(level, top[facts(x + dx, z + dz)]);
                    }
                }
            }
            if (floor - level > 1 || level - bottom + 1 < 1 + FLOOR_UNDER) {
                return NONE;
            }
            return level;
        }

        /** The depth column (x, z) would like (0: no water), before the bowl rule of {@link #depth}. */
        int wanted(int x, int z) {
            int i = slot(x, z);
            long key = key(x, z);
            if (wantKey[i] != key) {
                wantKey[i] = key;
                want[i] = (byte) computeWanted(x, z);
            }
            return want[i];
        }

        private int computeWanted(int x, int z) {
            int level = level(x, z);
            if (level == NONE) {
                return 0;
            }
            // Sealed sides: each neighbour either could hold water at this same level (if it ends
            // up dry, its floor is at most one block above: see computeLevel), or can't hold
            // water at all and is solid from one block above this level down past it.
            if (!side(level, x + 1, z) || !side(level, x - 1, z) || !side(level, x, z + 1) || !side(level, x, z - 1)) {
                return 0;
            }
            if (blocked(x + 0.5, z + 0.5)) {
                return 0;
            }
            int thickness = level - lo[facts(x, z)] + 1;
            int bowl = 1 + (int) (Level227Tree.puddleStrength(x + 0.5, z + 0.5) * BOWL);
            return Math.min(Math.min(MAX_DEPTH, thickness - FLOOR_UNDER), bowl);
        }

        private boolean side(int level, int x, int z) {
            int other = level(x, z);
            if (other != NONE) {
                return other == level;
            }
            int j = facts(x, z);
            int t = top[j];
            return t != NONE && t >= level && t <= level + 1 && lo[j] <= level;
        }
    }
}
