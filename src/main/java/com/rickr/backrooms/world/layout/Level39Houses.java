package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level39HousesBlocks.*;

import com.rickr.backrooms.block.Level39HouseWallBlock;
import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The houses of Level 39: the town ({@link Level39Town}) asks this class for every column of a
 * house footprint. Each house is drawn once by {@link Level39HousesBuilder} (style, rooms,
 * furniture, anomaly) and kept packed in a {@link Level39HousesPlan}; here we only look the
 * column up.
 *
 * <p>It also answers the questions the anomaly code ({@code world/anomaly/level39}) asks about a
 * house: where its interior doors are (vanishing doors), where its entry hall is, and what the
 * "bigger on the inside" interior looks like. That interior is a huge maze of identical papered
 * rooms around one endless hall. It is drawn at a copy of the town far away (the anomaly code owns
 * that offset, {@code Level39AnomalyPlan.BIG_DX}): the copy shows the same street, the same house
 * front and the same first few steps of the hall, and the anomaly code moves the player across
 * as they walk down the hall, so nothing seems to change - except that the house never ends.
 */
public final class Level39Houses {
    /** How far the bigger interior reaches to each side of the front door. */
    public static final int BIG_HALF_WIDTH = 36;
    /** How far it reaches back from the front wall. */
    public static final int BIG_DEPTH = 64;
    /** Hall rows (behind the front wall) that are the same in the house and in its copy. */
    public static final int HALL_ROWS = 4;

    private Level39Houses() {}

    /** Writes column (x, z) of house h (the town only calls this for footprint columns). */
    public static void fillColumn(long seed, Level39HouseSpec h, int x, int z, LevelLayout.Column out) {
        Level39HousesPlan.of(seed, h).fillColumn(x, z, out);
    }

    // ==========================================================================================
    // Doors (vanishing doors)
    // ==========================================================================================

    /** Receives one interior door: the lower half's position and the way the door faces. */
    @FunctionalInterface
    public interface DoorSink {
        void door(int x, int yLower, int z, Direction facing);
    }

    /** Every interior door of house h (upside-down houses have none: their doorways are open). */
    public static void forEachInteriorDoor(long seed, Level39HouseSpec h, DoorSink sink) {
        int[] d = Level39HousesPlan.of(seed, h).doors;
        for (int i = 0; i + 3 < d.length; i += 4) {
            sink.door(d[i], d[i + 1], d[i + 2], Direction.from2DDataValue(d[i + 3]));
        }
    }

    /**
     * What a vanished door's doorway turns into: the house's wallpaper (the bottom block with the
     * skirting board, the others plain).
     */
    public static BlockState doorwayWall(long seed, Level39HouseSpec h, int x, int y, int z) {
        Level39HousesPlan p = Level39HousesPlan.of(seed, h);
        int row = y - p.floorY;
        int finish = row <= 1 ? Level39HouseWallBlock.BASE + p.paper : p.paper;
        return resolve(withParam(code(INWALL), finish));
    }

    // ==========================================================================================
    // The entry hall and the bigger-on-the-inside interior
    // ==========================================================================================

    /**
     * The first rows of the hall behind the front door (interior cells, inclusive world
     * rectangle). The player is "past the line" when their block coordinate along
     * {@code inward}'s axis is at or beyond {@code shiftLine} (>= when inward points the positive
     * way, <= otherwise). Two rows lie on the door side of the line, two beyond it. feetY is the
     * floor's walking level.
     */
    public record EntryHall(int x0, int z0, int x1, int z1, Direction inward, int shiftLine, int feetY) {
    }

    /** The front wall's distance from the front of the footprint, per style (see the builder's templates). */
    static int frontWallV(int style) {
        return switch (style) {
            case Level39HouseSpec.STYLE_CRAFTSMAN -> 4;
            case Level39HouseSpec.STYLE_VICTORIAN -> 3;
            default -> 1;
        };
    }

    /** Storey pitch (floor to floor) per style. */
    static int pitch(int style) {
        return style == Level39HouseSpec.STYLE_COLONIAL || style == Level39HouseSpec.STYLE_CAPE_COD
                || style == Level39HouseSpec.STYLE_RANCH ? 4 : 5;
    }

    /** The local frame of a house, without building it. */
    private record Frame(Direction f, Direction b, Direction r, int ox, int oz, int doorU, int fv) {
        int x(int u, int v) {
            return ox + r.getStepX() * u + b.getStepX() * v;
        }

        int z(int u, int v) {
            return oz + r.getStepZ() * u + b.getStepZ() * v;
        }

        int u(int x, int z) {
            return (x - ox) * r.getStepX() + (z - oz) * r.getStepZ();
        }

        int v(int x, int z) {
            return (x - ox) * b.getStepX() + (z - oz) * b.getStepZ();
        }
    }

    private static Frame frame(Level39HouseSpec h) {
        Direction f = h.facing();
        Direction b = f.getOpposite();
        Direction r = f.getClockWise();
        int sx = r.getStepX() + b.getStepX();
        int sz = r.getStepZ() + b.getStepZ();
        int ox = sx > 0 ? h.x0() : h.x1();
        int oz = sz > 0 ? h.z0() : h.z1();
        int doorU = (h.doorX() - ox) * r.getStepX() + (h.doorZ() - oz) * r.getStepZ();
        return new Frame(f, b, r, ox, oz, doorU, frontWallV(h.style()));
    }

    public static EntryHall entryHall(Level39HouseSpec h) {
        Frame fr = frame(h);
        int ax = fr.x(fr.doorU() - 1, fr.fv() + 1);
        int az = fr.z(fr.doorU() - 1, fr.fv() + 1);
        int bx = fr.x(fr.doorU() + 1, fr.fv() + HALL_ROWS);
        int bz = fr.z(fr.doorU() + 1, fr.fv() + HALL_ROWS);
        Direction in = fr.b();
        int lineV = fr.fv() + 3;
        int shiftLine = in.getAxis() == Direction.Axis.X ? fr.x(fr.doorU(), lineV) : fr.z(fr.doorU(), lineV);
        return new EntryHall(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz), in, shiftLine, h.baseY() + 1);
    }

    /**
     * The inclusive world rectangle {x0, z0, x1, z1} of house h's bigger interior (original town
     * coordinates, the entry hall included), kept inside the town.
     */
    public static int[] bigInteriorBounds(Level39HouseSpec h) {
        Frame fr = frame(h);
        int ax = fr.x(fr.doorU() - BIG_HALF_WIDTH, fr.fv());
        int az = fr.z(fr.doorU() - BIG_HALF_WIDTH, fr.fv());
        int bx = fr.x(fr.doorU() + BIG_HALF_WIDTH, fr.fv() + BIG_DEPTH);
        int bz = fr.z(fr.doorU() + BIG_HALF_WIDTH, fr.fv() + BIG_DEPTH);
        int cx = h.x0() - Level39Layout.local(h.x0());
        int cz = h.z0() - Level39Layout.local(h.z0());
        int lim = Level39Layout.TOWN_HALF - 3;
        return new int[] {
                Math.max(Math.min(ax, bx), cx - lim), Math.max(Math.min(az, bz), cz - lim),
                Math.min(Math.max(ax, bx), cx + lim), Math.min(Math.max(az, bz), cz + lim)};
    }

    /** The bigger-inside houses of one town and their interiors' rectangles. */
    private record BigList(long seed, int cellX, int cellZ, Level39HouseSpec[] houses, int[][] bounds) {
    }

    private static final ConcurrentHashMap<Long, BigList> BIG = new ConcurrentHashMap<>();
    private static final ThreadLocal<BigList> LAST_BIG = new ThreadLocal<>();

    private static BigList bigList(long seed, int x, int z) {
        int cellX = Level39Cell.cellIndex(x);
        int cellZ = Level39Cell.cellIndex(z);
        BigList b = LAST_BIG.get();
        if (b != null && b.seed == seed && b.cellX == cellX && b.cellZ == cellZ) return b;
        long key = Hash.of(seed, cellX, cellZ, 3970);
        b = BIG.get(key);
        if (b == null || b.seed != seed || b.cellX != cellX || b.cellZ != cellZ) {
            if (BIG.size() > 32) BIG.clear();
            b = BIG.computeIfAbsent(key, k -> {
                Level39Cell cell = Level39Cell.byIndex(seed, cellX, cellZ);
                List<Level39HouseSpec> list = new ArrayList<>();
                for (Level39HouseSpec h : cell.houses) {
                    if (h.anomaly() == Level39HouseSpec.Anomaly.BIGGER_INSIDE) list.add(h);
                }
                Level39HouseSpec[] arr = list.toArray(new Level39HouseSpec[0]);
                int[][] bounds = new int[arr.length][];
                for (int i = 0; i < arr.length; i++) bounds[i] = bigInteriorBounds(arr[i]);
                return new BigList(seed, cellX, cellZ, arr, bounds);
            });
        }
        LAST_BIG.set(b);
        return b;
    }

    /**
     * The bigger-inside house whose interior covers (x, z) (original coordinates), or null.
     * Where two interiors would overlap, the one whose front door is nearer wins. Allocation-free.
     */
    public static Level39HouseSpec biggerInteriorAt(long seed, int x, int z) {
        BigList b = bigList(seed, x, z);
        Level39HouseSpec best = null;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < b.houses.length; i++) {
            int[] r = b.bounds[i];
            if (x < r[0] || x > r[2] || z < r[1] || z > r[3]) continue;
            Level39HouseSpec h = b.houses[i];
            long dx = x - h.doorX();
            long dz = z - h.doorZ();
            long d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /**
     * Writes column (x, z) (original coordinates) of house h's bigger interior over the ordinary
     * town column already written there. The entry hall's columns are left alone: there the copy
     * is the house itself. Everything else from the ground floor up is replaced: one endless hall
     * straight back from the door, with papered rooms off it on both sides, every room furnished
     * the same, popcorn ceilings and bare bulbs throughout.
     */
    public static void fillBigInterior(long seed, Level39HouseSpec h, int x, int z, LevelLayout.Column out) {
        BigInfo info = bigInfo(seed, h);
        Frame fr = info.frame;
        int u = fr.u(x, z);
        int v = fr.v(x, z);
        int du = fr.doorU();
        int fv = fr.fv();
        if (v < fv) return;
        if (Math.abs(u - du) <= 2 && v <= fv + HALL_ROWS) return; // the hall: the house's own columns
        int[] r = info.bounds;
        if (x < r[0] || x > r[2] || z < r[1] || z > r[3]) return;
        int f0 = h.baseY();
        int p = pitch(h.style());
        int paper = info.paper;
        // Clear the copy town above the floor (other houses, trees, poles of the copy).
        BlockState air = info.air;
        for (int y = f0 + 1; y <= f0 + 40; y++) out.set(y, air);
        // Is this column on the boundary of the whole interior?
        int lu0 = info.lu0;
        int lu1 = info.lu1;
        int lv1 = info.lv1;
        boolean boundary = u == lu0 || u == lu1 || v == lv1 || v == fv;
        // The grid: the hall runs back between du - 2 and du + 2; rooms 6 wide either side, 5 deep.
        boolean hallCol = Math.abs(u - du) <= 1;
        int ru = u > du ? u - (du + 2) : (du - 2) - u; // 0 on the hall walls, then 1.. outward
        boolean uLine = !hallCol && ru % 7 == 0;
        int rv = v - fv;
        boolean vLine = !hallCol && rv % 6 == 0;
        int roomU = ru / 7;
        int roomV = rv / 6;
        int side = u > du ? 1 : -1;
        long rh = Hash.of(h.hash(), roomU * side, roomV, 3971);
        boolean wall = boundary || uLine || vLine;
        if (wall && !boundary) {
            // Doorways: the middle of every wall into the hall; others mostly, so it rambles.
            if (uLine && !vLine && rv % 6 == 3) {
                long dh = Hash.of(h.hash(), ru / 7 * side, roomV, 3972);
                wall = !(ru == 0 || Hash.unit(dh) < 0.75);
            } else if (vLine && !uLine && ru % 7 == 4) {
                long dh = Hash.of(h.hash(), roomU * side, roomV, 3973);
                wall = Hash.unit(dh) >= 0.7;
            }
        }
        int floor = info.floorKind;
        out.set(f0, resolve(code(floor)));
        int top = f0 + p;
        if (wall) {
            for (int y = f0 + 1; y < top; y++) {
                int finish = y == f0 + 1 ? (info.old ? Level39HouseWallBlock.WAINSCOT : Level39HouseWallBlock.BASE + paper) : paper;
                out.set(y, resolve(withParam(code(INWALL), finish)));
            }
            out.set(top, resolve(code(POPCORN)));
            out.set(top + 1, resolve(withParam(code(INWALL), Level39HouseWallBlock.PLASTER)));
            return;
        }
        out.set(top, resolve(code(POPCORN)));
        out.set(top + 1, resolve(withParam(code(INWALL), Level39HouseWallBlock.PLASTER)));
        // The studded ceiling of the reference photo: a bare bulb in almost every block, a few left
        // out so the rows aren't perfect, most dead and about one in eight still glowing.
        if (Hash.unit(Hash.of(h.hash(), u, v, 3974)) >= 0.12) {
            double roll = Hash.unit(Hash.of(h.hash(), u, v, 3975));
            int bulb = roll < 0.12 ? code(BULB) | FLAG | (roll < 0.02 ? SECOND : 0) : code(BULB);
            out.set(top - 1, resolve(bulb));
        }
        if (hallCol) {
            // Now and then a picture or a side table in the endless hall.
            if (Math.abs(u - du) == 1 && rv % 9 == 5) {
                Direction facing = u > du ? fr.r().getOpposite() : fr.r();
                out.set(f0 + 2, resolve(code(PICTURE, facing)));
            }
            return;
        }
        // Every room furnished the same: an armchair in the corner, a lamp table beside it, a
        // picture on the wall, a shelf of almond water in some.
        int iu = ru % 7;
        int iv = rv % 6;
        Direction toHall = u > du ? fr.r().getOpposite() : fr.r();
        Direction toBack = fr.b();
        if (iu == 6 && iv == 1) {
            out.set(f0 + 1, resolve(code(ARMCHAIR, toHall)));
        } else if (iu == 6 && iv == 2) {
            out.set(f0 + 1, resolve(code(SIDE_TABLE, toHall)));
        } else if (iu == 3 && iv == 5) {
            out.set(f0 + 2, resolve(code(PICTURE, toBack.getOpposite())));
        } else if (iu == 1 && iv == 4 && Hash.unit(rh) < 0.3) {
            out.set(f0 + 1, resolve(withParam(code(SHELF, toHall.getOpposite()), 1 + Hash.range(Hash.mix(rh), 4))));
        }
    }

    /** What every column of one bigger interior needs, worked out once per house. */
    private static final class BigInfo {
        final long seed;
        final long hash;
        final Frame frame;
        final int[] bounds;
        final int lu0;
        final int lu1;
        final int lv1;
        final int paper;
        final int floorKind;
        final boolean old;
        final BlockState air;

        BigInfo(long seed, Level39HouseSpec h) {
            this.seed = seed;
            this.hash = h.hash();
            this.frame = frame(h);
            this.bounds = bigInteriorBounds(h);
            this.lu0 = Math.min(frame.u(bounds[0], bounds[1]), frame.u(bounds[2], bounds[3]));
            this.lu1 = Math.max(frame.u(bounds[0], bounds[1]), frame.u(bounds[2], bounds[3]));
            this.lv1 = Math.max(frame.v(bounds[0], bounds[1]), frame.v(bounds[2], bounds[3]));
            Level39HousesPlan plan = Level39HousesPlan.of(seed, h);
            this.paper = plan.paper;
            this.floorKind = plan.floorKind;
            this.old = plan.old;
            this.air = resolve(AIR);
        }
    }

    private static final ConcurrentHashMap<Long, BigInfo> BIG_INFO = new ConcurrentHashMap<>();
    private static final ThreadLocal<BigInfo> LAST_INFO = new ThreadLocal<>();

    private static BigInfo bigInfo(long seed, Level39HouseSpec h) {
        BigInfo i = LAST_INFO.get();
        if (i != null && i.seed == seed && i.hash == h.hash()) return i;
        long key = h.hash() ^ (seed * 0x9E3779B97F4A7C15L);
        i = BIG_INFO.get(key);
        if (i == null || i.seed != seed || i.hash != h.hash()) {
            if (BIG_INFO.size() > 64) BIG_INFO.clear();
            i = BIG_INFO.computeIfAbsent(key, k -> new BigInfo(seed, h));
        }
        LAST_INFO.set(i);
        return i;
    }

    // ==========================================================================================
    // Debug
    // ==========================================================================================

    /**
     * One line per house of the town around (x, z): position, style, anomaly, knock walls, plan
     * size. For a debug command; builds the plans it reports on.
     */
    public static void describeTown(long seed, int x, int z, Consumer<String> line) {
        Level39Cell cell = Level39Cell.of(seed, x, z);
        String[] styles = {"colonial", "victorian", "farmhouse", "craftsman", "cape_cod", "ranch"};
        for (Level39HouseSpec h : cell.houses) {
            Level39HousesPlan p = Level39HousesPlan.of(seed, h);
            line.accept("#" + h.id() + " door " + h.doorX() + " " + h.baseY() + " " + h.doorZ() + " " + styles[h.style()]
                    + (h.style2() != h.style() ? "+" + styles[h.style2()] : "") + " " + h.anomaly().name().toLowerCase()
                    + " knock=" + p.knockWalls + " doors=" + p.doors.length / 4 + " bytes=" + p.memoryBytes());
        }
    }
}
