package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A branch pipe dropping from a header under the ceiling down the wall to a valve: the "tee"
 * of a valve manifold. It stands in the block under the ceiling band and its model reaches up
 * into that band (block models may draw up to a block outside their own space), where a welded
 * saddle joins it to the wall-side pipe of a MEDIUM, HUNG {@link Level2PipeBlock} header. Below,
 * it runs into the top of a valve with FEED = true. FACING points away from the wall.
 *
 * <p>KIND matches the header it comes from (the paint colour code).
 */
public class Level2FeedPipeBlock extends WallMountBlock {
    public static final EnumProperty<Level2PipeBlock.Kind> KIND = Level2PipeBlock.KIND;

    public Level2FeedPipeBlock(Properties properties) {
        // Facing north: a 3 px pipe 4 px out from the wall (must match _feed_elements in level2_revamp.py).
        super(properties, 6.5, 0, 10.5, 9.5, 16, 13.5);
        registerDefaultState(defaultBlockState().setValue(KIND, Level2PipeBlock.Kind.STEEL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KIND);
    }
}
