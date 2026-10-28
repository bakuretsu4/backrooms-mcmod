package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.Level227AttachedPlantBlock;
import com.rickr.backrooms.block.Level227FacingBlock;
import com.rickr.backrooms.block.Level227GammaPaliBlock;
import com.rickr.backrooms.block.Level227HangingBlock;
import com.rickr.backrooms.block.Level227LeavesBlock;
import com.rickr.backrooms.block.Level227LestronBlock;
import com.rickr.backrooms.block.Level227PaletteBlock;
import com.rickr.backrooms.block.Level227PlantBlock;
import com.rickr.backrooms.block.Level227RingsBlock;
import com.rickr.backrooms.block.Level227TallPlantBlock;
import com.rickr.backrooms.block.ThinLayerBlock;
import com.rickr.backrooms.block.WallMountBlock;
import java.util.function.Function;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The blocks of Level 227, "Megaflora": the planet-sized upside-down tree over the void. Its wet,
 * deeply ridged bark and pale heartwood, the living forest floor on top of its branches (moss, root
 * soil, forest grass), the ordinary-but-huge trees of that forest and their undergrowth, and the
 * flora that kills: GammaPali, the Palette, and Lestron, the herb.
 *
 * <p>Placed by world/layout/Level227Layout. Textures, models and blockstates come from
 * tools/anomaly_assets/level227_flora.py. Most textures are 64 px (32 px for small plants), half
 * the size of other levels' wall textures, because every block texture shares one atlas.
 *
 * <p>Blocks with AXIS follow the grain: the direction the branch (or trunk, or log) runs. Many
 * blocks pick one of several textures or turns at random per position (in their blockstate
 * files), so the bark of a branch thousands of blocks long never repeats in a grid.
 *
 * <p>Like every Backrooms block they go through the shared {@link ModBlocks#BLOCKS} register and
 * cannot be broken. The one thing you can take is Lestron (right-click it).
 */
public final class Level227Blocks {

    // ================================================================== The giant tree's wood
    /** Wet, dark bark with deep ridges along AXIS: the sides and undersides of every branch and the trunk. */
    public static final DeferredBlock<RotatedPillarBlock> BARK = block("l227_bark", RotatedPillarBlock::new,
            unbreakable(MapColor.TERRACOTTA_BLACK, SoundType.WOOD));
    /** Bark with vivid moss on its top face and moss creeping down its sides: the edges of branch tops. */
    public static final DeferredBlock<RotatedPillarBlock> BARK_MOSSY = block("l227_bark_mossy", RotatedPillarBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.WOOD));
    /** A half block of bark, to smooth the steps of a curving branch. */
    public static final DeferredBlock<SlabBlock> BARK_SLAB = block("l227_bark_slab", SlabBlock::new,
            unbreakable(MapColor.TERRACOTTA_BLACK, SoundType.WOOD));
    /** Pale reddish heartwood: the inside of the branches (hollows, caves, broken ends). */
    public static final DeferredBlock<Block> HEARTWOOD = block("l227_heartwood", Block::new,
            unbreakable(MapColor.TERRACOTTA_ORANGE, SoundType.WOOD));
    /** The cut face of a broken branch or stump: growth rings across AXIS, banded per RINGS (see the block class). */
    public static final DeferredBlock<Level227RingsBlock> GROWTH_RINGS = block("l227_growth_rings", Level227RingsBlock::new,
            unbreakable(MapColor.TERRACOTTA_ORANGE, SoundType.WOOD));
    /** A knothole: a dark hollow ringed by swirled wood on its FACING side, bark on the rest. */
    public static final DeferredBlock<Level227FacingBlock> KNOT = block("l227_knot", Level227FacingBlock::new,
            unbreakable(MapColor.TERRACOTTA_BLACK, SoundType.WOOD));
    /** Bark with a seep of amber resin that glows faintly (light 6; only the resin is drawn bright). */
    public static final DeferredBlock<Block> RESIN = block("l227_resin", Block::new,
            unbreakable(MapColor.COLOR_ORANGE, SoundType.WOOD).lightLevel(state -> 6));

    // ================================================================== The forest floor
    /** Dark rooted earth full of fine roots: the soil on the branch tops, under the moss and grass. */
    public static final DeferredBlock<Block> ROOT_SOIL = block("l227_root_soil", Block::new,
            unbreakable(MapColor.DIRT, SoundType.ROOTED_DIRT));
    /** Thick, vivid, wet moss. */
    public static final DeferredBlock<Block> MOSS = block("l227_moss", Block::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.MOSS));
    public static final DeferredBlock<SlabBlock> MOSS_SLAB = block("l227_moss_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.MOSS));
    /** A thin skin of moss lying on anything (walk-through). */
    public static final DeferredBlock<ThinLayerBlock> MOSS_CARPET = block("l227_moss_carpet", ThinLayerBlock::new,
            plant(MapColor.COLOR_GREEN, SoundType.MOSS_CARPET));
    /** Lush forest grass over root soil (a full block). */
    public static final DeferredBlock<Block> FOREST_GRASS = block("l227_forest_grass", Block::new,
            unbreakable(MapColor.GRASS, SoundType.GRASS));
    public static final DeferredBlock<SlabBlock> FOREST_GRASS_SLAB = block("l227_forest_grass_slab", SlabBlock::new,
            unbreakable(MapColor.GRASS, SoundType.GRASS));
    /** Bleached grey-white ash: ground scorched by a sunbeam from the Sun over the Void. */
    public static final DeferredBlock<Block> ASH = block("l227_ash", Block::new,
            unbreakable(MapColor.QUARTZ, SoundType.SAND));

    // ================================================================== The forest's trees
    /** Trunk wood of the forest trees: pale grey-brown bark blotched with lichen, along AXIS. */
    public static final DeferredBlock<RotatedPillarBlock> TREE_LOG = block("l227_tree_log", RotatedPillarBlock::new,
            unbreakable(MapColor.TERRACOTTA_LIGHT_GRAY, SoundType.WOOD));
    /** The same trunk wrapped in moss and climbing plants. */
    public static final DeferredBlock<RotatedPillarBlock> TREE_LOG_MOSSY = block("l227_tree_log_mossy", RotatedPillarBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.WOOD));
    /** A buttress root: a 6 px thick vertical fin of trunk wood running along AXIS (x or z) out from a trunk. */
    public static final DeferredBlock<AxisShapeBlock> BUTTRESS = block("l227_buttress",
            props -> new AxisShapeBlock(props, Block.box(0, 0, 5, 16, 16, 11)),
            unbreakable(MapColor.TERRACOTTA_LIGHT_GRAY, SoundType.WOOD).noOcclusion());
    /** The big-leaf canopy: broad glossy tropical leaves (cutout, sways; solid to stand on). */
    public static final DeferredBlock<Level227LeavesBlock> LEAVES = block("l227_leaves", Level227LeavesBlock::new,
            foliage(MapColor.PLANT));
    /** Fern-like foliage, the drooping fronds of the wiki drawing (cutout, sways; solid to stand on). */
    public static final DeferredBlock<Level227LeavesBlock> FERN_LEAVES = block("l227_fern_leaves", Level227LeavesBlock::new,
            foliage(MapColor.PLANT));
    /** Fern fronds trailing down from fern foliage; TIP = the lowest one. */
    public static final DeferredBlock<Level227HangingBlock> HANGING_FRONDS = block("l227_hanging_fronds",
            props -> new Level227HangingBlock(props, 7, false), plant(MapColor.PLANT, SoundType.AZALEA_LEAVES));

    // ================================================================== Undergrowth
    /** Tall grass, two blocks high (HALF lower/upper): it hides the giant Venus flytraps. */
    public static final DeferredBlock<Level227TallPlantBlock> TALL_GRASS = block("l227_tall_grass", Level227TallPlantBlock::new,
            plant(MapColor.PLANT, SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ));
    /** A giant fern, two blocks (HALF lower/upper) whose fronds arch out to about 2.5 blocks high and wide. */
    public static final DeferredBlock<Level227TallPlantBlock> GIANT_FERN = block("l227_giant_fern", Level227TallPlantBlock::new,
            plant(MapColor.PLANT, SoundType.GRASS));
    /** Low undergrowth, KIND 0 broadleaf seedlings, 1 elephant ear, 2 low fern, 3 spiny palm sprout. */
    public static final DeferredBlock<Level227PlantBlock> UNDERGROWTH = block("l227_undergrowth", Level227PlantBlock::new,
            plant(MapColor.PLANT, SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ));
    /** Flowers of no known species, KIND 0 pale drooping bells, 1 red spathe, 2 blue star clusters, 3 the orange "eye". */
    public static final DeferredBlock<Level227PlantBlock> FLOWER = block("l227_flower", Level227PlantBlock::new,
            plant(MapColor.PLANT, SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ));
    /** A shelf of bracket fungus on bark; FACING points out of the wall. Solid: you can stand on the big ones. */
    public static final DeferredBlock<WallMountBlock> BRACKET_FUNGUS = block("l227_bracket_fungus",
            props -> new WallMountBlock(props, 1, 7, 4, 15, 11, 16),
            unbreakable(MapColor.TERRACOTTA_YELLOW, SoundType.FUNGUS).noOcclusion());
    /** Moss hanging in curtains under the branches; TIP = the lowest one. */
    public static final DeferredBlock<Level227HangingBlock> HANGING_MOSS = block("l227_hanging_moss",
            props -> new Level227HangingBlock(props, 6, false), plant(MapColor.COLOR_GREEN, SoundType.MOSS_CARPET));
    /** A liana (woody vine) dropping from the canopy; climbable like a ladder. TIP = the lowest one. */
    public static final DeferredBlock<Level227HangingBlock> LIANA = block("l227_liana",
            props -> new Level227HangingBlock(props, 2.5, true), plant(MapColor.COLOR_BROWN, SoundType.VINE));
    /** A huge fallen leaf lying flat on the ground (walk-through). */
    public static final DeferredBlock<ThinLayerBlock> FALLEN_LEAF = block("l227_fallen_leaf", ThinLayerBlock::new,
            plant(MapColor.PLANT, SoundType.AZALEA_LEAVES));
    /** Bromeliads and orchids growing on the bark; FACING points away from the surface (any of six ways). */
    public static final DeferredBlock<Level227AttachedPlantBlock> EPIPHYTE = block("l227_epiphyte",
            props -> new Level227AttachedPlantBlock(props, 6, 10), plant(MapColor.PLANT, SoundType.AZALEA_LEAVES));

    // ================================================================== The flora that kills (and one herb)
    /** Lestron: purple sage-like herb at the branch edges; right-click for the item. HARVESTED = cut back. */
    public static final DeferredBlock<Level227LestronBlock> LESTRON = block("l227_lestron", Level227LestronBlock::new,
            plant(MapColor.COLOR_PURPLE, SoundType.GRASS).randomTicks());
    /**
     * GammaPali: a small glowing plant on bark (FACING away from the bark, any of six ways). Light 9;
     * its glow is a toxic gas (world/Level227Hazards).
     */
    public static final DeferredBlock<Level227GammaPaliBlock> GAMMAPALI = block("l227_gammapali", Level227GammaPaliBlock::new,
            plant(MapColor.COLOR_LIGHT_GREEN, SoundType.FUNGUS).lightLevel(state -> 9));
    /**
     * The Palette: looks like bark (AXIS as bark) and drops on players passing under it.
     * FALLEN = it already fell (it lies on the ground and is harmless).
     */
    public static final DeferredBlock<Level227PaletteBlock> PALETTE = block("l227_palette", Level227PaletteBlock::new,
            unbreakable(MapColor.TERRACOTTA_BLACK, SoundType.WOOD));

    static {
        // Block items so builders can use every Level 227 block in creative (two-high plants need the tall item).
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {
                BARK, BARK_MOSSY, BARK_SLAB, HEARTWOOD, GROWTH_RINGS, KNOT, RESIN,
                ROOT_SOIL, MOSS, MOSS_SLAB, MOSS_CARPET, FOREST_GRASS, FOREST_GRASS_SLAB, ASH,
                TREE_LOG, TREE_LOG_MOSSY, BUTTRESS, LEAVES, FERN_LEAVES, HANGING_FRONDS,
                UNDERGROWTH, FLOWER, BRACKET_FUNGUS, HANGING_MOSS, LIANA, FALLEN_LEAF, EPIPHYTE,
                LESTRON, GAMMAPALI, PALETTE}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
        ModItems.ITEMS.registerItem("l227_tall_grass", props -> new DoubleHighBlockItem(TALL_GRASS.get(), props), new Item.Properties());
        ModItems.ITEMS.registerItem("l227_giant_fern", props -> new DoubleHighBlockItem(GIANT_FERN.get(), props), new Item.Properties());
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in Level39Blocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    /** Leaves: solid to walk on, but you see through the gaps (neighbours stay drawn). */
    private static BlockBehaviour.Properties foliage(MapColor color) {
        return unbreakable(color, SoundType.AZALEA_LEAVES).noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    /** Plants and thin layers: no collision, light passes, nothing spawns on them. */
    private static BlockBehaviour.Properties plant(MapColor color, SoundType sound) {
        return unbreakable(color, sound).noCollission().noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
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

    private Level227Blocks() {}
}
