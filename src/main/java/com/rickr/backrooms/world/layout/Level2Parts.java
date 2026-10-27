package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.DuctBlock;
import com.rickr.backrooms.block.Level2PipeElbowBlock;
import com.rickr.backrooms.block.Level2BulkheadFrameBlock;
import com.rickr.backrooms.block.Level2PipeBlock;
import com.rickr.backrooms.block.Level2StencilBlock;
import com.rickr.backrooms.block.Level2TallBlock;
import com.rickr.backrooms.block.Level2ValveBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Short names for the block states Level 2 is built from, so the layout code reads like a
 * description ("a rack of three steel pipes against the north wall") instead of a pile of
 * setValue calls. Every helper only picks an existing block state (Minecraft keeps them all in a
 * table), so none of this allocates anything.
 */
final class Level2Parts {
    private Level2Parts() {}

    /**
     * A length of pipe (or, with {@code flange}, the joint piece between two lengths) running
     * along {@code axis}, its pipes against the {@code against} face of the block (UP: hung under
     * the ceiling, DOWN: lying on the floor).
     */
    static BlockState pipe(Direction.Axis axis, Direction against, Level2PipeBlock.Kind kind, Level2PipeBlock.Size size,
                           boolean flange) {
        return (flange ? Level2Blocks.FLANGE : Level2Blocks.PIPE).get().defaultBlockState()
                .setValue(Level2PipeBlock.AXIS, axis)
                .setValue(Level2PipeBlock.SLOT, Level2PipeBlock.Slot.against(against))
                .setValue(Level2PipeBlock.KIND, kind)
                .setValue(Level2PipeBlock.SIZE, size);
    }

    /** One pipe through the middle of the block (where elbows and tees join). */
    static BlockState centrePipe(Direction.Axis axis, Level2PipeBlock.Kind kind, Level2PipeBlock.Size size) {
        return Level2Blocks.PIPE.get().defaultBlockState()
                .setValue(Level2PipeBlock.AXIS, axis)
                .setValue(Level2PipeBlock.SLOT, Level2PipeBlock.Slot.CENTER)
                .setValue(Level2PipeBlock.KIND, kind)
                .setValue(Level2PipeBlock.SIZE, size);
    }

    /** The big off-white insulated main along the bottom of a wall (a bolted flange every few blocks). */
    static BlockState duct(Direction facing, boolean flange) {
        return ModBlocks.PALE_DUCT.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(DuctBlock.FLANGE, flange);
    }

    /** A bend: its horizontal leg leaves through {@code facing}, its vertical leg up ({@code up}) or down. */
    static BlockState elbow(Direction facing, boolean up, Level2PipeBlock.Kind kind, Level2PipeBlock.Size size) {
        return Level2Blocks.PIPE_ELBOW.get().defaultBlockState()
                .setValue(Level2PipeElbowBlock.FACING, facing)
                .setValue(Level2PipeElbowBlock.VERTICAL, up ? Direction.UP : Direction.DOWN)
                .setValue(Level2PipeElbowBlock.KIND, kind)
                .setValue(Level2PipeElbowBlock.SIZE, size);
    }

    /** The old rusty rack of thin pipes on brackets (FACING away from its wall). */
    static BlockState rack(Direction facing, boolean bracket) {
        return ModBlocks.RUSTY_PIPE.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(PipeRackBlock.BRACKET, bracket);
    }

    static BlockState valve(Direction facing, boolean red) {
        return Level2Blocks.VALVE_WHEEL.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(Level2ValveBlock.COLOR, red ? Level2ValveBlock.WheelColor.RED : Level2ValveBlock.WheelColor.GREY);
    }

    static BlockState gauge(Direction facing) {
        return Level2Blocks.GAUGE.get().defaultBlockState().setValue(WallMountBlock.FACING, facing);
    }

    static BlockState stencil(Direction facing, Level2StencilBlock.Variant variant) {
        return Level2Blocks.STENCIL.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(Level2StencilBlock.VARIANT, variant);
    }

    static BlockState frame(Direction facing, Level2BulkheadFrameBlock.Part part) {
        return Level2Blocks.BULKHEAD_FRAME.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(Level2BulkheadFrameBlock.PART, part);
    }

    /** A caged bulkhead lamp, or (dead) the same lamp with its bulb gone. */
    static BlockState lamp(Direction facing, boolean dead) {
        return (dead ? ModBlocks.L2_DEAD_LAMP : ModBlocks.BULKHEAD_LAMP).get().defaultBlockState()
                .setValue(WallLampBlock.FACING, facing);
    }

    static BlockState emergencyLight(Direction facing) {
        return ModBlocks.L2_EMERGENCY_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, facing);
    }

    static BlockState steamVent(Direction facing) {
        return ModBlocks.L2_STEAM_VENT.get().defaultBlockState().setValue(WallMountBlock.FACING, facing);
    }

    /**
     * True for the cell of a 2x2x2 pump (wx, wz counted from its smallest-x, smallest-z corner)
     * whose top the discharge pipe leaves: its front-left cell, wherever the pump faces.
     */
    static boolean pumpOutlet(Direction facing, int wx, int wz) {
        int ox = facing == Direction.SOUTH || facing == Direction.EAST ? 1 : 0;
        int oz = facing == Direction.SOUTH || facing == Direction.WEST ? 1 : 0;
        return wx == ox && wz == oz;
    }

    static BlockState cableTray(Direction.Axis axis) {
        return Level2Blocks.CABLE_TRAY.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, axis);
    }

    static BlockState gutter(Direction.Axis axis) {
        return Level2Blocks.GUTTER.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, axis);
    }

    static BlockState grating(Direction.Axis axis) {
        return Level2Blocks.GRATING.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, axis);
    }

    /** Floor with the yellow lane line painted along its {@code facing} edge. */
    static BlockState floorLine(Direction facing) {
        return Level2Blocks.FLOOR_LINE.get().defaultBlockState().setValue(WallMountBlock.FACING, facing);
    }

    /** One half of a two-block-tall piece (shelf, breaker panel) facing {@code facing}. */
    static BlockState tall(BlockState base, Direction facing, boolean upper) {
        return base.setValue(WallMountBlock.FACING, facing)
                .setValue(Level2TallBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }
}
