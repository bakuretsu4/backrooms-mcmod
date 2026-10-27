package com.rickr.backrooms.client;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * What the client knows about Level 39 and where the camera is in it.
 *
 * <p>The server sends the level's layout seed on arrival ({@code world/Level39Sync}); with it the
 * client can call the same pure layout queries the world generator uses
 * ({@link Level39Layout#isStagnant}, {@link Level39Layout#facadeWindowAt} and friends). Until it
 * arrives {@link #hasSeed()} is false and seed-dependent touches (murky stagnant water, boarded
 * facade windows) simply don't apply yet.
 *
 * <p>Zones by height: the town is everything from the ground up (y 73+), the tunnels run at
 * y 38-48 under it, and the cloud ceiling hangs at y 250-290. Between the tunnels and the ground
 * (falling down a hole, climbing a shaft) we count as tunnel below {@link #TUNNEL_TOP}.
 */
public final class Level39ClientState {
    /** Below this camera height we are "in the tunnels" (they reach y 48; holes and shafts lead up to 72). */
    public static final double TUNNEL_TOP = 60.0;
    /** The cloud ceiling: from here up the fog turns into the inside of a cloud. */
    public static final double CLOUD_BOTTOM = 246.0;
    public static final double CLOUD_TOP = 292.0;

    private static volatile long layoutSeed;
    private static volatile boolean hasSeed;

    /** Network thread: the server told us the layout seed. */
    public static void receiveSeed(long seed) {
        layoutSeed = seed;
        hasSeed = true;
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    public static long seed() {
        return layoutSeed;
    }

    /** True when the client is in Level 39. */
    public static boolean inLevel39() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_39;
    }

    /** True when {@code eye} is down in the tunnel band (or a hole/shaft leading to it). */
    public static boolean inTunnels(Vec3 eye) {
        return eye.y < TUNNEL_TOP;
    }

    /** True when the tunnel water around {@code eye} is a stagnant (Hydrolitis) segment. */
    public static boolean inStagnantWater(Vec3 eye) {
        return hasSeed && inTunnels(eye)
                && Level39Layout.isStagnant(layoutSeed, (int) Math.floor(eye.x), (int) Math.floor(eye.z));
    }

    /**
     * 0..1 how close {@code eye} is to the superstructure: 0 in mid-town (more than
     * {@code range} blocks from the nearest facade), 1 right at the glass.
     */
    public static float facadeProximity(Vec3 eye, float range) {
        int d = Level39Layout.facadeDistance((int) Math.floor(eye.x), (int) Math.floor(eye.z));
        float t = 1.0f - Math.min(1.0f, Math.max(0.0f, -d / range));
        return t * t * (3.0f - 2.0f * t);
    }

    private Level39ClientState() {}
}
