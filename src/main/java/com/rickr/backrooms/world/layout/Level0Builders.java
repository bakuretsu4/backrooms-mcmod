package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;

/**
 * The film's "builders' rooms" in Level 0: rooms (about 7-12 blocks across) that someone fitted
 * out without a plan, but each with only a few deliberate features. They are built on their own
 * coarse grid, separate from the ordinary maze skeleton of {@link Level0Layout}: {@link #X_BIG}
 * and {@link #Z_BIG} cut the world into rooms with 2-3 thick walls.
 *
 * <p><b>Which rooms.</b> A big room belongs to the builders' zone when its centre lies in a
 * {@link Level0Zones#BUILDERS} zone; it then claims its own columns and its walls (a wall or
 * corner between a builders' room and anything else still belongs to the builders' room, so the
 * zone always ends at a proper wall with doorways in it). Everywhere else the ordinary maze runs.
 *
 * <p><b>Walls.</b> Every wall has one frameless full-height doorway, 3-4 wide. Between two builders'
 * rooms a wall is sometimes missing, making one longer room. Some long walls carry a deliberate
 * series of 2-3 window slots (right through the wall, at eye height) or 2-4 empty niches (one
 * face), evenly spaced, on the longer side of the doorway.
 *
 * <p><b>Rooms.</b> Each room picks one half-wall layout, always leaving the middle clear:
 * <ul>
 *   <li>open: nothing at all;</li>
 *   <li>runs: a long straight partition 3-4 blocks out from a long wall (both long walls in wide
 *       rooms), stopping 2-3 blocks short of the end walls, with a 3-wide gap in long ones;</li>
 *   <li>cubicles: a row of partitions sticking out 3 blocks from a long wall, every 4 blocks (or
 *       every 5 with an L-shaped foot), so they make bays, sometimes with mannequins in them;</li>
 *   <li>split: a partition straight across the middle from wall to wall, with a 4-wide gap in the
 *       centre;</li>
 *   <li>passage: a carpeted step at one end of a long wall leads down into a sunken passage (floor
 *       one block down) running the length of the room behind a full wall. Under the low Level 0
 *       ceiling the film's raised passage would leave no headroom, so this one goes down instead.</li>
 * </ul>
 * Pieces never stand closer than 3 blocks to each other or to a wall unless they touch it (2 in
 * the shortest rooms), and no partition is ever a lone 1-block stub. The half-walls stay open
 * above: under Level 0's 3-high ceiling there is no room for the film's soffit beams.
 *
 * <p>All answers are packed ints, like {@link Level0Layout#mazeColumn} (which calls
 * {@link #column} first), so nothing is allocated.
 */
final class Level0Builders {
    /**
     * The rooms' grid: walls 2-3 thick, rooms 8-11 wide plus a last one per 24 blocks of 7-12 (the
     * block's leftover), so every room is 7-12 across: small enough never to feel like a hall.
     */
    static final Bands X_BIG = new Bands(24, 2, 3, 8, 11, 103);
    static final Bands Z_BIG = new Bands(24, 2, 3, 8, 11, 104);

    // Room layouts.
    static final int L_OPEN = 0;
    static final int L_RUNS = 1;
    static final int L_CUBICLES = 2;
    static final int L_SPLIT = 3;
    static final int L_PASSAGE = 4;

    // Wall features.
    static final int F_PLAIN = 0;
    static final int F_WINDOWS = 1;
    static final int F_NICHES = 2;

    // Extra data of a builders' column (bits 12+ of the packed column).
    static final int MANNEQUIN = 2;
    /** Bits 2-3 of the extra data: the ramp piece, one of the RAMP_* values (0 = none). */
    static final int RAMP_SHIFT = 2;
    /** The step down into the sunken passage: a carpet slab in place of the floor (half a block down). */
    static final int RAMP_SLAB = 1;
    /** The sunken passage itself: the floor one block down. */
    static final int RAMP_SUNK = 2;

    /**
     * The big spans this thread looked up last. Worldgen asks for column after column in a row,
     * mostly inside the same big room, so reusing them saves two span searches per column.
     */
    private static final class LastSpans {
        boolean valid;
        long seed;
        long x;
        long z;
    }

    private static final ThreadLocal<LastSpans> LAST = ThreadLocal.withInitial(LastSpans::new);

    private Level0Builders() {}

    /** True if the big room (two gaps) is a builders' room. */
    static boolean isRoom(long seed, long bx, long bz) {
        int cx = X_BIG.start(bx) + Bands.length(bx) / 2;
        int cz = Z_BIG.start(bz) + Bands.length(bz) / 2;
        return Level0Zones.kindAt(seed, cx, cz) == Level0Zones.BUILDERS;
    }

    /**
     * Column (x, z) if it belongs to a builders' room or its walls, packed as in
     * {@link Level0Layout#mazeColumn} (without the zone kind), or -1 if it doesn't.
     */
    static int column(long seed, int x, int z) {
        LastSpans last = LAST.get();
        boolean reuse = last.valid && last.seed == seed;
        long bx = reuse && x >= X_BIG.start(last.x) && x < X_BIG.end(last.x) ? last.x : X_BIG.span(seed, x);
        long bz = reuse && z >= Z_BIG.start(last.z) && z < Z_BIG.end(last.z) ? last.z : Z_BIG.span(seed, z);
        last.valid = true;
        last.seed = seed;
        last.x = bx;
        last.z = bz;
        boolean bandX = Bands.isBand(bx);
        boolean bandZ = Bands.isBand(bz);
        if (!bandX && !bandZ) {
            return isRoom(seed, bx, bz) ? roomColumn(seed, x, z, bx, bz) : -1;
        }
        if (bandX && !bandZ) {
            boolean west = isRoom(seed, X_BIG.span(seed, X_BIG.start(bx) - 1), bz);
            boolean east = isRoom(seed, X_BIG.span(seed, X_BIG.end(bx)), bz);
            if (!west && !east) return -1;
            return wallColumn(seed, bx, bz, z - Z_BIG.start(bz), x - X_BIG.start(bx), 0, west && east);
        }
        if (!bandX) {
            boolean north = isRoom(seed, bx, Z_BIG.span(seed, Z_BIG.start(bz) - 1));
            boolean south = isRoom(seed, bx, Z_BIG.span(seed, Z_BIG.end(bz)));
            if (!north && !south) return -1;
            return wallColumn(seed, bz, bx, x - X_BIG.start(bx), z - Z_BIG.start(bz), 1, north && south);
        }
        long west = X_BIG.span(seed, X_BIG.start(bx) - 1);
        long east = X_BIG.span(seed, X_BIG.end(bx));
        long north = Z_BIG.span(seed, Z_BIG.start(bz) - 1);
        long south = Z_BIG.span(seed, Z_BIG.end(bz));
        boolean any = isRoom(seed, west, north) || isRoom(seed, east, north) || isRoom(seed, west, south) || isRoom(seed, east, south);
        return any ? Level0Layout.WALL : -1;
    }

    // ==========================================================================================
    // Walls
    // ==========================================================================================

    private static long wallHash(long seed, long wall, long gap, int axis) {
        return Hash.of(seed, Bands.index(wall), Bands.index(gap), 700 + axis);
    }

    /** True if the wall between two builders' rooms is left out (12% of them). */
    private static boolean missing(long seed, long wall, long gap, int axis, boolean inner) {
        return inner && Hash.unit(wallHash(seed, wall, gap, axis)) < 0.12;
    }

    /** The wall's doorway: offset along the wall (bits 0-7) and width (bits 8+), 3 or 4 wide, at least 2 from the ends. */
    static int doorway(long seed, long wall, long gap, int axis) {
        long h = Hash.mix(wallHash(seed, wall, gap, axis) ^ 2);
        int length = Bands.length(gap);
        int width = Hash.unit(h) < 0.6 ? 3 : 4;
        int offset = 2 + Hash.range(Hash.mix(h), Math.max(1, length - width - 3));
        return offset | width << 8;
    }

    /** F_PLAIN, F_WINDOWS (35%) or F_NICHES (20%). */
    static int feature(long seed, long wall, long gap, int axis) {
        double r = Hash.unit(Hash.mix(wallHash(seed, wall, gap, axis) ^ 1));
        return r < 0.35 ? F_WINDOWS : r < 0.55 ? F_NICHES : F_PLAIN;
    }

    private static int wallColumn(long seed, long wall, long gap, int along, int across, int axis, boolean inner) {
        if (missing(seed, wall, gap, axis, inner)) {
            return Level0Layout.OPEN;
        }
        int door = doorway(seed, wall, gap, axis);
        int doorOffset = door & 0xFF;
        int doorWidth = door >>> 8;
        if (along >= doorOffset && along < doorOffset + doorWidth) {
            return Level0Layout.DOORWAY;
        }
        int feature = feature(seed, wall, gap, axis);
        if (feature == F_PLAIN) {
            return Level0Layout.WALL;
        }
        // The series goes on the longer stretch of wall beside the doorway, one block clear of its ends.
        int length = Bands.length(gap);
        int leftLength = doorOffset - 2;
        int rightStart = doorOffset + doorWidth + 1;
        int rightLength = length - 1 - rightStart;
        int partStart = leftLength >= rightLength ? 1 : rightStart;
        int partLength = Math.max(leftLength, rightLength);
        int width = feature == F_WINDOWS ? 2 : 1;
        int period = feature == F_WINDOWS ? 4 : 3;
        int count = Math.min(feature == F_WINDOWS ? 3 : 4, (partLength + period - width) / period);
        if (count < 2) {
            return Level0Layout.WALL;
        }
        int span = period * (count - 1) + width;
        int start = partStart + (partLength - span) / 2;
        int pos = along - start;
        if (pos < 0 || pos >= span || pos % period >= width) {
            return Level0Layout.WALL;
        }
        if (feature == F_WINDOWS) {
            return Level0Layout.WINDOW;
        }
        int side = (int) (wallHash(seed, wall, gap, axis) >>> 37) & 1;
        if (across != (side == 0 ? 0 : Bands.length(wall) - 1)) {
            return Level0Layout.WALL;
        }
        return Level0Layout.NICHE_HIGH | Level0Layout.faceDirection(axis, side).get2DDataValue() << 8;
    }

    // ==========================================================================================
    // Rooms
    // ==========================================================================================

    private static long roomHash(long seed, long bx, long bz) {
        return Hash.of(seed, Bands.index(bx), Bands.index(bz), 710);
    }

    /**
     * The room's half-wall layout (L_*: open 20%, runs 25%, cubicles 25%, split 20%, passage 10%)
     * in bits 0-3, and which long wall it leans on (0 = low side, 1 = high side) in bit 4. Cubicles
     * and the passage need a real wall: if both long walls are missing the room stays open.
     */
    static int plan(long seed, long bx, long bz) {
        long h = roomHash(seed, bx, bz);
        double r = Hash.unit(h);
        int layout = r < 0.20 ? L_OPEN : r < 0.45 ? L_RUNS : r < 0.70 ? L_CUBICLES : r < 0.90 ? L_SPLIT : L_PASSAGE;
        int side = (int) (Hash.mix(h) >>> 20) & 1;
        if (layout == L_CUBICLES || layout == L_PASSAGE) {
            boolean alongX = Bands.length(bx) >= Bands.length(bz);
            if (sideWallMissing(seed, bx, bz, alongX, side)) {
                side ^= 1;
                if (sideWallMissing(seed, bx, bz, alongX, side)) {
                    layout = L_OPEN;
                }
            }
        }
        return layout | side << 4;
    }

    /** True if the bays of a cubicle room have mannequins standing in them (half of them). */
    static boolean hasMannequins(long seed, long bx, long bz) {
        return (plan(seed, bx, bz) & 15) == L_CUBICLES && Hash.unit(Hash.mix(roomHash(seed, bx, bz) ^ 4)) < 0.5;
    }

    private static int roomColumn(long seed, int x, int z, long bx, long bz) {
        int lenX = Bands.length(bx);
        int lenZ = Bands.length(bz);
        boolean alongX = lenX >= lenZ;
        int length = alongX ? lenX : lenZ;   // the long way ("a")
        int breadth = alongX ? lenZ : lenX;  // the short way ("b")
        int a = alongX ? x - X_BIG.start(bx) : z - Z_BIG.start(bz);
        int b = alongX ? z - Z_BIG.start(bz) : x - X_BIG.start(bx);
        long bits = Hash.mix(roomHash(seed, bx, bz));
        int plan = plan(seed, bx, bz);
        int side = plan >>> 4 & 1;                   // which long wall the layout leans on
        int fromWall = side == 0 ? b : breadth - 1 - b;
        Direction plusA = alongX ? Direction.EAST : Direction.SOUTH;
        Direction plusB = alongX ? Direction.SOUTH : Direction.EAST;
        Direction outOfWall = side == 0 ? plusB : plusB.getOpposite();
        int partitionAcrossB = plusB.get2DDataValue() << 8; // a partition running along a
        int partitionAcrossA = plusA.get2DDataValue() << 8; // a partition running along b

        switch (plan & 15) {
            case L_RUNS -> {
                // 3-4 blocks out from the wall (3 in narrow rooms, so the open side stays 3+ wide),
                // and in rooms wide enough for it a second run along the other long wall.
                int distance = 3 + (breadth >= 9 ? (int) (bits >>> 21 & 1) : 0);
                boolean both = breadth >= 2 * distance + 5 && (bits >>> 22 & 1) == 0;
                boolean onLine = fromWall == distance || (both && breadth - 1 - fromWall == distance);
                int margin = length >= 10 ? 3 : 2; // how far short of the end walls it stops
                boolean gap = length - 2 * margin > 8 && Math.abs(a - length / 2) <= 1;
                if (onLine && a >= margin && a <= length - 1 - margin && !gap) {
                    return Level0Layout.HALF | partitionAcrossB;
                }
            }
            case L_CUBICLES -> {
                boolean feet = (bits >>> 23 & 1) == 0;
                int period = feet ? 5 : 4;
                int k = Math.floorDiv(a - 3, period);
                int divider = 3 + k * period;
                boolean inRow = k >= 0 && divider <= length - 4;
                long wall = sideWall(seed, bx, bz, alongX, side);
                int door = doorway(seed, wall, alongX ? bx : bz, alongX ? 1 : 0);
                boolean byDoor = divider >= (door & 0xFF) - 1 && divider <= (door & 0xFF) + (door >>> 8);
                if (inRow && !byDoor && fromWall <= 2) {
                    if (a == divider) {
                        return Level0Layout.HALF | partitionAcrossA;
                    }
                    if (feet && fromWall == 2 && a == divider + 1) {
                        return Level0Layout.HALF | partitionAcrossB;
                    }
                }
                // Mannequins stand in the bays, looking out into the room (a few have turned).
                if (inRow && !byDoor && fromWall <= 1 && a > divider
                        && hasMannequins(seed, bx, bz) && Hash.unit(seed, x, z, 715) < 0.3) {
                    Direction look = Hash.unit(seed, x, z, 716) < 0.25
                            ? Direction.from2DDataValue(Hash.range(Hash.of(seed, x, z, 717), 4)) : outOfWall;
                    return Level0Layout.OPEN | look.get2DDataValue() << 8 | (MANNEQUIN << 12);
                }
            }
            case L_SPLIT -> {
                // Each side of the 4-wide gap is only built if it is at least 2 long (no lone stubs).
                int gapStart = breadth / 2 - 2 + Hash.range(Hash.mix(bits ^ 6), 3) - 1;
                boolean left = b < gapStart && gapStart >= 2;
                boolean right = b >= gapStart + 4 && breadth - gapStart - 4 >= 2;
                if (a == length / 2 && (left || right)) {
                    return Level0Layout.HALF | partitionAcrossA;
                }
            }
            case L_PASSAGE -> {
                if (fromWall == 3 && a <= length - 3) {
                    return Level0Layout.WALL; // the full wall between the room and the sunken passage
                }
                if (fromWall < 3) {
                    // A half-block step at the open end, then the passage one block down.
                    int part = a >= length - 2 ? RAMP_SLAB : RAMP_SUNK;
                    return Level0Layout.OPEN | part << RAMP_SHIFT << 12;
                }
            }
            default -> {
            }
        }
        return Level0Layout.OPEN;
    }

    /** The big wall along long side {@code side} of a room (the one its layout leans on). */
    private static long sideWall(long seed, long bx, long bz, boolean alongX, int side) {
        if (alongX) {
            return Z_BIG.span(seed, side == 0 ? Z_BIG.start(bz) - 1 : Z_BIG.end(bz));
        }
        return X_BIG.span(seed, side == 0 ? X_BIG.start(bx) - 1 : X_BIG.end(bx));
    }

    /** True if that long wall is one of the missing walls between two builders' rooms. */
    private static boolean sideWallMissing(long seed, long bx, long bz, boolean alongX, int side) {
        long wall = sideWall(seed, bx, bz, alongX, side);
        if (alongX) {
            long beyond = Z_BIG.span(seed, side == 0 ? Z_BIG.start(wall) - 1 : Z_BIG.end(wall));
            return missing(seed, wall, bx, 1, isRoom(seed, bx, beyond));
        }
        long beyond = X_BIG.span(seed, side == 0 ? X_BIG.start(wall) - 1 : X_BIG.end(wall));
        return missing(seed, wall, bz, 0, isRoom(seed, beyond, bz));
    }
}
