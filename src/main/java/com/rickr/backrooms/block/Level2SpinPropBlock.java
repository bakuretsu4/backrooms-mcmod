package com.rickr.backrooms.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Level 2's spinning props (registry/Level2SpinBlocks): a fan or a warning beacon set INTO a wall
 * or the ceiling. A full block (collision and all), so the one-block lane beside it stays exactly
 * as wide as the wall it replaces; FACING points out of the surface into the lane. The block's own
 * model is the static housing; the moving part (the fan's blades, the beacon's turning reflector
 * and beam) is drawn by its block-entity renderer (client/anomaly/level2/WallFanRenderer,
 * SpinBeaconRenderer), from the level clock, so every player sees the same turn. The block entity
 * carries no data: it only exists so the renderer has something to draw for.
 */
public class Level2SpinPropBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final Supplier<? extends BlockEntityType<Entity>> entityType;

    public Level2SpinPropBlock(Properties properties, Supplier<? extends BlockEntityType<Entity>> entityType) {
        super(properties);
        this.entityType = entityType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** In creative it faces out of the face you clicked, like a lamp on that surface. */
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(entityType.get(), pos, state);
    }

    /** No data: the prop only needs to exist for its renderer. */
    public static class Entity extends BlockEntity {
        public Entity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }
}
