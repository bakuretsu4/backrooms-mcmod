package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of furniture that has "noclipped" into the building, as in the film and Kane Pixels'
 * "Static Dead End": a chair half sunk into a wall, a table halfway through the floor, a
 * bookshelf hanging out of the ceiling. It sits in the AIR block next to the solid block it is
 * fused with; its model deliberately reaches into that neighbour, whose face hides the buried
 * part, so it looks as if the two had melted together.
 *
 * <ul>
 *   <li>MOUNT = WALL: half inside the wall behind it. FACING points away from that wall, into the
 *       room (as in {@link WallMountBlock}).</li>
 *   <li>MOUNT = FLOOR: sunk 30-60% into the floor below and tilted; FACING turns it.</li>
 *   <li>MOUNT = CEILING: hanging half out of the ceiling above; FACING turns it.</li>
 * </ul>
 * PROP picks the piece of furniture. Collision covers only the part sticking out.
 */
public class Level0EmbeddedBlock extends WallMountBlock {

    /** Which neighbour the prop is fused with. */
    public enum Mount implements StringRepresentable {
        WALL, FLOOR, CEILING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The furniture. For each mount, the box (in pixels, for FACING = NORTH) around the part
     * that sticks out into this block: that is what you bump into. The numbers come from the
     * models in tools/anomaly_assets/level0_revamp.py (it prints them when it writes them).
     */
    public enum Prop implements StringRepresentable {
        CHAIR(new double[] {0.5, 0, 8.5, 15, 9, 16}, new double[] {2.5, 0, 0, 13.5, 7.5, 16}, new double[] {1.5, 1.5, 2.5, 16, 16, 13.5}),
        OFFICE_CHAIR(new double[] {1.5, 0, 7.5, 16, 15.5, 16}, new double[] {0.5, 0, 0, 15.5, 15.5, 15}, new double[] {1.5, 0.5, 2, 14.5, 16, 14.5}),
        TABLE(new double[] {0, 0, 6.5, 16, 16, 16}, new double[] {0, 0, 0, 16, 13, 16}, new double[] {0, 5, 0, 16, 14.5, 12.5}),
        DRESSER(new double[] {0, 0, 9, 16, 16, 16}, new double[] {0, 0, 0, 16, 15, 16}, new double[] {0, 1, 2.5, 16, 16, 13.5}),
        LAMP(new double[] {1, 1, 0, 15, 15, 16}, new double[] {2.5, 0, 1.5, 13.5, 16, 16}, new double[] {2.5, 0, 0, 13.5, 16, 12.5}),
        DOOR(new double[] {1.5, 0, 11, 16, 16, 16}, new double[] {1.5, 0, 0, 16, 5, 16}, new double[] {0, 0.5, 0, 16, 16, 16}),
        CRT_TV(new double[] {1, 0, 5, 16, 15, 16}, new double[] {0.5, 0, 0, 15.5, 11.5, 16}, new double[] {0.5, 6, 0, 15.5, 16, 16}),
        SOFA_END(new double[] {0, 1.5, 2.5, 16, 16, 16}, new double[] {0, 0, 0, 16, 13, 16}, new double[] {0, 1.5, 0, 16, 16, 16}),
        FILING_CABINET(new double[] {2.5, 0, 9.5, 14, 16, 16}, new double[] {0, 0, 2, 16, 12, 14}, new double[] {4, 2.5, 0, 12, 16, 16}),
        BOOKSHELF(new double[] {0, 0, 9.5, 16, 16, 16}, new double[] {0, 0, 0, 16, 15, 16}, new double[] {0, 1, 0, 16, 16, 16}),
        PLASTIC_CHAIR(new double[] {0, 0, 7, 15.5, 13.5, 16}, new double[] {1.5, 0, 2.5, 14.5, 14, 16}, new double[] {0, 4, 2, 16, 16, 14}),
        MATTRESS(new double[] {0, 0, 10.5, 16, 16, 16}, new double[] {0, 0, 0, 16, 8.5, 16}, new double[] {0, 10.5, 0, 16, 16, 16});

        private final double[][] boxes;

        Prop(double[] wall, double[] floor, double[] ceiling) {
            this.boxes = new double[][] {wall, floor, ceiling};
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
    public static final EnumProperty<Prop> PROP = EnumProperty.create("prop", Prop.class);

    // [prop][mount][facing 2D index]: worked out once, so looking a shape up allocates nothing.
    private static final VoxelShape[][][] SHAPES = new VoxelShape[Prop.values().length][Mount.values().length][4];

    static {
        for (Prop prop : Prop.values()) {
            for (Mount mount : Mount.values()) {
                double[] b = prop.boxes[mount.ordinal()];
                VoxelShape north = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    SHAPES[prop.ordinal()][mount.ordinal()][facing.get2DDataValue()] = turn(north, facing);
                }
            }
        }
    }

    public Level0EmbeddedBlock(Properties properties) {
        super(properties, Shapes.block());
        registerDefaultState(defaultBlockState().setValue(MOUNT, Mount.WALL).setValue(PROP, Prop.CHAIR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT, PROP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(PROP).ordinal()][state.getValue(MOUNT).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    /** In creative: click a wall, the floor or the ceiling and the prop fuses with it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Mount mount = face == Direction.UP ? Mount.FLOOR : face == Direction.DOWN ? Mount.CEILING : Mount.WALL;
        return super.getStateForPlacement(context).setValue(MOUNT, mount);
    }
}
