package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.FloorArrowBlock;
import com.rickr.backrooms.block.PillarNumberBlock;
import com.rickr.backrooms.block.PoolLampBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.block.SupplyCrateBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.block.WallLampBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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
 * <p>Footstep sounds: carpet floors are {@link SoundType#WOOL}, garage floors {@link SoundType#STONE},
 * Level 2 floors {@link SoundType#METAL}.
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

    // Level 2: Pipe Dreams
    public static final DeferredBlock<Block> PIPE_WALL = structural("pipe_wall", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE);
    public static final DeferredBlock<Block> RUSTY_PIPE = structural("rusty_pipe", MapColor.COLOR_ORANGE, SoundType.METAL);
    public static final DeferredBlock<Block> PALE_DUCT = structural("pale_duct", MapColor.COLOR_LIGHT_GRAY, SoundType.METAL);
    public static final DeferredBlock<Block> PIPE_FLOOR = structural("pipe_floor", MapColor.COLOR_PURPLE, SoundType.METAL);
    public static final DeferredBlock<Block> PIPE_CEILING = structural("pipe_ceiling", MapColor.COLOR_GRAY, SoundType.STONE);
    public static final DeferredBlock<Block> PIPE_LIGHT = BLOCKS.registerBlock("pipe_light", Block::new,
            fixture(MapColor.SNOW, SoundType.METAL, 11));
    public static final DeferredBlock<WallLampBlock> BULKHEAD_LAMP = BLOCKS.registerBlock("bulkhead_lamp",
            props -> new WallLampBlock(props, 8, 3, 11, 5), wallFixture(MapColor.SNOW, 10));

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
