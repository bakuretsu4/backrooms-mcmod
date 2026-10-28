package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.GiantChainBlock;
import com.rickr.backrooms.block.IronLanternBlock;
import com.rickr.backrooms.block.ShackleBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The blocks of Level 490, "Penance" (world/layout/Level490Layout): floating rock islands with
 * crumbling stone ruins, the colossal chains, broken bridges with spiked iron railings, old
 * lanterns, small stone shelters, dead trees and the shackles of whoever was chained up here. Textures and models come from
 * tools/anomaly_assets/level490.py. Like every Backrooms block they go through the shared
 * {@link ModBlocks#BLOCKS} register and get creative-tab items; the mod constructor only calls
 * {@link #bootstrap()}.
 */
public final class Level490Blocks {
    /** Pale, worn flagstones: the deck of every platform and bridge. */
    public static final DeferredBlock<Block> PAVING = structural("l490_paving", MapColor.STONE, SoundType.STONE);
    /** Big dressed stone blocks: the platforms' thick undersides and the shelters' walls. */
    public static final DeferredBlock<Block> STONE = structural("l490_stone", MapColor.STONE, SoundType.STONE);
    /** Half-height flagstones: the half steps where a bridge climbs from one island to the next. */
    public static final DeferredBlock<SlabBlock> PAVING_SLAB = block("l490_paving_slab", SlabBlock::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    /** Dark weathered rock: the floating islands themselves, tapering into the void below. */
    public static final DeferredBlock<Block> ROCK = structural("l490_rock", MapColor.DEEPSLATE, SoundType.STONE);
    /** Broken masonry and grit: the ground where the ruins have crumbled. */
    public static final DeferredBlock<Block> RUBBLE = structural("l490_rubble", MapColor.STONE, SoundType.GRAVEL);
    /** Dead, wind-flattened grass over thin soil on the islands. */
    public static final DeferredBlock<Block> DEAD_GRASS = structural("l490_dead_grass", MapColor.TERRACOTTA_BROWN, SoundType.GRASS);
    /** The rusted iron of the colossal chains (each link is built of thousands of these). */
    public static final DeferredBlock<Block> CHAIN_IRON = structural("l490_chain_iron", MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK);
    /** The grey, barkless wood of the dead trees clinging to the ruins. */
    public static final DeferredBlock<Block> DEAD_WOOD = structural("l490_dead_wood", MapColor.COLOR_GRAY, SoundType.WOOD);
    /** A few dark red leaves still hanging on some of the trees. */
    public static final DeferredBlock<Block> RED_LEAVES = block("l490_red_leaves", Block::new,
            unbreakable(MapColor.COLOR_RED, SoundType.GRASS).noOcclusion()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
    /** Dark slate: the shelters' roofs. */
    public static final DeferredBlock<Block> SLATE = structural("l490_slate", MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES);
    /** Wrought-iron railing with spear tips (a fence: it joins up and you can't step over it). */
    public static final DeferredBlock<FenceBlock> RAILING = block("l490_railing", FenceBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.CHAIN).noOcclusion());
    /** A thicker iron post in the railing that carries a lantern. */
    public static final DeferredBlock<FenceBlock> POST = block("l490_post", FenceBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.CHAIN).noOcclusion());
    /** An old iron lantern on a post: a warm flame if LIT, else cold dark glass. */
    public static final DeferredBlock<IronLanternBlock> LANTERN = block("l490_lantern", IronLanternBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.LANTERN).noOcclusion()
                    .lightLevel(state -> state.getValue(IronLanternBlock.LIT) ? 13 : 0));
    /** A link of the small chains that dangle under the bridges and from the ruins. */
    public static final DeferredBlock<GiantChainBlock> CHAIN = block("l490_chain", GiantChainBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.CHAIN).noOcclusion());
    /** An open shackle on a ring in the paving. */
    public static final DeferredBlock<ShackleBlock> SHACKLE = block("l490_shackle", ShackleBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.CHAIN).noOcclusion().noCollission());

    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {PAVING, PAVING_SLAB, STONE, SLATE, ROCK, RUBBLE, DEAD_GRASS, CHAIN_IRON,
                DEAD_WOOD, RED_LEAVES, RAILING, POST, LANTERN, CHAIN, SHACKLE}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in ModBlocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    private static DeferredBlock<Block> structural(String name, MapColor color, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(name, (Function<BlockBehaviour.Properties, Block>) Block::new, unbreakable(color, sound));
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

    private Level490Blocks() {}
}
