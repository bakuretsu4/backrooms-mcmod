package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * A 1950s porcelain pedestal sink, as found in the single rooms and furniture piles out on the
 * Level 94 hills. In the M.E.G.'s log of the discovery of Level 94
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94), closer to the castle
 * "there were only piles of furniture", and in a sink in one of them they found a coin dated 1953.
 *
 * <p>COIN: the coin still lies in the plughole (the model shows it glinting). Right-click to take
 * it: you get the {@code coin_1953} keepsake and the sink is empty for good. A sink without a
 * coin is just a dry sink.
 */
public class Level94SinkBlock extends FurnitureBlock {
    public static final BooleanProperty COIN = BooleanProperty.create("coin");

    public Level94SinkBlock(Properties properties) {
        // The basin on its pedestal, its back against the south side when facing north.
        super(properties, Shapes.or(
                Block.box(6, 0, 7, 10, 10, 12),
                Block.box(2, 10, 4, 14, 14, 15)));
        registerDefaultState(defaultBlockState().setValue(COIN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COIN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(COIN)) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.4f, 1.4f);
            player.displayClientMessage(Component.translatable("message.backrooms.l94.sink_empty"), true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(COIN, false), Block.UPDATE_ALL);
        ItemStack coin = new ItemStack(ModItems.COIN_1953.get());
        if (!player.addItem(coin)) {
            player.drop(coin, false);
        }
        level.playSound(null, pos, ModSounds.L94_COIN.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        player.displayClientMessage(Component.translatable("message.backrooms.l94.sink_coin"), true);
        return InteractionResult.CONSUME;
    }
}
