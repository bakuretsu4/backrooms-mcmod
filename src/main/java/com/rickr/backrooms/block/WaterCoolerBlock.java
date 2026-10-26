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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * An office water cooler: a white cabinet with an upturned blue bottle on top. The water in it
 * turned out to be Almond Water. Right-click it to fill a bottle (whatever you are holding, so you
 * can fill several in a row: Minecraft asks the block before the item in your hand); it has
 * {@link #MAX_USES} fills (the USES property, so the model can show the water level going down),
 * then it is empty for good.
 */
public class WaterCoolerBlock extends FurnitureBlock {
    public static final int MAX_USES = 3;
    public static final IntegerProperty USES = IntegerProperty.create("uses", 0, MAX_USES);

    public WaterCoolerBlock(Properties properties) {
        // Cabinet, plus the bottle sticking up into the block above (collision 1.5 high, like a fence).
        super(properties, Shapes.or(
                Block.box(3, 0, 4, 13, 16, 14),
                Block.box(4.5, 16, 5.5, 11.5, 24, 12.5)));
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
        int uses = state.getValue(USES);
        if (uses <= 0) {
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.4f, 0.6f);
            player.displayClientMessage(Component.translatable("message.backrooms.water_cooler.empty"), true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(USES, uses - 1), Block.UPDATE_ALL);
        ItemStack bottle = new ItemStack(ModItems.ALMOND_WATER.get());
        if (!player.addItem(bottle)) {
            player.drop(bottle, false);
        }
        level.playSound(null, pos, ModSounds.WATER_COOLER_GLUG.get(), SoundSource.BLOCKS, 0.9f,
                0.95f + level.getRandom().nextFloat() * 0.1f);
        return InteractionResult.CONSUME;
    }
}
