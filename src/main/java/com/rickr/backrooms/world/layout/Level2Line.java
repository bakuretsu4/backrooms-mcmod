package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;

/**
 * The floor plan of Level 2 "Pipe Dreams": where the corridors, their segments, their props and
 * the exits are. No blocks here, only numbers: {@link Level2Layout} turns them into blocks,
 * {@link Level2AnomalyPlan} and {@link Level2Oddities} add the anomalies and {@link Level2Finder}
 * finds things for {@code /backrooms anomaly locate}.
 *
 * <p><b>Bands.</b> The world is cut into strips 48 blocks deep along Z ("bands"). Every band holds
 * ONE straight corridor running along X forever, its centre line a couple of blocks off the band's
 * middle. Everything else is solid concrete: the corridor never branches, no door or opening leads
 * off it, and the only way out is the EXIT bulkhead at the back of a tiny recess, one per
 * 1300-1600 blocks.
 *
 * <p><b>Across the corridor</b> (dz = distance from the centre line) it is one block wide:
 * <pre>
 *   dz   -2  -1   0  +1  +2
 *         #   W   .   W   #      . the lane (y41-42 clear, ceiling at y43)
 *                                W the walls round it: bare concrete, or the pipe bundle, a light
 *                                  panel, a fan, a supply crate... (see {@link Level2Section})
 * </pre>
 *
 * <p><b>Along the corridor</b> it is cut into segments 24 to 48 blocks long, each with its own
 * character (see the SEG_ constants), never the same kind twice in a row. Light panels, fans,
 * beacons, supplies and checkpoint radios come at their own rhythms.
 *
 * <p><b>Arrivals</b> are put halfway between two exits ({@link #arrivalX}), so wherever you land
 * the nearest exit is at least 500 blocks away along the corridor, either way.
 *
 * <p>Everything is a pure function of (seed, x, z) built from cheap hashes. The few answers that
 * take a bit of work (a segment's type) are remembered per thread in a small table, so building a
 * column allocates nothing.
 */
final class Level2Line {
    // ---- Bands ---------------------------------------------------------------------------------
    /** One corridor per this many blocks of Z. */
    static final int BAND = 48;
    /** Everything of a band stays within this distance of the band's middle. */
    static final int REACH = 20;
    /** The centre line sits up to this far off the band's middle (fixed per band). */
    private static final int MAX_OFFSET = 2;
    /** |dz| up to this is the lane: the clear walkway, one block wide. */
    static final int LANE = 0;
    /** |dz| = this is the corridor wall right beside the lane (the ring of dressing round it). */
    static final int WALL = 1;

    // ---- Segments ------------------------------------------------------------------------------
    static final int SEG_STANDARD = 0;
    /** A valve manifold set into one wall. */
    static final int SEG_VALVES = 1;
    /** A steel bulkhead ring round the lane. */
    static final int SEG_BULKHEAD = 2;
    /** A leak: drips and black ooze on the floor. */
    static final int SEG_LEAK = 3;
    /** A dark stretch: the panels dead but one. */
    static final int SEG_DARK = 4;
    /** The segment holding an exit recess (placed, never rolled). */
    static final int SEG_EXIT = 5;
    /** Names for /backrooms anomaly locate level_2/seg/NAME (indexed by SEG_ code). */
    static final String[] SEG_NAMES = {"standard", "valves", "bulkhead", "leak", "dark", "exit"};
    /** How common each segment type is (indexed by SEG_ code; EXIT is placed, never rolled). */
    private static final int[] SEG_WEIGHTS = {30, 16, 12, 14, 10, 0};
    /** Segments come in pairs of this length, split somewhere between 24 and 48 blocks. */
    static final int PAIR = 72;
    static final int MIN_SEG = 24;
    static final int MAX_SEG = 48;
    /** A dark segment's lamps are dead for at most this many blocks (one flickering survivor). */
    static final int DARK_RUN = 28;

    // ---- Exits ---------------------------------------------------------------------------------
    /** One exit per this many blocks of each corridor ... */
    static final int EXIT_CELL = 1450;
    /** ... at a random spot this wide inside the stretch (so 1300-1600 apart), then moved to the middle of its segment. */
    private static final int EXIT_JITTER = 151;
    /** The exit recess is this wide each way of the door (0: one block, as wide as the lane). */
    static final int BAY_HALF = 0;
    /** The exit door stands at the back of its recess, this far from the centre line (the recess is |dz| 1-2). */
    static final int BAY_BACK = 3;
    /** Arrivals land within this far of the point halfway between two exits. */
    static final int ARRIVAL_SPREAD = 80;

    // ---- What sits in a corridor wall at some x (see feature) -----------------------------------
    static final int F_NONE = 0;
    /** The exit recess. */
    static final int F_BAY = 5;

    // ---- Rhythms -------------------------------------------------------------------------------
    /** A recessed light panel every this many blocks. */
    static final int LAMP_SPACING = 10;
    /** The pipe bundle is clamped to a steel channel bracket every this many blocks. */
    static final int BRACKET_SPACING = 4;
    /** A segment's section number is stencilled this many blocks after its start. */
    static final int MARKER_IN = 3;
    /** A wall fan about every this many blocks ... */
    private static final int FAN_CELL = 17;
    /** ... a spinning warning beacon about every this many ... */
    private static final int BEACON_CELL = 28;
    /** ... each moved up to this many blocks less one along its stretch (fans 13-21 apart, beacons 24-32). */
    private static final int PROP_JITTER = 5;
    /** A supply spot (a backpack or crate at the foot of the wall) per this many blocks ... */
    private static final int SUPPLY_CELL = 70;
    /** ... taken this often. */
    private static final double SUPPLY_CHANCE = 0.55;
    /** A checkpoint radio set into the wall once per this many blocks ... */
    private static final int RADIO_CELL = 250;
    /** ... somewhere in its first this-many blocks (so 250-350 apart). */
    private static final int RADIO_JITTER = 101;

    private Level2Line() {}

    // ==========================================================================================
    // Bands
    // ==========================================================================================

    /** The band column z belongs to. */
    static int band(int z) {
        return Math.floorDiv(z + BAND / 2, BAND);
    }

    /** True if z is too far from its band's middle to be part of anything (solid concrete). */
    static boolean outsideReach(int z) {
        return Math.abs(z - band(z) * BAND) > REACH;
    }

    /** Z of the centre line of band {@code band}'s corridor. */
    static int centre(long seed, int band) {
        return band * BAND - MAX_OFFSET + Hash.range(Hash.of(seed, band, 0, 500), 2 * MAX_OFFSET + 1);
    }

    /** Which wall a side is: -1 = the north wall (smaller z), +1 = the south wall. */
    static Direction wallOf(int side) {
        return side < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** The way things fixed to a side's wall face: into the corridor. */
    static Direction inwardOf(int side) {
        return side < 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ==========================================================================================
    // Rhythms: lamps, brackets, fans, beacons, supplies, radios
    // ==========================================================================================

    private static int lampPhase(long seed, int band) {
        return Hash.range(Hash.of(seed, band, 3, 507), LAMP_SPACING);
    }

    /** True if a light panel belongs at x. */
    static boolean lampAt(long seed, int band, int x) {
        return Math.floorMod(x - lampPhase(seed, band), LAMP_SPACING) == 0;
    }

    /** True if the pipe bundle has a steel channel bracket at x. */
    static boolean bracketAt(long seed, int band, int x) {
        return Math.floorMod(x - lampPhase(seed, band) - 1, BRACKET_SPACING) == 0;
    }

    /** True if x is the one spot of its {@code cell}-long stretch for a prop (salt: which prop). */
    private static boolean rhythm(long seed, int band, int x, int cell, int jitter, int salt) {
        int phase = Hash.range(Hash.of(seed, band, salt, 546), cell);
        int k = Math.floorDiv(x - phase, cell);
        return x == k * cell + phase + Hash.range(Hash.of(seed, band, k, salt), jitter);
    }

    /** True if a wall fan belongs at x. */
    static boolean fanAt(long seed, int band, int x) {
        return rhythm(seed, band, x, FAN_CELL, PROP_JITTER, 547);
    }

    /** True if a spinning beacon belongs at x. */
    static boolean beaconAt(long seed, int band, int x) {
        return rhythm(seed, band, x, BEACON_CELL, PROP_JITTER, 548);
    }

    /** True if a supply container (backpack or crate) belongs at x. */
    static boolean supplyAt(long seed, int band, int x) {
        return rhythm(seed, band, x, SUPPLY_CELL, SUPPLY_CELL - 10, 549)
                && Hash.unit(seed, band, Math.floorDiv(x, SUPPLY_CELL), 550) < SUPPLY_CHANCE;
    }

    /** True if a checkpoint radio belongs at x. */
    static boolean radioAt(long seed, int band, int x) {
        return rhythm(seed, band, x, RADIO_CELL, RADIO_JITTER, 551);
    }

    /** X of segment n's section marker (and, in a repeat_section oddity, its dead lamp and chalk cross). */
    static int markerX(long seed, int band, int n) {
        return segStart(seed, band, n) + MARKER_IN;
    }

    // ==========================================================================================
    // Segments
    // ==========================================================================================

    private static int segPhase(long seed, int band) {
        return Hash.range(Hash.of(seed, band, 1, 501), PAIR);
    }

    /** Length of the first segment of pair p (the second gets the rest of the 72 blocks). */
    private static int split(long seed, int band, int pair) {
        return MIN_SEG + Hash.range(Hash.of(seed, band, pair, 502), MAX_SEG - MIN_SEG + 1);
    }

    /** Index of the segment containing x on this band (counts up along +X). */
    static int segment(long seed, int band, int x) {
        int phase = segPhase(seed, band);
        int pair = Math.floorDiv(x - phase, PAIR);
        int off = x - phase - pair * PAIR;
        return off < split(seed, band, pair) ? 2 * pair : 2 * pair + 1;
    }

    /** First x of segment n. */
    static int segStart(long seed, int band, int n) {
        int pair = n >> 1; // floor division by 2, negatives included
        int base = pair * PAIR + segPhase(seed, band);
        return (n & 1) == 0 ? base : base + split(seed, band, pair);
    }

    /** Last x of segment n. */
    static int segEnd(long seed, int band, int n) {
        return segStart(seed, band, n + 1) - 1;
    }

    /** Middle x of segment n. */
    static int segMid(long seed, int band, int n) {
        return (segStart(seed, band, n) + segEnd(seed, band, n)) >> 1;
    }

    /** The type the hash wants for segment n, before the no-repeat rule. */
    private static int rawType(long seed, int band, int n) {
        return pick(Hash.of(seed, band, n, 504), SEG_WEIGHTS);
    }

    /**
     * The segment's type. No two neighbours are ever the same: even segments take what the hash
     * says, odd ones re-roll until they differ from both even neighbours (which never change, so
     * this needs no chain of earlier answers).
     */
    private static int computeType(long seed, int band, int n) {
        if (holdsExit(seed, band, n)) {
            return SEG_EXIT;
        }
        if ((n & 1) == 0) {
            return rawType(seed, band, n);
        }
        int before = rawType(seed, band, n - 1);
        int after = rawType(seed, band, n + 1);
        long h = Hash.of(seed, band, n, 505);
        for (int tries = 0; tries < 8; tries++) {
            int t = pick(h, SEG_WEIGHTS);
            if (t != before && t != after) {
                return t;
            }
            h = Hash.mix(h + 0x9E3779B97F4A7C15L);
        }
        int t = 0;
        while (t == before || t == after) {
            t++;
        }
        return t;
    }

    private static int pick(long h, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = Hash.range(h, total);
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return i;
            }
        }
        return 0;
    }

    // ---- Remembered per segment ---------------------------------------------------------------

    /** The segment's type (SEG_ constant). */
    static int segType(long seed, int band, int n) {
        return seg(seed, band, n).type;
    }

    /** First and last x of the stretch of a dark segment whose lamps are dead. */
    static int darkFrom(long seed, int band, int n) {
        return seg(seed, band, n).darkFrom;
    }

    static int darkTo(long seed, int band, int n) {
        return seg(seed, band, n).darkTo;
    }

    /** The lamp spot that still works in a dark segment (x), or MIN_VALUE. */
    static int survivorX(long seed, int band, int n) {
        return seg(seed, band, n).survivor;
    }

    /** X of a bulkhead segment's steel ring (MIN_VALUE: no spot was free). */
    static int frameX(long seed, int band, int n) {
        return seg(seed, band, n).frame;
    }

    /** The side the valve manifold of this segment is on. */
    static int featureSide(long seed, int band, int n) {
        return seg(seed, band, n).side;
    }

    /** One remembered segment. */
    private static final class Seg {
        long seed;
        int band;
        int n;
        boolean filled;
        int type;
        int side;
        int frame;
        int darkFrom;
        int darkTo;
        int survivor;
    }

    private static final int SEG_MEMO = 32;

    private static final class SegMemo {
        final Seg[] slots = new Seg[SEG_MEMO];

        SegMemo() {
            for (int i = 0; i < SEG_MEMO; i++) {
                slots[i] = new Seg();
            }
        }
    }

    private static final ThreadLocal<SegMemo> SEGS = ThreadLocal.withInitial(SegMemo::new);

    /**
     * Segment n of this band with everything worked out. The returned object belongs to this
     * thread's little table and may be overwritten by the next lookup: read its fields right away.
     */
    private static Seg seg(long seed, int band, int n) {
        Seg[] slots = SEGS.get().slots;
        int i = (int) (Hash.mix(band * 0x9E3779B97F4A7C15L + n) & (SEG_MEMO - 1));
        Seg s = slots[i];
        if (s.filled && s.seed == seed && s.band == band && s.n == n) {
            return s;
        }
        int start = segStart(seed, band, n);
        int end = segEnd(seed, band, n);
        int type = computeType(seed, band, n);
        int mid = (start + end) >> 1;
        int side = Hash.unit(Hash.of(seed, band, n, 509)) < 0.5 ? -1 : 1;
        int frame = Integer.MIN_VALUE;
        int darkFrom = Integer.MIN_VALUE;
        int darkTo = Integer.MIN_VALUE;
        int survivor = Integer.MIN_VALUE;
        if (type == SEG_BULKHEAD) {
            frame = freeFrameX(seed, band, mid);
        } else if (type == SEG_DARK) {
            darkFrom = Math.max(start, mid - DARK_RUN / 2);
            darkTo = Math.min(end, darkFrom + DARK_RUN - 1);
            survivor = nearestLampSpot(seed, band, (darkFrom + darkTo) >> 1);
        }
        s = slots[i];
        s.seed = seed;
        s.band = band;
        s.n = n;
        s.type = type;
        s.side = side;
        s.frame = frame;
        s.darkFrom = darkFrom;
        s.darkTo = darkTo;
        s.survivor = survivor;
        s.filled = true;
        return s;
    }

    /** The lamp spot closest to x. */
    private static int nearestLampSpot(long seed, int band, int x) {
        int rel = Math.floorMod(x - lampPhase(seed, band), LAMP_SPACING);
        return rel <= LAMP_SPACING / 2 ? x - rel : x + LAMP_SPACING - rel;
    }

    /** A spot near the middle where the bulkhead ring has no exit recess beside it. */
    private static int freeFrameX(long seed, int band, int mid) {
        for (int k = 0; k < 5; k++) {
            int x = mid + (k == 0 ? 0 : (k & 1) == 1 ? 3 * ((k + 1) / 2) : -3 * (k / 2));
            boolean free = true;
            for (int dx = -1; dx <= 1 && free; dx++) {
                free = feature(seed, band, x + dx, -1) == F_NONE && feature(seed, band, x + dx, 1) == F_NONE;
            }
            if (free) {
                return x;
            }
        }
        return Integer.MIN_VALUE;
    }

    // ==========================================================================================
    // Exits and arrivals
    // ==========================================================================================

    private static int exitPhase(long seed, int band) {
        return Hash.range(Hash.of(seed, band, 2, 503), EXIT_CELL);
    }

    /** The random spot of exit j of this band, before it moves to the middle of its segment. */
    private static int exitRawX(long seed, int band, int j) {
        return j * EXIT_CELL + exitPhase(seed, band) + Hash.range(Hash.of(seed, band, j, 506), EXIT_JITTER);
    }

    /** X of the door of exit j of this band (the middle of the segment it falls in). */
    static int exitX(long seed, int band, int j) {
        int n = segment(seed, band, exitRawX(seed, band, j));
        return (segStart(seed, band, n) + segEnd(seed, band, n)) >> 1;
    }

    /** The wall exit j's recess opens into (-1 north, +1 south). */
    static int exitSide(long seed, int band, int j) {
        return Hash.unit(seed, band, j, 513) < 0.5 ? -1 : 1;
    }

    /** Index of the exit stretch containing x. */
    static int exitCell(long seed, int band, int x) {
        return Math.floorDiv(x - exitPhase(seed, band), EXIT_CELL);
    }

    /** Index j of this band's exit nearest to x. */
    static int nearestExit(long seed, int band, int x) {
        int j0 = exitCell(seed, band, x);
        int best = j0;
        int bestD = Integer.MAX_VALUE;
        for (int j = j0 - 1; j <= j0 + 1; j++) {
            int d = Math.abs(exitX(seed, band, j) - x);
            if (d < bestD) {
                bestD = d;
                best = j;
            }
        }
        return best;
    }

    /** True if an exit door of this band is closer than {@code gap} to x (along the corridor). */
    static boolean exitNear(long seed, int band, int x, int gap) {
        int j0 = exitCell(seed, band, x);
        for (int j = j0 - 1; j <= j0 + 1; j++) {
            if (Math.abs(exitX(seed, band, j) - x) < gap) {
                return true;
            }
        }
        return false;
    }

    /** True if segment n holds an exit (exits are over 1200 blocks apart, segments 48 at most). */
    private static boolean holdsExit(long seed, int band, int n) {
        int start = segStart(seed, band, n);
        int j0 = exitCell(seed, band, start);
        for (int j = j0; j <= j0 + 1; j++) {
            if (segment(seed, band, exitRawX(seed, band, j)) == n) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where along this band someone sent to x lands: the point halfway between the two exits
     * around the nearest such halfway point to x, moved up to {@link #ARRIVAL_SPREAD} blocks either
     * way (by a hash of x, so different arrivals spread out). Exits are at least ~1250 blocks apart
     * once moved to their segments' middles, so the nearest exit is at least ~545 blocks away
     * either way.
     */
    static int arrivalX(long seed, int band, int x) {
        int j0 = exitCell(seed, band, x);
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int j = j0 - 2; j <= j0 + 1; j++) {
            int mid = (int) (((long) exitX(seed, band, j) + exitX(seed, band, j + 1)) >> 1);
            long d = Math.abs((long) mid - x);
            if (d < bestD) {
                bestD = d;
                best = mid;
            }
        }
        return best + Hash.range(Hash.of(seed, band, x, 545), 2 * ARRIVAL_SPREAD + 1) - ARRIVAL_SPREAD;
    }

    // ==========================================================================================
    // What is in the wall at x
    // ==========================================================================================

    /** What breaks the wall on {@code side} at x: the exit recess, or F_NONE. */
    static int feature(long seed, int band, int x, int side) {
        int j0 = exitCell(seed, band, x);
        for (int j = j0 - 1; j <= j0 + 1; j++) {
            if (Math.abs(x - exitX(seed, band, j)) <= BAY_HALF && exitSide(seed, band, j) == side) {
                return F_BAY;
            }
        }
        return F_NONE;
    }
}
