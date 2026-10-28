package com.rickr.backrooms.block;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level2Damp;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Level 2 concrete surface (wall, wall base, floor, ceiling slab): a {@link SurfaceBlock} whose
 * wet versions gather in the damp stretches of the corridor instead of being sprinkled evenly.
 *
 * <p>Each block has two sets of chances for its versions (plain first): {@code dry} for most of
 * the corridor, where the plain version dominates and only the odd rust stain or chip shows, and
 * {@code damp} for the stretches {@link Level2Damp} marks (round a leak, and long patches where
 * the pipes sweat), where condensation, tide lines and wet patches take over. So a wet stretch is
 * wet on the floor, the wall and the ceiling together, the way real water behaves.
 *
 * <p>Some versions only make sense on one row: condensation runs down from the ceiling, so its
 * stain starts at the top edge of the block and belongs on the wall row right under the ceiling.
 * A pick on any other row falls back to the plain version.
 *
 * <p>Textures: tools/anomaly_assets/level2_materials.py. Counts must match gen_assets.py
 * SURFACE_VARIANTS.
 */
public class Level2SurfaceBlock extends SurfaceBlock {
    /** A version allowed on every row. */
    public static final int ANY_ROW = Integer.MIN_VALUE;
    /** The wall row right under the Level 2 ceiling (floor y40 + 4 = y44; the ceiling is y45). */
    public static final int TOP_ROW = 4;

    private final int[] dry;
    private final int[] damp;
    private final int[] rows;
    private final int dryTotal;
    private final int dampTotal;

    /**
     * @param dry  relative chances of each version (plain first) outside damp stretches
     * @param damp the same inside damp stretches
     * @param rows for each version, the only row (counted from the floor) it may appear on, or {@link #ANY_ROW}
     */
    public Level2SurfaceBlock(Properties properties, int[] dry, int[] damp, int... rows) {
        super(properties, dry);
        if (damp.length != dry.length || rows.length != dry.length) {
            throw new IllegalArgumentException("dry, damp and rows need one entry per version");
        }
        this.dry = dry.clone();
        this.damp = damp.clone();
        this.rows = rows.clone();
        this.dryTotal = sum(dry);
        this.dampTotal = sum(damp);
    }

    private static int sum(int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        return total;
    }

    @Override
    public BlockState withVariant(BlockState state, long seed, int x, int y, int z) {
        boolean wet = Level2Damp.isDamp(seed, x, z);
        int[] weights = wet ? damp : dry;
        int pick = (int) (Hash.unit(seed, x, z, 7300 + y) * (wet ? dampTotal : dryTotal));
        int row = y - LevelLayout.FLOOR_Y;
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                boolean fits = rows[i] == ANY_ROW || rows[i] == row;
                return state.setValue(VARIANT, fits ? i : 0);
            }
        }
        return state;
    }
}
