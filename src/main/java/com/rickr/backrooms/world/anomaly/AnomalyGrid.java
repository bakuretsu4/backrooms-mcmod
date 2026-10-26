package com.rickr.backrooms.world.anomaly;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Places at most one anomaly site in each {@code cell} x {@code cell} square of the world, the same
 * way safe rooms are placed: a pure function of (seed, cellX, cellZ) decides whether the square has
 * a site, what kind, and exactly which columns it covers.
 *
 * <p>A site may stick out of its own square by up to {@code reach} squares (so big sites near a
 * square's edge still fit); lookups check that many squares around a column.
 *
 * <p>Planners are asked for the same square many times (every column of every chunk near it), so
 * their answers are cached. Keep planners pure: same inputs, same site, no world access.
 */
public final class AnomalyGrid {
    /** Decides the site of one square, or null if that square has none. */
    @FunctionalInterface
    public interface Planner {
        @Nullable
        AnomalySite plan(long seed, int cellX, int cellZ);
    }

    private static final int CACHE_LIMIT = 8192;

    private final int cell;
    private final int reach;
    private final Planner planner;
    private final ConcurrentHashMap<Key, Optional<AnomalySite>> cache = new ConcurrentHashMap<>();

    private record Key(long seed, int cellX, int cellZ) {}

    public AnomalyGrid(int cell, int reach, Planner planner) {
        this.cell = cell;
        this.reach = reach;
        this.planner = planner;
    }

    public int cellSize() {
        return cell;
    }

    /** The site of square (cellX, cellZ), or null. */
    @Nullable
    public AnomalySite site(long seed, int cellX, int cellZ) {
        Key key = new Key(seed, cellX, cellZ);
        Optional<AnomalySite> hit = cache.get(key);
        if (hit == null) {
            if (cache.size() > CACHE_LIMIT) {
                cache.clear();
            }
            hit = Optional.ofNullable(planner.plan(seed, cellX, cellZ));
            cache.put(key, hit);
        }
        return hit.orElse(null);
    }

    /** The site covering column (x, z), or null. */
    @Nullable
    public AnomalySite at(long seed, int x, int z) {
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        for (int ox = -reach; ox <= reach; ox++) {
            for (int oz = -reach; oz <= reach; oz++) {
                AnomalySite s = site(seed, cx + ox, cz + oz);
                if (s != null && s.contains(x, z)) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Every site that comes within {@code radius} blocks of column (x, z). */
    public void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        int r = Math.floorDiv(radius, cell) + reach + 1;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                AnomalySite s = site(seed, cx + ox, cz + oz);
                if (s != null && s.near(x, z, radius)) {
                    out.accept(s);
                }
            }
        }
    }

    /** The nearest site matching {@code filter}, searching up to {@code maxRings} squares away (for /backrooms anomaly locate). */
    @Nullable
    public AnomalySite nearest(long seed, int x, int z, Predicate<AnomalySite> filter, int maxRings) {
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        AnomalySite best = null;
        double bestDist = Double.MAX_VALUE;
        for (int ring = 0; ring <= maxRings; ring++) {
            for (int ox = -ring; ox <= ring; ox++) {
                for (int oz = -ring; oz <= ring; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != ring) {
                        continue;
                    }
                    AnomalySite s = site(seed, cx + ox, cz + oz);
                    if (s != null && filter.test(s)) {
                        double d = s.distanceTo(x, z);
                        if (d < bestDist) {
                            bestDist = d;
                            best = s;
                        }
                    }
                }
            }
            if (best != null && bestDist < (ring - reach) * (double) cell) {
                break; // nothing in a farther ring can be closer
            }
        }
        return best;
    }
}
