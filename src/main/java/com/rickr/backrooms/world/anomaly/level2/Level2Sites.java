package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.List;

/**
 * The names of Level 2's anomalies, shared by the layout (world/layout/Level2AnomalyPlan and
 * Level2Oddities, which place them), the server behaviours and the client effects. Level 2 is one
 * straight corridor with no way off it, so every anomaly lies in the corridor itself.
 *
 * <p>Sources: https://backrooms-wiki.wikidot.com/level-2 (current page: series circuits, the
 * Blackout).
 */
public final class Level2Sites {
    public static final String BROWNOUT = "level_2/brownout";
    // The quiet architectural anomalies ("oddities", planned in world/layout/Level2Oddities): nothing
    // attacks you, the building is simply wrong in a way you notice only if you look.
    /** A valve manifold in the corridor wall whose quarter-turn valves are all turned the same wrong way (shut), yet water rushes. */
    public static final String WRONG_VALVES = "level_2/wrong_valves";
    /** A painted pipe goes into one corridor wall and comes out of the opposite wall upside down. */
    public static final String INVERTED_PIPE = "level_2/inverted_pipe";
    /** Four corridor sections in a row stencilled with the same section number. */
    public static final String REPEAT_SECTION = "level_2/repeat_section";
    /** A pressure gauge on the wall whose needle follows your heartbeat. */
    public static final String HEART_GAUGE = "level_2/heart_gauge";
    /** The oddities, in the order the planner rolls them. */
    public static final List<String> ODDITIES = List.of(WRONG_VALVES, INVERTED_PIPE, REPEAT_SECTION, HEART_GAUGE);
    /** Every kind the layout places (what /backrooms anomaly locate lists). */
    public static final List<String> KINDS = List.of(BROWNOUT, WRONG_VALVES, INVERTED_PIPE, REPEAT_SECTION, HEART_GAUGE);

    /** The level floor (y40); the walkway is the layer above it. */
    public static final int FLOOR_Y = LevelLayout.FLOOR_Y;

    private Level2Sites() {}
}
