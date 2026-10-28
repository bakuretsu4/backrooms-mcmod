package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.ChalkMarkBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.registry.ChalkRegistry;
import com.rickr.backrooms.registry.Level2ChamberBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Level 2's "oddities": small, rare architectural anomalies that are wrong in a way you only
 * notice if you look (kinds in {@link Level2Sites#ODDITIES}). Nothing attacks you and nothing
 * jumps out; the building is just not right:
 * <ul>
 *   <li><b>wrong_valves</b>: a valve gallery (a chamber, see {@link Level2Chambers}) where every
 *       quarter-turn valve has been turned the same way, all shut, while water still rushes
 *       through the pipes;</li>
 *   <li><b>inverted_pipe</b>: a yellow gas pipe rises from the floor and bends into one corridor
 *       wall; straight across, the same pipe comes out of the other wall upside down: high up,
 *       bending into the ceiling, its label below it instead of above;</li>
 *   <li><b>repeat_section</b>: three or four corridor sections in a row carry the same section
 *       number on the wall, each with the same dead lamp above it and the same chalk cross some
 *       earlier visitor drew to check;</li>
 *   <li><b>warm_door</b>: a steel door in the corridor wall that opens onto bare concrete. The air
 *       in front of it is warm and something like a boiler breathes behind it;</li>
 *   <li><b>heart_gauge</b>: a pressure gauge at eye level whose needle follows your heartbeat.</li>
 * </ul>
 *
 * <p><b>Placement.</b> Level 2's stretches ({@link Level2AnomalyPlan#CELL} = 180 blocks of each
 * corridor) hold at most one anomaly. An oddity only goes into a stretch the ordinary planner
 * left empty (no brownout, void door, loop door or hot zone): 40% of those, so about one stretch
 * in ten (measured); each kind turns up every 8-20 km of corridor. Everything here is a pure
 * function of (seed, band, stretch).
 *
 * <p><b>Order matters.</b> The side-room plan asks {@link #roomOverride} which slot an oddity
 * takes, and the corridor plan asks it while working out segments; so roomOverride uses only the
 * plain arithmetic of {@link Level2Line} (slots, exits, centre) and the ordinary sites. The site
 * itself ({@link #site}) is worked out later and may look at segment types and doorways.
 */
final class Level2Oddities {
    /** Out of 1: share of the empty stretches that get an oddity. */
    private static final double CHANCE = 0.4;
    /** Oddities keep this far (along the corridor) from an exit door. */
    private static final int EXIT_GAP = 24;
    /** How many corridor sections the repeating number covers (at most; at least {@link #REPEAT_MIN}). */
    private static final int REPEAT_MAX = 4;
    private static final int REPEAT_MIN = 3;
    /** The section numbers on the walls, in the order they come along +x (bulkhead rings use them too). */
    private static final Variant[] SECTIONS = {Variant.SEC_A03, Variant.SEC_A11, Variant.SEC_B14, Variant.SEC_B27,
            Variant.SEC_C06, Variant.SEC_C19, Variant.SEC_D08, Variant.SEC_D32};
    private static final Variant[] LABELS = {Variant.STEAM, Variant.RETURN, Variant.H2O};

    private Level2Oddities() {}

    // ==========================================================================================
    // Which oddity a stretch holds (plain arithmetic only: safe to ask from anywhere)
    // ==========================================================================================

    /** The oddity kind of stretch {@code cell} of band {@code band}, or null. */
    @Nullable
    static String kindOf(long seed, int band, int cell) {
        if (Level2AnomalyPlan.cellSite(seed, band, cell) != null) {
            return null;
        }
        long h = Hash.of(seed, band, cell, 930);
        if (Hash.unit(h) >= CHANCE) {
            return null;
        }
        return Level2Sites.ODDITIES.get(Hash.range(Hash.mix(h), Level2Sites.ODDITIES.size()));
    }

    /** The room type an oddity gives room slot k (ROOM_GALLERY_ODD, ROOM_WARM_DOOR), or ROOM_NONE. */
    static int roomOverride(long seed, int band, int k) {
        int cell = Math.floorDiv(k, 2);
        String kind = kindOf(seed, band, cell);
        if (kind == null || !(kind.equals(Level2Sites.WRONG_VALVES) || kind.equals(Level2Sites.WARM_DOOR))) {
            return Level2Line.ROOM_NONE;
        }
        if (claimedSlot(seed, band, cell, kind) != k) {
            return Level2Line.ROOM_NONE;
        }
        return kind.equals(Level2Sites.WRONG_VALVES) ? Level2Line.ROOM_GALLERY_ODD : Level2Line.ROOM_WARM_DOOR;
    }

    /**
     * The room slot (k) of the stretch a room-taking oddity uses: one away from exits whose room
     * stays clear of the void squares reaching in from the corridors either side; MIN_VALUE if none.
     */
    private static int claimedSlot(long seed, int band, int cell, String kind) {
        int first = Hash.range(Hash.of(seed, band, cell, 931), 2);
        int type = kind.equals(Level2Sites.WRONG_VALVES) ? Level2Line.ROOM_GALLERY_ODD : Level2Line.ROOM_WARM_DOOR;
        for (int i = 0; i < 2; i++) {
            int k = 2 * cell + ((first + i) & 1);
            int slot = Level2Line.slotX(seed, band, k);
            if (Level2Line.exitNear(seed, band, slot, EXIT_GAP) || voidTouches(seed, band, k, type)) {
                continue;
            }
            return k;
        }
        return Integer.MIN_VALUE;
    }

    /** True if a void door's square (its black sky lies under the floor) covers part of slot k's room of {@code type}. */
    static boolean voidUnder(long seed, int band, int k) {
        return voidTouches(seed, band, k, Level2Line.ROOM_SUMP);
    }

    private static boolean voidTouches(long seed, int band, int k, int type) {
        int slot = Level2Line.slotX(seed, band, k);
        int side = Level2Line.slotSide(seed, band, k);
        int c = Level2Line.centre(seed, band);
        int hw = Level2Line.roomHalfWidth(type) + 1;
        int near = c + side * Level2Line.WALL;
        int far = c + side * (Level2Line.WALL + 2 + Level2Line.roomDepth(type));
        int minX = slot - hw;
        int maxX = slot + hw;
        int minZ = Math.min(near, far);
        int maxZ = Math.max(near, far);
        int cell = Math.floorDiv(k, 2);
        for (int b = band - 1; b <= band + 1; b++) {
            AnomalySite s = Level2AnomalyPlan.cellSite(seed, b, cell);
            if (s != null && s.kind().equals(Level2Sites.VOID_DOOR)
                    && s.maxX() >= minX && s.minX() <= maxX && s.maxZ() >= minZ && s.minZ() <= maxZ) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================================
    // The sites
    // ==========================================================================================

    private static final int MEMO = 32;

    private static final class Memo {
        final long[] seed = new long[MEMO];
        final int[] band = new int[MEMO];
        final int[] cell = new int[MEMO];
        final boolean[] filled = new boolean[MEMO];
        final AnomalySite[] site = new AnomalySite[MEMO];
    }

    private static final ThreadLocal<Memo> SITES = ThreadLocal.withInitial(Memo::new);

    /** The oddity site of stretch {@code cell} of band {@code band}, or null. Remembered per thread. */
    @Nullable
    static AnomalySite site(long seed, int band, int cell) {
        Memo memo = SITES.get();
        int i = (int) (Hash.mix(band * 0x5851F42D4C957F2DL + cell) & (MEMO - 1));
        if (memo.filled[i] && memo.seed[i] == seed && memo.band[i] == band && memo.cell[i] == cell) {
            return memo.site[i];
        }
        AnomalySite site = plan(seed, band, cell);
        memo.seed[i] = seed;
        memo.band[i] = band;
        memo.cell[i] = cell;
        memo.site[i] = site;
        memo.filled[i] = true;
        return site;
    }

    @Nullable
    private static AnomalySite plan(long seed, int band, int cell) {
        String kind = kindOf(seed, band, cell);
        if (kind == null) {
            return null;
        }
        int c = Level2Line.centre(seed, band);
        long h = Hash.of(seed, band, cell, 932);
        switch (kind) {
            case Level2Sites.WRONG_VALVES, Level2Sites.WARM_DOOR -> {
                int k = claimedSlot(seed, band, cell, kind);
                if (k == Integer.MIN_VALUE) {
                    return null;
                }
                int slot = Level2Line.slotX(seed, band, k);
                int side = Level2Line.slotSide(seed, band, k);
                int facing = Level2Line.wallOf(side).get2DDataValue();
                if (kind.equals(Level2Sites.WARM_DOOR)) {
                    // The warm air in front of the door: the corridor from the lane's edge to the door.
                    int z0 = c + side;
                    int z1 = c + side * Level2Line.WALL;
                    return new AnomalySite(kind, slot - 2, Math.min(z0, z1), slot + 2, Math.max(z0, z1),
                            slot, z1, facing, h);
                }
                // The gallery's floor (the anchor: just inside its door).
                int hw = Level2Line.roomHalfWidth(Level2Line.ROOM_GALLERY_ODD);
                int z0 = c + side * (Level2Line.WALL + 1);
                int z1 = c + side * (Level2Line.WALL + Level2Line.roomDepth(Level2Line.ROOM_GALLERY_ODD));
                return new AnomalySite(kind, slot - hw, Math.min(z0, z1), slot + hw, Math.max(z0, z1), slot, z0, facing, h);
            }
            case Level2Sites.HEART_GAUGE -> {
                int side = Hash.unit(h) < 0.5 ? -1 : 1;
                int x = findSpot(seed, band, cell, h, side, false);
                if (x == Integer.MIN_VALUE) {
                    return null;
                }
                int z0 = c + side;
                int z1 = c + side * Level2Line.CHASE;
                // Anchor: the gauge itself, on the chase at eye level.
                return new AnomalySite(kind, x - 3, Math.min(z0, z1), x + 3, Math.max(z0, z1), x, z1,
                        Level2Line.wallOf(side).get2DDataValue(), h);
            }
            case Level2Sites.INVERTED_PIPE -> {
                int side = Hash.unit(h) < 0.5 ? -1 : 1; // the wall the pipe goes into
                int x = findSpot(seed, band, cell, h, side, true);
                if (x == Integer.MIN_VALUE) {
                    return null;
                }
                return new AnomalySite(kind, x - 2, c - Level2Line.CHASE, x + 2, c + Level2Line.CHASE,
                        x, c + side * Level2Line.CHASE, Level2Line.wallOf(side).get2DDataValue(), h);
            }
            default -> {
                return planRepeat(seed, band, cell, c, h);
            }
        }
    }

    /**
     * A spot on the chase of {@code side} (both chases if {@code both}) in the middle of a run of
     * racks, in a plain or dark segment, away from doorways, bays and exits: the gauge or the pipe
     * goes there. MIN_VALUE if the stretch has none (it nearly always has).
     */
    private static int findSpot(long seed, int band, int cell, long h, int side, boolean both) {
        int base = cell * Level2AnomalyPlan.CELL;
        int span = Level2AnomalyPlan.CELL - 40;
        int start = Hash.range(Hash.mix(h ^ 7), span);
        for (int t = 0; t < span; t++) {
            int x = base + 20 + Math.floorMod(start + t, span);
            if (Level2Line.chaseSlot(seed, band, x, side) != 2
                    || (both && Level2Line.chaseSlot(seed, band, x, -side) != 2)
                    || Level2Line.exitNear(seed, band, x, EXIT_GAP)) {
                continue;
            }
            int type = Level2Line.segType(seed, band, Level2Line.segment(seed, band, x));
            if (type != Level2Line.SEG_STANDARD && type != Level2Line.SEG_DARK) {
                continue;
            }
            boolean free = true;
            for (int dx = -3; dx <= 3 && free; dx++) {
                free = Level2Line.feature(seed, band, x + dx, side) == Level2Line.F_NONE
                        && (!both || Level2Line.feature(seed, band, x + dx, -side) == Level2Line.F_NONE)
                        && Level2Line.segType(seed, band, Level2Line.segment(seed, band, x + dx)) == type;
            }
            if (free) {
                return x;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The repeating section number: the segments starting in the stretch, three or four in a row,
     * none of them a squeeze, a detour or an exit (a bulkhead shows the number on its ring). The
     * salt holds the first segment's index and how many there are.
     */
    @Nullable
    private static AnomalySite planRepeat(long seed, int band, int cell, int c, long h) {
        int base = cell * Level2AnomalyPlan.CELL;
        int n0 = Level2Line.segment(seed, band, base + 4) + 1;
        int count = 0;
        while (count < REPEAT_MAX && Level2Line.segEnd(seed, band, n0 + count) <= base + Level2AnomalyPlan.CELL - 4) {
            int type = Level2Line.segType(seed, band, n0 + count);
            if (type == Level2Line.SEG_EXIT || !markerSegment(type) && type != Level2Line.SEG_BULKHEAD) {
                break;
            }
            count++;
        }
        if (count < REPEAT_MIN) {
            return null;
        }
        int x0 = Level2Line.segStart(seed, band, n0);
        int x1 = Level2Line.segEnd(seed, band, n0 + count - 1);
        int pier = Level2Line.pierAtOrAfter(seed, band, x0, -1);
        return new AnomalySite(Level2Sites.REPEAT_SECTION, x0, c - Level2Line.CHASE, x1, c + Level2Line.CHASE,
                pier, c - Level2Line.SHOULDER, 0, ((long) n0 << 4) | count);
    }

    // ==========================================================================================
    // Section numbers
    // ==========================================================================================

    /**
     * The section number stencilled at the start of segment n (and on its bulkhead ring, if it has
     * one). Numbers go up along +x, A03 to D32, then round again; inside a repeat_section oddity
     * every section has the number of the first.
     */
    static Variant section(long seed, int band, int n) {
        int start = Level2Line.segStart(seed, band, n);
        AnomalySite site = site(seed, band, Math.floorDiv(start, Level2AnomalyPlan.CELL));
        if (site != null && site.kind().equals(Level2Sites.REPEAT_SECTION) && start >= site.minX() && start <= site.maxX()) {
            n = (int) (site.salt() >> 4);
        }
        int offset = Hash.range(Hash.of(seed, band, 8, 533), SECTIONS.length);
        return SECTIONS[Math.floorMod(n + offset, SECTIONS.length)];
    }

    /** Segments whose first pier carries a section marker (squeezes and detours have no room for one). */
    private static boolean markerSegment(int type) {
        return type != Level2Line.SEG_SQUEEZE && type != Level2Line.SEG_BULKHEAD && !Level2Detours.isDetour(type);
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /**
     * Called after the corridor built column (x, z): the section marker at the start of each
     * segment, and the oddities that live in the corridor itself (the heart gauge, the inverted
     * pipe, the repeat section's dead lamps and chalk crosses). {@code site} is the anomaly at
     * (x, z) or null.
     */
    static void decorate(Level2Layout layout, long seed, int x, int z, @Nullable AnomalySite site, LevelLayout.Column out) {
        int band = Level2Line.band(z);
        int c = Level2Line.centre(seed, band);
        int dz = z - c;
        int floor = layout.floorY();
        int top = layout.ceilingY() - 1;
        if (dz == -Level2Line.SHOULDER && (site == null || !site.kind().equals(Level2Sites.BROWNOUT))) {
            // The section marker: on the face of the first pier of the segment on the north wall,
            // under its lamp, where you look as you walk in.
            int n = Level2Line.segment(seed, band, x);
            if (x == Level2Line.pierAtOrAfter(seed, band, Level2Line.segStart(seed, band, n), -1)
                    && markerSegment(Level2Line.segType(seed, band, n))
                    && Level2Line.feature(seed, band, x, -1) == Level2Line.F_NONE) {
                out.set(floor + 2, Level2Parts.stencil(Direction.SOUTH, section(seed, band, n)));
                if (site != null && site.kind().equals(Level2Sites.REPEAT_SECTION)) {
                    // Every repeated section: the same dead bulb, the same chalk cross by the pier's foot.
                    out.set(floor + 3, Level2Parts.lamp(Direction.SOUTH, true));
                    out.set(floor + 1, ChalkRegistry.CHALK_MARK.get().defaultBlockState()
                            .setValue(ChalkMarkBlock.FACING, Direction.SOUTH)
                            .setValue(ChalkMarkBlock.SYMBOL, ChalkMarkBlock.Symbol.CROSS));
                }
            }
        }
        if (Math.abs(dz) == Level2Line.CHASE) {
            // Over a chamber's door, on the corridor wall: an amber battery lamp that comes on when the section dies.
            int side = dz < 0 ? -1 : 1;
            int k = Level2Line.roomCell(x);
            if (Level2Line.slotX(seed, band, k) == x && Level2Line.slotSide(seed, band, k) == side
                    && Level2Chambers.isChamber(Level2Line.roomType(seed, band, k))) {
                out.set(floor + 3, com.rickr.backrooms.registry.Level2LightBlocks.emergency(Level2Line.inwardOf(side), true, true));
            }
        }
        if (site == null) {
            return;
        }
        if (site.kind().equals(Level2Sites.HEART_GAUGE)) {
            if (x == site.anchorX() && z == site.anchorZ()) {
                int side = dz < 0 ? -1 : 1;
                out.set(floor + 2, Level2ChamberBlocks.heartGauge(Level2Line.inwardOf(side)));
            }
        } else if (site.kind().equals(Level2Sites.INVERTED_PIPE)) {
            if (x != site.anchorX() || Math.abs(dz) != Level2Line.CHASE) {
                return;
            }
            int side = dz < 0 ? -1 : 1;
            Direction wall = Level2Line.wallOf(side);
            Direction in = Level2Line.inwardOf(side);
            Variant label = LABELS[Hash.range(site.salt(), LABELS.length)];
            if (z == site.anchorZ()) {
                // This wall: the pipe rises out of the floor and bends into the wall at waist height, its label above.
                out.set(floor + 1, Level2Parts.centrePipe(Direction.Axis.Y, Kind.PAINTED_YELLOW, Size.MEDIUM));
                out.set(floor + 2, Level2Parts.elbow(wall, false, Kind.PAINTED_YELLOW, Size.MEDIUM));
                out.set(floor + 3, Level2Parts.stencil(in, label));
            } else {
                // Straight across: the same pipe out of the other wall, upside down. It comes out high
                // and bends up into the ceiling, its label below it.
                out.set(top, Level2Parts.centrePipe(Direction.Axis.Y, Kind.PAINTED_YELLOW, Size.MEDIUM));
                out.set(top - 1, Level2Parts.elbow(wall, true, Kind.PAINTED_YELLOW, Size.MEDIUM));
                out.set(floor + 2, Level2Parts.stencil(in, label));
                out.set(floor + 1, Level2Parts.duct(in, false));
            }
        }
    }

    // ==========================================================================================
    // Test points (for /backrooms anomaly visit)
    // ==========================================================================================

    /** The first segment index and the number of segments of a repeat_section site. */
    static int repeatFirst(AnomalySite site) {
        return (int) (site.salt() >> 4);
    }

    static int repeatCount(AnomalySite site) {
        return (int) (site.salt() & 15);
    }
}
