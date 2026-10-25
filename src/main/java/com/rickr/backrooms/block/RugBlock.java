package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One ninth of a round 3x3 swirl rug, used as a floor block in Level 0. PART is the tile index
 * in reading order (row = north to south, column = west to east), so part 4 is the centre and
 * the texture for each part is {@code rug_<part>.png}.
 */
public class RugBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);

    public RugBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /** Tile index for an offset (-1..1, -1..1) from the rug's centre. */
    public static int part(int offsetX, int offsetZ) {
        return (offsetZ + 1) * 3 + (offsetX + 1);
    }

    /** In creative, the world position picks the tile, so laying a 3x3 square forms a whole rug. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(PART, Math.floorMod(pos.getZ(), 3) * 3 + Math.floorMod(pos.getX(), 3));
    }
}
