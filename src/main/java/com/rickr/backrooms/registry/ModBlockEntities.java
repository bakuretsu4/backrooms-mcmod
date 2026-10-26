package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.SupplyContainerBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entities: blocks that need to store extra data, like a crate's inventory. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Backrooms.MOD_ID);

    // The three supply containers share one block entity class; each block has its own type.
    @SuppressWarnings("DataFlowIssue") // build(null): we have no datafixer type, like most mods
    public static final Supplier<BlockEntityType<SupplyContainerBlockEntity>> SUPPLY_CRATE = BLOCK_ENTITIES.register("supply_crate",
            () -> BlockEntityType.Builder.of(SupplyContainerBlockEntity::new, ModBlocks.SUPPLY_CRATE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SupplyContainerBlockEntity>> BACKPACK = BLOCK_ENTITIES.register("backpack",
            () -> BlockEntityType.Builder.of(SupplyContainerBlockEntity::new, ModBlocks.BACKPACK.get()).build(null));
    // Level 4's filing-cabinet locker is a LOCKER too, so it shares this type (and its loot tables).
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SupplyContainerBlockEntity>> SUPPLY_LOCKER = BLOCK_ENTITIES.register("supply_locker",
            () -> BlockEntityType.Builder.of(SupplyContainerBlockEntity::new,
                    ModBlocks.SUPPLY_LOCKER.get(), ModBlocks.SUPPLY_CABINET.get()).build(null));

    private ModBlockEntities() {}
}
