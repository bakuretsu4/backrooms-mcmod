package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.OfficeFridgeBlock;
import com.rickr.backrooms.block.OfficeWindowBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.CubicleFrame;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.LobbyFrame;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.StairFrame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import static com.rickr.backrooms.world.layout.Level4AnomalyPlan.*;

/**
 * Turns {@link Level4AnomalyPlan}'s answers into blocks, for {@link Level4Layout#fillColumn}. Each
 * method writes the whole column (floor, walls, ceiling) and returns true, or returns false when
 * the column is an ordinary one that the plan builds as usual.
 */
public final class Level4AnomalyBlocks {
    /** Builds column (x, z) of {@code site}; false = not special, build it the normal way. */
    static boolean fill(Level4Layout layout, long seed, AnomalySite site, int x, int z, LevelLayout.Column out) {
        return switch (site.kind()) {
            case LOOPING_STAIRWELL -> {
                stairwell(layout, seed, site, x, z, out);
                yield true;
            }
            case WINDOW -> window(layout, seed, site, x, z, out);
            case VANISHING_ELEVATOR -> elevator(layout, seed, site, x, z, out);
            case CUBICLES -> {
                cubicles(layout, site, x, z, out);
                yield true;
            }
            default -> false;
        };
    }

    // ==========================================================================================
    // Looping stairwell
    // ==========================================================================================

    private static void stairwell(Level4Layout layout, long seed, AnomalySite site, int x, int z, LevelLayout.Column out) {
        StairFrame f = stairFrame(site);
        int lx = f.lx(x, z);
        int lz = f.lz(x, z);
        int cell = stairCell(lx, lz);
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        if (cell == S_MOAT) {
            // The strip of plain carpet around the core (no column, no furniture in the way).
            long c = Level4Plan.column(seed, x, z);
            out.set(floor, Level4Layout.floorBlock(Level4Plan.floor(c) & 3));
            out.set(ceiling, Level4Layout.ceilingBlock(Level4Plan.ceiling(c), Level4Plan.ceilingFacing(c)));
            return;
        }
        BlockState grey = ModBlocks.L4_STAIRWELL_WALL.get().defaultBlockState();
        BlockState concrete = ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();
        BlockState drywall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        Direction in = Direction.from2DDataValue(f.in());
        Direction outward = Direction.from2DDataValue(f.out());
        switch (cell) {
            case S_OUTER -> {
                // Grey all the way up and down, but office drywall where the office floor sees it.
                Level4Layout.fill(STAIR_BOTTOM, STAIR_TOP, grey, out);
                out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
                Level4Layout.fill(floor + 2, ceiling, drywall, out);
            }
            case S_INNER -> Level4Layout.fill(STAIR_BOTTOM, STAIR_TOP, grey, out);
            case S_DOOR -> {
                // The real door at the office floor, and a locked copy on every storey of the shaft:
                // climbing up you see "the same" door on the landing above, but it is only ever the
                // real one you can open (by then you are already back down here).
                BlockState door = ModBlocks.L4_STAIR_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, outward)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                        .setValue(DoorBlock.OPEN, false);
                for (int y = STAIR_BOTTOM; y <= STAIR_TOP; y++) {
                    int r = Math.floorMod(y - floor, STAIR_PERIOD);
                    boolean copy = y - r != floor;
                    BlockState locked = door.setValue(HouseDoorBlock.LOCKED, copy);
                    if (r == 0) {
                        out.set(y, concrete);
                    } else if (r == 1 && y + 1 < STAIR_TOP) {
                        out.set(y, locked.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    } else if (r == 2 && y - 1 > STAIR_BOTTOM) {
                        out.set(y, locked.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    } else {
                        out.set(y, grey);
                    }
                }
                Level4Layout.fill(floor + 3, ceiling, drywall, out); // over the door, seen from the office
            }
            case S_OPENING -> {
                for (int y = STAIR_BOTTOM; y <= STAIR_TOP; y++) {
                    int r = Math.floorMod(y - floor, STAIR_PERIOD);
                    if (r == 0) {
                        out.set(y, concrete);
                    } else if (r != 1 && r != 2) {
                        out.set(y, grey);
                    }
                }
            }
            case S_CENTRE -> Level4Layout.fill(STAIR_BOTTOM, STAIR_TOP, grey, out);
            default -> {
                // Inside: one tread (or landing) per storey, repeated all the way up and down.
                out.set(STAIR_BOTTOM, grey);
                out.set(STAIR_TOP, grey);
                int tread = stairTread(lx, lz);
                for (int y = STAIR_BOTTOM + 1; y < STAIR_TOP; y++) {
                    int r = Math.floorMod(y - floor, STAIR_PERIOD);
                    if (r == tread) {
                        out.set(y, switch (cell) {
                            // Stairs face the way you walk to go UP.
                            case S_UP -> stairs(in);
                            case S_DOWN -> stairs(outward);
                            default -> concrete;
                        });
                    } else if (lx == 2 && lz == 2 && r == 3) {
                        // A tube light over every landing, beside the door...
                        out.set(y, ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, in));
                    } else if (lx == 4 && lz == 8 && r == 0) {
                        // ...and one over every half landing (3 blocks above it).
                        out.set(y, ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, outward));
                    } else if (lx == 6 && lz == 2 && r == 2) {
                        // The floor number by the door, where you step off the stairs: always 4.
                        out.set(y, ModBlocks.L4_FLOOR_SIGN.get().defaultBlockState().setValue(WallMountBlock.FACING, in));
                    }
                }
            }
        }
    }

    private static BlockState stairs(Direction up) {
        return ModBlocks.CONCRETE_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, up);
    }

    // ==========================================================================================
    // The Window
    // ==========================================================================================

    private static boolean window(Level4Layout layout, long seed, AnomalySite site, int x, int z, LevelLayout.Column out) {
        int pane = windowPane(site, x, z);
        if (pane < 0) {
            return false;
        }
        long c = Level4Plan.column(seed, x, z);
        if (Level4Plan.kind(c) != Level4Plan.WINDOW) {
            return false; // never happens (the planner only picks window runs), but stay safe
        }
        int floor = layout.floorY();
        out.set(floor, Level4Layout.floorBlock(Level4Plan.floor(c)));
        out.set(layout.ceilingY(), Level4Layout.ceilingBlock(Level4Plan.ceiling(c), Level4Plan.ceilingFacing(c)));
        BlockState glass = (pane == 0 ? ModBlocks.L4_CLEAR_WINDOW : ModBlocks.L4_COVERED_WINDOW).get().defaultBlockState()
                .setValue(OfficeWindowBlock.FACING, Direction.from2DDataValue(Level4Plan.facing(c)));
        out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
        out.set(floor + 2, glass.setValue(OfficeWindowBlock.HALF, DoubleBlockHalf.LOWER));
        out.set(floor + 3, glass.setValue(OfficeWindowBlock.HALF, DoubleBlockHalf.UPPER));
        out.set(floor + 4, ModBlocks.OFFICE_WALL.get().defaultBlockState());
        return true;
    }

    // ==========================================================================================
    // Vanishing elevator
    // ==========================================================================================

    private static boolean elevator(Level4Layout layout, long seed, AnomalySite site, int x, int z, LevelLayout.Column out) {
        LobbyFrame f = lobbyFrame(site);
        int u = f.u(x, z);
        int v = f.v(x, z);
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        BlockState steel = ModBlocks.L4_ELEVATOR_WALL.get().defaultBlockState();
        int car = f.carAt(u, v);
        if (car >= 0) {
            // Inside a car: rubber floor, three blocks of air, a lit panel in the middle of the ceiling.
            out.set(floor, ModBlocks.L4_ELEVATOR_FLOOR.get().defaultBlockState());
            BlockState top = steel;
            if (v == f.carLightV(car)) {
                boolean first = u == f.doorU(car);
                top = Level4Layout.panel(first ? f.plusU() : opposite(f.plusU()), LampMode.LIT);
            }
            out.set(floor + 4, top);
            out.set(ceiling, steel);
            return true;
        }
        if (f.carWallAt(u, v) >= 0) {
            out.set(floor, ModBlocks.L4_ELEVATOR_FLOOR.get().defaultBlockState());
            Level4Layout.fill(floor + 1, ceiling, steel, out);
            return true;
        }
        int door = f.doorAt(u, v);
        if (door >= 0 && door == initialOpenElevator(site)) {
            // The one standing open: the leaves are gone, the header with the dead indicator stays.
            long c = Level4Plan.column(seed, x, z);
            out.set(floor, Level4Layout.floorBlock(Level4Plan.floor(c)));
            out.set(ceiling, Level4Layout.ceilingBlock(Level4Plan.ceiling(c), Level4Plan.ceilingFacing(c)));
            out.set(floor + 3, elevatorDoor(f, door, u - f.doorU(door), ElevatorDoorBlock.Part.HEADER));
            out.set(floor + 4, ModBlocks.OFFICE_WALL.get().defaultBlockState());
            return true;
        }
        return false;
    }

    /** One block of elevator {@code door}'s closed door (column 0 or 1, as seen in the plan). */
    public static BlockState elevatorDoor(LobbyFrame f, int door, int column, ElevatorDoorBlock.Part part) {
        return ModBlocks.ELEVATOR_DOOR.get().defaultBlockState()
                .setValue(ElevatorDoorBlock.FACING, Direction.from2DDataValue(f.out(door)))
                .setValue(ElevatorDoorBlock.RIGHT, f.right(door, column))
                .setValue(ElevatorDoorBlock.PART, part);
    }

    // ==========================================================================================
    // Level 4.3, the Cubicles
    // ==========================================================================================

    private static void cubicles(Level4Layout layout, AnomalySite site, int x, int z, LevelLayout.Column out) {
        CubicleFrame f = cubicleFrame(site);
        int u = f.u(x, z);
        int v = f.v(x, z);
        int packed = cubicleCell(u, v);
        int cell = packed & 0xFF;
        Direction facing = Direction.from2DDataValue(cubicleWorldDir(f, packed >> 8));
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        boolean hall = v < FIELD_V0;
        BlockState white = ModBlocks.L4_WHITE_WALL.get().defaultBlockState();
        BlockState drywall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        out.set(floor, hall ? ModBlocks.L4_WHITE_FLOOR.get().defaultBlockState() : ModBlocks.OFFICE_CARPET.get().defaultBlockState());
        out.set(ceiling, cubicleCeiling(f, u, v));
        switch (cell) {
            case C_OUTER -> {
                out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
                Level4Layout.fill(floor + 2, ceiling - 1, drywall, out);
            }
            case C_WHITE -> Level4Layout.fill(floor + 1, ceiling - 1, white, out);
            case C_DOORWAY -> out.set(ceiling - 1, v == Level4Plan.CORE_LO ? drywall : white); // 3 tall, lintel above
            case C_PARTITION -> {
                int bits = 0;
                for (int d = 0; d < 4; d++) {
                    int nx = x + stepX(d);
                    int nz = z + stepZ(d);
                    if ((cubicleCell(f.u(nx, nz), f.v(nx, nz)) & 0xFF) == C_PARTITION) {
                        bits |= 1 << d;
                    }
                }
                out.set(floor + 1, Level4Layout.partition(bits));
            }
            case C_DESK -> out.set(floor + 1, Level4Layout.faced(ModBlocks.OFFICE_DESK.get(), facing));
            case C_DESK_PC -> out.set(floor + 1, Level4Layout.faced(ModBlocks.OFFICE_DESK_PC.get(), facing));
            case C_CHAIR -> out.set(floor + 1, Level4Layout.faced(ModBlocks.OFFICE_CHAIR.get(), facing));
            case C_FRIDGE -> {
                // Some still have a bottle or two of Almond Water in them (they all look the same).
                double r = Hash.unit(site.salt(), x, z, 7950);
                int uses = r < 0.45 ? 0 : r < 0.8 ? 1 : 2;
                out.set(floor + 1, Level4Layout.faced(ModBlocks.L4_OFFICE_FRIDGE.get(), facing)
                        .setValue(OfficeFridgeBlock.USES, uses));
            }
            default -> {
            }
        }
    }

    /**
     * Ceiling of the Cubicles: in the hallways a panel pair every 12 blocks (only some of them
     * work: they are dim), in the field a lit panel over every aisle and cross aisle of the tile.
     * Everything here repeats with the field's pattern, so the endless field looks the same after a shift.
     */
    private static BlockState cubicleCeiling(CubicleFrame f, int u, int v) {
        BlockState tile = ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        if (u < FIELD_U0 || u > Level4Plan.CORE_HI - 1 || v <= Level4Plan.CORE_LO || v >= Level4Plan.CORE_HI) {
            return tile;
        }
        int minusU = opposite(f.plusU());
        if (v < FIELD_V0) {
            if (v != 6 && v != 9 || u < 6 || u > 42) {
                return tile;
            }
            int m = Math.floorMod(u, 12);
            if (m == 0 || m == 1) {
                return Level4Layout.panel(m == 0 ? f.plusU() : minusU, LampMode.LIT);
            }
            if (m == 6 || m == 7) {
                return Level4Layout.panel(m == 6 ? f.plusU() : minusU, LampMode.DEAD);
            }
            return tile;
        }
        int a = Math.floorMod(u - FIELD_U0, FIELD_PERIOD_U);
        int b = Math.floorMod(v - FIELD_V0, FIELD_PERIOD_V);
        if (b == 0 && (a == 1 || a == 2)) {
            return Level4Layout.panel(a == 1 ? f.plusU() : minusU, LampMode.LIT);
        }
        if (a == 10 && (b == 4 || b == 5)) {
            return Level4Layout.panel(b == 4 ? f.plusV() : opposite(f.plusV()), LampMode.LIT);
        }
        return tile;
    }

    private Level4AnomalyBlocks() {}
}
