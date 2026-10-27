package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wet concrete under a puddle that gives off steam (fandom: Level 1's puddles evaporate into fog
 * in the heat, but never dry up): thin wisps rising slowly from the floor.
 */
public class SteamingPuddleBlock extends Block {
    public SteamingPuddleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.02,
                    pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.01, 0.012 + random.nextDouble() * 0.01,
                    (random.nextDouble() - 0.5) * 0.01);
        }
    }
}
