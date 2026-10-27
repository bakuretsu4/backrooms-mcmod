package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One block-wide section of Level 39's unpainted picket fence: grey weathered pickets with pointed
 * tops on two rails, running along AXIS. Lot edges are straight lines, so a section never needs to
 * join round a corner the way a vanilla fence does (corners get a post block instead).
 *
 * <p>STYLE picks how badly the years treated it (the photo's fence is gappy and uneven):
 * <ul>
 *   <li>0: whole, the pickets a little uneven;</li>
 *   <li>1: two pickets missing;</li>
 *   <li>2: the whole section leaning over;</li>
 *   <li>3: broken: most pickets gone, one hanging from the top rail.</li>
 * </ul>
 * Like a vanilla fence it is taller to bump into than it looks, so nobody hops a yard fence.
 */
public class PicketFenceBlock extends AxisShapeBlock {
    public static final int STYLES = 4;
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, STYLES - 1);

    public PicketFenceBlock(Properties properties) {
        super(properties, Block.box(0, 0, 6.5, 16, 15, 9.5), Block.box(0, 0, 6.5, 16, 24, 9.5));
        registerDefaultState(defaultBlockState().setValue(STYLE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STYLE);
    }
}
