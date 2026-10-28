package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level227AttachedPlantBlock;
import com.rickr.backrooms.block.Level227HangingBlock;
import com.rickr.backrooms.block.Level227PaletteBlock;
import com.rickr.backrooms.block.Level227PlantBlock;
import com.rickr.backrooms.block.Level227RingsBlock;
import com.rickr.backrooms.block.Level227TallPlantBlock;
import com.rickr.backrooms.block.Level227FacingBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level227Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level227Tile.Cave;
import com.rickr.backrooms.world.layout.Level227Tile.Fly;
import com.rickr.backrooms.world.layout.Level227Tile.Log;
import com.rickr.backrooms.world.layout.Level227Tile.Tree;
import com.rickr.backrooms.world.layout.Level227Tree.Arrival;
import com.rickr.backrooms.world.layout.Level227Tree.Beam;
import com.rickr.backrooms.world.layout.Level227Tree.Span;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 227, "Megaflora": the terrain of the planet-sized upside-down tree ({@link Level227Tree})
 * and the rainforest growing on its branches.
 *
 * <p>Each column is built from the pieces of the tree crossing it ({@link Level227Tree#span}):
 * <ul>
 *   <li><b>The wood:</b> a bark shell (with long grooves on the flanks, knotholes and glowing resin
 *       seeps), pale heartwood behind it, and, deeper than {@link #HOLLOW_DEPTH} blocks inside,
 *       sealed air. Nobody can see into a branch, and the hollow cores make chunks far cheaper to
 *       generate, light and save. Broken tips end in a ragged face of growth rings.</li>
 *   <li><b>The forest floor</b> on top: grass and moss over 4-8 blocks of root soil, with rolling
 *       ground, exposed roots, puddles, thinning out at the edges to moss-streaked bark where the
 *       Lestron grows.</li>
 *   <li><b>The forest:</b> giant trees 25-70 tall with buttress roots and layered crowns, broken
 *       stumps showing their rings, fallen logs, lianas, epiphytes and dense undergrowth (from
 *       {@link Level227Tile}).</li>
 *   <li><b>Underneath:</b> curtains of moss and fern fronds hanging into the void, bracket fungi,
 *       GammaPali, and Palettes in the undersides that hang low over somewhere you can walk.</li>
 *   <li><b>Hollows:</b> bear caves under the forest floor and hollow knots at the edges; sunbeam
 *       clearings burnt to ash, with the canopy open above them.</li>
 * </ul>
 * Everything is a pure function of the column (and, like the tree, ignores the world seed).
 */
public final class Level227Layout extends LevelLayout {
    /** Wood deeper than this inside a branch (or the trunk) is left as sealed air. */
    public static final int HOLLOW_DEPTH = 12;
    /** Never used for travel (Level 227 has no exit); only so the base class has a locator. */
    private static final ExitLocator EXITS = new ExitLocator(4096, 64, 2271);
    private static final int HEIGHT = Level227Tree.WORLD_HEIGHT;
    private static final int MAX_SPANS = 16;
    private static final int MAX_CAVES = 6;
    private static final int MAX_RUNS = 12;

    /**
     * A spot for a spawner: block column (x, z), y = the feet height (first air block above the
     * floor), {@code yaw} which way it faces (degrees, Minecraft yaw), {@code size} a rough size
     * (a cave's half-width; 0 for plain spots).
     */
    public record Spot(int x, int y, int z, float yaw, int size) {}

    public Level227Layout() {
        super(8, EXITS);
    }

    @Override
    public int worldHeight() {
        return HEIGHT;
    }

    @Override
    public int highestCeilingY() {
        return HEIGHT - 1;
    }

    @Override
    public int lowestFixtureY() {
        return Level227Tree.VOID_DEATH_Y;
    }

    @Override
    public boolean hasExits() {
        return false;
    }

    /** No exit anywhere: a point far off, for any code that still asks (see {@link #hasExits()}). */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return new int[] {x + 1_000_000, z};
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        Span span = SCRATCH.get();
        return Level227Tile.ground(x + 0.5, z + 0.5, span) != Integer.MIN_VALUE && span.nd < 0.8;
    }

    @Override
    public int standY(long seed, int x, int z) {
        int ground = Level227Tree.topSurfaceY(x + 0.5, z + 0.5);
        return ground == Integer.MIN_VALUE ? 600 : ground;
    }

    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        Span span = SCRATCH.get();
        return Level227Tile.ground(x + 0.5, z + 0.5, span) != Integer.MIN_VALUE && span.nd < 0.5
                && Level227Tree.beamAt(x + 0.5, z + 0.5) == null;
    }

    /**
     * Without a player to keep apart from the others (see {@link #separatedArrival} for that):
     * the planned arrival point nearest to (x, z).
     */
    @Override
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        Arrival best = null;
        double bestD = Double.MAX_VALUE;
        for (Arrival a : Level227Tree.arrivals()) {
            double d = Math.hypot(a.x() - x, a.z() - z);
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best == null ? null : arrivalPos(best);
    }

    // ==========================================================================================
    // Arrival: everyone is separated
    // ==========================================================================================

    /** Every arriving wanderer lands at least this far from anyone else already in Level 227. */
    public static final int SEPARATION = 1500;

    /**
     * Half the width of a Venus flytrap's open dish (about 4.7 blocks across, centred on its spot's
     * block). Columns whose centre lies inside keep only low walk-through cover; the tall grass
     * starts right at the rim.
     */
    public static final double FLYTRAP_DISH_RADIUS = 2.35;

    /**
     * Where {@code player} arrives in Level 227 ({@code level}): always on a branch top in the
     * middle of the forest, and never near anyone else (canon: wanderers who enter are always
     * separated). In order of preference, a planned arrival point
     * <ol>
     *   <li>on a main branch nobody else is on, at least {@link #SEPARATION} blocks from everyone;</li>
     *   <li>anywhere at least {@link #SEPARATION} blocks from everyone;</li>
     *   <li>(only with very many players) the point farthest from everyone.</li>
     * </ol>
     * Among equally good points it picks at random, so arrivals vary.
     */
    public static BlockPos separatedArrival(ServerLevel level, ServerPlayer player) {
        List<ServerPlayer> others = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        Span span = new Span();
        for (ServerPlayer p : level.players()) {
            if (p == player || p.isSpectator()) {
                continue;
            }
            others.add(p);
            if (Level227Tile.ground(p.getX(), p.getZ(), span) != Integer.MIN_VALUE) {
                taken.add(Level227Tree.familyOf(Level227Tree.segments().get(span.id)));
            }
        }
        List<Arrival> freeBranch = new ArrayList<>();
        List<Arrival> farEnough = new ArrayList<>();
        Arrival farthest = null;
        double farthestD = -1;
        for (Arrival a : Level227Tree.arrivals()) {
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer p : others) {
                nearest = Math.min(nearest, Math.hypot(p.getX() - a.x(), p.getZ() - a.z()));
            }
            if (nearest >= SEPARATION) {
                (taken.contains(a.branch()) ? farEnough : freeBranch).add(a);
            }
            if (nearest > farthestD) {
                farthestD = nearest;
                farthest = a;
            }
        }
        List<Arrival> pool = !freeBranch.isEmpty() ? freeBranch : farEnough;
        Arrival pick = pool.isEmpty() ? farthest : pool.get(player.getRandom().nextInt(pool.size()));
        return arrivalPos(pick);
    }

    private static BlockPos arrivalPos(Arrival a) {
        return new BlockPos(a.x(), Level227Tree.topSurfaceY(a.x() + 0.5, a.z() + 0.5), a.z());
    }

    // ==========================================================================================
    // Spots for the spawners (all seedless)
    // ==========================================================================================

    /** Flytrap hiding places within {@code radius} of (x, z): open forest, ringed by tall grass. */
    public static void flytrapSpots(double x, double z, double radius, Consumer<Spot> out) {
        int c = Level227Tile.FLY_CELL;
        for (int cx = Math.floorDiv((int) (x - radius), c); cx <= Math.floorDiv((int) (x + radius), c); cx++) {
            for (int cz = Math.floorDiv((int) (z - radius), c); cz <= Math.floorDiv((int) (z + radius), c); cz++) {
                Fly fly = Level227Tile.flytrapAt(cx, cz);
                if (fly != null && Math.hypot(fly.x() + 0.5 - x, fly.z() + 0.5 - z) <= radius) {
                    out.accept(new Spot(fly.x(), fly.y(), fly.z(), (float) (Hash.unit(Hash.of(227, fly.x(), fly.z(), 7)) * 360 - 180), 0));
                }
            }
        }
    }

    /**
     * Bear caves within {@code radius} of (x, z): (x, y, z) is the middle of the cave floor (feet
     * height), {@code yaw} faces out along the tunnel, {@code size} is the cave's half-width (8-11).
     */
    public static void caveSpots(double x, double z, double radius, Consumer<Spot> out) {
        int c = Level227Tile.CAVE_CELL;
        for (int cx = Math.floorDiv((int) (x - radius), c); cx <= Math.floorDiv((int) (x + radius), c); cx++) {
            for (int cz = Math.floorDiv((int) (z - radius), c); cz <= Math.floorDiv((int) (z + radius), c); cz++) {
                Cave cave = Level227Tile.caveAt(Level227Tile.CAVE, cx, cz);
                if (cave != null && Math.hypot(cave.x() - x, cave.z() - z) <= radius) {
                    out.accept(new Spot((int) Math.floor(cave.x()), caveFloor(cave), (int) Math.floor(cave.z()), cave.yaw(), (int) cave.rx()));
                }
            }
        }
    }

    /** Spots on open forest floor (no trunk, log, puddle, beam or cave mouth there), about one per 20x20. */
    public static void forestSpots(double x, double z, double radius, Consumer<Spot> out) {
        forestSpots(x, z, radius, Integer.MIN_VALUE, out);
    }

    /** Open forest spots in the higher sections ({@link Level227Tree#isLevelHigh}), where the wolves hunt. */
    public static void highSpots(double x, double z, double radius, Consumer<Spot> out) {
        forestSpots(x, z, radius, (int) Level227Tree.HIGH_Y, out);
    }

    private static final int FOREST_CELL = 20;

    private static void forestSpots(double x, double z, double radius, int minY, Consumer<Spot> out) {
        Span span = new Span();
        for (int cx = Math.floorDiv((int) (x - radius), FOREST_CELL); cx <= Math.floorDiv((int) (x + radius), FOREST_CELL); cx++) {
            for (int cz = Math.floorDiv((int) (z - radius), FOREST_CELL); cz <= Math.floorDiv((int) (z + radius), FOREST_CELL); cz++) {
                long h = Hash.of(227, cx, cz, 11);
                int sx = cx * FOREST_CELL + 2 + Hash.range(h, FOREST_CELL - 4);
                int sz = cz * FOREST_CELL + 2 + Hash.range(Hash.mix(h + 1), FOREST_CELL - 4);
                if (Math.hypot(sx + 0.5 - x, sz + 0.5 - z) > radius) {
                    continue;
                }
                int ground = Level227Tile.ground(sx + 0.5, sz + 0.5, span);
                if (ground == Integer.MIN_VALUE || ground < minY || span.nd > 0.5 || Level227Tree.isPuddle(sx + 0.5, sz + 0.5)
                        || Level227Tree.beamAt(sx + 0.5, sz + 0.5) != null || blockedByForest(sx, sz, ground)) {
                    continue;
                }
                out.accept(new Spot(sx, ground, sz, (float) (Hash.unit(Hash.mix(h + 2)) * 360 - 180), 0));
            }
        }
    }

    /** True when a tree trunk, buttress, log, cave or ash clearing takes column (x, z). */
    private static boolean blockedByForest(int x, int z, int ground) {
        Level227Tile tile = Level227Tile.of(x, z);
        for (Tree t : tile.trees) {
            // The flared foot of the trunk; between the thin buttress fins there is room to stand.
            if (Math.hypot(t.x() - x, t.z() - z) < t.radius() * 1.9 + 2) {
                return true;
            }
        }
        for (Log l : tile.logs) {
            if (distToSegment(x + 0.5, z + 0.5, l.x0(), l.z0(), l.x1(), l.z1()) < l.r() + 1.5) {
                return true;
            }
        }
        for (Cave c : tile.caves) {
            if (Math.hypot(c.x() - x, c.z() - z) < c.rx() + 4 || Math.hypot(c.ex() - x, c.ez() - z) < 6) {
                return true;
            }
        }
        for (Beam b : tile.beams) {
            if (Math.hypot(b.x() - x, b.z() - z) < b.radius() + 8) {
                return true;
            }
        }
        return false;
    }

    private static int caveFloor(Cave c) {
        return (int) Math.ceil(c.y() - c.ry() * 0.65);
    }

    // ==========================================================================================
    // The blocks (looked up once, after registration)
    // ==========================================================================================

    /** Block states by kind, built the first time a column is generated (blocks exist by then). */
    private static final class S {
        static final BlockState[] BARK = axes(Level227Blocks.BARK.get());
        static final BlockState[] BARK_MOSSY = axes(Level227Blocks.BARK_MOSSY.get());
        static final BlockState HEARTWOOD = Level227Blocks.HEARTWOOD.get().defaultBlockState();
        static final BlockState[][] RINGS = new BlockState[3][4];
        static final BlockState[] KNOT = new BlockState[6];
        static final BlockState RESIN = Level227Blocks.RESIN.get().defaultBlockState();
        static final BlockState[] PALETTE = axes(Level227Blocks.PALETTE.get());
        static final BlockState ROOT_SOIL = Level227Blocks.ROOT_SOIL.get().defaultBlockState();
        static final BlockState MOSS = Level227Blocks.MOSS.get().defaultBlockState();
        static final BlockState MOSS_CARPET = Level227Blocks.MOSS_CARPET.get().defaultBlockState();
        static final BlockState GRASS = Level227Blocks.FOREST_GRASS.get().defaultBlockState();
        static final BlockState ASH = Level227Blocks.ASH.get().defaultBlockState();
        static final BlockState WATER = Blocks.WATER.defaultBlockState();
        static final BlockState[] LOG = axes(Level227Blocks.TREE_LOG.get());
        static final BlockState[] LOG_MOSSY = axes(Level227Blocks.TREE_LOG_MOSSY.get());
        static final BlockState LEAVES = Level227Blocks.LEAVES.get().defaultBlockState();
        static final BlockState FERN_LEAVES = Level227Blocks.FERN_LEAVES.get().defaultBlockState();
        static final BlockState[] FRONDS = hanging(Level227Blocks.HANGING_FRONDS.get());
        static final BlockState[] HANGING_MOSS = hanging(Level227Blocks.HANGING_MOSS.get());
        static final BlockState[] LIANA = hanging(Level227Blocks.LIANA.get());
        static final BlockState TALL_GRASS_LOW = Level227Blocks.TALL_GRASS.get().defaultBlockState().setValue(Level227TallPlantBlock.HALF, DoubleBlockHalf.LOWER);
        static final BlockState TALL_GRASS_HIGH = Level227Blocks.TALL_GRASS.get().defaultBlockState().setValue(Level227TallPlantBlock.HALF, DoubleBlockHalf.UPPER);
        static final BlockState FERN_LOW = Level227Blocks.GIANT_FERN.get().defaultBlockState().setValue(Level227TallPlantBlock.HALF, DoubleBlockHalf.LOWER);
        static final BlockState FERN_HIGH = Level227Blocks.GIANT_FERN.get().defaultBlockState().setValue(Level227TallPlantBlock.HALF, DoubleBlockHalf.UPPER);
        static final BlockState[] UNDERGROWTH = kinds(Level227Blocks.UNDERGROWTH.get());
        static final BlockState[] FLOWER = kinds(Level227Blocks.FLOWER.get());
        static final BlockState[] FUNGUS = new BlockState[6];
        static final BlockState FALLEN_LEAF = Level227Blocks.FALLEN_LEAF.get().defaultBlockState();
        static final BlockState[] EPIPHYTE = new BlockState[6];
        static final BlockState[] GAMMAPALI = new BlockState[6];
        static final BlockState LESTRON = Level227Blocks.LESTRON.get().defaultBlockState();

        static {
            Direction.Axis[] axes = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
            for (int a = 0; a < 3; a++) {
                for (int r = 0; r < 4; r++) {
                    RINGS[a][r] = Level227Blocks.GROWTH_RINGS.get().defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, axes[a]).setValue(Level227RingsBlock.RINGS, r);
                }
            }
            for (Direction d : Direction.values()) {
                KNOT[d.ordinal()] = Level227Blocks.KNOT.get().defaultBlockState().setValue(Level227FacingBlock.FACING, d);
                EPIPHYTE[d.ordinal()] = Level227Blocks.EPIPHYTE.get().defaultBlockState().setValue(Level227AttachedPlantBlock.FACING, d);
                GAMMAPALI[d.ordinal()] = Level227Blocks.GAMMAPALI.get().defaultBlockState().setValue(Level227AttachedPlantBlock.FACING, d);
                if (d.getAxis().isHorizontal()) {
                    FUNGUS[d.ordinal()] = Level227Blocks.BRACKET_FUNGUS.get().defaultBlockState().setValue(WallMountBlock.FACING, d);
                }
            }
            for (int a = 0; a < 3; a++) {
                if (PALETTE[a].hasProperty(Level227PaletteBlock.FALLEN)) {
                    PALETTE[a] = PALETTE[a].setValue(Level227PaletteBlock.FALLEN, false);
                }
            }
        }

        /** One state per axis, indexed 0 = X, 1 = Y, 2 = Z. */
        private static BlockState[] axes(Block block) {
            BlockState s = block.defaultBlockState();
            return new BlockState[] {s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                    s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z)};
        }

        /** [0] a strand block, [1] the strand's tip. */
        private static BlockState[] hanging(Block block) {
            BlockState s = block.defaultBlockState();
            return new BlockState[] {s.setValue(Level227HangingBlock.TIP, false), s.setValue(Level227HangingBlock.TIP, true)};
        }

        private static BlockState[] kinds(Block block) {
            BlockState s = block.defaultBlockState();
            return new BlockState[] {s.setValue(Level227PlantBlock.KIND, 0), s.setValue(Level227PlantBlock.KIND, 1),
                    s.setValue(Level227PlantBlock.KIND, 2), s.setValue(Level227PlantBlock.KIND, 3)};
        }
    }

    // ==========================================================================================
    // Column generation
    // ==========================================================================================

    /** Per-thread scratch, so generating a column allocates nothing. */
    private static final class Work {
        final BlockState[] col = new BlockState[HEIGHT];
        final Span[] spans = new Span[MAX_SPANS];
        final int[] lo = new int[MAX_SPANS];
        final int[] hi = new int[MAX_SPANS];
        final double[] groovePhase = new double[MAX_SPANS];
        final int[] axis = new int[MAX_SPANS];
        final Direction[] outward = new Direction[MAX_SPANS];
        // Caves crossing this column: carved range, lining range, mossy mouth, Palette in the ceiling.
        final int[] caveLo = new int[MAX_CAVES], caveHi = new int[MAX_CAVES];
        final int[] lineLo = new int[MAX_CAVES], lineHi = new int[MAX_CAVES];
        final boolean[] mouth = new boolean[MAX_CAVES];
        final boolean[] paletteCeiling = new boolean[MAX_CAVES];
        // The tops and undersides of the solid runs, top to bottom, with the piece that made them.
        final int[] runTop = new int[MAX_RUNS], runTopSpan = new int[MAX_RUNS];
        final double[] runTopNd = new double[MAX_RUNS];
        final int[] runBot = new int[MAX_RUNS], runBotSpan = new int[MAX_RUNS];
        int runs;
        int n;
        int caves;
        int minY, maxY;

        Work() {
            for (int i = 0; i < MAX_SPANS; i++) {
                spans[i] = new Span();
            }
        }

        void set(int y, BlockState state) {
            if (y < 0 || y >= HEIGHT) {
                return;
            }
            col[y] = state;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        boolean free(int y) {
            return y >= 0 && y < HEIGHT && col[y] == null;
        }
    }

    private static final ThreadLocal<Work> WORK = ThreadLocal.withInitial(Work::new);
    private static final ThreadLocal<Span> SCRATCH = ThreadLocal.withInitial(Span::new);

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        Work w = WORK.get();
        w.minY = HEIGHT;
        w.maxY = -1;
        double px = x + 0.5, pz = z + 0.5;
        Level227Tile tile = Level227Tile.of(x, z);

        double trunkD = Math.sqrt(px * px + pz * pz);
        boolean trunkSide = trunkD < Level227Tree.TRUNK_RADIUS + 25;
        // Pieces of branches deep inside the trunk would only be more sealed hollow: skip them.
        int n = 0;
        if (trunkD > Level227Tree.TRUNK_RADIUS - HOLLOW_DEPTH - 30) {
            for (int id : Level227Tree.cellAt(px, pz)) {
                Span s = w.spans[n];
                if (Level227Tree.span(id, px, pz, s)) {
                    w.lo[n] = (int) Math.ceil(s.bot);
                    w.hi[n] = (int) Math.floor(s.top);
                    if (w.hi[n] < w.lo[n]) {
                        continue;
                    }
                    w.groovePhase[n] = 4 * Level227Tree.noise(s.along * 0.012, id * 3.1, 93);
                    w.axis[n] = Math.abs(Level227Tree.ux[id]) >= Math.abs(Level227Tree.uz[id]) ? 0 : 2;
                    double sign = s.side >= 0 ? 1 : -1;
                    w.outward[n] = Direction.getNearest(-Level227Tree.uz[id] * sign, 0, Level227Tree.ux[id] * sign);
                    if (++n == MAX_SPANS) {
                        break;
                    }
                }
            }
        }
        w.n = n;
        prepareCaves(w, tile, px, pz);

        buildWood(w, x, z, px, pz, trunkSide, trunkD);
        if (w.maxY >= 0) {
            dressSurfaces(w, tile, x, z, px, pz);
            forest(w, tile, x, z, px, pz);
            undergrowth(w, tile, x, z, px, pz);
            hangings(w, x, z, px, pz);
            caveFloors(w, x, z);
        }

        for (int y = Math.max(0, w.minY); y <= w.maxY; y++) {
            BlockState s = w.col[y];
            if (s != null) {
                out.set(y, s);
                w.col[y] = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The wood: trunk and branches
    // ------------------------------------------------------------------------------------------

    /**
     * Fills the solid wood of the column, top to bottom, and records the tops and undersides of
     * its solid runs for the later passes. Soil goes on top of branch tops here too.
     */
    private static void buildWood(Work w, int x, int z, double px, double pz, boolean trunkSide, double trunkD) {
        int n = w.n;
        int top = -1, bottom = HEIGHT;
        for (int k = 0; k < n; k++) {
            top = Math.max(top, w.hi[k]);
            bottom = Math.min(bottom, w.lo[k]);
        }
        double angle = 0;
        boolean trunkWall = false, trunkInside = false;
        if (trunkSide) {
            angle = Math.atan2(pz, px);
            // Further in than the deepest flute plus the shell, the column is the trunk's sealed
            // core at every height: only its cap at the top of the world is solid.
            trunkInside = trunkD < Level227Tree.TRUNK_RADIUS - 10 - HOLLOW_DEPTH - 1;
            trunkWall = !trunkInside;
            top = HEIGHT - 1;
            bottom = trunkInside && n == 0 ? HEIGHT - HOLLOW_DEPTH - 1 : 0;
        }
        if (top < 0) {
            w.runs = 0;
            return;
        }
        top = Math.min(top, HEIGHT - 1);
        bottom = Math.max(bottom, 0);

        // Soil depth at this column (4-8 on the forest floor), and whether this is a root ridge.
        double soilNoise = 0.5 + 0.5 * Level227Tree.noise(px * 0.05, pz * 0.05, 61);
        double grassNoise = Level227Tree.noise(px * 0.06, pz * 0.06, 62);
        double ridge = 1 - Math.abs(Level227Tree.noise(px * 0.043, pz * 0.043, 33)) * 9;
        boolean puddle = Level227Tree.isPuddle(px, pz);

        boolean inRun = false;
        int runs = 0;
        int runTopY = 0, topSpan = -1;
        double topNd = 1;
        int lastSolid = -1;
        boolean recording = false;
        for (int y = top; y >= bottom; y--) {
            boolean solid = false;
            double depth = Double.NEGATIVE_INFINITY;
            int deepest = -1;
            double cut = Double.POSITIVE_INFINITY;
            int cutSpan = -1;
            int bestTop = Integer.MIN_VALUE, bestTopSpan = -1;
            // Where pieces merge (a fork, a fusion), the forest floor of the flatter one carries on
            // over the seam instead of turning into an "edge" in the middle of the forest.
            double nearTopNd = 1;
            for (int k = 0; k < n; k++) {
                if (y < w.lo[k] || y > w.hi[k]) {
                    continue;
                }
                Span s = w.spans[k];
                double dv = Math.min(y - w.lo[k], w.hi[k] - y);
                double ny = (y + 0.5 - s.yc) / s.b;
                double ny2 = ny * ny;
                // Sideways distance to the bark at this height (the top half is the flatter
                // superellipse of Level227Tree.span, the bottom half a plain ellipse).
                double reach = ny2 >= 1 ? 0 : ny > 0 ? Math.cbrt(1 - ny2) : Math.sqrt(1 - ny2);
                double dh = Math.max(0, s.a * (reach - s.nd));
                double d = Math.min(dv, dh);
                // Long grooves along the flanks of the branch (horizontal lines on its sides).
                if (s.nd > 0.72 && dv > 1.5 && d < 2.6) {
                    double g = Math.sin(y * 0.8 + w.groovePhase[k]);
                    if (g > 0.35 && dh < 2.2 * (g - 0.35) / 0.65) {
                        continue;
                    }
                }
                d = Math.min(d, s.cut);
                solid = true;
                if (d > depth) {
                    depth = d;
                    deepest = k;
                }
                if (s.cut < cut) {
                    cut = s.cut;
                    cutSpan = k;
                }
                if (w.hi[k] > bestTop || w.hi[k] == bestTop && s.nd < w.spans[bestTopSpan].nd) {
                    bestTop = w.hi[k];
                    bestTopSpan = k;
                }
                if (w.hi[k] >= y - 4 && s.nd < nearTopNd) {
                    nearTopNd = s.nd;
                }
            }
            boolean trunkHere = false;
            double trunkDepth = 0;
            if (trunkInside) {
                trunkHere = true;
                trunkDepth = 100;
            } else if (trunkWall) {
                double r = Level227Tree.trunkRadius(angle, y);
                if (trunkD < r) {
                    trunkHere = true;
                    trunkDepth = r - trunkD;
                }
            }
            if (trunkHere) {
                // The trunk's top cap (the world's ceiling) is solid, so it is sealed from above too.
                trunkDepth = Math.min(trunkDepth, Math.max(0, HEIGHT - 1 - y) + (y >= HEIGHT - HOLLOW_DEPTH ? 0 : HOLLOW_DEPTH + 1));
                solid = true;
                if (trunkDepth > depth) {
                    depth = trunkDepth;
                    deepest = -1;
                }
            }

            // Caves carve the wood; their lining keeps a cave from opening into the sealed hollow.
            int cave = caveAt(w, y);
            if (cave >= 0 && cave < 100) {
                solid = false;
            }
            if (!solid) {
                if (inRun && recording) {
                    w.runBot[runs - 1] = lastSolid;
                }
                inRun = false;
                continue;
            }
            if (!inRun) {
                inRun = true;
                recording = runs < MAX_RUNS;
                topNd = bestTopSpan >= 0 && bestTop == y ? nearTopNd : 1;
                if (recording) {
                    w.runTop[runs] = y;
                    w.runTopSpan[runs] = bestTopSpan;
                    w.runTopNd[runs] = topNd;
                    w.runBot[runs] = y;
                    runs++;
                }
                runTopY = y;
                topSpan = bestTopSpan;
            }
            lastSolid = y;
            if (recording) {
                w.runBotSpan[runs - 1] = deepest; // the piece that makes the underside, once the run ends
            }
            boolean lined = cave >= 100;
            if (depth > HOLLOW_DEPTH && !lined) {
                continue; // sealed hollow core
            }
            int below = runTopY - y;
            BlockState state;
            if (cutSpan >= 0 && cut < 1.0) {
                state = rings(w, cutSpan, x, y, z);
            } else if (topNd < Level227Tree.WALKABLE_ND && below < 12) {
                state = groundBlock(w, topSpan, topNd, below, depth, deepest, x, y, z, px, pz, soilNoise, grassNoise, ridge, puddle);
            } else {
                state = wood(w, deepest, depth, y, x, z, px, pz, trunkHere && deepest < 0, below);
            }
            if (cave >= 200 && depth < 6) {
                state = S.MOSS; // the mossy mouth of a bear cave or hollow knot
            }
            w.set(y, state);
        }
        if (inRun && recording) {
            w.runBot[runs - 1] = lastSolid;
        }
        w.runs = runs;
    }

    /** The forest floor (or the bark under it) at {@code below} blocks under the top of a walkable branch top. */
    private static BlockState groundBlock(Work w, int topSpan, double nd, int below, double depth, int deepest,
                                          int x, int y, int z, double px, double pz,
                                          double soilNoise, double grassNoise, double ridge, boolean puddle) {
        // How much forest floor there is: full in the middle, thinning to nothing at the edge.
        double forest = nd < 0.55 ? 1 : nd > 0.85 ? 0 : 1 - (nd - 0.55) / 0.3;
        int soil = (int) Math.round((4 + 4 * soilNoise) * forest);
        if (below == 0) {
            if (puddle && nd < 0.45) {
                return S.WATER;
            }
            if (forest > 0.5 && ridge > 0.55) {
                // An exposed root of the forest, snaking over the ground.
                return S.LOG_MOSSY[rootAxis(px, pz)];
            }
            if (soil >= 3) {
                return grassNoise > -0.15 ? S.GRASS : S.MOSS;
            }
            if (soil >= 1) {
                return S.MOSS;
            }
            return S.BARK_MOSSY[topSpan >= 0 ? w.axis[topSpan] : 1];
        }
        if (below < soil) {
            return S.ROOT_SOIL;
        }
        if (below < soil + 2) {
            return S.BARK[topSpan >= 0 ? w.axis[topSpan] : 1];
        }
        return depth < 3 ? S.BARK[deepest >= 0 ? w.axis[deepest] : 1] : S.HEARTWOOD;
    }

    /** Which way an exposed root runs: across the slope of the ridge noise (X or Z). */
    private static int rootAxis(double px, double pz) {
        double n0 = Level227Tree.noise(px * 0.043, pz * 0.043, 33);
        double gx = Level227Tree.noise((px + 1) * 0.043, pz * 0.043, 33) - n0;
        double gz = Level227Tree.noise(px * 0.043, (pz + 1) * 0.043, 33) - n0;
        // The ridge runs where the noise crosses zero, i.e. across its gradient.
        return Math.abs(gx) > Math.abs(gz) ? 2 : 0;
    }

    /** Bark (with knots, resin and moss streaks) near the surface; heartwood deeper in. */
    private static BlockState wood(Work w, int k, double depth, int y, int x, int z, double px, double pz, boolean trunk, int below) {
        if (depth >= 3) {
            return S.HEARTWOOD;
        }
        if (trunk) {
            double knot = Level227Tree.noise(px * 0.07 + y * 0.01, pz * 0.07 + y * 0.09, 94);
            if (depth < 1 && knot > 0.82) {
                return S.KNOT[Direction.getNearest(px, 0, pz).ordinal()];
            }
            if (depth < 1.5 && Level227Tree.noise(px * 0.4, y * 0.05 + pz * 0.4, 95) > 0.8) {
                return S.RESIN;
            }
            return S.BARK[1];
        }
        Span s = w.spans[k];
        int axis = w.axis[k];
        boolean upper = y + 0.5 > s.yc;
        if (depth < 1) {
            // Knotholes on the flanks and undersides.
            double knot = Level227Tree.noise(s.along * 0.06, y * 0.11 + s.side * 0.02, 96);
            if (knot > 0.8) {
                Direction face = s.nd > 0.75 ? w.outward[k] : upper ? Direction.UP : Direction.DOWN;
                return S.KNOT[face.ordinal()];
            }
        }
        if (depth < 1.5 && !upper || s.nd > 0.8) {
            // Seeps of glowing amber resin, in streaks running down the bark.
            if (Level227Tree.noise(s.along * 0.35, y * 0.06 + s.side * 0.01, 97) > 0.78) {
                return S.RESIN;
            }
        }
        // Moss creeps down the upper flanks in streaks.
        if (upper && below < 6 && Level227Tree.noise(s.along * 0.08, y * 0.15, 98) > -0.2) {
            return S.BARK_MOSSY[axis];
        }
        return S.BARK[axis];
    }

    /**
     * A block of a broken tip's cut face: growth rings across the branch, the bands turned to run
     * round the branch's heart (see {@link Level227RingsBlock}).
     */
    private static BlockState rings(Work w, int k, int x, int y, int z) {
        Span s = w.spans[k];
        int id = s.id;
        int axis = w.axis[k];
        // From this block to the heart: sideways (in world x/z) and up/down.
        double side = s.side;
        double hx = side * Level227Tree.uz[id];
        double hz = -side * Level227Tree.ux[id];
        double dh = axis == 0 ? hz : hx;
        double dv = s.yc - (y + 0.5);
        int r;
        if (Math.abs(dv) > 2 * Math.abs(dh)) {
            r = 0;
        } else if (Math.abs(dh) > 2 * Math.abs(dv)) {
            r = 1;
        } else if (axis == 0) {
            r = (dh > 0) == (dv > 0) ? 2 : 3;
        } else {
            r = (dh > 0) != (dv > 0) ? 2 : 3;
        }
        return S.RINGS[axis][r];
    }

    // ------------------------------------------------------------------------------------------
    // Caves and hollow knots
    // ------------------------------------------------------------------------------------------

    private static void prepareCaves(Work w, Level227Tile tile, double px, double pz) {
        int m = 0;
        for (Cave c : tile.caves) {
            if (m == MAX_CAVES) {
                break;
            }
            double reach = c.rx() + 4;
            double ddx = px - c.x(), ddz = pz - c.z();
            boolean nearBody = Math.abs(ddx) < reach && Math.abs(ddz) < reach;
            boolean nearTunnel = c.kind() == Level227Tile.CAVE && distToSegment(px, pz, c.x(), c.z(), c.ex(), c.ez()) < c.tunnelR() + 3;
            if (!nearBody && !nearTunnel) {
                continue;
            }
            int carveLo = Integer.MAX_VALUE, carveHi = Integer.MIN_VALUE, lineLo = Integer.MAX_VALUE, lineHi = Integer.MIN_VALUE;
            boolean mouth = false, palette = false;
            // The body: an ellipsoid with a flat floor.
            double q = (ddx * ddx) / (c.rx() * c.rx()) + (ddz * ddz) / (c.rz() * c.rz());
            double floor = c.y() - c.ry() * 0.65;
            if (q < 1) {
                double f = Math.sqrt(1 - q);
                carveLo = (int) Math.ceil(Math.max(c.y() - c.ry() * f, floor));
                carveHi = (int) Math.floor(c.y() + c.ry() * f);
                palette = c.kind() == Level227Tile.KNOT && q < 0.5;
            }
            double ql = (ddx * ddx) / ((c.rx() + 3) * (c.rx() + 3)) + (ddz * ddz) / ((c.rz() + 3) * (c.rz() + 3));
            if (ql < 1) {
                double f = Math.sqrt(1 - ql);
                lineLo = (int) Math.floor(Math.min(c.y() - (c.ry() + 3) * f, floor - 3));
                lineHi = (int) Math.ceil(c.y() + (c.ry() + 3) * f);
                mouth = c.kind() == Level227Tile.KNOT;
            }
            if (c.kind() == Level227Tile.CAVE) {
                // The tunnel: from the cave floor, up along the branch to the forest floor (and a
                // little past it, so it opens cleanly).
                double tx = c.ex() - c.x(), tz = c.ez() - c.z();
                double len2 = tx * tx + tz * tz;
                double t = ((px - c.x()) * tx + (pz - c.z()) * tz) / len2;
                if (t > 0 && t < 1.15) {
                    double ex = px - (c.x() + tx * t), ez = pz - (c.z() + tz * t);
                    double e = Math.sqrt(ex * ex + ez * ez);
                    double y0 = floor + 1.5, y1 = c.ey();
                    double yc = y0 + (y1 - y0) * Math.min(1, t);
                    if (e < c.tunnelR()) {
                        double f = Math.sqrt(1 - (e / c.tunnelR()) * (e / c.tunnelR()));
                        int lo = (int) Math.ceil(yc - 1.5);
                        int hi = (int) Math.floor(yc + 1.2 + 1.6 * f);
                        carveLo = Math.min(carveLo, lo);
                        carveHi = Math.max(carveHi, hi);
                        // A Palette clings to the ceiling halfway up the tunnel.
                        palette |= t > 0.55 && t < 0.72 && e < 1.2;
                    }
                    if (e < c.tunnelR() + 2.5) {
                        lineLo = Math.min(lineLo, (int) Math.floor(yc - 4.5));
                        lineHi = Math.max(lineHi, (int) Math.ceil(yc + 5.5));
                        mouth |= t > 0.45;
                    }
                }
            }
            if (carveLo > carveHi && lineLo > lineHi) {
                continue;
            }
            w.caveLo[m] = carveLo;
            w.caveHi[m] = carveHi;
            w.lineLo[m] = lineLo;
            w.lineHi[m] = lineHi;
            w.mouth[m] = mouth;
            w.paletteCeiling[m] = palette;
            m++;
        }
        w.caves = m;
    }

    /** -1: no cave here; 0..: carved air (cave index); 100: lining; 200: lining at a mossy mouth. */
    private static int caveAt(Work w, int y) {
        int result = -1;
        for (int i = 0; i < w.caves; i++) {
            if (y >= w.caveLo[i] && y <= w.caveHi[i]) {
                return i;
            }
            if (y >= w.lineLo[i] && y <= w.lineHi[i]) {
                result = Math.max(result, w.mouth[i] ? 200 : 100);
            }
        }
        return result;
    }

    /** Moss carpets on cave floors and the Palette waiting in a tunnel's ceiling. */
    private static void caveFloors(Work w, int x, int z) {
        for (int i = 0; i < w.caves; i++) {
            if (w.caveLo[i] > w.caveHi[i]) {
                continue;
            }
            int floor = w.caveLo[i];
            if (floor > 0 && w.free(floor) && !w.free(floor - 1) && isGround(w.col[floor - 1])
                    && Hash.unit(Hash.of(227, x, z, floor)) < 0.6) {
                w.set(floor, S.MOSS_CARPET);
            }
            int ceiling = w.caveHi[i] + 1;
            if (w.paletteCeiling[i] && ceiling < HEIGHT && !w.free(ceiling) && isWood(w.col[ceiling])) {
                w.set(ceiling, S.PALETTE[0]);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Surfaces: ash, wall decorations
    // ------------------------------------------------------------------------------------------

    /** Sunbeam clearings: the ground under a beam is burnt to ash, fading out in patches. */
    private static void dressSurfaces(Work w, Level227Tile tile, int x, int z, double px, double pz) {
        for (Beam b : tile.beams) {
            double d = Math.hypot(px - b.x(), pz - b.z());
            double edge = b.radius() + 2;
            if (d > edge + 3) {
                continue;
            }
            boolean ash = d < edge || Hash.unit(Hash.of(227, x, z, 13)) < 1 - (d - edge) / 3;
            if (!ash) {
                continue;
            }
            for (int r = 0; r < w.runs; r++) {
                int top = w.runTop[r];
                if (w.runTopSpan[r] >= 0 && w.runTopNd[r] < Level227Tree.WALKABLE_ND
                        && (isGround(w.col[top]) || w.col[top] == S.WATER)) {
                    w.col[top] = S.ASH;
                    if (top > 0 && w.col[top - 1] == S.ROOT_SOIL && d < b.radius()) {
                        w.col[top - 1] = S.ASH;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The forest: giant trees and fallen logs
    // ------------------------------------------------------------------------------------------

    private static void forest(Work w, Level227Tile tile, int x, int z, double px, double pz) {
        Tree[] trees = tile.trees;
        // Wood first (so no tree's leaves cut into another tree's trunk), then the foliage.
        for (Tree t : trees) {
            int dx = x - t.x(), dz = z - t.z();
            if (Math.abs(dx) <= t.reach() && Math.abs(dz) <= t.reach()) {
                treeWood(w, t, x, z, dx, dz);
            }
        }
        for (Log l : tile.logs) {
            log(w, l, x, z, px, pz);
        }
        for (Tree t : trees) {
            int dx = x - t.x(), dz = z - t.z();
            if (Math.abs(dx) <= t.reach() && Math.abs(dz) <= t.reach()) {
                treeFoliage(w, t, x, z, dx, dz);
            }
        }
    }

    /** Trunk radius of a forest tree {@code h} blocks above its foot (it flares out at the base). */
    private static double trunkR(Tree t, double h) {
        return t.radius() * (1 - 0.3 * Math.max(0, h) / Math.max(1, t.height())) + t.radius() * 0.9 * Math.exp(-Math.max(0, h) / 2.5);
    }

    private static void treeWood(Work w, Tree t, int x, int z, int dx, int dz) {
        if (t.kind() == Level227Tile.SNAPPED) {
            stump(w, t, x, z, dx, dz);
            return;
        }
        double rho = Math.sqrt(dx * dx + dz * dz);
        double theta = Math.atan2(dz, dx);
        int g = t.ground();
        int topY = g + t.height() - 1;
        long h = t.hash();
        // The trunk: round, with a few soft lobes at the foot so it never stands on a square.
        double lobe = 0.16 * Math.sin(3 * theta + (h & 63)) + 0.08 * Math.sin(5 * theta + (h >> 8 & 63));
        if (rho <= trunkR(t, -1) * (1 + lobe) + 0.3) {
            double moss = Level227Tree.noise(x * 0.5, z * 0.5, (int) (h & 1023));
            for (int y = g - 2; y <= topY; y++) {
                double hgt = y - g;
                double r = trunkR(t, hgt) * (1 + lobe * Math.exp(-Math.max(0, hgt) / 4)) + 0.3;
                if (rho > r) {
                    break; // it only gets thinner further up
                }
                boolean mossy = hgt < t.height() * 0.4 * (0.6 + 0.4 * moss) || moss > 0.55;
                w.set(y, mossy ? S.LOG_MOSSY[1] : S.LOG[1]);
            }
            return;
        }
        roots(w, t, x, z, rho, theta);
        if (t.kind() == Level227Tile.SHRUB) {
            return;
        }
        // Limbs: horizontal boughs from the trunk out under each crown layer.
        double[] ly = t.layerY(), lr = t.layerR(), lt = t.layerT();
        for (int l = 0; l < ly.length; l++) {
            int limbs = l == 0 ? 5 : 3;
            for (int j = 0; j < limbs; j++) {
                double phi = Hash.unit(Hash.mix(h + 101 + 17 * l + j)) * Math.PI * 2;
                double delta = theta - phi;
                double along = rho * Math.cos(delta);
                double lateral = Math.abs(rho * Math.sin(delta));
                if (lateral > 0.62 || along < 0 || along > lr[l] * 0.72) {
                    continue;
                }
                int y = g + (int) Math.round(ly[l] - lt[l] * 0.35 - 1 + along * 0.22);
                if (w.free(y)) {
                    boolean alongX = Math.abs(Math.cos(phi)) >= Math.abs(Math.sin(phi));
                    w.set(y, alongX ? S.LOG[0] : S.LOG[2]);
                }
            }
        }
    }

    /**
     * Buttress roots: a few thick, rounded roots that snake out from the foot of the trunk over
     * the forest floor, tallest against the trunk and sinking into the ground as they go out. Each
     * one is highest along its middle and rounds off to its sides, and its line wanders, so none of
     * them reads as a straight wall.
     */
    private static void roots(Work w, Tree t, int x, int z, double rho, double theta) {
        if (t.fins() == 0 || rho > t.radius() + t.finLen() + 2) {
            return;
        }
        long h = t.hash();
        int g = t.ground();
        for (int f = 0; f < t.fins(); f++) {
            double phi = t.finPhase() + f * (Math.PI * 2 / t.fins()) + 0.5 * (Hash.unit(Hash.mix(h + 31 * f)) - 0.5);
            double delta = theta - phi;
            double along = rho * Math.cos(delta);
            if (along <= 0) {
                continue;
            }
            double e = (rho - t.radius()) / t.finLen();
            if (e < -0.2 || e > 1) {
                continue;
            }
            // The root's line wanders from side to side as it goes out.
            double wander = 1.3 * Math.sin(rho * 0.42 + f * 1.9 + (h & 31)) * Math.min(1, Math.max(0, e * 3));
            double lateral = rho * Math.sin(delta) - wander;
            double width = 0.6 + 1.1 * (1 - Math.max(0, e));
            if (Math.abs(lateral) > width) {
                continue;
            }
            double across = lateral / width;
            double crest = t.finH() * Math.pow(1 - Math.max(0, e), 2) * Math.sqrt(1 - across * across)
                    + 0.6 * Level227Tree.noise(x * 0.7, z * 0.7, 120 + f);
            int top = g - 1 + (int) Math.floor(crest);
            boolean steep = crest > 3 && e < 0.3;
            boolean alongX = Math.abs(Math.cos(phi)) >= Math.abs(Math.sin(phi));
            BlockState wood = steep ? S.LOG_MOSSY[1] : S.LOG_MOSSY[alongX ? 0 : 2];
            for (int y = g - 2; y <= top; y++) {
                if (w.free(y) || isGround(w.col[y])) {
                    w.set(y, wood);
                }
            }
            if (w.free(top + 1)) {
                double u = Hash.unit(Hash.of(h, x, z, 140 + f));
                if (u < 0.35) {
                    w.set(top + 1, S.MOSS_CARPET);
                } else if (u < 0.4) {
                    w.set(top + 1, S.EPIPHYTE[Direction.UP.ordinal()]);
                }
            }
            return;
        }
    }

    /**
     * A snapped forest tree: a stump with an irregular oval cross-section (squashed one way, with
     * lobes) that flares into the ground; its top torn into splinters of uneven height, highest on
     * the side where the trunk tore away, rotted lower in the middle and showing its growth rings
     * there; moss and ferns growing on top; a few short roots; and splinters lying round it.
     */
    private static void stump(Work w, Tree t, int x, int z, int dx, int dz) {
        double rho = Math.sqrt(dx * dx + dz * dz);
        double theta = Math.atan2(dz, dx);
        long h = t.hash();
        int g = t.ground();
        double r0 = t.radius();
        double squash = 0.15 + 0.15 * Hash.unit(Hash.mix(h + 40));
        double turn = Hash.unit(Hash.mix(h + 41)) * Math.PI;
        double oval = r0 * (1 + squash * Math.cos(2 * (theta - turn)) + 0.12 * Math.sin(3 * theta + (h & 63))
                + 0.07 * Math.sin(5 * theta + (h >> 6 & 63)));
        if (rho <= oval * 1.6 + 0.3) {
            double rim = Math.min(1, rho / Math.max(0.5, oval));
            double tearSide = 0.5 + 0.5 * Math.cos(theta - Hash.unit(Hash.mix(h + 42)) * Math.PI * 2);
            double jag = Hash.unit(Hash.of(h, x, z, 43));
            int top = g + t.height() - 1 + (int) Math.floor(rim * (tearSide * 3.5 + jag * jag * 3));
            if (rim < 0.5 && r0 > 2.4) {
                top -= 1 + (int) (Hash.unit(Hash.of(h, x, z, 44)) * 2); // rotted in the middle
            }
            int placed = Integer.MIN_VALUE;
            for (int y = g - 2; y <= top; y++) {
                double hgt = y - g;
                // Flared at the foot, then the oval itself.
                double r = oval * (1 + 0.6 * Math.exp(-Math.max(0, hgt) / 1.8)) + 0.3;
                if (rho > r) {
                    break;
                }
                boolean mossy = hgt < 2 || Level227Tree.noise(x * 0.6, y * 0.4 + z * 0.6, (int) (h & 255)) > 0.1;
                w.set(y, mossy ? S.LOG_MOSSY[1] : S.LOG[1]);
                placed = y;
            }
            if (placed == Integer.MIN_VALUE) {
                return;
            }
            // The broken face: rings where the top is fairly level (the middle), splinter tips on the rim.
            if (placed == top && rim < 0.8) {
                w.set(placed, ringsY(dx, dz));
            }
            double u = Hash.unit(Hash.of(h, x, z, 45));
            if (w.free(placed + 1)) {
                if (u < 0.35) {
                    w.set(placed + 1, S.MOSS_CARPET);
                } else if (u < 0.5) {
                    w.set(placed + 1, S.UNDERGROWTH[2]);
                } else if (u < 0.56 && w.free(placed + 2)) {
                    w.set(placed + 1, S.FERN_LOW);
                    w.set(placed + 2, S.FERN_HIGH);
                } else if (u < 0.6) {
                    w.set(placed + 1, S.EPIPHYTE[Direction.UP.ordinal()]);
                }
            }
            return;
        }
        roots(w, t, x, z, rho, theta);
        // Splinters of the fallen trunk lying about: short, thin, every which way.
        for (int k = 0; k < 3; k++) {
            if (Hash.unit(Hash.mix(h + 50 + k)) < 0.3) {
                continue;
            }
            double a = Hash.unit(Hash.mix(h + 60 + k)) * Math.PI * 2;
            double d0 = r0 * 1.5 + 1.5 + Hash.unit(Hash.mix(h + 70 + k)) * 3;
            double dir = a + (Hash.unit(Hash.mix(h + 80 + k)) - 0.5) * 2.2;
            double len = 2 + Hash.unit(Hash.mix(h + 90 + k)) * 2.5;
            double sx = Math.cos(a) * d0, sz = Math.sin(a) * d0;
            double ex = sx + Math.cos(dir) * len, ez = sz + Math.sin(dir) * len;
            if (distToSegment(dx, dz, sx, sz, ex, ez) > 0.55 || !w.free(g)) {
                continue;
            }
            boolean alongX = Math.abs(Math.cos(dir)) >= Math.abs(Math.sin(dir));
            w.set(g, S.LOG_MOSSY[alongX ? 0 : 2]);
            if (w.free(g + 1) && Hash.unit(Hash.of(h, x, z, 100 + k)) < 0.3) {
                w.set(g + 1, S.MOSS_CARPET);
            }
        }
    }

    /** The rings on top of a snapped stump: AXIS = Y, the bands turned round the stump's heart. */
    private static BlockState ringsY(int dx, int dz) {
        // From the block to the heart: (-dx, -dz). North is -z.
        double hx = -dx, hz = -dz;
        int r;
        if (Math.abs(hz) > 2 * Math.abs(hx)) {
            r = 0; // heart north or south
        } else if (Math.abs(hx) > 2 * Math.abs(hz)) {
            r = 1; // heart east or west
        } else {
            r = (hx < 0) == (hz < 0) ? 2 : 3; // north-west / south-east : north-east / south-west
        }
        return S.RINGS[1][r];
    }

    private static void treeFoliage(Work w, Tree t, int x, int z, int dx, int dz) {
        if (t.kind() == Level227Tile.SNAPPED) {
            return;
        }
        double rho = Math.sqrt(dx * dx + dz * dz);
        int g = t.ground();
        long h = t.hash();
        double[] ly = t.layerY(), lr = t.layerR(), lt = t.layerT();
        int crownBottom = Integer.MAX_VALUE;
        for (int l = 0; l < ly.length; l++) {
            double r = lr[l] * (1 + 0.2 * Level227Tree.noise(x * 0.22, z * 0.22, (int) (h & 511) + l * 7));
            double q = rho / r;
            if (q >= 1) {
                continue;
            }
            double f = Math.sqrt(1 - q * q);
            double cy = g + ly[l];
            int top = (int) Math.floor(cy + lt[l] * 0.9 * f);
            int bottom = (int) Math.ceil(cy - lt[l] * 0.45 * f - (q > 0.6 ? (q - 0.6) * 4 : 0));
            boolean rim = q > 0.72;
            for (int y = bottom; y <= top; y++) {
                if (w.free(y) && Hash.unit(Hash.of(h, x, z, y)) > 0.07) {
                    w.set(y, rim || l > 0 && q > 0.5 ? S.FERN_LEAVES : S.LEAVES);
                }
            }
            if (l == 0) {
                crownBottom = bottom;
            }
            // Fern fronds trailing from the rim of every layer.
            if (rim && Hash.unit(Hash.of(h, x, z, 77 + l)) < 0.45) {
                int len = 1 + Hash.range(Hash.of(h, x, z, 78 + l), 4);
                strand(w, bottom - 1, len, S.FRONDS);
            }
        }
        // Lianas from the top crown down towards the forest floor: some reach it (a way up).
        if (crownBottom != Integer.MAX_VALUE && t.kind() != Level227Tile.SHRUB && rho > trunkR(t, 0) + 1.5 && rho < lr[0] * 0.8
                && Hash.unit(Hash.of(h, x, z, 90)) < 0.03) {
            int full = crownBottom - 1 - g;
            int len = Hash.unit(Hash.of(h, x, z, 91)) < 0.5 ? full : 3 + Hash.range(Hash.of(h, x, z, 92), Math.max(1, full - 3));
            strand(w, crownBottom - 1, len, S.LIANA);
        }
        // Epiphytes, bracket fungi and (rarely) GammaPali on the trunk's bark.
        double r0 = trunkR(t, 3) + 0.3;
        if (rho > r0 && rho <= r0 + 1.1) {
            Direction out = Direction.getNearest(dx, 0, dz);
            for (int y = g + 1; y < g + t.height() - 4; y++) {
                if (!w.free(y) || rho > trunkR(t, y - g) + 1.4) {
                    continue;
                }
                double u = Hash.unit(Hash.of(h, x, z, y + 1000));
                if (u < 0.035) {
                    w.set(y, S.EPIPHYTE[out.ordinal()]);
                } else if (u < 0.05 && y - g < t.height() * 0.6) {
                    w.set(y, S.FUNGUS[out.ordinal()]);
                } else if (u < 0.0525 && y - g >= 4) {
                    w.set(y, S.GAMMAPALI[out.ordinal()]);
                }
            }
        }
    }

    /**
     * A fallen log: a round, mossy trunk lying half sunk along the ground, slightly bowed, thick at
     * its broken end (growth rings) and tapering to a splintered point at the other. Moss, ferns
     * and epiphytes grow along its top and bracket fungi on its sides.
     */
    private static void log(Work w, Log l, int x, int z, double px, double pz) {
        double tx = l.x1() - l.x0(), tz = l.z1() - l.z0();
        double len = Math.sqrt(tx * tx + tz * tz);
        double t = ((px - l.x0()) * tx + (pz - l.z0()) * tz) / (len * len);
        if (t < 0 || t > 1) {
            return;
        }
        // Sideways offset from the straight line, minus the bow: how far off the log's axis we are.
        double lat = (-(px - l.x0()) * tz + (pz - l.z0()) * tx) / len - l.bend() * Math.sin(Math.PI * t);
        double e = Math.abs(lat);
        double rr = l.r() * (1 - 0.3 * t) * (1 + 0.1 * Level227Tree.noise(t * len * 0.25, l.x0(), 86));
        if (t > 0.9) {
            rr *= (1 - t) / 0.1; // the splintered crown end
        }
        if (e >= rr + 1) {
            return;
        }
        double cy = l.y0() + (l.y1() - l.y0()) * t;
        long hash = Hash.of(227, x, z, 87);
        if (e >= rr) {
            // Just beside the log: a bracket fungus on its flank now and then.
            int y = (int) Math.floor(cy);
            if (w.free(y) && Hash.unit(hash) < 0.07 && rr > 1) {
                double sx = -tz / len * Math.signum(lat), sz = tx / len * Math.signum(lat);
                w.set(y, S.FUNGUS[Direction.getNearest(sx, 0, sz).ordinal()]);
            }
            return;
        }
        double f = Math.sqrt(rr * rr - e * e);
        int axis = Math.abs(tx) >= Math.abs(tz) ? 0 : 2;
        boolean end = l.cutEnd() && t * len < 1.0;
        double moss = Level227Tree.noise(px * 0.3, pz * 0.3, 85);
        int top = (int) Math.floor(cy + f);
        for (int y = (int) Math.ceil(cy - f); y <= top; y++) {
            if (!w.free(y) && !isGround(w.col[y])) {
                continue;
            }
            if (end) {
                double dv = cy - (y + 0.5);
                // From this block to the heart, sideways in world x (axis Z) or z (axis X).
                double dh = axis == 0 ? -lat * tx / len : lat * tz / len;
                int r = Math.abs(dv) > 2 * Math.abs(dh) ? 0 : Math.abs(dh) > 2 * Math.abs(dv) ? 1
                        : axis == 0 ? ((dh > 0) == (dv > 0) ? 2 : 3) : ((dh > 0) != (dv > 0) ? 2 : 3);
                w.set(y, S.RINGS[axis][r]);
            } else {
                w.set(y, moss > -0.3 || y == top ? S.LOG_MOSSY[axis] : S.LOG[axis]);
            }
        }
        if (!end && w.free(top + 1)) {
            double u = Hash.unit(Hash.mix(hash));
            if (u < 0.5) {
                w.set(top + 1, S.MOSS_CARPET);
            } else if (u < 0.58) {
                w.set(top + 1, S.UNDERGROWTH[2]);
            } else if (u < 0.63) {
                w.set(top + 1, S.EPIPHYTE[Direction.UP.ordinal()]);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Undergrowth on the branch tops, decorations on the flanks
    // ------------------------------------------------------------------------------------------

    private static void undergrowth(Work w, Level227Tile tile, int x, int z, double px, double pz) {
        double grassPatch = Level227Tree.noise(px * 0.045, pz * 0.045, 71);
        double lestronPatch = Level227Tree.noise(px * 0.08, pz * 0.08, 72);
        double flowerPatch = Level227Tree.noise(px * 0.03, pz * 0.03, 73);
        // Flytraps: the trap's dish lies flat on the ground (about 4.7 wide, centred on its block),
        // and dense tall grass and giant ferns grow right up to its rim, so it can't be told apart
        // from the rest of the grass until you step on it.
        double flyDist = -1;
        for (Fly f : tile.flytraps) {
            double d = Math.hypot(f.x() - x, f.z() - z);
            if (d <= Level227Tile.FLY_PATCH + 0.5) {
                flyDist = flyDist < 0 ? d : Math.min(flyDist, d);
            }
        }
        boolean nearBeam = false;
        for (Beam b : tile.beams) {
            nearBeam |= Math.hypot(px - b.x(), pz - b.z()) < b.radius() + 4;
        }
        for (int r = 0; r < w.runs; r++) {
            int top = w.runTop[r];
            int k = w.runTopSpan[r];
            int y = top + 1;
            if (y >= HEIGHT || !w.free(y)) {
                continue;
            }
            BlockState ground = w.col[top];
            long hash = Hash.of(227, x, z, y);
            double u = Hash.unit(hash);
            if (k < 0) {
                continue; // the trunk: nothing grows on its sheer bark
            }
            double nd = w.runTopNd[r];
            if (nd >= Level227Tree.WALKABLE_ND) {
                // A step on the steep flank: things that grow on bark walls.
                Direction out = w.outward[k];
                if (u < 0.03) {
                    w.set(y, S.FUNGUS[out.ordinal()]);
                } else if (u < 0.06) {
                    w.set(y, S.EPIPHYTE[Direction.UP.ordinal()]);
                } else if (u < 0.064) {
                    w.set(y, S.GAMMAPALI[Direction.UP.ordinal()]);
                }
                continue;
            }
            if (ground == S.WATER || ground == S.ASH || nearBeam || !isGround(ground) && !isWood(ground)) {
                continue;
            }
            if (nd > 0.62) {
                // The edge: thin moss over bark, and the Lestron.
                if (lestronPatch > 0.15 && u < 0.4) {
                    w.set(y, S.LESTRON);
                } else if (u < 0.3) {
                    w.set(y, S.MOSS_CARPET);
                } else if (u < 0.36) {
                    w.set(y, S.UNDERGROWTH[2]);
                } else if (u < 0.3615) {
                    // Rare at the edges: its gas reaches 2.5 blocks, and people come here for the Lestron.
                    w.set(y, S.GAMMAPALI[Direction.UP.ordinal()]);
                }
                continue;
            }
            if (flyDist >= 0) {
                if (flyDist < FLYTRAP_DISH_RADIUS) {
                    // Inside the dish's outline: only low cover you walk straight through. Within
                    // the trap's 1.5-block trigger only flat cover (leaf litter, moss), nothing taller.
                    boolean trigger = flyDist < 1.5;
                    if (u < 0.35) {
                        w.set(y, S.FALLEN_LEAF);
                    } else if (u < 0.6) {
                        w.set(y, trigger ? S.MOSS_CARPET : S.UNDERGROWTH[u < 0.48 ? 0 : 2]);
                    } else if (u < 0.75) {
                        w.set(y, S.MOSS_CARPET);
                    }
                } else if (w.free(y + 1)) {
                    // From the rim out: a wall of grass, and giant ferns arching over the dish.
                    double fern = flyDist < FLYTRAP_DISH_RADIUS + 1.3 ? 0.2 : 0.06;
                    if (u < fern) {
                        w.set(y, S.FERN_LOW);
                        w.set(y + 1, S.FERN_HIGH);
                    } else if (u < 0.95) {
                        w.set(y, S.TALL_GRASS_LOW);
                        w.set(y + 1, S.TALL_GRASS_HIGH);
                    }
                }
                continue;
            }
            if (grassPatch > 0.38 && u < 0.72 && w.free(y + 1)) {
                w.set(y, S.TALL_GRASS_LOW);
                w.set(y + 1, S.TALL_GRASS_HIGH);
            } else if (u < 0.07 && w.free(y + 1)) {
                w.set(y, S.FERN_LOW);
                w.set(y + 1, S.FERN_HIGH);
            } else if (u < 0.32) {
                int kind = (int) ((Level227Tree.noise(px * 0.1, pz * 0.1, 74) * 0.5 + 0.5) * 3.99);
                w.set(y, S.UNDERGROWTH[Math.max(0, Math.min(3, kind))]);
            } else if (u < 0.32 + Math.max(0, flowerPatch) * 0.12) {
                w.set(y, S.FLOWER[Hash.range(Hash.mix(hash), 4)]);
            } else if (u < 0.5) {
                w.set(y, S.FALLEN_LEAF);
            } else if (u < 0.6 && ground == S.MOSS) {
                w.set(y, S.MOSS_CARPET);
            } else if (u < 0.61) {
                w.set(y, S.EPIPHYTE[Direction.UP.ordinal()]);
            }
        }
    }

    /**
     * Under every run of wood: moss curtains (and fern fronds under the thinner limbs) hanging into
     * the void, lianas, bracket fungi and GammaPali on the steep lower flanks, and a Palette in
     * the bark wherever an underside hangs low over somewhere you can walk.
     */
    private static void hangings(Work w, int x, int z, double px, double pz) {
        for (int r = 0; r < w.runs; r++) {
            int bot = w.runBot[r];
            int k = w.runBotSpan[r];
            int y = bot - 1;
            if (y < Level227Tree.VOID_DEATH_Y || !w.free(y) || k < 0 || bot >= HEIGHT) {
                continue;
            }
            // How far down is free (something below: another branch, its forest...).
            int gap = 0;
            while (gap < 48 && y - gap > 0 && w.free(y - gap)) {
                gap++;
            }
            boolean overGround = gap < 48 && y - gap > 0;
            Span s = w.spans[k];
            long hash = Hash.of(227, x, z, bot);
            double u = Hash.unit(hash);
            // A Palette hangs 3-8 blocks over somewhere you can walk (another branch's top, a cave floor).
            if (overGround && gap >= 3 && gap <= 8 && isWood(w.col[bot]) && u < 0.05) {
                w.col[bot] = S.PALETTE[w.axis[k]];
                continue;
            }
            if (s.nd > 0.78) {
                Direction out = w.outward[k];
                if (u < 0.025) {
                    w.set(y, S.FUNGUS[out.ordinal()]);
                    continue;
                }
                if (u < 0.03) {
                    w.set(y, S.GAMMAPALI[Direction.DOWN.ordinal()]);
                    continue;
                }
            }
            int limit = overGround ? gap - 3 : 60;
            if (limit < 1) {
                continue;
            }
            // Curtains: bands along the branch where the moss hangs thick.
            double band = Level227Tree.noise(s.along * 0.035, s.side * 0.09, 81);
            boolean limb = Level227Tree.segments().get(s.id).order() >= Level227Tree.LIMB;
            if (band > 0.1 && u < 0.6) {
                double lenNoise = 0.5 + 0.5 * Level227Tree.noise(px * 0.21, pz * 0.21, 82);
                int len = 2 + (int) (lenNoise * (s.nd > 0.6 ? 22 : 12)) + Hash.range(Hash.mix(hash), 4);
                strand(w, y, Math.min(len, limit), limb && band > 0.4 ? S.FRONDS : S.HANGING_MOSS);
            } else if (u < 0.008) {
                strand(w, y, Math.min(12 + Hash.range(Hash.mix(hash + 1), 26), limit), S.LIANA);
            } else if (u < 0.02) {
                w.set(y, S.EPIPHYTE[Direction.DOWN.ordinal()]);
            }
        }
    }

    /** A hanging strand from {@code y} down: {@code len} blocks, the last one its tip; stops at anything. */
    private static void strand(Work w, int y, int len, BlockState[] kind) {
        int last = y;
        for (int i = 0; i < len; i++) {
            int yy = y - i;
            if (!w.free(yy) || yy < Level227Tree.VOID_DEATH_Y) {
                break;
            }
            w.set(yy, kind[0]);
            last = yy;
        }
        if (last <= y && w.col[last] == kind[0]) {
            w.col[last] = kind[1];
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static boolean isGround(@Nullable BlockState s) {
        return s == S.GRASS || s == S.MOSS || s == S.ROOT_SOIL || s == S.ASH;
    }

    private static boolean isWood(@Nullable BlockState s) {
        return s != null && (s.getBlock() == Level227Blocks.BARK.get() || s.getBlock() == Level227Blocks.BARK_MOSSY.get()
                || s == S.HEARTWOOD);
    }

    static double distToSegment(double x, double z, double x0, double z0, double x1, double z1) {
        double tx = x1 - x0, tz = z1 - z0;
        double len2 = tx * tx + tz * tz;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - x0) * tx + (z - z0) * tz) / len2));
        return Math.hypot(x - (x0 + tx * t), z - (z0 + tz * t));
    }
}
