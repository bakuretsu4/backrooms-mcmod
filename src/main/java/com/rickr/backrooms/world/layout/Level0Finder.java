package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Finds Level 0's zones and set pieces for testing: {@code /backrooms anomaly locate
 * level_0/zone/pillar_hall} or {@code ... level_0/set/furniture_heap} answers with the nearest one,
 * through the same command that finds anomalies ({@link Level0Layout#nearestAnomaly}). The answer
 * is a made-up "site" covering the room or wall it is in, so the command can print where it is.
 *
 * <p>It walks the maze skeleton (every room, or every wall segment; the big grid of
 * {@link Level0Builders} for the builders' rooms) in a square around the player and asks the
 * layout's own pure functions, so it always agrees with what was generated. This is a debugging
 * aid: it allocates freely and takes a few milliseconds.
 */
final class Level0Finder {
    private static final String ZONE = "level_0/zone/";
    private static final String SET = "level_0/set/";
    /** How far (blocks, each way) to search. */
    private static final int RADIUS = 384;

    static final List<String> KINDS;

    static {
        List<String> kinds = new ArrayList<>();
        for (String name : Level0Zones.NAMES) {
            kinds.add(ZONE + name);
        }
        for (String name : new String[] {"furniture_heap", "throne", "mannequins", "hazmat_case", "ceiling_hole", "ceiling_cable",
                "tape", "switch_boxes", "ladder", "exit_alcove", "graffiti", "window", "niche", "ramp", "noclipped"}) {
            kinds.add(SET + name);
        }
        KINDS = List.copyOf(kinds);
    }

    private Level0Finder() {}

    /** Asks whether a room (two gaps of the grid being searched) is the thing being looked for. */
    private interface RoomTest {
        boolean matches(long seed, long sx, long sz);
    }

    /** Asks whether a wall segment (wall band, gap, axis) of the grid being searched is the thing being looked for. */
    private interface WallTest {
        boolean matches(long seed, long wall, long gap, int axis);
    }

    @Nullable
    static AnomalySite nearest(Level0Layout layout, long seed, int x, int z, String kind) {
        Bands xs = Level0Layout.X_WALLS;
        Bands zs = Level0Layout.Z_WALLS;
        Bands bigX = Level0Builders.X_BIG;
        Bands bigZ = Level0Builders.Z_BIG;
        if (kind.equals(ZONE + Level0Zones.NAMES[Level0Zones.BUILDERS])) {
            return nearestRoom(layout, seed, x, z, kind, bigX, bigZ, Level0Builders::isRoom);
        }
        for (int k = 0; k < Level0Zones.KINDS; k++) {
            if (kind.equals(ZONE + Level0Zones.NAMES[k])) {
                int wanted = k;
                return nearestRoom(layout, seed, x, z, kind, xs, zs, (s, sx, sz) -> Level0Layout.unitKind(s, sx, sz) == wanted);
            }
        }
        return switch (kind.substring(SET.length())) {
            case "furniture_heap" -> nearestRoom(layout, seed, x, z, kind, xs, zs, piece(Level0Layout.R_HEAP));
            case "throne" -> nearestRoom(layout, seed, x, z, kind, xs, zs, piece(Level0Layout.R_THRONE));
            case "hazmat_case" -> nearestRoom(layout, seed, x, z, kind, xs, zs, piece(Level0Layout.R_HAZMAT));
            case "ceiling_hole" -> nearestRoom(layout, seed, x, z, kind, xs, zs, piece(Level0Layout.R_HOLE));
            case "ceiling_cable" -> nearestRoom(layout, seed, x, z, kind, xs, zs, piece(Level0Layout.R_CABLE));
            case "tape" -> nearestRoom(layout, seed, x, z, kind, xs, zs, wallPiece(Level0Layout.W_TAPE));
            case "switch_boxes" -> nearestRoom(layout, seed, x, z, kind, xs, zs, wallPiece(Level0Layout.W_SWITCHES));
            case "ladder" -> nearestRoom(layout, seed, x, z, kind, xs, zs, wallPiece(Level0Layout.W_LADDER));
            case "exit_alcove" -> nearestWall(layout, seed, x, z, kind, xs, zs, solid(Level0Layout.F_ALCOVE));
            case "graffiti" -> nearestWall(layout, seed, x, z, kind, xs, zs, solid(Level0Layout.F_GRAFFITI));
            // The builders' rooms, on their own big grid.
            case "mannequins" -> nearestRoom(layout, seed, x, z, kind, bigX, bigZ,
                    (s, sx, sz) -> Level0Builders.isRoom(s, sx, sz) && Level0Builders.hasMannequins(s, sx, sz));
            case "ramp" -> nearestRoom(layout, seed, x, z, kind, bigX, bigZ,
                    (s, sx, sz) -> Level0Builders.isRoom(s, sx, sz) && (Level0Builders.plan(s, sx, sz) & 15) == Level0Builders.L_PASSAGE);
            case "window" -> nearestWall(layout, seed, x, z, kind, bigX, bigZ, buildersWall(Level0Builders.F_WINDOWS));
            case "niche" -> nearestWall(layout, seed, x, z, kind, bigX, bigZ, buildersWall(Level0Builders.F_NICHES));
            case "noclipped" -> nearestNoclipped(layout, seed, x, z, kind);
            default -> null;
        };
    }

    private static RoomTest piece(int wanted) {
        return (seed, sx, sz) -> {
            int kind = Level0Layout.unitKind(seed, sx, sz);
            return kind != Level0Zones.CORRIDOR && Level0Layout.roomPiece(seed, kind, sx, sz) == wanted;
        };
    }

    private static RoomTest wallPiece(int wanted) {
        return (seed, sx, sz) -> (Level0Layout.roomWallPiece(seed, sx, sz) & 3) == wanted;
    }

    /** A solid ordinary wall segment with feature F_* {@code wanted}. */
    private static WallTest solid(int wanted) {
        return (seed, wall, gap, axis) -> {
            long sx = axis == 0 ? wall : gap;
            long sz = axis == 0 ? gap : wall;
            int kind = Level0Layout.unitKind(seed, sx, sz);
            return kind != Level0Zones.CORRIDOR
                    && Level0Layout.segmentState(seed, kind, Bands.index(wall), Bands.index(gap), axis) == Level0Layout.SOLID
                    && (Level0Layout.solidFeature(seed, kind, Bands.index(wall), Bands.index(gap), axis,
                            Bands.length(gap), Bands.length(wall)) & 7) == wanted;
        };
    }

    /** A builders' room wall (with a builders' room on at least one side) carrying feature series {@code wanted}. */
    private static WallTest buildersWall(int wanted) {
        return (seed, wall, gap, axis) -> {
            Bands walls = axis == 0 ? Level0Builders.X_BIG : Level0Builders.Z_BIG;
            long low = walls.span(seed, walls.start(wall) - 1);
            long high = walls.span(seed, walls.end(wall));
            boolean builders = axis == 0
                    ? Level0Builders.isRoom(seed, low, gap) || Level0Builders.isRoom(seed, high, gap)
                    : Level0Builders.isRoom(seed, gap, low) || Level0Builders.isRoom(seed, gap, high);
            return builders && Level0Builders.feature(seed, wall, gap, axis) == wanted;
        };
    }

    /** The nearest room (in plain maze) passing the test, as a site covering the room. */
    @Nullable
    private static AnomalySite nearestRoom(Level0Layout layout, long seed, int x, int z, String name, Bands xs, Bands zs, RoomTest test) {
        AnomalySite best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int vx = x - RADIUS; vx <= x + RADIUS; ) {
            long sx = xs.span(seed, vx);
            vx = xs.end(sx);
            if (Bands.isBand(sx)) {
                continue;
            }
            for (int vz = z - RADIUS; vz <= z + RADIUS; ) {
                long sz = zs.span(seed, vz);
                vz = zs.end(sz);
                if (Bands.isBand(sz)) {
                    continue;
                }
                int cx = xs.start(sx) + Bands.length(sx) / 2;
                int cz = zs.start(sz) + Bands.length(sz) / 2;
                long distance = (long) (cx - x) * (cx - x) + (long) (cz - z) * (cz - z);
                if (distance >= bestDistance || !test.matches(seed, sx, sz) || !layout.inMaze(seed, cx, cz)) {
                    continue;
                }
                bestDistance = distance;
                best = new AnomalySite(name, xs.start(sx), zs.start(sz), xs.end(sx) - 1, zs.end(sz) - 1, cx, cz, 0, 0);
            }
        }
        return best;
    }

    /** The nearest wall segment (in plain maze) passing the test. */
    @Nullable
    private static AnomalySite nearestWall(Level0Layout layout, long seed, int x, int z, String name, Bands xs, Bands zs, WallTest test) {
        AnomalySite best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int axis = 0; axis < 2; axis++) {
            Bands walls = axis == 0 ? xs : zs;
            Bands gaps = axis == 0 ? zs : xs;
            int wallFrom = axis == 0 ? x : z;
            int gapFrom = axis == 0 ? z : x;
            for (int w = wallFrom - RADIUS; w <= wallFrom + RADIUS; ) {
                long wall = walls.span(seed, w);
                w = walls.end(wall);
                if (!Bands.isBand(wall)) {
                    continue;
                }
                for (int g = gapFrom - RADIUS; g <= gapFrom + RADIUS; ) {
                    long gap = gaps.span(seed, g);
                    g = gaps.end(gap);
                    if (Bands.isBand(gap)) {
                        continue;
                    }
                    int a = walls.start(wall);
                    int b = gaps.start(gap) + Bands.length(gap) / 2;
                    int cx = axis == 0 ? a : b;
                    int cz = axis == 0 ? b : a;
                    long distance = (long) (cx - x) * (cx - x) + (long) (cz - z) * (cz - z);
                    if (distance >= bestDistance || !test.matches(seed, wall, gap, axis) || !layout.inMaze(seed, cx, cz)) {
                        continue;
                    }
                    bestDistance = distance;
                    int x0 = axis == 0 ? walls.start(wall) : gaps.start(gap);
                    int z0 = axis == 0 ? gaps.start(gap) : walls.start(wall);
                    int x1 = axis == 0 ? walls.end(wall) - 1 : gaps.end(gap) - 1;
                    int z1 = axis == 0 ? gaps.end(gap) - 1 : walls.end(wall) - 1;
                    best = new AnomalySite(name, x0, z0, x1, z1, cx, cz, 0, 0);
                }
            }
        }
        return best;
    }

    /** The nearest piece of noclipped furniture (searched square by square of its placement grid). */
    @Nullable
    private static AnomalySite nearestNoclipped(Level0Layout layout, long seed, int x, int z, String name) {
        int cell = Level0Layout.embedCell();
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int cx = Math.floorDiv(x - RADIUS, cell); cx <= Math.floorDiv(x + RADIUS, cell); cx++) {
            for (int cz = Math.floorDiv(z - RADIUS, cell); cz <= Math.floorDiv(z + RADIUS, cell); cz++) {
                long mx = (long) cx * cell + cell / 2 - x;
                long mz = (long) cz * cell + cell / 2 - z;
                if (mx * mx + mz * mz >= bestDistance) {
                    continue;
                }
                int[] spot = layout.noclippedIn(seed, cx, cz);
                if (spot != null) {
                    long dx = spot[0] - x;
                    long dz = spot[1] - z;
                    if (dx * dx + dz * dz < bestDistance) {
                        bestDistance = dx * dx + dz * dz;
                        best = spot;
                    }
                }
            }
        }
        return best == null ? null : new AnomalySite(name, best[0], best[1], best[0], best[1], best[0], best[1], 0, 0);
    }
}
