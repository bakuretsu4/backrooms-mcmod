package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * "Sunlit void": what Level 37's skylights, slit windows and alcoves open onto. There is never
 * anything to see out there, just a dazzling, overexposed warm white, as if the sun were right
 * behind a wall of mist.
 *
 * <p>It glows full-bright and gives vanilla light 15 (the soft fill around every opening). The
 * sharp shafts of sunlight are dynamic lights made on the client ({@code client/light/SunBeams})
 * from groups of these blocks; {@link #FACING} is the way the light travels out of the opening
 * (DOWN for a skylight, into the room for a window). The block doesn't block light, so it also
 * never casts a dynamic shadow over its own opening.
 */
public class SunlitVoidBlock extends DirectionalBlock {
    public static final MapCodec<SunlitVoidBlock> CODEC = simpleCodec(SunlitVoidBlock::new);

    public SunlitVoidBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** In creative: shines toward you. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Light passes as through air (it is light). */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
