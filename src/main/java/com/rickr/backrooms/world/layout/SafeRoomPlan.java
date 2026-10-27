package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * One safe room: a small, warm, furnished room with a single open doorway, a battery-backed
 * ceiling lamp, a checkpoint radio, a supply crate and a cot. Monsters stay out and sanity
 * recovers in here (that part is gameplay, see {@code SafeRooms}); this class only says WHERE the
 * room is and WHAT blocks it is made of.
 *
 * <p><b>Where.</b> Every level asks for at most one safe room per {@link #CELL} x {@link #CELL}
 * square of the world ({@link #CHANCE} of the squares have one). Each level decides where in the
 * square the room goes, because it has to fit the level's own floor plan (a free spot in the Level
 * 0 maze, a block of garage bays in Level 1, the rock between two Level 2 corridors, ...). Like the
 * rest of the worldgen, all of it is a pure function of (seed, x, z).
 *
 * <p><b>The room "box"</b> is a rectangle of columns, walls included. One side of it is the door
 * wall. To write the furniture rules only once, columns are measured in a local frame that turns
 * with the door: standing in the doorway looking IN,
 * <ul>
 *   <li>{@code v} = how deep into the box (0 = the outer face of the door wall), and</li>
 *   <li>{@code u} = how far to the right (0 = the left wall's outer face).</li>
 * </ul>
 * The door wall is always {@link #DOOR_WALL} thick: an outer layer in the level's own wall block
 * (with a small recess in front of the doorway where the outside SAFE sign hangs) and an inner layer
 * in the room's own panelling. The other walls are {@code sideWall} thick (1 or 2).
 *
 * <p>Inside (interior coordinates i, j: i to the right, j deeper in), for a 7x7 room:
 * <pre>
 *        j=6   C . . R . . B     C crate, R radio (faces the door), B cot (head at the back)
 *        j=5   . . . s . . B     s the spot in front of the radio: you respawn here, kept clear
 *        j=4   T . o o o . .     T small table, A armchair, o the round rug, L lamp above
 *        j=3   A . o L o . .
 *        j=2   . . o o o . .
 *        j=1   . . . . . . .
 *        j=0   . . . S . . .     S the inside SAFE sign hangs above the doorway
 *                    ^ doorway
 * </pre>
 */
public final class SafeRoomPlan {
    /** At most one safe room per CELL x CELL square of the world. */
    public static final int CELL = 128;
    /** Fraction of the squares that get one. */
    public static final double CHANCE = 0.6;
    /** The door wall is two blocks thick: level wall outside, room panelling inside. */
    public static final int DOOR_WALL = 2;
    /** Doorways are this many blocks tall; the signs hang one block above them. */
    public static final int DOOR_HEIGHT = 3;
    /** Rooms keep this far from the edges of their square, so rooms of neighbouring squares never touch. */
    private static final int EDGE_GAP = 6;

    // What one column of the box is.
    public static final int OUTSIDE = 0;
    public static final int WALL = 1;
    public static final int DOORWAY = 2;
    public static final int FLOOR = 3;
    /** Floor with a piece of furniture standing on it (not walkable). */
    public static final int FURNITURE = 4;

    // Furniture pieces (interior only).
    private static final int NOTHING = 0;
    private static final int RADIO = 1;
    private static final int CRATE = 2;
    private static final int COT_FOOT = 3;
    private static final int COT_HEAD = 4;
    private static final int ARMCHAIR = 5;
    private static final int TABLE = 6;

    /** North-west corner of the box and its size along X and Z (walls included). */
    public final int x0;
    public final int z0;
    public final int sizeX;
    public final int sizeZ;
    /** The side of the box with the doorway; also the way you look when walking out. */
    public final Direction door;
    /** Thickness of the side and back walls (1 or 2). */
    public final int sideWall;

    /** Looking into the room from the doorway: straight ahead, and your right hand. */
    private final Direction in;
    private final Direction right;
    /** Box size across the door (along u) and away from it (along v). */
    private final int across;
    private final int deep;

    public SafeRoomPlan(int x0, int z0, int sizeX, int sizeZ, Direction door, int sideWall) {
        this.x0 = x0;
        this.z0 = z0;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.door = door;
        this.sideWall = sideWall;
        this.in = door.getOpposite();
        this.right = in.getClockWise();
        boolean doorOnXSide = door.getAxis() == Direction.Axis.Z; // north/south wall: runs along X
        this.across = doorOnXSide ? sizeX : sizeZ;
        this.deep = doorOnXSide ? sizeZ : sizeX;
    }

    // ==========================================================================================
    // Choosing rooms
    // ==========================================================================================

    /** The random number that decides everything about the safe room of square (cellX, cellZ). */
    public static long cellHash(long seed, int cellX, int cellZ, int salt) {
        return Hash.of(seed, cellX, cellZ, salt);
    }

    /** True when the square with this hash has a safe room at all. */
    public static boolean rolled(long cellHash) {
        return Hash.unit(cellHash) < CHANCE;
    }

    /** Which wall of the room has the doorway. */
    public static Direction doorSide(long cellHash) {
        return Direction.from2DDataValue(Hash.range(Hash.mix(cellHash ^ 0x5AFEL), 4));
    }

    /**
     * A square room of {@code size} x {@code size} (walls included) at a random spot of square
     * (cellX, cellZ), keeping {@code moat} free blocks around it inside the square. Null when the
     * square has no room. The level still has to check the spot is allowed (not near an exit...).
     */
    @Nullable
    public static SafeRoomPlan jittered(long seed, int cellX, int cellZ, int salt, int size, int moat, int sideWall) {
        long h = cellHash(seed, cellX, cellZ, salt);
        if (!rolled(h)) {
            return null;
        }
        int free = CELL - 2 * EDGE_GAP - (size + 2 * moat); // how far the room can slide
        int x0 = cellX * CELL + EDGE_GAP + moat + Hash.range(Hash.mix(h ^ 1), free + 1);
        int z0 = cellZ * CELL + EDGE_GAP + moat + Hash.range(Hash.mix(h ^ 2), free + 1);
        return new SafeRoomPlan(x0, z0, size, size, doorSide(h), sideWall);
    }

    // ==========================================================================================
    // Geometry
    // ==========================================================================================

    /** True for every column of the room, walls and doorway included. */
    public boolean contains(int x, int z) {
        return x >= x0 && x < x0 + sizeX && z >= z0 && z < z0 + sizeZ;
    }

    /** True for the room and the {@code margin} columns all around it. */
    public boolean near(int x, int z, int margin) {
        return x >= x0 - margin && x < x0 + sizeX + margin && z >= z0 - margin && z < z0 + sizeZ + margin;
    }

    public int centerX() {
        return x0 + sizeX / 2;
    }

    public int centerZ() {
        return z0 + sizeZ / 2;
    }

    /** Depth v of a column: 0 on the outer face of the door wall. */
    private int vOf(int x, int z) {
        return switch (door) {
            case NORTH -> z - z0;
            case SOUTH -> z0 + sizeZ - 1 - z;
            case WEST -> x - x0;
            default -> x0 + sizeX - 1 - x; // EAST
        };
    }

    /** Position u of a column along the door wall, counted from the left (looking in). */
    private int uOf(int x, int z) {
        return switch (right) {
            case EAST -> x - x0;
            case WEST -> x0 + sizeX - 1 - x;
            case SOUTH -> z - z0;
            default -> z0 + sizeZ - 1 - z; // NORTH
        };
    }

    private int interiorAcross() {
        return across - 2 * sideWall;
    }

    private int interiorDeep() {
        return deep - DOOR_WALL - sideWall;
    }

    /** Interior column (i) that lines up with the doorway. */
    private int doorI() {
        return across / 2 - sideWall;
    }

    /** Interior row (j) in the middle of the room. */
    private int midJ() {
        return interiorDeep() / 2;
    }

    // ==========================================================================================
    // What each column is
    // ==========================================================================================

    /** OUTSIDE, WALL, DOORWAY, FLOOR or FURNITURE. */
    public int role(int x, int z) {
        if (!contains(x, z)) {
            return OUTSIDE;
        }
        int u = uOf(x, z);
        int v = vOf(x, z);
        if (v < DOOR_WALL) {
            return u == across / 2 ? DOORWAY : WALL;
        }
        if (u < sideWall || u >= across - sideWall || v >= deep - sideWall) {
            return WALL;
        }
        return furniture(u - sideWall, v - DOOR_WALL) != NOTHING ? FURNITURE : FLOOR;
    }

    /** True where you can stand: the doorway and the free floor. */
    public boolean isWalkable(int x, int z) {
        int role = role(x, z);
        return role == DOORWAY || role == FLOOR;
    }

    /**
     * True for wall columns of the outer layer, which are built from the level's own wall block
     * (so a level may treat them as ordinary wall, e.g. to hang its lamps on them).
     */
    public boolean isOuterWall(int x, int z) {
        return role(x, z) == WALL && !isInnerLayer(uOf(x, z), vOf(x, z));
    }

    /** True on the wall layer touching the room (panelled on the inside). */
    private boolean isInnerLayer(int u, int v) {
        return u >= sideWall - 1 && u <= across - sideWall && v >= DOOR_WALL - 1 && v <= deep - sideWall;
    }

    /** The piece of furniture standing on interior column (i, j). */
    private int furniture(int i, int j) {
        int lastI = interiorAcross() - 1;
        int lastJ = interiorDeep() - 1;
        int mid = midJ();
        if (j == lastJ && i == doorI()) return RADIO;     // against the back wall, facing the door
        if (j == lastJ && i == 0) return CRATE;           // back-left corner
        if (i == lastI && j == lastJ) return COT_HEAD;    // along the right wall, head at the back
        if (i == lastI && j == lastJ - 1) return COT_FOOT;
        if (i == 0 && j == mid) return ARMCHAIR;          // against the left wall
        if (i == 0 && j == mid + 1) return TABLE;
        return NOTHING;
    }

    /** Lamps in the ceiling: one in the middle, or two along the middle line of a deep room. */
    private boolean lamp(int i, int j) {
        if (i != doorI()) {
            return false;
        }
        int deepInside = interiorDeep();
        return deepInside >= 9 ? j == 2 || j == deepInside - 3 : j == midJ();
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /**
     * The blocks a level builds its safe rooms from.
     *
     * @param floor     the floor (carpet, tiles)
     * @param ceiling   the ceiling layer
     * @param outerBase outer wall layer, lowest block (e.g. the level's baseboard)
     * @param outerWall outer wall layer, the rest (the level's own wall, seen from outside)
     * @param innerLow  inner wall layer, lowest block: the panelling below the dado line
     * @param innerHigh inner wall layer above the dado line (painted wall / wallpaper)
     * @param frame     door frame around the doorway, or null for none
     * @param rug       true for the round rug in the middle of the floor (Level 0 has none: plain carpet)
     */
    public record Style(BlockState floor, BlockState ceiling, BlockState outerBase, BlockState outerWall,
                        BlockState innerLow, BlockState innerHigh, @Nullable BlockState frame, boolean rug) {
        /** The usual style, with the round rug. */
        public Style(BlockState floor, BlockState ceiling, BlockState outerBase, BlockState outerWall,
                     BlockState innerLow, BlockState innerHigh, @Nullable BlockState frame) {
            this(floor, ceiling, outerBase, outerWall, innerLow, innerHigh, frame, true);
        }
    }

    /**
     * Writes column (x, z) of the room (call only where {@link #contains} is true).
     *
     * @param base    Y of the block you stand on (the floor layer)
     * @param ceiling Y of the ceiling layer
     */
    public void fill(int x, int z, int base, int ceiling, Style style, LevelLayout.Column out) {
        int u = uOf(x, z);
        int v = vOf(x, z);
        int signY = base + DOOR_HEIGHT + 1;
        int doorU = across / 2;
        out.set(base, style.floor());
        out.set(ceiling, style.ceiling());

        if (v < DOOR_WALL && u == doorU) {
            // The doorway, 3 tall. The outer layer is a little recess where the SAFE sign hangs
            // over the opening; the inner layer has the lintel the sign hangs on.
            boolean outer = v == 0;
            if (outer) {
                out.set(signY, sign(door));
            }
            for (int y = outer ? signY + 1 : signY; y < ceiling; y++) {
                BlockState wall = outer ? style.outerWall() : style.innerHigh();
                out.set(y, style.frame() != null && y == signY ? style.frame() : wall);
            }
            return;
        }

        boolean interior = v >= DOOR_WALL && u >= sideWall && u < across - sideWall && v < deep - sideWall;
        if (!interior) {
            // A wall. The layer touching the room is panelled; anything further out is level wall.
            boolean inner = isInnerLayer(u, v);
            boolean frame = style.frame() != null && v < DOOR_WALL && Math.abs(u - doorU) == 1;
            for (int y = base + 1; y < ceiling; y++) {
                BlockState block;
                if (frame && y <= signY) {
                    block = style.frame();
                } else if (inner) {
                    block = y == base + 1 ? style.innerLow() : style.innerHigh();
                } else {
                    block = y == base + 1 ? style.outerBase() : style.outerWall();
                }
                out.set(y, block);
            }
            return;
        }

        int i = u - sideWall;
        int j = v - DOOR_WALL;
        // A round rug in the middle of the floor (if the level wants one).
        int di = i - doorI();
        int dj = j - midJ();
        if (style.rug() && Math.abs(di) <= 1 && Math.abs(dj) <= 1) {
            int dx = di * right.getStepX() + dj * in.getStepX();
            int dz = di * right.getStepZ() + dj * in.getStepZ();
            out.set(base, ModBlocks.RUG.get().defaultBlockState().setValue(RugBlock.PART, RugBlock.part(dx, dz)));
        }
        if (lamp(i, j)) {
            out.set(ceiling, ModBlocks.SAFE_ROOM_LAMP.get().defaultBlockState());
        }
        if (j == 0 && i == doorI()) {
            out.set(signY, sign(in)); // inside, over the doorway, readable from the room
        }
        BlockState piece = switch (furniture(i, j)) {
            case RADIO -> faced(ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState(), door);
            case CRATE -> ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
            // HouseBedBlock: FACING points from the foot to the head.
            case COT_FOOT -> faced(ModBlocks.BED.get().defaultBlockState(), in).setValue(HouseBedBlock.HEAD, false);
            case COT_HEAD -> faced(ModBlocks.BED.get().defaultBlockState(), in).setValue(HouseBedBlock.HEAD, true);
            // Furniture FACING is the way its front looks: away from the left wall.
            case ARMCHAIR -> faced(ModBlocks.SOFA.get().defaultBlockState(), right);
            case TABLE -> faced(ModBlocks.DINING_TABLE.get().defaultBlockState(), right);
            default -> null;
        };
        if (piece != null) {
            out.set(base + 1, piece);
        }
    }

    /** The SAFE plaque: it hangs in the air in front of a wall, FACING away from the wall. */
    private static BlockState sign(Direction facing) {
        return faced(ModBlocks.SAFE_ROOM_SIGN.get().defaultBlockState(), facing);
    }

    private static BlockState faced(BlockState state, Direction facing) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }
}
