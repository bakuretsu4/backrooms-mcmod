package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The roads of Level 94 and the places they join: towns and the castles' ramp feet.
 *
 * <p>Everything here is a pure function of the seed, so any chunk can ask about the roads near it
 * without knowing what was generated before:
 * <ul>
 *   <li><b>Country roads</b> run between "nodes" on a jittered grid ({@link #NODE} apart). Each
 *       node may link to its east and south neighbour; the link is a gently winding curve.
 *       Nodes too close to a castle or a town are left out, so roads go around them.</li>
 *   <li><b>One town per castle region.</b> It sits just beyond the castle's clear zone, on the
 *       side the castle's ramp faces, so the castle road is a short drive from the plaza. Its
 *       street ends join the nearest country-road nodes.</li>
 * </ul>
 * Roads are polylines of floats. A column is road when its centre is within {@link #HALF} of one.
 */
final class Level94Roads {
    /** Spacing of the road-node grid. */
    static final int NODE = 160;
    private static final int JITTER = 40;
    /** Half the width of the asphalt: 5 blocks wide in all. */
    static final float HALF = 2.5f;
    /** Distances are only ever needed up to this far from a road (fade, stripes, hill spacing). */
    static final float CAP = 48.0f;
    /** Points per country-road curve. */
    private static final int CURVE_POINTS = 13;

    /** Half the size of a town square: its streets fill |dx|, |dz| <= 48 around the plaza centre. */
    static final int TOWN_HALF = 48;
    /** The four street lines of a town (offsets from its centre), both across and along. */
    static final int[] TOWN_LINES = {-45, -19, 19, 45};

    // Road flags.
    /** This road has a light concrete curb along its edges. */
    static final int CURB = 1;
    /** A street inside a town: the town draws it (no roadside houses, signs or sirens). */
    static final int TOWN_STREET = 2;
    /** The castle road: no roadside houses along it. */
    static final int CASTLE_ROAD = 4;
    /** A country road between two grid nodes (the only kind with junction signs). */
    static final int LATTICE = 8;
    /** A round turning circle at the end of a dead-end road. */
    static final int TURNING_CIRCLE = 16;

    private Level94Roads() {}

    // ==========================================================================================
    // Roads as polylines
    // ==========================================================================================

    /** One road: a polyline with its arc length, a few flags and its bounding box. Never changes once made. */
    static final class Road {
        final float[] px;
        final float[] pz;
        /** Arc length from the first point to point i. */
        final float[] arc;
        /** Extra half-width on top of {@link #HALF} (turning circles are wider). */
        final float extra;
        final int flags;
        /** Stable id for per-road random choices. */
        final long id;
        /** Grid node at each end, or Integer.MIN_VALUE when that end is not a node (town gates, castle). */
        final int ai;
        final int aj;
        final int bi;
        final int bj;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;

        Road(float[] px, float[] pz, float extra, int flags, long id, int ai, int aj, int bi, int bj) {
            this.px = px;
            this.pz = pz;
            this.extra = extra;
            this.flags = flags;
            this.id = id;
            this.ai = ai;
            this.aj = aj;
            this.bi = bi;
            this.bj = bj;
            arc = new float[px.length];
            float x0 = Float.MAX_VALUE;
            float z0 = Float.MAX_VALUE;
            float x1 = -Float.MAX_VALUE;
            float z1 = -Float.MAX_VALUE;
            for (int i = 0; i < px.length; i++) {
                if (i > 0) {
                    arc[i] = arc[i - 1] + (float) Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
                }
                x0 = Math.min(x0, px[i]);
                z0 = Math.min(z0, pz[i]);
                x1 = Math.max(x1, px[i]);
                z1 = Math.max(z1, pz[i]);
            }
            float pad = HALF + extra + 1;
            minX = (int) Math.floor(x0 - pad);
            minZ = (int) Math.floor(z0 - pad);
            maxX = (int) Math.ceil(x1 + pad);
            maxZ = (int) Math.ceil(z1 + pad);
        }

        float length() {
            return arc[arc.length - 1];
        }

        boolean has(int flag) {
            return (flags & flag) != 0;
        }

        /** True if this road comes within {@code margin} of the box. */
        boolean near(int x0, int z0, int x1, int z1, int margin) {
            return maxX >= x0 - margin && minX <= x1 + margin && maxZ >= z0 - margin && minZ <= z1 + margin;
        }

        /** Distance from (x, z) to the road's edge line at HALF (negative inside a wide turning circle). */
        float distance(float x, float z) {
            float best = Float.MAX_VALUE;
            if (px.length == 1) {
                best = (float) Math.hypot(x - px[0], z - pz[0]);
            }
            for (int i = 0; i + 1 < px.length; i++) {
                best = Math.min(best, segDist(x, z, px[i], pz[i], px[i + 1], pz[i + 1]));
            }
            return best - extra;
        }

        /** Writes the point at arc length s and the unit direction of travel there into {@code out} = {x, z, dx, dz}. */
        void pointAt(float s, float[] out) {
            int n = px.length;
            if (n == 1) {
                out[0] = px[0];
                out[1] = pz[0];
                out[2] = 1;
                out[3] = 0;
                return;
            }
            int i = 0;
            while (i < n - 2 && arc[i + 1] < s) {
                i++;
            }
            float segLen = Math.max(1e-4f, arc[i + 1] - arc[i]);
            float t = Math.max(0, Math.min(1, (s - arc[i]) / segLen));
            float dx = px[i + 1] - px[i];
            float dz = pz[i + 1] - pz[i];
            out[0] = px[i] + dx * t;
            out[1] = pz[i] + dz * t;
            float len = (float) Math.hypot(dx, dz);
            out[2] = len > 0 ? dx / len : 1;
            out[3] = len > 0 ? dz / len : 0;
        }
    }

    /** Distance from point (x, z) to segment (x0, z0)-(x1, z1). Plain float maths, so every caller gets the same answer. */
    static float segDist(float x, float z, float x0, float z0, float x1, float z1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len2 = dx * dx + dz * dz;
        float t = len2 > 0 ? ((x - x0) * dx + (z - z0) * dz) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        float ex = x0 + t * dx - x;
        float ez = z0 + t * dz - z;
        return (float) Math.sqrt(ex * ex + ez * ez);
    }

    /** Distance from (x, z) to the nearest road edge in the list (0 or less = on the road), capped at {@link #CAP}. */
    static float roadDistance(List<Road> roads, float x, float z) {
        float best = CAP;
        for (Road road : roads) {
            if (x < road.minX - CAP || x > road.maxX + CAP || z < road.minZ - CAP || z > road.maxZ + CAP) {
                continue;
            }
            best = Math.min(best, road.distance(x, z) + HALF);
        }
        return best - HALF;
    }

    /**
     * The point on the centre line of the nearest road to (x, z), written to {@code out} = {x, z};
     * returns its distance, or +infinity if there is none in the list. Town streets count too.
     */
    static float nearestRoadPoint(List<Road> roads, float x, float z, float[] out) {
        float best = Float.POSITIVE_INFINITY;
        for (Road road : roads) {
            float[] px = road.px;
            float[] pz = road.pz;
            for (int i = 0; i + 1 < px.length; i++) {
                float dx = px[i + 1] - px[i];
                float dz = pz[i + 1] - pz[i];
                float len2 = dx * dx + dz * dz;
                float t = len2 > 0 ? Math.max(0, Math.min(1, ((x - px[i]) * dx + (z - pz[i]) * dz) / len2)) : 0;
                float qx = px[i] + t * dx;
                float qz = pz[i] + t * dz;
                float d = (float) Math.hypot(qx - x, qz - z);
                if (d < best) {
                    best = d;
                    out[0] = qx;
                    out[1] = qz;
                }
            }
        }
        return best;
    }

    // ==========================================================================================
    // Towns (one per castle region)
    // ==========================================================================================

    /** A town: where its plaza is, which castle it belongs to and how its streets leave it. */
    static final class Town {
        final int rx;
        final int rz;
        final int tx;
        final int tz;
        final int castleX;
        final int castleZ;
        final int footX;
        final int footZ;
        /** The side of the town square that faces the castle; the castle road leaves there. */
        final Direction castleSide;
        /** Per side (2D data value): which street line (-19 or 19) runs out of the town on that side. */
        final int[] gateLine = new int[4];

        Town(long seed, int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
            int[] c = Level94Castle.centre(seed, rx, rz);
            castleX = c[0];
            castleZ = c[1];
            int[] foot = Level94Castle.rampFoot(seed, castleX, castleZ);
            footX = foot[0];
            footZ = foot[1];
            double dirX = foot[2];
            double dirZ = foot[3];
            double len = Math.hypot(dirX, dirZ);
            if (len < 1e-6) {
                // No direction given: point away from the castle centre instead.
                dirX = footX - castleX;
                dirZ = footZ - castleZ;
                len = Math.max(1e-6, Math.hypot(dirX, dirZ));
            }
            dirX /= len;
            dirZ /= len;
            // The plaza sits a short drive further out than the ramp foot, along the ramp's direction.
            int away = TOWN_HALF + 28;
            tx = (int) Math.round(footX + dirX * away);
            tz = (int) Math.round(footZ + dirZ * away);
            // The castle is behind the town, toward -dir.
            castleSide = Math.abs(dirX) >= Math.abs(dirZ)
                    ? (dirX > 0 ? Direction.WEST : Direction.EAST)
                    : (dirZ > 0 ? Direction.NORTH : Direction.SOUTH);
            for (int side = 0; side < 4; side++) {
                gateLine[side] = Hash.unit(seed, rx * 4 + side, rz, 9431) < 0.5 ? -19 : 19;
            }
            // The castle road takes the street line nearer the ramp foot, so it bends less.
            int lateral = castleSide.getAxis() == Direction.Axis.X ? footZ - tz : footX - tx;
            gateLine[castleSide.get2DDataValue()] = lateral < 0 ? -19 : 19;
        }

        boolean inBox(double x, double z, double margin) {
            return Math.abs(x - tx) <= TOWN_HALF + margin && Math.abs(z - tz) <= TOWN_HALF + margin;
        }

        /** How far (x, z) is outside the town square (0 inside). */
        double boxDistance(double x, double z) {
            double dx = Math.max(0, Math.abs(x - tx) - TOWN_HALF);
            double dz = Math.max(0, Math.abs(z - tz) - TOWN_HALF);
            return Math.hypot(dx, dz);
        }

        /** Where the street on {@code side} meets the edge of the town square: {x, z}. */
        int[] gate(Direction side) {
            int line = gateLine[side.get2DDataValue()];
            return side.getAxis() == Direction.Axis.X
                    ? new int[] {tx + side.getStepX() * TOWN_HALF, tz + line}
                    : new int[] {tx + line, tz + side.getStepZ() * TOWN_HALF};
        }
    }

    private static final ConcurrentHashMap<Long, Town> TOWNS = new ConcurrentHashMap<>();

    /** The town of castle region (rx, rz). Cached: the castle maths behind it is asked for constantly. */
    static Town town(long seed, int rx, int rz) {
        long key = Hash.of(seed, rx, rz, 9430);
        Town t = TOWNS.get(key);
        if (t == null) {
            if (TOWNS.size() > 512) {
                TOWNS.clear();
            }
            t = TOWNS.computeIfAbsent(key, k -> new Town(seed, rx, rz));
        }
        return t;
    }

    /** Distance from (x, z) to the nearest castle centre. */
    static double castleDistance(long seed, double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), Level94Castle.REGION);
        int rz = Math.floorDiv((int) Math.floor(z), Level94Castle.REGION);
        double best = Double.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                Town t = town(seed, rx + ox, rz + oz);
                best = Math.min(best, Math.hypot(x - t.castleX, z - t.castleZ));
            }
        }
        return best;
    }

    /** The town whose square (grown by margin) contains (x, z), or null. */
    @Nullable
    static Town townAt(long seed, double x, double z, double margin) {
        int rx = Math.floorDiv((int) Math.floor(x), Level94Castle.REGION);
        int rz = Math.floorDiv((int) Math.floor(z), Level94Castle.REGION);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                Town t = town(seed, rx + ox, rz + oz);
                if (t.inBox(x, z, margin)) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Distance from (x, z) to the nearest town square (0 inside one). */
    static double townDistance(long seed, double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), Level94Castle.REGION);
        int rz = Math.floorDiv((int) Math.floor(z), Level94Castle.REGION);
        double best = Double.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                best = Math.min(best, town(seed, rx + ox, rz + oz).boxDistance(x, z));
            }
        }
        return best;
    }

    /** True where roads and buildings must keep out: near a castle's clear zone or a town square. */
    private static boolean blocked(long seed, double x, double z, double castleMargin, double townMargin) {
        return castleDistance(seed, x, z) < Level94Castle.CLEAR_RADIUS + castleMargin || townAt(seed, x, z, townMargin) != null;
    }

    // ==========================================================================================
    // The country-road grid
    // ==========================================================================================

    static int nodeX(long seed, int i, int j) {
        return i * NODE + NODE / 2 + Hash.range(Hash.of(seed, i, j, 9401), 2 * JITTER + 1) - JITTER;
    }

    static int nodeZ(long seed, int i, int j) {
        return j * NODE + NODE / 2 + Hash.range(Hash.of(seed, i, j, 9402), 2 * JITTER + 1) - JITTER;
    }

    /** A node exists unless it would be close to a castle or inside a town. */
    static boolean nodeAlive(long seed, int i, int j) {
        return !blocked(seed, nodeX(seed, i, j), nodeZ(seed, i, j), 50, 30);
    }

    /** Marks "no road here" in the road cache. */
    private static final Road NO_ROAD = new Road(new float[] {0}, new float[] {0}, 0, 0, 0, 0, 0, 0, 0);
    private static final ConcurrentHashMap<Long, Road> EDGES = new ConcurrentHashMap<>();

    /** The road from node (i, j) east (dir 0) or south (dir 1) to the next node, or null if there is none. */
    @Nullable
    static Road edge(long seed, int i, int j, int dir) {
        long key = Hash.of(seed, i, j, 9410 + dir);
        Road r = EDGES.get(key);
        if (r == null) {
            if (EDGES.size() > 8192) {
                EDGES.clear();
            }
            r = EDGES.computeIfAbsent(key, k -> {
                Road made = buildEdge(seed, i, j, dir);
                return made != null ? made : NO_ROAD;
            });
        }
        return r == NO_ROAD ? null : r;
    }

    @Nullable
    private static Road buildEdge(long seed, int i, int j, int dir) {
        int bi = dir == 0 ? i + 1 : i;
        int bj = dir == 0 ? j : j + 1;
        long h = Hash.of(seed, i, j, 9412 + dir);
        if (Hash.unit(h) >= 0.72 || !nodeAlive(seed, i, j) || !nodeAlive(seed, bi, bj)) {
            return null;
        }
        float ax = nodeX(seed, i, j) + 0.5f;
        float az = nodeZ(seed, i, j) + 0.5f;
        float bx = nodeX(seed, bi, bj) + 0.5f;
        float bz = nodeZ(seed, bi, bj) + 0.5f;
        float[] px = new float[CURVE_POINTS];
        float[] pz = new float[CURVE_POINTS];
        // A gentle S or C bend: two sine waves across the straight line between the nodes.
        float len = (float) Math.hypot(bx - ax, bz - az);
        float nx = -(bz - az) / len;
        float nz = (bx - ax) / len;
        float a1 = (float) ((Hash.unit(Hash.mix(h)) - 0.5) * 0.44 * len);
        float a2 = (float) ((Hash.unit(Hash.mix(h + 1)) - 0.5) * 0.2 * len);
        for (int k = 0; k < CURVE_POINTS; k++) {
            float t = k / (float) (CURVE_POINTS - 1);
            float off = (float) (a1 * Math.sin(Math.PI * t) + a2 * Math.sin(2 * Math.PI * t));
            px[k] = ax + (bx - ax) * t + nx * off;
            pz[k] = az + (bz - az) * t + nz * off;
            if (blocked(seed, px[k], pz[k], 14, 8)) {
                return null;
            }
        }
        int flags = LATTICE | (Hash.unit(Hash.mix(h + 2)) < 0.35 ? CURB : 0);
        return new Road(px, pz, 0, flags, h, i, j, bi, bj);
    }

    /** How many roads meet at node (i, j): grid roads plus town streets that end there. */
    static int degree(long seed, int i, int j) {
        if (!nodeAlive(seed, i, j)) {
            return 0;
        }
        int n = 0;
        if (edge(seed, i, j, 0) != null) n++;
        if (edge(seed, i, j, 1) != null) n++;
        if (edge(seed, i - 1, j, 0) != null) n++;
        if (edge(seed, i, j - 1, 1) != null) n++;
        int x = nodeX(seed, i, j);
        int z = nodeZ(seed, i, j);
        int rx = Math.floorDiv(x, Level94Castle.REGION);
        int rz = Math.floorDiv(z, Level94Castle.REGION);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (Road gate : townRoads(seed, rx + ox, rz + oz).gates) {
                    if (gate.bi == i && gate.bj == j) n++;
                }
            }
        }
        return n;
    }

    // ==========================================================================================
    // Town streets, gate roads and the castle road
    // ==========================================================================================

    /** All roads belonging to one town. */
    static final class TownRoads {
        final Road[] streets;
        /** Roads from the town's street ends out to grid nodes (end b = the node). */
        final Road[] gates;
        @Nullable
        final Road castleRoad;

        TownRoads(Road[] streets, Road[] gates, @Nullable Road castleRoad) {
            this.streets = streets;
            this.gates = gates;
            this.castleRoad = castleRoad;
        }
    }

    private static final ConcurrentHashMap<Long, TownRoads> TOWN_ROADS = new ConcurrentHashMap<>();

    static TownRoads townRoads(long seed, int rx, int rz) {
        long key = Hash.of(seed, rx, rz, 9440);
        TownRoads t = TOWN_ROADS.get(key);
        if (t == null) {
            if (TOWN_ROADS.size() > 512) {
                TOWN_ROADS.clear();
            }
            // Built outside computeIfAbsent: it reads the node cache and other towns.
            t = buildTownRoads(seed, town(seed, rx, rz));
            TownRoads prev = TOWN_ROADS.putIfAbsent(key, t);
            if (prev != null) {
                t = prev;
            }
        }
        return t;
    }

    private static TownRoads buildTownRoads(long seed, Town town) {
        Road[] streets = new Road[TOWN_LINES.length * 2];
        int k = 0;
        for (int line : TOWN_LINES) {
            float lo = -TOWN_HALF + 1;
            float hi = TOWN_HALF - 1;
            streets[k++] = new Road(new float[] {town.tx + lo + 0.5f, town.tx + hi + 0.5f},
                    new float[] {town.tz + line + 0.5f, town.tz + line + 0.5f}, 0, TOWN_STREET, 0, Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0);
            streets[k++] = new Road(new float[] {town.tx + line + 0.5f, town.tx + line + 0.5f},
                    new float[] {town.tz + lo + 0.5f, town.tz + hi + 0.5f}, 0, TOWN_STREET, 0, Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0);
        }
        java.util.ArrayList<Road> gates = new java.util.ArrayList<>(3);
        Road castleRoad = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int[] g = town.gate(side);
            float gx = g[0] + 0.5f;
            float gz = g[1] + 0.5f;
            float ox = side.getStepX();
            float oz = side.getStepZ();
            long id = Hash.of(seed, town.rx * 4 + side.get2DDataValue(), town.rz, 9441);
            if (side == town.castleSide) {
                castleRoad = bentRoad(gx, gz, ox, oz, town.footX + 0.5f, town.footZ + 0.5f, CASTLE_ROAD, id,
                        Integer.MIN_VALUE, 0);
                continue;
            }
            // Head for the live node nearest a point 100 blocks out from the gate.
            float tx = gx + ox * 100;
            float tz = gz + oz * 100;
            int ci = Math.floorDiv((int) tx, NODE);
            int cj = Math.floorDiv((int) tz, NODE);
            int bestI = 0;
            int bestJ = 0;
            double best = Double.MAX_VALUE;
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    int i = ci + di;
                    int j = cj + dj;
                    int nx = nodeX(seed, i, j);
                    int nz = nodeZ(seed, i, j);
                    // Only nodes out in front of the gate, so the road never doubles back into town.
                    if ((nx - gx) * ox + (nz - gz) * oz < 40 || !nodeAlive(seed, i, j)) {
                        continue;
                    }
                    double d = Math.hypot(nx - tx, nz - tz);
                    if (d < best) {
                        best = d;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }
            if (best == Double.MAX_VALUE) {
                continue; // nowhere to go: this street just ends at the edge of town
            }
            Road gate = bentRoad(gx, gz, ox, oz, nodeX(seed, bestI, bestJ) + 0.5f, nodeZ(seed, bestI, bestJ) + 0.5f,
                    Hash.unit(Hash.mix(id)) < 0.5 ? CURB : 0, id, bestI, bestJ);
            if (gate != null && clearOfOthers(seed, gate, town)) {
                gates.add(gate);
            }
        }
        return new TownRoads(streets, gates.toArray(new Road[0]), castleRoad);
    }

    /**
     * A road that leaves (gx, gz) straight out along (ox, oz) and then bends smoothly to (ex, ez):
     * a quadratic curve whose control point lies straight out from the gate.
     */
    private static Road bentRoad(float gx, float gz, float ox, float oz, float ex, float ez, int flags, long id, int bi, int bj) {
        float dist = (float) Math.hypot(ex - gx, ez - gz);
        float sx = gx + ox * 6;
        float sz = gz + oz * 6;
        float cx = sx + ox * dist * 0.45f;
        float cz = sz + oz * dist * 0.45f;
        int n = CURVE_POINTS;
        float[] px = new float[n + 1];
        float[] pz = new float[n + 1];
        px[0] = gx;
        pz[0] = gz;
        for (int k = 0; k < n; k++) {
            float t = k / (float) (n - 1);
            float a = (1 - t) * (1 - t);
            float b = 2 * (1 - t) * t;
            float c = t * t;
            px[k + 1] = a * sx + b * cx + c * ex;
            pz[k + 1] = a * sz + b * cz + c * ez;
        }
        return new Road(px, pz, 0, flags, id, Integer.MIN_VALUE, 0, bi, bj);
    }

    /** A gate road must not run through a castle zone or another town. */
    private static boolean clearOfOthers(long seed, Road road, Town own) {
        for (int k = 1; k < road.px.length; k++) {
            float x = road.px[k];
            float z = road.pz[k];
            if (castleDistance(seed, x, z) < Level94Castle.CLEAR_RADIUS + 14) {
                return false;
            }
            Town t = townAt(seed, x, z, 8);
            if (t != null && t != own) {
                return false;
            }
        }
        return true;
    }

    // ==========================================================================================
    // Gathering
    // ==========================================================================================

    /**
     * Adds every road (grid roads, turning circles, town streets, gate and castle roads) that comes
     * within {@code margin} of the box to {@code out}. The same box always yields the same roads.
     */
    static void gather(long seed, int x0, int z0, int x1, int z1, int margin, List<Road> out) {
        int reach = NODE + JITTER + margin;
        int i0 = Math.floorDiv(x0 - reach, NODE);
        int i1 = Math.floorDiv(x1 + reach, NODE);
        int j0 = Math.floorDiv(z0 - reach, NODE);
        int j1 = Math.floorDiv(z1 + reach, NODE);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                for (int dir = 0; dir < 2; dir++) {
                    Road r = edge(seed, i, j, dir);
                    if (r != null && r.near(x0, z0, x1, z1, margin)) {
                        out.add(r);
                    }
                }
                Road circle = turningCircle(seed, i, j);
                if (circle != null && circle.near(x0, z0, x1, z1, margin)) {
                    out.add(circle);
                }
            }
        }
        int townReach = TOWN_HALF + 220 + margin;
        int rx0 = Math.floorDiv(x0 - townReach, Level94Castle.REGION);
        int rx1 = Math.floorDiv(x1 + townReach, Level94Castle.REGION);
        int rz0 = Math.floorDiv(z0 - townReach, Level94Castle.REGION);
        int rz1 = Math.floorDiv(z1 + townReach, Level94Castle.REGION);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                TownRoads t = townRoads(seed, rx, rz);
                for (Road r : t.streets) {
                    if (r.near(x0, z0, x1, z1, margin)) out.add(r);
                }
                for (Road r : t.gates) {
                    if (r.near(x0, z0, x1, z1, margin)) out.add(r);
                }
                if (t.castleRoad != null && t.castleRoad.near(x0, z0, x1, z1, margin)) {
                    out.add(t.castleRoad);
                }
            }
        }
    }

    private static final ConcurrentHashMap<Long, Road> CIRCLES = new ConcurrentHashMap<>();

    /** A dead-end road (node with a single road) ends in a round turning circle. */
    @Nullable
    static Road turningCircle(long seed, int i, int j) {
        long key = Hash.of(seed, i, j, 9420);
        Road r = CIRCLES.get(key);
        if (r == null) {
            if (CIRCLES.size() > 8192) {
                CIRCLES.clear();
            }
            Road made = degree(seed, i, j) == 1
                    ? new Road(new float[] {nodeX(seed, i, j) + 0.5f}, new float[] {nodeZ(seed, i, j) + 0.5f}, 4.0f,
                    TURNING_CIRCLE, key, i, j, i, j)
                    : NO_ROAD;
            r = CIRCLES.putIfAbsent(key, made);
            if (r == null) {
                r = made;
            }
        }
        return r == NO_ROAD ? null : r;
    }

    /** The live node nearest (x, z), as {x, z}, or null if none of the nine around it is live. */
    @Nullable
    static int[] nearestNode(long seed, int x, int z) {
        int ci = Math.floorDiv(x, NODE);
        int cj = Math.floorDiv(z, NODE);
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int i = ci + di;
                int j = cj + dj;
                if (degree(seed, i, j) == 0) {
                    continue;
                }
                long dx = nodeX(seed, i, j) - x;
                long dz = nodeZ(seed, i, j) - z;
                long d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = new int[] {nodeX(seed, i, j), nodeZ(seed, i, j)};
                }
            }
        }
        return best;
    }
}
