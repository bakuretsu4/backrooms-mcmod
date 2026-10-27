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
 * <p>It walks the maze skeleton (every room, or every wall segment) in a square around the player
 * and asks the layout's own pure functions, so it always agrees with what was generated. This is a
 * debugging aid: it allocates freely and takes a few milliseconds.
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
                "tape", "switch_boxes", "ladder", "exit_alcove", "graffiti", "window", "niche", "ramp", "soffit"}) {
            kinds.add(SET + name);
        }
        KINDS = List.copyOf(kinds);
    }

    private Level0Finder() {}

    /** Asks whether a room (two gaps) is the thing being looked for. */
    private interface RoomTest {
        boolean matches(long seed, int kind, long sx, long sz);
    }

    /** Asks whether a solid wall segment's feature (F_*) is the one being looked for. */
    private interface WallTest {
        boolean matches(int feature);
    }

    @Nullable
    static AnomalySite nearest(Level0Layout layout, long seed, int x, int z, String kind) {
        for (int k = 0; k < Level0Zones.KINDS; k++) {
            if (kind.equals(ZONE + Level0Zones.NAMES[k])) {
                int wanted = k;
                return nearestRoom(layout, seed, x, z, kind, (s, unitKind, sx, sz) -> unitKind == wanted);
            }
        }
        return switch (kind.substring(SET.length())) {
            case "furniture_heap" -> nearestRoom(layout, seed, x, z, kind, piece(Level0Layout.R_HEAP));
            case "throne" -> nearestRoom(layout, seed, x, z, kind, piece(Level0Layout.R_THRONE));
            case "mannequins" -> nearestRoom(layout, seed, x, z, kind, piece(Level0Layout.R_MANNEQUINS));
            case "hazmat_case" -> nearestRoom(layout, seed, x, z, kind, piece(Level0Layout.R_HAZMAT));
            case "ceiling_hole" -> nearestRoom(layout, seed, x, z, kind, piece(Level0Layout.R_HOLE));
            case "ceiling_cable" -> nearestRoom(layout, seed, x, z, kind, piece(Level0Layout.R_CABLE));
            case "tape" -> nearestRoom(layout, seed, x, z, kind, wallPiece(Level0Layout.W_TAPE));
            case "switch_boxes" -> nearestRoom(layout, seed, x, z, kind, wallPiece(Level0Layout.W_SWITCHES));
            case "ladder" -> nearestRoom(layout, seed, x, z, kind, wallPiece(Level0Layout.W_LADDER));
            case "ramp" -> nearestRoom(layout, seed, x, z, kind, Level0Layout::rampRoom);
            case "soffit" -> nearestRoom(layout, seed, x, z, kind, Level0Layout::soffitRoom);
            case "exit_alcove" -> nearestWall(layout, seed, x, z, kind, f -> (f & 7) == Level0Layout.F_ALCOVE);
            case "graffiti" -> nearestWall(layout, seed, x, z, kind, f -> (f & 7) == Level0Layout.F_GRAFFITI);
            case "window" -> nearestWall(layout, seed, x, z, kind, f -> (f & 7) == Level0Layout.F_WINDOW);
            case "niche" -> nearestWall(layout, seed, x, z, kind,
                    f -> (f & 7) == Level0Layout.F_NICHE_HIGH || (f & 7) == Level0Layout.F_NICHE_LOW);
            default -> null;
        };
    }

    private static RoomTest piece(int wanted) {
        return (seed, kind, sx, sz) -> kind != Level0Zones.CORRIDOR && Level0Layout.roomPiece(seed, kind, sx, sz) == wanted;
    }

    private static RoomTest wallPiece(int wanted) {
        return (seed, kind, sx, sz) -> (Level0Layout.roomWallPiece(seed, sx, sz) & 3) == wanted;
    }

    /** The nearest room (in plain maze) passing the test, as a site covering the room. */
    @Nullable
    private static AnomalySite nearestRoom(Level0Layout layout, long seed, int x, int z, String name, RoomTest test) {
        Bands xs = Level0Layout.X_WALLS;
        Bands zs = Level0Layout.Z_WALLS;
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
                if (distance >= bestDistance || !test.matches(seed, Level0Layout.unitKind(seed, sx, sz), sx, sz)
                        || !layout.inMaze(seed, cx, cz)) {
                    continue;
                }
                bestDistance = distance;
                best = new AnomalySite(name, xs.start(sx), zs.start(sz), xs.end(sx) - 1, zs.end(sz) - 1, cx, cz, 0, 0);
            }
        }
        return best;
    }

    /** The nearest solid wall segment (in plain maze) whose feature passes the test. */
    @Nullable
    private static AnomalySite nearestWall(Level0Layout layout, long seed, int x, int z, String name, WallTest test) {
        AnomalySite best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int axis = 0; axis < 2; axis++) {
            Bands walls = axis == 0 ? Level0Layout.X_WALLS : Level0Layout.Z_WALLS;
            Bands gaps = axis == 0 ? Level0Layout.Z_WALLS : Level0Layout.X_WALLS;
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
                    if (distance >= bestDistance) {
                        continue;
                    }
                    long sx = axis == 0 ? wall : gap;
                    long sz = axis == 0 ? gap : wall;
                    int kind = Level0Layout.unitKind(seed, sx, sz);
                    if (kind == Level0Zones.CORRIDOR
                            || Level0Layout.segmentState(seed, kind, Bands.index(wall), Bands.index(gap), axis) != Level0Layout.SOLID
                            || !test.matches(Level0Layout.solidFeature(seed, kind, Bands.index(wall), Bands.index(gap), axis,
                                    Bands.length(gap), Bands.length(wall)))
                            || !layout.inMaze(seed, cx, cz)) {
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
}
