package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.registry.Level227FloraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The foliage of the Level 227 forest: the big-leaf canopy of the ordinary (but huge) trees and
 * the fern-like masses hanging from the giant branches. Solid to stand on, see-through in the gaps.
 * Its texture is animated, a slow sway of a pixel or two (the leaves stir in the wind).
 *
 * <p>Now and then, near the player, a gust hisses through the leaves and a leaf comes loose and
 * spins down. Both happen on the client only (this is a display tick), and the sound has a shared
 * cool-down so a whole canopy of leaf blocks still only makes one gust at a time.
 */
public class Level227LeavesBlock extends Block {
    /** Game time of the last gust sound, shared by all leaf blocks (client side only). */
    private static long lastGust = Long.MIN_VALUE;
    /** At least this many ticks between two gusts. */
    private static final int GUST_GAP = 70;

    public Level227LeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A loose leaf drifts down from leaves with open air below.
        if (random.nextInt(40) == 0 && level.getBlockState(pos.below()).isAir()) {
            level.addParticle(Level227Flora.FALLING_LEAF.get(), pos.getX() + random.nextDouble(), pos.getY() - 0.05,
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
        // Hundreds of leaf blocks get these calls, so the chance per call is tiny.
        if (random.nextInt(260) == 0) {
            long now = level.getGameTime();
            if (now - lastGust > GUST_GAP) {
                lastGust = now;
                level.playLocalSound(pos, Level227FloraSounds.LEAF_WIND.get(), SoundSource.AMBIENT,
                        0.55f, 0.85f + random.nextFloat() * 0.3f, false);
            }
        }
    }
}
