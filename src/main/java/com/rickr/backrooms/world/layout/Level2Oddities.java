package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.ChalkMarkBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.registry.ChalkRegistry;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.Level2ChamberBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Level 2's "oddities": small, rare anomalies in the corridor itself that are wrong in a way you
 * only notice if you look (kinds in {@link Level2Sites#ODDITIES}). Nothing attacks you and nothing
 * jumps out; the building is just not right:
 * <ul>
 *   <li><b>wrong_valves</b>: a valve manifold in the wall where every quarter-turn valve has been
 *       turned the same way, all shut, while water still rushes through the pipes;</li>
 *   <li><b>inverted_pipe</b>: a yellow gas pipe rises from the floor and bends into one wall;
 *       straight across, the same pipe comes out of the other wall upside down: high up, bending
 *       into the ceiling, its label below it instead of above;</li>
 *   <li><b>repeat_section</b>: three or four corridor sections in a row carry the same section
 *       number on the wall, each under the same dead panel, with the same chalk cross some earlier
 *       visitor drew to check;</li>
 *   <li><b>heart_gauge</b>: a pressure gauge set into the wall at eye level whose needle follows
 *       your heartbeat.</li>
 * </ul>
 *
 * <p><b>Placement.</b> Level 2's stretches ({@link Level2AnomalyPlan#CELL} = 180 blocks of each
 * corridor) hold at most one anomaly. An oddity only goes into a stretch without a brownout: 40%
 * of those. Everything here is a pure function of (seed, band, stretch).
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
            case Level2Sites.WRONG_VALVES -> {
                // A valve segment wholly inside the stretch: its manifold is the oddity.
                int base = cell * Level2AnomalyPlan.CELL;
                for (int n = Level2Line.segment(seed, band, base) + 1; Level2Line.segEnd(seed, band, n) < base + Level2AnomalyPlan.CELL; n++) {
                    if (Level2Line.segType(seed, band, n) != Level2Line.SEG_VALVES) {
                        continue;
                    }
                    int x0 = Level2Line.segStart(seed, band, n) + 2;
                    int x1 = Level2Line.segEnd(seed, band, n) - 2;
                    int mid = Level2Line.segMid(seed, band, n);
                    if (Level2Line.exitNear(seed, band, mid, EXIT_GAP + Level2Line.MAX_SEG)) {
                        continue;
                    }
                    int side = Level2Line.featureSide(seed, band, n);
                    return new AnomalySite(kind, x0, Math.min(c, c + side), x1, Math.max(c, c + side), mid, c + side,
                            Level2Line.wallOf(side).get2DDataValue(), h);
                }
                return null;
            }
            case Level2Sites.HEART_GAUGE -> {
                int side = Hash.unit(h) < 0.5 ? -1 : 1;
                int x = findSpot(seed, band, cell, h, side);
                if (x == Integer.MIN_VALUE) {
                    return null;
                }
                // Anchor: the gauge itself, set into the wall at eye level.
                return new AnomalySite(kind, x - 3, Math.min(c, c + side), x + 3, Math.max(c, c + side), x, c + side,
                        Level2Line.wallOf(side).get2DDataValue(), h);
            }
            case Level2Sites.INVERTED_PIPE -> {
                int side = Hash.unit(h) < 0.5 ? -1 : 1; // the wall the pipe goes into
                int x = findSpot(seed, band, cell, h, 0);
                if (x == Integer.MIN_VALUE) {
                    return null;
                }
                return new AnomalySite(kind, x - 2, c - Level2Line.WALL, x + 2, c + Level2Line.WALL,
                        x, c + side * Level2Line.WALL, Level2Line.wallOf(side).get2DDataValue(), h);
            }
            default -> {
                return planRepeat(seed, band, cell, c);
            }
        }
    }

    /**
     * A spot for the gauge (in the high cell of the wall on {@code side}; that wall must be bare
     * there, so the row at your feet keeps the lane tight beside the gauge) or the pipe
     * ({@code side} 0: both walls): in a plain or dark segment, away from exits, the light panels and
     * the other props. MIN_VALUE if the stretch has none (it nearly always has).
     */
    private static int findSpot(long seed, int band, int cell, long h, int side) {
        int base = cell * Level2AnomalyPlan.CELL;
        int span = Level2AnomalyPlan.CELL - 40;
        int start = Hash.range(Hash.mix(h ^ 7), span);
        for (int t = 0; t < span; t++) {
            int x = base + 20 + Math.floorMod(start + t, span);
            double a = Level2Section.ring(seed, band, x);
            if (Level2Line.exitNear(seed, band, x, EXIT_GAP) || Level2Layout.propAt(seed, band, x)
                    || (side != 0 && (Level2Section.bundle(a, Level2Section.slot(side, 1)) || Level2Section.bundle(a, Level2Section.slot(side, 2))))) {
                continue;
            }
            int type = Level2Line.segType(seed, band, Level2Line.segment(seed, band, x));
            if (type == Level2Line.SEG_STANDARD || type == Level2Line.SEG_DARK) {
                return x;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The repeating section number: the segments starting in the stretch, three or four in a row,
     * none of them an exit or a bulkhead (a bulkhead shows the number on its ring). The salt holds
     * the first segment's index and how many there are.
     */
    @Nullable
    private static AnomalySite planRepeat(long seed, int band, int cell, int c) {
        int base = cell * Level2AnomalyPlan.CELL;
        int n0 = Level2Line.segment(seed, band, base + 4) + 1;
        int count = 0;
        while (count < REPEAT_MAX && Level2Line.segEnd(seed, band, n0 + count) <= base + Level2AnomalyPlan.CELL - 4) {
            if (!markerSegment(Level2Line.segType(seed, band, n0 + count))) {
                break;
            }
            count++;
        }
        if (count < REPEAT_MIN) {
            return null;
        }
        int x0 = Level2Line.segStart(seed, band, n0);
        int x1 = Level2Line.segEnd(seed, band, n0 + count - 1);
        int marker = Level2Line.markerX(seed, band, n0);
        int side = Level2Section.bareSide(Level2Section.ring(seed, band, marker));
        return new AnomalySite(Level2Sites.REPEAT_SECTION, x0, c - Level2Line.WALL, x1, c + Level2Line.WALL,
                marker, c + side * Level2Line.WALL, 0, ((long) n0 << 4) | count);
    }

    // ==========================================================================================
    // Section numbers
    // ==========================================================================================

    /**
     * The section number stencilled at the start of segment n. Numbers go up along +x, A03 to D32,
     * then round again; inside a repeat_section oddity every section has the number of the first.
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

    /** Segments whose start carries a section marker (not an exit's or a bulkhead's). */
    private static boolean markerSegment(int type) {
        return type != Level2Line.SEG_BULKHEAD && type != Level2Line.SEG_EXIT;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /**
     * Called after the corridor built column (x, z) (the lane or a wall, |dz| 0-1): the section
     * marker at the start of each segment, and the oddities built into the walls (the heart gauge,
     * the inverted pipe, the repeat section's dead panels and chalk crosses). {@code site} is the
     * anomaly at (x, z) or null.
     */
    static void decorate(Level2Layout layout, long seed, int x, int z, @Nullable AnomalySite site, LevelLayout.Column out) {
        int band = Level2Line.band(z);
        int c = Level2Line.centre(seed, band);
        int dz = z - c;
        int floor = layout.floorY();
        int top = floor + 3; // the lane's ceiling (y43)
        if (dz == 0 && (site == null || !site.kind().equals(Level2Sites.BROWNOUT))) {
            // The section marker: stencilled on the bare wall a few blocks into the segment, where
            // you look as you walk in.
            int n = Level2Line.segment(seed, band, x);
            if (x == Level2Line.markerX(seed, band, n) && markerSegment(Level2Line.segType(seed, band, n))) {
                int side = Level2Section.bareSide(Level2Section.ring(seed, band, x));
                Direction in = Level2Line.inwardOf(side);
                out.set(floor + 2, Level2Parts.stencil(in, section(seed, band, n)));
                if (site != null && site.kind().equals(Level2Sites.REPEAT_SECTION)) {
                    // Every repeated section: the same chalk cross at the wall's foot, the same dead panel overhead.
                    out.set(floor + 1, ChalkRegistry.CHALK_MARK.get().defaultBlockState()
                            .setValue(ChalkMarkBlock.FACING, in)
                            .setValue(ChalkMarkBlock.SYMBOL, ChalkMarkBlock.Symbol.CROSS));
                    out.set(top, Level2Parts.lightPanel(Direction.DOWN, true));
                }
            }
        }
        if (site == null || x != site.anchorX()) {
            return;
        }
        int side = Integer.signum(site.anchorZ() - c);
        if (site.kind().equals(Level2Sites.HEART_GAUGE)) {
            if (dz == side) {
                out.set(floor + 2, Level2ChamberBlocks.heartGauge(Level2Line.inwardOf(side)));
            }
        } else if (site.kind().equals(Level2Sites.INVERTED_PIPE)) {
            Variant label = LABELS[Hash.range(site.salt(), LABELS.length)];
            if (dz == side) {
                // This wall: the pipe rises out of the floor and bends into the wall at head height.
                out.set(floor + 1, Level2Parts.centrePipe(Direction.Axis.Y, Kind.PAINTED_YELLOW, Size.MEDIUM));
                out.set(floor + 2, Level2Parts.elbow(Level2Line.wallOf(side), false, Kind.PAINTED_YELLOW, Size.MEDIUM));
            } else if (dz == -side) {
                // Straight across: the same pipe out of the other wall, upside down: it comes out at
                // head height and bends up into the ceiling, bare concrete under it.
                out.set(floor + 1, Level2Blocks.WALL_BASE.get().defaultBlockState());
                out.set(floor + 2, Level2Parts.elbow(Level2Line.wallOf(-side), true, Kind.PAINTED_YELLOW, Size.MEDIUM));
                out.set(top, Level2Parts.centrePipe(Direction.Axis.Y, Kind.PAINTED_YELLOW, Size.MEDIUM));
            } else if (dz == 0) {
                // The labels, painted on the walls beside the lane: above the bend on this wall, below it across.
                out.set(floor + 2, Level2Parts.stencil(Level2Line.inwardOf(side), label));
                out.set(floor + 1, Level2Parts.stencil(Level2Line.inwardOf(-side), label));
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
