package com.rickr.backrooms.world.anomaly.level1;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Fog patches: "a low-hanging fog with no discernable source. The fog often coalesces into
 * condensation, forming puddles on the floor in inconsistent areas" (archived Wikidot,
 * <a href="https://backrooms-wiki.wikidot.com/archived:level-1">archived:level-1</a>).
 *
 * <p>The fog itself is drawn by the client ({@code client/anomaly/level1/FogPuddlesEffect}). Here,
 * once a second: the film of water on the puddle spots comes and goes, but only where nobody is
 * looking, and now and then water drips into one of the puddles.
 */
public final class FogPuddlesBehavior implements AnomalyBehavior {
    /** Players further away than this don't make the zone do anything. */
    private static final double ACTIVE_RANGE = 40.0;
    /** Chance that an unseen puddle spot that was picked changes (dries up or fills). */
    private static final float CHANGE_CHANCE = 0.35f;

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        ServerPlayer near = nearest(level, site);
        if (near == null) {
            return;
        }
        RandomSource random = level.getRandom();
        int width = site.maxX() - site.minX() + 1;
        int depth = site.maxZ() - site.minZ() + 1;
        int y = LevelLayout.FLOOR_Y + 1;
        BlockState film = ModBlocks.L1_CONDENSATION.get().defaultBlockState();
        for (int i = 0; i < 10; i++) {
            int x = site.minX() + random.nextInt(width);
            int z = site.minZ() + random.nextInt(depth);
            if (!Level1AnomalyPlan.fogPuddleSpot(site, x, z) || random.nextFloat() >= CHANGE_CHANCE) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos) || !level.getBlockState(pos.below()).is(ModBlocks.WET_CONCRETE_FLOOR.get())) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            boolean wet = now.is(ModBlocks.L1_CONDENSATION.get());
            if (!wet && !now.isAir() || Observation.seenByAnyone(level, Vec3.atCenterOf(pos), ACTIVE_RANGE)) {
                continue;
            }
            level.setBlock(pos, wet ? Blocks.AIR.defaultBlockState() : film, Block.UPDATE_ALL);
        }
        // A drop of water falls into a puddle near the player (about every three seconds).
        if (random.nextInt(3) == 0) {
            for (int i = 0; i < 8; i++) {
                int x = near.getBlockX() + random.nextInt(25) - 12;
                int z = near.getBlockZ() + random.nextInt(25) - 12;
                BlockPos pos = new BlockPos(x, y, z);
                if (site.contains(x, z) && level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.L1_CONDENSATION.get())) {
                    level.playSound(null, pos, ModSounds.L1_DRIP.get(), SoundSource.AMBIENT,
                            0.5f + random.nextFloat() * 0.3f, 0.85f + random.nextFloat() * 0.3f);
                    break;
                }
            }
        }
    }

    @Nullable
    private static ServerPlayer nearest(ServerLevel level, AnomalySite site) {
        ServerPlayer best = null;
        double bestDistance = ACTIVE_RANGE;
        for (ServerPlayer player : level.players()) {
            double distance = site.distanceTo(player.getX(), player.getZ());
            if (!player.isSpectator() && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }
}
