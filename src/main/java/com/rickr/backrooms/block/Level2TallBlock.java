package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Level 2 furniture two blocks tall, standing against a wall: the breaker panel and the steel
 * shelving. Like a door it is two blocks: HALF = LOWER and HALF = UPPER directly above it, both
 * with the same FACING (the side you look at, away from the wall behind it). The world generator
 * places both halves itself.
 */
public class Level2TallBlock extends FurnitureBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private final Map<Direction, VoxelShape> lower = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> upper = new EnumMap<>(Direction.class);

    /** The two halves' shapes when FACING = NORTH (the wall on the south side). */
    public Level2TallBlock(Properties properties, VoxelShape lowerFacingNorth, VoxelShape upperFacingNorth) {
        super(properties, Shapes.block());
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            lower.put(facing, turn(lowerFacingNorth, facing));
            upper.put(facing, turn(upperFacingNorth, facing));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? lower : upper).get(state.getValue(FACING));
    }

    /** In creative: only placed if there is room for the upper half too. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        if (above.getY() >= context.getLevel().getMaxBuildHeight() || !context.getLevel().getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    /** ...and the upper half is added right after the lower one, as a door does. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }
}
