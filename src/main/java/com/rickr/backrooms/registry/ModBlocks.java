package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.CheckpointRadioBlock;
import com.rickr.backrooms.block.DuctBlock;
import com.rickr.backrooms.block.CubiclePartitionBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.block.FloodableSlabBlock;
import com.rickr.backrooms.block.FloorArrowBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.HandrailBlock;
import com.rickr.backrooms.block.HighwaySignBlock;
import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.MarkingBlock;
import com.rickr.backrooms.block.PillarNumberBlock;
import com.rickr.backrooms.block.MusicDoorBlock;
import com.rickr.backrooms.block.OfficePanelBlock;
import com.rickr.backrooms.block.OfficeWindowBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.PoleBlock;
import com.rickr.backrooms.block.PoolLadderBlock;
import com.rickr.backrooms.block.PoolLampBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.block.ShallowWaterBlock;
import com.rickr.backrooms.block.StreetSignBlock;
import com.rickr.backrooms.block.StreetlampHeadBlock;
import com.rickr.backrooms.block.SunlitVoidBlock;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.SupplyKind;
import com.rickr.backrooms.block.ThinLayerBlock;
import com.rickr.backrooms.block.TileCurveBlock;
import com.rickr.backrooms.block.VendingMachineBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.block.WaterCoolerBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All Backrooms blocks. Structural blocks are unbreakable in survival (strength -1, like bedrock)
 * so nobody can tunnel straight through the maze: the only way out is to find an exit.
 *
 * <p>Light fixtures use {@code emissiveRendering}, so their own faces always draw full-bright
 * white, and only a modest vanilla light level: the real light pools come from the dynamic
 * lights on the client.
 *
 * <p>Footstep sounds: carpet floors are {@link SoundType#WOOL}, concrete and tile floors
 * {@link SoundType#STONE}.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Backrooms.MOD_ID);

    // Level 0: the Lobby
    public static final DeferredBlock<Block> YELLOW_WALLPAPER = structural("yellow_wallpaper", MapColor.COLOR_YELLOW, SoundType.WOOD);
    /** The bottom wall block, with the thin light-grey baseboard strip. */
    public static final DeferredBlock<Block> YELLOW_WALLPAPER_BASEBOARD = structural("yellow_wallpaper_baseboard", MapColor.COLOR_YELLOW, SoundType.WOOD);
    public static final DeferredBlock<Block> LOBBY_CARPET = structural("lobby_carpet", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL);
    public static final DeferredBlock<FloorArrowBlock> LOBBY_CARPET_ARROW = BLOCKS.registerBlock("lobby_carpet_arrow", FloorArrowBlock::new,
            unbreakable(MapColor.TERRACOTTA_YELLOW, SoundType.WOOL));
    public static final DeferredBlock<RugBlock> RUG = BLOCKS.registerBlock("rug", RugBlock::new,
            unbreakable(MapColor.COLOR_RED, SoundType.WOOL));
    public static final DeferredBlock<Block> CEILING_TILE = structural("ceiling_tile", MapColor.SAND, SoundType.WOOD);
    public static final DeferredBlock<Block> FLUORESCENT_LIGHT = BLOCKS.registerBlock("fluorescent_light", Block::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 12));
    public static final DeferredBlock<Block> FLUORESCENT_LIGHT_OFF = structural("fluorescent_light_off", MapColor.COLOR_LIGHT_GRAY, SoundType.GLASS);
    public static final DeferredBlock<WallDecalBlock> WALL_ARROW_LEFT = decal("wall_arrow_left");
    public static final DeferredBlock<WallDecalBlock> WALL_ARROW_RIGHT = decal("wall_arrow_right");
    public static final DeferredBlock<WallDecalBlock> WALL_HANDPRINT = decal("wall_handprint");
    public static final DeferredBlock<WallDecalBlock> WALL_WINDOW_DRAWING = decal("wall_window_drawing");
    public static final DeferredBlock<WallDoorDrawingBlock> WALL_DOOR_DRAWING = BLOCKS.registerBlock("wall_door_drawing", WallDoorDrawingBlock::new,
            unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD));
    /** Round red alarm lamp in the "front office" (also Level 1). */
    public static final DeferredBlock<WallLampBlock> RED_ALARM_LAMP = BLOCKS.registerBlock("red_alarm_lamp",
            props -> new WallLampBlock(props, 6, 5, 11, 4), wallFixture(MapColor.COLOR_RED, 7));
    /** The wooden door from the Lobby into the exit stairwell. A real door: it opens and closes. */
    public static final DeferredBlock<DoorBlock> LOBBY_DOOR = BLOCKS.registerBlock("lobby_door",
            props -> new DoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());

    // Exit stairwells (Level 0 and Level 1)
    public static final DeferredBlock<Block> STAIRWELL_WALL = structural("stairwell_wall", MapColor.QUARTZ, SoundType.STONE);
    public static final DeferredBlock<IronBarsBlock> CHAIN_LINK = BLOCKS.registerBlock("chain_link", IronBarsBlock::new,
            unbreakable(MapColor.METAL, SoundType.CHAIN).noOcclusion());

    // Level 1: Habitable Zone (underground parking garage)
    public static final DeferredBlock<Block> CONCRETE_WALL = structural("concrete_wall", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<Block> CONCRETE_FLOOR = structural("concrete_floor", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<Block> WET_CONCRETE_FLOOR = structural("wet_concrete_floor", MapColor.COLOR_GRAY, SoundType.STONE);
    /** Pillar block with the darker base band; only used at floor + 1. */
    public static final DeferredBlock<Block> GARAGE_PILLAR = structural("garage_pillar", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<PillarNumberBlock> PILLAR_NUMBER = BLOCKS.registerBlock("pillar_number", PillarNumberBlock::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    public static final DeferredBlock<Block> CEILING_BEAM = structural("ceiling_beam", MapColor.COLOR_GRAY, SoundType.STONE);
    /** Vertical tube light mounted on pillar faces and stairwell walls. */
    public static final DeferredBlock<WallLampBlock> WAREHOUSE_LIGHT = BLOCKS.registerBlock("warehouse_light",
            props -> new WallLampBlock(props, 4, 0, 16, 3), wallFixture(MapColor.SNOW, 12));
    /** Horizontal tube strip hanging under the garage ceiling. */
    public static final DeferredBlock<CeilingTubeBlock> CEILING_TUBE = BLOCKS.registerBlock("ceiling_tube", CeilingTubeBlock::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 12).noCollission().noOcclusion());
    /** Exit stairwells on Level 0 and Level 1. */
    public static final DeferredBlock<StairBlock> CONCRETE_STAIRS = BLOCKS.registerBlock("concrete_stairs",
            props -> new StairBlock(CONCRETE_FLOOR.get().defaultBlockState(), props), unbreakable(MapColor.STONE, SoundType.STONE));

    // Level 2: Pipe Dreams. Plain painted concrete; the pipes are real 3D parts bolted to one wall.
    public static final DeferredBlock<Block> PIPE_WALL = structural("pipe_wall", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE);
    public static final DeferredBlock<Block> PIPE_FLOOR = structural("pipe_floor", MapColor.COLOR_LIGHT_GRAY, SoundType.STONE);
    public static final DeferredBlock<Block> PIPE_CEILING = structural("pipe_ceiling", MapColor.COLOR_GRAY, SoundType.STONE);
    /** A dark metal drain grate set into the floor. */
    public static final DeferredBlock<Block> FLOOR_GRATE = structural("floor_grate", MapColor.COLOR_GRAY, SoundType.METAL);
    /** Rack of thin rusty pipes high on the wall (BRACKET = with a shelf bracket). */
    public static final DeferredBlock<PipeRackBlock> RUSTY_PIPE = BLOCKS.registerBlock("rusty_pipe", PipeRackBlock::new,
            unbreakable(MapColor.COLOR_ORANGE, SoundType.METAL).noOcclusion());
    /** One big pale duct along the bottom of the wall (FLANGE = with a bolted ring). */
    public static final DeferredBlock<DuctBlock> PALE_DUCT = BLOCKS.registerBlock("pale_duct", DuctBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL).noOcclusion());
    /** A red valve wheel on a short stem from the wall. */
    public static final DeferredBlock<WallMountBlock> PIPE_VALVE = BLOCKS.registerBlock("pipe_valve",
            props -> new WallMountBlock(props, 3, 3, 8, 13, 13, 16), unbreakable(MapColor.COLOR_RED, SoundType.METAL).noOcclusion());
    /** Ceiling strip; now only lights the middle of the Level 0/1 exit stairwells. */
    public static final DeferredBlock<Block> PIPE_LIGHT = BLOCKS.registerBlock("pipe_light", Block::new,
            fixture(MapColor.SNOW, SoundType.METAL, 11));
    /** Small caged bulb on the plain wall of the Level 2 corridors, just under the ceiling. */
    public static final DeferredBlock<WallLampBlock> BULKHEAD_LAMP = BLOCKS.registerBlock("bulkhead_lamp",
            props -> new WallLampBlock(props, 6, 3, 13, 6), wallFixture(MapColor.SNOW, 10));

    // The Poolrooms: big white tiles, dark tiles for the drain and lane lines, a tile floor with
    // moving light ripples (caustics) under all the water, daylight skylights and underwater lamps.
    public static final DeferredBlock<Block> POOL_TILE = structural("pool_tile", MapColor.SNOW, SoundType.STONE);
    public static final DeferredBlock<Block> POOL_TILE_DARK = structural("pool_tile_dark", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Tile floor under the water; its texture is animated (pool_floor.png.mcmeta). */
    public static final DeferredBlock<Block> POOL_FLOOR = structural("pool_floor", MapColor.COLOR_CYAN, SoundType.STONE);
    /** Frosted daylight glass in the atrium ceilings. */
    public static final DeferredBlock<Block> POOL_SKYLIGHT = BLOCKS.registerBlock("pool_skylight", Block::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 15));
    /** Underwater lamp set into a pool wall; FACING is the lens side, pointing into the water. */
    public static final DeferredBlock<PoolLampBlock> POOL_LAMP = BLOCKS.registerBlock("pool_lamp", PoolLampBlock::new,
            fixture(MapColor.COLOR_CYAN, SoundType.GLASS, 14));
    /**
     * Ankle-deep water (Level 37's flooded floors): an invisible, walk-through marker in the
     * feet-level cell; the pool water renderer draws its surface 0.375 above the floor.
     */
    public static final DeferredBlock<ShallowWaterBlock> SHALLOW_WATER = BLOCKS.registerBlock(
            "shallow_water", ShallowWaterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().noOcclusion().replaceable()
                    .strength(-1.0f, 3_600_000.0f).sound(SoundType.EMPTY).noLootTable()
                    .isValidSpawn((state, level, pos, type) -> false).pushReaction(PushReaction.DESTROY));

    // Level 37 "Sublimity": fine cream-white mosaic tile on every surface (wall, floor, ceiling
    // and a dark sea-green tile for the deep pit shafts), smooth curved tile pieces for pillars,
    // round rooms and arches, floating stair treads, chrome ladders and rails, and the glowing
    // "sunlit void" seen through every skylight and window.
    public static final DeferredBlock<Block> L37_TILE_WALL = structural("l37_tile_wall", MapColor.QUARTZ, SoundType.STONE);
    public static final DeferredBlock<Block> L37_TILE_FLOOR = structural("l37_tile_floor", MapColor.QUARTZ, SoundType.STONE);
    public static final DeferredBlock<Block> L37_TILE_CEILING = structural("l37_tile_ceiling", MapColor.QUARTZ, SoundType.STONE);
    public static final DeferredBlock<Block> L37_TILE_DEEP = structural("l37_tile_deep", MapColor.COLOR_GREEN, SoundType.STONE);
    /** Curved tile: one piece of a circle (see block/CurveShape). OBJ models; lets light through. */
    public static final DeferredBlock<TileCurveBlock> L37_TILE_CURVE = BLOCKS.registerBlock("l37_tile_curve",
            TileCurveBlock::new, unbreakable(MapColor.QUARTZ, SoundType.STONE).noOcclusion());
    /** Tile slab; the floating treads of the spiral stairs. Can stand in the shallows (FLOODED). */
    public static final DeferredBlock<FloodableSlabBlock> L37_TILE_SLAB = BLOCKS.registerBlock("l37_tile_slab",
            FloodableSlabBlock::new, unbreakable(MapColor.QUARTZ, SoundType.STONE));
    /** Overexposed warm-white daylight behind every opening: full-bright, light 15, never casts a shadow. */
    public static final DeferredBlock<SunlitVoidBlock> L37_SUNLIT_VOID = BLOCKS.registerBlock("l37_sunlit_void",
            SunlitVoidBlock::new, fixture(MapColor.SNOW, SoundType.GLASS, 15).noOcclusion());
    /** Chrome pool ladder at a pool's edge, in the water; FACING points toward the deck. */
    public static final DeferredBlock<PoolLadderBlock> L37_POOL_LADDER = BLOCKS.registerBlock("l37_pool_ladder",
            PoolLadderBlock::new, unbreakable(MapColor.METAL, SoundType.METAL).noCollission().noOcclusion());
    /** Chrome handrail on a wall, a metre above the floor; FACING points away from the wall. */
    public static final DeferredBlock<HandrailBlock> L37_HANDRAIL = BLOCKS.registerBlock("l37_handrail",
            HandrailBlock::new, unbreakable(MapColor.METAL, SoundType.METAL).noCollission().noOcclusion());

    // Level 9: The Suburbs. A night-time 1960s suburb: wet asphalt, sidewalks, lawns, houses,
    // streetlamps and signs. Block models are drawn for FACING = NORTH and turned by the blockstates.
    // Ground & street (road surface at y40, sidewalk slabs and lawns on top of it).
    public static final DeferredBlock<Block> ASPHALT = structural("asphalt", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Darker asphalt with a puddle sheen. */
    public static final DeferredBlock<Block> ASPHALT_WET = structural("asphalt_wet", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Asphalt with a faded paint stripe running along AXIS (centre lines, crosswalk bars). */
    public static final DeferredBlock<MarkingBlock> ASPHALT_MARKING = BLOCKS.registerBlock("asphalt_marking", MarkingBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.STONE));
    /** Half-block concrete sidewalk: half a block up from the road, so nobody needs to jump. */
    public static final DeferredBlock<SlabBlock> SIDEWALK_SLAB = BLOCKS.registerBlock("sidewalk_slab", SlabBlock::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    /** The sidewalk's edge along the road: same slab with a darker curb band. */
    public static final DeferredBlock<SlabBlock> SIDEWALK_CURB = BLOCKS.registerBlock("sidewalk_curb", SlabBlock::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    /** Road gutter block with a drain grate on top. */
    public static final DeferredBlock<Block> STORM_DRAIN = structural("storm_drain", MapColor.COLOR_GRAY, SoundType.METAL);
    public static final DeferredBlock<Block> MANHOLE = structural("manhole", MapColor.COLOR_GRAY, SoundType.METAL);
    /** Damp night lawn: grass on top, dirt on the sides. */
    public static final DeferredBlock<Block> LAWN_GRASS = structural("lawn_grass", MapColor.COLOR_GREEN, SoundType.GRASS);
    /** Dead leaves piled along the curbs; you walk through them. */
    public static final DeferredBlock<ThinLayerBlock> LEAF_LITTER = BLOCKS.registerBlock("leaf_litter", ThinLayerBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.GRASS).noCollission().noOcclusion());
    /**
     * Grey dust left where a Watcher turned someone to dust. Unlike the rest of the level it breaks
     * by hand (and drops nothing), so players can sweep it away.
     */
    public static final DeferredBlock<ThinLayerBlock> DUST_PILE = BLOCKS.registerBlock("dust_pile", ThinLayerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.1f)
                    .sound(SoundType.SAND)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));

    // Houses
    public static final DeferredBlock<Block> SIDING_GREEN = structural("siding_green", MapColor.COLOR_GREEN, SoundType.WOOD);
    public static final DeferredBlock<Block> SIDING_GREY = structural("siding_grey", MapColor.COLOR_GRAY, SoundType.WOOD);
    public static final DeferredBlock<Block> SIDING_BLUE = structural("siding_blue", MapColor.COLOR_LIGHT_BLUE, SoundType.WOOD);
    public static final DeferredBlock<Block> SIDING_YELLOW = structural("siding_yellow", MapColor.COLOR_YELLOW, SoundType.WOOD);
    public static final DeferredBlock<Block> SIDING_WHITE = structural("siding_white", MapColor.SNOW, SoundType.WOOD);
    /** White painted corner boards and window/door trim. */
    public static final DeferredBlock<Block> HOUSE_TRIM = structural("house_trim", MapColor.SNOW, SoundType.WOOD);
    public static final DeferredBlock<Block> BRICK_FOUNDATION = structural("brick_foundation", MapColor.TERRACOTTA_RED, SoundType.STONE);
    public static final DeferredBlock<Block> ROOF_SHINGLES = structural("roof_shingles", MapColor.COLOR_GRAY, SoundType.STONE);
    public static final DeferredBlock<StairBlock> ROOF_SHINGLES_STAIRS = BLOCKS.registerBlock("roof_shingles_stairs",
            props -> new StairBlock(ROOF_SHINGLES.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_GRAY, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> ROOF_SHINGLES_SLAB = BLOCKS.registerBlock("roof_shingles_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.STONE));
    /** Blacked-out window on the FACING side of a trim block. */
    public static final DeferredBlock<WallDecalBlock> HOUSE_WINDOW = BLOCKS.registerBlock("house_window", WallDecalBlock::new,
            unbreakable(MapColor.SNOW, SoundType.WOOD));
    /** Front door; LOCKED doors only rattle. */
    public static final DeferredBlock<HouseDoorBlock> HOUSE_DOOR = BLOCKS.registerBlock("house_door",
            props -> new HouseDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.COLOR_RED, SoundType.WOOD).noOcclusion());
    /** One block of a garage door on its FACING side; the texture tiles, so 3 in a row read as one door. */
    public static final DeferredBlock<WallDecalBlock> GARAGE_DOOR = BLOCKS.registerBlock("garage_door", WallDecalBlock::new,
            unbreakable(MapColor.SNOW, SoundType.METAL));
    public static final DeferredBlock<Block> PORCH_PLANKS = structural("porch_planks", MapColor.COLOR_GRAY, SoundType.WOOD);
    public static final DeferredBlock<FenceBlock> PORCH_POST = BLOCKS.registerBlock("porch_post", FenceBlock::new,
            unbreakable(MapColor.SNOW, SoundType.WOOD).forceSolidOn());
    /** Small lantern beside the front door; FACING points away from the wall. */
    public static final DeferredBlock<WallLampBlock> PORCH_LIGHT = BLOCKS.registerBlock("porch_light",
            props -> new WallLampBlock(props, 7, 4, 15, 9), wallFixture(MapColor.COLOR_ORANGE, 10));

    // House interiors. Furniture FACING = the way its front looks (its back is on the other side).
    public static final DeferredBlock<Block> BEIGE_CARPET = structural("beige_carpet", MapColor.SAND, SoundType.WOOL);
    public static final DeferredBlock<Block> DIM_WALLPAPER = structural("dim_wallpaper", MapColor.TERRACOTTA_PINK, SoundType.WOOD);
    public static final DeferredBlock<Block> WOOD_FLOOR = structural("wood_floor", MapColor.WOOD, SoundType.WOOD);
    /** Seat, backrest along the back, an armrest on each side. */
    public static final DeferredBlock<FurnitureBlock> SOFA = furniture("sofa", MapColor.COLOR_BROWN, SoundType.WOOL, Shapes.or(
            Block.box(0, 0, 1, 16, 8.5, 16),
            Block.box(0, 0, 12, 16, 14.75, 16),
            Block.box(0, 0, 1.5, 2, 10, 16),
            Block.box(14, 0, 1.5, 16, 10, 16)));
    /** A dead CRT television on a low cabinet. */
    public static final DeferredBlock<FurnitureBlock> TV = furniture("tv", MapColor.COLOR_BROWN, SoundType.WOOD, Shapes.or(
            Block.box(1, 0, 3, 15, 6, 15),
            Block.box(3, 6, 3.5, 13, 16, 10.5),
            Block.box(4.5, 7.5, 10.5, 11.5, 14.5, 14)));
    public static final DeferredBlock<FurnitureBlock> FRIDGE = furniture("fridge", MapColor.QUARTZ, SoundType.METAL,
            Block.box(1, 0, 2, 15, 16, 16));
    /** Cabinet with a counter top that overhangs the front by a pixel. */
    public static final DeferredBlock<FurnitureBlock> KITCHEN_COUNTER = furniture("kitchen_counter", MapColor.WOOD, SoundType.WOOD, Shapes.or(
            Block.box(0, 0, 1, 16, 14, 16),
            Block.box(0, 14, 0, 16, 16, 16)));
    /** Table top on four legs. FACING doesn't change its look, but the block keeps it like all furniture. */
    public static final DeferredBlock<FurnitureBlock> DINING_TABLE = furniture("dining_table", MapColor.WOOD, SoundType.WOOD, Shapes.or(
            Block.box(0, 12.5, 0, 16, 14.5, 16),
            Block.box(1, 0, 1, 2.5, 12.5, 2.5),
            Block.box(13.5, 0, 1, 15, 12.5, 2.5),
            Block.box(1, 0, 13.5, 2.5, 12.5, 15),
            Block.box(13.5, 0, 13.5, 15, 12.5, 15)));
    /** Two blocks long: foot at P, head (HEAD = true) at P + FACING. */
    public static final DeferredBlock<HouseBedBlock> BED = BLOCKS.registerBlock("bed", HouseBedBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOL).noOcclusion());

    // Yards & nature
    public static final DeferredBlock<FenceBlock> PICKET_FENCE = BLOCKS.registerBlock("picket_fence", FenceBlock::new,
            unbreakable(MapColor.SNOW, SoundType.WOOD).forceSolidOn());
    /** Mailbox on a post at the edge of the lawn; FACING = the lid side, towards the street. */
    public static final DeferredBlock<FurnitureBlock> MAILBOX = furniture("mailbox", MapColor.COLOR_GRAY, SoundType.METAL, Shapes.or(
            Block.box(7, 0, 7, 9, 10, 9),
            Block.box(5.5, 10, 2, 10.5, 15, 14)));
    public static final DeferredBlock<RotatedPillarBlock> NIGHT_LOG = BLOCKS.registerBlock("night_log", RotatedPillarBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD));
    /** Dark conifer needles. You can see through the gaps, so neighbours must still be drawn. */
    public static final DeferredBlock<Block> NIGHT_LEAVES = BLOCKS.registerBlock("night_leaves", Block::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.GRASS).noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** A bare dead branch; connects like a fence to other branches and to logs. */
    public static final DeferredBlock<FenceBlock> DEAD_BRANCH = BLOCKS.registerBlock("dead_branch", FenceBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).forceSolidOn());
    public static final DeferredBlock<Block> HEDGE = structural("hedge", MapColor.COLOR_GREEN, SoundType.GRASS);

    // Street furniture. A streetlamp is a stack of poles, an arm beside the top pole block (FACING =
    // the way the arm points) and a head at the arm's tip (same FACING), all at the same height.
    public static final DeferredBlock<PoleBlock> STREETLAMP_POLE = BLOCKS.registerBlock("streetlamp_pole",
            props -> new PoleBlock(props, 2.5), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<WallMountBlock> STREETLAMP_ARM = BLOCKS.registerBlock("streetlamp_arm",
            props -> new WallMountBlock(props, 7, 11, 0, 9, 14, 16), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Old sodium lamp: warm orange light. */
    public static final DeferredBlock<StreetlampHeadBlock> STREETLAMP_HEAD = BLOCKS.registerBlock("streetlamp_head",
            StreetlampHeadBlock::new, streetlampHead());
    /** Mercury-vapour lamp: cold greenish-white light. */
    public static final DeferredBlock<StreetlampHeadBlock> STREETLAMP_HEAD_MERCURY = BLOCKS.registerBlock("streetlamp_head_mercury",
            StreetlampHeadBlock::new, streetlampHead());
    /** Thin galvanised post; a street_sign sits on top of the highest one. */
    public static final DeferredBlock<PoleBlock> SIGN_POST = BLOCKS.registerBlock("sign_post",
            props -> new PoleBlock(props, 1), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<StreetSignBlock> STREET_SIGN = BLOCKS.registerBlock("street_sign", StreetSignBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.METAL).noOcclusion());

    // The exit underpass
    public static final DeferredBlock<Block> TUNNEL_CONCRETE = structural("tunnel_concrete", MapColor.STONE, SoundType.STONE);
    /** Road surface deep inside the underpass; marks where the exit is. */
    public static final DeferredBlock<Block> EXIT_TUNNEL_MARKER = structural("exit_tunnel_marker", MapColor.COLOR_GRAY, SoundType.STONE);
    /** The big green "EXIT" sign over each underpass mouth: 4x2 tiles, PART picks the tile. */
    public static final DeferredBlock<HighwaySignBlock> HIGHWAY_SIGN_EXIT = BLOCKS.registerBlock(
            "highway_sign_exit", HighwaySignBlock::new, unbreakable(MapColor.COLOR_GREEN, SoundType.METAL));

    // Level 4: The Abandoned Office. Commercial loop-pile carpet, off-white drywall, a suspended
    // ceiling with 1x2 fluorescent troffers (most of them dead), ribbon windows streaming with rain,
    // and the furniture of an office everyone left at once. Furniture FACING = the way its front
    // looks; wall-mounted things FACING = away from the wall. Models are drawn facing north.
    public static final DeferredBlock<Block> OFFICE_CARPET = structural("office_carpet", MapColor.COLOR_GRAY, SoundType.WOOL);
    public static final DeferredBlock<Block> OFFICE_CARPET_BLUE = structural("office_carpet_blue", MapColor.COLOR_BLUE, SoundType.WOOL);
    /** Darker, unfaded carpet where furniture stood for years: the "dents" left behind. */
    public static final DeferredBlock<Block> OFFICE_CARPET_INDENT = structural("office_carpet_indent", MapColor.COLOR_GRAY, SoundType.WOOL);
    public static final DeferredBlock<Block> OFFICE_CARPET_BLUE_INDENT = structural("office_carpet_blue_indent", MapColor.COLOR_BLUE, SoundType.WOOL);
    public static final DeferredBlock<Block> OFFICE_WALL = structural("office_wall", MapColor.SAND, SoundType.STONE);
    /** Bottom wall block with the dark vinyl base strip. */
    public static final DeferredBlock<Block> OFFICE_WALL_BASE = structural("office_wall_base", MapColor.SAND, SoundType.STONE);
    /** Bottom wall block of the lobbies: base strip, darker paint and a wooden chair rail on top. */
    public static final DeferredBlock<Block> OFFICE_WALL_RAIL = structural("office_wall_rail", MapColor.SAND, SoundType.STONE);
    public static final DeferredBlock<Block> OFFICE_COLUMN = structural("office_column", MapColor.QUARTZ, SoundType.STONE);
    public static final DeferredBlock<Block> OFFICE_COLUMN_BASE = structural("office_column_base", MapColor.QUARTZ, SoundType.STONE);
    public static final DeferredBlock<Block> OFFICE_CEILING = structural("office_ceiling", MapColor.SAND, SoundType.WOOD);
    /** A ceiling tile that fell out: a dark hole into the plenum (ducts, cables). */
    public static final DeferredBlock<Block> OFFICE_CEILING_MISSING = structural("office_ceiling_missing", MapColor.COLOR_BLACK, SoundType.WOOD);
    public static final DeferredBlock<Block> OFFICE_CEILING_HATCH = structural("office_ceiling_hatch", MapColor.SAND, SoundType.METAL);
    /** Half of a 1x2 fluorescent troffer; MODE dead = tubes out (no light, grey lens). */
    public static final DeferredBlock<OfficePanelBlock> OFFICE_PANEL = BLOCKS.registerBlock("office_panel", OfficePanelBlock::new,
            unbreakable(MapColor.SNOW, SoundType.GLASS)
                    .lightLevel(state -> state.getValue(OfficePanelBlock.MODE) == LampMode.DEAD ? 0 : 11)
                    .emissiveRendering((state, level, pos) -> state.getValue(OfficePanelBlock.MODE) != LampMode.DEAD));
    /** Rainy window pane; its block light is the cold night glow (and the lightning flash). */
    public static final DeferredBlock<OfficeWindowBlock> OFFICE_WINDOW = BLOCKS.registerBlock("office_window", OfficeWindowBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.GLASS).lightLevel(state -> 15));
    /** Conference-room glass wall on thin black mullions; the second one has its blinds half down. */
    public static final DeferredBlock<TransparentBlock> OFFICE_GLASS = BLOCKS.registerBlock("office_glass", TransparentBlock::new, glass());
    public static final DeferredBlock<TransparentBlock> OFFICE_GLASS_BLINDS = BLOCKS.registerBlock("office_glass_blinds", TransparentBlock::new, glass());
    /** Heavy red velvet stage curtain, floor to ceiling (the curtain room). */
    public static final DeferredBlock<Block> STAGE_CURTAIN = structural("stage_curtain", MapColor.COLOR_RED, SoundType.WOOL);
    /** Wood-veneer office door; LOCKED ones (closets) only rattle. */
    public static final DeferredBlock<HouseDoorBlock> OFFICE_DOOR = BLOCKS.registerBlock("office_door",
            props -> new HouseDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** The locked door with the brass "6" and the faint music behind it. */
    public static final DeferredBlock<MusicDoorBlock> OFFICE_DOOR_SIX = BLOCKS.registerBlock("office_door_six",
            props -> new MusicDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** Aluminium-framed glass door of the conference rooms. */
    public static final DeferredBlock<DoorBlock> OFFICE_GLASS_DOOR = BLOCKS.registerBlock("office_glass_door",
            props -> new DoorBlock(BlockSetType.COPPER, props), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Grey steel fire door with a push bar: the way into the emergency stairwell. */
    public static final DeferredBlock<DoorBlock> FIRE_DOOR = BLOCKS.registerBlock("fire_door",
            props -> new DoorBlock(BlockSetType.COPPER, props), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<ElevatorDoorBlock> ELEVATOR_DOOR = BLOCKS.registerBlock("elevator_door",
            ElevatorDoorBlock::new, unbreakable(MapColor.METAL, SoundType.METAL));
    /** Grey cubicle partition, 1.5 blocks tall, joining its neighbours like a pane. */
    public static final DeferredBlock<CubiclePartitionBlock> CUBICLE_PARTITION = BLOCKS.registerBlock("cubicle_partition",
            CubiclePartitionBlock::new, unbreakable(MapColor.COLOR_GRAY, SoundType.WOOL).noOcclusion());
    /** Cast-iron radiator under the window sill, its back against the wall. */
    public static final DeferredBlock<FurnitureBlock> RADIATOR = furniture("radiator", MapColor.QUARTZ, SoundType.METAL,
            Block.box(0.5, 0, 10.5, 15.5, 11, 16));
    /** Grey laminate office desk; FACING = the side you sit at. */
    public static final DeferredBlock<FurnitureBlock> OFFICE_DESK = furniture("office_desk", MapColor.COLOR_LIGHT_GRAY, SoundType.WOOD, Shapes.or(
            Block.box(0, 11.5, 0, 16, 13, 16),
            Block.box(0.5, 0, 1, 1.5, 11.5, 15),
            Block.box(14.5, 0, 1, 15.5, 11.5, 15),
            Block.box(1.5, 3, 13.5, 14.5, 11.5, 14.5)));
    /** The same desk with a beige CRT monitor and a keyboard on it. */
    public static final DeferredBlock<FurnitureBlock> OFFICE_DESK_PC = furniture("office_desk_pc", MapColor.COLOR_LIGHT_GRAY, SoundType.WOOD, Shapes.or(
            Block.box(0, 11.5, 0, 16, 13, 16),
            Block.box(0.5, 0, 1, 1.5, 11.5, 15),
            Block.box(14.5, 0, 1, 15.5, 11.5, 15),
            Block.box(1.5, 3, 13.5, 14.5, 11.5, 14.5),
            Block.box(3.5, 13, 5, 12.5, 22, 14)));
    /** Blue fabric swivel chair on a five-star base; FACING = the way you'd sit. */
    public static final DeferredBlock<FurnitureBlock> OFFICE_CHAIR = furniture("office_chair", MapColor.COLOR_BLUE, SoundType.WOOL, Shapes.or(
            Block.box(3, 6.5, 3, 13, 8.5, 13),
            Block.box(3.5, 8.5, 11.5, 12.5, 18, 13.5),
            Block.box(7, 0, 7, 9, 6.5, 9),
            Block.box(2, 0, 2, 14, 1.5, 14)));
    public static final DeferredBlock<FurnitureBlock> FILING_CABINET = furniture("filing_cabinet", MapColor.COLOR_GRAY, SoundType.METAL,
            Block.box(1.5, 0, 2, 14.5, 16, 16));
    public static final DeferredBlock<FurnitureBlock> COPIER = furniture("copier", MapColor.QUARTZ, SoundType.METAL, Shapes.or(
            Block.box(0.5, 0, 2, 15.5, 13, 16),
            Block.box(1, 13, 3, 15, 14.5, 15.5)));
    /** A potted office plant that died long ago. */
    public static final DeferredBlock<FurnitureBlock> DEAD_PLANT = furniture("dead_plant", MapColor.COLOR_BROWN, SoundType.GRASS, Shapes.or(
            Block.box(4.5, 0, 4.5, 11.5, 7, 11.5),
            Block.box(5, 7, 5, 11, 20, 11)));
    public static final DeferredBlock<FurnitureBlock> CARDBOARD_BOXES = furniture("cardboard_boxes", MapColor.COLOR_BROWN, SoundType.WOOL, Shapes.or(
            Block.box(1, 0, 1.5, 15, 9, 14.5),
            Block.box(2.5, 9, 3, 12, 16, 12)));
    /** Aluminium step ladder left standing where the work stopped. */
    public static final DeferredBlock<FurnitureBlock> STEP_LADDER = furniture("step_ladder", MapColor.METAL, SoundType.METAL,
            Block.box(3, 0, 2, 13, 16, 14));
    /** One block of a long dark-wood conference table (the tops join up seamlessly). */
    public static final DeferredBlock<FurnitureBlock> CONFERENCE_TABLE = furniture("conference_table", MapColor.COLOR_BROWN, SoundType.WOOD, Shapes.or(
            Block.box(0, 11.5, 0, 16, 13, 16),
            Block.box(6, 0, 6, 10, 11.5, 10)));
    /** Black server rack with dead status lights; two stacked make a full-height cabinet. */
    public static final DeferredBlock<FurnitureBlock> SERVER_RACK = furniture("server_rack", MapColor.COLOR_BLACK, SoundType.METAL,
            Block.box(1, 0, 1, 15, 16, 15));
    /** Break-room microwave, standing on a counter. */
    public static final DeferredBlock<FurnitureBlock> MICROWAVE = furniture("microwave", MapColor.QUARTZ, SoundType.METAL,
            Block.box(2, 0, 4, 14, 7.5, 14));
    /** Whiteboard on the wall, ghosts of old meetings still on it. You walk past it, not into it. */
    public static final DeferredBlock<WallMountBlock> WHITEBOARD = BLOCKS.registerBlock("whiteboard",
            props -> new WallMountBlock(props, 0, 1, 15, 16, 15, 16), unbreakable(MapColor.SNOW, SoundType.WOOD).noCollission().noOcclusion());
    /** Up/down call buttons beside an elevator. */
    public static final DeferredBlock<WallMountBlock> ELEVATOR_PANEL = BLOCKS.registerBlock("elevator_panel",
            props -> new WallMountBlock(props, 5.5, 3, 15, 10.5, 12, 16), unbreakable(MapColor.METAL, SoundType.METAL).noCollission().noOcclusion());
    /** A dome-less security camera on a wall bracket, high up. */
    public static final DeferredBlock<WallMountBlock> CCTV_CAMERA = BLOCKS.registerBlock("cctv_camera",
            props -> new WallMountBlock(props, 5, 6, 3, 11, 12, 16), unbreakable(MapColor.SNOW, SoundType.METAL).noCollission().noOcclusion());
    /** Lobby directory board. */
    public static final DeferredBlock<WallMountBlock> OFFICE_PLAQUE = BLOCKS.registerBlock("office_plaque",
            props -> new WallMountBlock(props, 1, 2, 15, 15, 14, 16), unbreakable(MapColor.COLOR_BLACK, SoundType.WOOD).noCollission().noOcclusion());
    /** Break-room vending machine (two tall, glowing front): right-click for Almond Water or an Energy Bar while stock lasts. */
    public static final DeferredBlock<VendingMachineBlock> VENDING_MACHINE = BLOCKS.registerBlock("vending_machine",
            VendingMachineBlock::new, unbreakable(MapColor.COLOR_RED, SoundType.METAL).lightLevel(state -> 6).noOcclusion());
    /** Water cooler: right-click with an empty hand for Almond Water, three times. */
    public static final DeferredBlock<WaterCoolerBlock> WATER_COOLER = BLOCKS.registerBlock("water_cooler",
            WaterCoolerBlock::new, unbreakable(MapColor.SNOW, SoundType.METAL).noOcclusion());
    /** A filing cabinet that is really a supply locker (filled from the locker loot table). */
    public static final DeferredBlock<SupplyContainerBlock> SUPPLY_CABINET = BLOCKS.registerBlock("supply_cabinet",
            props -> new SupplyContainerBlock(props, SupplyKind.LOCKER, Block.box(1.5, 0, 2, 14.5, 16, 16)),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());

    // Exits and loot
    public static final DeferredBlock<Block> EXIT_SIGN = BLOCKS.registerBlock("exit_sign", Block::new,
            fixture(MapColor.COLOR_GREEN, SoundType.GLASS, 9));
    public static final DeferredBlock<ExitDoorBlock> EXIT_DOOR = BLOCKS.registerBlock("exit_door", ExitDoorBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.METAL).noOcclusion());
    // Supply containers: filled from a loot table the first time they're opened (see SupplyContainerBlockEntity).
    public static final DeferredBlock<SupplyContainerBlock> SUPPLY_CRATE = BLOCKS.registerBlock("supply_crate",
            props -> new SupplyContainerBlock(props, SupplyKind.CRATE, Shapes.block()),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK).noLootTable());
    /** A canvas backpack lying flat on the floor: flap and straps on top, a pocket on the front. */
    public static final DeferredBlock<SupplyContainerBlock> BACKPACK = BLOCKS.registerBlock("backpack",
            props -> new SupplyContainerBlock(props, SupplyKind.BACKPACK, Shapes.or(
                    Block.box(2.75, 0, 3.5, 13.25, 6, 13.25),
                    Block.box(4, 0, 2.5, 12, 3, 3.5))),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5f).sound(SoundType.WOOL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    /** A painted metal cabinet with a vented door, its back against the wall. */
    public static final DeferredBlock<SupplyContainerBlock> SUPPLY_LOCKER = BLOCKS.registerBlock("supply_locker",
            props -> new SupplyContainerBlock(props, SupplyKind.LOCKER, Block.box(1, 0, 1.5, 15, 16, 16)),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.0f).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());

    // Safe rooms (placed by the level layouts; see world/SafeRooms).
    /** Old field radio on a little table: right-click to save your checkpoint. FACING = its front, into the room. */
    public static final DeferredBlock<CheckpointRadioBlock> CHECKPOINT_RADIO = BLOCKS.registerBlock("checkpoint_radio",
            CheckpointRadioBlock::new, unbreakable(MapColor.COLOR_GREEN, SoundType.METAL).lightLevel(state -> 6).noOcclusion());
    /**
     * Warm incandescent ceiling lamp (part of the ceiling, like the Lobby panels). Battery-backed:
     * its light stays on in blackouts. Unlike the other fixtures it is NOT drawn full-bright,
     * because full-bright faces all go dark together in a blackout; instead its own light (and
     * vanilla light 13, for when Veil isn't installed) lights its face.
     */
    public static final DeferredBlock<Block> SAFE_ROOM_LAMP = BLOCKS.registerBlock("safe_room_lamp", Block::new,
            unbreakable(MapColor.TERRACOTTA_WHITE, SoundType.GLASS).lightLevel(state -> 13));
    /** "SAFE" plaque hung on a wall over a safe room's doorway; FACING points away from the wall. You walk through it. */
    public static final DeferredBlock<WallMountBlock> SAFE_ROOM_SIGN = BLOCKS.registerBlock("safe_room_sign",
            props -> new WallMountBlock(props, 0, 2, 15, 16, 14, 16),
            unbreakable(MapColor.COLOR_GREEN, SoundType.METAL).noCollission().noOcclusion());

    private static DeferredBlock<Block> structural(String name, MapColor color, SoundType sound) {
        return BLOCKS.registerBlock(name, (Function<BlockBehaviour.Properties, Block>) Block::new, unbreakable(color, sound));
    }

    private static DeferredBlock<WallDecalBlock> decal(String name) {
        return BLOCKS.registerBlock(name, WallDecalBlock::new, unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD));
    }

    private static DeferredBlock<FurnitureBlock> furniture(String name, MapColor color, SoundType sound, VoxelShape shapeFacingNorth) {
        return BLOCKS.registerBlock(name, props -> new FurnitureBlock(props, shapeFacingNorth),
                unbreakable(color, sound).noOcclusion());
    }

    /**
     * A streetlamp head: bright (vanilla light 15) and drawn full-bright while it works, plain and
     * dark once its MODE is dead.
     */
    private static BlockBehaviour.Properties streetlampHead() {
        return unbreakable(MapColor.METAL, SoundType.METAL)
                .lightLevel(state -> state.getValue(StreetlampHeadBlock.MODE) != LampMode.DEAD ? 15 : 0)
                .emissiveRendering((state, level, pos) -> state.getValue(StreetlampHeadBlock.MODE) != LampMode.DEAD)
                .noOcclusion();
    }

    /** A glowing block: modest vanilla light, and its faces always render full-bright. */
    private static BlockBehaviour.Properties fixture(MapColor color, SoundType sound, int light) {
        return unbreakable(color, sound)
                .lightLevel(state -> light)
                .emissiveRendering((state, level, pos) -> true);
    }

    /** A glowing wall-mounted fixture that sits in the air in front of a wall. */
    private static BlockBehaviour.Properties wallFixture(MapColor color, int light) {
        return fixture(color, SoundType.METAL, light).noCollission().noOcclusion();
    }

    /** See-through wall glass: lets light through, never suffocates, doesn't hide the blocks behind it. */
    private static BlockBehaviour.Properties glass() {
        return unbreakable(MapColor.NONE, SoundType.GLASS)
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
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

    private ModBlocks() {}
}
