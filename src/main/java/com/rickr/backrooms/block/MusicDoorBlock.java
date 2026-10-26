package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The office door with a brass "6" on it. It is always locked (it only rattles), and when you
 * stand near it you can hear faint classical music playing somewhere behind it. Nobody knows who
 * is listening.
 *
 * <p>The music is played on each client from {@link #animateTick}, which Minecraft calls now and
 * then for blocks near the player (that's how torches make smoke). A short clip plays at the door,
 * at most once every {@link #CLIP_GAP_MS} so clips never overlap.
 */
public class MusicDoorBlock extends HouseDoorBlock {
    /** The clip is ~11 s long; leave a little silence between plays. */
    private static final long CLIP_GAP_MS = 14_000L;
    /** Client side only: when the last clip started. */
    private static long lastPlayMs;

    public MusicDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
        registerDefaultState(defaultBlockState().setValue(LOCKED, true));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPlayMs < CLIP_GAP_MS) {
            return;
        }
        lastPlayMs = now;
        // Quiet and muffled, as if through the door and a room; it fades within ~10 blocks.
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ModSounds.LEVEL4_DOOR_MUSIC.get(),
                SoundSource.AMBIENT, 0.55f, 1.0f, false);
    }
}
