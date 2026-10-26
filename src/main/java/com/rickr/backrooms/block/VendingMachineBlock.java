package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.registry.ModSounds;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Level 4 break-room vending machine, two blocks tall, its glass front still glowing. Right-click
 * it (either half) and it drops a bottle of Almond Water or an Energy Bar into your
 * hands, until it is sold out.
 *
 * <p>How many items are left is the STOCK property, kept equal on both halves. Because it lives
 * in the block state (not a block entity) it is saved with the world for free, and every machine
 * the level generates starts with its own stock (2-6).
 *
 * <p>FACING is the way the front looks; HALF is LOWER (the delivery flap) or UPPER (the rows of
 * bottles behind glass). The glowing front is part of the block model (full-bright faces).
 */
public class VendingMachineBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final int MAX_STOCK = 6;
    public static final IntegerProperty STOCK = IntegerProperty.create("stock", 0, MAX_STOCK);
    /** Out of 10: how often it gives Almond Water instead of an Energy Bar. */
    private static final int WATER_CHANCE = 6;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public VendingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(STOCK, MAX_STOCK));
        // The cabinet stands with its back against the wall (south side when facing north).
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, WallMountBlock.turn(Block.box(0.5, 0, 2, 15.5, 16, 16), facing));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, STOCK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState bottom = level.getBlockState(lower);
        if (!bottom.is(this)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int stock = bottom.getValue(STOCK);
        if (stock <= 0) {
            level.playSound(null, lower, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6f, 0.8f);
            player.displayClientMessage(Component.translatable("message.backrooms.vending_machine.empty"), true);
            return InteractionResult.CONSUME;
        }
        // Take one item out of BOTH halves, so they always agree.
        setStock(level, lower, stock - 1);
        ItemStack item = level.getRandom().nextInt(10) < WATER_CHANCE
                ? new ItemStack(ModItems.ALMOND_WATER.get())
                : new ItemStack(ModItems.ENERGY_BAR.get());
        if (!player.addItem(item)) {
            // Hands full: it lands in the delivery flap instead.
            Block.popResourceFromFace(level, lower, bottom.getValue(FACING), item);
        }
        level.playSound(null, lower, ModSounds.VENDING_MACHINE_VEND.get(), SoundSource.BLOCKS, 0.9f,
                0.95f + level.getRandom().nextFloat() * 0.1f);
        return InteractionResult.CONSUME;
    }

    private void setStock(Level level, BlockPos lower, int stock) {
        BlockState bottom = level.getBlockState(lower);
        BlockState top = level.getBlockState(lower.above());
        level.setBlock(lower, bottom.setValue(STOCK, stock), Block.UPDATE_ALL);
        if (top.is(this)) {
            level.setBlock(lower.above(), top.setValue(STOCK, stock), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Two blocks tall: placing, breaking (creative only; in the Backrooms nothing breaks)
    // ------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1 || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** If one half disappears, the other goes too (like a door). */
    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = side == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardsOtherHalf && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
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
