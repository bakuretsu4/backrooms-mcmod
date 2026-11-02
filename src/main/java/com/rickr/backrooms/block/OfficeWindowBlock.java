package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A pane of Level 4's ribbon windows. Canon: most windows are blacked out, yet they still glow.
 * Frosted glass gives off a cold, even grey light. It is a solid block in the wall: only its
 * FACING side shows glass, and the back and the sides are plain painted wall, so a window wall
 * looks like an outer wall from the room and like an ordinary wall from behind.
 *
 * <p>HALF picks the frame: the LOWER pane has the thin sill rail at its bottom, the UPPER pane the
 * head rail at its top, so two stacked panes make one tall window. PANE places the thin aluminium
 * mullions: real ribbon windows come in bays (here three blocks wide), with a mullion only where
 * one bay meets the next, not on every block. COVER is what blacks the bay out: lowered venetian
 * blinds, paper and foil taped over the glass, or nothing. The block gives off block light (less
 * when covered): that is the cold glow on the floor near the windows (see {@code ClientLighting}
 * and {@code Level4Atmosphere}).
 */
public class OfficeWindowBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Pane> PANE = EnumProperty.create("pane", Pane.class);
    public static final EnumProperty<Cover> COVER = EnumProperty.create("cover", Cover.class);

    /**
     * Where this pane sits in its bay, seen from inside the room (standing in front of the glass,
     * looking at it). "Left" is the viewer's left, which is the world direction
     * {@code FACING.getClockWise()}. LEFT has a mullion on its left edge, RIGHT on its right edge,
     * SINGLE (a one-block window) on both, MID on neither. A run of panes should start with LEFT
     * (or SINGLE) and end with RIGHT (or SINGLE), so its ends are always framed.
     */
    public enum Pane implements StringRepresentable {
        SINGLE, LEFT, MID, RIGHT;

        /** The lowercase name used in blockstate files ("pane=mid"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The same pane seen in a mirror (left and right swap). */
        public Pane mirrored() {
            return this == LEFT ? RIGHT : this == RIGHT ? LEFT : this;
        }
    }

    /**
     * What blacks the window out. Both panes of a bay get the same value. NONE: bare frosted
     * glass. HALF: venetian blinds let down over the upper pane only. BLINDS: blinds down to the
     * sill, the light showing between the slats. TAPED: paper and foil taped over the glass. The
     * glass glows the same behind every cover; the cover just hides most of it. {@link #light}
     * is the block light each one lets into the room.
     */
    public enum Cover implements StringRepresentable {
        NONE(12), HALF(12), BLINDS(11), TAPED(10);

        /** Block light given off by a pane with this cover (agreed with the Level 4 lighting). */
        public final int light;

        Cover(int light) {
            this.light = light;
        }

        /** The lowercase name used in blockstate files ("cover=blinds"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OfficeWindowBlock(Properties properties) {
        super(properties);
        // MID and NONE are the defaults, so windows saved before panes and covers existed load as
        // plain frosted panes.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(PANE, Pane.MID).setValue(COVER, Cover.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, PANE, COVER);
    }

    /** In creative: the glass faces you; placed on top of another pane it becomes the upper half. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        DoubleBlockHalf half = below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER
                ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, half);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror turns the pane round and swaps its left and right mullions. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(PANE, state.getValue(PANE).mirrored());
    }
}
