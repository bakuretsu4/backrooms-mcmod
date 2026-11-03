package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.PoolLadderBlock;
import com.rickr.backrooms.block.PoolLampBlock;
import com.rickr.backrooms.block.SunlitVoidBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The Poolrooms' way out: the great pool hall, one at every exit point of {@link PoolroomsLayout}.
 * Pure functions of the hall's orientation and of where a column is in it.
 *
 * <p>A dark, very tall square hall, 41 x 41 blocks inside, its floor one big pool. The walls rise
 * straight up for 39 blocks; from there the ceiling slopes up from all four walls (45 degrees,
 * smoothed with upside-down tile stairs) to a small 7 x 7 skylight at the top of the world, open
 * sky 56 blocks over the water. Along one wall a straight, 3-wide flight of tile stairs climbs 36
 * blocks from the pool deck to a landing under the slope, where a niche in the wall is full of
 * daylight: stepping into it is the way out ({@code PoolroomsRules}, on to {@code next()}).
 *
 * <p>The hall is sunk below the level: the pool's surface is at y6, 35 blocks under the walkways.
 * You come in at the level's height through a doorway in its outer wall and walk down a long,
 * dark stairway inside the far wall, out of a small doorway at the water's edge in the far corner:
 * the whole hall opens over you at once, the stair you have to climb right across the water.
 * Lamps glow in the pool walls and in the four corners, hidden light makes the whole pool glow,
 * and a rising line of step lights picks out the stair; ladders and a floating plank at the edge.
 *
 * <p>Hall-local coordinates: {@code a} runs along the stair wall, the way the stairs climb; {@code p}
 * runs away from the stair wall, across the hall. Both are -20..20 inside. Top view (stairs climb to
 * the right; S stairs, L landing, X the daylit niche, ~ pool, . deck, | the passage down, D its
 * doors):
 * <pre>
 *        a = -20                       a = 20
 *   p=-23  #  #  #  #  #  #  #  #  #  X X  #      (the niche: daylight at p = -23)
 *   p=-20  . . S S S S S S S S S S S S L L L      (3 wide: p = -20..-18)
 *          . . ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
 *          . . ~ ~ ~ ~ ~ ~  pool  ~ ~ ~ ~ ~ ~
 *   p= 20  . . . . . . . . . . . . . . . . .
 *   p= 21  D D #  #  #  #  #  #  #  #  #  # #
 *   p= 22  | | | | | | | | | | | | | | | | |      (down from y42 at the right to y7 at the left)
 *   p= 24  #  #  #  #  #  #  #  #  #  #  # D D    (the way in, from the walkway around the hall)
 * </pre>
 *
 * <p>Water never touches air at its sides or below: the pool is closed by the deck, the stairs and
 * the wall, all solid down to y0.
 */
final class PoolroomsHall {
    // ---- Plan ------------------------------------------------------------------------------------
    /** Inside the hall: |a|, |p| <= 20. */
    static final int INSIDE = 20;
    /** The walls are 4 thick (rings 21..24) ... */
    private static final int WALL_OUTER = 24;
    /** ... and ring 25 is a dry walkway at the level's height, so every room cut by the hall opens onto it. */
    static final int RADIUS = 25;

    // ---- Heights ---------------------------------------------------------------------------------
    /** Top of the deck; the pool's water is y2..y6, its floor y1. Feet on the deck at y7. */
    static final int DECK_Y = 6;
    private static final int POOL_FLOOR_Y = 1;
    /** Underwater lamps, two blocks under the surface (as in the rest of the level). */
    static final int LAMP_Y = DECK_Y - 2;
    /** Lamps in the corners, just above the deck. */
    private static final int CORNER_LAMP_Y = DECK_Y + 2;
    /** The sky, at the very top of the world (the level is 64 high). */
    static final int SKY_Y = 63;
    /** The skylight is 7 x 7: rings 0..3. */
    private static final int SKY_HALF = 3;
    /** The stairs' first step is at a = -18, one block above the deck; each step one higher. */
    private static final int STAIR_START = -18;
    /** The landing is a = 18..20, level with the last step: its top is y42, feet at y43. */
    private static final int LANDING_A = 18;
    static final int LANDING_FEET_Y = DECK_Y + 1 + (LANDING_A - 1 - STAIR_START) + 1;
    /** The daylit niche: a = 18..19, two deep (p = -21, -22), light at p = -23, 4 tall. */
    private static final int NICHE_HEIGHT = 4;
    /** The way down: feet from y42 (a >= 18) to y7 (a <= -17), one block per step. */
    private static final int DESCENT_TOP_A = 18;
    private static final int DESCENT_HEADROOM = 4;

    // ---- Parts of the hall -----------------------------------------------------------------------
    static final int RING = 0;
    private static final int WALL = 1;
    private static final int DECK = 2;
    private static final int POOL = 3;
    private static final int STAIRS = 4;
    private static final int LANDING = 5;
    private static final int NICHE = 6;
    private static final int NICHE_LIGHT = 7;
    private static final int NICHE_LINING = 8;
    private static final int DESCENT = 9;
    private static final int DOOR_LOW = 10;
    private static final int DOOR_HIGH = 11;

    /** Underwater lamps every 6 blocks along each side of the pool. */
    private static final int[] LAMPS = {-15, -9, -3, 3, 9, 15};
    /**
     * Hidden light (an invisible, waterlogged vanilla light block) in the pool every 5 blocks, two
     * blocks under the surface: the whole pool glows bright turquoise from within, with Iris and
     * plain rendering too (they light everything from the vanilla level), while level 11 has faded
     * out about 10 blocks above the water and leaves the hall itself dark.
     */
    private static final int POOL_GLOW_SPACING = 5;
    private static final int POOL_GLOW_Y = DECK_Y - 2;
    private static final int POOL_GLOW_LEVEL = 11;
    /**
     * Step lights: a dim lamp set into the stair wall just above every second tread, so the flight
     * reads from anywhere in the hall as a rising line of lights, and hidden light over every
     * fourth step (and at the foot and the landing) so the white treads themselves stand out.
     */
    private static final int STEP_LIGHT_EVERY = 2;
    private static final int STEP_GLOW_EVERY = 4;
    private static final int STEP_GLOW_LEVEL = 10;

    /**
     * The hall's orientation at one exit: {@code in} points from the stair wall into the hall,
     * {@code up} the way the stairs climb. Random per exit, fixed by the seed.
     */
    record Orientation(Direction in, Direction up) {
        int a(int dx, int dz) {
            return dx * up.getStepX() + dz * up.getStepZ();
        }

        int p(int dx, int dz) {
            return dx * in.getStepX() + dz * in.getStepZ();
        }

        int dx(int a, int p) {
            return a * up.getStepX() + p * in.getStepX();
        }

        int dz(int a, int p) {
            return a * up.getStepZ() + p * in.getStepZ();
        }
    }

    private static final Orientation[] ORIENTATIONS = new Orientation[8];

    static {
        for (int i = 0; i < 8; i++) {
            Direction in = Direction.from2DDataValue(i & 3);
            ORIENTATIONS[i] = new Orientation(in, (i & 4) == 0 ? in.getClockWise() : in.getCounterClockWise());
        }
    }

    /** The orientation of the hall of exit region (regionX, regionZ). */
    static Orientation orientation(long seed, int regionX, int regionZ) {
        return ORIENTATIONS[Hash.range(Hash.of(seed, regionX, regionZ, 5310), 8)];
    }

    /** {dx, dz} from the hall's centre to the column where the way out triggers (the back of the niche). */
    static int[] wayOut(Orientation o) {
        return new int[] {o.dx(LANDING_A, -INSIDE - 2), o.dz(LANDING_A, -INSIDE - 2)};
    }

    /**
     * True when feet in block {@code feetY} of hall column (a, p) stand in the back of the daylit
     * niche at the top of the stairs: the way out.
     */
    static boolean isWayOut(int a, int p, int feetY) {
        return p == -INSIDE - 2 && (a == LANDING_A || a == LANDING_A + 1)
                && feetY >= LANDING_FEET_Y && feetY < LANDING_FEET_Y + NICHE_HEIGHT - 1;
    }

    /** Which part of the hall column (a, p) is (chessboard ring <= {@link #RADIUS}). */
    static int part(int a, int p) {
        int r = Math.max(Math.abs(a), Math.abs(p));
        if (r == RADIUS) {
            return RING;
        }
        if (r > INSIDE) {
            return wallPart(a, p);
        }
        if (p <= -INSIDE + 2 && a >= STAIR_START) {
            return a >= LANDING_A ? LANDING : STAIRS;
        }
        if (a >= STAIR_START && p >= -INSIDE + 3 && p <= INSIDE - 2) {
            return POOL;
        }
        return DECK;
    }

    private static int wallPart(int a, int p) {
        boolean niche = a == LANDING_A || a == LANDING_A + 1;
        if (p == -INSIDE - 1 || p == -INSIDE - 2) {
            if (niche) {
                return NICHE;
            }
            if (a == LANDING_A - 1 || a == LANDING_A + 2) {
                return NICHE_LINING;
            }
        }
        if (p == -INSIDE - 3 && niche) {
            return NICHE_LIGHT;
        }
        if ((p == INSIDE + 2 || p == INSIDE + 3) && Math.abs(a) <= INSIDE) {
            return DESCENT;
        }
        if (p == INSIDE + 1 && (a == -INSIDE || a == -INSIDE + 1)) {
            return DOOR_LOW;
        }
        if (p == WALL_OUTER && (a == INSIDE || a == INSIDE - 1)) {
            return DOOR_HIGH;
        }
        return WALL;
    }

    /** Feet height on the way down at a: y42 at the top (a >= 18), one lower per step, y7 at the bottom. */
    private static int descentFeet(int a) {
        return Math.max(DECK_Y + 1, Math.min(LANDING_FEET_Y - 1, a + (LANDING_FEET_Y - 1 - DESCENT_TOP_A)));
    }

    /** The ceiling over ring r inside the hall: straight up to y46 at the walls, then up to the sky. */
    private static int ceilingY(int r) {
        return SKY_Y - (r - SKY_HALF);
    }

    /**
     * Writes hall column (a, p) at world offset (dx, dz) from the centre. Not called for the ring
     * (the layout builds that as an ordinary dry walkway).
     */
    static void fill(Orientation o, int a, int p, int dx, int dz, int part, LevelLayout.Column out) {
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        int r = Math.max(Math.abs(a), Math.abs(p));
        switch (part) {
            case WALL -> {
                solid(out, 0, SKY_Y, tile);
                cornerLamp(o, a, p, out);
                wallLamp(o, a, p, out);
                stepLamp(o, a, p, out);
            }
            case NICHE, NICHE_LIGHT, NICHE_LINING -> fillNiche(o, part, out, tile);
            case DESCENT -> {
                int feet = descentFeet(a);
                solid(out, 0, feet - 2, tile);
                boolean step = descentFeet(a - 1) < feet;
                out.set(feet - 1, step ? stairs(o.up(), Half.BOTTOM) : tile);
                solid(out, feet + DESCENT_HEADROOM, SKY_Y, tile);
            }
            case DOOR_LOW -> {
                solid(out, 0, DECK_Y, tile);
                solid(out, DECK_Y + 4, SKY_Y, tile);
            }
            case DOOR_HIGH -> {
                solid(out, 0, LANDING_FEET_Y - 2, tile);
                solid(out, LANDING_FEET_Y + 3, SKY_Y, tile);
            }
            default -> fillInside(o, a, p, dx, dz, r, part, out, tile);
        }
    }

    private static void fillInside(Orientation o, int a, int p, int dx, int dz, int r, int part, LevelLayout.Column out,
                                   BlockState tile) {
        switch (part) {
            case POOL -> {
                out.set(0, tile);
                // Three dark lane lines along the hall, pointing at the stairs.
                boolean lane = (p == -9 || p == 0 || p == 9) && a >= -14 && a <= 16;
                out.set(POOL_FLOOR_Y, lane ? ModBlocks.POOL_TILE_DARK.get().defaultBlockState()
                        : ModBlocks.POOL_FLOOR.get().defaultBlockState());
                BlockState water = Blocks.WATER.defaultBlockState();
                for (int y = POOL_FLOOR_Y + 1; y <= DECK_Y; y++) {
                    out.set(y, water);
                }
                if (Math.floorMod(a, POOL_GLOW_SPACING) == 0 && Math.floorMod(p, POOL_GLOW_SPACING) == 0) {
                    out.set(POOL_GLOW_Y, glow(POOL_GLOW_LEVEL, true));
                }
                poolEdge(o, a, p, out);
            }
            case STAIRS -> {
                int top = stairTop(a);
                solid(out, 0, top - 1, tile);
                out.set(top, stairs(o.up(), Half.BOTTOM));
                if (p == -INSIDE + 2 && contains(LAMPS, a)) {
                    out.set(LAMP_Y, lamp(o.in()));
                }
                if (p == -INSIDE + 1 && (a - STAIR_START) % STEP_GLOW_EVERY == 0) {
                    out.set(top + 2, glow(STEP_GLOW_LEVEL, false)); // head height over the middle of the flight
                }
            }
            case LANDING -> {
                solid(out, 0, LANDING_FEET_Y - 1, tile);
                if (p == -INSIDE + 1 && a == LANDING_A + 1) {
                    out.set(LANDING_FEET_Y + 1, glow(STEP_GLOW_LEVEL, false));
                }
            }
            default -> { // the deck
                solid(out, 0, DECK_Y, tile);
                if (p == INSIDE - 1 && contains(LAMPS, a)) {
                    out.set(LAMP_Y, lamp(o.in().getOpposite()));
                } else if (a == STAIR_START - 1 && contains(LAMPS, p)) {
                    out.set(LAMP_Y, lamp(o.up()));
                } else if (a == STAIR_START - 1 && p == -INSIDE + 1) {
                    out.set(DECK_Y + 2, glow(STEP_GLOW_LEVEL, false)); // the foot of the stairs
                }
            }
        }
        // The ceiling: a 45-degree slope up from every wall to the skylight.
        if (r <= SKY_HALF) {
            out.set(SKY_Y, ModBlocks.POOL_SKY.get().defaultBlockState());
        } else if (Math.abs(dx) == Math.abs(dz)) {
            out.set(ceilingY(r), tile); // the four diagonal valleys stay plain blocks
        } else {
            // An upside-down step whose lower half sits on the outward side, so the steps of the
            // slope read as one smooth slant from below.
            Direction outward = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            out.set(ceilingY(r), stairs(outward, Half.TOP));
        }
    }

    /** Ladders at the far edge and the near end, and the plank floating by the far edge. */
    private static void poolEdge(Orientation o, int a, int p, LevelLayout.Column out) {
        if (p == INSIDE - 2 && (a == -12 || a == 2 || a == 14)) {
            out.set(DECK_Y, ladder(o.in()));
        } else if (a == STAIR_START && p == 8) {
            out.set(DECK_Y, ladder(o.up().getOpposite()));
        } else if (p == INSIDE - 7 && a >= 6 && a <= 9) {
            // A plank adrift near the edge: a top slab floats half out of the water.
            out.set(DECK_Y, Blocks.BIRCH_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP).setValue(SlabBlock.WATERLOGGED, true));
        }
    }

    /** The daylit niche at the top of the stairs: Level 37's fine tile, then a wall of daylight. */
    private static void fillNiche(Orientation o, int part, LevelLayout.Column out, BlockState tile) {
        int floor = LANDING_FEET_Y - 1;
        int top = LANDING_FEET_Y + NICHE_HEIGHT - 1;
        solid(out, 0, floor - 1, tile);
        out.set(floor, ModBlocks.L37_TILE_FLOOR.get().defaultBlockState());
        BlockState inside = switch (part) {
            case NICHE_LIGHT -> ModBlocks.L37_SUNLIT_VOID.get().defaultBlockState().setValue(SunlitVoidBlock.FACING, o.in());
            case NICHE_LINING -> ModBlocks.L37_TILE_WALL.get().defaultBlockState();
            default -> null;
        };
        if (inside != null) {
            solid(out, floor + 1, top, inside);
        }
        out.set(top + 1, ModBlocks.L37_TILE_CEILING.get().defaultBlockState());
        solid(out, top + 2, SKY_Y, tile);
    }

    /** The underwater lamps set into the wall at the landing's end of the pool. */
    private static void wallLamp(Orientation o, int a, int p, LevelLayout.Column out) {
        if (a == INSIDE + 1 && contains(LAMPS, p)) {
            out.set(LAMP_Y, lamp(o.up().getOpposite()));
        }
    }

    /** A lamp in each corner of the hall, just above the deck (one corner is over the pool). */
    private static void cornerLamp(Orientation o, int a, int p, LevelLayout.Column out) {
        if (a == -INSIDE - 1 && (p == -INSIDE || p == INSIDE)) {
            out.set(CORNER_LAMP_Y, lamp(o.up()));
        } else if (a == INSIDE + 1 && (p == INSIDE || p == -INSIDE + 3)) {
            out.set(CORNER_LAMP_Y, lamp(o.up().getOpposite()));
        }
    }

    private static BlockState lamp(Direction facing) {
        return ModBlocks.POOL_LAMP.get().defaultBlockState().setValue(PoolLampBlock.FACING, facing);
    }

    /** Top block of the step at a (the first, a = -18, is one block above the deck). */
    private static int stairTop(int a) {
        return DECK_Y + 1 + (a - STAIR_START);
    }

    /** Step lights: a dim lamp in the stair wall just above every second tread, facing into the hall. */
    private static void stepLamp(Orientation o, int a, int p, LevelLayout.Column out) {
        if (p == -INSIDE - 1 && a >= STAIR_START && a < LANDING_A && (a - STAIR_START) % STEP_LIGHT_EVERY == 0) {
            out.set(stairTop(a) + 1, ModBlocks.POOL_LAMP_DIM.get().defaultBlockState().setValue(PoolLampBlock.FACING, o.in()));
        }
    }

    /** Invisible vanilla light (in the water: waterlogged, so the pool never meets air). */
    private static BlockState glow(int level, boolean underwater) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level).setValue(LightBlock.WATERLOGGED, underwater);
    }

    private static BlockState ladder(Direction towardDeck) {
        return ModBlocks.L37_POOL_LADDER.get().defaultBlockState()
                .setValue(PoolLadderBlock.FACING, towardDeck).setValue(PoolLadderBlock.WATERLOGGED, true);
    }

    private static BlockState stairs(Direction facing, Half half) {
        return ModBlocks.POOL_TILE_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }

    private static void solid(LevelLayout.Column out, int from, int to, BlockState state) {
        for (int y = from; y <= to; y++) {
            out.set(y, state);
        }
    }

    private static boolean contains(int[] values, int v) {
        for (int value : values) {
            if (value == v) {
                return true;
            }
        }
        return false;
    }

    private PoolroomsHall() {}
}
