package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pit room: Level 0's "Hole Variation" (https://backrooms-wiki.wikidot.com/level-0: pitch-black
 * pits in grid clusters, light reaches only a few feet down) as in Kane Pixels' "Pitfalls", where
 * the floor of a room gives way to a grid of carpeted vertical shafts
 * (https://kane-pixels-backrooms.fandom.com/wiki/The_Backrooms) and Marvin, falling in, lands
 * somewhere else entirely.
 *
 * <p>A 15x15 room with a doorway in the middle of each wall. Inside, a one-block ledge runs round
 * the walls, and the rest of the floor is a grid of 2x2 shafts with one-block carpet ridges between
 * them (the doorways line up with the middle ridges, so you can cross on them if you're careful):
 * <pre>
 *   L L L L L L L L L L L L L      L ledge, = ridge, # shaft
 *   L # # = # # = # # = # # L
 *   L # # = # # = # # = # # L
 *   L = = = = = = = = = = = L
 *   ...
 * </pre>
 * The shafts are carpeted all the way down and have a floor at {@link #SHAFT_BOTTOM}, above the
 * height where falling would take you on to Level 1. Falling in is handled by the server
 * ({@code Level0Anomalies}): the shaft seems to go on and on, then you land, hurt, far away.
 */
public final class Level0Pitfalls {
    public static final int BOX = 15;
    public static final int MOAT = 2;
    /** Y of the carpet at the bottom of the shafts (Level 0 sends you to Level 1 below y30). */
    public static final int SHAFT_BOTTOM = 31;

    // What one column is.
    private static final int MOAT_FLOOR = 0;
    private static final int WALL = 1;
    private static final int DOORWAY = 2;
    private static final int LEDGE = 3;
    private static final int SHAFT = 4;

    private Level0Pitfalls() {}

    private static int role(AnomalySite site, int x, int z) {
        int i = x - site.minX() - MOAT;
        int j = z - site.minZ() - MOAT;
        if (i < 0 || i >= BOX || j < 0 || j >= BOX) {
            return MOAT_FLOOR;
        }
        if (i == 0 || i == BOX - 1 || j == 0 || j == BOX - 1) {
            int along = i == 0 || i == BOX - 1 ? j : i;
            boolean corner = (i == 0 || i == BOX - 1) && (j == 0 || j == BOX - 1);
            return !corner && along >= 6 && along <= 8 ? DOORWAY : WALL;
        }
        int p = i - 1;
        int q = j - 1;
        if (p == 0 || p == BOX - 3 || q == 0 || q == BOX - 3 || p % 3 == 0 || q % 3 == 0) {
            return LEDGE; // the ledge round the walls and the ridges between the shafts
        }
        return SHAFT;
    }

    /** True above one of the shafts (nothing to stand on). */
    public static boolean isShaft(AnomalySite site, int x, int z) {
        return role(site, x, z) == SHAFT;
    }

    static boolean isSolid(AnomalySite site, int x, int z) {
        return role(site, x, z) == WALL;
    }

    static boolean isWalkable(AnomalySite site, int x, int z) {
        int r = role(site, x, z);
        return r != WALL && r != SHAFT;
    }

    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        BlockState carpet = ModBlocks.LOBBY_CARPET.get().defaultBlockState();
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        int i = x - site.minX() - MOAT;
        int j = z - site.minZ() - MOAT;
        out.set(ceiling, tile);
        switch (role(site, x, z)) {
            case WALL -> {
                out.set(floor, carpet);
                out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                }
                fillBelow(floor, carpet, out);
            }
            case DOORWAY -> {
                out.set(floor, carpet);
                out.set(ceiling - 1, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
            }
            case LEDGE -> {
                out.set(floor, carpet);
                fillBelow(floor, carpet, out); // the carpeted sides of the shafts next to it
                int p = i - 1;
                int q = j - 1;
                // Four small panels over the ridge crossings: enough light to see the grid, not the bottom.
                if ((p == 3 || p == 9) && (q == 3 || q == 9)) {
                    out.set(ceiling, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState());
                }
            }
            case SHAFT -> out.set(SHAFT_BOTTOM, carpet);
            default -> out.set(floor, carpet);
        }
    }

    /** Solid carpet from the floor down to the bottom of the shafts. */
    private static void fillBelow(int floor, BlockState carpet, LevelLayout.Column out) {
        for (int y = SHAFT_BOTTOM; y < floor; y++) {
            out.set(y, carpet);
        }
    }
}
