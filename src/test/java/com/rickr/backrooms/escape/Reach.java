package com.rickr.backrooms.escape;

import static com.rickr.backrooms.escape.VoxelWorld.CLIMB;
import static com.rickr.backrooms.escape.VoxelWorld.HAZARD;
import static com.rickr.backrooms.escape.VoxelWorld.LOW;
import static com.rickr.backrooms.escape.VoxelWorld.PASS;
import static com.rickr.backrooms.escape.VoxelWorld.SUPPORT;
import static com.rickr.backrooms.escape.VoxelWorld.TALL;
import static com.rickr.backrooms.escape.VoxelWorld.WATER;

/**
 * Where a player can get to on foot, as a search over the blocks of a {@link VoxelWorld}.
 *
 * <p>The movement rules are a deliberately plain, slightly cautious version of vanilla's: walk to
 * a neighbouring block, step up half a block, jump up one block when there is headroom, drop down
 * any distance, swim anywhere through water (up and down too), climb ladders and ropes, open any
 * door you could open by hand. No sprint-jumping across gaps, no pillaring, no breaking blocks:
 * the levels are unbreakable, and an exit that needs parkour is worth hearing about anyway.
 *
 * <p>A node is "a player whose feet are in block (x, y, z)". It exists when the body fits (that
 * block and the one above it are passable) and something holds the player there: a floor under
 * the feet, a slab or step the feet are in, water, or a ladder.
 */
final class Reach {
    /** What the search is looking for. {@code fromY} is the feet height a fall started from. */
    @FunctionalInterface
    interface Goal {
        boolean test(VoxelWorld world, int x, int y, int z, int fromY);
    }

    /** The outcome: found or not, where, how much was searched, and the closest the search got. */
    record Result(boolean reached, int x, int y, int z, int nodes, int columns, int nearX, int nearY, int nearZ, int nearDistance) {
        String describe() {
            return reached
                    ? "reached the exit at " + x + " " + y + " " + z + " (" + nodes + " positions searched)"
                    : "no way to the exit: searched " + nodes + " positions over " + columns
                            + " columns; got closest at " + nearX + " " + nearY + " " + nearZ + " (" + nearDistance + " blocks from the exit)";
        }
    }

    private static final int STAND = 1;
    private static final int SWIM = 2;
    private static final int CLIMBING = 3;

    private final VoxelWorld w;
    private final Goal goal;
    private final int targetX;
    private final int targetZ;
    private final boolean jumping;
    private final long[] seen;
    private long[][] buckets = new long[64][];
    private int[] bucketSize = new int[64];

    private Reach(VoxelWorld world, Goal goal, int targetX, int targetZ, boolean jumping) {
        this.w = world;
        this.goal = goal;
        this.targetX = targetX;
        this.targetZ = targetZ;
        this.jumping = jumping;
        this.seen = new long[(int) (((long) world.size * world.size * world.height + 63) / 64)];
    }

    /**
     * Searches from feet block (x, y, z) for the goal, always trying next the position closest (in
     * x and z) to (targetX, targetZ). It only steers the order: if the goal can be reached anywhere
     * inside the window, it is found.
     *
     * @param jumping whether the level lets you jump on land. The Backrooms switch the jump key off
     *                (client/gameplay/MovementClientEvents) except in water, on anything you climb,
     *                and on Levels 490 and 227; so elsewhere a one-block ledge is a wall.
     */
    static Result search(VoxelWorld world, int x, int y, int z, int targetX, int targetZ, Goal goal, boolean jumping) {
        return new Reach(world, goal, targetX, targetZ, jumping).run(x, y, z);
    }

    /** Levels where the jump key works on land (MovementClientEvents). */
    static boolean jumpsOn(com.rickr.backrooms.world.BackroomsLevel level) {
        return level == com.rickr.backrooms.world.BackroomsLevel.LEVEL_490 || level == com.rickr.backrooms.world.BackroomsLevel.LEVEL_227;
    }

    /** What holds a player whose feet are in block (x, y, z): STAND, SWIM, CLIMBING, or 0 (no such node). */
    static int kind(VoxelWorld w, int x, int y, int z) {
        if (y < 0 || y >= w.height || !w.inside(x, z)) {
            return 0;
        }
        int at = w.flags(x, y, z);
        int above = w.flags(x, y + 1, z);
        int below = w.flags(x, y - 1, z);
        if ((at & HAZARD) != 0 || (above & HAZARD) != 0 || (below & TALL) != 0) {
            return 0;
        }
        if ((at & LOW) != 0) {
            // Feet on a slab or half step, half a block up: the head needs the two blocks above.
            return (above & PASS) != 0 && (w.flags(x, y + 2, z) & PASS) != 0 ? STAND : 0;
        }
        if ((at & PASS) == 0 || (above & PASS) == 0) {
            return 0;
        }
        if ((at & WATER) != 0) {
            return SWIM;
        }
        if ((at & CLIMB) != 0 || (above & CLIMB) != 0) {
            return CLIMBING;
        }
        return (below & SUPPORT) != 0 && (below & HAZARD) == 0 ? STAND : 0;
    }

    /** Height of the feet above block y (0, or 0.5 on a slab). */
    private static double lift(VoxelWorld w, int x, int y, int z) {
        return (w.flags(x, y, z) & LOW) != 0 ? 0.5 : 0.0;
    }

    private boolean bodyFits(int x, int y, int z) {
        return y >= 0 && (w.flags(x, y, z) & PASS) != 0 && (w.flags(x, y + 1, z) & PASS) != 0
                && (w.flags(x, y - 1, z) & TALL) == 0;
    }

    private long index(int x, int y, int z) {
        return ((long) (x - w.originX) * w.size + (z - w.originZ)) * w.height + y;
    }

    private boolean mark(int x, int y, int z) {
        long i = index(x, y, z);
        int word = (int) (i >>> 6);
        long bit = 1L << (i & 63);
        if ((seen[word] & bit) != 0) {
            return false;
        }
        seen[word] |= bit;
        return true;
    }

    private int distance(int x, int z) {
        return Math.max(Math.abs(x - targetX), Math.abs(z - targetZ));
    }

    private void push(int x, int y, int z) {
        int d = distance(x, z);
        if (d >= buckets.length) {
            int n = Math.max(d + 1, buckets.length * 2);
            buckets = java.util.Arrays.copyOf(buckets, n);
            bucketSize = java.util.Arrays.copyOf(bucketSize, n);
        }
        long[] b = buckets[d];
        if (b == null) {
            b = buckets[d] = new long[256];
        } else if (bucketSize[d] == b.length) {
            b = buckets[d] = java.util.Arrays.copyOf(b, b.length * 2);
        }
        b[bucketSize[d]++] = pack(x, y, z);
    }

    private long pack(int x, int y, int z) {
        return ((long) (x - w.originX) << 40) | ((long) (z - w.originZ) << 20) | y;
    }

    private Result run(int sx, int sy, int sz) {
        int nodes = 0;
        int nearX = sx;
        int nearY = sy;
        int nearZ = sz;
        int nearD = distance(sx, sz);
        if (kind(w, sx, sy, sz) == 0) {
            return new Result(false, sx, sy, sz, 0, w.built, sx, sy, sz, nearD);
        }
        mark(sx, sy, sz);
        push(sx, sy, sz);
        int lowest = 0;
        int[] found = null;
        while (found == null) {
            while (lowest < buckets.length && bucketSize[lowest] == 0) {
                lowest++;
            }
            if (lowest >= buckets.length) {
                break;
            }
            long p = buckets[lowest][--bucketSize[lowest]];
            int x = (int) (p >>> 40) + w.originX;
            int z = (int) ((p >>> 20) & 0xFFFFF) + w.originZ;
            int y = (int) (p & 0xFFFFF);
            nodes++;
            if (lowest < nearD) {
                nearD = lowest;
                nearX = x;
                nearY = y;
                nearZ = z;
            }
            if (goal.test(w, x, y, z, y)) {
                found = new int[] {x, y, z};
                break;
            }
            found = expand(x, y, z);
            // Anything pushed may sit in a lower bucket than the one we are emptying.
            lowest = Math.max(0, Math.min(lowest, distance(x, z) - 1));
        }
        if (found != null) {
            return new Result(true, found[0], found[1], found[2], nodes, w.built, nearX, nearY, nearZ, nearD);
        }
        return new Result(false, 0, 0, 0, nodes, w.built, nearX, nearY, nearZ, nearD);
    }

    /** Adds every position one move away from (x, y, z); returns a goal position met on the way (falls), or null. */
    private int[] expand(int x, int y, int z) {
        int kind = kind(w, x, y, z);
        double h = y + lift(w, x, y, z);
        // The jump key works in water and on ladders everywhere; on land only where the level allows.
        boolean canJump = (jumping || kind == SWIM || kind == CLIMBING) && (w.flags(x, y + 2, z) & PASS) != 0;
        for (int d = 0; d < 4; d++) {
            int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            int nx = x + dx;
            int nz = z + dz;
            if (!w.inside(nx, nz)) {
                continue;
            }
            // Up: a step (at most 0.6 blocks), a stair (a full block, but in two half steps), or a
            // jump (1.25 with the block above your head clear).
            if (kind(w, nx, y + 1, nz) != 0) {
                double rise = y + 1 + lift(w, nx, y + 1, nz) - h;
                boolean stair = rise <= 1.0 && (w.flags(nx, y, nz) & VoxelWorld.STAIR) != 0;
                if (rise <= 0.6 || stair || rise <= 1.25 && canJump) {
                    visit(nx, y + 1, nz);
                }
            }
            int level = kind(w, nx, y, nz);
            if (level != 0) {
                double rise = y + lift(w, nx, y, nz) - h;
                if (rise <= 0.6 || rise <= 1.25 && canJump) {
                    visit(nx, y, nz);
                }
                continue;
            }
            if (!bodyFits(nx, y, nz)) {
                continue;
            }
            // A gap: where jumping is allowed, a running jump clears one or two open columns and
            // lands level or lower on the far side (Level 490's broken stair: gaps of a block and a half).
            if (jumping && kind == STAND && canJump) {
                jumpAcross(x, y, z, dx, dz);
            }
            // Walk (or swim) off an edge: fall straight down until something holds you.
            int[] hit = fall(nx, y, nz, y);
            if (hit != null) {
                return hit;
            }
        }
        if (kind == SWIM || kind == CLIMBING) {
            if (kind(w, x, y + 1, z) != 0) {
                visit(x, y + 1, z);
            }
            if (kind(w, x, y - 1, z) != 0) {
                visit(x, y - 1, z);
            } else if (bodyFits(x, y - 1, z)) {
                int[] hit = fall(x, y - 1, z, y);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** Lands a jump from (x, y, z) over open columns in direction (dx, dz), on the first floor 2-3 blocks out. */
    private void jumpAcross(int x, int y, int z, int dx, int dz) {
        for (int dist = 2; dist <= 3; dist++) {
            int mx = x + dx * (dist - 1);
            int mz = z + dz * (dist - 1);
            // The column jumped over must be clear for the whole arc (feet up to a block above head).
            if (!w.inside(mx, mz) || !bodyFits(mx, y, mz) || (w.flags(mx, y + 2, mz) & PASS) == 0) {
                return;
            }
            int lx = x + dx * dist;
            int lz = z + dz * dist;
            if (!w.inside(lx, lz)) {
                return;
            }
            for (int ly = y; ly >= y - 1; ly--) {
                if (kind(w, lx, ly, lz) == STAND) {
                    visit(lx, ly, lz);
                    return;
                }
            }
        }
    }

    /** Falls from feet block {@code y} (already clear) downwards; checks the goal on every block passed. */
    private int[] fall(int x, int y, int z, int fromY) {
        for (int yy = y; ; yy--) {
            if (goal.test(w, x, yy, z, fromY)) {
                return new int[] {x, yy, z};
            }
            if (yy < 0) {
                return null; // out of the world: nothing to land on
            }
            if (kind(w, x, yy, z) != 0) {
                visit(x, yy, z);
                return null;
            }
            if (!bodyFits(x, yy, z)) {
                return null;
            }
        }
    }

    private void visit(int x, int y, int z) {
        if (mark(x, y, z)) {
            push(x, y, z);
        }
    }
}
