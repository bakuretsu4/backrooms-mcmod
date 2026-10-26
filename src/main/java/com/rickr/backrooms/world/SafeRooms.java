package com.rickr.backrooms.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Safe rooms: small rooms with a checkpoint radio, a warm lamp and a cot, scattered through the
 * levels (the floor plan decides where, see {@code LevelLayout.isSafeRoom}). Inside one nothing
 * spawns, the Bacteria won't come in, and the Watchers and Observers leave you alone.
 *
 * <p>Like everything about the floor plan this is a pure function of (seed, x, z), so asking is
 * cheap and needs no loaded chunks. Only the server knows the layout seed, so on the client this
 * always answers false (the client hears about safe rooms from the server instead).
 */
public final class SafeRooms {

    /** True if {@code pos} is inside a safe room (its walls and doorway count too). False outside the Backrooms. */
    public static boolean isSafe(Level level, BlockPos pos) {
        return isSafeColumn(level, pos.getX(), pos.getZ());
    }

    /** True if the entity stands inside a safe room. */
    public static boolean isSafe(Entity entity) {
        return isSafeColumn(entity.level(), entity.getBlockX(), entity.getBlockZ());
    }

    /**
     * True if any part of a safe room lies within {@code radius} blocks (a square, measured
     * sideways) of {@code pos}. Used to keep spawns away from safe rooms. Columns are checked
     * every 2 blocks: a safe room with its walls is always much wider than that, so none is missed.
     */
    public static boolean isNearSafeRoom(Level level, BlockPos pos, int radius) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (isSafeColumn(level, pos.getX() + dx, pos.getZ() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSafeColumn(Level level, int x, int z) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false; // the client doesn't know the seed
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null) {
            return false;
        }
        return floor.layout().isSafeRoom(BackroomsSeeds.layoutSeed(serverLevel, floor), x, z);
    }

    private SafeRooms() {}
}
