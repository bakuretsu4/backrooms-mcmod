package com.rickr.backrooms.block.yard;

import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lawn sprinkler's block entity. It stores nothing; its client tick makes the water and the
 * sound, all from the game clock and the block's position, so every player sees the same.
 *
 * <ul>
 *   <li><b>Timer:</b> it runs for {@link #RUN_TICKS} (75 s) out of every {@link #CYCLE_TICKS}
 *       (2 min), each sprinkler on its own schedule. It is always night on Level 9, so the night
 *       timer is always on.</li>
 *   <li><b>Sweep:</b> one {@link #SWEEP_TICKS} (6 s) sweep: 18 little jerks across a 150 degree
 *       arc centred on FACING, a quarter second apart (the arm snapping into the jet), then a
 *       1.5 s swing back.</li>
 *   <li><b>Water:</b> while you are within {@link #SPRAY_RANGE} blocks: drops falling along the
 *       jet out to about 4 blocks, and a splash at the nozzle.</li>
 *   <li><b>Sound:</b> at the start of every sweep, if you are within {@link #SOUND_RANGE} blocks,
 *       one 6 s tick-tick-tick-hiss (it matches the sweep).</li>
 * </ul>
 * Common code (the server loads it too), so it only uses the level's own particle and sound methods.
 */
public class SprinklerBlockEntity extends BlockEntity {
    static final int CYCLE_TICKS = 2400;
    static final int RUN_TICKS = 1500;
    static final int SWEEP_TICKS = 120;
    /** The forward part of the sweep: 18 steps of 5 ticks. */
    private static final int FORWARD_TICKS = 90;
    private static final int STEP_TICKS = 5;
    private static final float HALF_ARC = 75.0f * Mth.DEG_TO_RAD;
    static final double SPRAY_RANGE = 32.0;
    static final double SOUND_RANGE = 20.0;
    /** How far the jet reaches, in blocks. */
    private static final double REACH = 4.0;

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.L9_SPRINKLER.get(), pos, state);
    }

    /** Runs on the client only (see {@link SprinklerBlock#getTicker}). */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SprinklerBlockEntity sprinkler) {
        long t = level.getGameTime() + ((Hash.mix(pos.asLong()) >>> 20) & 0xFFFF);
        if (Math.floorMod(t, CYCLE_TICKS) >= RUN_TICKS) {
            return; // its timer has it off for now
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.35;
        double z = pos.getZ() + 0.5;
        if (level.getNearestPlayer(x, y, z, SPRAY_RANGE, false) == null) {
            return;
        }
        int phase = (int) Math.floorMod(t, SWEEP_TICKS);
        float along = phase < FORWARD_TICKS
                ? (phase / STEP_TICKS) / (float) (FORWARD_TICKS / STEP_TICKS - 1)
                : 1.0f - (phase - FORWARD_TICKS) / (float) (SWEEP_TICKS - FORWARD_TICKS);
        Direction facing = state.getValue(SprinklerBlock.FACING);
        float yaw = (float) Mth.atan2(facing.getStepZ(), facing.getStepX()) + HALF_ARC * (2.0f * Mth.clamp(along, 0.0f, 1.0f) - 1.0f);
        double dx = Mth.cos(yaw);
        double dz = Mth.sin(yaw);
        RandomSource random = level.random;

        // Drops raining down along the jet's arc (fewer while it swings back).
        int drops = phase < FORWARD_TICKS ? 2 : 1;
        for (int i = 0; i < drops; i++) {
            double r = 0.6 + random.nextDouble() * (REACH - 0.6);
            double h = 0.25 + 0.55 * Mth.sin((float) (Math.PI * r / (REACH + 0.4)));
            level.addParticle(ParticleTypes.FALLING_WATER,
                    x + dx * r + (random.nextDouble() - 0.5) * 0.3, y + h, z + dz * r + (random.nextDouble() - 0.5) * 0.3,
                    0.0, 0.0, 0.0);
        }
        // The jet leaving the nozzle.
        if (phase < FORWARD_TICKS) {
            level.addParticle(ParticleTypes.SPLASH, x + dx * 0.25, y + 0.05, z + dz * 0.25, dx * 0.3, 0.0, dz * 0.3);
        }
        if (phase == 0 && level.getNearestPlayer(x, y, z, SOUND_RANGE, false) != null) {
            level.playLocalSound(x, y, z, ModSounds.L9_SPRINKLER.get(), SoundSource.AMBIENT,
                    0.45f, 0.95f + random.nextFloat() * 0.1f, false);
        }
    }
}
