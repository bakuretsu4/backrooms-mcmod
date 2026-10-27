package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A Level 0 wall block with words smeared on its FACING side in dark paint, like the scrawls in
 * Kane Pixels' "Found Footage" (registry/Level0Blocks.GRAFFITI). Like every {@link WallDecalBlock}
 * it REPLACES a wall block: a full wallpaper cube, only the FACING side carries the drawing.
 *
 * <p>The writing is too long for one block, so each drawing is two blocks wide: PART 0 is the left
 * half and PART 1 the right half, as seen by someone standing in the room looking at the wall.
 * VARIANT picks the drawing:
 * <ol start="0">
 *   <li>"DON'T MOVE / STAY STILL"</li>
 *   <li>"IT HEARS ME"</li>
 *   <li>"GOD CLOSED HIS EYES"</li>
 *   <li>a child's doodle of a sun in a window (right half: a small stick figure)</li>
 * </ol>
 */
public class Level0GraffitiBlock extends WallDecalBlock {
    /** Which drawing (see the class comment). */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);
    /** Which half of the two-block drawing: 0 = left, 1 = right (seen from the room). */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);

    public Level0GraffitiBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT, PART);
    }
}
