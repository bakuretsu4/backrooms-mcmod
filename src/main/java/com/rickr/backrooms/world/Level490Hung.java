package com.rickr.backrooms.world;

import com.rickr.backrooms.world.layout.Level490Layout;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lives of Level 490's hung ruins (drawn by client/Level490SkyHung): which ruin hangs in which
 * 1500-block cell at a given game time, and when it falls. Shared by the client (to draw them) and
 * the server (to make one fall in front of you at the key moments, world/Level490Falls).
 *
 * <p>Each ruin hangs for one to two hours of game time, then its chains snap and it falls into the
 * cloud sea; 5-15 minutes later another hangs there. With about twenty in sight that is a fall
 * somewhere around you every few minutes. All of it follows from the layout seed and the game clock,
 * so everyone sees the same fall. The server can also make a ruin fall early (a "forced" fall),
 * which it remembers in the save and tells every client, so a ruin that fell stays gone.
 */
public final class Level490Hung {
    public static final int CELL = 1500;
    private static final double HOUR = 72000.0;
    /** A fall lasts this long (ticks). */
    public static final double FALL = 900.0;

    /** Cell (i, j)'s ruin at some moment: where it hangs, how old it is, when it falls (ticks of its age). */
    public record Life(int i, int j, long id, long gen, double x, double z, double y, double age, double falls) {
        public boolean falling() {
            return age >= falls;
        }

        public double fallSeconds() {
            return (age - falls) / 20.0;
        }
    }

    /** The client's forced falls, key(i, j) -> {generation, tick the fall starts}, as the server sent them. */
    public static final Map<Long, long[]> CLIENT = new ConcurrentHashMap<>();

    public static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xffffffffL);
    }

    /** Cell (i, j)'s ruin at game time t (ticks), or null when the cell is empty now. */
    public static Life life(long seed, int i, int j, double t, Map<Long, long[]> forced) {
        long h = Hash.of(seed, i, j, 4980);
        if (Hash.unit(h) > 0.55) {
            return null;
        }
        double period = HOUR * (1 + Hash.unit(Hash.mix(h)));
        double gap = 1200 * (5 + 10 * Hash.unit(Hash.mix(h + 1)));
        double phase = t + Hash.unit(Hash.mix(h + 2)) * period;
        long gen = (long) Math.floor(phase / period);
        double age = phase - gen * period;
        double falls = period - gap;
        long[] f = forced.get(key(i, j));
        if (f != null && f[0] == gen) {
            // Made to fall early: its age when that fall started.
            falls = Math.min(falls, age - (t - f[1]));
        }
        if (age > falls + FALL) {
            return null;
        }
        long g = Hash.of(h, (int) gen, (int) (gen >>> 32), 4981);
        double x = (i + 0.15 + 0.7 * Hash.unit(g)) * CELL;
        double z = (j + 0.15 + 0.7 * Hash.unit(Hash.mix(g))) * CELL;
        // Never over a spire's island: that is where you are going.
        int[] site = Level490Layout.siteCentre(seed, (int) x, (int) z);
        if (Math.hypot(x - site[0], z - site[1]) < 450) {
            return null;
        }
        return new Life(i, j, g, gen, x, z, 170 + 260 * Hash.unit(Hash.mix(g + 1)), age, falls);
    }

    private Level490Hung() {}
}
