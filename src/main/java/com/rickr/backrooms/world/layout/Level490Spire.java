package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.GiantChainBlock;
import com.rickr.backrooms.block.IronLanternBlock;
import com.rickr.backrooms.block.MarkedStoneBlock;
import com.rickr.backrooms.registry.Level490Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The spire on each of Level 490's sites (stage 2, docs/level-490-penance.md): a tower of pale,
 * grown-looking stone about 275 blocks tall, stacked like a spine. Each vertebra is turned, shifted
 * and tilted a little from the one below, with a darker disc where two meet, so its outline is
 * different from every side. Ribs curl out of it, broken chunks of it float around it, an iron band
 * round its upper body is bolted by beams to the site's four colossal chains, and four more chains
 * run from its top platform up to them: it hangs in the chains.
 *
 * <pre>
 *   y 476        the top platform, open, with a spur over the void: step off = the leap out
 *   y 425-475    ledge 3 (1 wide, a gap), then a 50-block ladder up the outside to the top
 *   y 375-425    stair D, inside, windows
 *   y 357        ledge 2 (2 wide, half way round), ladder to stair D
 *   y 317-347    LOOP 2: the stair repeats until you go back down (world/Level490Loops)
 *   y 307-313    the half-way room with a radio (checkpoint), big windows
 *   y 267-297    LOOP 1
 *   y 267-357    stair B-C, enclosed, lantern-lit, one long spiral through the room
 *   y 251        ledge 1 (3 wide), ladder to stair B-C
 *   y 201-251    stair A, inside, windows; it starts in the hall at the foot
 *   y 200        the great island; the entrance faces the bridge you came in by
 * </pre>
 *
 * <p>Everything is laid out in the spire's own frame: u points from its centre (the middle of
 * block (cx, cz)) toward the bridge you arrive by, v a quarter turn on, angles in degrees from +u
 * toward +v. Doors, ladders and the ledges' ends sit on the u and v axes so ladders face straight
 * out. The stairs are spirals 4 wide around a 3 x 3 newel, rising {@value #RISE} blocks a turn in
 * half steps (you walk up them, no jumping): one turn is the same as the next, which is what lets a
 * stretch of them repeat without you seeing the join.
 *
 * <p>Built per column like the rest of the level: the vertebrae and the shafts are worked out
 * directly; ribs, chunks, iron and the outside route (ledges, piers, ladders, doors, windows,
 * lanterns) are worked out once per site into per-column lists. The route goes last, so nothing
 * ever blocks it.
 */
public final class Level490Spire {
    // ---- the vertical plan ("feet" heights: the y you stand at) ----
    static final int BASE = Level490Layout.DECK_Y;
    /** Where you stand on the top platform; stepping off from up here is the leap out. */
    public static final int TOP = 476;
    static final int PLATFORM_Y = TOP - 1;
    /** How much the stairs climb in one turn (and how far a looping stretch moves you back). */
    public static final int RISE = 10;
    static final double NEWEL_R = 1.5;
    static final double SHAFT_R = 5.5;
    static final double WALL_R = 7.5;
    static final double HALL_R = 9.0;
    static final double HALL_WALL_R = 10.5;
    static final int HALL_TOP = 212;
    static final double ROOM_R = 9.0;
    static final double ROOM_WALL_R = 10.5;
    static final int ROOM_FLOOR = 307;
    static final int ROOM_TOP = 313;
    /** Farthest any part of the spire (collars round the chains) reaches from its centre. */
    public static final int REACH = 62;
    /** Farthest the vertebrae and the ledges reach. */
    private static final double BODY_REACH = 20.0;
    /** Where the vertebrae meet; ledges run at three of the joints. */
    private static final int[] JOINTS = {200, 228, 251, 279, 307, 332, 357, 384, 404, 425, 450, PLATFORM_Y};
    private static final int[] LEDGE_JOINTS = {251, 357, 425};
    /** Ribs curl out of the spire at these heights, four each, near the diagonals. */
    private static final int[] RIB_LEVELS = {236, 290, 340, 392};
    /** The iron band round the upper body, and the collars on the colossal chains. */
    private static final int BAND_Y = 433;
    private static final int UPPER_COLLAR_Y = 505;
    /** The spur off the top platform (angle from +u), over open void. */
    private static final double SPUR_DEG = 22.5;
    /** The site's colossal chains stand this far out on the diagonals (Level490Layout). */
    private static final double CHAIN_OFFSET = 34;

    /** A spiral stair from feet height {@code bottom} to {@code top}, starting (and ending) at angle a0. */
    private record Stair(int bottom, int top, int a0, boolean windows) {
        int revs() {
            return (top - bottom) / RISE;
        }

        /** Height of the stair's walking surface at turn n, angle deg. */
        double surface(int n, double deg) {
            return bottom + (n + frac(deg - a0)) * RISE;
        }
    }

    private static final Stair A = new Stair(201, 251, 30, true);
    private static final Stair BC = new Stair(267, 357, 210, false);
    private static final Stair D = new Stair(375, 425, 120, true);
    private static final Stair[] STAIRS = {A, BC, D};

    /**
     * An outside ledge at feet height {@code feet}, from the door at angle {@code from} round
     * {@code sweep} degrees to a ladder, which climbs to {@code ladderTop} (the block under the
     * next door's sill, or the top platform).
     */
    private record Ledge(int feet, int from, int sweep, int width, int ladderTop) {
        int ladder() {
            return (from + sweep) % 360;
        }
    }

    private static final Ledge[] LEDGES = {
            new Ledge(251, 90, 90, 3, 266),
            new Ledge(357, 270, 180, 2, 374),
            new Ledge(425, 180, 90, 1, PLATFORM_Y)};

    /**
     * A looping stretch of stair B-C: three identical turns from {@code bottom}. Climbing past
     * {@code trigger} (in the third) moves you down one turn; once you have gone round, walking back
     * below {@code release} breaks the loop. See world/Level490Loops.
     */
    public record Loop(int bottom, int trigger, double release) {}

    public static final Loop[] LOOPS = {new Loop(267, 289, 268.6), new Loop(317, 339, 315.0)};

    // ---- the blocks, by index (so a column's list is plain ints) ----
    private static final int AIR = 0;
    private static final int SPIRE = 1;
    private static final int JOINT = 2;
    private static final int STONE = 3;
    private static final int PAVING = 4;
    private static final int IRON = 5;
    private static final int SLAB = 6;
    private static final int LANTERN = 7;
    /** + world direction index (0 east, 1 south, 2 west, 3 north). */
    private static final int LADDER = 8;
    private static final int RADIO = 12;
    private static final Direction[] DIRS = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    private static volatile BlockState[] palette;

    // ---- the cells of the looping stretches' details (stair B-C's frame; the same every turn) ----
    private static final int[] NICHE_A = wallCell(BC.a0 + 0.3 * 360, 5.5, 6.6);
    private static final int[] NICHE_B = wallCell(BC.a0 + 0.8 * 360, 5.5, 6.6);
    private static final int[] ALCOVE_1 = wallCell(BC.a0 + 0.55 * 360, 5.5, 6.6);
    private static final int[] ALCOVE_2 = wallCell(BC.a0 + 0.55 * 360, 6.6, 7.5);
    private static final int[] MARKS = wallCell(BC.a0 + 0.15 * 360, 5.5, 6.6);
    private static final int[] WORDS_1 = wallCell(BC.a0 + 0.9 * 360, 5.5, 6.6);
    private static final int[] WORDS_2 = wallCell(BC.a0 + 0.45 * 360, 5.5, 6.6);
    private static final int[] SHACKLE_1 = treadCell(BC, BC.a0 + 0.12 * 360);
    private static final int[] SHACKLE_2 = treadCell(BC, BC.a0 + 0.62 * 360);

    private static final Map<Long, Level490Spire> CACHE = new ConcurrentHashMap<>();

    private final long seed;
    private final int cx;
    private final int cz;
    /** Quarter turns from world +x to the spire's +u (0 east, 1 south, 2 west, 3 north). */
    private final int q;
    // The vertebrae: centre offset, tilt, size, turn and lobes of each.
    private final double[] offU;
    private final double[] offV;
    private final double[] tiltU;
    private final double[] tiltV;
    private final double[] scale;
    private final double[] theta;
    private final double[] theta2;
    private final double[] lobe2;
    private final double[] lobe3;
    private final double[] discPhase;
    private final double platformPhase;
    private volatile float[][] outline;
    /** Each rib's centre line {x, y, z, radius} in world coordinates, for drawing it far away. */
    private final java.util.List<float[][]> ribLines = new java.util.ArrayList<>();
    /** Per column (key(u, v)): packed (y << 5 | block) to set, in order. */
    private final Long2ObjectOpenHashMap<IntArrayList> decor = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<IntArrayList> route = new Long2ObjectOpenHashMap<>();

    /** The spire of the site centred on block (cx, cz), its entrance facing quarter {@code q}. */
    static Level490Spire of(long seed, int cx, int cz, int q) {
        long key = Hash.of(seed, cx, cz, 4960);
        Level490Spire spire = CACHE.get(key);
        if (spire == null || spire.seed != seed || spire.cx != cx || spire.cz != cz) {
            if (CACHE.size() > 32) {
                CACHE.clear();
            }
            spire = new Level490Spire(seed, cx, cz, q);
            CACHE.put(key, spire);
        }
        return spire;
    }

    private Level490Spire(long seed, int cx, int cz, int q) {
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.q = q;
        int n = JOINTS.length - 1;
        offU = new double[n];
        offV = new double[n];
        tiltU = new double[n];
        tiltV = new double[n];
        scale = new double[n];
        theta = new double[n];
        theta2 = new double[n];
        lobe2 = new double[n];
        lobe3 = new double[n];
        discPhase = new double[n];
        for (int k = 0; k < n; k++) {
            long h = Hash.of(seed, cx * 31 + k, cz, 4961);
            // The foot stands square on the island; higher up the vertebrae wander more.
            double reach = 0.8 + 2.0 * Math.min(1.0, k / 3.0);
            double oa = Hash.unit(h) * Math.PI * 2;
            double om = reach * Math.sqrt(Hash.unit(Hash.mix(h)));
            offU[k] = om * Math.cos(oa);
            offV[k] = om * Math.sin(oa);
            double ta = Hash.unit(Hash.mix(h + 1)) * Math.PI * 2;
            double tm = (k == 0 ? 0.5 : 2.6) * Hash.unit(Hash.mix(h + 2));
            tiltU[k] = tm * Math.cos(ta);
            tiltV[k] = tm * Math.sin(ta);
            scale[k] = 0.95 + 0.13 * Hash.unit(Hash.mix(h + 3));
            theta[k] = Hash.unit(Hash.mix(h + 4)) * Math.PI * 2;
            theta2[k] = Hash.unit(Hash.mix(h + 5)) * Math.PI * 2;
            lobe2[k] = 0.08 + 0.06 * Hash.unit(Hash.mix(h + 6));
            lobe3[k] = 0.04 + 0.04 * Hash.unit(Hash.mix(h + 7));
            discPhase[k] = Hash.unit(Hash.mix(h + 8)) * Math.PI * 2;
        }
        platformPhase = Hash.unit(seed, cx, cz, 4964) * Math.PI * 2;
        buildDecor();
        buildRoute();
    }

    public int centreX() {
        return cx;
    }

    public int centreZ() {
        return cz;
    }

    // ==========================================================================================
    // Frames and small helpers
    // ==========================================================================================

    private int relU(int dx, int dz) {
        return switch (q) {
            case 0 -> dx;
            case 1 -> dz;
            case 2 -> -dx;
            default -> -dz;
        };
    }

    private int relV(int dx, int dz) {
        return switch (q) {
            case 0 -> dz;
            case 1 -> -dx;
            case 2 -> -dz;
            default -> dx;
        };
    }

    private int worldX(int u, int v) {
        return cx + switch (q) {
            case 0 -> u;
            case 1 -> -v;
            case 2 -> -u;
            default -> v;
        };
    }

    private int worldZ(int u, int v) {
        return cz + switch (q) {
            case 0 -> v;
            case 1 -> u;
            case 2 -> -v;
            default -> -u;
        };
    }

    /** World direction index (into DIRS) of the spire-frame angle {@code deg} (a multiple of 90). */
    private int worldDir(int deg) {
        return (q + deg / 90) & 3;
    }

    private static long key(int u, int v) {
        return ((long) u << 32) ^ (v & 0xffffffffL);
    }

    private static double deg(double u, double v) {
        return mod360(Math.toDegrees(Math.atan2(v, u)));
    }

    private static double mod360(double a) {
        double m = a % 360.0;
        return m < 0 ? m + 360.0 : m;
    }

    /** Fraction of a turn (0..1) that angle difference {@code d} degrees is. */
    private static double frac(double d) {
        return mod360(d) / 360.0;
    }

    private static double baseR(double y) {
        return 11.5 - 2.5 * (y - BASE) / (PLATFORM_Y - BASE);
    }

    private static int section(int y) {
        for (int k = JOINTS.length - 2; k > 0; k--) {
            if (y >= JOINTS[k]) {
                return k;
            }
        }
        return 0;
    }

    private static boolean ledgeJoint(int j) {
        for (int l : LEDGE_JOINTS) {
            if (l == j) {
                return true;
            }
        }
        return false;
    }

    /** The wall cell (radius rMin..rMax) nearest in angle to {@code deg}. */
    private static int[] wallCell(double deg, double rMin, double rMax) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int u = -9; u <= 9; u++) {
            for (int v = -9; v <= 9; v++) {
                double r = Math.hypot(u, v);
                if (r < rMin || r >= rMax) {
                    continue;
                }
                double d = Math.abs(Math.IEEEremainder(deg(u, v) - deg, 360.0));
                if (d < bestD) {
                    bestD = d;
                    best = new int[] {u, v};
                }
            }
        }
        return best;
    }

    /** A tread cell of stair s near {@code deg} whose step is a whole block (something can stand on it). */
    private static int[] treadCell(Stair s, double deg) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int u = -6; u <= 6; u++) {
            for (int v = -6; v <= 6; v++) {
                double r = Math.hypot(u, v);
                double q2 = Math.round(s.surface(0, deg(u, v)) * 2) / 2.0;
                if (r < 2.9 || r > 4.6 || q2 != Math.floor(q2)) {
                    continue;
                }
                double d = Math.abs(Math.IEEEremainder(deg(u, v) - deg, 360.0));
                if (d < bestD) {
                    bestD = d;
                    best = new int[] {u, v};
                }
            }
        }
        return best;
    }

    private static BlockState[] palette() {
        BlockState[] p = palette;
        if (p == null) {
            p = new BlockState[16];
            p[AIR] = Blocks.AIR.defaultBlockState();
            p[SPIRE] = Level490Blocks.SPIRE.get().defaultBlockState();
            p[JOINT] = Level490Blocks.SPIRE_JOINT.get().defaultBlockState();
            p[STONE] = Level490Blocks.STONE.get().defaultBlockState();
            p[PAVING] = Level490Blocks.PAVING.get().defaultBlockState();
            p[IRON] = Level490Blocks.CHAIN_IRON.get().defaultBlockState();
            p[SLAB] = Level490Blocks.PAVING_SLAB.get().defaultBlockState();
            p[LANTERN] = Level490Blocks.LANTERN.get().defaultBlockState().setValue(IronLanternBlock.LIT, true);
            for (int i = 0; i < 4; i++) {
                p[LADDER + i] = Level490Blocks.LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, DIRS[i]);
                p[RADIO + i] = ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, DIRS[i]);
            }
            palette = p;
        }
        return p;
    }

    // ==========================================================================================
    // The vertebrae
    // ==========================================================================================

    /** True if (u, v, y) is inside a vertebra (cell centres are whole numbers in this frame). */
    private boolean inBody(double u, double v, int y) {
        if (y < BASE || y >= PLATFORM_Y) {
            return false;
        }
        int k = section(y);
        double t = (y + 0.5 - JOINTS[k]) / (JOINTS[k + 1] - JOINTS[k]);
        double du = u - offU[k] - tiltU[k] * (t - 0.5);
        double dv = v - offV[k] - tiltV[k] * (t - 0.5);
        double a = Math.atan2(dv, du) - theta[k];
        // Narrow where it meets the next vertebra, full in the middle; lobed, never round.
        double rho = baseR(y) * scale[k] * (0.86 + 0.14 * Math.sin(Math.PI * t))
                * (1 + lobe2[k] * Math.cos(2 * a) + lobe3[k] * Math.cos(3 * a + theta2[k]));
        rho *= flare(y);
        return du * du + dv * dv <= rho * rho;
    }

    /** Roots flaring out over the island at the foot; a capital spreading under the top platform. */
    private static double flare(int y) {
        if (y < BASE + 12) {
            double f = (BASE + 12 - y) / 12.0;
            return 1 + 0.3 * f * f;
        }
        if (y > PLATFORM_Y - 13) {
            double f = (y - (PLATFORM_Y - 13)) / 12.0;
            return 1 + 0.4 * f * f;
        }
        return 1.0;
    }

    /**
     * The spire's outline, for drawing it where fog hides the blocks (client/Level490SpireFar):
     * {x, y, z, half width} every few blocks up its axis, world coordinates, then the top platform.
     */
    public float[][] outline() {
        float[][] o = outline;
        if (o == null) {
            java.util.List<float[]> points = new java.util.ArrayList<>();
            for (int y = BASE; y < PLATFORM_Y; y += 3) {
                int k = section(y);
                double t = (y + 0.5 - JOINTS[k]) / (JOINTS[k + 1] - JOINTS[k]);
                double u = offU[k] + tiltU[k] * (t - 0.5);
                double v = offV[k] + tiltV[k] * (t - 0.5);
                double w = Math.max(WALL_R, baseR(y) * scale[k] * (0.86 + 0.14 * Math.sin(Math.PI * t))
                        * (1 + 0.6 * (lobe2[k] + lobe3[k])) * flare(y));
                points.add(new float[] {(float) (cx + 0.5 + worldDx(u, v)), y, (float) (cz + 0.5 + worldDz(u, v)), (float) w});
            }
            points.add(new float[] {cx + 0.5f, PLATFORM_Y - 1, cz + 0.5f, 12.4f});
            points.add(new float[] {cx + 0.5f, PLATFORM_Y + 1, cz + 0.5f, 12.4f});
            o = points.toArray(new float[0][]);
            outline = o;
        }
        return o;
    }

    /** Each rib's centre line, {x, y, z, radius} points in world coordinates. */
    public java.util.List<float[][]> ribLines() {
        return ribLines;
    }

    private double worldDx(double u, double v) {
        return switch (q) {
            case 0 -> u;
            case 1 -> -v;
            case 2 -> -u;
            default -> v;
        };
    }

    private double worldDz(double u, double v) {
        return switch (q) {
            case 0 -> v;
            case 1 -> u;
            case 2 -> -v;
            default -> -u;
        };
    }

    /** True if (u, v, y) is in the darker disc where two vertebrae meet (not at the ledges). */
    private boolean inDisc(double u, double v, int y) {
        for (int k = 1; k < JOINTS.length - 1; k++) {
            int j = JOINTS[k];
            if ((y == j || y == j - 1) && !ledgeJoint(j)) {
                double du = u - offU[k - 1] - tiltU[k - 1] * 0.5;
                double dv = v - offV[k - 1] - tiltV[k - 1] * 0.5;
                double a = Math.atan2(dv, du);
                double rho = baseR(j) * scale[k - 1] * 1.12 * (1 + 0.05 * Math.sin(7 * a + discPhase[k]));
                return du * du + dv * dv <= rho * rho;
            }
        }
        return false;
    }

    private boolean solid(double u, double v, int y) {
        return u * u + v * v < WALL_R * WALL_R || inDisc(u, v, y) || inBody(u, v, y);
    }

    /** How far the spire's stone reaches from its centre at angle deg, over heights yLo..yHi. */
    private double extent(double deg, int yLo, int yHi) {
        double c = Math.cos(Math.toRadians(deg));
        double s = Math.sin(Math.toRadians(deg));
        double best = WALL_R;
        for (int y = yLo; y <= yHi; y++) {
            for (double r = BODY_REACH; r > best; r -= 0.5) {
                if (solid(r * c, r * s, y)) {
                    best = r;
                    break;
                }
            }
        }
        return best;
    }

    // ==========================================================================================
    // Building a column
    // ==========================================================================================

    /** Adds the spire's blocks to world column (x, z), over whatever the island put there. */
    void fill(int x, int z, LevelLayout.Column out) {
        int dx = x - cx;
        int dz = z - cz;
        if (Math.abs(dx) > REACH || Math.abs(dz) > REACH) {
            return;
        }
        int u = relU(dx, dz);
        int v = relV(dx, dz);
        double r = Math.hypot(u, v);
        BlockState[] pal = palette();
        if (r <= BODY_REACH) {
            for (int y = BASE; y < PLATFORM_Y; y++) {
                if (r < WALL_R) {
                    out.set(y, pal[STONE]); // the masonry core the stairs are cut into
                } else if (inDisc(u, v, y)) {
                    out.set(y, pal[JOINT]);
                } else if (inBody(u, v, y)) {
                    out.set(y, pal[SPIRE]);
                }
            }
            if (r < HALL_WALL_R) {
                shafts(u, v, r, out, pal);
            }
        }
        platform(u, v, r, out, pal);
        apply(decor.get(key(u, v)), out, pal);
        apply(route.get(key(u, v)), out, pal);
    }

    private static void apply(IntArrayList ops, LevelLayout.Column out, BlockState[] pal) {
        if (ops != null) {
            for (int i = 0; i < ops.size(); i++) {
                int op = ops.getInt(i);
                out.set(op >> 5, pal[op & 31]);
            }
        }
    }

    /** The hall, the stair shafts, the half-way room: cut into the core. */
    private void shafts(int u, int v, double r, LevelLayout.Column out, BlockState[] pal) {
        double deg = deg(u, v);
        for (Stair s : STAIRS) {
            if (r < WALL_R) {
                for (int y = s.bottom - 1; y <= s.top + 3; y++) {
                    out.set(y, pal[r < SHAFT_R && y >= s.bottom && y <= s.top + 2 ? AIR : STONE]);
                }
            }
        }
        // The hall at the foot: a round room the first stair rises out of.
        for (int y = BASE; y <= HALL_TOP + 1; y++) {
            if (r >= HALL_R) {
                out.set(y, pal[STONE]);
            } else if (y == BASE) {
                out.set(y, pal[PAVING]);
            } else if (y <= HALL_TOP || r < SHAFT_R) {
                out.set(y, pal[AIR]);
            } else {
                out.set(y, pal[STONE]);
            }
        }
        // The half-way room: a ring of floor round the stair, walls, a ceiling.
        if (r < ROOM_WALL_R) {
            for (int y = ROOM_FLOOR - 1; y <= ROOM_TOP + 1; y++) {
                if (r < SHAFT_R) {
                    continue; // the stair's shaft runs on through
                }
                if (r >= ROOM_R || y == ROOM_TOP + 1) {
                    out.set(y, pal[STONE]);
                } else {
                    out.set(y, pal[y == ROOM_FLOOR - 1 ? PAVING : AIR]);
                }
            }
        }
        for (Stair s : STAIRS) {
            if (r < NEWEL_R) {
                for (int y = s.bottom - 1; y <= s.top + 2; y++) {
                    out.set(y, pal[STONE]);
                }
                continue;
            }
            if (r >= SHAFT_R) {
                continue;
            }
            // The treads: a full block where the step is whole, a half step where it is half.
            for (int n = 0; n < s.revs(); n++) {
                double q2 = Math.round(s.surface(n, deg) * 2) / 2.0;
                if (q2 <= s.bottom) {
                    continue;
                }
                int b = (int) Math.floor(q2);
                if (q2 == b) {
                    out.set(b - 1, pal[PAVING]);
                    out.set(b - 2, pal[STONE]);
                } else {
                    out.set(b, pal[SLAB]);
                    out.set(b - 1, pal[STONE]);
                }
            }
            // Landings: the floor at the top leaves a hole over the last part of the climb.
            if (frac(deg - s.a0) < 0.6) {
                out.set(s.top - 1, pal[PAVING]);
            }
            if (s != A) {
                out.set(s.bottom - 1, pal[PAVING]);
            }
        }
    }

    /** The top platform, its broken parapet, and the spur reaching out over the void. */
    private void platform(int u, int v, double r, LevelLayout.Column out, BlockState[] pal) {
        double a = Math.atan2(v, u);
        double rim = 12.4 + 0.8 * Math.sin(5 * a + platformPhase);
        double sr = Math.toRadians(SPUR_DEG);
        double along = u * Math.cos(sr) + v * Math.sin(sr);
        double across = -u * Math.sin(sr) + v * Math.cos(sr);
        double tip = 32.5 - 1.5 * Hash.unit(seed, worldX(u, v), worldZ(u, v), 4967);
        boolean spur = along >= 9 && along <= tip && Math.abs(across) <= 1.6 - 0.6 * (along - 9) / (tip - 9);
        boolean on = r <= rim;
        if (!on && !spur) {
            return;
        }
        out.set(PLATFORM_Y, pal[PAVING]);
        if (on ? r <= rim - 1 : along < tip - 8) {
            out.set(PLATFORM_Y - 1, pal[STONE]);
        }
        if (on && r <= 11) {
            out.set(PLATFORM_Y - 2, pal[STONE]);
        }
        // A low broken wall round the edge: open where the ladder arrives and where the spur leaves.
        double d = deg(u, v);
        boolean open = Math.abs(Math.IEEEremainder(d - 270, 360)) < 14 || Math.abs(Math.IEEEremainder(d - SPUR_DEG, 360)) < 16;
        if (on && !spur && !open && r > rim - 1.2 && Hash.unit(seed, worldX(u, v), worldZ(u, v), 4966) < 0.55) {
            out.set(PLATFORM_Y + 1, pal[STONE]);
        }
    }

    // ==========================================================================================
    // Ribs, floating chunks, iron (worked out once per site)
    // ==========================================================================================

    private void buildDecor() {
        Long2IntOpenHashMap voxels = new Long2IntOpenHashMap();
        // Ribs: out of the body, curling round and down, thinning to a point.
        for (int level : RIB_LEVELS) {
            for (int i = 0; i < 4; i++) {
                long h = Hash.of(seed, cx + level, cz + i, 4962);
                double a0 = Math.toRadians(45 + 90 * i + (Hash.unit(h) - 0.5) * 24);
                double sweep = Math.toRadians(14 + 16 * Hash.unit(Hash.mix(h))) * (((h >>> 8) & 1) == 0 ? 1 : -1);
                double len = 12 + 6 * Hash.unit(Hash.mix(h + 2));
                double drop = 10 + 8 * Hash.unit(Hash.mix(h + 3));
                double root = extent(Math.toDegrees(a0), level - 2, level + 2) - 1.5;
                float[][] line = new float[11][];
                for (int step = 0; step <= 50; step++) {
                    double t = step / 50.0;
                    double a = a0 + sweep * t;
                    double rr = root + len * Math.sin(t * Math.PI * 0.6);
                    double u = rr * Math.cos(a);
                    double v = rr * Math.sin(a);
                    double y = level - drop * Math.pow(t, 1.6);
                    double th = 2.3 - 1.3 * t;
                    sphere(voxels, u, y, v, th, SPIRE);
                    if (step % 5 == 0) {
                        line[step / 5] = new float[] {(float) (cx + 0.5 + worldDx(u, v)), (float) y, (float) (cz + 0.5 + worldDz(u, v)), (float) th};
                    }
                }
                ribLines.add(line);
            }
        }
        // Broken chunks of it floating round it, clear of the ladders' axes, the chains and the leap.
        double[][] bands = {{60, 78}, {102, 120}, {150, 168}, {192, 210}, {240, 258}, {282, 300}, {330, 348}};
        for (int i = 0; i < 9; i++) {
            long h = Hash.of(seed, cx + 977 * i, cz, 4963);
            double[] band = bands[Hash.range(h, bands.length)];
            double a = Math.toRadians(band[0] + (band[1] - band[0]) * Hash.unit(Hash.mix(h)));
            double rr = 26 + 6 * Hash.unit(Hash.mix(h + 1));
            double y = 232 + 188 * Hash.unit(Hash.mix(h + 2));
            double s = 2.5 + 3 * Hash.unit(Hash.mix(h + 3));
            chunk(voxels, rr * Math.cos(a), y, rr * Math.sin(a), s, h);
        }
        // The iron band round the upper body ...
        double[] bandExt = new double[360];
        for (int d = 0; d < 360; d++) {
            bandExt[d] = extent(d, BAND_Y - 2, BAND_Y + 2);
        }
        for (int u = -21; u <= 21; u++) {
            for (int v = -21; v <= 21; v++) {
                double r = Math.hypot(u, v);
                double e = bandExt[(int) Math.round(deg(u, v)) % 360];
                if (r >= e - 0.5 && r <= e + 1.2) {
                    for (int y = BAND_Y - 2; y <= BAND_Y + 2; y++) {
                        voxels.put(vkey(u, y, v), IRON);
                    }
                }
            }
        }
        // ... bolted by beams to the colossal chains, which wear collars there and near the top;
        // and from the top platform four chains run up to them.
        for (int i = 0; i < 4; i++) {
            double da = Math.toRadians(45 + 90 * i);
            double ca = Math.cos(da);
            double sa = Math.sin(da);
            double[] axis = chainAxis(i);
            double r0 = bandExt[45 + 90 * i] - 1;
            double r1 = Math.hypot(axis[0], axis[1]) - 12.5;
            for (double t = r0; t <= r1; t += 0.4) {
                sphere(voxels, t * ca, BAND_Y, t * sa, 1.6, IRON);
            }
            for (int u = (int) axis[0] - 15; u <= (int) axis[0] + 15; u++) {
                for (int v = (int) axis[1] - 15; v <= (int) axis[1] + 15; v++) {
                    double rc = Math.hypot(u - axis[0], v - axis[1]);
                    if (rc >= 11.5 && rc <= 13.6) {
                        for (int y = -2; y <= 2; y++) {
                            voxels.put(vkey(u, BAND_Y + y, v), IRON);
                            voxels.put(vkey(u, UPPER_COLLAR_Y + y, v), IRON);
                        }
                    }
                }
            }
            double[] p0 = {11 * ca, TOP + 2.5, 11 * sa};
            double[] p1 = {r1 * ca, UPPER_COLLAR_Y, r1 * sa};
            links(voxels, p0, p1, 9.0, 5.0, 1.1);
            int au = (int) Math.round(11 * ca);
            int av = (int) Math.round(11 * sa);
            voxels.put(vkey(au, TOP, av), IRON);
            voxels.put(vkey(au, TOP + 1, av), IRON);
        }
        for (Long2IntOpenHashMap.Entry e : voxels.long2IntEntrySet()) {
            long k = e.getLongKey();
            int u = (int) ((k << 4) >> 44);
            int v = (int) ((k << 32) >> 44);
            int y = (int) (k & 0xfff);
            if (u * u + v * v >= WALL_R * WALL_R) {
                decor.computeIfAbsent(key(u, v), c -> new IntArrayList()).add(y << 5 | e.getIntValue());
            }
        }
    }

    /** Axis (u, v) of the site's colossal chain on diagonal i, in this frame (they hang at block corners). */
    private double[] chainAxis(int i) {
        // World offsets of the axis from the centre of block (cx, cz), then into this frame.
        double wx = ((((i + q) & 3) == 0 || ((i + q) & 3) == 3) ? CHAIN_OFFSET : -CHAIN_OFFSET) - 0.5;
        double wz = ((((i + q) & 3) == 0 || ((i + q) & 3) == 1) ? CHAIN_OFFSET : -CHAIN_OFFSET) - 0.5;
        return switch (q) {
            case 0 -> new double[] {wx, wz};
            case 1 -> new double[] {wz, -wx};
            case 2 -> new double[] {-wx, -wz};
            default -> new double[] {-wz, wx};
        };
    }

    private static long vkey(int u, int y, int v) {
        return ((long) (u & 0xfffff) << 40) | ((long) (v & 0xfffff) << 12) | (y & 0xfff);
    }

    private static void sphere(Long2IntOpenHashMap voxels, double u, double y, double v, double rad, int block) {
        for (int cu = (int) Math.floor(u - rad); cu <= (int) Math.ceil(u + rad); cu++) {
            for (int cv = (int) Math.floor(v - rad); cv <= (int) Math.ceil(v + rad); cv++) {
                for (int cy = (int) Math.floor(y - rad); cy <= (int) Math.ceil(y + rad); cy++) {
                    double du = cu - u;
                    double dv = cv - v;
                    double dy = cy + 0.5 - y;
                    if (du * du + dv * dv + dy * dy <= rad * rad) {
                        voxels.put(vkey(cu, cy, cv), block);
                    }
                }
            }
        }
    }

    /** A lump of the spire broken off and floating: flattened, with a ragged outline. */
    private void chunk(Long2IntOpenHashMap voxels, double u, double y, double v, double s, long h) {
        for (int cu = (int) Math.floor(u - s - 1); cu <= (int) Math.ceil(u + s + 1); cu++) {
            for (int cv = (int) Math.floor(v - s - 1); cv <= (int) Math.ceil(v + s + 1); cv++) {
                for (int cy = (int) Math.floor(y - s); cy <= (int) Math.ceil(y + s); cy++) {
                    double du = (cu - u) / s;
                    double dv = (cv - v) / (s * 0.85);
                    double dy = (cy + 0.5 - y) / (s * 0.7);
                    double ragged = 0.25 * (Hash.unit(Hash.of(h, cu, cv * 4096 + cy, 4968)) - 0.5);
                    if (du * du + dv * dv + dy * dy <= 1 + ragged) {
                        voxels.put(vkey(cu, cy, cv), dy > 0.55 ? JOINT : SPIRE);
                    }
                }
            }
        }
    }

    /**
     * A chain of block links from p0 to p1 {u, y, v}: links {@code len} long and {@code wid} wide,
     * bar radius {@code bar}, every other one turned a quarter so each hooks through the next
     * (the same shape as Level490Layout's colossal links, along any direction).
     */
    private static void links(Long2IntOpenHashMap voxels, double[] p0, double[] p1, double len, double wid, double bar) {
        double[] d = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]};
        double total = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d[0] /= total;
        d[1] /= total;
        d[2] /= total;
        double hn = Math.hypot(d[0], d[2]);
        double[] e1 = {-d[2] / hn, 0, d[0] / hn};
        double[] e2 = {d[1] * e1[2] - d[2] * e1[1], d[2] * e1[0] - d[0] * e1[2], d[0] * e1[1] - d[1] * e1[0]};
        double arc = wid / 2 - bar;
        double straight = len / 2 - bar - arc;
        double pitch = 0.65 * len;
        int count = (int) Math.ceil((total - len) / pitch) + 1;
        for (int k = 0; k < count; k++) {
            double s = Math.min(len / 2 + k * pitch, total - len / 2);
            double[] c = {p0[0] + d[0] * s, p0[1] + d[1] * s, p0[2] + d[2] * s};
            double[] across = (k & 1) == 0 ? e1 : e2;
            double[] out = (k & 1) == 0 ? e2 : e1;
            int reach = (int) Math.ceil(len / 2 + 1);
            for (int cu = (int) c[0] - reach; cu <= (int) c[0] + reach; cu++) {
                for (int cv = (int) c[2] - reach; cv <= (int) c[2] + reach; cv++) {
                    for (int cy = (int) c[1] - reach; cy <= (int) c[1] + reach; cy++) {
                        double lu = cu - c[0];
                        double ly = cy + 0.5 - c[1];
                        double lv = cv - c[2];
                        double w = lu * out[0] + ly * out[1] + lv * out[2];
                        if (Math.abs(w) > bar) {
                            continue;
                        }
                        double along = Math.abs(lu * d[0] + ly * d[1] + lv * d[2]);
                        double ac = lu * across[0] + ly * across[1] + lv * across[2];
                        double inPlane = along <= straight ? Math.abs(Math.abs(ac) - arc)
                                : Math.abs(Math.hypot(ac, along - straight) - arc);
                        if (inPlane * inPlane + w * w <= bar * bar) {
                            voxels.put(vkey(cu, cy, cv), IRON);
                        }
                    }
                }
            }
        }
    }

    // ==========================================================================================
    // The way up: doors, windows, lanterns, ledges, piers, ladders (worked out once per site)
    // ==========================================================================================

    private void op(int u, int v, int y0, int y1, int block) {
        IntArrayList list = route.computeIfAbsent(key(u, v), k -> new IntArrayList());
        for (int y = y0; y <= y1; y++) {
            list.add(y << 5 | block);
        }
    }

    /** A cell on the axis at angle {@code deg} (a multiple of 90), k out, p to the side. */
    private static int[] onAxis(int deg, int k, int p) {
        return switch (deg) {
            case 0 -> new int[] {k, p};
            case 90 -> new int[] {-p, k};
            case 180 -> new int[] {-k, -p};
            default -> new int[] {p, -k};
        };
    }

    private void buildRoute() {
        // The entrance, toward the bridge you came in by: 3 wide, a round-topped arch.
        for (int k = 8; k <= 22; k++) {
            for (int p = -1; p <= 1; p++) {
                op(k, p, BASE, BASE, PAVING);
                op(k, p, BASE + 1, BASE + (p == 0 ? 6 : 5), AIR);
            }
        }
        for (int[] c : new int[][] {{5, 5}, {-5, 5}, {-5, -5}, {5, -5}}) {
            op(c[0], c[1], BASE + 1, BASE + 1, LANTERN);
        }
        // Lanterns in niches in the stairwells' walls, two a turn; a closet-like alcove each turn of
        // B-C; windows two a turn in A and D.
        for (Stair s : STAIRS) {
            int[][] niches = {wallCell(s.a0 + 0.3 * 360, 5.5, 6.6), wallCell(s.a0 + 0.8 * 360, 5.5, 6.6)};
            for (int n = 0; n < s.revs(); n++) {
                for (int[] c : niches) {
                    int y = (int) Math.round(s.surface(n, deg(c[0], c[1]))) + 1;
                    if (y > HALL_TOP + 1 && (y < ROOM_FLOOR - 1 || y > ROOM_TOP + 1)) {
                        op(c[0], c[1], y, y, LANTERN);
                        op(c[0], c[1], y + 1, y + 1, AIR);
                    }
                }
                if (s == BC) {
                    int y = (int) Math.round(s.surface(n, deg(ALCOVE_1[0], ALCOVE_1[1])));
                    if (y < ROOM_FLOOR - 1 || y > ROOM_TOP + 1) {
                        op(ALCOVE_1[0], ALCOVE_1[1], y, y + 1, AIR);
                        op(ALCOVE_2[0], ALCOVE_2[1], y, y + 1, AIR);
                    }
                }
                if (s.windows) {
                    for (double wa : new double[] {s.a0 + 45, s.a0 + 225}) {
                        int y = (int) Math.round(s.surface(n, wa)) + 1;
                        if (y > HALL_TOP + 1) {
                            ray(mod360(wa), SHAFT_R - 0.3, extent(mod360(wa), y, y + 1) + 1.5, 0.75, y, y + 1);
                        }
                    }
                }
            }
        }
        // The half-way room: a radio facing in (the checkpoint), lanterns, two big windows.
        op(8, 0, ROOM_FLOOR, ROOM_FLOOR, RADIO + worldDir(180));
        for (int[] c : new int[][] {{0, 8}, {-8, 0}, {0, -8}}) {
            op(c[0], c[1], ROOM_FLOOR, ROOM_FLOOR, LANTERN);
        }
        for (double wa : new double[] {45, 225}) {
            ray(wa, ROOM_R - 0.5, extent(wa, ROOM_FLOOR + 1, ROOM_FLOOR + 4) + 1.5, 1.3, ROOM_FLOOR + 1, ROOM_FLOOR + 4);
        }
        for (int i = 0; i < LEDGES.length; i++) {
            ledge(LEDGES[i], i);
        }
    }

    /** Opens the cells within {@code half} of the ray at angle deg, from radius r0 to r1, heights y0..y1. */
    private void ray(double deg, double r0, double r1, double half, int y0, int y1) {
        double c = Math.cos(Math.toRadians(deg));
        double s = Math.sin(Math.toRadians(deg));
        int reach = (int) Math.ceil(r1) + 1;
        for (int u = -reach; u <= reach; u++) {
            for (int v = -reach; v <= reach; v++) {
                double along = u * c + v * s;
                double across = -u * s + v * c;
                if (along >= r0 && along <= r1 && Math.abs(across) <= half) {
                    op(u, v, y0, y1, AIR);
                }
            }
        }
    }

    /**
     * Ledge {@code i}: the door out of the stair below, the ledge round the outside, a landing, the
     * pier and ladder up, and the door into the next stair (or the top platform).
     */
    private void ledge(Ledge l, int i) {
        int f = l.feet;
        double[] ext = new double[360];
        for (int d = 0; d < 360; d++) {
            ext[d] = extent(d, f, f + 3);
        }
        int axis = l.ladder();
        int top = l.ladderTop;
        int rL = (int) Math.ceil(Math.max(extent(axis, f - 1, Math.min(top + 2, PLATFORM_Y - 1)), ext[axis])) + 1;
        // The ledge: a band of joint stone outside the body (headroom above it), the outer edge crumbled.
        double gapAt = l.sweep * 0.5;
        for (int u = -24; u <= 24; u++) {
            for (int v = -24; v <= 24; v++) {
                double r = Math.hypot(u, v);
                double d = deg(u, v);
                double e = ext[(int) Math.round(d) % 360];
                double along = mod360(d - l.from);
                boolean in = along <= l.sweep + 4 || along >= 356;
                if (!in || r <= e || r > e + l.width + 0.5) {
                    continue;
                }
                if (l.width == 1 && Math.abs(along - gapAt) <= 0.5 * Math.toDegrees(1.0 / (e + 1))) {
                    continue; // the gap you have to step over
                }
                boolean outer = r > e + l.width - 0.5;
                if (l.width >= 2 && outer && Hash.unit(seed, worldX(u, v), worldZ(u, v), 4969 + i) < 0.15) {
                    continue;
                }
                op(u, v, f - 1, f - 1, JOINT);
                if (!outer) {
                    op(u, v, f - 2, f - 2, JOINT);
                }
                op(u, v, f, f + 2, AIR);
            }
        }
        // The landing at the foot of the ladder, round the pier (and under the ladder's first rung).
        for (int k = (int) Math.floor(ext[axis]); k <= rL + 1; k++) {
            for (int p = -2; p <= 2; p++) {
                if (Math.abs(p) <= 1 && k <= rL - 1) {
                    continue; // the pier
                }
                int[] c = onAxis(axis, k, p);
                op(c[0], c[1], f - 1, f - 1, JOINT);
                if (k != rL || p != 0) {
                    op(c[0], c[1], f, f + 2, AIR);
                }
            }
        }
        // The pier the ladder is bolted to, out from the core.
        for (int k = 7; k <= rL - 1; k++) {
            for (int p = -1; p <= 1; p++) {
                int[] c = onAxis(axis, k, p);
                op(c[0], c[1], f - 2, top, JOINT);
            }
        }
        // The door out of the stair below onto the ledge.
        for (int k = 5; k <= (int) Math.ceil(ext[l.from]) + 1; k++) {
            int[] c = onAxis(l.from, k, 0);
            op(c[0], c[1], f - 1, f - 1, PAVING);
            op(c[0], c[1], f, f + 2, AIR);
        }
        // The ladder, room to climb in front of it and to step off at its top.
        int[] lc = onAxis(axis, rL, 0);
        int[] front = onAxis(axis, rL + 1, 0);
        op(front[0], front[1], f, top + 2, AIR);
        op(lc[0], lc[1], f, top, LADDER + worldDir(axis));
        op(lc[0], lc[1], top + 1, top + 2, AIR);
        if (top < PLATFORM_Y) {
            // Into the next stair through a narrow door at the ladder's top.
            for (int k = 5; k <= rL - 1; k++) {
                int[] c = onAxis(axis, k, 0);
                op(c[0], c[1], top, top, PAVING);
                op(c[0], c[1], top + 1, top + 3, AIR);
            }
        } else {
            for (int p = -1; p <= 1; p++) {
                int[] c = onAxis(axis, rL - 1, p);
                op(c[0], c[1], TOP, TOP + 1, AIR);
            }
        }
    }

    // ==========================================================================================
    // The looping stretches (world/Level490Loops shows these to the one player going round)
    // ==========================================================================================

    private BlockPos at(int[] cell, int y) {
        return new BlockPos(worldX(cell[0], cell[1]), y, worldZ(cell[0], cell[1]));
    }

    /** Height of the step beside cell c on turn n of stair B-C (rounded to a whole block). */
    private static int step(int[] c, int n) {
        return (int) Math.round(BC.surface(n, deg(c[0], c[1])));
    }

    /**
     * What a player who has gone round loop {@code loop} {@code lap} times sees changed (all three
     * of its turns alike, so the join still doesn't show). The first loop goes dark and someone
     * starts counting; in the second something keeps leaving lanterns and shackles out for you.
     */
    public Map<BlockPos, BlockState> loopView(int loop, int lap) {
        Map<BlockPos, BlockState> view = new LinkedHashMap<>();
        BlockState stone = Level490Blocks.STONE.get().defaultBlockState();
        BlockState unlit = Level490Blocks.LANTERN.get().defaultBlockState().setValue(IronLanternBlock.LIT, false);
        BlockState lit = Level490Blocks.LANTERN.get().defaultBlockState().setValue(IronLanternBlock.LIT, true);
        BlockState words = Level490Blocks.WORDS.get().defaultBlockState();
        BlockState chain = Level490Blocks.CHAIN.get().defaultBlockState();
        int first = (LOOPS[loop].bottom - BC.bottom) / RISE;
        for (int c = 0; c < 3; c++) {
            int n = first + c;
            int marks = loop == 0 ? lap : lap - 1;
            if (lap >= 6) {
                // Every stone at chest height scratched full of tallies.
                for (int u = -7; u <= 7; u++) {
                    for (int v = -7; v <= 7; v++) {
                        double r = Math.hypot(u, v);
                        int[] cell = {u, v};
                        if (r >= 5.5 && r < 6.6 && !same(cell, NICHE_A) && !same(cell, NICHE_B) && !same(cell, ALCOVE_1)
                                && !same(cell, MARKS) && !same(cell, WORDS_1) && !same(cell, WORDS_2)) {
                            view.put(at(cell, step(cell, n) + 1), marked(6));
                        }
                    }
                }
            }
            if (loop == 0) {
                if (lap >= 1 && lap < 6) {
                    view.put(at(NICHE_A, step(NICHE_A, n) + 1), unlit);
                }
                if (lap >= 2) {
                    int y = step(ALCOVE_1, n);
                    for (int dy = 0; dy <= 1; dy++) {
                        view.put(at(ALCOVE_1, y + dy), stone);
                        view.put(at(ALCOVE_2, y + dy), stone);
                    }
                }
                if (lap >= 4) {
                    view.put(at(NICHE_B, step(NICHE_B, n) + 1), unlit);
                }
            } else {
                if (lap >= 1) {
                    view.put(at(ALCOVE_1, step(ALCOVE_1, n)), lit);
                }
                if (lap >= 2) {
                    view.put(at(NICHE_A, step(NICHE_A, n) + 1), unlit);
                    view.put(at(NICHE_B, step(NICHE_B, n) + 1), unlit);
                }
                if (lap >= 3) {
                    int y = step(ALCOVE_2, n);
                    view.put(at(ALCOVE_2, y), chain);
                    view.put(at(ALCOVE_2, y + 1), chain.setValue(GiantChainBlock.TWIST, true));
                }
            }
            if (marks >= 1) {
                view.put(at(MARKS, step(MARKS, n) + 1), marked(Math.min(marks, 5)));
            }
            if (lap >= (loop == 0 ? 3 : 4)) {
                for (int[] t : new int[][] {SHACKLE_1, SHACKLE_2}) {
                    view.put(at(t, step(t, n)), Level490Blocks.SHACKLE.get().defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, DIRS[(n + t[0]) & 3]));
                }
            }
            if (lap >= 5) {
                view.put(at(WORDS_1, step(WORDS_1, n) + 1), words);
            }
            if (lap >= 6 && loop == 1) {
                view.put(at(WORDS_2, step(WORDS_2, n) + 1), words);
            }
        }
        return view;
    }

    /** Every block any lap of loop {@code loop} changes (to put back how it really is). */
    public Set<BlockPos> loopCells(int loop) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int lap = 1; lap <= 6; lap++) {
            cells.addAll(loopView(loop, lap).keySet());
        }
        return cells;
    }

    private static boolean same(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    private static BlockState marked(int marks) {
        return Level490Blocks.MARKED_STONE.get().defaultBlockState().setValue(MarkedStoneBlock.MARKS, marks);
    }

    /** True if world column (x, z) is inside the spire's hall, shafts or room (indoors). */
    public boolean inside(double x, double y, double z) {
        double r = Math.hypot(x - cx - 0.5, z - cz - 0.5);
        return r < HALL_WALL_R && y > BASE && y < D.top + 4;
    }
}
