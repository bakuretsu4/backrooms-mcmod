package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The "cobra head" lamp at the tip of a Level 9 streetlamp arm, with the glowing glass bowl on
 * its underside. FACING is the way the arm points (the pole is behind the lamp, on the opposite
 * side). The lamp hangs in the upper part of its block, so the block below it stays open air.
 *
 * <p>MODE says whether the lamp burns, flickers or is dead. Light level and full-bright rendering
 * are set per MODE where the block is registered (ModBlocks), and the lighting code reads MODE
 * to make flickering lamps flicker.
 */
public class StreetlampHeadBlock extends WallMountBlock {
    public static final EnumProperty<LampMode> MODE = EnumProperty.create("mode", LampMode.class);

    /** Housing + glass bowl, and the neck where the arm plugs in (south side, for FACING = NORTH). */
    private static final VoxelShape SHAPE_FACING_NORTH = Shapes.or(
            Block.box(4.5, 9.5, 1, 11.5, 15, 12.5),
            Block.box(6.5, 11.5, 12.5, 9.5, 14.5, 16));

    public StreetlampHeadBlock(Properties properties) {
        super(properties, SHAPE_FACING_NORTH);
        registerDefaultState(defaultBlockState().setValue(MODE, LampMode.LIT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }
}
