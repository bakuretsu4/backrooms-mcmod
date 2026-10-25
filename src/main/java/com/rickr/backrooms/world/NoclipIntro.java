package com.rickr.backrooms.world;

import com.rickr.backrooms.network.IntroCutscenePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the noclip intro. When a player starts to noclip we don't teleport them right
 * away: first their client plays the "filming in the real world, then it glitches" part for
 * {@link #FILMING_TICKS}. Then we move them to Level 0 and tell the client to play the fall.
 * While a player is in this window, suffocation damage is ignored.
 */
public final class NoclipIntro {
    /** Length of the real-world part (must match the client's FILMING timeline: 2.6 s). */
    public static final int FILMING_TICKS = 52;

    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    /** Begin the intro for this player (does nothing if it's already running). */
    public static void start(ServerPlayer player) {
        if (PENDING.putIfAbsent(player.getUUID(), FILMING_TICKS) == null) {
            PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.FILMING));
        }
    }

    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    /** Called every server tick for each player. */
    public static void tick(ServerPlayer player) {
        Integer left = PENDING.get(player.getUUID());
        if (left == null) {
            return;
        }
        if (left > 0) {
            PENDING.put(player.getUUID(), left - 1);
            return;
        }
        PENDING.remove(player.getUUID());
        Travel.sendToQuietly(player, BackroomsLevel.LEVEL_0, player.getBlockX(), player.getBlockZ());
        PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.FALLING));
        player.sendSystemMessage(Component.translatable("message.backrooms.noclip").withStyle(s -> s.withItalic(true).withColor(0xC8B45A)));
    }

    /** Player left mid-intro: forget them (they stay in the real world). */
    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private NoclipIntro() {}
}
