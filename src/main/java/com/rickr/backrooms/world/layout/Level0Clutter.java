package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level39FurnitureBlock;
import com.rickr.backrooms.registry.Level0SetPieceBlocks;
import com.rickr.backrooms.registry.Level39HouseBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Abandoned furniture in Level 0's big anomalous areas, like the film's tapes of the Lobby: old
 * dressers, shelves, side tables and armchairs, filing cabinets and cardboard boxes, left in
 * ragged piles, a few things upside down on top of others, a ceiling tile or two fallen beside
 * them. A pile only goes where its whole 5 x 5 surroundings are open floor (asked from the area's
 * own pure floor plan), so it never blocks a doorway, a walkway, a ladder or a drawn door.
 * Applied on top of the area's own blocks by {@link Level0AnomalyPlan#fill}.
 */
public final class Level0Clutter {
    /** One pile per this many columns of the site, up to {@link #MAX_PILES}. */
    private static final int COLUMNS_PER_PILE = 260;
    private static final int MAX_PILES = 7;
    private static final int TRIES_PER_PILE = 6;

    /** Pile cells of each site seen: world column -> code (see {@link #piece}). Cleared when large. */
    private static final Map<AnomalySite, Map<Long, Integer>> PILES = new ConcurrentHashMap<>();

    /** Asks whether column (x, z) of the site's ground floor is open floor. */
    @FunctionalInterface
    interface Walkable {
        boolean at(int x, int z);
    }

    private Level0Clutter() {}

    /** Writes the pile piece (if any) of column (x, z) on the floor at {@code floor}. */
    static void overlay(AnomalySite site, int x, int z, int floor, Walkable walkable, LevelLayout.Column out) {
        Map<Long, Integer> piles = PILES.get(site);
        if (piles == null) {
            if (PILES.size() > 64) {
                PILES.clear();
            }
            piles = plan(site, walkable);
            PILES.put(site, piles);
        }
        Integer code = piles.get(key(x, z));
        if (code != null) {
            piece(code, floor, out);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Pile cells: 3 x 2 footprints in open floor, each column a piece code. */
    private static Map<Long, Integer> plan(AnomalySite site, Walkable walkable) {
        Map<Long, Integer> cells = new ConcurrentHashMap<>();
        int w = site.maxX() - site.minX() + 1;
        int d = site.maxZ() - site.minZ() + 1;
        int piles = Math.min(MAX_PILES, Math.max(1, w * d / COLUMNS_PER_PILE));
        long h = Hash.mix(site.salt() ^ 0x50F1A5L);
        for (int p = 0; p < piles; p++) {
            for (int t = 0; t < TRIES_PER_PILE; t++) {
                h = Hash.mix(h + 0x9E3779B97F4A7C15L);
                int cx = site.minX() + 3 + Hash.range(h, Math.max(1, w - 6));
                int cz = site.minZ() + 3 + Hash.range(Hash.mix(h ^ 1), Math.max(1, d - 6));
                if (!open(cx, cz, walkable) || cells.containsKey(key(cx, cz))) {
                    continue;
                }
                build(cells, cx, cz, Hash.mix(h ^ 2));
                break;
            }
        }
        return cells;
    }

    /** True when the 5 x 5 around (cx, cz) is open floor and no other pile is near. */
    private static boolean open(int cx, int cz, Walkable walkable) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!walkable.at(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Piece codes: bits 0-3 the thing, 4-5 its facing (2D index), 6-9 what's stacked on it, 10 inverted.
    private static final int NONE = 0;
    private static final int DRESSER = 1;
    private static final int BOOKSHELF = 2;
    private static final int SIDE_TABLE = 3;
    private static final int ARMCHAIR = 4;
    private static final int DINING_CHAIR = 5;
    private static final int FILING = 6;
    private static final int BOXES = 7;
    private static final int OFFICE_CHAIR = 8;
    private static final int TILE = 9;

    private static final int[] BACK = {DRESSER, BOOKSHELF, FILING, BOXES, DRESSER};
    private static final int[] FRONT = {SIDE_TABLE, ARMCHAIR, DINING_CHAIR, BOXES, OFFICE_CHAIR, NONE};
    private static final int[] ON_TOP = {NONE, NONE, SIDE_TABLE, BOXES, DINING_CHAIR};

    /** A ragged 3 x 2 pile around (cx, cz): a back row of big pieces, a front row of small ones. */
    private static void build(Map<Long, Integer> cells, int cx, int cz, long h) {
        int turn = Hash.range(h, 4);
        Direction front = Direction.from2DDataValue(turn);
        Direction right = front.getClockWise();
        for (int i = -1; i <= 1; i++) {
            long r = Hash.mix(h + i * 31L);
            // Back row (facing the front, as if pushed against a wall that isn't there).
            int bx = cx + right.getStepX() * i - front.getStepX();
            int bz = cz + right.getStepZ() * i - front.getStepZ();
            int back = BACK[Hash.range(r, BACK.length)];
            int top = ON_TOP[Hash.range(Hash.mix(r ^ 3), ON_TOP.length)];
            boolean flipped = Hash.unit(Hash.mix(r ^ 4)) < 0.6;
            cells.put(key(bx, bz), back | front.get2DDataValue() << 4 | top << 6 | (flipped ? 1 << 10 : 0));
            // Front row: smaller things, some turned askew, some missing; a fallen tile now and then.
            int fx = cx + right.getStepX() * i;
            int fz = cz + right.getStepZ() * i;
            int f = FRONT[Hash.range(Hash.mix(r ^ 5), FRONT.length)];
            if (f == NONE && Hash.unit(Hash.mix(r ^ 6)) < 0.5) {
                f = TILE;
            }
            Direction facing = Direction.from2DDataValue(Hash.range(Hash.mix(r ^ 7), 4));
            if (f != NONE) {
                cells.put(key(fx, fz), f | facing.get2DDataValue() << 4);
            }
        }
    }

    private static void piece(int code, int floor, LevelLayout.Column out) {
        Direction facing = Direction.from2DDataValue(code >> 4 & 3);
        int thing = code & 15;
        BlockState state = state(thing, facing, false);
        if (state == null) {
            return;
        }
        out.set(floor + 1, state);
        int top = code >> 6 & 15;
        if (top != NONE) {
            BlockState above = state(top, facing.getOpposite(), (code & 1 << 10) != 0);
            if (above != null) {
                out.set(floor + 2, above);
            }
        }
    }

    private static BlockState state(int thing, Direction facing, boolean inverted) {
        BlockState s = switch (thing) {
            case DRESSER -> Level39HouseBlocks.DRESSER.get().defaultBlockState();
            case BOOKSHELF -> Level39HouseBlocks.BOOKSHELF.get().defaultBlockState();
            case SIDE_TABLE -> Level39HouseBlocks.SIDE_TABLE.get().defaultBlockState();
            case ARMCHAIR -> Level39HouseBlocks.ARMCHAIR.get().defaultBlockState();
            case DINING_CHAIR -> Level39HouseBlocks.DINING_CHAIR.get().defaultBlockState();
            case FILING -> ModBlocks.FILING_CABINET.get().defaultBlockState();
            case BOXES -> ModBlocks.CARDBOARD_BOXES.get().defaultBlockState();
            case OFFICE_CHAIR -> ModBlocks.OFFICE_CHAIR.get().defaultBlockState();
            case TILE -> Level0SetPieceBlocks.FALLEN_TILE.get().defaultBlockState();
            default -> null;
        };
        if (s == null) {
            return null;
        }
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        if (inverted && s.hasProperty(Level39FurnitureBlock.INVERTED)) {
            s = s.setValue(Level39FurnitureBlock.INVERTED, true);
        }
        return s;
    }

    /**
     * Wear for a column of open floor that must stay walkable (the pit room's walkways, the pillar
     * halls, the blackout zones): now and then ({@code chance} per column) a ceiling tile is
     * missing, a cable hanging out of the hole about half the time, and the tile lies broken on the
     * carpet below. Nothing here collides.
     */
    static void wear(long salt, int x, int z, int floor, int ceiling, double chance, LevelLayout.Column out) {
        long h = Hash.mix(salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL));
        if (Hash.unit(h) >= chance) {
            return;
        }
        out.set(ceiling, com.rickr.backrooms.registry.Level0Blocks.CEILING_HOLE.get().defaultBlockState());
        if (Hash.unit(Hash.mix(h ^ 1)) < 0.5) {
            out.set(ceiling - 1, com.rickr.backrooms.registry.Level0Blocks.DANGLING_CABLE.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 2), 4))));
        }
        out.set(floor + 1, Level0SetPieceBlocks.FALLEN_TILE.get().defaultBlockState());
    }
}
