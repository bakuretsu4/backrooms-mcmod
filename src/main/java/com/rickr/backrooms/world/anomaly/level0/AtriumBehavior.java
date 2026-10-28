package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.FixtureOutages;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level0Atrium;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The Atrium on the server (floor plan: {@link Level0Atrium}). Two quiet things, no monsters:
 * <ul>
 *   <li><b>The watcher.</b> The mannequin at the balcony's edge always ends up facing whoever is
 *       nearest, but it only turns while nobody can see it. Look up from the floor, look away,
 *       look up again: it is looking at you.</li>
 *   <li><b>You were noticed.</b> The first time someone climbs onto the balcony (at most once
 *       every {@value #OUTAGE_COOLDOWN_MINUTES} minutes per atrium), the tubes of the hall below
 *       die one after another in a wave rolling out from under the void, so the way back down is
 *       black, and come back by themselves {@value #OUTAGE_SECONDS} seconds later (the shared
 *       local-outage system, world/FixtureOutages; the ladder alcove's emergency lamp comes on
 *       meanwhile). Only the ground floor's tubes: the pendants stay lit, so the balcony is the
 *       one lit place left, hanging over a black hole.</li>
 * </ul>
 */
public final class AtriumBehavior implements AnomalyBehavior {
    private static final double WATCH_RANGE = 64.0;
    private static final int OUTAGE_SECONDS = 25;
    private static final int OUTAGE_COOLDOWN_MINUTES = 10;
    /** The wave's speed: milliseconds of delay per block from the middle of the void. */
    private static final int WAVE_MS_PER_BLOCK = 55;

    private final Map<AnomalySite, Long> lastOutage = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 10 != 0 || !Level0Atrium.upstairs(site, player.getX(), player.getY(), player.getZ())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = lastOutage.get(site);
        if (last != null && now - last < OUTAGE_COOLDOWN_MINUTES * 60L * 20L) {
            return;
        }
        if (lastOutage.size() > 64) {
            lastOutage.clear();
        }
        lastOutage.put(site, now);
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        List<BlockPos> lights = Level0Atrium.lowerLights(site, layout.floorY(), layout.ceilingY());
        if (lights.isEmpty()) {
            return;
        }
        long[] blocks = new long[lights.size()];
        int[] delays = new int[lights.size()];
        for (int i = 0; i < blocks.length; i++) {
            BlockPos pos = lights.get(i);
            blocks[i] = pos.asLong();
            double dx = pos.getX() + 0.5 - site.centerX();
            double dz = pos.getZ() + 0.5 - site.centerZ();
            delays[i] = (int) (Math.sqrt(dx * dx + dz * dz) * WAVE_MS_PER_BLOCK);
        }
        FixtureOutages.startList(level, blocks, delays, OUTAGE_SECONDS * 1000, FixtureOutages.Pattern.WAVE);
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        BlockPos lower = Level0Atrium.mannequin(site);
        if (!level.isLoaded(lower)) {
            return;
        }
        BlockState state = level.getBlockState(lower);
        if (!state.is(Level0Blocks.MANNEQUIN.get())) {
            return;
        }
        ServerPlayer nearest = null;
        double best = WATCH_RANGE * WATCH_RANGE;
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(Vec3.atCenterOf(lower));
            if (!player.isSpectator() && d < best) {
                best = d;
                nearest = player;
            }
        }
        if (nearest == null) {
            return;
        }
        Direction toward = Direction.getNearest(nearest.getX() - (lower.getX() + 0.5), 0.0, nearest.getZ() - (lower.getZ() + 0.5));
        if (toward.getAxis() == Direction.Axis.Y || state.getValue(BlockStateProperties.HORIZONTAL_FACING) == toward) {
            return;
        }
        Vec3 chest = Vec3.atCenterOf(lower.above());
        if (Observation.seenByAnyone(level, chest, WATCH_RANGE) || Observation.seenByAnyone(level, Vec3.atCenterOf(lower), WATCH_RANGE)) {
            return;
        }
        level.setBlock(lower, state.setValue(BlockStateProperties.HORIZONTAL_FACING, toward), Block.UPDATE_CLIENTS);
        BlockState upper = level.getBlockState(lower.above());
        if (upper.is(Level0Blocks.MANNEQUIN.get())) {
            level.setBlock(lower.above(), upper.setValue(BlockStateProperties.HORIZONTAL_FACING, toward), Block.UPDATE_CLIENTS);
        }
    }
}
