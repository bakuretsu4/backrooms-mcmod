package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.BallPitBlock;
import com.rickr.backrooms.block.ClimbingFrameBlock;
import com.rickr.backrooms.block.ParapetBlock;
import com.rickr.backrooms.block.ThroneLightBlock;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.ClayDoorBlock;
import com.rickr.backrooms.block.RopeBlock;
import com.rickr.backrooms.block.SeaDoorBlock;
import com.rickr.backrooms.block.WaterloggedFurnitureBlock;
import com.rickr.backrooms.block.CheckpointRadioBlock;
import com.rickr.backrooms.block.DuctBlock;
import com.rickr.backrooms.block.CubiclePartitionBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.block.FieldGrassBlock;
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
import com.rickr.backrooms.block.SirenBlock;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.SupplyKind;
import com.rickr.backrooms.block.SurfaceBlock;
import com.rickr.backrooms.block.ThinLayerBlock;
import com.rickr.backrooms.block.TileCurveBlock;
import com.rickr.backrooms.block.VehicleBlock;
import com.rickr.backrooms.block.VehicleHullBlock;
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
import net.minecraft.world.level.block.TrapDoorBlock;
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
    // The big surfaces come in a few texture versions (plain first; the numbers are relative
    // chances, see block/SurfaceBlock). The counts must match tools/gen_assets.py SURFACE_VARIANTS.
    public static final DeferredBlock<SurfaceBlock> YELLOW_WALLPAPER = surface("yellow_wallpaper", MapColor.COLOR_YELLOW, SoundType.WOOD, 6, 2, 1, 1);
    /** The bottom wall block, with the thin light-grey baseboard strip. */
    public static final DeferredBlock<SurfaceBlock> YELLOW_WALLPAPER_BASEBOARD = surface("yellow_wallpaper_baseboard", MapColor.COLOR_YELLOW, SoundType.WOOD, 6, 2, 1, 1);
    public static final DeferredBlock<SurfaceBlock> LOBBY_CARPET = surface("lobby_carpet", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL, 5, 1, 1, 1);
    public static final DeferredBlock<FloorArrowBlock> LOBBY_CARPET_ARROW = BLOCKS.registerBlock("lobby_carpet_arrow", FloorArrowBlock::new,
            unbreakable(MapColor.TERRACOTTA_YELLOW, SoundType.WOOL));
    public static final DeferredBlock<RugBlock> RUG = BLOCKS.registerBlock("rug", RugBlock::new,
            unbreakable(MapColor.COLOR_RED, SoundType.WOOL));
    public static final DeferredBlock<SurfaceBlock> CEILING_TILE = surface("ceiling_tile", MapColor.SAND, SoundType.WOOD, 6, 2, 1, 1);
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
    public static final DeferredBlock<SurfaceBlock> CONCRETE_WALL = surface("concrete_wall", MapColor.STONE, SoundType.STONE, 5, 1, 1);
    public static final DeferredBlock<SurfaceBlock> CONCRETE_FLOOR = surface("concrete_floor", MapColor.STONE, SoundType.STONE, 5, 1, 1, 1);
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
    public static final DeferredBlock<SurfaceBlock> PIPE_WALL = surface("pipe_wall", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE, 5, 1, 1);
    public static final DeferredBlock<SurfaceBlock> PIPE_FLOOR = surface("pipe_floor", MapColor.COLOR_LIGHT_GRAY, SoundType.STONE, 5, 1, 1);
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
    /**
     * Small caged bulb on the plain wall of the Level 2 corridors, just under the ceiling. Its bulb
     * stops glowing while its series circuit is out (see block/Level2LampGlow).
     */
    public static final DeferredBlock<WallLampBlock> BULKHEAD_LAMP = BLOCKS.registerBlock("bulkhead_lamp",
            props -> new WallLampBlock(props, 6, 3, 13, 6), wallFixture(MapColor.SNOW, 10)
                    .emissiveRendering(com.rickr.backrooms.block.Level2LampGlow::emissive));

    // The Poolrooms: big white tiles, dark tiles for the drain and lane lines, a tile floor with
    // moving light ripples (caustics) under all the water, daylight skylights and underwater lamps.
    public static final DeferredBlock<SurfaceBlock> POOL_TILE = surface("pool_tile", MapColor.SNOW, SoundType.STONE, 7, 1, 1);
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
    public static final DeferredBlock<SurfaceBlock> ASPHALT = surface("asphalt", MapColor.COLOR_BLACK, SoundType.STONE, 5, 1, 1, 1);
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
    public static final DeferredBlock<SurfaceBlock> OFFICE_CARPET = surface("office_carpet", MapColor.COLOR_GRAY, SoundType.WOOL, 5, 1, 1);
    public static final DeferredBlock<Block> OFFICE_CARPET_BLUE = structural("office_carpet_blue", MapColor.COLOR_BLUE, SoundType.WOOL);
    /** Darker, unfaded carpet where furniture stood for years: the "dents" left behind. */
    public static final DeferredBlock<Block> OFFICE_CARPET_INDENT = structural("office_carpet_indent", MapColor.COLOR_GRAY, SoundType.WOOL);
    public static final DeferredBlock<Block> OFFICE_CARPET_BLUE_INDENT = structural("office_carpet_blue_indent", MapColor.COLOR_BLUE, SoundType.WOOL);
    public static final DeferredBlock<SurfaceBlock> OFFICE_WALL = surface("office_wall", MapColor.SAND, SoundType.STONE, 5, 1, 1);
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
    /** Water cooler: right-click for Almond Water, three times. */
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

    // Level 94 "Motion" props (Props slice): siren poles that play cartoon music all day, parked
    // 1930s cars and milk vans, and the CRT TV full of static in the house interiors.
    /** Weathered dark wooden pole; stack seven and put an l94_siren on top. */
    public static final DeferredBlock<PoleBlock> L94_SIREN_POLE = BLOCKS.registerBlock("l94_siren_pole",
            props -> new PoleBlock(props, 2), unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noOcclusion());
    /** Old horn loudspeaker on top of the pole; FACING = the way the front horn points. Sings by day (client side). */
    public static final DeferredBlock<SirenBlock> L94_SIREN = BLOCKS.registerBlock("l94_siren", SirenBlock::new,
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Anchor of a parked vehicle; its renderer draws the whole car or van (see block/Level94Vehicles). */
    public static final DeferredBlock<VehicleBlock> L94_VEHICLE = BLOCKS.registerBlock("l94_vehicle", VehicleBlock::new,
            vehicleBody());
    /** Invisible solid filler for the rest of a vehicle's space. */
    public static final DeferredBlock<VehicleHullBlock> L94_VEHICLE_HULL = BLOCKS.registerBlock("l94_vehicle_hull", VehicleHullBlock::new,
            vehicleBody());
    /**
     * Wooden-cabinet CRT on short legs, its screen hissing with static (animated texture). The
     * screen glows a little: light 4, and the model draws only the screen face full-bright.
     */
    public static final DeferredBlock<FurnitureBlock> L94_TV = BLOCKS.registerBlock("l94_tv",
            props -> new FurnitureBlock(props, Shapes.or(
                    Block.box(2, 0, 4, 14, 2.5, 13),
                    Block.box(1, 2.5, 2.5, 15, 15, 14.5),
                    Block.box(4, 4.5, 14, 12, 12.5, 15.5))),
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noOcclusion().lightLevel(state -> 4));

    // Level 94 "Motion" world (World slice): felt-green hills with mowing stripes, smooth grey roads,
    // toy houses of painted cardboard, lollipop trees and water towers. The house walls are drawn
    // like the Level 9 window decals: FACING is the outside face (siding), the other faces show the
    // bright wallpaper inside.
    /** Grass in three mown shades; the top is felt, the sides dirt with a grass fringe. */
    public static final DeferredBlock<Block> L94_GRASS_LIGHT = structural("l94_grass_light", MapColor.COLOR_LIGHT_GREEN, SoundType.GRASS);
    public static final DeferredBlock<Block> L94_GRASS_MID = structural("l94_grass_mid", MapColor.COLOR_LIGHT_GREEN, SoundType.GRASS);
    public static final DeferredBlock<Block> L94_GRASS_DARK = structural("l94_grass_dark", MapColor.COLOR_GREEN, SoundType.GRASS);
    /** Half-block grass: the in-between steps that make the hills look (and walk) smooth. */
    public static final DeferredBlock<SlabBlock> L94_GRASS_SLAB_LIGHT = BLOCKS.registerBlock("l94_grass_slab_light", SlabBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_GREEN, SoundType.GRASS));
    public static final DeferredBlock<SlabBlock> L94_GRASS_SLAB_MID = BLOCKS.registerBlock("l94_grass_slab_mid", SlabBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_GREEN, SoundType.GRASS));
    public static final DeferredBlock<SlabBlock> L94_GRASS_SLAB_DARK = BLOCKS.registerBlock("l94_grass_slab_dark", SlabBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.GRASS));
    public static final DeferredBlock<Block> L94_DIRT = structural("l94_dirt", MapColor.DIRT, SoundType.GRAVEL);
    /** Smooth grey asphalt with no lines. */
    public static final DeferredBlock<Block> L94_ASPHALT = structural("l94_asphalt", MapColor.COLOR_GRAY, SoundType.STONE);
    /** Light concrete: road curbs and town sidewalks. */
    public static final DeferredBlock<Block> L94_CURB = structural("l94_curb", MapColor.QUARTZ, SoundType.STONE);
    /** Tan dirt footpath up to the hilltop houses. */
    public static final DeferredBlock<Block> L94_PATH = structural("l94_path", MapColor.SAND, SoundType.GRAVEL);
    public static final DeferredBlock<SlabBlock> L94_PATH_SLAB = BLOCKS.registerBlock("l94_path_slab", SlabBlock::new,
            unbreakable(MapColor.SAND, SoundType.GRAVEL));
    /** Pale stone paving of the town plaza. */
    public static final DeferredBlock<Block> L94_PAVING = structural("l94_paving", MapColor.SAND, SoundType.STONE);
    public static final DeferredBlock<Block> L94_FOUNTAIN_STONE = structural("l94_fountain_stone", MapColor.STONE, SoundType.STONE);
    /** Glossy painted plaster of the king chess piece in the fountain. */
    public static final DeferredBlock<Block> L94_STATUE = structural("l94_statue", MapColor.QUARTZ, SoundType.STONE);
    /** Bright round crown of a lollipop tree (solid, like a toy). */
    public static final DeferredBlock<Block> L94_LEAVES = structural("l94_leaves", MapColor.COLOR_LIGHT_GREEN, SoundType.GRASS);
    public static final DeferredBlock<PoleBlock> L94_TRUNK = BLOCKS.registerBlock("l94_trunk",
            props -> new PoleBlock(props, 2.5), unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noOcclusion());
    // Water towers.
    public static final DeferredBlock<PoleBlock> L94_TOWER_LEG = BLOCKS.registerBlock("l94_tower_leg",
            props -> new PoleBlock(props, 1.5), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Lattice cross-bracing between the legs; joins its neighbours like a pane. */
    public static final DeferredBlock<IronBarsBlock> L94_TOWER_BRACE = BLOCKS.registerBlock("l94_tower_brace", IronBarsBlock::new,
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<Block> L94_TANK = structural("l94_tank", MapColor.METAL, SoundType.METAL);
    public static final DeferredBlock<Block> L94_TANK_ROOF = structural("l94_tank_roof", MapColor.COLOR_GRAY, SoundType.METAL);
    public static final DeferredBlock<SlabBlock> L94_TANK_ROOF_SLAB = BLOCKS.registerBlock("l94_tank_roof_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.METAL));
    // Houses.
    /** Cream painted-cardboard siding on every face (gables). */
    public static final DeferredBlock<Block> L94_SIDING = structural("l94_siding", MapColor.TERRACOTTA_WHITE, SoundType.WOOD);
    /** House wall: siding on the FACING side, wallpaper on the others. */
    public static final DeferredBlock<WallDecalBlock> L94_HOUSE_WALL = BLOCKS.registerBlock("l94_house_wall", WallDecalBlock::new,
            unbreakable(MapColor.TERRACOTTA_WHITE, SoundType.WOOD));
    /** The band of oversized bricks along the base: bricks outside, wallpaper and skirting inside. */
    public static final DeferredBlock<WallDecalBlock> L94_BRICK_BAND = BLOCKS.registerBlock("l94_brick_band", WallDecalBlock::new,
            unbreakable(MapColor.TERRACOTTA_RED, SoundType.STONE));
    /** Oversized bricks on every face (house corners). */
    public static final DeferredBlock<Block> L94_BRICK = structural("l94_brick", MapColor.TERRACOTTA_RED, SoundType.STONE);
    /** Dark mullioned window in the siding (FACING = outside); seen from inside too. */
    public static final DeferredBlock<WallDecalBlock> L94_WINDOW = BLOCKS.registerBlock("l94_window", WallDecalBlock::new,
            unbreakable(MapColor.TERRACOTTA_WHITE, SoundType.WOOD));
    /** White painted trim: corner boards, the band between storeys, ceilings. */
    public static final DeferredBlock<Block> L94_TRIM = structural("l94_trim", MapColor.SNOW, SoundType.WOOD);
    /** Bright vinyl floor of the house interiors. */
    public static final DeferredBlock<Block> L94_FLOOR = structural("l94_floor", MapColor.COLOR_CYAN, SoundType.WOOD);
    public static final DeferredBlock<StairBlock> L94_STAIRS = BLOCKS.registerBlock("l94_stairs",
            props -> new StairBlock(L94_FLOOR.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_CYAN, SoundType.WOOD));
    public static final DeferredBlock<DoorBlock> L94_HOUSE_DOOR = BLOCKS.registerBlock("l94_house_door",
            props -> new DoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.COLOR_RED, SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<Block> L94_ROOF_RED = structural("l94_roof_red", MapColor.COLOR_RED, SoundType.STONE);
    public static final DeferredBlock<StairBlock> L94_ROOF_RED_STAIRS = BLOCKS.registerBlock("l94_roof_red_stairs",
            props -> new StairBlock(L94_ROOF_RED.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_RED, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> L94_ROOF_RED_SLAB = BLOCKS.registerBlock("l94_roof_red_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_RED, SoundType.STONE));
    public static final DeferredBlock<Block> L94_ROOF_BROWN = structural("l94_roof_brown", MapColor.COLOR_BROWN, SoundType.STONE);
    public static final DeferredBlock<StairBlock> L94_ROOF_BROWN_STAIRS = BLOCKS.registerBlock("l94_roof_brown_stairs",
            props -> new StairBlock(L94_ROOF_BROWN.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_BROWN, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> L94_ROOF_BROWN_SLAB = BLOCKS.registerBlock("l94_roof_brown_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.STONE));
    /** A fat cushioned armchair; FACING = the way you'd sit. */
    public static final DeferredBlock<FurnitureBlock> L94_ARMCHAIR = furniture("l94_armchair", MapColor.COLOR_ORANGE, SoundType.WOOL, Shapes.or(
            Block.box(1, 0, 2, 15, 8, 15),
            Block.box(1, 0, 11, 15, 16, 15),
            Block.box(0, 0, 2, 3, 11, 15),
            Block.box(13, 0, 2, 16, 11, 15)));
    /** 3x3 play rug printed with toy roads (PART = which ninth, like the Level 0 rug). */
    public static final DeferredBlock<RugBlock> L94_RUG = BLOCKS.registerBlock("l94_rug", RugBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.WOOL));
    /** An odd, lopsided floor lamp that lights the room (vanilla light 13, plus a Veil light). */
    public static final DeferredBlock<FurnitureBlock> L94_LAMP = BLOCKS.registerBlock("l94_lamp",
            props -> new FurnitureBlock(props, Shapes.or(
                    Block.box(4, 0, 4, 12, 1.5, 12),
                    Block.box(7, 1.5, 7, 9, 10, 9),
                    Block.box(3, 10, 3, 13, 16, 13))),
            unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD).noOcclusion().lightLevel(state -> 13));
    /**
     * The dark behind a dark-door house's door: pitch black, gives no light, lets no light in and
     * you can walk into it (which takes you back to Level 9). The dark sibling of l37_sunlit_void.
     */
    public static final DeferredBlock<Block> L94_DARK_VOID = BLOCKS.registerBlock("l94_dark_void", Block::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.EMPTY).noCollission()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** The murky dark at the bottom of a flooded house's stairwell: wade down into it and you come out in Level 7. */
    public static final DeferredBlock<Block> L94_FLOOD_DARK = BLOCKS.registerBlock("l94_flood_dark", Block::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.EMPTY).noCollission()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** The moulded-clay front door on a few Level 9 houses: step through it into Level 94. */
    public static final DeferredBlock<ClayDoorBlock> L94_CLAY_DOOR = BLOCKS.registerBlock("l94_clay_door",
            props -> new ClayDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.TERRACOTTA_ORANGE, SoundType.MUD).noOcclusion());

    // Level 94 castle (Castle slice): the see-through glass mountain and its opal ramp, the white
    // cartoon castle on top, and the play-centre funhouse inside, where things stop looking like
    // toys and start looking real (see world/layout/Level94Castle and Level94CastlePlan).
    /** The glass mountain: faint blue-white glass full of sky reflections. Faces between two glass blocks are hidden, so only the outside draws. */
    public static final DeferredBlock<TransparentBlock> L94_GLASS_MOUNTAIN = BLOCKS.registerBlock("l94_glass_mountain",
            TransparentBlock::new, glass());
    /** Pale opal paving: the ramp up the mountain, its approach and the plateau around the castle. */
    public static final DeferredBlock<Block> L94_OPAL_PATH = structural("l94_opal_path", MapColor.QUARTZ, SoundType.STONE);
    /** Half-block opal paving: the in-between steps of the ramp, so you can walk up without jumping. */
    public static final DeferredBlock<SlabBlock> L94_OPAL_PATH_SLAB = BLOCKS.registerBlock("l94_opal_path_slab", SlabBlock::new,
            unbreakable(MapColor.QUARTZ, SoundType.STONE));
    /** Knee-high opal railing on the outer edge of the ramp and the plateau (collides 1.5 tall). */
    public static final DeferredBlock<ParapetBlock> L94_OPAL_RAIL = BLOCKS.registerBlock("l94_opal_rail", ParapetBlock::new,
            unbreakable(MapColor.QUARTZ, SoundType.STONE).noOcclusion());
    /** Outside of the castle: smooth white cartoon plaster. */
    public static final DeferredBlock<Block> L94_CASTLE_WALL = structural("l94_castle_wall", MapColor.SNOW, SoundType.STONE);
    /** White plaster with a narrow dark arched window on its four sides. */
    public static final DeferredBlock<Block> L94_CASTLE_WINDOW = structural("l94_castle_window", MapColor.SNOW, SoundType.STONE);
    /** Bright red roof tiles of the towers and halls. */
    public static final DeferredBlock<Block> L94_CASTLE_ROOF = structural("l94_castle_roof", MapColor.COLOR_RED, SoundType.STONE);
    public static final DeferredBlock<StairBlock> L94_CASTLE_ROOF_STAIRS = BLOCKS.registerBlock("l94_castle_roof_stairs",
            props -> new StairBlock(L94_CASTLE_ROOF.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_RED, SoundType.STONE));
    /** Grey dressed stone of the throne room, its dais and the throne. */
    public static final DeferredBlock<Block> L94_CASTLE_STONE = structural("l94_castle_stone", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<StairBlock> L94_CASTLE_STONE_STAIRS = BLOCKS.registerBlock("l94_castle_stone_stairs",
            props -> new StairBlock(L94_CASTLE_STONE.get().defaultBlockState(), props), unbreakable(MapColor.STONE, SoundType.STONE));
    /** Harsh square ceiling light of the play centre ("very bright in some rooms"). */
    public static final DeferredBlock<Block> L94_CASTLE_LAMP = BLOCKS.registerBlock("l94_castle_lamp", Block::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 15));
    /** Funhouse floors: blue/yellow checker, the red/white striped path (stripes cross AXIS), and the star circle. */
    public static final DeferredBlock<Block> L94_CHECKER_FLOOR = structural("l94_checker_floor", MapColor.COLOR_BLUE, SoundType.STONE);
    public static final DeferredBlock<MarkingBlock> L94_STRIPE_PATH = BLOCKS.registerBlock("l94_stripe_path", MarkingBlock::new,
            unbreakable(MapColor.COLOR_RED, SoundType.STONE));
    public static final DeferredBlock<Block> L94_STAR_TILE = structural("l94_star_tile", MapColor.COLOR_YELLOW, SoundType.STONE);
    public static final DeferredBlock<Block> L94_DISC_FLOOR = structural("l94_disc_floor", MapColor.COLOR_YELLOW, SoundType.STONE);
    public static final DeferredBlock<Block> L94_RING_FLOOR = structural("l94_ring_floor", MapColor.COLOR_GREEN, SoundType.STONE);
    /** Red/yellow striped pillar; the porthole one has a round window ring. */
    public static final DeferredBlock<Block> L94_PLAY_PILLAR = structural("l94_play_pillar", MapColor.COLOR_RED, SoundType.WOOD);
    public static final DeferredBlock<Block> L94_PLAY_PILLAR_PORTHOLE = structural("l94_play_pillar_porthole", MapColor.COLOR_RED, SoundType.WOOD);
    /** Fat green duct hanging under the ceiling, running along AXIS. */
    public static final DeferredBlock<RotatedPillarBlock> L94_PLAY_PIPE = BLOCKS.registerBlock("l94_play_pipe", RotatedPillarBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.METAL).noOcclusion());
    /** Scuffed painted interior wall, acoustic-tile ceiling (carpet on its upper face) and foam play mats. */
    public static final DeferredBlock<Block> L94_PLAY_WALL = structural("l94_play_wall", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE);
    public static final DeferredBlock<Block> L94_PLAY_CEILING = structural("l94_play_ceiling", MapColor.SAND, SoundType.WOOL);
    public static final DeferredBlock<Block> L94_PLAY_MAT = structural("l94_play_mat", MapColor.COLOR_RED, SoundType.WOOL);
    /** Plastic balls: you sink in and wade slowly, like powder snow. */
    public static final DeferredBlock<BallPitBlock> L94_BALL_PIT = BLOCKS.registerBlock("l94_ball_pit", BallPitBlock::new,
            unbreakable(MapColor.COLOR_RED, SoundType.WOOL).noCollission().noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** Climbing-frame cube: walk in and climb it like a ladder. */
    public static final DeferredBlock<ClimbingFrameBlock> L94_CLIMBING_FRAME = BLOCKS.registerBlock("l94_climbing_frame", ClimbingFrameBlock::new,
            unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOL).noCollission().noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** Glossy plastic tube lining (crawl tubes, the slide's side walls). */
    public static final DeferredBlock<Block> L94_PLAY_TUBE = structural("l94_play_tube", MapColor.COLOR_BLUE, SoundType.STONE);
    /** Slippery yellow slide: stairs as slick as ice, so you slide down them. */
    public static final DeferredBlock<StairBlock> L94_SLIDE = BLOCKS.registerBlock("l94_slide",
            props -> new StairBlock(L94_PLAY_TUBE.get().defaultBlockState(), props),
            unbreakable(MapColor.COLOR_YELLOW, SoundType.STONE).friction(0.98f));
    /** Animation studio: tilted drawing desk, sketches pinned to the wall (FACING side), the big table. */
    public static final DeferredBlock<FurnitureBlock> L94_DRAWING_DESK = furniture("l94_drawing_desk", MapColor.WOOD, SoundType.WOOD, Shapes.or(
            Block.box(0, 10, 1, 16, 14, 16),
            Block.box(1, 0, 13, 15, 10, 15)));
    public static final DeferredBlock<WallDecalBlock> L94_SKETCH_WALL = BLOCKS.registerBlock("l94_sketch_wall", WallDecalBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.STONE));
    public static final DeferredBlock<Block> L94_STUDIO_TABLE = structural("l94_studio_table", MapColor.WOOD, SoundType.WOOD);
    /** The miniature town on the studio table: a tiny house, a tiny tree, the tiny fountain. */
    public static final DeferredBlock<FurnitureBlock> L94_MINI_HOUSE = furniture("l94_mini_house", MapColor.SNOW, SoundType.WOOD,
            Block.box(3, 0, 4, 13, 10, 12));
    public static final DeferredBlock<FurnitureBlock> L94_MINI_TREE = furniture("l94_mini_tree", MapColor.COLOR_GREEN, SoundType.GRASS,
            Block.box(5, 0, 5, 11, 11, 11));
    public static final DeferredBlock<FurnitureBlock> L94_MINI_FOUNTAIN = furniture("l94_mini_fountain", MapColor.STONE, SoundType.STONE,
            Block.box(2, 0, 2, 14, 9, 14));
    /** Throne room: worn red carpet, the empty grey stone throne, and the white light behind it (walk in: Level 1). */
    public static final DeferredBlock<Block> L94_RED_CARPET = structural("l94_red_carpet", MapColor.COLOR_RED, SoundType.WOOL);
    public static final DeferredBlock<FurnitureBlock> L94_THRONE = furniture("l94_throne", MapColor.STONE, SoundType.STONE, Shapes.or(
            Block.box(1, 0, 1, 15, 8, 15),
            Block.box(1, 8, 12, 15, 30, 15),
            Block.box(1, 8, 1, 3, 14, 12),
            Block.box(13, 8, 1, 15, 14, 12)));
    public static final DeferredBlock<ThroneLightBlock> L94_THRONE_LIGHT = BLOCKS.registerBlock("l94_throne_light", ThroneLightBlock::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 15).noCollission().noOcclusion());
    // Level 94 anomalies and supplies (world/anomaly/level94): the porcelain sink of the furniture
    // piles (one in seven still holds the 1953 coin), the water tower tap and the milk-van crate
    // (Almond Water and Almond Milk, a few bottles each), the wandering door (a plain house door
    // in a free-standing frame) and the blinding hall's light ceiling and white floor.
    public static final DeferredBlock<com.rickr.backrooms.block.Level94SinkBlock> L94_SINK = BLOCKS.registerBlock("l94_sink",
            com.rickr.backrooms.block.Level94SinkBlock::new, unbreakable(MapColor.QUARTZ, SoundType.STONE).noOcclusion());
    public static final DeferredBlock<com.rickr.backrooms.block.Level94SupplyBlock> L94_TOWER_TAP = BLOCKS.registerBlock("l94_tower_tap",
            props -> new com.rickr.backrooms.block.Level94SupplyBlock(props, Shapes.or(
                    Block.box(6.5, 0, 6.5, 9.5, 15, 9.5),
                    Block.box(7, 9, 3, 9, 11, 6.5)),
                    () -> ModItems.ALMOND_WATER.get(), net.minecraft.sounds.SoundEvents.BOTTLE_FILL, "message.backrooms.l94.tap_empty"),
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<com.rickr.backrooms.block.Level94SupplyBlock> L94_MILK_CRATE = BLOCKS.registerBlock("l94_milk_crate",
            props -> new com.rickr.backrooms.block.Level94SupplyBlock(props, Block.box(2, 0, 2, 14, 11, 14),
                    () -> ModItems.ALMOND_MILK.get(), net.minecraft.sounds.SoundEvents.BOTTLE_FILL, "message.backrooms.l94.crate_empty"),
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<com.rickr.backrooms.block.WanderingDoorBlock> L94_WANDERING_DOOR = BLOCKS.registerBlock("l94_wandering_door",
            props -> new com.rickr.backrooms.block.WanderingDoorBlock(BlockSetType.OAK, props),
            unbreakable(MapColor.COLOR_RED, SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<Block> L94_GLARE_PANEL = BLOCKS.registerBlock("l94_glare_panel", Block::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 15));
    public static final DeferredBlock<Block> L94_WHITE_TILE = structural("l94_white_tile", MapColor.SNOW, SoundType.STONE);
    // Poolrooms anomalies (world/anomaly/poolrooms): the Sanguine Reservoir, the red copy of the
    // level. Its dim tile and floor under the water (no bright caustics), the red pool lamp and the
    // glowing red sprouts on the walkways, the only light left there.
    public static final DeferredBlock<Block> RESERVOIR_TILE = structural("reservoir_tile", MapColor.TERRACOTTA_WHITE, SoundType.STONE);
    public static final DeferredBlock<Block> RESERVOIR_FLOOR = structural("reservoir_floor", MapColor.TERRACOTTA_RED, SoundType.STONE);
    public static final DeferredBlock<PoolLampBlock> RESERVOIR_LAMP = BLOCKS.registerBlock("reservoir_lamp", PoolLampBlock::new,
            fixture(MapColor.COLOR_RED, SoundType.GLASS, 10));
    public static final DeferredBlock<com.rickr.backrooms.block.ReservoirSproutBlock> RESERVOIR_SPROUT = BLOCKS.registerBlock(
            "reservoir_sprout", com.rickr.backrooms.block.ReservoirSproutBlock::new,
            fixture(MapColor.COLOR_RED, SoundType.GRASS, 7).noCollission().noOcclusion());
    // Level 1 anomalies (world/anomaly/level1, world/layout/Level1AnomalyPlan): the orange storage
    // racks of the crate halls, the crude paintings (wall and floor) that change while nobody looks,
    // the film of condensation the low fog leaves on the floor, and the Corrupted Corridor
    // (Level 1.1): white cinderblock and blue tile, a wall you can walk into, bright patches in the
    // dark, black-and-white walls and a floor that is not really there.
    public static final DeferredBlock<Block> L1_STORAGE_SHELF = structural("l1_storage_shelf", MapColor.COLOR_ORANGE, SoundType.METAL);
    public static final DeferredBlock<com.rickr.backrooms.block.CrudePaintingBlock> L1_PAINTING = BLOCKS.registerBlock("l1_painting",
            com.rickr.backrooms.block.CrudePaintingBlock::new, unbreakable(MapColor.COLOR_BROWN, SoundType.WOOL).noCollission().noOcclusion());
    public static final DeferredBlock<com.rickr.backrooms.block.FloorPaintingBlock> L1_FLOOR_PAINTING = BLOCKS.registerBlock("l1_floor_painting",
            com.rickr.backrooms.block.FloorPaintingBlock::new, unbreakable(MapColor.COLOR_RED, SoundType.STONE).noCollission().noOcclusion());
    public static final DeferredBlock<ThinLayerBlock> L1_CONDENSATION = BLOCKS.registerBlock("l1_condensation", ThinLayerBlock::new,
            unbreakable(MapColor.WATER, SoundType.EMPTY).noCollission().noOcclusion());
    public static final DeferredBlock<Block> L1_CINDERBLOCK = structural("l1_cinderblock", MapColor.SNOW, SoundType.STONE);
    public static final DeferredBlock<Block> L1_BLUE_TILE = structural("l1_blue_tile", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE);
    public static final DeferredBlock<SlabBlock> L1_BLUE_TILE_SLAB = BLOCKS.registerBlock("l1_blue_tile_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.STONE));
    /** Looks like the cinderblock wall (a shade off), but you can walk into it: it throws you out somewhere else in Level 1. */
    public static final DeferredBlock<Block> L1_NOCLIP_WALL = BLOCKS.registerBlock("l1_noclip_wall", Block::new,
            unbreakable(MapColor.SNOW, SoundType.STONE).noCollission()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** An unexplained bright patch in the corridor's dark sector: full-bright, light 12. */
    public static final DeferredBlock<Block> L1_BRIGHT_PATCH = BLOCKS.registerBlock("l1_bright_patch", Block::new,
            fixture(MapColor.SNOW, SoundType.GLASS, 12));
    public static final DeferredBlock<Block> L1_MONOCHROME_WALL = structural("l1_monochrome_wall", MapColor.COLOR_BLACK, SoundType.STONE);
    public static final DeferredBlock<Block> L1_MONOCHROME_FLOOR = structural("l1_monochrome_floor", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Looks exactly like the monochrome floor but has no collision: you drop into the void below. */
    public static final DeferredBlock<Block> L1_VOID_FLOOR = BLOCKS.registerBlock("l1_void_floor", Block::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.STONE).noCollission()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));

    // ---- Level 4 anomalies (world/anomaly/level4, world/layout/Level4AnomalyPlan) ----------------
    /** Plain grey painted block wall of the looping stairwells. */
    public static final DeferredBlock<Block> L4_STAIRWELL_WALL = structural("l4_stairwell_wall", MapColor.COLOR_GRAY, SoundType.STONE);
    /** Grey steel stairwell door (looks like the fire door); the copies further up and down the shaft are LOCKED. */
    public static final DeferredBlock<HouseDoorBlock> L4_STAIR_DOOR = BLOCKS.registerBlock("l4_stair_door",
            props -> new HouseDoorBlock(BlockSetType.COPPER, props), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** The floor-number sign on every landing of a looping stairwell. It always says 4. */
    public static final DeferredBlock<WallMountBlock> L4_FLOOR_SIGN = BLOCKS.registerBlock("l4_floor_sign",
            props -> new WallMountBlock(props, 2, 1, 15, 14, 15, 16), unbreakable(MapColor.COLOR_GRAY, SoundType.METAL).noCollission().noOcclusion());
    /** The Window (Entity 2): a clear pane onto a sunny, blurry, familiar place. Warm light pours out. */
    public static final DeferredBlock<OfficeWindowBlock> L4_CLEAR_WINDOW = BLOCKS.registerBlock("l4_clear_window", OfficeWindowBlock::new,
            unbreakable(MapColor.COLOR_YELLOW, SoundType.GLASS).lightLevel(state -> 12));
    /** A window pane covered with cardboard and tape, the way survivors cover them. No glow. */
    public static final DeferredBlock<OfficeWindowBlock> L4_COVERED_WINDOW = BLOCKS.registerBlock("l4_covered_window", OfficeWindowBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD));
    /** Level 4.3: the dim white hallways. */
    public static final DeferredBlock<Block> L4_WHITE_WALL = structural("l4_white_wall", MapColor.SNOW, SoundType.STONE);
    public static final DeferredBlock<Block> L4_WHITE_FLOOR = structural("l4_white_floor", MapColor.SNOW, SoundType.STONE);
    /** Brushed-steel walls and ceiling of an elevator car, and its ribbed rubber floor. */
    public static final DeferredBlock<Block> L4_ELEVATOR_WALL = structural("l4_elevator_wall", MapColor.METAL, SoundType.METAL);
    public static final DeferredBlock<Block> L4_ELEVATOR_FLOOR = structural("l4_elevator_floor", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Office fridge in the Cubicles: right-click for Almond Water while it lasts. */
    public static final DeferredBlock<com.rickr.backrooms.block.OfficeFridgeBlock> L4_OFFICE_FRIDGE = BLOCKS.registerBlock("l4_office_fridge",
            com.rickr.backrooms.block.OfficeFridgeBlock::new, unbreakable(MapColor.QUARTZ, SoundType.METAL).noOcclusion());

    // ---- Level 2 anomalies (world/anomaly/level2, world/layout/Level2AnomalyPlan) ----------------
    /** Battery emergency light from the Blackout: a small violet glow (vanilla light 7 + a Veil light). */
    public static final DeferredBlock<WallLampBlock> L2_EMERGENCY_LIGHT = BLOCKS.registerBlock("l2_emergency_light",
            props -> new WallLampBlock(props, 10, 9, 14, 4), wallFixture(MapColor.COLOR_PURPLE, 7));
    /** A caged lamp whose bulb burst in the Blackout: dark, no light at all. */
    public static final DeferredBlock<WallLampBlock> L2_DEAD_LAMP = BLOCKS.registerBlock("l2_dead_lamp",
            props -> new WallLampBlock(props, 6, 3, 13, 6), unbreakable(MapColor.COLOR_GRAY, SoundType.METAL).noCollission().noOcclusion());
    /** Thick black liquid from burst pipes: wading through it is slow, like honey. Never glows. */
    public static final DeferredBlock<com.rickr.backrooms.block.BlackOozeBlock> L2_BLACK_OOZE = BLOCKS.registerBlock("l2_black_ooze",
            com.rickr.backrooms.block.BlackOozeBlock::new, unbreakable(MapColor.COLOR_BLACK, SoundType.HONEY_BLOCK)
                    .noCollission().noOcclusion().speedFactor(0.45f).jumpFactor(0.6f));
    /** A faulty machine cabinet in a hot zone; FACING = its front (gauges and vents). */
    public static final DeferredBlock<FurnitureBlock> L2_MACHINE = BLOCKS.registerBlock("l2_machine",
            props -> new FurnitureBlock(props, Shapes.block()), unbreakable(MapColor.METAL, SoundType.METAL));
    /** A short leaking steam pipe high on a machine; FACING = the way the steam blows out. */
    public static final DeferredBlock<WallMountBlock> L2_STEAM_VENT = BLOCKS.registerBlock("l2_steam_vent",
            props -> new WallMountBlock(props, 5, 4, 6, 11, 10, 16), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Steel door with a pitch-black window. LOCKED ones only rattle; the rare unlocked one opens on the void. */
    public static final DeferredBlock<HouseDoorBlock> L2_VOID_DOOR = BLOCKS.registerBlock("l2_void_door",
            props -> new HouseDoorBlock(BlockSetType.COPPER, props), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Lightless matte black: the passage behind a void door, and the floor and sky of the void itself. */
    public static final DeferredBlock<Block> L2_VOID_BLACK = structural("l2_void_black", MapColor.COLOR_BLACK, SoundType.STONE);

    // ---- Level 0 anomalies (world/anomaly/level0, world/layout/Level0AnomalyPlan) ----------------
    /** The Red Rooms: crimson wallpaper, with black mould, and with tally marks on its FACING side. */
    public static final DeferredBlock<Block> RED_WALLPAPER = structural("red_wallpaper", MapColor.COLOR_RED, SoundType.WOOD);
    public static final DeferredBlock<Block> RED_WALLPAPER_MOULD = structural("red_wallpaper_mould", MapColor.COLOR_RED, SoundType.WOOD);
    public static final DeferredBlock<WallDecalBlock> RED_WALL_TALLY = BLOCKS.registerBlock("red_wall_tally", WallDecalBlock::new,
            unbreakable(MapColor.COLOR_RED, SoundType.WOOD));
    /** Coarse, sticky dark-red carpet that smells of copper: you walk a little slower on it. */
    public static final DeferredBlock<Block> RED_CARPET_COARSE = BLOCKS.registerBlock("red_carpet_coarse", Block::new,
            unbreakable(MapColor.COLOR_RED, SoundType.WOOL).speedFactor(0.85f));
    /** Dim darkroom-red ceiling lamp of the Red Rooms (silent; see FixtureType.RED_ROOM_LIGHT). */
    public static final DeferredBlock<Block> RED_ROOM_LIGHT = BLOCKS.registerBlock("red_room_light", Block::new,
            fixture(MapColor.COLOR_RED, SoundType.GLASS, 4));
    /** A cluster of small pale mushrooms growing out of the red carpet. You walk through them. */
    public static final DeferredBlock<WallMountBlock> RED_MUSHROOMS = BLOCKS.registerBlock("red_mushrooms",
            props -> new WallMountBlock(props, 3, 0, 3, 13, 6, 13), unbreakable(MapColor.COLOR_BROWN, SoundType.FUNGUS).noCollission().noOcclusion());
    /** Warning signs of the Red Rooms: yellow wallpaper peeling off crimson, and sticky carpet (slows you down). */
    public static final DeferredBlock<Block> PEELING_WALLPAPER = structural("peeling_wallpaper", MapColor.COLOR_YELLOW, SoundType.WOOD);
    public static final DeferredBlock<Block> STICKY_CARPET = BLOCKS.registerBlock("sticky_carpet", Block::new,
            unbreakable(MapColor.TERRACOTTA_YELLOW, SoundType.WOOL).speedFactor(0.75f));
    /** The flickering wall: walk into it on Level 0 and you come out in Level 1. */
    public static final DeferredBlock<com.rickr.backrooms.block.FlickeringWallBlock> FLICKERING_WALL = BLOCKS.registerBlock("flickering_wall",
            com.rickr.backrooms.block.FlickeringWallBlock::new, unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD).noCollission()
                    .lightLevel(state -> 5)
                    .emissiveRendering((state, level, pos) -> true)
                    .isSuffocating((state, level, pos) -> false));
    /** Blackout Zones: rough, grimy, darker wallpaper, and the ankle-deep murky fluid on the carpet (slows you a little). */
    public static final DeferredBlock<Block> BLACKOUT_WALLPAPER = structural("blackout_wallpaper", MapColor.TERRACOTTA_BROWN, SoundType.WOOD);
    public static final DeferredBlock<ThinLayerBlock> CARPET_FLUID = BLOCKS.registerBlock("carpet_fluid", ThinLayerBlock::new,
            unbreakable(MapColor.TERRACOTTA_BROWN, SoundType.EMPTY).noCollission().noOcclusion().speedFactor(0.85f));
    /** The Manila Room: manila-beige wallpaper inside, the brown wall outside. */
    public static final DeferredBlock<Block> MANILA_WALLPAPER = structural("manila_wallpaper", MapColor.SAND, SoundType.WOOD);
    public static final DeferredBlock<Block> MANILA_BROWN_WALL = structural("manila_brown_wall", MapColor.COLOR_BROWN, SoundType.WOOD);
    /**
     * One quarter of the Manila Room's octagonal wooden table: four make the table, each quarter's
     * cut corner pointing away from the middle (FACING north = the north-west quarter).
     */
    public static final DeferredBlock<FurnitureBlock> OCTAGON_TABLE = furniture("octagon_table", MapColor.WOOD, SoundType.WOOD, Shapes.or(
            Block.box(6, 12.5, 0, 16, 14.5, 16),
            Block.box(3, 12.5, 3, 6, 14.5, 16),
            Block.box(0, 12.5, 6, 3, 14.5, 16),
            Block.box(13, 0, 13, 16, 12.5, 16)));
    /** Plain wooden chair; FACING = the way you'd sit. */
    public static final DeferredBlock<FurnitureBlock> WOODEN_CHAIR = furniture("wooden_chair", MapColor.WOOD, SoundType.WOOD, Shapes.or(
            Block.box(3, 7, 3, 13, 8.5, 13),
            Block.box(3, 8.5, 11.5, 13, 16, 13),
            Block.box(3, 0, 3, 4.5, 7, 4.5),
            Block.box(11.5, 0, 3, 13, 7, 4.5),
            Block.box(3, 0, 11.5, 4.5, 7, 13),
            Block.box(11.5, 0, 11.5, 13, 7, 13)));
    /** A volunteer's note on the Manila Room's wall: right-click to read it. */
    public static final DeferredBlock<com.rickr.backrooms.block.VolunteerNoteBlock> VOLUNTEER_NOTE = BLOCKS.registerBlock("volunteer_note",
            com.rickr.backrooms.block.VolunteerNoteBlock::new, unbreakable(MapColor.SAND, SoundType.WOOD).noCollission().noOcclusion());
    /** The pale plaster of the Arch Variation (the stable landmark of the shifting pillar hall), and its arch pieces. */
    public static final DeferredBlock<Block> ARCH_WALL = structural("arch_wall", MapColor.TERRACOTTA_WHITE, SoundType.STONE);
    public static final DeferredBlock<StairBlock> ARCH_WALL_STAIRS = BLOCKS.registerBlock("arch_wall_stairs",
            props -> new StairBlock(ARCH_WALL.get().defaultBlockState(), props), unbreakable(MapColor.TERRACOTTA_WHITE, SoundType.STONE));

    // ---- Level 9 anomalies (world/anomaly/level9, world/layout/Level9AnomalyPlan) ----------------
    /** Tall wild grass of Level 9.1, the Crimson Field: you wade through it (no collision). */
    public static final DeferredBlock<FieldGrassBlock> L9_FIELD_GRASS = BLOCKS.registerBlock("l9_field_grass", FieldGrassBlock::new,
            unbreakable(MapColor.COLOR_GREEN, SoundType.GRASS).noCollission().noOcclusion().offsetType(BlockBehaviour.OffsetType.XZ));
    /** Heaved, shattered asphalt round the crater the Mangled fog's pillar leaves in the road. */
    public static final DeferredBlock<Block> L9_CRATER_ASPHALT = structural("l9_crater_asphalt", MapColor.COLOR_BLACK, SoundType.STONE);
    public static final DeferredBlock<SlabBlock> L9_CRATER_ASPHALT_SLAB = BLOCKS.registerBlock("l9_crater_asphalt_slab", SlabBlock::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.STONE));
    /** Churned wet earth: the bottom of a crater, and the mud round a foxhole hatch in the field. */
    public static final DeferredBlock<Block> L9_CRATER_SOIL = structural("l9_crater_soil", MapColor.DIRT, SoundType.GRAVEL);
    /** Stacked sandbags ringing a foxhole in the Crimson Field. */
    public static final DeferredBlock<Block> L9_SANDBAGS = structural("l9_sandbags", MapColor.COLOR_BROWN, SoundType.WOOL);

    // ---- Level 7 "Thalassophobia" (world/layout/Level7Layout) ---------------------------------
    // The sea under the concrete ceiling, the flooded entrance room hanging off that ceiling, and
    // the exit ring on the sea floor.
    /** Stained grey concrete: the ceiling over the whole sea, and the rubble blocking the room's stairs. */
    public static final DeferredBlock<Block> L7_CONCRETE = structural("l7_concrete", MapColor.STONE, SoundType.STONE);
    /** Riveted metal plates scabbed with rust: the entrance room's walls and roof. */
    public static final DeferredBlock<Block> L7_RUST_METAL = structural("l7_rust_metal", MapColor.TERRACOTTA_BROWN, SoundType.METAL);
    /** Rusty floor grating: the dry landing by the hatches. */
    public static final DeferredBlock<Block> L7_GRATING = structural("l7_grating", MapColor.TERRACOTTA_BROWN, SoundType.METAL);
    /** Waterlogged carpet: the room's floor, and the floor of the deep sea. */
    public static final DeferredBlock<Block> L7_CARPET = structural("l7_carpet", MapColor.COLOR_GRAY, SoundType.WOOL);
    /** Pale silt of bone dust: the shallows around the room. */
    public static final DeferredBlock<Block> L7_SILT = structural("l7_silt", MapColor.SAND, SoundType.SAND);
    /** Dark wet rock: under everything, the cliffs and the stacks that rise to the islands. */
    public static final DeferredBlock<Block> L7_ROCK = structural("l7_rock", MapColor.COLOR_GRAY, SoundType.STONE);
    /** A heap of old bones (and the giant ribcages on the sea floor). */
    public static final DeferredBlock<Block> L7_BONE = structural("l7_bone", MapColor.SAND, SoundType.BONE_BLOCK);
    /** Black glossy tar: the mounds in the deep. */
    public static final DeferredBlock<Block> L7_TAR = structural("l7_tar", MapColor.COLOR_BLACK, SoundType.MUD);
    /** Rusted scrap metal lying on the bottom. */
    public static final DeferredBlock<Block> L7_SCRAP = structural("l7_scrap", MapColor.TERRACOTTA_BROWN, SoundType.METAL);
    /** The giant pipes rising out of the dark around the exit. */
    public static final DeferredBlock<RotatedPillarBlock> L7_PIPE = BLOCKS.registerBlock("l7_pipe", RotatedPillarBlock::new,
            unbreakable(MapColor.TERRACOTTA_BROWN, SoundType.METAL));
    /** Weathered stone: the pillars around the exit. */
    public static final DeferredBlock<Block> L7_PILLAR = structural("l7_pillar", MapColor.STONE, SoundType.STONE);
    /** Carved stone with rings round it: the round platform the exit door lies in. */
    public static final DeferredBlock<Block> L7_PLATFORM = structural("l7_platform", MapColor.STONE, SoundType.STONE);
    /**
     * The exit: an old wooden door lying in the platform. Swim into it (to Level 9). A faint light
     * seeps round its edges (light 10), the only thing you can make out down there from afar.
     */
    public static final DeferredBlock<SeaDoorBlock> L7_EXIT_DOOR = BLOCKS.registerBlock("l7_exit_door", SeaDoorBlock::new,
            unbreakable(MapColor.WOOD, SoundType.WOOD).noCollission().noOcclusion().lightLevel(state -> 10));
    /** The rope from the room's landing down to the water: climb it (hold jump) to get back up. */
    public static final DeferredBlock<RopeBlock> L7_ROPE = BLOCKS.registerBlock("l7_rope", RopeBlock::new,
            unbreakable(MapColor.WOOD, SoundType.WOOL).noCollission().noOcclusion());
    /** The rusty metal hatches in the room's floor, over the sea. Open by hand. */
    public static final DeferredBlock<TrapDoorBlock> L7_HATCH = BLOCKS.registerBlock("l7_hatch",
            props -> new TrapDoorBlock(BlockSetType.COPPER, props), unbreakable(MapColor.TERRACOTTA_BROWN, SoundType.METAL).noOcclusion());
    /** A swollen bookcase of books that are only blocks of paper in dust jackets. */
    public static final DeferredBlock<Block> L7_BOOKCASE = structural("l7_bookcase", MapColor.WOOD, SoundType.WOOD);
    /** The room's low coffee table and its one armchair, standing in the water (they hold water themselves). */
    public static final DeferredBlock<WaterloggedFurnitureBlock> L7_COFFEE_TABLE = BLOCKS.registerBlock("l7_coffee_table",
            props -> new WaterloggedFurnitureBlock(props, Shapes.or(
                    Block.box(0, 12.5, 0, 16, 14.5, 16),
                    Block.box(1, 0, 1, 2.5, 12.5, 2.5),
                    Block.box(13.5, 0, 1, 15, 12.5, 2.5),
                    Block.box(1, 0, 13.5, 2.5, 12.5, 15),
                    Block.box(13.5, 0, 13.5, 15, 12.5, 15))),
            unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<WaterloggedFurnitureBlock> L7_ARMCHAIR = BLOCKS.registerBlock("l7_armchair",
            props -> new WaterloggedFurnitureBlock(props, Shapes.or(
                    Block.box(1, 0, 2, 15, 8, 15),
                    Block.box(1, 0, 11, 15, 16, 15),
                    Block.box(0, 0, 2, 3, 11, 15),
                    Block.box(13, 0, 2, 16, 11, 15))),
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOL).noOcclusion());

    private static DeferredBlock<Block> structural(String name, MapColor color, SoundType sound) {
        return BLOCKS.registerBlock(name, (Function<BlockBehaviour.Properties, Block>) Block::new, unbreakable(color, sound));
    }

    private static DeferredBlock<SurfaceBlock> surface(String name, MapColor color, SoundType sound, int... weights) {
        return BLOCKS.registerBlock(name, props -> new SurfaceBlock(props, weights), unbreakable(color, sound));
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

    /**
     * Level 94 vehicle blocks: solid to bump into, but see-through for light and the camera (the
     * renderer draws the body), so they never cast block-shaped shadows or suffocate anyone.
     */
    private static BlockBehaviour.Properties vehicleBody() {
        return unbreakable(MapColor.METAL, SoundType.METAL)
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
