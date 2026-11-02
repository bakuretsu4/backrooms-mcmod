package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A small framed family photo hanging on a Level 9 house wall. It sits in the air block in front
 * of the wall; FACING points away from the wall (like every {@link WallMountBlock}).
 *
 * <p>PHOTO picks one of three pictures. SCRATCHED photos have the faces gouged out: someone in
 * the house didn't want to be looked at any more (or didn't want to look at them).
 */
public class FamilyPhotoBlock extends WallMountBlock {
    /** Which of the three family pictures is in the frame. */
    public static final IntegerProperty PHOTO = IntegerProperty.create("photo", 0, 2);
    /** The faces are scratched out. */
    public static final BooleanProperty SCRATCHED = BooleanProperty.create("scratched");

    public FamilyPhotoBlock(Properties properties) {
        // A 7 x 9 px frame, 1 px deep, against the south side of the block when it faces north.
        super(properties, 4.5, 3, 15, 11.5, 12, 16);
        registerDefaultState(defaultBlockState().setValue(PHOTO, 0).setValue(SCRATCHED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PHOTO, SCRATCHED);
    }
}
