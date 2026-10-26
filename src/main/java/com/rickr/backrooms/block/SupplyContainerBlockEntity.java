package com.rickr.backrooms.block;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.item.WandererNoteItem;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The inside of a supply container (Wooden Crate, Backpack or Supply Locker). It starts empty and
 * gets filled from a loot table the first time anyone opens it (so we don't do any work for
 * containers nobody finds). The menu is vanilla's chest menu, so the vanilla chest screen shows
 * it: no client code needed.
 *
 * <p>Which loot table? {@code data/backrooms/loot_table/supplies/<container>/<level>.json}, e.g.
 * {@code supplies/backpack/level_2}. If that file doesn't exist (or we're outside the Backrooms),
 * {@code supplies/<container>/default.json} is used instead.
 */
public class SupplyContainerBlockEntity extends BaseContainerBlockEntity {
    private final SupplyKind kind;
    private NonNullList<ItemStack> items;
    private boolean lootFilled;

    /** One constructor for all three block entity types: the block tells us which kind we are. */
    public SupplyContainerBlockEntity(BlockPos pos, BlockState state) {
        this(kindOf(state), pos, state);
    }

    private SupplyContainerBlockEntity(SupplyKind kind, BlockPos pos, BlockState state) {
        super(kind.blockEntityType(), pos, state);
        this.kind = kind;
        this.items = NonNullList.withSize(kind.size(), ItemStack.EMPTY);
    }

    private static SupplyKind kindOf(BlockState state) {
        return state.getBlock() instanceof SupplyContainerBlock block ? block.kind() : SupplyKind.CRATE;
    }

    /** True once someone has opened it (its loot has been rolled). Level 1's vanishing crates only take unopened ones. */
    public boolean isLootFilled() {
        return lootFilled;
    }

    /** Server only: rolls the loot once. Called right before the menu opens. */
    public void fillLootIfNeeded(ServerLevel level, @Nullable Player opener) {
        if (lootFilled) {
            return;
        }
        lootFilled = true;
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition));
        if (opener != null) {
            params.withLuck(opener.getLuck()).withParameter(LootContextParams.THIS_ENTITY, opener);
        }
        // fill() puts the rolled items into random slots, so it looks scattered like a vanilla chest.
        lootTable(level).fill(this, params.create(LootContextParamSets.CHEST), level.getRandom().nextLong());
        if (isEmpty()) {
            // Never a completely empty container: that just feels like a bug.
            items.set(level.getRandom().nextInt(items.size()), new ItemStack(ModItems.CANNED_FOOD.get()));
        }
        tuckInNote(level);
        setChanged();
    }

    /**
     * Sometimes someone who came through before left a torn note in here, about where they think
     * the exit is (see WandererNoteItem). Backpacks most often: they belonged to someone.
     */
    private void tuckInNote(ServerLevel level) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        float chance = kind == SupplyKind.BACKPACK ? 0.4f : 0.2f;
        if (floor == null || level.getRandom().nextFloat() >= chance) {
            return;
        }
        int start = level.getRandom().nextInt(items.size());
        for (int i = 0; i < items.size(); i++) {
            int slot = (start + i) % items.size();
            if (items.get(slot).isEmpty()) {
                items.set(slot, WandererNoteItem.create(level, floor, worldPosition, level.getRandom()));
                return;
            }
        }
    }

    /** The loot table for this container on this floor, falling back to the container's default table. */
    private LootTable lootTable(ServerLevel level) {
        ReloadableServerRegistries.Holder tables = level.getServer().reloadableRegistries();
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor != null) {
            LootTable table = tables.getLootTable(lootKey(floor.getSerializedName()));
            // getLootTable never returns null: a missing file gives the shared EMPTY table.
            if (table != LootTable.EMPTY) {
                return table;
            }
        }
        return tables.getLootTable(lootKey("default"));
    }

    /** backrooms:supplies/<container>/<name>, e.g. backrooms:supplies/crate/level_0 */
    private ResourceKey<LootTable> lootKey(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, Backrooms.id("supplies/" + kind.lootName() + "/" + name));
    }

    /** Called (server side) when a player's menu opens; plays the open sound (creak, zipper, latch). */
    @Override
    public void startOpen(Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, kind.openSound(), SoundSource.BLOCKS, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.15f);
        }
    }

    /** Called (server side) when a player's menu closes. */
    @Override
    public void stopOpen(Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, kind.closeSound(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.15f);
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(kind.titleKey());
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
        // A backpack has one row of slots, crates and lockers have three.
        MenuType<ChestMenu> type = kind.rows() == 1 ? MenuType.GENERIC_9x1 : MenuType.GENERIC_9x3;
        return new ChestMenu(type, containerId, inventory, this, kind.rows());
    }

    @Override
    public int getContainerSize() {
        return kind.size();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(kind.size(), ItemStack.EMPTY);
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
