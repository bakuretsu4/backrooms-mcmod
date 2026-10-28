package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.block.AxisBlock;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.Level2LightBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The occasional unusual stretches of Level 2's corridor (segment types SEG_DOGLEG, SEG_DUCT,
 * SEG_CATWALK and SEG_BYPASS in {@link Level2Line}). Each is something a real service tunnel does
 * when it meets an obstacle, so the level stays believable: the corridor never branches, and you
 * can always walk on.
 *
 * <ul>
 *   <li><b>Dog-leg</b>: a massive concrete column stands in the middle of the corridor. The wall on
 *       one side is pushed back three blocks for nine blocks, so you step sideways round it.</li>
 *   <li><b>Duct crossing</b>: a big galvanised duct crosses under the ceiling, boxed into a low
 *       concrete soffit: four blocks where the ceiling drops (2.2 blocks of headroom, no crouching).</li>
 *   <li><b>Catwalk</b>: two levels. On one side the floor gives way to an open trench four blocks
 *       deep with a walkway of its own at the bottom; a grated catwalk with a railing runs along the
 *       other side over the lower level. A ladder at the west end joins the two.</li>
 *   <li><b>Bypass</b>: the corridor is sealed by two concrete walls with locked steel doors; a
 *       one-wide, low passage runs round them behind the corridor wall.</li>
 * </ul>
 *
 * <p>Seen across the corridor everything is counted in q = dz * side, the distance from the centre
 * line towards the detour's side ({@link Level2Line#detourSide}): q = 1 the lane, 2 the shoulder,
 * 3 the chase, 4 the wall. The dog-leg, viewed from above (side = south, so q grows downwards):
 * <pre>
 *   q-4  # # # # # # # # # # # #
 *   q-3  P P P P P P P P P P P P     P: the far wall's chase, untouched
 *   q-2  . . . . C C C . . . . .     C: the column (q -2..1, three blocks along x)
 *   q 0  . . . . C C C . . . . .
 *   q 2  . . . . . * . . . . . .     *: a lamp on the column's face
 *   q 3  P # . . . . . . . # P P     #: concrete piers closing the widened part
 *   q 5  P # . . . . . . . # P P
 *   q 6  # # r r r * r r r # # #     r: a short chase on the new wall, its own lamp
 *   q 7  # # # # # # # # # # # #
 * </pre>
 *
 * <p>Three entry points from {@link Level2Layout}: {@link #column} (what a column is, for walking,
 * arrivals and spawning), {@link #fill} (columns this class builds whole) and {@link #after}
 * (small changes on top of the normal corridor). All pure functions of (seed, x, z), no allocation.
 */
final class Level2Detours {
    /** The dog-leg's widened part reaches this far each way of the column (its closing piers). */
    static final int DOG_HALF = 5;
    /** The bypass passage reaches this far each way (its two entries). */
    static final int BYPASS_HALF = 7;
    /** The sealing walls stand this far each way from the middle of a bypass. */
    static final int SEAL_HALF = 5;
    /** The catwalk's trench reaches this far each way (its end walls one further). */
    static final int CAT_HALF = 8;
    /** The trench floor lies this far below the corridor floor (the lower walkway: three blocks of headroom). */
    static final int CAT_DEPTH = 4;
    /** Blocks of headroom in the bypass passage and its entries: one less than the corridor, concrete above. */
    private static final int BYPASS_HEADROOM = 3;

    private Level2Detours() {}

    /** True for the segment types this class builds. */
    static boolean isDetour(int type) {
        return type == Level2Line.SEG_DOGLEG || type == Level2Line.SEG_DUCT
                || type == Level2Line.SEG_CATWALK || type == Level2Line.SEG_BYPASS;
    }

    // ==========================================================================================
    // Column codes
    // ==========================================================================================

    /**
     * The column code (see {@link Level2Layout}) a detour gives column (x, centre + dz), or -1 where
     * the corridor is as usual.
     */
    static int column(long seed, int band, int x, int dz) {
        int n = Level2Line.segment(seed, band, x);
        int type = Level2Line.segType(seed, band, n);
        if (type != Level2Line.SEG_DOGLEG && type != Level2Line.SEG_CATWALK && type != Level2Line.SEG_BYPASS) {
            return -1; // the duct crossing only changes what hangs overhead
        }
        int side = Level2Line.detourSide(seed, band, n);
        int m = x - Level2Line.segMid(seed, band, n);
        int q = dz * side;
        int am = Math.abs(m);
        if (type == Level2Line.SEG_DOGLEG) {
            if (am > DOG_HALF) {
                return -1;
            }
            if (am == DOG_HALF) {
                return q >= 3 && q <= 6 ? Level2Layout.CHASE : q == 7 ? Level2Layout.ROCK : -1;
            }
            if (am <= 1 && q >= -2 && q <= 1) {
                return Level2Layout.CHASE; // the column
            }
            if (q >= 2 && q <= 5) {
                return Level2Layout.OPEN;
            }
            return q == 6 ? Level2Layout.CHASE : q == 7 ? Level2Layout.ROCK : -1;
        }
        if (type == Level2Line.SEG_BYPASS) {
            if (am < SEAL_HALF && Math.abs(q) <= Level2Line.CHASE) {
                return Level2Layout.CHASE; // the sealed section: nobody walks (or arrives) in there
            }
            if (am == SEAL_HALF && Math.abs(q) <= Level2Line.CHASE) {
                return Level2Layout.CHASE; // the sealing walls
            }
            if (am <= BYPASS_HALF && q == Level2Line.WALL + 1) {
                return Level2Layout.OPEN; // the passage
            }
            if (am == BYPASS_HALF && (q == Level2Line.CHASE || q == Level2Line.WALL)) {
                return Level2Layout.OPEN; // its two entries through the chase and the wall
            }
            if (am <= BYPASS_HALF && q == Level2Line.WALL + 2) {
                return Level2Layout.ROCK;
            }
            return -1;
        }
        // Catwalk: the trench (q -3..-1) and the wall beyond it are no place to stand at floor level.
        if (am <= CAT_HALF && q >= -Level2Line.WALL && q <= -1) {
            return Level2Layout.CHASE;
        }
        return -1;
    }

    // ==========================================================================================
    // Columns built here
    // ==========================================================================================

    /**
     * Builds column (x, centre + dz) of a detour and returns true, or returns false to let the
     * normal corridor build it (after maybe adding blocks below the floor, which it never touches).
     */
    static boolean fill(Level2Layout layout, long seed, int band, int n, int type, int x, int dz, LevelLayout.Column out) {
        if (type == Level2Line.SEG_DOGLEG) {
            return fillDogleg(layout, seed, band, n, x, dz, out);
        }
        if (type == Level2Line.SEG_BYPASS) {
            return fillBypass(layout, seed, band, n, x, dz, out);
        }
        if (type == Level2Line.SEG_CATWALK) {
            return fillCatwalk(layout, seed, band, n, x, dz, out);
        }
        return false;
    }

    private static boolean fillDogleg(Level2Layout layout, long seed, int band, int n, int x, int dz, LevelLayout.Column out) {
        int side = Level2Line.detourSide(seed, band, n);
        int mid = Level2Line.segMid(seed, band, n);
        int m = x - mid;
        int am = Math.abs(m);
        int q = dz * side;
        if (am > DOG_HALF) {
            return false;
        }
        int floor = layout.floorY();
        int top = layout.ceilingY() - 1;
        Direction toSide = Level2Line.wallOf(side); // from the lane towards the widened side
        Direction in = Level2Line.inwardOf(side);   // from the new wall back towards the lane
        if (am == DOG_HALF) {
            if (q >= 3 && q <= 6) {
                solid(layout, out, true); // the piers closing the widened part: clean concrete corners
                return true;
            }
            return false;
        }
        if (am <= 1 && q >= -2 && q <= 1) {
            solid(layout, out, true); // the column, floor to ceiling: the pipes overhead run into it
            return true;
        }
        if (q >= 2 && q <= 5) {
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            // Overhead: the shoulder's fat main follows the jog; a copper line and a steel one beyond.
            out.set(top, switch (q) {
                case 2 -> Level2Parts.hung(Direction.Axis.X, Kind.INSULATED, Size.LARGE, Math.floorMod(x + 4, 5) == 0, false);
                case 4 -> Level2Parts.hung(Direction.Axis.X, Kind.COPPER, Size.SMALL, Math.floorMod(x, 5) == 0, false);
                case 5 -> Level2Parts.hung(Direction.Axis.X, Kind.STEEL, Size.MEDIUM, Math.floorMod(x + 2, 5) == 0, false);
                default -> Blocks.AIR.defaultBlockState();
            });
            if (q == 2 && m == 0) {
                out.set(floor + 3, Level2Parts.lamp(toSide, false)); // on the column's face
            } else if (q == 5 && Math.abs(m) == 3) {
                out.set(floor + 1, Level2Parts.puddle()); // water pools where the floor dips by the new wall
            }
            return true;
        }
        if (q == 6) {
            // The new wall's own short chase: the insulated main low down, a pier with a lamp in the middle.
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            if (m == 0) {
                solid(layout, out, false);
                out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.CONDUIT, Size.SMALL, false, false));
                return true;
            }
            out.set(floor + 1, Level2Parts.duct(in, Math.floorMod(x, 4) == 0));
            out.set(floor + 2, Level2Parts.pipe(Direction.Axis.X, toSide, Kind.STEEL, Size.MEDIUM, am == 2));
            out.set(floor + 3, am == 3 ? Level2Parts.gauge(in)
                    : Level2Parts.pipe(Direction.Axis.X, toSide, Kind.PAINTED_GREEN, Size.SMALL, am == 2));
            out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.CONDUIT, Size.SMALL, false, false));
            return true;
        }
        return false;
    }

    private static boolean fillBypass(Level2Layout layout, long seed, int band, int n, int x, int dz, LevelLayout.Column out) {
        int side = Level2Line.detourSide(seed, band, n);
        int mid = Level2Line.segMid(seed, band, n);
        int m = x - mid;
        int am = Math.abs(m);
        int q = dz * side;
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        if (am == SEAL_HALF && Math.abs(q) <= Level2Line.CHASE) {
            // A sealing wall of poured concrete with a locked steel door on the centre line. The
            // door FACING points into the sealed part, so its slab sits flush on the side you see.
            solid(layout, out, true);
            if (q == 0) {
                BlockState door = Level2Blocks.STEEL_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, m < 0 ? Direction.EAST : Direction.WEST)
                        .setValue(HouseDoorBlock.LOCKED, true);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            return true;
        }
        if (am <= BYPASS_HALF && q == Level2Line.WALL + 1) {
            // The passage: one block wide, plain poured concrete all round (nothing to brush past),
            // its ceiling one block lower than the corridor's.
            Direction in = Level2Line.inwardOf(side);
            BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            for (int y = floor + 1 + BYPASS_HEADROOM; y < ceiling; y++) {
                out.set(y, wall);
            }
            out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
            // Its lights, all just under the low ceiling (above head height): a caged lamp halfway
            // (on the series circuits like the corridor's), and amber battery lamps that are always
            // on (like an exit's), on the outer wall and on the two end walls where it turns out to
            // the corridor. The passage is never black: dim amber at worst.
            if (m == 0) {
                out.set(floor + BYPASS_HEADROOM, Level2Parts.lamp(in, false));
            } else if (am == BYPASS_HALF - 3) {
                out.set(floor + BYPASS_HEADROOM, Level2LightBlocks.emergency(in, false, true));
            } else if (am == BYPASS_HALF) {
                out.set(floor + BYPASS_HEADROOM, Level2LightBlocks.emergency(m < 0 ? Direction.EAST : Direction.WEST, false, true));
            }
            if (m == 2) {
                out.set(floor + 1, Level2Parts.puddle());
            }
            return true;
        }
        if (am == BYPASS_HALF && (q == Level2Line.CHASE || q == Level2Line.WALL)) {
            // An entry: a plain opening through the chase and the wall, a concrete lintel above it.
            BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            for (int y = floor + 1 + BYPASS_HEADROOM; y < ceiling; y++) {
                out.set(y, wall);
            }
            return true;
        }
        return false;
    }

    private static boolean fillCatwalk(Level2Layout layout, long seed, int band, int n, int x, int dz, LevelLayout.Column out) {
        int side = Level2Line.detourSide(seed, band, n);
        int mid = Level2Line.segMid(seed, band, n);
        int m = x - mid;
        int am = Math.abs(m);
        int q = dz * side;
        int floor = layout.floorY();
        int bottom = floor - CAT_DEPTH;
        int top = layout.ceilingY() - 1;
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        if (am == CAT_HALF + 1 && q >= -Level2Line.CHASE && q <= 2) {
            for (int y = bottom; y < floor; y++) {
                out.set(y, wall); // the trench's end walls
            }
            return false;
        }
        if (am > CAT_HALF || q > Level2Line.CHASE || q < -Level2Line.WALL) {
            return false;
        }
        if (q == Level2Line.CHASE || q == -Level2Line.WALL) {
            for (int y = bottom; y < floor; y++) {
                out.set(y, wall); // the trench's long walls
            }
            if (q == Level2Line.CHASE) {
                return false; // the catwalk side's chase stays as it is
            }
            fillRockColumn(layout, out);
            return true;
        }
        if (q >= 0) {
            out.set(bottom, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            return false; // the catwalk itself: the normal corridor, grated afterwards (see after)
        }
        // The open trench (q -3..-1): the lower walkway's floor, the far chase moved down onto it.
        Direction farIn = Level2Line.inwardOf(-side);
        Direction farWall = Level2Line.wallOf(-side);
        out.set(bottom, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (q == -Level2Line.CHASE) {
            out.set(bottom + 1, Level2Parts.duct(farIn, Math.floorMod(x, 6) == 0));
            out.set(bottom + 2, Level2Parts.pipe(Direction.Axis.X, farWall, Kind.STEEL, Size.MEDIUM, Math.floorMod(x, 4) == 0));
            out.set(floor - 1, Math.floorMod(m, 6) == 3 ? Level2Parts.lamp(farIn, false)
                    : Level2Parts.pipe(Direction.Axis.X, farWall, Kind.PAINTED_RED, Size.SMALL, Math.floorMod(x + 1, 4) == 0));
            out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.CONDUIT, Size.SMALL, Math.floorMod(x + 2, 5) == 0, false));
        } else if (q == -2) {
            out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.INSULATED, Size.LARGE, Math.floorMod(x + 4, 5) == 0, false));
        } else {
            out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.STEEL, Size.SMALL, Math.floorMod(x, 5) == 0, false));
            if (m == -CAT_HALF) {
                // The ladder down, against the trench's west end wall (FACING away from it).
                BlockState ladder = Level0Blocks.LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
                for (int y = bottom + 1; y <= floor; y++) {
                    out.set(y, ladder);
                }
            } else {
                // The railing along the catwalk's edge: a run of steel bars, waist high, joined end to end.
                out.set(floor + 1, Blocks.IRON_BARS.defaultBlockState()
                        .setValue(IronBarsBlock.EAST, m < CAT_HALF)
                        .setValue(IronBarsBlock.WEST, m > -CAT_HALF + 1));
            }
        }
        return true;
    }

    // ==========================================================================================
    // Small changes on top of the normal corridor
    // ==========================================================================================

    /** Called after the normal corridor built column (x, centre + dz) of segment n. */
    static void after(Level2Layout layout, long seed, int band, int n, int type, int x, int dz, LevelLayout.Column out) {
        if (!isDetour(type)) {
            return;
        }
        int floor = layout.floorY();
        int top = layout.ceilingY() - 1;
        int mid = Level2Line.segMid(seed, band, n);
        int m = x - mid;
        int a = Math.abs(dz);
        if (type == Level2Line.SEG_DUCT) {
            // The duct crossing: a concrete soffit four blocks long, the duct (two blocks wide) under
            // its middle, running from wall to wall across the corridor at head height + 1.
            if (a <= Level2Line.CHASE && m >= -2 && m <= 1) {
                out.set(top, ModBlocks.PIPE_WALL.get().defaultBlockState());
                if (m == -1 || m == 0) {
                    out.set(top - 1, ModBlocks.L1_DUCT.get().defaultBlockState().setValue(AxisBlock.AXIS, Direction.Axis.Z));
                }
            }
            return;
        }
        int side = Level2Line.detourSide(seed, band, n);
        int q = dz * side;
        if (type == Level2Line.SEG_DOGLEG) {
            // Painted arrows on the column's two end faces: which way round.
            if (q == 0 && Math.abs(m) == 2) {
                boolean lookingEast = m < 0;
                Variant arrow = lookingEast == (side > 0) ? Variant.ARROW_RIGHT : Variant.ARROW_LEFT;
                out.set(floor + 2, Level2Parts.stencil(m < 0 ? Direction.WEST : Direction.EAST, arrow));
            }
            return;
        }
        if (type == Level2Line.SEG_BYPASS) {
            int am = Math.abs(m);
            if (am == SEAL_HALF + 1 && q == 2) {
                // On each sealing wall, beside the door: an arrow pointing to the bypass.
                boolean lookingEast = m < 0;
                Variant arrow = lookingEast == (side > 0) ? Variant.ARROW_RIGHT : Variant.ARROW_LEFT;
                out.set(floor + 2, Level2Parts.stencil(m < 0 ? Direction.WEST : Direction.EAST, arrow));
            } else if (am == SEAL_HALF + 1 && q == 0) {
                // An amber battery lamp over each sealed door, waiting for the power to fail.
                out.set(floor + 3, Level2LightBlocks.emergency(m < 0 ? Direction.WEST : Direction.EAST, true, true));
            } else if (am == BYPASS_HALF && q == 2) {
                out.set(floor + 2, Blocks.AIR.defaultBlockState()); // nothing hangs in front of an entry
                out.set(floor + 3, Blocks.AIR.defaultBlockState());
            }
            return;
        }
        // Catwalk: the walkway over the trench is grating (you see the lower level through it), and a
        // lamp hangs under it on the catwalk side's trench wall.
        if (Math.abs(m) <= CAT_HALF && q >= 0 && q <= 2) {
            out.set(floor, Level2Parts.grating(Direction.Axis.X));
            if (q == 2 && Math.floorMod(m, 6) == 0) {
                out.set(floor - 1, Level2Parts.lamp(Level2Line.inwardOf(side), false));
            } else if (q == 2) {
                out.set(floor - CAT_DEPTH + 1, Level2Parts.duct(Level2Line.inwardOf(side), Math.floorMod(x, 6) == 3));
            }
        }
    }

    // ==========================================================================================
    // Helpers
    // ==========================================================================================

    /** Poured concrete from floor to ceiling (the scuffed base row at the bottom); {@code full}: up to the ceiling. */
    private static void solid(Level2Layout layout, LevelLayout.Column out, boolean full) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(floor + 1, Level2Blocks.WALL_BASE.get().defaultBlockState());
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        for (int y = floor + 2; y < (full ? ceiling : ceiling - 1); y++) {
            out.set(y, wall);
        }
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
    }

    /** A plain wall column, as {@code Level2Layout.fillRock} builds it. */
    private static void fillRockColumn(Level2Layout layout, LevelLayout.Column out) {
        solid(layout, out, true);
    }
}
