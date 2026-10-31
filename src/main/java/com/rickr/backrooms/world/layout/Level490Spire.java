package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.GiantChainBlock;
import com.rickr.backrooms.block.IronLanternBlock;
import com.rickr.backrooms.block.MarkedStoneBlock;
import com.rickr.backrooms.block.StatueBlock;
import com.rickr.backrooms.registry.Level490Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The spire on each of Level 490's sites (stage 2, docs/level-490-penance.md): a tower of dark
 * Gothic stone about 275 blocks tall, stacked like a spine, its ribs pale bone; turrets cling to it
 * (pointed roofs, or broken off with red trees growing out), flying buttresses arc between them,
 * candlelit windows and a rose window glow in it, pinnacles stand round its joints. Each vertebra is turned, shifted
 * and tilted a little from the one below, with a darker disc where two meet, so its outline is
 * different from every side. Ribs curl out of it, broken chunks of it float around it, an iron band
 * round its upper body is bolted by beams to the site's four colossal chains, and four more chains
 * run from its top platform up to them: it hangs in the chains.
 *
 * <pre>
 *   y 505-508    the iron collar on a colossal chain: step off = the leap out
 *   y 476-507    a broken bridge from the top to that chain, a ladder welded up it
 *   y 476        the crumbled top, a radio (checkpoint)
 *   y 357-476    flight 2: the broken stair round the outside, out of a doorway to the top
 *   y 317-347    the LOOP: the stair repeats until you go back down (world/Level490Loops)
 *   y 307-357    the inside stair, from the half-way room up
 *   y 307-313    the half-way room with a radio (checkpoint)
 *   y 201-307    flight 1: the broken stair round the outside, from the island to the room
 *   y 200        the great island: the hall (a shrine) and, beside its door, the first step
 * </pre>
 *
 * <p>Everything is laid out in the spire's own frame: u points from its centre (the middle of
 * block (cx, cz)) toward the bridge you arrive by, v a quarter turn on, angles in degrees from +u
 * toward +v. The climb (after the play-test: "a spiralling stair on the outside, messy, chaotic")
 * is two flights of a broken stair winding round the outside ({@link Flight}), hugging the body
 * wherever it bulges, cutting through the turrets and ribs it meets; between them, inside, the
 * half-way room and a spiral stair 4 wide round a 3 x 3 newel rising {@value #RISE} blocks a turn
 * (one turn the same as the next, which is what lets a stretch of it repeat unseen).
 *
 * <p>Built per column like the rest of the level: the vertebrae and the shafts are worked out
 * directly; ribs, chunks, iron and the way up (the outside stair, doorways, windows, lanterns,
 * the final climb) are worked out once per site into per-column lists. The way up goes last, so
 * nothing ever blocks it.
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
    /** Farthest the vertebrae reach. */
    private static final double BODY_REACH = 20.0;
    /** Where the vertebrae meet. */
    private static final int[] JOINTS = {200, 228, 251, 279, 307, 332, 357, 384, 404, 425, 450, PLATFORM_Y};
    /** Ribs curl out of the spire at these heights, four each, near the diagonals. */
    private static final int[] RIB_LEVELS = {236, 290, 340, 392};
    /** The iron band round the upper body, and the collars on the colossal chains. */
    private static final int BAND_Y = 433;
    private static final int UPPER_COLLAR_Y = 505;
    /**
     * Where you stand on the colossal chain's upper collar after the last climb (the top of the
     * rungs welded to the chain): stepping off from up here is the leap out.
     */
    public static final int LEAP_Y = UPPER_COLLAR_Y + 3;
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

    /** The inside stair, from the half-way room up. */
    private static final Stair BC = new Stair(307, 357, 210, false);
    private static final Stair[] STAIRS = {BC};

    /**
     * A flight of the outside stair: from feet height {@code y0} at angle {@code deg0} round the
     * outside (angles growing) up to {@code y1}, rising {@code rise} blocks a turn.
     */
    private record Flight(int y0, int y1, double deg0, double rise) {
        double sweep() {
            return (y1 - y0) / rise * 360.0;
        }

        double degEnd() {
            return mod360(deg0 + sweep());
        }

        double y(double s) {
            return y0 + s / 360.0 * rise;
        }
    }

    /**
     * Flight 1 from the island to the half-way room, flight 2 from the inside stair's top to the top.
     * 34 blocks a turn: hugging a body 11 or more blocks across, that is under half a block of rise
     * per block walked, so every step is a half step (none to jump).
     */
    private static final Flight F1 = new Flight(201, ROOM_FLOOR, 15, 34);
    private static final Flight F2 = new Flight(357, TOP, 300, 34);
    private static final Flight[] FLIGHTS = {F1, F2};
    /** The outside stair is drawn every this many degrees round its sweep. */
    private static final double STEP_DEG = 0.25;
    private static final double STAIR_WIDTH = 3.0;

    /**
     * A looping stretch of stair B-C: three identical turns from {@code bottom}. Climbing past
     * {@code trigger} (in the third) moves you down one turn; once you have gone round, walking back
     * below {@code release} breaks the loop. See world/Level490Loops.
     */
    public record Loop(int bottom, int trigger, double release) {}

    public static final Loop[] LOOPS = {new Loop(317, 339, 315.0)};

    // ---- the blocks, by index (so a column's list is plain ints) ----
    private static final int AIR = 0;
    private static final int SPIRE = 1;
    private static final int JOINT = 2;
    private static final int POST = 3;
    private static final int PAVING = 4;
    private static final int IRON = 5;
    private static final int SLAB = 6;
    private static final int LANTERN = 7;
    /** + world direction index (0 east, 1 south, 2 west, 3 north). */
    private static final int LADDER = 8;
    private static final int RADIO = 12;
    private static final int GOTHIC = 16;
    private static final int CARVED = 17;
    private static final int WIN_LIT = 18;
    private static final int WIN_DARK = 19;
    /** + world direction index; the upper half at + 4. */
    private static final int STATUE = 20;
    private static final int CANDLE = 28;
    private static final int RUBBLE = 29;
    private static final int WOOD = 30;
    private static final int LEAVES = 31;
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
    /** The colossal chain of the last climb (its axis in this frame), the column the rungs are welded to, the bridge's heading. */
    private final double[] finalChain;
    private final int rungU;
    private final int rungV;
    private final double bridgeDeg;
    private volatile float[][] outline;
    private volatile boolean built;
    private volatile java.util.List<double[][]> ribPaths;
    private volatile java.util.List<float[][]> ribLines;
    /** Per column (key(u, v)): packed (y << 5 | block) to set, in order. */
    private final Long2ObjectOpenHashMap<IntArrayList> decor = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<IntArrayList> route = new Long2ObjectOpenHashMap<>();

    /** The spire of the site centred on block (cx, cz), its entrance facing quarter {@code q}. */
    static Level490Spire of(long seed, int cx, int cz, int q) {
        long key = Hash.of(seed, cx, cz, 4960);
        Level490Spire spire = CACHE.get(key);
        if (spire == null || spire.seed != seed || spire.cx != cx || spire.cz != cz) {
            if (CACHE.size() > 128) {
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
        // The last climb: up the colossal chain on the diagonal at 135 degrees in this frame.
        double[] best = null;
        for (int i = 0; i < 4; i++) {
            double[] c = chainAxis(i);
            if (c[0] < 0 && c[1] > 0) {
                best = c;
            }
        }
        finalChain = best;
        rungU = (int) Math.round(best[0]) + 15;
        rungV = (int) Math.round(best[1]);
        bridgeDeg = deg(rungU + 3, rungV);
    }

    /**
     * The blocks are only worked out when a column of this spire is first built (server side): the
     * client only ever needs a spire's outline and ribs for drawing it far off, which are cheap.
     */
    private synchronized void build() {
        if (!built) {
            buildDecor();
            buildRoute();
            built = true;
        }
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
            p = new BlockState[32];
            p[AIR] = Blocks.AIR.defaultBlockState();
            p[SPIRE] = Level490Blocks.SPIRE.get().defaultBlockState();
            p[JOINT] = Level490Blocks.SPIRE_JOINT.get().defaultBlockState();
            p[POST] = Level490Blocks.POST.get().defaultBlockState();
            p[PAVING] = Level490Blocks.PAVING.get().defaultBlockState();
            p[IRON] = Level490Blocks.CHAIN_IRON.get().defaultBlockState();
            p[SLAB] = Level490Blocks.PAVING_SLAB.get().defaultBlockState();
            p[LANTERN] = Level490Blocks.LANTERN.get().defaultBlockState().setValue(IronLanternBlock.LIT, true);
            for (int i = 0; i < 4; i++) {
                p[LADDER + i] = Level490Blocks.LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, DIRS[i]);
                p[RADIO + i] = ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, DIRS[i]);
                BlockState statue = Level490Blocks.STATUE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, DIRS[i]);
                p[STATUE + i] = statue.setValue(StatueBlock.HALF, DoubleBlockHalf.LOWER);
                p[STATUE + 4 + i] = statue.setValue(StatueBlock.HALF, DoubleBlockHalf.UPPER);
            }
            p[GOTHIC] = Level490Blocks.GOTHIC.get().defaultBlockState();
            p[CARVED] = Level490Blocks.GOTHIC_CARVED.get().defaultBlockState();
            p[WIN_LIT] = Level490Blocks.WINDOW_LIT.get().defaultBlockState();
            p[WIN_DARK] = Level490Blocks.WINDOW_DARK.get().defaultBlockState();
            p[CANDLE] = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
            p[RUBBLE] = Level490Blocks.RUBBLE.get().defaultBlockState();
            p[WOOD] = Level490Blocks.DEAD_WOOD.get().defaultBlockState();
            p[LEAVES] = Level490Blocks.RED_LEAVES.get().defaultBlockState();
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

    /**
     * Each rib's centre line in this spire's frame: {u, y, v, radius} points from its root in the
     * body, curling round and down, thinning to a point.
     */
    private java.util.List<double[][]> ribPaths() {
        java.util.List<double[][]> paths = ribPaths;
        if (paths == null) {
            paths = new java.util.ArrayList<>();
            for (int level : RIB_LEVELS) {
                for (int i = 0; i < 4; i++) {
                    long h = Hash.of(seed, cx + level, cz + i, 4962);
                    double a0 = Math.toRadians(45 + 90 * i + (Hash.unit(h) - 0.5) * 24);
                    double sweep = Math.toRadians(14 + 16 * Hash.unit(Hash.mix(h))) * (((h >>> 8) & 1) == 0 ? 1 : -1);
                    double len = 12 + 6 * Hash.unit(Hash.mix(h + 2));
                    double drop = 10 + 8 * Hash.unit(Hash.mix(h + 3));
                    double root = extent(Math.toDegrees(a0), level - 2, level + 2) - 1.5;
                    double[][] path = new double[51][];
                    for (int step = 0; step <= 50; step++) {
                        double t = step / 50.0;
                        double a = a0 + sweep * t;
                        double rr = root + len * Math.sin(t * Math.PI * 0.6);
                        path[step] = new double[] {rr * Math.cos(a), level - drop * Math.pow(t, 1.6), rr * Math.sin(a), 2.3 - 1.3 * t};
                    }
                    paths.add(path);
                }
            }
            ribPaths = paths;
        }
        return paths;
    }

    /** Each rib's centre line, {x, y, z, radius} points in world coordinates (every fifth point). */
    public java.util.List<float[][]> ribLines() {
        java.util.List<float[][]> lines = ribLines;
        if (lines == null) {
            lines = new java.util.ArrayList<>();
            for (double[][] path : ribPaths()) {
                float[][] line = new float[path.length / 5 + 1][];
                for (int k = 0; k < path.length; k += 5) {
                    double[] p = path[k];
                    line[k / 5] = new float[] {(float) (cx + 0.5 + worldDx(p[0], p[2])), (float) p[1],
                            (float) (cz + 0.5 + worldDz(p[0], p[2])), (float) p[3]};
                }
                lines.add(line);
            }
            ribLines = lines;
        }
        return lines;
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

    /** True if (u, v, y) is in the darker disc where two vertebrae meet. */
    private boolean inDisc(double u, double v, int y) {
        for (int k = 1; k < JOINTS.length - 1; k++) {
            int j = JOINTS[k];
            if (y == j || y == j - 1) {
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
        if (!built) {
            build();
        }
        int u = relU(dx, dz);
        int v = relV(dx, dz);
        double r = Math.hypot(u, v);
        BlockState[] pal = palette();
        if (r <= BODY_REACH) {
            for (int y = BASE; y < PLATFORM_Y; y++) {
                if (r < WALL_R) {
                    out.set(y, pal[GOTHIC]); // the masonry core the stairs are cut into
                } else if (inDisc(u, v, y)) {
                    out.set(y, pal[CARVED]);
                } else if (inBody(u, v, y)) {
                    out.set(y, pal[GOTHIC]);
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
                    out.set(y, pal[r < SHAFT_R && y >= s.bottom && y <= s.top + 2 ? AIR : GOTHIC]);
                }
            }
        }
        // The hall at the foot: a round room the first stair rises out of.
        for (int y = BASE; y <= HALL_TOP + 1; y++) {
            if (r >= HALL_R) {
                out.set(y, pal[GOTHIC]);
            } else if (y == BASE) {
                out.set(y, pal[PAVING]);
            } else if (y <= HALL_TOP) {
                out.set(y, pal[AIR]);
            } else {
                out.set(y, pal[GOTHIC]);
            }
        }
        // The half-way room: a ring of floor round the stair, walls, a ceiling.
        if (r < ROOM_WALL_R) {
            for (int y = ROOM_FLOOR - 1; y <= ROOM_TOP + 1; y++) {
                if (r < SHAFT_R) {
                    continue; // the stair's shaft runs on through
                }
                if (r >= ROOM_R || y == ROOM_TOP + 1) {
                    out.set(y, pal[GOTHIC]);
                } else {
                    out.set(y, pal[y == ROOM_FLOOR - 1 ? PAVING : AIR]);
                }
            }
        }
        for (Stair s : STAIRS) {
            if (r < NEWEL_R) {
                for (int y = s.bottom - 1; y <= s.top + 2; y++) {
                    out.set(y, pal[GOTHIC]);
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
                    out.set(b - 2, pal[GOTHIC]);
                } else {
                    out.set(b, pal[SLAB]);
                    out.set(b - 1, pal[GOTHIC]);
                }
            }
            // Landings: the floor at the top leaves a hole over the last part of the climb.
            if (frac(deg - s.a0) < 0.6) {
                out.set(s.top - 1, pal[PAVING]);
            }
            out.set(s.bottom - 1, pal[PAVING]);
        }
    }

    /**
     * The top: the spire has crumbled away to a broken floor, rubble on it, the stumps of a parapet
     * and of pinnacles round the edge; open where the ladder arrives and where the last bridge
     * leaves for the colossal chain.
     */
    private void platform(int u, int v, double r, LevelLayout.Column out, BlockState[] pal) {
        double a = Math.atan2(v, u);
        double rim = 12.4 + 0.8 * Math.sin(5 * a + platformPhase) - 1.3 * Hash.unit(seed, worldX(u, v), worldZ(u, v), 4965);
        if (r > rim) {
            return;
        }
        long h = Hash.of(seed, worldX(u, v), worldZ(u, v), 4966);
        out.set(PLATFORM_Y, pal[Hash.unit(h) < 0.2 ? RUBBLE : PAVING]);
        out.set(PLATFORM_Y - 1, pal[GOTHIC]);
        if (r <= 11) {
            out.set(PLATFORM_Y - 2, pal[GOTHIC]);
        }
        double d = deg(u, v);
        boolean open = Math.abs(Math.IEEEremainder(d - F2.degEnd(), 360)) < 18 || Math.abs(Math.IEEEremainder(d - bridgeDeg, 360)) < 16
                || Math.abs(Math.IEEEremainder(d, 360)) < 12;
        double edge = Hash.unit(Hash.mix(h));
        if (!open && r > rim - 1.2 && edge < 0.6) {
            // Stumps of the parapet and its pinnacles, broken off at different heights.
            int height = edge < 0.12 ? 3 : edge < 0.3 ? 2 : 1;
            for (int y = PLATFORM_Y + 1; y <= PLATFORM_Y + height; y++) {
                out.set(y, pal[CARVED]);
            }
        } else if (!open && r < rim - 2 && r > 4 && edge > 0.93) {
            out.set(PLATFORM_Y + 1, pal[RUBBLE]); // fallen masonry
        }
    }

    // ==========================================================================================
    // Ribs, floating chunks, iron (worked out once per site)
    // ==========================================================================================

    private void buildDecor() {
        Long2IntOpenHashMap voxels = new Long2IntOpenHashMap();
        gothic(voxels);
        // Ribs: out of the body, curling round and down, thinning to a point (pale bone against the dark stone).
        for (double[][] rib : ribPaths()) {
            for (double[] p : rib) {
                sphere(voxels, p[0], p[1], p[2], p[3], SPIRE);
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

    // ==========================================================================================
    // The Gothic outside: windows, the rose window, turrets, flying buttresses, pinnacles
    // ==========================================================================================

    /**
     * Where the climb comes out of the spire or runs round it: {angle, half width (degrees), lowest
     * y, highest y}. Nothing is built onto the outside there (the doorways, where the stair comes
     * onto the top, the rose window).
     */
    private static final double[][] KEEP_CLEAR = {
            {0, 14, 196, 214},                                  // the entrance to the hall
            {F1.degEnd(), 14, ROOM_FLOOR - 4, ROOM_FLOOR + 6},   // the doorway into the half-way room
            {F2.deg0(), 14, F2.y0() - 4, F2.y0() + 6},          // the doorway out above the inside stair
            {F2.degEnd(), 16, TOP - 8, PLATFORM_Y + 6},          // where the stair comes up onto the top
            {0, 42, 318, 342},                                  // the rose window
    };

    private static boolean clear(double deg, double halfWidth, double y0, double y1) {
        for (double[] k : KEEP_CLEAR) {
            if (Math.abs(Math.IEEEremainder(deg - k[0], 360)) < k[1] + halfWidth && y1 > k[2] && y0 < k[3]) {
                return false;
            }
        }
        return true;
    }

    /** The tallest stretch {from, to} within lo..hi left free at angle deg (with the given half width). */
    private static int[] freeSpan(double deg, double halfWidth, int lo, int hi) {
        int bestFrom = lo;
        int bestTo = lo;
        int from = lo;
        for (int y = lo; y <= hi + 1; y++) {
            boolean blocked = y > hi || !clear(deg, halfWidth, y, y + 1);
            if (blocked) {
                if (y - from > bestTo - bestFrom) {
                    bestFrom = from;
                    bestTo = y;
                }
                from = y + 1;
            }
        }
        return new int[] {bestFrom, bestTo};
    }

    /** The outermost cell of the body at angle {@code rad} and height y (spire frame), or null. */
    private int[] surface(double rad, int y) {
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        for (double r = BODY_REACH; r > WALL_R; r -= 0.5) {
            int u = (int) Math.round(r * c);
            int v = (int) Math.round(r * s);
            if (solid(u, v, y)) {
                return new int[] {u, v};
            }
        }
        return null;
    }

    private void gothic(Long2IntOpenHashMap voxels) {
        // Pointed windows round every vertebra, some lit by candles inside, some dark.
        for (int k = 1; k < JOINTS.length - 1; k++) {
            int y0 = (JOINTS[k] + JOINTS[k + 1]) / 2 - 2;
            for (int n = 0; n < 12; n++) {
                double deg = 15 + 30 * n;
                if (!clear(deg, 6, y0, y0 + 5)) {
                    continue;
                }
                long h = Hash.of(seed, cx + k * 13 + n, cz, 4971);
                int glass = Hash.unit(h) < 0.55 ? WIN_LIT : WIN_DARK;
                double ext = extent(deg, y0, y0 + 4);
                for (int t = -1; t <= 1; t++) {
                    for (int dy = 0; dy <= 4; dy++) {
                        if (dy == 4 && t != 0) {
                            continue; // the pointed top
                        }
                        int[] c = surface(Math.toRadians(deg) + t / ext, y0 + dy);
                        if (c != null) {
                            voxels.put(vkey(c[0], y0 + dy, c[1]), glass);
                        }
                    }
                }
            }
        }
        // The rose window over the entrance: a wheel of carved spokes, candlelit glass between.
        int ry = 330;
        double rext = extent(0, ry - 7, ry + 7);
        for (int t = -7; t <= 7; t++) {
            for (int dy = -7; dy <= 7; dy++) {
                double rho = Math.hypot(t, dy);
                if (rho > 6.6) {
                    continue;
                }
                int[] c = surface(t / rext, ry + dy);
                if (c == null) {
                    continue;
                }
                double psi = Math.atan2(dy, t);
                double spoke = Math.abs(Math.IEEEremainder(psi, Math.PI / 6)) * rho;
                int block = rho > 5.4 || rho < 1.3 || spoke < 0.55 ? CARVED : WIN_LIT;
                voxels.put(vkey(c[0], ry + dy, c[1]), block);
            }
        }
        // Turrets clinging to the body, at the angles between the climb's axes and the chains' diagonals.
        double[] candidates = {25, 65, 115, 155, 205, 245, 295, 335};
        java.util.List<double[]> turrets = new java.util.ArrayList<>();
        for (double base : candidates) {
            long h = Hash.of(seed, cx + (int) base, cz, 4970);
            if (Hash.unit(h) > 0.8) {
                continue;
            }
            double deg = base + (Hash.unit(Hash.mix(h)) - 0.5) * 8;
            double rt = 3.5 + 2.2 * Hash.unit(Hash.mix(h + 1));
            boolean broken = Hash.unit(Hash.mix(h + 4)) < 0.35;
            double roof = broken ? 0 : rt * 2.6 + 4;
            double halfWidth = Math.toDegrees(Math.atan2(rt + 1.5, 12.0 + rt));
            // Fitted into the tallest stretch at this angle the climb leaves free (it runs round
            // the spire at three heights, so a turret goes between them).
            int[] free = freeSpan(deg, halfWidth, 216, PLATFORM_Y - 4);
            int low = free[0] + (int) Math.ceil(2 * rt) + 2;
            int high = free[1] - (int) Math.ceil(roof) - 2;
            int len = Math.min(high - low, 45 + (int) (70 * Hash.unit(Hash.mix(h + 3))));
            if (len < 25) {
                continue;
            }
            int y0 = low + (int) ((high - low - len) * Hash.unit(Hash.mix(h + 2)));
            int y1 = y0 + len;
            double ext = extent(deg, y0, y1);
            double rc = ext + rt - 1.8;
            turret(voxels, h, deg, rc, rt, y0, y1, broken);
            turrets.add(new double[] {deg, rc, rt, y0, y1});
        }
        // Flying buttresses arcing between neighbouring turrets.
        turrets.sort((p, q2) -> Double.compare(p[0], q2[0]));
        for (int i = 0; i + 1 < turrets.size(); i++) {
            double[] ta = turrets.get(i);
            double[] tb = turrets.get(i + 1);
            if (tb[0] - ta[0] > 62) {
                continue;
            }
            double y = Math.min(ta[4], tb[4]) - 10;
            if (y < Math.max(ta[3], tb[3]) + 6 || !clear((ta[0] + tb[0]) / 2, (tb[0] - ta[0]) / 2, y - 3, y + 12)) {
                continue;
            }
            double[] p0 = {(ta[1] + ta[2]) * Math.cos(Math.toRadians(ta[0])), y, (ta[1] + ta[2]) * Math.sin(Math.toRadians(ta[0]))};
            double[] p2 = {(tb[1] + tb[2]) * Math.cos(Math.toRadians(tb[0])), y, (tb[1] + tb[2]) * Math.sin(Math.toRadians(tb[0]))};
            double mid = Math.toRadians((ta[0] + tb[0]) / 2);
            double mr = Math.max(ta[1] + ta[2], tb[1] + tb[2]) + 7;
            double[] p1 = {mr * Math.cos(mid), y + 9, mr * Math.sin(mid)};
            for (double t = 0; t <= 1.0001; t += 0.02) {
                double w0 = (1 - t) * (1 - t);
                double w1 = 2 * t * (1 - t);
                double w2 = t * t;
                sphere(voxels, w0 * p0[0] + w1 * p1[0] + w2 * p2[0], w0 * p0[1] + w1 * p1[1] + w2 * p2[1],
                        w0 * p0[2] + w1 * p1[2] + w2 * p2[2], 1.2, CARVED);
            }
        }
        // Pinnacles round the joints between the vertebrae.
        for (int k = 1; k < JOINTS.length - 1; k++) {
            int j = JOINTS[k];
            for (int n = 0; n < 8; n++) {
                double deg = 22.5 + 45 * n;
                if (!clear(deg, 3, j, j + 6)) {
                    continue;
                }
                int[] c = surface(Math.toRadians(deg), j);
                if (c == null) {
                    continue;
                }
                int height = 3 + Hash.range(Hash.of(seed, cx + j, cz + n, 4972), 3);
                for (int y = j + 1; y <= j + height; y++) {
                    voxels.put(vkey(c[0], y, c[1]), CARVED);
                }
            }
        }
    }

    /**
     * One turret: a round tower on a corbel narrowing into the body below it, carved bands up it,
     * small lit and dark windows; a pointed roof and spike, or (broken) a jagged top, now and then
     * with a dead red-leaved tree growing out of it.
     */
    private void turret(Long2IntOpenHashMap voxels, long h, double deg, double rc, double rt, int y0, int y1, boolean broken) {
        double ca = Math.cos(Math.toRadians(deg));
        double sa = Math.sin(Math.toRadians(deg));
        double lean = (Hash.unit(Hash.mix(h + 5)) - 0.5) * 0.06;
        int corbel = (int) Math.ceil(rt * 2);
        int reach = (int) Math.ceil(rt + 2);
        for (int y = y0 - corbel; y <= y1; y++) {
            double off = rc + lean * Math.max(0, y - y0);
            double tu = off * ca;
            double tv = off * sa;
            boolean band = y > y0 && (y - y0) % 18 == 0;
            double rr = y < y0 ? Math.max(0.8, rt * (1.0 - (double) (y0 - y) / corbel)) : rt + (band ? 1.0 : 0.0);
            for (int u = (int) Math.floor(tu) - reach; u <= (int) Math.ceil(tu) + reach; u++) {
                for (int v = (int) Math.floor(tv) - reach; v <= (int) Math.ceil(tv) + reach; v++) {
                    double du = u - tu;
                    double dv = v - tv;
                    double d = Math.hypot(du, dv);
                    if (d > rr) {
                        continue;
                    }
                    int block = band || y < y0 ? CARVED : GOTHIC;
                    if (broken && d > rr - 1.5 && y > y1 - 8) {
                        // The jagged broken top: each stone of the wall stops at its own height.
                        int stop = y1 - Hash.range(Hash.of(seed, worldX(u, v), worldZ(u, v), 4973), 8);
                        if (y > stop) {
                            continue;
                        }
                    }
                    if (!band && y >= y0 && d > rr - 1.2 && (y - y0) % 12 >= 5 && (y - y0) % 12 <= 7) {
                        double phi = Math.atan2(dv, du);
                        for (int w = 0; w < 4; w++) {
                            if (Math.abs(Math.IEEEremainder(phi - (w * Math.PI / 2 + Math.PI / 4), Math.PI * 2)) < 0.24) {
                                block = Hash.unit(Hash.of(h, w, y / 12, 4974)) < 0.6 ? WIN_LIT : WIN_DARK;
                            }
                        }
                    }
                    voxels.put(vkey(u, y, v), block);
                }
            }
        }
        double tu = (rc + lean * (y1 - y0)) * ca;
        double tv = (rc + lean * (y1 - y0)) * sa;
        if (!broken) {
            // A carved cornice, then a steep pointed roof and a spike.
            double roof = rt * 2.6;
            for (int y = y1 + 1; y <= y1 + (int) roof; y++) {
                double rr = (rt + 1) * (1.0 - (y - y1 - 1) / roof);
                sphereDisc(voxels, tu, y, tv, rr, CARVED);
            }
            for (int y = y1 + (int) roof + 1; y <= y1 + (int) roof + 3; y++) {
                voxels.put(vkey((int) Math.round(tu), y, (int) Math.round(tv)), CARVED);
            }
        } else if (Hash.unit(Hash.mix(h + 6)) < 0.55) {
            // A dead tree rooted in the broken top, a few red leaves left on it.
            int bu = (int) Math.round(tu);
            int bv = (int) Math.round(tv);
            int trunk = 4 + Hash.range(Hash.mix(h + 7), 3);
            for (int y = y1 - 6; y <= y1 - 6 + trunk + 6; y++) {
                voxels.put(vkey(bu, y, bv), WOOD);
            }
            int top = y1 + trunk;
            for (int du = -2; du <= 2; du++) {
                for (int dv = -2; dv <= 2; dv++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (du * du + dv * dv + dy * dy * 2 <= 5 && Hash.unit(Hash.of(h, du * 7 + dv, dy, 4975)) < 0.6) {
                            voxels.put(vkey(bu + du, top + dy, bv + dv), LEAVES);
                        }
                    }
                }
            }
        }
    }

    /** A flat disc of radius rr at height y. */
    private static void sphereDisc(Long2IntOpenHashMap voxels, double u, int y, double v, double rr, int block) {
        for (int cu = (int) Math.floor(u - rr); cu <= (int) Math.ceil(u + rr); cu++) {
            for (int cv = (int) Math.floor(v - rr); cv <= (int) Math.ceil(v + rr); cv++) {
                if (Math.hypot(cu - u, cv - v) <= rr) {
                    voxels.put(vkey(cu, y, cv), block);
                }
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
                        voxels.put(vkey(cu, cy, cv), dy > 0.55 ? CARVED : GOTHIC);
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
    // The way up: the outside stair, doorways, windows, lanterns (worked out once per site)
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
        rin = new double[FLIGHTS.length][];
        wide = new double[FLIGHTS.length][];
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
        for (int i = 0; i < FLIGHTS.length; i++) {
            flight(FLIGHTS[i], i);
        }
        // Through the wall into the half-way room at the top of flight 1, and out of the inside stair's
        // landing onto flight 2.
        doorway(F1.degEnd(), ROOM_R - 0.5, rin[0][rin[0].length - 1] + 1.0, ROOM_FLOOR);
        doorway(F2.deg0(), SHAFT_R - 0.5, rin[1][0] + 1.0, F2.y0());
        dress();
        finalClimb();
    }

    /** A rough doorway 3 wide and 3 high at angle deg from radius r0 to r1, its floor under feet height y. */
    private void doorway(double deg, double r0, double r1, int y) {
        ray(deg, r0, r1, 1.1, y, y + 2);
        double c = Math.cos(Math.toRadians(deg));
        double sn = Math.sin(Math.toRadians(deg));
        int reach = (int) Math.ceil(r1) + 1;
        for (int u = -reach; u <= reach; u++) {
            for (int v = -reach; v <= reach; v++) {
                double along = u * c + v * sn;
                double across = -u * sn + v * c;
                if (along >= r0 && along <= r1 && Math.abs(across) <= 1.1) {
                    op(u, v, y - 1, y - 1, PAVING);
                }
            }
        }
    }

    /** Inner radius of each flight's stair (outside the body there) every STEP_DEG of its sweep. */
    private double[][] rin;
    /** Width of each flight's stair every STEP_DEG of its sweep (narrower where it has crumbled). */
    private double[][] wide;

    /** How far round flight f a position is, and the height difference: {s, dy}, or null if not near it. */
    private double[] onFlight(int f, double u, double v, double y) {
        Flight fl = FLIGHTS[f];
        double th = mod360(deg(u, v) - fl.deg0());
        double best = Double.MAX_VALUE;
        double bestS = -1;
        for (double s = th > 330 ? th - 360 : th; s <= fl.sweep() + 1e-6; s += 360) {
            double dy = Math.abs(y - fl.y(s));
            if (dy < best) {
                best = dy;
                bestS = s;
            }
        }
        return bestS < 0 ? null : new double[] {bestS, best};
    }

    /**
     * Where to head to keep climbing from world position (x, y, z) on the outside stair: a point
     * on the middle of its tread {@code ahead} blocks further up, {x, y, z} in world coordinates,
     * or null when not on a flight. For the scripted tests and for anything that climbs it.
     */
    public double[] stairGuide(double x, double y, double z, double ahead) {
        if (!built) {
            build();
        }
        double dx = x - cx - 0.5;
        double dz = z - cz - 0.5;
        double u = switch (q) {
            case 0 -> dx;
            case 1 -> dz;
            case 2 -> -dx;
            default -> -dz;
        };
        double v = switch (q) {
            case 0 -> dz;
            case 1 -> -dx;
            case 2 -> -dz;
            default -> dx;
        };
        for (int f = 0; f < FLIGHTS.length; f++) {
            double[] at = onFlight(f, u, v, y);
            if (at == null || at[1] > 5) {
                continue;
            }
            Flight fl = FLIGHTS[f];
            int k = (int) Math.max(0, Math.min(rin[f].length - 1, at[0] / STEP_DEG));
            double r = rin[f][k] + wide[f][k] * 0.45;
            double s = Math.max(0, Math.min(fl.sweep(), at[0] + Math.toDegrees(ahead / r)));
            return flightPoint(f, s, 0.45);
        }
        return null;
    }

    /**
     * A point on flight f (0 or 1), {@code s} degrees round from its start, {@code across} of
     * the way from its inner edge to its outer (0..1): {x, y (its feet height), z} in the world.
     */
    public double[] flightPoint(int f, double s, double across) {
        if (!built) {
            build();
        }
        Flight fl = FLIGHTS[f];
        int k = (int) Math.max(0, Math.min(rin[f].length - 1, s / STEP_DEG));
        double rr = rin[f][k] + wide[f][k] * across;
        double a = Math.toRadians(fl.deg0() + s);
        double tu = rr * Math.cos(a);
        double tv = rr * Math.sin(a);
        return new double[] {cx + 0.5 + worldDx(tu, tv), fl.y(s), cz + 0.5 + worldDz(tu, tv)};
    }

    /** How far round flight f runs, in degrees. */
    public static double flightSweep(int f) {
        return FLIGHTS[f].sweep();
    }

    /**
     * Where world (x, y, z) is on the outside stair: flight 1 from 0 to 1, flight 2 from 1 to 2 (how
     * far round it), or -1 if not standing on it (inside, or anywhere else). For the creature
     * (world/Level490Hunt): the outside of the climb is where it can reach you.
     */
    public double outsideProgress(double x, double y, double z) {
        if (!built) {
            build();
        }
        double[] uv = frame(x, z);
        double r = Math.hypot(uv[0], uv[1]);
        for (int f = 0; f < FLIGHTS.length; f++) {
            double[] at = onFlight(f, uv[0], uv[1], y);
            if (at == null || at[1] > 3.5) {
                continue;
            }
            int k = (int) Math.max(0, Math.min(rin[f].length - 1, at[0] / STEP_DEG));
            if (r >= rin[f][k] - 1.0 && r <= rin[f][k] + wide[f][k] + 1.5) {
                return f + Math.max(0, Math.min(1, at[0] / FLIGHTS[f].sweep()));
            }
        }
        return -1;
    }

    /**
     * The point on the spire's outside, in the direction of world (x, z) from its axis, at height y:
     * {x, z} in the world. Where the creature takes hold of it.
     */
    public double[] surface(double x, double y, double z) {
        double[] uv = frame(x, z);
        double deg = Math.toDegrees(Math.atan2(uv[1], uv[0]));
        int yi = (int) Math.floor(y);
        double r = y >= PLATFORM_Y - 1 ? 12.9 : extent(mod360(deg), yi, yi + 1) + 0.3;
        double a = Math.toRadians(deg);
        double tu = r * Math.cos(a);
        double tv = r * Math.sin(a);
        return new double[] {cx + 0.5 + worldDx(tu, tv), cz + 0.5 + worldDz(tu, tv)};
    }

    /** World (x, z) to this spire's frame {u, v}. */
    private double[] frame(double x, double z) {
        double dx = x - cx - 0.5;
        double dz = z - cz - 0.5;
        double u = switch (q) {
            case 0 -> dx;
            case 1 -> dz;
            case 2 -> -dx;
            default -> -dz;
        };
        double v = switch (q) {
            case 0 -> dz;
            case 1 -> -dx;
            case 2 -> -dz;
            default -> dx;
        };
        return new double[] {u, v};
    }

    /**
     * One flight of the outside stair (after the play-test: "messy and slightly chaotic, not so
     * proper and clean"). Swept round the body a quarter degree at a time: its inner edge just
     * outside the body at that height (so it bulges out where the body does), 3 wide, steps in half
     * blocks (walked, not jumped), thick stone under it propped on carved brackets. And broken:
     * gaps you have to jump, stretches crumbled to a block or two wide, a stretch of rotten planks
     * on posts, a stretch fallen away and crossed on a single plank, rubble on the steps, the stumps
     * of a railing, candles left by someone. Where it meets a turret or a rib it cuts through.
     */
    private void flight(Flight fl, int f) {
        int n = (int) Math.ceil(fl.sweep() / STEP_DEG) + 1;
        double[] radius = new double[n];
        for (int k = 0; k < n; k++) {
            double s = k * STEP_DEG;
            int y = (int) Math.floor(fl.y(s));
            radius[k] = extent(mod360(fl.deg0() + s), y - 1, y + 3) + 0.6;
        }
        // Smooth it a little so the tread doesn't jump in and out block by block.
        double[] smooth = new double[n];
        int w = 24;
        for (int k = 0; k < n; k++) {
            double m = 0;
            for (int j = Math.max(0, k - w); j <= Math.min(n - 1, k + w); j++) {
                m = Math.max(m, radius[j]);
            }
            smooth[k] = m;
        }
        rin[f] = smooth;
        wide[f] = new double[n];
        double len = fl.sweep();
        long h = Hash.of(seed, cx + f * 7, cz, 4980);
        // Where it is broken (fractions of the flight), different for each spire.
        double[] gaps = f == 0 ? new double[] {0.21, 0.5, 0.79} : new double[] {0.14, 0.38, 0.62, 0.86};
        double[][] narrow = f == 0 ? new double[][] {{0.3, 0.36}} : new double[][] {{0.52, 0.58}, {0.9, 0.94}};
        double[] rotten = f == 0 ? new double[] {0.6, 0.67} : new double[] {0.7, 0.78};
        double[] fallen = f == 0 ? new double[] {0.43, 0.47} : new double[] {0.25, 0.29};
        // First, which sample each block column takes its height from: the one nearest its centre,
        // so neighbouring columns differ by half a block (a column crossed by several samples would
        // otherwise jump by a block or more).
        // Keyed by column; one entry per turn of the stair over it (told apart by height).
        Long2ObjectOpenHashMap<List<double[]>> cells = new Long2ObjectOpenHashMap<>();
        for (int k = 0; k < n; k++) {
            double s = k * STEP_DEG;
            double frac = s / len;
            double a = Math.toRadians(fl.deg0() + s);
            double width = STAIR_WIDTH;
            for (double[] nr : narrow) {
                if (frac >= nr[0] && frac <= nr[1]) {
                    width = 1.5 + 0.5 * Hash.unit(Hash.of(h, k / 40, 1, 4981));
                }
            }
            wide[f][k] = width;
            for (double d = 0; d < width; d += 0.25) {
                double pu = (smooth[k] + d) * Math.cos(a);
                double pv = (smooth[k] + d) * Math.sin(a);
                int u = (int) Math.round(pu);
                int v = (int) Math.round(pv);
                double d2 = (pu - u) * (pu - u) + (pv - v) * (pv - v);
                double y = fl.y(s);
                List<double[]> turns = cells.computeIfAbsent(key(u, v), x -> new ArrayList<>());
                double[] c = null;
                for (double[] t : turns) {
                    if (Math.abs(t[6] - y) < 12) {
                        c = t;
                    }
                }
                if (c == null) {
                    turns.add(new double[] {u, v, d2, k, d, width, y});
                } else if (d2 < c[2]) {
                    c[2] = d2;
                    c[3] = k;
                    c[4] = d;
                    c[5] = width;
                    c[6] = y;
                }
            }
        }
        for (double[] c : cells.values().stream().flatMap(List::stream).toList()) {
            int u = (int) c[0];
            int v = (int) c[1];
            int k = (int) c[3];
            double d = c[4];
            double width = c[5];
            double s = k * STEP_DEG;
            double frac = s / len;
            // Its height from where its centre is round the flight (not the sample's), so the
            // rounding to half steps is the same for neighbours.
            double sc = s + Math.IEEEremainder(deg(u, v) - (fl.deg0() + s), 360);
            double qy = Math.round(fl.y(sc) * 2) / 2.0;
            int base = (int) Math.floor(qy);
            boolean half = qy != base;
            double r0 = smooth[k];
            boolean wood = frac >= rotten[0] && frac <= rotten[1];
            boolean plankOnly = frac >= fallen[0] && frac <= fallen[1];
            // A gap a block and a half long along the middle of the tread: one ordinary jump.
            double gapHalf = Math.toDegrees(0.75 / (r0 + 1.5)) / len;
            boolean gap = false;
            for (double g : gaps) {
                gap |= Math.abs(frac - g) < gapHalf;
            }
            boolean outer = d >= width - 0.75;
            long hc = Hash.of(seed, worldX(u, v), worldZ(u, v), 4982 + f);
            // Headroom: 3 clear above the step, whatever it cuts through.
            op(u, v, base + (half ? 1 : 0), base + 3, AIR);
            if (gap || (plankOnly && Math.abs(d - width / 2 + 0.25) > 0.6)) {
                continue; // broken off here
            }
            if (outer && width >= 2.5 && Hash.unit(hc) < 0.22) {
                continue; // the crumbled edge
            }
            int tread = wood || plankOnly ? WOOD : PAVING;
            if (half) {
                op(u, v, base, base, wood || plankOnly ? WOOD : SLAB);
                if (!plankOnly) {
                    op(u, v, base - 1, base - 1, wood ? AIR : GOTHIC);
                }
            } else {
                op(u, v, base - 1, base - 1, tread);
                if (!plankOnly && !wood) {
                    op(u, v, base - 2, base - 2, GOTHIC);
                }
            }
            if (wood && outer && (k % 32) < 4) {
                op(u, v, base - 8, base - 2, WOOD); // the posts the rotten planks stand on
            }
            if (!wood && !plankOnly && outer && (k % 40) < 4) {
                op(u, v, base - 5, base - 2, CARVED); // a carved bracket under the edge
            }
            double roll = Hash.unit(Hash.mix(hc));
            int stand = half ? base + 1 : base;
            if (!half && d < 1 && roll < 0.025) {
                op(u, v, stand, stand, RUBBLE);
            } else if (!half && d < 1.5 && roll > 0.985) {
                op(u, v, stand, stand, CANDLE);
            } else if (!half && outer && !wood && !plankOnly && roll > 0.93) {
                op(u, v, stand, stand, POST); // only on a full step: on a slab it would float
            }
        }
    }

    /** World direction index (into DIRS) of the axis nearest to frame angle deg. */
    private int nearestDir(double deg) {
        return worldDir(((int) Math.round(mod360(deg) / 90.0) % 4) * 90);
    }

    /** A statue in a niche cut into the wall at cell c, standing on y, looking toward the spire's middle. */
    private void statue(int[] c, int y) {
        int dir = nearestDir(deg(c[0], c[1]) + 180);
        op(c[0], c[1], y, y, STATUE + dir);
        op(c[0], c[1], y + 1, y + 1, STATUE + 4 + dir);
        op(c[0], c[1], y + 2, y + 2, AIR);
    }

    /**
     * Inside (after the play-test: "chaotic inside"): statues in niches round the hall and the
     * half-way room, a fallen bell and rubble in the hall, candles on the stairs, a statue niche a
     * turn in the lit stairs.
     */
    private void dress() {
        // The hall: statues round the wall, candles at their feet, rubble, the bell that fell.
        for (double a : new double[] {70, 130, 180, 230, 290}) {
            statue(wallCell(a, 9.0, 10.4), BASE + 1);
            int[] f = wallCell(a, 7.6, 8.4);
            op(f[0], f[1], BASE + 1, BASE + 1, CANDLE);
        }
        for (double a : new double[] {100, 160, 255, 315}) {
            int[] c = wallCell(a, 8.0, 8.9);
            op(c[0], c[1], BASE + 1, BASE + 1, RUBBLE);
        }
        double ba = Math.toRadians(205);
        double bu = 7.3 * Math.cos(ba);
        double bv = 7.3 * Math.sin(ba);
        double tu = -Math.sin(ba);
        double tv = Math.cos(ba);
        for (int u = (int) bu - 3; u <= (int) bu + 3; u++) {
            for (int v = (int) bv - 3; v <= (int) bv + 3; v++) {
                double along = (u - bu) * tu + (v - bv) * tv;
                double across = -(u - bu) * tv + (v - bv) * tu;
                if (Math.abs(along) > 1.8 || Math.hypot(u, v) < SHAFT_R + 0.3 || Math.hypot(u, v) >= HALL_R) {
                    continue;
                }
                double flare = 1.0 + 0.45 * (along + 1.8) / 3.6;
                for (int y = BASE + 1; y <= BASE + 3; y++) {
                    if (Math.hypot(across, y + 0.5 - (BASE + 2.1)) <= flare) {
                        op(u, v, y, y, IRON);
                    }
                }
            }
        }
        // The stairs: candles along their outer edge; in the lit stairs a statue in a niche each
        // turn, and here and there the wall broken open to the storm.
        for (Stair s : STAIRS) {
            for (int n = 0; n < s.revs(); n++) {
                for (double f : new double[] {0.22, 0.72}) {
                    int[] c = edgeCell(s, s.a0 + f * 360);
                    if (c != null) {
                        int y = (int) Math.round(s.surface(n, deg(c[0], c[1])));
                        if (y > s.bottom && (y < ROOM_FLOOR - 1 || y > ROOM_TOP + 1)) {
                            op(c[0], c[1], y, y, CANDLE);
                        }
                    }
                }
                if (s.windows) {
                    int[] c = wallCell(s.a0 + 0.55 * 360, 5.5, 6.6);
                    int y = (int) Math.round(s.surface(n, deg(c[0], c[1]))) + 1;
                    if (y > HALL_TOP + 2) {
                        statue(c, y);
                    }
                }
            }
        }
        // The half-way room: statues between the windows, candles, fallen masonry.
        for (double a : new double[] {135, 315}) {
            statue(wallCell(a, 9.0, 10.4), ROOM_FLOOR);
            int[] f = wallCell(a, 7.6, 8.4);
            op(f[0], f[1], ROOM_FLOOR, ROOM_FLOOR, CANDLE);
        }
        for (double a : new double[] {160, 290, 20}) {
            int[] c = wallCell(a, 7.2, 8.0);
            op(c[0], c[1], ROOM_FLOOR, ROOM_FLOOR, RUBBLE);
        }
    }

    /** A tread cell at the stair's outer edge near angle deg whose step is a whole block (a candle can stand on it). */
    private static int[] edgeCell(Stair s, double deg) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int u = -6; u <= 6; u++) {
            for (int v = -6; v <= 6; v++) {
                double r = Math.hypot(u, v);
                double q2 = Math.round(s.surface(0, deg(u, v)) * 2) / 2.0;
                if (r < 4.5 || r >= SHAFT_R || q2 != Math.floor(q2)) {
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

    /**
     * The last climb (after the play-test: "the top: a final challenge"): from the crumbled top a
     * narrow broken bridge runs out to the colossal chain on the diagonal; a rusted ladder is welded
     * up a column of iron bolted to the chain, up to an iron plate at its collar. Stepping off from
     * up there is the leap ({@link #LEAP_Y}). A radio on the top, so a fall from the bridge or the
     * chain only costs this last stretch.
     */
    private void finalClimb() {
        int su = rungU;
        int sv = rungV;
        // The radio and its lanterns on the top.
        op(8, 0, TOP, TOP, RADIO + worldDir(180));
        op(8, 2, TOP, TOP, LANTERN);
        op(8, -2, TOP, TOP, LANTERN);
        // The bridge: from the top's edge to a landing in front of the ladder, three wide, crumbling.
        double a = Math.toRadians(bridgeDeg);
        double x0 = 10.5 * Math.cos(a);
        double z0 = 10.5 * Math.sin(a);
        double x1 = su + 3;
        double z1 = sv;
        double len = Math.hypot(x1 - x0, z1 - z0);
        double dx = (x1 - x0) / len;
        double dz = (z1 - z0) / len;
        int lo = (int) Math.floor(Math.min(x0, x1)) - 3;
        int hi = (int) Math.ceil(Math.max(x0, x1)) + 3;
        int vlo = (int) Math.floor(Math.min(z0, z1)) - 3;
        int vhi = (int) Math.ceil(Math.max(z0, z1)) + 3;
        for (int u = lo; u <= hi; u++) {
            for (int v = vlo; v <= vhi; v++) {
                double t = (u - x0) * dx + (v - z0) * dz;
                double across = Math.abs(-(u - x0) * dz + (v - z0) * dx);
                if (t < -1 || t > len + 0.5 || across > 1.4) {
                    continue;
                }
                if (t > len * 0.55 && t < len * 0.55 + 1.6) {
                    continue; // where it broke: a step across the void
                }
                if (across > 0.6 && Hash.unit(seed, worldX(u, v), worldZ(u, v), 4976) < 0.2) {
                    continue; // crumbled edge
                }
                op(u, v, PLATFORM_Y, PLATFORM_Y, JOINT);
                if (across <= 0.6) {
                    op(u, v, PLATFORM_Y - 1, PLATFORM_Y - 1, JOINT);
                }
                op(u, v, TOP, TOP + 2, AIR);
            }
        }
        // The landing in front of the ladder.
        for (int u = su + 1; u <= su + 3; u++) {
            for (int v = sv - 1; v <= sv + 1; v++) {
                op(u, v, PLATFORM_Y - 1, PLATFORM_Y, JOINT);
                op(u, v, TOP, TOP + 2, AIR);
            }
        }
        // The iron column, the ladder up it, room to climb, and the plate at the collar.
        op(su, sv, PLATFORM_Y - 6, LEAP_Y - 1, IRON);
        op(su + 1, sv, TOP, LEAP_Y - 1, LADDER + worldDir(0));
        op(su + 1, sv, LEAP_Y, LEAP_Y + 2, AIR);
        op(su + 2, sv, TOP, LEAP_Y + 2, AIR);
        int cu = (int) Math.round(finalChain[0]);
        for (int u = cu + 11; u <= su; u++) {
            for (int v = sv - 1; v <= sv + 1; v++) {
                op(u, v, UPPER_COLLAR_Y, LEAP_Y - 1, IRON);
                op(u, v, LEAP_Y, LEAP_Y + 2, AIR);
            }
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
        BlockState stone = Level490Blocks.GOTHIC.get().defaultBlockState();
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
        return r < HALL_WALL_R && y > BASE && y < BC.top + 4;
    }
}
