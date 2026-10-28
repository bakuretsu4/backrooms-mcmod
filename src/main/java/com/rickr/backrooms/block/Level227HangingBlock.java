package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something that hangs down in a strand from the block above: the moss curtains under the giant
 * branches, the lianas dropping out of the canopy, and the fern fronds trailing from the fern
 * foliage. Stack them downwards; the lowest one has TIP = true and gets the frayed end.
 *
 * <p>You pass through them (the registry gives them no collision). Lianas are climbable like a
 * ladder, so a liana is a way up into the canopy; moss and fronds tear if you try.
 */
public class Level227HangingBlock extends Block {
    /** The bottom end of a strand (its model tapers off). */
    public static final BooleanProperty TIP = BooleanProperty.create("tip");

    private final VoxelShape shape;
    private final boolean climbable;

    /** @param halfWidth half the strand's width in pixels (outline only) */
    public Level227HangingBlock(Properties properties, double halfWidth, boolean climbable) {
        super(properties);
        this.shape = Block.box(8 - halfWidth, 0, 8 - halfWidth, 8 + halfWidth, 16, 8 + halfWidth);
        this.climbable = climbable;
        registerDefaultState(stateDefinition.any().setValue(TIP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return climbable;
    }
}
