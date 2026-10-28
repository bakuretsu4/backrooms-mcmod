package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.Level2ChamberPropBlock;
import com.rickr.backrooms.block.Level2HeartGaugeBlock;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Blocks of Level 2's maintenance chambers and oddities (world/layout/Level2Chambers and
 * Level2Oddities): the things the crews left behind, and the gauge that reads your heart.
 * Textures, models and blockstates: tools/anomaly_assets/level2_chambers.py.
 *
 * <p>Like every Backrooms block they go through the shared {@link ModBlocks#BLOCKS} register (and
 * {@link ModItems#ITEMS}, {@link ModBlockEntities#BLOCK_ENTITIES}); the mod constructor calls
 * {@link #bootstrap()} so this class loads in time.
 */
public final class Level2ChamberBlocks {
    /** A logbook, kettle, mug or work jacket (PROP); the logbook can be read. Walk-through. */
    public static final DeferredBlock<Level2ChamberPropBlock> PROP = ModBlocks.BLOCKS.registerBlock("l2_chamber_prop",
            Level2ChamberPropBlock::new, unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noCollission().noOcclusion());
    /** A pressure gauge whose needle follows your heartbeat (the heart_gauge oddity). */
    public static final DeferredBlock<Level2HeartGaugeBlock> HEART_GAUGE = ModBlocks.BLOCKS.registerBlock("l2_heart_gauge",
            Level2HeartGaugeBlock::new, unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());

    @SuppressWarnings("DataFlowIssue") // build(null): we have no datafixer type, like most mods
    public static final Supplier<BlockEntityType<Level2HeartGaugeBlock.Level2HeartGaugeBlockEntity>> HEART_GAUGE_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("l2_heart_gauge",
                    () -> BlockEntityType.Builder.of(Level2HeartGaugeBlock.Level2HeartGaugeBlockEntity::new, HEART_GAUGE.get()).build(null));

    static {
        ModItems.ITEMS.registerSimpleBlockItem(PROP);
        ModItems.ITEMS.registerSimpleBlockItem(HEART_GAUGE);
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    /** A prop facing {@code facing} (away from its wall, or towards whoever reads it). */
    public static BlockState prop(Level2ChamberPropBlock.Prop prop, Direction facing) {
        return PROP.get().defaultBlockState()
                .setValue(Level2ChamberPropBlock.FACING, facing)
                .setValue(Level2ChamberPropBlock.PROP, prop);
    }

    /** The heart gauge on a wall, facing away from it. */
    public static BlockState heartGauge(Direction facing) {
        return HEART_GAUGE.get().defaultBlockState().setValue(Level2HeartGaugeBlock.FACING, facing);
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(sound)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private Level2ChamberBlocks() {}
}
