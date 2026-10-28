package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small plant of the Level 227 forest floor that comes in a few KINDs (four different species
 * drawn by tools/anomaly_assets/level227_flora.py): the undergrowth (broadleaf seedlings, an
 * elephant ear, a low fern, a spiny palm sprout) and the unknown flowers.
 *
 * <p>You walk straight through it (the registry gives it no collision). Like vanilla grass it is
 * nudged a little off centre per block, so a patch never lines up in a grid.
 */
public class Level227PlantBlock extends Block {
    /** Which species: the blockstate file gives every number its own model. */
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 3);

    /** Only used for the outline you see when you look at it. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public Level227PlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
