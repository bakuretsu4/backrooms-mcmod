package com.rickr.backrooms.client;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level94Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The camcorder's auto-exposure, like eyes adjusting: it follows how bright it is around you,
 * slowly, so walking from the dark into a lit room overexposes the picture for a moment before it
 * settles, and stepping into the dark leaves it too dark for a moment until the gain comes up.
 * It only ever affects the moment of change; once settled the picture looks exactly as before.
 *
 * <p>Brightness is the light where you stand and in front of what you look at (block light, the
 * mains power during a blackout, real daylight, your flashlight), on a log scale like the eye's.
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

    /**
     * How bright it is where you are and where you look, 0.04 .. ~1.3. Half of it is the light
     * where you stand, half the light in front of the surfaces you are looking at (each sample is
     * traced along your view and stops just short of the first block, so looking at the floor or
     * a wall never reads the darkness inside it). Sky light counts only as much as it is really
     * day (Level 94's own clock; Minecraft thinks it is always noon there).
     */
    private static float brightness(Minecraft mc) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 look = Vec3.directionFromRotation(mc.gameRenderer.getMainCamera().getXRot(), mc.gameRenderer.getMainCamera().getYRot());
        float day = daylight(mc);
        float power = 0.15f + 0.85f * FixturePower.mainsPower();
        float here = lightAt(mc, eye, power, day);
        float ahead = 0.0f;
        for (int i = 1; i <= 3; i++) {
            Vec3 target = eye.add(look.scale(i * 3.0));
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            Vec3 at = hit.getType() == HitResult.Type.MISS ? target : hit.getLocation().subtract(look.scale(0.3));
            ahead += lightAt(mc, at, power, day);
        }
        float light = 0.5f * here + 0.5f * ahead / 3.0f;
        if (CameraClientState.flashlight && CameraClientState.battery > 0.0f) {
            light += 0.25f;
        }
        return 0.04f + light;
    }

    /** 0 .. 1: block light (dimmed in a blackout) or real daylight, whichever is brighter. */
    private static float lightAt(Minecraft mc, Vec3 pos, float power, float day) {
        CURSOR.set(pos.x, pos.y, pos.z);
        return Math.max(mc.level.getBrightness(LightLayer.BLOCK, CURSOR) * power,
                mc.level.getBrightness(LightLayer.SKY, CURSOR) * day) / 15.0f;
    }

    /** How much of the sky light is real: Level 94 follows its own clock, elsewhere the sky's own darkening. */
    private static float daylight(Minecraft mc) {
        if (BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_94) {
            return Level94Clock.daylight(mc.level, 1.0f);
        }
        return mc.level.getSkyDarken(1.0f);
    }
}
