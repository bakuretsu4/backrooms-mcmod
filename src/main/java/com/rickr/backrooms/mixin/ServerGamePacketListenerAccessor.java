package com.rickr.backrooms.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets {@code world/anomaly/Teleports} tell the server's movement check where the player is now.
 *
 * <p>Each tick the server remembers where the player started ("first good" position). Right after
 * the player's tick it puts them back there, and it compares the next movement packets against it.
 * A teleport made during the player's tick (where our anomalies run) would be undone on the server
 * and then flagged as "moved too quickly", which snaps the player back and stops them dead: a
 * visible hitch in a corridor that is supposed to loop without a seam. Calling resetPosition()
 * right after the teleport makes the new spot the "first good" one.
 *
 * <p>It also lets {@code world/Level39Rules} clear the server's "floating too long" counters for a
 * player drifting down from Level 39's sky (the gondola entrance): on a server without
 * allow-flight, vanilla kicks anyone who stays off the ground for 4 seconds, and Slow Falling
 * doesn't count as falling for that check.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccessor {
    @Invoker("resetPosition")
    void backrooms$resetPosition();

    @Accessor("aboveGroundTickCount")
    void backrooms$setAboveGroundTickCount(int ticks);

    @Accessor("aboveGroundVehicleTickCount")
    void backrooms$setAboveGroundVehicleTickCount(int ticks);
}
