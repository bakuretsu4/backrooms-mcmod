package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level0StreetBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The big industrial ceiling fan hanging high in Level 0's street hall (world/layout/Level0Street):
 * eight long blades slowly turning round a hub on a rod from the ceiling, like the fans in the
 * film. The block is the hub; everything is drawn by client/CeilingFanRenderer from its block
 * entity, which carries no data (it exists so the renderer has something to draw for).
 */
public class CeilingFanBlock extends Block implements EntityBlock {
    private static final VoxelShape HUB = Block.box(4, 4, 4, 12, 12, 12);

    public CeilingFanBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HUB;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** No data: the fan only needs to exist for its renderer. */
    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(Level0StreetBlocks.CEILING_FAN_ENTITY.get(), pos, state);
        }
    }
}
