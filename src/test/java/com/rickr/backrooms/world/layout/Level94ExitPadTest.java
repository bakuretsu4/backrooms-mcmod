package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rickr.backrooms.world.BackroomsLevel;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Every Level 94 exit house (dark-door and flooded) keeps its own pad: flat ground at its floor
 * height all round it. A neighbour's pad flattened after it used to tilt that ground (a hilltop
 * house raising the garden in front of the dark door a block above its sill), so the door could
 * not be walked into.
 */
class Level94ExitPadTest {
    private static final int REGIONS = 6;
    private static final int EXAMPLES = 6;

    @Test
    void exitHousesKeepTheirPads() {
        StringBuilder bad = new StringBuilder();
        int houses = 0;
        int tilted = 0;
        for (long worldSeed : new long[] {1L, 42L, -7L, 0x5EEDL, 2024L, 777L}) {
            // Escape.layoutSeed (other package): the seed the escape tests give the level.
            long seed = new SplittableRandom(worldSeed * 31 + BackroomsLevel.LEVEL_94.ordinal()).nextLong();
            for (int ex = -REGIONS; ex <= REGIONS; ex++) {
                for (int ez = -REGIONS; ez <= REGIONS; ez++) {
                    Level94Builds.Thing t = Level94Plan.darkHouse(seed, ex, ez);
                    Level94Plan at = Level94Plan.of(seed, t.x, t.z);
                    int centre = at.index(t.x, t.z);
                    if (at.surf[centre] == Level94Plan.CASTLE || at.townOf[centre] >= 0) {
                        continue; // left where a castle or town draws instead: never built (Level94Plan.hasDarkDoor)
                    }
                    houses++;
                    int want = 2 * t.padHeight;
                    int[] p = t.pad;
                    String first = null;
                    for (int x = p[0]; x <= p[2] && first == null; x++) {
                        for (int z = p[1]; z <= p[3]; z++) {
                            Level94Plan plan = Level94Plan.of(seed, x, z);
                            int i = plan.index(x, z);
                            if (plan.top[i] != want) {
                                first = " ground at " + x + " " + z + " is " + plan.top[i] / 2.0 + " (surface " + plan.surf[i]
                                        + "), pad is " + t.padHeight;
                                break;
                            }
                        }
                    }
                    if (first != null && ++tilted <= EXAMPLES) {
                        bad.append("\n  layout seed ").append(seed).append(" region ").append(ex).append(' ').append(ez)
                                .append(t.house.kind == Level94Houses.DARK ? " dark" : " flooded").append(" house:").append(first);
                    }
                }
            }
        }
        assertEquals(0, tilted, "Level 94: " + tilted + " of " + houses + " exit houses stand on tilted ground (first "
                + EXAMPLES + "):" + bad);
    }
}
