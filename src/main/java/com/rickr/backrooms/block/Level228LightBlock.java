package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A coloured light panel in Level 228's ceiling. The maze is lit in sectors of blue, green or red
 * (COLOR), each panel its own SHADE (0 the deepest and dimmest .. 3 the palest and brightest), so a
 * sector is never one flat colour. It glows 12 + SHADE: under a shader pack (which lights by block
 * light alone) a corridor must stay readable from end to end, with brighter pools under the panels.
 */
public class Level228LightBlock extends Block {
    public enum Color implements StringRepresentable {
        BLUE, GREEN, RED;

        private final String name = name().toLowerCase(Locale.ROOT);

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Color> COLOR = EnumProperty.create("color", Color.class);
    public static final IntegerProperty SHADE = IntegerProperty.create("shade", 0, 3);

    public Level228LightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, Color.BLUE).setValue(SHADE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, SHADE);
    }

    /** The block light a panel gives (the registry's lightLevel): 12..15. */
    public static int emission(BlockState state) {
        return 12 + state.getValue(SHADE);
    }
}
