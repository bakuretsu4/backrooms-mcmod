package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * The shapes of Level 2's anomalous areas, shared by the three places that need them: the layout
 * (which builds the blocks), the server behaviours (which teleport, wrap and hurt) and the client
 * effects (which play sounds and puff steam). Everything here is plain arithmetic on an
 * {@link AnomalySite}, so all three always agree, and the client doesn't need the seed.
 *
 * <p>Sources: https://backrooms-wiki.wikidot.com/level-2 (current page: series circuits, the
 * Blackout, void rooms, looping hallways) and https://backrooms-wiki.wikidot.com/archived:level-2
 * (heat near the loudest machinery at the ends of hallways).
 */
public final class Level2Sites {
    public static final String BROWNOUT = "level_2/brownout";
    public static final String HOT_ZONE = "level_2/hot_zone";
    public static final String VOID_DOOR = "level_2/void_door";
    public static final String LOOP_HALL = "level_2/loop_hall";
    // The quiet architectural anomalies ("oddities", planned in world/layout/Level2Oddities): nothing
    // attacks you, the building is simply wrong in a way you notice only if you look.
    /** A valve gallery where every quarter-turn valve has been turned the same wrong way (all shut), yet water rushes. */
    public static final String WRONG_VALVES = "level_2/wrong_valves";
    /** A painted pipe goes into one corridor wall and comes out of the opposite wall upside down. */
    public static final String INVERTED_PIPE = "level_2/inverted_pipe";
    /** Four corridor sections in a row stencilled with the same section number. */
    public static final String REPEAT_SECTION = "level_2/repeat_section";
    /** A steel door that opens onto bare concrete, warm to stand near. */
    public static final String WARM_DOOR = "level_2/warm_door";
    /** A pressure gauge on the wall whose needle follows your heartbeat. */
    public static final String HEART_GAUGE = "level_2/heart_gauge";
    /** The oddities, in the order the planner rolls them. */
    public static final List<String> ODDITIES = List.of(WRONG_VALVES, INVERTED_PIPE, REPEAT_SECTION, WARM_DOOR, HEART_GAUGE);
    public static final List<String> KINDS = List.of(BROWNOUT, HOT_ZONE, VOID_DOOR, LOOP_HALL,
            WRONG_VALVES, INVERTED_PIPE, REPEAT_SECTION, WARM_DOOR, HEART_GAUGE);

    /** The level floor (y40); the walkway is the layer above it. */
    public static final int FLOOR_Y = LevelLayout.FLOOR_Y;

    // ---- Void room -----------------------------------------------------------------------------
    /**
     * The void lies this far below the level: its floor is at y10, its black ceiling at y39 (just
     * under the level's floor), so it is 28 blocks of empty dark. Walking into the void door's
     * passage drops you this far, onto an exact copy of the passage.
     */
    public static final int VOID_DROP = 30;
    /**
     * The void repeats every this many blocks each way: crossing an edge quietly moves you back by
     * this much. Small enough that you are never more than ~28 blocks from the doorway's lamp,
     * the furthest a fixture still shines on HIGH quality (ClientSettings.Quality#lightRange): the lit
     * doorway stays in sight from anywhere in front of it.
     */
    public static final int VOID_WRAP = 40;
    /** The black floor goes on this far past the wrap edge, so no torch beam ever finds an edge. */
    public static final int VOID_MARGIN = 12;
    /** Half the width of the void's square of columns (and so of the site), around the door. */
    public static final int VOID_HALF = VOID_WRAP / 2 + VOID_MARGIN;

    // ---- Looping hallway -----------------------------------------------------------------------
    /**
     * The hallway behind the loop door runs this far above the level, over the tunnel ceilings
     * (floor y46, ceiling y51). Stepping into its little entrance passage lifts you onto an exact
     * copy of that passage up there.
     */
    public static final int LOOP_RISE = 6;
    /**
     * Walking this far along the hallway brings you back past the door again (you are moved back
     * by this much once you are half of it away from the door, so you are always near the real one).
     */
    public static final int LOOP_PERIOD = 32;
    /**
     * The hallway's floor runs from -LOOP_HALF to +LOOP_HALF beside the door (its end walls one
     * further): far past the point where you are moved back, so the view down it never changes.
     * Copies of the door's passage open off it every LOOP_PERIOD blocks, to be seen, not reached.
     */
    public static final int LOOP_HALF = 63;
    /** A ceiling light every this many blocks along the hallway (one right in front of the door). */
    public static final int LOOP_LIGHT_SPACING = 16;

    // ---- Shared door geometry ------------------------------------------------------------------
    /** The copy of the tunnel behind a void/loop door reaches this far to each side of the door. */
    public static final int STUB_HALF = 4;
    /** The lamp beside a void/loop door hangs this far to the side of it (on the copy too). */
    public static final int STUB_LAMP_LAT = 2;

    // ---- Hot zone room (u = depth from the dead end's node, v = across, 1 = middle) ------------
    public static final int HOT_LENGTH = 10;

    /**
     * A local frame on the ground: "depth" runs along {@code facing} from the anchor, "lat" across
     * it to the right (clockwise). For doors the anchor is the door column and facing points from
     * the tunnel into the rock behind the door.
     */
    public record Frame(int ax, int az, Direction facing) {
        public static Frame of(AnomalySite site) {
            return new Frame(site.anchorX(), site.anchorZ(), Direction.from2DDataValue(site.facing()));
        }

        /**
         * The hot zone frame: origin at the dead end's node corner, so depth (u) 0..2 and lat (v)
         * 0..2 are the node itself. The site's anchor is the middle of the room (u 5, v 1).
         */
        public static Frame hotZone(AnomalySite site) {
            Direction f = Direction.from2DDataValue(site.facing());
            Direction r = f.getClockWise();
            return new Frame(site.anchorX() - 5 * f.getStepX() - r.getStepX(),
                    site.anchorZ() - 5 * f.getStepZ() - r.getStepZ(), f);
        }

        public Direction right() {
            return facing.getClockWise();
        }

        public int depth(int x, int z) {
            return (x - ax) * facing.getStepX() + (z - az) * facing.getStepZ();
        }

        public int lat(int x, int z) {
            Direction r = right();
            return (x - ax) * r.getStepX() + (z - az) * r.getStepZ();
        }

        /** Exact depth of a point, measured from the middle of the anchor column. */
        public double depth(double x, double z) {
            return (x - ax - 0.5) * facing.getStepX() + (z - az - 0.5) * facing.getStepZ();
        }

        /** Exact lat of a point, measured from the middle of the anchor column. */
        public double lat(double x, double z) {
            Direction r = right();
            return (x - ax - 0.5) * r.getStepX() + (z - az - 0.5) * r.getStepZ();
        }

        public int x(int depth, int lat) {
            return ax + depth * facing.getStepX() + lat * right().getStepX();
        }

        public int z(int depth, int lat) {
            return az + depth * facing.getStepZ() + lat * right().getStepZ();
        }

        /** World x offset of a move by (depth, lat). */
        public double dx(double depth, double lat) {
            return depth * facing.getStepX() + lat * right().getStepX();
        }

        /** World z offset of a move by (depth, lat). */
        public double dz(double depth, double lat) {
            return depth * facing.getStepZ() + lat * right().getStepZ();
        }
    }

    /** One steam vent in a hot zone: its block, and the way the steam blows out of it. */
    public record Vent(int x, int y, int z, Direction facing) {}

    /**
     * The steam vents of a hot zone, high on the machines (layer y44): two on each side wall and
     * one over the big machine at the back. The layout puts a vent block at each, the client
     * blows steam out of them.
     */
    public static Vent[] vents(AnomalySite site) {
        Frame f = Frame.hotZone(site);
        Direction r = f.right();
        int y = FLOOR_Y + 4;
        return new Vent[] {
                new Vent(f.x(4, -1), y, f.z(4, -1), r),
                new Vent(f.x(7, -1), y, f.z(7, -1), r),
                new Vent(f.x(5, 3), y, f.z(5, 3), r.getOpposite()),
                new Vent(f.x(8, 3), y, f.z(8, 3), r.getOpposite()),
                new Vent(f.x(9, 1), y, f.z(9, 1), f.facing().getOpposite()),
        };
    }

    /** True for the column of a vent (u, v in the hot zone's frame, see {@link Frame#hotZone}). */
    public static boolean isVent(int u, int v) {
        return (v == -1 && (u == 4 || u == 7)) || (v == 3 && (u == 5 || u == 8)) || (u == 9 && v == 1);
    }

    private Level2Sites() {}
}
