package com.rickr.backrooms.client;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * The camcorder's auto-exposure, like eyes adjusting: it follows how bright it is around you,
 * slowly, so walking from the dark into a lit room overexposes the picture for a moment before it
 * settles, and stepping into the dark leaves it too dark for a moment until the gain comes up.
 * It only ever affects the moment of change; once settled the picture looks exactly as before.
 *
 * <p>Brightness is the light at your eyes and a little way ahead where you look (block light, the
 * mains power during a blackout, your flashlight), on a log scale like the eye's.
 */
public final class EyeAdaptation {
    /** Seconds to settle when it gets brighter (fast, like a camera) and darker (slower). */
    private static final float TO_BRIGHT_S = 1.1f;
    private static final float TO_DARK_S = 2.4f;
    /** How strongly the difference shows, and the limits. */
    private static final float STRENGTH = 0.5f;
    private static final float MIN = 0.55f;
    private static final float MAX = 1.9f;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static float adapted = Float.NaN;
    private static long lastNanos;

    private EyeAdaptation() {}

    /** Exposure multiplier for this frame (1 = settled). */
    public static float factor(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            adapted = Float.NaN;
            return 1.0f;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min((now - lastNanos) / 1.0e9f, 0.25f);
        lastNanos = now;
        float lum = (float) Math.log(brightness(mc));
        if (Float.isNaN(adapted)) {
            adapted = lum;
        }
        float tau = lum > adapted ? TO_BRIGHT_S : TO_DARK_S;
        adapted += (lum - adapted) * (1.0f - (float) Math.exp(-dt / tau));
        return Mth.clamp((float) Math.exp(STRENGTH * (lum - adapted)), MIN, MAX);
    }

    private static float brightness(Minecraft mc) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 look = Vec3.directionFromRotation(mc.gameRenderer.getMainCamera().getXRot(), mc.gameRenderer.getMainCamera().getYRot());
        float sum = 0.0f;
        for (int i = 0; i < 3; i++) {
            Vec3 p = eye.add(look.scale(i * 3.0));
            CURSOR.set(p.x, p.y, p.z);
            sum += Math.max(mc.level.getBrightness(LightLayer.BLOCK, CURSOR) * (0.15f + 0.85f * FixturePower.mainsPower()),
                    mc.level.getBrightness(LightLayer.SKY, CURSOR));
        }
        float light = sum / 3.0f / 15.0f;
        if (CameraClientState.flashlight && CameraClientState.battery > 0.0f) {
            light += 0.25f;
        }
        return 0.04f + light;
    }
}
