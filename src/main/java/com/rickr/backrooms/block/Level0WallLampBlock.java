package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small lamp bolted to a wall that can be switched on and off: Level 0's rotating red beacon and
 * its emergency lamp (registry/Level0LightBlocks). FACING points away from the wall, into the
 * room, like every {@link WallMountBlock}. LIT is what the server switches; what it means is up to
 * the light code (client/light/FixtureType): an unlit beacon is dark, an unlit emergency lamp is
 * on standby and comes on by itself when the power fails.
 */
public class Level0WallLampBlock extends WallMountBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public Level0WallLampBlock(Properties properties, VoxelShape shapeFacingNorth) {
        super(properties, shapeFacingNorth);
        registerDefaultState(defaultBlockState().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
