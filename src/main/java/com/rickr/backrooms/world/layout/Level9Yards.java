package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.yard.ParkedCarBlock;
import com.rickr.backrooms.block.yard.SprinklerBlock;
import com.rickr.backrooms.block.yard.SwingSetBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Level 9 street life: what people left in their yards and on the street. Called from small
 * hooks in {@link Level9Layout} (yard, driveway, road); everything is a pure function of the
 * seed and the lot, like the rest of the layout, and only ever writes the column being filled.
 *
 * <p>Each lot's choices are made once ({@link Plan}, keyed by the lot's front corner) from its own
 * hash. Placement rules and chances (per house lot unless said otherwise):
 * <ul>
 *   <li><b>Car on the driveway</b> ({@link #DRIVEWAY_CAR}, 35% of houses with a garage): 2 x 4
 *       blocks on two of the driveway's three columns, rows setback-5 to setback-2 (the row in
 *       front of the garage door stays free), nose to the garage or backed in. Never on top of a
 *       driveway backpack.</li>
 *   <li><b>Car at the curb</b> ({@link #CURB_CAR}, 10%): parked along the gutter in front of the
 *       lot, facing the way traffic would go (right-hand traffic), never across the driveway
 *       mouth. Houses only stand away from side streets, roundabouts and the underpass, so these
 *       never block a junction or the exit; the Mangled's pillar and crater avoid them
 *       ({@link #curbCarAt}).</li>
 *   <li><b>Swing set</b> ({@link #SWING_SET}, 18% of backyards deep enough: the house's back wall
 *       at least 3 rows from the back fence): three wide, one row in front of the back fence,
 *       never behind the back door.</li>
 *   <li><b>Sprinkler</b> ({@link #SPRINKLER}, 12%): on the front lawn, rows 2 to setback-3.</li>
 *   <li><b>A toy</b> ({@link #TOY}, 18%): a tipped tricycle, a ball or a bike lying down, on the
 *       front lawn or (half the time, if there is room) in the backyard.</li>
 *   <li><b>Wheelie bins</b> ({@link #BINS}, 30% of houses without a front fence): one, or two side
 *       by side (40%), on the lawn's front row by the sidewalk, next to the driveway or the path.</li>
 *   <li><b>Mailbox flag up</b> ({@link #FLAG_UP}, 25% of mailboxes).</li>
 * </ul>
 * Front-lawn props keep off the path and porch (door column +-2, porch +-1), the garage and
 * driveway (+-1) and the lot's edge columns; nothing goes where Level9Story puts its posters
 * (lawn row setback-3 in front of the porch posts, the driveway row in front of the garage door).
 */
final class Level9Yards {
    static final double DRIVEWAY_CAR = 0.35;
    static final double CURB_CAR = 0.10;
    static final double SWING_SET = 0.18;
    static final double SPRINKLER = 0.12;
    static final double TOY = 0.18;
    static final double BINS = 0.30;
    static final double FLAG_UP = 0.25;
    /** Props stand on the lawn / driveway (feet level). */
    private static final int YARD_Y = 42;
    /** A car at the curb stands on the asphalt. */
    private static final int ROAD_Y = 41;
    private static final int NONE = Integer.MIN_VALUE;

    private Level9Yards() {}

    /** One lot's choices. Made once per lot per thread (neighbouring columns share the lot). */
    private static final class Plan {
        long seed;
        int x0 = NONE;
        int z0;
        // Driveway car: first column (u), facing, paint.
        int carU0 = NONE;
        Direction carFacing;
        ParkedCarBlock.Paint carPaint;
        // Car at the curb: first column (u), paint.
        int curbU0 = NONE;
        ParkedCarBlock.Paint curbPaint;
        // Swing set: first column (u) and row (v), the way the seats face.
        int swingU0 = NONE;
        int swingV;
        Direction swingFacing;
        int sprinklerU = NONE;
        int sprinklerV;
        Direction sprinklerFacing;
        int toyU = NONE;
        int toyV;
        Block toy;
        Direction toyFacing;
        int binU = NONE;
        int binU2 = NONE;
    }

    private static final ThreadLocal<Plan> PLAN = ThreadLocal.withInitial(Plan::new);

    /** The plan of the lot whose (u = 0, v = 0) corner is at (x0, z0). */
    private static Plan plan(long seed, Level9Layout.House h, int x0, int z0) {
        Plan p = PLAN.get();
        if (p.x0 == x0 && p.z0 == z0 && p.seed == seed) {
            return p;
        }
        p.seed = seed;
        p.x0 = x0;
        p.z0 = z0;
        p.carU0 = p.curbU0 = p.swingU0 = p.sprinklerU = p.toyU = p.binU = p.binU2 = NONE;
        int s = h.setback;
        boolean odd = h.clipA != null || h.glitch;

        // Car on the driveway.
        if (h.garage && s >= 5 && !odd && unit(seed, x0, z0, 1) < DRIVEWAY_CAR) {
            p.carU0 = h.drivewayBackpack ? h.gu0 + 2 : h.gu0 + 1 + range(seed, x0, z0, 2, 2);
            p.carFacing = unit(seed, x0, z0, 3) < 0.6 ? h.inward : h.front;
            p.carPaint = paint(seed, x0, z0, 4);
        }
        // Car at the curb: up to four tries for a spot clear of the driveway mouth.
        if (!odd && unit(seed, x0, z0, 5) < CURB_CAR) {
            for (int t = 0; t < 4 && p.curbU0 == NONE; t++) {
                int u0 = 1 + range(seed, x0, z0, 6 + t, Level9Layout.LW - 5);
                if (!h.garage || u0 + 3 < h.gu0 - 1 || u0 > h.gu0 + 5) {
                    p.curbU0 = u0;
                }
            }
            p.curbPaint = paint(seed, x0, z0, 10);
        }
        // Swing set, one row in front of the back fence, if the backyard is deep enough.
        int backyard = s + h.depth + 1;          // first row behind the house's roof overhang
        int swingRow = Level9Layout.LD - 3;
        if (!odd && backyard + 1 <= swingRow && unit(seed, x0, z0, 11) < SWING_SET) {
            for (int t = 0; t < 4 && p.swingU0 == NONE; t++) {
                int u0 = 2 + range(seed, x0, z0, 12 + t, Level9Layout.LW - 6);
                if (u0 + 2 < h.doorU - 2 || u0 > h.doorU + 2) {
                    p.swingU0 = u0;
                }
            }
            p.swingV = swingRow;
            p.swingFacing = unit(seed, x0, z0, 16) < 0.5 ? h.front : h.inward;
        }
        // Sprinkler on the front lawn.
        if (!odd && s >= 5 && unit(seed, x0, z0, 17) < SPRINKLER) {
            for (int t = 0; t < 4 && p.sprinklerU == NONE; t++) {
                int u = 1 + range(seed, x0, z0, 18 + t, Level9Layout.LW - 2);
                if (frontFree(h, u)) {
                    p.sprinklerU = u;
                }
            }
            p.sprinklerV = 2 + range(seed, x0, z0, 22, s - 4);
            p.sprinklerFacing = Direction.from2DDataValue(range(seed, x0, z0, 23, 4));
        }
        // A toy: on the front lawn, or in the backyard.
        if (!odd && unit(seed, x0, z0, 24) < TOY) {
            int kind = range(seed, x0, z0, 25, 3);
            p.toy = (kind == 0 ? ModBlocks.L9_TRICYCLE : kind == 1 ? ModBlocks.L9_TOY_BALL : ModBlocks.L9_BIKE).get();
            // Lying along the street (east or west): a bike's wheels reach a little into the next column.
            p.toyFacing = unit(seed, x0, z0, 26) < 0.5 ? Direction.EAST : Direction.WEST;
            boolean back = backyard <= Level9Layout.LD - 2 && unit(seed, x0, z0, 27) < 0.5;
            for (int t = 0; t < 4 && p.toyU == NONE; t++) {
                int u = 2 + range(seed, x0, z0, 28 + t, Level9Layout.LW - 4);
                if (back) {
                    int v = backyard + range(seed, x0, z0, 32 + t, Level9Layout.LD - 1 - backyard);
                    boolean nearSwing = p.swingU0 != NONE && Math.abs(v - p.swingV) <= 1 && u >= p.swingU0 - 1 && u <= p.swingU0 + 3;
                    if (!nearSwing && Math.abs(u - h.doorU) > 2) {
                        p.toyU = u;
                        p.toyV = v;
                    }
                } else if (s >= 5 && frontFree(h, u - 1) && frontFree(h, u) && frontFree(h, u + 1)) {
                    int v = 2 + range(seed, x0, z0, 32 + t, s - 4);
                    if (p.sprinklerU == NONE || Math.abs(u - p.sprinklerU) > 2 || v != p.sprinklerV) {
                        p.toyU = u;
                        p.toyV = v;
                    }
                }
            }
        }
        // Wheelie bins out by the sidewalk (not behind a front fence).
        if (!h.frontFence && !odd && unit(seed, x0, z0, 36) < BINS) {
            boolean pair = unit(seed, x0, z0, 37) < 0.4;
            if (h.garage) {
                boolean far = unit(seed, x0, z0, 38) < 0.5;
                p.binU = far ? h.gu0 + 4 : h.gu0;
                int second = far ? p.binU + 1 : p.binU - 1;
                p.binU2 = pair && second >= 1 && second <= Level9Layout.LW - 1 ? second : NONE;
            } else {
                p.binU = h.doorU + 2;
                p.binU2 = pair ? h.doorU + 3 : NONE;
            }
        }
        return p;
    }

    /** May a front-lawn prop stand in lot column u? (Off the path, porch, garage, driveway and edges.) */
    private static boolean frontFree(Level9Layout.House h, int u) {
        if (u < 1 || u > Level9Layout.LW - 2) return false;
        if (u >= h.doorU - 2 && u <= h.doorU + 2) return false;
        if (u >= h.porchU0 - 1 && u <= h.porchU1 + 1) return false;
        return !h.garage || u < h.gu0 - 1 || u > h.gu0 + 5;
    }

    // ---- Hooks ---------------------------------------------------------------------------------

    /** Lot corner (u = 0, v = 0) in the world for a column (x, z) that is lot column (u, v). */
    private static Plan planAt(long seed, Level9Layout.House h, int x, int z, int u, int v) {
        return plan(seed, h, x - u, z - v * h.inward.getStepZ());
    }

    /** Level9Layout.yard(), on a driveway column: maybe part of a parked car. */
    static void driveway(long seed, int x, int z, Level9Layout.House h, int u, int v, Level9Layout.Column out) {
        Plan p = planAt(seed, h, x, z, u, v);
        int s = h.setback;
        if (p.carU0 == NONE || u < p.carU0 || u > p.carU0 + 1 || v < s - 5 || v > s - 2) {
            return;
        }
        // Nose toward the garage (larger v) or toward the street.
        int along = p.carFacing == h.inward ? (s - 2) - v : v - (s - 5);
        out.set(YARD_Y, carPiece(p.carFacing, along, u - p.carU0, p.carPaint));
    }

    /** Level9Layout.yard(), on a plain lawn column (no fence, path, porch or mailbox): the yard props. */
    static void lawn(long seed, int x, int z, Level9Layout.House h, int u, int v, Level9Layout.Column out) {
        Plan p = planAt(seed, h, x, z, u, v);
        if (p.swingU0 != NONE && v == p.swingV && u >= p.swingU0 && u <= p.swingU0 + 2) {
            // Left / middle / right as seen sitting on it; "right" is the seats' clockwise side.
            int k = u - p.swingU0;
            boolean rightIsPlusU = p.swingFacing.getClockWise() == Direction.EAST;
            int fromLeft = rightIsPlusU ? k : 2 - k;
            SwingSetBlock.Part part = fromLeft == 0 ? SwingSetBlock.Part.LEFT : fromLeft == 1 ? SwingSetBlock.Part.MIDDLE : SwingSetBlock.Part.RIGHT;
            out.set(YARD_Y, ModBlocks.L9_SWING_SET.get().defaultBlockState()
                    .setValue(SwingSetBlock.FACING, p.swingFacing).setValue(SwingSetBlock.PART, part));
        } else if (u == p.sprinklerU && v == p.sprinklerV) {
            out.set(YARD_Y, ModBlocks.L9_SPRINKLER.get().defaultBlockState().setValue(SprinklerBlock.FACING, p.sprinklerFacing));
        } else if (u == p.toyU && v == p.toyV) {
            out.set(YARD_Y, p.toy.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.toyFacing));
        } else if (v == 0 && (u == p.binU || u == p.binU2)) {
            out.set(YARD_Y, ModBlocks.L9_TRASH_BIN.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, h.front));
        }
    }

    /** Is the mailbox at (x, z) showing its red flag? */
    static boolean flagUp(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 9306) < FLAG_UP;
    }

    /** Level9Layout.fillRoad(), on a plain road column: maybe part of a car parked at the curb. */
    static void road(Level9Layout layout, long seed, int x, int z, Level9Layout.Column out) {
        BlockState car = curbCar(layout, seed, x, z);
        if (car != null) {
            out.set(ROAD_Y, car);
        }
    }

    /** True if a car is parked on this road column (the Mangled's pillar and crater keep off it). */
    static boolean curbCarAt(Level9Layout layout, long seed, int x, int z) {
        return curbCar(layout, seed, x, z) != null;
    }

    /**
     * The car piece parked on road column (x, z), or null. Cars park in the two lanes nearest a
     * main street's curb: the gutter (curb right next to it) and the lane beside that. The lot is
     * two blocks past the curb (curb, sidewalk, then the lot's front row).
     */
    private static BlockState curbCar(Level9Layout layout, long seed, int x, int z) {
        for (int dir = -1; dir <= 1; dir += 2) {
            int lane;
            int curbZ;
            if (layout.classify(seed, x, z + dir) == Level9Layout.CURB) {
                lane = 0;
                curbZ = z + dir;
            } else if (layout.classify(seed, x, z + 2 * dir) == Level9Layout.CURB
                    && layout.classify(seed, x, z + dir) == Level9Layout.ROAD) {
                lane = 1;
                curbZ = z + 2 * dir;
            } else {
                continue;
            }
            int lotZ = curbZ + 2 * dir;
            long info = layout.lotInfo(seed, x, lotZ);
            if (info < 0 || Level9Layout.infoV(info) != 0 || layout.classify(seed, x, lotZ) != Level9Layout.OTHER) {
                continue;
            }
            int r = Level9Layout.infoRow(info);
            int side = Level9Layout.infoSide(info);
            int idx = Level9Layout.infoIdx(info);
            if (!layout.lotValid(seed, r, side, idx)) {
                continue;
            }
            Level9Layout.House h = layout.houseOf(seed, r, side, idx);
            if (h.inward.getStepZ() != dir) {
                continue; // that lot faces another street
            }
            int u = Level9Layout.infoU(info);
            Plan p = plan(seed, h, x - u, lotZ);
            if (p.curbU0 == NONE || u < p.curbU0 || u > p.curbU0 + 3) {
                continue;
            }
            // Right-hand traffic: south of the street cars face east, north of it west; their
            // right side is against the curb (the gutter lane).
            Direction facing = dir > 0 ? Direction.EAST : Direction.WEST;
            int along = facing == Direction.EAST ? (p.curbU0 + 3) - u : u - p.curbU0;
            return carPiece(facing, along, lane == 0 ? 1 : 0, p.curbPaint);
        }
        return null;
    }

    /**
     * The piece of a car at (along, side) in the car's own terms when the caller only knows the
     * column offset from the car's first lot column ({@code k} = 0 or 1 along +x / +u for the
     * driveway; already left/right for the curb).
     */
    private static BlockState carPiece(Direction facing, int along, int sideOrK, ParkedCarBlock.Paint paint) {
        int side = sideOrK;
        if (facing.getAxis() == Direction.Axis.Z) {
            // Driveway: the car points along z, so its two columns lie along x (u). Right is east
            // when it faces north, west when it faces south.
            side = facing.getClockWise() == Direction.EAST ? sideOrK : 1 - sideOrK;
        }
        return ModBlocks.L9_PARKED_CAR.get().piece(facing, along, side, paint);
    }

    // ---- Hash helpers --------------------------------------------------------------------------

    private static double unit(long seed, int x0, int z0, int salt) {
        return Hash.unit(seed, x0, z0, 9300 + salt);
    }

    private static int range(long seed, int x0, int z0, int salt, int bound) {
        return Hash.range(Hash.of(seed, x0, z0, 9300 + salt), Math.max(1, bound));
    }

    private static ParkedCarBlock.Paint paint(long seed, int x0, int z0, int salt) {
        ParkedCarBlock.Paint[] all = ParkedCarBlock.Paint.values();
        return all[range(seed, x0, z0, salt, all.length)];
    }
}
