package com.rickr.backrooms.client.anomaly.level37;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The warning of a void opening ({@code level_37/void_opening}, Fandom Level 37.2,
 * https://backrooms.fandom.com/wiki/Level_37.2): the nearer you get, the more the glare
 * swallows the picture (overexposed, washed out, a white veil over everything) and the louder a
 * high soft tone sings out of the gap. Over the lip, inside the gap, the picture goes completely
 * white; the server then takes you ({@code world/anomaly/level37/VoidOpening}).
 */
final class VoidOpeningEffect implements AnomalyClientEffect {
    /** The glare starts this far from the gap (blocks) ... */
    private static final double GLARE_RANGE = 11.0;
    /** ... and reaches this much white veil right at the lip (you can still see your way back). */
    private static final float GLARE_AT_LIP = 0.55f;
    private static final float TONE_MIN = 0.06f;
    private static final float TONE_MAX = 0.8f;
    /**
     * The picture in the glare: overexposed, low contrast, colour bleached out, white air and a
     * heavy glow (exposure, contrast, saturation | shadow tint | highlight tint | lift | haze
     * colour, start, density, max | contact shadows, glow, breath).
     */
    private static final AtmosphereGrade.Mood GLARE = new AtmosphereGrade.Mood(1.3f, 0.75f, 0.6f,
            1.04f, 1.03f, 1.0f, 1.08f, 1.05f, 0.98f,
            0.07f, 0.068f, 0.062f, 1.0f, 0.98f, 0.92f, 1.5f, 0.07f, 0.65f, 0.12f, 0.7f, 0.012f);

    private final Map<AnomalySite, SiteLoopSound> tones = new HashMap<>();

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        double d = Level37AnomalyPlan.distanceToOpening(site, player.getX(), player.getZ());
        float near = (float) Mth.clamp((GLARE_RANGE - d) / (GLARE_RANGE - 0.5), 0.0, 1.0);
        near = near * near * (3.0f - 2.0f * near);
        if (near <= 0.0f) {
            return;
        }
        boolean inGap = inGap(site, player);
        Level37AnomalyOverlay.requestWhite(inGap ? 1.0f : near * near * GLARE_AT_LIP);
        AtmosphereGrade.setAnomalyMood(GLARE, inGap ? 1.0f : near);

        SiteLoopSound tone = tones.get(site);
        if (tone == null || tone.isStopped()) {
            tone = new SiteLoopSound(ModSounds.L37_VOID_TONE.get(), Level37AnomalyPlan.faceCentre(site));
            tones.put(site, tone);
            mc.getSoundManager().play(tone);
        }
        // Louder, and a touch higher, the closer you are.
        tone.feed(inGap ? 1.0f : Mth.lerp(near, TONE_MIN, TONE_MAX), 1.0f + 0.08f * near);
        tones.values().removeIf(SiteLoopSound::isStopped);
    }

    /** Over the lip and inside the gap (same test as the server's). */
    private static boolean inGap(AnomalySite site, LocalPlayer player) {
        double front = Level37AnomalyPlan.inFront(site, player.getX(), player.getZ());
        double across = Level37AnomalyPlan.across(site, player.getX(), player.getZ());
        return front < 2.0 && across > 0.0 && across < Level37AnomalyPlan.GAP_WIDTH
                && player.getY() >= Level37AnomalyPlan.GAP_BOTTOM_Y - 0.2;
    }
}
