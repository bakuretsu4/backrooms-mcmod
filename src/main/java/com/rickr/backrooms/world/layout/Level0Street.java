package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.MarkingBlock;
import com.rickr.backrooms.registry.Level0StreetBlocks;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The street: a colossal Level 0 hall with a suburban street running down it, from the film. The
 * hall is 35 wide, 100 long and 21 high: yellow wallpaper going up and up, a drop ceiling of light
 * panels far overhead, and three big industrial fans slowly turning up there. Down the middle runs
 * a road with a double yellow line and a manhole or two, sidewalks either side, and eight houses a
 * side with their backs to the hall's walls: siding in blue-grey, white, cream, green and dark red,
 * white garage doors, gable roofs of grey shingles facing the street, porch lights, little lawns
 * with white picket fences, mailboxes and hedges. A stop sign stands at the far end. The road just
 * runs into the end walls; you come in through doorways at the ends of the sidewalks.
 *
 * <p><b>Some houses open</b> (about one in three, the user's "a mix"): a <i>home</i> (a lived-in
 * room behind the front door: wood floor, wallpaper, sofa, TV, a kitchen corner), <i>more Level 0</i>
 * (behind the front door: yellow wallpaper, carpet and a buzzing troffer, as if the house were a
 * shell), or a <i>garage</i> (its door rolled up: boxes, a desk, a filing cabinet, a supply crate).
 * Every other door is locked (it only rattles) and every other garage shut.
 *
 * <p>Plan, local frame (u across the hall, v along it; the site's {@code facing} turns it):
 * <pre>
 *   u  0- 7  houses (backs against the hall wall, fronts at u 7)      mirrored: u 27-34
 *   u  8-11  front yards: lawn, driveway, the path to the door, fence  mirrored: u 23-26
 *   u 12-13  sidewalk                                                  mirrored: u 21-22
 *   u 14-16  road                                                      mirrored: u 18-20
 *   u 17     the double yellow line
 * </pre>
 * Along v the houses sit on 12-block lots (house 10 long, a 1-block side yard either side); v
 * 96-99 is the end of the street. The hall's walls are 2 thick round that, with a 1-block moat of
 * carpet outside. Columns use {@code c} = distance from the nearer side wall (u, or 34 - u) and
 * {@code a} = position along the lot (0..11).
 */
public final class Level0Street {
    /** Hall inside: 35 across (u), 100 along (v). */
    static final int IN_W = 35;
    static final int IN_L = 100;
    static final int WALL = 2;
    static final int MOAT = 1;
    /** The whole site: hall, walls and moat. Along x or z depending on the site's turn. */
    public static final int SIZE_W = IN_W + 2 * (WALL + MOAT);
    public static final int SIZE_L = IN_L + 2 * (WALL + MOAT);

    /** The hall's ceiling (light panels and tiles) and the cap above it. */
    static final int HALL_CEILING = 61;
    static final int CAP = 62;
    /**
     * The panels are 20 blocks up, past where block light reaches, so invisible light blocks carry
     * their light down: a grid over the street and yards just above head height, and rows high up
     * along both walls and the middle, so the hall's walls read yellow all the way up.
     */
    private static final int LOW_LIGHT_Y = 44;
    private static final int HIGH_LIGHT_Y = 54;
    /** The fans' hubs hang here, on rods from the ceiling (FAN_ROD blocks long). */
    public static final int FAN_Y = 57;
    public static final int FAN_ROD = HALL_CEILING - FAN_Y - 1;
    private static final int[] FAN_V = {18, 50, 82};
    private static final int FAN_U = 17;

    /** World {x, z} of each fan's hub (for its hum). */
    public static int[][] fans(AnomalySite site) {
        int[][] out = new int[FAN_V.length][];
        for (int i = 0; i < FAN_V.length; i++) {
            int lu = FAN_U + WALL + MOAT;
            int lv = FAN_V[i] + WALL + MOAT;
            out[i] = new int[] {worldX(site, lu, lv), worldZ(site, lu, lv)};
        }
        return out;
    }

    /** True when world column (x, z) is inside the street hall itself (not its walls or moat). */
    public static boolean inside(AnomalySite site, int x, int z) {
        int u = lu(site, x, z) - WALL - MOAT;
        int v = lv(site, x, z) - WALL - MOAT;
        return u >= 0 && u < IN_W && v >= 0 && v < IN_L;
    }

    private static final int LOT = 12;
    private static final int LOTS = 8;
    /** Houses: walls up to y44, the attic and roof above. */
    private static final int HOUSE_TOP = 44;

    // House kinds (the open ones).
    static final int SHUT = 0;
    static final int HOME = 1;
    static final int LOBBY = 2;
    static final int GARAGE = 3;

    /** Named spots for /backrooms anomaly visit. */
    public static final List<String> VISIT_POINTS = List.of("street", "far_end", "home", "home_in", "lobby", "lobby_in", "garage");

    private Level0Street() {}

    // ---- The turned frame (the site is long, not square: Level0SetPieces.u/v don't apply) -----

    /** Local u (across, 0..SIZE_W-1 including wall and moat) of world column (x, z). */
    static int lu(AnomalySite site, int x, int z) {
        return switch (site.facing() & 3) {
            case 0 -> x - site.minX();
            case 1 -> z - site.minZ();
            case 2 -> site.maxX() - x;
            default -> site.maxZ() - z;
        };
    }

    /** Local v (along, 0..SIZE_L-1 including wall and moat) of world column (x, z). */
    static int lv(AnomalySite site, int x, int z) {
        return switch (site.facing() & 3) {
            case 0 -> z - site.minZ();
            case 1 -> site.maxX() - x;
            case 2 -> site.maxZ() - z;
            default -> x - site.minX();
        };
    }

    /** World x of local (lu, lv). */
    static int worldX(AnomalySite site, int lu, int lv) {
        return switch (site.facing() & 3) {
            case 0 -> site.minX() + lu;
            case 1 -> site.maxX() - lv;
            case 2 -> site.maxX() - lu;
            default -> site.minX() + lv;
        };
    }

    /** World z of local (lu, lv). */
    static int worldZ(AnomalySite site, int lu, int lv) {
        return switch (site.facing() & 3) {
            case 0 -> site.minZ() + lv;
            case 1 -> site.minZ() + lu;
            case 2 -> site.maxZ() - lv;
            default -> site.maxZ() - lu;
        };
    }

    /** Width along x and depth along z of a site turned {@code facing} times. */
    public static int sizeX(int facing) {
        return (facing & 1) == 0 ? SIZE_W : SIZE_L;
    }

    public static int sizeZ(int facing) {
        return (facing & 1) == 0 ? SIZE_L : SIZE_W;
    }

    /** The real direction of a local one (local east = +u, local south = +v). */
    private static Direction dir(AnomalySite site, Direction local) {
        return Level0SetPieces.toWorld(site, local);
    }

    // ---- Lots ---------------------------------------------------------------------------------

    /** What kind of house lot {@code lot} on {@code side} (0 left, 1 right) has. */
    static int houseKind(AnomalySite site, int side, int lot) {
        long h = Hash.mix(site.salt() + side * 131L + lot * 7919L);
        double r = Hash.unit(h);
        if (r < 0.66) {
            return SHUT;
        }
        return r < 0.79 ? HOME : r < 0.90 ? LOBBY : GARAGE;
    }

    /** The siding of a house: blue-grey, dark red and white most, like the film; cream and green now and then. */
    private static BlockState siding(AnomalySite site, int side, int lot) {
        int pick = Hash.range(Hash.mix(site.salt() ^ (side * 977L + lot * 31L + 5)), 9);
        return (switch (pick) {
            case 0, 1, 2 -> ModBlocks.SIDING_GREY;
            case 3, 4 -> Level0StreetBlocks.SIDING_RED;
            case 5, 6 -> ModBlocks.SIDING_WHITE;
            case 7 -> ModBlocks.SIDING_YELLOW;
            default -> ModBlocks.SIDING_GREEN;
        }).get().defaultBlockState();
    }

    // ---- Building a column -----------------------------------------------------------------------

    /** Writes the blocks of column (x, z) of a street site. {@code ceiling} is the maze's own ceiling layer. */
    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int u = lu(site, x, z) - WALL - MOAT;
        int v = lv(site, x, z) - WALL - MOAT;
        BlockState carpet = ModBlocks.LOBBY_CARPET.get().defaultBlockState();
        if (u < -WALL || u >= IN_W + WALL || v < -WALL || v >= IN_L + WALL) {
            // The moat: plain carpet and the maze's own ceiling, so the maze meets the hall cleanly.
            out.set(floor, carpet);
            out.set(ceiling, ModBlocks.CEILING_TILE.get().defaultBlockState());
            return;
        }
        if (u < 0 || u >= IN_W || v < 0 || v >= IN_L) {
            hallWall(u, v, floor, ceiling, out);
            return;
        }
        // The hall's ceiling far overhead: tiles with rows of light panels, and a cap above.
        boolean panel = Math.floorMod(u - 2, 5) == 0 && Math.floorMod(v, 4) <= 1;
        out.set(HALL_CEILING, panel ? Level0Tubes.pick(site.salt(), x, z, false, false)
                : ModBlocks.CEILING_TILE.get().defaultBlockState());
        out.set(CAP, ModBlocks.CEILING_TILE.get().defaultBlockState());
        for (int fv : FAN_V) {
            if (u == FAN_U && v == fv) {
                out.set(FAN_Y, Level0StreetBlocks.CEILING_FAN.get().defaultBlockState());
            }
        }
        int c = Math.min(u, IN_W - 1 - u);
        int side = u <= IN_W / 2 ? 0 : 1;
        if (c >= 8 && Math.floorMod(u, 4) == 1 && Math.floorMod(v, 4) == 2) {
            out.set(LOW_LIGHT_Y, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
        if ((c == 3 || c >= 12 && Math.floorMod(u, 6) == 5) && Math.floorMod(v, 5) == 2) {
            out.set(HIGH_LIGHT_Y, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
        if (c >= 12) {
            street(site, u, v, c, floor, out);
            return;
        }
        int lot = v / LOT;
        if (lot >= LOTS) {
            // The end of the street: lawn, a hedge along the back.
            out.set(floor, ModBlocks.LAWN_GRASS.get().defaultBlockState());
            if (c <= 1) {
                out.set(floor + 1, ModBlocks.HEDGE.get().defaultBlockState());
            }
            return;
        }
        int a = v - lot * LOT;
        if (c >= 8) {
            yard(site, side, lot, a, c, floor, out);
            if (c == 8) {
                roof(site, a, c, floor, siding(site, side, lot), false, out); // the roof overhangs the yard by one
            }
        } else {
            house(site, x, z, side, lot, a, c, floor, out);
        }
    }

    /** The hall's walls (2 thick, 21 high) with a doorway at the end of each sidewalk. */
    private static void hallWall(int u, int v, int floor, int ceiling, LevelLayout.Column out) {
        out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
        boolean endWall = v < 0 || v >= IN_L;
        boolean sidewalk = u == 12 || u == 13 || u == IN_W - 13 || u == IN_W - 14;
        out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
        for (int y = floor + 2; y <= CAP; y++) {
            out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
        }
        if (endWall && sidewalk && u >= 0 && u < IN_W) {
            for (int y = floor + 1; y <= floor + 3; y++) {
                out.set(y, Blocks.AIR.defaultBlockState()); // a doorway in from the maze
            }
        }
    }

    private static void street(AnomalySite site, int u, int v, int c, int floor, LevelLayout.Column out) {
        if (c <= 13) {
            // The sidewalk is Level 0's own carpet, flat with the road (no paving: it would sound
            // and look like nothing else on the level).
            out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
            if (c == 12 && v == 97 && u < IN_W / 2) {
                out.set(floor + 1, Level0StreetBlocks.STOP_SIGN.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, dir(site, Direction.NORTH)));
            }
            return;
        }
        Direction.Axis along = dir(site, Direction.SOUTH).getAxis();
        if (c == 17) {
            out.set(floor, Level0StreetBlocks.ROAD_YELLOW.get().defaultBlockState().setValue(MarkingBlock.AXIS, along));
        } else if (u == 15 && Math.floorMod(v, 37) == 30 || u == 19 && Math.floorMod(v, 41) == 12) {
            out.set(floor, ModBlocks.MANHOLE.get().defaultBlockState());
        } else {
            out.set(floor, Level0StreetBlocks.ROAD.get().defaultBlockState());
        }
    }

    /**
     * Front yard: lawn, the driveway to the garage, the path to the door, a picket fence along the
     * sidewalk with the mailbox in it, a bush or two, and the porch light beside the front door.
     */
    private static void yard(AnomalySite site, int side, int lot, int a, int c, int floor, LevelLayout.Column out) {
        Direction toRoad = dir(site, side == 0 ? Direction.EAST : Direction.WEST);
        if (c == 8 && a == 8) {
            out.set(floor + 3, ModBlocks.PORCH_LIGHT.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, toRoad));
        }
        if (!isFence(a) && a != 6) {
            // The driveway and the path to the door: carpet, like the sidewalk.
            out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
            return;
        }
        out.set(floor, ModBlocks.LAWN_GRASS.get().defaultBlockState());
        if (c == 11) {
            if (a == 6) {
                out.set(floor + 1, ModBlocks.MAILBOX.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, toRoad));
            } else {
                // White pickets along the sidewalk, joined along the street (and on into the next lot's).
                Direction along = dir(site, Direction.SOUTH);
                boolean prev = a == 0 ? lot > 0 : isFence(a - 1);
                boolean next = a == LOT - 1 ? lot < LOTS - 1 : isFence(a + 1);
                out.set(floor + 1, ModBlocks.PICKET_FENCE.get().defaultBlockState()
                        .setValue(fenceSide(along.getOpposite()), prev).setValue(fenceSide(along), next));
            }
        } else if (c == 8 && (a == 0 || a == 11 || a == 9) && Hash.unit(Hash.mix(site.salt() + lot * 17L + side)) < 0.7) {
            out.set(floor + 1, Level39Blocks.BUSH.get().defaultBlockState());
        }
    }

    /** The lawn's edge along the sidewalk has pickets everywhere but the driveway, the path and the mailbox. */
    private static boolean isFence(int a) {
        return !(a >= 2 && a <= 4) && a != 7 && a != 6;
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty fenceSide(Direction d) {
        return switch (d) {
            case NORTH -> FenceBlock.NORTH;
            case SOUTH -> FenceBlock.SOUTH;
            case EAST -> FenceBlock.EAST;
            default -> FenceBlock.WEST;
        };
    }

    /**
     * One column of a house lot (a 0..11 along the lot, c 0..7 from the hall wall). The house is a
     * = 1..10: siding walls 4 high, the front at c = 7 with the garage door (a 2-4), the front door
     * (a 7) and a window (a 9); a gable roof facing the street, overhanging the front by one block.
     * The side yards (a 0 and 11) are lawn with a hedge.
     */
    private static void house(AnomalySite site, int x, int z, int side, int lot, int a, int c, int floor,
                              LevelLayout.Column out) {
        Direction front = dir(site, side == 0 ? Direction.EAST : Direction.WEST);
        if (a == 0 || a == 11) {
            out.set(floor, ModBlocks.LAWN_GRASS.get().defaultBlockState());
            if (c <= 6) {
                out.set(floor + 1, ModBlocks.HEDGE.get().defaultBlockState());
            }
            roof(site, a, c, floor, siding(site, side, lot), false, out);
            return;
        }
        int kind = houseKind(site, side, lot);
        BlockState wall = siding(site, side, lot);
        BlockState trim = ModBlocks.HOUSE_TRIM.get().defaultBlockState();
        boolean edge = c == 0 || c == 7 || a == 1 || a == 10;
        out.set(floor, ModBlocks.BRICK_FOUNDATION.get().defaultBlockState());
        if (edge) {
            for (int y = floor + 1; y <= HOUSE_TOP; y++) {
                out.set(y, wall);
            }
            if (c == 7) {
                houseFront(site, kind, a, front, floor, wall, trim, out);
            }
            if (c == 7 && (a == 1 || a == 10)) {
                for (int y = floor + 1; y <= HOUSE_TOP; y++) {
                    out.set(y, trim); // white corner boards
                }
            }
        } else {
            interior(site, x, z, kind, a, c, front, floor, wall, out);
        }
        roof(site, a, c, floor, wall, true, out);
    }

    /** The front wall (c = 7): garage door, front door, window, porch light. */
    private static void houseFront(AnomalySite site, int kind, int a, Direction front, int floor, BlockState wall,
                                   BlockState trim, LevelLayout.Column out) {
        if (a >= 2 && a <= 4) {
            for (int y = floor + 1; y <= floor + 3; y++) {
                out.set(y, kind == GARAGE ? Blocks.AIR.defaultBlockState()
                        : ModBlocks.GARAGE_DOOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, front));
            }
            out.set(floor + 4, trim);
        } else if (a == 7) {
            boolean open = kind == HOME || kind == LOBBY;
            BlockState door = ModBlocks.HOUSE_DOOR.get().defaultBlockState()
                    .setValue(DoorBlock.FACING, front.getOpposite())
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                    .setValue(DoorBlock.OPEN, false)
                    .setValue(HouseDoorBlock.LOCKED, !open);
            out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            out.set(floor + 3, wall);
        } else if (a == 9 || a == 5) {
            out.set(floor + 2, ModBlocks.HOUSE_WINDOW.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, front));
        }
    }

    /** Inside a house: solid for a shut one, a room for an open one. */
    private static void interior(AnomalySite site, int x, int z, int kind, int a, int c, Direction front, int floor,
                                 BlockState wall, LevelLayout.Column out) {
        if (kind == SHUT) {
            for (int y = floor + 1; y <= HOUSE_TOP; y++) {
                out.set(y, wall);
            }
            return;
        }
        Direction back = front.getOpposite();
        // The lining (one block in from the outer wall) and the room inside it.
        boolean lining = c == 1 || c == 6 || a == 2 || a == 9;
        int ceil = HOUSE_TOP + 1;
        switch (kind) {
            case HOME -> {
                out.set(floor, ModBlocks.WOOD_FLOOR.get().defaultBlockState());
                if (lining && !(c == 6 && a == 7)) {
                    for (int y = floor + 1; y <= HOUSE_TOP; y++) {
                        out.set(y, ModBlocks.DIM_WALLPAPER.get().defaultBlockState());
                    }
                } else {
                    if (c == 2 && a == 4) {
                        out.set(floor + 1, facing(ModBlocks.SOFA.get().defaultBlockState(), front)); // faces the TV
                    } else if (c == 2 && a == 5) {
                        out.set(floor + 1, facing(ModBlocks.SOFA.get().defaultBlockState(), front));
                    } else if (c == 5 && a == 4) {
                        out.set(floor + 1, facing(ModBlocks.TV.get().defaultBlockState(), back));
                    } else if (c == 2 && a == 8) {
                        out.set(floor + 1, facing(ModBlocks.FRIDGE.get().defaultBlockState(), front));
                    } else if ((c == 3 || c == 4) && a == 8) {
                        out.set(floor + 1, facing(ModBlocks.KITCHEN_COUNTER.get().defaultBlockState(), dir(site, Direction.NORTH)));
                    } else if (c == 4 && a == 6) {
                        out.set(floor + 1, facing(ModBlocks.DINING_TABLE.get().defaultBlockState(), front));
                    }
                    if (c == 3 && a == 4 || c == 4 && a == 7) {
                        out.set(HOUSE_TOP, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 14));
                    }
                }
                out.set(ceil, ModBlocks.CEILING_TILE.get().defaultBlockState());
            }
            case LOBBY -> {
                out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
                if (lining && !(c == 6 && a == 7)) {
                    out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                    for (int y = floor + 2; y <= HOUSE_TOP; y++) {
                        out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                    }
                    out.set(ceil, ModBlocks.CEILING_TILE.get().defaultBlockState());
                } else {
                    out.set(ceil, (c == 3 || c == 4) && (a == 5 || a == 6) ? Level0Tubes.pick(site.salt(), x, z, false, false)
                            : ModBlocks.CEILING_TILE.get().defaultBlockState());
                }
            }
            default -> {
                // The garage: carpet, a desk under the window, a filing cabinet, boxes, a crate.
                out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
                if (c == 1 && a >= 2 && a <= 4) {
                    out.set(floor + 1, facing(ModBlocks.OFFICE_DESK.get().defaultBlockState(), front));
                } else if (c == 1 && a == 6) {
                    out.set(floor + 1, facing(ModBlocks.FILING_CABINET.get().defaultBlockState(), front));
                } else if (c == 2 && a == 8) {
                    out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
                } else if ((c == 1 || c == 2) && a == 9 || c == 1 && a == 8) {
                    out.set(floor + 1, wall);
                } else if (c == 1 && a == 7) {
                    out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
                    out.set(floor + 2, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
                }
                if (c == 3 && a == 5) {
                    out.set(HOUSE_TOP, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
                }
                out.set(ceil, ModBlocks.CEILING_TILE.get().defaultBlockState());
            }
        }
    }

    private static BlockState facing(BlockState state, Direction d) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /**
     * The gable roof over a lot, facing the street: grey shingle stairs climbing from both side
     * yards (a 0 and 11, the eaves) to the ridge at a 5-6, filled with the house's siding below
     * (so the front and back show a gable), overhanging the front by one block (c = 8). The porch
     * light hangs under the overhang beside the door.
     *
     * @param house false for the side yards: only the eave stair, nothing under it
     */
    private static void roof(AnomalySite site, int a, int c, int floor, BlockState wall, boolean house, LevelLayout.Column out) {
        if (c > 8) {
            return;
        }
        int rise = Math.min(a, LOT - 1 - a);
        int y = HOUSE_TOP + 1 + rise;
        Direction up = dir(site, a <= 5 ? Direction.SOUTH : Direction.NORTH);
        out.set(y, ModBlocks.ROOF_SHINGLES_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, up));
        if (house && c <= 7) {
            // Under the roof: the attic (or the gable end at the front and back), solid.
            int from = HOUSE_TOP + (a >= 2 && a <= 9 && c >= 1 && c <= 6 ? 2 : 1);
            for (int yy = from; yy < y; yy++) {
                out.set(yy, wall);
            }
        }
    }

    /** True if column (x, z) is a wall at eye level (the hall's walls, a house). */
    static boolean isSolid(AnomalySite site, int x, int z) {
        int u = lu(site, x, z) - WALL - MOAT;
        int v = lv(site, x, z) - WALL - MOAT;
        if (u < -WALL || u >= IN_W + WALL || v < -WALL || v >= IN_L + WALL) {
            return false;
        }
        if (u < 0 || u >= IN_W || v < 0 || v >= IN_L) {
            boolean endWall = v < 0 || v >= IN_L;
            boolean sidewalk = u == 12 || u == 13 || u == IN_W - 13 || u == IN_W - 14;
            return !(endWall && sidewalk && u >= 0 && u < IN_W);
        }
        int c = Math.min(u, IN_W - 1 - u);
        int lot = v / LOT;
        if (c >= 8 || lot >= LOTS) {
            return false;
        }
        int a = v - lot * LOT;
        if (a == 0 || a == 11) {
            return false;
        }
        int side = u <= IN_W / 2 ? 0 : 1;
        int kind = houseKind(site, side, lot);
        boolean inside = c >= 1 && c <= 6 && a >= 2 && a <= 9;
        if (kind == SHUT) {
            return true;
        }
        if (c == 7) {
            return !(a == 7 && kind != GARAGE || a >= 2 && a <= 4 && kind == GARAGE);
        }
        return !inside;
    }

    /** True if you can stand on the ground floor of column (x, z) (the moat, the street, the yards, an open house). */
    static boolean isWalkable(AnomalySite site, int x, int z) {
        return !isSolid(site, x, z);
    }

    /** {x, feet y, z, yaw, pitch} of a named spot (VISIT_POINTS), or null. */
    @Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        int u;
        int v;
        Direction look = Direction.SOUTH;
        switch (name) {
            case "street" -> {
                u = 15;
                v = 1;
            }
            case "far_end" -> {
                u = 19;
                v = 98;
                look = Direction.NORTH;
            }
            case "home", "lobby", "garage", "home_in", "lobby_in" -> {
                boolean in = name.endsWith("_in");
                String base = in ? name.substring(0, name.length() - 3) : name;
                int want = base.equals("home") ? HOME : base.equals("lobby") ? LOBBY : GARAGE;
                for (int side = 0; side < 2; side++) {
                    for (int lot = 0; lot < LOTS; lot++) {
                        if (houseKind(site, side, lot) == want) {
                            int a = in ? 7 : want == GARAGE ? 3 : 7;
                            int c = in ? 4 : 10;
                            u = side == 0 ? c : IN_W - 1 - c;
                            v = lot * LOT + a;
                            Direction toHouse = side == 0 ? Direction.WEST : Direction.EAST;
                            return spot(site, u, v, floor + 1, in ? Direction.NORTH : toHouse);
                        }
                    }
                }
                return null;
            }
            default -> {
                return null;
            }
        }
        return spot(site, u, v, floor + 1, look);
    }

    private static double[] spot(AnomalySite site, int u, int v, double feet, Direction look) {
        int lu = u + WALL + MOAT;
        int lv = v + WALL + MOAT;
        return new double[] {worldX(site, lu, lv) + 0.5, feet, worldZ(site, lu, lv) + 0.5, dir(site, look).toYRot(), 0.0};
    }
}
