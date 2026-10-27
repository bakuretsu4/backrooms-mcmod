package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thin grey electrical conduit screwed onto a Level 0 wall, the pipe that runs from a wall
 * switch box (registry/Level0Blocks.SWITCH_BOX) down to the floor or up to the ceiling. It sits
 * in the AIR block in front of the wall; FACING points away from the wall into the room (as in
 * {@link WallMountBlock}). VERTICAL = true runs up and down the wall, false runs sideways along it.
 */
public class Level0ConduitBlock extends WallMountBlock {
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

    // The pipe as it hangs on a south wall (FACING = NORTH), turned for the other walls below.
    private static final VoxelShape UPRIGHT_NORTH = Block.box(7, 0, 14.5, 9, 16, 16);
    private static final VoxelShape SIDEWAYS_NORTH = Block.box(0, 7, 14.5, 16, 9, 16);

    private final Map<Direction, VoxelShape> upright = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> sideways = new EnumMap<>(Direction.class);

    public Level0ConduitBlock(Properties properties) {
        super(properties, UPRIGHT_NORTH);
        registerDefaultState(defaultBlockState().setValue(VERTICAL, true));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            upright.put(facing, turn(UPRIGHT_NORTH, facing));
            sideways.put(facing, turn(SIDEWAYS_NORTH, facing));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VERTICAL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(VERTICAL) ? upright : sideways).get(state.getValue(FACING));
    }

    /** In creative: click a wall; looking up or down gives an upright run, otherwise a sideways one. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean vertical = context.getPlayer() == null || Math.abs(context.getPlayer().getXRot()) > 30;
        return super.getStateForPlacement(context).setValue(VERTICAL, vertical);
    }
}
