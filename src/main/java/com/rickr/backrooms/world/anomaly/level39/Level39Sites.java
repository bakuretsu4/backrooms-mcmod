package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level39Layout;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * The shapes of Level 39's anomalous areas, worked out from an {@link AnomalySite} alone.
 *
 * <p>The server plan ({@code world/layout/Level39AnomalyPlan}) decides where the sites are, the
 * server behaviours make them act, and the client draws their effects. All three must agree on
 * where a facade window is or how tall a house is; keeping that maths here (pure functions of the
 * site) means there is exactly one copy of it, usable on both sides.
 *
 * <p>Canon sources (Fandom, Level 39 by SherlsF): the Window entities (Entity 2) infesting the
 * superstructure, the house anomalies (inverted, merged, cut, floating, odd furniture, bigger on
 * the inside, flooded rooms, vanishing doors), screams from houses that are hallucinations,
 * "if sounds come from the holes, run", the Hydrolitis plague in stagnant water, and the M.E.G.
 * teams that survey the town.
 */
public final class Level39Sites {
    // ---- Kinds -----------------------------------------------------------------------------------
    /** A stretch of superstructure facade whose ground-storey windows house Window entities. */
    public static final String HOSTILE_WINDOWS = "level_39/hostile_windows";
    /** A house whose doors are walls once you have walked through and looked back. */
    public static final String VANISHING_DOOR = "level_39/vanishing_door";
    /** A house whose front door leads into a far larger interior. */
    public static final String BIGGER_INSIDE = "level_39/bigger_inside";
    /** An ordinary-looking house that screams at you (only you hear it, and it isn't real). */
    public static final String HOUSE_SCREAMS = "level_39/house_screams";
    /** A hole down to the tunnels: when it makes noises, a Hound is coming up. */
    public static final String HOLE_SOUNDS = "level_39/hole_sounds";
    /** A stagnant tunnel run: its water carries the Hydrolitis plague. */
    public static final String HYDROLITIS = "level_39/hydrolitis";
    /** An abandoned M.E.G. survey camp: notes to read and a little almond water. */
    public static final String MEG_CAMP = "level_39/meg_camp";
    // Houses whose anomaly is all in their blocks (built by Level39Houses); sites only so they can
    // be found with /backrooms anomaly locate.
    public static final String INVERTED_HOUSE = "level_39/inverted_house";
    public static final String MERGED_HOUSE = "level_39/merged_house";
    public static final String CUT_HOUSE = "level_39/cut_house";
    public static final String FLOATING_HOUSE = "level_39/floating_house";
    public static final String ODD_FURNITURE = "level_39/odd_furniture";
    public static final String FLOODED_ROOM = "level_39/flooded_room";

    public static final List<String> KINDS = List.of(HOSTILE_WINDOWS, VANISHING_DOOR, BIGGER_INSIDE, HOUSE_SCREAMS,
            HOLE_SOUNDS, HYDROLITIS, MEG_CAMP, INVERTED_HOUSE, MERGED_HOUSE, CUT_HOUSE, FLOATING_HOUSE,
            ODD_FURNITURE, FLOODED_ROOM);

    // ---- The facade --------------------------------------------------------------------------------
    /** |local| of the glass blocks (depth 1 into the band). */
    public static final int GLASS_LOCAL = Level39Layout.TOWN_HALF + 1;
    /** A windows site reaches this far into the town from the glass (blocks). */
    public static final int STRIP_DEPTH = 15;
    /** Facade sites are this long (blocks along the wall; 8 bays). */
    public static final int SEGMENT = 48;
    /** The first and last facade column a site may cover (the corners stay out of it). */
    public static final int ALONG_MIN = -432;
    public static final int SEGMENTS_PER_SIDE = 18;
    /** Glass rows of storey k: from y {@code 75 + 7k}, five blocks tall. */
    public static final int GLASS_BOTTOM_Y = Level39Layout.GROUND_Y + 3;
    public static final int GLASS_HEIGHT = 5;
    /** Only the two lowest storeys' windows come for you (the rest are too far up to matter). */
    public static final int HUNTING_STOREYS = 2;

    private Level39Sites() {}

    /** World coordinate of the centre of the cell (town) that column {@code v} belongs to. */
    public static int cellOrigin(int v) {
        return Math.floorDiv(v + Level39Layout.CELL / 2, Level39Layout.CELL) * Level39Layout.CELL;
    }

    /** The direction a facade site faces (from the wall toward the town). */
    public static Direction facing(AnomalySite site) {
        return Direction.from2DDataValue(site.facing());
    }

    /** True for the east and west walls (glass plane across x, windows lined up along z). */
    public static boolean xSide(AnomalySite site) {
        return facing(site).getAxis() == Direction.Axis.X;
    }

    /**
     * The plane of the glass on the town side, as a world coordinate on the wall's normal axis (x
     * for east/west walls, z for north/south). Stored in the site's anchor.
     */
    public static int glassFace(AnomalySite site) {
        return xSide(site) ? site.anchorX() : site.anchorZ();
    }

    /** World x/z of the glass blocks themselves (one block behind the face, inside the band). */
    public static int glassColumn(AnomalySite site) {
        Direction f = facing(site);
        // The face is the town-side side of the glass block; the block lies behind it (away from town).
        int face = glassFace(site);
        return f.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? face : face - 1;
    }

    /** Origin of the "along" axis (the cell centre on that axis), so bays line up with the facade. */
    public static int alongOrigin(AnomalySite site) {
        // The anchor's along coordinate is the middle of the site, always well inside its town.
        return cellOrigin(xSide(site) ? site.anchorZ() : site.anchorX());
    }

    /** First and last world along-coordinate of the site. */
    public static int alongMin(AnomalySite site) {
        return xSide(site) ? site.minZ() : site.minX();
    }

    public static int alongMax(AnomalySite site) {
        return xSide(site) ? site.maxZ() : site.maxX();
    }

    /** The along-coordinate of a position (z on east/west walls, x on north/south walls). */
    public static double along(AnomalySite site, double x, double z) {
        return xSide(site) ? z : x;
    }

    /** How far (blocks) a position is in front of the glass: 0 at the glass, positive in town. */
    public static double inFront(AnomalySite site, double x, double z) {
        Direction f = facing(site);
        double c = f.getAxis() == Direction.Axis.X ? x : z;
        return (c - glassFace(site)) * f.getAxisDirection().getStep();
    }

    /** First bay index of the site (bays are numbered in local along coordinates, 6 blocks each). */
    public static int firstBay(AnomalySite site) {
        int lo = alongMin(site) - alongOrigin(site);
        return Math.floorDiv(lo - 2 + Level39Layout.BAY - 1, Level39Layout.BAY);
    }

    /** Last bay index whose whole window lies inside the site. */
    public static int lastBay(AnomalySite site) {
        int hi = alongMax(site) - alongOrigin(site);
        return Math.floorDiv(hi - 4, Level39Layout.BAY);
    }

    /** The bay containing along-coordinate {@code along}. */
    public static int bayAt(AnomalySite site, double along) {
        return (int) Math.floor((along - alongOrigin(site)) / Level39Layout.BAY);
    }

    /** World along-coordinate of the middle of bay {@code b}'s window (its 3 glass columns). */
    public static double bayCentre(AnomalySite site, int bay) {
        return alongOrigin(site) + bay * Level39Layout.BAY + 3.5;
    }

    /** The middle glass column of bay {@code b} (world along coordinate, block). */
    public static int bayColumn(AnomalySite site, int bay) {
        return alongOrigin(site) + bay * Level39Layout.BAY + 3;
    }

    /** Bottom of storey k's glass (y of its lowest glass block). */
    public static int glassBottom(int storey) {
        return GLASS_BOTTOM_Y + storey * Level39Layout.STOREY;
    }

    /** World x of a point on the facade: along {@code along}, {@code front} blocks in front of the glass. */
    public static double worldX(AnomalySite site, double along, double front) {
        Direction f = facing(site);
        return xSide(site) ? glassFace(site) + front * f.getAxisDirection().getStep() : along;
    }

    public static double worldZ(AnomalySite site, double along, double front) {
        Direction f = facing(site);
        return xSide(site) ? along : glassFace(site) + front * f.getAxisDirection().getStep();
    }

    // ---- Houses (screams, bigger inside, vanishing doors): salt packs the house's size -------------

    /** Packs what the client needs to know about a house into a site salt. */
    public static long houseSalt(int baseY, int width, int depth, int storeys, int id) {
        return (baseY & 0x1FFL) | (long) (width & 0xFF) << 9 | (long) (depth & 0xFF) << 17
                | (long) (storeys & 0xF) << 25 | (long) (id & 0xFFFF) << 29;
    }

    public static int houseBaseY(AnomalySite site) {
        return (int) (site.salt() & 0x1FF);
    }

    public static int houseWidth(AnomalySite site) {
        return (int) (site.salt() >> 9 & 0xFF);
    }

    public static int houseDepth(AnomalySite site) {
        return (int) (site.salt() >> 17 & 0xFF);
    }

    public static int houseStoreys(AnomalySite site) {
        return (int) (site.salt() >> 25 & 0xF);
    }

    public static int houseId(AnomalySite site) {
        return (int) (site.salt() >> 29 & 0xFFFF);
    }

    // ---- Holes ------------------------------------------------------------------------------------------

    /** Hole sites keep the hole's radius (tenths of a block) in the salt. */
    public static double holeRadius(AnomalySite site) {
        return (site.salt() & 0xFFF) / 10.0;
    }
}
