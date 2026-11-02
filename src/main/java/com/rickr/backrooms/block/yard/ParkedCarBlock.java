package com.rickr.backrooms.block.yard;

import com.rickr.backrooms.block.WallMountBlock;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One cell of a car parked on a Level 9 driveway or at the curb. A car is 2 blocks wide and 4
 * long, so it is eight of these blocks; PIECE says which one. It is a plain block with a normal
 * block model, not an entity: it never moves, and that is cheap.
 *
 * <p>Only two cells draw anything. The front-left cell's model is the whole front half of the car,
 * the back-left cell's the back half (a block model may reach one block past its own cell). The
 * other six are invisible, but every cell has its own collision box, so you bump into the body,
 * and the cabin is taller than the bonnet and the boot.
 *
 * <p>FACING is where the car's front points. PAINT is its colour: the model's paint is grey and
 * is coloured in game by a block tint (client/level9/StreetClientSetup), so one set of models
 * gives every colour. Placed by world/layout/Level9Yards.
 */
public class ParkedCarBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Piece> PIECE = EnumProperty.create("piece", Piece.class);
    public static final EnumProperty<Paint> PAINT = EnumProperty.create("paint", Paint.class);

    /**
     * The eight cells, counted from the front: {@code along} 0 = nose, 1 = front (the windscreen
     * starts here), 2 = back (the rear seats), 3 = boot; {@code side} 0 = left, 1 = right (as seen
     * sitting in it).
     */
    public enum Piece implements StringRepresentable {
        NOSE_L(0, 0), NOSE_R(0, 1), FRONT_L(1, 0), FRONT_R(1, 1), BACK_L(2, 0), BACK_R(2, 1), REAR_L(3, 0), REAR_R(3, 1);

        public final int along;
        public final int side;
        private final String serializedName = name().toLowerCase(Locale.ROOT);

        Piece(int along, int side) {
            this.along = along;
            this.side = side;
        }

        /** The piece at this cell of the car (along 0-3 from the front, side 0 left / 1 right). */
        public static Piece at(int along, int side) {
            return values()[along * 2 + side];
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** Faded colours of old family cars, a little washed out by the night. */
    public enum Paint implements StringRepresentable {
        MAROON(0x7C302C), NAVY(0x33415F), BEIGE(0xB6A888), SILVER(0xA2A6AA), PINE(0x405E48);

        /** The tint (0xRRGGBB) the grey paint is multiplied by. */
        public final int rgb;
        private final String serializedName = name().toLowerCase(Locale.ROOT);

        Paint(int rgb) {
            this.rgb = rgb;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** Collision of each piece when the car faces north (front at the north side), in pixels. */
    private static final VoxelShape[] NORTH_SHAPES = {
            Block.box(1, 0, 1, 16, 11, 16),                                                     // nose, left
            Block.box(0, 0, 1, 15, 11, 16),                                                     // nose, right
            Shapes.or(Block.box(1, 0, 0, 16, 11, 16), Block.box(3, 11, 6, 16, 20, 16)),         // front: bonnet + cabin
            Shapes.or(Block.box(0, 0, 0, 15, 11, 16), Block.box(0, 11, 6, 13, 20, 16)),
            Shapes.or(Block.box(1, 0, 0, 16, 11, 16), Block.box(3, 11, 0, 16, 20, 13)),         // back: cabin + boot lid
            Shapes.or(Block.box(0, 0, 0, 15, 11, 16), Block.box(0, 11, 0, 13, 20, 13)),
            Block.box(1, 0, 0, 16, 11, 15),                                                     // boot, left
            Block.box(0, 0, 0, 15, 11, 15),                                                     // boot, right
    };

    /** [piece][facing as 2D data value]. */
    private final VoxelShape[][] shapes = new VoxelShape[Piece.values().length][4];

    public ParkedCarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PIECE, Piece.NOSE_L)
                .setValue(PAINT, Paint.SILVER));
        for (Piece piece : Piece.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                shapes[piece.ordinal()][facing.get2DDataValue()] = WallMountBlock.turn(NORTH_SHAPES[piece.ordinal()], facing);
            }
        }
    }

    /**
     * The block for one cell of a parked car.
     *
     * @param facing where the car's front points
     * @param along  0 (nose) to 3 (boot)
     * @param side   0 = left, 1 = right, as seen sitting in the car
     */
    public BlockState piece(Direction facing, int along, int side, Paint paint) {
        return defaultBlockState().setValue(FACING, facing).setValue(PIECE, Piece.at(along, side)).setValue(PAINT, paint);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PIECE, PAINT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(PIECE).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
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
