package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import com.rickr.backrooms.registry.ModBlockEntities;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Level 94's siren: an old 1930s horn loudspeaker on top of a wooden pole (two grey trumpets back
 * to back on a bracket). All day it plays a happy cartoon tune; at dusk the music falters and
 * stops, and the night is silent. The music itself is played on the client by the block entity
 * ({@link SirenBlockEntity}).
 *
 * <p>FACING = the way the front horn points. The model is drawn facing north and turned by the
 * blockstate file, and the shape below is turned the same way ({@link WallMountBlock#turn}).
 */
public class SirenBlock extends BaseEntityBlock {
    public static final MapCodec<SirenBlock> CODEC = simpleCodec(SirenBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Bracket on the pole plus both horns, facing north (front horn flares out at z = 0). */
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(6.5, 0, 6.5, 9.5, 6, 9.5),
            Block.box(3, 4, 0, 13, 14, 16));
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public SirenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, WallMountBlock.turn(SHAPE_NORTH, facing));
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** BaseEntityBlock defaults to invisible (for chests drawn by a renderer); the siren uses its block model. */
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SirenBlockEntity(pos, state);
    }

    /** Only the client ticks a siren: the music is a client sound. The server has nothing to do. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.L94_SIREN.get(), SirenBlockEntity::clientTick) : null;
    }

    /** In creative the horn points towards the player. */
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
}
