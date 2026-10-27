package com.rickr.backrooms.block;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One piece of a smooth shingle roof on a Level 39 house. Instead of vanilla stairs (which make
 * a roof look like a staircase) every piece holds part of a flat sloping plane, so a run of them
 * reads as one clean roof.
 *
 * <p>This block is the steep roof: it rises one block for every block across (45 degrees). The
 * low roof of ranches and porches ({@link Level39LowRoofBlock}) rises half a block per block.
 *
 * <ul>
 *   <li>FACING: the way the slope rises (like stairs: the high side is toward FACING).</li>
 *   <li>SHAPE: a plain slope, or where slopes meet: OUTER (a hip corner: the slope rising toward
 *       FACING meets the one rising toward FACING's left), INNER (a valley between those two),
 *       RIDGE (two slopes meeting in the middle of the block: up from the FACING side and up
 *       from the opposite side), RIDGE_END (the end of a hip ridge: three slopes, all but the one
 *       rising away from FACING) and APEX (the top of a pyramid roof: four slopes).</li>
 *   <li>TRIM: NONE; EAVE (a white fascia board along the low edge, for the bottom row of a roof);
 *       GABLE (the piece sits on a gable wall: the space under the slope is filled with clapboard
 *       in the house's SHADE, with a white rake board under the shingles, so a gable is a clean
 *       triangle instead of a staircase).</li>
 *   <li>INVERTED: upside down, for the inverted houses (like {@link Level39FurnitureBlock}).</li>
 * </ul>
 *
 * <p>The collision is a stair-like approximation: each quarter of the block is solid up to the
 * roof surface above its middle, rounded up to an eighth of a block.
 */
public class Level39RoofBlock extends Block {
    public enum Shape implements StringRepresentable {
        STRAIGHT, OUTER, INNER, RIDGE, RIDGE_END, APEX;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public enum Trim implements StringRepresentable {
        NONE, EAVE, GABLE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final EnumProperty<Trim> TRIM = EnumProperty.create("trim", Trim.class);
    public static final IntegerProperty SHADE = Level39KnockWallBlock.SHADE;
    public static final BooleanProperty INVERTED = Level39FurnitureBlock.INVERTED;

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public Level39RoofBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, Shape.STRAIGHT)
                .setValue(TRIM, Trim.NONE)
                .setValue(SHADE, 0)
                .setValue(INVERTED, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, buildShape(state));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, TRIM, SHADE, INVERTED);
    }

    /** How much of the block's height the slope starts at (the low roof's upper half starts at 0.5). */
    protected double base(BlockState state) {
        return 0.0;
    }

    /** Rise across the block: 1 for this steep roof, 0.5 for the low one. */
    protected double rise() {
        return 1.0;
    }

    /** Up-slope distance (0 at the low edge, 1 at the high edge) of a slope rising toward d. */
    private static double along(Direction d, double x, double z) {
        return switch (d) {
            case NORTH -> 1 - z;
            case SOUTH -> z;
            case EAST -> x;
            default -> 1 - x;
        };
    }

    /** Height of the roof surface (0..1 of the block) at (x, z) inside the block, right way up. */
    public double surface(BlockState state, double x, double z) {
        Direction f = state.getValue(FACING);
        double h = switch (state.getValue(SHAPE)) {
            case STRAIGHT -> along(f, x, z);
            case OUTER -> Math.min(along(f, x, z), along(f.getCounterClockWise(), x, z));
            case INNER -> Math.max(along(f, x, z), along(f.getCounterClockWise(), x, z));
            case RIDGE -> Math.min(along(f, x, z), along(f.getOpposite(), x, z));
            case RIDGE_END -> Math.min(along(f, x, z),
                    Math.min(along(f.getClockWise(), x, z), along(f.getCounterClockWise(), x, z)));
            case APEX -> Math.min(Math.min(along(Direction.NORTH, x, z), along(Direction.SOUTH, x, z)),
                    Math.min(along(Direction.EAST, x, z), along(Direction.WEST, x, z)));
        };
        return base(state) + rise() * h;
    }

    private VoxelShape buildShape(BlockState state) {
        VoxelShape out = Shapes.empty();
        boolean inverted = state.getValue(INVERTED);
        for (int qx = 0; qx < 2; qx++) {
            for (int qz = 0; qz < 2; qz++) {
                double h = surface(state, qx * 0.5 + 0.25, qz * 0.5 + 0.25);
                h = Math.max(1.0 / 8, Math.min(1.0, Math.ceil(h * 8 - 1e-6) / 8));
                double y0 = inverted ? 1 - h : 0;
                double y1 = inverted ? 1 : h;
                out = Shapes.or(out, Shapes.box(qx * 0.5, y0, qz * 0.5, qx * 0.5 + 0.5, y1, qz * 0.5 + 0.5));
            }
        }
        return out.optimize();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = shapes.get(state);
        return shape != null ? shape : Shapes.block();
    }

    /** In creative: placed rising away from the player, like stairs. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
