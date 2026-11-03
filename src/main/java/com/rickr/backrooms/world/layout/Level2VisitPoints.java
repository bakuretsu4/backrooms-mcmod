package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Named test points of Level 2's oddities, for {@code /backrooms anomaly visit level_2/...
 * <point>}. Each point is where a tester should stand (always in the one-block lane) and which way
 * to look to check the thing:
 * <ul>
 *   <li>{@code front} (wrong_valves, heart_gauge): beside it, facing the wall;</li>
 *   <li>{@code between}/{@code wall_a}/{@code wall_b} (inverted_pipe);</li>
 *   <li>{@code first}/{@code second}/{@code last} (repeat_section: its section markers).</li>
 * </ul>
 * Points are {x, feetY, z, yawDegrees, pitchDegrees}.
 */
public final class Level2VisitPoints {
    private static final int STAND = LevelLayout.FLOOR_Y + 1;
    /** Eye height above the feet, to aim the view. */
    private static final double EYE = 1.62;

    private Level2VisitPoints() {}

    /** Every kind that has points (for the command's suggestions). */
    public static List<String> kinds() {
        return Level2Sites.ODDITIES;
    }

    /** The point names a kind has (empty for kinds without points). */
    public static List<String> names(String kind) {
        return switch (kind) {
            case Level2Sites.WRONG_VALVES, Level2Sites.HEART_GAUGE -> List.of("front");
            case Level2Sites.INVERTED_PIPE -> List.of("between", "wall_a", "wall_b");
            case Level2Sites.REPEAT_SECTION -> List.of("first", "second", "last");
            default -> List.of();
        };
    }

    /** {x, feetY, z, yaw, pitch} of a named point of the site, or null if it has no such point. */
    @Nullable
    public static double[] point(long seed, AnomalySite site, String name) {
        int band = Level2Line.band(site.anchorZ());
        int c = Level2Line.centre(seed, band);
        int side = Integer.signum(site.anchorZ() - c);
        int ax = site.anchorX();
        int az = site.anchorZ();
        return switch (site.kind()) {
            case Level2Sites.WRONG_VALVES, Level2Sites.HEART_GAUGE -> name.equals("front") ? look(ax, c, ax, az, 1.55) : null;
            case Level2Sites.INVERTED_PIPE -> switch (name) {
                case "between" -> look(ax - 4, c, ax, c, 1.5);
                case "wall_a" -> look(ax, c, ax, c + side, 1.0);
                case "wall_b" -> look(ax, c, ax, c - side, 2.0);
                default -> null;
            };
            case Level2Sites.REPEAT_SECTION -> {
                int first = Level2Oddities.repeatFirst(site);
                int which = switch (name) {
                    case "first" -> 0;
                    case "second" -> 1;
                    case "last" -> Level2Oddities.repeatCount(site) - 1;
                    default -> -1;
                };
                if (which < 0) {
                    yield null;
                }
                int marker = Level2Line.markerX(seed, band, first + which);
                int wall = Level2Section.bareSide(Level2Section.ring(seed, band, marker));
                yield look(marker - 1, c, marker, c + wall, 1.5);
            }
            default -> null;
        };
    }

    /**
     * Standing on column (x, z), looking at the middle of column (tx, tz) at height {@code ty}
     * above the floor you stand on.
     */
    private static double[] look(int x, int z, int tx, int tz, double ty) {
        double fx = x + 0.5;
        double fz = z + 0.5;
        double dx = tx + 0.5 - fx;
        double dz = tz + 0.5 - fz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(ty - EYE, Math.max(0.5, Math.hypot(dx, dz))));
        return new double[] {fx, STAND, fz, yaw, pitch};
    }
}
