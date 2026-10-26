package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A supply container left behind by earlier wanderers: the Wooden Crate, a dropped Backpack or a
 * metal Supply Locker ({@link SupplyKind}). Right-click to open it like a chest; the first time, it
 * is filled from a loot table for the level you're on. Breaking it spills whatever is still inside.
 *
 * <p>FACING is the way its front looks (placed facing the player, like a chest). The shape is given
 * once for FACING = NORTH and turned for the other directions, exactly like the block model is
 * turned by the blockstate file ({@link WallMountBlock#turn}).
 */
public class SupplyContainerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final SupplyKind kind;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final MapCodec<SupplyContainerBlock> codec;

    /** @param shapeFacingNorth the container's shape when its front looks north */
    public SupplyContainerBlock(Properties properties, SupplyKind kind, VoxelShape shapeFacingNorth) {
        super(properties);
        this.kind = kind;
        // Every block needs a codec; ours just rebuilds the same kind of container from its properties.
        this.codec = simpleCodec(props -> new SupplyContainerBlock(props, kind, shapeFacingNorth));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, WallMountBlock.turn(shapeFacingNorth, facing));
        }
    }

    public SupplyKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    /** BaseEntityBlock defaults to invisible (for chests drawn by a renderer); we use a normal block model. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** Placed with its front towards the player, like a chest. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return new SupplyContainerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SupplyContainerBlockEntity container) {
            container.fillLootIfNeeded(serverLevel, player);
            // The open/close sounds are played by the block entity when the menu opens and closes.
            player.openMenu(container);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking the container spills whatever is still inside. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
