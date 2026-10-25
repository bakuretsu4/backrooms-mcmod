package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something bolted against a wall that only fills part of its block: Level 2's pipe racks, big
 * ducts and valve wheels. FACING points away from the wall, into the corridor, so pipes run
 * sideways (across FACING), along the wall.
 *
 * <p>The shape is given once for FACING = NORTH (wall on the south side, z = 16) and turned for
 * the other directions, exactly like the block model is turned by the blockstate file.
 */
public class WallMountBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /** The box (in pixels, 0-16) the part fills when it faces north, i.e. hangs on a south wall. */
    public WallMountBlock(Properties properties, double x0, double y0, double z0, double x1, double y1, double z1) {
        this(properties, Block.box(x0, y0, z0, x1, y1, z1));
    }

    /** Same, for parts made of several boxes (furniture, lamp heads): the whole shape as it faces north. */
    protected WallMountBlock(Properties properties, VoxelShape shapeFacingNorth) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, turn(shapeFacingNorth, facing));
        }
    }

    /**
     * Turns a shape drawn for FACING = NORTH so it faces another way. This is the same turn the
     * blockstate file gives the model ("y": 90 for east and so on), so shape and model always match.
     */
    public static VoxelShape turn(VoxelShape shapeFacingNorth, Direction facing) {
        VoxelShape[] turned = {Shapes.empty()};
        // Shapes use 0-1 block coordinates here, not pixels.
        shapeFacingNorth.forAllBoxes((x0, y0, z0, x1, y1, z1) -> turned[0] = Shapes.or(turned[0], switch (facing) {
            // Turned half way round: the wall is now on the north side.
            case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
            // A quarter turn clockwise (seen from above) takes north to east and the wall to the west.
            case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
            case WEST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
            default -> Shapes.box(x0, y0, z0, x1, y1, z1);
        }));
        return turned[0].optimize();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** In creative: click the side of a wall and the part sticks out of that side. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
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
