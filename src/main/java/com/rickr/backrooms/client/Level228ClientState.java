package com.rickr.backrooms.client;

import com.rickr.backrooms.network.Level228StatePayload;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.client.Minecraft;

/**
 * What the client knows about Level 228 (from world/Level228Rules on arrival, login, respawn and
 * every shift): the layout seed and the shared shift state, so the props, reflections and the
 * imitator use exactly the floor plan ({@code world/layout/Level228Plan}) the server collides with.
 */
public final class Level228ClientState {
    private static volatile long layoutSeed;
    private static volatile boolean hasSeed;
    private static volatile Level228Plan.ShiftState shift = Level228Plan.ShiftState.INITIAL;

    private Level228ClientState() {}

    public static void receive(Level228StatePayload payload) {
        if (!hasSeed || layoutSeed != payload.layoutSeed()) {
            // Build the floor plan off the render thread before the props and reflections ask.
            long seed = payload.layoutSeed();
            java.util.concurrent.CompletableFuture.runAsync(() -> Level228Plan.prepare(seed));
        }
        layoutSeed = payload.layoutSeed();
        Level228Plan.ShiftState now = shift;
        // Reuse the old object when nothing changed, so listeners comparing by identity stay quiet.
        if (now.bits != payload.shiftBits() || now.version != payload.shiftVersion()) {
            shift = new Level228Plan.ShiftState(payload.shiftBits(), payload.shiftVersion());
        }
        hasSeed = true;
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    public static long seed() {
        return layoutSeed;
    }

    /** The current shared shift state (INITIAL until the server has said otherwise); never null. */
    public static Level228Plan.ShiftState shift() {
        return shift;
    }

    /** True while the client's world is Level 228. */
    public static boolean inLevel228() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_228;
    }
}
