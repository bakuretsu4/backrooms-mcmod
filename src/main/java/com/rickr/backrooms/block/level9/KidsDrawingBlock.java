package com.rickr.backrooms.block.level9;

import com.rickr.backrooms.block.WallMountBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A child's crayon drawing taped to a bedroom wall in a Level 9 house. DRAWING picks the picture
 * (textures/block/l9_kids_drawing_N, and a big copy in textures/gui/l9_kids_drawing_N):
 * <ol start="0">
 *   <li>the tall man with the light, over our houses (a Watcher);</li>
 *   <li>my family (one of us has no face);</li>
 *   <li>the streetlights on our street (one of them is coloured in black);</li>
 *   <li>our house at night, and the porch light that stays on.</li>
 * </ol>
 * FACING points away from the wall. You walk past it. Right-click to look at it closely (the
 * client opens the page, see client/level9/Level9StoryClient).
 */
public class KidsDrawingBlock extends WallMountBlock {
    public static final int DRAWINGS = 4;
    public static final IntegerProperty DRAWING = IntegerProperty.create("drawing", 0, DRAWINGS - 1);

    public KidsDrawingBlock(Properties properties) {
        // A landscape sheet of paper, flat against the wall at about eye height.
        super(properties, 2, 3, 15.5, 14, 13, 16);
        registerDefaultState(defaultBlockState().setValue(DRAWING, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DRAWING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
