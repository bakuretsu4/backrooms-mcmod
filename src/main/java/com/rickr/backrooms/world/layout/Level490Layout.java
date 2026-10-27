package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.GiantChainBlock;
import com.rickr.backrooms.block.IronLanternBlock;
import com.rickr.backrooms.registry.Level490Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Level 490, "Penance": stone platforms and fenced stone bridges hung from giant iron chains over
 * a sea of storm cloud, at night (docs/level-490-penance.md).
 *
 * <pre>
 *   y 201-511    the chains, rising from the platforms' corners and the bridges' middles into the storm
 *   y 201        railings (spiked iron), lantern posts, shelters, shackles
 *   y 200        the paving every platform and bridge is decked with
 *   y 198-199    dressed stone underneath
 *   y < 150      falling this far into the clouds sends you back (world/Level490Rules)
 * </pre>
 *
 * <p>Platforms stand on a grid every {@value #SPACING} blocks (a few missing), joined by straight
 * bridges along the grid lines: main bridges 5 wide inside their railings, side bridges 3 wide,
 * and some side bridges broken off in the middle (mostly too far to jump, now and then a 2-block
 * gap you might risk). Each {@value #REGION}-block region holds one <b>site</b>, where the strange
 * structure stands (stage 2; for now a great round platform ringed with chains, one stretch of its
 * railing broken away), and about 300 blocks from it the big <b>arrival</b> platform where you come
 * in. A route of main bridges always joins the two. Falling off the site is the way out (the leap,
 * to Level 9); falling anywhere else sends you back to your last checkpoint. Shelters (little
 * stone huts with a checkpoint radio) are safe rooms.
 */
public final class Level490Layout extends LevelLayout {
    public static final int WORLD_HEIGHT = 512;
    /** The paving layer of every platform and bridge. */
    public static final int DECK_Y = 200;
    public static final int STAND_Y = DECK_Y + 1;
    /** Below this you have fallen into the clouds. */
    public static final int FALL_Y = 150;
    public static final int SPACING = 48;
    public static final int REGION = 1024;
    /** One site per region, far enough from its edges that its arrival platform and route fit in. */
    static final ExitLocator SITES = new ExitLocator(REGION, 400, 490490);
    /** The site's round platform. */
    public static final int SITE_RADIUS = 22;
    /** Falling from within this far of a site's centre is the leap out. */
    public static final int LEAP_REACH = SITE_RADIUS + 10;
    private static final int ARRIVAL_HALF = 11;
    /** Arrival platform offsets from the site in grid steps: all about 300 blocks away. */
    private static final int[][] ARRIVAL_STEPS = arrivalSteps();
    /** Walls of a shelter: 5 x 5 outside, 3 high inside, under a slate roof. */
    private static final int SHELTER = 5;

    // Column kinds.
    private static final int VOID = 0;
    private static final int DECK = 1;
    private static final int RAIL = 2;
    private static final int CHAIN = 3;
    private static final int POST_LIT = 4;
    private static final int POST_DARK = 5;
    private static final int SHELTER_WALL = 6;
    private static final int SHELTER_DOOR = 7;
    private static final int SHELTER_IN = 8;
    private static final int SHELTER_RADIO = 9;
    private static final int SHACKLE = 10;

    public Level490Layout() {
        super(3, SITES);
    }

    @Override
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    @Override
    public int highestCeilingY() {
        return STAND_Y + SHELTER - 2;
    }

    @Override
    public int lowestFixtureY() {
        return STAND_Y;
    }

    // ==========================================================================================
    // The plan: regions, sites, the grid of platforms, bridges
    // ==========================================================================================

    /** A region's site and arrival platform (grid coordinates) and how the route between them turns. */
    private record Site(int si, int sj, int ai, int aj) {}

    private static int[][] arrivalSteps() {
        java.util.List<int[]> steps = new java.util.ArrayList<>();
        for (int a = -7; a <= 7; a++) {
            for (int b = -7; b <= 7; b++) {
                double d = SPACING * Math.hypot(a, b);
                if (d >= 280 && d <= 320) {
                    steps.add(new int[] {a, b});
                }
            }
        }
        return steps.toArray(new int[0][]);
    }

    private static Site site(long seed, int regionX, int regionZ) {
        int si = Math.round(SITES.exitX(seed, regionX, regionZ) / (float) SPACING);
        int sj = Math.round(SITES.exitZ(seed, regionX, regionZ) / (float) SPACING);
        int[] step = ARRIVAL_STEPS[Hash.range(Hash.of(seed, regionX, regionZ, 4902), ARRIVAL_STEPS.length)];
        return new Site(si, sj, si + step[0], sj + step[1]);
    }

    /** The site of the region grid node (i, j) lies in. */
    private static Site siteOfNode(long seed, int i, int j) {
        return site(seed, Math.floorDiv(i * SPACING, REGION), Math.floorDiv(j * SPACING, REGION));
    }

    /** Centre {x, z} of the site nearest (x, z) (the region's). */
    public static int[] siteCentre(long seed, int x, int z) {
        Site s = site(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
        return new int[] {s.si * SPACING, s.sj * SPACING};
    }

    /** The route runs from the arrival platform along its row to the site's column, then along that. */
    private static boolean onRouteRow(Site s, int i, int j) {
        return j == s.aj && i >= Math.min(s.ai, s.si) && i <= Math.max(s.ai, s.si);
    }

    private static boolean onRouteColumn(Site s, int i, int j) {
        return i == s.si && j >= Math.min(s.aj, s.sj) && j <= Math.max(s.aj, s.sj);
    }

    private static boolean isSite(Site s, int i, int j) {
        return i == s.si && j == s.sj;
    }

    private static boolean isArrival(Site s, int i, int j) {
        return i == s.ai && j == s.aj;
    }

    private static boolean nodeExists(long seed, int i, int j) {
        Site s = siteOfNode(seed, i, j);
        return onRouteRow(s, i, j) || onRouteColumn(s, i, j) || Hash.unit(seed, i, j, 4901) < 0.8;
    }

    /** Half the width of a square platform (the railing ring), or 0 for the round site. */
    private static int half(long seed, Site s, int i, int j) {
        if (isSite(s, i, j)) {
            return 0;
        }
        if (isArrival(s, i, j)) {
            return ARRIVAL_HALF;
        }
        return 3 + Hash.range(Hash.of(seed, i, j, 4903), 7); // 7 x 7 up to 19 x 19
    }

    /** How far a platform reaches from its node toward a bridge, for spans and lanterns. */
    private static int reach(long seed, Site s, int i, int j) {
        return isSite(s, i, j) ? SITE_RADIUS : half(seed, s, i, j);
    }

    /**
     * A bridge between grid nodes (i, j) and the next node along x (alongX) or z: 0 = none, else
     * its half width (3 = main bridge, 5 wide inside the railings; 2 = side bridge, 3 wide). A
     * negative value is a broken side bridge.
     */
    private static int bridge(long seed, int i, int j, boolean alongX) {
        int i2 = alongX ? i + 1 : i;
        int j2 = alongX ? j : j + 1;
        if (!nodeExists(seed, i, j) || !nodeExists(seed, i2, j2)) {
            return 0;
        }
        Site s = siteOfNode(seed, i, j);
        boolean route = alongX ? onRouteRow(s, i, j) && onRouteRow(s, i2, j2)
                : onRouteColumn(s, i, j) && onRouteColumn(s, i2, j2);
        if (route) {
            return 3;
        }
        int salt = alongX ? 4904 : 4905;
        if (Hash.unit(seed, i, j, salt) >= 0.6) {
            return 0;
        }
        if (Hash.unit(seed, i, j, salt + 10) < 0.2) {
            return -2;
        }
        boolean main = alongX ? Math.floorMod(j, 3) == 0 : Math.floorMod(i, 3) == 0;
        return main ? 3 : 2;
    }

    /** The missing stretch of a broken bridge, along its axis: {first, last} block. */
    private static int[] gap(long seed, int i, int j, boolean alongX, int from, int to) {
        long h = Hash.of(seed, i, j, alongX ? 4906 : 4907);
        int len = Hash.unit(h) < 0.35 ? 2 : 7 + Hash.range(Hash.mix(h), 6);
        int mid = (from + to) / 2 + Hash.range(Hash.mix(h + 1), 7) - 3;
        int start = Math.max(from + 2, Math.min(to - 2 - len, mid - len / 2));
        return new int[] {start, start + len - 1};
    }

    // ==========================================================================================
    // What stands in a column
    // ==========================================================================================

    /** The kind of column (x, z), and for platforms whether it is a platform (deck two blocks thick). */
    private record Cell(int kind, boolean platform, int facing) {}

    private static final Cell NONE = new Cell(VOID, false, 0);

    private Cell cell(long seed, int x, int z) {
        int i = Math.round(x / (float) SPACING);
        int j = Math.round(z / (float) SPACING);
        if (nodeExists(seed, i, j)) {
            Cell p = platformCell(seed, i, j, x - i * SPACING, z - j * SPACING);
            if (p != null) {
                return p;
            }
        }
        // A bridge along x on grid row j, or along z on grid column i.
        int ox = z - j * SPACING;
        int bi = Math.floorDiv(x, SPACING);
        Cell b = bridgeCell(seed, bi, j, true, x - bi * SPACING, ox);
        if (b != null) {
            return b;
        }
        int oz = x - i * SPACING;
        int bj = Math.floorDiv(z, SPACING);
        b = bridgeCell(seed, i, bj, false, z - bj * SPACING, oz);
        return b != null ? b : NONE;
    }

    /** Column (dx, dz) from node (i, j)'s centre, if it is part of that node's platform. */
    private Cell platformCell(long seed, int i, int j, int dx, int dz) {
        Site s = siteOfNode(seed, i, j);
        if (isSite(s, i, j)) {
            return siteCell(seed, s, i, j, dx, dz);
        }
        int h = half(seed, s, i, j);
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax > h || az > h) {
            return null;
        }
        if (ax == h && az == h) {
            return new Cell(CHAIN, true, 0); // a chain up from each corner
        }
        if (ax == h || az == h) {
            // The railing ring, open where a bridge comes in.
            int side = ax == h ? dz : dx;
            int bw = ax == h ? Math.abs(bridge(seed, dx > 0 ? i : i - 1, j, true))
                    : Math.abs(bridge(seed, i, dz > 0 ? j : j - 1, false));
            if (bw > 0 && Math.abs(side) < bw) {
                return new Cell(DECK, true, 0);
            }
            // Lantern posts a little in from each corner (big enough platforms).
            if (h >= 5 && Math.abs(side) == h - 2) {
                boolean lit = Hash.unit(seed, i * 4 + Integer.signum(dx) + 1, j * 4 + Integer.signum(dz) + 1, 4910) < 0.55;
                return new Cell(lit ? POST_LIT : POST_DARK, true, 0);
            }
            return new Cell(RAIL, true, 0);
        }
        Cell shelter = shelterCell(seed, s, i, j, h, dx, dz);
        if (shelter != null) {
            return shelter;
        }
        if (Hash.unit(seed, i * SPACING + dx, j * SPACING + dz, 4911) < 0.02) {
            return new Cell(SHACKLE, true, Hash.range(Hash.of(seed, dx, dz, 4912), 4));
        }
        return new Cell(DECK, true, 0);
    }

    /** The round site platform: railing ring with a broken stretch (the leap), eight chains. */
    private Cell siteCell(long seed, Site s, int i, int j, int dx, int dz) {
        if (!inSite(dx, dz)) {
            return null;
        }
        // The rim: every cell with a neighbour (diagonals too) outside the circle, so the railing
        // ring is joined all the way round and nobody slips out between two posts.
        boolean rim = false;
        for (int ex = -1; ex <= 1 && !rim; ex++) {
            for (int ez = -1; ez <= 1; ez++) {
                if (!inSite(dx + ex, dz + ez)) {
                    rim = true;
                    break;
                }
            }
        }
        if (!rim) {
            return new Cell(DECK, true, 0);
        }
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        // Bridges come in along the axes.
        int bwx = Math.abs(bridge(seed, dx > 0 ? i : i - 1, j, true));
        int bwz = Math.abs(bridge(seed, i, dz > 0 ? j : j - 1, false));
        if (Math.abs(dx) > Math.abs(dz) && bwx > 0 && Math.abs(dz) < bwx
                || Math.abs(dz) >= Math.abs(dx) && bwz > 0 && Math.abs(dx) < bwz) {
            return new Cell(DECK, true, 0);
        }
        // The broken stretch: on the diagonal facing away from the arrival platform.
        double away = Math.toDegrees(Math.atan2(-(s.aj - s.sj), -(s.ai - s.si)));
        double leap = Math.round((away - 45) / 90.0) * 90.0 + 45;
        if (Math.abs(Math.IEEEremainder(angle - leap, 360)) < 9) {
            return new Cell(DECK, true, 0);
        }
        // Chains at 22.5 + 45 k degrees, lanterns at the diagonals.
        double chainPhase = Math.abs(Math.IEEEremainder(angle - 22.5, 45));
        if (chainPhase < 2.0) {
            return new Cell(CHAIN, true, 0);
        }
        double postPhase = Math.abs(Math.IEEEremainder(angle - 45, 90));
        if (postPhase < 2.0) {
            return new Cell(POST_LIT, true, 0);
        }
        return new Cell(RAIL, true, 0);
    }

    private static boolean inSite(int dx, int dz) {
        return dx * dx + dz * dz <= (SITE_RADIUS + 0.5) * (SITE_RADIUS + 0.5);
    }

    /**
     * A shelter in one corner of some big platforms (always the arrival platform): 5 x 5 stone
     * walls, a doorway facing the platform's middle, a checkpoint radio inside. A safe room.
     */
    private Cell shelterCell(long seed, Site s, int i, int j, int h, int dx, int dz) {
        int[] box = shelterBox(seed, s, i, j, h);
        if (box == null) {
            return null;
        }
        int sx = dx - box[0];
        int sz = dz - box[1];
        if (sx < 0 || sx >= SHELTER || sz < 0 || sz >= SHELTER) {
            return null;
        }
        // The doorway is in the wall facing the platform's middle along x; the radio faces it.
        int doorX = box[2] > 0 ? 0 : SHELTER - 1;
        int radioX = box[2] > 0 ? SHELTER - 2 : 1;
        if (sx == doorX && sz == 2) {
            return new Cell(SHELTER_DOOR, true, 0);
        }
        if (sx == 0 || sx == SHELTER - 1 || sz == 0 || sz == SHELTER - 1) {
            return new Cell(SHELTER_WALL, true, 0);
        }
        if (sx == radioX && sz == 2) {
            return new Cell(SHELTER_RADIO, true, box[2] > 0 ? Direction.WEST.get2DDataValue() : Direction.EAST.get2DDataValue());
        }
        return new Cell(SHELTER_IN, true, 0);
    }

    /** {min dx, min dz, x sign of the corner} of the node's shelter, or null if it has none. */
    private static int[] shelterBox(long seed, Site s, int i, int j, int h) {
        boolean arrival = isArrival(s, i, j);
        if (!arrival && (h < 8 || Hash.unit(seed, i, j, 4913) >= 0.5)) {
            return null;
        }
        long q = Hash.of(seed, i, j, 4914);
        int qx = (q & 1) == 0 ? 1 : -1;
        int qz = (q & 2) == 0 ? 1 : -1;
        // Inside the railing, in the corner: dx from h-5 to h-1 (mirrored for the other corners).
        int minX = qx > 0 ? h - SHELTER : -h + 1;
        int minZ = qz > 0 ? h - SHELTER : -h + 1;
        return new int[] {minX, minZ, qx};
    }

    /**
     * Column {@code along} blocks past node (i, j) and {@code off} blocks to the side, if it is part
     * of the bridge from (i, j) to the next node along x (alongX) or z.
     */
    private Cell bridgeCell(long seed, int i, int j, boolean alongX, int along, int off) {
        int bw = bridge(seed, i, j, alongX);
        int w = Math.abs(bw);
        if (w == 0 || Math.abs(off) > w) {
            return null;
        }
        int i2 = alongX ? i + 1 : i;
        int j2 = alongX ? j : j + 1;
        Site s1 = siteOfNode(seed, i, j);
        Site s2 = siteOfNode(seed, i2, j2);
        int from = reach(seed, s1, i, j);
        int to = SPACING - reach(seed, s2, i2, j2);
        if (along < from || along > to) {
            return null;
        }
        if (bw < 0) {
            int[] g = gap(seed, i, j, alongX, from, to);
            if (along >= g[0] && along <= g[1]) {
                return NONE;
            }
            // The chains hold up what is left on either side of the gap.
            if (Math.abs(off) == w && (along == g[0] - 1 || along == g[1] + 1)) {
                return new Cell(CHAIN, false, 0);
            }
        } else if (Math.abs(off) == w && along == SPACING / 2) {
            return new Cell(CHAIN, false, 0);
        }
        if (Math.abs(off) < w) {
            return new Cell(DECK, false, 0);
        }
        // Railing; lantern posts every 16 blocks, on alternating sides.
        int gx = alongX ? i * SPACING + along : i * SPACING + off;
        int gz = alongX ? j * SPACING + off : j * SPACING + along;
        if (Math.floorMod(along, 16) == 8 && (Math.floorDiv(along, 16) % 2 == 0) == (off > 0)) {
            return new Cell(Hash.unit(seed, gx, gz, 4915) < 0.5 ? POST_LIT : POST_DARK, false, 0);
        }
        // Stretches of railing gone: the unsafe parts.
        int seg = Math.floorDiv(along, 7);
        long hseg = Hash.of(seed, alongX ? i * 8 + seg : seg, alongX ? j * 2 + (off > 0 ? 1 : 0) : i * 2 + (off > 0 ? 1 : 0), alongX ? 4916 : 4917);
        double damage = w == 3 ? 0.05 : 0.12;
        if (Hash.unit(hseg) < damage && Math.floorMod(along, 7) >= 1 && Math.floorMod(along, 7) <= 1 + Hash.range(Hash.mix(hseg), 3)) {
            return new Cell(DECK, false, 0);
        }
        return new Cell(RAIL, false, 0);
    }

    // ==========================================================================================
    // Building the columns
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        Cell c = cell(seed, x, z);
        if (c.kind == VOID) {
            return;
        }
        out.set(DECK_Y - 1, Level490Blocks.STONE.get().defaultBlockState());
        if (c.platform) {
            out.set(DECK_Y - 2, Level490Blocks.STONE.get().defaultBlockState());
        }
        boolean shelter = c.kind >= SHELTER_WALL && c.kind <= SHELTER_RADIO;
        out.set(DECK_Y, shelter ? Level490Blocks.STONE.get().defaultBlockState() : Level490Blocks.PAVING.get().defaultBlockState());
        switch (c.kind) {
            case RAIL -> out.set(STAND_Y, connect(seed, x, z, Level490Blocks.RAILING.get().defaultBlockState()));
            case POST_LIT, POST_DARK -> {
                out.set(STAND_Y, connect(seed, x, z, Level490Blocks.POST.get().defaultBlockState()));
                out.set(STAND_Y + 1, Level490Blocks.LANTERN.get().defaultBlockState().setValue(IronLanternBlock.LIT, c.kind == POST_LIT));
            }
            case CHAIN -> {
                for (int y = STAND_Y; y < WORLD_HEIGHT; y++) {
                    out.set(y, Level490Blocks.CHAIN.get().defaultBlockState().setValue(GiantChainBlock.TWIST, (y & 1) == 1));
                }
            }
            case SHELTER_WALL -> {
                for (int y = STAND_Y; y < STAND_Y + SHELTER - 2; y++) {
                    out.set(y, Level490Blocks.STONE.get().defaultBlockState());
                }
                out.set(STAND_Y + SHELTER - 2, Level490Blocks.SLATE.get().defaultBlockState());
            }
            case SHELTER_DOOR -> {
                out.set(STAND_Y + 2, Level490Blocks.STONE.get().defaultBlockState());
                out.set(STAND_Y + SHELTER - 2, Level490Blocks.SLATE.get().defaultBlockState());
            }
            case SHELTER_IN -> out.set(STAND_Y + SHELTER - 2, Level490Blocks.SLATE.get().defaultBlockState());
            case SHELTER_RADIO -> {
                out.set(STAND_Y, ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(c.facing)));
                out.set(STAND_Y + SHELTER - 2, Level490Blocks.SLATE.get().defaultBlockState());
            }
            case SHACKLE -> out.set(STAND_Y, Level490Blocks.SHACKLE.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.from2DDataValue(c.facing)));
            default -> { }
        }
    }

    /** A railing or post joined to the railings, posts and shelter walls beside it. */
    private BlockState connect(long seed, int x, int z, BlockState state) {
        return state.setValue(CrossCollisionBlock.NORTH, joins(seed, x, z - 1))
                .setValue(CrossCollisionBlock.SOUTH, joins(seed, x, z + 1))
                .setValue(CrossCollisionBlock.EAST, joins(seed, x + 1, z))
                .setValue(CrossCollisionBlock.WEST, joins(seed, x - 1, z));
    }

    private boolean joins(long seed, int x, int z) {
        int k = cell(seed, x, z).kind;
        return k == RAIL || k == POST_LIT || k == POST_DARK || k == SHELTER_WALL;
    }

    // ==========================================================================================
    // Standing, arriving, the way out
    // ==========================================================================================

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int k = cell(seed, x, z).kind;
        return k == DECK || k == SHACKLE || k == SHELTER_IN || k == SHELTER_DOOR;
    }

    @Override
    public int standY(long seed, int x, int z) {
        return STAND_Y;
    }

    /** You arrive on the open paving of the region's arrival platform. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        int i = Math.round(x / (float) SPACING);
        int j = Math.round(z / (float) SPACING);
        Site s = siteOfNode(seed, i, j);
        if (!isArrival(s, i, j)) {
            return false;
        }
        int k = cell(seed, x, z).kind;
        return k == DECK || k == SHACKLE;
    }

    /** Arrivals look for the arrival platform of the region they were sent to. */
    @Override
    public int[] arrivalHint(long seed, int x, int z) {
        Site s = site(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
        return new int[] {s.ai * SPACING, s.aj * SPACING};
    }

    /** The arrival platform's middle: where falling sends you if you never used a radio. */
    public int[] arrivalCentre(long seed, int x, int z) {
        return arrivalHint(seed, x, z);
    }

    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return siteCentre(seed, x, z);
    }

    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        int k = cell(seed, x, z).kind;
        return k >= SHELTER_WALL && k <= SHELTER_RADIO;
    }
}
