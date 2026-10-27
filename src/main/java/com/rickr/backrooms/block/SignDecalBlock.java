package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A wall block with a sign painted on its FACING side (see {@link WallDecalBlock}); VARIANT picks
 * which sign. Level 1's direction signs: EXIT, SALIDA, SORTIE or AUSGANG with an arrow, pointing
 * nowhere in particular (usually at a blank wall).
 */
public class SignDecalBlock extends WallDecalBlock {
    public static final int VARIANTS = 8;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANTS - 1);

    public SignDecalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
}
