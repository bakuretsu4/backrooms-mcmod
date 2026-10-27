package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.DomeBlock;
import com.rickr.backrooms.block.VaultBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The curved marble ceilings of the Level 39 tunnels, worked out block by block.
 *
 * <p>A run's barrel vault is a half circle of radius {@value #R} whose centre is
 * {@value #Y0} high over the run's centre line: under the curve is air, over it marble. A block
 * the curve passes through gets a {@link VaultBlock} piece whose model follows the curve in
 * 1/16-block steps. A junction chamber is a round room under a shallow dome ({@link DomeBlock}),
 * radius {@value #DOME_WALL} to its wall. The same formulas generate the piece models in
 * tools/anomaly_assets/level39.py, so piece and model always agree.
 *
 * <p>Each cell is AIR (wholly under the curve), SOLID (wholly over it) or a piece.
 */
final class Level39Vault {
    /** Height of the barrel vault's centre (the springing line of the arc). */
    static final double Y0 = 43.0;
    /** Radius of the barrel vault: from the centre line to the face of the walls. */
    static final double R = 4.5;
    /** The dome: a sphere of this radius centred this high over the chamber's middle... */
    static final double DOME_R = 10.0;
    static final double DOME_Y0 = 40.0;
    /** ...cut off by the round wall at this radius. */
    static final double DOME_WALL = 7.5;
    /** Floor of the cells the pieces are counted from (the walkway feet level). */
    static final int BASE_Y = Level39Layout.TUNNEL_FEET_Y;

    static final int AIR = 0;
    static final int SOLID = 1;
    static final int PIECE = 2;

    private Level39Vault() {}

    static double barrelCeiling(double u) {
        double a = Math.abs(u);
        return a >= R ? Double.NEGATIVE_INFINITY : Y0 + Math.sqrt(R * R - a * a);
    }

    static double domeCeiling(double r) {
        return r > DOME_WALL ? Double.NEGATIVE_INFINITY : DOME_Y0 + Math.sqrt(DOME_R * DOME_R - r * r);
    }

    /** AIR, SOLID or PIECE for the vault cell at offset |o| from the centre line, {@code row} above the walkway. */
    static int barrelCell(int ao, int row) {
        if (ao > 4) {
            return SOLID;
        }
        double bottom = BASE_Y + row;
        double top = bottom + 1;
        // Below the springing line the vault's sides are straight walls at R: a cell wholly inside
        // them and wholly below Y0 is open (the walkways). Without this the curve function, which
        // only describes the arc, would fill the rims at feet height and nobody could stand there.
        if (top <= Y0 && ao + 0.5 <= R) return AIR;
        double cMax = barrelCeiling(Math.max(0, ao - 0.5));
        double cMin = barrelCeiling(ao + 0.5);
        if (cMin >= top) return AIR;
        if (cMax <= bottom) return SOLID;
        return PIECE;
    }

    /** AIR, SOLID or PIECE for the dome cell (a, b) (south-east quarter) {@code row} above the walkway. */
    static int domeCell(int a, int b, int row) {
        if (a > 8 || b > 8) {
            return SOLID;
        }
        double bottom = BASE_Y + row;
        double top = bottom + 1;
        double rNear = Math.hypot(Math.max(0, a - 0.5), Math.max(0, b - 0.5));
        double rFar = Math.hypot(a + 0.5, b + 0.5);
        double cMax = domeCeiling(rNear);
        double cMin = domeCeiling(rFar);
        if (cMin >= top) return AIR;
        if (cMax <= bottom) return SOLID;
        return PIECE;
    }

    /**
     * The vault piece for offset {@code o} (signed) of a run along x ({@code alongX}) or z.
     * The model is drawn for SOUTH (offset growing toward +z).
     */
    static BlockState barrelPiece(boolean alongX, int o, int row) {
        Direction facing = alongX ? (o >= 0 ? Direction.SOUTH : Direction.NORTH) : (o >= 0 ? Direction.EAST : Direction.WEST);
        return Level39Blocks.VAULT.get().defaultBlockState()
                .setValue(VaultBlock.FACING, facing)
                .setValue(VaultBlock.O, Math.abs(o))
                .setValue(VaultBlock.ROW, row);
    }

    /** The dome piece for the cell (dx, dz) from the chamber's centre block. */
    static BlockState domePiece(int dx, int dz, int row) {
        int a;
        int b;
        Direction facing;
        if (dx >= 0 && dz >= 0) {
            a = dx;
            b = dz;
            facing = Direction.SOUTH;
        } else if (dx < 0 && dz >= 0) {
            a = dz;
            b = -dx;
            facing = Direction.WEST;
        } else if (dx < 0) {
            a = -dx;
            b = -dz;
            facing = Direction.NORTH;
        } else {
            a = -dz;
            b = dx;
            facing = Direction.EAST;
        }
        return Level39Blocks.DOME.get().defaultBlockState()
                .setValue(DomeBlock.FACING, facing)
                .setValue(DomeBlock.PIECE, (a * 9 + b) * 10 + row);
    }

    /** The (a, b) a cell (dx, dz) turns into (see {@link #domePiece}); returns a * 16 + b. */
    static int domeCanonical(int dx, int dz) {
        if (dx >= 0 && dz >= 0) return dx * 16 + dz;
        if (dx < 0 && dz >= 0) return dz * 16 - dx;
        if (dx < 0) return -dx * 16 - dz;
        return -dz * 16 + dx;
    }
}
