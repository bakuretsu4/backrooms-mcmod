package com.rickr.backrooms.world.anomaly;

import com.rickr.backrooms.mixin.ServerGamePacketListenerAccessor;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;

/**
 * Teleports inside one level, made from a player's tick (where anomalies run).
 *
 * <p>{@link #shift} moves a player by an offset while keeping where they look and how they move
 * (every coordinate is sent as RELATIVE). If the place they land in looks exactly like the place
 * they left, this is the classic endless corridor: walk past a point and you are quietly back at
 * the start, with nothing on screen jumping. Far shifts are fine too: the client simply stands
 * still until the destination chunks arrive (it doesn't move a player whose chunk isn't loaded).
 *
 * <p>Every teleport here ends with {@link #settle}, see {@link ServerGamePacketListenerAccessor}.
 */
public final class Teleports {
    public static void shift(ServerPlayer player, double dx, double dy, double dz) {
        // teleport() takes the ABSOLUTE target; the RELATIVE flags only make the packet carry the
        // difference, so the client adds it to wherever it is right now (no rubber-banding).
        player.connection.teleport(player.getX() + dx, player.getY() + dy, player.getZ() + dz,
                player.getYRot(), player.getXRot(), EnumSet.allOf(RelativeMovement.class));
        player.resetFallDistance();
        settle(player);
    }

    /** An ordinary teleport to a spot in the same level (the picture jumps; use it in the dark). */
    public static void to(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        player.resetFallDistance();
        settle(player);
    }

    /**
     * Makes the spot the player was just teleported to the server's new reference for its movement
     * check, so the teleport isn't undone at the end of the tick or snapped back as "moved too
     * quickly". Harmless outside a player's tick.
     */
    public static void settle(ServerPlayer player) {
        ((ServerGamePacketListenerAccessor) player.connection).backrooms$resetPosition();
    }

    private Teleports() {}
}
