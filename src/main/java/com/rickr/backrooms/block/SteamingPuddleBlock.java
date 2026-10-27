package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import com.rickr.backrooms.registry.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wet concrete under a puddle that gives off steam (fandom: Level 1's puddles evaporate into fog
 * in the heat, but never dry up): soft wisps rising slowly from the floor and spreading into a low,
 * drifting layer (client/particle/SteamParticle).
 */
public class SteamingPuddleBlock extends Block {
    public SteamingPuddleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ModParticles.STEAM.get(), pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.006, 0.006 + random.nextDouble() * 0.006,
                    (random.nextDouble() - 0.5) * 0.006);
        }
    }
}
