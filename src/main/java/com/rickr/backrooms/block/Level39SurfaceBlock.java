package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A wallpaper, floor or ceiling of the Level 39 houses: a {@link SurfaceBlock} (worn and stained
 * versions picked by the world generator) that can also be drawn upside down (INVERTED), for the
 * houses that are built upside down: their floorboards are overhead, their popcorn ceiling is
 * underfoot and their skirting boards run along the top of the walls.
 *
 * <p>The floor blocks have the popcorn ceiling on their underside, so one block is both the floor
 * of a room and the ceiling of the room below it.
 */
public class Level39SurfaceBlock extends SurfaceBlock {
    public static final BooleanProperty INVERTED = Level39FurnitureBlock.INVERTED;

    public Level39SurfaceBlock(Properties properties, int... weights) {
        super(properties, weights);
        registerDefaultState(defaultBlockState().setValue(INVERTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INVERTED);
    }
}
