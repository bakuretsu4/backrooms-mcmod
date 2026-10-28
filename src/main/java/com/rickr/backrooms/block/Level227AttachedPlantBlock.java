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
 * A small plant growing out of the bark of a branch, on any side of it: Level 227's epiphytes
 * (bromeliads and orchids) and the glowing GammaPali. FACING points away from the surface it grows
 * on (UP = sitting on top of a branch, DOWN = hanging from the underside, a side = on a wall of
 * bark). You walk through it.
 */
public class Level227AttachedPlantBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /**
     * @param radius half the plant's width in pixels
     * @param height how far it sticks out of the surface, in pixels
     */
    public Level227AttachedPlantBlock(Properties properties, double radius, double height) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
        double a = 8 - radius;
        double b = 8 + radius;
        double h = height / 16.0;
        for (Direction facing : Direction.values()) {
            // The plant fills a box of the given size against the face it grows from.
            VoxelShape shape = switch (facing) {
                case UP -> Shapes.box(a / 16, 0, a / 16, b / 16, h, b / 16);
                case DOWN -> Shapes.box(a / 16, 1 - h, a / 16, b / 16, 1, b / 16);
                case NORTH -> Shapes.box(a / 16, a / 16, 1 - h, b / 16, b / 16, 1);
                case SOUTH -> Shapes.box(a / 16, a / 16, 0, b / 16, b / 16, h);
                case WEST -> Shapes.box(1 - h, a / 16, a / 16, 1, b / 16, b / 16);
                case EAST -> Shapes.box(0, a / 16, a / 16, h, b / 16, b / 16);
            };
            shapes.put(facing, shape);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** In creative: it grows out of the face you clicked. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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
