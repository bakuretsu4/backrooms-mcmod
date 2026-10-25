package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The inside of a Wooden Crate: 27 slots like a single chest. It starts empty and gets filled
 * with random supplies the first time anyone opens it (so we don't do any work for crates nobody finds).
 * The menu is vanilla's 9x3 chest menu, so the vanilla chest screen shows it — no client code needed.
 */
public class SupplyCrateBlockEntity extends BaseContainerBlockEntity {
    public static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean lootFilled;

    public SupplyCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPPLY_CRATE.get(), pos, state);
    }

    /** Server only: rolls the loot once. Called right before the menu opens. */
    public void fillLootIfNeeded(RandomSource random) {
        if (lootFilled) {
            return;
        }
        lootFilled = true;
        add(random, new ItemStack(ModItems.CANNED_FOOD.get(), 1 + random.nextInt(2)), 0.55f);
        add(random, new ItemStack(ModItems.BACKSHROOM.get(), 1 + random.nextInt(3)), 0.45f);
        add(random, new ItemStack(ModItems.ALMOND_WATER.get(), 1 + random.nextInt(2)), 0.5f);
        add(random, new ItemStack(Items.BREAD, 1 + random.nextInt(3)), 0.3f);
        // Batteries keep your camcorder (and flashlight) alive, so they're the rare prize.
        add(random, new ItemStack(ModItems.CAMCORDER_BATTERY.get()), 0.2f);
        if (isEmpty()) {
            // Never a completely empty crate: that just feels like a bug.
            add(random, new ItemStack(ModItems.CANNED_FOOD.get()), 1.0f);
        }
        setChanged();
    }

    /** With the given chance, puts the stack into a random empty slot (looks scattered, like a real crate). */
    private void add(RandomSource random, ItemStack stack, float chance) {
        if (random.nextFloat() >= chance) {
            return;
        }
        for (int tries = 0; tries < SIZE; tries++) {
            int slot = random.nextInt(SIZE);
            if (items.get(slot).isEmpty()) {
                items.set(slot, stack);
                return;
            }
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.backrooms.supply_crate");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        lootFilled = tag.getBoolean("LootFilled");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("LootFilled", lootFilled);
    }
}
