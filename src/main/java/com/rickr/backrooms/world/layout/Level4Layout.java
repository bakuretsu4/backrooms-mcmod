package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CubiclePartitionBlock;
import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.OfficePanelBlock;
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
 * Level 4, "The Abandoned Office": endless, empty office floors on a stormy night. Grey-taupe or
 * blue-grey carpet, off-white drywall, a suspended ceiling with 1x2 fluorescent panels (most of
 * them dead in the big open areas), long walls of dark rainy windows with radiators under the
 * sills, cubicle fields, corridors of wooden doors, break rooms with vending machines and water
 * coolers, glass conference rooms and elevator lobbies whose elevators never come. No monsters:
 * it's a safe haven, if a lonely one.
 *
 * <p>The floor plan itself (zones, rooms, furniture) lives in {@link Level4Plan}, which describes
 * every column with plain numbers; this class turns those numbers into blocks and adds the parts
 * that use the shared building code:
 * <ul>
 *   <li><b>Exit:</b> one emergency stairwell per 288 x 288 region (the shared stairwell tower of
 *       Levels 0/1, here with office walls and a steel fire door under a glowing EXIT sign). The
 *       open, brightly lit floor around it makes the sign easy to spot. Going down the stairs
 *       (below y30) takes you on to the Poolrooms.</li>
 *   <li><b>Safe rooms:</b> a small office box standing in the middle of an open-plan floor
 *       ({@link SafeRoomPlan}), with the checkpoint radio, a cot and a crate.</li>
 *   <li><b>Anomalies:</b> looping stairwells, the Window, vanishing elevators and the way into
 *       Level 4.3 "The Cubicles" ({@link Level4AnomalyPlan}, blocks in {@link Level4AnomalyBlocks}).</li>
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
        int floor = floorY();
        int ceiling = ceilingY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        BlockState wallBase = ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState();
        if (stairwell(seed, dx, dz, x - dx, z - dz, wall, wallBase, ModBlocks.FIRE_DOOR.get(), false, out)) {
            return;
        }
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= Level4Plan.CLEARING_RADIUS) {
            fillClearing(dx, dz, out);
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

        long c = Level4Plan.column(seed, x, z);
        out.set(floor, floorBlock(Level4Plan.floor(c)));
        out.set(ceiling, ceilingBlock(Level4Plan.ceiling(c), Level4Plan.ceilingFacing(c)));
        Direction facing = Direction.from2DDataValue(Level4Plan.facing(c));
        int variant = Level4Plan.variant(c);
        switch (Level4Plan.kind(c)) {
            case Level4Plan.FLOOR -> placeDeco(c, out);
            case Level4Plan.WALL -> fillWall(Level4Plan.style(c), out);
            case Level4Plan.FILL -> fill(floor + 1, ceiling - 1, wall, out);
            case Level4Plan.COLUMN -> {
                out.set(floor + 1, ModBlocks.OFFICE_COLUMN_BASE.get().defaultBlockState());
                fill(floor + 2, ceiling - 1, ModBlocks.OFFICE_COLUMN.get().defaultBlockState(), out);
            }
            case Level4Plan.DOORWAY -> out.set(ceiling - 1, wall); // 3 tall, lintel above
            case Level4Plan.DOOR -> fillDoor(facing, variant, out);
            case Level4Plan.WINDOW -> {
                BlockState pane = ModBlocks.OFFICE_WINDOW.get().defaultBlockState().setValue(OfficeWindowBlock.FACING, facing);
                out.set(floor + 1, wallBase); // the sill wall
                out.set(floor + 2, pane.setValue(OfficeWindowBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 3, pane.setValue(OfficeWindowBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 4, wall);
            }
            case Level4Plan.RADIATOR -> out.set(floor + 1, faced(ModBlocks.RADIATOR.get(), facing));
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
            case Level4Plan.COPIER -> out.set(floor + 1, faced(ModBlocks.COPIER.get(), facing));
            case Level4Plan.PLANT -> out.set(floor + 1, faced(ModBlocks.DEAD_PLANT.get(), facing));
            case Level4Plan.BOXES -> stack(faced(ModBlocks.CARDBOARD_BOXES.get(), facing), variant == 1 ? 2 : 1, out);
            case Level4Plan.LADDER -> out.set(floor + 1, faced(ModBlocks.STEP_LADDER.get(), facing));
            case Level4Plan.CRATE -> stack(faced(ModBlocks.SUPPLY_CRATE.get(), facing), variant == 1 ? 2 : 1, out);
            case Level4Plan.LOCKER -> out.set(floor + 1, faced(ModBlocks.SUPPLY_LOCKER.get(), facing));
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
            case Level4Plan.COUNTER -> {
                out.set(floor + 1, faced(ModBlocks.KITCHEN_COUNTER.get(), facing));
                if (variant == 1) {
                    out.set(floor + 2, faced(ModBlocks.MICROWAVE.get(), facing));
                }
            }
            case Level4Plan.FRIDGE -> out.set(floor + 1, faced(ModBlocks.FRIDGE.get(), facing));
            case Level4Plan.TABLE -> out.set(floor + 1, faced(ModBlocks.DINING_TABLE.get(), facing));
            case Level4Plan.CONF_TABLE -> out.set(floor + 1, faced(ModBlocks.CONFERENCE_TABLE.get(), facing));
            case Level4Plan.SOFA -> out.set(floor + 1, faced(ModBlocks.SOFA.get(), facing));
            case Level4Plan.SERVER_RACK -> stack(faced(ModBlocks.SERVER_RACK.get(), facing), 2, out);
            case Level4Plan.CURTAIN -> fill(floor + 1, ceiling - 1, ModBlocks.STAGE_CURTAIN.get().defaultBlockState(), out);
            case Level4Plan.PARTITION -> out.set(floor + 1, partition(variant));
            default -> {
            }
        }
    }

    static BlockState floorBlock(int floor) {
        boolean indent = (floor & Level4Plan.INDENT) != 0;
        return switch (floor & 3) {
            case Level4Plan.CARPET_BLUE -> (indent ? ModBlocks.OFFICE_CARPET_BLUE_INDENT : ModBlocks.OFFICE_CARPET_BLUE).get().defaultBlockState();
            case Level4Plan.CONCRETE -> ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();
            default -> (indent ? ModBlocks.OFFICE_CARPET_INDENT : ModBlocks.OFFICE_CARPET).get().defaultBlockState();
        };
    }

    static BlockState ceilingBlock(int ceiling, int facing) {
        return switch (ceiling) {
            case Level4Plan.TILE_MISSING -> ModBlocks.OFFICE_CEILING_MISSING.get().defaultBlockState();
            case Level4Plan.HATCH -> ModBlocks.OFFICE_CEILING_HATCH.get().defaultBlockState();
            case Level4Plan.PANEL_LIT -> panel(facing, LampMode.LIT);
            case Level4Plan.PANEL_FLICKER -> panel(facing, LampMode.FLICKER);
            case Level4Plan.PANEL_DEAD -> panel(facing, LampMode.DEAD);
            default -> ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        };
    }

    static BlockState panel(int facing, LampMode mode) {
        return ModBlocks.OFFICE_PANEL.get().defaultBlockState()
                .setValue(OfficePanelBlock.FACING, Direction.from2DDataValue(facing))
                .setValue(OfficePanelBlock.MODE, mode);
    }

    /** A drywall wall from floor to ceiling; its style decides the bottom (or glass). */
    private void fillWall(int style, Column out) {
        int floor = floorY();
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        switch (style) {
            case Level4Plan.STYLE_RAIL -> out.set(floor + 1, ModBlocks.OFFICE_WALL_RAIL.get().defaultBlockState());
            case Level4Plan.STYLE_GLASS -> fill(floor + 1, floor + 3, ModBlocks.OFFICE_GLASS.get().defaultBlockState(), out);
            case Level4Plan.STYLE_BLINDS -> {
                out.set(floor + 1, ModBlocks.OFFICE_GLASS.get().defaultBlockState());
                fill(floor + 2, floor + 3, ModBlocks.OFFICE_GLASS_BLINDS.get().defaultBlockState(), out);
            }
            default -> out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
        }
        // Glass walls stop under a strip of drywall (the bulkhead the glass hangs from).
        int from = style == Level4Plan.STYLE_GLASS || style == Level4Plan.STYLE_BLINDS ? floor + 4 : floor + 2;
        fill(from, ceilingY() - 1, wall, out);
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
        fill(floor + 3, ceilingY() - 1, ModBlocks.OFFICE_WALL.get().defaultBlockState(), out);
    }

    /** Things hung in the air in front of a wall: whiteboards, call buttons, cameras, plaques. */
    private void placeDeco(long c, Column out) {
        int deco = Level4Plan.deco(c);
        if (deco == Level4Plan.DECO_NONE) {
            return;
        }
        Direction facing = Direction.from2DDataValue(Level4Plan.decoFacing(c));
        int floor = floorY();
        switch (deco) {
            case Level4Plan.DECO_WHITEBOARD -> out.set(floor + 2, faced(ModBlocks.WHITEBOARD.get(), facing));
            case Level4Plan.DECO_ELEVATOR_PANEL -> out.set(floor + 2, faced(ModBlocks.ELEVATOR_PANEL.get(), facing));
            case Level4Plan.DECO_CCTV -> out.set(floor + 4, faced(ModBlocks.CCTV_CAMERA.get(), facing));
            case Level4Plan.DECO_PLAQUE -> out.set(floor + 2, faced(ModBlocks.OFFICE_PLAQUE.get(), facing));
            default -> {
            }
        }
    }

    /** A cubicle partition joined to the sides set in {@code bits} (bit = 2D data value). */
    static BlockState partition(int bits) {
        return ModBlocks.CUBICLE_PARTITION.get().defaultBlockState()
                .setValue(CubiclePartitionBlock.SOUTH, (bits & 1) != 0)
                .setValue(CubiclePartitionBlock.WEST, (bits & 2) != 0)
                .setValue(CubiclePartitionBlock.NORTH, (bits & 4) != 0)
                .setValue(CubiclePartitionBlock.EAST, (bits & 8) != 0);
    }

    /** The open, bright floor around an exit stairwell (outside its 11x11 tower). */
    private void fillClearing(int dx, int dz, Column out) {
        out.set(floorY(), ModBlocks.OFFICE_CARPET.get().defaultBlockState());
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
