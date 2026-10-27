package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * One piece of the heavy steel ring a Level 2 corridor passes through (a "bulkhead frame"): solid
 * riveted steel plate with a raised lip round the opening, painted with yellow and black hazard
 * stripes. The corridor runs ACROSS FACING, so the ring stands in the plane of FACING.
 *
 * <ul>
 *   <li>PART = SIDE: the upright beside the opening (in the pipe chase); FACING points into the
 *       opening, away from the wall. Its lip stands 2 px proud on the FACING side.</li>
 *   <li>PART = TOP: the lintel over the opening; its lip hangs 2 px below it. FACING is either of
 *       the two directions across the corridor (the model is the same).</li>
 *   <li>PART = CORNER: where an upright meets the lintel (above the upright); FACING as for SIDE.
 *       The lips meet in a bolted corner gusset.</li>
 * </ul>
 * Every piece is a full block you can't walk through; the lips are only paint deep for collision.
 */
public class Level2BulkheadFrameBlock extends WallMountBlock {

    public enum Part implements StringRepresentable {
        SIDE, TOP, CORNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public Level2BulkheadFrameBlock(Properties properties) {
        super(properties, Shapes.block());
        registerDefaultState(defaultBlockState().setValue(PART, Part.SIDE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }
}
