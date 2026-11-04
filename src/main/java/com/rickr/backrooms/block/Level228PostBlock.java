package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Level 228 v2: the collision of a mirror-maze column (and of the ticket booth's corners). It
 * draws nothing: the turned-wood, Moorish, barber-striped or steel column is a prop
 * (client/level228 prop renderer), so the block only has to stop you where the shaft stands and
 * let light and the view pass around it.
 */
public class Level228PostBlock extends Block {
    /** The shaft is ~0.55 across; a 0.5 box keeps you off it without snagging on the capital. */
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public Level228PostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
