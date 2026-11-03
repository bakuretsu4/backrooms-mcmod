package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.client.water.VanillaWaterHider;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Level 1's lights-out (the BLACKOUT event): the garage goes nearly pitch black, only the faintest
 * shape of the floor near you left (docs/reference/level1-garage/lights_out.jpg). The fixtures, their
 * glowing faces and the wet-floor reflections (client/light/Level1WetFloor) already die with the
 * power; this class makes what is left dark in every renderer:
 * <ul>
 *   <li><b>Veil lights</b> (ClientLighting): the lightmap keeps only a faint cold-violet ambient
 *       ({@link #addBlackoutAmbient}), enough to make out the concrete at your feet.</li>
 *   <li><b>Without Veil</b> the lightmap already drops to its blackout floor; nothing extra.</li>
 *   <li><b>Under an Iris shader pack</b> the pack lights the world by block light, which a blackout
 *       doesn't change (the dead tubes still give light 12). The camcorder pass presses the picture
 *       down to near black instead ({@link #screenCrush}, the shader's Blackout uniform), before
 *       its fallback flashlight so your own light still works.</li>
 *   <li><b>All of them:</b> the camcorder's haze turns black ({@link #hazeDensity} etc.), so the
 *       dark swallows the floor a few blocks out, and the tape's black goes darker. Your flashlight
 *       holds it back further, NightShot (infrared) sees through most of it. It stops short of
 *       total: a Smiler's glowing grin 15-20 blocks off still shows (dimly) through it. Vanilla
 *       fog is left alone for the same reason (it would hide them outright).</li>
 * </ul>
 * Everything follows the power's 2-second fade (FixturePower#mainsPower), down and back up.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level1Atmosphere {
    /**
     * The lightmap's ambient at full lights-out (Veil mode): ~0.02, a hint of violet like the
     * footage's black. On the garage concrete (~0.45) the floor at your feet lands around 3/255 on
     * camera, its white paint a little more: the faintest shape, as under Iris (round 2 in game:
     * three times this still showed the markings and pillars clearly). The black haze takes it
     * to nothing a few blocks out.
     */
    private static final float AMBIENT_R = 0.019f;
    private static final float AMBIENT_G = 0.016f;
    private static final float AMBIENT_B = 0.026f;
    /**
     * The black air: clear for the first blocks round you, then thick: 0.4 dark at 8 blocks, 0.7
     * at 15 (a Smiler's grin there still reads as two faint points), 0.85 at 25.
     */
    private static final float HAZE_START = 3.0f;
    private static final float HAZE_DENSITY = 0.12f;
    /** With your flashlight on: the dark starts further out and thickens slower, so the beam reaches. */
    private static final float HAZE_START_BEAM = 6.0f;
    private static final float HAZE_DENSITY_BEAM = 0.05f;
    private static final float HAZE_MAX = 0.92f;
    /** The tape's darkest colour in the lights-out: nearly black, the faint violet of the footage. */
    private static final float LIFT_R = 0.004f;
    private static final float LIFT_G = 0.002f;
    private static final float LIFT_B = 0.007f;
    /** NightShot (infrared) sees through this share of the dark. */
    private static final float NIGHTSHOT_SEES = 0.8f;
    /** How fast the flashlight / NightShot easing follows a switch (per tick): ~0.3 s. */
    private static final float EASE = 0.2f;

    /** 0..1 how far the lights are out on Level 1 (0 anywhere else). */
    private static float dark;
    /** 0..1 eased: your flashlight is on (and has battery). */
    private static float beam;
    /** 0..1 eased: NightShot is on. */
    private static float night;
    /** An Iris shader pack draws the world (asked once a tick, only in the dark: the check goes through reflection). */
    private static boolean packDraws;

    private Level1Atmosphere() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean here = mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_1;
        if (!here) {
            dark = 0.0f;
            beam = 0.0f;
            night = 0.0f;
            packDraws = false;
            return;
        }
        dark = Mth.clamp(1.0f - FixturePower.mainsPower(), 0.0f, 1.0f);
        boolean powered = CameraClientState.battery > 0.0f;
        beam += ((CameraClientState.flashlight && powered ? 1.0f : 0.0f) - beam) * EASE;
        night += ((CameraClientState.nightShot && powered ? 1.0f : 0.0f) - night) * EASE;
        packDraws = dark > 0.0f && VanillaWaterHider.shaderPackInUse();
    }

    /** 0..1 how dark the air and the picture are made right now (NightShot sees through most of it). */
    private static float airDark() {
        return dark * (1.0f - NIGHTSHOT_SEES * night);
    }

    /**
     * The camcorder shader's Blackout uniform: how hard the picture is pressed toward black, only
     * while a shader pack draws the world (otherwise the lightmap already did it).
     */
    public static float screenCrush() {
        return packDraws ? airDark() : 0.0f;
    }

    /** Veil mode: the faint ambient left in the lights-out, added to Level 1's lightmap cell. */
    static void addBlackoutAmbient(Vector3f colors) {
        if (dark > 0.0f) {
            colors.add(AMBIENT_R * dark, AMBIENT_G * dark, AMBIENT_B * dark);
        }
    }

    // ---- The camcorder's haze, glow and lift (AtmosphereGrade): the mood's own values while the ----
    // ---- power is on and everywhere but Level 1 (dark is 0 there).                               ----

    /** Multiplier for the haze colour: the air stops glowing and turns black. */
    static float hazeLight() {
        return 1.0f - airDark();
    }

    static float hazeStart(float base) {
        return Mth.lerp(airDark(), base, Mth.lerp(beam, HAZE_START, HAZE_START_BEAM));
    }

    static float hazeDensity(float base) {
        return Mth.lerp(airDark(), base, Mth.lerp(beam, HAZE_DENSITY, HAZE_DENSITY_BEAM));
    }

    static float hazeMax(float base) {
        return Mth.lerp(airDark(), base, HAZE_MAX);
    }

    /** Multiplier for the soft glow: in the dark nothing is bright enough to bleed. */
    static float glow() {
        return 1.0f - airDark();
    }

    static float liftR(float base) {
        return Mth.lerp(airDark(), base, LIFT_R);
    }

    static float liftG(float base) {
        return Mth.lerp(airDark(), base, LIFT_G);
    }

    static float liftB(float base) {
        return Mth.lerp(airDark(), base, LIFT_B);
    }
}
