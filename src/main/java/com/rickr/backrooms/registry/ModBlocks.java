package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.DuctBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.FloorArrowBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.HighwaySignBlock;
import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.MarkingBlock;
import com.rickr.backrooms.block.PillarNumberBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.PoleBlock;
import com.rickr.backrooms.block.PoolLampBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.block.StreetSignBlock;
import com.rickr.backrooms.block.StreetlampHeadBlock;
import com.rickr.backrooms.block.SupplyCrateBlock;
import com.rickr.backrooms.block.ThinLayerBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
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

    // Exits and loot
    public static final DeferredBlock<Block> EXIT_SIGN = BLOCKS.registerBlock("exit_sign", Block::new,
            fixture(MapColor.COLOR_GREEN, SoundType.GLASS, 9));
    public static final DeferredBlock<ExitDoorBlock> EXIT_DOOR = BLOCKS.registerBlock("exit_door", ExitDoorBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.METAL).noOcclusion());
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE = BLOCKS.registerBlock("supply_crate", SupplyCrateBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK).noLootTable());

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
