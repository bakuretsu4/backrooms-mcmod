package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.MarkingBlock;
import com.rickr.backrooms.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * The Level 94 castle as a fixed 3D template, built once when the class loads: one small number
 * ("code") per block, relative to the castle centre. {@link Level94Castle#fillColumn} then only
 * looks codes up, which keeps world generation fast.
 *
 * <p>Template coordinates: lx = east, lz = south, ly = height above the plateau surface (ly 0 is
 * the floor block at y150, so you stand at ly 1). The gate is on the NORTH side (lz = -17); the
 * world turns the whole template so the gate faces the top of the ramp.
 *
 * <p>Outside it is the cartoon castle of cc3: white tiered walls, round turrets with red cones,
 * narrow windows, a spire up to ly 65 (y215). Inside, the cartoon fades into a real (and wrong)
 * kids' play centre:
 * <pre>
 *  ground floor (ly 1-8)                         upper floor (ly 10-16)
 *  z -16..-8  entrance hall (whole width)         ring corridor ("looping hall") round
 *  z  -6..4   ball pit | gallery | playground     the animation studio, x -14..4, z -16..4
 *  z   6..15  throne room (ly 1-23, whole width)  (reached by the playground's climbing frame)
 * </pre>
 */
final class Level94CastlePlan {
    /** The template covers lx, lz in [-HALF, HALF]. */
    static final int HALF = 20;
    static final int MIN_LY = -3;
    static final int MAX_LY = Level94Castle.CASTLE_TOP_Y - Level94Castle.MOUNTAIN_TOP_Y;
    private static final int SIZE = 2 * HALF + 1;
    private static final int LAYERS = MAX_LY - MIN_LY + 1;

    /** Not part of the castle: the world leaves whatever is there (plateau paving, sky). */
    static final int OUT = 0;
    /** Air inside the building (rooms). Nothing is written, but it counts as "inside the castle". */
    static final int AIR = 1;

    // ------------------------------------------------------------------------------------------
    // Palette: code -> block. The blocks are only looked up the first time the castle is built
    // into the world (registries are ready by then), so this class can load any time.
    // ------------------------------------------------------------------------------------------

    /** Code -> where its block comes from. Codes 0 (OUT) and 1 (AIR) write nothing. */
    private static final List<Supplier<BlockState>> SOURCES = new ArrayList<>();

    static {
        SOURCES.add(() -> null);
        SOURCES.add(() -> null);
    }

    private static int reg(Supplier<BlockState> source) {
        SOURCES.add(source);
        return SOURCES.size() - 1;
    }

    private static int reg(DeferredBlock<? extends Block> block) {
        return reg(() -> block.get().defaultBlockState());
    }

    /** One code per horizontal FACING, indexed by Direction.get2DDataValue() (S, W, N, E). */
    private static int[] facing(DeferredBlock<? extends Block> block) {
        int[] codes = new int[4];
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            codes[dir.get2DDataValue()] = reg(() -> block.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir));
        }
        return codes;
    }

    private static final int S = Direction.SOUTH.get2DDataValue();
    private static final int W = Direction.WEST.get2DDataValue();
    private static final int N = Direction.NORTH.get2DDataValue();
    private static final int E = Direction.EAST.get2DDataValue();

    // Outside: cartoon
    private static final int WALL = reg(ModBlocks.L94_CASTLE_WALL);
    private static final int WINDOW = reg(ModBlocks.L94_CASTLE_WINDOW);
    private static final int ROOF = reg(ModBlocks.L94_CASTLE_ROOF);
    private static final int[] ROOF_STAIRS = facing(ModBlocks.L94_CASTLE_ROOF_STAIRS);
    // Inside: realistic play centre
    private static final int STONE = reg(ModBlocks.L94_CASTLE_STONE);
    private static final int[] STONE_STAIRS = facing(ModBlocks.L94_CASTLE_STONE_STAIRS);
    private static final int LAMP = reg(ModBlocks.L94_CASTLE_LAMP);
    private static final int CHECKER = reg(ModBlocks.L94_CHECKER_FLOOR);
    private static final int STRIPES = reg(() -> ModBlocks.L94_STRIPE_PATH.get().defaultBlockState().setValue(MarkingBlock.AXIS, Direction.Axis.Z));
    private static final int STAR = reg(ModBlocks.L94_STAR_TILE);
    private static final int DISC = reg(ModBlocks.L94_DISC_FLOOR);
    private static final int RING = reg(ModBlocks.L94_RING_FLOOR);
    private static final int PILLAR = reg(ModBlocks.L94_PLAY_PILLAR);
    private static final int PORTHOLE = reg(ModBlocks.L94_PLAY_PILLAR_PORTHOLE);
    private static final int PIPE_X = reg(() -> ModBlocks.L94_PLAY_PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
    private static final int PIPE_Z = reg(() -> ModBlocks.L94_PLAY_PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
    private static final int PLAY_WALL = reg(ModBlocks.L94_PLAY_WALL);
    private static final int CEILING = reg(ModBlocks.L94_PLAY_CEILING);
    private static final int MAT = reg(ModBlocks.L94_PLAY_MAT);
    private static final int BALLS = reg(ModBlocks.L94_BALL_PIT);
    private static final int FRAME = reg(ModBlocks.L94_CLIMBING_FRAME);
    private static final int TUBE = reg(ModBlocks.L94_PLAY_TUBE);
    private static final int[] SLIDE = facing(ModBlocks.L94_SLIDE);
    private static final int[] DESK = facing(ModBlocks.L94_DRAWING_DESK);
    private static final int[] SKETCH = facing(ModBlocks.L94_SKETCH_WALL);
    private static final int TABLE = reg(ModBlocks.L94_STUDIO_TABLE);
    private static final int[] MINI_HOUSE = facing(ModBlocks.L94_MINI_HOUSE);
    private static final int MINI_TREE = reg(ModBlocks.L94_MINI_TREE);
    private static final int MINI_FOUNTAIN = reg(ModBlocks.L94_MINI_FOUNTAIN);
    private static final int CARPET = reg(ModBlocks.L94_RED_CARPET);
    private static final int[] THRONE = facing(ModBlocks.L94_THRONE);
    private static final int LIGHT = reg(ModBlocks.L94_THRONE_LIGHT);

    private static final byte[] CELLS = new byte[SIZE * SIZE * LAYERS];

    static {
        buildShell();
        buildWindows();
        buildEntranceHall();
        buildBallPit();
        buildGallery();
        buildPlayground();
        buildUpperFloor();
        buildThroneRoom();
        buildRoofsAndTowers();
    }

    // ------------------------------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------------------------------

    /** The blocks for each code, turned 0-3 quarter turns clockwise (stairs, furniture face the right way). */
    private static final class Palette {
        static final BlockState[][] BY_TURNS = new BlockState[4][];

        static {
            BlockState[] base = new BlockState[SOURCES.size()];
            for (int code = 0; code < base.length; code++) {
                base[code] = SOURCES.get(code).get();
            }
            Rotation[] rotations = Rotation.values(); // NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90
            for (int turns = 0; turns < 4; turns++) {
                BY_TURNS[turns] = new BlockState[base.length];
                for (int code = 0; code < base.length; code++) {
                    BY_TURNS[turns][code] = base[code] == null ? null : base[code].rotate(rotations[turns]);
                }
            }
        }
    }

    static boolean covers(int lx, int lz) {
        return Math.abs(lx) <= HALF && Math.abs(lz) <= HALF;
    }

    /** The code at a template position (OUT outside the template). */
    static int code(int lx, int ly, int lz) {
        if (!covers(lx, lz) || ly < MIN_LY || ly > MAX_LY) {
            return OUT;
        }
        return CELLS[index(lx, ly, lz)] & 0xFF;
    }

    /** The block to write at a template position, or null to leave it alone (OUT or room air). */
    @Nullable
    static BlockState state(int lx, int ly, int lz, int turns) {
        return Palette.BY_TURNS[turns][CELLS[index(lx, ly, lz)] & 0xFF];
    }

    static boolean isThroneLight(int code) {
        return code == LIGHT;
    }

    /** True when you can stand on the ground floor here: a solid floor and two blocks of room air above it. */
    static boolean isStandable(int lx, int lz) {
        int floor = code(lx, 0, lz);
        return floor > AIR && floor != BALLS && code(lx, 1, lz) == AIR && code(lx, 2, lz) == AIR;
    }

    private static int index(int lx, int ly, int lz) {
        return ((ly - MIN_LY) * SIZE + (lz + HALF)) * SIZE + (lx + HALF);
    }

    // ------------------------------------------------------------------------------------------
    // Building helpers
    // ------------------------------------------------------------------------------------------

    private static void set(int x, int y, int z, int code) {
        if (covers(x, z) && y >= MIN_LY && y <= MAX_LY) {
            CELLS[index(x, y, z)] = (byte) code;
        }
    }

    /** Fills the box x0..x1, y0..y1, z0..z1 (all inclusive). */
    private static void box(int x0, int x1, int y0, int y1, int z0, int z1, int code) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z, code);
                }
            }
        }
    }

    /** Stairs facing (tall side towards) the centre (cx, cz): how roof edges climb inward. */
    private static int roofStairTowards(double cx, double cz, int x, int z) {
        double dx = cx - x;
        double dz = cz - z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return ROOF_STAIRS[dx > 0 ? E : W];
        }
        return ROOF_STAIRS[dz > 0 ? S : N];
    }

    /** A solid round tower of white wall, with window slits facing the four directions at the given heights. */
    private static void tower(int cx, int cz, double radius, int y0, int y1, int... windowHeights) {
        int r = (int) Math.floor(radius);                  // the outermost whole block on each axis
        for (int y = y0; y <= y1; y++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    double dist = Math.hypot(x - cx, z - cz);
                    if (dist <= radius) {
                        set(x, y, z, WALL);
                    }
                }
            }
        }
        for (int y : windowHeights) {
            set(cx + r, y, cz, WINDOW);
            set(cx - r, y, cz, WINDOW);
            set(cx, y, cz + r, WINDOW);
            set(cx, y, cz - r, WINDOW);
        }
    }

    /** A red conical roof: radius shrinking to a single tip block at y0 + height - 1. */
    private static void cone(int cx, int cz, double radius, int y0, int height) {
        int r = (int) Math.ceil(radius + 0.5);
        for (int i = 0; i < height; i++) {
            double ri = radius * (1 - (double) i / height);
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    double dist = Math.hypot(x - cx, z - cz);
                    if (dist > ri + 0.5) {
                        continue;
                    }
                    boolean edge = dist > ri - 0.5 && dist > 0;
                    set(x, y0 + i, z, edge ? roofStairTowards(cx, cz, x, z) : ROOF);
                }
            }
        }
    }

    /** A red hipped roof over the rectangle x0..x1, z0..z1, one block in from every side per layer. */
    private static void hipRoof(int x0, int x1, int z0, int z1, int y0) {
        for (int i = 0; x0 + i <= x1 - i && z0 + i <= z1 - i; i++) {
            int ax0 = x0 + i;
            int ax1 = x1 - i;
            int az0 = z0 + i;
            int az1 = z1 - i;
            boolean ridge = ax1 - ax0 < 2 || az1 - az0 < 2;
            for (int z = az0; z <= az1; z++) {
                for (int x = ax0; x <= ax1; x++) {
                    int code;
                    if (ridge) {
                        code = ROOF;
                    } else if (z == az0) {
                        code = ROOF_STAIRS[S];
                    } else if (z == az1) {
                        code = ROOF_STAIRS[N];
                    } else if (x == ax0) {
                        code = ROOF_STAIRS[E];
                    } else if (x == ax1) {
                        code = ROOF_STAIRS[W];
                    } else {
                        code = ROOF;
                    }
                    set(x, y0 + i, z, code);
                }
            }
        }
    }

    /** Alternate merlons on the edge of a flat roof (x0..x1, z0..z1) at height y. */
    private static void battlements(int x0, int x1, int z0, int z1, int y) {
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge && Math.floorMod(x + z, 2) == 0) {
                    set(x, y, z, WALL);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The castle, part by part
    // ------------------------------------------------------------------------------------------

    /** The keep (x -15..15, z -17..17, roof at ly 17) and the taller throne hall at its back, rooms carved out. */
    private static void buildShell() {
        box(-15, 15, MIN_LY, 0, -17, 17, STONE);           // foundation; the default floor
        box(-15, 15, 1, 17, -17, 17, WALL);                // the keep, solid for now
        box(-15, 15, 18, 24, 5, 17, WALL);                 // the throne hall rises to ly 24

        // Ground floor rooms (ly 1-8 under a ly 9 ceiling), the two-storey playground and the throne room.
        box(-14, 14, 1, 8, -16, -8, AIR);                  // entrance hall
        box(-14, -6, 1, 8, -6, 4, AIR);                    // ball pit room
        box(-4, 4, 1, 8, -6, 4, AIR);                      // gallery to the throne room
        box(6, 14, 1, 16, -6, 4, AIR);                     // playground (two storeys)
        box(-13, 13, 1, 23, 6, 15, AIR);                   // throne room
        // Upper floor (ly 10-16 on the ly 9 ceiling of the ground floor).
        box(-14, 4, 10, 16, -16, 4, AIR);                  // looping hall + studio
        box(6, 14, 10, 16, -16, -8, AIR);                  // a sealed, empty room over the east end of the hall

        // Between the storeys: a ceiling of acoustic tiles that is carpet on its upper side.
        box(-14, 14, 9, 9, -16, -8, CEILING);
        box(-14, 4, 9, 9, -7, 4, CEILING);

        // Painted partition walls (the outer walls stay white plaster).
        box(-14, 14, 1, 8, -7, -7, PLAY_WALL);             // hall | middle rooms
        box(5, 14, 9, 16, -7, -7, PLAY_WALL);              // playground's north wall, upper part
        box(-5, -5, 1, 8, -6, 4, PLAY_WALL);               // ball pit | gallery
        box(5, 5, 1, 16, -6, 4, PLAY_WALL);                // gallery and upper floor | playground
        box(5, 5, 10, 16, -16, -8, PLAY_WALL);             // upper floor | sealed room
        // The throne room is stone: its front wall and an inner lining on the other three sides.
        box(-14, 14, 1, 23, 5, 5, STONE);
        box(-14, -14, 1, 23, 6, 16, STONE);
        box(14, 14, 1, 23, 6, 16, STONE);
        box(-14, 14, 1, 23, 16, 16, STONE);
    }

    /** Narrow dark windows in the outer walls (before the rooms add their features). */
    private static void buildWindows() {
        for (int y : new int[] {3, 4, 12, 13}) {
            for (int x = -13; x <= 13; x++) {
                if (Math.floorMod(x, 4) == 2 && Math.abs(x) >= 3) {
                    set(x, y, -17, WINDOW);
                    set(x, y, 17, WINDOW);
                }
            }
            for (int z = -15; z <= 15; z++) {
                if (Math.floorMod(z, 4) == 2) {
                    set(-15, y, z, WINDOW);
                    set(15, y, z, WINDOW);
                }
            }
        }
        for (int y = 19; y <= 21; y++) {                   // tall windows of the throne hall
            for (int z : new int[] {8, 11, 14}) {
                set(-15, y, z, WINDOW);
                set(15, y, z, WINDOW);
            }
            for (int x = -10; x <= 10; x += 5) {
                set(x, y, 17, WINDOW);
            }
        }
    }

    /**
     * Entrance hall (x -14..14, z -16..-8): blue/yellow checker floor, a red/white striped path from
     * the gate to the throne room, a star circle, four striped pillars with portholes, green pipes
     * under a bright ceiling.
     */
    private static void buildEntranceHall() {
        box(-14, 14, 0, 0, -16, -7, CHECKER);
        box(-1, 1, 1, 4, -17, -17, AIR);                   // the gate
        // Doorways into the middle rooms.
        box(-11, -9, 1, 3, -7, -7, AIR);                   // ball pit room
        box(-2, 2, 1, 6, -7, -7, AIR);                     // gallery (wide)
        box(9, 11, 1, 3, -7, -7, AIR);                     // playground
        // The striped path runs from the gate, through the gallery, to the throne room door.
        box(-1, 1, 0, 0, -17, 4, STRIPES);
        // Star circle on the floor, centred at (0, -12): green ring, yellow disc, star in the middle.
        for (int z = -16; z <= -8; z++) {
            for (int x = -4; x <= 4; x++) {
                int d2 = x * x + (z + 12) * (z + 12);
                if (d2 == 0) {
                    set(x, 0, z, STAR);
                } else if (d2 <= 6) {
                    set(x, 0, z, DISC);
                } else if (d2 <= 12) {
                    set(x, 0, z, RING);
                }
            }
        }
        // 2x2 striped pillars, porthole bands at ly 3 and 6.
        for (int px : new int[] {-9, 8}) {
            for (int pz : new int[] {-14, -11}) {
                box(px, px + 1, 1, 8, pz, pz + 1, PILLAR);
                box(px, px + 1, 3, 3, pz, pz + 1, PORTHOLE);
                box(px, px + 1, 6, 6, pz, pz + 1, PORTHOLE);
            }
        }
        // Green pipes: two along the hall just under the ceiling, one across it a little lower.
        box(-5, -5, 8, 8, -16, -8, PIPE_Z);
        box(5, 5, 8, 8, -16, -8, PIPE_Z);
        box(-14, 14, 7, 7, -9, -9, PIPE_X);
        for (int x : new int[] {-11, -3, 3, 11}) {
            for (int z : new int[] {-14, -10}) {
                set(x, 9, z, LAMP);
            }
        }
    }

    /** Ball pit room (x -14..-6, z -6..4): a two-deep pit of balls with a walkway round it, a crawl tube to the gallery. */
    private static void buildBallPit() {
        box(-14, -6, 0, 0, -6, 4, MAT);
        box(-13, -7, -2, -2, -5, 3, MAT);                  // bottom of the pit
        box(-13, -7, -1, 0, -5, 3, BALLS);
        for (int x : new int[] {-12, -8}) {
            for (int z : new int[] {-3, 1}) {
                set(x, 9, z, LAMP);
            }
        }
        // Crawl tube through the wall into the gallery: a 2-high tunnel with a plastic rim.
        box(-5, -4, 1, 2, 1, 2, AIR);
        box(-5, -4, 1, 3, 0, 0, TUBE);
        box(-5, -4, 1, 3, 3, 3, TUBE);
        box(-5, -4, 3, 3, 1, 2, TUBE);
    }

    /** Gallery (x -4..4, z -6..4): the striped path between striped pillars to the very large throne room doorway. */
    private static void buildGallery() {
        box(-4, 4, 0, 0, -6, 4, CHECKER);
        box(-1, 1, 0, 0, -6, 4, STRIPES);
        for (int x : new int[] {-3, 3}) {
            for (int z : new int[] {-4, -1}) {
                box(x, x, 1, 8, z, z, PILLAR);
                set(x, 4, z, PORTHOLE);
            }
        }
        for (int z : new int[] {-5, -1, 3}) {
            set(0, 9, z, LAMP);
        }
        box(-3, 3, 1, 8, 5, 5, AIR);                       // the throne room doorway: 7 wide, 8 tall
        box(-3, 3, 0, 0, 5, 5, STONE);
        box(-1, 1, 0, 0, 5, 5, CARPET);
    }

    /**
     * Playground (x 6..14, z -6..4, two storeys): a climbing frame up to a deck at the upper floor's
     * level (door into the looping hall), a slide back down, a crawl tube from the gallery and a
     * small jungle gym.
     */
    private static void buildPlayground() {
        box(6, 14, 0, 0, -6, 4, MAT);
        box(6, 9, 9, 9, -6, -5, MAT);                      // deck at the upper floor's level
        box(8, 9, 10, 10, -4, -4, TUBE);                   // a low wall at the deck's edge
        box(6, 7, 1, 10, -4, -3, FRAME);                   // climb up here
        // The slide: from beside the deck down to the floor, 8 slippery steps going south.
        for (int i = 0; i < 8; i++) {
            int z = -6 + i;
            int y = 8 - i;
            box(10, 11, y, y, z, z, SLIDE[N]);
            set(12, y + 1, z, TUBE);
            if (i >= 2) {
                set(9, y + 1, z, TUBE);
            }
        }
        // Crawl tube from the gallery through the west wall.
        box(5, 8, 1, 2, 1, 2, AIR);
        box(5, 8, 1, 3, 0, 0, TUBE);
        box(5, 8, 1, 3, 3, 3, TUBE);
        box(5, 8, 3, 3, 1, 2, TUBE);
        box(13, 14, 1, 3, 1, 4, FRAME);                    // jungle gym in the corner
        for (int x : new int[] {7, 10, 13}) {
            for (int z : new int[] {-5, -1, 3}) {
                set(x, 17, z, LAMP);
            }
        }
    }

    /**
     * Upper floor: a 2-wide ring corridor (the looping hall: walk round and you are back where you
     * started) around the animation studio (x -11..1, z -13..1), with drawing desks along the
     * walls, sketches pinned above them, and a big table holding a miniature of the town.
     */
    private static void buildUpperFloor() {
        // Studio walls, two doors from the ring, and the door from the playground deck.
        box(-12, -12, 10, 16, -14, 2, PLAY_WALL);
        box(2, 2, 10, 16, -14, 2, PLAY_WALL);
        box(-12, 2, 10, 16, -14, -14, PLAY_WALL);
        box(-12, 2, 10, 16, 2, 2, PLAY_WALL);
        for (int y = 11; y <= 12; y++) {
            box(-12, -12, y, y, -13, 1, SKETCH[E]);
            box(2, 2, y, y, -13, 1, SKETCH[W]);
            box(-11, 1, y, y, -14, -14, SKETCH[S]);
            box(-11, 1, y, y, 2, 2, SKETCH[N]);
        }
        box(-6, -4, 10, 12, -14, -14, AIR);
        box(-6, -4, 10, 12, 2, 2, AIR);
        box(5, 5, 10, 11, -6, -5, AIR);
        // Bright lamps: every few blocks round the ring, a grid over the studio.
        for (int z = -16; z <= 4; z++) {
            for (int x = -14; x <= 4; x++) {
                boolean ring = x <= -13 || x >= 3 || z <= -15 || z >= 3;
                if (ring && Math.floorMod(x - z, 6) == 0) {
                    set(x, 17, z, LAMP);
                }
            }
        }
        for (int x : new int[] {-9, -5, -1}) {
            for (int z : new int[] {-11, -7, -3, 0}) {
                set(x, 17, z, LAMP);
            }
        }
        // Drawing desks against the side walls, facing into the room.
        for (int z = -12; z <= 0; z += 3) {
            set(-11, 10, z, DESK[E]);
            set(1, 10, z, DESK[W]);
        }
        // The big table and the little town on it: fountain in the middle, houses round it, trees.
        box(-7, -3, 10, 10, -10, -2, TABLE);
        set(-5, 11, -6, MINI_FOUNTAIN);
        set(-7, 11, -9, MINI_HOUSE[S]);
        set(-3, 11, -9, MINI_HOUSE[S]);
        set(-5, 11, -10, MINI_HOUSE[S]);
        set(-7, 11, -3, MINI_HOUSE[N]);
        set(-3, 11, -3, MINI_HOUSE[N]);
        set(-5, 11, -2, MINI_HOUSE[N]);
        set(-7, 11, -6, MINI_HOUSE[E]);
        set(-3, 11, -6, MINI_HOUSE[W]);
        set(-6, 11, -10, MINI_TREE);
        set(-4, 11, -2, MINI_TREE);
        set(-7, 11, -8, MINI_TREE);
        set(-3, 11, -4, MINI_TREE);
    }

    /**
     * Throne room (x -13..13, z 6..15, ly 1-23): red carpet to a stone dais, the empty throne, and
     * behind it an archway full of white light (x -2..2, z 16, ly 2-8) that is the way to Level 1.
     */
    private static void buildThroneRoom() {
        box(-1, 1, 0, 0, 6, 10, CARPET);
        box(-1, 1, 1, 1, 11, 11, STONE_STAIRS[S]);         // the step up onto the dais
        box(-4, 4, 1, 1, 12, 15, STONE);                   // dais
        set(0, 2, 14, THRONE[N]);
        box(-2, 2, 2, 7, 16, 16, LIGHT);                   // the light fills an arch in the back wall
        box(-1, 1, 8, 8, 16, 16, LIGHT);
        for (int x : new int[] {-9, 9}) {                  // a row of plain stone columns each side
            for (int z : new int[] {7, 10, 13}) {
                box(x, x, 1, 23, z, z, STONE);
            }
        }
        for (int z : new int[] {8, 11, 14}) {              // lamps in the side walls, low and high
            for (int y : new int[] {6, 14}) {
                set(-14, y, z, LAMP);
                set(14, y, z, LAMP);
            }
        }
        for (int x : new int[] {-6, 0, 6}) {
            for (int z : new int[] {8, 12}) {
                set(x, 24, z, LAMP);
            }
        }
    }

    /** The tiers, turrets, gate towers and red roofs of the outside (cc3). */
    private static void buildRoofsAndTowers() {
        battlements(-15, 15, -17, 4, 18);
        hipRoof(-15, 15, 5, 17, 25);                       // over the throne hall
        // Second tier over the front half, hollow and closed.
        box(-9, 9, 18, 35, -14, 2, WALL);
        box(-8, 8, 18, 34, -13, 1, AIR);
        battlements(-9, 9, -14, 2, 36);
        for (int y : new int[] {22, 23, 28, 29}) {
            for (int x = -7; x <= 7; x += 3) {
                set(x, y, -14, WINDOW);
                set(x, y, 2, WINDOW);
            }
            for (int z = -12; z <= 0; z += 3) {
                set(-9, y, z, WINDOW);
                set(9, y, z, WINDOW);
            }
        }
        // Third tier with its own hipped roof.
        box(-5, 5, 36, 46, -10, -2, WALL);
        box(-4, 4, 36, 46, -9, -3, AIR);
        for (int y = 40; y <= 41; y++) {
            for (int x = -4; x <= 4; x += 2) {
                set(x, y, -10, WINDOW);
                set(x, y, -2, WINDOW);
            }
            for (int z = -8; z <= -4; z += 2) {
                set(-5, y, z, WINDOW);
                set(5, y, z, WINDOW);
            }
        }
        hipRoof(-5, 5, -10, -2, 47);
        // The central spire: up through the third tier's roof to the tip at ly 65 (y215).
        tower(0, -6, 2.2, 47, 55, 52, 53);
        cone(0, -6, 3.2, 56, MAX_LY - 56 + 1);
        // Turrets: front corners of the keep, back corners of the throne hall, corners of tier two.
        for (int sx : new int[] {-1, 1}) {
            tower(12 * sx, -14, 2.5, 18, 28, 23, 24);
            cone(12 * sx, -14, 3.3, 29, 7);
            tower(13 * sx, 15, 2.0, 25, 33, 29, 30);
            cone(13 * sx, 15, 2.8, 34, 6);
            tower(9 * sx, -14, 1.5, 36, 42, 39);
            cone(9 * sx, -14, 2.2, 43, 5);
        }
        // Square gate towers either side of the gate, with little hipped roofs.
        for (int sx : new int[] {-1, 1}) {
            int x0 = sx < 0 ? -6 : 3;
            box(x0, x0 + 3, 0, 21, -20, -17, WALL);
            for (int y : new int[] {8, 9, 15, 16}) {
                set(5 * sx, y, -20, WINDOW);
            }
            hipRoof(x0, x0 + 3, -20, -17, 22);
        }
        // Gate arch: keep the opening clear and give it a stone frame.
        box(-1, 1, 1, 4, -17, -17, AIR);
        box(-2, 2, 5, 5, -17, -17, STONE);
        box(-2, -2, 1, 4, -17, -17, STONE);
        box(2, 2, 1, 4, -17, -17, STONE);
    }

    private Level94CastlePlan() {}
}
