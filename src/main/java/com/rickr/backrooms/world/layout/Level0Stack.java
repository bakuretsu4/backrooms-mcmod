package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * The stack, from the film: a square void through Level 0, ringed by floor after floor of open
 * office space, partitions and doorways and the odd chair, going down into the dark and up out of
 * sight. Most of the floors are not there: five are built (y35-y59, the one you walk in on in the
 * middle) and client/Level0StackRenderer draws dozens more above and below them, the same plan,
 * darker the further they go.
 *
 * <p>Plan (local frame, see {@link Level0SetPieces}; m = rings from the middle, 0..15):
 * <pre>
 *   m  0- 6  the void: open from the bottom of the world to the top
 *   m  7-13  the ring of floor round it, partitioned into bays (partitions every 7, with doorways)
 *   m 14-15  the outer wall, with a doorway into the maze on three sides of the entry floor
 * </pre>
 * Floors: carpet at y35, 40, 45, 50, 55 (story 5: carpet, three of air, the ceiling tile). You walk
 * in on y40. On three sides its ring is ordinary floor that just ends at the void ("from a floor's
 * edge"). On the fourth (local west) the floor is gone but for a two-wide ledge along the wall
 * (the film's ledge), and a floating stair of half steps climbs from it, over the drop, up through
 * a slot in the ceiling into a sealed room on the floor above (the hidden room: supplies and a
 * radio). Falling into the void (or off the y35 floor) is handled by the server
 * (world/anomaly/level0/StackBehavior): you wake somewhere else on Level 0, hurt.
 */
public final class Level0Stack {
    public static final int BOX = 32;
    public static final int MOAT = 1;
    private static final int HALF = BOX / 2;

    public static final int VOID_MAX = 6;
    public static final int RING_MIN = 7;
    public static final int RING_MAX = 13;
    public static final int WALL_MIN = 14;

    /** The real floors (carpet layers); the entry floor is LevelLayout.FLOOR_Y = 40. */
    public static final int[] FLOORS = {35, 40, 45, 50, 55};
    public static final int STORY = 5;
    public static final int LOWEST_REAL = 35;
    public static final int HIGHEST_REAL_CEILING = 59;
    /** Below this (feet) inside the site you are falling: StackBehavior catches you. */
    public static final double FALL_BELOW = 34.0;
    private static final int ENTRY = 40;
    private static final int HIDDEN_FLOOR = 45;

    /** Partitions stand at these along-offsets of each side (shifted per floor and side). */
    private static final int PARTITION_STEP = 7;

    /** Named spots for /backrooms anomaly visit. */
    public static final List<String> VISIT_POINTS = List.of("edge", "ledge", "stairs", "hidden", "below", "down", "up");

    private Level0Stack() {}

    // ---- Shape ------------------------------------------------------------------------------------

    private static int ring(int d) {
        return d >= 0 ? d : -d - 1;
    }

    /** Local {dx, dz, m, side, along} of world column (x, z), or null outside the box. */
    @Nullable
    public static int[] local(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        if (u < 0 || u >= BOX || v < 0 || v >= BOX) {
            return null;
        }
        int dx = u - HALF;
        int dz = v - HALF;
        int ax = ring(dx);
        int az = ring(dz);
        int m = Math.max(ax, az);
        int side = ax >= az ? (dx < 0 ? Level0SetPieces.W : Level0SetPieces.E) : (dz < 0 ? Level0SetPieces.N : Level0SetPieces.S);
        int along = ax >= az ? dz : dx;
        return new int[] {dx, dz, m, side, along};
    }

    /** True where floor {@code f} has a partition across the ring at {@code along} on {@code side}. */
    public static boolean partition(long salt, int f, int side, int along) {
        int shift = Hash.range(Hash.mix(salt + f * 131L + side * 17L), PARTITION_STEP);
        return Math.floorMod(along + shift, PARTITION_STEP) == 0 && Math.abs(along) <= RING_MAX - 1;
    }

    /** The doorway through a partition (ring offsets m 9-10). */
    public static boolean partitionDoor(int m) {
        return m == 9 || m == 10;
    }

    /** Which side of the entry floor (local, 0-3) has the ledge and the stair: always local west. */
    private static boolean ledgeSide(int side) {
        return side == Level0SetPieces.W;
    }

    /** The doorway into the maze through the outer wall of {@code side} (N, E, S) on the entry floor: its along. */
    private static int mazeDoor(long salt, int side) {
        return -8 + Hash.range(Hash.mix(salt + side * 977L), 16);
    }

    // ---- The stair ----------------------------------------------------------------------------------

    /**
     * The stair runs along the ledge side over ring offsets m 10-11, from along STAIR_FROM up 10
     * half steps (it starts inside the hidden room's walls, so the slot never opens into the
     * neighbouring side's floor).
     */
    private static final int STAIR_FROM = -9;
    private static final int STAIR_STEPS = 10;

    /** Height of the stair's tread at {@code along} (half blocks: 41.5 ... 46), or NaN off the stair. */
    private static double stairSurface(int along, int m) {
        int k = along - STAIR_FROM;
        if (m < 10 || m > 11 || k < 0 || k >= STAIR_STEPS) {
            return Double.NaN;
        }
        return ENTRY + 1 + 0.5 * (k + 1);
    }

    /** The slot through the ceiling over the stair, from its second step up (head room). */
    private static boolean inSlot(int m, int along) {
        int k = along - STAIR_FROM;
        return m >= 10 && m <= 11 && k >= 1 && k < STAIR_STEPS;
    }

    // ---- Building a column -------------------------------------------------------------------------

    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int[] l = local(site, x, z);
        BlockState carpet = ModBlocks.LOBBY_CARPET.get().defaultBlockState();
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        BlockState paper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        BlockState base = ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState();
        if (l == null) {
            // The moat: the maze's own floor and ceiling, so it meets the stack cleanly.
            out.set(floor, carpet);
            out.set(ceiling, tile);
            return;
        }
        int m = l[2];
        int side = l[3];
        int along = l[4];
        if (m >= WALL_MIN) {
            // The outer wall, from the lowest real slab to the highest real ceiling: above and below
            // that, client/Level0StackRenderer draws it on with the floors it draws.
            for (int y = LOWEST_REAL - 1; y <= HIGHEST_REAL_CEILING; y++) {
                out.set(y, paper);
            }
            for (int f : FLOORS) {
                out.set(f + 1, base);
            }
            if (side != Level0SetPieces.W && Math.abs(along - mazeDoor(site.salt(), side)) <= 1) {
                out.set(ENTRY, carpet);
                for (int y = ENTRY + 1; y <= ENTRY + 3; y++) {
                    out.set(y, Blocks.AIR.defaultBlockState());
                }
            }
            return;
        }
        if (m <= VOID_MAX) {
            return; // the void: open all the way
        }
        // The ring: one story per real floor.
        for (int f : FLOORS) {
            story(site, x, z, f, m, side, along, carpet, tile, paper, base, out);
        }
        // The ceiling slab under the lowest real floor (the fake floor below's ceiling).
        out.set(LOWEST_REAL - 1, tile);
    }

    private static void story(AnomalySite site, int x, int z, int f, int m, int side, int along, BlockState carpet,
                              BlockState tile, BlockState paper, BlockState base, LevelLayout.Column out) {
        int ceil = f + 4;
        boolean entryLedgeSide = f == ENTRY && ledgeSide(side);
        boolean hidden = f == HIDDEN_FLOOR && ledgeSide(side);
        // The ceiling: tiles, a light panel in a loose grid (some dead), the slot over the stair.
        boolean slot = ledgeSide(side) && f == ENTRY && inSlot(m, along);
        if (!slot) {
            boolean panel = Math.floorMod(along, 4) == 1 && (m == 9 || m == 12) && !hidden;
            boolean dead = Hash.unit(Hash.mix(site.salt() + f * 7919L + x * 31L + z)) < (f == ENTRY ? 0.15 : 0.35);
            out.set(ceil, panel ? Level0Tubes.pick(site.salt(), x, z, dead, false) : tile);
        }
        if (entryLedgeSide) {
            // The ledge (m 12-13) and the stair; the rest of this side's floor is gone.
            if (m >= 12) {
                out.set(f, carpet);
            }
            double s = stairSurface(along, m);
            if (!Double.isNaN(s)) {
                int halves = (int) Math.round(s * 2.0);
                int y = Math.floorDiv(halves, 2);
                if (halves % 2 == 1) {
                    out.set(y, ModBlocks.STAIRWELL_STEP.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                } else {
                    out.set(y - 1, ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
                }
            }
            return;
        }
        boolean slotAbove = f == HIDDEN_FLOOR && ledgeSide(side) && inSlot(m, along);
        if (slotAbove) {
            // The top of the stair comes up through the hidden room's floor.
            double s = stairSurface(along, m);
            int halves = (int) Math.round(s * 2.0);
            int y = Math.floorDiv(halves, 2);
            if (y >= f) {
                if (halves % 2 == 1) {
                    out.set(y, ModBlocks.STAIRWELL_STEP.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                } else {
                    out.set(y - 1, ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
                }
            }
            return;
        }
        out.set(f, carpet);
        if (hidden) {
            hiddenRoom(site, f, m, along, paper, out);
            return;
        }
        if (partition(site.salt(), f, side, along) && !partitionDoor(m)) {
            out.set(f + 1, base);
            for (int y = f + 2; y < ceil; y++) {
                out.set(y, paper);
            }
            return;
        }
        // Now and then a chair or a filing cabinet, as if someone had just left.
        long h = Hash.mix(site.salt() ^ (f * 104729L + x * 31L + z * 17L));
        if (m >= 11 && Hash.unit(h) < 0.03) {
            Direction d = Direction.from2DDataValue(Hash.range(Hash.mix(h), 4));
            out.set(f + 1, (Hash.unit(Hash.mix(h ^ 5)) < 0.6 ? ModBlocks.OFFICE_CHAIR : ModBlocks.FILING_CABINET).get()
                    .defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d));
        }
    }

    /**
     * The hidden room: the whole ledge side of the floor above the entry, walled off from the void
     * and from the two neighbouring sides, reached only by the stair. A radio (save your
     * checkpoint), a crate, a chair turned to face the wall towards the void, a warm lamp.
     */
    private static void hiddenRoom(AnomalySite site, int f, int m, int along, BlockState paper, LevelLayout.Column out) {
        boolean wall = m == RING_MIN || ring(along) >= m;
        if (wall) {
            for (int y = f + 1; y < f + 4; y++) {
                out.set(y, paper);
            }
            return;
        }
        Direction toVoid = Level0SetPieces.toWorld(site, Direction.EAST);
        if (m == 13 && along == 0) {
            out.set(f + 1, ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toVoid));
        } else if (m == 13 && along == 2) {
            out.set(f + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
        } else if (m == 9 && along == 4) {
            out.set(f + 1, ModBlocks.OFFICE_CHAIR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                    toVoid.getOpposite()));
        } else if (m == 12 && along == -4) {
            out.set(f + 3, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12));
        }
    }

    // ---- Queries ------------------------------------------------------------------------------------

    /** True if the column is a wall at eye level on the entry floor. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        int[] l = local(site, x, z);
        if (l == null) {
            return false;
        }
        int m = l[2];
        int side = l[3];
        int along = l[4];
        if (m >= WALL_MIN) {
            return side == Level0SetPieces.W || Math.abs(along - mazeDoor(site.salt(), side)) > 1;
        }
        if (m <= VOID_MAX) {
            return false;
        }
        return !ledgeSide(side) && partition(site.salt(), ENTRY, side, along) && !partitionDoor(m);
    }

    /** True if you can stand on the entry floor of column (x, z) (not the void, not off the ledge). */
    static boolean isWalkable(AnomalySite site, int x, int z) {
        int[] l = local(site, x, z);
        if (l == null) {
            return true;
        }
        int m = l[2];
        if (m <= VOID_MAX || m >= WALL_MIN) {
            return m >= WALL_MIN && !isSolid(site, x, z);
        }
        if (ledgeSide(l[3])) {
            return m >= 12;
        }
        return !isSolid(site, x, z);
    }

    /** True inside the box (void, ring or wall) at (x, z): where StackBehavior watches for falls. */
    public static boolean inBox(AnomalySite site, int x, int z) {
        return local(site, x, z) != null;
    }

    /** True over the void itself (any height). */
    public static boolean overVoid(AnomalySite site, int x, int z) {
        int[] l = local(site, x, z);
        return l != null && l[2] <= VOID_MAX;
    }

    /** {x, feet y, z, yaw, pitch} of a named spot. */
    @Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        return switch (name) {
            // On the entry floor at the void's edge, looking down into it.
            case "edge" -> Level0SetPieces.spot(site, MOAT, HALF, HALF - RING_MIN - 1, ENTRY + 1, Direction.SOUTH, 55.0f);
            // On the ledge, the void and the stair in front (image 3).
            case "ledge" -> Level0SetPieces.spot(site, MOAT, HALF - 13, HALF - 13, ENTRY + 1, Direction.SOUTH, 15.0f);
            case "stairs" -> Level0SetPieces.spot(site, MOAT, HALF - 11, HALF + STAIR_FROM + 4, ENTRY + 3.5, Direction.SOUTH, -10.0f);
            case "hidden" -> Level0SetPieces.spot(site, MOAT, HALF - 13, HALF + 2, HIDDEN_FLOOR + 1, Direction.NORTH, 10.0f);
            case "below" -> Level0SetPieces.spot(site, MOAT, HALF, HALF - 11, LOWEST_REAL + 1, Direction.SOUTH, 20.0f);
            // Looking straight down and straight up the void from the entry floor's edge.
            case "down" -> Level0SetPieces.spot(site, MOAT, HALF, HALF - RING_MIN - 1, ENTRY + 1, Direction.SOUTH, 80.0f);
            case "up" -> Level0SetPieces.spot(site, MOAT, HALF, HALF - RING_MIN - 1, ENTRY + 1, Direction.SOUTH, -70.0f);
            default -> null;
        };
    }
}
