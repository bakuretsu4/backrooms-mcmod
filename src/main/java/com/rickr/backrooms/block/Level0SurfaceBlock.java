package com.rickr.backrooms.block;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Level 0 surface (wallpaper, baseboard, carpet, ceiling tile): a {@link SurfaceBlock} whose
 * aged versions cluster into patches instead of being sprinkled evenly.
 *
 * <p>Real buildings don't age evenly: a leak soaks one stretch of ceiling, the carpet under it
 * and the wall beside it, while the next room stays clean. So the map gets a soft, blobby "wear"
 * field ({@link #worn}), the same one for every surface. Inside a worn patch (about a quarter of
 * the map) the {@code worn} chances apply and the aged versions are common; everywhere else the
 * {@code calm} chances apply and the plain version dominates.
 *
 * <p>Some versions only make sense on one wall row (a leak stain must start at the ceiling, hand
 * marks belong at hand height), so each version can be limited to a row: a pick on any other row
 * falls back to the plain version.
 *
 * <p>Textures: tools/anomaly_assets/level0_surfaces.py. Counts must match gen_assets.py
 * SURFACE_VARIANTS.
 */
public class Level0SurfaceBlock extends SurfaceBlock {
    /** A version allowed on every row. */
    public static final int ANY_ROW = 0;
    /** Wall rows, counted from the floor: 1 = the baseboard row, 2 = hand height, 3 = under the ceiling. */
    public static final int HAND_ROW = 2;
    public static final int TOP_ROW = 3;

    /** Size of one cell of the wear field in blocks; patches come out roughly this big. */
    private static final int PATCH = 9;
    /** Wear above this counts as a worn patch (about 27% of the map). */
    private static final double WORN_FROM = 0.64;
    /** Hash salt of the wear field: shared by all surfaces so a damp patch is damp on every face. */
    private static final int WEAR_SALT = 7350;

    private final int[] calm;
    private final int[] worn;
    private final int[] rows;
    private final int calmTotal;
    private final int wornTotal;

    /**
     * @param calm relative chances of each version (plain first) outside worn patches
     * @param worn the same inside worn patches
     * @param rows for each version, the only wall row it may appear on ({@link #ANY_ROW} = all)
     */
    public Level0SurfaceBlock(Properties properties, int[] calm, int[] worn, int... rows) {
        super(properties, calm);
        if (worn.length != calm.length || rows.length != calm.length) {
            throw new IllegalArgumentException("calm, worn and rows need one entry per version");
        }
        this.calm = calm.clone();
        this.worn = worn.clone();
        this.rows = rows.clone();
        this.calmTotal = sum(calm);
        this.wornTotal = sum(worn);
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
        boolean patch = wear(seed, x, z) > WORN_FROM;
        int[] weights = patch ? worn : calm;
        int pick = (int) (Hash.unit(seed, x, z, 7300 + y) * (patch ? wornTotal : calmTotal));
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

    /**
     * The wear field at (x, z), 0..1: random values on a coarse grid, blended smoothly in between
     * (so patches have soft, irregular edges). Pure arithmetic: called for every surface block
     * while a chunk is generated, so it must not allocate.
     */
    static double wear(long seed, int x, int z) {
        int cx = Math.floorDiv(x, PATCH);
        int cz = Math.floorDiv(z, PATCH);
        double fx = smooth((x - cx * PATCH + 0.5) / PATCH);
        double fz = smooth((z - cz * PATCH + 0.5) / PATCH);
        double a = Hash.unit(seed, cx, cz, WEAR_SALT);
        double b = Hash.unit(seed, cx + 1, cz, WEAR_SALT);
        double c = Hash.unit(seed, cx, cz + 1, WEAR_SALT);
        double d = Hash.unit(seed, cx + 1, cz + 1, WEAR_SALT);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
