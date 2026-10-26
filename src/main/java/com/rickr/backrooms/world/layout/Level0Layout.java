package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.FloorArrowBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 0, "the Lobby": mono-yellow wallpaper, damp carpet, a low drop ceiling and buzzing
 * fluorescent panels.
 *
 * <p><b>Floor plan.</b> A thick-walled maze. Both axes are cut by {@link Bands} into wall bands
 * (1-3 blocks thick) and gaps (rooms/corridors, 3-7 wide; doorways 2-3). Where an X wall band crosses a
 * Z gap we get a wall "segment"; each segment is solid, has a doorway, is missing entirely, or is
 * a half-length stub. Where two wall bands cross we get a "junction", which is solid whenever a
 * wall touches it, and otherwise may stay as a free-standing pillar.
 *
 * <p><b>Tunnel vistas.</b> The doorway position in a segment depends on the row (the gap it spans)
 * and not on which wall it is in, so the doorways of many parallel walls line up. Looking through
 * one, you see a nested series of door frames (the walls have a lintel above each doorway).
 *
 * <p><b>Zones.</b> 48x48 zones are tight mazes, ordinary rooms, or big pillared halls. Light comes
 * in 8x8 ceiling patches; half the level is in "lit" zones where many patches have panels, the
 * other half is nearly dark.
 *
 * <p><b>Exit.</b> One per 512x512 region: a wooden door in a wall opening into a white stairwell
 * with stairs going down. Arrows drawn on walls point toward it... mostly.
 *
 * <p><b>Safe rooms.</b> About 60% of the 128x128 squares hide one somewhere in the maze: a cosy
 * office room (wood panelling under the yellow wallpaper, beige carpet, a warm lamp) behind thick
 * walls with one doorway. A 1-wide strip of plain floor runs all around it, so the doorway always
 * leads back into the maze. See {@link SafeRoomPlan}.
 *
 * <p><b>Anomalies.</b> Five kinds of anomalous area, placed by {@link Level0AnomalyPlan}: the Red
 * Rooms ({@link Level0RedRooms}, with peeling wallpaper and sticky carpet in the maze around them
 * as a warning), Blackout Zones (the maze with no lights, rough walls and fluid on the carpet; see
 * {@link Level0Blackout}), the Manila Room ({@link Level0ManilaRoom}, with a flickering wall to
 * Level 1 in the maze nearby), a pillar hall that rearranges itself ({@link Level0PillarHall}) and a
 * room whose floor is a grid of pits ({@link Level0Pitfalls}). The first four replace the maze in a
 * rectangle (like the front office); blackouts change the maze in place.
 */
public final class Level0Layout extends LevelLayout {
    /** Wall bands running north-south (cut along X) and east-west (cut along Z). */
    private static final Bands X_WALLS = new Bands(32, 1, 3, 3, 7, 101);
    private static final Bands Z_WALLS = new Bands(32, 1, 3, 3, 7, 102);
    /** Columns within this distance of the exit (outside the stairwell) are an open lobby. */
    private static final int CLEARING_RADIUS = 8;
    private static final int ARROW_RANGE = 320;
    private static final int ZONE = 48;
    private static final int OFFICE_GRID = 96;
    /** Safe rooms: a free strip this wide runs all around each one, joining it to the maze. */
    private static final int SAFE_MOAT = 1;
    /** Safe rooms keep at least this far (chessboard distance from their centre) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;

    // What a maze column is.
    private static final int OPEN = 0;
    private static final int WALL = 1;
    private static final int DOORWAY = 2; // walkable, with a lintel above

    // What a wall segment is.
    private static final int SOLID = 0;
    private static final int DOOR = 1;
    private static final int MISSING = 2;
    private static final int STUB_LOW = 3;  // only the half touching the lower-coordinate junction
    private static final int STUB_HIGH = 4; // only the half touching the higher-coordinate junction

    // What a "front office" column is.
    private static final int NO_OFFICE = -1;
    private static final int OFFICE_MOAT = 0;
    private static final int OFFICE_INSIDE = 1;
    private static final int OFFICE_WALL = 2;
    private static final int OFFICE_YELLOW_WALL = 3;
    private static final int OFFICE_DOORWAY = 4;

    /** Where the anomalous areas are (see {@link Level0AnomalyPlan}). */
    private final Level0AnomalyPlan anomalies;

    public Level0Layout() {
        super(4, new ExitLocator(512, 64, 1000));
        this.anomalies = new Level0AnomalyPlan(this);
    }

    // ==========================================================================================
    // Maze
    // ==========================================================================================

    /** 0 = tight maze, 1 = ordinary rooms, 2 = big open hall. */
    private static int zoneKind(long seed, int x, int z) {
        double v = zone(seed, x, z, ZONE, 11);
        return v < 0.33 ? 0 : v < 0.82 ? 1 : 2;
    }

    private static int segmentState(long seed, int wallIndex, int gapIndex, int axis, int repX, int repZ) {
        int kind = zoneKind(seed, repX, repZ);
        double r = Hash.unit(seed, wallIndex, gapIndex, 200 + axis);
        double solid = kind == 0 ? 0.25 : kind == 1 ? 0.12 : 0.03;
        double door = solid + (kind == 0 ? 0.55 : kind == 1 ? 0.55 : 0.12);
        double missing = door + (kind == 0 ? 0.08 : kind == 1 ? 0.18 : 0.75);
        if (r < solid) return SOLID;
        if (r < door) return DOOR;
        if (r < missing) return MISSING;
        return Hash.unit(seed, wallIndex, gapIndex, 202 + axis) < 0.5 ? STUB_LOW : STUB_HIGH;
    }

    /** State of the segment of X wall band {@code xWall} that spans Z gap {@code zGap}. */
    private static int xSegment(long seed, long xWall, long zGap) {
        return segmentState(seed, Bands.index(xWall), Bands.index(zGap), 0, X_WALLS.start(xWall), Z_WALLS.start(zGap));
    }

    /** State of the segment of Z wall band {@code zWall} that spans X gap {@code xGap}. */
    private static int zSegment(long seed, long zWall, long xGap) {
        return segmentState(seed, Bands.index(zWall), Bands.index(xGap), 1, X_WALLS.start(xGap), Z_WALLS.start(zWall));
    }

    /** Column kind from the maze alone (ignores the exit and the front office). */
    private static int mazeKind(long seed, int x, int z, long sx, long sz) {
        boolean wallX = Bands.isBand(sx);
        boolean wallZ = Bands.isBand(sz);
        if (!wallX && !wallZ) {
            return roomPillar(seed, x, z, sx, sz) ? WALL : OPEN;
        }
        if (wallX && !wallZ) {
            return segmentColumn(seed, xSegment(seed, sx, sz), Bands.index(sx), sz, Z_WALLS, z, 0);
        }
        if (!wallX) {
            return segmentColumn(seed, zSegment(seed, sz, sx), Bands.index(sz), sx, X_WALLS, x, 1);
        }
        return junctionSolid(seed, sx, sz) ? WALL : OPEN;
    }

    private static int mazeKind(long seed, int x, int z) {
        return mazeKind(seed, x, z, X_WALLS.span(seed, x), Z_WALLS.span(seed, z));
    }

    /** One column inside a wall segment; {@code along} is the coordinate along the segment. */
    private static int segmentColumn(long seed, int state, int wallIndex, long gap, Bands gapBands, int along, int axis) {
        int length = Bands.length(gap);
        int pos = along - gapBands.start(gap);
        switch (state) {
            case SOLID:
                return WALL;
            case MISSING:
                return OPEN;
            case STUB_LOW:
                return pos < length / 2 ? WALL : OPEN;
            case STUB_HIGH:
                return pos >= length - length / 2 ? WALL : OPEN;
            default: {
                // Doorway. Its width and offset depend on the gap ("row") and on a 48-block lane of
                // walls, not on the individual wall, so neighbouring walls' doorways line up.
                int lane = Math.floorDiv(wallIndex, 32);
                long h = Hash.of(seed, Bands.index(gap), lane, 210 + axis);
                int width = length <= 2 ? length : length >= 5 && Hash.unit(h) < 0.4 ? 3 : 2;
                int offset = Hash.range(Hash.mix(h), length - width + 1);
                return pos >= offset && pos < offset + width ? DOORWAY : WALL;
            }
        }
    }

    /** Where two wall bands cross: solid if any wall reaches it, otherwise maybe a lone pillar. */
    private static boolean junctionSolid(long seed, long sx, long sz) {
        long zBefore = Z_WALLS.span(seed, Z_WALLS.start(sz) - 1);
        long zAfter = Z_WALLS.span(seed, Z_WALLS.end(sz));
        long xBefore = X_WALLS.span(seed, X_WALLS.start(sx) - 1);
        long xAfter = X_WALLS.span(seed, X_WALLS.end(sx));
        if (reachesHighEnd(xSegment(seed, sx, zBefore)) || reachesLowEnd(xSegment(seed, sx, zAfter))
                || reachesHighEnd(zSegment(seed, sz, xBefore)) || reachesLowEnd(zSegment(seed, sz, xAfter))) {
            return true;
        }
        int kind = zoneKind(seed, X_WALLS.start(sx), Z_WALLS.start(sz));
        double pillarChance = kind == 0 ? 0.1 : kind == 1 ? 0.25 : 0.7;
        return Hash.unit(seed, Bands.index(sx), Bands.index(sz), 220) < pillarChance;
    }

    private static boolean reachesHighEnd(int state) {
        return state == SOLID || state == DOOR || state == STUB_HIGH;
    }

    private static boolean reachesLowEnd(int state) {
        return state == SOLID || state == DOOR || state == STUB_LOW;
    }

    /** Big rooms (6x6 and up) sometimes get a 2x2 pillar in the middle. */
    private static boolean roomPillar(long seed, int x, int z, long sx, long sz) {
        int lenX = Bands.length(sx);
        int lenZ = Bands.length(sz);
        if (lenX < 6 || lenZ < 6 || Hash.unit(seed, Bands.index(sx), Bands.index(sz), 230) >= 0.45) {
            return false;
        }
        int px = X_WALLS.start(sx) + lenX / 2 - 1;
        int pz = Z_WALLS.start(sz) + lenZ / 2 - 1;
        return x >= px && x <= px + 1 && z >= pz && z <= pz + 1;
    }

    // ==========================================================================================
    // Front office: a rare large grey room with red alarm lamps and a drawn "fake exit"
    // ==========================================================================================

    /**
     * The office of a 96x96 grid cell, packed as (x0 offset, z0 offset, width, depth), or 0 if
     * that cell has no office.
     */
    private static long officeRect(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 300);
        if (Hash.unit(h) >= 0.2) {
            return 0;
        }
        int width = 14 + Hash.range(Hash.mix(h ^ 1), 7);
        int depth = 10 + Hash.range(Hash.mix(h ^ 2), 6);
        int x0 = 6 + Hash.range(Hash.mix(h ^ 3), OFFICE_GRID - 12 - width);
        int z0 = 6 + Hash.range(Hash.mix(h ^ 4), OFFICE_GRID - 12 - depth);
        return 1L << 40 | (long) x0 << 24 | (long) z0 << 16 | (long) width << 8 | depth;
    }

    private int officeAt(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, OFFICE_GRID);
        int cellZ = Math.floorDiv(z, OFFICE_GRID);
        long rect = officeRect(seed, cellX, cellZ);
        if (rect == 0) {
            return NO_OFFICE;
        }
        int width = (int) (rect >>> 8) & 0xFF;
        int depth = (int) rect & 0xFF;
        int rx = x - (cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF));
        int rz = z - (cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF));
        if (rx < -2 || rx >= width + 2 || rz < -2 || rz >= depth + 2) {
            return NO_OFFICE;
        }
        // Never build an office near the exit stairwell. Measured from the office centre so every
        // column of the same office gets the same answer.
        int centerX = x - rx + width / 2;
        int centerZ = z - rz + depth / 2;
        int[] exit = exits().nearest(seed, centerX, centerZ);
        if (Math.max(Math.abs(exit[0] - centerX), Math.abs(exit[1] - centerZ)) < 48) {
            return NO_OFFICE;
        }
        return officeKind(rx, rz, width, depth);
    }

    private static int officeKind(int rx, int rz, int width, int depth) {
        if (rx < 0 || rx >= width || rz < 0 || rz >= depth) {
            return OFFICE_MOAT; // a 2-wide walkway all around, so the room is always reachable
        }
        if (rx > 0 && rx < width - 1 && rz > 0 && rz < depth - 1) {
            return OFFICE_INSIDE;
        }
        boolean southDoor = rz == depth - 1 && (rx == width / 2 - 1 || rx == width / 2);
        boolean sideDoor = (rx == 0 || rx == width - 1) && (rz == depth / 2 - 1 || rz == depth / 2);
        if (southDoor || sideDoor) {
            return OFFICE_DOORWAY;
        }
        return rz == 0 ? OFFICE_YELLOW_WALL : OFFICE_WALL;
    }

    private void fillOffice(long seed, int x, int z, int kind, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        BlockState grey = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
        out.set(ceiling, ModBlocks.CEILING_TILE.get().defaultBlockState());

        int cellX = Math.floorDiv(x, OFFICE_GRID);
        int cellZ = Math.floorDiv(z, OFFICE_GRID);
        long rect = officeRect(seed, cellX, cellZ);
        int width = (int) (rect >>> 8) & 0xFF;
        int depth = (int) rect & 0xFF;
        int rx = x - (cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF));
        int rz = z - (cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF));

        switch (kind) {
            case OFFICE_WALL -> {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, grey);
                }
            }
            case OFFICE_YELLOW_WALL -> {
                // The one yellow wall, with the drawn door and window facing into the room.
                out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                }
                if (rx == width / 2) {
                    BlockState door = ModBlocks.WALL_DOOR_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, Direction.SOUTH);
                    out.set(floor + 1, door.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.LOWER));
                    out.set(floor + 2, door.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.UPPER));
                } else if (rx == width / 2 + 2) {
                    out.set(floor + 2, ModBlocks.WALL_WINDOW_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, Direction.SOUTH));
                }
            }
            case OFFICE_DOORWAY -> out.set(ceiling - 1, grey);
            case OFFICE_INSIDE -> {
                Direction lamp = officeLamp(rx, rz, width, depth);
                if (lamp != null) {
                    out.set(floor + 3, ModBlocks.RED_ALARM_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, lamp));
                }
                int cx = width / 2;
                int cz = depth / 2;
                if (rx == cx && rz == cz) {
                    out.set(ceiling, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState()); // the one small white lamp
                }
                if (Math.abs(rx - cx) <= 1 && Math.abs(rz - cz) <= 1) {
                    out.set(floor, ModBlocks.RUG.get().defaultBlockState().setValue(RugBlock.PART, RugBlock.part(rx - cx, rz - cz)));
                }
                if (officeCrate(seed, x, z, rx, rz, width)) {
                    out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
                }
            }
            default -> {
                // OFFICE_MOAT: plain dark carpet.
            }
        }
    }

    /** Red alarm lamps every 4 blocks on the grey inner walls; returns the lamp's facing or null. */
    @Nullable
    private static Direction officeLamp(int rx, int rz, int width, int depth) {
        if (rx == 1 && Math.floorMod(rz, 4) == 2 && officeKind(0, rz, width, depth) == OFFICE_WALL) return Direction.EAST;
        if (rx == width - 2 && Math.floorMod(rz, 4) == 2 && officeKind(width - 1, rz, width, depth) == OFFICE_WALL) return Direction.WEST;
        if (rz == depth - 2 && Math.floorMod(rx, 4) == 2 && officeKind(rx, depth - 1, width, depth) == OFFICE_WALL) return Direction.NORTH;
        return null;
    }

    /** A crate in each of the two corners by the yellow wall, sometimes. */
    private static boolean officeCrate(long seed, int x, int z, int rx, int rz, int width) {
        return rz == 1 && (rx == 1 || rx == width - 2) && Hash.unit(seed, x, z, 310) < 0.6;
    }

    // ==========================================================================================
    // Safe rooms
    // ==========================================================================================

    /**
     * The safe room whose footprint (the room plus the free strip around it) contains (x, z), or
     * null. Each 128x128 square has at most one, so only one square needs checking.
     */
    @Nullable
    private SafeRoomPlan safeRoomNear(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomPlan(seed, Math.floorDiv(x, SafeRoomPlan.CELL), Math.floorDiv(z, SafeRoomPlan.CELL));
        if (plan == null || !plan.near(x, z, SAFE_MOAT) || !safeRoomAllowed(seed, plan)) {
            return null;
        }
        return plan;
    }

    /** Where the safe room of a 128x128 square would go (before checking it's allowed there), or null. */
    @Nullable
    private static SafeRoomPlan safeRoomPlan(long seed, int cellX, int cellZ) {
        // 7x7 or 9x9 inside, with walls two blocks thick (11 or 13 across).
        int size = Hash.unit(seed, cellX, cellZ, 401) < 0.5 ? 11 : 13;
        return SafeRoomPlan.jittered(seed, cellX, cellZ, 400, size, SAFE_MOAT, 2);
    }

    /** Never near an exit stairwell, never overlapping a front office (or the walkway around it). */
    private boolean safeRoomAllowed(long seed, SafeRoomPlan plan) {
        int[] exit = exits().nearest(seed, plan.centerX(), plan.centerZ());
        if (Math.max(Math.abs(exit[0] - plan.centerX()), Math.abs(exit[1] - plan.centerZ())) < SAFE_EXIT_DISTANCE) {
            return false;
        }
        int fx0 = plan.x0 - SAFE_MOAT;
        int fz0 = plan.z0 - SAFE_MOAT;
        int fx1 = plan.x0 + plan.sizeX - 1 + SAFE_MOAT;
        int fz1 = plan.z0 + plan.sizeZ - 1 + SAFE_MOAT;
        for (int cellX = Math.floorDiv(fx0, OFFICE_GRID); cellX <= Math.floorDiv(fx1, OFFICE_GRID); cellX++) {
            for (int cellZ = Math.floorDiv(fz0, OFFICE_GRID); cellZ <= Math.floorDiv(fz1, OFFICE_GRID); cellZ++) {
                long rect = officeRect(seed, cellX, cellZ);
                if (rect == 0) {
                    continue;
                }
                // The office plus its 2-wide walkway, as in officeAt.
                int ox0 = cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF) - 2;
                int oz0 = cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF) - 2;
                int ox1 = ox0 + ((int) (rect >>> 8) & 0xFF) + 3;
                int oz1 = oz0 + ((int) rect & 0xFF) + 3;
                if (ox0 <= fx1 && ox1 >= fx0 && oz0 <= fz1 && oz1 >= fz0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Everything inside a safe room, walls and doorway included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomNear(seed, x, z);
        return plan != null && plan.contains(x, z);
    }

    private void fillSafeRoom(SafeRoomPlan plan, int x, int z, Column out) {
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        if (!plan.contains(x, z)) {
            // The free strip around the room: plain carpet, no lights.
            out.set(floorY(), ModBlocks.LOBBY_CARPET.get().defaultBlockState());
            out.set(ceilingY(), tile);
            return;
        }
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        plan.fill(x, z, floorY(), ceilingY(), new SafeRoomPlan.Style(
                ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                tile,
                ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState(),
                wallpaper,
                ModBlocks.WOOD_FLOOR.get().defaultBlockState(), // wood panelling below the dado line
                wallpaper,
                null), out);
    }

    // ==========================================================================================
    // Anomalies (where they are: Level0AnomalyPlan; what they look like: the Level0* plan classes)
    // ==========================================================================================

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return anomalies.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        anomalies.near(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return anomalies.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level0AnomalyPlan.KINDS;
    }

    /** Nobody arrives (or respawns) inside an anomalous structure: you have to walk in. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        return isWalkable(seed, x, z) && anomalies.structureAt(seed, x, z) == null;
    }

    /**
     * True if a rectangle is clear of exit stairwells (and the lobby around them), safe rooms (with
     * their free strip) and front offices (with their walkway). The anomaly planner only puts sites
     * where this holds. Front offices count even where they are switched off near an exit, to keep
     * this simple and safe.
     */
    boolean rectFree(long seed, int x0, int z0, int x1, int z1) {
        int keep = CLEARING_RADIUS + 2;
        int[][] probes = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {(x0 + x1) / 2, (z0 + z1) / 2}};
        for (int[] probe : probes) {
            int[] exit = exits().nearest(seed, probe[0], probe[1]);
            if (exit[0] >= x0 - keep && exit[0] <= x1 + keep && exit[1] >= z0 - keep && exit[1] <= z1 + keep) {
                return false;
            }
        }
        for (int cellX = Math.floorDiv(x0, SafeRoomPlan.CELL); cellX <= Math.floorDiv(x1, SafeRoomPlan.CELL); cellX++) {
            for (int cellZ = Math.floorDiv(z0, SafeRoomPlan.CELL); cellZ <= Math.floorDiv(z1, SafeRoomPlan.CELL); cellZ++) {
                SafeRoomPlan plan = safeRoomPlan(seed, cellX, cellZ);
                if (plan != null && safeRoomAllowed(seed, plan)
                        && plan.x0 - SAFE_MOAT <= x1 && plan.x0 + plan.sizeX - 1 + SAFE_MOAT >= x0
                        && plan.z0 - SAFE_MOAT <= z1 && plan.z0 + plan.sizeZ - 1 + SAFE_MOAT >= z0) {
                    return false;
                }
            }
        }
        for (int cellX = Math.floorDiv(x0, OFFICE_GRID); cellX <= Math.floorDiv(x1, OFFICE_GRID); cellX++) {
            for (int cellZ = Math.floorDiv(z0, OFFICE_GRID); cellZ <= Math.floorDiv(z1, OFFICE_GRID); cellZ++) {
                long rect = officeRect(seed, cellX, cellZ);
                if (rect == 0) {
                    continue;
                }
                // The office plus its 2-wide walkway, as in officeAt.
                int ox0 = cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF) - 2;
                int oz0 = cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF) - 2;
                int ox1 = ox0 + ((int) (rect >>> 8) & 0xFF) + 3;
                int oz1 = oz0 + ((int) rect & 0xFF) + 3;
                if (ox0 <= x1 && ox1 >= x0 && oz0 <= z1 && oz1 >= z0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A maze wall outside exits, offices and safe rooms (never asks the anomaly plan: the planner uses it). */
    boolean plainMazeWall(long seed, int x, int z) {
        return exits().distance(seed, x, z) > CLEARING_RADIUS && officeAt(seed, x, z) == NO_OFFICE
                && mazeKind(seed, x, z) == WALL && safeRoomNear(seed, x, z) == null;
    }

    /** Open maze floor (or a doorway) outside exits, offices and safe rooms. */
    boolean plainMazeOpen(long seed, int x, int z) {
        return exits().distance(seed, x, z) > CLEARING_RADIUS && officeAt(seed, x, z) == NO_OFFICE
                && mazeKind(seed, x, z) != WALL && safeRoomNear(seed, x, z) == null;
    }

    /**
     * Does the spot suit an anomaly that likes {@code zone}: 0 tight maze, 1 ordinary rooms, 2 big
     * halls (see {@link #zoneKind}), 3 the unlit half of the level.
     */
    boolean zonePrefers(long seed, int x, int z, int zone) {
        return zone == 3 ? isUnlitZone(seed, x, z) : zoneKind(seed, x, z) == zone;
    }

    /** True in the half of the level where hardly any ceiling panel works (see {@link #ceilingLight}). */
    public boolean isUnlitZone(long seed, int x, int z) {
        return zone(seed, x, z, 32, 13) >= 0.5;
    }

    private void fillStructure(AnomalySite site, int x, int z, Column out) {
        switch (site.kind()) {
            case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.fill(site, x, z, floorY(), ceilingY(), out);
            case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.fill(site, x, z, floorY(), ceilingY(), out);
            case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.fill(site, x, z, floorY(), ceilingY(), out);
            default -> Level0PillarHall.fill(site, x, z, floorY(), ceilingY(), out);
        }
    }

    private static boolean structureSolid(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.isSolid(site, x, z);
            case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.isSolid(site, x, z);
            case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.isSolid(site, x, z);
            default -> Level0PillarHall.isSolid(site, x, z);
        };
    }

    private static boolean structureWalkable(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.isWalkable(site, x, z);
            case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.isWalkable(site, x, z);
            case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.isWalkable(site, x, z);
            default -> Level0PillarHall.isWalkable(site, x, z);
        };
    }

    /**
     * Warning signs of the Red Rooms in the maze around them: how close (blocks) column (x, z) is to
     * a Red Rooms site, or -1 if it is out of range (see {@link Level0AnomalyPlan#RED_RING}).
     */
    private double redRoomsDistance(long seed, int x, int z) {
        AnomalySite red = anomalies.redRoomsOfSquare(seed, x, z);
        if (red == null) {
            return -1;
        }
        double d = red.distanceTo(x + 0.5, z + 0.5);
        return d <= Level0AnomalyPlan.RED_RING ? d : -1;
    }

    // ==========================================================================================
    // Column queries shared by fillColumn / isWalkable / decals / lights
    // ==========================================================================================

    /** True if the column is solid at eye level (walls of the maze, office or stairwell). */
    private boolean solidAtEyeLevel(long seed, int x, int z) {
        int d = exits().distance(seed, x, z);
        if (d <= STAIRWELL_RADIUS) return true;
        if (d <= CLEARING_RADIUS) return false;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) return safe.role(x, z) == SafeRoomPlan.WALL;
        int office = officeAt(seed, x, z);
        if (office != NO_OFFICE) return office == OFFICE_WALL || office == OFFICE_YELLOW_WALL;
        AnomalySite site = anomalies.structureAt(seed, x, z);
        if (site != null) return structureSolid(site, x, z);
        return mazeKind(seed, x, z) == WALL;
    }

    /**
     * A plain yellow maze wall column (where the door drawing may go): not part of an anomaly, not
     * rough blackout wall, not peeling wallpaper near the Red Rooms, not the Manila Room's exit.
     */
    private boolean isMazeWall(long seed, int x, int z) {
        return plainMazeWall(seed, x, z) && anomalies.structureAt(seed, x, z) == null
                && anomalies.blackoutAt(seed, x, z) == null && redRoomsDistance(seed, x, z) < 0
                && !anomalies.isManilaExit(seed, x, z);
    }

    /** A plain maze floor column (where ceiling panels may hang). Never inside a blackout zone. */
    private boolean isMazeRoom(long seed, int x, int z) {
        return exits().distance(seed, x, z) > CLEARING_RADIUS && officeAt(seed, x, z) == NO_OFFICE
                && mazeKind(seed, x, z) == OPEN && safeRoomNear(seed, x, z) == null
                && anomalies.structureAt(seed, x, z) == null && anomalies.blackoutAt(seed, x, z) == null;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int d = exits().distance(seed, x, z);
        if (d <= STAIRWELL_RADIUS) return false;
        if (d <= CLEARING_RADIUS) return true;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            return !safe.contains(x, z) || safe.isWalkable(x, z);
        }
        int office = officeAt(seed, x, z);
        if (office != NO_OFFICE) {
            if (office == OFFICE_WALL || office == OFFICE_YELLOW_WALL) return false;
            if (office == OFFICE_INSIDE) {
                int cellX = Math.floorDiv(x, OFFICE_GRID);
                int cellZ = Math.floorDiv(z, OFFICE_GRID);
                long rect = officeRect(seed, cellX, cellZ);
                int width = (int) (rect >>> 8) & 0xFF;
                int rx = x - (cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF));
                int rz = z - (cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF));
                return !officeCrate(seed, x, z, rx, rz, width);
            }
            return true;
        }
        AnomalySite site = anomalies.structureAt(seed, x, z);
        if (site != null) {
            return structureWalkable(site, x, z);
        }
        long sx = X_WALLS.span(seed, x);
        long sz = Z_WALLS.span(seed, z);
        int kind = mazeKind(seed, x, z, sx, sz);
        if (kind == WALL) return false;
        if (kind == DOORWAY) return true;
        return crateHeight(seed, x, z, sx, sz) == 0 && backpackFacing(seed, x, z, sx, sz) == null;
    }

    // ==========================================================================================
    // Generation
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        BlockState baseboard = ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState();
        BlockState carpet = ModBlocks.LOBBY_CARPET.get().defaultBlockState();
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int exitDistance = Math.max(Math.abs(dx), Math.abs(dz));

        if (stairwell(seed, dx, dz, x - dx, z - dz, wallpaper, baseboard, true, out)) {
            return;
        }
        if (exitDistance <= CLEARING_RADIUS) {
            // Open, well-lit lobby around the stairwell so the door is easy to spot.
            out.set(floor, carpet);
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            boolean panel = (ax == 7 && (az == 3 || az == 4)) || (az == 7 && (ax == 3 || ax == 4));
            out.set(ceiling, panel ? ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState() : tile);
            return;
        }
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            fillSafeRoom(safe, x, z, out);
            return;
        }
        int office = officeAt(seed, x, z);
        if (office != NO_OFFICE) {
            fillOffice(seed, x, z, office, out);
            return;
        }
        AnomalySite structure = anomalies.structureAt(seed, x, z);
        if (structure != null) {
            fillStructure(structure, x, z, out);
            return;
        }

        long sx = X_WALLS.span(seed, x);
        long sz = Z_WALLS.span(seed, z);
        int kind = mazeKind(seed, x, z, sx, sz);
        // A blackout zone is the same maze with no lights, rough dark walls and fluid on the carpet.
        boolean blackout = anomalies.blackoutAt(seed, x, z) != null;
        // Near the Red Rooms the wallpaper peels off crimson and the carpet turns sticky, more and
        // more of it the closer you get.
        double red = blackout ? -1 : redRoomsDistance(seed, x, z);
        BlockState rough = ModBlocks.BLACKOUT_WALLPAPER.get().defaultBlockState();
        BlockState fluid = ModBlocks.CARPET_FLUID.get().defaultBlockState();
        out.set(ceiling, tile);
        if (kind == WALL) {
            out.set(floor, carpet);
            if (anomalies.isManilaExit(seed, x, z)) {
                // The flickering wall the Manila Room's volunteers write about: walk into it to reach Level 1.
                for (int y = floor + 1; y <= floor + 3; y++) {
                    out.set(y, ModBlocks.FLICKERING_WALL.get().defaultBlockState());
                }
                for (int y = floor + 4; y < ceiling; y++) {
                    out.set(y, wallpaper);
                }
                return;
            }
            if (blackout) {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, rough);
                }
                return;
            }
            boolean peeling = red >= 0 && Hash.unit(seed, x, z, 3290) < 1.0 - red / (Level0AnomalyPlan.RED_RING + 1);
            out.set(floor + 1, baseboard);
            for (int y = floor + 2; y < ceiling; y++) {
                out.set(y, peeling ? ModBlocks.PEELING_WALLPAPER.get().defaultBlockState() : wallpaper);
            }
            if (!peeling) {
                drawDecals(seed, x, z, out);
            }
            return;
        }
        boolean sticky = red >= 0 && Hash.unit(seed, x, z, 3291) < 1.0 - red / 7.0;
        BlockState stickyCarpet = ModBlocks.STICKY_CARPET.get().defaultBlockState();
        if (kind == DOORWAY) {
            out.set(floor, sticky ? stickyCarpet : carpet);
            out.set(ceiling - 1, blackout ? rough : wallpaper); // lintel: this is what frames the "tunnel" view
            if (blackout) {
                out.set(floor + 1, fluid);
            }
            return;
        }

        out.set(floor, sticky ? stickyCarpet : floorBlock(seed, x, z, sx, sz, carpet));
        int crates = crateHeight(seed, x, z, sx, sz);
        for (int i = 0; i < crates; i++) {
            out.set(floor + 1 + i, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
        }
        Direction backpack = backpackFacing(seed, x, z, sx, sz);
        if (backpack != null) {
            out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, backpack));
        }
        if (blackout) {
            if (crates == 0 && backpack == null) {
                out.set(floor + 1, fluid); // ankle-deep, murky
            }
            return; // and not a single working lamp
        }
        BlockState light = ceilingLight(seed, x, z);
        if (light != null) {
            out.set(ceiling, light);
        }
    }

    /** Carpet, a piece of a round rug, or (rarely) a scuffed arrow painted on the carpet. */
    private BlockState floorBlock(long seed, int x, int z, long sx, long sz, BlockState carpet) {
        int lenX = Bands.length(sx);
        int lenZ = Bands.length(sz);
        int ix = Bands.index(sx);
        int iz = Bands.index(sz);
        int x0 = X_WALLS.start(sx);
        int z0 = Z_WALLS.start(sz);
        // Rugs sit in the middle of small-to-medium rooms (big rooms may have a centre pillar).
        if (lenX >= 3 && lenZ >= 3 && lenX < 6 && lenZ < 6 && Hash.unit(seed, ix, iz, 240) < 0.07) {
            int ox = x - (x0 + (lenX - 1) / 2);
            int oz = z - (z0 + (lenZ - 1) / 2);
            if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                return ModBlocks.RUG.get().defaultBlockState().setValue(RugBlock.PART, RugBlock.part(ox, oz));
            }
            return carpet;
        }
        if (x == x0 + lenX / 2 && z == z0 + lenZ / 2 && Hash.unit(seed, ix, iz, 241) < 0.04) {
            int[] exit = exits().nearest(seed, x, z);
            int ex = exit[0] - x;
            int ez = exit[1] - z;
            if (Math.abs(ex) <= ARROW_RANGE && Math.abs(ez) <= ARROW_RANGE) {
                Direction direction = Math.abs(ex) > Math.abs(ez)
                        ? (ex > 0 ? Direction.EAST : Direction.WEST)
                        : (ez > 0 ? Direction.SOUTH : Direction.NORTH);
                if (Hash.unit(seed, ix, iz, 242) < 0.2) {
                    direction = Direction.from2DDataValue(Hash.range(Hash.of(seed, ix, iz, 243), 4));
                }
                return ModBlocks.LOBBY_CARPET_ARROW.get().defaultBlockState().setValue(FloorArrowBlock.FACING, direction);
            }
        }
        return carpet;
    }

    /**
     * Crates hide in the back corner of dead-end rooms (exactly one way in). Returns how many are
     * stacked on this column (0, 1 or 2).
     */
    private static int crateHeight(long seed, int x, int z, long sx, long sz) {
        int ix = Bands.index(sx);
        int iz = Bands.index(sz);
        if (Hash.unit(seed, ix, iz, 250) >= 0.55) {
            return 0;
        }
        int x0 = X_WALLS.start(sx);
        int x1 = X_WALLS.end(sx) - 1;
        int z0 = Z_WALLS.start(sz);
        int z1 = Z_WALLS.end(sz) - 1;
        boolean west = xSegment(seed, X_WALLS.span(seed, x0 - 1), sz) != SOLID;
        boolean east = xSegment(seed, X_WALLS.span(seed, x1 + 1), sz) != SOLID;
        boolean north = zSegment(seed, Z_WALLS.span(seed, z0 - 1), sx) != SOLID;
        boolean south = zSegment(seed, Z_WALLS.span(seed, z1 + 1), sx) != SOLID;
        int ways = (west ? 1 : 0) + (east ? 1 : 0) + (north ? 1 : 0) + (south ? 1 : 0);
        if (ways != 1) {
            return 0;
        }
        boolean pick = Hash.unit(seed, ix, iz, 251) < 0.5;
        int cx;
        int cz;
        if (west || east) {
            cx = west ? x1 : x0;          // far from the opening
            cz = pick ? z0 : z1;
        } else {
            cz = north ? z1 : z0;
            cx = pick ? x0 : x1;
        }
        if (x != cx || z != cz) {
            return 0;
        }
        return Hash.unit(seed, ix, iz, 252) < 0.3 ? 2 : 1;
    }

    /**
     * Now and then someone's backpack lies on the floor of a room: rare (about one per 400 floor
     * blocks), at most one per room. Returns the way it faces, or null for no backpack here.
     */
    @Nullable
    private static Direction backpackFacing(long seed, int x, int z, long sx, long sz) {
        if (Bands.isBand(sx) || Bands.isBand(sz)) {
            return null; // rooms only, not the gaps in walls
        }
        long h = Hash.of(seed, Bands.index(sx), Bands.index(sz), 255);
        if (Hash.unit(h) >= 0.06) {
            return null;
        }
        int bx = X_WALLS.start(sx) + Hash.range(Hash.mix(h ^ 1), Bands.length(sx));
        int bz = Z_WALLS.start(sz) + Hash.range(Hash.mix(h ^ 2), Bands.length(sz));
        if (x != bx || z != bz || roomPillar(seed, x, z, sx, sz) || crateHeight(seed, x, z, sx, sz) > 0) {
            return null;
        }
        return Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 3), 4));
    }

    /**
     * Ceiling panels: 8x8 patches, each with four 1x2 panels 4 blocks apart. In "lit" zones 40% of
     * patches are on; in dark zones almost none, but dead panels still show under the flashlight.
     * A panel is dropped whenever either of its two blocks would hang over a wall.
     */
    @Nullable
    private BlockState ceilingLight(long seed, int x, int z) {
        int px = Math.floorDiv(x, 8);
        int pz = Math.floorDiv(z, 8);
        boolean alongZ = Hash.unit(seed, px, pz, 19) < 0.5;
        int across = alongZ ? Math.floorMod(x, 8) : Math.floorMod(z, 8);
        int along = alongZ ? Math.floorMod(z, 8) : Math.floorMod(x, 8);
        if (across != 2 && across != 6) return null;
        if (along != 1 && along != 2 && along != 5 && along != 6) return null;
        int partnerStep = along == 1 || along == 5 ? 1 : -1;
        int partnerX = alongZ ? x : x + partnerStep;
        int partnerZ = alongZ ? z + partnerStep : z;
        if (!isMazeRoom(seed, partnerX, partnerZ)) return null;

        boolean litZone = zone(seed, x, z, 32, 13) < 0.5;
        boolean patchLit = Hash.unit(seed, px, pz, 17) < (litZone ? 0.4 : 0.03);
        int panel = (across == 6 ? 2 : 0) + (along >= 5 ? 1 : 0);
        boolean dead = Hash.unit(seed, px * 4 + panel, pz, 21) < 0.08;
        if (patchLit) {
            return (dead ? ModBlocks.FLUORESCENT_LIGHT_OFF : ModBlocks.FLUORESCENT_LIGHT).get().defaultBlockState();
        }
        return Hash.unit(seed, px, pz, 18) < 0.35 ? ModBlocks.FLUORESCENT_LIGHT_OFF.get().defaultBlockState() : null;
    }

    // ==========================================================================================
    // Wall drawings
    // ==========================================================================================

    private void drawDecals(long seed, int x, int z, Column out) {
        int floor = floorY();
        Direction door = doorDrawingFace(seed, x, z);
        if (door != null) {
            BlockState state = ModBlocks.WALL_DOOR_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, door);
            out.set(floor + 1, state.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, state.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.UPPER));
            return;
        }
        Direction window = windowFace(seed, x, z);
        if (window != null) {
            out.set(floor + 2, ModBlocks.WALL_WINDOW_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, window));
            return;
        }
        if (Hash.unit(seed, x, z, 270) >= 0.03) {
            return;
        }
        Direction face = openFace(seed, x, z, 271);
        if (face == null) {
            return;
        }
        int[] exit = exits().nearest(seed, x, z);
        int ex = exit[0] - x;
        int ez = exit[1] - z;
        if (Math.abs(ex) <= ARROW_RANGE && Math.abs(ez) <= ARROW_RANGE && Hash.unit(seed, x, z, 272) < 0.7) {
            // Someone facing this wall has FACING behind them; their right hand points FACING's counter-clockwise.
            Direction right = face.getCounterClockWise();
            boolean pointsRight = ex * right.getStepX() + ez * right.getStepZ() >= 0;
            if (Hash.unit(seed, x, z, 273) < 0.25) {
                pointsRight = !pointsRight; // a lie
            }
            BlockState arrow = (pointsRight ? ModBlocks.WALL_ARROW_RIGHT : ModBlocks.WALL_ARROW_LEFT).get().defaultBlockState();
            out.set(floor + 2, arrow.setValue(WallDecalBlock.FACING, face));
        } else {
            int y = Hash.unit(seed, x, z, 274) < 0.5 ? floor + 2 : floor + 3;
            out.set(y, ModBlocks.WALL_HANDPRINT.get().defaultBlockState().setValue(WallDecalBlock.FACING, face));
        }
    }

    /** A very rare "fake exit" door drawn on a maze wall; returns the face it is drawn on. */
    @Nullable
    private Direction doorDrawingFace(long seed, int x, int z) {
        if (Hash.unit(seed, x, z, 260) >= 0.004 || !isMazeWall(seed, x, z)) {
            return null;
        }
        return openFace(seed, x, z, 261);
    }

    /** The window doodle goes one block to the right of a door drawing (as seen by the viewer). */
    @Nullable
    private Direction windowFace(long seed, int x, int z) {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            Direction toDoor = face.getClockWise();
            int doorX = x + toDoor.getStepX();
            int doorZ = z + toDoor.getStepZ();
            if (Hash.unit(seed, doorX, doorZ, 260) < 0.004 && doorDrawingFace(seed, doorX, doorZ) == face
                    && !solidAtEyeLevel(seed, x + face.getStepX(), z + face.getStepZ())) {
                return face;
            }
        }
        return null;
    }

    /** A side of this wall column that looks into open space, picked in a random order. */
    @Nullable
    private Direction openFace(long seed, int x, int z, int salt) {
        int start = Hash.range(Hash.of(seed, x, z, salt), 4);
        for (int i = 0; i < 4; i++) {
            Direction face = Direction.from2DDataValue((start + i) & 3);
            if (!solidAtEyeLevel(seed, x + face.getStepX(), z + face.getStepZ())) {
                return face;
            }
        }
        return null;
    }
}
