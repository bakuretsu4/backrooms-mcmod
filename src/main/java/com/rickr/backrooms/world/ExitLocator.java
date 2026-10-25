package com.rickr.backrooms.world;

/**
 * Places exactly one exit per square region of the world. The position inside the region comes
 * from the seed, so it is random but identical for every player and every time the chunk loads.
 * Exits keep {@code margin} blocks away from region edges so an exit never straddles two regions.
 */
public final class ExitLocator {
    private final int regionSize;
    private final int margin;
    private final int salt;

    public ExitLocator(int regionSize, int margin, int salt) {
        this.regionSize = regionSize;
        this.margin = margin;
        this.salt = salt;
    }

    public int exitX(long seed, int regionX, int regionZ) {
        return regionX * regionSize + margin + Hash.range(Hash.of(seed, regionX, regionZ, salt), regionSize - 2 * margin);
    }

    public int exitZ(long seed, int regionX, int regionZ) {
        return regionZ * regionSize + margin + Hash.range(Hash.of(seed, regionX, regionZ, salt + 1), regionSize - 2 * margin);
    }

    /** Signed offset from the exit in this column's region: [dx, dz]. */
    public int dx(long seed, int x, int z) {
        return x - exitX(seed, Math.floorDiv(x, regionSize), Math.floorDiv(z, regionSize));
    }

    public int dz(long seed, int x, int z) {
        return z - exitZ(seed, Math.floorDiv(x, regionSize), Math.floorDiv(z, regionSize));
    }

    /** Square ("chessboard") distance to the exit of the region containing (x, z). */
    public int distance(long seed, int x, int z) {
        return Math.max(Math.abs(dx(seed, x, z)), Math.abs(dz(seed, x, z)));
    }

    /** Nearest exit centre to (x, z), checking the surrounding 3x3 regions. Returns {x, z}. */
    public int[] nearest(long seed, int x, int z) {
        int rx = Math.floorDiv(x, regionSize);
        int rz = Math.floorDiv(z, regionSize);
        int[] best = null;
        long bestDist = Long.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ex = exitX(seed, rx + ox, rz + oz);
                int ez = exitZ(seed, rx + ox, rz + oz);
                long ddx = (long) ex - x;
                long ddz = (long) ez - z;
                long d = ddx * ddx + ddz * ddz;
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[] {ex, ez};
                }
            }
        }
        return best;
    }
}
