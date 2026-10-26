package com.rickr.backrooms.world;

import com.rickr.backrooms.network.IntroCutscenePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of noclipping into the Backrooms (every way in ends here: suffocating in a block,
 * pushing into a wall in the dark, filming the dark, staying alone in it too long, a night's
 * sleep; see {@link RealWorldEntries}). We don't move the player at once: first their client
 * freezes and breaks up the picture for {@link #LAG_TICKS} (client/intro/NoclipTransition), then
 * we move them to Level 0 and the client moshes the old picture into the new one.
 * While a player is in this window, suffocation damage is ignored.
 */
public final class NoclipIntro {
    /** Length of the real-world lag (must match the client's LAG_MS: 0.5 s). */
    public static final int LAG_TICKS = 10;

    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    /** Begin noclipping this player (does nothing if it's already running). */
    public static void start(ServerPlayer player) {
        if (PENDING.putIfAbsent(player.getUUID(), LAG_TICKS) == null) {
            PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.FREEZE));
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
        PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.ARRIVED));
        player.sendSystemMessage(Component.translatable("message.backrooms.noclip").withStyle(s -> s.withItalic(true).withColor(0xC8B45A)));
    }

    /** Player left mid-noclip: forget them (they stay in the real world). */
    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private NoclipIntro() {}
}
