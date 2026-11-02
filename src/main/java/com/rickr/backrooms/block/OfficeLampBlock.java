package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Level 4 lobby or cafeteria light with no direction of its own: the coffer light (a full ceiling
 * block) or the drum pendant hanging under the ceiling. MODE works like the troffers' (the lighting
 * code reads the "mode" property by name, see {@code FixtureType#modeOf}): LIT, FLICKER, or DEAD
 * (no light, drawn with the grey "off" texture).
 */
public class OfficeLampBlock extends Block {
    public static final EnumProperty<LampMode> MODE = EnumProperty.create("mode", LampMode.class);

    private final VoxelShape shape;

    /** @param shape the lamp's outline (and collision, unless the block has none) */
    public OfficeLampBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(MODE, LampMode.LIT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
