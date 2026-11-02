package com.rickr.backrooms.block;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The ghost of something that used to hang on a Level 4 wall: a paler, unfaded rectangle of paint
 * with the nail holes or wall plugs still in it. It sits flat on the wall in the second block above
 * the floor, FACING away from the wall. Empty rooms look lived-in-then-abandoned instead of new.
 *
 * <p>KIND: PICTURE (a framed print's patch), BOARD_LEFT / BOARD_RIGHT (the two halves of a
 * whiteboard's big patch; LEFT is the half on the viewer's left, which is the world direction
 * {@code FACING.getClockWise()}), HOLES (just the nail holes and a screw left in the plaster).
 */
public class OfficeWallMarkBlock extends WallMountBlock {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** Which mark this is (see the class comment). */
    public enum Kind implements StringRepresentable {
        PICTURE(Block.box(3, 3, 15.75, 13, 12, 16)),
        BOARD_LEFT(Block.box(0, 1, 15.75, 15, 14, 16)),
        BOARD_RIGHT(Block.box(1, 1, 15.75, 16, 14, 16)),
        HOLES(Block.box(5, 6, 15.5, 11, 10, 16));

        /** The mark's outline when it faces north (on a south wall). */
        private final VoxelShape shapeFacingNorth;

        Kind(VoxelShape shapeFacingNorth) {
            this.shapeFacingNorth = shapeFacingNorth;
        }

        /** The lowercase name used in blockstate files ("kind=board_left"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Outlines per kind and facing, turned once up front (getShape runs very often). */
    private final Map<Kind, Map<Direction, VoxelShape>> shapes = turnAll(Kind.class, kind -> kind.shapeFacingNorth);

    public OfficeWallMarkBlock(Properties properties) {
        super(properties, Kind.PICTURE.shapeFacingNorth);
        registerDefaultState(defaultBlockState().setValue(KIND, Kind.PICTURE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KIND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(KIND)).get(state.getValue(FACING));
    }

    /** In a mirror the whiteboard's two halves swap. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = super.mirror(state, mirror);
        Kind kind = state.getValue(KIND);
        if (mirror == Mirror.NONE || (kind != Kind.BOARD_LEFT && kind != Kind.BOARD_RIGHT)) {
            return turned;
        }
        return turned.setValue(KIND, kind == Kind.BOARD_LEFT ? Kind.BOARD_RIGHT : Kind.BOARD_LEFT);
    }
}
