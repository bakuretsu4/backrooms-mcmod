package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
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
 * <p>Ways out: rare exit houses (one per {@value #EXIT_REGION}-block square). About one in three
 * is a dark-door house, found with {@code /backrooms locate}, that leads on to Level 369 (so about
 * one per 1024 x 1024 blocks); the others are flooded and lead back down to Level 7. The light behind the castle's throne leads to
 * Level 1. All are handled in {@code Level94Rules}. And about one house in
 * {@value Level94Houses#CLAY_DOOR_ONE_IN} has a moulded-clay front door instead of its own: the way back
 * to Level 9, whose clay doors lead here ({@code world/ClayDoors}).
 *
 * <p>Anomalies (furniture drift on the hills, the wandering door and the blinding hall in the
 * castle) are placed by {@link Level94AnomalyPlan}.
 */
public final class Level94Layout extends LevelLayout {
    /** World height: hills reach ~y66, the glass mountain and castle go up to ~y215. */
    public static final int WORLD_HEIGHT = 256;
    /**
     * One exit house (dark-door or flooded) in every square this size. With Level94Plan's
     * FLOODED_SHARE that makes about one dark-door house (the way on) per 1024 x 1024 blocks and
     * one flooded house (the way back down to Level 7) per ~700 x 700 blocks.
     */
    static final int EXIT_REGION = 576;
    static final ExitLocator EXITS = new ExitLocator(EXIT_REGION, 70, 9400);
    /** Solid dirt goes down to here under the ground (nothing ever sees lower). */
    private static final int DIRT_BOTTOM_Y = 34;
    /**
     * How far an exit house's door can lie outside its own region: the locator keeps 70 blocks
     * inside, the house may move up to 80 off it (Level94Plan's DARK_TRIES), the door is 5 from the
     * middle. Rounded up generously; it only decides when exitPosition may stop looking.
     */
    private static final int DOOR_SPILL = 100;
    /** How far round the crossroads arrivals look (the same reach as Travel.findSafeSpot). */
    private static final int ARRIVAL_SEARCH = 96;

    public Level94Layout() {
        super(8, EXITS);
    }

    @Override
    public int arrivalExitClearance() {
        return 400;
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
        return isOpenRoad(plan, x, z) && plan.nearHouse(x, z);
    }

    /** Road, curb, sidewalk or plaza with nothing standing on it. */
    private static boolean isOpenRoad(Level94Plan plan, int x, int z) {
        int i = plan.index(x, z);
        byte s = plan.surf[i];
        if (s != Level94Plan.ROAD && s != Level94Plan.CURB && s != Level94Plan.SIDEWALK && s != Level94Plan.PAVING) {
            return false;
        }
        return (plan.flags[i] & Level94Plan.BLOCKED) == 0;
    }

    /**
     * Every arrival: open road with houses in sight ({@link #isSafeArrival}) as near the crossroads
     * ({@link #arrivalHint}) as possible. Near a castle the hilltop houses have drifted apart into
     * fragments, which don't count, so a crossroads can have no house within reach at all; then any
     * open road will do, rather than the unchecked spot Travel falls back on when its search finds
     * nothing.
     */
    @Override
    @Nullable
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        int[] hint = arrivalHint(seed, x, z);
        BlockPos spot = Travel.searchWalkable(this, seed, hint[0], hint[1], ARRIVAL_SEARCH);
        return spot != null ? spot : openRoadNear(seed, hint[0], hint[1]);
    }

    /** Nearest open road column to (x, z) with open road on all four sides (room to move), or null. */
    @Nullable
    private BlockPos openRoadNear(long seed, int x, int z) {
        for (int r = 0; r <= ARRIVAL_SEARCH; r++) {
            for (int ox = -r; ox <= r; ox++) {
                // Only the ring's edge: the inside was searched at smaller r.
                int step = Math.abs(ox) == r ? 1 : 2 * r;
                for (int oz = -r; oz <= r; oz += step) {
                    int cx = x + ox;
                    int cz = z + oz;
                    if (isOpenRoad(seed, cx, cz) && isOpenRoad(seed, cx + 1, cz) && isOpenRoad(seed, cx - 1, cz)
                            && isOpenRoad(seed, cx, cz + 1) && isOpenRoad(seed, cx, cz - 1)) {
                        return new BlockPos(cx, standY(seed, cx, cz), cz);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isOpenRoad(long seed, int x, int z) {
        return isOpenRoad(Level94Plan.of(seed, x, z), x, z);
    }

    /** Inside an intact house (town houses too): Smilers keep out and your sanity recovers. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        return (plan.flags[plan.index(x, z)] & Level94Plan.SAFE) != 0;
    }

    /**
     * /backrooms locate, the exit hint sound and the camcorder signal: the cell in front of the
     * nearest dark-door house's door. Flooded exit houses are skipped (they lead back to Level 7,
     * not on), and so are the few left with no room beside a castle or town (they are never drawn),
     * so the nearest dark one can be a region or two further out.
     */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        int rx = Math.floorDiv(x, EXIT_REGION);
        int rz = Math.floorDiv(z, EXIT_REGION);
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        // Rings of regions round (x, z)'s own. A door in ring r is at least (r - 1) regions away,
        // less DOOR_SPILL; once even that is further than the best door so far, nothing nearer is left.
        for (int r = 0; ; r++) {
            long reach = Math.max(0L, (long) (r - 1) * EXIT_REGION - DOOR_SPILL);
            if (best != null && reach * reach > bestD) {
                return best;
            }
            for (int ox = -r; ox <= r; ox++) {
                int step = Math.abs(ox) == r ? 1 : 2 * r;
                for (int oz = -r; oz <= r; oz += step) {
                    if (!Level94Plan.hasDarkDoor(seed, rx + ox, rz + oz)) {
                        continue;
                    }
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
        }
    }

    /** The cell just outside the door of exit region (rx, rz)'s exit house: {x, z}. */
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

    /**
     * The door column {x, z} of the house with a clay front door (the way back to Level 9) nearest
     * to (x, z), within about {@code radius} blocks, or null if there is none. Works out whole plan
     * squares, ring by ring outward, stopping once no further ring can hold a nearer door: for
     * commands and tests, not for world generation.
     */
    @Nullable
    public static int[] nearestClayDoor(long seed, int x, int z, int radius) {
        long[] best = {Long.MAX_VALUE, 0, 0};
        Level94Town.DoorSink keep = (dx, dz) -> {
            long d = (long) (dx - x) * (dx - x) + (long) (dz - z) * (dz - z);
            if (d < best[0]) {
                best[0] = d;
                best[1] = dx;
                best[2] = dz;
            }
        };
        int rings = radius / Level94Plan.SIZE + 1;
        for (int r = 0; r <= rings; r++) {
            // Every square of ring r is at least (r - 1) squares away from (x, z).
            long nearestPossible = (long) Math.max(0, r - 1) * Level94Plan.SIZE;
            if (best[0] != Long.MAX_VALUE && nearestPossible * nearestPossible > best[0]) {
                break;
            }
            for (int px = -r; px <= r; px++) {
                for (int pz = -r; pz <= r; pz++) {
                    if (Math.max(Math.abs(px), Math.abs(pz)) != r) {
                        continue;
                    }
                    Level94Plan plan = Level94Plan.of(seed, x + px * Level94Plan.SIZE, z + pz * Level94Plan.SIZE);
                    for (Level94Builds.Thing t : plan.things) {
                        Level94Houses.House h = t.house;
                        if (t.kind == Level94Builds.HOUSE && h != null && h.clayDoor()) {
                            keep.door(h.worldX(h.mid(), 0), h.worldZ(h.mid(), 0));
                        }
                    }
                    for (Level94Roads.Town town : plan.towns) {
                        Level94Town.forEachClayDoor(seed, town, keep);
                    }
                }
            }
        }
        return best[0] == Long.MAX_VALUE ? null : new int[] {(int) best[1], (int) best[2]};
    }
}
