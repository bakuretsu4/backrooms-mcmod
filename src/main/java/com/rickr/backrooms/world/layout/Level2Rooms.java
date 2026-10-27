package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2ShelfBlock;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The ordinary side rooms off Level 2's corridor (see {@link Level2Line} for where they are). Each
 * is a small dead-end room cut into the rock behind one chase wall, entered through a steel door
 * or an open doorway in the middle of its corridor wall. Safe rooms ({@link SafeRoomPlan}) and the
 * hot zone's machinery room ({@link Level2AnomalyPlan}) are built elsewhere.
 *
 * <p>Inside a room, columns are counted from its doorway: i = blocks east of the doorway (x - door
 * x), j = rows into the rock (0 = just inside the doorway). The walls are lined with things and the
 * middle stays clear to walk in. A maintenance room on the north side of the corridor, from above
 * (north is up; everything at the top row faces the door):
 * <pre>
 *   j=4   S S S L S S S     S steel shelving   L locker     C crate
 *   j=3   C . . . . . S     W workbench        B breaker panel (conduit up to the ceiling)
 *   j=2   W = = = = = S     = a pipe across the ceiling     t tube light
 *   j=1   W . . t . . B
 *   j=0   . . . . . . .
 *               ^ steel door (in the corridor wall)
 * </pre>
 */
final class Level2Rooms {
    private Level2Rooms() {}

    // What stands on a room's floor (0 = nothing: you can walk there).
    private static final int NOTHING = 0;
    private static final int SHELF = 1;
    private static final int WORKBENCH = 2;
    private static final int LOCKER = 3;
    private static final int CRATE = 4;
    private static final int BREAKER = 5;
    private static final int FAN = 6;
    private static final int DUCT_RISER = 7;
    private static final int LOW_DUCT = 8;
    private static final int PUMP = 9;
    private static final int MACHINE = 10;
    private static final int RED_RISER = 11;
    private static final int BACKPACK = 12;

    /** True if the room has a steel door (the others have an open doorway). */
    static boolean hasDoor(int type) {
        return type == Level2Line.ROOM_MAINTENANCE || type == Level2Line.ROOM_BOILER;
    }

    /** True if floor column (i, j) of a room of {@code type} has nothing standing on it. */
    static boolean isFree(int type, int i, int j) {
        return prop(type, i, j) == NOTHING;
    }

    private static int prop(int type, int i, int j) {
        int hw = Level2Line.roomHalfWidth(type);
        int back = Level2Line.roomDepth(type) - 1;
        return switch (type) {
            // A full wall of shelving with a locker in the middle; a workbench and a crate on the
            // west wall; a breaker panel and more shelving on the east wall.
            case Level2Line.ROOM_MAINTENANCE -> j == back ? (i == 0 ? LOCKER : SHELF)
                    : i == -hw ? (j == 1 || j == 2 ? WORKBENCH : j == 3 ? CRATE : NOTHING)
                    : i == hw ? (j == 1 ? BREAKER : j >= 2 ? SHELF : NOTHING)
                    : NOTHING;
            // Two fan housings and the main duct between them on the back wall, shelving on the west
            // wall, a low duct along the east wall.
            case Level2Line.ROOM_VENTILATION -> j == back ? (Math.abs(i) == 1 ? FAN : i == 0 ? DUCT_RISER : i == 2 ? CRATE
                    : i == hw ? LOW_DUCT : SHELF)
                    : i == -hw && j >= 1 ? SHELF
                    : i == hw ? LOW_DUCT
                    : NOTHING;
            // The pump set at the back, machine cabinets down both side walls, a red pipe climbing
            // the back wall, a crate in the corner.
            case Level2Line.ROOM_BOILER -> (i == -1 || i == 0) && j >= back - 1 ? PUMP
                    : Math.abs(i) == hw && j >= 2 ? MACHINE
                    : i == 1 && j == back ? RED_RISER
                    : i == 2 && j == back ? CRATE
                    : NOTHING;
            // Shelving down both sides and across the back; a dropped backpack by the door.
            case Level2Line.ROOM_CLOSET -> j >= 1 && i != 0 || j == back ? SHELF
                    : i == 1 && j == 0 ? BACKPACK
                    : NOTHING;
            default -> NOTHING;
        };
    }

    /**
     * One floor column of a room. {@code side} is the corridor wall it opens off, {@code salt} a
     * per-room random number and (i, j) the column's place in the room (see the class comment).
     */
    static void fill(Level2Layout layout, int type, int side, long salt, int i, int j, LevelLayout.Column out) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        int top = ceiling - 1;
        int hw = Level2Line.roomHalfWidth(type);
        int back = Level2Line.roomDepth(type) - 1;
        Direction toDoor = Level2Line.inwardOf(side);  // what stands at the back faces the doorway
        Direction sideFacing = i < 0 ? Direction.EAST : Direction.WEST; // things on a side wall face across the room
        Direction facing = j == back && Math.abs(i) < hw ? toDoor : Math.abs(i) == hw ? sideFacing : toDoor;
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        switch (prop(type, i, j)) {
            case SHELF -> {
                BlockState shelf = Level2Blocks.SHELF.get().defaultBlockState()
                        .setValue(Level2ShelfBlock.VARIANT, Hash.range(Hash.mix(salt + i * 31L + j), 4));
                out.set(floor + 1, Level2Parts.tall(shelf, facing, false));
                out.set(floor + 2, Level2Parts.tall(shelf, facing, true));
            }
            case WORKBENCH -> out.set(floor + 1, Level2Blocks.WORKBENCH.get().defaultBlockState().setValue(WallMountBlock.FACING, sideFacing));
            // Supplies (filled from the loot tables the first time they're opened).
            case LOCKER -> out.set(floor + 1, ModBlocks.SUPPLY_LOCKER.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, facing));
            case CRATE -> out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, facing));
            case BACKPACK -> out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState()
                    .setValue(SupplyContainerBlock.FACING, Direction.from2DDataValue(Hash.range(salt, 4))));
            case BREAKER -> {
                BlockState panel = Level2Blocks.BREAKER_PANEL.get().defaultBlockState();
                out.set(floor + 2, Level2Parts.tall(panel, sideFacing, false));
                out.set(floor + 3, Level2Parts.tall(panel, sideFacing, true));
                // Its conduits climb into the ceiling.
                out.set(top, Level2Parts.pipe(Direction.Axis.Y, sideFacing.getOpposite(), Kind.CONDUIT, Size.SMALL, false));
            }
            case FAN -> {
                // A bank of fan housings up to the ceiling, where the big duct runs into it.
                out.set(floor + 1, Level2Parts.duct(toDoor, true));
                BlockState fan = Level2Blocks.FAN_HOUSING.get().defaultBlockState().setValue(WallMountBlock.FACING, toDoor);
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, fan);
                }
            }
            case DUCT_RISER -> {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, Level2Parts.centrePipe(Direction.Axis.Y, Kind.INSULATED, Size.LARGE));
                }
            }
            case LOW_DUCT -> out.set(floor + 1, Level2Parts.duct(sideFacing, j == 2));
            case PUMP -> {
                // The 2x2x2 pump set, its front (gauge, valve) towards the door. wx/wz count from its
                // smallest-x, smallest-z corner (the back row is the smaller z on the north side).
                int wx = i + 1;
                int wz = (j == back) == (side < 0) ? 0 : 1;
                out.set(floor + 1, Level2Blocks.PUMP.get().partAt(toDoor, wx, 0, wz));
                out.set(floor + 2, Level2Blocks.PUMP.get().partAt(toDoor, wx, 1, wz));
                if (Level2Parts.pumpOutlet(toDoor, wx, wz)) {
                    // Its discharge pipe rises into the ceiling.
                    out.set(floor + 3, Level2Parts.centrePipe(Direction.Axis.Y, Kind.STEEL, Size.LARGE));
                    out.set(floor + 4, Level2Parts.centrePipe(Direction.Axis.Y, Kind.STEEL, Size.LARGE));
                } else if (j == back && i == 0) {
                    // A thinner copper riser out of the expansion tank.
                    out.set(floor + 3, Level2Parts.centrePipe(Direction.Axis.Y, Kind.COPPER, Size.MEDIUM));
                    out.set(floor + 4, Level2Parts.centrePipe(Direction.Axis.Y, Kind.COPPER, Size.MEDIUM));
                }
            }
            case MACHINE -> {
                BlockState machine = ModBlocks.L2_MACHINE.get().defaultBlockState().setValue(WallMountBlock.FACING, sideFacing);
                out.set(floor + 1, machine);
                out.set(floor + 2, machine);
                // Above the cabinets: gauges and valve wheels, and one hissing vent.
                out.set(floor + 3, (j & 1) == 0 ? Level2Parts.gauge(sideFacing)
                        : i < 0 && j == back ? Level2Parts.steamVent(sideFacing)
                        : Level2Parts.valve(sideFacing, true));
            }
            case RED_RISER -> {
                // A red service pipe up the back wall, turning at the ceiling to run out over the room.
                for (int y = floor + 1; y < top; y++) {
                    out.set(y, Level2Parts.centrePipe(Direction.Axis.Y, Kind.PAINTED_RED, Size.MEDIUM));
                }
                out.set(top, Level2Parts.elbow(toDoor, false, Kind.PAINTED_RED, Size.MEDIUM));
            }
            default -> { }
        }
        fillOverhead(type, i, j, hw, back, out, floor, top, toDoor);
    }

    /** Ceiling lights, pipes and ducts overhead, stencils: whatever hangs in the room. */
    private static void fillOverhead(int type, int i, int j, int hw, int back, LevelLayout.Column out,
                                     int floor, int top, Direction toDoor) {
        // Every room has a caged lamp on a side wall above head height (on the series circuits like
        // the corridor's), besides its tube: the room never goes completely dark at once.
        int lampI = type == Level2Line.ROOM_MAINTENANCE ? hw : -hw;
        int lampJ = switch (type) {
            case Level2Line.ROOM_MAINTENANCE -> 3;
            case Level2Line.ROOM_VENTILATION -> 2;
            default -> 1;
        };
        if (i == lampI && j == lampJ) {
            out.set(floor + 3, Level2Parts.lamp(i < 0 ? Direction.EAST : Direction.WEST, false));
        }
        switch (type) {
            case Level2Line.ROOM_MAINTENANCE -> {
                if (j == 2) {
                    // A steel pipe right across the ceiling, wall to wall.
                    out.set(top, Level2Parts.pipe(Direction.Axis.X, Direction.UP, Kind.STEEL, Size.MEDIUM, i == 0));
                } else if (i == 0 && j == 1) {
                    out.set(top, tube(Direction.Axis.X));
                } else if (i == -hw && j == 3) {
                    // A junction box over the crate, its conduit going up.
                    out.set(floor + 3, Level2Blocks.JUNCTION_BOX.get().defaultBlockState().setValue(WallMountBlock.FACING, Direction.EAST));
                    out.set(top, Level2Parts.pipe(Direction.Axis.Y, Direction.WEST, Kind.CONDUIT, Size.SMALL, false));
                } else if (i == -1 && j == back) {
                    out.set(floor + 3, Level2Parts.stencil(toDoor, Variant.NO_SMOKING));
                }
            }
            case Level2Line.ROOM_VENTILATION -> {
                if (Math.abs(i) == 1 && j < back) {
                    // The big ducts across the ceiling, into the fan housings.
                    out.set(top, Level2Parts.pipe(Direction.Axis.Z, Direction.UP, Kind.INSULATED, Size.LARGE, j == 2));
                } else if (i == hw) {
                    out.set(floor + 3, Level2Parts.pipe(Direction.Axis.Z, Direction.EAST, Kind.STEEL, Size.MEDIUM, j == 3));
                } else if (i == 0 && j == 2) {
                    out.set(top, tube(Direction.Axis.Z));
                } else if (i == -hw && j == back - 1) {
                    out.set(floor + 3, Level2Parts.stencil(Direction.EAST, Variant.H2O));
                }
            }
            case Level2Line.ROOM_BOILER -> {
                if (Math.abs(i) == hw) {
                    out.set(top, Level2Parts.pipe(Direction.Axis.Z, i < 0 ? Direction.WEST : Direction.EAST,
                            Kind.STEEL, Size.MEDIUM, j == 3));
                } else if (i == 1 && j < back) {
                    // The red pipe from the back wall, out over the room to the front wall.
                    out.set(top, Level2Parts.centrePipe(Direction.Axis.Z, Kind.PAINTED_RED, Size.MEDIUM));
                } else if (i == -2 && j == 2) {
                    out.set(top, tube(Direction.Axis.Z));
                } else if (i == 2 && j == back) {
                    out.set(floor + 3, Level2Parts.stencil(toDoor, Variant.DANGER_HOT));
                }
            }
            case Level2Line.ROOM_CLOSET -> {
                if (i == 0 && j == 1) {
                    out.set(top, tube(Direction.Axis.Z));
                }
            }
            default -> { }
        }
    }

    private static BlockState tube(Direction.Axis axis) {
        return ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis);
    }

    /**
     * The doorway column in the corridor wall: a closed steel door (FACING into the room, so its
     * slab sits on the corridor side, flush with the wall) or an open doorway, wall above.
     */
    static void fillDoorway(Level2Layout layout, int type, int side, LevelLayout.Column out) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        if (hasDoor(type)) {
            BlockState door = Level2Blocks.STEEL_DOOR.get().defaultBlockState()
                    .setValue(DoorBlock.FACING, Level2Line.wallOf(side))
                    .setValue(HouseDoorBlock.LOCKED, false);
            out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            out.set(floor + 3, wall);
        }
        for (int y = floor + 4; y < ceiling; y++) {
            out.set(y, wall);
        }
    }
}
