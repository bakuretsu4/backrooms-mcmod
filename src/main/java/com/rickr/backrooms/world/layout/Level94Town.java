package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level94Vehicles;
import com.rickr.backrooms.block.SirenBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The little town of Level 94: a paved plaza with the almond-water fountain (a big king chess
 * piece standing in a round stone basin), four streets each way around it and tiny houses packed
 * along them, with old cars and milk vans parked at the kerb.
 *
 * <p>Everything is laid out in town coordinates (u, v) = (x - tx, z - tz). The town square fills
 * |u|, |v| <= {@link Level94Roads#TOWN_HALF}; streets run along the {@link Level94Roads#TOWN_LINES}.
 * The whole town is flat: ground at y40.
 */
final class Level94Town {
    /** Half the plaza (paved |u|, |v| <= 16, inside the inner streets). */
    private static final int PLAZA = 16;
    /** Outer radius of the fountain's stone rim. */
    private static final double BASIN = 7.3;
    private static final double BASIN_INNER = 6.0;
    /** Parking spaces along every street, every 9 blocks. */
    private static final int SLOT = 9;

    // Surfaces.
    static final int GRASS = 0;
    static final int ROAD = 1;
    static final int SIDEWALK = 2;
    static final int PAVING = 3;

    /** One house reused per thread for the town's houses (they're laid out on the fly). */
    private static final ThreadLocal<Level94Houses.House> HOUSE = ThreadLocal.withInitial(Level94Houses.House::new);

    private Level94Town() {}

    /** Distance from (u, v) to the centre line of the nearest town street. */
    private static int streetDistance(int u, int v) {
        int best = Integer.MAX_VALUE;
        for (int line : Level94Roads.TOWN_LINES) {
            if (Math.abs(u) <= Level94Roads.TOWN_HALF - 1) best = Math.min(best, Math.abs(v - line));
            if (Math.abs(v) <= Level94Roads.TOWN_HALF - 1) best = Math.min(best, Math.abs(u - line));
        }
        return best;
    }

    /** What the ground at town column (u, v) is made of. */
    static int surface(int u, int v) {
        int d = streetDistance(u, v);
        if (d <= 2) return ROAD;
        if (Math.abs(u) <= PLAZA && Math.abs(v) <= PLAZA) return PAVING;
        if (d == 3) return SIDEWALK;
        return GRASS;
    }

    /**
     * Which of the three building strips between the streets offset w falls in: 0 = [-41, -23],
     * 1 = [-15, 15], 2 = [23, 41]; -1 if none (a street, a sidewalk or a front yard).
     */
    private static int strip(int w) {
        if (w >= -41 && w <= -23) return 0;
        if (w >= -15 && w <= 15) return 1;
        if (w >= 23 && w <= 41) return 2;
        return -1;
    }

    private static int stripLo(int i) {
        return i == 0 ? -41 : i == 1 ? -15 : 23;
    }

    private static int stripHi(int i) {
        return i == 0 ? -23 : i == 1 ? 15 : 41;
    }

    /**
     * Finds the town house standing on column (u, v), sets {@code h} to it and returns true, or
     * returns false if the column is a yard, street or plaza. House fronts face the nearest street.
     */
    private static boolean houseAt(long seed, Level94Roads.Town t, int u, int v, Level94Houses.House h) {
        int iu = strip(u);
        int iv = strip(v);
        if (iu < 0 || iv < 0 || (iu == 1 && iv == 1)) {
            return false;
        }
        // Blocks beside the plaza (iv == 1) face the north-south streets, the rest the east-west ones.
        boolean alongU = iv != 1;
        int across = alongU ? v : u;          // across the street direction (front to back)
        int along = alongU ? u : v;           // along the street
        int acrossStrip = alongU ? iv : iu;
        int alongStrip = alongU ? iu : iv;
        int lo = stripLo(acrossStrip);
        int hi = stripHi(acrossStrip);
        int depth = 7;
        int front0;
        int front1;
        Direction front;
        if (across <= lo + depth - 1) {
            front0 = lo;
            front1 = lo + depth - 1;
            front = alongU ? Direction.NORTH : Direction.WEST;
        } else if (across >= hi - depth + 1) {
            front0 = hi - depth + 1;
            front1 = hi;
            front = alongU ? Direction.SOUTH : Direction.EAST;
        } else {
            return false; // back gardens
        }
        int alo = stripLo(alongStrip);
        int len = stripHi(alongStrip) - alo + 1;
        int n = (len + 1) / 6;
        int off = (len - (6 * n - 1)) / 2;
        int rel = along - alo - off;
        if (rel < 0) return false;
        int k = rel / 6;
        if (k >= n || rel - 6 * k > 4) return false;
        int a0 = alo + off + 6 * k;
        int a1 = a0 + 4;
        int rx0 = t.tx + (alongU ? a0 : front0);
        int rx1 = t.tx + (alongU ? a1 : front1);
        int rz0 = t.tz + (alongU ? front0 : a0);
        int rz1 = t.tz + (alongU ? front1 : a1);
        boolean red = Hash.unit(seed, rx0, rz0, 9470) < 0.5;
        h.inRect(Level94Houses.TINY, rx0, rz0, rx1, rz1, front, LevelLayout.FLOOR_Y, red);
        return true;
    }

    /** True inside the walls of a town house. */
    static boolean isSafe(long seed, Level94Roads.Town t, int x, int z) {
        Level94Houses.House h = HOUSE.get();
        return houseAt(seed, t, x - t.tx, z - t.tz, h) && h.isSafe(x, z);
    }

    /** Writes everything standing on town column (x, z): fountain, sirens, houses, parked vehicles. */
    static void column(long seed, Level94Roads.Town t, int x, int z, LevelLayout.Column out) {
        int u = x - t.tx;
        int v = z - t.tz;
        double r = Math.sqrt(u * (double) u + v * (double) v);
        if (r < BASIN) {
            fountain(u, v, r, out);
            return;
        }
        if (Math.abs(u) == 13 && Math.abs(v) == 13) {
            // Siren poles on the plaza corners, horns toward the streets east and west.
            siren(LevelLayout.FLOOR_Y, u > 0 ? Direction.EAST : Direction.WEST, out);
            return;
        }
        Level94Houses.House h = HOUSE.get();
        if (houseAt(seed, t, u, v, h)) {
            Level94Houses.column(h, x, z, out);
            return;
        }
        parked(seed, t, u, v, out);
    }

    /** A siren pole: seven pole blocks on the ground, the horn on top. */
    static void siren(int groundY, Direction horn, LevelLayout.Column out) {
        BlockState pole = ModBlocks.L94_SIREN_POLE.get().defaultBlockState();
        for (int y = groundY + 1; y <= groundY + 7; y++) {
            out.set(y, pole);
        }
        out.set(groundY + 8, ModBlocks.L94_SIREN.get().defaultBlockState().setValue(SirenBlock.FACING, horn));
    }

    /**
     * The almond-water fountain: a round stone basin full of water with a tall white king chess
     * piece in the middle (base, body, collar, head and a cross on top).
     */
    private static void fountain(int u, int v, double r, LevelLayout.Column out) {
        int y0 = LevelLayout.FLOOR_Y;
        BlockState stone = ModBlocks.L94_FOUNTAIN_STONE.get().defaultBlockState();
        if (r >= BASIN_INNER) {
            out.set(y0, stone);
            out.set(y0 + 1, stone);
            return;
        }
        out.set(y0 - 1, stone);
        BlockState statue = ModBlocks.L94_STATUE.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        // Radius of the king piece at each height above the plaza (0 = none).
        double[] profile = {2.6, 2.6, 2.0, 1.5, 1.5, 1.5, 1.5, 1.5, 2.2, 1.5, 2.0, 2.0, 1.5};
        for (int k = 0; k < profile.length; k++) {
            int y = y0 + k;
            if (r <= profile[k]) {
                out.set(y, statue);
            } else if (k <= 1) {
                out.set(y, water);
            }
        }
        // The cross on the crown.
        int top = y0 + profile.length;
        if (u == 0 && v == 0) {
            for (int y = top; y < top + 3; y++) out.set(y, statue);
        } else if (v == 0 && Math.abs(u) == 1) {
            out.set(top + 1, statue);
        }
    }

    /** Cars and milk vans parked on the right-hand side of every town street. */
    private static void parked(long seed, Level94Roads.Town t, int u, int v, LevelLayout.Column out) {
        for (int li = 0; li < Level94Roads.TOWN_LINES.length; li++) {
            int line = Level94Roads.TOWN_LINES[li];
            if (Math.abs(v - line) <= 2 && Math.abs(u) <= Level94Roads.TOWN_HALF - 1) {
                vehicleOnStreet(seed, t, li, false, u, v - line, out);
            }
            if (Math.abs(u - line) <= 2 && Math.abs(v) <= Level94Roads.TOWN_HALF - 1) {
                vehicleOnStreet(seed, t, li, true, v, u - line, out);
            }
        }
    }

    /**
     * Vehicle parts on one street. {@code along} is the position along the street, {@code lane}
     * the offset across it (-2..2). Streets running north-south have {@code northSouth} set.
     */
    private static void vehicleOnStreet(long seed, Level94Roads.Town t, int li, boolean northSouth, int along, int lane,
                                        LevelLayout.Column out) {
        for (int k = 0; k < 10; k++) {
            int p = -42 + SLOT * k;
            if (Math.abs(along - p) > Level94Vehicles.REACH || Math.abs(p) > 43 || nearCrossing(p)) {
                continue;
            }
            long h = Hash.of(seed, t.tx * 31 + li * 2 + (northSouth ? 1 : 0), t.tz + k, 9475);
            if (Hash.unit(h) >= 0.45) {
                continue;
            }
            boolean positive = Hash.unit(Hash.mix(h)) < 0.5;
            Direction facing = northSouth ? (positive ? Direction.SOUTH : Direction.NORTH)
                    : (positive ? Direction.EAST : Direction.WEST);
            // Park on the driver's right: that side of the street is +1 lane for east/north, -1 for west/south.
            Direction right = facing.getClockWise();
            int anchorLane = (northSouth ? right.getStepX() : right.getStepZ());
            Level94Vehicles.Kind kind = kind(Hash.mix(h + 1));
            int dAlong = along - p;
            int dLane = lane - anchorLane;
            int dx = northSouth ? dLane : dAlong;
            int dz = northSouth ? dAlong : dLane;
            for (int dy = 0; dy <= 2; dy++) {
                BlockState part = Level94Vehicles.cell(kind, facing, dx, dy, dz);
                if (part != null) {
                    out.set(LevelLayout.FLOOR_Y + 1 + dy, part);
                }
            }
        }
    }

    /** About 70% cars (three colours) and 30% milk vans. */
    static Level94Vehicles.Kind kind(long h) {
        double k = Hash.unit(h);
        if (k < 0.30) return Level94Vehicles.Kind.MILK_VAN;
        if (k < 0.53) return Level94Vehicles.Kind.CAR_RED;
        if (k < 0.76) return Level94Vehicles.Kind.CAR_BLACK;
        return Level94Vehicles.Kind.CAR_CREAM;
    }

    private static boolean nearCrossing(int p) {
        for (int line : Level94Roads.TOWN_LINES) {
            if (Math.abs(p - line) <= 6) return true;
        }
        return false;
    }
}
