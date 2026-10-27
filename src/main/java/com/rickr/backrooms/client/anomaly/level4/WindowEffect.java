package com.rickr.backrooms.client.anomaly.level4;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Window (Entity 2, https://backrooms-wiki.wikidot.com/entity-2) on the client: soft,
 * comforting, unintelligible whispers coming from the glass; a warm, golden cast over the picture
 * while you look at it, like remembering a summer afternoon; and, within about two metres, a gentle
 * pull toward the glass. The pull is done here because the client moves its own player; it is
 * weaker than walking, so you can always step away. Touching the glass is handled by the server.
 */
final class WindowEffect implements AnomalyClientEffect {
    private static final double WHISPER_RANGE = 14.0;
    private static final double PULL_RANGE = 2.6;
    /** Pull per tick (blocks/tick^2) at the edge and right at the glass. */
    private static final double PULL_MIN = 0.006;
    private static final double PULL_MAX = 0.022;
    private static final double LOOK_RANGE = 12.0;
    /** Golden, soft and a little hazy: the light of a place you miss. */
    private static final AtmosphereGrade.Mood WARM = new AtmosphereGrade.Mood(1.06f, 0.94f, 1.04f,
            1.02f, 0.97f, 0.90f, 1.08f, 1.0f, 0.84f,
            0.022f, 0.018f, 0.010f, 0.55f, 0.44f, 0.30f, 5.0f, 0.020f, 0.30f, 0.30f, 0.32f, 0.012f);

    private final RandomSource random = RandomSource.create();
    private final Map<AnomalySite, Long> nextWhisper = new HashMap<>();

    /** The middle of the window's glass (same point the server uses). */
    private static Vec3 glass(AnomalySite site) {
        int facing = site.facing();
        return new Vec3(site.anchorX() + 0.5 + 0.5 * Level4AnomalyPlan.stepX(facing),
                LevelLayout.FLOOR_Y + 3.0,
                site.anchorZ() + 0.5 + 0.5 * Level4AnomalyPlan.stepZ(facing));
    }

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        Vec3 glass = glass(site);
        double dx = glass.x - player.getX();
        double dz = glass.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > WHISPER_RANGE * 1.5) {
            return;
        }
        if (!mc.isPaused()) {
            whisper(mc, site, glass, distance);
        }

        // Warmth while you look at it.
        Vec3 to = glass.subtract(player.getEyePosition());
        double length = to.length();
        if (length > 1.0e-3 && length < LOOK_RANGE) {
            double facing = player.getLookAngle().dot(to.scale(1.0 / length));
            float look = (float) Mth.clamp((facing - 0.80) / 0.18, 0.0, 1.0);
            float near = (float) (1.0 - length / LOOK_RANGE);
            if (look > 0.0f) {
                AtmosphereGrade.setAnomalyMood(WARM, 0.65f * look * near);
            }
        }

        // Come closer (not while you hide in furniture: your body is held still there).
        if (inside && distance < PULL_RANGE && distance > 0.05 && !player.isSpectator() && !mc.isPaused()
                && !com.rickr.backrooms.client.HidingClient.hiding()) {
            double t = 1.0 - distance / PULL_RANGE;
            double strength = PULL_MIN + (PULL_MAX - PULL_MIN) * t;
            player.setDeltaMovement(player.getDeltaMovement().add(dx / distance * strength, 0.0, dz / distance * strength));
        }
    }

    /** A soft whisper from the glass every few seconds, louder the closer you are. */
    private void whisper(Minecraft mc, AnomalySite site, Vec3 glass, double distance) {
        long now = System.currentTimeMillis();
        Long next = nextWhisper.get(site);
        if (next != null && now < next) {
            return;
        }
        if (nextWhisper.size() > 32) {
            nextWhisper.clear();
        }
        nextWhisper.put(site, now + 4_500L + random.nextInt(4_000));
        if (next == null || distance > WHISPER_RANGE) {
            return; // the first time only sets the clock
        }
        float volume = (float) (0.35 + 0.55 * (1.0 - distance / WHISPER_RANGE));
        mc.level.playLocalSound(glass.x, glass.y, glass.z, ModSounds.L4_WINDOW_WHISPER.get(), SoundSource.AMBIENT,
                volume, 0.92f + random.nextFloat() * 0.12f, false);
    }
}
