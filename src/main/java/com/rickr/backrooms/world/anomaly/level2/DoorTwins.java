package com.rickr.backrooms.world.anomaly.level2;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps a void/loop door and its copy (in the void, or up by the looping hallway) open or shut
 * together, so the seamless swap between them never shows a door snapping. Whichever of the two
 * somebody moved last wins: we remember the state we last made them agree on, and the one that
 * differs from it is the one a player just used.
 *
 * <p>Both doors are unlocked, so a player can always open the one in front of them: nobody is ever
 * shut in. Server thread only.
 */
final class DoorTwins {
    /** Real door position (packed) -> the open state both doors last agreed on. */
    private static final Map<Long, Boolean> AGREED = new HashMap<>();

    static void sync(ServerLevel level, BlockPos real, BlockPos copy) {
        if (!level.isLoaded(real) || !level.isLoaded(copy)) {
            return;
        }
        BlockState realState = level.getBlockState(real);
        BlockState copyState = level.getBlockState(copy);
        if (!(realState.getBlock() instanceof DoorBlock realDoor) || !(copyState.getBlock() instanceof DoorBlock copyDoor)) {
            return;
        }
        boolean realOpen = realState.getValue(DoorBlock.OPEN);
        boolean copyOpen = copyState.getValue(DoorBlock.OPEN);
        long key = real.asLong();
        if (realOpen == copyOpen) {
            AGREED.put(key, realOpen);
            return;
        }
        Boolean agreed = AGREED.get(key);
        if (agreed == null || agreed != realOpen) {
            // The real door was used (or we just started): the copy follows it.
            copyDoor.setOpen(null, level, copyState, copy, realOpen);
            AGREED.put(key, realOpen);
        } else {
            // Somebody used the copy: the real door follows.
            realDoor.setOpen(null, level, realState, real, copyOpen);
            AGREED.put(key, copyOpen);
        }
        if (AGREED.size() > 4096) {
            AGREED.clear(); // only a memory of recent doors; forgetting just makes the copy follow the real one
        }
    }

    private DoorTwins() {}
}
