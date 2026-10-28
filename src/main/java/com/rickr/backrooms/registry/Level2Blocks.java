package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.Level2BulkheadFrameBlock;
import com.rickr.backrooms.block.Level2CableTrayBlock;
import com.rickr.backrooms.block.Level2DripBlock;
import com.rickr.backrooms.block.Level2ExitBulkheadBlock;
import com.rickr.backrooms.block.Level2FeedPipeBlock;
import com.rickr.backrooms.block.Level2GratingBlock;
import com.rickr.backrooms.block.Level2LeverValveBlock;
import com.rickr.backrooms.block.Level2PipeBlock;
import com.rickr.backrooms.block.Level2PipeElbowBlock;
import com.rickr.backrooms.block.Level2PipeEndBlock;
import com.rickr.backrooms.block.Level2PipeTeeBlock;
import com.rickr.backrooms.block.Level2PumpBlock;
import com.rickr.backrooms.block.Level2ShelfBlock;
import com.rickr.backrooms.block.Level2StencilBlock;
import com.rickr.backrooms.block.Level2TallBlock;
import com.rickr.backrooms.block.Level2ValveBlock;
import com.rickr.backrooms.block.Level2SurfaceBlock;
import com.rickr.backrooms.block.SurfaceBlock;
import com.rickr.backrooms.block.ThinLayerBlock;
import com.rickr.backrooms.block.WallMountBlock;
import java.util.function.Function;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The extra blocks of the Level 2 "Pipe Dreams" revamp: one long straight maintenance corridor
 * lined with pipework, with pump bays, bulkhead rings, maintenance rooms and an exit bay. The
 * corridor's concrete, the caged lamps and the anomaly blocks stay in {@link ModBlocks}.
 *
 * <p>Like every Backrooms block they go through the shared {@link ModBlocks#BLOCKS} register, and
 * their items through {@link ModItems#ITEMS}. The mod constructor only calls {@link #bootstrap()},
 * which makes Java load this class in time. Textures, models and blockstates come from
 * tools/anomaly_assets/level2_revamp.py.
 *
 * <p>Two rules hold for everything here, as in the rest of the mod:
 * <ul>
 *   <li>FACING on things fixed to a wall points AWAY from the wall, into the corridor or room
 *       (see {@link WallMountBlock}). Free-standing furniture: FACING = the side you look at.</li>
 *   <li>Pipes (PIPE, FLANGE, PIPE_ELBOW, PIPE_TEE) share KIND and SIZE (see {@link Level2PipeBlock}).
 *       Elbows and tees always carry ONE pipe through the middle of the block, so they join PIPE
 *       blocks with SLOT = CENTER. PIPE_END finishes a wall rack (same KIND and SIZE as the rack).</li>
 * </ul>
 */
public final class Level2Blocks {

    // ================================================================== Pipework
    /** A length of pipe: AXIS, KIND, SIZE (3 small / 2 medium / 1 large) and SLOT (the side they hug). */
    public static final DeferredBlock<Level2PipeBlock> PIPE = block("l2_pipe", Level2PipeBlock::new,
            metal(MapColor.COLOR_ORANGE).noOcclusion());
    /** The joint between two lengths: same states as PIPE (flanges, sleeve, strap, or the painted label). */
    public static final DeferredBlock<Level2PipeBlock> FLANGE = block("l2_flange", Level2PipeBlock::new,
            metal(MapColor.COLOR_ORANGE).noOcclusion());
    /** A bend between a horizontal leg (FACING) and a vertical one (VERTICAL up/down). */
    public static final DeferredBlock<Level2PipeElbowBlock> PIPE_ELBOW = block("l2_pipe_elbow", Level2PipeElbowBlock::new,
            metal(MapColor.COLOR_ORANGE).noOcclusion());
    /** A straight run along AXIS with a branch out through FACING. */
    public static final DeferredBlock<Level2PipeTeeBlock> PIPE_TEE = block("l2_pipe_tee", Level2PipeTeeBlock::new,
            metal(MapColor.COLOR_ORANGE).noOcclusion());
    /** A gate valve with a hand wheel on a stub out of the wall; COLOR red or grey. */
    public static final DeferredBlock<Level2ValveBlock> VALVE_WHEEL = block("l2_valve_wheel", Level2ValveBlock::new,
            metal(MapColor.COLOR_RED).noOcclusion());
    /** A round pressure gauge on a pigtail pipe out of the wall. */
    public static final DeferredBlock<WallMountBlock> GAUGE = block("l2_gauge",
            props -> new WallMountBlock(props, 4, 2, 10, 12, 13, 16), metal(MapColor.METAL).noOcclusion());
    /** Where a wall rack stops: its pipes bend into the wall, or pass into a pier through a bolted collar. */
    public static final DeferredBlock<Level2PipeEndBlock> PIPE_END = block("l2_pipe_end", Level2PipeEndBlock::new,
            metal(MapColor.COLOR_ORANGE).noOcclusion());
    /** A branch dropping from a ceiling header down the wall into a valve (the manifold's tee). */
    public static final DeferredBlock<Level2FeedPipeBlock> FEED_PIPE = block("l2_feed_pipe", Level2FeedPipeBlock::new,
            metal(MapColor.COLOR_ORANGE).noOcclusion());
    /** A quarter-turn ball valve with a lever handle: OPEN (handle along the pipe) or shut, FEED from above. */
    public static final DeferredBlock<Level2LeverValveBlock> VALVE_LEVER = block("l2_valve_lever", Level2LeverValveBlock::new,
            metal(MapColor.COLOR_RED).noOcclusion());

    // ================================================================== Electrics
    /** A ladder cable tray hung on rods under the ceiling, full of cables; AXIS, BEAM above it, DROOP of loose cable. */
    public static final DeferredBlock<Level2CableTrayBlock> CABLE_TRAY = block("l2_cable_tray",
            Level2CableTrayBlock::new, metal(MapColor.METAL).noOcclusion());
    /** A grey steel junction box with a conduit running down out of it to the floor. */
    public static final DeferredBlock<WallMountBlock> JUNCTION_BOX = block("l2_junction_box",
            props -> new WallMountBlock(props, 3, 0, 11, 13, 13, 16), metal(MapColor.METAL).noOcclusion());
    /** A tall breaker panel (two blocks, HALF) with conduits rising out of its top. */
    public static final DeferredBlock<Level2TallBlock> BREAKER_PANEL = block("l2_breaker_panel",
            props -> new Level2TallBlock(props, Block.box(2, 3, 12, 14, 16, 16), Block.box(2, 0, 12, 14, 16, 16)),
            metal(MapColor.COLOR_GRAY).noOcclusion());

    // ================================================================== Rooms
    /** Grey steel shelving, two blocks tall (HALF); VARIANT 0..3 = what is on it. */
    public static final DeferredBlock<Level2ShelfBlock> SHELF = block("l2_shelf",
            props -> new Level2ShelfBlock(props, Block.box(0, 0, 3, 16, 16, 16), Block.box(0, 0, 3, 16, 16, 16)),
            metal(MapColor.COLOR_GRAY).noOcclusion());
    /** A steel workbench with a worn wooden top, a vice and a few tools. */
    public static final DeferredBlock<FurnitureBlock> WORKBENCH = block("l2_workbench",
            props -> new FurnitureBlock(props, Block.box(0, 0, 1, 16, 15, 16)), metal(MapColor.WOOD).noOcclusion());
    /** The pump set: 2 x 2 x 2 blocks, PART 0..7 (use {@link Level2PumpBlock#partAt}). */
    public static final DeferredBlock<Level2PumpBlock> PUMP = block("l2_pump", Level2PumpBlock::new,
            metal(MapColor.COLOR_GREEN).noOcclusion());
    /** A square duct fan housing; FACING = the side with the round grille and the fan behind it. */
    public static final DeferredBlock<FurnitureBlock> FAN_HOUSING = block("l2_fan_housing",
            props -> new FurnitureBlock(props, Shapes.block()), metal(MapColor.METAL).noOcclusion());

    // ================================================================== Structure
    /** A piece of a steel bulkhead ring with hazard stripes; PART side / top / corner. */
    public static final DeferredBlock<Level2BulkheadFrameBlock> BULKHEAD_FRAME = block("l2_bulkhead_frame",
            Level2BulkheadFrameBlock::new, metal(MapColor.COLOR_YELLOW).noOcclusion());
    /** A steel maintenance door that opens by hand (LOCKED ones only rattle), like the void door. */
    public static final DeferredBlock<HouseDoorBlock> STEEL_DOOR = block("l2_steel_door",
            props -> new HouseDoorBlock(BlockSetType.COPPER, props), metal(MapColor.METAL).noOcclusion());
    /** The way out: a heavy EXIT bulkhead door (two blocks, HALF, in place of the wall), works like EXIT_DOOR. */
    public static final DeferredBlock<Level2ExitBulkheadBlock> EXIT_BULKHEAD = block("l2_exit_bulkhead",
            Level2ExitBulkheadBlock::new, metal(MapColor.COLOR_GRAY).noOcclusion());
    /** Stencilled paint on a wall: VARIANT = section number, warning, arrow or pipe label. Walk-through. */
    public static final DeferredBlock<Level2StencilBlock> STENCIL = block("l2_stencil", Level2StencilBlock::new,
            metal(MapColor.COLOR_YELLOW).noOcclusion().noCollission());
    /**
     * A steel I-beam under the ceiling slab, running along AXIS, where nothing hangs below it (pipes
     * and trays under a beam carry it as their BEAM state instead). Same model as that BEAM.
     */
    public static final DeferredBlock<AxisShapeBlock> BEAM = block("l2_beam",
            props -> new AxisShapeBlock(props, Block.box(0, 9, 5, 16, 16, 11)), metal(MapColor.METAL).noOcclusion());

    // ================================================================== Floor and ceiling
    /** Walkable steel bar grating you can see through; bars along AXIS. */
    public static final DeferredBlock<Level2GratingBlock> GRATING = block("l2_grating", Level2GratingBlock::new,
            metal(MapColor.COLOR_GRAY).noOcclusion());
    /** A floor block with a drainage channel along AXIS (dark wet grime at the bottom). */
    public static final DeferredBlock<AxisShapeBlock> GUTTER = block("l2_gutter",
            props -> new AxisShapeBlock(props, Shapes.or(Block.box(0, 0, 0, 16, 12, 16),
                    Block.box(0, 12, 0, 16, 16, 5), Block.box(0, 12, 11, 16, 16, 16))),
            unbreakable(MapColor.COLOR_LIGHT_GRAY, SoundType.STONE).noOcclusion());
    /** The corridor floor with a worn yellow lane line painted along its FACING edge. */
    public static final DeferredBlock<FurnitureBlock> FLOOR_LINE = block("l2_floor_line",
            props -> new FurnitureBlock(props, Shapes.block()), unbreakable(MapColor.COLOR_LIGHT_GRAY, SoundType.STONE));
    /** A leak under the ceiling: wet rust stain and a hanging drop; drips now and then. Walk-through. */
    public static final DeferredBlock<Level2DripBlock> DRIP = block("l2_drip", Level2DripBlock::new,
            unbreakable(MapColor.COLOR_BROWN, SoundType.STONE).noOcclusion().noCollission());
    /** The puddle under a leak: a film of dirty water on the floor (walk-through). */
    public static final DeferredBlock<ThinLayerBlock> LEAK_PUDDLE = block("l2_leak_puddle", ThinLayerBlock::new,
            unbreakable(MapColor.WATER, SoundType.MUD).noOcclusion().noCollission());

    // ================================================================== Walls
    /**
     * The lowest row of corridor wall (just above the floor): the same poured concrete as
     * ModBlocks.PIPE_WALL with a darker scuffed band at the bottom. Versions: plain, rust run-off
     * down to the floor, tide lines where water once stood (mostly in damp stretches, see
     * block/Level2SurfaceBlock), a chipped kicker. Counts must match gen_assets SURFACE_VARIANTS.
     */
    public static final DeferredBlock<SurfaceBlock> WALL_BASE = block("l2_wall_base",
            props -> new Level2SurfaceBlock(props, new int[] {18, 2, 1, 2}, new int[] {6, 3, 10, 1},
                    Level2SurfaceBlock.ANY_ROW, Level2SurfaceBlock.ANY_ROW, Level2SurfaceBlock.ANY_ROW, Level2SurfaceBlock.ANY_ROW),
            unbreakable(MapColor.STONE, SoundType.STONE));

    static {
        // Block items so builders can use every Level 2 block in creative (the tall ones need the tall item).
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {
                PIPE, FLANGE, PIPE_ELBOW, PIPE_TEE, PIPE_END, FEED_PIPE, VALVE_WHEEL, VALVE_LEVER, GAUGE, CABLE_TRAY,
                JUNCTION_BOX, WORKBENCH, PUMP, FAN_HOUSING, BULKHEAD_FRAME, EXIT_BULKHEAD, STENCIL, BEAM, GRATING, GUTTER,
                FLOOR_LINE, DRIP, LEAK_PUDDLE, WALL_BASE}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
        ModItems.ITEMS.registerItem("l2_breaker_panel", props -> new DoubleHighBlockItem(BREAKER_PANEL.get(), props), new Item.Properties());
        ModItems.ITEMS.registerItem("l2_shelf", props -> new DoubleHighBlockItem(SHELF.get(), props), new Item.Properties());
        ModItems.ITEMS.registerItem("l2_steel_door", props -> new DoubleHighBlockItem(STEEL_DOOR.get(), props), new Item.Properties());
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in ModBlocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return unbreakable(color, SoundType.METAL);
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

    private Level2Blocks() {}
}
