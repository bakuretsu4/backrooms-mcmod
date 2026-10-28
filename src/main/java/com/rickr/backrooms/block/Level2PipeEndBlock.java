package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a run of Level 2 pipes stops: real pipes never just end in mid-air, they turn into the
 * wall or the ceiling, or pass into a concrete pier through a bolted wall collar. Same KIND and
 * SIZE as the {@link Level2PipeBlock} run it finishes (a rack against the wall, SLOT = the wall
 * side, or a run HUNG under the ceiling). FACING points away from the wall, into the corridor (as
 * for every {@link WallMountBlock}); for a hung run it is either side square to the run.
 *
 * <p>FORM says where the pipes come from and what they do here. "CCW" means the run arrives from
 * the side {@code FACING.getCounterClockWise()} (in the model, which faces north, that is the
 * west end), "CW" from the other end.
 * <ul>
 *   <li>BEND_CCW / BEND_CW: the pipes bend 90 degrees and disappear into the wall behind,
 *       through a round collar plate on the wall.</li>
 *   <li>COLLAR_CCW / COLLAR_CW: the pipes run straight on into the solid block at the far end
 *       (a pier), through a square bolted collar on its face.</li>
 *   <li>RISER: pipes standing up the wall from below bend into the wall in this block.</li>
 *   <li>HUNG_CCW / HUNG_CW: pipes hung under the ceiling bend up into the slab through a collar.</li>
 * </ul>
 * The models come from tools/anomaly_assets/level2_revamp.py (_pipe_end_elements).
 */
public class Level2PipeEndBlock extends WallMountBlock {

    public enum Form implements StringRepresentable {
        BEND_CCW, BEND_CW, COLLAR_CCW, COLLAR_CW, RISER, HUNG_CCW, HUNG_CW;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);
    public static final EnumProperty<Level2PipeBlock.Kind> KIND = Level2PipeBlock.KIND;
    public static final EnumProperty<Level2PipeBlock.Size> SIZE = Level2PipeBlock.SIZE;

    // [facing 2D index][wall rack, riser, hung run][size]: the outline of the run it ends, turned like the model.
    private static final VoxelShape[][][] SHAPES = new VoxelShape[4][3][Level2PipeBlock.Size.values().length];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Level2PipeBlock.Size size : Level2PipeBlock.Size.values()) {
                // Facing north the wall is on the south side: a rack along x (or up y) against it.
                SHAPES[facing.get2DDataValue()][0][size.ordinal()] = WallMountBlock.turn(
                        Level2PipeBlock.rackShape(Direction.Axis.X, Level2PipeBlock.Slot.SOUTH, size), facing);
                SHAPES[facing.get2DDataValue()][1][size.ordinal()] = WallMountBlock.turn(
                        Level2PipeBlock.rackShape(Direction.Axis.Y, Level2PipeBlock.Slot.SOUTH, size), facing);
                SHAPES[facing.get2DDataValue()][2][size.ordinal()] = WallMountBlock.turn(
                        Level2PipeBlock.rackShape(Direction.Axis.X, Level2PipeBlock.Slot.HUNG, size), facing);
            }
        }
    }

    public Level2PipeEndBlock(Properties properties) {
        super(properties, 0, 0, 8, 16, 16, 16);
        registerDefaultState(defaultBlockState().setValue(FORM, Form.BEND_CCW)
                .setValue(KIND, Level2PipeBlock.Kind.STEEL).setValue(SIZE, Level2PipeBlock.Size.MEDIUM));
    }

    /**
     * The form for a rack on the wall FACING away from, whose pipes arrive from the side
     * {@code from} (a horizontal direction along the wall) and either bend into the wall here or run
     * on into the solid block on the far side.
     */
    public static Form form(Direction facing, Direction from, boolean bend) {
        boolean ccw = from == facing.getCounterClockWise();
        if (bend) {
            return ccw ? Form.BEND_CCW : Form.BEND_CW;
        }
        return ccw ? Form.COLLAR_CCW : Form.COLLAR_CW;
    }

    /** The form for a run hung under the ceiling, square to FACING, whose pipes arrive from {@code from}. */
    public static Form hungForm(Direction facing, Direction from) {
        return from == facing.getCounterClockWise() ? Form.HUNG_CCW : Form.HUNG_CW;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORM, KIND, SIZE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Form form = state.getValue(FORM);
        int shape = form == Form.RISER ? 1 : form == Form.HUNG_CCW || form == Form.HUNG_CW ? 2 : 0;
        return SHAPES[state.getValue(FACING).get2DDataValue()][shape][state.getValue(SIZE).ordinal()];
    }
}
