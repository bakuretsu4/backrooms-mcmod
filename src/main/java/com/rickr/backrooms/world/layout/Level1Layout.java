package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.AxisBlock;
import com.rickr.backrooms.block.FloorMarkBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.PillarCornerBlock;
import com.rickr.backrooms.block.PillarNumberBigBlock;
import com.rickr.backrooms.block.PillarSignBlock;
import com.rickr.backrooms.block.PillarNumberBlock;
import com.rickr.backrooms.block.SignDecalBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Level 1, "Habitable Zone": an endless underground parking structure crossed with a warehouse
 * (both wikis: wikidot's "halls" and "corridors", fandom's parking floors and storage lockers).
 *
 * <p>Everything hangs off a regular 8-block grid. At every grid corner stands a 2x2 concrete
 * pillar (darker base band, a big painted number on each face; now and then a direction sign above
 * it pointing nowhere). The 2-block strips between neighbouring pillars are "partitions": almost
 * always open, now and then a chain-link fence or a stretch of wall.
 *
 * <ul>
 *   <li><b>The halls</b> (three quarters of the level): wide open pillar forests stretching into
 *       the dark, tube strips across the bays and tubes on the pillars, ceiling beams, puddles
 *       (some steaming), rusty pipes under the ceiling, rebar jutting out of the floor, crate
 *       stacks. They change slowly by <b>sector</b> (see {@link Level1Areas}): grey parking, the
 *       warm warehouse sector, a decrepit one.</li>
 *   <li><b>Corridor blocks</b>: walled-off zones of tight white corridors and small rooms, behind
 *       fire doors under EXIT signs ({@link Level1Areas#fillCorridors}). No monsters spawn in them.</li>
 *   <li><b>Storage lockers</b> and <b>ramps down</b> to a lower floor, placed here (they keep clear
 *       of exits, safe rooms, anomalies and corridor blocks) and built by {@link Level1Areas}.</li>
 * </ul>
 *
 * <p>Exit: one white stairwell per 384x384 region.
 *
 * <p><b>Safe rooms.</b> About 60% of the 128x128 squares try for one (only in the halls): a
 * concrete room built over a block of 2x2 bays (so its outside lines up with the pillar rows), with
 * a metal door frame, and inside a carpet, wood panelling under white paint and a warm lamp. A
 * 1-wide strip of floor runs all around it so it always joins the hall.
 *
 * <p><b>Anomalous areas</b> (shapes in {@link Level1AnomalyPlan}, behaviours in
 * {@code world/anomaly/level1}): crate halls with racks inside the halls, small painting rooms
 * standing in the halls, patches of low fog and puddles, and, rarely, the doorway into the
 * Corrupted Corridor (Level 1.1). Two grids place them: one site at most per 96x96 square for the
 * first three, per 512x512 square for the corridor. Their planners only use this file's own pure
 * plan functions, and keep clear of exits, safe rooms and each other.
 */
public final class Level1Layout extends LevelLayout {
    /** The pillar grid: a pillar every 12 blocks ("spacious": wide bays under a high ceiling). */
    static final int GRID = 12;
    /** Pillars are 3x3 blocks, so the rows between them (the partitions) are 3 wide too. */
    static final int PILLAR = 3;
    private static final int CLEARING_RADIUS = 8;
    /** Interior height of the halls; the corridor blocks and the lockers keep the old, lower one. */
    private static final int HALL_INTERIOR = 7;
    private static final int LOW_INTERIOR = 5;
    /** The painting room's own ceiling, above its floor: a 4-high room. */
    private static final int PAINTING_ROOF = 5;

    // Partition states between two pillars.
    private static final int OPEN = 0;
    private static final int CHAIN = 1;
    private static final int SOLID = 2; // a stretch of wall

    /** Storage lockers: one try per square of this size; ramps down: likewise. */
    private static final int LOCKER_CELL = 160;
    private static final double LOCKER_SHARE = 0.5;
    private static final int RAMP_CELL = 192;
    private static final double RAMP_SHARE = 0.45;

    /** Safe rooms: the free strip all around each one. */
    private static final int SAFE_MOAT = 1;
    /** Safe rooms keep at least this far (chessboard distance from their centre) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;
    /** The Corrupted Corridor; its 138-block length may cross into the next square. */
    private final AnomalyGrid corridorGrid = new AnomalyGrid(Level1AnomalyPlan.CORRIDOR_CELL, 1, this::planCorridor);
    /** Crate halls, painting rooms and fog patches; each always fits inside its own square. */
    private final AnomalyGrid roomGrid = new AnomalyGrid(Level1AnomalyPlan.ROOM_CELL, 0, this::planRoomSite);

    public Level1Layout() {
        super(HALL_INTERIOR, new ExitLocator(384, 48, 2000));
    }

    // ==========================================================================================
    // Safe rooms
    // ==========================================================================================

    /**
     * The safe room whose footprint (room plus the free strip around it) contains (x, z), or null.
     * The footprint covers two bays by two bays, from just inside one pillar row to just before
     * the pillar row two cells further on, so it never leaves half a pillar standing.
     */
    @Nullable
    private SafeRoomPlan safeRoomNear(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, SafeRoomPlan.CELL);
        int cellZ = Math.floorDiv(z, SafeRoomPlan.CELL);
        long h = SafeRoomPlan.cellHash(seed, cellX, cellZ, 410);
        if (!SafeRoomPlan.rolled(h)) {
            return null;
        }
        // Grid cells a..a+1 and b..b+1, wholly inside the square and never at its edge.
        int a = firstCell(cellX * SafeRoomPlan.CELL) + 1 + Hash.range(Hash.mix(h ^ 1), cellsIn(cellX * SafeRoomPlan.CELL, SafeRoomPlan.CELL) - 4);
        int b = firstCell(cellZ * SafeRoomPlan.CELL) + 1 + Hash.range(Hash.mix(h ^ 2), cellsIn(cellZ * SafeRoomPlan.CELL, SafeRoomPlan.CELL) - 4);
        int footX = a * GRID + PILLAR; // footprint: 21 x 21 blocks from here
        int footZ = b * GRID + PILLAR;
        int footSize = 2 * GRID - PILLAR;
        if (x < footX || x >= footX + footSize || z < footZ || z >= footZ + footSize) {
            return null;
        }
        // Only out in the halls (never cutting into a corridor block).
        if (!hallZone(seed, footX - 1, footZ - 1) || !hallZone(seed, footX + footSize, footZ - 1)
                || !hallZone(seed, footX - 1, footZ + footSize) || !hallZone(seed, footX + footSize, footZ + footSize)) {
            return null;
        }
        SafeRoomPlan plan = new SafeRoomPlan(footX + SAFE_MOAT, footZ + SAFE_MOAT, footSize - 2 * SAFE_MOAT,
                footSize - 2 * SAFE_MOAT, SafeRoomPlan.doorSide(h), 2);
        int[] exit = exits().nearest(seed, plan.centerX(), plan.centerZ());
        if (Math.max(Math.abs(exit[0] - plan.centerX()), Math.abs(exit[1] - plan.centerZ())) < SAFE_EXIT_DISTANCE) {
            return null;
        }
        return plan;
    }

    /** Everything inside a safe room, walls and doorway included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomNear(seed, x, z);
        return plan != null && plan.contains(x, z);
    }

    private void fillSafeRoom(SafeRoomPlan plan, int x, int z, Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        if (!plan.contains(x, z)) {
            // The free strip around the room: bare concrete.
            out.set(floorY(), ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
            out.set(ceilingY(), concrete);
            return;
        }
        plan.fill(x, z, floorY(), ceilingY(), new SafeRoomPlan.Style(
                ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                concrete,
                ModBlocks.GARAGE_PILLAR.get().defaultBlockState(), // the dark base band outside
                concrete,
                ModBlocks.WOOD_FLOOR.get().defaultBlockState(),    // wood panelling below the dado line
                ModBlocks.STAIRWELL_WALL.get().defaultBlockState(), // white paint above it
                ModBlocks.FLOOR_GRATE.get().defaultBlockState()),   // dark metal door frame
                out);
    }

    // ==========================================================================================
    // Halls
    // ==========================================================================================

    /** Large areas where the power is out: no tubes at all. */
    private static boolean darkZone(long seed, int x, int z) {
        return zone(seed, x, z, 40, 25) < 0.25;
    }

    /** True out in the open halls, false inside a walled-off corridor block. */
    private static boolean hallZone(long seed, int x, int z) {
        return !Level1Areas.corridorZone(seed, x, z);
    }

    /**
     * The partition between pillar (cx, cz) and its neighbour: axis 0 = the row running east
     * (along X), axis 1 = the row running south (along Z). Almost always open: now and then a
     * chain-link fence, or a stretch of wall that breaks up the view.
     */
    private static int partition(long seed, int cx, int cz, int axis) {
        double r = Hash.unit(seed, cx, cz, 30 + axis);
        return r < 0.86 ? OPEN : r < 0.94 ? CHAIN : SOLID;
    }

    /**
     * The first grid cell starting inside a square that starts at {@code squareMin} (squares of
     * other sizes than a multiple of 12 don't line up with the grid, so planners count from here).
     */
    private static int firstCell(int squareMin) {
        return Math.floorDiv(squareMin + GRID - 1, GRID);
    }

    /** How many whole grid cells fit in that square from {@link #firstCell} on. */
    private static int cellsIn(int squareMin, int size) {
        return Math.floorDiv(squareMin + size, GRID) - firstCell(squareMin);
    }

    /** Partition containing a pillar-row column (mx/mz as in {@link #fillColumn}); OPEN for bays and pillars. */
    private static int partitionAt(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mz < PILLAR && mx >= PILLAR) return partition(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), 0);
        if (mx < PILLAR && mz >= PILLAR) return partition(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), 1);
        return OPEN;
    }

    /**
     * A vertical tube light on a stretch of wall (walls stand in the middle of their 3-wide row),
     * facing into a bay. Returns the direction the lamp faces, or null. Each side of a wall gets
     * one, 40% of the time.
     */
    @Nullable
    private static Direction wallLampFace(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        Direction face;
        int axis;
        if (mz == 0 && mx == 7) { face = Direction.NORTH; axis = 0; }
        else if (mz == 2 && mx == 5) { face = Direction.SOUTH; axis = 0; }
        else if (mx == 0 && mz == 5) { face = Direction.WEST; axis = 1; }
        else if (mx == 2 && mz == 7) { face = Direction.EAST; axis = 1; }
        else return null;
        if (partition(seed, cx, cz, axis) != SOLID || darkZone(seed, x, z)) {
            return null;
        }
        return Hash.unit(seed, x, z, 52) < 0.4 ? face : null;
    }

    /** Rusty rebar sticking out of the floor beside a pillar row: mostly in the decrepit sector. */
    private static boolean rebar(long seed, int x, int z, int sector) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (!(mx == PILLAR || mz == PILLAR) || mx < PILLAR || mz < PILLAR) {
            return false;
        }
        double share = sector == Level1Areas.RUST ? 0.035 : sector == Level1Areas.AQUILA ? 0.005 : 0.003;
        return Hash.unit(seed, x, z, 7430) < share;
    }

    /** A rusty dripping pipe in the services row (when no duct runs there). */
    private static boolean rustyPipe(long seed, int x, int z, int sector) {
        double share = sector == Level1Areas.RUST ? 0.6 : sector == Level1Areas.AQUILA ? 0.25 : 0.15;
        // One decision per run of three bays, so pipes run on for a while and then stop.
        return Hash.unit(seed, Math.floorDiv(x, 3 * GRID), Math.floorDiv(z, GRID), 7431) < share;
    }

    /** A wide ventilation duct over a run of three bays (more in the warehouse sector). */
    private static boolean duct(long seed, int x, int z, int sector) {
        double share = sector == Level1Areas.GILD ? 0.35 : 0.18;
        return Hash.unit(seed, Math.floorDiv(x, 3 * GRID), Math.floorDiv(z, GRID), 7473) < share;
    }

    /**
     * Some of the warehouse sector's bays hold pallet racks (decided per bay, from its centre's
     * sector, so a bay is either all racks or none).
     */
    private static boolean rackBay(long seed, int cx, int cz) {
        return Level1Areas.sector(seed, cx * GRID + GRID / 2, cz * GRID + GRID / 2) == Level1Areas.GILD
                && Hash.unit(seed, cx, cz, 7470) < 0.4;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return walkable(seed, x, z, true);
    }

    /**
     * Nobody arrives (or respawns) inside a crate hall, painting room or the Corrupted Corridor:
     * crates appear on the floor there, the room is a dead end, and the corridor throws you around.
     * Nor in a corridor block or the storage lockers: you arrive out in the halls. The fog patches
     * are just a damp bit of hall, so arriving there is fine.
     */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        AnomalySite site = anomalyAt(seed, x, z);
        if (site != null && !Level1AnomalyPlan.isFog(site)) {
            return false;
        }
        // Out in the halls: not shut away in a corridor block or a locker hallway.
        if (!hallZone(seed, x, z) || lockersAt(seed, x, z) != null) {
            return false;
        }
        return isWalkable(seed, x, z);
    }

    /**
     * {@link #isWalkable}, optionally ignoring the anomalous areas: their planners ask about the
     * garage as it would be without them (asking about the anomalies there would go round in circles).
     */
    private boolean walkable(long seed, int x, int z, boolean withAnomalies) {
        int d = exits().distance(seed, x, z);
        if (d <= STAIRWELL_RADIUS) return false;
        if (d <= CLEARING_RADIUS) return true;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            return !safe.contains(x, z) || safe.isWalkable(x, z);
        }
        AnomalySite site = withAnomalies ? anomalyAt(seed, x, z) : null;
        if (Level1AnomalyPlan.isCorridor(site)) {
            return Level1AnomalyPlan.corridorWalkable(site, x, z);
        }
        if (site != null && Level1AnomalyPlan.PAINTING_ROOM.equals(site.kind())) {
            return paintingRoomWalkable(site, x, z);
        }
        if (withAnomalies) {
            Level1Areas.LockerSite lockers = lockersAt(seed, x, z);
            if (lockers != null) {
                return Level1Areas.lockersWalkable(lockers, x, z);
            }
            Level1Areas.RampSite ramp = rampAt(seed, x, z);
            if (ramp != null && (ramp.rampIndex(x, z) >= 0 || ramp.curb(x, z))) {
                return false;
            }
        }
        if (!hallZone(seed, x, z)) {
            return Level1Areas.corridorWalkable(seed, x, z);
        }
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        boolean crateHall = Level1AnomalyPlan.inCrateHall(site, x, z);
        if (mx < PILLAR && mz < PILLAR) return false; // pillar
        if (mz < PILLAR || mx < PILLAR) {
            // A wall or fence stands in the middle of its row; the rows either side stay open.
            int axis = mz < PILLAR ? 0 : 1;
            int state = crateHall ? OPEN : partition(seed, cx, cz, axis);
            int row = axis == 0 ? mz : mx;
            return state == OPEN || row != 1;
        }
        if ((crateHall || rackBay(seed, cx, cz)) && Level1Racks.rackRow(mx, mz)) return false;
        if (crateHall) return true;
        return crateHeight(seed, x, z) == 0 && backpackFacing(seed, x, z) == null
                && !rebar(seed, x, z, Level1Areas.sector(seed, x, z));
    }

    /** Monsters keep out of the corridor blocks (wikidot: entities very rarely wander there). */
    @Override
    public boolean isQuietArea(long seed, int x, int z) {
        return !hallZone(seed, x, z);
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        BlockState floorBlock = ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int exitDistance = Math.max(Math.abs(dx), Math.abs(dz));

        if (stairwell(seed, dx, dz, x - dx, z - dz, concrete, concrete, false, out)) {
            return;
        }
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            fillSafeRoom(safe, x, z, out);
            return;
        }
        // Anomalous areas (see Level1AnomalyPlan). The corridor and the painting room replace the
        // whole column; a crate hall and a fog patch change parts of the garage built below.
        AnomalySite site = anomalyAt(seed, x, z);
        if (Level1AnomalyPlan.isCorridor(site)) {
            Level1AnomalyPlan.fillCorridor(site, x, z, floor, ceiling, out);
            return;
        }
        if (site != null && Level1AnomalyPlan.PAINTING_ROOM.equals(site.kind())) {
            fillPaintingRoom(seed, site, x, z, out);
            return;
        }
        Level1Areas.LockerSite lockers = lockersAt(seed, x, z);
        if (lockers != null) {
            Level1Areas.fillLockers(lockers, x, z, floor, lowCeiling(), out);
            fillAboveLowCeiling(out);
            return;
        }
        // A ramp down: the lower floor under this part of the hall, the ramp itself, and a curb
        // along the opening in the floor above. The rest of the hall is built over it as usual.
        Level1Areas.RampSite ramp = rampAt(seed, x, z);
        if (ramp != null) {
            int a = ramp.rampIndex(x, z);
            if (a >= 0) {
                Level1Areas.fillRamp(a, floor, ceiling, out);
                return;
            }
            Level1Areas.fillLowerFloor(seed, ramp, x, z, floor, out);
            if (ramp.curb(x, z)) {
                out.set(floor + 1, concrete);
            }
        }
        if (!hallZone(seed, x, z)) {
            Level1Areas.fillCorridors(seed, x, z, floor, lowCeiling(), out);
            fillAboveLowCeiling(out);
            return;
        }
        int sector = Level1Areas.sector(seed, x, z);
        boolean crateHall = Level1AnomalyPlan.inCrateHall(site, x, z);
        out.set(ceiling, concrete);
        boolean wet = puddle(seed, x, z, sector) || Level1AnomalyPlan.fogPuddleSpot(site, x, z);
        out.set(floor, !wet ? floorBlock
                : steaming(seed, x, z, sector) ? ModBlocks.L1_STEAMING_PUDDLE.get().defaultBlockState()
                : ModBlocks.WET_CONCRETE_FLOOR.get().defaultBlockState());
        if (exitDistance <= CLEARING_RADIUS) {
            // Open apron around the stairwell, lit by tube strips on all four sides.
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            if (az == 7 && ax <= 3) {
                out.set(ceiling - 1, hanging(Direction.Axis.X));
            } else if (ax == 7 && az <= 3) {
                out.set(ceiling - 1, hanging(Direction.Axis.Z));
            }
            return;
        }

        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        boolean racks = crateHall || rackBay(seed, cx, cz);
        fillCeiling(seed, x, z, mx, mz, cx, cz, sector, racks, crateHall, out);

        if (mx < PILLAR && mz < PILLAR) {
            fillPillar(seed, cx, cz, mx, mz, out);
            return;
        }

        if (mz < PILLAR || mx < PILLAR) {
            int axis = mz < PILLAR ? 0 : 1;
            // Inside a crate hall every partition is open: one big storage hall.
            int state = crateHall ? OPEN : partition(seed, cx, cz, axis);
            int row = axis == 0 ? mz : mx;
            if (state != OPEN && row == 1) {
                if (state == SOLID) {
                    BlockState wall = sector == Level1Areas.RUST ? ModBlocks.L1_CRACKED_CONCRETE.get().defaultBlockState() : concrete;
                    for (int y = floor + 1; y < ceiling - 1; y++) { // up to the beam over the row
                        out.set(y, wall);
                    }
                } else {
                    BlockState fence = ModBlocks.CHAIN_LINK.get().defaultBlockState()
                            .setValue(IronBarsBlock.EAST, axis == 0).setValue(IronBarsBlock.WEST, axis == 0)
                            .setValue(IronBarsBlock.NORTH, axis == 1).setValue(IronBarsBlock.SOUTH, axis == 1);
                    for (int y = floor + 1; y <= floor + 3; y++) {
                        out.set(y, fence);
                    }
                }
                return;
            }
            fillBesidePillar(seed, x, z, out);
            Direction lampFace = wallLampFace(seed, x, z);
            if (lampFace != null) {
                BlockState lamp = ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, lampFace);
                out.set(floor + 3, lamp);
                out.set(floor + 4, lamp);
            }
            return;
        }

        // A bay.
        if (racks && Level1Racks.rackRow(mx, mz)) {
            if (crateHall) {
                Level1AnomalyPlan.fillCrateHallBay(site, x, z, mx, mz, floor, out);
            } else {
                Level1Racks.fill(seed, x, z, mx, mz, floor, false, out);
            }
            return;
        }
        if (crateHall) {
            return; // the aisles stay clear: crates turn up there
        }
        if (!wet) {
            BlockState mark = floorMark(seed, x, z, mx, mz, cx, cz, sector, racks);
            if (mark != null) {
                out.set(floor, mark);
            }
        }
        if (rebar(seed, x, z, sector)) {
            out.set(floor + 1, ModBlocks.L1_REBAR.get().defaultBlockState());
            return;
        }
        int crates = crateHeight(seed, x, z);
        for (int i = 0; i < crates; i++) {
            out.set(floor + 1 + i, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
        }
        Direction backpack = backpackFacing(seed, x, z);
        if (backpack != null) {
            out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, backpack));
        }
        if (crates == 0 && backpack == null) {
            BlockState low = racks || sector == Level1Areas.GILD ? null : parkingFixture(seed, mx, mz, cx, cz);
            if (low != null) {
                out.set(floor + 1, low);
            } else if (Level1AnomalyPlan.fogPuddleAtStart(site, x, z)) {
                // Condensation from the low fog, where nothing else stands.
                out.set(floor + 1, ModBlocks.L1_CONDENSATION.get().defaultBlockState());
            }
        }
    }

    /** The corridor blocks' and lockers' ceiling (the old 5-high Level 1). */
    private int lowCeiling() {
        return floorY() + LOW_INTERIOR + 1;
    }

    /** Solid concrete from just above the low ceiling up to the halls' ceiling. */
    private void fillAboveLowCeiling(Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        for (int y = lowCeiling() + 1; y <= ceilingY(); y++) {
            out.set(y, concrete);
        }
    }

    /**
     * Everything under the ceiling of a hall, in rows across each bay (they all run east-west):
     * downstand beams over every pillar row both ways; the red sprinkler main with a head every
     * three blocks; a conduit tight to the ceiling; two hanging fixtures down the middle of the bay
     * (over the aisles where there are racks); a wide duct or a rusty pipe; a cable tray.
     */
    private void fillCeiling(long seed, int x, int z, int mx, int mz, int cx, int cz, int sector, boolean racks, boolean alwaysLit,
                             Column out) {
        int y = ceilingY() - 1;
        if (mx < PILLAR || mz < PILLAR) {
            out.set(y, ModBlocks.CEILING_BEAM.get().defaultBlockState());
            return;
        }
        boolean lightRow = racks ? mz == 4 || mz == 10 : mz == 7;
        if (lightRow) {
            // More of them dead in the decrepit sector; a crate hall is always lit (the Gild's
            // crate halls are "better lit" than the rest), every fixture working.
            double dead = alwaysLit ? -1 : sector == Level1Areas.RUST ? 0.55 : 0.3;
            boolean fixture = mx >= 4 && mx <= 6 || mx >= 8 && mx <= 10;
            if (fixture && (alwaysLit || !darkZone(seed, x, z)) && Hash.unit(seed, cx * 2 + (mx >= 8 ? 1 : 0), cz * 2 + (mz == 10 ? 1 : 0), 27) > dead) {
                out.set(y, hanging(Direction.Axis.X));
            }
            return;
        }
        if (mz == (racks ? 7 : 4)) {
            out.set(y, (mx % 3 == 1 ? ModBlocks.L1_SPRINKLER_HEAD : ModBlocks.L1_SPRINKLER_PIPE).get().defaultBlockState());
            return;
        }
        if (mz == 5) {
            if (sector != Level1Areas.RUST || Hash.unit(seed, cx, cz, 7471) < 0.5) {
                out.set(y, ModBlocks.L1_CONDUIT.get().defaultBlockState());
            }
            return;
        }
        if (mz == 8 || mz == 9) {
            if (duct(seed, x, z, sector)) {
                out.set(y, ModBlocks.L1_DUCT.get().defaultBlockState());
            } else if (mz == 9 && rustyPipe(seed, x, z, sector)) {
                out.set(y, ModBlocks.L1_CEILING_PIPE.get().defaultBlockState());
            }
            return;
        }
        if (mz == 10 && !racks && Hash.unit(seed, Math.floorDiv(x, 3 * GRID), cz, 7472) < 0.6) {
            out.set(y, ModBlocks.L1_CABLE_TRAY.get().defaultBlockState());
        }
    }

    /**
     * Parking markings on a bay floor (the warehouse sector gets a yellow walkway line instead).
     * Each bay is two rows of three stalls along its pillar rows (stalls 3 wide, a white line
     * between them, the stall number at the lane end) and a 3-wide lane down the middle: yellow
     * dashes, a one-way arrow, now and then SLOW or a drain. Oil under the cars, tyre marks.
     */
    @Nullable
    private static BlockState floorMark(long seed, int x, int z, int mx, int mz, int cx, int cz, int sector, boolean racks) {
        long h = Hash.of(seed, x, z, 7476);
        double r = Hash.unit(h);
        if (racks || sector == Level1Areas.GILD) {
            if (!racks && mz == 7) {
                return mark(FloorMarkBlock.DASHES, Direction.EAST);
            }
            return r < 0.03 ? mark(FloorMarkBlock.OIL, Direction.from2DDataValue(Hash.range(Hash.mix(h), 4)))
                    : r < 0.06 ? mark(FloorMarkBlock.TYRES, Hash.mix(h) % 2 == 0 ? Direction.EAST : Direction.WEST) : null;
        }
        if (mz < 6 || mz > 8) {
            if (mx == 3 || mx == 6 || mx == 9) return mark(FloorMarkBlock.LINE, Direction.WEST);
            if (mx == 11) return mark(FloorMarkBlock.LINE, Direction.EAST);
            if ((mz == 5 || mz == 9) && (mx == 4 || mx == 7 || mx == 10)) {
                // The stall number at the lane end of each stall, readable from the lane.
                int number = FloorMarkBlock.STALL + Hash.range(Hash.of(seed, x, z, 7477), FloorMarkBlock.STALL_NUMBERS);
                return mark(number, mz == 5 ? Direction.SOUTH : Direction.NORTH);
            }
            if ((mz == 4 || mz == 10) && r < 0.22) {
                return mark(FloorMarkBlock.OIL, Direction.from2DDataValue(Hash.range(Hash.mix(h), 4)));
            }
            return null;
        }
        if (mz == 7) {
            // One-way lanes: every bay of a row points the same way.
            Direction way = Hash.unit(seed, 0, cz, 7478) < 0.5 ? Direction.EAST : Direction.WEST;
            if (mx == 7) return mark(FloorMarkBlock.ARROW, way);
            if (mx == 4 && Hash.unit(seed, cx, cz, 7479) < 0.1) return mark(FloorMarkBlock.SLOW, way.getOpposite());
            if (mx == 10 && Hash.unit(seed, cx, cz, 7481) < 0.2) return mark(FloorMarkBlock.DRAIN, Direction.NORTH);
            return mark(FloorMarkBlock.DASHES, Direction.EAST);
        }
        return r < 0.08 ? mark(FloorMarkBlock.TYRES, Hash.mix(h) % 2 == 0 ? Direction.EAST : Direction.WEST) : null;
    }

    private static BlockState mark(int variant, Direction facing) {
        return ModBlocks.L1_FLOOR_MARK.get().defaultBlockState().setValue(FloorMarkBlock.VARIANT, variant)
                .setValue(FloorMarkBlock.FACING, facing);
    }

    /** Wheel stops at the back of the stalls (against the pillar rows), and a speed bump across some lanes. */
    @Nullable
    private static BlockState parkingFixture(long seed, int mx, int mz, int cx, int cz) {
        if ((mz == PILLAR || mz == GRID - 1) && (mx == 4 || mx == 7 || mx == 10)
                && Hash.unit(seed, cx * 3 + mx, cz * 2 + (mz == PILLAR ? 0 : 1), 7482) < 0.85) {
            return ModBlocks.L1_WHEEL_STOP.get().defaultBlockState();
        }
        if (mx == 9 && mz >= 6 && mz <= 8 && Hash.unit(seed, cx, cz, 7483) < 0.12) {
            return ModBlocks.L1_SPEED_BUMP.get().defaultBlockState().setValue(AxisBlock.AXIS, Direction.Axis.Z);
        }
        return null;
    }

    /**
     * In front of a pillar face (the face has three blocks: 0, 1 = the number, 2): on block 0 a
     * vertical tube light up high (now and then a red alarm lamp above it), on block 2 now and
     * then a fire extinguisher cabinet low down or a convex mirror up high.
     */
    private void fillBesidePillar(long seed, int x, int z, Column out) {
        int floor = floorY();
        for (Direction toPillar : Direction.Plane.HORIZONTAL) {
            int px = x + toPillar.getStepX();
            int pz = z + toPillar.getStepZ();
            int along = toPillar.getAxis() == Direction.Axis.Z ? Math.floorMod(px, GRID) : Math.floorMod(pz, GRID);
            if (Math.floorMod(px, GRID) >= PILLAR || Math.floorMod(pz, GRID) >= PILLAR) {
                continue;
            }
            Direction face = toPillar.getOpposite();
            long h = Hash.of(seed, Math.floorDiv(px, GRID) * 4 + face.get2DDataValue(), Math.floorDiv(pz, GRID), 7475);
            if (along == 0) {
                if (!darkZone(seed, px, pz) && Hash.unit(h) > 0.25) {
                    BlockState tube = ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, face);
                    out.set(floor + 4, tube);
                    out.set(floor + 5, tube);
                }
                if (Hash.unit(Hash.mix(h ^ 1)) < 0.05) {
                    out.set(floor + 6, ModBlocks.RED_ALARM_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, face));
                }
            } else if (along == 2) {
                if (Hash.unit(Hash.mix(h ^ 2)) < 0.2) {
                    out.set(floor + 2, ModBlocks.L1_EXTINGUISHER.get().defaultBlockState().setValue(FurnitureBlock.FACING, face));
                }
                if (Hash.unit(Hash.mix(h ^ 3)) < 0.08) {
                    out.set(floor + 5, ModBlocks.L1_CONVEX_MIRROR.get().defaultBlockState().setValue(FurnitureBlock.FACING, face));
                }
            }
            return; // a cell touches at most one pillar face
        }
    }

    /**
     * Now and then a backpack someone dropped lies on the floor of a bay: rare. Returns the way it
     * faces, or null for no backpack here.
     */
    @Nullable
    private Direction backpackFacing(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < PILLAR || mz < PILLAR) {
            return null;
        }
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        long h = Hash.of(seed, cx, cz, 54);
        if (Hash.unit(h) >= 0.05 || rackBay(seed, cx, cz)) {
            return null;
        }
        if (mx != PILLAR + Hash.range(Hash.mix(h ^ 1), GRID - PILLAR) || mz != PILLAR + Hash.range(Hash.mix(h ^ 2), GRID - PILLAR)) {
            return null;
        }
        if (crateHeight(seed, x, z) > 0) {
            return null;
        }
        return Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 3), 4));
    }

    private static BlockState hanging(Direction.Axis axis) {
        return Level1Areas.hanging(axis);
    }

    /**
     * One block of a 3x3 pillar. The four corners are bevelled; the middle block of each face
     * carries the pillar's number, two blocks tall, on a band in the sector's colour that runs all
     * the way round. Below that the base band (dark, or hazard stripes in the warehouse sector);
     * above it bare concrete, now and then a garage sign or an EXIT sign pointing nowhere.
     */
    private void fillPillar(long seed, int cx, int cz, int mx, int mz, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        int sector = Level1Areas.sector(seed, cx * GRID, cz * GRID);
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        if (mx == 1 && mz == 1) {
            for (int y = floor + 1; y < ceiling; y++) {
                out.set(y, concrete);
            }
            return;
        }
        if (mx != 1 && mz != 1) {
            Direction corner = mz == 0 ? (mx == 0 ? Direction.NORTH : Direction.EAST) : (mx == 2 ? Direction.SOUTH : Direction.WEST);
            BlockState block = ModBlocks.L1_PILLAR_CORNER.get().defaultBlockState().setValue(PillarCornerBlock.FACING, corner);
            for (int y = floor + 1; y < ceiling; y++) {
                int band = y == floor + 1 ? (sector == Level1Areas.GILD ? PillarCornerBlock.BASE_HAZARD : PillarCornerBlock.BASE_DARK)
                        : y <= floor + 3 ? PillarCornerBlock.SECTOR_BAND + sector : PillarCornerBlock.PLAIN;
                out.set(y, block.setValue(PillarCornerBlock.BAND, band));
            }
            return;
        }
        Direction face = mz == 0 ? Direction.NORTH : mx == 2 ? Direction.EAST : mz == 2 ? Direction.SOUTH : Direction.WEST;
        BlockState cracked = ModBlocks.L1_CRACKED_CONCRETE.get().defaultBlockState();
        out.set(floor + 1, sector == Level1Areas.GILD ? ModBlocks.L1_PILLAR_PAINTED.get().defaultBlockState()
                : ModBlocks.GARAGE_PILLAR.get().defaultBlockState());
        int number = Hash.range(Hash.of(seed, cx, cz, 34), PillarNumberBlock.LABELS.length);
        BlockState digits = ModBlocks.L1_PILLAR_NUMBER.get().defaultBlockState()
                .setValue(PillarNumberBigBlock.FACING, face)
                .setValue(PillarNumberBigBlock.NUMBER, number)
                .setValue(PillarNumberBigBlock.SECTOR, sector);
        out.set(floor + 2, digits.setValue(PillarNumberBigBlock.UPPER, false));
        out.set(floor + 3, digits.setValue(PillarNumberBigBlock.UPPER, true));
        for (int y = floor + 4; y < ceiling; y++) {
            boolean crack = sector == Level1Areas.RUST && Hash.unit(seed, cx * 3 + mx, cz * 3 + mz, 7440 + y) < 0.45;
            out.set(y, crack ? cracked : concrete);
        }
        long signHash = Hash.of(seed, cx * 4 + face.get2DDataValue(), cz, 7441);
        double r = Hash.unit(signHash);
        if (r < 0.07) {
            // fandom: "exit signs point to nonexistent doorways or locations, often directly at blank walls".
            out.set(floor + 4, ModBlocks.L1_SIGN.get().defaultBlockState()
                    .setValue(SignDecalBlock.FACING, face)
                    .setValue(SignDecalBlock.VARIANT, Level1Areas.signVariant(sector, Hash.mix(signHash))));
        } else if (r < 0.3) {
            out.set(floor + 4, ModBlocks.L1_PILLAR_SIGN.get().defaultBlockState()
                    .setValue(PillarSignBlock.FACING, face)
                    .setValue(PillarSignBlock.VARIANT, pillarSign(sector, Hash.unit(Hash.mix(signHash ^ 5)))));
        }
    }

    /** Which garage sign: mostly the level plate, the rest headroom, no parking, speed, the zone letter. */
    private static int pillarSign(int sector, double r) {
        if (r < 0.35) return PillarSignBlock.LEVEL;
        if (r < 0.55) return PillarSignBlock.HEADROOM;
        if (r < 0.7) return PillarSignBlock.NO_PARKING;
        if (r < 0.85) return PillarSignBlock.SPEED;
        return PillarSignBlock.ZONE + (sector == Level1Areas.GILD ? 1 : 0);
    }

    /** Round wet puddles: some 6x6 cells hold one (more in the decrepit sector), with a random radius. */
    private static boolean puddle(long seed, int x, int z, int sector) {
        int px = Math.floorDiv(x, 6);
        int pz = Math.floorDiv(z, 6);
        long h = Hash.of(seed, px, pz, 23);
        if (Hash.unit(h) >= (sector == Level1Areas.RUST ? 0.4 : 0.22)) {
            return false;
        }
        int ox = x - (px * 6 + 2 + Hash.range(Hash.mix(h), 2));
        int oz = z - (pz * 6 + 2 + Hash.range(Hash.mix(h ^ 7), 2));
        int radiusSq = 2 + Hash.range(Hash.mix(h ^ 9), 5);
        return ox * ox + oz * oz <= radiusSq;
    }

    /** Some puddles give off steam (the whole puddle, from its own roll). */
    private static boolean steaming(long seed, int x, int z, int sector) {
        double share = sector == Level1Areas.RUST ? 0.35 : 0.12;
        return Hash.unit(seed, Math.floorDiv(x, 6), Math.floorDiv(z, 6), 24) < share;
    }

    /** Stacks of 1-3 crates in the north-west corner of some bays (2x2 footprint); many more in the warehouse sector. */
    private int crateHeight(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < PILLAR || mx > PILLAR + 1 || mz < PILLAR || mz > PILLAR + 1) {
            return 0;
        }
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        double share = Level1Areas.sector(seed, x, z) == Level1Areas.GILD ? 0.28 : 0.1;
        if (Hash.unit(seed, cx, cz, 29) >= share || rackBay(seed, cx, cz)
                || exits().distance(seed, x, z) <= CLEARING_RADIUS) {
            return 0;
        }
        return 1 + Hash.range(Hash.of(seed, x, z, 28), 3);
    }

    // ==========================================================================================
    // Storage lockers and ramps down (their shapes: Level1Areas)
    // ==========================================================================================

    /** The last square asked about, per thread (every column of a hallway asks about the same one). */
    private static final class SquareCache<T> {
        long key = Long.MIN_VALUE;
        @Nullable
        T site;
    }

    private final ThreadLocal<SquareCache<Level1Areas.LockerSite>> lockerCache = ThreadLocal.withInitial(SquareCache::new);
    private final ThreadLocal<SquareCache<Level1Areas.RampSite>> rampCache = ThreadLocal.withInitial(SquareCache::new);

    /** The storage-locker hallway containing (x, z), or null. */
    @Nullable
    private Level1Areas.LockerSite lockersAt(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, LOCKER_CELL);
        int cellZ = Math.floorDiv(z, LOCKER_CELL);
        SquareCache<Level1Areas.LockerSite> cache = lockerCache.get();
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        if (cache.key != key) {
            cache.key = key;
            cache.site = planLockers(seed, cellX, cellZ);
        }
        Level1Areas.LockerSite site = cache.site;
        return site != null && site.contains(x, z) ? site : null;
    }

    /** The lower floor (with its ramp) containing (x, z), or null. */
    @Nullable
    private Level1Areas.RampSite rampAt(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, RAMP_CELL);
        int cellZ = Math.floorDiv(z, RAMP_CELL);
        SquareCache<Level1Areas.RampSite> cache = rampCache.get();
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        if (cache.key != key) {
            cache.key = key;
            cache.site = planRamp(seed, cellX, cellZ);
        }
        Level1Areas.RampSite site = cache.site;
        return site != null && site.contains(x, z) ? site : null;
    }

    /**
     * One storage-locker hallway in about half the 160x160 squares: a few spots are tried until
     * one lies wholly in the open halls, clear of exits, safe rooms and anomalies, with open hall
     * in front of its entrance.
     */
    @Nullable
    private Level1Areas.LockerSite planLockers(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 7450);
        if (Hash.unit(h) >= LOCKER_SHARE) {
            return null;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            long a = Hash.mix(h + (attempt + 1) * 0x9E3779B97F4A7C15L);
            boolean alongX = (a & 1) == 0;
            int sizeX = alongX ? Level1Areas.LockerSite.LENGTH : Level1Areas.LockerSite.WIDTH;
            int sizeZ = alongX ? Level1Areas.LockerSite.WIDTH : Level1Areas.LockerSite.LENGTH;
            int minX = cellX * LOCKER_CELL + 8 + Hash.range(Hash.mix(a ^ 1), LOCKER_CELL - 16 - sizeX);
            int minZ = cellZ * LOCKER_CELL + 8 + Hash.range(Hash.mix(a ^ 2), LOCKER_CELL - 16 - sizeZ);
            Level1Areas.LockerSite site = new Level1Areas.LockerSite(minX, minZ, alongX, Hash.mix(a ^ 3));
            int entranceX = alongX ? minX - 1 : minX + 5;
            int entranceZ = alongX ? minZ + 5 : minZ - 1;
            if (areaClear(seed, minX, minZ, site.maxX(), site.maxZ(), 3, false)
                    && hallZone(seed, entranceX, entranceZ) && walkable(seed, entranceX, entranceZ, false)) {
                return site;
            }
        }
        return null;
    }

    /**
     * A ramp down to a lower floor in about half the 192x192 squares: a 5x5-bay block on the
     * pillar grid, wholly in the open halls and clear of everything else (lockers included), with
     * the ramp's top open to the bay before it.
     */
    @Nullable
    private Level1Areas.RampSite planRamp(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 7460);
        if (Hash.unit(h) >= RAMP_SHARE) {
            return null;
        }
        int firstX = firstCell(cellX * RAMP_CELL);
        int firstZ = firstCell(cellZ * RAMP_CELL);
        int cells = cellsIn(cellX * RAMP_CELL, RAMP_CELL);
        for (int attempt = 0; attempt < 6; attempt++) {
            long a = Hash.mix(h + (attempt + 1) * 0x9E3779B97F4A7C15L);
            int gx = firstX + 2 + Hash.range(Hash.mix(a ^ 1), cells - 9);
            int gz = firstZ + 2 + Hash.range(Hash.mix(a ^ 2), cells - 9);
            int minX = gx * GRID;
            int minZ = gz * GRID;
            int max = Level1Areas.RampSite.SIZE - 1;
            if (partition(seed, gx, gz + 2, 1) != OPEN) {
                continue; // the ramp starts right behind this partition: it must be open
            }
            if (areaClear(seed, minX - GRID, minZ, minX + max, minZ + max, 4, true)) {
                return new Level1Areas.RampSite(minX, minZ, Hash.mix(a ^ 3));
            }
        }
        return null;
    }

    /** Open hall with nothing special in it: no exit, safe room, corridor block, anomaly (or lockers). */
    private boolean areaClear(long seed, int x0, int z0, int x1, int z1, int margin, boolean checkLockers) {
        x0 -= margin;
        z0 -= margin;
        x1 += margin;
        z1 += margin;
        for (int x = x0; x <= x1 + 2; x += 3) {
            for (int z = z0; z <= z1 + 2; z += 3) {
                int sx = Math.min(x, x1);
                int sz = Math.min(z, z1);
                if (blocksAnomaly(seed, sx, sz) || !hallZone(seed, sx, sz) || anomalyAt(seed, sx, sz) != null
                        || checkLockers && lockersAt(seed, sx, sz) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    // ==========================================================================================
    // Anomalous areas (their shapes: Level1AnomalyPlan; what they do: world/anomaly/level1)
    // ==========================================================================================

    /** Share of the 96x96 squares that try to hold each kind (if a good spot is found). */
    private static final double CRATE_HALL_SHARE = 0.26;
    private static final double FOG_SHARE = 0.24;
    private static final double PAINTING_SHARE = 0.16;

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        AnomalySite corridor = corridorGrid.at(seed, x, z);
        return corridor != null ? corridor : roomGrid.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        corridorGrid.near(seed, x, z, radius, out);
        roomGrid.near(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        if (Level1AnomalyPlan.CORRUPTED_CORRIDOR.equals(kind)) {
            return corridorGrid.nearest(seed, x, z, site -> kind.equals(site.kind()), 8);
        }
        return roomGrid.nearest(seed, x, z, site -> kind.equals(site.kind()), 40);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level1AnomalyPlan.KINDS;
    }

    /** True if (x, z) belongs to an exit's stairwell or clearing (with a margin) or to a safe room. */
    private boolean blocksAnomaly(long seed, int x, int z) {
        return exits().distance(seed, x, z) <= CLEARING_RADIUS + 2 || safeRoomNear(seed, x, z) != null;
    }

    /**
     * The Corrupted Corridor of one 512x512 square, or null. A straight 8-wide band down the
     * middle of a row (or column) of bays, 138 blocks long. Its doorway is in the side wall near
     * one end and opens onto the row of pillars beside it, so you can always walk up to it. Tries a
     * few spots and gives up if none keeps clear of exits and safe rooms (so it is rare).
     */
    @Nullable
    private AnomalySite planCorridor(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 7100);
        int bays = Level1AnomalyPlan.CORRIDOR_LENGTH / GRID + 1;
        int size = Level1AnomalyPlan.CORRIDOR_CELL;
        for (int attempt = 0; attempt < 16; attempt++) {
            long a = Hash.mix(h + (attempt + 1) * 0x9E3779B97F4A7C15L);
            boolean alongX = (a & 1) == 0;
            boolean forward = (a & 2) == 0; // walking in towards +x / +z
            int acrossSquare = (alongX ? cellZ : cellX) * size;
            int alongSquare = (alongX ? cellX : cellZ) * size;
            int band = firstCell(acrossSquare) + 1 + Hash.range(Hash.mix(a ^ 1), cellsIn(acrossSquare, size) - 2);
            int start = firstCell(alongSquare) + Hash.range(Hash.mix(a ^ 2), cellsIn(alongSquare, size) - bays);
            int alongMin = start * GRID;
            int alongMax = alongMin + Level1AnomalyPlan.CORRIDOR_LENGTH - 1;
            int acrossMin = band * GRID + PILLAR;
            int acrossMax = acrossMin + Level1AnomalyPlan.CORRIDOR_WIDTH - 1;
            Direction facing = alongX ? (forward ? Direction.EAST : Direction.WEST) : (forward ? Direction.SOUTH : Direction.NORTH);
            int anchor = forward ? alongMin : alongMax;
            long salt = Hash.mix(a ^ 3);
            AnomalySite site = alongX
                    ? new AnomalySite(Level1AnomalyPlan.CORRUPTED_CORRIDOR, alongMin, acrossMin, alongMax, acrossMax,
                            anchor, acrossMin, facing.get2DDataValue(), salt)
                    : new AnomalySite(Level1AnomalyPlan.CORRUPTED_CORRIDOR, acrossMin, alongMin, acrossMax, alongMax,
                            acrossMin, anchor, facing.get2DDataValue(), salt);
            if (corridorFits(seed, site)) {
                return site;
            }
        }
        return null;
    }

    private boolean corridorFits(long seed, AnomalySite site) {
        // The doorway opens onto the floor of an open garage hall.
        for (int t = Level1AnomalyPlan.DOOR_T; t <= Level1AnomalyPlan.DOOR_T + 1; t++) {
            int x = Level1AnomalyPlan.corridorX(site, t, -1);
            int z = Level1AnomalyPlan.corridorZ(site, t, -1);
            if (!hallZone(seed, x, z) || !walkable(seed, x, z, false)) {
                return false;
            }
        }
        // Clear of exits and safe rooms: test the lines of columns just outside both long sides and
        // the ends. Anything that matters (a stairwell clearing, a safe room) is wider than the
        // corridor, so if it touched the corridor it would cross one of those lines.
        int width = Level1AnomalyPlan.CORRIDOR_WIDTH;
        for (int t = -1; t <= Level1AnomalyPlan.CORRIDOR_LENGTH; t += 3) {
            for (int a = -1; a <= width; a += width + 1) {
                if (blocksAnomaly(seed, Level1AnomalyPlan.corridorX(site, t, a), Level1AnomalyPlan.corridorZ(site, t, a))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The crate hall, painting room or fog patch of one 96x96 square, or null. The square's roll
     * picks the kind; then a few spots are tried until one suits it.
     */
    @Nullable
    private AnomalySite planRoomSite(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 7200);
        double roll = Hash.unit(h);
        String kind = roll < CRATE_HALL_SHARE ? Level1AnomalyPlan.CRATE_HALL
                : roll < CRATE_HALL_SHARE + FOG_SHARE ? Level1AnomalyPlan.FOG_PUDDLES
                : roll < CRATE_HALL_SHARE + FOG_SHARE + PAINTING_SHARE ? Level1AnomalyPlan.PAINTING_ROOM
                : null;
        if (kind == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            long a = Hash.mix(h + (attempt + 1) * 0x9E3779B97F4A7C15L);
            AnomalySite site = switch (kind) {
                case Level1AnomalyPlan.CRATE_HALL -> crateHallCandidate(seed, cellX, cellZ, a);
                case Level1AnomalyPlan.PAINTING_ROOM -> paintingRoomCandidate(seed, cellX, cellZ, a);
                default -> fogCandidate(cellX, cellZ, a);
            };
            // A painting room also keeps the bay outside its door clear.
            int margin = Level1AnomalyPlan.PAINTING_ROOM.equals(kind) ? GRID : 2;
            if (site != null && anomalyAreaClear(seed, site, margin)) {
                return site;
            }
        }
        return null;
    }

    /** 3x3 bays of pallet racks inside an open garage hall (the Gild sector's crate-rich halls). */
    @Nullable
    private static AnomalySite crateHallCandidate(long seed, int cellX, int cellZ, long a) {
        int size = Level1AnomalyPlan.ROOM_CELL;
        int minX = (firstCell(cellX * size) + Hash.range(Hash.mix(a ^ 1), cellsIn(cellX * size, size) - Level1AnomalyPlan.CRATE_HALL_CELLS)) * GRID;
        int minZ = (firstCell(cellZ * size) + Hash.range(Hash.mix(a ^ 2), cellsIn(cellZ * size, size) - Level1AnomalyPlan.CRATE_HALL_CELLS)) * GRID;
        int maxX = minX + Level1AnomalyPlan.CRATE_HALL_SIZE - 1;
        int maxZ = minZ + Level1AnomalyPlan.CRATE_HALL_SIZE - 1;
        if (!hallZone(seed, minX, minZ) || !hallZone(seed, maxX, minZ) || !hallZone(seed, minX, maxZ) || !hallZone(seed, maxX, maxZ)) {
            return null;
        }
        return new AnomalySite(Level1AnomalyPlan.CRATE_HALL, minX, minZ, maxX, maxZ, (minX + maxX) / 2, (minZ + maxZ) / 2, 0, Hash.mix(a ^ 3));
    }

    /**
     * A small room standing in a bay (from the last column of the pillar row to the end of the
     * bay), closed on all four sides, its only doorway (in the middle of one wall) opening onto
     * the pillar row and the bay beyond. That partition must be open, and that bay open hall.
     */
    @Nullable
    private static AnomalySite paintingRoomCandidate(long seed, int cellX, int cellZ, long a) {
        int size = Level1AnomalyPlan.ROOM_CELL;
        int cx = firstCell(cellX * size) + Hash.range(Hash.mix(a ^ 1), cellsIn(cellX * size, size) - 1);
        int cz = firstCell(cellZ * size) + Hash.range(Hash.mix(a ^ 2), cellsIn(cellZ * size, size) - 1);
        Direction door = Direction.from2DDataValue(Hash.range(Hash.mix(a ^ 4), 4));
        int nx = cx + door.getStepX();
        int nz = cz + door.getStepZ();
        if (!hallZone(seed, cx * GRID, cz * GRID) || !hallZone(seed, cx * GRID + GRID - 1, cz * GRID + GRID - 1)
                || !hallZone(seed, nx * GRID, nz * GRID) || !hallZone(seed, nx * GRID + GRID - 1, nz * GRID + GRID - 1)) {
            return null;
        }
        int doorWall = switch (door) {
            case NORTH -> partition(seed, cx, cz, 0);
            case SOUTH -> partition(seed, cx, cz + 1, 0);
            case WEST -> partition(seed, cx, cz, 1);
            default -> partition(seed, cx + 1, cz, 1);
        };
        if (doorWall != OPEN) {
            return null;
        }
        int minX = cx * GRID + PILLAR - 1;
        int minZ = cz * GRID + PILLAR - 1;
        int room = Level1AnomalyPlan.PAINTING_ROOM_SIZE;
        return new AnomalySite(Level1AnomalyPlan.PAINTING_ROOM, minX, minZ, minX + room - 1, minZ + room - 1,
                minX + 4, minZ + 4, door.get2DDataValue(), Hash.mix(a ^ 3));
    }

    /** A patch of low fog, 18 to 28 blocks across, anywhere in the garage. */
    private static AnomalySite fogCandidate(int cellX, int cellZ, long a) {
        int w = 18 + Hash.range(Hash.mix(a ^ 1), 11);
        int d = 18 + Hash.range(Hash.mix(a ^ 2), 11);
        int minX = cellX * Level1AnomalyPlan.ROOM_CELL + 2 + Hash.range(Hash.mix(a ^ 4), Level1AnomalyPlan.ROOM_CELL - w - 4);
        int minZ = cellZ * Level1AnomalyPlan.ROOM_CELL + 2 + Hash.range(Hash.mix(a ^ 5), Level1AnomalyPlan.ROOM_CELL - d - 4);
        return new AnomalySite(Level1AnomalyPlan.FOG_PUDDLES, minX, minZ, minX + w - 1, minZ + d - 1,
                minX + w / 2, minZ + d / 2, 0, Hash.mix(a ^ 3));
    }

    /** Clear of exits, safe rooms and any Corrupted Corridor (and the bay in front of its doorway). */
    private boolean anomalyAreaClear(long seed, AnomalySite site, int margin) {
        int x0 = site.minX() - margin;
        int z0 = site.minZ() - margin;
        int x1 = site.maxX() + margin;
        int z1 = site.maxZ() + margin;
        for (int x = x0; x <= x1 + 2; x += 3) {
            for (int z = z0; z <= z1 + 2; z += 3) {
                if (blocksAnomaly(seed, Math.min(x, x1), Math.min(z, z1))) {
                    return false;
                }
            }
        }
        boolean[] hit = {false};
        corridorGrid.near(seed, (x0 + x1) / 2, (z0 + z1) / 2, Math.max(x1 - x0, z1 - z0) / 2 + 4, corridor -> {
            if (corridor.minX() - 3 <= x1 && corridor.maxX() + 3 >= x0 && corridor.minZ() - 3 <= z1 && corridor.maxZ() + 3 >= z0) {
                hit[0] = true;
            }
        });
        return !hit[0];
    }

    /**
     * One column of a painting room: a cramped box of concrete standing in the hall (two-thick
     * walls, corner posts, its own low ceiling with solid concrete above), a single doorway, a single
     * tube light, and the crude paintings on the walls and the floor.
     */
    private void fillPaintingRoom(long seed, AnomalySite site, int x, int z, Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int roof = floor + PAINTING_ROOF;
        int lx = x - site.minX();
        int lz = z - site.minZ();
        int far = Level1AnomalyPlan.PAINTING_ROOM_SIZE - 2;
        boolean wallX = lx <= 1 || lx >= far;
        boolean wallZ = lz <= 1 || lz >= far;
        out.set(floor, ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < PILLAR && mz < PILLAR) {
            // The room's outer corner stands on a corner of the hall's pillar: keep the pillar whole.
            out.set(ceiling, concrete);
            fillPillar(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), mx, mz, out);
            return;
        }
        for (int y = roof; y <= ceiling; y++) {
            out.set(y, concrete);
        }
        if (wallX && wallZ) {
            out.set(floor + 1, ModBlocks.GARAGE_PILLAR.get().defaultBlockState());
            for (int y = floor + 2; y < roof; y++) {
                out.set(y, concrete);
            }
            return;
        }
        if (wallX || wallZ) {
            boolean doorway = Level1AnomalyPlan.paintingDoorway(site, lx, lz);
            for (int y = floor + 1; y < roof; y++) {
                if (!(doorway && y <= floor + 3)) {
                    out.set(y, concrete);
                }
            }
            return;
        }
        if (lz == 4 && (lx == 4 || lx == 5)) {
            out.set(roof - 1, Level1Areas.tube(Direction.Axis.X));
        }
        for (int y = floor + 1; y <= floor + 3; y++) {
            BlockState painting = Level1AnomalyPlan.initialPainting(site, x, y, z);
            if (painting != null) {
                out.set(y, painting);
            }
        }
    }

    private static boolean paintingRoomWalkable(AnomalySite site, int x, int z) {
        int lx = x - site.minX();
        int lz = z - site.minZ();
        int far = Level1AnomalyPlan.PAINTING_ROOM_SIZE - 2;
        boolean wallX = lx <= 1 || lx >= far;
        boolean wallZ = lz <= 1 || lz >= far;
        if (wallX && wallZ) {
            return false;
        }
        if (wallX || wallZ) {
            return Level1AnomalyPlan.paintingDoorway(site, lx, lz);
        }
        return true;
    }
}
