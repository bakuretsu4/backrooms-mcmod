package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The shape of one chairlift line in Level 9's Crimson Field, worked out from its
 * {@link AnomalySite} alone (anchor, facing and salt; never the world seed).
 *
 * <p>The world generator ({@link GondolaBuild}) places the station and the towers from it, the
 * server moves the seat ({@link GondolaChair}) along its cable with it, and the client draws the
 * cable and the passing chairs with it. All three must agree to the centimetre (a chair must hang
 * exactly on the drawn cable), so the maths lives here once. The client only ever gets the site
 * (or the same numbers through the seat entity), which is all this class needs.
 *
 * <p><b>Local coordinates.</b> {@code a} runs ALONG the line, away from the station (0 = the
 * bull wheel's column, the site anchor), {@code s} ACROSS it (positive = to the right when you
 * look up the line). The line runs in one of the four compass directions (the site's
 * {@code facing}), so a and s are whole blocks and map straight onto world x and z. For
 * continuous positions a column's centre is at a whole number (column a=0 spans -0.5..0.5).
 *
 * <p><b>The cable</b> is a loop: the up line runs on the right ({@code s = +LINE_HALF}), round the
 * bull wheel at the station and back down on the left. It sits level over the station, then climbs
 * from tower head to tower head, sagging a little in each span, each tower taller than the last,
 * and runs on past the last tower into the fog.
 */
public final class GondolaLine {
    // ---- Fixed heights (block y). Level 9's ground top is FLOOR_Y + 1, so you stand at y 42. ----
    /** Top block of the field's ground. */
    public static final int GROUND_Y = LevelLayout.FLOOR_Y + 1;
    /** The station deck: a slab on the ground (its top is at 42.5). */
    public static final int DECK_Y = GROUND_Y + 1;
    /** The boarding chair's block (its seat is a hand's width above the deck). */
    public static final int CHAIR_Y = DECK_Y + 1;
    /** Height of the cable over the station: the chair hangs two blocks under it. */
    public static final int STATION_CABLE_Y = CHAIR_Y + 2;
    /** Level 9 is only 64 blocks tall: real blocks stop here and the client draws the rest. */
    public static final int BUILD_TOP = 63;

    // ---- Station layout (local a) ------------------------------------------------------------------
    /** Distance between the up line and the down line is twice this (blocks). */
    public static final double LINE_HALF = 1.5;
    /** The bull wheel's column (the site anchor). */
    public static final int WHEEL_A = 0;
    /** The column where the boarding chair hangs (s = 1; its model is offset half a block right). */
    public static final int CHAIR_A = 3;
    public static final int CHAIR_S = 1;
    /** The short tower at the front edge of the deck, where the cable leaves the station. */
    public static final int FRAME_A = 8;
    /** The deck covers a = DECK_A0..FRAME_A, s = -DECK_HALF..DECK_HALF. */
    public static final int DECK_A0 = -3;
    public static final int DECK_HALF = 4;
    /** The site reaches this far behind the wheel and this far to each side. */
    private static final int SITE_BACK = 5;
    private static final int SITE_HALF = 5;

    // ---- Towers (tunables) -------------------------------------------------------------------------
    public static final int MIN_TOWERS = 5;
    public static final int MAX_TOWERS = 8;
    public static final int MIN_SPACING = 25;
    public static final int MAX_SPACING = 35;
    /** The last tower carries the cable this many blocks above the ground (a random amount in the range). */
    public static final int MIN_TOP = 30;
    public static final int MAX_TOP = 44;
    /** How the climb is shared out: below 1 the first towers climb fastest (you rise quickly). */
    private static final double CLIMB_CURVE = 0.65;
    /** The cable sags this much at mid-span: a fixed part plus a share of the span length. */
    private static final double SAG_BASE = 0.3;
    private static final double SAG_PER_BLOCK = 0.02;
    /** After the last tower the cable (and the chairs) go on this far into the fog. */
    public static final int RUN_OUT = 160;
    /** The cable runs over the top of the sheave wheels, which are at the top of the head block. */
    public static final int CABLE_ABOVE_HEAD = 1;

    // ---- The ride (tunables) -----------------------------------------------------------------------
    /** How long the ride lasts before you are through the ceiling of Level 39 (ticks, ~6 s). */
    public static final int RIDE_TICKS = 120;
    /** Top speed along the line (blocks per second) ... */
    public static final double RIDE_SPEED = 9.0;
    /** ... reached after this long, like a chair clamping onto the moving cable (ticks). */
    public static final int RIDE_ACCEL_TICKS = 20;
    /** You may still hop off (sneak) during this first part of the ride (ticks). */
    public static final int DISMOUNT_TICKS = 20;
    /** Height of the seat above the bottom of the chair model (where the rider sits). */
    public static final float SEAT_HEIGHT = 0.16f;

    /** Lines are looked up per world column, so keep them (a line is small). */
    private static final ConcurrentHashMap<AnomalySite, GondolaLine> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 256;

    public final int anchorX;
    public final int anchorZ;
    public final int facing;
    public final long salt;
    /** The direction the line climbs away from the station, and the direction of +s. */
    public final Direction dir;
    public final Direction right;
    /** Number of real towers (the station frame is tower 0 and isn't counted). */
    public final int towers;
    /** Tower k's column (local a); index 0 is the station frame. */
    private final int[] towerA;
    /** Height of the cable at tower k (block y; its head block is one lower). */
    private final int[] cableY;

    public GondolaLine(int anchorX, int anchorZ, int facing, long salt) {
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        this.facing = facing;
        this.salt = salt;
        this.dir = Direction.from2DDataValue(facing);
        this.right = dir.getClockWise();
        this.towers = towerCount(salt);
        this.towerA = new int[towers + 1];
        this.cableY = new int[towers + 1];
        towerA[0] = FRAME_A;
        cableY[0] = STATION_CABLE_Y;
        int top = GROUND_Y + 1 + MIN_TOP + Hash.range(Hash.mix(salt ^ 0x70FL), MAX_TOP - MIN_TOP + 1);
        for (int k = 1; k <= towers; k++) {
            towerA[k] = towerA[k - 1] + spacing(salt, k);
            double share = Math.pow(k / (double) towers, CLIMB_CURVE);
            int y = STATION_CABLE_Y + (int) Math.round((top - STATION_CABLE_Y) * share);
            cableY[k] = Math.max(y, cableY[k - 1] + 2); // always taller than the one before
        }
    }

    /** The line of a gondola site (cached; no allocation once known). */
    public static GondolaLine of(AnomalySite site) {
        GondolaLine line = CACHE.get(site);
        if (line == null) {
            if (CACHE.size() > CACHE_LIMIT) {
                CACHE.clear();
            }
            line = new GondolaLine(site.anchorX(), site.anchorZ(), site.facing(), site.salt());
            CACHE.put(site, line);
        }
        return line;
    }

    // ==========================================================================================
    // Planning helpers (used by the Level 9 anomaly plan)
    // ==========================================================================================

    private static int towerCount(long salt) {
        return MIN_TOWERS + Hash.range(Hash.mix(salt ^ 0x70EL), MAX_TOWERS - MIN_TOWERS + 1);
    }

    private static int spacing(long salt, int k) {
        return MIN_SPACING + Hash.range(Hash.mix(salt + 0x9E3779B97F4A7C15L * k), MAX_SPACING - MIN_SPACING + 1);
    }

    /**
     * The site for a line whose bull wheel stands at (anchorX, anchorZ), climbing toward
     * {@code facing}: a long narrow rectangle covering the station and every tower (not the
     * cable's run-out past the last tower, which has no blocks).
     */
    public static AnomalySite site(int anchorX, int anchorZ, int facing, long salt) {
        int towers = towerCount(salt);
        int end = FRAME_A;
        for (int k = 1; k <= towers; k++) {
            end += spacing(salt, k);
        }
        end += 2; // the last tower's legs
        Direction d = Direction.from2DDataValue(facing);
        Direction r = d.getClockWise();
        int x0 = anchorX + -SITE_BACK * d.getStepX() + -SITE_HALF * r.getStepX();
        int z0 = anchorZ + -SITE_BACK * d.getStepZ() + -SITE_HALF * r.getStepZ();
        int x1 = anchorX + end * d.getStepX() + SITE_HALF * r.getStepX();
        int z1 = anchorZ + end * d.getStepZ() + SITE_HALF * r.getStepZ();
        return new AnomalySite(Level9Sites.GONDOLA, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1),
                anchorX, anchorZ, facing, salt);
    }

    // ==========================================================================================
    // Local <-> world
    // ==========================================================================================

    public int localA(int x, int z) {
        return (x - anchorX) * dir.getStepX() + (z - anchorZ) * dir.getStepZ();
    }

    public int localS(int x, int z) {
        return (x - anchorX) * right.getStepX() + (z - anchorZ) * right.getStepZ();
    }

    public double localA(double x, double z) {
        return (x - (anchorX + 0.5)) * dir.getStepX() + (z - (anchorZ + 0.5)) * dir.getStepZ();
    }

    public double localS(double x, double z) {
        return (x - (anchorX + 0.5)) * right.getStepX() + (z - (anchorZ + 0.5)) * right.getStepZ();
    }

    /** World x of the column (a, s). */
    public int blockX(int a, int s) {
        return anchorX + a * dir.getStepX() + s * right.getStepX();
    }

    public int blockZ(int a, int s) {
        return anchorZ + a * dir.getStepZ() + s * right.getStepZ();
    }

    /** World x of a continuous local point (column centres at whole numbers). */
    public double worldX(double a, double s) {
        return anchorX + 0.5 + a * dir.getStepX() + s * right.getStepX();
    }

    public double worldZ(double a, double s) {
        return anchorZ + 0.5 + a * dir.getStepZ() + s * right.getStepZ();
    }

    // ==========================================================================================
    // Towers and cable
    // ==========================================================================================

    /** Column of tower k (0 = the station frame, 1..towers = the towers). */
    public int towerA(int k) {
        return towerA[k];
    }

    /** Cable height at tower k (block y, exact). */
    public int towerCableY(int k) {
        return cableY[k];
    }

    /** y of tower k's head block (the crossarm with the sheaves). */
    public int towerHeadY(int k) {
        return cableY[k] - CABLE_ABOVE_HEAD;
    }

    /** Where the cable ends in the fog, past the last tower (local a). */
    public double endA() {
        return towerA[towers] + RUN_OUT;
    }

    /**
     * Height of the cable at local a (both lines are equally high): level over the station, then
     * from tower to tower with a little sag in each span, and past the last tower on up at the
     * last span's slope.
     */
    public double cableY(double a) {
        if (a <= FRAME_A) {
            return STATION_CABLE_Y;
        }
        for (int k = 1; k <= towers; k++) {
            if (a <= towerA[k]) {
                double span = towerA[k] - towerA[k - 1];
                double u = (a - towerA[k - 1]) / span;
                double sag = SAG_BASE + SAG_PER_BLOCK * span;
                return cableY[k - 1] + (cableY[k] - cableY[k - 1]) * u - sag * 4.0 * u * (1.0 - u);
            }
        }
        double slope = (cableY[towers] - cableY[towers - 1]) / (double) (towerA[towers] - towerA[towers - 1]);
        return cableY[towers] + slope * (a - towerA[towers]);
    }

    /** Index of the span a lies in (0 = the station, k = between tower k-1 and k, towers+1 = past the last). */
    public int spanAt(double a) {
        if (a <= FRAME_A) {
            return 0;
        }
        for (int k = 1; k <= towers; k++) {
            if (a <= towerA[k]) {
                return k;
            }
        }
        return towers + 1;
    }

    // ==========================================================================================
    // The station chair and the ride
    // ==========================================================================================

    /** Where the boarding chair block stands. */
    public BlockPos stationChairPos() {
        return new BlockPos(blockX(CHAIR_A, CHAIR_S), CHAIR_Y, blockZ(CHAIR_A, CHAIR_S));
    }

    /** How far along the line (blocks) the seat has travelled after {@code ticks}: easing in, then steady. */
    public static double rideDistance(double ticks) {
        double perTick = RIDE_SPEED / 20.0;
        double t = Math.max(0.0, ticks);
        if (t < RIDE_ACCEL_TICKS) {
            return perTick * t * t / (2.0 * RIDE_ACCEL_TICKS);
        }
        return perTick * (t - RIDE_ACCEL_TICKS / 2.0);
    }

    /** Local a of the riding seat after {@code ticks}. */
    public double rideA(double ticks) {
        return CHAIR_A + rideDistance(ticks);
    }

    /** World position of the seat entity (bottom centre of the chair, hanging two blocks under the cable). */
    public double rideX(double ticks) {
        return worldX(rideA(ticks), LINE_HALF);
    }

    public double rideY(double ticks) {
        return cableY(rideA(ticks)) - 2.0;
    }

    public double rideZ(double ticks) {
        return worldZ(rideA(ticks), LINE_HALF);
    }

    /**
     * Where in Level 39 the ride comes out (x and z), always the same for the same line. Kept
     * within +-65536 of the origin: Level 39's far-out region (|x| of a million and more) is
     * reserved for something else.
     */
    public int arrivalX() {
        return (int) (Math.floorMod(salt, 131072L) - 65536L);
    }

    public int arrivalZ() {
        return (int) (Math.floorMod(Hash.mix(salt ^ 0x39L), 131072L) - 65536L);
    }

    /** Distance (blocks, sideways) from (x, z) to the nearest point of the line's footprint. */
    public double distanceTo(double x, double z) {
        double a = localA(x, z);
        double s = localS(x, z);
        double da = a < DECK_A0 ? DECK_A0 - a : a > towerA[towers] ? a - towerA[towers] : 0.0;
        double ds = Math.max(0.0, Math.abs(s) - LINE_HALF);
        return Math.sqrt(da * da + ds * ds);
    }

    /** The nearest local a on the line to (x, z), clamped to the station .. last tower. */
    public double nearestA(double x, double z) {
        return Mth.clamp(localA(x, z), DECK_A0, towerA[towers]);
    }
}
