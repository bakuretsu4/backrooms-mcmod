package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.RackBayBlock;
import com.rickr.backrooms.block.RackUprightBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steel pallet racking across a Level 1 bay (the crate halls and some of the warehouse sector's
 * bays): two rows back to back down the middle of the bay, an aisle on either side. Each row is
 * blue upright frames at both ends and in the middle of the bay (two sections of three slots) and
 * three levels: pallets on the floor, then two levels of orange beams two blocks apart, so you
 * can see between the loads into the next aisle. Loads vary: shrink-wrapped stacks, a few loose
 * boxes, an empty pallet, nothing, or a stack that slumped.
 *
 * <p>Coordinates are the bay's (mx, mz in the 12-block grid; the bay is 3..11 on both axes).
 */
final class Level1Racks {
    /** The two rows: mz 6 faces the north aisle, mz 7 the south one. */
    static final int ROW_NORTH = 6;
    static final int ROW_SOUTH = 7;
    /** Upright frames at these mx; the slots are between them. */
    private static final int FRAME_A = Level1Layout.PILLAR;
    private static final int FRAME_B = 7;
    private static final int FRAME_C = Level1Layout.GRID - 1;
    /** Rack height: the frames stand this many blocks tall; the slot levels are 2 apart. */
    private static final int HEIGHT = 6;
    private static final int LEVELS = 3;

    private Level1Racks() {}

    /** True for the rack rows (not walkable). */
    static boolean rackRow(int mx, int mz) {
        return (mz == ROW_NORTH || mz == ROW_SOUTH) && mx >= Level1Layout.PILLAR;
    }

    /** True for a slot (between the frames), where a crate may stand on the floor level. */
    static boolean slot(int mx, int mz) {
        return rackRow(mx, mz) && mx != FRAME_A && mx != FRAME_B && mx != FRAME_C;
    }

    /**
     * One rack column. {@code salt} varies the loads per hall; with {@code freeFloor} the floor
     * level of every slot is left empty (a crate hall's crate spots).
     */
    static void fill(long salt, int x, int z, int mx, int mz, int floor, boolean freeFloor, LevelLayout.Column out) {
        Direction aisle = mz == ROW_NORTH ? Direction.NORTH : Direction.SOUTH;
        if (!slot(mx, mz)) {
            BlockState frame = ModBlocks.L1_RACK_UPRIGHT.get().defaultBlockState().setValue(FurnitureBlock.FACING, aisle);
            for (int y = floor + 1; y <= floor + HEIGHT; y++) {
                // Beams at every level above the floor one (y = floor + 3, floor + 5).
                boolean beams = (y - floor - 1) % 2 == 0 && y > floor + 1;
                out.set(y, frame.setValue(RackUprightBlock.BEAMS, beams));
            }
            return;
        }
        for (int level = 0; level < LEVELS; level++) {
            if (level == 0 && freeFloor) {
                continue;
            }
            int load = load(Hash.unit(salt, x * 7 + level, z, 7480), level);
            if (level == 0 && load == RackBayBlock.EMPTY) {
                continue; // nothing on the floor level: no beams there either
            }
            out.set(floor + 1 + 2 * level, ModBlocks.L1_RACK_BAY.get().defaultBlockState()
                    .setValue(FurnitureBlock.FACING, aisle)
                    .setValue(RackBayBlock.LOAD, load)
                    .setValue(RackBayBlock.BEAMS, level > 0));
        }
    }

    /** Mostly full, the top level a little emptier; now and then a slumped stack. */
    private static int load(double r, int level) {
        double empty = level == 2 ? 0.28 : 0.16;
        if (r < empty) return RackBayBlock.EMPTY;
        if (r < empty + 0.45) return RackBayBlock.WRAPPED;
        if (r < empty + 0.65) return RackBayBlock.LOOSE;
        if (r < empty + 0.76) return RackBayBlock.PALLET;
        return r < empty + 0.8 ? RackBayBlock.COLLAPSED : RackBayBlock.WRAPPED;
    }
}
