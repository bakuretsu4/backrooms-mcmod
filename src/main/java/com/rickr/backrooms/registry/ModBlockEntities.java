package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.SupplyCrateBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entities: blocks that need to store extra data, like a crate's inventory. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Backrooms.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // build(null): we have no datafixer type, like most mods
    public static final Supplier<BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE = BLOCK_ENTITIES.register("supply_crate",
            () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new, ModBlocks.SUPPLY_CRATE.get()).build(null));

    private ModBlockEntities() {}
}
