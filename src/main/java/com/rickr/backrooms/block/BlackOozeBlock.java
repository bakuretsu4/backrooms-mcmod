package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Level 2's thick black liquid, pooled on the tunnel floor where pipes burst during the Blackout
 * (https://backrooms-wiki.wikidot.com/level-2, "The Blackout": pipes backed up and burst, leaving
 * "a thick, pungent smell similar to that of rotting meat").
 *
 * <p>A thin layer like the leaf litter, but sticky: the registry gives it a honey-like speed and
 * jump factor, so wading through slows you down. More of it keeps dripping from the pipes overhead.
 * The stench (brief nausea) is handled by the brownout's server behaviour, which knows you're
 * standing in it. It is black and dull: it never glows.
 */
public class BlackOozeBlock extends ThinLayerBlock {
    public BlackOozeBlock(Properties properties) {
        super(properties);
    }

    /** Client side, now and then: a black drip falls from the pipes above, sometimes with a wet plop. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            // Falling dust takes the block's map colour (near-black), so it reads as a dark drip.
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
                    pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 3.2, pos.getZ() + 0.25 + random.nextDouble() * 0.5,
                    0.0, 0.0, 0.0);
        }
        if (random.nextInt(30) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, ModSounds.L2_OOZE_DRIP.get(),
                    SoundSource.BLOCKS, 0.35f, 0.8f + random.nextFloat() * 0.3f, false);
        }
    }
}
