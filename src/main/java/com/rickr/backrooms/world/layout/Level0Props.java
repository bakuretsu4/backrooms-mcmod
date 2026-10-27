package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level39FurnitureBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.registry.Level39HouseBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which loose objects make up Level 0's set pieces (see {@code Level0Layout}): the film's heaps of
 * 80s/90s bargain furniture and the bits lying around the throne. The furniture is borrowed from
 * other levels (Level 39's houses, the office and house interiors), so no new blocks are needed.
 *
 * <p>Everything is a pure function of (seed, position), like the rest of the layout, and uses only
 * block-state lookups (no allocation).
 */
final class Level0Props {
    /** Pieces in a furniture heap; the first {@link #TURNABLE} are Level 39 furniture that can lie upside down. */
    private static final int PIECES = 13;
    private static final int TURNABLE = 7;

    private Level0Props() {}

    /**
     * One piece of a furniture heap at (x, y, z): a random dresser, armchair, CRT television, desk,
     * chair, lamp table, filing cabinet ... facing any way, and (for the pieces that can) sometimes
     * upside down, which reads as "tipped over" in a pile.
     */
    static BlockState heapPiece(long seed, int x, int y, int z) {
        long h = Hash.of(seed, x, z, 640 + y);
        Direction facing = Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 1), 4));
        int pick = Hash.range(h, PIECES);
        BlockState state = switch (pick) {
            case 0 -> Level39HouseBlocks.DRESSER.get().defaultBlockState();
            case 1 -> Level39HouseBlocks.ARMCHAIR.get().defaultBlockState();
            case 2 -> Level39HouseBlocks.SIDE_TABLE.get().defaultBlockState(); // with its dead table lamp
            case 3 -> Level39HouseBlocks.BOOKSHELF.get().defaultBlockState();
            case 4 -> Level39HouseBlocks.DINING_CHAIR.get().defaultBlockState();
            case 5 -> Level39HouseBlocks.DINING_TABLE.get().defaultBlockState();
            case 6 -> Level39HouseBlocks.RADIO.get().defaultBlockState();
            case 7 -> ModBlocks.TV.get().defaultBlockState(); // the CRT
            case 8 -> ModBlocks.OFFICE_DESK.get().defaultBlockState();
            case 9 -> ModBlocks.WOODEN_CHAIR.get().defaultBlockState();
            case 10 -> ModBlocks.FILING_CABINET.get().defaultBlockState();
            case 11 -> ModBlocks.L94_ARMCHAIR.get().defaultBlockState();
            default -> ModBlocks.DINING_TABLE.get().defaultBlockState();
        };
        state = state.setValue(WallMountBlock.FACING, facing);
        if (pick < TURNABLE && Hash.unit(Hash.mix(h ^ 2)) < 0.35) {
            state = state.setValue(Level39FurnitureBlock.INVERTED, true);
        }
        return state;
    }

    /** A small object dropped near the throne: a dead lamp or a chair, any way round. */
    static BlockState litter(long seed, int x, int z) {
        long h = Hash.of(seed, x, z, 645);
        Direction facing = Direction.from2DDataValue(Hash.range(Hash.mix(h), 4));
        BlockState state = Hash.unit(h) < 0.6
                ? Level39Blocks.DEAD_LAMP.get().defaultBlockState()
                : Level39HouseBlocks.DINING_CHAIR.get().defaultBlockState();
        return state.setValue(WallMountBlock.FACING, facing);
    }
}
