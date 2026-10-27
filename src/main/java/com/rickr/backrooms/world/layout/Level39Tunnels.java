package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.world.Hash;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import org.jetbrains.annotations.Nullable;

/**
 * The flooded marble tunnels under a Level 39 town (tunnel.png): long, claustrophobic, spotless,
 * lit by "invisible orbs". The network itself (runs on a jittered 96-block lattice, which nodes
 * are dead ends or junctions, the holes and shafts) is planned in {@link Level39Cell}; this class
 * turns it into blocks and answers questions about it.
 *
 * <p>Cross-section of a run (offset {@code o} from its centre line, 9 wide):
 * <pre>
 *   y47 ......###......   barrel vault of white veined marble (air up to y47 in the middle,
 *   y45 ..###.....###..   y45 over the walkways)
 *   y41 #|.........|#     feet: on the rims (|o| = 4) or wading
 *   y40 #T~~~~~~~~~T#     T = cream rim tile with the dark-blue border, ~ = water (y39-40)
 *   y38 #T_________T#     _ = pale tiles under the water
 * </pre>
 * Where three or four runs meet there is a domed round chamber; where a run ends there is a dry
 * platform, a round arch and a wall with two white panelled doors (always locked). Side arches
 * open little dry alcoves in the walls, small square drains go up from the crown of the vault,
 * and rare stagnant runs (Hydrolitis zones) are darker, filmed with green and have no lights.
 */
public final class Level39Tunnels {
    /** Water surface: feet of a swimmer float about here. */
    public static final int WATER_TOP_Y = Level39Layout.TUNNEL_WATER_Y;
    /** Feet height on the rims, platforms and alcove floors. */
    public static final int RIM_FEET_Y = Level39Layout.TUNNEL_WATER_Y + 1;
    /** Orbs hang in the crown of the vault every this many blocks. */
    public static final int ORB_SPACING = 10;
    /** Height of the orbs: the highest wholly open block under a run's vault (its crown cell holds a vault piece). */
    public static final int RUN_ORB_Y = 46;
    /** The orb in the middle of a junction chamber's dome... */
    public static final int DOME_ORB_Y = 48;
    /** ...and the four on its diagonals (4, 4 blocks out). */
    public static final int RING_ORB_Y = 46;
    /** A drain feature's y: inside the drain shaft, just over the crown of the vault. */
    public static final int DRAIN_Y = 48;
    private static final int DRAIN_SPACING = 55;
    /** Highest air block of a side alcove (feet y41 to y43: three blocks tall). */
    private static final int ALCOVE_TOP = 43;
    private static final int ARCH_SPACING = 45;

    /** What a tunnel feature is (see {@link #featuresNear}). */
    public enum Kind {
        /** An invisible light ("orb") in the crown of the vault: the block {@code l39_orb_light} at (x, y, z). */
        ORB,
        /** A dry alcove beside a rim: (x, y, z) = its floor centre (feet), facing = toward the tunnel. */
        SIDE_ARCH,
        /** A 2x2 drain up from the vault's crown: (x, z) = its minimum corner, y = its bottom air block. */
        DRAIN,
        /** An access shaft: (x, y, z) = its bottom (feet on the rim level), facing = the way the rungs face. */
        SHAFT,
        /** A hole from the town: (x, z) = its centre, y = the town's ground. */
        HOLE,
        /** A dead end: (x, y, z) = the foot of the door wall's middle, facing = back into the tunnel. */
        DEAD_END,
        /** A domed chamber where three or four runs meet: (x, z) = its centre, y = rim feet level. */
        JUNCTION
    }

    /** One tunnel feature, in world coordinates. */
    public record Feature(Kind kind, int x, int y, int z, Direction facing, boolean stagnant) {
        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    // ---- Column classification, packed into an int (allocation-free) -----------------------------
    static final int NONE = 0;
    static final int WALL = 1;
    static final int DOOR_WALL = 2;
    static final int ARCH = 3;
    static final int ALCOVE = 4;
    static final int RIM = 5;
    static final int WATER = 6;
    private static final int KIND_MASK = 7;
    private static final int TOP_SHIFT = 3;          // 8 bits: highest air y
    private static final int STAGNANT = 1 << 11;
    private static final int EDGE_SHIFT = 12;        // 3 bits: 0 none, else 1 + 2D direction toward the water
    private static final int DOOR = 1 << 15;
    private static final int HINGE_RIGHT = 1 << 16;
    private static final int DOOR_FACING_SHIFT = 17; // 2 bits: 2D direction
    private static final int ORB = 1 << 19;
    private static final int DRAIN_INNER = 1 << 20;
    private static final int DRAIN_RING = 1 << 21;

    static int kind(int c) {
        return c & KIND_MASK;
    }

    static int airTop(int c) {
        return (c >>> TOP_SHIFT) & 0xFF;
    }

    static boolean interior(int c) {
        int k = kind(c);
        return k >= ALCOVE || (k == ARCH && airTop(c) > 0);
    }

    /** Air top of a run's barrel vault at offset |o| from the centre line. */
    private static int vaultTop(int ao) {
        return ao <= 2 ? 47 : ao == 3 ? 46 : 45;
    }

    private static int pack(int kind, int top, boolean stagnant) {
        return kind | (top << TOP_SHIFT) | (stagnant ? STAGNANT : 0);
    }

    /**
     * Everything a column of the tunnels needs to know: which kind of tunnel column it is, how
     * high its air goes, and the flags for its special blocks. NONE for plain rock.
     */
    static int classify(Level39Tile tile, int x, int z) {
        Level39Cell cell = tile.cell;
        int best = NONE;
        int bestRank = 0;
        int top = 0;
        for (int s : tile.segs) {
            int c = runColumn(cell.segs[s], x, z);
            int k = kind(c);
            if (k == NONE) {
                continue;
            }
            if (interiorKind(c)) {
                top = Math.max(top, airTop(c));
            }
            if (k > bestRank) {
                bestRank = k;
                best = c;
            }
        }
        for (int n : tile.nodes) {
            Level39Cell.Node node = cell.nodes[n];
            if (node.degree() < 3) {
                continue;
            }
            int c = chamberColumn(node, x, z);
            int k = kind(c);
            if (k == NONE) {
                continue;
            }
            if (interiorKind(c)) {
                top = Math.max(top, airTop(c));
            }
            if (k > bestRank || (k == bestRank && k == RIM)) {
                // Inside a chamber the chamber's own rim tiles (edges toward its pool) win.
                bestRank = k;
                best = c;
            }
        }
        if (best == NONE) {
            return NONE;
        }
        if (interiorKind(best)) {
            best = (best & ~(0xFF << TOP_SHIFT)) | (top << TOP_SHIFT);
        }
        return best;
    }

    private static boolean interiorKind(int c) {
        int k = kind(c);
        return k >= ALCOVE || (k == ARCH && airTop(c) > 0);
    }

    /** One run's say about column (x, z). */
    private static int runColumn(Level39Cell.Seg seg, int x, int z) {
        int t = seg.alongX() ? x : z;
        int o = (seg.alongX() ? z : x) - seg.fixed();
        int ao = Math.abs(o);
        if (ao > 8) {
            return NONE;
        }
        boolean stag = seg.stagnant();
        int a = seg.a();
        int b = seg.b();
        // Extents along the run. Joined ends reach 3 (water) / 4 (rim) / 5 (wall) past the node so
        // bends and straight joins close up; dead ends get a platform, an arch and the door wall.
        int waterLo = seg.deadA() ? a : a - 3;
        int waterHi = seg.deadB() ? b : b + 3;
        int rimLo = seg.deadA() ? a - 3 : a - 4;
        int rimHi = seg.deadB() ? b + 3 : b + 4;
        int wallLo = a - 5;
        int wallHi = b + 5;
        if (t < wallLo || t > wallHi) {
            return NONE;
        }
        // Dead-end arch frame and door wall.
        boolean endA = seg.deadA() && t <= a - 4;
        boolean endB = seg.deadB() && t >= b + 4;
        if (endA || endB) {
            if (ao > 5) {
                return NONE;
            }
            boolean arch = endA ? t == a - 4 : t == b + 4;
            if (arch) {
                if (ao <= 3) {
                    int archTop = ao <= 1 ? 44 : ao == 2 ? 43 : 42;
                    return pack(ARCH, archTop, stag);
                }
                return pack(WALL, 0, stag);
            }
            int c = pack(DOOR_WALL, 0, stag);
            if (ao == 2) {
                Direction back = seg.alongX() ? (endA ? Direction.EAST : Direction.WEST) : (endA ? Direction.SOUTH : Direction.NORTH);
                c |= DOOR | (back.get2DDataValue() << DOOR_FACING_SHIFT);
                // The pair is hinged on the outsides, so the two doors read as a symmetrical pair.
                boolean right = (o > 0) == (back == Direction.EAST || back == Direction.SOUTH);
                if (right) c |= HINGE_RIGHT;
            }
            return c;
        }
        // Alcoves (side arches) open in the wall: 3 wide, one flat ceiling (a stepped one read as
        // a stack of boxes), and marble all round, including the columns just past each side
        // (without them the alcove's side walls were the bare rock the tunnels are cut through).
        int side = sideArchSide(seg);
        int archT = sideArchAt(seg, t);
        if (archT != Integer.MIN_VALUE && o * side >= 5 && o * side <= 8) {
            if (o * side == 8) {
                return pack(WALL, 0, stag);
            }
            return pack(ALCOVE, ALCOVE_TOP, stag);
        }
        if (archT == Integer.MIN_VALUE && o * side >= 6 && o * side <= 8
                && (sideArchAt(seg, t - 1) != Integer.MIN_VALUE || sideArchAt(seg, t + 1) != Integer.MIN_VALUE)) {
            return pack(WALL, 0, stag); // just past the alcove's side
        }
        if (ao <= 3 && t >= waterLo && t <= waterHi) {
            int c = pack(WATER, vaultTop(ao), stag);
            if (o == 0 && !stag && Math.floorMod(t - a - ORB_SPACING / 2, ORB_SPACING) == 0 && t > a && t < b) {
                c |= ORB;
            }
            c |= drainBits(seg, t, o);
            return c;
        }
        if (ao <= 4 && t >= rimLo && t <= rimHi) {
            int c = pack(RIM, vaultTop(ao), stag);
            if (ao == 4 && t >= waterLo && t <= waterHi) {
                Direction toWater = seg.alongX() ? (o > 0 ? Direction.NORTH : Direction.SOUTH) : (o > 0 ? Direction.WEST : Direction.EAST);
                c |= (1 + toWater.get2DDataValue()) << EDGE_SHIFT;
            } else if (ao <= 3 && (seg.deadA() && t == a - 1 || seg.deadB() && t == b + 1)) {
                Direction toWater = seg.alongX() ? (t < a ? Direction.EAST : Direction.WEST) : (t < a ? Direction.SOUTH : Direction.NORTH);
                c |= (1 + toWater.get2DDataValue()) << EDGE_SHIFT;
            }
            return c;
        }
        if (ao == 5 || (ao <= 5 && (t == wallLo || t == wallHi))) {
            return pack(WALL, 0, stag);
        }
        return NONE;
    }

    private static int drainBits(Level39Cell.Seg seg, int t, int o) {
        int d0 = drainStart(seg, t);
        if (d0 == Integer.MIN_VALUE) {
            return 0;
        }
        if (o >= 0 && o <= 1 && t >= d0 && t <= d0 + 1) {
            return DRAIN_INNER;
        }
        if (o >= -1 && o <= 2 && t >= d0 - 1 && t <= d0 + 2) {
            return DRAIN_RING;
        }
        return 0;
    }

    /** Start (along) of the drain whose 4x4 ring covers along position t, or MIN_VALUE. */
    private static int drainStart(Level39Cell.Seg seg, int t) {
        int phase = 20 + Hash.range(Hash.mix(seg.salt() + 31), 20);
        int k = Math.floorDiv(t - seg.a() - phase + 1, DRAIN_SPACING);
        int d0 = seg.a() + phase + k * DRAIN_SPACING;
        if (t < d0 - 1 || t > d0 + 2 || d0 < seg.a() + 12 || d0 + 1 > seg.b() - 12) {
            return Integer.MIN_VALUE;
        }
        return d0;
    }

    /** Which wall (+1 or -1 in offset) the run's side arches open in. */
    private static int sideArchSide(Level39Cell.Seg seg) {
        return (seg.salt() & 8) == 0 ? 1 : -1;
    }

    /** Middle (along) of the side arch covering along position t (within 1), or MIN_VALUE. */
    private static int sideArchAt(Level39Cell.Seg seg, int t) {
        int phase = 12 + Hash.range(Hash.mix(seg.salt() + 41), 25);
        int k = Math.floorDiv(t - seg.a() - phase + 1, ARCH_SPACING);
        int m = seg.a() + phase + k * ARCH_SPACING;
        if (Math.abs(t - m) > 1 || m < seg.a() + 10 || m > seg.b() - 10) {
            return Integer.MIN_VALUE;
        }
        return m;
    }

    /** A domed chamber's say about column (x, z). */
    private static int chamberColumn(Level39Cell.Node node, int x, int z) {
        int dx = x - node.x();
        int dz = z - node.z();
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > 8.5) {
            return NONE;
        }
        if (r > 7.5) {
            return pack(WALL, 0, false);
        }
        int dome = 40 + (int) Math.round(Math.sqrt(Math.max(0, 100 - r * r)));
        if (r <= 3.5) {
            int c = pack(WATER, dome, false);
            if (dx == 0 && dz == 0) c |= ORB;
            return c;
        }
        int c = pack(RIM, dome, false);
        if (r <= 4.6) {
            Direction toWater = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST)
                    : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
            c |= (1 + toWater.get2DDataValue()) << EDGE_SHIFT;
        }
        // Four more orbs round the dome, on the diagonals (clear of the runs coming in).
        if (Math.abs(dx) == 4 && Math.abs(dz) == 4) {
            c |= ORB;
        }
        return c;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /**
     * Writes the tunnel blocks of column (x, z) over the rock already there, and returns the
     * column's highest air y in the tunnels (0 if it is not inside a tunnel), so the holes above
     * know where to stop.
     */
    static int fillColumn(Level39Tile tile, int x, int z, LevelLayout.Column out) {
        if (tile.segs.length == 0 && tile.nodes.length == 0) {
            return 0;
        }
        int c = classify(tile, x, z);
        int k = kind(c);
        if (k == NONE) {
            return 0;
        }
        boolean stag = (c & STAGNANT) != 0;
        BlockState marble = stag ? Level39Blocks.MARBLE_STAINED.get().defaultBlockState() : Level39Blocks.MARBLE.get().defaultBlockState();
        BlockState rim = Level39Blocks.TILE_RIM.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int top = airTop(c);
        // Marble floor bed under every tunnel column.
        for (int y = 35; y <= 37; y++) {
            out.set(y, marble);
        }
        switch (k) {
            case WALL -> {
                for (int y = 38; y <= 50; y++) out.set(y, marble);
                // The round wall of a junction chamber: the pieces follow its curve.
                if (nearDome(tile, x, z)) {
                    out.set(38, rim);
                    out.set(39, rim);
                    out.set(40, rim);
                    return ceiling(tile, x, z, marble, out);
                }
                wallPieces(tile, x, z, out);
                return 0;
            }
            case DOOR_WALL -> {
                for (int y = 38; y <= 50; y++) out.set(y, marble);
                if ((c & DOOR) != 0) {
                    Direction facing = Direction.from2DDataValue((c >>> DOOR_FACING_SHIFT) & 3);
                    BlockState door = Level39Blocks.PANEL_DOOR.get().defaultBlockState()
                            .setValue(DoorBlock.FACING, facing)
                            .setValue(DoorBlock.HINGE, (c & HINGE_RIGHT) != 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                            .setValue(DoorBlock.OPEN, false)
                            .setValue(HouseDoorBlock.LOCKED, true);
                    out.set(RIM_FEET_Y, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    out.set(RIM_FEET_Y + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                }
                return 0;
            }
            case ARCH, ALCOVE -> {
                out.set(38, rim);
                out.set(39, rim);
                out.set(40, rim);
                for (int y = RIM_FEET_Y; y <= top; y++) out.set(y, air);
                for (int y = top + 1; y <= 50; y++) out.set(y, marble);
                return top;
            }
            case RIM -> {
                out.set(38, rim);
                out.set(39, rim);
                int edge = (c >>> EDGE_SHIFT) & 7;
                out.set(40, edge == 0 ? rim : Level39Blocks.TILE_EDGE.get().defaultBlockState()
                        .setValue(WallDecalBlock.FACING, Direction.from2DDataValue(edge - 1)));
            }
            default -> { // WATER
                out.set(38, Level39Blocks.TILE_FLOOR.get().defaultBlockState());
                BlockState water = Blocks.WATER.defaultBlockState();
                out.set(39, water);
                out.set(40, water);
            }
        }
        int airTop = ceiling(tile, x, z, marble, out);
        if (k == RIM && inFrontOfShaft(tile, x, z)) {
            // A pool of light on the walkway at the foot of every shaft: the way back up to the
            // town shows from far down the tunnel.
            out.set(RIM_FEET_Y + 1, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, SHAFT_LIGHT));
        }
        if (k == WATER && stag && Hash.unit(tile.seed, x, z, 3980) < 0.22) {
            // Patches of greenish scum float on the still water of a stagnant run.
            out.set(RIM_FEET_Y, Level39Blocks.STAGNANT_FILM.get().defaultBlockState());
        }
        if ((c & ORB) != 0) {
            out.set(airTop, Level39Blocks.ORB_LIGHT.get().defaultBlockState());
        } else if (k == WATER && !stag && fillLightAt(tile, x, z)) {
            // Soft fill light low along both walls, so the lower walls are as bright as the vault
            // (the orbs up in the crown light the curve far better than the foot of the wall).
            out.set(RIM_FEET_Y + 1, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, FILL_LIGHT));
        }
        if ((c & DRAIN_INNER) != 0) {
            // A square iron-lined drain straight up out of the crown of the vault.
            for (int y = airTop + 1; y <= 52; y++) out.set(y, air);
            out.set(53, Level39Blocks.DRAIN_LINING.get().defaultBlockState());
        } else if ((c & DRAIN_RING) != 0) {
            BlockState lining = Level39Blocks.DRAIN_LINING.get().defaultBlockState();
            for (int y = 48; y <= 53; y++) out.set(y, lining);
        }
        return Math.max(airTop, top);
    }

    /** Light level at the foot of an access shaft. */
    private static final int SHAFT_LIGHT = 14;

    /** True for the walkway cell right in front of an access shaft's rungs. */
    private static boolean inFrontOfShaft(Level39Tile tile, int x, int z) {
        for (int k : tile.shafts) {
            Level39Cell.Shaft shaft = tile.cell.shafts[k];
            if (shaft.x() + shaft.ladderFacing().getStepX() == x && shaft.z() + shaft.ladderFacing().getStepZ() == z) {
                return true;
            }
        }
        return false;
    }

    /** Level of the hidden fill lights along the foot of the walls (vanilla light blocks). */
    private static final int FILL_LIGHT = 12;
    /** One fill light on each side of a run every this many blocks (the two sides staggered). */
    private static final int FILL_SPACING = 4;

    /** True in the water column beside a rim (offset +-3) where a fill light hangs just above the water. */
    private static boolean fillLightAt(Level39Tile tile, int x, int z) {
        for (int s : tile.segs) {
            Level39Cell.Seg seg = tile.cell.segs[s];
            int t = seg.alongX() ? x : z;
            int o = (seg.alongX() ? z : x) - seg.fixed();
            if (Math.abs(o) == 3 && t > seg.a() + 1 && t < seg.b() - 1
                    && Math.floorMod(t - seg.a() + (o > 0 ? FILL_SPACING / 2 : 0), FILL_SPACING) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The straight wall of a run under the springing of the vault (offset +-5, the walkway's two
     * rows): marble pieces lit like the vault above them, so wall and vault read as one shell.
     */
    private static void wallPieces(Level39Tile tile, int x, int z, LevelLayout.Column out) {
        for (int s : tile.segs) {
            Level39Cell.Seg seg = tile.cell.segs[s];
            int t = seg.alongX() ? x : z;
            int o = (seg.alongX() ? z : x) - seg.fixed();
            int rimLo = seg.deadA() ? seg.a() - 3 : seg.a() - 4;
            int rimHi = seg.deadB() ? seg.b() + 3 : seg.b() + 4;
            if (Math.abs(o) == 5 && t >= rimLo && t <= rimHi) {
                out.set(RIM_FEET_Y, Level39Vault.barrelPiece(seg.alongX(), o, 0));
                out.set(RIM_FEET_Y + 1, Level39Vault.barrelPiece(seg.alongX(), o, 1));
                return;
            }
        }
    }

    /** Rows of the vault and dome pieces counted from the walkway (y41 .. y50). */
    private static final int CEILING_ROWS = 10;

    /**
     * Writes the curved ceiling of an interior column, row by row from the walkway up: air under
     * the curve, a vault or dome piece where the curve passes, marble over it (three blocks thick
     * above the highest piece). Returns the highest wholly-open y (where an orb can hang).
     */
    private static int ceiling(Level39Tile tile, int x, int z, BlockState marble, LevelLayout.Column out) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int lastAir = RIM_FEET_Y - 1;
        int lastOpen = RIM_FEET_Y - 1;
        for (int row = 0; row < CEILING_ROWS; row++) {
            int y = Level39Vault.BASE_Y + row;
            BlockState s = ceilingCell(tile, x, z, row);
            if (s == null) {
                out.set(y, air);
                lastAir = y;
                lastOpen = y;
            } else if (s == marble || s.is(Level39Blocks.MARBLE.get()) || s.is(Level39Blocks.MARBLE_STAINED.get())) {
                out.set(y, marble);
            } else {
                out.set(y, s);
                lastOpen = y;
            }
        }
        for (int y = Math.max(lastOpen + 1, Level39Vault.BASE_Y + CEILING_ROWS); y <= lastOpen + 3; y++) {
            out.set(y, marble);
        }
        return lastAir;
    }

    /** True when (x, z) is close enough to a junction chamber for its round wall to curve through it. */
    private static boolean nearDome(Level39Tile tile, int x, int z) {
        for (int n : tile.nodes) {
            Level39Cell.Node node = tile.cell.nodes[n];
            if (node.degree() >= 3 && Math.abs(x - node.x()) <= 8 && Math.abs(z - node.z()) <= 8) {
                return true;
            }
        }
        return false;
    }

    /**
     * The block of the ceiling cell {@code row} above the walkway in column (x, z): null for air,
     * a vault or dome piece, or plain marble. Air wins (a run opening into a chamber cuts through
     * the chamber's wall); where only pieces meet, the higher curve wins.
     */
    @Nullable
    private static BlockState ceilingCell(Level39Tile tile, int x, int z, int row) {
        BlockState junction = junctionCell(tile, x, z, row);
        if (junction != null) {
            return junction;
        }
        Level39Cell cell = tile.cell;
        BlockState piece = null;
        double pieceHeight = Double.NEGATIVE_INFINITY;
        for (int s : tile.segs) {
            Level39Cell.Seg seg = cell.segs[s];
            int t = seg.alongX() ? x : z;
            int o = (seg.alongX() ? z : x) - seg.fixed();
            int rimLo = seg.deadA() ? seg.a() - 3 : seg.a() - 4;
            int rimHi = seg.deadB() ? seg.b() + 3 : seg.b() + 4;
            if (Math.abs(o) > 4 || t < rimLo || t > rimHi) {
                continue;
            }
            int kind = Level39Vault.barrelCell(Math.abs(o), row);
            if (kind == Level39Vault.AIR) {
                return null;
            }
            if (kind == Level39Vault.PIECE) {
                double h = Level39Vault.barrelCeiling(Math.abs(o));
                if (h > pieceHeight) {
                    pieceHeight = h;
                    piece = Level39Vault.barrelPiece(seg.alongX(), o, row);
                }
            }
        }
        for (int n : tile.nodes) {
            Level39Cell.Node node = cell.nodes[n];
            if (node.degree() < 3) {
                continue;
            }
            int dx = x - node.x();
            int dz = z - node.z();
            if (Math.abs(dx) > 8 || Math.abs(dz) > 8) {
                continue;
            }
            int ab = Level39Vault.domeCanonical(dx, dz);
            int kind = Level39Vault.domeCell(ab >> 4, ab & 15, row);
            if (kind == Level39Vault.AIR) {
                return null;
            }
            if (kind == Level39Vault.PIECE) {
                double h = Level39Vault.domeCeiling(Math.hypot(dx, dz));
                if (h > pieceHeight || piece == null) {
                    pieceHeight = h;
                    piece = Level39Vault.domePiece(dx, dz, row);
                }
            }
        }
        return piece != null ? piece : Level39Blocks.MARBLE.get().defaultBlockState();
    }

    /**
     * A piece where two curved ceilings meet, or null: a junction piece where a run comes into a
     * chamber (see {@code Level39Vault.junctionPiece}; only the run along the cell's own side
     * matters, the others are under the dome there), or a corner piece where a run turns
     * ({@code Level39Vault.cornerPiece}).
     */
    @Nullable
    private static BlockState junctionCell(Level39Tile tile, int x, int z, int row) {
        Level39Cell cell = tile.cell;
        for (int n : tile.nodes) {
            Level39Cell.Node node = cell.nodes[n];
            int dx = x - node.x();
            int dz = z - node.z();
            if (node.degree() == 2 && Math.abs(dx) <= 9 && Math.abs(dz) <= 9) {
                BlockState piece = cornerCell(tile, node, dx, dz, row);
                if (piece != null) {
                    return piece;
                }
                continue;
            }
            if (node.degree() < 3 || Math.abs(dx) > 8 || Math.abs(dz) > 8) {
                continue;
            }
            for (int s : tile.segs) {
                Level39Cell.Seg seg = cell.segs[s];
                int at = seg.alongX() ? node.x() : node.z();
                if (seg.fixed() != (seg.alongX() ? node.z() : node.x()) || at < seg.a() || at > seg.b()) {
                    continue;
                }
                // the run leaves the chamber on each side the segment carries on past the node
                if (seg.b() > at) {
                    BlockState piece = Level39Vault.junctionPiece(seg.alongX() ? Direction.EAST : Direction.SOUTH, dx, dz, row);
                    if (piece != null) {
                        return piece;
                    }
                }
                if (seg.a() < at) {
                    BlockState piece = Level39Vault.junctionPiece(seg.alongX() ? Direction.WEST : Direction.NORTH, dx, dz, row);
                    if (piece != null) {
                        return piece;
                    }
                }
            }
        }
        return null;
    }

    /** The corner piece at (dx, dz) from a two-run node, if the runs there turn (not straight on). */
    @Nullable
    private static BlockState cornerCell(Level39Tile tile, Level39Cell.Node node, int dx, int dz, int row) {
        Direction first = null;
        Direction second = null;
        for (int s : tile.segs) {
            Level39Cell.Seg seg = tile.cell.segs[s];
            if (seg.fixed() != (seg.alongX() ? node.z() : node.x())) {
                continue;
            }
            int at = seg.alongX() ? node.x() : node.z();
            Direction leaves = at == seg.a() ? (seg.alongX() ? Direction.EAST : Direction.SOUTH)
                    : at == seg.b() ? (seg.alongX() ? Direction.WEST : Direction.NORTH) : null;
            if (leaves == null) {
                continue;
            }
            if (first == null) {
                first = leaves;
            } else {
                second = leaves;
            }
        }
        if (first == null || second == null || first.getAxis() == second.getAxis()) {
            return null; // straight on: one barrel, nothing to meet
        }
        // Turned like the dome: the piece whose runs leave toward `turn` and its counter-clockwise neighbour.
        Direction turn = second == first.getCounterClockWise() ? first : second;
        return Level39Vault.cornerPiece(turn, dx, dz, row);
    }

    /** The access shafts of this tile that stand in column (x, z): rungs up to a hatch. */
    static boolean shaftColumn(Level39Tile tile, int x, int z, LevelLayout.Column out) {
        for (int k : tile.shafts) {
            Level39Cell.Shaft shaft = tile.cell.shafts[k];
            if (shaft.x() != x || shaft.z() != z) {
                continue;
            }
            out.set(40, Level39Blocks.TILE_RIM.get().defaultBlockState());
            BlockState rungs = Level39Blocks.RUNGS.get().defaultBlockState().setValue(LadderBlock.FACING, shaft.ladderFacing());
            for (int y = RIM_FEET_Y; y < Level39Layout.GROUND_Y; y++) {
                out.set(y, rungs);
            }
            out.set(Level39Layout.GROUND_Y, Level39Blocks.HATCH.get().defaultBlockState()
                    .setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(TrapDoorBlock.FACING, shaft.ladderFacing())
                    .setValue(TrapDoorBlock.OPEN, false));
            return true;
        }
        return false;
    }

    // ==========================================================================================
    // Queries
    // ==========================================================================================

    /** True when column (x, z) is inside a tunnel (on the water, a rim, a platform or an alcove). */
    public static boolean tunnelAt(long seed, int x, int z) {
        if (!Level39Layout.inTown(x, z)) {
            return false;
        }
        return interior(classify(Level39Tile.of(seed, x, z), x, z));
    }

    /** True when column (x, z) is inside a stagnant (Hydrolitis) tunnel run. */
    public static boolean isStagnant(long seed, int x, int z) {
        if (!Level39Layout.inTown(x, z)) {
            return false;
        }
        int c = classify(Level39Tile.of(seed, x, z), x, z);
        return interior(c) && (c & STAGNANT) != 0;
    }

    /** True when column (x, z) has dry tunnel floor to stand on (rim, platform, alcove), feet at {@link #RIM_FEET_Y}. */
    public static boolean isTunnelWalkable(long seed, int x, int z) {
        if (!Level39Layout.inTown(x, z)) {
            return false;
        }
        int k = kind(classify(Level39Tile.of(seed, x, z), x, z));
        return k == RIM || k == ALCOVE || k == ARCH;
    }

    /** True when column (x, z) is tunnel water (y39-40; swimmers' feet about y40-41). */
    public static boolean isTunnelWater(long seed, int x, int z) {
        if (!Level39Layout.inTown(x, z)) {
            return false;
        }
        return kind(classify(Level39Tile.of(seed, x, z), x, z)) == WATER;
    }

    /** True when column (x, z) is part of a side arch (alcove). */
    public static boolean sideArchAt(long seed, int x, int z) {
        if (!Level39Layout.inTown(x, z)) {
            return false;
        }
        return kind(classify(Level39Tile.of(seed, x, z), x, z)) == ALCOVE;
    }

    /**
     * A rim spot (feet block, y = {@link #RIM_FEET_Y}) on the tunnel run nearest to (x, z),
     * away from junctions and holes, or null if this town has no tunnels at all. Used when the
     * Poolrooms' drain empties a traveller into Level 39.
     */
    @Nullable
    public static BlockPos nearestRim(long seed, int x, int z) {
        // Search the town (x, z) belongs to; from inside the superstructure, the town it faces.
        int cx = Level39Cell.cellIndex(x);
        int cz = Level39Cell.cellIndex(z);
        Level39Cell cell = Level39Cell.byIndex(seed, cx, cz);
        Level39Cell.Seg bestSeg = null;
        int bestT = 0;
        long bestD = Long.MAX_VALUE;
        for (Level39Cell.Seg seg : cell.segs) {
            if (seg.b() - seg.a() < 30) {
                continue;
            }
            int t = Math.max(seg.a() + 12, Math.min(seg.b() - 12, seg.alongX() ? x : z));
            int px = seg.alongX() ? t : seg.fixed();
            int pz = seg.alongX() ? seg.fixed() : t;
            long d = (long) (px - x) * (px - x) + (long) (pz - z) * (pz - z);
            if (d < bestD) {
                bestD = d;
                bestSeg = seg;
                bestT = t;
            }
        }
        if (bestSeg == null) {
            return null;
        }
        int across = bestSeg.alongX() ? z : x;
        int side = across >= bestSeg.fixed() ? 1 : -1;
        // Step along the run until the rim is plain walkway (not a hole's landing or a shaft).
        for (int step = 0; step < 40; step++) {
            int t = bestT + (step % 2 == 0 ? step / 2 : -(step / 2 + 1));
            for (int s = 0; s < 2; s++) {
                int o = (s == 0 ? side : -side) * Level39Layout.TUNNEL_HALF;
                int px = bestSeg.alongX() ? t : bestSeg.fixed() + o;
                int pz = bestSeg.alongX() ? bestSeg.fixed() + o : t;
                if (isTunnelWalkable(seed, px, pz) && !shaftAt(cell, px, pz)) {
                    return new BlockPos(px, RIM_FEET_Y, pz);
                }
            }
        }
        return null;
    }

    private static boolean shaftAt(Level39Cell cell, int x, int z) {
        for (Level39Cell.Shaft s : cell.shafts) {
            if (Math.abs(s.x() - x) <= 1 && Math.abs(s.z() - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every tunnel feature within {@code radius} blocks (square) of (x, z): orbs, side arches,
     * drains, shafts, holes, dead ends and junction chambers. Pure; fine on the client thread.
     */
    public static void featuresNear(long seed, int x, int z, int radius, Consumer<Feature> out) {
        int c0x = Level39Cell.cellIndex(x - radius);
        int c1x = Level39Cell.cellIndex(x + radius);
        int c0z = Level39Cell.cellIndex(z - radius);
        int c1z = Level39Cell.cellIndex(z + radius);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                cellFeatures(Level39Cell.byIndex(seed, cx, cz), x - radius, z - radius, x + radius, z + radius, out);
            }
        }
    }

    private static boolean in(int px, int pz, int x0, int z0, int x1, int z1) {
        return px >= x0 && px <= x1 && pz >= z0 && pz <= z1;
    }

    private static void cellFeatures(Level39Cell cell, int x0, int z0, int x1, int z1, Consumer<Feature> out) {
        for (Level39Cell.Seg seg : cell.segs) {
            int sx0 = seg.alongX() ? seg.a() - 6 : seg.fixed() - 9;
            int sx1 = seg.alongX() ? seg.b() + 6 : seg.fixed() + 9;
            int sz0 = seg.alongX() ? seg.fixed() - 9 : seg.a() - 6;
            int sz1 = seg.alongX() ? seg.fixed() + 9 : seg.b() + 6;
            if (sx1 < x0 || sx0 > x1 || sz1 < z0 || sz0 > z1) {
                continue;
            }
            boolean stag = seg.stagnant();
            int lo = Math.max(seg.a(), seg.alongX() ? x0 : z0);
            int hi = Math.min(seg.b(), seg.alongX() ? x1 : z1);
            Direction plus = seg.alongX() ? Direction.EAST : Direction.SOUTH;
            // Orbs.
            if (!stag) {
                int first = seg.a() + ORB_SPACING / 2;
                int t = first + Math.max(0, Math.floorDiv(lo - first + ORB_SPACING - 1, ORB_SPACING)) * ORB_SPACING;
                for (; t <= hi && t < seg.b(); t += ORB_SPACING) {
                    int px = seg.alongX() ? t : seg.fixed();
                    int pz = seg.alongX() ? seg.fixed() : t;
                    if (in(px, pz, x0, z0, x1, z1)) {
                        out.accept(new Feature(Kind.ORB, px, RUN_ORB_Y, pz, Direction.DOWN, false));
                    }
                }
            }
            // Side arches and drains.
            int side = sideArchSide(seg);
            Direction toTunnel = seg.alongX() ? (side > 0 ? Direction.NORTH : Direction.SOUTH) : (side > 0 ? Direction.WEST : Direction.EAST);
            int archPhase = 12 + Hash.range(Hash.mix(seg.salt() + 41), 25);
            for (int m = seg.a() + archPhase; m <= seg.b() - 10; m += ARCH_SPACING) {
                if (m < seg.a() + 10 || m < lo - 2 || m > hi + 2) continue;
                int px = seg.alongX() ? m : seg.fixed() + side * 6;
                int pz = seg.alongX() ? seg.fixed() + side * 6 : m;
                if (in(px, pz, x0, z0, x1, z1)) {
                    out.accept(new Feature(Kind.SIDE_ARCH, px, RIM_FEET_Y, pz, toTunnel, stag));
                }
            }
            int drainPhase = 20 + Hash.range(Hash.mix(seg.salt() + 31), 20);
            for (int d = seg.a() + drainPhase; d + 1 <= seg.b() - 12; d += DRAIN_SPACING) {
                if (d < seg.a() + 12 || d < lo - 2 || d > hi + 2) continue;
                int px = seg.alongX() ? d : seg.fixed();
                int pz = seg.alongX() ? seg.fixed() : d;
                if (in(px, pz, x0, z0, x1, z1)) {
                    out.accept(new Feature(Kind.DRAIN, px, DRAIN_Y, pz, Direction.DOWN, stag));
                }
            }
            // Dead ends.
            if (seg.deadA()) {
                int t = seg.a() - 5;
                int px = seg.alongX() ? t : seg.fixed();
                int pz = seg.alongX() ? seg.fixed() : t;
                if (in(px, pz, x0, z0, x1, z1)) {
                    out.accept(new Feature(Kind.DEAD_END, px, RIM_FEET_Y, pz, plus, stag));
                }
            }
            if (seg.deadB()) {
                int t = seg.b() + 5;
                int px = seg.alongX() ? t : seg.fixed();
                int pz = seg.alongX() ? seg.fixed() : t;
                if (in(px, pz, x0, z0, x1, z1)) {
                    out.accept(new Feature(Kind.DEAD_END, px, RIM_FEET_Y, pz, plus.getOpposite(), stag));
                }
            }
        }
        for (Level39Cell.Node node : cell.nodes) {
            if (node.degree() >= 3 && in(node.x(), node.z(), x0, z0, x1, z1)) {
                out.accept(new Feature(Kind.JUNCTION, node.x(), RIM_FEET_Y, node.z(), Direction.UP, false));
                out.accept(new Feature(Kind.ORB, node.x(), DOME_ORB_Y, node.z(), Direction.DOWN, false));
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        out.accept(new Feature(Kind.ORB, node.x() + 4 * sx, RING_ORB_Y, node.z() + 4 * sz, Direction.DOWN, false));
                    }
                }
            }
        }
        for (Level39Cell.Hole hole : cell.holes) {
            if (in(hole.x(), hole.z(), x0, z0, x1, z1)) {
                out.accept(new Feature(Kind.HOLE, hole.x(), Level39Layout.GROUND_Y, hole.z(), Direction.DOWN,
                        cell.segs[hole.seg()].stagnant()));
            }
        }
        for (Level39Cell.Shaft shaft : cell.shafts) {
            if (in(shaft.x(), shaft.z(), x0, z0, x1, z1)) {
                out.accept(new Feature(Kind.SHAFT, shaft.x(), RIM_FEET_Y, shaft.z(), shaft.ladderFacing(),
                        cell.segs[shaft.seg()].stagnant()));
            }
        }
    }

    private Level39Tunnels() {}
}
