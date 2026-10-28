package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The pit room: Level 0's "Hole Variation" (https://backrooms-wiki.wikidot.com/level-0: pitch-black
 * pits in grid clusters, light reaches only a few feet down) built like Room 14D in Kane Pixels'
 * "Pitfalls": a big evenly lit room whose floor is a grid of square, carpet-lined shafts, with
 * narrow carpeted walkways between them and a plain wooden door on the far wall. Marvin, falling
 * in, lands somewhere else entirely.
 *
 * <p>A 19x19 room. Inside, one-block walkways run round the walls and every fourth block across
 * the room, so the floor is a 4x4 grid of 3x3 shafts:
 * <pre>
 *   = = = = = = = = = = = = = = = = =      = walkway, # shaft
 *   = # # # = # # # = # # # = # # # =
 *   = # # # = # # # = # # # = # # # =
 *   = # # # = # # # = # # # = # # # =
 *   = = = = = = = = = = = = = = = = =
 *   ...
 * </pre>
 * Three walls have a wide open doorway in the middle; the fourth (picked per room) has the wooden
 * door instead. The doorways open onto the walkway that runs along the wall, and the middle
 * walkway lines up with them, so you can cross the room on it if you're careful.
 *
 * <p>The shafts are carpeted for the first two blocks down and then turn lightless black (so from
 * the edge each one reads as a square of nothing, "light reaches only a few feet down"), and have
 * a floor at {@link #SHAFT_BOTTOM}, above the height where falling would take you on to Level 1.
 * Falling in is handled by the server ({@code PitfallsBehavior}): the shaft seems to go on and on,
 * then you land, hurt, far away. Before you even see the room you hear cold air breathing up out
 * of the shafts (client, {@code PitfallsEffect}).
 */
public final class Level0Pitfalls {
    public static final int BOX = 19;
    public static final int MOAT = 2;
    /** Y of the black bottom of the shafts (Level 0 sends you to Level 1 below y30). */
    public static final int SHAFT_BOTTOM = 31;
    /** How far down (blocks below the floor) the carpet lining goes before the black starts. */
    private static final int LINED = 2;
    /** Walkways every this many blocks (so the shafts are one less across). */
    private static final int PITCH = 4;

    // What one column is.
    private static final int MOAT_FLOOR = 0;
    private static final int WALL = 1;
    private static final int DOORWAY = 2;
    private static final int LEDGE = 3;
    private static final int SHAFT = 4;
    private static final int DOOR = 5;

    private Level0Pitfalls() {}

    private static int role(AnomalySite site, int x, int z) {
        int i = x - site.minX() - MOAT;
        int j = z - site.minZ() - MOAT;
        if (i < 0 || i >= BOX || j < 0 || j >= BOX) {
            return MOAT_FLOOR;
        }
        if (i == 0 || i == BOX - 1 || j == 0 || j == BOX - 1) {
            boolean corner = (i == 0 || i == BOX - 1) && (j == 0 || j == BOX - 1);
            if (corner) {
                return WALL;
            }
            int along = i == 0 || i == BOX - 1 ? j : i;
            int middle = BOX / 2;
            if (wallSide(i, j) == doorSide(site)) {
                return along == middle ? DOOR : WALL;
            }
            return Math.abs(along - middle) <= 1 ? DOORWAY : WALL;
        }
        int p = i - 1;
        int q = j - 1;
        return p % PITCH == 0 || q % PITCH == 0 ? LEDGE : SHAFT;
    }

    /** Which wall (as a direction out of the room) a wall column (i, j) belongs to. */
    private static Direction wallSide(int i, int j) {
        if (i == 0) return Direction.WEST;
        if (i == BOX - 1) return Direction.EAST;
        if (j == 0) return Direction.NORTH;
        return Direction.SOUTH;
    }

    /** The wall with the wooden door: random per room, but fixed by the site. */
    private static Direction doorSide(AnomalySite site) {
        return Direction.from2DDataValue((int) (site.salt() >>> 59) & 3);
    }

    /** True above one of the shafts (nothing to stand on). */
    public static boolean isShaft(AnomalySite site, int x, int z) {
        return role(site, x, z) == SHAFT;
    }

    /**
     * A test point for /backrooms anomaly visit: {x, feetY, z, lookX, lookZ}, or null. "edge"
     * stands on the walkway just inside an open doorway (never the wooden door's wall), right at
     * the shafts, looking along the middle walkway across the room: shafts on both sides.
     */
    @org.jetbrains.annotations.Nullable
    static double[] visit(AnomalySite site, String name, int floorY) {
        if (!name.equals("edge")) {
            return null;
        }
        Direction wall = doorSide(site) == Direction.NORTH ? Direction.SOUTH : Direction.NORTH;
        int middle = BOX / 2;
        int i = middle;
        int j = wall == Direction.NORTH ? 1 : BOX - 2;
        double x = site.minX() + MOAT + i + 0.5;
        double z = site.minZ() + MOAT + j + 0.5;
        double look = wall == Direction.NORTH ? 4.0 : -4.0;
        return new double[] {x, floorY + 1.0, z, x, z + look};
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
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        int i = x - site.minX() - MOAT;
        int j = z - site.minZ() - MOAT;
        out.set(ceiling, tile);
        switch (role(site, x, z)) {
            case WALL -> {
                out.set(floor, carpet);
                out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, wallpaper);
                }
                fillBelow(floor, carpet, out);
            }
            case DOOR -> {
                // Room 14D's plain wooden door, shut, in the middle of the far wall.
                out.set(floor, carpet);
                BlockState door = ModBlocks.LOBBY_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, doorSide(site))
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                        .setValue(DoorBlock.OPEN, false);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                for (int y = floor + 3; y < ceiling; y++) {
                    out.set(y, wallpaper);
                }
                fillBelow(floor, carpet, out);
            }
            case DOORWAY -> {
                // Frameless, like every Kane opening: no lintel, the ceiling just carries on.
                out.set(floor, carpet);
                fillBelow(floor, carpet, out);
            }
            case LEDGE -> {
                out.set(floor, carpet);
                fillBelow(floor, carpet, out); // the carpeted sides of the shafts next to it
                int p = i - 1;
                int q = j - 1;
                // A troffer over every inner walkway crossing (4 blocks apart): the room is as evenly
                // lit as the rest of the level, and the light still dies a few blocks down the shafts.
                if (p % PITCH == 0 && q % PITCH == 0 && p > 0 && q > 0 && p < BOX - 3 && q < BOX - 3) {
                    out.set(ceiling, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState());
                }
            }
            case SHAFT -> out.set(SHAFT_BOTTOM, ModBlocks.L2_VOID_BLACK.get().defaultBlockState());
            default -> out.set(floor, carpet);
        }
    }

    /** The sides of the shafts: carpet just under the floor, lightless black further down. */
    private static void fillBelow(int floor, BlockState carpet, LevelLayout.Column out) {
        BlockState black = ModBlocks.L2_VOID_BLACK.get().defaultBlockState();
        for (int y = SHAFT_BOTTOM; y < floor; y++) {
            out.set(y, y >= floor - LINED ? carpet : black);
        }
    }
}
