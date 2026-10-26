package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModItems;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The small office fridges standing in the pods of Level 4.3 ("The Cubicles"): canon says they
 * hold Almond Water (https://backrooms.fandom.com/wiki/Level_4.3). Right-click to take a bottle
 * while there is one left (the USES property; it looks the same full or empty, like any fridge),
 * then it only holds an old lunch. It works like the water cooler ({@link WaterCoolerBlock}).
 */
public class OfficeFridgeBlock extends FurnitureBlock {
    public static final int MAX_USES = 2;
    public static final IntegerProperty USES = IntegerProperty.create("uses", 0, MAX_USES);

    public OfficeFridgeBlock(Properties properties) {
        // Same box as the house fridge (its model is reused).
        super(properties, Block.box(1, 0, 2, 15, 16, 16));
        registerDefaultState(defaultBlockState().setValue(USES, MAX_USES));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(USES);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The door seal unsticking with a little rattle of glass.
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5f, 1.4f);
        int uses = state.getValue(USES);
        if (uses <= 0) {
            player.displayClientMessage(Component.translatable("message.backrooms.office_fridge.empty"), true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(USES, uses - 1), Block.UPDATE_ALL);
        ItemStack bottle = new ItemStack(ModItems.ALMOND_WATER.get());
        if (!player.addItem(bottle)) {
            player.drop(bottle, false);
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4f, 1.2f);
        return InteractionResult.CONSUME;
    }
}
