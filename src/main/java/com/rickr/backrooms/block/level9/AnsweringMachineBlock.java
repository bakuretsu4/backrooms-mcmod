package com.rickr.backrooms.block.level9;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * A 1980s answering machine on a kitchen counter in a Level 9 house. Its little red light blinks
 * slowly while a message is waiting (UNHEARD), and quickly while the tape plays (PLAYING): the
 * two blink speeds are two animated textures picked by the blockstate file, so no code runs on
 * the client for it.
 *
 * <p>Right-click: the tape plays message MESSAGE (sound {@code level9.machine_message_N}, a
 * recorded voicemail that starts ordinary and goes wrong). The light keeps blinking fast for as
 * long as the recording lasts ({@link #LENGTH_TICKS}), then a scheduled block tick turns it off.
 * Clicking while it plays does nothing, so the same message can't pile up on itself. Nothing in
 * these recordings is loud: they are played at a normal speaking volume.
 *
 * <p>FACING = the way its front (the buttons) looks, like all furniture. It stands on top of the
 * counter (one block above it).
 */
public class AnsweringMachineBlock extends FurnitureBlock {
    public static final int MESSAGES = 8;
    public static final IntegerProperty MESSAGE = IntegerProperty.create("message", 0, MESSAGES - 1);
    public static final BooleanProperty UNHEARD = BooleanProperty.create("unheard");
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    /**
     * How long each recording lasts, in ticks (20 per second), rounded up. These come from
     * tools/level9_assets/story.py, which prints them after it makes the sounds: if a message is
     * re-recorded, copy the new numbers here.
     */
    static final int[] LENGTH_TICKS = {565, 632, 450, 413, 460, 474, 549, 481};

    public AnsweringMachineBlock(Properties properties) {
        super(properties, Shapes.or(
                Block.box(3, 0, 4, 13, 3, 12),
                Block.box(4, 3, 6, 12, 3.5, 11)));
        registerDefaultState(defaultBlockState()
                .setValue(MESSAGE, 0)
                .setValue(UNHEARD, true)
                .setValue(PLAYING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MESSAGE, UNHEARD, PLAYING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || state.getValue(PLAYING)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int message = state.getValue(MESSAGE);
        // The PLAY button, then the tape. Both at an ordinary volume (heard ~14 blocks away).
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.7f);
        level.playSound(null, pos, ModSounds.L9_MACHINE_MESSAGES.get(message).get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        player.displayClientMessage(Component.translatable(state.getValue(UNHEARD)
                ? "message.backrooms.l9_machine.new" : "message.backrooms.l9_machine.saved"), true);
        level.setBlock(pos, state.setValue(PLAYING, true).setValue(UNHEARD, false), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, LENGTH_TICKS[message]);
        return InteractionResult.SUCCESS;
    }

    /** The recording has ended: the light goes out (the message has been heard now). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PLAYING)) {
            level.setBlock(pos, state.setValue(PLAYING, false), Block.UPDATE_CLIENTS);
        }
    }
}
