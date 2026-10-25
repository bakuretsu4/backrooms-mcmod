package com.rickr.backrooms.block;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of furniture in a Level 9 house: sofa, TV, fridge, counter, table, mailbox.
 * FACING is the way its FRONT looks, so a sofa facing north stands with its back against the
 * south side of its block. That is the same rule as {@link WallMountBlock} (FACING points away
 * from the wall), which is why furniture reuses its turning code for the collision shape.
 */
public class FurnitureBlock extends WallMountBlock {
    /** @param shapeFacingNorth the furniture's shape when its front looks north (back on the south side) */
    public FurnitureBlock(Properties properties, VoxelShape shapeFacingNorth) {
        super(properties, shapeFacingNorth);
    }

    /** In creative, furniture is placed with its front towards the player, like a chest. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
