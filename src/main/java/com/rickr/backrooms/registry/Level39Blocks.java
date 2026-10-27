package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.DomeBlock;
import com.rickr.backrooms.block.FieldGrassBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.InvisibleLightBlock;
import com.rickr.backrooms.block.MillWindowBlock;
import com.rickr.backrooms.block.PicketFenceBlock;
import com.rickr.backrooms.block.PoleBlock;
import com.rickr.backrooms.block.SurfaceBlock;
import com.rickr.backrooms.block.SurveyNoteBlock;
import com.rickr.backrooms.block.ThinLayerBlock;
import com.rickr.backrooms.block.TowerWindowBlock;
import com.rickr.backrooms.block.TreeBlock;
import com.rickr.backrooms.block.VaultBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallMountBlock;
import java.util.function.Function;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The blocks of Level 39, "Megalophobia" (world/layout/Level39Layout): the muddy town ground, its
 * fences, poles and trees, the light-blue clapboard of the houses, the colossal mill facade of the
 * superstructure with its glowing windows and the bright empty gallery behind them, and the white
 * marble tunnels under the town.
 *
 * <p>Like every Backrooms block they go through the shared {@link ModBlocks#BLOCKS} register, and
 * their items through {@link ModItems#ITEMS} (so they show up in the creative tab). The mod
 * constructor only calls {@link #bootstrap()}, which makes Java load this class in time.
 *
 * <p>Big surfaces are {@link SurfaceBlock}s: the numbers after the name are the relative chances
 * of each texture version (plain first) and must match tools/gen_assets.py SURFACE_VARIANTS.
 * Textures and models come from tools/anomaly_assets/level39.py and level39_props.py.
 */
public final class Level39Blocks {

    // ================================================================== Town ground
    /** Uneven yard grass, thin and flattened by rain; mud shows through. */
    public static final DeferredBlock<SurfaceBlock> YARD_GRASS = surface("l39_yard_grass", MapColor.COLOR_GREEN, SoundType.GRASS, 6, 2, 2, 1);
    /** Bare wet yard dirt where nothing grows. */
    public static final DeferredBlock<SurfaceBlock> YARD_DIRT = surface("l39_yard_dirt", MapColor.DIRT, SoundType.GRAVEL, 5, 2, 1);
    /** Packed dirt of the paths: the town has no asphalt at all (canon). */
    public static final DeferredBlock<SurfaceBlock> PATH_DIRT = surface("l39_path_dirt", MapColor.DIRT, SoundType.GRAVEL, 6, 2, 2, 1);
    /** Rocky gravel stretches of the paths. */
    public static final DeferredBlock<SurfaceBlock> PATH_GRAVEL = surface("l39_path_gravel", MapColor.STONE, SoundType.GRAVEL, 5, 2, 1);
    /** Dark churned mud: cart ruts and the rims of puddles. */
    public static final DeferredBlock<Block> PATH_MUD = structural("l39_path_mud", MapColor.TERRACOTTA_BROWN, SoundType.MUD);
    /** Subsoil under the yards and paths (seen in the walls of the holes). */
    public static final DeferredBlock<Block> SOIL = structural("l39_soil", MapColor.DIRT, SoundType.ROOTED_DIRT);
    /** Grey packed rock between the soil and the tunnel vaults. */
    public static final DeferredBlock<Block> ROCK = structural("l39_rock", MapColor.STONE, SoundType.STONE);
    /** Tall dry weeds in the yards and along the fences; you walk through them. */
    public static final DeferredBlock<FieldGrassBlock> WEEDS = block("l39_weeds", FieldGrassBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.GRASS).noCollission().noOcclusion().offsetType(BlockBehaviour.OffsetType.XZ));
    /** A rounded, dark, scruffy shrub. */
    public static final DeferredBlock<Block> BUSH = block("l39_bush", Block::new, foliage(MapColor.COLOR_GREEN));
    /** An overgrown privet hedge along some lot fronts instead of a fence. */
    public static final DeferredBlock<Block> HEDGE = block("l39_hedge", Block::new, foliage(MapColor.COLOR_GREEN));
    /** Grass growing down the middle of a little-used path. */
    public static final DeferredBlock<Block> PATH_GRASS = structural("l39_path_grass", MapColor.COLOR_GREEN, SoundType.GRASS);
    // Half blocks of the gently rolling yards (the town undulates in half-block steps, so nobody needs to jump).
    public static final DeferredBlock<SlabBlock> GRASS_SLAB = block("l39_grass_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.GRASS));
    public static final DeferredBlock<SlabBlock> DIRT_SLAB = block("l39_dirt_slab", SlabBlock::new,
            unbreakable(MapColor.DIRT, SoundType.GRAVEL));
    /** Mud half block: cart ruts and puddles (waterlogged, the water lies in it). */
    public static final DeferredBlock<SlabBlock> MUD_SLAB = block("l39_mud_slab", SlabBlock::new,
            unbreakable(MapColor.TERRACOTTA_BROWN, SoundType.MUD));
    // The holes: a lip of turf hanging over the edge, roots dangling into the pit, rubble and
    // layered clay and rock in the shaft walls.
    /** Turf lip over a hole's edge; FACING points out over the hole. */
    public static final DeferredBlock<WallMountBlock> GRASS_OVERHANG = block("l39_grass_overhang",
            props -> new WallMountBlock(props, 0, 9, 0, 16, 16, 16), unbreakable(MapColor.COLOR_GREEN, SoundType.GRASS).noOcclusion());
    /** Roots hanging from a hole's wall; you fall straight through them. */
    public static final DeferredBlock<FieldGrassBlock> ROOTS = block("l39_roots", FieldGrassBlock::new,
            unbreakable(MapColor.DIRT, SoundType.ROOTS).noCollission().noOcclusion());
    public static final DeferredBlock<Block> RUBBLE = structural("l39_rubble", MapColor.STONE, SoundType.GRAVEL);
    public static final DeferredBlock<Block> CLAY = structural("l39_clay", MapColor.CLAY, SoundType.MUD);
    public static final DeferredBlock<Block> ROCK_LAYERED = structural("l39_rock_layered", MapColor.STONE, SoundType.STONE);
    // Yard clutter.
    /** Split firewood stacked against a wall; FACING = its front. */
    public static final DeferredBlock<FurnitureBlock> WOODPILE = block("l39_woodpile",
            props -> new FurnitureBlock(props, Block.box(0, 0, 4, 16, 14, 16)), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** An old wooden rain barrel under a downpipe. */
    public static final DeferredBlock<FurnitureBlock> RAIN_BARREL = block("l39_rain_barrel",
            props -> new FurnitureBlock(props, Block.box(2, 0, 2, 14, 15, 14)), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** Weathered top of a clothesline T-post; the crossbar runs along AXIS, like the line it holds. */
    public static final DeferredBlock<AxisShapeBlock> CLOTHESLINE_POST = block("l39_clothesline_post",
            props -> new AxisShapeBlock(props, Block.box(6.5, 0, 6.5, 9.5, 16, 9.5)), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** Sagging line along AXIS with a few grey sheets and shirts left on it; walk-through. */
    public static final DeferredBlock<AxisShapeBlock> CLOTHESLINE = block("l39_clothesline",
            props -> new AxisShapeBlock(props, Block.box(0, 2, 7, 16, 16, 9), Shapes.empty()),
            unbreakable(MapColor.WOOL, SoundType.WOOL).noOcclusion().noCollission());
    /** Rural mailbox on a post at the path; FACING = the lid side, toward the path. */
    public static final DeferredBlock<FurnitureBlock> MAILBOX = block("l39_mailbox",
            props -> new FurnitureBlock(props, Shapes.or(Block.box(7, 0, 7, 9, 11, 9), Block.box(5, 11, 3, 11, 16, 13))),
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** An old two-wheeled wooden handcart, shafts on the ground; FACING = where the shafts point. */
    public static final DeferredBlock<FurnitureBlock> HANDCART = block("l39_handcart",
            props -> new FurnitureBlock(props, Block.box(1, 0, 1, 15, 11, 15)), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** A rusty wheelbarrow left in the yard; FACING = the wheel end. */
    public static final DeferredBlock<FurnitureBlock> WHEELBARROW = block("l39_wheelbarrow",
            props -> new FurnitureBlock(props, Block.box(2, 0, 0, 14, 9, 16)), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());

    // ================================================================== Fences, poles, towers, trees
    /** Unpainted, weathered picket fence section along AXIS; STYLE = whole, gappy, leaning, broken. */
    public static final DeferredBlock<PicketFenceBlock> PICKET_FENCE = block("l39_picket_fence", PicketFenceBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.WOOD).noOcclusion());
    /** The square post at the corners and gates of a fence. */
    public static final DeferredBlock<PoleBlock> FENCE_POST = block("l39_fence_post", props -> new PoleBlock(props, 2),
            unbreakable(MapColor.COLOR_GRAY, SoundType.WOOD).noOcclusion());
    /** Creosoted wooden utility pole; stack about ten and put a crossarm on top. */
    public static final DeferredBlock<PoleBlock> UTILITY_POLE = block("l39_utility_pole", props -> new PoleBlock(props, 3),
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noOcclusion());
    /**
     * The top of a utility pole: the last bit of pole with a crossarm across AXIS (sticking out a
     * block and a bit each side), its braces and four green glass insulators. The wires between
     * poles are drawn by the client from {@code Level39Layout.polesNear}.
     */
    public static final DeferredBlock<AxisShapeBlock> POLE_CROSSARM = block("l39_pole_crossarm",
            props -> new AxisShapeBlock(props, Block.box(5, 0, 5, 11, 16, 11)),
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noOcclusion());
    /** A pole-mounted transformer can; FACING points away from the pole. */
    public static final DeferredBlock<WallMountBlock> POLE_TRANSFORMER = block("l39_pole_transformer",
            props -> new WallMountBlock(props, 3.5, 1, 7, 12.5, 14, 16), unbreakable(MapColor.COLOR_GRAY, SoundType.METAL).noOcclusion());
    /** Rusty steel lattice of the abandoned telephone towers (an open frame: you see through it). */
    public static final DeferredBlock<Block> LATTICE = block("l39_lattice", Block::new, seeThrough(MapColor.COLOR_GRAY, SoundType.METAL));
    /** Trunks, limbs, branches and twigs of the bare trees (see block/TreeBlock). */
    public static final DeferredBlock<TreeBlock> TREE = block("l39_tree", TreeBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.WOOD).noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** A tangle of fine twigs at the ends of the branches; you push through it. */
    public static final DeferredBlock<FieldGrassBlock> TWIGS = block("l39_twigs", FieldGrassBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.AZALEA_LEAVES).noCollission().noOcclusion());
    /** Sparse, dark leaves of the few trees that still have them. */
    public static final DeferredBlock<Block> DARK_LEAVES = block("l39_dark_leaves", Block::new, foliage(MapColor.COLOR_GREEN));

    // ================================================================== Houses (shell; see Level39Houses)
    /** Light-blue clapboard, the paint of every house in town (canon). */
    public static final DeferredBlock<SurfaceBlock> CLAPBOARD = surface("l39_clapboard", MapColor.COLOR_LIGHT_BLUE, SoundType.WOOD, 6, 2, 2, 1);
    /** The same blue, faded by decades of rain. */
    public static final DeferredBlock<Block> CLAPBOARD_FADED = structural("l39_clapboard_faded", MapColor.COLOR_LIGHT_BLUE, SoundType.WOOD);
    /** Blue paint flaking off grey wood. */
    public static final DeferredBlock<Block> CLAPBOARD_WEATHERED = structural("l39_clapboard_weathered", MapColor.COLOR_LIGHT_GRAY, SoundType.WOOD);
    /** White painted trim: corner boards, frames, fascia. */
    public static final DeferredBlock<Block> WHITE_TRIM = structural("l39_white_trim", MapColor.SNOW, SoundType.WOOD);
    /** Dark asphalt shingles. */
    public static final DeferredBlock<Block> SHINGLES = structural("l39_shingles", MapColor.COLOR_GRAY, SoundType.STONE);
    public static final DeferredBlock<StairBlock> SHINGLE_STAIRS = block("l39_shingle_stairs",
            props -> new StairBlock(SHINGLES.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_GRAY, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> SHINGLE_SLAB = block("l39_shingle_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.STONE));
    /** Old soot-darkened brick of the chimneys. */
    public static final DeferredBlock<Block> CHIMNEY_BRICK = structural("l39_chimney_brick", MapColor.TERRACOTTA_RED, SoundType.STONE);
    /** Fieldstone foundation under the clapboard. */
    public static final DeferredBlock<Block> FOUNDATION = structural("l39_foundation", MapColor.STONE, SoundType.STONE);
    /** 6/6 sash window, dark glass (FACING = outside); its back shows the same window from inside. */
    public static final DeferredBlock<WallDecalBlock> SASH_WINDOW = block("l39_sash_window", WallDecalBlock::new,
            unbreakable(MapColor.SNOW, SoundType.GLASS));
    /** The one dimly lit window (warm lamplight behind a drawn shade) in about one house in twelve. */
    public static final DeferredBlock<WallDecalBlock> SASH_WINDOW_LIT = block("l39_sash_window_lit", WallDecalBlock::new,
            fixture(MapColor.COLOR_ORANGE, SoundType.GLASS, 6));
    /** A panelled front door, painted the house's blue; most are LOCKED (canon: hard to get in). */
    public static final DeferredBlock<HouseDoorBlock> HOUSE_DOOR = block("l39_house_door",
            props -> new HouseDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.WOOD).noOcclusion());

    // ================================================================== Parks and the M.E.G. camp
    /** Slatted park bench on cast-iron legs; FACING = the way you'd sit. */
    public static final DeferredBlock<FurnitureBlock> PARK_BENCH = block("l39_park_bench",
            props -> new FurnitureBlock(props, Shapes.or(Block.box(0, 0, 3, 16, 8, 13), Block.box(0, 8, 11, 16, 16, 13))),
            unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** Turned white post of the gazebo. */
    public static final DeferredBlock<PoleBlock> WHITE_POST = block("l39_white_post", props -> new PoleBlock(props, 2),
            unbreakable(MapColor.SNOW, SoundType.WOOD).noOcclusion());
    /** Grey weathered floorboards (gazebo floor). */
    public static final DeferredBlock<Block> PORCH_PLANKS = structural("l39_porch_planks", MapColor.COLOR_GRAY, SoundType.WOOD);
    /** Rusty steel pipe: the legs of the swing set. */
    public static final DeferredBlock<PoleBlock> RUSTY_POST = block("l39_rusty_post", props -> new PoleBlock(props, 1.5),
            unbreakable(MapColor.COLOR_ORANGE, SoundType.METAL).noOcclusion());
    /** The swing set's top bar along AXIS, at the bottom of its block. */
    public static final DeferredBlock<AxisShapeBlock> SWING_BAR = block("l39_swing_bar",
            props -> new AxisShapeBlock(props, Block.box(0, 0, 6.5, 16, 3, 9.5)),
            unbreakable(MapColor.COLOR_ORANGE, SoundType.METAL).noOcclusion());
    /** A swing hanging still from the bar above: two chains reach up into the bar's block. */
    public static final DeferredBlock<FurnitureBlock> SWING = block("l39_swing",
            props -> new FurnitureBlock(props, Block.box(2, 3, 5, 14, 5, 11)),
            unbreakable(MapColor.COLOR_ORANGE, SoundType.CHAIN).noOcclusion().noCollission());
    /** Olive-drab tent canvas of the M.E.G. survey camp. */
    public static final DeferredBlock<Block> CANVAS = structural("l39_canvas", MapColor.COLOR_GREEN, SoundType.WOOL);
    public static final DeferredBlock<StairBlock> CANVAS_STAIRS = block("l39_canvas_stairs",
            props -> new StairBlock(CANVAS.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_GREEN, SoundType.WOOL));
    /** Stencilled M.E.G. equipment crate (empty: supplies don't last here). */
    public static final DeferredBlock<Block> MEG_CRATE = structural("l39_meg_crate", MapColor.COLOR_GREEN, SoundType.WOOD);
    /** A dead camp lantern on the ground; FACING turns it. */
    public static final DeferredBlock<FurnitureBlock> DEAD_LAMP = block("l39_dead_lamp",
            props -> new FurnitureBlock(props, Block.box(4.5, 0, 4.5, 11.5, 12, 11.5)),
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** A clipboard of survey notes; right-click to read. */
    public static final DeferredBlock<SurveyNoteBlock> SURVEY_NOTE = block("l39_survey_note", SurveyNoteBlock::new,
            unbreakable(MapColor.SNOW, SoundType.WOOD).noCollission().noOcclusion());

    // ================================================================== The superstructure
    /** Old red mill brick of the facade, piers and stair towers. */
    public static final DeferredBlock<SurfaceBlock> MILL_BRICK = surface("l39_mill_brick", MapColor.TERRACOTTA_RED, SoundType.STONE, 6, 2, 2, 1);
    /** The same brick under the windows, blackened by coal smoke. */
    public static final DeferredBlock<Block> MILL_BRICK_SOOTY = structural("l39_mill_brick_sooty", MapColor.TERRACOTTA_BLACK, SoundType.STONE);
    /** Brick of the upper storeys, nearly black with a century of smoke. */
    public static final DeferredBlock<Block> MILL_BRICK_BLACK = structural("l39_mill_brick_black", MapColor.COLOR_BLACK, SoundType.STONE);
    /** The solid inside of the superstructure, behind everything you can see. */
    public static final DeferredBlock<Block> MASS = structural("l39_mass", MapColor.TERRACOTTA_BLACK, SoundType.STONE);
    /** Tarred flat roof on top of the band. */
    public static final DeferredBlock<Block> ROOF_TAR = structural("l39_roof_tar", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Grey granite of the sills, lintels and the cornice. */
    public static final DeferredBlock<Block> GRANITE = structural("l39_granite", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<StairBlock> GRANITE_STAIRS = block("l39_granite_stairs",
            props -> new StairBlock(GRANITE.get().defaultBlockState(), props), unbreakable(MapColor.STONE, SoundType.STONE));
    /**
     * A piece of a mill window (see block/MillWindowBlock): 12-over-12 panes in a heavy dark frame,
     * see-through glass with coal soot low down. Light 11; the brightness you see through it comes
     * from the gallery behind.
     */
    public static final DeferredBlock<MillWindowBlock> MILL_WINDOW = block("l39_mill_window", MillWindowBlock::new,
            glass(MapColor.SNOW, 11));
    /** The tall round-headed slit windows of the stair towers. */
    public static final DeferredBlock<TowerWindowBlock> TOWER_WINDOW = block("l39_tower_window", TowerWindowBlock::new,
            glass(MapColor.SNOW, 11));
    /** A ground-storey window boarded over with grey planks. */
    public static final DeferredBlock<Block> BOARDED_WINDOW = structural("l39_boarded_window", MapColor.COLOR_GRAY, SoundType.WOOD);
    /** A ground-storey window bricked up with newer, mismatched brick. */
    public static final DeferredBlock<Block> BRICKED_WINDOW = structural("l39_bricked_window", MapColor.TERRACOTTA_RED, SoundType.STONE);
    /** Seamless white panels of the futuristic gallery behind the glass (always drawn bright). */
    public static final DeferredBlock<Block> GALLERY_PANEL = block("l39_gallery_panel", Block::new,
            fixture(MapColor.SNOW, SoundType.STONE, 0));
    /** Its ceiling light panels. */
    public static final DeferredBlock<Block> GALLERY_LIGHT = block("l39_gallery_light", Block::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 15));
    /** Its polished pale floor. */
    public static final DeferredBlock<Block> GALLERY_FLOOR = block("l39_gallery_floor", Block::new,
            fixture(MapColor.SNOW, SoundType.STONE, 0));

    // ================================================================== The tunnels
    /** White marble with blue-grey veining: the vault (it damps sound, canon). */
    public static final DeferredBlock<SurfaceBlock> MARBLE = surface("l39_marble", MapColor.QUARTZ, SoundType.STONE, 6, 2, 2, 1);
    /** Marble of the stagnant (Hydrolitis) stretches: yellowed, a greenish bloom creeping up. */
    public static final DeferredBlock<Block> MARBLE_STAINED = structural("l39_marble_stained", MapColor.TERRACOTTA_WHITE, SoundType.STONE);
    /** Small cream tiles of the walkways along the water. */
    public static final DeferredBlock<Block> TILE_RIM = structural("l39_tile_rim", MapColor.SAND, SoundType.STONE);
    /** The rim's edge tile with the dark-blue border line; FACING points toward the water. */
    public static final DeferredBlock<WallDecalBlock> TILE_EDGE = block("l39_tile_edge", WallDecalBlock::new,
            unbreakable(MapColor.SAND, SoundType.STONE));
    /** Pale tiles at the bottom of the water. */
    public static final DeferredBlock<Block> TILE_FLOOR = structural("l39_tile_floor", MapColor.QUARTZ, SoundType.STONE);
    /** Smooth pieces of the barrel vault over the runs (see block/VaultBlock). */
    public static final DeferredBlock<VaultBlock> VAULT = block("l39_vault", VaultBlock::new,
            unbreakable(MapColor.QUARTZ, SoundType.STONE).noOcclusion());
    /** Pieces of the round wall and dome of the junction chambers (see block/DomeBlock). */
    public static final DeferredBlock<DomeBlock> DOME = block("l39_dome", DomeBlock::new,
            unbreakable(MapColor.QUARTZ, SoundType.STONE).noOcclusion());
    /** The plain white panelled doors in the round-arched wall at a dead end (always locked). */
    public static final DeferredBlock<HouseDoorBlock> PANEL_DOOR = block("l39_panel_door",
            props -> new HouseDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.SNOW, SoundType.WOOD).noOcclusion());
    /** Cast-iron hatch at the top of an access shaft, in a town path. Opens by hand. */
    public static final DeferredBlock<TrapDoorBlock> HATCH = block("l39_hatch",
            props -> new TrapDoorBlock(BlockSetType.COPPER, props), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Iron rungs set into the shaft wall (climbable like a ladder). */
    public static final DeferredBlock<LadderBlock> RUNGS = block("l39_rungs", LadderBlock::new,
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** An "invisible orb": light without a source, under the vault every ten blocks. */
    public static final DeferredBlock<InvisibleLightBlock> ORB_LIGHT = block("l39_orb_light", InvisibleLightBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).noCollission().noOcclusion().replaceable()
                    .strength(-1.0f, 3_600_000.0f).sound(SoundType.EMPTY).noLootTable().lightLevel(state -> 15)
                    .isValidSpawn((state, level, pos, type) -> false).pushReaction(PushReaction.DESTROY));
    /** Greenish film floating on stagnant water along the rims. */
    public static final DeferredBlock<ThinLayerBlock> STAGNANT_FILM = block("l39_stagnant_film", ThinLayerBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.EMPTY).noCollission().noOcclusion());
    /** Rusty iron lining of the drains in the vault. */
    public static final DeferredBlock<Block> DRAIN_LINING = structural("l39_drain_lining", MapColor.COLOR_BROWN, SoundType.METAL);

    static {
        // Block items so builders can use every Level 39 block in creative (doors need the tall item).
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {
                YARD_GRASS, YARD_DIRT, PATH_DIRT, PATH_GRAVEL, PATH_MUD, SOIL, ROCK, WEEDS, BUSH,
                PICKET_FENCE, FENCE_POST, UTILITY_POLE, POLE_CROSSARM, POLE_TRANSFORMER, LATTICE, TREE, TWIGS, DARK_LEAVES,
                HEDGE, PATH_GRASS, GRASS_SLAB, DIRT_SLAB, MUD_SLAB, GRASS_OVERHANG, ROOTS, RUBBLE, CLAY, ROCK_LAYERED,
                WOODPILE, RAIN_BARREL, CLOTHESLINE_POST, CLOTHESLINE, MAILBOX, HANDCART, WHEELBARROW,
                CLAPBOARD, CLAPBOARD_FADED, CLAPBOARD_WEATHERED, WHITE_TRIM, SHINGLES, SHINGLE_STAIRS, SHINGLE_SLAB, CHIMNEY_BRICK,
                FOUNDATION, SASH_WINDOW, SASH_WINDOW_LIT,
                PARK_BENCH, WHITE_POST, PORCH_PLANKS, RUSTY_POST, SWING_BAR, SWING, CANVAS, CANVAS_STAIRS, MEG_CRATE, DEAD_LAMP, SURVEY_NOTE,
                MILL_BRICK, MILL_BRICK_SOOTY, MILL_BRICK_BLACK, MASS, ROOF_TAR, GRANITE, GRANITE_STAIRS, MILL_WINDOW, TOWER_WINDOW,
                BOARDED_WINDOW, BRICKED_WINDOW,
                GALLERY_PANEL, GALLERY_LIGHT, GALLERY_FLOOR,
                MARBLE, MARBLE_STAINED, TILE_RIM, TILE_EDGE, TILE_FLOOR, VAULT, DOME, HATCH, RUNGS, ORB_LIGHT, STAGNANT_FILM, DRAIN_LINING}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
        ModItems.ITEMS.registerItem("l39_house_door", props -> new DoubleHighBlockItem(HOUSE_DOOR.get(), props), new Item.Properties());
        ModItems.ITEMS.registerItem("l39_panel_door", props -> new DoubleHighBlockItem(PANEL_DOOR.get(), props), new Item.Properties());
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

    private static DeferredBlock<SurfaceBlock> surface(String name, MapColor color, SoundType sound, int... weights) {
        return ModBlocks.BLOCKS.registerBlock(name, props -> new SurfaceBlock(props, weights), unbreakable(color, sound));
    }

    /** A glowing block: vanilla light, and its faces always render full-bright. */
    private static BlockBehaviour.Properties fixture(MapColor color, SoundType sound, int light) {
        return unbreakable(color, sound)
                .lightLevel(state -> light)
                .emissiveRendering((state, level, pos) -> true);
    }

    /** Leaves and shrubs: solid to walk into, but you see through the gaps (neighbours stay drawn). */
    private static BlockBehaviour.Properties foliage(MapColor color) {
        return unbreakable(color, SoundType.GRASS).noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    /** See-through window glass that gives off some light (not drawn full-bright: the muntins stay dark). */
    private static BlockBehaviour.Properties glass(MapColor color, int light) {
        return unbreakable(color, SoundType.GLASS).noOcclusion()
                .lightLevel(state -> light)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    /** An open steel frame: solid, but light and the eye go straight through. */
    private static BlockBehaviour.Properties seeThrough(MapColor color, SoundType sound) {
        return unbreakable(color, sound).noOcclusion()
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

    private Level39Blocks() {}
}
