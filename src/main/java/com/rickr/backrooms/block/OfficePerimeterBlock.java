package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The low run of white metal trunking along the foot of Level 4's window walls, with a laminate
 * window board on top. Real open-plan offices hide their cables and heating pipes in exactly this
 * box under the glass, so one continuous run under every window wall reads as a real building.
 *
 * <p>FACING points away from the window wall, into the room (like every wall-mounted block). PART
 * picks the piece: TRUNK is the plain box, RADIATOR has a slim double-panel radiator set into it
 * (one under the middle of each window bay), SOCKET has a double power outlet on its front, END is
 * the box with its cover-plate joints showing (used where a run stops against a pier or a corner).
 * Every part has the same shape: 11/16 tall and 6/16 deep against the wall, with the window board
 * sticking out one more pixel.
 */
public class OfficePerimeterBlock extends WallMountBlock {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Which piece of the perimeter run this block is (see the class comment). */
    public enum Part implements StringRepresentable {
        TRUNK, RADIATOR, SOCKET, END;

        /** The lowercase name used in blockstate files ("part=radiator"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OfficePerimeterBlock(Properties properties) {
        super(properties, Shapes.or(
                Block.box(0, 0, 10, 16, 10, 16),      // the trunking box
                Block.box(0, 10, 9, 16, 11, 16)));    // the window board on top, a pixel proud
        registerDefaultState(defaultBlockState().setValue(PART, Part.TRUNK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }
}
