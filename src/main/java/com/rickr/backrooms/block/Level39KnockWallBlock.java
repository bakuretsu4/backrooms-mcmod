package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level39HouseBlocks;
import com.rickr.backrooms.world.Level39Quakes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A rotten, cracked stretch of a Level 39 house wall: cracked clapboard on the outside (FACING),
 * cracked plaster with the wallpaper torn on the inside. Canon: the houses are hard to get into
 * (their doors are locked or fake) and the usual way in is knocking a wall down. These are the
 * only house blocks that break: a few seconds by hand, and they drop nothing.
 *
 * <p>It gives way with a loud, splintering crack, and the town echoes it back. A sound that loud
 * can set off one of Level 39's echo quakes ({@link Level39Quakes}).
 *
 * <p>SHADE matches the house's paint: 0 = fresh light blue, 1 = faded, 2 = weathered.
 */
public class Level39KnockWallBlock extends WallDecalBlock {
    public static final IntegerProperty SHADE = IntegerProperty.create("shade", 0, 2);
    /** How loud a wall giving way is, for the echo quake (wall knocking is about half strength). */
    public static final float QUAKE_STRENGTH = 0.55f;

    public Level39KnockWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SHADE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHADE);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            // Heard far away (volume over 1 carries further), slightly different every time.
            server.playSound(null, pos, Level39HouseBlocks.WALL_CRACK.get(), SoundSource.BLOCKS, 4.0f,
                    0.85f + server.getRandom().nextFloat() * 0.25f);
            // A puff of splinters and plaster dust on top of the usual breaking bits.
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.45, 0.45, 0.45, 0.15);
            server.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.4, 0.4, 0.4, 0.02);
            Level39Quakes.trigger(server, pos, QUAKE_STRENGTH);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
