package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Where Level 39's anomalous areas are. Like the rest of the town this is a pure function of
 * (seed, x, z): the town plan ({@link Level39Cell}) already knows every house, hole, camp and
 * tunnel run, so most sites simply point at one of those. Shapes live in {@link Level39Sites};
 * behaviours in {@code world/anomaly/level39}; client effects in {@code client/anomaly/level39}.
 *
 * <ul>
 *   <li>{@code hostile_windows}: the facade is cut into 48-block stretches; about three in four
 *       are infested with Window entities (Entity 2). The site is a strip from the glass
 *       {@value Level39Sites#STRIP_DEPTH} blocks into the town. Every infested stretch has at least
 *       one Window behind lit ground-storey glass, the only kind that can take you through to
 *       Level 94; {@link #exitWindowSpot} finds the nearest one for the exit hints.</li>
 *   <li>House anomalies: every house the town plan gave an anomaly (inverted, merged, cut, floating,
 *       odd furniture, bigger inside, flooded room, vanishing door) is a site covering its lot.</li>
 *   <li>{@code hole_sounds}: every hole into the tunnels, with a few blocks round it.</li>
 *   <li>{@code meg_camp}: the M.E.G. survey camp, where a town has one.</li>
 *   <li>{@code hydrolitis}: every stagnant tunnel run.</li>
 *   <li>Bigger-inside interiors: a BIGGER_INSIDE house's front hall leads into a much larger
 *       interior built far to the east, at x + {@link #BIG_DX} (see {@link #isCopy}). That copy
 *       region is the same town shifted by a whole number of cells (so even the far facade looks
 *       the same through the door), with each such house's big interior built over it.</li>
 * </ul>
 * One column belongs to at most one site; where they would overlap the first in this order wins:
 * windows, holes, the camp, houses, stagnant runs.
 */
public final class Level39AnomalyPlan {
    /** Bigger-inside interiors are built this far east of their house (1954 whole cells). */
    public static final int BIG_DX = 1954 * Level39Layout.CELL;
    /** From this x on, the world is the bigger-inside copy region, not the ordinary town. */
    public static final int BIG_MIN_X = BIG_DX / 2;

    /** Chance that a facade stretch is infested with Windows. */
    private static final double INFESTED = 0.75;
    /** Hole sites reach this far past the hole's edge. */
    private static final int HOLE_MARGIN = 5;
    /** /backrooms anomaly locate looks this many towns away in each direction. */
    private static final int LOCATE_CELLS = 2;
    /**
     * How far in front of the glass (blocks) the exit spot's middle is: the first town column, well
     * inside a Window's grab reach (3.2) and its "pressed against the glass" distance (2.6).
     */
    private static final double EXIT_FRONT = 1.5;
    /** Columns tried for the exit spot, across the window: its middle first, then either side. */
    private static final int[] SPOT_SIDES = {0, -1, 1};

    private Level39AnomalyPlan() {}

    // ==========================================================================================
    // The bigger-inside copy region
    // ==========================================================================================

    /** True in the copy region where bigger-inside interiors are built. */
    public static boolean isCopy(int x) {
        return x >= BIG_MIN_X;
    }

    /** True if a house at original x can have its interior in the copy region at all. */
    private static boolean copyFits(int x) {
        return x + BIG_DX >= BIG_MIN_X && x < BIG_MIN_X;
    }

    /**
     * Column (x, z) of the copy region: the ordinary town column it copies, with the big interior
     * of a bigger-inside house built over it where there is one.
     */
    static void fillCopy(long seed, int x, int z, LevelLayout.Column out) {
        int ox = x - BIG_DX;
        if (Level39Layout.inBand(ox, z)) {
            Level39Superstructure.fillColumn(seed, ox, z, out);
            return;
        }
        Level39Tile tile = Level39Tile.of(seed, ox, z);
        Level39Town.fillColumn(seed, tile, ox, z, out);
        if (Level39Superstructure.depth(ox, z) >= Level39Superstructure.TOWER_FRONT) {
            Level39Superstructure.towerColumn(seed, ox, z, out);
        }
        Level39HouseSpec h = Level39Houses.biggerInteriorAt(seed, ox, z);
        if (h != null && copyFits(h.x0())) {
            Level39Houses.fillBigInterior(seed, h, ox, z, out);
        }
    }

    // ==========================================================================================
    // Lookups
    // ==========================================================================================

    @Nullable
    public static AnomalySite siteAt(long seed, int x, int z) {
        if (isCopy(x)) {
            Level39HouseSpec h = Level39Houses.biggerInteriorAt(seed, x - BIG_DX, z);
            return h != null && copyFits(h.x0()) ? copySite(h) : null;
        }
        int d = Level39Superstructure.depth(x, z);
        if (d >= -Level39Sites.STRIP_DEPTH && d <= 1) {
            AnomalySite w = windowSiteAt(seed, x, z);
            if (w != null) {
                return w;
            }
        }
        if (!Level39Layout.inTown(x, z)) {
            return null;
        }
        Level39Tile tile = Level39Tile.of(seed, x, z);
        Level39Cell cell = tile.cell;
        for (int k : tile.holes) {
            AnomalySite s = holeSite(cell.holes[k], cell);
            if (s.contains(x, z)) {
                return s;
            }
        }
        int t = tile.thing[tile.index(x, z)];
        if (t >= 0 && cell.things[t].kind() == Level39Cell.CAMP) {
            return campSite(cell.things[t]);
        }
        Level39HouseSpec house = t >= 0 ? cell.things[t].house() : null;
        if (house == null) {
            for (Level39HouseSpec h : cell.houses) {
                if (x >= h.lotX0() && x <= h.lotX1() && z >= h.lotZ0() && z <= h.lotZ1()) {
                    house = h;
                    break;
                }
            }
        }
        if (house != null) {
            AnomalySite s = houseSite(house);
            if (s != null) {
                return s;
            }
        }
        for (int k : tile.segs) {
            Level39Cell.Seg seg = cell.segs[k];
            if (seg.stagnant()) {
                AnomalySite s = stagnantSite(seg);
                if (s.contains(x, z)) {
                    return s;
                }
            }
        }
        return null;
    }

    public static void sitesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        if (isCopy(x + radius)) {
            Level39Layout.housesNear(seed, x - BIG_DX, z, radius + 80, h -> {
                if (h.anomaly() == Level39HouseSpec.Anomaly.BIGGER_INSIDE && copyFits(h.x0())) {
                    AnomalySite s = copySite(h);
                    if (s.near(x, z, radius)) {
                        out.accept(s);
                    }
                }
            });
        }
        if (isCopy(x - radius)) {
            return;
        }
        int c0x = Level39Cell.cellIndex(x - radius);
        int c1x = Level39Cell.cellIndex(x + radius);
        int c0z = Level39Cell.cellIndex(z - radius);
        int c1z = Level39Cell.cellIndex(z + radius);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                windowSites(seed, cx, cz, s -> {
                    if (s.near(x, z, radius)) {
                        out.accept(s);
                    }
                });
            }
        }
        Level39Layout.housesNear(seed, x, z, radius + 16, h -> {
            AnomalySite s = houseSite(h);
            if (s != null && s.near(x, z, radius)) {
                out.accept(s);
            }
        });
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                Level39Cell cell = Level39Cell.byIndex(seed, cx, cz);
                for (Level39Cell.Hole hole : cell.holes) {
                    if (Math.abs(hole.x() - x) <= radius + 12 && Math.abs(hole.z() - z) <= radius + 12) {
                        AnomalySite s = holeSite(hole, cell);
                        if (s.near(x, z, radius)) {
                            out.accept(s);
                        }
                    }
                }
                for (Level39Cell.Thing t : cell.things) {
                    if (t.kind() == Level39Cell.CAMP) {
                        AnomalySite s = campSite(t);
                        if (s.near(x, z, radius)) {
                            out.accept(s);
                        }
                    }
                }
                for (Level39Cell.Seg seg : cell.segs) {
                    if (seg.stagnant()) {
                        AnomalySite s = stagnantSite(seg);
                        if (s.near(x, z, radius)) {
                            out.accept(s);
                        }
                    }
                }
            }
        }
    }

    /** The nearest site of {@code kind} (full "level_39/..." or the short name after the slash). */
    @Nullable
    public static AnomalySite nearest(long seed, int x, int z, String kind) {
        String want = kind.contains("/") ? kind : "level_39/" + kind;
        if (!Level39Sites.KINDS.contains(want)) {
            return null;
        }
        // From inside a bigger-inside interior, search around the house it belongs to.
        int sx = isCopy(x) ? x - BIG_DX : x;
        int ccx = Level39Cell.cellIndex(sx);
        int ccz = Level39Cell.cellIndex(z);
        AnomalySite[] best = {null};
        double[] bestD = {Double.MAX_VALUE};
        Consumer<AnomalySite> take = s -> {
            if (s.kind().equals(want)) {
                double dd = s.distanceTo(sx, z);
                if (dd < bestD[0]) {
                    bestD[0] = dd;
                    best[0] = s;
                }
            }
        };
        for (int ring = 0; ring <= LOCATE_CELLS && best[0] == null; ring++) {
            for (int cx = ccx - ring; cx <= ccx + ring; cx++) {
                for (int cz = ccz - ring; cz <= ccz + ring; cz++) {
                    if (Math.max(Math.abs(cx - ccx), Math.abs(cz - ccz)) == ring) {
                        cellSites(seed, cx, cz, take);
                    }
                }
            }
        }
        return best[0];
    }

    /** Every site of one town (for locate). */
    private static void cellSites(long seed, int cellX, int cellZ, Consumer<AnomalySite> out) {
        Level39Cell cell = Level39Cell.byIndex(seed, cellX, cellZ);
        windowSites(seed, cellX, cellZ, out);
        for (Level39HouseSpec h : cell.houses) {
            AnomalySite s = houseSite(h);
            if (s != null) {
                out.accept(s);
            }
        }
        for (Level39Cell.Hole hole : cell.holes) {
            out.accept(holeSite(hole, cell));
        }
        for (Level39Cell.Thing t : cell.things) {
            if (t.kind() == Level39Cell.CAMP) {
                out.accept(campSite(t));
            }
        }
        for (Level39Cell.Seg seg : cell.segs) {
            if (seg.stagnant()) {
                out.accept(stagnantSite(seg));
            }
        }
    }

    // ==========================================================================================
    // The sites
    // ==========================================================================================

    /** The four walls: the 2D data value of the way each faces (toward the town). */
    private static final Direction[] WALLS = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

    @Nullable
    private static AnomalySite windowSiteAt(long seed, int x, int z) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        boolean xSide = Math.abs(lx) >= Math.abs(lz);
        int along = xSide ? lz : lx;
        int i = Math.floorDiv(along - Level39Sites.ALONG_MIN, Level39Sites.SEGMENT);
        if (i < 0 || i >= Level39Sites.SEGMENTS_PER_SIDE) {
            return null;
        }
        Direction wall = xSide ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
        AnomalySite s = windowSite(seed, Level39Cell.cellIndex(x), Level39Cell.cellIndex(z), wall, i);
        return s != null && s.contains(x, z) ? s : null;
    }

    private static void windowSites(long seed, int cellX, int cellZ, Consumer<AnomalySite> out) {
        for (Direction wall : WALLS) {
            for (int i = 0; i < Level39Sites.SEGMENTS_PER_SIDE; i++) {
                AnomalySite s = windowSite(seed, cellX, cellZ, wall, i);
                if (s != null) {
                    out.accept(s);
                }
            }
        }
    }

    /**
     * Facade stretch {@code i} of one wall of town (cellX, cellZ), or null if not infested. A
     * stretch whose ground-storey windows are all boarded, bricked or behind a stair tower is never
     * infested: nothing could grab you through it, so it would be a dead end for everything that
     * points at the exit.
     */
    @Nullable
    private static AnomalySite windowSite(long seed, int cellX, int cellZ, Direction wall, int i) {
        if (Hash.unit(seed, cellX * 8 + wall.get2DDataValue(), cellZ * 64 + i, 39101) >= INFESTED) {
            return null;
        }
        int ox = cellX * Level39Layout.CELL;
        int oz = cellZ * Level39Layout.CELL;
        int a0 = Level39Sites.ALONG_MIN + i * Level39Sites.SEGMENT;
        int a1 = a0 + Level39Sites.SEGMENT - 1;
        int g = Level39Sites.GLASS_LOCAL;
        int inner = g - Level39Sites.STRIP_DEPTH;
        long salt = i | (long) wall.get2DDataValue() << 8;
        int facing = wall.get2DDataValue();
        AnomalySite site = switch (wall) {
            // East wall (local x +449), facing west: its town-side face is at local x 449.
            case WEST -> new AnomalySite(Level39Sites.HOSTILE_WINDOWS, ox + inner, oz + a0, ox + g, oz + a1,
                    ox + g, oz + (a0 + a1) / 2, facing, salt);
            // West wall (local x -449): face at local x -448.
            case EAST -> new AnomalySite(Level39Sites.HOSTILE_WINDOWS, ox - g, oz + a0, ox - inner, oz + a1,
                    ox - g + 1, oz + (a0 + a1) / 2, facing, salt);
            case NORTH -> new AnomalySite(Level39Sites.HOSTILE_WINDOWS, ox + a0, oz + inner, ox + a1, oz + g,
                    ox + (a0 + a1) / 2, oz + g, facing, salt);
            default -> new AnomalySite(Level39Sites.HOSTILE_WINDOWS, ox + a0, oz - g, ox + a1, oz - inner,
                    ox + (a0 + a1) / 2, oz - g + 1, facing, salt);
        };
        for (int bay = Level39Sites.firstBay(site); bay <= Level39Sites.lastBay(site); bay++) {
            if (windowLit(seed, site, bay, 0)) {
                return site;
            }
        }
        return null;
    }

    /**
     * True if bay {@code bay} of storey {@code storey} on this facade stretch has a Window living in
     * it. Mostly the seed's choice, but every stretch keeps at least one Window behind lit
     * ground-storey glass ({@link #keptGroundBay}): only those can grab you and pull you through to
     * Level 94, and the upper storeys alone would make the stretch a death trap with no way out.
     */
    public static boolean windowInhabited(long seed, AnomalySite site, int bay, int storey) {
        if (seededInhabited(seed, site, bay, storey)) {
            return true;
        }
        return storey == 0 && bay == keptGroundBay(seed, site);
    }

    private static boolean seededInhabited(long seed, AnomalySite site, int bay, int storey) {
        return Hash.unit(seed, Level39Sites.bayColumn(site, bay), Level39Sites.glassColumn(site) * 4 + storey, 39102) < 0.55;
    }

    /**
     * The lit ground-storey bay given a Window when the seed put none behind lit ground glass (the
     * one nearest the middle of the stretch), or MIN_VALUE when the seed already did. Only ever
     * adds that one Window, so the rest of the seeded distribution is untouched.
     */
    private static int keptGroundBay(long seed, AnomalySite site) {
        int first = Level39Sites.firstBay(site);
        int last = Level39Sites.lastBay(site);
        double middle = (first + last) / 2.0;
        int best = Integer.MIN_VALUE;
        double bestOff = Double.MAX_VALUE;
        for (int bay = first; bay <= last; bay++) {
            if (!windowLit(seed, site, bay, 0)) {
                continue;
            }
            if (seededInhabited(seed, site, bay, 0)) {
                return Integer.MIN_VALUE;
            }
            double off = Math.abs(bay - middle);
            if (off < bestOff) {
                bestOff = off;
                best = bay;
            }
        }
        return best;
    }

    /**
     * True when bay {@code bay} of storey {@code storey} shows lit glass: not boarded or bricked
     * over, not hidden behind a stair tower. Only such windows wake (and the ground ones grab).
     */
    public static boolean windowLit(long seed, AnomalySite site, int bay, int storey) {
        int column = Level39Sites.glassColumn(site);
        int along = Level39Sites.bayColumn(site, bay);
        boolean xSide = Level39Sites.xSide(site);
        return Level39Superstructure.facadeWindowAt(seed, xSide ? column : along, Level39Sites.glassBottom(storey) + 2,
                xSide ? along : column);
    }

    /**
     * Where you stand to be taken through the wall: the town column in front of the nearest
     * ground-storey window that is lit and inhabited (so its Window wakes and can grab you), on
     * open ground. Searched ring by ring of towns like {@link #nearest}; null if none is near.
     */
    @Nullable
    public static int[] exitWindowSpot(long seed, int x, int z) {
        int sx = isCopy(x) ? x - BIG_DX : x;
        int ccx = Level39Cell.cellIndex(sx);
        int ccz = Level39Cell.cellIndex(z);
        for (int ring = 0; ring <= LOCATE_CELLS; ring++) {
            // First pass: the nearest window by distance alone. The open-ground check needs the
            // town tile in front of it built, so it is only asked of the winner; it almost always
            // passes (the gravel apron runs all round the wall), and the slow path below only runs
            // when a pole happens to stand right in front of that window.
            ExitCandidate best = nearestExitWindow(seed, sx, z, ccx, ccz, ring, null);
            if (best == null) {
                continue;
            }
            int[] spot = floorSpot(seed, best.site, best.bay);
            if (spot != null) {
                return spot;
            }
            List<ExitCandidate> blocked = new ArrayList<>();
            blocked.add(best);
            while ((best = nearestExitWindow(seed, sx, z, ccx, ccz, ring, blocked)) != null) {
                spot = floorSpot(seed, best.site, best.bay);
                if (spot != null) {
                    return spot;
                }
                blocked.add(best);
            }
        }
        return null;
    }

    private record ExitCandidate(AnomalySite site, int bay) {}

    /** Nearest lit, inhabited ground-storey window in one ring of towns, skipping {@code skip}. */
    @Nullable
    private static ExitCandidate nearestExitWindow(long seed, int x, int z, int ccx, int ccz, int ring,
            @Nullable List<ExitCandidate> skip) {
        AnomalySite bestSite = null;
        int bestBay = 0;
        double bestD = Double.MAX_VALUE;
        for (int cx = ccx - ring; cx <= ccx + ring; cx++) {
            for (int cz = ccz - ring; cz <= ccz + ring; cz++) {
                if (Math.max(Math.abs(cx - ccx), Math.abs(cz - ccz)) != ring) {
                    continue;
                }
                for (Direction wall : WALLS) {
                    for (int i = 0; i < Level39Sites.SEGMENTS_PER_SIDE; i++) {
                        AnomalySite site = windowSite(seed, cx, cz, wall, i);
                        if (site == null) {
                            continue;
                        }
                        for (int bay = Level39Sites.firstBay(site); bay <= Level39Sites.lastBay(site); bay++) {
                            double along = Level39Sites.bayCentre(site, bay);
                            double dx = Level39Sites.worldX(site, along, EXIT_FRONT) - x;
                            double dz = Level39Sites.worldZ(site, along, EXIT_FRONT) - z;
                            double d = dx * dx + dz * dz;
                            if (d >= bestD || !windowLit(seed, site, bay, 0) || !windowInhabited(seed, site, bay, 0)
                                    || skipped(skip, site, bay)) {
                                continue;
                            }
                            bestD = d;
                            bestSite = site;
                            bestBay = bay;
                        }
                    }
                }
            }
        }
        return bestSite != null ? new ExitCandidate(bestSite, bestBay) : null;
    }

    private static boolean skipped(@Nullable List<ExitCandidate> skip, AnomalySite site, int bay) {
        if (skip == null) {
            return false;
        }
        for (ExitCandidate c : skip) {
            if (c.bay == bay && c.site.equals(site)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Open town ground in front of the window of {@code bay}: the column right before the middle of
     * the glass, else one beside it, else one row further out (all still within grab reach); null
     * when something stands on all of them.
     */
    @Nullable
    private static int[] floorSpot(long seed, AnomalySite site, int bay) {
        int middle = Level39Sites.bayColumn(site, bay);
        for (double front = EXIT_FRONT; front <= EXIT_FRONT + 1.0; front += 1.0) {
            for (int side : SPOT_SIDES) {
                double along = middle + side + 0.5;
                int x = (int) Math.floor(Level39Sites.worldX(site, along, front));
                int z = (int) Math.floor(Level39Sites.worldZ(site, along, front));
                if (Level39Layout.townWalkable(seed, x, z)) {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }

    /** Lot-wide site of a house with an anomaly, else null. */
    @Nullable
    private static AnomalySite houseSite(Level39HouseSpec h) {
        String kind = switch (h.anomaly()) {
            case NONE -> null;
            case INVERTED -> Level39Sites.INVERTED_HOUSE;
            case MERGED -> Level39Sites.MERGED_HOUSE;
            case CUT_HALF -> Level39Sites.CUT_HOUSE;
            case FLOATING -> Level39Sites.FLOATING_HOUSE;
            case ODD_FURNITURE -> Level39Sites.ODD_FURNITURE;
            case BIGGER_INSIDE -> Level39Sites.BIGGER_INSIDE;
            case FLOODED_ROOM -> Level39Sites.FLOODED_ROOM;
            case VANISHING_DOOR -> Level39Sites.VANISHING_DOOR;
        };
        if (kind == null) {
            return null;
        }
        return new AnomalySite(kind, h.lotX0(), h.lotZ0(), h.lotX1(), h.lotZ1(), h.doorX(), h.doorZ(),
                h.facing().get2DDataValue(), Level39Sites.houseSalt(h.baseY(), h.width(), h.depth(), h.storeys(), h.id()));
    }

    /** The big interior of a bigger-inside house, in the copy region. */
    private static AnomalySite copySite(Level39HouseSpec h) {
        int[] b = Level39Houses.bigInteriorBounds(h);
        return new AnomalySite(Level39Sites.BIGGER_INSIDE, b[0] + BIG_DX, b[1], b[2] + BIG_DX, b[3],
                h.doorX() + BIG_DX, h.doorZ(), h.facing().get2DDataValue(),
                Level39Sites.houseSalt(h.baseY(), h.width(), h.depth(), h.storeys(), h.id()));
    }

    private static AnomalySite holeSite(Level39Cell.Hole hole, Level39Cell cell) {
        int r = (int) Math.ceil(hole.radius()) + HOLE_MARGIN;
        long salt = Math.round(hole.radius() * 10.0) | (cell.segs[hole.seg()].stagnant() ? 1L << 12 : 0L);
        return new AnomalySite(Level39Sites.HOLE_SOUNDS, hole.x() - r, hole.z() - r, hole.x() + r, hole.z() + r,
                hole.x(), hole.z(), 0, salt);
    }

    private static AnomalySite campSite(Level39Cell.Thing t) {
        return new AnomalySite(Level39Sites.MEG_CAMP, t.x0(), t.z0(), t.x1(), t.z1(), t.cx(), t.cz(), 0, t.salt() & 0xFFFFL);
    }

    private static AnomalySite stagnantSite(Level39Cell.Seg seg) {
        int w = Level39Layout.TUNNEL_HALF;
        int mid = (seg.a() + seg.b()) / 2;
        if (seg.alongX()) {
            return new AnomalySite(Level39Sites.HYDROLITIS, seg.a() - 3, seg.fixed() - w, seg.b() + 3, seg.fixed() + w,
                    mid, seg.fixed(), Direction.EAST.get2DDataValue(), seg.salt() & 0xFFFFL);
        }
        return new AnomalySite(Level39Sites.HYDROLITIS, seg.fixed() - w, seg.a() - 3, seg.fixed() + w, seg.b() + 3,
                seg.fixed(), mid, Direction.SOUTH.get2DDataValue(), seg.salt() & 0xFFFFL);
    }

    /** The house spec behind a house site (same seed), or null. */
    @Nullable
    public static Level39HouseSpec houseOf(long seed, AnomalySite site) {
        int x = isCopy(site.anchorX()) ? site.anchorX() - BIG_DX : site.anchorX();
        Level39HouseSpec[] found = {null};
        int id = Level39Sites.houseId(site);
        Level39Layout.housesNear(seed, x, site.anchorZ(), 2, h -> {
            if ((h.id() & 0xFFFF) == id && found[0] == null) {
                found[0] = h;
            }
        });
        return found[0];
    }
}
