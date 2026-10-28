package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.GiantChainBlock;
import com.rickr.backrooms.block.IronLanternBlock;
import com.rickr.backrooms.registry.Level490Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Level 490, "Penance": crumbling ruins on floating rock islands over a sea of storm cloud, at
 * night, among colossal chains that hang out of the storm (docs/level-490-penance.md; the look
 * after Elden Ring's Crumbling Farum Azula).
 *
 * <pre>
 *   y 0-511      the colossal chains: links 34 long, bars 6 thick, from the cloud sea into the storm
 *   y ~194-206   the islands' ground (each island its own height), the bridges between them
 *   below        each island's rock tapering down into the void, 15-30 blocks deep
 *   y < 150      falling this far into the clouds sends you back (world/Level490Rules)
 * </pre>
 *
 * <p>Islands sit on a loose grid every {@value #SPACING} blocks (some missing), each a ragged
 * disc of rock with a crumbling rim. On them stand the ruins: rings of pillars (many broken, a
 * few still carrying their capitals), walls pierced by arches with jagged broken tops, round
 * tower stubs, rubble, dead trees, now and then a small stone shelter with a checkpoint radio (a
 * safe room). Straight stone bridges with spiked iron railings join neighbouring islands, climbing
 * in half steps where the islands' heights differ: main bridges 5 wide, side bridges 3, some side
 * bridges broken off in the middle, stretches of railing gone, small chains dangling underneath.
 *
 * <p>Each {@value #REGION}-block region holds one <b>site</b>, a great round island ringed with
 * broken pillars where the structure stands (stage 2), with four colossal chains around it, and
 * about 300 blocks away the <b>arrival</b> island. A route of main bridges always joins the two.
 * One island in forty or so has a colossal chain running straight through it, its ruins built
 * around the chain like a collar. Falling off the site is the way out (to Level 9); falling
 * anywhere else sends you back to your last checkpoint.
 */
public final class Level490Layout extends LevelLayout {
    public static final int WORLD_HEIGHT = 512;
    /** The ground of the arrival island and the site; the other islands are a few blocks off. */
    public static final int DECK_Y = 200;
    public static final int STAND_Y = DECK_Y + 1;
    /** Below this you have fallen into the clouds. */
    public static final int FALL_Y = 150;
    public static final int SPACING = 44;
    public static final int REGION = 1024;
    /** One site per region, far enough from its edges that its arrival island and route fit in. */
    static final ExitLocator SITES = new ExitLocator(REGION, 400, 490490);
    public static final int SITE_RADIUS = 22;
    /** Falling from within this far of a site's centre is the leap out. */
    public static final int LEAP_REACH = SITE_RADIUS + 12;
    private static final int ARRIVAL_RADIUS = 16;
    /** Most an island's rim pokes out past its radius (see {@link #rim}). */
    private static final double RIM_MAX = 1.23;
    private static final int[][] ARRIVAL_STEPS = arrivalSteps();
    private static final int SHELTER = 5;

    // ---- the colossal chains ----
    /** Outer length and width of a link, the bar's radius, and how far one link is from the next. */
    private static final double LINK_LENGTH = 34;
    private static final double LINK_WIDTH = 18;
    private static final double BAR = 3.0;
    private static final int LINK_PITCH = 22;
    /** The centre line of a link: half-circles of this radius, their centres this far apart (each side). */
    private static final double LINK_ARC = LINK_WIDTH / 2 - BAR;
    private static final double LINK_STRAIGHT = LINK_LENGTH / 2 - BAR - LINK_ARC;
    private static final double LINK_HALF = LINK_LENGTH / 2;
    /** Reach of a chain's blocks from its axis. */
    private static final int CHAIN_REACH = (int) Math.ceil(LINK_WIDTH / 2) + 1;
    /** The four chains around a site stand this far out from its centre (on the diagonals). */
    private static final int SITE_CHAIN_OFFSET = 34;

    public Level490Layout() {
        super(3, SITES);
    }

    @Override
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    @Override
    public int highestCeilingY() {
        return DECK_Y + 12;
    }

    @Override
    public int lowestFixtureY() {
        return DECK_Y - 12;
    }

    // ==========================================================================================
    // Regions, sites, islands
    // ==========================================================================================

    private record Site(int si, int sj, int ai, int aj) {}

    private static int[][] arrivalSteps() {
        java.util.List<int[]> steps = new java.util.ArrayList<>();
        for (int a = -8; a <= 8; a++) {
            for (int b = -8; b <= 8; b++) {
                double d = SPACING * Math.hypot(a, b);
                if (d >= 280 && d <= 320) {
                    steps.add(new int[] {a, b});
                }
            }
        }
        return steps.toArray(new int[0][]);
    }

    private static Site site(long seed, int regionX, int regionZ) {
        int si = Math.round(SITES.exitX(seed, regionX, regionZ) / (float) SPACING);
        int sj = Math.round(SITES.exitZ(seed, regionX, regionZ) / (float) SPACING);
        int[] step = ARRIVAL_STEPS[Hash.range(Hash.of(seed, regionX, regionZ, 4902), ARRIVAL_STEPS.length)];
        return new Site(si, sj, si + step[0], sj + step[1]);
    }

    private static Site siteOfNode(long seed, int i, int j) {
        return site(seed, Math.floorDiv(i * SPACING, REGION), Math.floorDiv(j * SPACING, REGION));
    }

    /** Centre {x, z} of the site of the region containing (x, z). */
    public static int[] siteCentre(long seed, int x, int z) {
        Site s = site(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
        return new int[] {s.si * SPACING, s.sj * SPACING};
    }

    private static boolean onRouteRow(Site s, int i, int j) {
        return j == s.aj && i >= Math.min(s.ai, s.si) && i <= Math.max(s.ai, s.si);
    }

    private static boolean onRouteColumn(Site s, int i, int j) {
        return i == s.si && j >= Math.min(s.aj, s.sj) && j <= Math.max(s.aj, s.sj);
    }

    private static final int PLAIN = 0;
    private static final int ARRIVAL = 1;
    private static final int SITE = 2;
    private static final int CHAINED = 3;

    /**
     * One island: its node, centre, ground height, radius, kind, the hash its details come from,
     * and the shape of its rim (three phases).
     */
    private record Island(int i, int j, int cx, int cz, int top, int r, int kind, long h, double p1, double p2, double p3) {
        boolean special() {
            return kind == ARRIVAL || kind == SITE;
        }
    }

    /** Islands are looked up over and over while a chunk is built: a small per-thread cache. */
    private static final class IslandCache {
        final Map<Long, Island> islands = new HashMap<>();
        long seed;
    }

    private static final ThreadLocal<IslandCache> CACHE = ThreadLocal.withInitial(IslandCache::new);
    private static final Island NO_ISLAND = new Island(0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0);

    private static boolean nodeExists(long seed, int i, int j) {
        Site s = siteOfNode(seed, i, j);
        if (onRouteRow(s, i, j) || onRouteColumn(s, i, j)) {
            return true;
        }
        // Clear air round the site (its diagonal neighbours), so its four chains hang free.
        if (Math.max(Math.abs(i - s.si), Math.abs(j - s.sj)) == 1 && Math.abs(i - s.si) + Math.abs(j - s.sj) == 2) {
            return false;
        }
        return Hash.unit(seed, i, j, 4901) < 0.72;
    }

    private static int kind(long seed, Site s, int i, int j) {
        if (i == s.si && j == s.sj) {
            return SITE;
        }
        if (i == s.ai && j == s.aj) {
            return ARRIVAL;
        }
        return Hash.unit(seed, i, j, 4920) < 0.025 ? CHAINED : PLAIN;
    }

    /** An island's own radius before it is fitted between its neighbours. */
    private static int baseRadius(long seed, int i, int j) {
        Site s = siteOfNode(seed, i, j);
        int k = kind(seed, s, i, j);
        if (k == SITE) {
            return SITE_RADIUS;
        }
        if (k == ARRIVAL) {
            return ARRIVAL_RADIUS;
        }
        double u = Hash.unit(seed, i, j, 4903);
        int r = u < 0.45 ? 7 + Hash.range(Hash.of(seed, i, j, 4904), 3)
                : u < 0.85 ? 10 + Hash.range(Hash.of(seed, i, j, 4904), 4) : 14 + Hash.range(Hash.of(seed, i, j, 4904), 3);
        return k == CHAINED ? Math.max(r, 12) : r;
    }

    /** Jitter of a node's centre (none next to a site or arrival island, so they always fit). */
    private static int jitter(long seed, int i, int j, int salt) {
        Site s = siteOfNode(seed, i, j);
        if (Math.max(Math.abs(i - s.si), Math.abs(j - s.sj)) <= 1 || Math.max(Math.abs(i - s.ai), Math.abs(j - s.aj)) <= 1) {
            return 0;
        }
        return Hash.range(Hash.of(seed, i, j, salt), 9) - 4;
    }

    private static Island island(long seed, int i, int j) {
        IslandCache holder = CACHE.get();
        Map<Long, Island> cache = holder.islands;
        if (holder.seed != seed || cache.size() > 8192) {
            cache.clear();
            holder.seed = seed;
        }
        long key = ((long) i << 32) ^ (j & 0xffffffffL);
        Island found = cache.get(key);
        if (found == null) {
            found = makeIsland(seed, i, j);
            cache.put(key, found);
        }
        return found == NO_ISLAND ? null : found;
    }

    private static Island makeIsland(long seed, int i, int j) {
        if (!nodeExists(seed, i, j)) {
            return NO_ISLAND;
        }
        Site s = siteOfNode(seed, i, j);
        int k = kind(seed, s, i, j);
        int cx = i * SPACING + jitter(seed, i, j, 4905);
        int cz = j * SPACING + jitter(seed, i, j, 4906);
        int r0 = baseRadius(seed, i, j);
        int r = r0;
        if (k != SITE && k != ARRIVAL) {
            // Share the gap with each neighbour by size, so no two islands ever touch.
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    if ((di != 0 || dj != 0) && nodeExists(seed, i + di, j + dj)) {
                        int n0 = baseRadius(seed, i + di, j + dj);
                        double gap = SPACING * Math.hypot(di, dj) - 8 - 6;
                        int fit = (int) Math.floor(gap / RIM_MAX * r0 / (r0 + n0));
                        if (kind(seed, s, i + di, j + dj) == SITE || kind(seed, s, i + di, j + dj) == ARRIVAL) {
                            fit = (int) Math.floor(gap / RIM_MAX - n0);
                        }
                        r = Math.min(r, fit);
                    }
                }
            }
            r = Math.max(r, 7); // big enough to always meet its bridges (centres are jittered up to 4)
        }
        int top = k == SITE || k == ARRIVAL ? DECK_Y : DECK_Y + (int) Math.round(6 * (smooth(seed, i, j) - 0.5) * 2);
        long h = Hash.of(seed, i, j, 4907);
        return new Island(i, j, cx, cz, top, r, k, h,
                Hash.unit(h) * Math.PI * 2, Hash.unit(Hash.mix(h)) * Math.PI * 2, Hash.unit(Hash.mix(h + 1)) * Math.PI * 2);
    }

    /** Smooth value noise over the grid (0..1): neighbouring islands get similar heights. */
    private static double smooth(long seed, int i, int j) {
        double fx = i / 3.0;
        double fz = j / 3.0;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = Hash.unit(seed, x0, z0, 4908);
        double b = Hash.unit(seed, x0 + 1, z0, 4908);
        double c = Hash.unit(seed, x0, z0 + 1, 4908);
        double d = Hash.unit(seed, x0 + 1, z0 + 1, 4908);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    /** How far the island's rim reaches in direction {@code angle}: a ragged circle. */
    private static double rim(Island is, double angle) {
        return is.r * (1.0 + 0.12 * Math.sin(3 * angle + is.p1) + 0.07 * Math.sin(5 * angle + is.p2) + 0.04 * Math.sin(9 * angle + is.p3));
    }

    // ==========================================================================================
    // Bridges
    // ==========================================================================================

    /**
     * The bridge from node (i, j) to the next node along x (alongX) or z: 0 = none, else its half
     * width (2 = main bridge, 5 wide; 1 = side bridge, 3 wide), negative if it is broken.
     */
    private static int bridge(long seed, int i, int j, boolean alongX) {
        int i2 = alongX ? i + 1 : i;
        int j2 = alongX ? j : j + 1;
        if (island(seed, i, j) == null || island(seed, i2, j2) == null) {
            return 0;
        }
        Site s = siteOfNode(seed, i, j);
        boolean route = alongX ? onRouteRow(s, i, j) && onRouteRow(s, i2, j2) : onRouteColumn(s, i, j) && onRouteColumn(s, i2, j2);
        if (route) {
            return 2;
        }
        int salt = alongX ? 4911 : 4912;
        if (Hash.unit(seed, i, j, salt) >= 0.62) {
            return 0;
        }
        if (Hash.unit(seed, i, j, salt + 10) < 0.22) {
            return -1;
        }
        boolean main = alongX ? Math.floorMod(j, 3) == 0 : Math.floorMod(i, 3) == 0;
        return main ? 2 : 1;
    }

    // ==========================================================================================
    // Building a column
    // ==========================================================================================

    private static BlockState b(net.neoforged.neoforge.registries.DeferredBlock<?> block) {
        return block.get().defaultBlockState();
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        int ni = Math.round(x / (float) SPACING);
        int nj = Math.round(z / (float) SPACING);
        Island on = null;
        double onD = 2.0;
        double onAngle = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Island is = island(seed, ni + di, nj + dj);
                if (is == null) {
                    continue;
                }
                double dx = x - is.cx;
                double dz = z - is.cz;
                double angle = Math.atan2(dz, dx);
                double d = Math.hypot(dx, dz) / rim(is, angle);
                if (d < onD) {
                    on = is;
                    onD = d;
                    onAngle = angle;
                }
            }
        }
        if (on != null && onD <= 1.0) {
            islandColumn(seed, on, x, z, onD, onAngle, out);
        } else {
            bridgeColumn(seed, x, z, ni, nj, out);
        }
        chainColumn(seed, x, z, ni, nj, out);
    }

    // ---- islands ----

    private void islandColumn(long seed, Island is, int x, int z, double d, double angle, Column out) {
        long hc = Hash.of(seed, x, z, 4930);
        // The crumbling rim: the ground drops a block here and there, and the outermost ring is broken.
        int top = is.top;
        if (d > 0.9) {
            if (Hash.unit(hc) < 0.22) {
                return;
            }
            if (Hash.unit(Hash.mix(hc)) < 0.45) {
                top -= 1;
            }
        }
        // The rock underneath, tapering to a ragged point.
        double depthMax = is.r * 1.3 + 4 + (is.h & 7);
        double depth = depthMax * Math.pow(Math.max(0.0, 1.0 - d * d), 0.55) * (0.8 + 0.4 * Hash.unit(Hash.mix(hc + 2)));
        int bottom = top - 1 - (int) Math.round(depth);
        for (int y = Math.max(1, bottom); y < top; y++) {
            out.set(y, b(Level490Blocks.ROCK));
        }
        // The ground: flagstones in the middle (once a plaza), rubble and dead grass toward the rim.
        double patch = Hash.unit(seed, Math.floorDiv(x, 3), Math.floorDiv(z, 3), 4931);
        BlockState ground = d < 0.62 && patch < 0.7 ? b(Level490Blocks.PAVING)
                : patch < 0.45 ? b(Level490Blocks.RUBBLE) : b(Level490Blocks.DEAD_GRASS);
        if (d < 0.62 && Hash.unit(hc) < 0.06) {
            ground = b(Level490Blocks.RUBBLE); // a broken flagstone
        }
        out.set(top, ground);
        int dx = x - is.cx;
        int dz = z - is.cz;
        // A shelter, else the ruins standing on the island.
        if (shelter(seed, is, dx, dz, top, out)) {
            return;
        }
        if (ruins(seed, is, dx, dz, d, top, x, z, out)) {
            return;
        }
        if (tree(seed, is, dx, dz, top, out)) {
            return;
        }
        double u = Hash.unit(Hash.mix(hc + 3));
        if (u < 0.035) {
            out.set(top + 1, Hash.unit(Hash.mix(hc + 4)) < 0.5 ? b(Level490Blocks.RUBBLE) : b(Level490Blocks.STONE));
            if (u < 0.008) {
                out.set(top + 2, b(Level490Blocks.RUBBLE));
            }
        } else if (u < 0.05 && ground.is(Level490Blocks.PAVING.get())) {
            out.set(top + 1, Level490Blocks.SHACKLE.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.from2DDataValue(Hash.range(hc, 4))));
        } else if (u < 0.06) {
            out.set(top + 1, Level490Blocks.PAVING_SLAB.get().defaultBlockState()); // a toppled flagstone
        }
    }

    /**
     * The ruins on an island, chosen from its hash: a ring of pillars, an arcade wall, a tower
     * stub, or (on chained islands) a broken collar of masonry around the chain. Returns true if
     * column (dx, dz) was part of one.
     */
    private boolean ruins(long seed, Island is, int dx, int dz, double d, int top, int x, int z, Column out) {
        long h = is.h;
        boolean big = is.r >= 10;
        if (is.kind == CHAINED) {
            // The collar: a thick broken ring of stone round the chain, as if built to hold it.
            double rr = Math.hypot(dx, dz);
            if (rr >= CHAIN_REACH && rr <= CHAIN_REACH + 2.5) {
                double a = Math.atan2(dz, dx);
                int height = 3 + (int) Math.round(5 * (0.5 + 0.5 * Math.sin(3 * a + is.p1)) * Hash.unit(seed, x, z, 4940));
                for (int y = top + 1; y <= top + height; y++) {
                    out.set(y, b(Level490Blocks.STONE));
                }
                return true;
            }
            return rr < CHAIN_REACH; // under the chain: nothing else
        }
        int pick = (int) (h >>> 40) & 7;
        boolean colonnade = is.kind == SITE || is.kind == ARRIVAL || (big ? pick < 5 : pick < 3);
        boolean wall = big && !colonnade || is.kind == ARRIVAL || big && pick >= 3;
        boolean tower = big && pick % 3 == 1 && is.kind == PLAIN;
        if (colonnade && pillar(seed, is, dx, dz, top, x, z, out)) {
            return true;
        }
        if (wall && arcade(seed, is, dx, dz, top, out)) {
            return true;
        }
        return tower && tower(seed, is, dx, dz, top, x, z, out);
    }

    /** A ring of 2 x 2 pillars, a third of them broken short, the rest topped by a 4 x 4 capital. */
    private boolean pillar(long seed, Island is, int dx, int dz, int top, int x, int z, Column out) {
        double ring = is.kind == SITE ? 15 : is.r * 0.62;
        int n = is.kind == SITE ? 12 : Math.max(3, (int) Math.round(ring * 0.75));
        int baseH = 9 + (int) (is.h & 7);
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n + is.p2;
            int px = (int) Math.round(ring * Math.cos(a));
            int pz = (int) Math.round(ring * Math.sin(a));
            int ox = dx - px;
            int oz = dz - pz;
            if (ox < -1 || ox > 2 || oz < -1 || oz > 2) {
                continue;
            }
            long hp = Hash.of(seed, is.i * 64 + k, is.j, 4941);
            boolean broken = Hash.unit(hp) < (is.kind == SITE ? 0.6 : 0.35);
            int height = broken ? 1 + Hash.range(Hash.mix(hp), Math.max(2, baseH - 3)) : baseH;
            boolean shaft = ox >= 0 && ox <= 1 && oz >= 0 && oz <= 1;
            if (shaft) {
                // A broken pillar's top is jagged: its four blocks stop at different heights.
                int jag = broken ? Hash.range(Hash.of(seed, x, z, 4942), 3) : 0;
                for (int y = top + 1; y <= top + height - jag; y++) {
                    out.set(y, b(Level490Blocks.STONE));
                }
                if (!broken) {
                    out.set(top + height + 1, b(Level490Blocks.STONE));
                }
                return true;
            }
            if (!broken) {
                // The capital overhangs the shaft by one block all round.
                out.set(top + height + 1, b(Level490Blocks.STONE));
                return true;
            }
            // Rubble fallen from the broken top, round its foot.
            if (Hash.unit(seed, x, z, 4943) < 0.5) {
                out.set(top + 1, b(Level490Blocks.RUBBLE));
                return true;
            }
        }
        return false;
    }

    /** A wall 2 thick across the island, arches every 7 blocks, its top broken off unevenly. */
    private boolean arcade(long seed, Island is, int dx, int dz, int top, Column out) {
        double phi = is.p3;
        double off = (Hash.unit(Hash.mix(is.h + 5)) - 0.5) * is.r * 0.6;
        // Along the wall (t) and across it (w).
        double t = dx * Math.cos(phi) + dz * Math.sin(phi);
        double w = -dx * Math.sin(phi) + dz * Math.cos(phi) - off;
        double half = Math.sqrt(Math.max(0, (is.r * 0.85) * (is.r * 0.85) - off * off));
        if (Math.abs(w) > 1.0 || Math.abs(t) > half) {
            return false;
        }
        int ti = (int) Math.floor(t);
        int baseH = 11 + (int) ((is.h >>> 8) & 3);
        // Broken top: lower toward the ends, ragged along the way.
        double ends = Math.min(1.0, (half - Math.abs(t)) / 5.0);
        int height = (int) Math.round(baseH * (0.45 + 0.55 * ends) - 3 * Hash.unit(seed, is.i * 97 + ti, is.j, 4944));
        int m = Math.floorMod(ti, 7);
        for (int y = top + 1; y <= top + height; y++) {
            int ly = y - top;
            // The arch: 3 wide, 6 high at the sides, 7 in the middle (a rounded top).
            boolean opening = m >= 2 && m <= 4 && (ly <= 6 || ly == 7 && m == 3);
            if (!opening) {
                out.set(y, b(Level490Blocks.STONE));
            }
        }
        return true;
    }

    /** A round tower stub, its wall broken off at a slant, a doorway toward the island's middle. */
    private boolean tower(long seed, Island is, int dx, int dz, int top, int x, int z, Column out) {
        double ta = is.p1 + 1.3;
        double tx = Math.cos(ta) * is.r * 0.4;
        double tz = Math.sin(ta) * is.r * 0.4;
        double rt = 3.5 + ((is.h >>> 16) & 1);
        double rr = Math.hypot(dx - tx, dz - tz);
        if (rr > rt + 0.5) {
            return false;
        }
        if (rr < rt - 0.5) {
            out.set(top, b(Level490Blocks.PAVING));
            return true;
        }
        double a = Math.atan2(dz - tz, dx - tx);
        double toMiddle = Math.atan2(-tz, -tx);
        boolean door = Math.abs(Math.IEEEremainder(a - toMiddle, Math.PI * 2)) < 0.45;
        int height = 12 + (int) ((is.h >>> 20) & 15) + (int) Math.round(6 * Math.sin(a + is.p2)) - Hash.range(Hash.of(seed, x, z, 4945), 3);
        for (int y = top + 1; y <= top + Math.max(2, height); y++) {
            if (!(door && y <= top + 3)) {
                out.set(y, b(Level490Blocks.STONE));
            }
        }
        return true;
    }

    /** A dead tree on some bigger islands: a trunk and a few bare branches, some with red leaves. */
    private boolean tree(long seed, Island is, int dx, int dz, int top, Column out) {
        if (is.r < 9 || is.special() || is.kind == CHAINED || Hash.unit(Hash.mix(is.h + 9)) >= 0.45) {
            return false;
        }
        double ta = is.p2 + 2.6;
        int tx = (int) Math.round(Math.cos(ta) * is.r * 0.55);
        int tz = (int) Math.round(Math.sin(ta) * is.r * 0.55);
        int trunk = 6 + (int) ((is.h >>> 24) & 3);
        boolean leaves = Hash.unit(Hash.mix(is.h + 10)) < 0.4;
        if (dx == tx && dz == tz) {
            for (int y = top + 1; y <= top + trunk; y++) {
                out.set(y, b(Level490Blocks.DEAD_WOOD));
            }
            return true;
        }
        // Branches: four directions from the upper trunk, each rising one block per step.
        boolean any = false;
        for (int k = 0; k < 4; k++) {
            double ba = is.p3 + k * Math.PI / 2 + 0.4 * Math.sin(k + is.p1);
            int len = 2 + (int) ((is.h >>> (28 + k)) & 1) + (k == 1 ? 1 : 0);
            int start = top + trunk - 2 + (k & 1);
            for (int step = 1; step <= len; step++) {
                int bx = tx + (int) Math.round(Math.cos(ba) * step);
                int bz = tz + (int) Math.round(Math.sin(ba) * step);
                if (bx == dx && bz == dz) {
                    out.set(start + step, b(Level490Blocks.DEAD_WOOD));
                    if (leaves && step == len) {
                        out.set(start + step + 1, b(Level490Blocks.RED_LEAVES));
                    }
                    any = true;
                } else if (leaves && step == len && Math.abs(bx - dx) <= 1 && Math.abs(bz - dz) <= 1
                        && Hash.unit(Hash.mix(is.h + dx * 31L + dz)) < 0.5) {
                    out.set(start + step, b(Level490Blocks.RED_LEAVES));
                    any = true;
                }
            }
        }
        return any;
    }

    /**
     * A shelter on some islands (always on the arrival island): 5 x 5 stone walls, a doorway facing
     * the island's middle, a checkpoint radio inside. A safe room.
     */
    private boolean shelter(long seed, Island is, int dx, int dz, int top, Column out) {
        int[] box = shelterBox(is);
        if (box == null) {
            return false;
        }
        int sx = dx - box[0];
        int sz = dz - box[1];
        if (sx < -1 || sx > SHELTER || sz < -1 || sz > SHELTER) {
            return false;
        }
        if (sx < 0 || sx >= SHELTER || sz < 0 || sz >= SHELTER) {
            return true; // a clear strip round it (no ruins leaning on it)
        }
        int doorX = box[2] > 0 ? 0 : SHELTER - 1;
        int radioX = box[2] > 0 ? SHELTER - 2 : 1;
        out.set(top, b(Level490Blocks.PAVING));
        out.set(top + SHELTER - 1, b(Level490Blocks.SLATE));
        if (sx == doorX && sz == 2) {
            out.set(top + 3, b(Level490Blocks.STONE));
        } else if (sx == 0 || sx == SHELTER - 1 || sz == 0 || sz == SHELTER - 1) {
            for (int y = top + 1; y <= top + 3; y++) {
                out.set(y, b(Level490Blocks.STONE));
            }
        } else if (sx == radioX && sz == 2) {
            out.set(top + 1, ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, box[2] > 0 ? Direction.WEST : Direction.EAST));
        }
        return true;
    }

    /** {min dx, min dz, x sign} of the island's shelter (cells relative to its centre), or null. */
    private static int[] shelterBox(Island is) {
        if (is.kind != ARRIVAL && (is.r < 11 || is.kind != PLAIN || Hash.unit(Hash.mix(is.h + 11)) >= 0.4)) {
            return null;
        }
        int qx = (is.h & 1) == 0 ? 1 : -1;
        int qz = (is.h & 2) == 0 ? 1 : -1;
        int far = (int) Math.floor(is.r * 0.62);
        int minX = qx > 0 ? far - SHELTER : -far;
        int minZ = qz > 0 ? far - SHELTER : -far;
        return new int[] {minX, minZ, qx};
    }

    // ---- bridges ----

    /** Height of the deck (in blocks, fractional) at {@code along} on the bridge from a to b. */
    private static double deckHeight(Island a, Island b2, int along, boolean alongX) {
        int start = alongX ? a.cx : a.cz;
        int end = alongX ? b2.cx : b2.cz;
        double f = Math.max(0, Math.min(1, (along - start) / (double) (end - start)));
        return a.top + (b2.top - a.top) * f;
    }

    private void bridgeColumn(long seed, int x, int z, int ni, int nj, Column out) {
        // A bridge along x lies on the line halfway between its two islands' centres (z), and
        // likewise along z. Look at the bridges of the nearby nodes.
        for (int di = -1; di <= 0; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (tryBridge(seed, x, z, ni + di, nj + dj, true, out)) {
                    return;
                }
            }
        }
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 0; dj++) {
                if (tryBridge(seed, x, z, ni + di, nj + dj, false, out)) {
                    return;
                }
            }
        }
    }

    private boolean tryBridge(long seed, int x, int z, int i, int j, boolean alongX, Column out) {
        int bw = bridge(seed, i, j, alongX);
        if (bw == 0) {
            return false;
        }
        Island a = island(seed, i, j);
        Island c = island(seed, alongX ? i + 1 : i, alongX ? j : j + 1);
        int line = alongX ? Math.round((a.cz + c.cz) / 2.0f) : Math.round((a.cx + c.cx) / 2.0f);
        int off = alongX ? z - line : x - line;
        int along = alongX ? x : z;
        int from = alongX ? a.cx : a.cz;
        int to = alongX ? c.cx : c.cz;
        int w = Math.abs(bw) + 1;
        if (Math.abs(off) > w || along <= from || along >= to) {
            return false;
        }
        long hb = Hash.of(seed, i, j, alongX ? 4950 : 4951);
        int span = to - from;
        int t = along - from;
        if (bw < 0) {
            // Broken off in the middle: mostly a long fall, now and then a 2-block gap you might jump.
            int len = Hash.unit(hb) < 0.35 ? 2 : 7 + Hash.range(Hash.mix(hb), 6);
            int g0 = span / 2 - len / 2 + Hash.range(Hash.mix(hb + 1), 5) - 2;
            if (t >= g0 && t < g0 + len) {
                return true;
            }
        }
        double hgt = deckHeight(a, c, along, alongX);
        int base = (int) Math.floor(hgt);
        boolean half = hgt - base >= 0.5;
        long hc = Hash.of(seed, x, z, 4952);
        // Crumbled deck edges (more on side bridges).
        if (Math.abs(off) == w && Hash.unit(hc) < (Math.abs(bw) == 2 ? 0.04 : 0.1)) {
            return true;
        }
        out.set(base - 1, b(Level490Blocks.STONE));
        if (Math.abs(off) < w) {
            out.set(base, b(Level490Blocks.PAVING));
            if (half) {
                out.set(base + 1, Level490Blocks.PAVING_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
            // Small chains dangle from the underside at the middle.
            if (off == 0 && Math.abs(t - span / 2) == 3 && (hb & 4) != 0) {
                int len = 6 + Hash.range(Hash.mix(hb + 3), 10);
                for (int y = base - 2; y >= base - 1 - len; y--) {
                    out.set(y, Level490Blocks.CHAIN.get().defaultBlockState().setValue(GiantChainBlock.TWIST, (y & 1) == 1));
                }
            }
            return true;
        }
        // The edge: a full block a little higher, carrying the railing (or a lantern post).
        int edge = (int) Math.round(hgt);
        out.set(edge, b(Level490Blocks.PAVING));
        if (edge > base) {
            out.set(base, b(Level490Blocks.STONE));
        }
        // A lantern post at each end, just past the island's rim, on one side.
        boolean lampSide = (off > 0) == ((hb & 8) != 0);
        int lampA = (int) Math.ceil(a.r * RIM_MAX) + 1;
        int lampB = span - (int) Math.ceil(c.r * RIM_MAX) - 1;
        int railY = edge + 1;
        // Stretches of railing gone: the unsafe parts.
        int seg = Math.floorDiv(t, 6);
        long hs = Hash.of(seed, i * 16 + seg, j * 2 + (off > 0 ? 1 : 0), alongX ? 4953 : 4954);
        boolean gone = Hash.unit(hs) < (Math.abs(bw) == 2 ? 0.08 : 0.2) && Math.floorMod(t, 6) <= 2 + Hash.range(Hash.mix(hs), 3);
        if (lampSide && (t == lampA || t == lampB) && lampA < lampB) {
            out.set(railY, connect(seed, x, z, railY, Level490Blocks.POST.get().defaultBlockState()));
            out.set(railY + 1, Level490Blocks.LANTERN.get().defaultBlockState()
                    .setValue(IronLanternBlock.LIT, Hash.unit(Hash.mix(hb + 7 + t)) < 0.55));
        } else if (!gone) {
            out.set(railY, connect(seed, x, z, railY, Level490Blocks.RAILING.get().defaultBlockState()));
        }
        return true;
    }

    /** The height of a railing or lantern post in column (x, z), or -1: for joining railings up. */
    private int railingY(long seed, int x, int z) {
        int[] found = {-1};
        fillColumn(seed, x, z, (y, state) -> {
            if (state.is(Level490Blocks.RAILING.get()) || state.is(Level490Blocks.POST.get())) {
                found[0] = y;
            }
        });
        return found[0];
    }

    /** Guards against the railing lookups recursing into their neighbours' neighbours. */
    private static final ThreadLocal<Boolean> CONNECTING = ThreadLocal.withInitial(() -> false);

    private BlockState connect(long seed, int x, int z, int y, BlockState state) {
        if (CONNECTING.get()) {
            return state; // only asking where the railing is
        }
        CONNECTING.set(true);
        try {
            return state.setValue(CrossCollisionBlock.NORTH, railingY(seed, x, z - 1) == y)
                    .setValue(CrossCollisionBlock.SOUTH, railingY(seed, x, z + 1) == y)
                    .setValue(CrossCollisionBlock.EAST, railingY(seed, x + 1, z) == y)
                    .setValue(CrossCollisionBlock.WEST, railingY(seed, x - 1, z) == y);
        } finally {
            CONNECTING.set(false);
        }
    }

    // ---- the colossal chains ----

    /** Axis {x, z} and link phase of each colossal chain that can reach column (x, z). */
    private void chainColumn(long seed, int x, int z, int ni, int nj, Column out) {
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Island is = island(seed, ni + di, nj + dj);
                if (is == null) {
                    continue;
                }
                if (is.kind == CHAINED) {
                    chain(x - is.cx, z - is.cz, (int) (is.h & 31), out);
                } else if (is.kind == SITE) {
                    for (int k = 0; k < 4; k++) {
                        int sx = (k & 1) == 0 ? SITE_CHAIN_OFFSET : -SITE_CHAIN_OFFSET;
                        int sz = (k & 2) == 0 ? SITE_CHAIN_OFFSET : -SITE_CHAIN_OFFSET;
                        chain(x - is.cx - sx, z - is.cz - sz, 7 * k + (int) (is.h & 15), out);
                    }
                }
            }
        }
    }

    /**
     * The blocks of one colossal chain in the column (dx, dz) from its axis: link k is centred at
     * y = phase + k * pitch and stands in the x-y plane (even k) or the z-y plane (odd k), so each
     * link hooks through the next. A block belongs to a link when the middle of the block is within
     * the bar's radius of the link's centre line (two half circles joined by straight sides).
     */
    private static void chain(int dx, int dz, int phase, Column out) {
        if (Math.abs(dx) > CHAIN_REACH || Math.abs(dz) > CHAIN_REACH) {
            return;
        }
        double px = dx + 0.5;
        double pz = dz + 0.5;
        BlockState iron = b(Level490Blocks.CHAIN_IRON);
        for (int k = -2; k * LINK_PITCH + phase - LINK_HALF < WORLD_HEIGHT; k++) {
            int cy = phase + k * LINK_PITCH;
            boolean inX = (k & 1) == 0;
            double u = inX ? px : pz;   // across the link, in its plane
            double w = inX ? pz : px;   // out of its plane
            if (Math.abs(w) > BAR) {
                continue;
            }
            int y0 = Math.max(0, (int) Math.floor(cy - LINK_HALF));
            int y1 = Math.min(WORLD_HEIGHT - 1, (int) Math.ceil(cy + LINK_HALF));
            for (int y = y0; y <= y1; y++) {
                double v = y + 0.5 - cy;
                double inPlane;
                double av = Math.abs(v);
                if (av <= LINK_STRAIGHT) {
                    inPlane = Math.abs(Math.abs(u) - LINK_ARC);
                } else {
                    inPlane = Math.abs(Math.hypot(u, av - LINK_STRAIGHT) - LINK_ARC);
                }
                if (inPlane * inPlane + w * w <= BAR * BAR) {
                    out.set(y, iron);
                }
            }
        }
    }

    // ==========================================================================================
    // Standing, arriving, the way out
    // ==========================================================================================

    /**
     * The ground of column (x, z) near deck height (the highest flagstone, rubble, dead grass or
     * half step), or -1, and whether it is clear to stand on (nothing in the two blocks above).
     */
    private int[] groundInfo(long seed, int x, int z) {
        BlockState[] col = new BlockState[61];
        int lo = DECK_Y - 30;
        fillColumn(seed, x, z, (y, state) -> {
            if (y >= lo && y < lo + col.length) {
                col[y - lo] = state;
            }
        });
        for (int k = col.length - 1; k >= 0; k--) {
            BlockState st = col[k];
            if (st != null && (st.is(Level490Blocks.PAVING.get()) || st.is(Level490Blocks.RUBBLE.get())
                    || st.is(Level490Blocks.DEAD_GRASS.get()) || st.is(Level490Blocks.PAVING_SLAB.get()))) {
                boolean clear = (k + 1 >= col.length || col[k + 1] == null) && (k + 2 >= col.length || col[k + 2] == null);
                return new int[] {lo + k, clear ? 1 : 0};
            }
        }
        return new int[] {-1, 0};
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int[] g = groundInfo(seed, x, z);
        return g[0] >= 0 && g[1] == 1;
    }

    @Override
    public int standY(long seed, int x, int z) {
        int g = groundInfo(seed, x, z)[0];
        return g < 0 ? STAND_Y : g + 1;
    }

    /** You arrive on the open ground in the middle of the region's arrival island. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        Site s = site(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
        int ax = s.ai * SPACING;
        int az = s.aj * SPACING;
        if (Math.hypot(x - ax, z - az) > ARRIVAL_RADIUS * 0.5) {
            return false;
        }
        return isWalkable(seed, x, z);
    }

    @Override
    public int[] arrivalHint(long seed, int x, int z) {
        Site s = site(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
        return new int[] {s.ai * SPACING, s.aj * SPACING};
    }

    /** The arrival island's middle: where falling sends you if you never used a radio. */
    public int[] arrivalCentre(long seed, int x, int z) {
        return arrivalHint(seed, x, z);
    }

    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return siteCentre(seed, x, z);
    }

    @Override
    public int exitY(long seed, int x, int z) {
        return STAND_Y;
    }

    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        int ni = Math.round(x / (float) SPACING);
        int nj = Math.round(z / (float) SPACING);
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Island is = island(seed, ni + di, nj + dj);
                int[] box = is == null ? null : shelterBox(is);
                if (box != null) {
                    int sx = x - is.cx - box[0];
                    int sz = z - is.cz - box[1];
                    if (sx >= 0 && sx < SHELTER && sz >= 0 && sz < SHELTER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ==========================================================================================
    // For the client: open air for the floating debris, where the colossal chains hang
    // ==========================================================================================

    /**
     * True when a chunk of debris of radius {@code size} can float at (x, y, z) without touching
     * an island (with its rock below), a bridge or a colossal chain.
     */
    public static boolean openAir(long seed, double x, double y, double z, double size) {
        int ni = (int) Math.round(x / SPACING);
        int nj = (int) Math.round(z / SPACING);
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Island is = island(seed, ni + di, nj + dj);
                if (is == null) {
                    continue;
                }
                double d = Math.hypot(x - is.cx, z - is.cz);
                if (y > is.top - is.r * 1.6 - 12 && y < is.top + 22 && d < is.r * RIM_MAX + size + 4) {
                    return false;
                }
                if (is.kind == CHAINED && d < CHAIN_REACH + size + 6) {
                    return false;
                }
                if (is.kind == SITE && Math.abs(Math.abs(x - is.cx) - SITE_CHAIN_OFFSET) < CHAIN_REACH + size + 6
                        && Math.abs(Math.abs(z - is.cz) - SITE_CHAIN_OFFSET) < CHAIN_REACH + size + 6) {
                    return false;
                }
                // Its bridges: the lines halfway to the next islands.
                for (int k = 0; k < 2; k++) {
                    boolean alongX = k == 0;
                    if (bridge(seed, is.i, is.j, alongX) == 0) {
                        continue;
                    }
                    Island c = island(seed, alongX ? is.i + 1 : is.i, alongX ? is.j : is.j + 1);
                    double line = alongX ? (is.cz + c.cz) / 2.0 : (is.cx + c.cx) / 2.0;
                    double off = alongX ? z - line : x - line;
                    double along = alongX ? x : z;
                    double from = alongX ? is.cx : is.cz;
                    double to = alongX ? c.cx : c.cz;
                    double h = is.top + (c.top - is.top) * Math.max(0, Math.min(1, (along - from) / (to - from)));
                    if (along > from && along < to && Math.abs(off) < 4 + size && y > h - 20 && y < h + 6 + size) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ==========================================================================================
    // For the client's sky: where the colossal chains hang
    // ==========================================================================================

    /** Calls {@code out} with the axis {x, z} of every colossal chain within {@code radius} of (x, z). */
    public static void chainsNear(long seed, int x, int z, int radius, java.util.function.BiConsumer<Integer, Integer> out) {
        int i0 = Math.floorDiv(x - radius, SPACING) - 1;
        int i1 = Math.floorDiv(x + radius, SPACING) + 1;
        int j0 = Math.floorDiv(z - radius, SPACING) - 1;
        int j1 = Math.floorDiv(z + radius, SPACING) + 1;
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Island is = island(seed, i, j);
                if (is == null) {
                    continue;
                }
                if (is.kind == CHAINED) {
                    out.accept(is.cx, is.cz);
                } else if (is.kind == SITE) {
                    for (int k = 0; k < 4; k++) {
                        out.accept(is.cx + ((k & 1) == 0 ? SITE_CHAIN_OFFSET : -SITE_CHAIN_OFFSET),
                                is.cz + ((k & 2) == 0 ? SITE_CHAIN_OFFSET : -SITE_CHAIN_OFFSET));
                    }
                }
            }
        }
    }
}
