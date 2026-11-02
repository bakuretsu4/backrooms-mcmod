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
 * "Sunlit void": the hidden source of daylight at the back of Level 37's skylights, slit windows,
 * alcoves, light well and oculus (and the Poolrooms' sunlit archway). You never see the source
 * itself: the block looks like the fine tile around it (wall tile on its sides, ceiling tile
 * underneath, floor tile on top) and is lit like tile, so each opening reads as a tiled niche
 * with light coming from somewhere inside it.
 *
 * <p>It gives vanilla light 14 (14, not 15, see ModBlocks#L37_SUNLIT_VOID): the soft fill around every opening, and without Veil (or with
 * Iris) the whole light, falling off block by block. With Veil the client turns each group of
 * these blocks into hidden lights ({@code client/light/SunBeams}): a shaft of sun out of the
 * opening, a soft glow washing the niche's back, and a faint wide bounce on the tile and water
 * around it. {@link #FACING} is the way the light travels out of the opening (DOWN for a
 * skylight, into the room for a window). The block doesn't block light, so it also never casts
 * a dynamic shadow over its own opening.
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
