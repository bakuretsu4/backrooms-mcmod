package com.rickr.backrooms.client.anomaly.level9;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Client effects of Level 9's anomalies (see {@code world/anomaly/level9}). The Crimson Field's
 * sky, fog, sounds and the Gloom live in {@link CrimsonFieldClient}, the cold in
 * {@link Level9Frost}, the Mangled fog's pillar in {@link MangledPillar}. Sources:
 * https://backrooms-wiki.wikidot.com/level-9 and http://backrooms-mghc.wikidot.com/level-9-1.
 */
public final class Level9AnomalyEffects {
    /**
     * Level 9.1: the cold blue-grey dark between pulses (exposure, contrast, saturation | shadow
     * tint | highlight tint | lift | haze colour, start, density, max | contact shadows, glow,
     * breath; see AtmosphereGrade). The mist is a little lighter than the grass, so the field
     * reads as dark stalks against grey murk instead of a black screen.
     */
    private static final AtmosphereGrade.Mood FIELD_DARK = new AtmosphereGrade.Mood(0.96f, 1.08f, 0.62f,
            0.86f, 0.92f, 1.10f, 1.0f, 1.0f, 1.02f,
            0.006f, 0.008f, 0.014f, 0.11f, 0.12f, 0.15f, 6.0f, 0.040f, 0.60f, 0.34f, 0.18f, 0.012f);
    /** ... and while the Gloom glows: the mist itself turns crimson. */
    private static final AtmosphereGrade.Mood FIELD_RED = new AtmosphereGrade.Mood(0.98f, 1.10f, 0.80f,
            1.06f, 0.86f, 0.86f, 1.10f, 0.90f, 0.88f,
            0.016f, 0.004f, 0.004f, 0.34f, 0.04f, 0.04f, 5.0f, 0.045f, 0.55f, 0.34f, 0.24f, 0.012f);
    /** Walking a grass walkway: colder and greyer the nearer the field. */
    private static final AtmosphereGrade.Mood WALKWAY_COLD = new AtmosphereGrade.Mood(0.97f, 1.06f, 0.60f,
            0.84f, 0.92f, 1.14f, 1.0f, 1.0f, 1.05f,
            0.008f, 0.010f, 0.020f, 0.06f, 0.08f, 0.11f, 7.0f, 0.036f, 0.45f, 0.34f, 0.20f, 0.010f);
    /** Inside the clipped houses: the picture can't settle (more glow, a restless auto-iris). */
    private static final AtmosphereGrade.Mood CLIPPED = new AtmosphereGrade.Mood(1.02f, 0.98f, 0.80f,
            0.88f, 0.92f, 1.10f, 1.06f, 0.98f, 0.86f,
            0.012f, 0.012f, 0.022f, 0.06f, 0.07f, 0.09f, 8.0f, 0.030f, 0.40f, 0.40f, 0.34f, 0.035f);

    public static void register() {
        AnomalyClientEffects.register(Level9Sites.GRASS_WALKWAY, Level9AnomalyEffects::walkway);
        AnomalyClientEffects.register(Level9Sites.CRIMSON_FIELD, Level9AnomalyEffects::field);
        AnomalyClientEffects.register(Level9Sites.CLIPPED_HOUSES, Level9AnomalyEffects::clipped);
        AnomalyClientEffects.register(Level9Sites.GONDOLA, com.rickr.backrooms.client.anomaly.level39.gondola.GondolaSiteEffect::tick);
    }

    /**
     * The temperature drops as you follow the path toward the field: frost grows from halfway
     * along and is thick by the clearing, and the picture loses its colour.
     */
    private static void walkway(Minecraft mc, com.rickr.backrooms.world.anomaly.AnomalySite site, boolean inside, float secondsInside) {
        if (!inside || mc.player == null) {
            return;
        }
        double p = Level9Sites.walkwayAlong(site, mc.player.getZ()) / Level9Sites.walkwayLength(site);
        float t = (float) Mth.clamp((p - 0.4) / 0.6, 0.0, 1.0);
        float cold = t * t * (3.0f - 2.0f * t);
        Level9Frost.request(Level9Frost.WALKWAY, cold * 0.9f);
        AtmosphereGrade.setAnomalyMood(WALKWAY_COLD, cold * 0.8f);
    }

    /** Out in the field, wherever you are: the Gloom's pulse tints the whole picture. */
    private static void field(Minecraft mc, com.rickr.backrooms.world.anomaly.AnomalySite site, boolean inside, float secondsInside) {
        if (!CrimsonFieldClient.inField(mc)) {
            return;
        }
        float pulse = CrimsonFieldClient.gloomPulse(0.0f) * (0.55f + 0.45f * CrimsonFieldClient.proximity());
        AtmosphereGrade.setAnomalyMood(FIELD_DARK.lerp(FIELD_RED, pulse), 1.0f);
    }

    private static void clipped(Minecraft mc, com.rickr.backrooms.world.anomaly.AnomalySite site, boolean inside, float secondsInside) {
        if (inside && mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9) {
            AtmosphereGrade.setAnomalyMood(CLIPPED, Math.min(1.0f, secondsInside / 2.0f) * 0.85f);
        }
    }

    private Level9AnomalyEffects() {}
}
