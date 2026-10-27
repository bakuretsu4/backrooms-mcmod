package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rusty rebar jutting out of Level 1's cracked floor (fandom: "sharp enough to pierce skin"). You
 * can push through it, but it holds you up and pricks you if you keep moving, like a berry bush.
 */
public class RebarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public RebarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        entity.makeStuckInBlock(state, new Vec3(0.8, 0.75, 0.8));
        if (!level.isClientSide && (entity.xOld != entity.getX() || entity.zOld != entity.getZ())) {
            double moved = Math.max(Math.abs(entity.getX() - entity.xOld), Math.abs(entity.getZ() - entity.zOld));
            if (moved >= 0.003) {
                living.hurt(level.damageSources().cactus(), 1.0f);
            }
        }
    }
}
