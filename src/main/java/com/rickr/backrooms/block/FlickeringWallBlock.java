package com.rickr.backrooms.block;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Level 0's "flickering wall" (https://backrooms-wiki.wikidot.com/level-0: find a wall that
 * flickers and throw yourself through it, and you come out in Level 1). It looks like a stretch of
 * yellow wallpaper stuttering like a dying tube (animated texture), it buzzes and flickers like
 * one (the lighting code sees MODE = flicker, see client/light/FixtureType), and it is not really
 * there: you walk into it, and walking into it on Level 0 takes you to Level 1.
 *
 * <p>Found in the maze near the Manila Room, and in the Red Rooms' tally room when it's time to
 * let you go (see {@code world/anomaly/level0}).
 */
public class FlickeringWallBlock extends Block {
    /**
     * Always "flicker": only here so the lighting code treats it as a stuttering lamp. Minecraft
     * refuses a property with a single value, so "lit" exists too (same look; never placed).
     */
    public static final EnumProperty<LampMode> MODE = EnumProperty.create("mode", LampMode.class, LampMode.FLICKER, LampMode.LIT);

    public FlickeringWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, LampMode.FLICKER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()
                && BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_0) {
            Travel.useExit(player, BackroomsLevel.LEVEL_0);
        }
    }
}
