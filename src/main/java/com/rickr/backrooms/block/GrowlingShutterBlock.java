package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A closed storage-locker shutter that looks like all the others, except something is in there:
 * now and then a low growl or a chatter of teeth comes through it (fandom: "listen for faint
 * growling or chattering of teeth"). It never opens.
 */
public class GrowlingShutterBlock extends Block {
    public GrowlingShutterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(160) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.L1_LOCKER_GROWL.get(),
                    SoundSource.HOSTILE, 0.5f + random.nextFloat() * 0.3f, 0.85f + random.nextFloat() * 0.3f, false);
        }
    }
}
