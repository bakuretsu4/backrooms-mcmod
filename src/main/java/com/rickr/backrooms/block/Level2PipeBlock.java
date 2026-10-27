package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block length of Level 2 pipework: a single pipe, a pair or a rack of three, running along
 * AXIS. The same class makes two blocks: {@code l2_pipe} (plain lengths) and {@code l2_flange}
 * (the joint between two lengths: bolted flanges on steel, a soldered sleeve on copper, a strap
 * on insulation, the painted label on service pipes). Both take exactly the same states, so the
 * world generator can swap one for the other every few blocks.
 *
 * <ul>
 *   <li>KIND = what the pipe is made of (rusty steel, copper, insulated main, painted service
 *       pipe in four colours, electrical conduit).</li>
 *   <li>SIZE = SMALL: three thin pipes side by side; MEDIUM: two pipes; LARGE: one fat main.</li>
 *   <li>SLOT = which side of the block the pipes sit against. A rack on the north wall of a
 *       corridor running east-west is AXIS = X, SLOT = NORTH; UP hangs them from the ceiling,
 *       DOWN stands them on the floor, CENTER puts ONE pipe in the middle of the block (that is
 *       the position the elbows and tees join). A SLOT along the pipe's own AXIS (say AXIS = X,
 *       SLOT = EAST) makes no sense and is drawn as CENTER.</li>
 * </ul>
 * The numbers below must match the models in tools/anomaly_assets/level2_revamp.py.
 */
public class Level2PipeBlock extends Block {

    /** What the pipe is made of. Painted pipes carry a stencilled label (see l2_flange). */
    public enum Kind implements StringRepresentable {
        STEEL, COPPER, INSULATED, PAINTED_RED, PAINTED_BLUE, PAINTED_YELLOW, PAINTED_GREEN, CONDUIT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How many pipes and how thick (diameter in pixels, 16 to a block). */
    public enum Size implements StringRepresentable {
        SMALL(3, 3.0, 3.25, new double[] {2.5, 8, 13.5}),
        MEDIUM(2, 5.0, 4.25, new double[] {4, 12}),
        LARGE(1, 10, 6.5, new double[] {8});

        /** Number of pipes in the rack. */
        public final int count;
        /** Pipe diameter in pixels. */
        public final double diameter;
        /** Distance from the surface the pipes sit against to their centre line. */
        final double offset;
        /** Centre of each pipe, measured along the surface. */
        final double[] spread;

        Size(int count, double diameter, double offset, double[] spread) {
            this.count = count;
            this.diameter = diameter;
            this.offset = offset;
            this.spread = spread;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The side of the block the pipes sit against, or CENTER for one pipe in the middle. */
    public enum Slot implements StringRepresentable {
        NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), EAST(Direction.EAST), WEST(Direction.WEST),
        UP(Direction.UP), DOWN(Direction.DOWN), CENTER(null);

        /** The face the pipes hug (null for CENTER). */
        public final Direction side;

        Slot(Direction side) {
            this.side = side;
        }

        /** The slot for pipes against the given face of the block. */
        public static Slot against(Direction side) {
            return switch (side) {
                case NORTH -> NORTH;
                case SOUTH -> SOUTH;
                case EAST -> EAST;
                case WEST -> WEST;
                case UP -> UP;
                case DOWN -> DOWN;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Size> SIZE = EnumProperty.create("size", Size.class);
    public static final EnumProperty<Slot> SLOT = EnumProperty.create("slot", Slot.class);

    // [axis][slot][size], worked out once so looking a shape up allocates nothing.
    private static final VoxelShape[][][] SHAPES = new VoxelShape[3][Slot.values().length][Size.values().length];

    static {
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (Slot slot : Slot.values()) {
                for (Size size : Size.values()) {
                    SHAPES[axis.ordinal()][slot.ordinal()][size.ordinal()] = rackShape(axis, slot, size);
                }
            }
        }
    }

    public Level2PipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(KIND, Kind.STEEL)
                .setValue(SIZE, Size.MEDIUM).setValue(SLOT, Slot.CENTER));
    }

    /** True when SLOT really is a side the pipes can lie against (not along the pipe itself). */
    public static boolean slotFits(Direction.Axis axis, Slot slot) {
        return slot.side != null && slot.side.getAxis() != axis;
    }

    /**
     * The pipes' outline: one box per pipe, the pipe's diameter square, end to end along AXIS.
     * Pipe centres: {@code offset} out from the surface, spread along it (see {@link Size}).
     */
    static VoxelShape rackShape(Direction.Axis axis, Slot slot, Size size) {
        if (!slotFits(axis, slot)) {
            return pipeBox(axis, 8, 8, 8, size.diameter);
        }
        Direction side = slot.side;
        // The pipes are spread along the cross-section axis that is neither the pipe's nor the side's.
        Direction.Axis spreadAxis = otherAxis(axis, side.getAxis());
        double across = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - size.offset : size.offset;
        VoxelShape shape = Shapes.empty();
        for (double along : size.spread) {
            double[] c = {8, 8, 8};
            c[side.getAxis().ordinal()] = across;
            c[spreadAxis.ordinal()] = along;
            shape = Shapes.or(shape, pipeBox(axis, c[0], c[1], c[2], size.diameter));
        }
        return shape.optimize();
    }

    /** The third axis, given two different ones. */
    static Direction.Axis otherAxis(Direction.Axis a, Direction.Axis b) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != a && axis != b) {
                return axis;
            }
        }
        throw new IllegalArgumentException("same axis twice: " + a);
    }

    /** A square box of the given diameter around (cx, cy, cz), running the full block along AXIS. */
    static VoxelShape pipeBox(Direction.Axis axis, double cx, double cy, double cz, double diameter) {
        double r = diameter / 2;
        return Block.box(
                axis == Direction.Axis.X ? 0 : cx - r, axis == Direction.Axis.Y ? 0 : cy - r, axis == Direction.Axis.Z ? 0 : cz - r,
                axis == Direction.Axis.X ? 16 : cx + r, axis == Direction.Axis.Y ? 16 : cy + r, axis == Direction.Axis.Z ? 16 : cz + r);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, KIND, SIZE, SLOT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AXIS).ordinal()][state.getValue(SLOT).ordinal()][state.getValue(SIZE).ordinal()];
    }

    /** In creative: the pipe runs along the face you click, against that face (or across a floor/ceiling). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction.Axis axis = face.getAxis().isHorizontal()
                ? (context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? Direction.Axis.Y : face.getClockWise().getAxis())
                : context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis).setValue(SLOT, Slot.against(face.getOpposite()));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Slot slot = state.getValue(SLOT);
        if (slot.side != null && slot.side.getAxis().isHorizontal()) {
            state = state.setValue(SLOT, Slot.against(rotation.rotate(slot.side)));
        }
        Direction.Axis axis = state.getValue(AXIS);
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            state = state.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
