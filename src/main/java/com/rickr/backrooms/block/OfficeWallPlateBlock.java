package com.rickr.backrooms.block;

import java.util.EnumMap;
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
 * The small plates a real office has on its walls. FACING points away from the wall. KIND, and the
 * block each is modelled for (floor = the carpet block): SWITCH (a light switch beside a door, high
 * in the floor+1 block), THERMOSTAT (a beige room thermostat, low in the floor+2 block, at eye
 * height), CALL_POINT (the red "break glass" fire-alarm call point by exits, low in floor+2) and
 * SOCKET (a double power outlet just above the skirting, in floor+1).
 */
public class OfficeWallPlateBlock extends WallMountBlock {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** Which plate this is (see the class comment). */
    public enum Kind implements StringRepresentable {
        SWITCH(Block.box(6, 11, 15.5, 10, 15, 16)),
        THERMOSTAT(Block.box(5.5, 3, 15.25, 10.5, 7, 16)),
        CALL_POINT(Block.box(5.5, 1.5, 15, 10.5, 6.5, 16)),
        SOCKET(Block.box(4.5, 2, 15.5, 11.5, 5, 16));

        /** The plate's outline when it faces north (hangs on a south wall). */
        private final VoxelShape shapeFacingNorth;

        Kind(VoxelShape shapeFacingNorth) {
            this.shapeFacingNorth = shapeFacingNorth;
        }

        /** The lowercase name used in blockstate files ("kind=call_point"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Outlines per kind and facing, turned once up front (getShape runs very often). */
    private final Map<Kind, Map<Direction, VoxelShape>> shapes = new EnumMap<>(Kind.class);

    public OfficeWallPlateBlock(Properties properties) {
        super(properties, Kind.SWITCH.shapeFacingNorth);
        registerDefaultState(defaultBlockState().setValue(KIND, Kind.SWITCH));
        for (Kind kind : Kind.values()) {
            Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                byFacing.put(facing, turn(kind.shapeFacingNorth, facing));
            }
            shapes.put(kind, byFacing);
        }
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
