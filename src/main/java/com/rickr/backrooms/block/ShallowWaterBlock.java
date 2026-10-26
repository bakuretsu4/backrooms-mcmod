package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Ankle-deep water (Level 37 floods most of its floors with it). Placed in the feet-level cell
 * right on top of the floor.
 *
 * <p>Why not real water? A full vanilla water block is ~0.9 deep: you would be "swimming" in
 * every room. This block is just a marker: no collision, no outline, nothing drawn by the block
 * renderer (an invisible model). The pool water renderer ({@code client/water}) draws a water
 * surface 0.375 blocks above the floor over every one of these, and you hear wading steps
 * ({@code player/PoolWaterSounds}) and feel a little drag while walking through it.
 *
 * <p>It is a {@link LiquidBlockContainer} that refuses every liquid, so a stray vanilla water
 * source can never flow into it and wash it away.
 */
public class ShallowWaterBlock extends Block implements LiquidBlockContainer {
    public static final MapCodec<ShallowWaterBlock> CODEC = simpleCodec(ShallowWaterBlock::new);
    /** Height of the water surface above the bottom of this block (keep in sync with client/water/WaterSurfaces). */
    public static final double SURFACE = 0.375;
    /** Horizontal speed kept per tick while your feet are in it (1 = no drag). Tune here. */
    public static final double DRAG = 0.85;

    public ShallowWaterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Nothing for the block renderer to draw: the water surface comes from the water renderer. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** No outline and nothing to click: you look and build straight through it, like air. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Light (and the sun) passes straight through. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        applyDrag(entity, pos);
    }

    /**
     * Wading drag: while the entity's feet are below the water line of the cell at {@code pos},
     * it keeps only {@link #DRAG} of its horizontal speed each tick. Public so other blocks that
     * stand in the shallows (Level 37's flooded pieces) can apply the same drag.
     */
    public static void applyDrag(Entity entity, BlockPos pos) {
        if (entity.getY() >= pos.getY() + SURFACE || entity.isSpectator()) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x * DRAG, motion.y, motion.z * DRAG);
    }

    /** Vanilla water may never flow in (it would delete this block, like it washes away torches). */
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
