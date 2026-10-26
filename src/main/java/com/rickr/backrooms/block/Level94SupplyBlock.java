package com.rickr.backrooms.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Level 94 supply you can drink from, a few times: the tap at the foot of a water tower (the
 * towers "contain Almond Water") and the crate of Almond Milk at the back of a milk van (the vans
 * are "filled with Almond Milk"), both from https://backrooms-wiki.wikidot.com/level-94.
 *
 * <p>Works like the office water cooler: right-click with whatever you're holding and you get one
 * bottle; LEFT counts how many are left (the crate's model shows the bottles until it is empty),
 * then it is dry for good.
 */
public class Level94SupplyBlock extends FurnitureBlock {
    public static final int MAX_LEFT = 4;
    public static final IntegerProperty LEFT = IntegerProperty.create("left", 0, MAX_LEFT);

    private final Supplier<? extends Item> item;
    private final SoundEvent fillSound;
    private final String emptyMessage;

    /**
     * @param item         what you get each time
     * @param fillSound    the sound of getting it
     * @param emptyMessage translation key of the message once it is dry
     */
    public Level94SupplyBlock(Properties properties, VoxelShape shapeFacingNorth, Supplier<? extends Item> item,
                              SoundEvent fillSound, String emptyMessage) {
        super(properties, shapeFacingNorth);
        this.item = item;
        this.fillSound = fillSound;
        this.emptyMessage = emptyMessage;
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
        int left = state.getValue(LEFT);
        if (left <= 0) {
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.4f, 0.6f);
            player.displayClientMessage(Component.translatable(emptyMessage), true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(LEFT, left - 1), Block.UPDATE_ALL);
        ItemStack bottle = new ItemStack(item.get());
        if (!player.addItem(bottle)) {
            player.drop(bottle, false);
        }
        level.playSound(null, pos, fillSound, SoundSource.BLOCKS, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        return InteractionResult.CONSUME;
    }
}
