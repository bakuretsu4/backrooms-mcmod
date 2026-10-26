package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * One half of a Level 4 ceiling light: a 2 x 4 ft fluorescent troffer (a 1 x 2 block panel) set
 * into the drop ceiling. FACING points from this half towards the other half, so the texture's open
 * edge meets its partner and the two blocks read as one long panel.
 *
 * <p>MODE: LIT shines steadily, FLICKER is a failing tube that stutters now and then (the lighting
 * code reads the "mode" property by name, see {@code FixtureType#modeOf}), DEAD is a panel whose
 * tubes are out: no light at all, drawn with the grey "off" texture.
 */
public class OfficePanelBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<LampMode> MODE = EnumProperty.create("mode", LampMode.class);

    public OfficePanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, LampMode.LIT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    /** In creative: the panel's long side runs the way you look. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
