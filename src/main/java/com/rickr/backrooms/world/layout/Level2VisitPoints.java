package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Named test points of Level 2's chambers, detours and oddities, for {@code /backrooms anomaly
 * visit level_2/... <point>}. Each point is where a tester should stand and which way to look to
 * check the thing:
 * <ul>
 *   <li>chambers ({@code level_2/room/pump_room} ...): {@code door} (in the corridor, facing the
 *       steel door) and {@code story} (beside the logbook, looking down at it);</li>
 *   <li>detours ({@code level_2/seg/dogleg} ...): {@code approach} (on the centre line, looking
 *       along the corridor at it) and one inside it ({@code around}, {@code under},
 *       {@code catwalk} / {@code lower}, {@code seal} / {@code passage});</li>
 *   <li>oddities: {@code inside}/{@code door} (wrong_valves), {@code front} (warm_door,
 *       heart_gauge), {@code between}/{@code wall_a}/{@code wall_b} (inverted_pipe),
 *       {@code first}/{@code second}/{@code last} (repeat_section: its section markers).</li>
 * </ul>
 * Points are {x, feetY, z, yawDegrees, pitchDegrees}.
 */
public final class Level2VisitPoints {
    private static final String ROOM = "level_2/room/";
    private static final String SEG = "level_2/seg/";
    private static final int STAND = LevelLayout.FLOOR_Y + 1;
    /** Eye height above the feet, to aim the view. */
    private static final double EYE = 1.62;

    private Level2VisitPoints() {}

    /** Every kind that has points (for the command's suggestions). */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>();
        for (int t = Level2Line.ROOM_PUMP_ROOM; t <= Level2Line.ROOM_BREAK; t++) {
            kinds.add(ROOM + Level2Line.ROOM_NAMES[t]);
        }
        for (int t = Level2Line.SEG_DOGLEG; t <= Level2Line.SEG_BYPASS; t++) {
            kinds.add(SEG + Level2Line.SEG_NAMES[t]);
        }
        kinds.addAll(Level2Sites.ODDITIES);
        return kinds;
    }

    /** The point names a kind has (empty for kinds without points). */
    public static List<String> names(String kind) {
        if (kind.startsWith(ROOM) && roomType(kind) >= Level2Line.ROOM_PUMP_ROOM) {
            return List.of("door", "story");
        }
        return switch (kind) {
            case "level_2/seg/dogleg" -> List.of("approach", "around");
            case "level_2/seg/duct_crossing" -> List.of("approach", "under");
            case "level_2/seg/catwalk" -> List.of("approach", "catwalk", "lower");
            case "level_2/seg/bypass" -> List.of("approach", "seal", "passage");
            case Level2Sites.WRONG_VALVES -> List.of("door", "inside");
            case Level2Sites.WARM_DOOR, Level2Sites.HEART_GAUGE -> List.of("front");
            case Level2Sites.INVERTED_PIPE -> List.of("between", "wall_a", "wall_b");
            case Level2Sites.REPEAT_SECTION -> List.of("first", "second", "last");
            default -> List.of();
        };
    }

    private static int roomType(String kind) {
        String name = kind.substring(ROOM.length());
        for (int t = 0; t < Level2Line.ROOM_NAMES.length; t++) {
            if (name.equals(Level2Line.ROOM_NAMES[t])) {
                return t;
            }
        }
        return -1;
    }

    /** {x, feetY, z, yaw, pitch} of a named point of the site, or null if it has no such point. */
    @Nullable
    public static double[] point(long seed, AnomalySite site, String name) {
        String kind = site.kind();
        int band = Level2Line.band(site.anchorZ());
        int c = Level2Line.centre(seed, band);
        if (kind.startsWith(ROOM)) {
            int type = roomType(kind);
            if (type < Level2Line.ROOM_PUMP_ROOM) {
                return null;
            }
            int slot = site.anchorX();
            int side = site.anchorZ() < c ? -1 : 1;
            return chamber(type, slot, c, side, name);
        }
        if (kind.startsWith(SEG)) {
            int n = Level2Line.segment(seed, band, site.anchorX());
            return detour(seed, band, n, c, Level2Line.segType(seed, band, n), name);
        }
        int side = Level2AnomalyPlan.doorSide(site);
        int ax = site.anchorX();
        int az = site.anchorZ();
        return switch (kind) {
            case Level2Sites.WRONG_VALVES -> switch (name) {
                case "door" -> look(ax, c + side * 2, ax, c + side * Level2Line.WALL, 1.5);
                case "inside" -> look(ax, az + side, ax, az + side * Level2Line.roomDepth(Level2Line.ROOM_GALLERY_ODD), 1.6);
                default -> null;
            };
            case Level2Sites.WARM_DOOR -> name.equals("front") ? look(ax, c + side * 2, ax, az, 1.0) : null;
            case Level2Sites.HEART_GAUGE -> name.equals("front") ? look(ax, c + side, ax, az, 1.55) : null;
            case Level2Sites.INVERTED_PIPE -> switch (name) {
                case "between" -> look(ax - 4, c, ax, c, 2.0);
                case "wall_a" -> look(ax, c - side, ax, c + side * Level2Line.CHASE, 1.5);
                case "wall_b" -> look(ax, c + side, ax, c - side * Level2Line.CHASE, 2.6);
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
                int pier = Level2Line.pierAtOrAfter(seed, band, Level2Line.segStart(seed, band, first + which), -1);
                yield look(pier, c + 1, pier, c - Level2Line.SHOULDER, 1.5);
            }
            default -> null;
        };
    }

    /** A chamber's door (from the corridor) or its story cell (the logbook). */
    @Nullable
    private static double[] chamber(int type, int slot, int c, int side, String name) {
        if (name.equals("door")) {
            return look(slot, c + side * 2, slot, c + side * Level2Line.WALL, 1.5);
        }
        if (!name.equals("story")) {
            return null;
        }
        // {stand i, stand j, look i, look j}: beside the logbook (or, in the sump, at the walkway's end).
        int[] cell = Level2Chambers.storyCell(type);
        int x = slot + cell[0];
        int z = c + side * (Level2Line.WALL + 1 + cell[1]);
        return look(x, z, slot + cell[2], c + side * (Level2Line.WALL + 1 + cell[3]), 1.1);
    }

    /** The approach to a detour and the spots inside it. */
    @Nullable
    private static double[] detour(long seed, int band, int n, int c, int type, String name) {
        int mid = Level2Line.segMid(seed, band, n);
        int side = Level2Line.detourSide(seed, band, n);
        if (name.equals("approach")) {
            return look(mid - 10, c, mid, c, 2.0);
        }
        return switch (type) {
            case Level2Line.SEG_DOGLEG -> name.equals("around") ? look(mid - 3, c + side * 4, mid + 3, c + side * 3, 2.0) : null;
            case Level2Line.SEG_DUCT -> name.equals("under") ? look(mid - 4, c, mid, c, 2.6) : null;
            case Level2Line.SEG_CATWALK -> switch (name) {
                case "catwalk" -> look(mid - 6, c + side, mid + 2, c - side * 2, 0.5);
                case "lower" -> {
                    double[] p = look(mid - 4, c - side * 2, mid + 4, c - side * 2, 1.6);
                    p[1] = STAND - Level2Detours.CAT_DEPTH;
                    yield p;
                }
                default -> null;
            };
            case Level2Line.SEG_BYPASS -> switch (name) {
                case "seal" -> look(mid - Level2Detours.SEAL_HALF - 4, c, mid, c, 1.5);
                // In the middle of the one-wide passage just inside its west end, looking along it at eye height.
                case "passage" -> look(mid - Level2Detours.BYPASS_HALF + 1, c + side * (Level2Line.WALL + 1),
                        mid + Level2Detours.BYPASS_HALF - 1, c + side * (Level2Line.WALL + 1), 1.62);
                default -> null;
            };
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
