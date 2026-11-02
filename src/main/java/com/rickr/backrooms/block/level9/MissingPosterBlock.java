package com.rickr.backrooms.block.level9;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A "MISSING" poster stapled to a lamp pole, a fence post, a garage door or a house wall on
 * Level 9 (and sometimes a fridge). PERSON picks who is missing: the photo on the block
 * (textures/block/l9_missing_poster_N) and the words on the close-up
 * ({@code poster.backrooms.l9.N.*} in the language file). Right-click to read it (see
 * {@link PinnedPaperBlock}); now and then the face and the name on it are your own.
 */
public class MissingPosterBlock extends PinnedPaperBlock {
    public static final int PEOPLE = 8;
    public static final IntegerProperty PERSON = IntegerProperty.create("person", 0, PEOPLE - 1);

    /** Portrait sheets: big on a wall, narrower and a little lower on a lamp pole, small on a fence post. */
    private static final double[][] SHEETS = {
            {3, 1, 13, 15},
            {4, 0, 12, 11},
            {5, 3, 11, 12}};

    public MissingPosterBlock(Properties properties) {
        super(properties, SHEETS);
        registerDefaultState(defaultBlockState().setValue(PERSON, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PERSON);
    }
}
