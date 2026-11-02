package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A Level 4 ceiling tile with an air-conditioning grille in it: a full ceiling block, its underside
 * showing the grille (with a few small modelled parts hanging a pixel below the tile).
 *
 * <p>KIND: SQUARE is the four-way square diffuser that sits on a regular grid between the lights,
 * SLOT is the long linear slot diffuser that runs along the window walls (AXIS = the way the slots
 * run, so a row of them joins into one long line), RETURN is the plain egg-crate return-air
 * grille. AXIS only changes the look of SLOT.
 */
public class OfficeCeilingDiffuserBlock extends Block {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** Which grille the tile has (see the class comment). */
    public enum Kind implements StringRepresentable {
        SQUARE, SLOT, RETURN;

        /** The lowercase name used in blockstate files ("kind=slot"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OfficeCeilingDiffuserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.SQUARE).setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, AXIS);
    }

    /** In creative: the slots run the way you look. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    /** A quarter turn swaps the slot direction; a half turn leaves it as it was. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
