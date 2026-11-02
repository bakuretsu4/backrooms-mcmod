package com.rickr.backrooms.client;

import com.rickr.backrooms.network.Level369StatePayload;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * What the client knows about Level 369 (from world/Level369Forgetting, once a second): the layout
 * seed, for the drifting light (world/Level369Light), and how much you have forgotten.
 */
public final class Level369ClientState {
    private static volatile long layoutSeed;
    private static volatile boolean hasSeed;
    private static volatile float forgotten;

    private Level369ClientState() {}

    public static void receive(Level369StatePayload payload) {
        layoutSeed = payload.layoutSeed();
        hasSeed = true;
        forgotten = payload.forgotten();
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    public static long seed() {
        return layoutSeed;
    }

    private static boolean inLevel369() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_369;
    }

    /** How much you have forgotten, 0..1; always 0 outside Level 369 (leaving brings it all back). */
    public static float forgotten() {
        return inLevel369() ? forgotten : 0.0f;
    }

    /**
     * How much of a camcorder readout is still remembered (1 = all of it): the readouts go one by
     * one, {@code order} 0 first (the battery), then 1 (the tape counter and the clock), 2 (the heart
     * rate) and 3 (REC), each fading out over its own quarter of the way.
     */
    public static float remembered(int order) {
        float f = forgotten() * 4.0f - order;
        return 1.0f - Mth.clamp(f, 0.0f, 1.0f);
    }
}
