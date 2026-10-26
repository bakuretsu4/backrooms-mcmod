package com.rickr.backrooms.world.anomaly.level9;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level9Layout;
import net.minecraft.core.Direction;

/**
 * The shapes of Level 9's anomalous areas, worked out from an {@link AnomalySite} alone.
 *
 * <p>The layout builds the blocks, the server makes them behave and the client draws effects; all
 * three need to agree on where a walkway ends or where the back hall of a twin house is. Keeping
 * that maths here (as pure functions of the site) means there is exactly one copy of it. The
 * client never knows the world seed, but it does get the sites, so this works there too.
 *
 * <p>Lot coordinates, as in {@link Level9Layout}: {@code u} runs along the street with x (0..17),
 * {@code v} away from the street (0 = next to the sidewalk). A site's {@code facing} is the
 * direction of +v in the world ("inward", away from the street): SOUTH or NORTH.
 *
 * <p>Sources: https://backrooms-wiki.wikidot.com/level-9 (clipped and identical houses, the
 * walkway to a grass field) and http://backrooms-mghc.wikidot.com/level-9-1 (the Crimson Field,
 * the Gloom, the foxhole doors).
 */
public final class Level9Sites {
    // ---- Kinds ------------------------------------------------------------------------------------
    public static final String CLIPPED_HOUSES = "level_9/clipped_houses";
    public static final String TWIN_HOUSES = "level_9/twin_houses";
    public static final String GRASS_WALKWAY = "level_9/grass_walkway";
    /** The same walkway on the Level 9.1 side: walk back along it to its start to go home. */
    public static final String FIELD_WALKWAY = "level_9/field_walkway";
    public static final String FOXHOLE = "level_9/foxhole";
    /** Level 9.1 itself: big tiles covering the whole Crimson Field region. */
    public static final String CRIMSON_FIELD = "level_9/crimson_field";

    // ---- The Crimson Field region -------------------------------------------------------------------
    /**
     * Level 9.1 lives in the same dimension as the suburb, far to the east: every column from this
     * x on is endless grass field instead of suburb. Nobody walks a million blocks, so the two
     * never meet; the walkways are the only way across.
     */
    public static final int FIELD_MIN_X = 1_000_000;
    /**
     * A walkway at suburb x leads to the field at x + FIELD_DX (z stays the same), so every walkway
     * has its own spot in the field and the way back is simply the same shift the other way.
     * Walkways only exist where both ends make sense (suburb x from -1,000,000 up to the field).
     */
    public static final int FIELD_DX = 2_000_000;
    /** Size of the crimson_field tiles (blocks). */
    public static final int FIELD_TILE = 1024;

    // ---- Walkways ------------------------------------------------------------------------------------
    /** The walkway is 2 wide: columns anchorX - 1 and anchorX (lot u 8 and 9). */
    public static final int WALKWAY_PATH_U = 8;
    /** Radius of the grass clearing at its end. */
    public static final int CLEARING_RADIUS = 4;
    /** Standing this close to the middle of the clearing takes you across. */
    public static final double CROSS_DISTANCE = 1.6;
    /** On the field side: this close to the start of the path (or past it) takes you home. */
    public static final double RETURN_ALONG = 1.0;
    /** The field side has a scrap of sidewalk and a dead streetlamp in front of the path start. */
    public static final int FIELD_START_EXTRA = 6;

    // ---- Twin houses (both identical; lot coordinates) -----------------------------------------------
    public static final int TWIN_HU0 = 3;
    public static final int TWIN_WIDTH = 11;
    public static final int TWIN_DOOR_U = 8;
    public static final int TWIN_SETBACK = 5;
    public static final int TWIN_DEPTH = 11;
    /** First row of the front hall (just inside the front door) and of the back hall. */
    public static final int TWIN_FRONT_HALL_V = TWIN_SETBACK + 1;
    public static final int TWIN_BACK_HALL_V = TWIN_SETBACK + TWIN_DEPTH - 3;
    /**
     * Crossing this far into the back hall's first row (blocks) moves you to the other house: far
     * enough that your whole body is past the door frame, on both sides of the jump.
     */
    public static final double TWIN_SHIFT_LINE = 0.35;

    // ---- Clipped houses (canonical lot coordinates; mirrored per site) ----------------------------
    /** House A: walls u 1..10, v 5..13; front door at (4, 5). */
    public static final int CLIP_A_U0 = 1;
    public static final int CLIP_A_U1 = 10;
    public static final int CLIP_A_V0 = 5;
    public static final int CLIP_A_V1 = 13;
    public static final int CLIP_A_DOOR_U = 4;
    /** House B, pushed back and sideways into A: walls u 5..14, v 8..16; front door at (12, 8). */
    public static final int CLIP_B_U0 = 5;
    public static final int CLIP_B_U1 = 14;
    public static final int CLIP_B_V0 = 8;
    public static final int CLIP_B_V1 = 16;
    public static final int CLIP_B_DOOR_U = 12;

    // ---- Foxholes ------------------------------------------------------------------------------------
    /** Foxhole sites are 7x7 around the hatch. */
    public static final int FOXHOLE_RADIUS = 3;

    public static boolean inField(double x) {
        return x >= FIELD_MIN_X;
    }

    public static boolean inField(int x) {
        return x >= FIELD_MIN_X;
    }

    /** +v in the world for this site (SOUTH or NORTH). */
    public static Direction inward(AnomalySite site) {
        return Direction.from2DDataValue(site.facing());
    }

    private static boolean southward(AnomalySite site) {
        return site.facing() == Direction.SOUTH.get2DDataValue();
    }

    // ==========================================================================================
    // Walkways: anchor = (path's second column, middle of the clearing), salt = blocks from the lot
    // front to the middle of the clearing.
    // ==========================================================================================

    /** z of the lot's front edge row (v = 0): where the walkway leaves the sidewalk. */
    public static int walkwayFrontZ(AnomalySite site) {
        int length = (int) site.salt();
        return southward(site) ? site.anchorZ() - length : site.anchorZ() + length;
    }

    /** How far along the walkway (blocks, 0 at the sidewalk edge) the point (x, z) is. */
    public static double walkwayAlong(AnomalySite site, double z) {
        int front = walkwayFrontZ(site);
        return southward(site) ? z - front : front + 1 - z;
    }

    /** Length of the walkway, sidewalk edge to the middle of the clearing. */
    public static double walkwayLength(AnomalySite site) {
        return site.salt() + 0.5;
    }

    /** Middle of the clearing (world, block-edge x between the two path columns). */
    public static double clearingX(AnomalySite site) {
        return site.anchorX();
    }

    public static double clearingZ(AnomalySite site) {
        return site.anchorZ() + 0.5;
    }

    /** True for the two path columns. */
    public static boolean onWalkwayPath(AnomalySite site, int x) {
        return x == site.anchorX() - 1 || x == site.anchorX();
    }

    /** True for a column inside the grass clearing at the end of the walkway. */
    public static boolean inClearing(AnomalySite site, int x, int z) {
        double dx = x + 0.5 - clearingX(site);
        double dz = z + 0.5 - clearingZ(site);
        return dx * dx + dz * dz <= (CLEARING_RADIUS + 0.3) * (CLEARING_RADIUS + 0.3);
    }

    /** Standing in the middle of the clearing: across to Level 9.1. */
    public static boolean atCrossing(AnomalySite site, double x, double z) {
        double dx = x - clearingX(site);
        double dz = z - clearingZ(site);
        return dx * dx + dz * dz <= CROSS_DISTANCE * CROSS_DISTANCE;
    }

    /** Field side: at (or past) the start of the path, back where the sidewalk would be. */
    public static boolean atReturn(AnomalySite site, double x, double z) {
        return walkwayAlong(site, z) <= RETURN_ALONG && Math.abs(x - site.anchorX()) <= 2.5;
    }

    /** The field-side copy of a suburb walkway: shifted FIELD_DX east, with room for the sidewalk scrap. */
    public static AnomalySite toField(AnomalySite walkway) {
        boolean south = southward(walkway);
        return new AnomalySite(FIELD_WALKWAY,
                walkway.minX() + FIELD_DX, south ? walkway.minZ() - FIELD_START_EXTRA : walkway.minZ(),
                walkway.maxX() + FIELD_DX, south ? walkway.maxZ() : walkway.maxZ() + FIELD_START_EXTRA,
                walkway.anchorX() + FIELD_DX, walkway.anchorZ(), walkway.facing(), walkway.salt());
    }

    /** The suburb walkway a field walkway belongs to (inverse of {@link #toField}). */
    public static AnomalySite toSuburb(AnomalySite field) {
        boolean south = southward(field);
        return new AnomalySite(GRASS_WALKWAY,
                field.minX() - FIELD_DX, south ? field.minZ() + FIELD_START_EXTRA : field.minZ(),
                field.maxX() - FIELD_DX, south ? field.maxZ() : field.maxZ() - FIELD_START_EXTRA,
                field.anchorX() - FIELD_DX, field.anchorZ(), field.facing(), field.salt());
    }

    /** A point {@code along} blocks up the path (world z of it, block-centre precision). */
    public static double walkwayZAt(AnomalySite site, double along) {
        int front = walkwayFrontZ(site);
        return southward(site) ? front + along : front + 1 - along;
    }

    // ==========================================================================================
    // Twin houses: anchor = (x of lot A's u 0, z of the v = 0 row); house B is the next lot east.
    // ==========================================================================================

    /** World x of lot column u of house {@code house} (0 = A, 1 = B). */
    public static int twinX(AnomalySite site, int house, int u) {
        return site.anchorX() + house * Level9Layout.LW + u;
    }

    /** Which twin (0 or 1) world x falls in, or -1. */
    public static int twinHouseAt(AnomalySite site, double x) {
        double u = x - site.anchorX();
        if (u < 0 || u >= 2 * Level9Layout.LW) {
            return -1;
        }
        return u < Level9Layout.LW ? 0 : 1;
    }

    /** Fractional u inside its own lot (0..18). */
    public static double twinU(AnomalySite site, double x) {
        double u = x - site.anchorX();
        return u >= Level9Layout.LW ? u - Level9Layout.LW : u;
    }

    /** Fractional v of world z (block v covers [v, v + 1)). */
    public static double lotV(AnomalySite site, double z) {
        return southward(site) ? z - site.anchorZ() : site.anchorZ() + 1 - z;
    }

    /** Inside the back hall (or its front hall twin) across its whole width. */
    public static boolean inTwinHallColumns(double u) {
        return u >= TWIN_DOOR_U - 1 && u < TWIN_DOOR_U + 2;
    }

    // ==========================================================================================
    // Clipped houses: anchor = (x of the lot's u 0, z of its v = 0 row), salt bit 0 = mirrored.
    // ==========================================================================================

    public static boolean clipMirrored(AnomalySite site) {
        return (site.salt() & 1L) != 0;
    }

    /** Canonical u of world column x (the plan is drawn for the unmirrored lot). */
    public static int clipU(AnomalySite site, int x) {
        int u = x - site.anchorX();
        return clipMirrored(site) ? Level9Layout.LW - 1 - u : u;
    }

    public static int clipX(AnomalySite site, int cu) {
        return site.anchorX() + (clipMirrored(site) ? Level9Layout.LW - 1 - cu : cu);
    }

    public static int clipV(AnomalySite site, int z) {
        return southward(site) ? z - site.anchorZ() : site.anchorZ() - z;
    }

    public static int clipZ(AnomalySite site, int v) {
        return southward(site) ? site.anchorZ() + v : site.anchorZ() - v;
    }

    public static boolean clipInA(int cu, int v) {
        return cu >= CLIP_A_U0 && cu <= CLIP_A_U1 && v >= CLIP_A_V0 && v <= CLIP_A_V1;
    }

    public static boolean clipInB(int cu, int v) {
        return cu >= CLIP_B_U0 && cu <= CLIP_B_U1 && v >= CLIP_B_V0 && v <= CLIP_B_V1;
    }

    public static boolean clipWallA(int cu, int v) {
        return clipInA(cu, v) && (cu == CLIP_A_U0 || cu == CLIP_A_U1 || v == CLIP_A_V0 || v == CLIP_A_V1);
    }

    public static boolean clipWallB(int cu, int v) {
        return clipInB(cu, v) && (cu == CLIP_B_U0 || cu == CLIP_B_U1 || v == CLIP_B_V0 || v == CLIP_B_V1);
    }

    public static boolean clipInteriorA(int cu, int v) {
        return clipInA(cu, v) && !clipWallA(cu, v);
    }

    public static boolean clipInteriorB(int cu, int v) {
        return clipInB(cu, v) && !clipWallB(cu, v);
    }

    /**
     * Doorway-sized gaps where one house's wall runs through the other's rooms, so every pocket
     * the overlap makes can be walked into: one in B's front wall (into the shared middle) and one
     * in A's side wall (from the middle into the rest of B).
     */
    public static boolean clipGap(int cu, int v) {
        return cu == 7 && v == CLIP_B_V0 || cu == CLIP_A_U1 && v == 11;
    }

    /**
     * A wall that has no business being where it is: one house's wall standing inside the other
     * house's room. These are the walls you can sometimes lean straight through.
     */
    public static boolean clipForeignWall(int cu, int v) {
        if (clipGap(cu, v)) {
            return false;
        }
        return clipWallB(cu, v) && clipInteriorA(cu, v) || clipWallA(cu, v) && clipInteriorB(cu, v);
    }

    /** Either house's room (not a wall): somewhere you can stand inside. */
    public static boolean clipRoom(int cu, int v) {
        boolean wall = (clipWallA(cu, v) || clipWallB(cu, v)) && !clipGap(cu, v);
        return !wall && (clipInA(cu, v) || clipInB(cu, v));
    }

    // ==========================================================================================
    // Foxholes: anchor = the hatch, facing = the side of the sandbag ring that has the gap.
    // ==========================================================================================

    public static boolean isFoxholeHatch(AnomalySite site, int x, int z) {
        return x == site.anchorX() && z == site.anchorZ();
    }

    private Level9Sites() {}
}
