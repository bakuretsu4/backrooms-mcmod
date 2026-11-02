package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.CeilingFanBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.MarkingBlock;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The few new blocks of Level 0's street hall (world/layout/Level0Street). Everything else there
 * is Level 9's suburb (siding, roofs, garage doors, fences, mailboxes, asphalt, the house kit).
 * <ul>
 *   <li>{@link #ROAD}: pale grey daytime asphalt (64 px), and {@link #ROAD_YELLOW} the same with
 *       the double yellow centre line.</li>
 *   <li>{@link #SIDING_RED}: dark red-brown siding, the film's right-hand house (64 px).</li>
 *   <li>{@link #STOP_SIGN}: a stop sign on its post (32 px texture; the post reuses sign_post).</li>
 *   <li>{@link #CEILING_FAN}: the big fan high in the hall, drawn by client/CeilingFanRenderer
 *       with no texture of its own.</li>
 * </ul>
 * Textures and models: tools/anomaly_assets/level0_street.py.
 */
public final class Level0StreetBlocks {
    public static final DeferredBlock<Block> ROAD = ModBlocks.BLOCKS.registerBlock("l0_road", Block::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    /** The road with the double yellow centre line running along AXIS. */
    public static final DeferredBlock<MarkingBlock> ROAD_YELLOW = ModBlocks.BLOCKS.registerBlock("l0_road_yellow",
            MarkingBlock::new, unbreakable(MapColor.STONE, SoundType.STONE));
    public static final DeferredBlock<Block> SIDING_RED = ModBlocks.BLOCKS.registerBlock("l0_siding_red", Block::new,
            unbreakable(MapColor.TERRACOTTA_RED, SoundType.WOOD));
    /** A stop sign on its post, facing FACING; only the post collides. */
    public static final DeferredBlock<FurnitureBlock> STOP_SIGN = ModBlocks.BLOCKS.registerBlock("l0_stop_sign",
            props -> new FurnitureBlock(props, Block.box(7, 0, 7, 9, 16, 9)),
            unbreakable(MapColor.COLOR_RED, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<CeilingFanBlock> CEILING_FAN = ModBlocks.BLOCKS.registerBlock("l0_ceiling_fan",
            CeilingFanBlock::new, unbreakable(MapColor.COLOR_GRAY, SoundType.METAL).noOcclusion());

    @SuppressWarnings("DataFlowIssue") // build(null): we have no datafixer type, like most mods
    public static final Supplier<BlockEntityType<CeilingFanBlock.Entity>> CEILING_FAN_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("l0_ceiling_fan",
                    () -> BlockEntityType.Builder.of(CeilingFanBlock.Entity::new, CEILING_FAN.get()).build(null));

    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {ROAD, ROAD_YELLOW, SIDING_RED, STOP_SIGN, CEILING_FAN}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
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

    private Level0StreetBlocks() {}
}
