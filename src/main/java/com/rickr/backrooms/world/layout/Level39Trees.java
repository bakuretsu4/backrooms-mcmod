package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.TreeBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Grows the bare deciduous trees of Level 39 (the photo's tree is the model): a tapering trunk
 * that forks into a few crooked limbs leaning out in every direction, branches forking off them,
 * fine twigs at the tips, and on some trees a few clumps of dark leaves.
 *
 * <p>A tree is grown once, when the town plan is made, into a list of parts relative to its trunk
 * foot: tree pieces ({@link TreeBlock}: thickness, which way the piece below is, which ways the
 * pieces above are), twig tangles and leaf clumps. Limbs climb one block out for each block up
 * (the tree blocks have 45-degree diagonal joins), so they lean naturally instead of sticking out
 * flat like a cross.
 *
 * <p>Each part is packed into one int: dx + 16 (5 bits), dy (5), dz + 16 (5), type (2: 0 piece,
 * 1 twigs, 2 leaves), thick (2), down (3: {@link TreeBlock.Link} ordinal), up (5: bits C N E S W).
 */
final class Level39Trees {
    static final int PIECE = 0;
    static final int TWIGS = 1;
    static final int LEAVES = 2;

    static final int UP_C = 1;
    static final int UP_N = 2;
    static final int UP_E = 4;
    static final int UP_S = 8;
    static final int UP_W = 16;

    private static final TreeBlock.Link[] LINKS = TreeBlock.Link.values();

    private Level39Trees() {}

    static int dx(int p) {
        return (p & 31) - 16;
    }

    static int dy(int p) {
        return (p >>> 5) & 31;
    }

    static int dz(int p) {
        return ((p >>> 10) & 31) - 16;
    }

    static int type(int p) {
        return (p >>> 15) & 3;
    }

    private static int pack(int dx, int dy, int dz, int type, int thick, int down, int up) {
        return (dx + 16) | (dy << 5) | ((dz + 16) << 10) | (type << 15) | (thick << 17) | (down << 19) | (up << 22);
    }

    /** The block a part stands for (no allocation: block states are shared). */
    static BlockState state(int p) {
        int type = type(p);
        if (type == TWIGS) {
            return Level39Blocks.TWIGS.get().defaultBlockState();
        }
        if (type == LEAVES) {
            return Level39Blocks.DARK_LEAVES.get().defaultBlockState();
        }
        int up = (p >>> 22) & 31;
        return Level39Blocks.TREE.get().defaultBlockState()
                .setValue(TreeBlock.THICK, (p >>> 17) & 3)
                .setValue(TreeBlock.DOWN, LINKS[(p >>> 19) & 7])
                .setValue(TreeBlock.UP_C, (up & UP_C) != 0)
                .setValue(TreeBlock.UP_N, (up & UP_N) != 0)
                .setValue(TreeBlock.UP_E, (up & UP_E) != 0)
                .setValue(TreeBlock.UP_S, (up & UP_S) != 0)
                .setValue(TreeBlock.UP_W, (up & UP_W) != 0);
    }

    /** How far the tree reaches sideways from its trunk (blocks). */
    static int reach(int[] parts) {
        int r = 1;
        for (int p : parts) {
            r = Math.max(r, Math.max(Math.abs(dx(p)), Math.abs(dz(p))));
        }
        return r;
    }

    /** Height of the tree's top above its foot (blocks). */
    static int height(int[] parts) {
        int h = 0;
        for (int p : parts) {
            h = Math.max(h, dy(p));
        }
        return h;
    }

    // ---- Growing -------------------------------------------------------------------------------

    /** Directions a limb can lean: N, E, S, W (as x/z steps) and the matching up bit and down link. */
    private static final int[] STEP_X = {0, 1, 0, -1};
    private static final int[] STEP_Z = {-1, 0, 1, 0};
    private static final int[] UP_BIT = {UP_N, UP_E, UP_S, UP_W};
    /** A piece reached by stepping toward direction d has its parent on the opposite side. */
    private static final TreeBlock.Link[] DOWN_FROM = {TreeBlock.Link.SOUTH, TreeBlock.Link.WEST, TreeBlock.Link.NORTH, TreeBlock.Link.EAST};

    /** A tree being grown: parts by position. */
    private static final class Grower {
        final Map<Integer, int[]> parts = new HashMap<>(); // key -> {type, thick, down, up}
        final List<Integer> order = new ArrayList<>();
        long state;

        Grower(long salt) {
            state = salt;
        }

        int next(int bound) {
            state = Hash.mix(state + 0x9E3779B97F4A7C15L);
            return Hash.range(state, bound);
        }

        double unit() {
            state = Hash.mix(state + 0x9E3779B97F4A7C15L);
            return Hash.unit(state);
        }

        static int key(int x, int y, int z) {
            return ((x + 16) << 10) | (y << 5) | (z + 16);
        }

        boolean free(int x, int y, int z) {
            return Math.abs(x) <= 7 && Math.abs(z) <= 7 && y < 31 && !parts.containsKey(key(x, y, z));
        }

        void put(int x, int y, int z, int type, int thick, TreeBlock.Link down) {
            int k = key(x, y, z);
            parts.put(k, new int[] {type, thick, down.ordinal(), 0});
            order.add(k);
        }

        void addUp(int x, int y, int z, int bit) {
            int[] p = parts.get(key(x, y, z));
            if (p != null) p[3] |= bit;
        }

        /** Twigs (and on leafy trees leaves) round a branch tip, wherever there is room. */
        void tip(int x, int y, int z, boolean leafy) {
            if (free(x, y + 1, z)) put(x, y + 1, z, TWIGS, 0, TreeBlock.Link.NONE);
            for (int d = 0; d < 4; d++) {
                if (unit() < 0.45 && free(x + STEP_X[d], y, z + STEP_Z[d])) {
                    put(x + STEP_X[d], y, z + STEP_Z[d], TWIGS, 0, TreeBlock.Link.NONE);
                }
            }
            if (leafy) {
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        for (int oy = 0; oy <= 2; oy++) {
                            if (unit() < 0.55 && free(x + ox, y + oy, z + oz)) {
                                put(x + ox, y + oy, z + oz, LEAVES, 0, TreeBlock.Link.NONE);
                            }
                        }
                    }
                }
            }
        }

        /**
         * A limb from the piece at (x, y, z), leaning toward {@code dir}: each step climbs one
         * block, mostly also one block out (now and then straight up, or veering sideways), getting
         * thinner, forking once, ending in twigs.
         */
        void limb(int x, int y, int z, int dir, int thick, int len, boolean fork, boolean leafy) {
            for (int s = 0; s < len; s++) {
                double r = unit();
                int d = dir;
                boolean up = r < 0.22;
                if (!up && r > 0.88) {
                    d = (dir + (unit() < 0.5 ? 1 : 3)) & 3; // veer sideways for a step
                }
                int nx = up ? x : x + STEP_X[d];
                int nz = up ? z : z + STEP_Z[d];
                int ny = y + 1;
                if (!free(nx, ny, nz)) {
                    break;
                }
                addUp(x, y, z, up ? UP_C : UP_BIT[d]);
                int t = s >= len / 2 ? Math.max(0, thick - 1) : thick;
                put(nx, ny, nz, PIECE, t, up ? TreeBlock.Link.CENTER : DOWN_FROM[d]);
                x = nx;
                y = ny;
                z = nz;
                if (fork && (s == len / 3 || s == 2 * len / 3) && thick > 0) {
                    int side = (dir + (unit() < 0.5 ? 1 : 3)) & 3;
                    limb(x, y, z, side, thick - 1, 2 + next(3), s == len / 3, leafy);
                }
            }
            tip(x, y, z, leafy);
        }
    }

    /** Grows one tree from {@code salt}. Leafy trees keep a few clumps of dark leaves. */
    static int[] grow(long salt, boolean leafy) {
        Grower g = new Grower(salt);
        // Trunk: thick at the foot, thinner further up, sometimes leaning a step.
        int trunk = 4 + g.next(3);
        int x = 0;
        int z = 0;
        g.put(0, 0, 0, PIECE, 3, TreeBlock.Link.CENTER);
        for (int y = 1; y < trunk; y++) {
            boolean lean = y == trunk - 1 && g.unit() < 0.35;
            int d = g.next(4);
            int nx = lean ? x + STEP_X[d] : x;
            int nz = lean ? z + STEP_Z[d] : z;
            g.addUp(x, y - 1, z, lean ? UP_BIT[d] : UP_C);
            g.put(nx, y, nz, PIECE, y < trunk / 2 + 1 ? 3 : 2, lean ? DOWN_FROM[d] : TreeBlock.Link.CENTER);
            x = nx;
            z = nz;
        }
        int topY = trunk - 1;
        // Limbs fanning out from the top of the trunk (and one lower down on big trees).
        int limbs = 3 + g.next(2);
        int first = g.next(4);
        for (int l = 0; l < limbs; l++) {
            int dir = (first + l) & 3;
            g.limb(x, topY, z, dir, 2, 4 + g.next(3), true, leafy);
        }
        // A leader carrying on straight up out of the middle.
        if (g.unit() < 0.7) {
            g.limb(x, topY, z, first, 1, 3 + g.next(3), false, leafy);
        }
        if (trunk >= 4 && g.unit() < 0.6) {
            g.limb(0, 1 + g.next(Math.max(1, trunk - 2)), 0, (first + 2) & 3, 1, 2 + g.next(2), false, leafy);
        }
        int[] out = new int[g.order.size()];
        int n = 0;
        for (int key : g.order) {
            int[] p = g.parts.get(key);
            int px = ((key >>> 10) & 31) - 16;
            int py = (key >>> 5) & 31;
            int pz = (key & 31) - 16;
            out[n++] = pack(px, py, pz, p[0], p[1], p[2], p[3]);
        }
        return out;
    }
}
