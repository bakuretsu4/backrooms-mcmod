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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where Level 39 keeps its Almond Water (canon: "on shelves, and occasionally in fridges"): the
 * pantry shelf and the old monitor-top fridge of the houses. Right-click with anything and you
 * take one bottle; LEFT counts the bottles still there (the shelf's model shows them), then it is
 * bare for good. Works like the Level 94 supplies ({@link Level94SupplyBlock}), plus the upside
 * down look every Level 39 furniture piece has.
 */
public class Level39SupplyFurnitureBlock extends Level39FurnitureBlock {
    public static final int MAX_LEFT = 4;
    public static final IntegerProperty LEFT = IntegerProperty.create("left", 0, MAX_LEFT);

    private final String emptyMessage;
    private final boolean fridge;

    /**
     * @param emptyMessage translation key of the message once nothing is left
     * @param fridge       true for the fridge: its door unsticks with a clunk before you look inside
     */
    public Level39SupplyFurnitureBlock(Properties properties, VoxelShape shapeFacingNorth, String emptyMessage, boolean fridge) {
        super(properties, shapeFacingNorth);
        this.emptyMessage = emptyMessage;
        this.fridge = fridge;
        registerDefaultState(defaultBlockState().setValue(LEFT, MAX_LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEFT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (fridge) {
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5f, 1.3f);
        }
        int left = state.getValue(LEFT);
        if (left <= 0) {
            player.displayClientMessage(Component.translatable(emptyMessage), true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(LEFT, left - 1), Block.UPDATE_ALL);
        ItemStack bottle = new ItemStack(ModItems.ALMOND_WATER.get());
        if (!player.addItem(bottle)) {
            player.drop(bottle, false);
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f, 1.1f + level.getRandom().nextFloat() * 0.1f);
        return InteractionResult.CONSUME;
    }
}
