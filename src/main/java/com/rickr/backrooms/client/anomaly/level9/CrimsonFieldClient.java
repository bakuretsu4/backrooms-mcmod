package com.rickr.backrooms.client.anomaly.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.level9.CrimsonField;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Level 9.1, "The Crimson Field" (http://backrooms-mghc.wikidot.com/level-9-1), on the client.
 *
 * <ul>
 *   <li><b>The Gloom:</b> a red glow low on the eastern horizon that "pulses on and off every 10
 *       seconds" and turns the mist crimson. The pulse runs on the world clock, so everyone sees it
 *       together; {@code Level9Sky} draws the glow and {@code Level9Atmosphere} tints the fog with
 *       {@link #gloomPulse} and {@link #proximity}. A faint low throb comes with each pulse.</li>
 *   <li><b>Being dragged in:</b> the same rule as the server ({@link CrimsonField#GLOOM_REACH}
 *       blocks east of where you arrived): the red creeps over the picture as you get close, and
 *       once it has you the screen goes red, then black.</li>
 *   <li><b>Voices from nowhere:</b> every 20-45 s, quietly, somewhere in the grass around you:
 *       radio chatter, distorted elevator music or someone talking backwards.</li>
 *   <li><b>The cold:</b> frost at the edges of the picture and your breath, all the time.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class CrimsonFieldClient {
    /** One Gloom cycle: 10 s (200 ticks). */
    private static final int PULSE_TICKS = 200;
    /** The red starts creeping over the picture this far before the Gloom's reach (blocks). */
    private static final double WARN_DISTANCE = 60.0;
    /** How long the screen takes to go from red to black once it has you. */
    private static final float DRAG_MS = 3200.0f;
    private static final float FIELD_FROST = 0.42f;

    private static final RandomSource RANDOM = RandomSource.create();
    private static double entryX = Double.NaN;
    private static long dragStartMs;
    private static long nextVoiceMs;
    private static long lastPulse = Long.MIN_VALUE;

    /** True when the local player is out in the field. */
    public static boolean inField(Minecraft mc) {
        return mc.level != null && mc.player != null
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9
                && Level9Sites.inField(mc.player.getX());
    }

    /**
     * 0..1 how bright the Gloom is right now: on for about 4.5 s of every 10 (swelling up over a
     * second, fading over two), then dark.
     */
    public static float gloomPulse(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0.0f;
        }
        float t = ((mc.level.getGameTime() % PULSE_TICKS) + partialTick) / PULSE_TICKS;
        float level;
        if (t < 0.10f) {
            level = t / 0.10f;
        } else if (t < 0.30f) {
            level = 1.0f;
        } else if (t < 0.48f) {
            level = 1.0f - (t - 0.30f) / 0.18f;
        } else {
            level = 0.0f;
        }
        return level * level * (3.0f - 2.0f * level);
    }

    /** 0..1 how far toward the Gloom you have walked (1 = it is about to take you). */
    public static float proximity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Double.isNaN(entryX)) {
            return 0.0f;
        }
        return (float) Mth.clamp((mc.player.getX() - entryX) / CrimsonField.GLOOM_REACH, 0.0, 1.0);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inField(mc)) {
            entryX = Double.NaN;
            dragStartMs = 0L;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (Double.isNaN(entryX)) {
            entryX = mc.player.getX(); // the server counts from the same spot: where you arrived
            nextVoiceMs = now + 6000L + RANDOM.nextInt(6000);
        }
        Level9Frost.request(Level9Frost.FIELD, FIELD_FROST);

        // The Gloom: a warning glow over the picture as you near it, then red to black.
        double east = mc.player.getX() - entryX;
        float pulse = gloomPulse(0.0f);
        if (east >= CrimsonField.GLOOM_REACH) {
            if (dragStartMs == 0L) {
                dragStartMs = now;
            }
            float p = Math.min(1.0f, (now - dragStartMs) / DRAG_MS);
            Level9Frost.requestGloom(0.45f + 0.55f * Math.min(1.0f, p * 2.0f), Math.max(0.0f, (p - 0.35f) / 0.65f));
        } else {
            dragStartMs = 0L;
            double warn = (east - (CrimsonField.GLOOM_REACH - WARN_DISTANCE)) / WARN_DISTANCE;
            if (warn > 0.0) {
                float w = (float) Math.min(1.0, warn);
                Level9Frost.requestGloom(w * w * (0.18f + 0.22f * pulse), 0.0f);
            }
        }

        // A faint throb as each pulse begins, from the Gloom's direction (east).
        long pulseIndex = mc.level.getGameTime() / PULSE_TICKS;
        if (pulseIndex != lastPulse) {
            boolean first = lastPulse == Long.MIN_VALUE;
            lastPulse = pulseIndex;
            if (!first) {
                Vec3 eye = mc.player.getEyePosition();
                play(mc, ModSounds.L9_GLOOM_THROB.get(), eye.x + 14.0, eye.y, eye.z, 1.4f + proximity(), 0.9f);
            }
        }

        // Voices from nowhere.
        if (now >= nextVoiceMs) {
            nextVoiceMs = now + 20000L + RANDOM.nextInt(25000);
            SoundEvent sound = switch (RANDOM.nextInt(3)) {
                case 0 -> ModSounds.L9_FIELD_RADIO.get();
                case 1 -> ModSounds.L9_FIELD_ELEVATOR.get();
                default -> ModSounds.L9_FIELD_REVERSED.get();
            };
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            double distance = 9.0 + RANDOM.nextDouble() * 9.0;
            Vec3 eye = mc.player.getEyePosition();
            play(mc, sound, eye.x + Mth.cos(angle) * distance, eye.y - 0.5, eye.z + Mth.sin(angle) * distance,
                    1.6f + RANDOM.nextFloat() * 0.6f, 0.92f + RANDOM.nextFloat() * 0.12f);
        }
    }

    /** Volume above 1 widens the hearing range (16 blocks per 1.0), so these stay faint but audible. */
    private static void play(Minecraft mc, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, RANDOM, x, y, z));
    }

    private CrimsonFieldClient() {}
}
