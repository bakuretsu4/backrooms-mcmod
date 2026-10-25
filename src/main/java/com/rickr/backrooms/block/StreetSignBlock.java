package com.rickr.backrooms.block;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A street sign panel bolted to the top of a sign post. It sits directly on a {@code sign_post}
 * block and its model carries its own short piece of post behind the panel, so post and sign
 * read as one sign. FACING is the way the printed face looks, towards the reader; KIND picks
 * the picture.
 */
public class StreetSignBlock extends WallMountBlock {
    public static final EnumProperty<SignKind> KIND = EnumProperty.create("kind", SignKind.class);

    /** Thin panel on the north side of the post, and the post itself (FACING = NORTH). */
    private static final VoxelShape SHAPE_FACING_NORTH = Shapes.or(
            Block.box(1, 1.5, 6.5, 15, 15.5, 7),
            Block.box(7, 0, 7, 9, 15, 9));

    public StreetSignBlock(Properties properties) {
        super(properties, SHAPE_FACING_NORTH);
        registerDefaultState(defaultBlockState().setValue(KIND, SignKind.ARROW_AHEAD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KIND);
    }

    /** In creative, the sign's face looks at the player who places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
