package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rusty pipe running just under Level 1's ceiling (AXIS: the way it runs). It drips now and
 * then: the damp under the pipes that the wikis warn isn't safe to drink.
 */
public class CeilingPipeBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape ALONG_X = Block.box(0, 10, 5, 16, 15, 11);
    private static final VoxelShape ALONG_Z = Block.box(5, 10, 0, 11, 15, 16);

    public CeilingPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            double along = random.nextDouble();
            boolean x = state.getValue(AXIS) == Direction.Axis.X;
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + (x ? along : 0.5), pos.getY() + 0.6,
                    pos.getZ() + (x ? 0.5 : along), 0.0, 0.0, 0.0);
        }
    }
}
