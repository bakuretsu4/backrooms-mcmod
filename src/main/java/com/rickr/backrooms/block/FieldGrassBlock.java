package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The tall wild grass of Level 9.1, the Crimson Field ("3 ft tall", per
 * http://backrooms-mghc.wikidot.com/level-9-1). Drawn as a crossed plant, nudged a little off
 * centre per block (like vanilla grass) so the field doesn't look like a grid. You walk straight
 * through it (the registry gives it no collision), so it never blocks anyone or trips the no-jump
 * rule.
 *
 * <p>A plain block instead of vanilla's grass plant on purpose: vanilla plants pop off when the
 * ground under them isn't dirt, and the field's ground is the mod's own lawn block.
 */
public class FieldGrassBlock extends Block {
    /** Only used for the outline you see when you look at it. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public FieldGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
