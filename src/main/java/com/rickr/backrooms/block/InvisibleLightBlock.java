package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Level 39's "invisible orbs": the tunnels are evenly lit, but nobody can see where the light comes
 * from. This block sits in the air under the vault and only gives off light (its strength comes
 * from the registry). Nothing is drawn, nothing collides and it can't even be outlined, so it
 * behaves like air in every way except for the glow. The client adds a soft Veil light at the
 * same spot.
 */
public class InvisibleLightBlock extends Block {
    public InvisibleLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
