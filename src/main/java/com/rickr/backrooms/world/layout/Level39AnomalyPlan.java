package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
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
 *       {@value Level39Sites#STRIP_DEPTH} blocks into the town.</li>
 *   <li>House anomalies: every house the town plan gave an anomaly (inverted, merged, cut, floating,
 *       odd furniture, bigger inside, flooded room, vanishing door) is a site covering its lot. A
 *       few ordinary houses are {@code house_screams} houses.</li>
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
    /** Chance that an ordinary house is one of the screaming houses. */
    private static final double SCREAM_HOUSE = 0.07;
    /** Hole sites reach this far past the hole's edge. */
    private static final int HOLE_MARGIN = 5;
    /** /backrooms anomaly locate looks this many towns away in each direction. */
    private static final int LOCATE_CELLS = 2;

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

    /** Facade stretch {@code i} of one wall of town (cellX, cellZ), or null if not infested. */
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
        return switch (wall) {
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
    }

    /** True if bay {@code bay} of storey {@code storey} on this facade stretch has a Window living in it. */
    public static boolean windowInhabited(long seed, AnomalySite site, int bay, int storey) {
        return Hash.unit(seed, Level39Sites.bayColumn(site, bay), Level39Sites.glassColumn(site) * 4 + storey, 39102) < 0.55;
    }

    /** Lot-wide site of a house with an anomaly (or one of the screaming houses), else null. */
    @Nullable
    private static AnomalySite houseSite(Level39HouseSpec h) {
        String kind = switch (h.anomaly()) {
            case NONE -> Hash.unit(Hash.mix(h.hash() ^ 0x5C2EAL)) < SCREAM_HOUSE ? Level39Sites.HOUSE_SCREAMS : null;
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
