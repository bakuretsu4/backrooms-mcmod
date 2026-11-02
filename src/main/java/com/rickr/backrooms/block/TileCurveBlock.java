package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
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
 * <p>The shapes are OBJ models (written by {@code tools/gen_assets.py}) with smooth normals, but
 * Minecraft's block lighting can't light a curved surface evenly (one light value per block, or
 * corner values meant for cube faces), so the normal chunk mesher skips this block
 * ({@link #getRenderShape}) and {@code client/CurveTileRenderer} draws the pieces into the chunk
 * instead, with light and shading that run smoothly round the curve and match the flat walls.
 * The block doesn't block light (it's not a full cube), and it can stand in pool water
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

    /** Drawn by client/CurveTileRenderer while the chunk is built, not by the normal block mesher. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * Faces on the block's boundary are hidden where the piece next door is solid wherever this one
     * is: a piece of the same circle (two pieces of a ring, a pillar or a vault stacked along its
     * axis), or of a circle on the same centre line that is solid over more (a radius-3 tunnel ring
     * against a radius-4 one: concave = solid outside the circle, so the smaller radius covers the
     * bigger one; convex the other way round). Without this both pieces drew their face there, back
     * to back inside the tile. (Faces against solid tile are hidden anyway, like any block's.)
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        if (adjacent.is(this) && covers(state, adjacent, direction)) {
            return true;
        }
        return super.skipRendering(state, adjacent, direction);
    }

    /** True when {@code next} (the piece one step {@code direction} from {@code piece}) is solid everywhere {@code piece} is. */
    private static boolean covers(BlockState piece, BlockState next, Direction direction) {
        CurveShape a = piece.getValue(SHAPE);
        CurveShape b = next.getValue(SHAPE);
        CurveOrientation oa = piece.getValue(ORIENTATION);
        CurveOrientation ob = next.getValue(ORIENTATION);
        Direction.Axis axis = axisOf(oa);
        if (axis != axisOf(ob) || a.convex != b.convex || (a.convex ? b.radius < a.radius : b.radius > a.radius)) {
            return false;
        }
        // Both circles' centre lines, relative to this piece's cell; equal (across the axis) = same line.
        double[] ca = centreLine(a, oa);
        double[] cb = centreLine(b, ob);
        cb[0] += direction.getStepX();
        cb[1] += direction.getStepY();
        cb[2] += direction.getStepZ();
        for (Direction.Axis other : Direction.Axis.values()) {
            if (other != axis && Math.abs(other.choose(ca[0], ca[1], ca[2]) - other.choose(cb[0], cb[1], cb[2])) > 1.0e-6) {
                return false;
            }
        }
        return true;
    }

    /** Where the piece's circle centre line passes, relative to the piece's own cell corner (block units). */
    private static double[] centreLine(CurveShape shape, CurveOrientation orientation) {
        // In the drawn (SE) piece the centre sits i blocks west and j blocks north of the cell.
        double[] p = {-shape.i, 0.0, -shape.j};
        orientation.apply(p);
        return p;
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
