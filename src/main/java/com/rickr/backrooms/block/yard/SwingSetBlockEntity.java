package com.rickr.backrooms.block.yard;

import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The middle of a swing set. It stores nothing; it gives the client a renderer for the two seats
 * and a client tick for the creak.
 *
 * <p>The sway is a pure function of the game clock and the block's position ({@link #seatAngle}),
 * so the renderer and the creak agree and every player sees the same swing. Seat 0 swings
 * about 7 degrees each way in 2.9 s, seat 1 about 4 degrees in 3.3 s, each set started at its
 * own moment. Nothing pushes them: there is no wind on Level 9.
 *
 * <p>The creak: whenever seat 0 turns at the end of a swing and a player is within
 * {@link #CREAK_RANGE} blocks, 1 time in {@link #CREAK_ONE_IN} it creaks (about every 30 s
 * while you are near). This class is common code, so it only uses the level's own sound and
 * clock methods (the same ones vanilla blocks use for their ambient sounds).
 */
public class SwingSetBlockEntity extends BlockEntity {
    /** Seat 0 / seat 1: how far they swing (radians) and how long one full swing takes (ticks). */
    private static final float[] AMPLITUDE = {0.12f, 0.07f};
    private static final float[] PERIOD_TICKS = {58.0f, 66.0f};
    static final float CREAK_RANGE = 24.0f;
    static final int CREAK_ONE_IN = 20;

    /** Which way seat 0 was moving last tick (+1 / -1), to notice the turn at the end of a swing. */
    private int lastDirection;
    /** The whole set (3 blocks long, 2 tall, legs leaning out): made once, for the renderer's culling. */
    @Nullable
    private AABB renderBounds;

    /** The box the renderer must stay visible for (the whole frame, not just the middle block). */
    public AABB renderBounds() {
        if (renderBounds == null) {
            renderBounds = new AABB(getBlockPos()).inflate(1.5, 0.0, 1.5).expandTowards(0.0, 1.5, 0.0);
        }
        return renderBounds;
    }

    public SwingSetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.L9_SWING_SET.get(), pos, state);
    }

    /**
     * How far seat 0 or 1 of the swing set at {@code pos} hangs out of plumb at {@code ticks}
     * (game time plus partial tick), in radians; positive swings toward the front (FACING).
     */
    public static float seatAngle(BlockPos pos, int seat, float ticks) {
        float phase = (float) ((Hash.mix(pos.asLong() * 31 + seat) >>> 40) & 0xFFFF) / 65536.0f * Mth.TWO_PI;
        return AMPLITUDE[seat] * Mth.sin(ticks / PERIOD_TICKS[seat] * Mth.TWO_PI + phase);
    }

    /** Runs on the client only (see {@link SwingSetBlock#getTicker}). */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SwingSetBlockEntity swing) {
        long now = level.getGameTime();
        float d = seatAngle(pos, 0, now + 1) - seatAngle(pos, 0, now);
        int direction = d >= 0 ? 1 : -1;
        boolean turned = swing.lastDirection != 0 && direction != swing.lastDirection;
        swing.lastDirection = direction;
        if (!turned || level.random.nextInt(CREAK_ONE_IN) != 0) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.5;
        double z = pos.getZ() + 0.5;
        if (level.getNearestPlayer(x, y, z, CREAK_RANGE, false) == null) {
            return;
        }
        level.playLocalSound(x, y, z, ModSounds.L9_SWING_CREAK.get(), SoundSource.AMBIENT,
                0.55f, 0.9f + level.random.nextFloat() * 0.2f, false);
    }
}
