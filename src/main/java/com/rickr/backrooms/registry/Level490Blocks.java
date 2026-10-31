package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.GiantChainBlock;
import com.rickr.backrooms.block.MarkedStoneBlock;
import com.rickr.backrooms.block.IronLanternBlock;
import com.rickr.backrooms.block.ShackleBlock;
import com.rickr.backrooms.block.StatueBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LadderBlock;
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
    /** The spire's pale, grown-looking stone (world/layout/Level490Spire): its vertebrae and ribs. */
    public static final DeferredBlock<Block> SPIRE = structural("l490_spire", MapColor.TERRACOTTA_WHITE, SoundType.BONE_BLOCK);
    /** Darker ridged stone where the spire's vertebrae meet, and its outside ledges. */
    public static final DeferredBlock<Block> SPIRE_JOINT = structural("l490_spire_joint", MapColor.STONE, SoundType.BONE_BLOCK);
    /** Rusted iron ladder bolted up the outside of the spire (climbable, see the climbable tag). */
    public static final DeferredBlock<LadderBlock> LADDER = block("l490_ladder", LadderBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.METAL).noOcclusion());
    /**
     * The spire's stairwell stone with tally marks scratched into it: 1-5 marks, 6 a wall covered
     * in them. Only ever shown to a player stuck in a looping stairwell (world/Level490Loops).
     */
    public static final DeferredBlock<MarkedStoneBlock> MARKED_STONE = block("l490_marked_stone", MarkedStoneBlock::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    /** Stairwell stone with "GO BACK DOWN" scratched into it (world/Level490Loops). */
    public static final DeferredBlock<Block> WORDS = structural("l490_words", MapColor.STONE, SoundType.STONE);
    /** The spire's dark, rain-streaked Gothic masonry: its body, turrets and the walls inside. */
    public static final DeferredBlock<Block> GOTHIC = structural("l490_gothic", MapColor.COLOR_GRAY, SoundType.DEEPSLATE_BRICKS);
    /** Darker carved stone: cornices, tracery, pinnacles, the turrets' pointed roofs. */
    public static final DeferredBlock<Block> GOTHIC_CARVED = structural("l490_gothic_carved", MapColor.COLOR_BLACK, SoundType.DEEPSLATE_BRICKS);
    /** A window with warm candlelight behind leaded glass (gives light). */
    public static final DeferredBlock<Block> WINDOW_LIT = block("l490_window_lit", Block::new,
            unbreakable(MapColor.COLOR_ORANGE, SoundType.GLASS).lightLevel(state -> 10));
    /** A dark window: leaded glass with nothing lit behind it. */
    public static final DeferredBlock<Block> WINDOW_DARK = structural("l490_window_dark", MapColor.COLOR_BLACK, SoundType.GLASS);
    /** A weathered robed statue, two blocks tall, its head worn away. */
    public static final DeferredBlock<StatueBlock> STATUE = block("l490_statue", StatueBlock::new,
            unbreakable(MapColor.TERRACOTTA_WHITE, SoundType.STONE).noOcclusion());

    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {PAVING, PAVING_SLAB, STONE, SLATE, ROCK, RUBBLE, DEAD_GRASS, CHAIN_IRON,
                DEAD_WOOD, RED_LEAVES, RAILING, POST, LANTERN, CHAIN, SHACKLE, SPIRE, SPIRE_JOINT, LADDER, MARKED_STONE, WORDS,
                GOTHIC, GOTHIC_CARVED, WINDOW_LIT, WINDOW_DARK, STATUE}) {
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
