package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Level 39, "Megalophobia": a foggy night town of light-blue clapboard houses, dirt paths, picket
 * fences, utility poles and bare trees, walled in on all four sides by a colossal 1900s mill whose
 * rows of glowing windows loom over everything. Under the town run long flooded tunnels of white
 * marble, reached by falling into a hole (or, rarely, down an access shaft).
 *
 * <p>The world tiles into cells of {@value #CELL} blocks. Local coordinates
 * ({@link #local}) put a town's centre at 0: the town fills |local| &lt; {@value #TOWN_HALF},
 * the superstructure band {@value #TOWN_HALF}..{@value #BAND_OUTER} (two towns' bands meet back to
 * back, 128 blocks of building between them).
 *
 * <p>The work is split up:
 * <ul>
 *   <li>{@link Level39Cell}: one town's plan (paths, lots, houses, poles, trees, tunnels, holes);</li>
 *   <li>{@link Level39Tile}: one 128 x 128 square of it, read back per column;</li>
 *   <li>{@link Level39Town}: the ground and everything standing on it;</li>
 *   <li>{@link Level39Houses}: the houses;</li>
 *   <li>{@link Level39Superstructure}: the mill facade, its gallery and stair towers;</li>
 *   <li>{@link Level39Tunnels}: the tunnels.</li>
 * </ul>
 * This class holds the shared numbers and answers the questions other code asks. Every query is
 * a pure function of (seed, position), safe on any thread (the client uses them too, with
 * {@code BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_39)}).
 *
 * <p>Ways in: the Poolrooms' drain (you come out in the tunnels) and the gondola in Level 9's
 * Crimson Field (you drift down from the sky). The way on: through a window of the superstructure.
 */
public final class Level39Layout extends LevelLayout {
    /** World height (dimension_type megalophobia): the facade, towers and the sky arrival fit in. */
    public static final int WORLD_HEIGHT = 320;
    /** Top solid block of the town ground: feet stand at y 73. */
    public static final int GROUND_Y = 72;
    public static final int CELL = 1024;
    public static final int TOWN_HALF = 448;
    public static final int BAND_OUTER = 512;
    /** Top of the facade's glass (the last storey's arched windows). */
    public static final int FACADE_TOP_Y = 240;
    public static final int STOREY = 7;
    public static final int BAY = 6;
    /** Top block of the tunnel water: its surface is at y 41.0. */
    public static final int TUNNEL_WATER_Y = 40;
    public static final int TUNNEL_FLOOR_Y = 38;
    public static final int TUNNEL_VAULT_TOP = 48;
    /** Tunnel half width: 9 wide including the tiled rims. */
    public static final int TUNNEL_HALF = 4;
    /** The unbreakable floor of the world; nothing below it. */
    public static final int BEDROCK_Y = 29;
    /** Feet height on the tunnel rims. */
    public static final int TUNNEL_FEET_Y = TUNNEL_WATER_Y + 1;

    /**
     * Only used to pick a spot near you when you leave for Level 9 (the real exit is any facade
     * window); one "exit" per town.
     */
    static final ExitLocator EXITS = new ExitLocator(CELL, 200, 3900);

    public Level39Layout() {
        super(8, EXITS);
    }

    /**
     * The real way on is a mill window with a Window living in it, so {@code /backrooms locate} and
     * the exit hints (the faint sound, the NightShot marks) point at the nearest infested stretch of
     * facade, not at the random spot above. Falls back to that spot if no infested stretch is near.
     */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        AnomalySite windows = Level39AnomalyPlan.nearest(seed, x, z, "hostile_windows");
        return windows != null ? new int[] {windows.anchorX(), windows.anchorZ()} : super.exitPosition(seed, x, z);
    }

    /** The ground-floor windows: glass starts two blocks above the town ground. */
    @Override
    public int exitY(long seed, int x, int z) {
        return GROUND_Y + 4;
    }

    @Override
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    /** Light fixtures can be anywhere from the tunnel floors up to the houses' top floors. */
    @Override
    public int highestCeilingY() {
        return GROUND_Y + 24;
    }

    @Override
    public int lowestFixtureY() {
        return TUNNEL_FLOOR_Y;
    }

    // ==========================================================================================
    // Shared geometry
    // ==========================================================================================

    /** Local coordinate inside the cell: the town's centre is 0. */
    public static int local(int v) {
        return Math.floorMod(v + CELL / 2, CELL) - CELL / 2;
    }

    /** True inside a town (|local| &lt; {@value #TOWN_HALF} on both axes). */
    public static boolean inTown(int x, int z) {
        return Math.abs(local(x)) < TOWN_HALF && Math.abs(local(z)) < TOWN_HALF;
    }

    /** True inside the superstructure band. */
    public static boolean inBand(int x, int z) {
        return !inTown(x, z);
    }

    /** Signed distance from the town to the facade glass: 0 at the glass, negative in town. */
    public static int facadeDistance(int x, int z) {
        return Math.max(Math.abs(local(x)), Math.abs(local(z))) - (TOWN_HALF + 1);
    }

    /** True when column (x, z) is inside a tunnel (water, rim, platform or alcove). */
    public static boolean tunnelAt(long seed, int x, int z) {
        return Level39Tunnels.tunnelAt(seed, x, z);
    }

    /** True inside a stagnant tunnel run (a Hydrolitis zone). */
    public static boolean isStagnant(long seed, int x, int z) {
        return Level39Tunnels.isStagnant(seed, x, z);
    }

    /** True when there is dry tunnel floor in column (x, z) (feet at {@link #TUNNEL_FEET_Y}). */
    public static boolean isTunnelWalkable(long seed, int x, int z) {
        return Level39Tunnels.isTunnelWalkable(seed, x, z);
    }

    /** True when column (x, z) is tunnel water. */
    public static boolean isTunnelWater(long seed, int x, int z) {
        return Level39Tunnels.isTunnelWater(seed, x, z);
    }

    /** True when column (x, z) belongs to a side arch (a dry alcove in a tunnel wall). */
    public static boolean sideArchAt(long seed, int x, int z) {
        return Level39Tunnels.sideArchAt(seed, x, z);
    }

    /** Every tunnel feature (orbs, side arches, drains, shafts, holes, dead ends, junctions) near (x, z). */
    public static void tunnelFeaturesNear(long seed, int x, int z, int radius, Consumer<Level39Tunnels.Feature> out) {
        Level39Tunnels.featuresNear(seed, x, z, radius, out);
    }

    /** True for a lit pane of facade glass at (x, y, z) (see {@link Level39Superstructure}). */
    public static boolean facadeWindowAt(long seed, int x, int y, int z) {
        return Level39Superstructure.facadeWindowAt(seed, x, y, z);
    }

    /** True for the glass columns of a facade bay ({@code along} = coordinate along the facade). */
    public static boolean isWindowColumn(int along) {
        return Level39Superstructure.isWindowColumn(along);
    }

    /** True inside a stair tower's footprint. */
    public static boolean inStairTower(int x, int z) {
        return Level39Superstructure.inStairTower(x, z);
    }

    /**
     * A utility pole: its crossarm block at (x, y, z), the crossarm running across the wires.
     * {@code linked} = wires run from this pole to the pole at (linkX, linkY, linkZ). The four
     * insulators' necks sit about 1.1 above the block's bottom (17.5 px), in the middle across the
     * arm, at -0.69, -0.19, +1.19 and +1.69 blocks along it from the block's west/north edge; a
     * fifth sits on the pole top (0.5, 1.1, 0.5).
     */
    public record Pole(int x, int y, int z, Direction.Axis armAxis, boolean linked, int linkX, int linkY, int linkZ) {
    }

    /** Every pole within {@code radius} (square) of (x, z), each with its link to the next pole. */
    public static void polesNear(long seed, int x, int z, int radius, Consumer<Pole> out) {
        int c0x = Level39Cell.cellIndex(x - radius);
        int c1x = Level39Cell.cellIndex(x + radius);
        int c0z = Level39Cell.cellIndex(z - radius);
        int c1z = Level39Cell.cellIndex(z + radius);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                Level39Cell cell = Level39Cell.byIndex(seed, cx, cz);
                Level39Cell.Pole[] poles = cell.poles;
                for (Level39Cell.Pole p : poles) {
                    if (Math.abs(p.x() - x) > radius || Math.abs(p.z() - z) > radius || !poleStands(seed, p)) {
                        continue;
                    }
                    Direction.Axis arm = p.wiresAlongX() ? Direction.Axis.Z : Direction.Axis.X;
                    int y = Level39Town.CROSSARM_Y;
                    if (p.next() >= 0 && poleStands(seed, poles[p.next()])) {
                        Level39Cell.Pole q = poles[p.next()];
                        out.accept(new Pole(p.x(), y, p.z(), arm, true, q.x(), y, q.z()));
                    } else {
                        out.accept(new Pole(p.x(), y, p.z(), arm, false, 0, 0, 0));
                    }
                }
            }
        }
    }

    /** False for the few planned poles that ended up on a hole or in a house (they are skipped). */
    private static boolean poleStands(long seed, Level39Cell.Pole p) {
        Level39Tile tile = Level39Tile.of(seed, p.x(), p.z());
        return tile.prop[tile.index(p.x(), p.z())] == Level39Tile.POLE;
    }

    /** Every house whose footprint lies within {@code radius} (square) of (x, z). */
    public static void housesNear(long seed, int x, int z, int radius, Consumer<Level39HouseSpec> out) {
        int c0x = Level39Cell.cellIndex(x - radius);
        int c1x = Level39Cell.cellIndex(x + radius);
        int c0z = Level39Cell.cellIndex(z - radius);
        int c1z = Level39Cell.cellIndex(z + radius);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                for (Level39HouseSpec h : Level39Cell.byIndex(seed, cx, cz).houses) {
                    if (h.x1() >= x - radius && h.x0() <= x + radius && h.z1() >= z - radius && h.z0() <= z + radius) {
                        out.accept(h);
                    }
                }
            }
        }
    }

    /** The house whose footprint contains column (x, z), or null. */
    @Nullable
    public static Level39HouseSpec houseAt(long seed, int x, int z) {
        if (!inTown(x, z)) {
            return null;
        }
        Level39Tile tile = Level39Tile.of(seed, x, z);
        int k = tile.thing[tile.index(x, z)];
        return k >= 0 ? tile.cell.things[k].house() : null;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        // Far to the east lie the bigger-inside interiors (see Level39AnomalyPlan), not the town.
        if (Level39AnomalyPlan.isCopy(x)) {
            Level39AnomalyPlan.fillCopy(seed, x, z, out);
            return;
        }
        if (inBand(x, z)) {
            Level39Superstructure.fillColumn(seed, x, z, out);
            return;
        }
        Level39Tile tile = Level39Tile.of(seed, x, z);
        Level39Town.fillColumn(seed, tile, x, z, out);
        // Stair towers stand out over the edge of the town.
        if (Level39Superstructure.depth(x, z) >= Level39Superstructure.TOWER_FRONT) {
            Level39Superstructure.towerColumn(seed, x, z, out);
        }
    }

    // ==========================================================================================
    // Standing and arriving (the town; see also preferredArrival for the tunnels)
    // ==========================================================================================

    /** Open town ground: no house, fence, pole, tree trunk, hole or tower in the way. */
    @Override
    public boolean isWalkable(long seed, int x, int z) {
        if (!inTown(x, z) || inStairTower(x, z)) {
            return false;
        }
        Level39Tile tile = Level39Tile.of(seed, x, z);
        int i = tile.index(x, z);
        byte s = tile.surf[i];
        if (s == Level39Tile.HOLE) {
            return false;
        }
        byte p = tile.prop[i];
        // Everything but a transformer (hung up on its pole) stands in the way.
        if (p != Level39Tile.NO_PROP && !(p >= Level39Tile.TRANSFORMER && p < Level39Tile.TRANSFORMER + 4)) {
            return false;
        }
        int k = tile.thing[i];
        if (k >= 0) {
            Level39Cell.Thing t = tile.cell.things[k];
            return switch (t.kind()) {
                case Level39Cell.HOUSE, Level39Cell.TOWER, Level39Cell.CAMP -> false;
                case Level39Cell.TREE_BARE, Level39Cell.TREE_LEAFY -> !(t.cx() == x && t.cz() == z);
                default -> true;
            };
        }
        return true;
    }

    @Override
    public int standY(long seed, int x, int z) {
        return (int) Math.floor(feetY(seed, x, z));
    }

    /** Feet height: the town rolls in half blocks; ruts and puddles are half a block down. */
    @Override
    public double feetY(long seed, int x, int z) {
        if (!inTown(x, z)) {
            return GROUND_Y + 1;
        }
        Level39Tile tile = Level39Tile.of(seed, x, z);
        int i = tile.index(x, z);
        byte s = tile.surf[i];
        double feet = GROUND_Y + 1 + tile.half[i] / 2.0;
        return s == Level39Tile.PUDDLE ? feet - 0.5 : feet;
    }

    /** Arrivals in town land on a path, in the open (never in a puddle, a hole or a park). */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        if (Level39AnomalyPlan.isCopy(x) || !isWalkable(seed, x, z)) {
            return false;
        }
        Level39Tile tile = Level39Tile.of(seed, x, z);
        int i = tile.index(x, z);
        byte s = tile.surf[i];
        return (s == Level39Tile.PATH_DIRT || s == Level39Tile.PATH_GRAVEL || s == Level39Tile.PATH_MUD
                || s == Level39Tile.PATH_GRASS) && tile.thing[i] < 0;
    }

    /**
     * Coming out of the Poolrooms' drain you surface in the flooded tunnels: on the rim of the
     * run nearest to where you went down. Everyone else arrives in town (the usual search).
     */
    @Override
    @Nullable
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        // Never arrive in the far-east copy region of the bigger-inside interiors: land at the same
        // spot of the ordinary town instead (same local position, cell column 0).
        boolean copy = Level39AnomalyPlan.isCopy(x);
        int ax = copy ? local(x) : x;
        if (from == BackroomsLevel.POOLROOMS) {
            return Level39Tunnels.nearestRim(seed, ax, z);
        }
        if (copy) {
            BlockPos spot = Travel.searchWalkable(this, seed, ax, z, 96);
            return spot != null ? spot : new BlockPos(ax, GROUND_Y + 1, z);
        }
        return null;
    }

    /** A rim spot in the tunnels near (x, z), or null (for spawners and commands). */
    @Nullable
    public static BlockPos tunnelArrival(long seed, int x, int z) {
        return Level39Tunnels.nearestRim(seed, x, z);
    }

    // ==========================================================================================
    // Anomalies (Level39AnomalyPlan decides where, world/anomaly/level39 makes them behave)
    // ==========================================================================================

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return Level39AnomalyPlan.siteAt(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        Level39AnomalyPlan.sitesNear(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return Level39AnomalyPlan.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level39Sites.KINDS;
    }
}
