package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Level 94, "Motion": endless rolling green hills under a painted sky, winding roads, toy houses
 * all alike, water towers, a little town with an almond-water fountain in each castle region,
 * and the castle on its glass mountain (drawn by {@link Level94Castle}).
 *
 * <p>The work is split up:
 * <ul>
 *   <li>{@link Level94Roads}: country roads, towns and the castle roads;</li>
 *   <li>{@link Level94Terrain}: the hills and the mowing stripes;</li>
 *   <li>{@link Level94Plan}: one 128 x 128 square worked out at a time (heights, ground, buildings);</li>
 *   <li>{@link Level94Houses}, {@link Level94Town}, {@link Level94Builds}: the blocks of houses,
 *       the town, trees, water towers, cars, siren poles and signs.</li>
 * </ul>
 * This class turns a plan's numbers into blocks and answers the questions other code asks
 * (where can I stand, is this a safe room, where is the exit).
 *
 * <p>Ways out: rare dark-door houses (one per {@value #EXIT_REGION}-block square, found with
 * {@code /backrooms locate}) lead to Level 9; the light behind the castle's throne leads to
 * Level 1. Both are handled in {@code Level94Rules}.
 *
 * <p>Anomalies (furniture drift on the hills, the wandering door and the blinding hall in the
 * castle) are placed by {@link Level94AnomalyPlan}.
 */
public final class Level94Layout extends LevelLayout {
    /** World height: hills reach ~y66, the glass mountain and castle go up to ~y215. */
    public static final int WORLD_HEIGHT = 256;
    /** One dark-door house in every square this size. */
    static final int EXIT_REGION = 400;
    static final ExitLocator EXITS = new ExitLocator(EXIT_REGION, 70, 9400);
    /** Solid dirt goes down to here under the ground (nothing ever sees lower). */
    private static final int DIRT_BOTTOM_Y = 34;

    public Level94Layout() {
        super(8, EXITS);
    }

    @Override
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    @Override
    public int highestCeilingY() {
        return Level94Castle.CASTLE_TOP_Y;
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        int i = plan.index(x, z);
        BlockState dirt = ModBlocks.L94_DIRT.get().defaultBlockState();
        byte surf = plan.surf[i];
        if (surf == Level94Plan.CASTLE) {
            // Flat grass first; the castle code then builds whatever it wants on top (later writes win).
            for (int y = DIRT_BOTTOM_Y; y < FLOOR_Y; y++) out.set(y, dirt);
            out.set(FLOOR_Y, grass(plan.shade[i]));
            Level94Castle.fillColumn(seed, x, z, out);
            Level94AnomalyPlan.castleColumn(seed, x, z, out);
            return;
        }
        int half = plan.top[i];
        int yTop = FLOOR_Y + (half >> 1);
        for (int y = DIRT_BOTTOM_Y; y < yTop; y++) out.set(y, dirt);
        out.set(yTop, switch (surf) {
            case Level94Plan.ROAD -> ModBlocks.L94_ASPHALT.get().defaultBlockState();
            case Level94Plan.CURB, Level94Plan.SIDEWALK -> ModBlocks.L94_CURB.get().defaultBlockState();
            case Level94Plan.PATH -> ModBlocks.L94_PATH.get().defaultBlockState();
            case Level94Plan.PAVING -> ModBlocks.L94_PAVING.get().defaultBlockState();
            default -> grass(plan.shade[i]);
        });
        if ((half & 1) != 0) {
            BlockState slab = surf == Level94Plan.PATH ? ModBlocks.L94_PATH_SLAB.get().defaultBlockState() : grassSlab(plan.shade[i]);
            out.set(yTop + 1, slab); // slabs default to the bottom half
        }
        Level94Roads.Town town = plan.townAt(i);
        if (town != null) {
            Level94Town.column(seed, town, x, z, out);
            return;
        }
        Level94Builds.Thing thing = plan.thingAt(i);
        if (thing != null) {
            Level94Builds.column(thing, x, z, out);
        }
    }

    private static BlockState grass(int shade) {
        return (shade == 1 ? ModBlocks.L94_GRASS_MID : shade == 2 ? ModBlocks.L94_GRASS_DARK : ModBlocks.L94_GRASS_LIGHT)
                .get().defaultBlockState();
    }

    private static BlockState grassSlab(int shade) {
        return (shade == 1 ? ModBlocks.L94_GRASS_SLAB_MID : shade == 2 ? ModBlocks.L94_GRASS_SLAB_DARK : ModBlocks.L94_GRASS_SLAB_LIGHT)
                .get().defaultBlockState();
    }

    /** Feet height in a castle column, or NaN if (x, z) is not in a castle zone or not walkable there. */
    private static double castleFeet(Level94Plan plan, int i, long seed, int x, int z) {
        return plan.surf[i] == Level94Plan.CASTLE ? Level94Castle.standY(seed, x, z) : Double.NaN;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        int i = plan.index(x, z);
        if (plan.surf[i] == Level94Plan.CASTLE) {
            return !Double.isNaN(Level94Castle.standY(seed, x, z));
        }
        return (plan.flags[i] & Level94Plan.BLOCKED) == 0;
    }

    @Override
    public int standY(long seed, int x, int z) {
        return (int) Math.floor(feetY(seed, x, z));
    }

    @Override
    public double feetY(long seed, int x, int z) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        int i = plan.index(x, z);
        double castle = castleFeet(plan, i, seed, x, z);
        if (!Double.isNaN(castle)) {
            return castle;
        }
        return FLOOR_Y + 1 + plan.top[i] / 2.0;
    }

    /** Arrivals land on a road, curb, sidewalk or the plaza, in the open, with houses in sight. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        int i = plan.index(x, z);
        byte s = plan.surf[i];
        if (s != Level94Plan.ROAD && s != Level94Plan.CURB && s != Level94Plan.SIDEWALK && s != Level94Plan.PAVING) {
            return false;
        }
        return (plan.flags[i] & Level94Plan.BLOCKED) == 0 && plan.nearHouse(x, z);
    }

    /** Inside an intact house (town houses too): Smilers keep out and your sanity recovers. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        return (plan.flags[plan.index(x, z)] & Level94Plan.SAFE) != 0;
    }

    /** /backrooms locate: the cell in front of the nearest dark-door house's door. */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        int rx = Math.floorDiv(x, EXIT_REGION);
        int rz = Math.floorDiv(z, EXIT_REGION);
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int[] door = darkDoor(seed, rx + ox, rz + oz);
                long dx = door[0] - x;
                long dz = door[1] - z;
                long d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = door;
                }
            }
        }
        return best;
    }

    /** The cell just outside the door of exit region (rx, rz)'s dark house: {x, z}. */
    static int[] darkDoor(long seed, int rx, int rz) {
        Level94Houses.House h = Level94Plan.darkHouse(seed, rx, rz).house;
        int mid = h.mid();
        return new int[] {h.worldX(mid, -1), h.worldZ(mid, -1)};
    }

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return Level94AnomalyPlan.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        Level94AnomalyPlan.near(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return Level94AnomalyPlan.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level94AnomalyPlan.KINDS;
    }

    /**
     * Everyone arrives at the crossroads of the country roads nearest (x, z) (from Level 39's
     * windows, Level 9's clay doors, commands), so the arrival search always finds asphalt close
     * by. Falls back to (x, z) itself.
     */
    @Override
    public int[] arrivalHint(long seed, int x, int z) {
        @Nullable int[] node = Level94Roads.nearestNode(seed, x, z);
        return node != null ? node : new int[] {x, z};
    }
}
