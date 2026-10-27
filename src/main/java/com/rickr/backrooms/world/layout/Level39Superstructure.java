package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.MillWindowBlock;
import com.rickr.backrooms.block.TowerWindowBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The superstructure of Level 39: the colossal 1900s mill that walls in every town on all four
 * sides (original.png, scaled up beyond reason). Each side of the town is one facade, rising from
 * the ground to y {@value Level39Layout#FACADE_TOP_Y} in 24 storeys:
 * <ul>
 *   <li>bays of {@value Level39Layout#BAY} blocks: a recessed panel with a 3-wide, 5-tall
 *       multi-pane window glowing cold white, framed by 1-wide brick pilasters;</li>
 *   <li>a granite string course every storey, granite sills, sooty brick under the windows;</li>
 *   <li>segmentally arched windows on the top storey, then a cornice and a parapet;</li>
 *   <li>stair towers every {@value #TOWER_SPACING} blocks, standing out from the wall and rising
 *       above it to a hipped cap;</li>
 *   <li>on the ground storey some windows boarded over, some bricked up.</li>
 * </ul>
 * Behind the glass is the futuristic contrast: a 10-deep gallery per storey, eerily clean and
 * bright, white seamless panels, light panels in the ceiling and a polished floor, completely empty.
 * Behind the gallery the band is one solid mass; only the inner {@value #REAL_DEPTH} blocks of
 * each facade are real detail.
 *
 * <p>Geometry (also used by the far-facade renderer and the Window entities): depth
 * {@code d = max(|lx|, |lz|) - 448} (d = 0 pilasters, d = 1 glass, d = 2..11 gallery, d = 12 its
 * back wall); "along" is the local coordinate parallel to the facade; storey k (0..23) starts at
 * {@code yb = 73 + 7k}: yb granite course, yb+1 spandrel and sill, yb+2..yb+6 glass.
 */
public final class Level39Superstructure {
    /** |local| of the first band column (the pilaster plane). */
    public static final int FACE = Level39Layout.TOWN_HALF;
    /** Real detail stops this deep into the band; the rest is solid. */
    static final int REAL_DEPTH = 13;
    /** Bottom of the first storey (the granite plinth). */
    public static final int BASE_Y = Level39Layout.GROUND_Y + 1;
    public static final int STOREYS = 24;
    /** Glass rows within a storey: yb + 2 .. yb + 6. */
    public static final int GLASS_FROM = 2;
    public static final int GLASS_TO = 6;
    /** Flat tarred roof of the band. */
    public static final int ROOF_Y = 245;
    /** Gallery behind the glass: depths 2..11. */
    public static final int GALLERY_FROM = 2;
    public static final int GALLERY_TO = 11;

    public static final int TOWER_SPACING = 128;
    /** Stair towers reach along +-this from their centre... */
    public static final int TOWER_HALF = 6;
    /** ...and from depth TOWER_FRONT (in front of the wall, over the town's apron) to TOWER_BACK. */
    public static final int TOWER_FRONT = -8;
    public static final int TOWER_BACK = 1;
    /** Top of a tower's brick body; its hipped cap rises above. */
    public static final int TOWER_TOP_Y = 252;

    private Level39Superstructure() {}

    // ==========================================================================================
    // Geometry queries
    // ==========================================================================================

    /** True for the 3 glass columns of each 6-block bay (bay offsets 2, 3 and 4). */
    public static boolean isWindowColumn(int along) {
        int b = Math.floorMod(along, Level39Layout.BAY);
        return b >= 2 && b <= 4;
    }

    /** Facade depth of column (x, z): 0 at the pilasters, 1 at the glass, negative in town. */
    public static int depth(int x, int z) {
        return Math.max(Math.abs(Level39Layout.local(x)), Math.abs(Level39Layout.local(z))) - FACE;
    }

    /** The coordinate along the nearest facade (local z on the east/west sides, local x on the north/south). */
    public static int along(int x, int z) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        return Math.abs(lx) >= Math.abs(lz) ? lz : lx;
    }

    /** The direction the nearest facade faces (from the wall toward the town). */
    public static Direction facing(int x, int z) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        if (Math.abs(lx) >= Math.abs(lz)) {
            return lx >= 0 ? Direction.WEST : Direction.EAST;
        }
        return lz >= 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** True inside the footprint of a stair tower (any height). */
    public static boolean inStairTower(int x, int z) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        int ax = Math.abs(lx);
        int az = Math.abs(lz);
        if (ax >= FACE && az >= FACE) {
            return false;
        }
        int d = Math.max(ax, az) - FACE;
        if (d < TOWER_FRONT || d > TOWER_BACK) {
            return false;
        }
        return towerOffset(ax >= az ? lz : lx) != Integer.MIN_VALUE;
    }

    /** Offset of {@code along} from its stair tower's centre, or MIN_VALUE when not within a tower. */
    private static int towerOffset(int along) {
        int j = Math.round(along / (float) TOWER_SPACING);
        if (Math.abs(j) > 3) {
            return Integer.MIN_VALUE;
        }
        int u = along - j * TOWER_SPACING;
        return Math.abs(u) <= TOWER_HALF ? u : Integer.MIN_VALUE;
    }

    /**
     * True when (x, y, z) is a lit pane of facade glass (not boarded or bricked, not a tower):
     * where the Window entities live and where you can noclip out.
     */
    public static boolean facadeWindowAt(long seed, int x, int y, int z) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        int ax = Math.abs(lx);
        int az = Math.abs(lz);
        if ((ax >= FACE) == (az >= FACE)) {
            return false; // in town, or in a corner
        }
        int d = Math.max(ax, az) - FACE;
        int along = ax >= FACE ? lz : lx;
        if (d != 1 || !isWindowColumn(along) || towerOffset(along) != Integer.MIN_VALUE) {
            return false;
        }
        if (y < BASE_Y || y > Level39Layout.FACADE_TOP_Y) {
            return false;
        }
        int k = (y - BASE_Y) / Level39Layout.STOREY;
        int r = (y - BASE_Y) % Level39Layout.STOREY;
        if (r < GLASS_FROM) {
            return false;
        }
        return k != 0 || groundWindow(seed, facing(x, z), along) == 0;
    }

    /** Ground-storey window state of the bay containing {@code along}: 0 lit, 1 boarded, 2 bricked. */
    private static int groundWindow(long seed, Direction side, int along) {
        int bay = Math.floorDiv(along, Level39Layout.BAY);
        double v = Hash.unit(seed, bay, side.get2DDataValue(), 3995);
        return v < 0.15 ? 1 : v < 0.24 ? 2 : 0;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /** One column of the band (|local| >= 448 on either axis). */
    static void fillColumn(long seed, int x, int z, LevelLayout.Column out) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        int ax = Math.abs(lx);
        int az = Math.abs(lz);
        out.set(Level39Layout.BEDROCK_Y, Blocks.BEDROCK.defaultBlockState());
        BlockState rock = Level39Blocks.ROCK.get().defaultBlockState();
        for (int y = Level39Layout.BEDROCK_Y + 1; y <= Level39Layout.GROUND_Y; y++) {
            out.set(y, rock);
        }
        BlockState mass = Level39Blocks.MASS.get().defaultBlockState();
        BlockState roof = Level39Blocks.ROOF_TAR.get().defaultBlockState();
        if (ax >= FACE && az >= FACE) {
            // Corner: two facades meet. A brick pier in the inside corner, white end walls where the
            // two galleries stop, solid everywhere else.
            int dx = ax - FACE;
            int dz = az - FACE;
            BlockState fill = (dx == 0 && dz >= GALLERY_FROM && dz <= GALLERY_TO + 1)
                    || (dz == 0 && dx >= GALLERY_FROM && dx <= GALLERY_TO + 1)
                    ? Level39Blocks.GALLERY_PANEL.get().defaultBlockState()
                    : dx <= 1 && dz <= 1 ? Level39Blocks.MILL_BRICK.get().defaultBlockState() : mass;
            for (int y = BASE_Y; y < ROOF_Y; y++) {
                out.set(y, fill);
            }
            out.set(ROOF_Y, roof);
            return;
        }
        boolean xSide = ax >= FACE;
        int d = (xSide ? ax : az) - FACE;
        int along = xSide ? lz : lx;
        Direction facing = xSide ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
        if (d <= TOWER_BACK && towerOffset(along) != Integer.MIN_VALUE) {
            towerColumn(seed, x, z, out);
            // The tower's back merges into the band: the gallery carries on behind it.
            return;
        }
        if (d >= REAL_DEPTH) {
            for (int y = BASE_Y; y < ROOF_Y; y++) out.set(y, mass);
            out.set(ROOF_Y, roof);
            return;
        }
        if (d == GALLERY_TO + 1) {
            BlockState panel = Level39Blocks.GALLERY_PANEL.get().defaultBlockState();
            for (int y = BASE_Y; y < ROOF_Y; y++) out.set(y, panel);
            out.set(ROOF_Y, roof);
            return;
        }
        if (d >= GALLERY_FROM) {
            gallery(along, d, out);
            out.set(ROOF_Y, roof);
            return;
        }
        facade(seed, d, along, facing, out);
    }

    /** The bright empty gallery behind the glass, storey by storey. */
    private static void gallery(int along, int d, LevelLayout.Column out) {
        BlockState panel = Level39Blocks.GALLERY_PANEL.get().defaultBlockState();
        BlockState light = Level39Blocks.GALLERY_LIGHT.get().defaultBlockState();
        BlockState floor = Level39Blocks.GALLERY_FLOOR.get().defaultBlockState();
        // Light panels in two rows down the gallery, one every bay.
        boolean lamp = Math.floorMod(along, Level39Layout.BAY) == 3 && (d == 4 || d == 9);
        int top = BASE_Y + STOREYS * Level39Layout.STOREY; // 241: above the last ceiling
        for (int k = 0; k < STOREYS; k++) {
            int yb = BASE_Y + k * Level39Layout.STOREY;
            // Ceiling of the storey below (or the plinth under the first), then this storey's floor.
            out.set(yb, k == 0 ? panel : lamp ? light : panel);
            out.set(yb + 1, floor);
        }
        out.set(top, lamp ? light : panel);
        BlockState mass = Level39Blocks.MASS.get().defaultBlockState();
        for (int y = top + 1; y < ROOF_Y; y++) out.set(y, mass);
    }

    /**
     * The mill's brick at height {@code y}: dark sooty red-brown low down, more and more of it
     * blackened higher up, nearly all black near the top (a century of coal smoke rising).
     */
    static BlockState brick(long seed, int a, int y) {
        double up = (y - BASE_Y) / (double) (Level39Layout.FACADE_TOP_Y - BASE_Y); // 0 at the foot, 1 at the top
        double black = Math.max(0, Math.min(1, (up - 0.55) / 0.4));
        double sooty = Math.max(0, Math.min(1, (up - 0.1) / 0.5));
        double h = Hash.unit(seed, a, y, 3997);
        if (h < black * 0.85) return Level39Blocks.MILL_BRICK_BLACK.get().defaultBlockState();
        if (h < black * 0.85 + sooty * 0.55) return Level39Blocks.MILL_BRICK_SOOTY.get().defaultBlockState();
        return Level39Blocks.MILL_BRICK.get().defaultBlockState();
    }

    /**
     * The wall itself: the brick face (d = 0) with a 3-wide recess for each window, and behind it
     * (d = 1) the glass, set back a block, between brick piers.
     */
    private static void facade(long seed, int d, int along, Direction facing, LevelLayout.Column out) {
        BlockState granite = Level39Blocks.GRANITE.get().defaultBlockState();
        int b = Math.floorMod(along, Level39Layout.BAY);
        boolean win = b >= 2 && b <= 4;
        // Which way "left" is when you look at this facade from the town (window pieces depend on it).
        Direction plus = facing.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        boolean leftIsPlus = facing.getClockWise() == plus;
        int salt = along * 4 + facing.get2DDataValue() * 8191 + d;
        for (int k = 0; k < STOREYS; k++) {
            int yb = BASE_Y + k * Level39Layout.STOREY;
            for (int r = 0; r < Level39Layout.STOREY; r++) {
                int y = yb + r;
                BlockState s;
                if (d == 0) {
                    if (k == 0 && r <= 1) {
                        s = granite; // plinth
                    } else if (r == 0) {
                        s = granite; // string course
                    } else if (!win) {
                        s = brick(seed, salt, y); // the wall face between the windows
                    } else if (r == 1) {
                        s = Level39Blocks.GRANITE_STAIRS.get().defaultBlockState()
                                .setValue(StairBlock.FACING, facing.getOpposite())
                                .setValue(StairBlock.HALF, Half.BOTTOM); // sloped sill
                    } else {
                        continue; // the window recess
                    }
                } else if (r == 0) {
                    s = granite;
                } else if (r == 1 || !win) {
                    // Spandrel under the window (always sooty) and the piers between windows.
                    s = r == 1 && win ? Level39Blocks.MILL_BRICK_SOOTY.get().defaultBlockState() : brick(seed, salt, y);
                } else {
                    s = glass(seed, k, r, b, facing, along, leftIsPlus);
                }
                out.set(y, s);
            }
        }
        // Cornice and parapet.
        int y = BASE_Y + STOREYS * Level39Layout.STOREY; // 241
        out.set(y, granite);
        out.set(y + 1, d == 0 ? granite : Level39Blocks.MILL_BRICK_BLACK.get().defaultBlockState());
        out.set(y + 2, granite);
        out.set(y + 3, Level39Blocks.MILL_BRICK_BLACK.get().defaultBlockState());
        out.set(ROOF_Y, granite);
    }

    /** One block of a window (or its boarding): which piece of the 3 x 5 sash it is. */
    private static BlockState glass(long seed, int k, int r, int b, Direction facing, int along, boolean leftIsPlus) {
        if (k == 0) {
            int state = groundWindow(seed, facing, along);
            if (state == 1) return Level39Blocks.BOARDED_WINDOW.get().defaultBlockState();
            if (state == 2) return Level39Blocks.BRICKED_WINDOW.get().defaultBlockState();
        }
        int row = r - GLASS_FROM;
        int col = leftIsPlus ? 4 - b : b - 2; // 0 = left as seen from the town
        return Level39Blocks.MILL_WINDOW.get().defaultBlockState()
                .setValue(MillWindowBlock.FACING, facing)
                .setValue(MillWindowBlock.COL, col)
                .setValue(MillWindowBlock.ROW, row)
                .setValue(MillWindowBlock.ARCH, k == STOREYS - 1 && row == 4);
    }

    /**
     * A stair tower column: a plain brick shaft standing out from the wall, one tall round-headed
     * window per landing (staggered half a storey from the mill's floors), granite quoins and
     * courses, a corbelled cornice above the mill's roof line and a steep hipped slate cap.
     * Writes from the ground up; returns false if (x, z) is not in a tower.
     */
    static boolean towerColumn(long seed, int x, int z, LevelLayout.Column out) {
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        int ax = Math.abs(lx);
        int az = Math.abs(lz);
        if (ax >= FACE && az >= FACE) {
            return false;
        }
        boolean xSide = ax >= az;
        int d = (xSide ? ax : az) - FACE;
        int along = xSide ? lz : lx;
        int u = towerOffset(along);
        if (u == Integer.MIN_VALUE || d < TOWER_FRONT || d > TOWER_BACK) {
            return false;
        }
        Direction facing = xSide ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
        BlockState granite = Level39Blocks.GRANITE.get().defaultBlockState();
        boolean front = d == TOWER_FRONT;
        boolean side = Math.abs(u) == TOWER_HALF && d < 0;
        boolean corner = front && Math.abs(u) == TOWER_HALF;
        // The side faces look along the wall: "out" for a side is toward the outside of the tower.
        Direction plus = facing.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction sideOut = u > 0 ? plus : plus.getOpposite();
        boolean frontWindow = front && u == 0;
        boolean sideWindow = side && d == -4;
        BlockState inner = Level39Blocks.MASS.get().defaultBlockState();
        int salt = along * 4 + facing.get2DDataValue() * 8191 + d * 31 + 5;
        for (int y = BASE_Y; y <= TOWER_TOP_Y; y++) {
            BlockState s;
            if (!front && !side) {
                s = inner;
            } else {
                int r = (y - BASE_Y) % Level39Layout.STOREY;
                // Landings sit half a storey off the mill's floors: windows from yb+4 to yb+6, arch course above.
                int w = Math.floorMod(y - BASE_Y - 4, Level39Layout.STOREY);
                if (y <= BASE_Y + 1) {
                    s = granite; // plinth
                } else if (corner) {
                    s = ((y - BASE_Y) / 2) % 2 == 0 ? granite : brick(seed, salt, y); // quoins
                } else if ((frontWindow || sideWindow) && y > BASE_Y + 3 && y < ROOF_Y - 6 && w <= 3) {
                    if (w == 3) {
                        s = granite; // the keystone course over the window's arch
                    } else {
                        s = Level39Blocks.TOWER_WINDOW.get().defaultBlockState()
                                .setValue(TowerWindowBlock.FACING, frontWindow ? facing : sideOut)
                                .setValue(TowerWindowBlock.ROW, w);
                    }
                } else if (r == 0 && y < ROOF_Y && (y - BASE_Y) / Level39Layout.STOREY % 4 == 0) {
                    s = granite; // a string course every fourth storey
                } else if (y == 241 || y == TOWER_TOP_Y - 1) {
                    s = granite;
                } else if (y == TOWER_TOP_Y) {
                    // Corbelled cornice: upside-down granite steps jutting out.
                    s = Level39Blocks.GRANITE_STAIRS.get().defaultBlockState()
                            .setValue(StairBlock.FACING, front ? facing.getOpposite() : sideOut.getOpposite())
                            .setValue(StairBlock.HALF, Half.TOP);
                } else {
                    s = brick(seed, salt, y);
                }
            }
            out.set(y, s);
        }
        // Steep hipped cap of dark slate (two rows up per step in), and a granite finial with a rod.
        int edge = Math.min(TOWER_HALF - Math.abs(u), Math.min(d - TOWER_FRONT, TOWER_BACK - d));
        BlockState shingles = Level39Blocks.SHINGLES.get().defaultBlockState();
        out.set(TOWER_TOP_Y + 1, Level39Blocks.MILL_BRICK_BLACK.get().defaultBlockState());
        int capTop = TOWER_TOP_Y + 2 + edge * 2;
        for (int y = TOWER_TOP_Y + 2; y <= capTop; y++) {
            out.set(y, shingles);
        }
        if (u == 0 && edge == (TOWER_BACK - TOWER_FRONT) / 2) {
            out.set(capTop + 1, granite);
            out.set(capTop + 2, Level39Blocks.RUSTY_POST.get().defaultBlockState());
            out.set(capTop + 3, Level39Blocks.RUSTY_POST.get().defaultBlockState());
        }
        return true;
    }
}
