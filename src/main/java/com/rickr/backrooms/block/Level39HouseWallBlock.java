package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * An outside wall block of a Level 39 house: light-blue clapboard on the FACING side (the street
 * side), and on the other sides whatever the room behind it is papered with. That way one block
 * thick walls look right from both sides, as in the Level 94 houses.
 *
 * <p>INNER picks the inside finish (see {@link #INNERS}); SHADE the paint outside (0 = fresh
 * light blue, 1 = faded, 2 = weathered, the same three as the clapboard blocks); INVERTED draws the
 * block upside down for the upside-down houses (the baseboard on top, see
 * {@link Level39FurnitureBlock}).
 */
public class Level39HouseWallBlock extends WallDecalBlock {
    /**
     * The inside finishes, in INNER order. "_base" papers have a skirting board along the bottom;
     * beadboard (white tongue-and-groove, kitchens and bathrooms) and wainscot (dark panelled wood
     * with a chair rail) are the bottom row of a room; plaster is for attics, basements and garages.
     */
    public static final String[] INNERS = {
            "damask", "stripe", "floral", "rose", "lattice",
            "damask_base", "stripe_base", "floral_base", "rose_base", "lattice_base",
            "beadboard", "wainscot", "plaster"};
    public static final int PAPERS = 5;
    public static final int BASE = 5;
    public static final int BEADBOARD = 10;
    public static final int WAINSCOT = 11;
    public static final int PLASTER = 12;

    public static final IntegerProperty INNER = IntegerProperty.create("inner", 0, INNERS.length - 1);
    public static final IntegerProperty SHADE = Level39KnockWallBlock.SHADE;
    public static final BooleanProperty INVERTED = Level39FurnitureBlock.INVERTED;

    public Level39HouseWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(INNER, 0).setValue(SHADE, 0).setValue(INVERTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INNER, SHADE, INVERTED);
    }
}
