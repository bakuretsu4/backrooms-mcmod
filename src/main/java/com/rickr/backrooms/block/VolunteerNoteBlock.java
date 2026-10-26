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
 * A handwritten note pinned to the wall of the Manila Room by the volunteers who keep it stocked
 * (https://backrooms-wiki.wikidot.com/manila-room). Right-click to read it. NOTE picks which of
 * the notes it is (message.backrooms.manila_note.N in the language file); FACING points away from
 * the wall, like every wall-mounted block. You walk past it, not into it.
 */
public class VolunteerNoteBlock extends WallMountBlock {
    public static final int NOTES = 6;
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, NOTES - 1);

    public VolunteerNoteBlock(Properties properties) {
        // A sheet of paper flat against the wall, a little above the middle.
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
            player.sendSystemMessage(Component.translatable("message.backrooms.manila_note." + state.getValue(NOTE))
                    .withStyle(s -> s.withItalic(true)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
