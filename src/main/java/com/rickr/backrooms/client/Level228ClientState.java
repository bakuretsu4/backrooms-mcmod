package com.rickr.backrooms.client;

import com.rickr.backrooms.network.Level228StatePayload;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;

/**
 * What the client knows about Level 228 (from world/Level228Rules on arrival, login and respawn):
 * the layout seed, so the reflections can be placed in the image volumes the floor plan
 * ({@code world/layout/Level228Plan}) builds behind the glass.
 */
public final class Level228ClientState {
    private static volatile long layoutSeed;
    private static volatile boolean hasSeed;

    private Level228ClientState() {}

    public static void receive(Level228StatePayload payload) {
        layoutSeed = payload.layoutSeed();
        hasSeed = true;
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    public static long seed() {
        return layoutSeed;
    }

    /** True while the client's world is Level 228. */
    public static boolean inLevel228() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_228;
    }
}
