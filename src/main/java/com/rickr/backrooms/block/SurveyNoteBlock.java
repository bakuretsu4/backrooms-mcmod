package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A water-stained clipboard left behind at an abandoned M.E.G. survey camp in Level 39 (the
 * M.E.G. sends a team there every couple of months). It lies flat on a crate or on the ground;
 * right-click to read it. NOTE picks which page it is (message.backrooms.l39_survey_note.N in the
 * language file). FACING only turns it so the camps don't all look alike.
 */
public class SurveyNoteBlock extends WallMountBlock {
    public static final int NOTES = 4;
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, NOTES - 1);

    public SurveyNoteBlock(Properties properties) {
        // A clipboard lying flat, a little off centre.
        super(properties, 3.5, 0, 2.5, 12.5, 1, 14);
        registerDefaultState(defaultBlockState().setValue(NOTE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NOTE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.sendSystemMessage(Component.translatable("message.backrooms.l39_survey_note." + state.getValue(NOTE))
                    .withStyle(s -> s.withItalic(true)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
