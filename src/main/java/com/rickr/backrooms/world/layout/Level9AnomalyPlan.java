package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.StreetlampHeadBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaBuild;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaLine;
import com.rickr.backrooms.world.layout.LevelLayout.Column;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

import static com.rickr.backrooms.world.layout.LevelLayout.FLOOR_Y;
import static com.rickr.backrooms.world.layout.Level9Layout.LD;
import static com.rickr.backrooms.world.layout.Level9Layout.LW;

/**
 * Where Level 9's anomalous areas are, and what their blocks look like. Canon sources:
 * <ul>
 *   <li>Clipped and identical houses: "two houses weirdly clipped inside of each other when
 *       travelling too far, which is physically impossible"; "sometimes two identical houses
 *       stand near each other" (https://backrooms-wiki.wikidot.com/level-9).</li>
 *   <li>Grass walkways: "a walkway that leads to a grass field" takes you to Level 9.1, the
 *       Crimson Field (https://backrooms-wiki.wikidot.com/level-9,
 *       http://backrooms-mghc.wikidot.com/level-9-1): endless tall grass at permanent night, fog,
 *       the red Gloom on the horizon, and rare foxhole doors that lead one way to other levels.</li>
 * </ul>
 *
 * <p><b>The suburb</b> has one {@link AnomalyGrid} of 176x176 squares; about four in five squares
 * hold one site, always fully inside its square (so sites never overlap):
 * <ul>
 *   <li>{@code clipped_houses} (~30%): one ordinary lot whose house has grown into a second house
 *       pushed back and sideways into it. Walls run through rooms, a table and a counter stand
 *       half inside walls, there are two front doors, the floors of both houses checker the
 *       shared middle, and the two roofs cut through each other.</li>
 *   <li>{@code twin_houses} (~30%): two neighbouring lots with exactly the same house. Each has a
 *       small front hall behind its front door and an identical small back hall in front of its
 *       back door; step into one house's back hall and you are standing in the other house's
 *       front hall instead (the server moves you; you can't see the difference).</li>
 *   <li>{@code grass_walkway} (~20%): an empty lot between two houses with a hedge-lined concrete
 *       path running from the sidewalk to the back of the lot and on through the woods, ending in
 *       a round clearing of tall grass. Stand in the middle of the clearing and you are in the
 *       Crimson Field. Only lots with houses on both sides and enough woods behind them qualify.</li>
 * </ul>
 *
 * <p><b>The Crimson Field</b> (x &gt;= {@link Level9Sites#FIELD_MIN_X}) is ground-level grass
 * forever. Each suburb walkway has a copy here, shifted {@link Level9Sites#FIELD_DX} east
 * ({@code field_walkway}), so you arrive on the same path you left by; walking back to its start
 * (where a scrap of sidewalk and a dead streetlamp stand in the grass) takes you home. Foxholes
 * ({@code foxhole}, one 144x144 square in two) are rings of sandbags with a red lamp around a
 * wooden hatch in the ground. Rarely (one 512x512 square in three) a chairlift line
 * ({@code gondola}) stands in the grass: a small frosted loading station and steel towers
 * marching off into the fog, each taller than the last; its chair carries you up into the
 * whiteout and out into Level 39's sky ({@code world/anomaly/level39/gondola}). The whole region
 * is also covered by {@code crimson_field} tiles, so the client always has a site telling it to
 * draw the field's sky, mist and sounds.
 */
final class Level9AnomalyPlan {
    static final List<String> KINDS = List.of(Level9Sites.CLIPPED_HOUSES, Level9Sites.TWIN_HOUSES,
            Level9Sites.GRASS_WALKWAY, Level9Sites.FIELD_WALKWAY, Level9Sites.FOXHOLE, Level9Sites.GONDOLA,
            Level9Sites.CRIMSON_FIELD);

    private static final int SUBURB_CELL = 176;
    private static final int FOXHOLE_CELL = 144;
    /** Chairlift lines: at most one per square this big, wholly inside it (a line is ~300 long). */
    private static final int GONDOLA_CELL = 512;
    /** Share of field squares that have a line (only ever one in a few: it's a rare way out). */
    private static final double GONDOLA_CHANCE = 1.0 / 3.0;
    /** Lines keep this far inside their square, and this far from foxholes and walkways (blocks). */
    private static final int GONDOLA_MARGIN = 16;
    private static final int GONDOLA_CLEARANCE = 6;
    /** Search this many squares for /backrooms anomaly locate. */
    private static final int LOCATE_RINGS = 10;
    /** A walkway needs at least this much woods behind its lot (blocks). */
    private static final int MIN_WOODS = 14;
    /** The clearing is never more than this far into the woods. */
    private static final int MAX_WOODS_WALK = 10;

    private final Level9Layout layout;
    private final AnomalyGrid suburb;
    private final AnomalyGrid foxholes;
    private final AnomalyGrid gondolas;

    Level9AnomalyPlan(Level9Layout layout) {
        this.layout = layout;
        this.suburb = new AnomalyGrid(SUBURB_CELL, 0, this::planSuburb);
        this.foxholes = new AnomalyGrid(FOXHOLE_CELL, 0, this::planFoxhole);
        this.gondolas = new AnomalyGrid(GONDOLA_CELL, 0, this::planGondola);
    }

    // ==========================================================================================
    // Lookups
    // ==========================================================================================

    @Nullable
    AnomalySite siteAt(long seed, int x, int z) {
        if (!Level9Sites.inField(x)) {
            return suburb.at(seed, x, z);
        }
        AnomalySite fox = foxholes.at(seed, x, z);
        if (fox != null) {
            return fox;
        }
        AnomalySite walk = fieldWalkwayAt(seed, x, z);
        if (walk != null) {
            return walk;
        }
        AnomalySite lift = gondolas.at(seed, x, z);
        return lift != null ? lift : fieldTile(x, z);
    }

    void sitesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        if (x - radius < Level9Sites.FIELD_MIN_X) {
            suburb.near(seed, x, z, radius, site -> {
                if (!Level9Sites.inField(site.maxX())) {
                    out.accept(site);
                }
            });
        }
        if (x + radius < Level9Sites.FIELD_MIN_X) {
            return;
        }
        foxholes.near(seed, x, z, radius, out);
        gondolas.near(seed, x, z, radius, out);
        suburb.near(seed, x - Level9Sites.FIELD_DX, z, radius + Level9Sites.FIELD_START_EXTRA, site -> {
            if (site.kind().equals(Level9Sites.GRASS_WALKWAY)) {
                AnomalySite field = Level9Sites.toField(site);
                if (field.near(x, z, radius)) {
                    out.accept(field);
                }
            }
        });
        int t0 = Math.max(0, Math.floorDiv(x - radius - Level9Sites.FIELD_MIN_X, Level9Sites.FIELD_TILE));
        int t1 = Math.floorDiv(x + radius - Level9Sites.FIELD_MIN_X, Level9Sites.FIELD_TILE);
        int s0 = Math.floorDiv(z - radius, Level9Sites.FIELD_TILE);
        int s1 = Math.floorDiv(z + radius, Level9Sites.FIELD_TILE);
        for (int tx = t0; tx <= t1; tx++) {
            for (int tz = s0; tz <= s1; tz++) {
                out.accept(tile(tx, tz));
            }
        }
    }

    @Nullable
    AnomalySite nearest(long seed, int x, int z, String kind) {
        // From the field, suburb kinds are searched around the matching suburb spot.
        int suburbX = Level9Sites.inField(x) ? x - Level9Sites.FIELD_DX : x;
        return switch (kind) {
            case Level9Sites.CRIMSON_FIELD -> fieldTile(Math.max(x, Level9Sites.FIELD_MIN_X), z);
            case Level9Sites.FOXHOLE -> foxholes.nearest(seed, Math.max(x, Level9Sites.FIELD_MIN_X + FOXHOLE_CELL), z,
                    site -> true, LOCATE_RINGS);
            // "gondola" works too: it's what people type, and it is the only lift there is.
            case Level9Sites.GONDOLA, "gondola" -> gondolas.nearest(seed, Math.max(x, Level9Sites.FIELD_MIN_X + GONDOLA_CELL), z,
                    site -> true, LOCATE_RINGS);
            case Level9Sites.FIELD_WALKWAY -> {
                AnomalySite walk = suburb.nearest(seed, suburbX, z, site -> site.kind().equals(Level9Sites.GRASS_WALKWAY), LOCATE_RINGS);
                yield walk == null ? null : Level9Sites.toField(walk);
            }
            case Level9Sites.GRASS_WALKWAY, Level9Sites.TWIN_HOUSES, Level9Sites.CLIPPED_HOUSES ->
                    suburb.nearest(seed, suburbX, z, site -> site.kind().equals(kind), LOCATE_RINGS);
            default -> null;
        };
    }

    /** The field-side walkway covering (x, z), if any (field x only). */
    @Nullable
    private AnomalySite fieldWalkwayAt(long seed, int x, int z) {
        // The field copy reaches FIELD_START_EXTRA further toward the street side than the suburb
        // walkway, so also look that far up and down from the matching suburb column.
        int sx = x - Level9Sites.FIELD_DX;
        for (int dz = 0; dz <= Level9Sites.FIELD_START_EXTRA; dz += Level9Sites.FIELD_START_EXTRA) {
            for (int sign = -1; sign <= 1; sign += 2) {
                AnomalySite site = suburb.at(seed, sx, z + sign * dz);
                if (site != null && site.kind().equals(Level9Sites.GRASS_WALKWAY)) {
                    AnomalySite field = Level9Sites.toField(site);
                    if (field.contains(x, z)) {
                        return field;
                    }
                }
                if (dz == 0) {
                    break;
                }
            }
        }
        return null;
    }

    private static AnomalySite fieldTile(int x, int z) {
        return tile(Math.floorDiv(x - Level9Sites.FIELD_MIN_X, Level9Sites.FIELD_TILE), Math.floorDiv(z, Level9Sites.FIELD_TILE));
    }

    private static AnomalySite tile(int tx, int tz) {
        int x0 = Level9Sites.FIELD_MIN_X + tx * Level9Sites.FIELD_TILE;
        int z0 = tz * Level9Sites.FIELD_TILE;
        int half = Level9Sites.FIELD_TILE / 2;
        return new AnomalySite(Level9Sites.CRIMSON_FIELD, x0, z0, x0 + Level9Sites.FIELD_TILE - 1, z0 + Level9Sites.FIELD_TILE - 1,
                x0 + half, z0 + half, 0, 0L);
    }

    // ==========================================================================================
    // Planning (pure: hashing plus the layout's own street and lot maths)
    // ==========================================================================================

    @Nullable
    private AnomalySite planSuburb(long seed, int cellX, int cellZ) {
        int x0 = cellX * SUBURB_CELL;
        int z0 = cellZ * SUBURB_CELL;
        int x1 = x0 + SUBURB_CELL - 1;
        int z1 = z0 + SUBURB_CELL - 1;
        if (x1 >= Level9Sites.FIELD_MIN_X) {
            return null; // that far east is the field, not suburb
        }
        long h = Hash.of(seed, cellX, cellZ, 7700);
        double roll = Hash.unit(h);
        String kind;
        if (roll < 0.20) {
            kind = Level9Sites.GRASS_WALKWAY;
            if (x0 + Level9Sites.FIELD_DX < Level9Sites.FIELD_MIN_X) {
                return null; // its field end would fall back into the suburb
            }
        } else if (roll < 0.50) {
            kind = Level9Sites.TWIN_HOUSES;
        } else if (roll < 0.80) {
            kind = Level9Sites.CLIPPED_HOUSES;
        } else {
            return null;
        }
        // A few random points in the square; the first that lands on a suitable lot wins.
        for (int attempt = 0; attempt < 8; attempt++) {
            long ha = Hash.mix(h + 0x9E3779B97F4A7C15L * (attempt + 1));
            int px = x0 + 12 + Hash.range(ha, SUBURB_CELL - 24);
            int pz = z0 + 12 + Hash.range(Hash.mix(ha ^ 0x5BD1E995L), SUBURB_CELL - 24);
            if (layout.classify(seed, px, pz) != Level9Layout.OTHER) {
                continue;
            }
            long info = layout.lotInfo(seed, px, pz);
            if (info < 0) {
                continue;
            }
            int r = Level9Layout.infoRow(info);
            int side = Level9Layout.infoSide(info);
            int idx = Level9Layout.infoIdx(info);
            AnomalySite site = switch (kind) {
                case Level9Sites.GRASS_WALKWAY -> planWalkway(seed, r, side, idx, ha);
                case Level9Sites.TWIN_HOUSES -> planTwins(seed, r, side, idx, ha);
                default -> planClipped(seed, r, side, idx, ha);
            };
            if (site != null && site.minX() >= x0 && site.maxX() <= x1 && site.minZ() >= z0 && site.maxZ() <= z1) {
                return site;
            }
        }
        return null;
    }

    private static Direction inwardOf(int side) {
        return side == Level9Layout.SOUTH_OF_STREET ? Direction.SOUTH : Direction.NORTH;
    }

    /** Lot rows run from the front row z away from the street for {@code depth} rows. */
    private static int[] zRange(int front, int side, int depth) {
        return side == Level9Layout.SOUTH_OF_STREET ? new int[] {front, front + depth - 1} : new int[] {front - depth + 1, front};
    }

    @Nullable
    private AnomalySite planWalkway(long seed, int r, int side, int idx, long h) {
        // "Between houses": the lots on both sides keep their houses.
        if (!layout.lotValidBase(seed, r, side, idx) || !layout.lotValidBase(seed, r, side, idx - 1)
                || !layout.lotValidBase(seed, r, side, idx + 1) || layout.safeHousePicked(seed, r, side, idx)) {
            return null;
        }
        int x0 = layout.lotX0(seed, r, side, idx);
        int front = layout.lotFrontZ(seed, r, side, x0);
        int woods = layout.woodsDepth(seed, r, x0);
        if (woods < MIN_WOODS || layout.woodsDepth(seed, r, x0 + LW - 1) != woods
                || layout.lotFrontZ(seed, r, side, x0 + LW - 1) != front) {
            return null; // too little forest behind it, or a street bends somewhere along it
        }
        int length = LD + Math.min(MAX_WOODS_WALK, woods / 2 - 1);
        int[] z = zRange(front, side, length + Level9Sites.CLEARING_RADIUS + 2);
        int clearingZ = side == Level9Layout.SOUTH_OF_STREET ? front + length : front - length;
        return new AnomalySite(Level9Sites.GRASS_WALKWAY, x0, z[0], x0 + LW - 1, z[1],
                x0 + Level9Sites.WALKWAY_PATH_U + 1, clearingZ, inwardOf(side).get2DDataValue(), length);
    }

    @Nullable
    private AnomalySite planTwins(long seed, int r, int side, int idx, long h) {
        if (!layout.lotValidBase(seed, r, side, idx) || !layout.lotValidBase(seed, r, side, idx + 1)
                || layout.safeHousePicked(seed, r, side, idx) || layout.safeHousePicked(seed, r, side, idx + 1)) {
            return null;
        }
        int x0 = layout.lotX0(seed, r, side, idx);
        int front = layout.lotFrontZ(seed, r, side, x0);
        if (layout.lotFrontZ(seed, r, side, x0 + 2 * LW - 1) != front) {
            return null;
        }
        int[] z = zRange(front, side, LD);
        // Salt: the shared colour and fences of the pair.
        return new AnomalySite(Level9Sites.TWIN_HOUSES, x0, z[0], x0 + 2 * LW - 1, z[1],
                x0, front, inwardOf(side).get2DDataValue(), Hash.mix(h ^ 0x7A11L) & 0xFFFFL);
    }

    @Nullable
    private AnomalySite planClipped(long seed, int r, int side, int idx, long h) {
        if (!layout.lotValidBase(seed, r, side, idx) || layout.safeHousePicked(seed, r, side, idx)) {
            return null;
        }
        int x0 = layout.lotX0(seed, r, side, idx);
        int front = layout.lotFrontZ(seed, r, side, x0);
        int[] z = zRange(front, side, LD);
        // Salt bit 0: mirrored (B grows into A from the other side); the rest: colours and fences.
        return new AnomalySite(Level9Sites.CLIPPED_HOUSES, x0, z[0], x0 + LW - 1, z[1],
                x0, front, inwardOf(side).get2DDataValue(), Hash.mix(h ^ 0xC11BL) & 0xFFFFL);
    }

    @Nullable
    private AnomalySite planFoxhole(long seed, int cellX, int cellZ) {
        int x0 = cellX * FOXHOLE_CELL;
        int z0 = cellZ * FOXHOLE_CELL;
        if (x0 < Level9Sites.FIELD_MIN_X + 16) {
            return null;
        }
        long h = Hash.of(seed, cellX, cellZ, 7720);
        if (Hash.unit(h) >= 0.5) {
            return null;
        }
        int ax = x0 + 8 + Hash.range(Hash.mix(h ^ 1), FOXHOLE_CELL - 16);
        int az = z0 + 8 + Hash.range(Hash.mix(h ^ 2), FOXHOLE_CELL - 16);
        // Never on (or right beside) a walkway's field end.
        boolean[] clash = {false};
        suburb.near(seed, ax - Level9Sites.FIELD_DX, az, 16, site -> {
            if (site.kind().equals(Level9Sites.GRASS_WALKWAY) && Level9Sites.toField(site).near(ax, az, 12)) {
                clash[0] = true;
            }
        });
        if (clash[0]) {
            return null;
        }
        int rr = Level9Sites.FOXHOLE_RADIUS;
        return new AnomalySite(Level9Sites.FOXHOLE, ax - rr, az - rr, ax + rr, az + rr, ax, az,
                Hash.range(Hash.mix(h ^ 3), 4), Hash.mix(h ^ 4) & 0xFFFFL);
    }

    /**
     * A chairlift line, in one field square in three. It runs in a random compass direction from
     * its station and must fit wholly inside its square (so lines never overlap), well clear of
     * every foxhole and walkway (it is drawn in their place otherwise). A few placements are
     * tried; if none is clear the square simply has no lift. The line's towers come from the salt
     * ({@link GondolaLine}).
     */
    @Nullable
    private AnomalySite planGondola(long seed, int cellX, int cellZ) {
        int x0 = cellX * GONDOLA_CELL;
        int z0 = cellZ * GONDOLA_CELL;
        if (x0 < Level9Sites.FIELD_MIN_X + GONDOLA_MARGIN) {
            return null;
        }
        long h = Hash.of(seed, cellX, cellZ, 7740);
        if (Hash.unit(h) >= GONDOLA_CHANCE) {
            return null;
        }
        // Positive 32-bit salt: the towers, and where in Level 39 the ride comes out.
        long salt = Hash.mix(h ^ 0x60D0L) & 0xFFFFFFFFL;
        for (int attempt = 0; attempt < 6; attempt++) {
            long ha = Hash.mix(h + 0x9E3779B97F4A7C15L * (attempt + 1));
            int facing = Hash.range(ha, 4);
            // The rectangle the line covers if its station stood at (0, 0): shift it into the square.
            AnomalySite shape = GondolaLine.site(0, 0, facing, salt);
            int ax0 = x0 + GONDOLA_MARGIN - shape.minX();
            int ax1 = x0 + GONDOLA_CELL - 1 - GONDOLA_MARGIN - shape.maxX();
            int az0 = z0 + GONDOLA_MARGIN - shape.minZ();
            int az1 = z0 + GONDOLA_CELL - 1 - GONDOLA_MARGIN - shape.maxZ();
            if (ax1 < ax0 || az1 < az0) {
                continue;
            }
            int ax = ax0 + Hash.range(Hash.mix(ha ^ 1), ax1 - ax0 + 1);
            int az = az0 + Hash.range(Hash.mix(ha ^ 2), az1 - az0 + 1);
            AnomalySite site = GondolaLine.site(ax, az, facing, salt);
            if (!gondolaBlocked(seed, site)) {
                return site;
            }
        }
        return null;
    }

    /** True if a foxhole or a field walkway comes within GONDOLA_CLEARANCE of the line's rectangle. */
    private boolean gondolaBlocked(long seed, AnomalySite line) {
        int m = GONDOLA_CLEARANCE;
        // Foxholes always lie wholly inside their own square, so these squares are all there is to check.
        for (int cx = Math.floorDiv(line.minX() - m, FOXHOLE_CELL); cx <= Math.floorDiv(line.maxX() + m, FOXHOLE_CELL); cx++) {
            for (int cz = Math.floorDiv(line.minZ() - m, FOXHOLE_CELL); cz <= Math.floorDiv(line.maxZ() + m, FOXHOLE_CELL); cz++) {
                AnomalySite fox = foxholes.site(seed, cx, cz);
                if (fox != null && overlaps(line, fox, m)) {
                    return true;
                }
            }
        }
        int cx = (line.minX() + line.maxX()) / 2;
        int cz = (line.minZ() + line.maxZ()) / 2;
        int radius = Math.max(line.maxX() - line.minX(), line.maxZ() - line.minZ()) / 2 + 1 + m + Level9Sites.FIELD_START_EXTRA;
        boolean[] clash = {false};
        suburb.near(seed, cx - Level9Sites.FIELD_DX, cz, radius, site -> {
            if (site.kind().equals(Level9Sites.GRASS_WALKWAY) && overlaps(line, Level9Sites.toField(site), m)) {
                clash[0] = true;
            }
        });
        return clash[0];
    }

    private static boolean overlaps(AnomalySite a, AnomalySite b, int margin) {
        return a.minX() - margin <= b.maxX() && b.minX() <= a.maxX() + margin
                && a.minZ() - margin <= b.maxZ() && b.minZ() <= a.maxZ() + margin;
    }

    // ==========================================================================================
    // Questions the layout asks
    // ==========================================================================================

    @Nullable
    private AnomalySite walkwayAt(long seed, int x, int z) {
        AnomalySite site = suburb.at(seed, x, z);
        return site != null && site.kind().equals(Level9Sites.GRASS_WALKWAY) ? site : null;
    }

    /** True if a grass walkway takes up lot (r, side, idx) (that lot then has no house). */
    boolean isWalkwayLot(long seed, int r, int side, int idx) {
        int x0 = layout.lotX0(seed, r, side, idx);
        AnomalySite site = walkwayAt(seed, x0 + Level9Sites.WALKWAY_PATH_U, layout.lotFrontZ(seed, r, side, x0));
        return site != null && site.minX() == x0;
    }

    /** Woods columns where no tree trunk may stand: the walkway lot, the path and the clearing. */
    boolean blocksTrees(long seed, int x, int z) {
        AnomalySite site = walkwayAt(seed, x, z);
        if (site == null) {
            return false;
        }
        if (Level9Sites.walkwayAlong(site, z + 0.5) < LD || Math.abs(x + 0.5 - site.anchorX()) <= 3.0) {
            return true;
        }
        double dx = x + 0.5 - Level9Sites.clearingX(site);
        double dz = z + 0.5 - Level9Sites.clearingZ(site);
        double reach = Level9Sites.CLEARING_RADIUS + 2.5;
        return dx * dx + dz * dz <= reach * reach;
    }

    /** No tree crowns over the clearing: it must read as open field from the path. */
    boolean inWalkwayClearing(long seed, int x, int z) {
        AnomalySite site = walkwayAt(seed, x, z);
        return site != null && Level9Sites.inClearing(site, x, z);
    }

    /**
     * Configures the house of lot (r, side, idx) if it belongs to twin or clipped houses; returns
     * true if it did (the layout then never makes it a safe house).
     */
    boolean dressHouse(long seed, int r, int side, int idx, Level9Layout.House h) {
        int x0 = layout.lotX0(seed, r, side, idx);
        int front = layout.lotFrontZ(seed, r, side, x0);
        int midZ = side == Level9Layout.SOUTH_OF_STREET ? front + LD / 2 : front - LD / 2;
        AnomalySite site = suburb.at(seed, x0 + LW / 2, midZ);
        if (site == null) {
            return false;
        }
        if (site.kind().equals(Level9Sites.TWIN_HOUSES) && (site.anchorX() == x0 || site.anchorX() == x0 - LW)) {
            dressTwin(h, site.salt());
            return true;
        }
        if (site.kind().equals(Level9Sites.CLIPPED_HOUSES) && site.anchorX() == x0) {
            dressClipped(h, site.salt());
            return true;
        }
        return false;
    }

    /** No extras: no porch light, crate, backpack, clay door or glitch ghost on an anomaly house. */
    private static void plain(Level9Layout.House h) {
        h.storeys = 1;
        h.garage = false;
        h.unlocked = true;
        h.clayDoor = false;
        h.porchLight = false;
        h.glitch = false;
        h.crate = false;
        h.porchBackpack = false;
        h.drivewayBackpack = false;
        h.safe = false;
        h.roofBase = 41 + 5;
        h.partitionU = Integer.MIN_VALUE;
    }

    /** Both twins get exactly these numbers, so they are identical inside and out. */
    private static void dressTwin(Level9Layout.House h, long salt) {
        plain(h);
        h.twin = true;
        h.width = Level9Sites.TWIN_WIDTH;
        h.depth = Level9Sites.TWIN_DEPTH;
        h.hu0 = Level9Sites.TWIN_HU0;
        h.hu1 = h.hu0 + h.width - 1;
        h.setback = Level9Sites.TWIN_SETBACK;
        h.doorU = Level9Sites.TWIN_DOOR_U;
        h.porchU0 = h.doorU - 1;
        h.porchU1 = h.doorU + 1;
        h.ridgeAlongU = true;
        h.colour = (int) (salt % 5);
        h.frontFence = ((salt >> 4) & 1) == 0;
        h.sideFence = (int) ((salt >> 5) % 3);
        h.backFence = ((salt >> 7) & 1) == 0 ? 2 : 1;
    }

    /** The lot's own House stays as the "holder"; the two clipped houses hang off it. */
    private static void dressClipped(Level9Layout.House h, long salt) {
        plain(h);
        boolean mirror = (salt & 1L) != 0;
        int colourA = (int) ((salt >> 1) % 5);
        int colourB = (colourA + 1 + (int) ((salt >> 4) % 4)) % 5;
        Level9Layout.House a = clipHouse(h, Level9Sites.CLIP_A_U0, Level9Sites.CLIP_A_U1, Level9Sites.CLIP_A_V0,
                Level9Sites.CLIP_A_V1, Level9Sites.CLIP_A_DOOR_U, colourA, true);
        Level9Layout.House b = clipHouse(h, Level9Sites.CLIP_B_U0, Level9Sites.CLIP_B_U1, Level9Sites.CLIP_B_V0,
                Level9Sites.CLIP_B_V1, Level9Sites.CLIP_B_DOOR_U, colourB, false);
        h.clipA = a;
        h.clipB = b;
        h.clipMirror = mirror;
        // The holder's numbers only matter for the yard rules (fences, street trees).
        h.hu0 = -100;
        h.hu1 = -100;
        h.width = 0;
        h.setback = Level9Sites.CLIP_A_V0;
        h.porchU0 = -10;
        h.porchU1 = -10;
        h.frontFence = false;
        h.sideFence = (int) ((salt >> 7) % 3);
        h.backFence = ((salt >> 9) & 1) == 0 ? 2 : 1;
        h.doorU = mirror ? LW - 1 - Level9Sites.CLIP_A_DOOR_U : Level9Sites.CLIP_A_DOOR_U;
        h.clipDoorBU = mirror ? LW - 1 - Level9Sites.CLIP_B_DOOR_U : Level9Sites.CLIP_B_DOOR_U;
    }

    private static Level9Layout.House clipHouse(Level9Layout.House holder, int u0, int u1, int v0, int v1, int door,
                                                int colour, boolean ridgeAlongU) {
        Level9Layout.House c = new Level9Layout.House();
        plain(c);
        c.hu0 = u0;
        c.hu1 = u1;
        c.width = u1 - u0 + 1;
        c.setback = v0;
        c.depth = v1 - v0 + 1;
        c.doorU = door;
        c.colour = colour;
        // The two roofs run crosswise, so they slice through each other.
        c.ridgeAlongU = ridgeAlongU;
        c.inward = holder.inward;
        c.front = holder.front;
        c.porchU0 = -10;
        c.porchU1 = -10;
        return c;
    }

    // ==========================================================================================
    // Blocks: twin houses
    // ==========================================================================================

    /**
     * Inside a twin house (lot coordinates, one storey). Behind the front door is a 3x2 front hall
     * with a door into the rooms; in front of the back door an identical 3x2 back hall. Both halls
     * are walled in the house's own siding (inside and out), so standing in either one, door
     * behind you and door ahead, they look exactly the same. The rest is one U-shaped living room
     * around the halls: sofa, TV, a dining table, kitchen counter and fridge.
     */
    static void twinInterior(Level9Layout.House h, int u, int v, Column out) {
        int s = h.setback;
        int door = h.doorU;
        int frontHall = Level9Sites.TWIN_FRONT_HALL_V;
        int backHall = Level9Sites.TWIN_BACK_HALL_V;
        boolean hallRows = v >= frontHall && v <= frontHall + 1 || v >= backHall && v <= backHall + 1;
        boolean hallSide = (u == door - 2 || u == door + 2) && hallRows;
        boolean cross = u >= door - 2 && u <= door + 2 && (v == frontHall + 2 || v == backHall - 1);
        if (hallSide || cross) {
            BlockState wall = Level9Layout.siding(h.colour);
            for (int y = 42; y <= 45; y++) {
                out.set(y, wall);
            }
            if (cross && u == door) {
                BlockState inner = ModBlocks.HOUSE_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, h.inward)
                        .setValue(HouseDoorBlock.LOCKED, false);
                out.set(42, inner.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(43, inner.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            return;
        }
        int back = s + h.depth - 2;
        Direction east = Level9Layout.House.alongU(true);
        Direction west = Level9Layout.House.alongU(false);
        BlockState piece = null;
        if (u == h.hu0 + 1 && (v == frontHall + 3 || v == frontHall + 4)) {
            piece = Level9Layout.facing(ModBlocks.SOFA.get().defaultBlockState(), east);
        } else if (u == h.hu1 - 1 && v == frontHall + 4) {
            piece = Level9Layout.facing(ModBlocks.TV.get().defaultBlockState(), west);
        } else if (u == door && v == frontHall + 4) {
            piece = Level9Layout.facing(ModBlocks.DINING_TABLE.get().defaultBlockState(), h.front);
        } else if (v == back && u == h.hu1 - 2) {
            piece = Level9Layout.facing(ModBlocks.KITCHEN_COUNTER.get().defaultBlockState(), h.front);
        } else if (v == back && u == h.hu1 - 1) {
            piece = Level9Layout.facing(ModBlocks.FRIDGE.get().defaultBlockState(), h.front);
        }
        if (piece != null) {
            out.set(42, piece);
        }
    }

    // ==========================================================================================
    // Blocks: clipped houses
    // ==========================================================================================

    /** Column (u, v) of a clipped-houses lot (lawn already laid). */
    void fillClipped(long seed, int x, int z, Level9Layout.House h, int u, int v, Column out) {
        Level9Layout.House a = h.clipA;
        Level9Layout.House b = h.clipB;
        boolean mirror = h.clipMirror;
        int cu = mirror ? LW - 1 - u : u;
        boolean inA = Level9Sites.clipInA(cu, v);
        boolean inB = Level9Sites.clipInB(cu, v);
        if (!inA && !inB) {
            clippedYard(seed, x, z, h, u, cu, v, out);
        } else {
            clippedBody(a, b, cu, v, mirror, out);
        }
        // Both roofs, each where it would be on its own; where they meet they cut into each other.
        clippedRoof(a, cu, v, mirror, out);
        clippedRoof(b, cu, v, mirror, out);
        BlockState piece = clippedFurniture(a, b, cu, v);
        if (piece != null) {
            out.set(42, mirror ? piece.mirror(Mirror.FRONT_BACK) : piece);
        }
    }

    private void clippedYard(long seed, int x, int z, Level9Layout.House h, int u, int cu, int v, Column out) {
        BlockState concrete = ModBlocks.SIDEWALK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        if (cu == Level9Sites.CLIP_A_DOOR_U && v < Level9Sites.CLIP_A_V0 || cu == Level9Sites.CLIP_B_DOOR_U && v < Level9Sites.CLIP_B_V0) {
            out.set(FLOOR_Y + 1, concrete); // a path to each front door
            return;
        }
        int fence = Level9Layout.fenceKind(h, u, v);
        if (fence == 1) {
            out.set(42, ModBlocks.HEDGE.get().defaultBlockState());
            out.set(43, ModBlocks.HEDGE.get().defaultBlockState());
        } else if (fence == 2) {
            out.set(42, layout.picket(seed, x, z));
        } else if (cu == Level9Sites.CLIP_A_DOOR_U - 1 && v == 0) {
            out.set(42, Level9Layout.facing(ModBlocks.MAILBOX.get().defaultBlockState(), h.front));
        }
    }

    private static void clippedBody(Level9Layout.House a, Level9Layout.House b, int cu, int v, boolean mirror, Column out) {
        boolean wallA = Level9Sites.clipWallA(cu, v);
        boolean wallB = Level9Sites.clipWallB(cu, v);
        boolean gap = Level9Sites.clipGap(cu, v);
        boolean foreign = Level9Sites.clipForeignWall(cu, v);
        boolean roomA = Level9Sites.clipInteriorA(cu, v);
        boolean roomB = Level9Sites.clipInteriorB(cu, v);
        Level9Layout.House owner = wallA ? a : b;
        BlockState wallBlock = Level9Layout.siding(owner.colour);
        BlockState trim = ModBlocks.HOUSE_TRIM.get().defaultBlockState();
        BlockState carpet = ModBlocks.BEIGE_CARPET.get().defaultBlockState();
        BlockState wood = ModBlocks.WOOD_FLOOR.get().defaultBlockState();

        // The floor: A has carpet, B wood; where both houses claim the floor they take turns.
        BlockState floor;
        if ((wallA || wallB) && !foreign && !gap) {
            floor = ModBlocks.BRICK_FOUNDATION.get().defaultBlockState();
        } else if (roomA && roomB) {
            floor = ((cu + v) & 1) == 0 ? carpet : wood;
        } else if (roomA || Level9Sites.clipInA(cu, v) && !roomB) {
            floor = carpet;
        } else {
            floor = wood;
        }
        out.set(41, floor);
        out.set(46, trim); // the ceiling (or the wall plate on the walls below)

        if (!(wallA || wallB)) {
            for (int y = 42; y <= 45; y++) {
                out.set(y, Level9Layout.AIR);
            }
            return;
        }
        boolean corner = isCorner(owner, cu, v);
        for (int y = 42; y <= 45; y++) {
            out.set(y, corner ? trim : wallBlock);
        }
        if (gap) {
            // A doorway through the wall that shouldn't be there.
            out.set(42, Level9Layout.AIR);
            out.set(43, Level9Layout.AIR);
            return;
        }
        if (!foreign && !corner) {
            out.set(46, wallBlock);
            boolean coveredByOther = owner == a ? Level9Sites.clipInB(cu, v) : Level9Sites.clipInA(cu, v);
            if (!coveredByOther && clipWindow(owner, cu, v)) {
                BlockState window = Level9Layout.facing(ModBlocks.HOUSE_WINDOW.get().defaultBlockState(), outward(owner, cu, v));
                window = mirror ? window.mirror(Mirror.FRONT_BACK) : window;
                out.set(43, window);
                out.set(44, window);
            }
            if (v == owner.setback && cu == owner.doorU) {
                BlockState door = ModBlocks.HOUSE_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, owner.inward)
                        .setValue(HouseDoorBlock.LOCKED, false);
                door = mirror ? door.mirror(Mirror.FRONT_BACK) : door;
                out.set(42, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(43, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            int gable = Level9Layout.gableLayers(owner, cu, v);
            for (int i = 1; i < gable; i++) {
                out.set(owner.roofBase + i, wallBlock);
            }
        }
    }

    private static boolean isCorner(Level9Layout.House h, int cu, int v) {
        return (cu == h.hu0 || cu == h.hu1) && (v == h.setback || v == h.setback + h.depth - 1);
    }

    /** Windows every third column of an outside wall, never right beside the front door. */
    private static boolean clipWindow(Level9Layout.House h, int cu, int v) {
        int s = h.setback;
        if (v == s || v == s + h.depth - 1) {
            if (v == s && Math.abs(cu - h.doorU) <= 1) {
                return false;
            }
            int w = cu - h.hu0;
            return w % 3 == 2 && w < h.width - 1;
        }
        int w = v - s;
        return w % 3 == 2 && w < h.depth - 1;
    }

    private static Direction outward(Level9Layout.House h, int cu, int v) {
        if (v == h.setback) return h.front;
        if (v == h.setback + h.depth - 1) return h.inward;
        return Level9Layout.House.alongU(cu == h.hu1);
    }

    private static void clippedRoof(Level9Layout.House h, int cu, int v, boolean mirror, Column out) {
        BlockState roof = Level9Layout.roofAt(h, cu, v);
        if (roof != null) {
            out.set(h.roofBase + Level9Layout.roofLayer(h, cu, v), mirror ? roof.mirror(Mirror.FRONT_BACK) : roof);
        }
    }

    /**
     * Each house's own furniture, placed as if the other house weren't there: A's dining table
     * stands inside B's wall and B's table and a counter inside A's walls (the wall rests on top
     * of them), A's kitchen fills the shared middle and B's bed sits beside it.
     */
    @Nullable
    private static BlockState clippedFurniture(Level9Layout.House a, Level9Layout.House b, int cu, int v) {
        BlockState sofa = ModBlocks.SOFA.get().defaultBlockState();
        BlockState table = ModBlocks.DINING_TABLE.get().defaultBlockState();
        BlockState counter = ModBlocks.KITCHEN_COUNTER.get().defaultBlockState();
        // House A.
        if (v == 12 && (cu == 2 || cu == 3)) return Level9Layout.facing(sofa, a.front);
        if (v == 6 && cu == 2) return Level9Layout.facing(ModBlocks.TV.get().defaultBlockState(), a.inward);
        if (v == 10 && cu == Level9Sites.CLIP_B_U0) return Level9Layout.facing(table, a.front);
        if (v == 12 && cu >= 6 && cu <= 8) return Level9Layout.facing(counter, a.front);
        if (v == 12 && cu == 9) return Level9Layout.facing(ModBlocks.FRIDGE.get().defaultBlockState(), a.front);
        // House B.
        if (v == 15 && (cu == 7 || cu == 8)) return Level9Layout.facing(sofa, b.front);
        if (v == 9 && cu == 13) return Level9Layout.facing(ModBlocks.TV.get().defaultBlockState(), b.inward);
        if (v == Level9Sites.CLIP_A_V1 && cu == 8) return Level9Layout.facing(table, b.front);
        if (v == 10 && cu == Level9Sites.CLIP_A_U1) return Level9Layout.facing(counter, Level9Layout.House.alongU(false));
        if (cu == 9 && (v == 9 || v == 10)) {
            // HouseBedBlock: FACING points from the foot to the head.
            return Level9Layout.facing(ModBlocks.BED.get().defaultBlockState(), b.front).setValue(HouseBedBlock.HEAD, v == 9);
        }
        return null;
    }

    // ==========================================================================================
    // Blocks: the grass walkway (suburb side)
    // ==========================================================================================

    /**
     * Draws column (x, z) if it is part of a grass walkway: the whole lot (lawn, the 2-wide
     * concrete path, hedges on both sides of it) and, in the woods behind, the path and the round
     * clearing of tall grass at its end. Returns false for the ordinary woods beside the path.
     */
    boolean fillWalkway(long seed, int x, int z, Column out) {
        AnomalySite site = walkwayAt(seed, x, z);
        if (site == null) {
            return false;
        }
        double along = Level9Sites.walkwayAlong(site, z + 0.5);
        boolean lotPart = along < LD;
        if (Level9Sites.onWalkwayPath(site, x) && along <= site.salt() + 0.5) {
            out.set(FLOOR_Y + 1, ModBlocks.SIDEWALK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            if (!lotPart && Hash.unit(seed, x, z, 7731) < 0.15) {
                out.set(FLOOR_Y + 2, ModBlocks.LEAF_LITTER.get().defaultBlockState()); // blown in from the woods
            }
            return true;
        }
        if (Level9Sites.inClearing(site, x, z)) {
            if (Hash.unit(seed, x, z, 7732) < 0.9) {
                out.set(FLOOR_Y + 2, ModBlocks.L9_FIELD_GRASS.get().defaultBlockState()); // the field showing through
            }
            return true;
        }
        if (lotPart) {
            int u = x - site.minX();
            boolean hedge = (u == Level9Sites.WALKWAY_PATH_U - 2 || u == Level9Sites.WALKWAY_PATH_U + 3) && along >= 2;
            if (hedge) {
                out.set(42, ModBlocks.HEDGE.get().defaultBlockState());
                out.set(43, ModBlocks.HEDGE.get().defaultBlockState());
            } else if (Hash.unit(seed, x, z, 7733) < 0.12) {
                out.set(FLOOR_Y + 2, ModBlocks.LEAF_LITTER.get().defaultBlockState());
            }
            return true;
        }
        return false;
    }

    // ==========================================================================================
    // Blocks: the Crimson Field
    // ==========================================================================================

    /** One column of Level 9.1: ground, knee-to-waist-high grass, and the odd walkway, foxhole or lift. */
    void fillField(long seed, int x, int z, Column out) {
        BlockState ground = ModBlocks.LAWN_GRASS.get().defaultBlockState();
        out.set(FLOOR_Y, ground);
        out.set(FLOOR_Y + 1, ground);
        AnomalySite walk = fieldWalkwayAt(seed, x, z);
        if (walk != null && fillFieldWalkway(walk, x, z, out)) {
            return;
        }
        AnomalySite fox = foxholes.at(seed, x, z);
        if (fox != null) {
            fillFoxhole(fox, x, z, out);
            return;
        }
        AnomalySite lift = gondolas.at(seed, x, z);
        if (lift != null && GondolaBuild.fill(lift, x, z, out)) {
            return; // the station, or a tower's pier and braces (grass grows between the towers)
        }
        if (Hash.unit(seed, x, z, 7710) < 0.92) {
            out.set(FLOOR_Y + 2, ModBlocks.L9_FIELD_GRASS.get().defaultBlockState());
        }
    }

    /**
     * The walkway's field end: the same concrete path, and in front of where it starts a scrap of
     * sidewalk with a dead streetlamp leaning over nothing, the only hint of the way home.
     */
    private static boolean fillFieldWalkway(AnomalySite site, int x, int z, Column out) {
        double along = Level9Sites.walkwayAlong(site, z + 0.5);
        if (Level9Sites.onWalkwayPath(site, x) && along >= 0 && along <= site.salt() + 0.5) {
            out.set(FLOOR_Y + 1, ModBlocks.SIDEWALK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            return true;
        }
        double dx = x + 0.5 - site.anchorX();
        if (x == site.anchorX() + 3 && along < -1 && along >= -4) {
            // Arm (two blocks) and the dead head, reaching out over where the road would be.
            Direction towardStreet = Level9Sites.inward(site).getOpposite();
            boolean head = along < -3;
            BlockState state = head
                    ? ModBlocks.STREETLAMP_HEAD.get().defaultBlockState().setValue(StreetlampHeadBlock.MODE, LampMode.DEAD)
                    : ModBlocks.STREETLAMP_ARM.get().defaultBlockState();
            out.set(47, state.setValue(BlockStateProperties.HORIZONTAL_FACING, towardStreet));
        }
        if (along < 0 && along >= -2 && Math.abs(dx) <= 4) {
            out.set(FLOOR_Y + 1, ModBlocks.SIDEWALK_SLAB.get().defaultBlockState());
            if (x == site.anchorX() + 3 && along > -1) {
                BlockState pole = ModBlocks.STREETLAMP_POLE.get().defaultBlockState();
                for (int y = FLOOR_Y + 1; y <= 47; y++) {
                    out.set(y, pole);
                }
            }
            return true;
        }
        return false; // grass still grows under the lamp's arm
    }

    /**
     * A foxhole: a ring of sandbags (one gap to walk in by, a red lamp on a corner: in the field
     * red light is the only safe light) around a wooden hatch set flush in the ground. Under the
     * hatch a narrow shaft drops away into the dark.
     */
    private static void fillFoxhole(AnomalySite site, int x, int z, Column out) {
        int dx = x - site.anchorX();
        int dz = z - site.anchorZ();
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        BlockState soil = ModBlocks.L9_CRATER_SOIL.get().defaultBlockState();
        Direction gapSide = Direction.from2DDataValue(site.facing());
        if (ring == 0) {
            out.set(FLOOR_Y, Level9Layout.AIR);
            out.set(FLOOR_Y + 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(TrapDoorBlock.FACING, gapSide)
                    .setValue(TrapDoorBlock.OPEN, false));
            out.set(FLOOR_Y - 6, soil); // the bottom of the shaft (you're gone long before)
        } else if (ring == 1) {
            for (int y = FLOOR_Y - 6; y <= FLOOR_Y + 1; y++) {
                out.set(y, soil); // muddy ground around the hatch and the shaft walls under it
            }
        } else if (ring == 2) {
            boolean gap = dx == 2 * gapSide.getStepX() && dz == 2 * gapSide.getStepZ();
            if (!gap) {
                out.set(FLOOR_Y + 2, ModBlocks.L9_SANDBAGS.get().defaultBlockState());
                Direction corner = gapSide.getOpposite();
                Direction cornerSide = gapSide.getClockWise();
                if (dx == 2 * corner.getStepX() + 2 * cornerSide.getStepX() && dz == 2 * corner.getStepZ() + 2 * cornerSide.getStepZ()) {
                    out.set(FLOOR_Y + 3, Blocks.REDSTONE_TORCH.defaultBlockState());
                }
            }
        }
        // ring 3: trampled flat, no grass.
    }
}
