package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The floor plan of Level 2 "Pipe Dreams": where the corridors, their segments, the side rooms
 * and the exits are. No blocks here, only numbers: {@link Level2Layout} turns them into blocks,
 * {@link Level2Rooms} builds the side rooms, {@link Level2AnomalyPlan} adds the anomalies and
 * {@link Level2Finder} finds things for {@code /backrooms anomaly locate}.
 *
 * <p><b>Bands.</b> The world is cut into strips 48 blocks deep along Z ("bands"). Every band holds
 * ONE straight corridor running along X forever, its centre line a couple of blocks off the band's
 * middle (the same offset all along it). Everything between two corridors is solid concrete, so
 * corridors never meet: a player can only walk forwards, backwards, or into a dead-end side room.
 * Everything that belongs to a band stays within {@link #REACH} blocks of its middle.
 *
 * <p><b>Across the corridor</b> (dz = distance from the centre line):
 * <pre>
 *   dz  -4  -3  -2  -1   0  +1  +2  +3  +4
 *        #   P   s   .   .   .   s   P   #      # wall   P pipe chase   s shoulder   . lane
 * </pre>
 * The lane (|dz| &lt;= 1) is never blocked. The shoulders hold the lamps and the gutter.
 *
 * <p><b>Along the corridor</b> it is cut into segments 24 to 48 blocks long, each with its own
 * character (see the SEG_ constants), never the same kind twice in a row; about one in ten is an
 * odd but grounded detour (a dog-leg round a column, a duct crossing, a catwalk over a trench, a
 * sealed section with a bypass: {@link Level2Detours}). Side rooms open off the chase walls about
 * every 60-120 blocks, alternating sides (about one in six a bigger maintenance chamber,
 * {@link Level2Chambers}), and an exit bay comes every 320-420 blocks.
 *
 * <p>Everything is a pure function of (seed, x, z) built from cheap hashes. The few answers that
 * take a bit of work (a segment's type, a room's type) are remembered per thread in small tables,
 * so building a column allocates nothing.
 */
final class Level2Line {
    // ---- Bands ---------------------------------------------------------------------------------
    /** One corridor per this many blocks of Z. */
    static final int BAND = 48;
    /** Everything of a band (rooms, anomaly doors) stays within this distance of the band's middle. */
    static final int REACH = 20;
    /** The centre line sits up to this far off the band's middle (fixed per band). */
    private static final int MAX_OFFSET = 2;
    /** |dz| up to this is the lane: the clear walkway, never blocked. */
    static final int LANE = 1;
    /** |dz| = this is the shoulder, beside the chase: lamps (hung on the chase's piers), the gutter. */
    static final int SHOULDER = 2;
    /** |dz| = this is the pipe chase along each wall. */
    static final int CHASE = 3;
    /** |dz| = this is the corridor wall. */
    static final int WALL = 4;

    // ---- Segments ------------------------------------------------------------------------------
    static final int SEG_STANDARD = 0;
    static final int SEG_VALVES = 1;
    static final int SEG_PUMP = 2;
    static final int SEG_SQUEEZE = 3;
    static final int SEG_TRENCH = 4;
    static final int SEG_BULKHEAD = 5;
    static final int SEG_LEAK = 6;
    static final int SEG_DARK = 7;
    /** The segment holding an exit bay (always a plain run with the bay on one side). */
    static final int SEG_EXIT = 8;
    // The unusual-but-grounded layouts (built by Level2Detours). Occasional: about 1 segment in 10.
    /** The corridor steps sideways around a massive structural column (the wall pushed back on one side). */
    static final int SEG_DOGLEG = 9;
    /** The ceiling drops for a big duct crossing the corridor (2 blocks of headroom under it, no crouching). */
    static final int SEG_DUCT = 10;
    /** Two levels: a grated catwalk along one side over an open trench with a lower walkway in it. */
    static final int SEG_CATWALK = 11;
    /** The corridor is sealed by two locked bulkhead walls; a narrow bypass runs round them through the rock. */
    static final int SEG_BYPASS = 12;
    /** Names for /backrooms anomaly locate level_2/seg/NAME (indexed by SEG_ code). */
    static final String[] SEG_NAMES = {"standard", "valves", "pump", "squeeze", "trench", "bulkhead", "leak", "dark", "exit",
            "dogleg", "duct_crossing", "catwalk", "bypass"};
    /**
     * How common each segment type is (indexed by SEG_ code; EXIT is placed, never rolled). Dark
     * segments only kill up to {@link #DARK_RUN} blocks of lamps, so with this weight they cover
     * well under 12% of the route. The four detours get 3 each: together one segment in ten, so
     * the corridor stays the long straight corridor and the odd stretch is a surprise.
     */
    private static final int[] SEG_WEIGHTS = {24, 13, 11, 11, 11, 11, 11, 8, 0, 3, 3, 3, 3};
    /** Segments come in pairs of this length, split somewhere between 24 and 48 blocks. */
    static final int PAIR = 72;
    static final int MIN_SEG = 24;
    static final int MAX_SEG = 48;
    /** A dark segment's lamps are dead for at most this many blocks (one flickering survivor). */
    static final int DARK_RUN = 28;
    /** A pump bay's alcove reaches this far each way from the middle of its segment. */
    static final int ALCOVE_HALF = 6;

    // ---- Exits ---------------------------------------------------------------------------------
    /** One exit per this many blocks of each corridor ... */
    static final int EXIT_CELL = 370;
    /** ... at a random spot this wide inside the stretch, then moved to the middle of its segment. */
    private static final int EXIT_JITTER = 31;
    /** The exit bay reaches this far each way from the exit door along the corridor. */
    static final int BAY_HALF = 5;
    /** The exit door stands in the bay's back wall, this far from the centre line. */
    static final int BAY_BACK = 6;

    // ---- Side rooms ----------------------------------------------------------------------------
    /** One side room slot per this many blocks of corridor (rooms alternate sides). */
    static final int ROOM_CELL = 90;
    /** Rooms keep this far (along the corridor) from an exit door (its bay reaches 5 each way). */
    private static final int ROOM_EXIT_GAP = 14;
    /** Rooms keep this far from a void or loop door on their side of the corridor (its hall and passage). */
    static final int ROOM_DOOR_GAP = 12;
    static final int ROOM_NONE = -1;
    static final int ROOM_MAINTENANCE = 0;
    static final int ROOM_VENTILATION = 1;
    static final int ROOM_BOILER = 2;
    static final int ROOM_CLOSET = 3;
    static final int ROOM_SAFE = 4;
    /** The slot holds the hot zone's machinery room (an anomaly site, see Level2AnomalyPlan). */
    static final int ROOM_HOT = 5;
    // Maintenance chambers (built by Level2Chambers from their drawn plans): bigger, rarer rooms
    // where the crews worked, each telling a little of their story.
    static final int ROOM_PUMP_ROOM = 6;
    static final int ROOM_VALVE_GALLERY = 7;
    static final int ROOM_ELECTRICAL = 8;
    /** A flooded sump: a pit one block deep, full of black water, with a grated walkway. */
    static final int ROOM_SUMP = 9;
    /** The crew's break room: a table with the shift logbook, a kettle, a jacket left on its hook. */
    static final int ROOM_BREAK = 10;
    /** A valve gallery whose valves are all turned the same wrong way (the wrong_valves oddity's slot). */
    static final int ROOM_GALLERY_ODD = 11;
    /** Only a steel door in the wall, bare concrete behind it (the warm_door oddity's slot). */
    static final int ROOM_WARM_DOOR = 12;
    /**
     * Names for /backrooms anomaly locate level_2/room/NAME (indexed by ROOM_ code; null = no
     * key: the hot room and the oddities' rooms are found through their anomaly kinds).
     */
    static final String[] ROOM_NAMES = {"maintenance", "ventilation", "boiler", "closet", "safe", null,
            "pump_room", "valve_gallery", "electrical", "sump", "break_room", null, null};
    /**
     * How common each room type is (indexed by ROOM_ code; the site-made ones are never rolled).
     * Chambers get 30 of 130: with the slots lost to exits, safe rooms and anomalies, about one
     * room slot in six is a chamber.
     */
    private static final int[] ROOM_WEIGHTS = {28, 20, 16, 20, 16, 0, 6, 6, 6, 5, 7};

    // ---- What sits in a corridor wall at some x (see feature) -----------------------------------
    static final int F_NONE = 0;
    /** The doorway of an ordinary side room (chase cleared, door in the wall). */
    static final int F_DOOR = 1;
    /** The doorway of a safe room (its own plan builds the wall part). */
    static final int F_SAFE_DOOR = 2;
    /** The wide opening of the hot zone's machinery room (built by the anomaly plan). */
    static final int F_HOT = 3;
    /** The way into the little hall in front of a void or loop door. */
    static final int F_MOUTH = 4;
    /** The exit bay. */
    static final int F_BAY = 5;
    /** A pump bay's alcove. */
    static final int F_ALCOVE = 6;

    // ---- Lamps ---------------------------------------------------------------------------------
    /** A caged lamp every this many blocks, alternating sides (so each wall has one every 12). */
    static final int LAMP_SPACING = 6;

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

    /** The way things hung on a side's wall face: into the corridor. */
    static Direction inwardOf(int side) {
        return side < 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The side of the corridor the drainage gutter runs along (fixed per band). */
    static int gutterSide(long seed, int band) {
        return Hash.unit(seed, band, 4, 508) < 0.5 ? -1 : 1;
    }

    // ==========================================================================================
    // Lamps
    // ==========================================================================================

    private static int lampPhase(long seed, int band) {
        return Hash.range(Hash.of(seed, band, 3, 507), LAMP_SPACING);
    }

    /** The side of the lamp at x, or 0 if no lamp spot is at x. */
    static int lampSide(long seed, int band, int x) {
        int rel = x - lampPhase(seed, band);
        if (Math.floorMod(rel, LAMP_SPACING) != 0) {
            return 0;
        }
        return (Math.floorDiv(rel, LAMP_SPACING) & 1) == 0 ? -1 : 1;
    }

    // ==========================================================================================
    // The rhythm of each wall's pipe chase
    // ==========================================================================================

    /** A concrete pier in the chase: the caged lamp hangs on its face (one every 12 blocks per wall). */
    static final int CHASE_PIER = -1;
    /** A riser: pipes climbing from the low main to the ceiling band (halfway between two piers). */
    static final int CHASE_RISER = -2;

    /**
     * Where column x sits in the rhythm of one wall's chase: {@link #CHASE_PIER} (at every lamp spot
     * of that wall), {@link #CHASE_RISER}, or 0..4 = its place in the 5-block run of racks between them.
     */
    static int chaseSlot(long seed, int band, int x, int side) {
        int r = Math.floorMod(chaseRel(seed, band, x, side), 2 * LAMP_SPACING);
        if (r == 0) {
            return CHASE_PIER;
        }
        if (r == LAMP_SPACING) {
            return CHASE_RISER;
        }
        return r < LAMP_SPACING ? r - 1 : r - LAMP_SPACING - 1;
    }

    /** Which 5-block run of racks column x belongs to on that wall (each run picks its own look). */
    static int chaseRun(long seed, int band, int x, int side) {
        return Math.floorDiv(chaseRel(seed, band, x, side), LAMP_SPACING);
    }

    /** x counted from a lamp spot of that wall (the north wall's lamps come first, the south's 6 later). */
    private static int chaseRel(long seed, int band, int x, int side) {
        return x - lampPhase(seed, band) - (side < 0 ? 0 : LAMP_SPACING);
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

    /** Which side a pump bay's alcove is on (0: the walls are taken, no alcove). */
    static int alcoveSide(long seed, int band, int n) {
        return seg(seed, band, n).alcove;
    }

    /** X of the middle of a pump bay's alcove (the pump stands at this x and the next). */
    static int alcoveMid(long seed, int band, int n) {
        return seg(seed, band, n).alcoveMid;
    }

    /** X of a bulkhead segment's steel ring (MIN_VALUE: no spot was free). */
    static int frameX(long seed, int band, int n) {
        return seg(seed, band, n).frame;
    }

    /**
     * True if a trench segment really has its trench: not where a void door's square reaches over
     * the corridor (the void's black sky lies right under the floor there, where the trench would be).
     */
    static boolean hasTrench(long seed, int band, int n) {
        return seg(seed, band, n).trench;
    }

    /** The side the valve manifold / steam jet of this segment is on. */
    static int featureSide(long seed, int band, int n) {
        return seg(seed, band, n).side;
    }

    /**
     * The side a detour segment (SEG_DOGLEG, SEG_BYPASS, SEG_CATWALK) bends towards: the wall pushed
     * back round the column, the wall the bypass runs behind, the side the catwalk runs along (the
     * trench is on the other side). 0 for every other segment.
     */
    static int detourSide(long seed, int band, int n) {
        return seg(seed, band, n).detour;
    }

    /** Middle x of segment n. */
    static int segMid(long seed, int band, int n) {
        Seg s = seg(seed, band, n);
        return (s.start + s.end) >> 1;
    }

    /** One remembered segment. */
    private static final class Seg {
        long seed;
        int band;
        int n;
        boolean filled;
        int start;
        int end;
        int type;
        int side;
        int alcove;
        int alcoveMid;
        int frame;
        int darkFrom;
        int darkTo;
        int survivor;
        boolean trench;
        int detour;
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
        // Work everything out first (that may look up other segments), then store it.
        int start = segStart(seed, band, n);
        int end = segEnd(seed, band, n);
        int type = computeType(seed, band, n);
        int mid = (start + end) >> 1;
        long h = Hash.of(seed, band, n, 509);
        int side = Hash.unit(h) < 0.5 ? -1 : 1;
        int alcove = 0;
        int alcoveMid = Integer.MIN_VALUE;
        int frame = Integer.MIN_VALUE;
        int darkFrom = Integer.MIN_VALUE;
        int darkTo = Integer.MIN_VALUE;
        int survivor = Integer.MIN_VALUE;
        boolean trench = false;
        if (type == SEG_PUMP) {
            // The alcove goes in the middle of the segment, or a little to one side, on the preferred
            // wall or else the other one, wherever it is clear of doorways, rooms and the exit bay.
            for (int k = 0; k < 5 && alcove == 0; k++) {
                int m = mid + (k == 0 ? 0 : (k & 1) == 1 ? 4 * ((k + 1) / 2) : -4 * (k / 2));
                if (m - ALCOVE_HALF <= start || m + ALCOVE_HALF + 1 >= end) {
                    continue; // the alcove (and the pump's second column) must stay inside the segment
                }
                alcove = freeAlcoveSide(seed, band, m, side);
                alcoveMid = m;
            }
            if (alcove == 0) {
                // Nowhere for the alcove (rare: doorways all round): a plain kind of segment instead,
                // still different from both neighbours (they are never pumps, so their types stand).
                int before = computeType(seed, band, n - 1);
                int after = computeType(seed, band, n + 1);
                type = before != SEG_STANDARD && after != SEG_STANDARD ? SEG_STANDARD
                        : before != SEG_VALVES && after != SEG_VALVES ? SEG_VALVES : SEG_LEAK;
            }
        } else if (type == SEG_BULKHEAD) {
            frame = freeFrameX(seed, band, mid);
        } else if (type == SEG_DARK) {
            darkFrom = Math.max(start, mid - DARK_RUN / 2);
            darkTo = Math.min(end, darkFrom + DARK_RUN - 1);
            survivor = nearestLampSpot(seed, band, (darkFrom + darkTo) >> 1);
        } else if (type == SEG_TRENCH) {
            trench = !Level2AnomalyPlan.voidOver(seed, band, start, end);
        }
        int detour = 0;
        if (type == SEG_DOGLEG || type == SEG_BYPASS || type == SEG_CATWALK) {
            // The preferred side, else the other one, wherever the walls involved are free of doorways,
            // rooms, bays and door halls (and a catwalk's trench clear of the void).
            detour = detourFits(seed, band, type, start, end, mid, side) ? side
                    : detourFits(seed, band, type, start, end, mid, -side) ? -side : 0;
            if (detour == 0) {
                // No room for it here: a plain kind of segment instead, as for a pump bay without an alcove.
                int before = computeType(seed, band, n - 1);
                int after = computeType(seed, band, n + 1);
                type = before != SEG_STANDARD && after != SEG_STANDARD ? SEG_STANDARD
                        : before != SEG_VALVES && after != SEG_VALVES ? SEG_VALVES : SEG_LEAK;
            }
        }
        s = slots[i];
        s.seed = seed;
        s.band = band;
        s.n = n;
        s.start = start;
        s.end = end;
        s.type = type;
        s.side = side;
        s.alcove = alcove;
        s.alcoveMid = alcoveMid;
        s.frame = frame;
        s.darkFrom = darkFrom;
        s.darkTo = darkTo;
        s.survivor = survivor;
        s.trench = trench;
        s.detour = detour;
        s.filled = true;
        return s;
    }

    /**
     * True if a detour of {@code type} fits around {@code mid} bending to {@code side}: every wall it
     * digs into is free of doorways, rooms (they reach 5 blocks each way of their door), bays and
     * door halls, with a margin, and a catwalk's trench doesn't lie over a void square.
     */
    private static boolean detourFits(long seed, int band, int type, int start, int end, int mid, int side) {
        int reach;
        int farReach;
        if (type == SEG_DOGLEG) {
            reach = Level2Detours.DOG_HALF + 8;
            farReach = 2;
        } else if (type == SEG_BYPASS) {
            reach = Level2Detours.BYPASS_HALF + 8;
            farReach = Level2Detours.SEAL_HALF + 1;
        } else {
            // The catwalk side stays a normal corridor wall; the trench side may not have a doorway.
            if (Level2AnomalyPlan.voidOver(seed, band, start, end)) {
                return false;
            }
            reach = 0;
            farReach = Level2Detours.CAT_HALF + 1;
        }
        for (int x = mid - reach; x <= mid + reach && reach > 0; x++) {
            if (fixedFeature(seed, band, x, side) != F_NONE) {
                return false;
            }
        }
        for (int x = mid - farReach; x <= mid + farReach; x++) {
            if (fixedFeature(seed, band, x, -side) != F_NONE) {
                return false;
            }
        }
        return true;
    }

    /** The lamp spot closest to x (any side). */
    private static int nearestLampSpot(long seed, int band, int x) {
        int rel = Math.floorMod(x - lampPhase(seed, band), LAMP_SPACING);
        return rel <= LAMP_SPACING / 2 ? x - rel : x + LAMP_SPACING - rel;
    }

    /** The first concrete pier (lamp spot) of {@code side}'s chase at or after x. */
    static int pierAtOrAfter(long seed, int band, int x, int side) {
        return x + Math.floorMod(-chaseRel(seed, band, x, side), 2 * LAMP_SPACING);
    }

    /**
     * The side for a pump alcove around {@code mid}: the preferred side unless a doorway, a room, a
     * door hall or the exit bay is in that wall nearby (near enough for their rooms to reach behind
     * the alcove); then the other side; 0 if both are taken.
     */
    private static int freeAlcoveSide(long seed, int band, int mid, int preferred) {
        int clear = ALCOVE_HALF + 6;
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? preferred : -preferred;
            boolean free = true;
            for (int x = mid - clear; x <= mid + clear && free; x++) {
                free = fixedFeature(seed, band, x, side) == F_NONE;
            }
            if (free) {
                return side;
            }
        }
        return 0;
    }

    /** A spot near the middle where the bulkhead ring has no doorway beside it on either side. */
    private static int freeFrameX(long seed, int band, int mid) {
        for (int k = 0; k < 5; k++) {
            int x = mid + (k == 0 ? 0 : (k & 1) == 1 ? 3 * ((k + 1) / 2) : -3 * (k / 2));
            boolean free = true;
            for (int dx = -1; dx <= 1 && free; dx++) {
                free = fixedFeature(seed, band, x + dx, -1) == F_NONE && fixedFeature(seed, band, x + dx, 1) == F_NONE;
            }
            if (free) {
                return x;
            }
        }
        return Integer.MIN_VALUE;
    }

    // ==========================================================================================
    // Exits
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

    /** The wall exit j's bay opens into (-1 north, +1 south). */
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

    /** True if segment n holds an exit (exits are at least 300 blocks apart, segments 48 at most). */
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

    // ==========================================================================================
    // Side rooms
    // ==========================================================================================

    /** X of the doorway of room slot k (the middle of the room). */
    static int slotX(long seed, int band, int k) {
        return k * ROOM_CELL + 30 + Hash.range(Hash.of(seed, band, k, 510), 31);
    }

    /** The wall room slot k opens off: sides alternate from one slot to the next. */
    static int slotSide(long seed, int band, int k) {
        int flip = Hash.range(Hash.of(seed, band, 5, 511), 2);
        return ((k + flip) & 1) == 0 ? -1 : 1;
    }

    /** The room slot whose room could reach column x (rooms stay well inside their 90 blocks). */
    static int roomCell(int x) {
        return Math.floorDiv(x, ROOM_CELL);
    }

    /** What room slot k of this band holds (ROOM_ constant). */
    static int roomType(long seed, int band, int k) {
        return room(seed, band, k).type;
    }

    /** The safe room of slot k (only when its type is ROOM_SAFE), else null. */
    @Nullable
    static SafeRoomPlan safePlan(long seed, int band, int k) {
        return room(seed, band, k).safe;
    }

    /** Half the width of a room's floor (its walls are one further). */
    static int roomHalfWidth(int type) {
        if (Level2Chambers.isChamber(type)) {
            return Level2Chambers.halfWidth(type);
        }
        return type == ROOM_CLOSET ? 1 : type == ROOM_WARM_DOOR ? 0 : 3;
    }

    /** How many rows deep a room's floor goes into the rock, from |dz| = 5 (0: only a door, rock behind it). */
    static int roomDepth(int type) {
        if (Level2Chambers.isChamber(type)) {
            return Level2Chambers.depth(type);
        }
        return switch (type) {
            case ROOM_MAINTENANCE -> 5;
            case ROOM_CLOSET -> 3;
            case ROOM_WARM_DOOR -> 0;
            default -> 6;
        };
    }

    private static final class Room {
        long seed;
        int band;
        int k;
        boolean filled;
        int type;
        @Nullable
        SafeRoomPlan safe;
    }

    private static final int ROOM_MEMO = 16;

    private static final class RoomMemo {
        final Room[] slots = new Room[ROOM_MEMO];

        RoomMemo() {
            for (int i = 0; i < ROOM_MEMO; i++) {
                slots[i] = new Room();
            }
        }
    }

    private static final ThreadLocal<RoomMemo> ROOMS = ThreadLocal.withInitial(RoomMemo::new);

    private static Room room(long seed, int band, int k) {
        Room[] slots = ROOMS.get().slots;
        int i = (int) (Hash.mix(band * 0xC2B2AE3D27D4EB4FL + k) & (ROOM_MEMO - 1));
        Room r = slots[i];
        if (r.filled && r.seed == seed && r.band == band && r.k == k) {
            return r;
        }
        int type = computeRoomType(seed, band, k);
        SafeRoomPlan safe = null;
        if (type == ROOM_SAFE) {
            int slot = slotX(seed, band, k);
            int side = slotSide(seed, band, k);
            int c = centre(seed, band);
            // 9 wide, 11 deep with walls; its two-thick door wall IS the corridor wall (|dz| 4-5).
            safe = new SafeRoomPlan(slot - 4, side < 0 ? c - 14 : c + WALL, 9, 11, inwardOf(side), 1);
        }
        r = slots[i];
        r.seed = seed;
        r.band = band;
        r.k = k;
        r.type = type;
        r.safe = safe;
        r.filled = true;
        return r;
    }

    private static int computeRoomType(long seed, int band, int k) {
        int slot = slotX(seed, band, k);
        int side = slotSide(seed, band, k);
        if (exitNear(seed, band, slot, ROOM_EXIT_GAP)) {
            return ROOM_NONE;
        }
        AnomalySite site = Level2AnomalyPlan.cellSite(seed, band, Math.floorDiv(k, 2));
        if (site != null) {
            if (site.kind().equals(Level2Sites.HOT_ZONE)) {
                if (site.anchorX() == slot) {
                    return ROOM_HOT;
                }
            } else if (Level2AnomalyPlan.isDoor(site)
                    && Level2AnomalyPlan.doorSide(site) == side
                    && Math.abs(site.anchorX() - slot) <= ROOM_DOOR_GAP) {
                return ROOM_NONE; // the void/loop door has this bit of wall
            }
        }
        // An oddity may claim the slot (the valve gallery turned the wrong way, the warm door).
        int odd = Level2Oddities.roomOverride(seed, band, k);
        if (odd != ROOM_NONE) {
            return odd;
        }
        int type = pick(Hash.of(seed, band, k, 512), ROOM_WEIGHTS);
        if (type == ROOM_SUMP && Level2Oddities.voidUnder(seed, band, k)) {
            return ROOM_MAINTENANCE; // a void's black sky lies where the sump's pit would be
        }
        return type;
    }

    // ==========================================================================================
    // What is in the wall at x
    // ==========================================================================================

    /**
     * What breaks the pipe chase and wall on {@code side} at x: a doorway, an exit bay, a pump
     * alcove... or F_NONE for the plain chase.
     */
    static int feature(long seed, int band, int x, int side) {
        int f = fixedFeature(seed, band, x, side);
        if (f != F_NONE) {
            return f;
        }
        int n = segment(seed, band, x);
        if (segType(seed, band, n) == SEG_PUMP && alcoveSide(seed, band, n) == side
                && Math.abs(x - alcoveMid(seed, band, n)) <= ALCOVE_HALF) {
            return F_ALCOVE;
        }
        return F_NONE;
    }

    /** {@link #feature} without the pump alcoves (they make way for everything else). */
    private static int fixedFeature(long seed, int band, int x, int side) {
        int j0 = exitCell(seed, band, x);
        for (int j = j0 - 1; j <= j0 + 1; j++) {
            if (Math.abs(x - exitX(seed, band, j)) <= BAY_HALF && exitSide(seed, band, j) == side) {
                return F_BAY;
            }
        }
        int k = roomCell(x);
        int type = roomType(seed, band, k);
        if (type != ROOM_NONE && slotSide(seed, band, k) == side) {
            int slot = slotX(seed, band, k);
            if (type == ROOM_HOT) {
                if (Math.abs(x - slot) <= 2) {
                    return F_HOT;
                }
            } else if (x == slot) {
                return type == ROOM_SAFE ? F_SAFE_DOOR : F_DOOR;
            }
        }
        AnomalySite site = Level2AnomalyPlan.cellSite(seed, band, Math.floorDiv(x, Level2AnomalyPlan.CELL));
        if (site != null && Level2AnomalyPlan.isDoor(site) && Level2AnomalyPlan.doorSide(site) == side
                && Level2AnomalyPlan.isMouth(site, x)) {
            return F_MOUTH;
        }
        return F_NONE;
    }
}
