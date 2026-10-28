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
 * A note left on the wall of one of Level 0's hidden rooms (world/layout/Level0HiddenRooms) by
 * whoever found the room first. Right-click to read it. It looks like the Manila Room's volunteer
 * notes (same model and paper), but these were written by people on their own, not volunteers.
 * NOTE picks the text (message.backrooms.l0_hidden_note.N in the language file); FACING points
 * away from the wall, like every wall-mounted block. You walk past it, not into it.
 */
public class HiddenNoteBlock extends WallMountBlock {
    public static final int NOTES = 8;
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, NOTES - 1);

    public HiddenNoteBlock(Properties properties) {
        // The same sheet of paper as the volunteer notes: flat against the wall, a little above the middle.
        super(properties, 3, 2, 15.5, 13, 15, 16);
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
            player.sendSystemMessage(Component.translatable("message.backrooms.l0_hidden_note." + state.getValue(NOTE))
                    .withStyle(s -> s.withItalic(true)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
