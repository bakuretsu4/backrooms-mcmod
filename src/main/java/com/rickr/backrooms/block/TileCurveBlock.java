package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Level 37's curved tile: one block-sized piece of a round pillar, a rounded room corner, a wavy
 * wall, a round pool or an arch (see {@link CurveShape} for which piece, {@link CurveOrientation}
 * for which way round). The layout builds every curve in the level out of these, so walls and
 * pillars are really round instead of stepped.
 *
 * <p>The models are OBJ files with smooth normals, so the dynamic lights shade them like real
 * cylinders. The block doesn't block light (it's not a full cube), and it can stand in pool water
 * ({@link #WATERLOGGED}, pool and pit walls) or in the ankle-deep shallows ({@link Flooding#FLOODED},
 * the foot of a pillar on a flooded floor).
 */
public class TileCurveBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<TileCurveBlock> CODEC = simpleCodec(TileCurveBlock::new);
    public static final EnumProperty<CurveShape> SHAPE = EnumProperty.create("shape", CurveShape.class);
    public static final EnumProperty<CurveOrientation> ORIENTATION = EnumProperty.create("orientation", CurveOrientation.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty FLOODED = Flooding.FLOODED;

    /** Collision shapes per [shape][orientation], built on first use. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[CurveShape.values().length][CurveOrientation.values().length];
    /** Collision resolution: the cross-section is sampled on an 8 x 8 grid (2 px steps). */
    private static final int GRID = 8;

    public TileCurveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SHAPE, CurveShape.CONVEX_R1_0_0)
                .setValue(ORIENTATION, CurveOrientation.SE)
                .setValue(WATERLOGGED, false)
                .setValue(FLOODED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, ORIENTATION, WATERLOGGED, FLOODED);
    }

    /** The piece {@code shape}, turned to {@code orientation}. */
    public BlockState piece(CurveShape shape, CurveOrientation orientation) {
        return defaultBlockState().setValue(SHAPE, shape).setValue(ORIENTATION, orientation);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state.getValue(SHAPE), state.getValue(ORIENTATION));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * Two identical pieces stacked along their axis (a pillar, a tunnel vault) hide the end faces
     * they press together, like full blocks do. (Faces against solid tile are hidden anyway.)
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        if (adjacent.is(this) && adjacent.getValue(SHAPE) == state.getValue(SHAPE)
                && adjacent.getValue(ORIENTATION) == state.getValue(ORIENTATION)
                && direction.getAxis() == axisOf(state.getValue(ORIENTATION))) {
            return true;
        }
        return super.skipRendering(state, adjacent, direction);
    }

    /** The axis a piece's circle runs along: up for upright pieces, along the tunnel for arches. */
    private static Direction.Axis axisOf(CurveOrientation orientation) {
        if (!orientation.horizontal) {
            return Direction.Axis.Y;
        }
        return orientation == CurveOrientation.X_SOUTH || orientation == CurveOrientation.X_NORTH
                ? Direction.Axis.X : Direction.Axis.Z;
    }

    /** Wading through the shallows around a pillar's foot drags like the open shallows do. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(FLOODED)) {
            ShallowWaterBlock.applyDrag(entity, pos);
        }
    }

    /** The solid part of a piece as boxes: the cross-section sampled every 2 px, extruded along the axis. */
    private static VoxelShape shapeOf(CurveShape shape, CurveOrientation orientation) {
        VoxelShape cached = SHAPES[shape.ordinal()][orientation.ordinal()];
        if (cached != null) {
            return cached;
        }
        VoxelShape result = Shapes.empty();
        double[] a = new double[3];
        double[] b = new double[3];
        for (int col = 0; col < GRID; col++) {
            double x = (col + 0.5) / GRID;
            int row = 0;
            while (row < GRID) {
                if (!shape.isSolidAt(x, (row + 0.5) / GRID)) {
                    row++;
                    continue;
                }
                int start = row;
                while (row < GRID && shape.isSolidAt(x, (row + 0.5) / GRID)) {
                    row++;
                }
                // One box of the upright (SE) piece, then turned like the model.
                a[0] = (double) col / GRID;
                a[1] = 0.0;
                a[2] = (double) start / GRID;
                b[0] = (double) (col + 1) / GRID;
                b[1] = 1.0;
                b[2] = (double) row / GRID;
                orientation.apply(a);
                orientation.apply(b);
                result = Shapes.or(result, Shapes.box(
                        Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                        Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
            }
        }
        result = result.optimize();
        SHAPES[shape.ordinal()][orientation.ordinal()] = result;
        return result;
    }
}
