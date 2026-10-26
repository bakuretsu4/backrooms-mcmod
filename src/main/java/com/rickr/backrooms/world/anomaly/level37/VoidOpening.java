package com.rickr.backrooms.world.anomaly.level37;

import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;

/**
 * {@code level_37/void_opening}: a gap in a slit-window wall where the white void can be stepped
 * into. Fandom's Level 37.2 ("Sunlit Pools", https://backrooms.fandom.com/wiki/Level_37.2): beyond
 * the openings there is only a white void of glowing gas, and wanderers who go into it
 * inexplicably vanish. (Placement and blocks: {@link Level37AnomalyPlan}.)
 *
 * <ul>
 *   <li><b>Warning:</b> the nearer you get, the more your sanity drains (nothing ~12 blocks away,
 *       3 per second at the gap). The client adds the glare and a high soft tone.</li>
 *   <li><b>Stepping in:</b> climb over the knee-high lip into the gap and stay there a moment (the
 *       picture whites out completely), and you are gone from Level 37: you come to somewhere
 *       dark in Level 0, thousands of blocks from anywhere, with a big chunk of sanity missing.</li>
 * </ul>
 * Step back out before the white is complete and nothing happens.
 */
final class VoidOpening implements AnomalyBehavior {
    /** Sanity drained per second right at the gap ... */
    private static final float DRAIN_AT_GAP = 3.0f;
    /** ... fading to nothing this far away (blocks). */
    private static final double DRAIN_RANGE = 12.0;
    /** Ticks standing in the gap before the light takes you (the client whites out meanwhile). */
    private static final int TAKE_TICKS = 16;
    /** Sanity lost on the way through. */
    private static final float VANISH_COST = 35.0f;
    /** You come to this far (blocks) from where you vanished, in a random direction ... */
    private static final int SCATTER_MIN = 2000;
    private static final int SCATTER_MAX = 6000;
    /** ... in the first of this many tries that is dark (no light fixture within LIT_RADIUS). */
    private static final int DARK_TRIES = 12;
    private static final int LIT_RADIUS = 6;

    /** Ticks each player has been standing in the gap. */
    private final Map<UUID, Integer> inGap = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        double d = Level37AnomalyPlan.distanceToOpening(site, player.getX(), player.getZ());
        if (ticksInside % 20 == 0 && !player.isCreative()) {
            float k = (float) Mth.clamp(1.0 - d / DRAIN_RANGE, 0.0, 1.0);
            if (k > 0.0f) {
                SanityService.drain(player, DRAIN_AT_GAP * k);
            }
        }
        if (!inGap(site, player)) {
            inGap.remove(player.getUUID());
            return;
        }
        int ticks = inGap.merge(player.getUUID(), 1, Integer::sum);
        if (ticks >= TAKE_TICKS) {
            inGap.remove(player.getUUID());
            vanish(player);
        }
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        inGap.remove(player.getUUID());
    }

    /** Over the lip and inside the gap (between the lip's front edge and the white). */
    static boolean inGap(AnomalySite site, ServerPlayer player) {
        double front = Level37AnomalyPlan.inFront(site, player.getX(), player.getZ());
        double across = Level37AnomalyPlan.across(site, player.getX(), player.getZ());
        return front < 2.0 && across > 0.0 && across < Level37AnomalyPlan.GAP_WIDTH
                && player.getY() >= Level37AnomalyPlan.GAP_BOTTOM_Y - 0.2;
    }

    private void vanish(ServerPlayer player) {
        ServerLevel lobby = player.server.getLevel(BackroomsLevel.LEVEL_0.dimension());
        if (lobby == null) {
            return;
        }
        BlockPos spot = darkSpot(lobby, player);
        // A way out of the level like any other: Level 37's checkpoint (if any) no longer applies.
        Checkpoints.clear(player);
        if (!player.isCreative()) {
            SanityService.drain(player, VANISH_COST);
        }
        if (Travel.sendToQuietly(player, BackroomsLevel.LEVEL_0, spot.getX(), spot.getZ())) {
            // The client holds the picture white and lets Level 0 fade in (Level37AnomalyOverlay).
            player.playNotifySound(ModSounds.L37_VOID_VANISH.get(), SoundSource.MASTER, 0.9f, 1.0f);
            player.sendSystemMessage(Component.translatable("message.backrooms.level37.void")
                    .withStyle(ChatFormatting.ITALIC));
        }
    }

    /**
     * A random spot of Level 0 far away where no light fixture is near: the floor plan alone says
     * where the panels are (no chunk loading), so this checks the blocks the layout would place.
     */
    private static BlockPos darkSpot(ServerLevel lobby, ServerPlayer player) {
        RandomSource random = player.getRandom();
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        long seed = BackroomsSeeds.layoutSeed(lobby, BackroomsLevel.LEVEL_0);
        BlockPos spot = null;
        for (int i = 0; i < DARK_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int dist = SCATTER_MIN + random.nextInt(SCATTER_MAX - SCATTER_MIN);
            int x = player.getBlockX() + (int) (Math.cos(angle) * dist);
            int z = player.getBlockZ() + (int) (Math.sin(angle) * dist);
            spot = Travel.findSafeSpot(lobby, BackroomsLevel.LEVEL_0, x, z);
            if (!litNear(layout, seed, spot.getX(), spot.getZ())) {
                break;
            }
        }
        return spot;
    }

    private static boolean litNear(LevelLayout layout, long seed, int x, int z) {
        boolean[] lit = {false};
        for (int dx = -LIT_RADIUS; dx <= LIT_RADIUS && !lit[0]; dx++) {
            for (int dz = -LIT_RADIUS; dz <= LIT_RADIUS && !lit[0]; dz++) {
                layout.fillColumn(seed, x + dx, z + dz, (y, state) -> {
                    // (the floor plan alone, no world: an empty getter is enough for fixed lamps)
                    if (state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) > 0) {
                        lit[0] = true;
                    }
                });
            }
        }
        return lit[0];
    }
}
