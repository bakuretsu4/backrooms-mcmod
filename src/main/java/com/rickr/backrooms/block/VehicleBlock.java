package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The anchor of a parked Level 94 vehicle. The block itself draws nothing: its block entity's
 * renderer ({@code client/VehicleRenderer}) draws the whole car or van around it. The rest of the
 * vehicle's space is filled with invisible {@link VehicleHullBlock}s (see {@link Level94Vehicles}).
 *
 * <p>FACING = the way the vehicle's front (headlights) points. KIND = which vehicle.
 */
public class VehicleBlock extends BaseEntityBlock {
    public static final MapCodec<VehicleBlock> CODEC = simpleCodec(VehicleBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Level94Vehicles.Kind> KIND = EnumProperty.create("kind", Level94Vehicles.Kind.class);

    public VehicleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(KIND, Level94Vehicles.Kind.CAR_RED));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleBlockEntity(pos, state);
    }

    /** Drawn only by the block entity renderer (like a chest), never as a block model. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /**
     * No black outline box in survival: the car body is bigger than one block, and a cube outline
     * would look odd on it. Creative builders get a cube so they can click it.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return VehicleHullBlock.creativeOutline(context);
    }

    /** ...but it is solid to bump into, like the hull blocks around it. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /**
     * Placed by hand or with /setblock: fill the rest of the vehicle with invisible hull blocks
     * (only where there is air or something replaceable). The world generator writes the hull
     * itself, so this never runs there.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        forEachHullCell(state, pos, (cell, hull) -> {
            if (level.getBlockState(cell).canBeReplaced()) {
                level.setBlock(cell, hull, Block.UPDATE_ALL);
            }
        });
    }

    /** Removed or turned into another vehicle: take the old hull away with it. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state != newState) {
            forEachHullCell(state, pos, (cell, hull) -> {
                if (level.getBlockState(cell).getBlock() instanceof VehicleHullBlock) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            });
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Calls {@code action} for every hull cell of the vehicle whose anchor (in {@code state}) is at {@code pos}. */
    private static void forEachHullCell(BlockState state, BlockPos pos, BiConsumer<BlockPos, BlockState> action) {
        Level94Vehicles.Kind kind = state.getValue(KIND);
        Direction facing = state.getValue(FACING);
        int r = Level94Vehicles.REACH;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockState cell = Level94Vehicles.cell(kind, facing, dx, dy, dz);
                    if (cell != null && cell.getBlock() instanceof VehicleHullBlock) {
                        action.accept(pos.offset(dx, dy, dz), cell);
                    }
                }
            }
        }
    }

    /** In creative the vehicle's front points away from the player (you place it from behind). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
