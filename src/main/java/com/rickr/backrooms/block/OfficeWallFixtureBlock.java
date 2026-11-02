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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The small fittings a real office building has on its walls, which keep Level 4's nearly empty
 * rooms from looking unfinished. FACING points away from the wall. KIND, and the block each is
 * modelled for (floor = the carpet block):
 * <ul>
 *   <li>floor + 1: DADO (white cable trunking along the wall at desk height; blocks join into a
 *   run), DADO_OUTLETS (the same with a power and a data outlet), DOOR_STOP (a rubber stop on the
 *   skirting behind a door)</li>
 *   <li>floor + 2: COAT_HOOKS, SANITISER (hand sanitiser dispenser), FIRST_AID (green box),
 *   EVAC_PLAN (framed "you are here" fire plan), NO_SMOKING sign, FIRE_DOOR_SIGN (blue "fire door
 *   keep shut"), SIGN_MEETING and SIGN_OFFICE (room plaques beside doors)</li>
 *   <li>floor + 3: TV_BRACKET (an empty TV wall bracket, conference rooms)</li>
 * </ul>
 */
public class OfficeWallFixtureBlock extends WallMountBlock {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** Which fitting this is (see the class comment). */
    public enum Kind implements StringRepresentable {
        DADO(Block.box(0, 10, 14.5, 16, 13, 16)),
        DADO_OUTLETS(Shapes.or(Block.box(0, 10, 14.5, 16, 13, 16), Block.box(3, 9.5, 14.2, 13, 13.5, 14.5))),
        DOOR_STOP(Block.box(7, 0.5, 12.5, 9, 2.5, 16)),
        TV_BRACKET(Block.box(4, 2, 12.5, 12, 10, 16)),
        COAT_HOOKS(Block.box(1, 6, 14, 15, 9.5, 16)),
        SANITISER(Block.box(6, 2, 13.5, 10, 8.5, 16)),
        FIRST_AID(Block.box(4, 2, 13, 12, 10, 16)),
        EVAC_PLAN(Block.box(2, 1, 15.5, 14, 12, 16)),
        NO_SMOKING(Block.box(5, 6, 15.6, 11, 12, 16)),
        FIRE_DOOR_SIGN(Block.box(5, 6, 15.6, 11, 12, 16)),
        SIGN_MEETING(Block.box(3, 6, 15.6, 13, 9, 16)),
        SIGN_OFFICE(Block.box(3, 6, 15.6, 13, 9, 16));

        /** The fitting's outline when it faces north (on a south wall). */
        private final VoxelShape shapeFacingNorth;

        Kind(VoxelShape shapeFacingNorth) {
            this.shapeFacingNorth = shapeFacingNorth;
        }

        /** The lowercase name used in blockstate files ("kind=first_aid"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Outlines per kind and facing, turned once up front (getShape runs very often). */
    private final Map<Kind, Map<Direction, VoxelShape>> shapes = turnAll(Kind.class, kind -> kind.shapeFacingNorth);

    public OfficeWallFixtureBlock(Properties properties) {
        super(properties, Kind.DADO.shapeFacingNorth);
        registerDefaultState(defaultBlockState().setValue(KIND, Kind.DADO));
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
