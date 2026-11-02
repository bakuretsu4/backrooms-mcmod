package com.rickr.backrooms.block;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A flat detail lying on Level 4's carpet, in the air block just above the floor (walk-through).
 * KIND: FLOOR_BOX (the flush lid of a floor power box, in the grid where desks used to stand),
 * THRESHOLD (an aluminium edge strip where the carpet meets another floor, along the block's
 * FACING edge), STAIN (an old faint coffee or water stain).
 */
public class OfficeFloorDetailBlock extends WallMountBlock {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** Which detail this is (see the class comment). */
    public enum Kind implements StringRepresentable {
        FLOOR_BOX(Block.box(4, 0, 4, 12, 0.3, 12)),
        THRESHOLD(Block.box(0, 0, 0, 16, 0.4, 1.5)),
        STAIN(Block.box(2, 0, 2, 14, 0.1, 14));

        /** The detail's outline when it faces north. */
        private final VoxelShape shapeFacingNorth;

        Kind(VoxelShape shapeFacingNorth) {
            this.shapeFacingNorth = shapeFacingNorth;
        }

        /** The lowercase name used in blockstate files ("kind=floor_box"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Outlines per kind and facing, turned once up front (getShape runs very often). */
    private final Map<Kind, Map<Direction, VoxelShape>> shapes = turnAll(Kind.class, kind -> kind.shapeFacingNorth);

    public OfficeFloorDetailBlock(Properties properties) {
        super(properties, Kind.FLOOR_BOX.shapeFacingNorth);
        registerDefaultState(defaultBlockState().setValue(KIND, Kind.FLOOR_BOX));
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
}
