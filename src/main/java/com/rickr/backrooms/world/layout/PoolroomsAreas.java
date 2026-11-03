package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.PoolLampBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Poolrooms' big anomalous areas: whole blocks of rooms that are one strange space, cut out of
 * the room grid of {@link PoolroomsLayout} (which builds their outer walls, with doorways, and asks
 * here for everything inside). Pure functions of the seed and the room grid, like the rest of the
 * level. Three kinds:
 *
 * <p><b>Sky courts</b> ({@value #SKY_COURT}, 2 x 2 rooms): an open-air courtyard pool. Plain grey
 * concrete walls rise 17 blocks to an open roof, painted sky over it and two heavy concrete beams
 * across it; a bright turquoise pool fills the court, a tiled deck round it, and along one side a
 * deep concrete overhang shelters a dark covered deck. The doorways out are dark holes in the
 * concrete. The only daylit place in the level apart from the atriums.
 *
 * <p><b>Glow halls</b> ({@value #GLOW_HALL}, 1 x 4 rooms): a long, tall, dark hall. Down its middle
 * runs a 5-wide channel of water lit from below, the brightest thing in it; a narrow walkway on each
 * side, then a row of tall tiled piers with openings between them into low, black side aisles. A
 * strip light hangs in every other opening, its reflection lying in the water; high above, dark
 * slots in the upper walls. Side view across the hall (# tile, ~ water, = the strip lights):
 * <pre>
 *   ###############
 *   ###.........###   y61 ceiling
 *   ##  .........  ##  slots in the upper walls
 *   ###.........###
 *   #  =.......=  #   piers and openings, aisles behind
 *   #  #.~~~~~.#  #
 * </pre>
 *
 * <p><b>Tangles</b> ({@value #TANGLE}, 2 x 2 rooms): a dark, tall room flooded deep, crossed by a
 * tangle of narrow tiled walkways, raised ledges and blocks, some reaching out from the rim, some
 * standing alone in the water. No light but the lamps in the walkways' sides under the water:
 * from the rim it is black shapes over glowing cyan.
 *
 * <p>All three avoid atriums, pillared halls, safe rooms, the sunlit archway, the exit hall and
 * each other; the dark corners and stair pits never fall inside them. The Sanguine Reservoir has
 * them too (its sky is walled over and its strip lights are dead, like everywhere there).
 */
public final class PoolroomsAreas {
    public static final String SKY_COURT = "poolrooms/sky_court";
    public static final String GLOW_HALL = "poolrooms/glow_hall";
    public static final String TANGLE = "poolrooms/tangle";
    static final List<String> KINDS = List.of(SKY_COURT, GLOW_HALL, TANGLE);

    private static final int CELL = PoolroomsLayout.CELL;
    /** At most one area of each kind per 15 x 15 rooms (210 x 210 blocks) ... */
    private static final int REGION_CELLS = 15;
    /** ... in this share of those squares. */
    private static final double SHARE = 0.55;
    /** Rooms of a square tried before it gives up (most squares have an atrium or a hall somewhere). */
    private static final int TRIES = 8;

    static final AnomalyGrid COURTS = new AnomalyGrid(REGION_CELLS * CELL, 0, (s, gx, gz) -> plan(s, gx, gz, SKY_COURT));
    static final AnomalyGrid HALLS = new AnomalyGrid(REGION_CELLS * CELL, 0, (s, gx, gz) -> plan(s, gx, gz, GLOW_HALL));
    static final AnomalyGrid TANGLES = new AnomalyGrid(REGION_CELLS * CELL, 0, (s, gx, gz) -> plan(s, gx, gz, TANGLE));

    // ---- Heights ---------------------------------------------------------------------------------
    private static final int SURFACE_Y = PoolroomsLayout.SURFACE_Y;
    private static final int DEEP_FLOOR_Y = PoolroomsLayout.DEEP_FLOOR_Y;
    private static final int FLOOR_Y = LevelLayout.FLOOR_Y;
    /** Lamps sit in the side of a walkway two blocks under the water's surface. */
    private static final int LAMP_Y = FLOOR_Y - 1;
    /** Ceiling layers: the court's open sky, the glow hall's roof, the tangle's roof. */
    private static final int COURT_SKY_Y = 59;
    private static final int HALL_CEILING_Y = 61;
    private static final int TANGLE_CEILING_Y = 56;

    // ---- Sky court -------------------------------------------------------------------------------
    /** The covered deck reaches this far in from its wall, under a concrete overhang ... */
    private static final int PORTICO = 6;
    /** ... whose underside is here (the overhang fills everything above it). */
    private static final int PORTICO_CEILING_Y = 47;
    /** The two beams across the open roof: rows 9-10 and 18-19, three blocks deep. */
    private static final int BEAM_LOW_Y = 55;
    /** Invisible light (vanilla light 15) over the open court, every 4 blocks: daylight without Veil. */
    private static final int DAYLIGHT_Y = 46;

    // ---- Glow hall -------------------------------------------------------------------------------
    /** Dry landings at both ends: along 1..3 and 53..55. */
    private static final int LANDING = 3;
    /** Piers along a period of 5: 3 blocks of pier, a 2-wide opening. */
    private static final int PIER_PERIOD = 5;
    /** Openings into the aisles are 3 high under the strip light (y42..44, the light at y45). */
    private static final int OPENING_TOP_Y = SURFACE_Y + 4;
    /** The aisles' low ceiling. */
    private static final int AISLE_CEILING_Y = SURFACE_Y + 6;
    /** Dark slots in the upper walls, over each opening. */
    private static final int SLOT_LOW_Y = 50;
    private static final int SLOT_HIGH_Y = 55;
    /**
     * The channel's glow: hidden (waterlogged, invisible) vanilla light down its middle every 3
     * blocks, two under the surface. Level 9 keeps the water lit while the walkways get about 3 and
     * the piers 2 or less: with Iris (which lights everything from the vanilla level) the hall
     * stays black round a glowing channel. The visible lamps are the dim kind for the same reason.
     */
    private static final int CHANNEL_GLOW_EVERY = 3;
    private static final int CHANNEL_GLOW_LEVEL = 9;

    // ---- Tangle ----------------------------------------------------------------------------------
    private static final int SEGMENTS = 10;
    private static final int BOXES = 4;
    /** Feature heights: water, a walkway at the water's level, a ledge a block up, blocks 2-3 up. */
    private static final int WATER = -1;
    private static final int WALKWAY = 0;
    private static final int LEDGE = 1;
    /** Share of walkway columns with a (dim) lamp in their side, where they meet the water. */
    private static final double LAMP_SHARE = 0.12;
    /** Hidden light in the tangle's water (every 4 blocks, two under the surface): glowing water, black walkways. */
    private static final int TANGLE_GLOW_EVERY = 4;
    private static final int TANGLE_GLOW_LEVEL = 8;
    /** Hidden lights sit two blocks under the water's surface. */
    private static final int GLOW_Y = SURFACE_Y - 2;

    // ==========================================================================================
    // Planning
    // ==========================================================================================

    @Nullable
    private static AnomalySite plan(long seed, int gx, int gz, String kind) {
        int salt = kind.equals(SKY_COURT) ? 6400 : kind.equals(GLOW_HALL) ? 6500 : 6600;
        long h = Hash.of(seed, gx, gz, salt);
        if (Hash.unit(h) >= SHARE) {
            return null;
        }
        for (int k = 0; k < TRIES; k++) {
            long hk = Hash.mix(h + k);
            boolean alongX = !kind.equals(GLOW_HALL) || (hk & 1) == 0;
            int w = kind.equals(GLOW_HALL) ? (alongX ? 4 : 1) : 2;
            int d = kind.equals(GLOW_HALL) ? (alongX ? 1 : 4) : 2;
            // Never in the square's edge rooms, so areas of neighbouring squares never touch.
            int cx = gx * REGION_CELLS + 1 + Hash.range(Hash.mix(hk ^ 1), REGION_CELLS - 1 - w);
            int cz = gz * REGION_CELLS + 1 + Hash.range(Hash.mix(hk ^ 2), REGION_CELLS - 1 - d);
            if (!PoolroomsLayout.areaAllowed(seed, cx, cz, w, d) || overlapsEarlierKind(seed, kind, cx, cz, w, d)) {
                continue;
            }
            int minX = cx * CELL + 1;
            int minZ = cz * CELL + 1;
            int maxX = (cx + w) * CELL - 1;
            int maxZ = (cz + d) * CELL - 1;
            return new AnomalySite(kind, minX, minZ, maxX, maxZ, (minX + maxX) / 2, (minZ + maxZ) / 2,
                    alongX ? 3 : 0, Hash.mix(hk ^ 3));
        }
        return null;
    }

    /**
     * True if the rooms (with a room's margin) touch an area of a kind planned before this one:
     * courts first, then glow halls, then tangles, so no planner ever waits on itself.
     */
    private static boolean overlapsEarlierKind(long seed, String kind, int cx, int cz, int w, int d) {
        if (kind.equals(SKY_COURT)) {
            return false;
        }
        for (int x = cx - 1; x <= cx + w; x++) {
            for (int z = cz - 1; z <= cz + d; z++) {
                if (inArea(COURTS, seed, x, z) || kind.equals(TANGLE) && inArea(HALLS, seed, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inArea(AnomalyGrid grid, long seed, int cx, int cz) {
        AnomalySite site = grid.site(seed, Math.floorDiv(cx, REGION_CELLS), Math.floorDiv(cz, REGION_CELLS));
        return site != null && site.contains(cx * CELL + CELL / 2, cz * CELL + CELL / 2);
    }

    /** The area that room (cx, cz) belongs to, or null (cached, so cheap for every column). */
    @Nullable
    static AnomalySite areaOfCell(long seed, int cx, int cz) {
        int gx = Math.floorDiv(cx, REGION_CELLS);
        int gz = Math.floorDiv(cz, REGION_CELLS);
        int mx = cx * CELL + CELL / 2;
        int mz = cz * CELL + CELL / 2;
        AnomalySite site = COURTS.site(seed, gx, gz);
        if (site != null && site.contains(mx, mz)) {
            return site;
        }
        site = HALLS.site(seed, gx, gz);
        if (site != null && site.contains(mx, mz)) {
            return site;
        }
        site = TANGLES.site(seed, gx, gz);
        return site != null && site.contains(mx, mz) ? site : null;
    }

    /** The area whose inside covers column (x, z) (its outer walls are not part of it), or null. */
    @Nullable
    static AnomalySite areaAt(long seed, int x, int z) {
        AnomalySite site = areaOfCell(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        return site != null && site.contains(x, z) ? site : null;
    }

    /** The ceiling layer of an area (its outer walls reach up to it). */
    static int ceilingY(AnomalySite site) {
        return switch (site.kind()) {
            case SKY_COURT -> COURT_SKY_Y;
            case GLOW_HALL -> HALL_CEILING_Y;
            default -> TANGLE_CEILING_Y;
        };
    }

    // ==========================================================================================
    // Shapes: (u, v) are area-local, 1 .. size-1 (the outer walls are 0 and size)
    // ==========================================================================================

    /**
     * The layout's column kind (DRY, SHALLOW, DEEP or WALL) of area column (x, z). A tangle's
     * ledges and blocks count as walls: there is no jumping on land in the Poolrooms, so nobody
     * stands on them.
     */
    static int column(AnomalySite site, int x, int z) {
        int u = x - site.minX() + 1;
        int v = z - site.minZ() + 1;
        return switch (site.kind()) {
            case SKY_COURT -> courtColumn(site, u, v);
            case GLOW_HALL -> hallColumn(along(site, u, v), across(site, u, v));
            default -> {
                int level = tangleLevel(site, u, v);
                yield level == WATER ? PoolroomsLayout.DEEP : level == WALKWAY ? PoolroomsLayout.DRY : PoolroomsLayout.WALL;
            }
        };
    }

    // ---- Sky court -------------------------------------------------------------------------------

    /** Distance from the covered side's wall (1 = right against it). */
    private static int fromPortico(AnomalySite site, int u, int v) {
        int size = 2 * CELL;
        return switch ((int) (site.salt() & 3)) {
            case 0 -> v;
            case 1 -> size - u;
            case 2 -> size - v;
            default -> u;
        };
    }

    private static int courtColumn(AnomalySite site, int u, int v) {
        int size = 2 * CELL;
        int s = fromPortico(site, u, v);
        int d = Math.min(Math.min(u, size - u), Math.min(v, size - v));
        if (s <= PORTICO || d <= 2) {
            return PoolroomsLayout.DRY;
        }
        return s == PORTICO + 1 ? PoolroomsLayout.SHALLOW : PoolroomsLayout.DEEP; // a step down by the overhang
    }

    private static boolean courtBeam(AnomalySite site, int u, int v) {
        int t = ((site.salt() >> 2) & 1) == 0 ? v : u;
        return t == 9 || t == 10 || t == 18 || t == 19;
    }

    // ---- Glow hall -------------------------------------------------------------------------------

    private static int along(AnomalySite site, int u, int v) {
        return site.facing() == 3 ? u : v;
    }

    private static int across(AnomalySite site, int u, int v) {
        return site.facing() == 3 ? v : u;
    }

    private static boolean landing(int along) {
        return along <= LANDING || along >= 4 * CELL - LANDING;
    }

    private static boolean pier(int along) {
        return !landing(along) && (along - LANDING - 1) % PIER_PERIOD < 3;
    }

    /** Which opening (counting from the first) an opening column belongs to. */
    private static int opening(int along) {
        return (along - LANDING - 1) / PIER_PERIOD;
    }

    private static int hallColumn(int along, int c) {
        if ((c == 3 || c == CELL - 3) && pier(along)) {
            return PoolroomsLayout.WALL;
        }
        if (c >= 5 && c <= CELL - 5 && !landing(along)) {
            return PoolroomsLayout.DEEP;
        }
        return PoolroomsLayout.DRY;
    }

    // ---- Tangle ----------------------------------------------------------------------------------

    /**
     * Height of the tangle at (u, v): WATER, WALKWAY (top at the water's level), LEDGE (one up), or
     * 2-3 for the blocks. A dry rim runs all round; walkways reach in from it or stand alone.
     */
    private static int tangleLevel(AnomalySite site, int u, int v) {
        int size = 2 * CELL;
        if (Math.min(Math.min(u, size - u), Math.min(v, size - v)) == 1) {
            return WALKWAY;
        }
        int level = WATER;
        long salt = site.salt();
        for (int i = 0; i < SEGMENTS; i++) {
            long h = Hash.mix(salt + 0x9E3779B97F4A7C15L * (i + 1));
            boolean alongU = (h & 1) == 0;
            int width = 1 + (int) ((h >>> 1) & 1);
            int line = 3 + Hash.range(Hash.mix(h ^ 1), size - 5 - width);
            int length = 6 + Hash.range(Hash.mix(h ^ 2), 14);
            int mode = Hash.range(Hash.mix(h ^ 3), 3);
            int start = mode == 0 ? 2 : mode == 1 ? size - 1 - length : 3 + Hash.range(Hash.mix(h ^ 4), size - 5 - length);
            int a = alongU ? u : v;
            int c = alongU ? v : u;
            if (a < start || a >= start + length || c < line || c >= line + width) {
                continue;
            }
            // Some walkways are raised a block (a 1-wide ledge) or carry a low wall on one side.
            boolean raised = Hash.range(Hash.mix(h ^ 5), 3) == 0;
            int here = raised && (width == 1 || c == line + 1) ? LEDGE : WALKWAY;
            level = Math.max(level, here);
        }
        for (int i = 0; i < BOXES; i++) {
            long h = Hash.mix(salt ^ 0xC2B2AE3D27D4EB4FL * (i + 1));
            int bu = 4 + Hash.range(h, size - 9);
            int bv = 4 + Hash.range(Hash.mix(h ^ 1), size - 9);
            int bw = 2 + (int) ((h >>> 7) & 1);
            int bd = 2 + (int) ((h >>> 8) & 1);
            if (u >= bu && u < bu + bw && v >= bv && v < bv + bd) {
                level = Math.max(level, 2 + (int) ((h >>> 9) & 1));
            }
        }
        return level;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /** Writes every block of area column (x, z) (the area covers it). */
    static void fill(AnomalySite site, int x, int z, LevelLayout.Column out) {
        int u = x - site.minX() + 1;
        int v = z - site.minZ() + 1;
        switch (site.kind()) {
            case SKY_COURT -> fillCourt(site, u, v, out);
            case GLOW_HALL -> fillHall(site, along(site, u, v), across(site, u, v), out);
            default -> fillTangle(site, u, v, out);
        }
    }

    /** The water or dry floor of a column, from the deep-water floor up to the surface. */
    private static void floor(int kind, LevelLayout.Column out) {
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        if (kind == PoolroomsLayout.DEEP) {
            out.set(DEEP_FLOOR_Y, ModBlocks.POOL_FLOOR.get().defaultBlockState());
            for (int y = DEEP_FLOOR_Y + 1; y <= SURFACE_Y; y++) {
                out.set(y, water);
            }
            return;
        }
        for (int y = DEEP_FLOOR_Y; y < FLOOR_Y; y++) {
            out.set(y, tile);
        }
        if (kind == PoolroomsLayout.SHALLOW) {
            out.set(FLOOR_Y, ModBlocks.POOL_FLOOR.get().defaultBlockState());
            out.set(SURFACE_Y, water);
        } else {
            out.set(FLOOR_Y, tile);
            out.set(SURFACE_Y, tile);
        }
    }

    private static void fillCourt(AnomalySite site, int u, int v, LevelLayout.Column out) {
        floor(courtColumn(site, u, v), out);
        BlockState concrete = ModBlocks.L7_CONCRETE.get().defaultBlockState();
        if (fromPortico(site, u, v) <= PORTICO) {
            // The covered deck: a dark ceiling, the solid concrete overhang over it.
            for (int y = PORTICO_CEILING_Y; y <= COURT_SKY_Y; y++) {
                out.set(y, concrete);
            }
            return;
        }
        out.set(COURT_SKY_Y, ModBlocks.POOL_SKY.get().defaultBlockState());
        if (courtBeam(site, u, v)) {
            for (int y = BEAM_LOW_Y; y < COURT_SKY_Y; y++) {
                out.set(y, concrete);
            }
        }
        if (u % 4 == 2 && v % 4 == 2) {
            // Daylight that also reads without Veil (with Iris or plain rendering): the painted sky
            // alone is 17 blocks up, too far for vanilla light to reach the water.
            out.set(DAYLIGHT_Y, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
    }

    private static void fillHall(AnomalySite site, int along, int c, LevelLayout.Column out) {
        int kind = hallColumn(along, c);
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        boolean pierLine = c == 3 || c == CELL - 3;
        boolean aisle = c <= 2 || c >= CELL - 2;
        floor(kind == PoolroomsLayout.WALL ? PoolroomsLayout.DRY : kind, out);
        out.set(HALL_CEILING_Y, tile);
        if (kind == PoolroomsLayout.WALL) {
            for (int y = SURFACE_Y + 1; y < HALL_CEILING_Y; y++) {
                out.set(y, tile);
            }
            return;
        }
        if (aisle || pierLine && landing(along)) {
            // The low, black aisles behind the piers; the wall above them closes up to the roof.
            for (int y = AISLE_CEILING_Y; y < HALL_CEILING_Y; y++) {
                out.set(y, tile);
            }
            return;
        }
        if (pierLine) {
            // An opening between two piers: a lintel and the upper wall above it, with a dark slot.
            for (int y = OPENING_TOP_Y + 1; y < HALL_CEILING_Y; y++) {
                if (y < SLOT_LOW_Y || y > SLOT_HIGH_Y) {
                    out.set(y, tile);
                }
            }
            int side = c == 3 ? 0 : 1;
            if (((opening(along) + side + (int) (site.salt() >>> 8)) & 1) == 0) {
                Direction.Axis axis = site.facing() == 3 ? Direction.Axis.X : Direction.Axis.Z;
                out.set(OPENING_TOP_Y, ModBlocks.POOL_STRIP_DIM.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis));
            }
            return;
        }
        if (kind == PoolroomsLayout.DEEP && c == CELL / 2 && along % CHANNEL_GLOW_EVERY == 0) {
            out.set(GLOW_Y, glow(CHANNEL_GLOW_LEVEL));
        }
        // The walkways beside the channel carry its lamps, one every 5 blocks on each side.
        if ((c == 4 || c == CELL - 4) && !landing(along) && along % PIER_PERIOD == 1) {
            boolean towardHighC = c == 4;
            Direction facing = site.facing() == 3
                    ? (towardHighC ? Direction.SOUTH : Direction.NORTH)
                    : (towardHighC ? Direction.EAST : Direction.WEST);
            out.set(LAMP_Y, lamp(facing));
        }
    }

    private static void fillTangle(AnomalySite site, int u, int v, LevelLayout.Column out) {
        int level = tangleLevel(site, u, v);
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        floor(level == WATER ? PoolroomsLayout.DEEP : PoolroomsLayout.DRY, out);
        for (int y = SURFACE_Y + 1; y <= SURFACE_Y + level; y++) {
            out.set(y, tile);
        }
        out.set(TANGLE_CEILING_Y, tile);
        if (level == WATER) {
            if (u % TANGLE_GLOW_EVERY == 0 && v % TANGLE_GLOW_EVERY == 2) {
                out.set(GLOW_Y, glow(TANGLE_GLOW_LEVEL));
            }
            return;
        }
        if (Hash.unit(site.salt(), u, v, 6610) >= LAMP_SHARE) {
            return;
        }
        // A lamp in the walkway's side, facing the first open water beside it.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nu = u + d.getStepX();
            int nv = v + d.getStepZ();
            if (nu > 0 && nv > 0 && nu < 2 * CELL && nv < 2 * CELL && tangleLevel(site, nu, nv) == WATER) {
                out.set(LAMP_Y, lamp(d));
                return;
            }
        }
    }

    /**
     * The dark areas' underwater lamps: the dim kind, whose lens glows as bright as any but whose
     * light (vanilla 6) dies within a few blocks, so the tile round the water stays black.
     */
    private static BlockState lamp(Direction facing) {
        return ModBlocks.POOL_LAMP_DIM.get().defaultBlockState().setValue(PoolLampBlock.FACING, facing);
    }

    /** Invisible vanilla light inside the water (waterlogged, so the water never meets air). */
    private static BlockState glow(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level).setValue(LightBlock.WATERLOGGED, true);
    }

    private PoolroomsAreas() {}
}
