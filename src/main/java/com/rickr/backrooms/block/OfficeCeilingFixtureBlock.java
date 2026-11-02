package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A Level 4 ceiling tile with something in or under it: a full ceiling block with a small
 * modelled part just below the tile.
 *
 * <p>KIND: SPRINKLER is a chrome pendant sprinkler head on its escutcheon, SMOKE a round white smoke
 * detector, SPEAKER a flush round PA speaker grille (these three on a sparse regular grid);
 * PROJECTOR is the empty ceiling mount of a conference room's projector (a drop pole and a bare
 * plate); CABLE is a tile with a cut data cable dangling out of a hole, and LIFTED a tile pushed up
 * out of its grid at one corner, a dark gap showing (both for water-damaged patches).
 */
public class OfficeCeilingFixtureBlock extends Block {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** Which fitting the tile has (see the class comment). */
    public enum Kind implements StringRepresentable {
        SPRINKLER, SMOKE, SPEAKER, PROJECTOR, CABLE, LIFTED;

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
