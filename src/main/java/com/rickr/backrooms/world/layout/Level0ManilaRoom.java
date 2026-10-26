package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.VolunteerNoteBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The Manila Room: https://backrooms-wiki.wikidot.com/manila-room. An 8x8 room with thick walls,
 * hidden behind a brown wall with a wooden door: manila-beige wallpaper, wooden floorboards, an
 * octagonal table with two chairs, a door in each wall, and the cupboards and notes left by the
 * volunteers who keep it stocked. Near it the hum fades and a soft piano plays from nowhere (client
 * side); the interior never changes. Canon puts a Level 1 exit nearby: the planner picks a maze
 * wall a few blocks away that flickers (see {@link Level0AnomalyPlan#isManilaExit}).
 *
 * <p>Layout of the 12x12 box (local i, j from its north-west corner), walls two blocks thick: an
 * outer ring of brown wall with the four wooden doors, an inner ring of manila wallpaper, and the
 * 8x8 room inside (a, b = i - 2, j - 2):
 * <pre>
 *        b=0   K . N . . . . C      K locker, C supply cabinet, X crate, N notes on the wall
 *        b=1   . . . . . . . .
 *        b=2   . . . . . . . N
 *        b=3   . . h t t . . .      t the octagonal table (four quarters), h chairs
 *        b=4   . . . t t h . .
 *        b=5   N . . . . . . .
 *        b=6   . . . . . . . N
 *        b=7   K N . . . N . X
 * </pre>
 * Doors: north wall above a=4, south below a=3, west beside b=3, east beside b=4.
 */
public final class Level0ManilaRoom {
    public static final int BOX = 12;
    public static final int MOAT = 2;

    // What one column of the box is.
    private static final int MOAT_FLOOR = 0;
    private static final int BROWN_WALL = 1;
    private static final int DOOR = 2;
    private static final int MANILA_WALL = 3;
    private static final int INNER_DOORWAY = 4;
    private static final int ROOM = 5;

    private Level0ManilaRoom() {}

    private static int localI(AnomalySite site, int x) {
        return x - site.minX() - MOAT;
    }

    private static int localJ(AnomalySite site, int z) {
        return z - site.minZ() - MOAT;
    }

    private static int role(int i, int j) {
        if (i < 0 || i >= BOX || j < 0 || j >= BOX) {
            return MOAT_FLOOR;
        }
        boolean outer = i == 0 || i == BOX - 1 || j == 0 || j == BOX - 1;
        if (outer) {
            return doorSide(i, j) != null ? DOOR : BROWN_WALL;
        }
        boolean inner = i == 1 || i == BOX - 2 || j == 1 || j == BOX - 2;
        if (inner) {
            boolean doorway = (i == 6 && j == 1) || (i == 5 && j == BOX - 2) || (j == 5 && i == 1) || (j == 6 && i == BOX - 2);
            return doorway ? INNER_DOORWAY : MANILA_WALL;
        }
        return ROOM;
    }

    /** For a door column of the outer ring: the way out (the side the door is on); null otherwise. */
    @Nullable
    private static Direction doorSide(int i, int j) {
        if (j == 0 && i == 6) return Direction.NORTH;
        if (j == BOX - 1 && i == 5) return Direction.SOUTH;
        if (i == 0 && j == 5) return Direction.WEST;
        if (i == BOX - 1 && j == 6) return Direction.EAST;
        return null;
    }

    /** True inside the 8x8 room itself (not the walls or the moat). */
    public static boolean isInside(AnomalySite site, int x, int z) {
        return role(localI(site, x), localJ(site, z)) == ROOM;
    }

    /** World point {x, z} of the middle of the room (where the piano seems to come from). */
    public static double[] centre(AnomalySite site) {
        return new double[] {site.minX() + MOAT + BOX / 2.0, site.minZ() + MOAT + BOX / 2.0};
    }

    static boolean isSolid(AnomalySite site, int x, int z) {
        int r = role(localI(site, x), localJ(site, z));
        return r == BROWN_WALL || r == MANILA_WALL || r == DOOR;
    }

    static boolean isWalkable(AnomalySite site, int x, int z) {
        int i = localI(site, x);
        int j = localJ(site, z);
        int r = role(i, j);
        if (r == ROOM) {
            return !hasFurniture(i - 2, j - 2);
        }
        return r == MOAT_FLOOR || r == INNER_DOORWAY;
    }

    /** True if a piece of furniture stands on interior cell (a, b): the table, the chairs, the four corners. */
    private static boolean hasFurniture(int a, int b) {
        boolean table = a >= 3 && a <= 4 && b >= 3 && b <= 4;
        boolean chair = (a == 2 && b == 3) || (a == 5 && b == 4);
        boolean corner = (a == 0 || a == 7) && (b == 0 || b == 7);
        return table || chair || corner;
    }

    /** The piece of furniture standing on interior cell (a, b), or null for bare floor. */
    @Nullable
    private static BlockState furniture(int a, int b) {
        if (!hasFurniture(a, b)) {
            return null;
        }
        if (a >= 3 && a <= 4 && b >= 3 && b <= 4) {
            // Quarters of the octagonal table; each quarter's cut corner points away from the middle.
            Direction quarter = a == 3 ? (b == 3 ? Direction.NORTH : Direction.WEST) : (b == 3 ? Direction.EAST : Direction.SOUTH);
            return ModBlocks.OCTAGON_TABLE.get().defaultBlockState().setValue(FurnitureBlock.FACING, quarter);
        }
        if (a == 2 && b == 3) {
            return ModBlocks.WOODEN_CHAIR.get().defaultBlockState().setValue(FurnitureBlock.FACING, Direction.EAST);
        }
        if (a == 5 && b == 4) {
            return ModBlocks.WOODEN_CHAIR.get().defaultBlockState().setValue(FurnitureBlock.FACING, Direction.WEST);
        }
        if (a == 0 && b == 0) {
            return ModBlocks.SUPPLY_LOCKER.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, Direction.SOUTH);
        }
        if (a == 7 && b == 0) {
            return ModBlocks.SUPPLY_CABINET.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, Direction.SOUTH);
        }
        if (a == 0 && b == 7) {
            return ModBlocks.SUPPLY_LOCKER.get().defaultBlockState().setValue(SupplyContainerBlock.FACING, Direction.NORTH);
        }
        return ModBlocks.SUPPLY_CRATE.get().defaultBlockState(); // a == 7 && b == 7
    }

    /** The volunteers' note pinned on the wall above interior cell (a, b), or null. */
    @Nullable
    private static BlockState note(int a, int b) {
        int index;
        Direction facing; // away from the wall, into the room
        if (a == 2 && b == 0) {
            index = 0;
            facing = Direction.SOUTH;
        } else if (a == 7 && b == 2) {
            index = 1;
            facing = Direction.WEST;
        } else if (a == 0 && b == 5) {
            index = 2;
            facing = Direction.EAST;
        } else if (a == 7 && b == 6) {
            index = 3;
            facing = Direction.WEST;
        } else if (a == 1 && b == 7) {
            index = 4;
            facing = Direction.NORTH;
        } else if (a == 5 && b == 7) {
            index = 5;
            facing = Direction.NORTH;
        } else {
            return null;
        }
        return ModBlocks.VOLUNTEER_NOTE.get().defaultBlockState()
                .setValue(VolunteerNoteBlock.FACING, facing)
                .setValue(VolunteerNoteBlock.NOTE, index);
    }

    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int i = localI(site, x);
        int j = localJ(site, z);
        BlockState brown = ModBlocks.MANILA_BROWN_WALL.get().defaultBlockState();
        BlockState manila = ModBlocks.MANILA_WALLPAPER.get().defaultBlockState();
        BlockState wood = ModBlocks.WOOD_FLOOR.get().defaultBlockState();
        out.set(ceiling, ModBlocks.CEILING_TILE.get().defaultBlockState());
        switch (role(i, j)) {
            case BROWN_WALL -> {
                out.set(floor, wood);
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, brown);
                }
            }
            case DOOR -> {
                out.set(floor, wood);
                Direction side = doorSide(i, j);
                BlockState door = ModBlocks.LOBBY_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, side)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                        .setValue(DoorBlock.OPEN, false);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                for (int y = floor + 3; y < ceiling; y++) {
                    out.set(y, brown);
                }
            }
            case MANILA_WALL -> {
                out.set(floor, wood);
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, manila);
                }
            }
            case INNER_DOORWAY -> {
                out.set(floor, wood);
                for (int y = floor + 3; y < ceiling; y++) {
                    out.set(y, manila);
                }
            }
            case ROOM -> {
                out.set(floor, wood);
                int a = i - 2;
                int b = j - 2;
                BlockState piece = furniture(a, b);
                if (piece != null) {
                    out.set(floor + 1, piece);
                }
                BlockState note = note(a, b);
                if (note != null) {
                    out.set(floor + 2, note);
                }
                if (a == 4 && b == 4) {
                    out.set(ceiling, ModBlocks.SAFE_ROOM_LAMP.get().defaultBlockState()); // one warm, silent bulb
                }
            }
            default -> out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState()); // moat: no lamps, so the hum fades
        }
    }
}
