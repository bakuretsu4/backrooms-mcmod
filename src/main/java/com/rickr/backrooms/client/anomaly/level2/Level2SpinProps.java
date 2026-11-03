package com.rickr.backrooms.client.anomaly.level2;

import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * The turning of Level 2's spin props (registry/Level2SpinBlocks), shared by their renderers
 * (WallFanRenderer, SpinBeaconRenderer) and the beacons' real Veil light cones
 * (client/light/VeilFixtures), so the lens, the drawn beam and the light all point the same way.
 *
 * <p>Both turn with {@link Level2Spin#phase}, i.e. from the level clock (the same for everyone),
 * and so they speed up, slow down, stop and drift back exactly when the whole corridor's turning
 * does. A beacon a block further along +x is {@value #BEACON_LAG_PER_BLOCK} radians behind, so
 * looking down the corridor the sweeps form a slow corkscrew into the dark.
 *
 * <p>Each prop turns about its FACING axis (out of its surface). Across that axis it has a fixed
 * pair of directions ({@link #u}, {@link #v}): for the ceiling and floor u runs along +x (the
 * corridor), for walls u points up; v = facing x u.
 */
public final class Level2SpinProps {
    /** Beacon sweeps per virtual turn: about one sweep every 3.5 s at the spin's mean rate. */
    private static final double BEACON_TURNS = 18.0;
    /** How far behind the beacon one block further along +x is (radians): a full turn per ~42 blocks. */
    private static final double BEACON_LAG_PER_BLOCK = 0.15;
    /** Fan turns per virtual turn: about 0.9 turns a second at the mean rate. */
    private static final double FAN_TURNS = 58.0;
    /** How far a beacon's cones lean away from its axis (radians, ~70 degrees): almost along the surface. */
    public static final float BEACON_LEAN = 1.22f;

    private static final float[][] U = new float[6][];
    private static final float[][] V = new float[6][];

    static {
        for (Direction d : Direction.values()) {
            float nx = d.getStepX();
            float ny = d.getStepY();
            float nz = d.getStepZ();
            float[] u = d.getAxis() == Direction.Axis.Y ? new float[] {1, 0, 0} : new float[] {0, 1, 0};
            U[d.ordinal()] = u;
            V[d.ordinal()] = new float[] {ny * u[2] - nz * u[1], nz * u[0] - nx * u[2], nx * u[1] - ny * u[0]};
        }
    }

    /** The first fixed direction across {@code facing} (x, y, z). */
    public static float[] u(Direction facing) {
        return U[facing.ordinal()];
    }

    /** The second fixed direction across {@code facing}: facing x u. */
    public static float[] v(Direction facing) {
        return V[facing.ordinal()];
    }

    /** The beacon at column {@code x}'s sweep angle round its axis (radians, from u toward v). */
    public static double beaconAngle(int x, float partialTick) {
        return Level2Spin.phase(partialTick) * BEACON_TURNS - x * BEACON_LAG_PER_BLOCK;
    }

    /** The fan at {@code (x, y, z)}'s blade angle (radians); each fan starts out of step with its neighbours. */
    public static double fanAngle(int x, int y, int z, float partialTick) {
        return Level2Spin.phase(partialTick) * FAN_TURNS + ((x * 31 + y * 17 + z * 7) & 15) * 0.39;
    }

    /**
     * The unit direction leaning {@code lean} radians away from {@code facing}'s axis, turned
     * {@code angle} round it (into {@code out}, no allocation).
     */
    public static Vector3f sweep(Direction facing, double angle, float lean, Vector3f out) {
        float[] u = U[facing.ordinal()];
        float[] v = V[facing.ordinal()];
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float along = (float) Math.cos(lean);
        float across = (float) Math.sin(lean);
        return out.set(facing.getStepX() * along + (u[0] * c + v[0] * s) * across,
                facing.getStepY() * along + (u[1] * c + v[1] * s) * across,
                facing.getStepZ() * along + (u[2] * c + v[2] * s) * across);
    }

    private Level2SpinProps() {}
}
