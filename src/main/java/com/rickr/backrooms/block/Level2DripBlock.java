package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A leak in the Level 2 ceiling: a wet rust stain with a bead of water hanging from it. It sits
 * in the AIR block right under the ceiling (or under a pipe) and you walk through it. Now and then
 * a drop forms and falls (vanilla dripping-water particles, which splash when they land). The
 * drip sound is played by the client's ambience code, which looks for these blocks nearby.
 */
public class Level2DripBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5, 12, 5, 11, 16, 11);
    /** animateTick only visits each nearby block now and then at random; half of those visits drip. */
    private static final int DROP_ONE_IN = 2;

    public Level2DripBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Client only: a drop gathers under the bead and falls. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(DROP_ONE_IN) != 0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.15;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.15;
        level.addParticle(ParticleTypes.DRIPPING_WATER, x, pos.getY() + 0.72, z, 0, 0, 0);
    }
}
