package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The rolling hills of Level 94: smooth round domes on a flat plain (grass top at y40).
 *
 * <p>The world is cut into {@link #HILL_CELL}-sized cells and each cell may hold one dome somewhere
 * inside it. A dome's height falls off as (1 - s^2)^2 with s = distance / radius, which gives a
 * round top and soft feet, and is never steeper than about one block up per block across, so
 * every hill can be climbed. Where domes overlap, the higher one wins.
 *
 * <p>Domes shrink away from roads, towns and castles, so roads always run through the valleys and
 * the land near them stays flat.
 */
final class Level94Terrain {
    static final int HILL_CELL = 48;
    /** Largest dome radius; a dome reaches at most this far from its centre. */
    static final int MAX_RADIUS = 40;

    // What stands on top of a hill.
    static final int NOTHING = 0;
    static final int HOUSE = 1;
    static final int TOWER = 2;
    static final int TREE = 3;
    static final int FRAGMENT = 4;
    static final int CAR = 5;

    private Level94Terrain() {}

    /** One dome: centre column, radius, height above the plain and what stands on top. */
    static final class Hill {
        final int ci;
        final int cj;
        final int cx;
        final int cz;
        final float radius;
        final float height;
        final int feature;
        final long hash;

        Hill(int ci, int cj, int cx, int cz, float radius, float height, int feature, long hash) {
            this.ci = ci;
            this.cj = cj;
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.height = height;
            this.feature = feature;
            this.hash = hash;
        }

        /** Height of this dome above the plain at (x, z), 0 outside it. */
        float at(float x, float z) {
            float dx = x - (cx + 0.5f);
            float dz = z - (cz + 0.5f);
            float s2 = (dx * dx + dz * dz) / (radius * radius);
            if (s2 >= 1) {
                return 0;
            }
            float k = 1 - s2;
            return height * k * k;
        }
    }

    /**
     * The dome of cell (ci, cj), or null if it has none (or the roads left it no room).
     *
     * @param roads every road within {@link Level94Roads#CAP} of the cell's possible centres
     */
    @Nullable
    static Hill hill(long seed, int ci, int cj, List<Level94Roads.Road> roads) {
        long h = cellHash(seed, ci, cj);
        if (Hash.unit(h) >= 0.92) {
            return null;
        }
        int cx = centreX(h, ci);
        int cz = centreZ(h, cj);
        float r0 = 14 + (float) Hash.unit(Hash.mix(h + 3)) * (MAX_RADIUS - 14);
        // Height 45-62% of the radius: tall enough to read as a dome, never too steep to climb.
        float h0 = Math.max(6, Math.min(26, r0 * (0.45f + 0.17f * (float) Hash.unit(Hash.mix(h + 4)))));

        float px = cx + 0.5f;
        float pz = cz + 0.5f;
        float room = Level94Roads.roadDistance(roads, px, pz) - 4;
        room = Math.min(room, (float) (Level94Roads.castleDistance(seed, px, pz) - Level94Castle.CLEAR_RADIUS - 6));
        room = Math.min(room, (float) (Level94Roads.townDistance(seed, px, pz) - 8));
        float r = Math.min(r0, room);
        if (r < 10) {
            return null;
        }
        float height = h0 * r / r0;
        if (height < 5) {
            return null;
        }

        double f = featureRoll(h);
        int feature;
        if (r >= 16) {
            feature = f < 0.30 ? HOUSE
                    : f < 0.36 && r >= 18 ? TOWER
                    : f < 0.52 ? TREE
                    : f < 0.58 ? FRAGMENT
                    : f < 0.605 ? CAR
                    : NOTHING;
        } else {
            feature = f < 0.35 ? TREE : NOTHING;
        }
        return new Hill(ci, cj, cx, cz, r, height, feature, h);
    }

    /** The random bits of hill cell (ci, cj); a hilltop building uses them as its key. */
    static long cellHash(long seed, int ci, int cj) {
        return Hash.of(seed, ci, cj, 9450);
    }

    /** Centre column of cell ci's dome (x), if it has one. */
    static int centreX(long cellHash, int ci) {
        return ci * HILL_CELL + 8 + Hash.range(Hash.mix(cellHash + 1), HILL_CELL - 16);
    }

    /** Centre column of cell cj's dome (z), if it has one. */
    static int centreZ(long cellHash, int cj) {
        return cj * HILL_CELL + 8 + Hash.range(Hash.mix(cellHash + 2), HILL_CELL - 16);
    }

    private static double featureRoll(long cellHash) {
        return Hash.unit(Hash.mix(cellHash + 5));
    }

    /**
     * Cheap pre-check (no roads needed): false when the cell can never hold a hilltop house or
     * fragment, whatever the roads do to its dome. Used by the furniture-drift anomaly planner.
     */
    static boolean mayHoldHouse(long cellHash) {
        if (Hash.unit(cellHash) >= 0.92) {
            return false;
        }
        double f = featureRoll(cellHash);
        return f < 0.30 || (f >= 0.52 && f < 0.58);
    }

    /** A slow, smooth wiggle (about +-1) that keeps the mowing stripes from looking ruler-straight. */
    static float wobble(int x, int z) {
        return (float) (0.6 * Math.sin(x * 0.071 + Math.sin(z * 0.043) * 1.7)
                + 0.5 * Math.sin(z * 0.083 + Math.sin(x * 0.037) * 1.9));
    }

    /**
     * Which of the three grass shades (0 light, 1 mid, 2 dark) column (x, z) gets. The stripes are
     * contour bands of the land: around the hills they follow the height, on the flat they run
     * parallel to the nearest road, like a freshly mown verge.
     */
    static int shade(float height, float roadDist, int x, int z) {
        float v = height * 0.8f + Math.min(roadDist, Level94Roads.CAP) * 0.34f + wobble(x, z);
        int band = (int) Math.floor(v / 1.7f);
        return switch (band & 3) {
            case 1 -> 1;
            case 3 -> 2;
            default -> 0;
        };
    }
}
