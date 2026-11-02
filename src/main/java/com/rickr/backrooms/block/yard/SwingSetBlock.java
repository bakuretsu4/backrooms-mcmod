package com.rickr.backrooms.block.yard;

import com.mojang.serialization.MapCodec;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlockEntities;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A rusty two-seat swing set in a Level 9 backyard, three blocks long. FACING is the way the
 * seats face; the top bar runs across it. PART says which of the three cells this is: the two
 * ends hold the A-frame legs (and their collision), the middle draws the whole frame (one block
 * model reaching into both neighbours) and has the block entity whose renderer draws the two seats
 * swaying gently on their chains (client/level9/SwingSetRenderer), although there is no wind.
 * Now and then one of them creaks ({@link SwingSetBlockEntity}). Placed by world/layout/Level9Yards.
 */
public class SwingSetBlock extends BaseEntityBlock {
    public static final MapCodec<SwingSetBlock> CODEC = simpleCodec(SwingSetBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Left and right as seen sitting on the swing (looking toward FACING). */
    public enum Part implements StringRepresentable {
        LEFT, MIDDLE, RIGHT;

        private final String serializedName = name().toLowerCase(Locale.ROOT);

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    // Shapes for FACING = north (the bar runs east-west), in pixels. The legs lean out to the front
    // and back, so the end cells get a thin post-shaped box; the middle only has the top bar.
    private static final VoxelShape LEFT_NORTH = Block.box(0.5, 0, 0, 2, 24, 16);
    private static final VoxelShape RIGHT_NORTH = Block.box(14, 0, 0, 15.5, 24, 16);
    private static final VoxelShape MIDDLE_NORTH = Block.box(0, 29.5, 7, 16, 31.5, 9);

    /** [part][facing as 2D data value]. */
    private final VoxelShape[][] shapes = new VoxelShape[3][4];

    public SwingSetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MIDDLE));
        VoxelShape[] north = {LEFT_NORTH, MIDDLE_NORTH, RIGHT_NORTH};
        for (Part part : Part.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                shapes[part.ordinal()][facing.get2DDataValue()] = WallMountBlock.turn(north[part.ordinal()], facing);
            }
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The frame is a normal block model (BaseEntityBlock would hide it by default). */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(PART).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    /** Only the middle cell has the swinging seats. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MIDDLE ? new SwingSetBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.L9_SWING_SET.get(), SwingSetBlockEntity::clientTick) : null;
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
