package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CubiclePartitionBlock;
import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.OfficeCeilingDiffuserBlock;
import com.rickr.backrooms.block.OfficeCeilingFixtureBlock;
import com.rickr.backrooms.block.OfficeDoorFrameBlock;
import com.rickr.backrooms.block.OfficeExitSignBlock;
import com.rickr.backrooms.block.OfficeFloorDetailBlock;
import com.rickr.backrooms.block.OfficeLampBlock;
import com.rickr.backrooms.block.OfficePanelBlock;
import com.rickr.backrooms.block.OfficePerimeterBlock;
import com.rickr.backrooms.block.OfficeTableItemsBlock;
import com.rickr.backrooms.block.OfficeWallFixtureBlock;
import com.rickr.backrooms.block.OfficeWallMarkBlock;
import com.rickr.backrooms.block.OfficeWallPlateBlock;
import com.rickr.backrooms.block.OfficeWindowBlock;
import com.rickr.backrooms.block.VendingMachineBlock;
import com.rickr.backrooms.block.WaterCoolerBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 4, "The Abandoned Office", as canon has it: an endless, empty office building almost
 * devoid of furniture. Grey carpet tiles, white drywall, a suspended ceiling with 1x2 louvre
 * troffers (most of them dead) and air diffusers, sprinklers and smoke detectors between them,
 * long ribbons of mostly blacked-out windows that still glow, in 3-pane bays over a perimeter casing
 * (a slim radiator under each bay); empty rooms, conference rooms with the one identical table,
 * lobbies and cafeterias run together, rare cubicle clusters. With so little furniture the detail
 * is on the building: door frames and room signs, switches, thermostats, fire equipment, marks
 * where things hung, clocks, notice boards, exit signs, and Almond Water in coolers, fountains and
 * vending machines everywhere. No monsters: it's a safe haven, if a lonely one.
 *
 * <p>The floor plan itself (zones, rooms, furniture) lives in {@link Level4Plan}, which describes
 * every column with plain numbers; this class turns those numbers into blocks and adds the parts
 * that use the shared building code:
 * <ul>
 *   <li><b>Exit:</b> one emergency stairwell per 288 x 288 region (the shared stairwell tower of
 *       Levels 0/1, here with office walls and a steel fire door under a glowing EXIT sign, the
 *       fire door signs, an extinguisher, a call point and an evacuation plan beside it). The
 *       open, brightly lit floor around it makes the sign easy to spot. Going down the stairs
 *       (below y30) takes you on to the Poolrooms. Some elevator lobbies also have an exit
 *       elevator going the same way ({@link Level4AnomalyPlan#ELEVATOR_EXIT}).</li>
 *   <li><b>Safe rooms:</b> a small office box standing in the middle of an open-plan floor
 *       ({@link SafeRoomPlan}), with the checkpoint radio, a cot and a crate.</li>
 *   <li><b>Anomalies:</b> looping stairwells, the Window, vanishing elevators, the way into
 *       Level 4.3 "The Cubicles", and three quiet ones that only look wrong: the Occupied Floor,
 *       the Inside-Out Room and the Cooler Row ({@link Level4AnomalyPlan}, blocks in
 *       {@link Level4AnomalyBlocks}).</li>
 * </ul>
 *
 * <p>Heights: floor y40, four blocks of air, the drop ceiling at y45 (like the Lobby).
 */
public final class Level4Layout extends LevelLayout {
    public Level4Layout() {
        super(4, Level4Plan.EXITS);
    }

    // ==========================================================================================
    // Queries
    // ==========================================================================================

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int d = exits().distance(seed, x, z);
        if (d <= STAIRWELL_RADIUS) return false;
        if (d <= Level4Plan.CLEARING_RADIUS) return true;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            return !safe.contains(x, z) || safe.isWalkable(x, z);
        }
        AnomalySite site = Level4AnomalyPlan.GRID.at(seed, x, z);
        if (site != null) {
            int walkable = Level4AnomalyPlan.walkable(site, x, z);
            if (walkable >= 0) {
                return walkable == 1;
            }
        }
        return Level4Plan.walkable(Level4Plan.column(seed, x, z));
    }

    /** Arrivals land on open floor only: never in a doorway or squeezed between furniture. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        int d = exits().distance(seed, x, z);
        if (d <= STAIRWELL_RADIUS) return false;
        if (d <= Level4Plan.CLEARING_RADIUS) return true;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            return !safe.contains(x, z) || safe.role(x, z) == SafeRoomPlan.FLOOR;
        }
        AnomalySite site = Level4AnomalyPlan.GRID.at(seed, x, z);
        if (site != null && Level4AnomalyPlan.blocksArrival(site, x, z)) {
            return false;
        }
        return Level4Plan.kind(Level4Plan.column(seed, x, z)) == Level4Plan.FLOOR;
    }

    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomNear(seed, x, z);
        return plan != null && plan.contains(x, z);
    }

    // ------------------------------------------------------------------------------------------
    // Anomalies (see Level4AnomalyPlan)
    // ------------------------------------------------------------------------------------------

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return Level4AnomalyPlan.GRID.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        Level4AnomalyPlan.GRID.near(seed, x, z, radius, out);
    }

    /** Searches up to 16 zones (768 blocks) away: even Level 4.3's rare entrances turn up. */
    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return Level4AnomalyPlan.GRID.nearest(seed, x, z, site -> site.kind().equals(kind), 16);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level4AnomalyPlan.KINDS;
    }

    /** The safe room whose box or surrounding strip covers (x, z), or null. */
    @Nullable
    private static SafeRoomPlan safeRoomNear(long seed, int x, int z) {
        int cx = Math.floorDiv(x, Level4Plan.CELL);
        int cz = Math.floorDiv(z, Level4Plan.CELL);
        long room = Level4Plan.safeRoom(seed, cx, cz);
        if (room < 0) {
            return null;
        }
        int size = Level4Plan.safeSize(room);
        int x0 = Level4Plan.safeX0(room, cx);
        int z0 = Level4Plan.safeZ0(room, cz);
        int m = Level4Plan.SAFE_MOAT;
        if (x < x0 - m || x >= x0 + size + m || z < z0 - m || z >= z0 + size + m) {
            return null;
        }
        return new SafeRoomPlan(x0, z0, size, size, Direction.from2DDataValue(Level4Plan.safeDoor(room)), 1);
    }

    // ==========================================================================================
    // Generation
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        BlockState wallBase = ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState();
        if (stairwell(seed, dx, dz, x - dx, z - dz, wall, wallBase, ModBlocks.FIRE_DOOR.get(), false, out)) {
            return;
        }
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= Level4Plan.CLEARING_RADIUS) {
            fillClearing(seed, x - dx, z - dz, dx, dz, out);
            return;
        }
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            fillSafeRoom(safe, x, z, out);
            return;
        }
        // Anomalous areas build their own columns (the rest of their site is ordinary office).
        AnomalySite site = Level4AnomalyPlan.GRID.at(seed, x, z);
        if (site != null && Level4AnomalyBlocks.fill(this, seed, site, x, z, out)) {
            return;
        }
        fillPlanColumn(seed, x, z, out);
    }

    /** A column builder that keeps nothing (for asking {@link Level4AnomalyBlocks#fill} whether it would build a column). */
    private static final Column NOWHERE = (y, state) -> {
    };

    /**
     * True when {@link #fillColumn} builds column (x, z) from the plan alone: not the exit stairwell
     * or its clearing, not a safe room or its strip, not a column an anomalous area builds itself.
     * (Checked in the same order as fillColumn.)
     */
    boolean buildsFromPlan(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= Level4Plan.CLEARING_RADIUS || safeRoomNear(seed, x, z) != null) {
            return false;
        }
        AnomalySite site = Level4AnomalyPlan.GRID.at(seed, x, z);
        return site == null || !Level4AnomalyBlocks.fill(this, seed, site, x, z, NOWHERE);
    }

    /**
     * Builds column (x, z) from {@link Level4Plan} alone (no stairwell, safe room or anomaly): the
     * anomaly builders call it too when they only add something to an ordinary column.
     */
    void fillPlanColumn(long seed, int x, int z, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        BlockState wallBase = ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState();
        long c = Level4Plan.column(seed, x, z);
        out.set(floor, floorBlock(Level4Plan.floor(c)));
        out.set(ceiling, ceilingBlock(Level4Plan.ceiling(c), Level4Plan.ceilingFacing(c)));
        Direction facing = Direction.from2DDataValue(Level4Plan.facing(c));
        int variant = Level4Plan.variant(c);
        int detail = Level4Plan.detail(c);
        if (detail != Level4Plan.DETAIL_NONE) {
            // Lies flat on the floor in the first air block (placed first: anything else goes over it).
            OfficeFloorDetailBlock.Kind kind = switch (detail) {
                case Level4Plan.DETAIL_FLOOR_BOX -> OfficeFloorDetailBlock.Kind.FLOOR_BOX;
                case Level4Plan.DETAIL_THRESHOLD -> OfficeFloorDetailBlock.Kind.THRESHOLD;
                default -> OfficeFloorDetailBlock.Kind.STAIN;
            };
            out.set(floor + 1, faced(ModBlocks.OFFICE_FLOOR_DETAIL.get(), Direction.from2DDataValue(Level4Plan.detailFacing(c)))
                    .setValue(OfficeFloorDetailBlock.KIND, kind));
        }
        switch (Level4Plan.kind(c)) {
            case Level4Plan.WALL -> fillWall(Level4Plan.style(c), variant, facing, out);
            case Level4Plan.FILL -> fill(floor + 1, ceiling - 1, wall, out);
            case Level4Plan.COLUMN -> {
                out.set(floor + 1, ModBlocks.OFFICE_COLUMN_BASE.get().defaultBlockState());
                fill(floor + 2, ceiling - 1, ModBlocks.OFFICE_COLUMN.get().defaultBlockState(), out);
            }
            // 3 tall, the frame's head above.
            case Level4Plan.DOORWAY -> out.set(ceiling - 1, frame(OfficeDoorFrameBlock.Part.HEAD, frameAxis(variant), false));
            case Level4Plan.DOOR -> fillDoor(facing, variant, out);
            case Level4Plan.WINDOW -> {
                BlockState pane = ModBlocks.OFFICE_WINDOW.get().defaultBlockState()
                        .setValue(OfficeWindowBlock.FACING, facing)
                        .setValue(OfficeWindowBlock.PANE, pane(variant & 3))
                        .setValue(OfficeWindowBlock.COVER, cover(variant >> 2));
                out.set(floor + 1, wallBase); // the sill wall
                out.set(floor + 2, pane.setValue(OfficeWindowBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 3, pane.setValue(OfficeWindowBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 4, wall);
            }
            case Level4Plan.PERIMETER -> out.set(floor + 1, ModBlocks.OFFICE_PERIMETER.get().defaultBlockState()
                    .setValue(OfficePerimeterBlock.FACING, facing)
                    .setValue(OfficePerimeterBlock.PART, part(variant)));
            case Level4Plan.ELEVATOR -> {
                BlockState door = ModBlocks.ELEVATOR_DOOR.get().defaultBlockState()
                        .setValue(ElevatorDoorBlock.FACING, facing)
                        .setValue(ElevatorDoorBlock.RIGHT, variant == 1);
                out.set(floor + 1, door.setValue(ElevatorDoorBlock.PART, ElevatorDoorBlock.Part.LOWER));
                out.set(floor + 2, door.setValue(ElevatorDoorBlock.PART, ElevatorDoorBlock.Part.UPPER));
                out.set(floor + 3, door.setValue(ElevatorDoorBlock.PART, ElevatorDoorBlock.Part.HEADER));
                out.set(floor + 4, ModBlocks.OFFICE_WALL.get().defaultBlockState());
            }
            case Level4Plan.DESK -> out.set(floor + 1, faced(ModBlocks.OFFICE_DESK.get(), facing));
            case Level4Plan.DESK_PC -> out.set(floor + 1, faced(ModBlocks.OFFICE_DESK_PC.get(), facing));
            case Level4Plan.CHAIR -> out.set(floor + 1, faced(ModBlocks.OFFICE_CHAIR.get(), facing));
            case Level4Plan.CABINET -> out.set(floor + 1, faced(ModBlocks.FILING_CABINET.get(), facing));
            case Level4Plan.PLANT -> out.set(floor + 1, faced(ModBlocks.DEAD_PLANT.get(), facing));
            case Level4Plan.BOXES -> stack(faced(ModBlocks.CARDBOARD_BOXES.get(), facing), variant == 1 ? 2 : 1, out);
            case Level4Plan.LADDER -> out.set(floor + 1, faced(ModBlocks.STEP_LADDER.get(), facing));
            case Level4Plan.CRATE -> stack(faced(ModBlocks.SUPPLY_CRATE.get(), facing), variant == 1 ? 2 : 1, out);
            // Was a Supply Locker (saved for a future level); the supply filing cabinet works the same.
            case Level4Plan.LOCKER -> out.set(floor + 1, faced(ModBlocks.SUPPLY_CABINET.get(), facing));
            case Level4Plan.SUPPLY_CABINET -> out.set(floor + 1, faced(ModBlocks.SUPPLY_CABINET.get(), facing));
            case Level4Plan.BACKPACK -> out.set(floor + 1, faced(ModBlocks.BACKPACK.get(), facing));
            case Level4Plan.VENDING -> {
                BlockState machine = faced(ModBlocks.VENDING_MACHINE.get(), facing)
                        .setValue(VendingMachineBlock.STOCK, Math.min(variant, VendingMachineBlock.MAX_STOCK));
                out.set(floor + 1, machine.setValue(VendingMachineBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, machine.setValue(VendingMachineBlock.HALF, DoubleBlockHalf.UPPER));
            }
            case Level4Plan.COOLER -> out.set(floor + 1, faced(ModBlocks.WATER_COOLER.get(), facing)
                    .setValue(WaterCoolerBlock.USES, Math.min(variant, WaterCoolerBlock.MAX_USES)));
            case Level4Plan.FOUNTAIN -> out.set(floor + 1, faced(ModBlocks.OFFICE_FOUNTAIN.get(), facing)
                    .setValue(WaterCoolerBlock.USES, Math.min(variant, WaterCoolerBlock.MAX_USES)));
            case Level4Plan.COUNTER -> {
                out.set(floor + 1, faced(ModBlocks.KITCHEN_COUNTER.get(), facing));
                if (variant == 1) {
                    out.set(floor + 2, faced(ModBlocks.MICROWAVE.get(), facing));
                }
            }
            case Level4Plan.FRIDGE -> out.set(floor + 1, faced(ModBlocks.FRIDGE.get(), facing));
            case Level4Plan.CONF_TABLE -> out.set(floor + 1, faced(ModBlocks.CONFERENCE_TABLE.get(), facing));
            case Level4Plan.SOFA -> out.set(floor + 1, faced(ModBlocks.SOFA.get(), facing));
            case Level4Plan.SERVER_RACK -> stack(faced(ModBlocks.SERVER_RACK.get(), facing), 2, out);
            case Level4Plan.CURTAIN -> fill(floor + 1, ceiling - 1, ModBlocks.STAGE_CURTAIN.get().defaultBlockState(), out);
            case Level4Plan.PARTITION -> out.set(floor + 1, partition(variant));
            default -> {
            }
        }
        int top = Level4Plan.top(c);
        if (top != Level4Plan.TOP_NONE) {
            // On the table or desk: the block above it (its model reaches down onto the tabletop).
            OfficeTableItemsBlock.Kind kind = switch (top) {
                case Level4Plan.TOP_PAPERS -> OfficeTableItemsBlock.Kind.PAPERS;
                case Level4Plan.TOP_PENCILS -> OfficeTableItemsBlock.Kind.PENCILS;
                case Level4Plan.TOP_MUG -> OfficeTableItemsBlock.Kind.MUG;
                case Level4Plan.TOP_PAPERS_MUG -> OfficeTableItemsBlock.Kind.PAPERS_MUG;
                case Level4Plan.TOP_BOTTLE -> OfficeTableItemsBlock.Kind.BOTTLE;
                case Level4Plan.TOP_CUBICLE_SET -> OfficeTableItemsBlock.Kind.CUBICLE_SET;
                default -> OfficeTableItemsBlock.Kind.BURGER_TRAY;
            };
            out.set(floor + 2, faced(ModBlocks.OFFICE_TABLE_ITEMS.get(), facing).setValue(OfficeTableItemsBlock.KIND, kind));
        }
        placeDeco(c, out);
    }

    /** The window block's COVER for a plan cover (Level4Plan.COVER_*). */
    private static OfficeWindowBlock.Cover cover(int cover) {
        return switch (cover) {
            case Level4Plan.COVER_HALF -> OfficeWindowBlock.Cover.HALF;
            case Level4Plan.COVER_BLINDS -> OfficeWindowBlock.Cover.BLINDS;
            case Level4Plan.COVER_TAPED -> OfficeWindowBlock.Cover.TAPED;
            default -> OfficeWindowBlock.Cover.NONE;
        };
    }

    /** The axis a framed wall runs along (Level4Plan.FRAME_AXIS_Z in a wall or doorway variant). */
    private static Direction.Axis frameAxis(int variant) {
        return (variant & Level4Plan.FRAME_AXIS_Z) != 0 ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** One block of a door frame: a jamb beside the opening (with skirting at the bottom) or the head over it. */
    private static BlockState frame(OfficeDoorFrameBlock.Part part, Direction.Axis axis, boolean base) {
        return ModBlocks.OFFICE_DOOR_FRAME.get().defaultBlockState()
                .setValue(OfficeDoorFrameBlock.PART, part)
                .setValue(OfficeDoorFrameBlock.AXIS, axis)
                .setValue(OfficeDoorFrameBlock.BASE, base);
    }

    /** The window block's PANE for a plan window variant (Level4Plan.PANE_*). */
    private static OfficeWindowBlock.Pane pane(int variant) {
        return switch (variant) {
            case Level4Plan.PANE_SINGLE -> OfficeWindowBlock.Pane.SINGLE;
            case Level4Plan.PANE_LEFT -> OfficeWindowBlock.Pane.LEFT;
            case Level4Plan.PANE_RIGHT -> OfficeWindowBlock.Pane.RIGHT;
            default -> OfficeWindowBlock.Pane.MID;
        };
    }

    /** The perimeter block's PART for a plan perimeter variant (Level4Plan.PART_*). */
    private static OfficePerimeterBlock.Part part(int variant) {
        return switch (variant) {
            case Level4Plan.PART_RADIATOR -> OfficePerimeterBlock.Part.RADIATOR;
            case Level4Plan.PART_SOCKET -> OfficePerimeterBlock.Part.SOCKET;
            case Level4Plan.PART_END -> OfficePerimeterBlock.Part.END;
            default -> OfficePerimeterBlock.Part.TRUNK;
        };
    }

    /** The floor block for a plan floor (material plus the indent flag). */
    static BlockState floorBlock(int floor) {
        boolean indent = (floor & Level4Plan.INDENT) != 0;
        return switch (floor & 7) {
            case Level4Plan.CARPET_BLUE -> (indent ? ModBlocks.OFFICE_CARPET_BLUE_INDENT : ModBlocks.OFFICE_CARPET_BLUE).get().defaultBlockState();
            case Level4Plan.CONCRETE -> ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();
            case Level4Plan.VINYL -> ModBlocks.OFFICE_VINYL_FLOOR.get().defaultBlockState();
            case Level4Plan.TERRAZZO -> ModBlocks.OFFICE_TERRAZZO.get().defaultBlockState();
            case Level4Plan.ENTRANCE_MAT -> ModBlocks.OFFICE_ENTRANCE_MAT.get().defaultBlockState();
            default -> (indent ? ModBlocks.OFFICE_CARPET_INDENT : ModBlocks.OFFICE_CARPET).get().defaultBlockState();
        };
    }

    static BlockState ceilingBlock(int ceiling, int facing) {
        return switch (ceiling) {
            case Level4Plan.TILE_MISSING -> ModBlocks.OFFICE_CEILING_MISSING.get().defaultBlockState();
            case Level4Plan.HATCH -> ModBlocks.OFFICE_CEILING_HATCH.get().defaultBlockState();
            case Level4Plan.STAINED -> ModBlocks.OFFICE_CEILING_STAINED.get().defaultBlockState();
            case Level4Plan.PANEL_LIT -> panel(facing, LampMode.LIT);
            case Level4Plan.PANEL_FLICKER -> panel(facing, LampMode.FLICKER);
            case Level4Plan.PANEL_DEAD -> panel(facing, LampMode.DEAD);
            case Level4Plan.DIFFUSER -> diffuser(OfficeCeilingDiffuserBlock.Kind.SQUARE, Direction.Axis.X);
            case Level4Plan.SLOT -> diffuser(OfficeCeilingDiffuserBlock.Kind.SLOT, Direction.from2DDataValue(facing).getAxis());
            case Level4Plan.RETURN_GRILLE -> diffuser(OfficeCeilingDiffuserBlock.Kind.RETURN, Direction.Axis.X);
            case Level4Plan.SPRINKLER -> fixture(OfficeCeilingFixtureBlock.Kind.SPRINKLER);
            case Level4Plan.SMOKE -> fixture(OfficeCeilingFixtureBlock.Kind.SMOKE);
            case Level4Plan.SPEAKER -> fixture(OfficeCeilingFixtureBlock.Kind.SPEAKER);
            case Level4Plan.PROJECTOR -> fixture(OfficeCeilingFixtureBlock.Kind.PROJECTOR);
            case Level4Plan.CABLE -> fixture(OfficeCeilingFixtureBlock.Kind.CABLE);
            case Level4Plan.LIFTED -> fixture(OfficeCeilingFixtureBlock.Kind.LIFTED);
            case Level4Plan.COFFER_LIT -> coffer(LampMode.LIT);
            case Level4Plan.COFFER_FLICKER -> coffer(LampMode.FLICKER);
            case Level4Plan.COFFER_DEAD -> coffer(LampMode.DEAD);
            default -> ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        };
    }

    /** A ceiling tile with an air diffuser or grille ({@code axis}: the way a slot diffuser's slots run). */
    private static BlockState diffuser(OfficeCeilingDiffuserBlock.Kind kind, Direction.Axis axis) {
        return ModBlocks.OFFICE_CEILING_DIFFUSER.get().defaultBlockState()
                .setValue(OfficeCeilingDiffuserBlock.KIND, kind)
                .setValue(OfficeCeilingDiffuserBlock.AXIS, axis);
    }

    /** A ceiling tile with a sprinkler head, smoke detector or speaker grille. */
    private static BlockState fixture(OfficeCeilingFixtureBlock.Kind kind) {
        return ModBlocks.OFFICE_CEILING_FIXTURE.get().defaultBlockState().setValue(OfficeCeilingFixtureBlock.KIND, kind);
    }

    private static BlockState coffer(LampMode mode) {
        return ModBlocks.OFFICE_COFFER_LIGHT.get().defaultBlockState().setValue(OfficeLampBlock.MODE, mode);
    }

    static BlockState panel(int facing, LampMode mode) {
        return ModBlocks.OFFICE_PANEL.get().defaultBlockState()
                .setValue(OfficePanelBlock.FACING, Direction.from2DDataValue(facing))
                .setValue(OfficePanelBlock.MODE, mode);
    }

    /**
     * A drywall wall from floor to ceiling; its style decides the bottom (or glass). Beside an
     * opening it is the opening's frame instead: jamb blocks (the bottom one with skirting) up to the
     * opening's height. A servery wall has its shuttered hatch at eye height.
     */
    private void fillWall(int style, int variant, Direction facing, Column out) {
        int floor = floorY();
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        switch (style) {
            case Level4Plan.STYLE_RAIL -> out.set(floor + 1, ModBlocks.OFFICE_WALL_RAIL.get().defaultBlockState());
            case Level4Plan.STYLE_GLASS -> fill(floor + 1, floor + 3, ModBlocks.OFFICE_GLASS.get().defaultBlockState(), out);
            case Level4Plan.STYLE_BLINDS -> {
                out.set(floor + 1, ModBlocks.OFFICE_GLASS.get().defaultBlockState());
                fill(floor + 2, floor + 3, ModBlocks.OFFICE_GLASS_BLINDS.get().defaultBlockState(), out);
            }
            case Level4Plan.STYLE_GLAZED -> {
                out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
                fill(floor + 2, floor + 3, ModBlocks.OFFICE_GLASS_BLINDS.get().defaultBlockState(), out);
            }
            default -> out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
        }
        // Glass walls stop under a strip of drywall (the bulkhead the glass hangs from).
        boolean glass = style == Level4Plan.STYLE_GLASS || style == Level4Plan.STYLE_BLINDS || style == Level4Plan.STYLE_GLAZED;
        int from = glass ? floor + 4 : floor + 2;
        fill(from, ceilingY() - 1, wall, out);
        if (style == Level4Plan.STYLE_HATCH) {
            out.set(floor + 2, faced(ModBlocks.OFFICE_SERVERY_HATCH.get(), facing));
        }
        int jamb = variant & (Level4Plan.JAMB_LEFT | Level4Plan.JAMB_RIGHT);
        if (style == Level4Plan.STYLE_BASE && jamb != 0) {
            OfficeDoorFrameBlock.Part part = jamb == Level4Plan.JAMB_LEFT ? OfficeDoorFrameBlock.Part.LEFT : OfficeDoorFrameBlock.Part.RIGHT;
            Direction.Axis axis = frameAxis(variant);
            int top = (variant & Level4Plan.JAMB_TALL) != 0 ? floor + 3 : floor + 2;
            out.set(floor + 1, frame(part, axis, true));
            for (int y = floor + 2; y <= top; y++) {
                out.set(y, frame(part, axis, false));
            }
        }
    }

    /** A door: two door blocks and the wall above them. */
    private void fillDoor(Direction facing, int variant, Column out) {
        int floor = floorY();
        int type = variant & 3;
        Block block = switch (type) {
            case Level4Plan.DOOR_SIX -> ModBlocks.OFFICE_DOOR_SIX.get();
            case Level4Plan.DOOR_GLASS -> ModBlocks.OFFICE_GLASS_DOOR.get();
            default -> ModBlocks.OFFICE_DOOR.get();
        };
        BlockState door = block.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.OPEN, (variant & Level4Plan.DOOR_OPEN) != 0)
                .setValue(DoorBlock.HINGE, (variant & Level4Plan.DOOR_HINGE_RIGHT) != 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        if (door.hasProperty(HouseDoorBlock.LOCKED)) {
            door = door.setValue(HouseDoorBlock.LOCKED, (variant & Level4Plan.DOOR_LOCKED) != 0);
        }
        out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // The frame's head over the door, the wall above it.
        out.set(floor + 3, frame(OfficeDoorFrameBlock.Part.HEAD, facing.getClockWise().getAxis(), false));
        fill(floor + 4, ceilingY() - 1, ModBlocks.OFFICE_WALL.get().defaultBlockState(), out);
    }

    /**
     * Things hung on the wall (in the air of this column, against the wall beside it), each at the
     * height its model is made for: switches, sockets, extinguishers, dado trunking and door stops in
     * the first block above the floor (an extinguisher's sign reaches into the block above);
     * thermostats, call points, whiteboards, notice boards, marks, signs, boxes and call buttons at
     * eye height; clocks, TV brackets and wall exit signs higher up; cameras, hanging exit signs and
     * pendant lamps just under the ceiling.
     */
    private void placeDeco(long c, Column out) {
        int deco = Level4Plan.deco(c);
        if (deco == Level4Plan.DECO_NONE) {
            return;
        }
        Direction facing = Direction.from2DDataValue(Level4Plan.decoFacing(c));
        int v = Level4Plan.decoVariant(c);
        int floor = floorY();
        switch (deco) {
            case Level4Plan.DECO_WHITEBOARD -> out.set(floor + 2, faced(ModBlocks.WHITEBOARD.get(), facing));
            case Level4Plan.DECO_ELEVATOR_PANEL -> out.set(floor + 2, faced(ModBlocks.ELEVATOR_PANEL.get(), facing));
            case Level4Plan.DECO_CCTV -> out.set(floor + 4, faced(ModBlocks.CCTV_CAMERA.get(), facing));
            case Level4Plan.DECO_DIRECTORY -> out.set(floor + 2, faced(ModBlocks.OFFICE_PLAQUE.get(), facing));
            case Level4Plan.DECO_EXIT -> {
                // Just over a door (2 tall), or one higher over a 3-tall doorway.
                int y = (v & Level4Plan.EXIT_HIGH) != 0 ? floor + 4 : floor + 3;
                out.set(y, faced(ModBlocks.OFFICE_EXIT_SIGN.get(), facing).setValue(OfficeExitSignBlock.ARROW, arrow(v)));
            }
            case Level4Plan.DECO_HANGING_EXIT -> out.set(floor + 4, faced(ModBlocks.OFFICE_EXIT_SIGN.get(), facing)
                    .setValue(OfficeExitSignBlock.ARROW, arrow(v))
                    .setValue(OfficeExitSignBlock.HANGING, true));
            case Level4Plan.DECO_PENDANT -> out.set(floor + 4, ModBlocks.OFFICE_PENDANT_LIGHT.get().defaultBlockState()
                    .setValue(OfficeLampBlock.MODE, v == Level4Plan.LAMP_LIT ? LampMode.LIT
                            : v == Level4Plan.LAMP_FLICKER ? LampMode.FLICKER : LampMode.DEAD));
            case Level4Plan.DECO_EXTINGUISHER -> out.set(floor + 1, faced(ModBlocks.OFFICE_EXTINGUISHER.get(), facing));
            case Level4Plan.DECO_SWITCH -> out.set(floor + 1, plate(OfficeWallPlateBlock.Kind.SWITCH, facing));
            case Level4Plan.DECO_SOCKET -> out.set(floor + 1, plate(OfficeWallPlateBlock.Kind.SOCKET, facing));
            case Level4Plan.DECO_THERMOSTAT -> out.set(floor + 2, plate(OfficeWallPlateBlock.Kind.THERMOSTAT, facing));
            case Level4Plan.DECO_CALL_POINT -> out.set(floor + 2, plate(OfficeWallPlateBlock.Kind.CALL_POINT, facing));
            case Level4Plan.DECO_NOTICE -> out.set(floor + 2, faced(ModBlocks.OFFICE_NOTICE_BOARD.get(), facing));
            case Level4Plan.DECO_CLOCK -> out.set(floor + 3, faced(ModBlocks.OFFICE_CLOCK.get(), facing));
            case Level4Plan.DECO_MARK -> out.set(floor + 2, faced(ModBlocks.OFFICE_WALL_MARK.get(), facing)
                    .setValue(OfficeWallMarkBlock.KIND, switch (v) {
                        case Level4Plan.MARK_BOARD_LEFT -> OfficeWallMarkBlock.Kind.BOARD_LEFT;
                        case Level4Plan.MARK_BOARD_RIGHT -> OfficeWallMarkBlock.Kind.BOARD_RIGHT;
                        case Level4Plan.MARK_HOLES -> OfficeWallMarkBlock.Kind.HOLES;
                        default -> OfficeWallMarkBlock.Kind.PICTURE;
                    }));
            case Level4Plan.DECO_DADO -> out.set(floor + 1, wallFixture(v == 1 ? OfficeWallFixtureBlock.Kind.DADO_OUTLETS
                    : OfficeWallFixtureBlock.Kind.DADO, facing));
            case Level4Plan.DECO_DOOR_STOP -> out.set(floor + 1, wallFixture(OfficeWallFixtureBlock.Kind.DOOR_STOP, facing));
            case Level4Plan.DECO_TV_BRACKET -> out.set(floor + 3, wallFixture(OfficeWallFixtureBlock.Kind.TV_BRACKET, facing));
            case Level4Plan.DECO_COAT_HOOKS -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.COAT_HOOKS, facing));
            case Level4Plan.DECO_SANITISER -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.SANITISER, facing));
            case Level4Plan.DECO_FIRST_AID -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.FIRST_AID, facing));
            case Level4Plan.DECO_EVAC_PLAN -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.EVAC_PLAN, facing));
            case Level4Plan.DECO_NO_SMOKING -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.NO_SMOKING, facing));
            case Level4Plan.DECO_FIRE_DOOR_SIGN -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.FIRE_DOOR_SIGN, facing));
            case Level4Plan.DECO_SIGN_MEETING -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.SIGN_MEETING, facing));
            case Level4Plan.DECO_SIGN_OFFICE -> out.set(floor + 2, wallFixture(OfficeWallFixtureBlock.Kind.SIGN_OFFICE, facing));
            default -> {
            }
        }
    }

    /** An exit sign's arrow for a plan arrow (Level4Plan.ARROW_*, bits 0-1). */
    private static OfficeExitSignBlock.Arrow arrow(int variant) {
        return switch (variant & 3) {
            case Level4Plan.ARROW_LEFT -> OfficeExitSignBlock.Arrow.LEFT;
            case Level4Plan.ARROW_RIGHT -> OfficeExitSignBlock.Arrow.RIGHT;
            default -> OfficeExitSignBlock.Arrow.NONE;
        };
    }

    /** A small fixture on the wall: trunking, a door stop, a bracket, a box or a sign. */
    static BlockState wallFixture(OfficeWallFixtureBlock.Kind kind, Direction facing) {
        return faced(ModBlocks.OFFICE_WALL_FIXTURE.get(), facing).setValue(OfficeWallFixtureBlock.KIND, kind);
    }

    /** A small plate on the wall: light switch, thermostat, fire-alarm call point or socket. */
    static BlockState plate(OfficeWallPlateBlock.Kind kind, Direction facing) {
        return faced(ModBlocks.OFFICE_WALL_PLATE.get(), facing).setValue(OfficeWallPlateBlock.KIND, kind);
    }

    /** A cubicle partition joined to the sides set in {@code bits} (bit = 2D data value). */
    static BlockState partition(int bits) {
        return ModBlocks.CUBICLE_PARTITION.get().defaultBlockState()
                .setValue(CubiclePartitionBlock.SOUTH, (bits & 1) != 0)
                .setValue(CubiclePartitionBlock.WEST, (bits & 2) != 0)
                .setValue(CubiclePartitionBlock.NORTH, (bits & 4) != 0)
                .setValue(CubiclePartitionBlock.EAST, (bits & 8) != 0);
    }

    /**
     * The open, bright floor around an exit stairwell (outside its 11x11 tower). On the tower wall
     * on both sides of its fire door: a "fire door, keep shut" sign and a no-smoking sign right
     * beside it, an extinguisher and a fire-alarm call point two blocks out, and the "you are here"
     * evacuation plan three blocks out.
     */
    private void fillClearing(long seed, int exitX, int exitZ, int dx, int dz, Column out) {
        out.set(floorY(), ModBlocks.OFFICE_CARPET.get().defaultBlockState());
        Direction door = stairwellDoorSide(seed, exitX, exitZ);
        Direction side = door.getClockWise();
        int outward = dx * door.getStepX() + dz * door.getStepZ();
        int lateral = dx * side.getStepX() + dz * side.getStepZ();
        if (outward == STAIRWELL_RADIUS + 1) {
            switch (lateral) {
                case -3 -> out.set(floorY() + 2, wallFixture(OfficeWallFixtureBlock.Kind.EVAC_PLAN, door));
                case -2 -> out.set(floorY() + 1, faced(ModBlocks.OFFICE_EXTINGUISHER.get(), door));
                case -1 -> out.set(floorY() + 2, wallFixture(OfficeWallFixtureBlock.Kind.FIRE_DOOR_SIGN, door));
                case 1 -> out.set(floorY() + 2, wallFixture(OfficeWallFixtureBlock.Kind.NO_SMOKING, door));
                case 2 -> out.set(floorY() + 2, plate(OfficeWallPlateBlock.Kind.CALL_POINT, door));
                default -> {
                }
            }
        }
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        // A ring of lit panels two blocks out from the stairwell walls, two pairs per side.
        BlockState ceiling = ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        if (ax == 7 && (az == 3 || az == 4)) {
            boolean towardsPlus = (az == 3) == (dz > 0); // the partner is one block further out on this side
            ceiling = panel((towardsPlus ? Direction.SOUTH : Direction.NORTH).get2DDataValue(), LampMode.LIT);
        } else if (az == 7 && (ax == 3 || ax == 4)) {
            boolean towardsPlus = (ax == 3) == (dx > 0);
            ceiling = panel((towardsPlus ? Direction.EAST : Direction.WEST).get2DDataValue(), LampMode.LIT);
        }
        out.set(ceilingY(), ceiling);
    }

    private void fillSafeRoom(SafeRoomPlan plan, int x, int z, Column out) {
        BlockState tile = ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        BlockState carpet = ModBlocks.OFFICE_CARPET.get().defaultBlockState();
        if (!plan.contains(x, z)) {
            // The strip of plain floor around the room.
            out.set(floorY(), carpet);
            out.set(ceilingY(), tile);
            return;
        }
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        plan.fill(x, z, floorY(), ceilingY(), new SafeRoomPlan.Style(
                ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                tile,
                ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState(),
                wall,
                ModBlocks.OFFICE_WALL_RAIL.get().defaultBlockState(), // chair rail inside, like a nicer office
                wall,
                null), out);
    }

    // ==========================================================================================
    // Small helpers
    // ==========================================================================================

    static BlockState faced(Block block, Direction facing) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    static void fill(int from, int to, BlockState state, Column out) {
        for (int y = from; y <= to; y++) {
            out.set(y, state);
        }
    }

    /** One or two of the same block on the floor. */
    private void stack(BlockState state, int height, Column out) {
        for (int i = 0; i < height; i++) {
            out.set(floorY() + 1 + i, state);
        }
    }
}
