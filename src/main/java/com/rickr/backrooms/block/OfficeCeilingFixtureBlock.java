package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A Level 4 ceiling tile with a small fitting in it, the kind every real office ceiling has on a
 * sparse regular grid: a full ceiling block with a tiny modelled part just below the tile.
 *
 * <p>KIND: SPRINKLER is a chrome pendant sprinkler head on its escutcheon, SMOKE a round white smoke
 * detector, SPEAKER a flush round PA speaker grille.
 */
public class OfficeCeilingFixtureBlock extends Block {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** Which fitting the tile has (see the class comment). */
    public enum Kind implements StringRepresentable {
        SPRINKLER, SMOKE, SPEAKER;

        /** The lowercase name used in blockstate files ("kind=smoke"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OfficeCeilingFixtureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.SPRINKLER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
