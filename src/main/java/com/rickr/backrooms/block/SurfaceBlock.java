package com.rickr.backrooms.block;

import com.rickr.backrooms.world.Hash;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A big flat surface (wallpaper, carpet, ceiling tile, concrete, ...) that comes in a few versions:
 * the plain texture and some with a local stain, crack or scuff (tools/gen_assets.py, "Surface
 * variants"). Without them the same stain would repeat on every block of a room, in a grid.
 *
 * <p>The world generator picks the version for each block ({@link #withVariant}), mostly the plain
 * one. Why not let Minecraft pick at random (a blockstate with several weighted models)? Its pick
 * depends on the block's position, and several anomalous areas loop or copy their rooms (you are
 * moved to an identical-looking copy without noticing): a stain that changed as you were moved
 * would give the trick away. So the generator leaves everything inside anomalous areas plain
 * (see BackroomsChunkGenerator), which only works if the version is a block state.
 */
public class SurfaceBlock extends Block {
    /** Which texture version: 0 = plain. The blockstate file maps unused numbers to the plain one. */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);

    /** Relative chance of each version, plain first (as many as the block has textures). */
    private final int[] weights;
    private final int total;

    public SurfaceBlock(Properties properties, int... weights) {
        super(properties);
        if (weights.length < 1 || weights.length > 4) {
            throw new IllegalArgumentException("A surface has 1 to 4 versions, got " + weights.length);
        }
        this.weights = weights.clone();
        int sum = 0;
        for (int w : weights) {
            sum += w;
        }
        this.total = sum;
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    /** The version this block gets at (x, y, z): a pure function of the seed and the position. */
    public BlockState withVariant(BlockState state, long seed, int x, int y, int z) {
        int pick = (int) (Hash.unit(seed, x, z, 7300 + y) * total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return state.setValue(VARIANT, i);
            }
        }
        return state;
    }
}
