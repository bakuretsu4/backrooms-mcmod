package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible body of a parked Level 94 vehicle: a full block you bump into, with nothing drawn
 * and no outline. The vehicle you see is drawn by the anchor's renderer ({@link VehicleBlock});
 * these blocks just fill its space so nobody walks through the car.
 */
public class VehicleHullBlock extends Block {
    public static final MapCodec<VehicleHullBlock> CODEC = simpleCodec(VehicleHullBlock::new);

    public VehicleHullBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** No outline: you would otherwise see cube outlines floating on the car body (creative builders excepted). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return creativeOutline(context);
    }

    /**
     * Vehicle blocks show no outline in survival, but a creative-mode builder gets a full cube so
     * the invisible blocks can still be clicked and removed.
     */
    static VoxelShape creativeOutline(CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player && player.isCreative()
                ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** Light passes through, so the invisible blocks don't cast block-shaped shadows on the grass. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
