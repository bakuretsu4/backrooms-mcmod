package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Spray-stencilled lettering on a Level 2 wall: section numbers, warnings, arrows and pipe labels.
 * It is only paint: a decal in the AIR block in front of the wall (or bulkhead frame), FACING
 * away from it, that you walk straight through. VARIANT picks the stencil.
 */
public class Level2StencilBlock extends WallMountBlock {

    public enum Variant implements StringRepresentable {
        /** Section numbers, "2-A 03" ... "2-D 32", for the bulkhead frames and corridor walls. */
        SEC_A03, SEC_A11, SEC_B14, SEC_B27, SEC_C06, SEC_C19, SEC_D08, SEC_D32,
        /** "DANGER / HOT SURFACE" with a warning triangle (red and black on yellow). */
        DANGER_HOT,
        /** A crossed-out cigarette and "NO SMOKING". */
        NO_SMOKING,
        /** A big painted arrow pointing left or right as you face the wall. */
        ARROW_LEFT, ARROW_RIGHT,
        /** Pipe labels, a flow arrow under the word (pointing right). */
        STEAM, RETURN, H2O,
        /** "EXIT" with an arrow pointing right, in green, for the way to the exit bay. */
        EXIT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);

    public Level2StencilBlock(Properties properties) {
        super(properties, 0, 0, 15.5, 16, 16, 16);
        registerDefaultState(defaultBlockState().setValue(VARIANT, Variant.SEC_A03));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
}
