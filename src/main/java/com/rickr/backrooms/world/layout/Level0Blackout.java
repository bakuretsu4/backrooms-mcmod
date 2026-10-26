package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;

/**
 * The shape of a Blackout Zone (https://backrooms-wiki.wikidot.com/level-0: "whole sections with no
 * light... the buzz is replaced by intense silence... recessed floors hold ankle-deep fluid").
 *
 * <p>The zone is the ordinary maze, only different: no light fixtures at all, rough dark walls and
 * a layer of murky fluid on the carpet (see Level0Layout). Its outline is an irregular blob: an
 * ellipse (centre = the site's anchor) whose edge wobbles in and out by up to 30%, using smooth
 * value noise so the boundary meanders rather than zig-zags. The site's bounds are just big enough
 * to hold the wobble, so both radii can be read back from them: the client, which doesn't know
 * the seed, gets exactly the same shape.
 */
public final class Level0Blackout {
    /** The edge moves in and out by this fraction of the radius. */
    private static final double WOBBLE = 0.3;
    /** The wobble changes over this many blocks. */
    private static final int NOISE_CELL = 7;

    private Level0Blackout() {}

    /** Half the width of the bounds that hold an ellipse radius {@code r} plus its wobble. */
    static int halfExtent(int r) {
        return (int) Math.ceil(r * Math.sqrt(1.0 + WOBBLE)) + 1;
    }

    private static double radius(int min, int max) {
        int half = (max - min) / 2;
        return (half - 1) / Math.sqrt(1.0 + WOBBLE);
    }

    /** True if column (x, z) is part of the zone. */
    public static boolean inside(AnomalySite site, int x, int z) {
        return depth(site, x + 0.5, z + 0.5) > 0.0;
    }

    /**
     * How deep into the zone a point is: 0 at (or outside) the edge, 1 at the very centre. The
     * client uses it to make the picture darker the further in you go.
     */
    public static double depth(AnomalySite site, double x, double z) {
        double rx = radius(site.minX(), site.maxX());
        double rz = radius(site.minZ(), site.maxZ());
        double nx = (x - (site.anchorX() + 0.5)) / rx;
        double nz = (z - (site.anchorZ() + 0.5)) / rz;
        double d = nx * nx + nz * nz;
        double edge = 1.0 + WOBBLE * (2.0 * noise(site.salt(), x, z) - 1.0);
        return Math.max(0.0, 1.0 - d / edge);
    }

    /** Smooth value noise in [0, 1): hashed corners of a NOISE_CELL grid, blended. */
    private static double noise(long salt, double x, double z) {
        double gx = x / NOISE_CELL;
        double gz = z / NOISE_CELL;
        int ix = (int) Math.floor(gx);
        int iz = (int) Math.floor(gz);
        double fx = smooth(gx - ix);
        double fz = smooth(gz - iz);
        double a = Hash.unit(salt, ix, iz, 9);
        double b = Hash.unit(salt, ix + 1, iz, 9);
        double c = Hash.unit(salt, ix, iz + 1, 9);
        double d = Hash.unit(salt, ix + 1, iz + 1, 9);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
