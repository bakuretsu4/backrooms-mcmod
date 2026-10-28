package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.registry.Level227FloraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * GammaPali: a small plant that glows on the bark of the giant branches. Its glow is a toxic gas
 * (canon: it kills quickly if inhaled). The gas itself is world/Level227Hazards: standing within
 * about 2.5 blocks poisons you and then suffocates you within seconds.
 *
 * <p>So it reads clearly and can be avoided, the plant always shows its danger zone: a faint,
 * glowing yellow-green haze drifts round it (the spore haze particle) and it hisses softly. This
 * block only does that showing; it runs on the client, for the blocks near the player.
 */
public class Level227GammaPaliBlock extends Level227AttachedPlantBlock {
    /** How far from the plant the haze particles start; matches the poisonous radius. */
    private static final double HAZE_RADIUS = 2.0;

    public Level227GammaPaliBlock(Properties properties) {
        super(properties, 5, 7);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // The game calls this for random blocks round the player, roughly every two seconds for any
        // one block, so each call puts out a few long-lived puffs: together they keep a steady haze.
        Direction facing = state.getValue(FACING);
        double cx = pos.getX() + 0.5 + facing.getStepX() * 0.6;
        double cy = pos.getY() + 0.5 + facing.getStepY() * 0.6;
        double cz = pos.getZ() + 0.5 + facing.getStepZ() * 0.6;
        for (int i = 0; i < 4; i++) {
            // A point in a ball round the plant, denser near it (the square root pulls points in less).
            double r = HAZE_RADIUS * Math.sqrt(random.nextDouble());
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitch = Math.asin(random.nextDouble() * 2 - 1);
            double x = cx + Math.cos(yaw) * Math.cos(pitch) * r;
            double y = cy + Math.sin(pitch) * r * 0.7;
            double z = cz + Math.sin(yaw) * Math.cos(pitch) * r;
            level.addParticle(Level227Flora.SPORE_HAZE.get(), x, y, z,
                    (random.nextDouble() - 0.5) * 0.004, 0.002 + random.nextDouble() * 0.003, (random.nextDouble() - 0.5) * 0.004);
        }
        if (random.nextInt(22) == 0) {
            level.playLocalSound(pos, Level227FloraSounds.GAMMAPALI_HISS.get(), SoundSource.BLOCKS,
                    0.3f, 0.8f + random.nextFloat() * 0.35f, false);
        }
    }
}
