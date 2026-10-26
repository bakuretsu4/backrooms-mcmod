package com.rickr.backrooms.world.anomaly.level37;

import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * {@code level_37/dark_opening}: a pitch-dark arch that leads back. Fandom's Level 37.2 ("Sunlit
 * Pools", https://backrooms.fandom.com/wiki/Level_37.2) lists among its exits "a dark opening"
 * that returns you to Level 37, which in this mod is the main Poolrooms. It is the mirror of the
 * Poolrooms' sunlit archway (placement and blocks: {@link Level37AnomalyPlan}).
 *
 * <p>Walk into the darkness filling the arch: the picture fades to black (client) and a moment
 * later you are back in the Poolrooms. The darkness is only one block deep with solid tile
 * behind it, so nobody walks through it into the next room before it takes them.
 */
final class DarkOpening implements AnomalyBehavior {
    /** Ticks in the darkness before you are through (the client fades to black meanwhile). */
    private static final int PASS_TICKS = 10;

    /** Ticks each player has spent in the darkness. */
    private final Map<UUID, Integer> inDark = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (!inDarkness(player)) {
            inDark.remove(player.getUUID());
            return;
        }
        int ticks = inDark.merge(player.getUUID(), 1, Integer::sum);
        if (ticks >= PASS_TICKS) {
            inDark.remove(player.getUUID());
            pass(player);
        }
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        inDark.remove(player.getUUID());
    }

    /** Feet or head in the darkness block. */
    private static boolean inDarkness(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return player.level().getBlockState(feet).is(ModBlocks.L94_DARK_VOID.get())
                || player.level().getBlockState(feet.above()).is(ModBlocks.L94_DARK_VOID.get());
    }

    private static void pass(ServerPlayer player) {
        // Back up a level: no checkpoint from down here applies any more.
        Checkpoints.clear(player);
        if (Travel.sendToQuietly(player, BackroomsLevel.POOLROOMS, player.getBlockX(), player.getBlockZ())) {
            // The client holds the picture black and lets the Poolrooms fade in (Level37AnomalyOverlay).
            player.playNotifySound(ModSounds.L37_DARK_PASS.get(), SoundSource.MASTER, 0.9f, 1.0f);
            player.sendSystemMessage(Component.translatable("message.backrooms.level37.dark")
                    .withStyle(ChatFormatting.ITALIC));
        }
    }
}
