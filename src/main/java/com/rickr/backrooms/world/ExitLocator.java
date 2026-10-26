package com.rickr.backrooms.world;

/**
 * Places exactly one exit per square region of the world. The position inside the region comes
 * from the seed, so it is random but identical for every player and every time the chunk loads.
 * Exits keep {@code margin} blocks away from region edges so an exit never straddles two regions.
 *
 * <p>A level can also have a "copied band" (see {@link #copying}): a strip of the world that is an
 * exact copy of another part of it, exits included.
 */
public final class ExitLocator {
    private final int regionSize;
    private final int margin;
    private final int salt;
    /** Columns with copyMinX <= x < copyMaxX copy the exits {@code copyShift} blocks west of them. */
    private final int copyMinX;
    private final int copyMaxX;
    private final int copyShift;

    public ExitLocator(int regionSize, int margin, int salt) {
        this(regionSize, margin, salt, 0, 0, 0);
    }

    private ExitLocator(int regionSize, int margin, int salt, int copyMinX, int copyMaxX, int copyShift) {
        this.regionSize = regionSize;
        this.margin = margin;
        this.salt = salt;
        this.copyMinX = copyMinX;
        this.copyMaxX = copyMaxX;
        this.copyShift = copyShift;
    }

    /**
     * The same exits, except that the band of columns {@code minX <= x < maxX} repeats, exit for
     * exit, the exits {@code shift} blocks west of it. The Poolrooms use this for the Sanguine
     * Reservoir, an exact copy of the level far to the east: its drains must sit exactly where the
     * copied level has them, and every bit of code that asks "where is the nearest exit?" (the drain
     * current, the camcorder signal, /backrooms locate) then gets the right answer there too.
     */
    public ExitLocator copying(int minX, int maxX, int shift) {
        return new ExitLocator(regionSize, margin, salt, minX, maxX, shift);
    }

    /** The column whose exits column x uses: x itself, or its original if x is in the copied band. */
    private int home(int x) {
        return x >= copyMinX && x < copyMaxX ? x - copyShift : x;
    }

    public int exitX(long seed, int regionX, int regionZ) {
        return regionX * regionSize + margin + Hash.range(Hash.of(seed, regionX, regionZ, salt), regionSize - 2 * margin);
    }

    public int exitZ(long seed, int regionX, int regionZ) {
        return regionZ * regionSize + margin + Hash.range(Hash.of(seed, regionX, regionZ, salt + 1), regionSize - 2 * margin);
    }

    /** Signed offset from the exit in this column's region: [dx, dz]. */
    public int dx(long seed, int x, int z) {
        int hx = home(x);
        return hx - exitX(seed, Math.floorDiv(hx, regionSize), Math.floorDiv(z, regionSize));
    }

    public int dz(long seed, int x, int z) {
        return z - exitZ(seed, Math.floorDiv(home(x), regionSize), Math.floorDiv(z, regionSize));
    }

    /** Square ("chessboard") distance to the exit of the region containing (x, z). */
    public int distance(long seed, int x, int z) {
        return Math.max(Math.abs(dx(seed, x, z)), Math.abs(dz(seed, x, z)));
    }

    /** Nearest exit centre to (x, z), checking the surrounding 3x3 regions. Returns {x, z}. */
    public int[] nearest(long seed, int x, int z) {
        int hx = home(x);
        int rx = Math.floorDiv(hx, regionSize);
        int rz = Math.floorDiv(z, regionSize);
        int[] best = null;
        long bestDist = Long.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ex = exitX(seed, rx + ox, rz + oz);
                int ez = exitZ(seed, rx + ox, rz + oz);
                long ddx = (long) ex - hx;
                long ddz = (long) ez - z;
                long d = ddx * ddx + ddz * ddz;
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[] {ex, ez};
                }
            }
        }
        best[0] += x - hx; // back into the copied band, if that's where we are
        return best;
    }
}
