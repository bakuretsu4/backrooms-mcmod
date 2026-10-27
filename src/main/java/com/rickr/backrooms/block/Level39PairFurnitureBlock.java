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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Level 39 furniture two blocks big: a bed or a clawfoot tub (two long), a sofa or an upright
 * piano (two wide), a wardrobe or a kitchen dresser (two tall). The first half sits at P and the
 * SECOND half next to it, at {@link #secondOffset}:
 * <ul>
 *   <li>{@link Layout#LONG}: at P + FACING (a bed's foot at P, its head and headboard at P + FACING);</li>
 *   <li>{@link Layout#WIDE}: at P + FACING turned clockwise (facing north, the second half is east);</li>
 *   <li>{@link Layout#TALL}: on top (underneath when INVERTED, because the whole piece is upside down).</li>
 * </ul>
 */
public class Level39PairFurnitureBlock extends Level39FurnitureBlock {
    public static final BooleanProperty SECOND = BooleanProperty.create("second");

    public enum Layout {
        LONG, WIDE, TALL;

        /** Where the second half is, seen from the first half. */
        public Direction secondOffset(Direction facing, boolean inverted) {
            return switch (this) {
                case LONG -> facing;
                case WIDE -> inverted ? facing.getCounterClockWise() : facing.getClockWise();
                case TALL -> inverted ? Direction.DOWN : Direction.UP;
            };
        }
    }

    private final Layout layout;
    private final Map<Direction, VoxelShape> secondShapes = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> secondInverted = new EnumMap<>(Direction.class);

    /**
     * @param firstFacingNorth  the first half's shape facing north, right way up
     * @param secondFacingNorth the second half's shape facing north, right way up
     */
    public Level39PairFurnitureBlock(Properties properties, Layout layout, VoxelShape firstFacingNorth, VoxelShape secondFacingNorth) {
        super(properties, firstFacingNorth);
        this.layout = layout;
        registerDefaultState(defaultBlockState().setValue(SECOND, false));
        VoxelShape flipped = flip(secondFacingNorth);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            secondShapes.put(facing, turn(secondFacingNorth, facing));
            secondInverted.put(facing, turn(flipped, facing));
        }
    }

    public Layout layout() {
        return layout;
    }

    /** Where the second half is, seen from the first half. */
    public static Direction secondOffset(Layout layout, Direction facing, boolean inverted) {
        return layout.secondOffset(facing, inverted);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SECOND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(SECOND)) {
            return (state.getValue(INVERTED) ? secondInverted : secondShapes).get(state.getValue(FACING));
        }
        return super.getShape(state, level, pos, context);
    }

    /** In creative: placed facing the player, if there is room for the second half. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (layout == Layout.LONG) {
            // A bed is placed like a vanilla bed: the head points away from the player.
            state = state.setValue(FACING, context.getHorizontalDirection());
        }
        Direction toSecond = secondOffset(layout, state.getValue(FACING), false);
        BlockPos other = context.getClickedPos().relative(toSecond);
        return context.getLevel().getBlockState(other).canBeReplaced(context) ? state : null;
    }

    /** After a player placed the first half: add the second. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction toSecond = secondOffset(layout, state.getValue(FACING), state.getValue(INVERTED));
        level.setBlock(pos.relative(toSecond), state.setValue(SECOND, true), Block.UPDATE_ALL);
    }

    /** Breaking one half (creative only: house furniture is unbreakable) takes the other with it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Direction toSecond = secondOffset(layout, state.getValue(FACING), state.getValue(INVERTED));
        BlockPos otherPos = pos.relative(state.getValue(SECOND) ? toSecond.getOpposite() : toSecond);
        BlockState other = level.getBlockState(otherPos);
        if (other.is(this) && other.getValue(SECOND) != state.getValue(SECOND)) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
