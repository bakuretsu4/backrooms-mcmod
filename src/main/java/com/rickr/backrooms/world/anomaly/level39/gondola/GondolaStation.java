package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The lift station on the server: after someone rides off in the waiting chair, "the next chair
 * comes round" a few seconds later. Called once a second for every gondola site near a player
 * (through the Level 9 anomaly behaviour's siteTick).
 */
public final class GondolaStation {
    /** How long the boarding spot stays empty before the next chair is there (ticks). */
    public static final int NEXT_CHAIR_TICKS = 80;
    /** No new chair while a riding seat is still this close to the station (blocks). */
    private static final double CLEAR_DISTANCE = 12.0;

    /** Station chair position (as a long) -> game time it was found empty. Server thread only. */
    private static final Map<Long, Long> EMPTY_SINCE = new HashMap<>();

    /** The chair at {@code chairPos} was just ridden away. */
    static void chairTaken(ServerLevel level, BlockPos chairPos) {
        EMPTY_SINCE.put(chairPos.asLong(), level.getGameTime());
    }

    /** Puts a fresh chair out once the boarding spot has been empty long enough and nobody's seat is still near. */
    public static void siteTick(ServerLevel level, AnomalySite site) {
        GondolaLine line = GondolaLine.of(site);
        BlockPos pos = line.stationChairPos();
        if (!level.isLoaded(pos)) {
            return;
        }
        if (!level.getBlockState(pos).isAir()) {
            EMPTY_SINCE.remove(pos.asLong()); // the chair is there (or something else is in the way)
            return;
        }
        long now = level.getGameTime();
        Long since = EMPTY_SINCE.putIfAbsent(pos.asLong(), now);
        if (since == null || now - since < NEXT_CHAIR_TICKS) {
            return;
        }
        if (!level.getEntitiesOfClass(GondolaChair.class, new AABB(pos).inflate(CLEAR_DISTANCE)).isEmpty()) {
            return; // the last chair hasn't left yet
        }
        EMPTY_SINCE.remove(pos.asLong());
        BlockState chair = GondolaRegistry.CHAIR.get().defaultBlockState().setValue(GondolaChairBlock.FACING, line.dir);
        level.setBlock(pos, chair, Block.UPDATE_ALL);
        // A soft clunk as it swings in on the cable.
        level.playSound(null, pos, GondolaRegistry.SHEAVE_CLACK.get(), SoundSource.BLOCKS, 0.6f, 1.1f);
    }

    private GondolaStation() {}
}
