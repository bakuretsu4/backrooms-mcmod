package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Finds Level 2's segments, side rooms and exits for testing: {@code /backrooms anomaly locate
 * level_2/seg/pump}, {@code ... level_2/room/safe} or {@code ... level_2/exit} answers with the
 * nearest one, through the same command that finds anomalies ({@link Level2Layout#nearestAnomaly}).
 * The answer is a made-up "site" covering the segment, room or bay, its anchor where you would
 * stand to see it (the middle of the segment on the centre line, a room's doorway, the exit door).
 *
 * <p>It walks the corridors of the nearest bands piece by piece and asks {@link Level2Line} (the
 * plan the blocks are built from), so it always agrees with what was generated. This is a
 * debugging aid: it allocates freely and takes a few milliseconds.
 */
final class Level2Finder {
    private static final String SEG = "level_2/seg/";
    private static final String ROOM = "level_2/room/";
    private static final String EXIT = "level_2/exit";
    /** Bands searched each way from yours. */
    private static final int BANDS = 6;
    /** How far (blocks along the corridor, each way) to search. */
    private static final int RADIUS = 1200;

    static final List<String> KINDS;

    static {
        List<String> kinds = new ArrayList<>();
        for (int t = 0; t < Level2Line.SEG_NAMES.length; t++) {
            if (t != Level2Line.SEG_EXIT) { // exits have their own key below
                kinds.add(SEG + Level2Line.SEG_NAMES[t]);
            }
        }
        for (String name : Level2Line.ROOM_NAMES) {
            if (name != null) { // the hot room and the oddities' rooms are found as anomalies
                kinds.add(ROOM + name);
            }
        }
        kinds.add(EXIT);
        KINDS = List.copyOf(kinds);
    }

    private Level2Finder() {}

    @Nullable
    static AnomalySite nearest(long seed, int x, int z, String kind) {
        int home = Level2Line.band(z);
        AnomalySite best = null;
        double bestDist = Double.MAX_VALUE;
        for (int band = home - BANDS; band <= home + BANDS; band++) {
            AnomalySite found = kind.equals(EXIT) ? exitOn(seed, band, x, kind)
                    : kind.startsWith(SEG) ? segmentOn(seed, band, x, kind, indexOf(Level2Line.SEG_NAMES, kind.substring(SEG.length())))
                    : roomOn(seed, band, x, kind, indexOf(Level2Line.ROOM_NAMES, kind.substring(ROOM.length())));
            if (found != null) {
                double d = found.distanceTo(x, z);
                if (d < bestDist) {
                    bestDist = d;
                    best = found;
                }
            }
        }
        return best;
    }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (name.equals(names[i])) {
                return i;
            }
        }
        return -1;
    }

    /** The segment of type {@code type} on this band nearest to x along the corridor. */
    @Nullable
    private static AnomalySite segmentOn(long seed, int band, int x, String kind, int type) {
        int c = Level2Line.centre(seed, band);
        int n0 = Level2Line.segment(seed, band, x);
        int steps = RADIUS / Level2Line.MIN_SEG;
        for (int step = 0; step <= steps; step++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int n = n0 + sign * step;
                if (Level2Line.segType(seed, band, n) == type
                        && (type != Level2Line.SEG_TRENCH || Level2Line.hasTrench(seed, band, n))) {
                    int start = Level2Line.segStart(seed, band, n);
                    int end = Level2Line.segEnd(seed, band, n);
                    return new AnomalySite(kind, start, c - Level2Line.WALL, end, c + Level2Line.WALL,
                            type == Level2Line.SEG_PUMP ? Level2Line.alcoveMid(seed, band, n) : Level2Line.segMid(seed, band, n), c, 0, 0);
                }
                if (step == 0) {
                    break;
                }
            }
        }
        return null;
    }

    /** The side room of type {@code type} on this band nearest to x. */
    @Nullable
    private static AnomalySite roomOn(long seed, int band, int x, String kind, int type) {
        if (type < 0) {
            return null;
        }
        int c = Level2Line.centre(seed, band);
        int k0 = Level2Line.roomCell(x);
        int steps = RADIUS / Level2Line.ROOM_CELL;
        for (int step = 0; step <= steps; step++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int k = k0 + sign * step;
                if (Level2Line.roomType(seed, band, k) == type) {
                    int slot = Level2Line.slotX(seed, band, k);
                    int side = Level2Line.slotSide(seed, band, k);
                    int hw = type == Level2Line.ROOM_SAFE ? 3 : Level2Line.roomHalfWidth(type);
                    int depth = type == Level2Line.ROOM_SAFE ? 8 : Level2Line.roomDepth(type);
                    int near = c + side * Level2Line.WALL;
                    int far = c + side * (Level2Line.WALL + 1 + depth);
                    return new AnomalySite(kind, slot - hw, Math.min(near, far), slot + hw, Math.max(near, far),
                            slot, near, 0, 0);
                }
                if (step == 0) {
                    break;
                }
            }
        }
        return null;
    }

    /** The exit bay on this band nearest to x. */
    private static AnomalySite exitOn(long seed, int band, int x, String kind) {
        int j = Level2Line.nearestExit(seed, band, x);
        int ex = Level2Line.exitX(seed, band, j);
        int c = Level2Line.centre(seed, band);
        int side = Level2Line.exitSide(seed, band, j);
        int door = c + side * Level2Line.BAY_BACK;
        int mouth = c + side * Level2Line.CHASE;
        return new AnomalySite(kind, ex - Level2Line.BAY_HALF, Math.min(door, mouth), ex + Level2Line.BAY_HALF,
                Math.max(door, mouth), ex, door, 0, 0);
    }
}
