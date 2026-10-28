package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The cut face of a broken giant branch or stump, showing its growth rings. AXIS is the branch's
 * direction: the two faces across it show the rings, the other four show bare heartwood.
 *
 * <p>One block is a tiny piece of a cut that is hundreds of blocks across, so its rings are nearly
 * straight bands. RINGS says which way the bands run, so the world generator can make them follow
 * circles round the branch's heart:
 * <ul>
 *   <li>0: bands run sideways (the heart is straight above or below this block; for AXIS = Y, the
 *       heart is to the north or south)</li>
 *   <li>1: bands run up and down (the heart is to the side; for AXIS = Y, to the east or west)</li>
 *   <li>2: bands run diagonally like "/" (the heart is up-left or down-right, seen from the face's
 *       positive side; for AXIS = Y, north-west or south-east)</li>
 *   <li>3: bands run diagonally like "\" (the other two diagonals)</li>
 * </ul>
 * The band spacing divides the block, so neighbouring blocks with the same RINGS join seamlessly.
 */
public class Level227RingsBlock extends RotatedPillarBlock {
    public static final IntegerProperty RINGS = IntegerProperty.create("rings", 0, 3);

    public Level227RingsBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RINGS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RINGS);
    }
}
