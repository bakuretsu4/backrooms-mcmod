package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The big pump set in a Level 2 pump bay or boiler room: a 2 x 2 x 2 block machine (motor, pump
 * casing, discharge pipe rising to the ceiling, gauge and valve) made of eight parts. Each part is
 * one block with the piece of the machine that falls in it; all eight share the same FACING (the
 * side the pump's front, with its gauge and valve wheel, looks at).
 *
 * <p>PART = lx + 2 * lz + 4 * ly in the machine's own frame (front to the north): lx 0..1 west to
 * east, lz 0 = front row, 1 = back row, ly 0 = bottom, 1 = top. The world generator should not
 * have to think about that: {@link #partAt} gives the right state for each of the eight blocks.
 */
public class Level2PumpBlock extends FurnitureBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);

    public Level2PumpBlock(Properties properties) {
        super(properties, Shapes.block());
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /**
     * The state of the block at offset (wx, wy, wz), each 0 or 1, from the machine's lowest corner
     * (smallest x, y and z) when its front looks towards FACING.
     */
    public BlockState partAt(Direction facing, int wx, int wy, int wz) {
        // Undo the blockstate's quarter turns: which of the machine's own cells lands at (wx, wz).
        int lx;
        int lz;
        switch (facing) {
            case EAST -> {
                lx = wz;
                lz = 1 - wx;
            }
            case SOUTH -> {
                lx = 1 - wx;
                lz = 1 - wz;
            }
            case WEST -> {
                lx = 1 - wz;
                lz = wx;
            }
            default -> {
                lx = wx;
                lz = wz;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, lx + 2 * lz + 4 * wy);
    }
}
