package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.Level2ChamberPropBlock.Prop;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2ShelfBlock;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.Level2ChamberBlocks;
import com.rickr.backrooms.registry.Level2LightBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Level 2's maintenance chambers: the bigger, rarer side rooms where the crews worked (room types
 * ROOM_PUMP_ROOM .. ROOM_BREAK and the wrong_valves oddity's ROOM_GALLERY_ODD in {@link Level2Line}).
 * They open off the corridor through a steel door like the other rooms ({@link Level2Rooms} sends
 * them here), but are 9 wide and 5-7 deep, and each tells a little of the story of the people who
 * kept the pipes running: a logbook left open on the bench, a kettle by the lockers, a jacket on
 * its hook, a mattress in the corner of the break room.
 *
 * <p><b>The plans are drawn.</b> Each chamber is two little maps below: what stands on the floor,
 * and what hangs overhead or on the walls. Rows go from the BACK wall (top line) to the row just
 * inside the door (bottom line); columns from west (left) to east (right), the door under the
 * middle column. A room on the south side of the corridor is the same drawing mirrored (the door
 * still under the middle column, the back wall to the south). To change a chamber, redraw it:
 * the rows must all be 9 characters long. The preview tool (tools/anomaly_assets/level2_chambers.py)
 * reads these same strings to draw its pictures.
 *
 * <p>Floor legend (what stands on the floor of a cell; '.' = walk there):
 * <pre>
 *   S steel shelving (2 tall)      W workbench          Y workbench with the logbook on it
 *   Z workbench with a kettle      C supply crate       K supply locker
 *   B breaker panel (2 tall)       M machine cabinet    P pump set (2x2x2; draw it as a 2x2 block of P)
 *   H pump set in the sump water   J junction box (conduit up to the ceiling)
 *   V valve station: the main at the foot of the wall, a quarter-turn valve, a gauge or label
 *   X fire extinguisher            T table with the logbook   U table with a mug
 *   h chair (faces the table)      m mattress           j work jacket on a hook (you can walk there)
 *   ~ the sump's black water (one deep)                 = grated walkway over the sump
 * </pre>
 * Overhead legend (under the ceiling, or high on the wall the cell is against):
 * <pre>
 *   - | fluorescent tube along x / z      = " steel pipe along x / z
 *   # % cable tray along x / z            L caged lamp (mercury in machine rooms)
 *   E amber battery lamp (standby)        R red battery lamp (standby)    o a dead lamp
 *   g gauge     w valve wheel with a gauge above it     n NO SMOKING     d a leak dripping
 * </pre>
 */
final class Level2Chambers {
    private Level2Chambers() {}

    // ---- The drawings ---------------------------------------------------------------------------

    /** Two pump sets on the back wall, their header overhead; the starter panels, the bench with the log. */
    private static final String[] PUMP_FLOOR = {
            "S.PP.PP.S",
            "S.PP.PP.S",
            ".........",
            "B.......W",
            "B.......Y",
            "........C",
            "X........",
    };
    private static final String[] PUMP_OVER = {
            "..g...g..",
            "=========",
            ".........",
            "L...-....",
            "........E",
            ".........",
            ".........",
    };

    /** A manifold of quarter-turn valves round three walls, each with its gauge or label. */
    private static final String[] GALLERY_FLOOR = {
            "VVVVVVVVV",
            "V.......V",
            "V.......V",
            "........Y",
            "X........",
    };
    private static final String[] GALLERY_OVER = {
            "=========",
            "\".......\"",
            "\"...-...\"",
            "........E",
            "L........",
    };

    /** Breaker panels and two transformer cabinets, cable trays everywhere overhead. */
    private static final String[] ELECTRICAL_FLOOR = {
            "BBBM.MBBB",
            ".........",
            "J.......J",
            "B.......B",
            "B.......X",
            ".........",
    };
    private static final String[] ELECTRICAL_OVER = {
            "....n....",
            "#########",
            "%...-...%",
            "%.......%",
            "%.......%",
            "L.......R",
    };

    /** A pit of black water a block deep, the dead sump pump standing in it, one grated walkway. */
    private static final String[] SUMP_FLOOR = {
            "~~HH~~~=~",
            "~~HH~~~=~",
            "~~~~~~~=~",
            "~~~~~~~=~",
            "~~~~~~~=~",
            "=========",
    };
    private static final String[] SUMP_OVER = {
            ".d..d..w.",
            "d..d.....",
            "......d..",
            "o.d......",
            ".........",
            "E.......L",
    };

    /** Where the crew ate and slept: lockers, the kettle on the counter, the logbook on the table. */
    private static final String[] BREAK_FLOOR = {
            "KK.Z..SS.",
            ".........",
            "m..hh....",
            "m.hTUh...",
            "........j",
            "X........",
    };
    private static final String[] BREAK_OVER = {
            "....n....",
            ".........",
            "....-....",
            ".........",
            "L........",
            "........E",
    };

    // ---- What the rooms are -------------------------------------------------------------------

    /** True for the room types built here. */
    static boolean isChamber(int type) {
        return type >= Level2Line.ROOM_PUMP_ROOM && type <= Level2Line.ROOM_GALLERY_ODD;
    }

    static int halfWidth(int type) {
        return (floorPlan(type)[0].length() - 1) / 2;
    }

    static int depth(int type) {
        return floorPlan(type).length;
    }

    /** True if nothing stands on floor cell (i, j): somewhere to walk (not the sump's water). */
    static boolean isFree(int type, int i, int j) {
        char c = cell(floorPlan(type), type, i, j);
        return c == '.' || c == '=' || c == 'j';
    }

    /** Every chamber has a steel door. */
    static boolean hasDoor(int type) {
        return true;
    }

    private static String[] floorPlan(int type) {
        return switch (type) {
            case Level2Line.ROOM_PUMP_ROOM -> PUMP_FLOOR;
            case Level2Line.ROOM_ELECTRICAL -> ELECTRICAL_FLOOR;
            case Level2Line.ROOM_SUMP -> SUMP_FLOOR;
            case Level2Line.ROOM_BREAK -> BREAK_FLOOR;
            default -> GALLERY_FLOOR; // the valve gallery, and the oddity's wrong one
        };
    }

    private static String[] overPlan(int type) {
        return switch (type) {
            case Level2Line.ROOM_PUMP_ROOM -> PUMP_OVER;
            case Level2Line.ROOM_ELECTRICAL -> ELECTRICAL_OVER;
            case Level2Line.ROOM_SUMP -> SUMP_OVER;
            case Level2Line.ROOM_BREAK -> BREAK_OVER;
            default -> GALLERY_OVER;
        };
    }

    /** The character of cell (i, j) in a drawing (i from -half width, j counted from the door); ' ' outside it. */
    private static char cell(String[] plan, int type, int i, int j) {
        int hw = (plan[0].length() - 1) / 2;
        int row = plan.length - 1 - j;
        if (row < 0 || row >= plan.length || i < -hw || i > hw) {
            return ' ';
        }
        return plan[row].charAt(i + hw);
    }

    private static boolean machineRoom(int type) {
        return type == Level2Line.ROOM_PUMP_ROOM || type == Level2Line.ROOM_ELECTRICAL;
    }

    // ---- Building one floor column ------------------------------------------------------------

    /**
     * One floor column (i, j) of a chamber. {@code side} is the corridor wall it opens off and
     * {@code salt} a per-room random number (shelf contents, which valves are open...).
     */
    static void fill(Level2Layout layout, int type, int side, long salt, int i, int j, LevelLayout.Column out) {
        String[] plan = floorPlan(type);
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        int top = ceiling - 1;
        int hw = halfWidth(type);
        int back = depth(type) - 1;
        Direction toDoor = Level2Line.inwardOf(side);
        Direction facing = wallFacing(i, j, hw, back, side);
        char c = cell(plan, type, i, j);
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        // Overhead first: things standing on the floor (a pump's riser) win where they meet it.
        fillOverhead(type, side, salt, i, j, hw, back, floor, top, out);
        switch (c) {
            case 'S' -> {
                BlockState shelf = Level2Blocks.SHELF.get().defaultBlockState()
                        .setValue(Level2ShelfBlock.VARIANT, Hash.range(Hash.mix(salt + i * 31L + j), 4));
                out.set(floor + 1, Level2Parts.tall(shelf, facing, false));
                out.set(floor + 2, Level2Parts.tall(shelf, facing, true));
            }
            case 'W', 'Y', 'Z' -> {
                out.set(floor + 1, Level2Blocks.WORKBENCH.get().defaultBlockState().setValue(WallMountBlock.FACING, facing));
                if (c != 'W') {
                    out.set(floor + 2, Level2ChamberBlocks.prop(c == 'Y' ? Prop.LOGBOOK : Prop.KETTLE, facing));
                }
            }
            case 'C' -> out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, facing));
            case 'K' -> out.set(floor + 1, ModBlocks.SUPPLY_LOCKER.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, facing));
            case 'B' -> {
                BlockState panel = Level2Blocks.BREAKER_PANEL.get().defaultBlockState();
                out.set(floor + 1, Level2Parts.tall(panel, facing, false));
                out.set(floor + 2, Level2Parts.tall(panel, facing, true));
                // Its conduits climb into the ceiling.
                out.set(top, Level2Parts.pipe(Direction.Axis.Y, facing.getOpposite(), Kind.CONDUIT, Size.SMALL, false));
            }
            case 'M' -> {
                BlockState machine = ModBlocks.L2_MACHINE.get().defaultBlockState().setValue(WallMountBlock.FACING, facing);
                out.set(floor + 1, machine);
                out.set(floor + 2, machine);
            }
            case 'J' -> {
                out.set(floor + 2, Level2Blocks.JUNCTION_BOX.get().defaultBlockState().setValue(WallMountBlock.FACING, facing));
                out.set(floor + 3, Level2Parts.pipe(Direction.Axis.Y, facing.getOpposite(), Kind.CONDUIT, Size.SMALL, false));
                out.set(top, Level2Parts.pipe(Direction.Axis.Y, facing.getOpposite(), Kind.CONDUIT, Size.SMALL, false));
            }
            case 'P', 'H' -> pump(plan, type, side, i, j, c == 'H' ? floor - 1 : floor, top, toDoor, out);
            case 'V' -> valveStation(type, salt, i, j, facing, floor, out);
            case 'X' -> out.set(floor + 1, ModBlocks.L1_EXTINGUISHER.get().defaultBlockState().setValue(WallMountBlock.FACING, facing));
            case 'T', 'U' -> {
                out.set(floor + 1, ModBlocks.L7_COFFEE_TABLE.get().defaultBlockState().setValue(WallMountBlock.FACING, toDoor));
                out.set(floor + 2, Level2ChamberBlocks.prop(c == 'T' ? Prop.LOGBOOK : Prop.MUG, toDoor.getOpposite()));
            }
            case 'h' -> out.set(floor + 1, ModBlocks.WOODEN_CHAIR.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, chairFacing(plan, type, i, j, side)));
            case 'm' -> out.set(floor + 1, ModBlocks.L1_MATTRESS.get().defaultBlockState().setValue(WallMountBlock.FACING, Direction.EAST));
            case 'j' -> out.set(floor + 2, Level2ChamberBlocks.prop(Prop.JACKET, facing));
            case '~' -> {
                // The sump: its floor one block down, black water up to the corridor's floor level.
                out.set(floor - 1, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
                out.set(floor, Blocks.WATER.defaultBlockState());
            }
            case '=' -> {
                out.set(floor - 1, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
                out.set(floor, Level2Parts.grating(i == 3 ? Direction.Axis.Z : Direction.Axis.X));
            }
            default -> { }
        }
    }

    /**
     * Which way something on cell (i, j) faces: away from the wall it stands against (the back
     * wall, a side wall, or the front wall beside the door), or towards the door in the open.
     */
    private static Direction wallFacing(int i, int j, int hw, int back, int side) {
        Direction toDoor = Level2Line.inwardOf(side);
        if (j == back && Math.abs(i) < hw) {
            return toDoor;
        }
        if (i == -hw) {
            return Direction.EAST;
        }
        if (i == hw) {
            return Direction.WEST;
        }
        return j == 0 ? toDoor.getOpposite() : toDoor;
    }

    /** A chair looks at the table next to it (west/east first, then behind or in front). */
    private static Direction chairFacing(String[] plan, int type, int i, int j, int side) {
        Direction toDoor = Level2Line.inwardOf(side);
        if (isTable(cell(plan, type, i + 1, j))) {
            return Direction.EAST;
        }
        if (isTable(cell(plan, type, i - 1, j))) {
            return Direction.WEST;
        }
        return isTable(cell(plan, type, i, j - 1)) ? toDoor : toDoor.getOpposite();
    }

    private static boolean isTable(char c) {
        return c == 'T' || c == 'U';
    }

    /**
     * One cell of a 2x2x2 pump set standing on {@code base} (the floor, or the sump's pit floor),
     * its front towards the door. wx/wz count from its smallest-x, smallest-z corner.
     */
    private static void pump(String[] plan, int type, int side, int i, int j, int base, int top, Direction toDoor,
                             LevelLayout.Column out) {
        char c = cell(plan, type, i, j);
        int wx = cell(plan, type, i - 1, j) == c ? 1 : 0;
        boolean backRow = cell(plan, type, i, j + 1) != c;
        // The back row is the smaller z on the north side of the corridor.
        int wz = backRow == (side < 0) ? 0 : 1;
        out.set(base, ModBlocks.PIPE_FLOOR.get().defaultBlockState()); // in the sump: the pit floor under it
        out.set(base + 1, Level2Blocks.PUMP.get().partAt(toDoor, wx, 0, wz));
        out.set(base + 2, Level2Blocks.PUMP.get().partAt(toDoor, wx, 1, wz));
        if (Level2Parts.pumpOutlet(toDoor, wx, wz)) {
            // Its discharge pipe rises into the header under the ceiling.
            for (int y = base + 3; y <= top; y++) {
                out.set(y, Level2Parts.centrePipe(Direction.Axis.Y, Kind.STEEL, Size.LARGE));
            }
        }
    }

    /**
     * A valve station on a manifold wall: the insulated main at the foot of the wall, a
     * quarter-turn valve on its branch, then a gauge or a stencilled label. In an ordinary gallery
     * most valves are open (handle along the pipe) and a few shut, as the last crew left them; in
     * the wrong_valves oddity every single one is shut the same way, yet the water still runs.
     */
    private static void valveStation(int type, long salt, int i, int j, Direction facing, int floor, LevelLayout.Column out) {
        long h = Hash.mix(salt + 977L * i + 31L * j);
        boolean open = type != Level2Line.ROOM_GALLERY_ODD && Hash.unit(h) < 0.75;
        out.set(floor + 1, Level2Parts.duct(facing, ((i + j) & 3) == 0));
        out.set(floor + 2, Level2Parts.leverValve(facing, open, false));
        out.set(floor + 3, ((i + j) & 1) == 0 ? Level2Parts.gauge(facing)
                : Level2Parts.stencil(facing, LABELS[Hash.range(Hash.mix(h), LABELS.length)]));
    }

    private static final Variant[] LABELS = {Variant.STEAM, Variant.RETURN, Variant.H2O};

    /** What hangs under the ceiling over cell (i, j), or high on the wall it stands against. */
    private static void fillOverhead(int type, int side, long salt, int i, int j, int hw, int back, int floor, int top,
                                     LevelLayout.Column out) {
        char c = cell(overPlan(type), type, i, j);
        Direction facing = wallFacing(i, j, hw, back, side);
        switch (c) {
            case '-' -> out.set(top, tube(Direction.Axis.X));
            case '|' -> out.set(top, tube(Direction.Axis.Z));
            case '=' -> out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.STEEL, Size.MEDIUM, Math.floorMod(i, 3) == 0, false));
            case '"' -> out.set(top, Level2Parts.hung(Direction.Axis.Z, Kind.STEEL, Size.MEDIUM, Math.floorMod(j, 3) == 0, false));
            case '#' -> out.set(top, Level2Parts.cableTray(Direction.Axis.X, false, (i & 1) == 0));
            case '%' -> out.set(top, Level2Parts.cableTray(Direction.Axis.Z, false, (j & 1) == 1));
            case 'L' -> out.set(floor + 3, machineRoom(type) ? Level2LightBlocks.mercury(facing) : Level2Parts.lamp(facing, false));
            case 'E' -> out.set(floor + 3, Level2LightBlocks.emergency(facing, true, true));
            case 'R' -> out.set(floor + 3, Level2LightBlocks.emergency(facing, true, false));
            case 'o' -> out.set(floor + 3, Level2Parts.lamp(facing, true));
            case 'g' -> out.set(floor + 3, Level2Parts.gauge(facing));
            case 'w' -> {
                out.set(floor + 2, Level2Parts.valve(facing, true));
                out.set(floor + 3, Level2Parts.gauge(facing));
            }
            case 'n' -> out.set(floor + 3, Level2Parts.stencil(facing, Variant.NO_SMOKING));
            case 'd' -> out.set(top, Level2Blocks.DRIP.get().defaultBlockState());
            default -> { }
        }
    }

    private static BlockState tube(Direction.Axis axis) {
        return ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis);
    }

    // ---- For the visit points ---------------------------------------------------------------------

    /**
     * Where a tester should stand to see the chamber's story, and what to look at, as
     * {stand i, stand j, look i, look j}: beside the logbook (towards the middle of the room); in
     * the sump, which has none, at the far end of the walkway, looking at the valve on the back wall.
     */
    static int[] storyCell(int type) {
        String[] plan = floorPlan(type);
        int hw = halfWidth(type);
        int depth = plan.length;
        int walkI = 0;
        int walkJ = -1;
        for (int row = 0; row < depth; row++) {
            for (int col = 0; col < plan[row].length(); col++) {
                char c = plan[row].charAt(col);
                int i = col - hw;
                int j = depth - 1 - row;
                if (c == 'Y' || c == 'T') {
                    // A book on a side wall: stand beside it towards the middle; elsewhere: in front of it.
                    return Math.abs(i) == hw ? new int[] {i - Integer.signum(i), j, i, j} : new int[] {i, j - 1, i, j};
                }
                if (c == '=' && j > walkJ) {
                    walkI = i;
                    walkJ = j;
                }
            }
        }
        return walkJ >= 0 ? new int[] {walkI, walkJ, walkI, depth} : new int[] {0, depth / 2, 0, depth};
    }
}
