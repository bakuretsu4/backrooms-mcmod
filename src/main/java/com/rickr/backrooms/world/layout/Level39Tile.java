package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;

/**
 * One {@value #SIZE} x {@value #SIZE} square of a Level 39 town, worked out once from its cell's
 * plan ({@link Level39Cell}) and then read back for each of its columns by
 * {@link Level39Layout#fillColumn}: what the ground is (grass, dirt path, cart ruts, a puddle, a
 * hole), how high it rolls ({@link #half}), which small prop stands on it (a fence section, a
 * hedge, a pole, a bush, yard clutter) and which bigger thing covers it (a house, a tree, a tower,
 * the park, a camp). The tunnel runs, holes and shafts that come near the square are listed too,
 * so a column only checks a handful of them.
 *
 * <p>Tiles line up with cells (a cell is 8 x 8 tiles), so a tile always belongs to one town.
 */
final class Level39Tile {
    static final int SIZE = 128;
    private static final int SHIFT = 7;

    // Ground surfaces.
    static final byte GRASS = 0;
    static final byte YARD_DIRT = 1;
    static final byte PATH_DIRT = 2;
    static final byte PATH_GRAVEL = 3;
    static final byte PATH_MUD = 4;
    /** A puddle: a waterlogged mud half block, the water level with the ground around it. */
    static final byte PUDDLE = 5;
    static final byte HOLE = 6;
    static final byte HOLE_RIM = 7;
    static final byte HATCH = 8;
    static final byte APRON = 9;
    /** The grassy strip down the middle of a little-used path. */
    static final byte PATH_GRASS = 10;
    /** A cart rut: a mud half block (a half-block dip, never a trench). */
    static final byte RUT = 11;

    // Props (one per column). Fence sections are FENCE + axis * 4 + style (axis 0 = x, 1 = z).
    static final byte NO_PROP = 0;
    static final byte FENCE = 1;
    static final byte FENCE_POST = 9;
    static final byte POLE = 10;
    static final byte BUSH = 11;
    static final byte BUSH_TALL = 12;
    /** A transformer can hung beside a pole: TRANSFORMER + 2D direction it faces (away from the pole). */
    static final byte TRANSFORMER = 13;
    static final byte HEDGE = 17;
    static final byte HEDGE_TALL = 18;
    /** Yard clutter: YARD + kind (Level39Cell.YARD_*) * 4 + direction (2D data value) or axis. */
    static final byte YARD = 20;

    /** The yards roll by up to this many half blocks above the paths. */
    static final int MAX_HALF = 3;

    final long seed;
    final int tx;
    final int tz;
    final int x0;
    final int z0;
    final Level39Cell cell;
    final byte[] surf = new byte[SIZE * SIZE];
    final byte[] prop = new byte[SIZE * SIZE];
    /**
     * Ground height in half blocks above the flat town level (0..{@value #MAX_HALF}): the top
     * full block is GROUND_Y + half / 2, with a half block on top when half is odd. Paths, house
     * plots, trees and the other big things stand at 0.
     */
    final byte[] half = new byte[SIZE * SIZE];
    /** Index into {@link Level39Cell#things} of what covers the column, or -1. */
    final short[] thing = new short[SIZE * SIZE];
    /** Pole index (into {@link Level39Cell#poles}) of POLE columns. */
    final short[] poleOf = new short[SIZE * SIZE];
    /** Tunnel runs, nodes, holes and shafts near this square (indices into the cell's arrays). */
    final int[] segs;
    final int[] nodes;
    final int[] holes;
    final int[] shafts;

    private Level39Tile(long seed, int tx, int tz) {
        this.seed = seed;
        this.tx = tx;
        this.tz = tz;
        this.x0 = tx << SHIFT;
        this.z0 = tz << SHIFT;
        int x1 = x0 + SIZE - 1;
        int z1 = z0 + SIZE - 1;
        cell = Level39Cell.of(seed, x0, z0);

        // ---- Tunnel bits near the square ---------------------------------------------------------
        int reach = 16;
        int[] tmp = new int[cell.segs.length];
        int n = 0;
        for (int s = 0; s < cell.segs.length; s++) {
            Level39Cell.Seg seg = cell.segs[s];
            int sx0 = seg.alongX() ? seg.a() : seg.fixed();
            int sx1 = seg.alongX() ? seg.b() : seg.fixed();
            int sz0 = seg.alongX() ? seg.fixed() : seg.a();
            int sz1 = seg.alongX() ? seg.fixed() : seg.b();
            if (sx1 + reach >= x0 && sx0 - reach <= x1 && sz1 + reach >= z0 && sz0 - reach <= z1) {
                tmp[n++] = s;
            }
        }
        segs = Arrays.copyOf(tmp, n);
        tmp = new int[cell.nodes.length];
        n = 0;
        for (int k = 0; k < cell.nodes.length; k++) {
            Level39Cell.Node node = cell.nodes[k];
            if (node.x() + reach >= x0 && node.x() - reach <= x1 && node.z() + reach >= z0 && node.z() - reach <= z1) {
                tmp[n++] = k;
            }
        }
        nodes = Arrays.copyOf(tmp, n);
        tmp = new int[cell.holes.length];
        n = 0;
        for (int k = 0; k < cell.holes.length; k++) {
            Level39Cell.Hole hole = cell.holes[k];
            if (hole.x() + 9 >= x0 && hole.x() - 9 <= x1 && hole.z() + 9 >= z0 && hole.z() - 9 <= z1) {
                tmp[n++] = k;
            }
        }
        holes = Arrays.copyOf(tmp, n);
        tmp = new int[cell.shafts.length];
        n = 0;
        for (int k = 0; k < cell.shafts.length; k++) {
            Level39Cell.Shaft shaft = cell.shafts[k];
            if (shaft.x() >= x0 && shaft.x() <= x1 && shaft.z() >= z0 && shaft.z() <= z1) {
                tmp[n++] = k;
            }
        }
        shafts = Arrays.copyOf(tmp, n);

        // ---- Ground ------------------------------------------------------------------------------
        Arrays.fill(thing, (short) -1);
        Arrays.fill(poleOf, (short) -1);
        // How high each column may rise: paths, holes and the big things stay at 0 and the
        // yards ease up away from them, half a block per block at most.
        byte[] cap = new byte[SIZE * SIZE];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = index(x, z);
                float depth = cell.pathDepth(x - cell.ox, z - cell.oz);
                surf[i] = ground(x, z, depth);
                cap[i] = (byte) Math.max(0, Math.min(MAX_HALF, (int) Math.floor(-depth)));
            }
        }
        // Worn footpaths from each front door straight out to the gate.
        for (Level39HouseSpec h : cell.houses) {
            footpath(h);
        }
        // Holes (ragged, with a muddy rim) and shaft hatches in the paths.
        for (int k : holes) {
            Level39Cell.Hole hole = cell.holes[k];
            int r = (int) Math.ceil(hole.radius() * 1.4f + 3) + 2;
            for (int x = Math.max(x0, hole.x() - r); x <= Math.min(x1, hole.x() + r); x++) {
                for (int z = Math.max(z0, hole.z() - r); z <= Math.min(z1, hole.z() + r); z++) {
                    int i = index(x, z);
                    float d = (float) Math.sqrt((x - hole.x()) * (x - hole.x()) + (z - hole.z()) * (z - hole.z()));
                    float edge = Level39Cell.holeRadius(hole, x, z, Level39Layout.GROUND_Y);
                    if (d <= edge) {
                        surf[i] = HOLE;
                    } else if (d <= edge + 1.0f + (float) Hash.unit(seed, x, z, 3960) * 0.8f) {
                        surf[i] = HOLE_RIM;
                    }
                    lowerCap(cap, i, (int) Math.floor(d - edge - 1.5f));
                }
            }
        }
        for (int k : shafts) {
            Level39Cell.Shaft shaft = cell.shafts[k];
            surf[index(shaft.x(), shaft.z())] = HATCH;
        }

        // ---- Things ------------------------------------------------------------------------------
        Level39Cell.Thing[] things = cell.things;
        for (int k = 0; k < things.length; k++) {
            Level39Cell.Thing t = things[k];
            boolean tree = t.kind() == Level39Cell.TREE_BARE || t.kind() == Level39Cell.TREE_LEAFY;
            // Houses, towers, camps and parks stand on flat ground; trees on a flat spot round the trunk.
            int fx0 = tree ? t.cx() : t.x0();
            int fz0 = tree ? t.cz() : t.z0();
            int fx1 = tree ? t.cx() : t.x1();
            int fz1 = tree ? t.cz() : t.z1();
            flatten(cap, fx0, fz0, fx1, fz1, x1, z1);
            if (t.x1() < x0 || t.x0() > x1 || t.z1() < z0 || t.z0() > z1) {
                continue;
            }
            for (int x = Math.max(x0, t.x0()); x <= Math.min(x1, t.x1()); x++) {
                for (int z = Math.max(z0, t.z0()); z <= Math.min(z1, t.z1()); z++) {
                    int i = index(x, z);
                    if (thing[i] < 0 && surf[i] != HOLE && surf[i] != HATCH) {
                        thing[i] = (short) k;
                    }
                }
            }
        }

        // ---- Props -------------------------------------------------------------------------------
        for (Level39Cell.Fence f : cell.fences) {
            stampFence(f, x1, z1);
        }
        Level39Cell.Pole[] poles = cell.poles;
        for (int k = 0; k < poles.length; k++) {
            Level39Cell.Pole p = poles[k];
            flatten(cap, p.x(), p.z(), p.x(), p.z(), x1, z1);
            if (inside(p.x(), p.z())) {
                int i = index(p.x(), p.z());
                if (surf[i] != HOLE && surf[i] != HATCH && !isHouse(i)) {
                    prop[i] = POLE;
                    poleOf[i] = (short) k;
                }
            }
            // About one pole in five carries a transformer can, on the side away from the wires.
            long th = Hash.of(seed, p.x(), p.z(), 3972);
            if (Hash.unit(th) < 0.2) {
                Direction side = p.wiresAlongX()
                        ? ((th & 64) == 0 ? Direction.NORTH : Direction.SOUTH)
                        : ((th & 64) == 0 ? Direction.WEST : Direction.EAST);
                int cx = p.x() + side.getStepX();
                int cz = p.z() + side.getStepZ();
                if (inside(cx, cz)) {
                    int i = index(cx, cz);
                    if (prop[i] == NO_PROP && thing[i] < 0 && surf[i] != HOLE && surf[i] != HATCH) {
                        prop[i] = (byte) (TRANSFORMER + side.get2DDataValue());
                    }
                }
            }
        }
        int[] bushes = cell.bushes;
        for (int k = 0; k < bushes.length; k += 3) {
            int bx = bushes[k];
            int bz = bushes[k + 1];
            if (inside(bx, bz)) {
                int i = index(bx, bz);
                if (prop[i] == NO_PROP && !isHouse(i) && (surf[i] == GRASS || surf[i] == YARD_DIRT)) {
                    prop[i] = bushes[k + 2] == 2 ? BUSH_TALL : BUSH;
                }
            }
        }
        int[] yard = cell.yardProps;
        for (int k = 0; k < yard.length; k += 4) {
            int yx = yard[k];
            int yz = yard[k + 1];
            int kind = yard[k + 2];
            // Yard clutter stands on level ground (the clothesline's posts and the line between them).
            flatten(cap, yx, yz, yx, yz, x1, z1);
            if (!inside(yx, yz)) {
                continue;
            }
            int i = index(yx, yz);
            byte s = surf[i];
            if (isHouse(i) || s == HOLE || s == HOLE_RIM || s == HATCH || s == PUDDLE) {
                continue;
            }
            // The mailbox replaces the fence section it stands in; everything else needs an empty spot.
            if (prop[i] != NO_PROP && !(kind == Level39Cell.YARD_MAILBOX && prop[i] < FENCE + 9)) {
                continue;
            }
            prop[i] = (byte) (YARD + kind * 4 + yard[k + 3]);
        }

        // ---- Heights -----------------------------------------------------------------------------
        // Never more than half a block between neighbours: let every limit spread outward one
        // step per block (a forward and a backward sweep over the square).
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = index(x, z);
                if (x > x0) lowerCap(cap, i, cap[i - SIZE] + 1);
                if (z > z0) lowerCap(cap, i, cap[i - 1] + 1);
            }
        }
        for (int x = x1; x >= x0; x--) {
            for (int z = z1; z >= z0; z--) {
                int i = index(x, z);
                if (x < x1) lowerCap(cap, i, cap[i + SIZE] + 1);
                if (z < z1) lowerCap(cap, i, cap[i + 1] + 1);
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = index(x, z);
                // Gentle swells: two sizes of smooth noise, flat-topped near the paths.
                double v = 0.6 * smooth(x, z, 15, 3981) + 0.4 * smooth(x, z, 31, 3982);
                int h = (int) Math.round(Math.max(0, Math.min(MAX_HALF, (v - 0.32) * 6.5)));
                h = Math.min(h, cap[i]);
                byte s = surf[i];
                // Props, puddles and the rest stand on whole blocks (no half-block gaps under them).
                if (prop[i] != NO_PROP || s == PUDDLE || s == HOLE || s == HOLE_RIM || s == HATCH) {
                    h &= ~1;
                }
                half[i] = (byte) h;
            }
        }
    }

    int index(int x, int z) {
        return (x - x0) * SIZE + (z - z0);
    }

    boolean inside(int x, int z) {
        return x >= x0 && x < x0 + SIZE && z >= z0 && z < z0 + SIZE;
    }

    private boolean isHouse(int i) {
        return thing[i] >= 0 && cell.things[thing[i]].kind() == Level39Cell.HOUSE;
    }

    private void lowerCap(byte[] cap, int i, int value) {
        int v = Math.max(0, value);
        if (v < cap[i]) {
            cap[i] = (byte) v;
        }
    }

    /** Keeps the rectangle (and a ramp around it) at the flat town level. */
    private void flatten(byte[] cap, int fx0, int fz0, int fx1, int fz1, int x1, int z1) {
        int m = MAX_HALF;
        if (fx1 + m < x0 || fx0 - m > x1 || fz1 + m < z0 || fz0 - m > z1) {
            return;
        }
        for (int x = Math.max(x0, fx0 - m); x <= Math.min(x1, fx1 + m); x++) {
            for (int z = Math.max(z0, fz0 - m); z <= Math.min(z1, fz1 + m); z++) {
                int d = Math.max(Math.max(fx0 - x, x - fx1), Math.max(fz0 - z, z - fz1));
                lowerCap(cap, index(x, z), Math.max(0, d) - 1);
            }
        }
    }

    /**
     * The ground of one column: path (brown packed earth, stony stretches, cart ruts, puddles, a
     * grassy middle on quiet paths) or yard (patchy grass and trodden dirt, puddles in hollows).
     */
    private byte ground(int x, int z, float depth) {
        int lx = x - cell.ox;
        int lz = z - cell.oz;
        if (Math.max(Math.abs(lx), Math.abs(lz)) >= Level39Cell.APRON) {
            // The wide apron in front of the mill: stony earth, dirt patches, rain puddles where
            // the water runs off the wall.
            double v = smooth(x, z, 7, 3961);
            if (v > 0.8) return PUDDLE;
            return v < 0.45 ? PATH_DIRT : PATH_GRAVEL;
        }
        if (depth > 0) {
            boolean stony = Hash.unit(seed, Math.floorDiv(x, 24), Math.floorDiv(z, 24), 3962) < 0.2;
            boolean rutted = Hash.unit(seed, Math.floorDiv(x, 32), Math.floorDiv(z, 32), 3963) < 0.55;
            boolean grassy = !rutted && Hash.unit(seed, Math.floorDiv(x, 40), Math.floorDiv(z, 40), 3964) < 0.45;
            double wet = smooth(x, z, 9, 3964);
            float off = cell.pathOffset(lx, lz);
            if (grassy && off < 0.75f) {
                return PATH_GRASS;
            }
            if (rutted && off >= 0.7f && off <= 1.6f) {
                // Mostly churned mud level with the path; short stretches hold rainwater.
                return wet > 0.7 ? PUDDLE : RUT;
            }
            if (depth > 1.0f && wet > 0.78) {
                return PUDDLE;
            }
            if (depth < 0.8f && Hash.unit(seed, x, z, 3965) < 0.3) {
                return YARD_DIRT; // the trodden edge
            }
            return stony ? PATH_GRAVEL : PATH_DIRT;
        }
        // Yards: uneven grass with bare, trampled dirt; puddles in the hollows.
        double bare = smooth(x, z, 6, 3966);
        if (depth > -1.6f && Hash.unit(seed, x, z, 3967) < 0.45) {
            return YARD_DIRT;
        }
        if (smooth(x, z, 7, 3968) > 0.88) {
            return PUDDLE;
        }
        if (bare > 0.62) {
            return Hash.unit(seed, x, z, 3969) < 0.12 ? PATH_MUD : YARD_DIRT;
        }
        return GRASS;
    }

    /** Value noise in [0, 1) with features about {@code size} blocks across. */
    private double smooth(int x, int z, int size, int salt) {
        int gx = Math.floorDiv(x, size);
        int gz = Math.floorDiv(z, size);
        double fx = (x - gx * size + 0.5) / size;
        double fz = (z - gz * size + 0.5) / size;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = Hash.unit(seed, gx, gz, salt);
        double b = Hash.unit(seed, gx + 1, gz, salt);
        double c = Hash.unit(seed, gx, gz + 1, salt);
        double d = Hash.unit(seed, gx + 1, gz + 1, salt);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    /** Trodden dirt from the front door out through the gate. */
    private void footpath(Level39HouseSpec h) {
        int dx = h.facing().getStepX();
        int dz = h.facing().getStepZ();
        int x = h.doorX();
        int z = h.doorZ();
        int sway = (int) (h.hash() & 1);
        for (int step = 0; step < 14; step++) {
            for (int w = 0; w <= 1; w++) {
                int px = x + (dx == 0 ? w - sway : 0);
                int pz = z + (dz == 0 ? w - sway : 0);
                if (inside(px, pz)) {
                    int i = index(px, pz);
                    byte s = surf[i];
                    if (s == GRASS || s == YARD_DIRT || s == PUDDLE) {
                        surf[i] = PATH_DIRT;
                    }
                }
            }
            if (!(x >= h.lotX0() && x <= h.lotX1() && z >= h.lotZ0() && z <= h.lotZ1())) {
                break; // out of the gate
            }
            x += dx;
            z += dz;
        }
    }

    private void stampFence(Level39Cell.Fence f, int x1, int z1) {
        boolean alongX = f.alongX();
        int fixed = f.fixed();
        if (alongX ? fixed < z0 || fixed > z1 : fixed < x0 || fixed > x1) {
            return;
        }
        int lo = Math.max(f.from(), alongX ? x0 : z0);
        int hi = Math.min(f.to(), alongX ? x1 : z1);
        for (int t = lo; t <= hi; t++) {
            if (t >= f.gapFrom() && t <= f.gapTo()) {
                continue;
            }
            int x = alongX ? t : fixed;
            int z = alongX ? fixed : t;
            int i = index(x, z);
            byte s = surf[i];
            if (s == HOLE || s == HOLE_RIM || s == HATCH || s == PUDDLE || isHouse(i)) {
                continue;
            }
            long h = Hash.of(seed, x, z, 3970);
            if (f.hedge()) {
                // An overgrown hedge, waist to head high, ragged along its length.
                if (Hash.unit(h) < 0.05) {
                    continue;
                }
                prop[i] = Hash.unit(Hash.of(seed, (t - f.from()) / 3, (int) f.salt(), 3973)) < 0.45 ? HEDGE_TALL : HEDGE;
                continue;
            }
            boolean post = t == f.from() || t == f.to() || t == f.gapFrom() - 1 || t == f.gapTo() + 1
                    || Math.floorMod(t - f.from(), 8) == 0;
            if (post) {
                prop[i] = FENCE_POST;
                continue;
            }
            if (Hash.unit(h) < 0.04) {
                continue; // a section that fell and was never put back
            }
            // Each 8-block section has its own state, with the odd different section inside it.
            long sh = Hash.of(seed, (t - f.from()) / 8, (int) f.salt(), 3971);
            int pick = Hash.range(Hash.unit(Hash.mix(h)) < 0.15 ? Hash.mix(h + 1) : sh, 100);
            int style = pick < 60 ? 0 : pick < 80 ? 1 : pick < 92 ? 2 : 3;
            prop[i] = (byte) (FENCE + (alongX ? 0 : 4) + style);
        }
    }

    // ==========================================================================================
    // Cache
    // ==========================================================================================

    private static final ConcurrentHashMap<Long, Level39Tile> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Level39Tile> LAST = new ThreadLocal<>();

    /** The tile containing (x, z). */
    static Level39Tile of(long seed, int x, int z) {
        int tx = x >> SHIFT;
        int tz = z >> SHIFT;
        Level39Tile t = LAST.get();
        if (t != null && t.tx == tx && t.tz == tz && t.seed == seed) {
            return t;
        }
        long key = Hash.of(seed, tx, tz, 3990);
        t = CACHE.get(key);
        if (t == null || t.seed != seed || t.tx != tx || t.tz != tz) {
            if (CACHE.size() > 160) {
                CACHE.clear();
            }
            t = CACHE.computeIfAbsent(key, k -> new Level39Tile(seed, tx, tz));
        }
        LAST.set(t);
        return t;
    }
}
