package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Named test points of Level 0's own anomalies and hidden rooms, for {@code /backrooms anomaly
 * visit <kind> <point>} (the command and the set pieces' points are L0SetPieces'). Each point is
 * where a tester should stand and which way to look to check the thing works:
 * <ul>
 *   <li>hidden rooms: {@code entrance} (in the moat, facing the way in) and {@code inside} (in the
 *       room, facing its story beat);</li>
 *   <li>Red Rooms: {@code entrance} (facing the opening) and {@code loop_hole} (in the empty room,
 *       facing the hole into the crawlway);</li>
 *   <li>Manila Room: {@code guestbook}; pillar hall: {@code centre}; pit room: {@code edge};</li>
 *   <li>blackout zone: {@code edge} (on open floor just outside it, facing in).</li>
 * </ul>
 */
public final class Level0VisitPoints {
    private static final List<String> HIDDEN = List.of("entrance", "inside");
    private static final List<String> RED = List.of("entrance", "loop_hole");
    /** The blackout edge: how far out (blocks past the edge) we look for open floor, and in how many directions. */
    private static final int EDGE_SEARCH = 4;
    private static final int EDGE_DIRECTIONS = 32;

    private Level0VisitPoints() {}

    /** The point names a kind has (empty for kinds that aren't Level 0's own or hidden rooms). */
    public static List<String> names(String kind) {
        if (Level0HiddenRooms.KINDS.contains(kind)) {
            return HIDDEN;
        }
        return switch (kind) {
            case Level0AnomalyPlan.RED_ROOMS -> RED;
            case Level0AnomalyPlan.MANILA_ROOM -> List.of("guestbook");
            case Level0AnomalyPlan.PERIPHERAL_SHIFT -> List.of("centre");
            case Level0AnomalyPlan.PITFALLS, Level0AnomalyPlan.BLACKOUT_ZONE -> List.of("edge");
            default -> List.of();
        };
    }

    /** {x, feetY, z, yawDegrees} of a named point of the site, or null if the site has no such point. */
    @Nullable
    public static double[] point(Level0Layout layout, long seed, AnomalySite site, String name) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        String kind = site.kind();
        double[] p;
        if (Level0HiddenRooms.KINDS.contains(kind)) {
            p = Level0HiddenRooms.visit(site, name, floor, ceiling);
        } else {
            p = switch (kind) {
                case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.visit(site, name, floor);
                case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.visit(site, name, floor);
                case Level0AnomalyPlan.PERIPHERAL_SHIFT -> Level0PillarHall.visit(site, name, floor);
                case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.visit(site, name, floor);
                case Level0AnomalyPlan.BLACKOUT_ZONE -> name.equals("edge") ? blackoutEdge(layout, seed, site) : null;
                default -> null;
            };
        }
        if (p == null) {
            return null;
        }
        return new double[] {p[0], p[1], p[2], yaw(p[0], p[2], p[3], p[4])};
    }

    /**
     * Open floor just outside a blackout zone, facing its middle: walks out from the middle in 32
     * directions and takes the first walkable column 1-4 blocks past the edge (nearest first).
     */
    @Nullable
    private static double[] blackoutEdge(Level0Layout layout, long seed, AnomalySite site) {
        double cx = site.anchorX() + 0.5;
        double cz = site.anchorZ() + 0.5;
        for (int past = 1; past <= EDGE_SEARCH; past++) {
            for (int k = 0; k < EDGE_DIRECTIONS; k++) {
                double a = 2.0 * Math.PI * k / EDGE_DIRECTIONS;
                double dx = Math.cos(a);
                double dz = Math.sin(a);
                // Out from the middle until we're outside the zone's wobbly edge.
                double r = 0.0;
                while (r < 64.0 && Level0Blackout.inside(site, (int) Math.floor(cx + dx * r), (int) Math.floor(cz + dz * r))) {
                    r += 0.5;
                }
                int x = (int) Math.floor(cx + dx * (r + past));
                int z = (int) Math.floor(cz + dz * (r + past));
                if (layout.isSafeArrival(seed, x, z) && layout.anomalyAt(seed, x, z) == null) {
                    return new double[] {x + 0.5, layout.feetY(seed, x, z), z + 0.5, cx, cz};
                }
            }
        }
        return null;
    }

    /** Minecraft yaw (0 = south, 90 = west, 180 = north, -90 = east) to look from one point at another. */
    private static double yaw(double fromX, double fromZ, double toX, double toZ) {
        return Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }
}
