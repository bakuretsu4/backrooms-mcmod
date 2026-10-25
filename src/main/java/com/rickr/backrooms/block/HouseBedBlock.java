package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A made single bed in a Level 9 bedroom, two blocks long. It is only furniture: you can't
 * sleep in it (there is no morning on Level 9).
 *
 * <p>Unlike other furniture, FACING here is the direction from the FOOT block to the HEAD block:
 * the foot is at P and the head at P + FACING, with the headboard on the far (FACING) end, so
 * a bed facing north has its headboard against a wall on the north side.
 */
public class HouseBedBlock extends FurnitureBlock {
    public static final BooleanProperty HEAD = BooleanProperty.create("head");

    // Shapes for FACING = NORTH (head end towards z = 0), in pixels.
    /** Foot half: frame + mattress, footboard across the south end. */
    private static final VoxelShape FOOT_NORTH = Shapes.or(
            Block.box(0, 2, 0, 16, 9, 15),
            Block.box(0, 0, 15, 16, 11, 16));
    /** Head half: frame + mattress, pillow, tall headboard across the north end. */
    private static final VoxelShape HEAD_NORTH = Shapes.or(
            Block.box(0, 2, 1, 16, 9, 16),
            Block.box(2, 9, 1.5, 14, 10.5, 5.5),
            Block.box(0, 0, 0, 16, 15, 1));

    private final Map<Direction, VoxelShape> headShapes = new EnumMap<>(Direction.class);

    public HouseBedBlock(Properties properties) {
        super(properties, FOOT_NORTH);
        registerDefaultState(defaultBlockState().setValue(HEAD, false));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            headShapes.put(facing, turn(HEAD_NORTH, facing));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAD);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HEAD) ? headShapes.get(state.getValue(FACING)) : super.getShape(state, level, pos, context);
    }

    /** In creative, like a vanilla bed: the player stands at the foot and the head points away. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos headPos = context.getClickedPos().relative(facing);
        if (!context.getLevel().getBlockState(headPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Called after a player placed the foot: add the head half. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(HEAD, true), Block.UPDATE_ALL);
    }

    /** Breaking one half (creative only, beds are unbreakable) removes the other half too. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Direction toOther = state.getValue(HEAD) ? state.getValue(FACING).getOpposite() : state.getValue(FACING);
        BlockPos otherPos = pos.relative(toOther);
        BlockState other = level.getBlockState(otherPos);
        if (other.is(this) && other.getValue(HEAD) != state.getValue(HEAD) && other.getValue(FACING) == state.getValue(FACING)) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
