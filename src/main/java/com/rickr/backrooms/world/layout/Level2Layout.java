package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Level 2, "Pipe Dreams": narrow lavender maintenance corridors, 2 wide and 3 tall.
 *
 * <p><b>Floor plan.</b> Long straight corridors run east-west forever, separated by thick rock
 * (5-15 blocks). Cross corridors running north-south link neighbouring corridors at random spots,
 * like the rungs of a ladder, and a few east-west stretches are blocked off into dead ends. Side
 * niches (2 wide, 1-2 deep) are cut into the corridor walls; some hide a crate.
 *
 * <p><b>Walls.</b> Each corridor wall is lined with rows of rusty pipes and pale ducts (at floor
 * level or along the ceiling edge), in patterns that change every dozen blocks. Caged bulkhead lamps
 * on the walls and strip lights in the ceiling light some stretches; others are dark.
 *
 * <p><b>Exit.</b> One emergency door per 320x320 region, standing in a short wall in an open room.
 * Opening it takes you (and anyone standing next to you) back to the real world.
 */
public final class Level2Layout extends LevelLayout {
    /** East-west corridors: 2-wide bands separated by solid gaps. */
    private static final Bands CORRIDORS = new Bands(40, 2, 2, 5, 9, 201);
    /** Where north-south cross corridors may run. */
    private static final Bands CROSSINGS = new Bands(64, 2, 2, 10, 22, 202);
    private static final int CLEARING_RADIUS = 8;

    // Column kinds.
    private static final int SOLID = 0;
    private static final int CORRIDOR_EW = 1; // runs along X
    private static final int CORRIDOR_NS = 2; // runs along Z
    private static final int NICHE = 3;

    // Wall lining rows, bottom to top (floor + 1 .. floor + 3): bare wall, rusty pipe run, pale duct.
    private static final int BARE = 0;
    private static final int PIPE = 1;
    private static final int DUCT = 2;
    private static final int[][] WALL_PATTERNS = {
            {DUCT, BARE, PIPE},
            {DUCT, PIPE, PIPE},
            {BARE, PIPE, DUCT},
            {PIPE, BARE, DUCT},
    };

    public Level2Layout() {
        super(3, new ExitLocator(320, 40, 3000));
    }

    /** The free-standing exit wall: 5 wide along X at the exit's Z, door in the middle. */
    private static boolean isExitWall(int dx, int dz) {
        return dz == 0 && Math.abs(dx) <= 2;
    }

    private static int kind(long seed, int x, int z) {
        long sz = CORRIDORS.span(seed, z);
        long sx = CROSSINGS.span(seed, x);
        if (Bands.isBand(sz)) {
            // East-west corridor, unless this stretch (between two crossings) is walled off here.
            if (!Bands.isBand(sx) && Hash.unit(seed, Bands.index(sz), Bands.index(sx), 211) < 0.1
                    && x == CROSSINGS.start(sx) + Bands.length(sx) / 2) {
                return SOLID;
            }
            return CORRIDOR_EW;
        }
        if (Bands.isBand(sx) && Hash.unit(seed, Bands.index(sx), Bands.index(sz), 210) < 0.45) {
            return CORRIDOR_NS;
        }
        return nicheDepth(seed, x, z, sz) > 0 ? NICHE : SOLID;
    }

    /**
     * Niches are cut into the rock right next to a corridor. Returns how deep into the niche this
     * column is (1 = first block off the corridor), or 0 if it is not part of a niche.
     */
    private static int nicheDepth(long seed, int x, int z, long sz) {
        if (Math.floorMod(x, 7) != 2 && Math.floorMod(x, 7) != 3) {
            return 0;
        }
        int pos = z - CORRIDORS.start(sz);
        int length = Bands.length(sz);
        int unit = Math.floorDiv(x, 7);
        for (int side = 0; side < 2; side++) {
            long h = Hash.of(seed, unit, Bands.index(sz) * 2 + side, 212);
            if (Hash.unit(h) >= 0.2) {
                continue;
            }
            int depth = Hash.unit(Hash.mix(h)) < 0.4 ? 2 : 1;
            int into = side == 0 ? pos + 1 : length - pos; // side 0 hangs off the corridor to the north
            if (into <= depth) {
                return into;
            }
        }
        return 0;
    }

    private boolean isOpen(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= CLEARING_RADIUS) {
            return !isExitWall(dx, dz);
        }
        return kind(seed, x, z) != SOLID;
    }

    /** A crate at the back of some niches. */
    private static boolean hasCrate(long seed, int x, int z) {
        long sz = CORRIDORS.span(seed, z);
        if (Bands.isBand(sz)) {
            return false;
        }
        int depth = nicheDepth(seed, x, z, sz);
        return depth > 0 && nicheDepth(seed, x, z + (z - CORRIDORS.start(sz) < Bands.length(sz) / 2 ? 1 : -1), sz) == 0
                && Hash.unit(seed, x, z, 35) < 0.35;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= CLEARING_RADIUS) {
            return !isExitWall(dx, dz);
        }
        int kind = kind(seed, x, z);
        return kind != SOLID && !(kind == NICHE && hasCrate(seed, x, z));
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        BlockState pipe = ModBlocks.RUSTY_PIPE.get().defaultBlockState();
        BlockState light = ModBlocks.PIPE_LIGHT.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int exitDistance = Math.max(Math.abs(dx), Math.abs(dz));

        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());

        if (exitDistance <= CLEARING_RADIUS) {
            if (isExitWall(dx, dz)) {
                if (dx == 0) {
                    out.set(floor + 1, ModBlocks.EXIT_DOOR.get().defaultBlockState().setValue(ExitDoorBlock.HALF, DoubleBlockHalf.LOWER));
                    out.set(floor + 2, ModBlocks.EXIT_DOOR.get().defaultBlockState().setValue(ExitDoorBlock.HALF, DoubleBlockHalf.UPPER));
                    out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
                } else {
                    for (int y = floor + 1; y < ceiling; y++) {
                        out.set(y, y == ceiling - 1 ? pipe : wall);
                    }
                }
            } else if (Math.floorMod(dx, 4) == 2 && Math.floorMod(dz, 4) == 2) {
                out.set(ceiling, light);
            }
            return;
        }

        int kind = kind(seed, x, z);
        if (kind == SOLID) {
            fillWall(seed, x, z, out);
            return;
        }
        if (kind == NICHE) {
            if (hasCrate(seed, x, z)) {
                out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
            }
            return;
        }

        // Corridor: lanes 0 and 1 across its width; lit or dark in 20-block stretches.
        boolean eastWest = kind == CORRIDOR_EW;
        long span = eastWest ? CORRIDORS.span(seed, z) : CROSSINGS.span(seed, x);
        int lane = eastWest ? z - CORRIDORS.start(span) : x - CROSSINGS.start(span);
        int along = eastWest ? x : z;
        boolean lit = Hash.unit(seed, Bands.index(span), Math.floorDiv(along, 20), eastWest ? 220 : 221) < 0.6;
        if (!lit) {
            return;
        }
        if (lane == 0 && Math.floorMod(along, 6) <= 1) {
            out.set(ceiling, light); // short strip light, 2 blocks long
        }
        // Caged lamps alternate between the two walls every 8 blocks.
        Direction lampFacing = null;
        if (lane == 0 && Math.floorMod(along, 16) == 4) {
            lampFacing = eastWest ? Direction.SOUTH : Direction.EAST;
        } else if (lane == 1 && Math.floorMod(along, 16) == 12) {
            lampFacing = eastWest ? Direction.NORTH : Direction.WEST;
        }
        if (lampFacing != null && !isOpen(seed, x - lampFacing.getStepX(), z - lampFacing.getStepZ())) {
            out.set(floor + 2, ModBlocks.BULKHEAD_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, lampFacing));
        }
    }

    /**
     * Rock between corridors. Where it faces a corridor it is lined with pipes and ducts; the
     * pattern (which of the three rows is a duct, a pipe run or bare wall) changes every 12 blocks.
     */
    private void fillWall(long seed, int x, int z, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        boolean lining = isOpen(seed, x + 1, z) || isOpen(seed, x - 1, z) || isOpen(seed, x, z + 1) || isOpen(seed, x, z - 1);
        if (!lining) {
            for (int y = floor + 1; y < ceiling; y++) {
                out.set(y, wall);
            }
            return;
        }
        int[] pattern = WALL_PATTERNS[Hash.range(Hash.of(seed, Math.floorDiv(x, 12), Math.floorDiv(z, 12), 37), WALL_PATTERNS.length)];
        for (int i = 0; i < pattern.length; i++) {
            BlockState row = switch (pattern[i]) {
                case PIPE -> ModBlocks.RUSTY_PIPE.get().defaultBlockState();
                case DUCT -> ModBlocks.PALE_DUCT.get().defaultBlockState();
                default -> wall;
            };
            out.set(floor + 1 + i, row);
        }
    }
}
