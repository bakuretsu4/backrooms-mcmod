package com.rickr.backrooms.client.anomaly.level39;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.anomaly.level39.Level39AnomalyRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * How the Hydrolitis plague feels (the server decides how sick you are and keeps the
 * {@code backrooms:hydrolitis} effect on you; its amplifier is the stage). Deliberately subtle: no
 * overlay, no static, only a feverish warmth in the colours, a slow sway of the head and the sounds
 * of your own body.
 * <ol>
 *   <li>Fever: the picture warms and its exposure drifts a little; chills now and then.</li>
 *   <li>Weakness: the head sways; your heart races in your ears.</li>
 *   <li>Nausea: the sway grows (the server makes you throw up).</li>
 *   <li>Delirium: whispers from nowhere, and a hound growling somewhere close that isn't there.</li>
 * </ol>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class HydrolitisClient {
    // exposure, contrast, saturation | shadow tint | highlight tint | lift | haze colour, start, density, max | ao, glow, breath
    /** Feverish: warm, slightly flushed, softly glowing, with the exposure unable to settle. */
    private static final AtmosphereGrade.Mood FEVER = new AtmosphereGrade.Mood(1.03f, 0.98f, 0.92f,
            1.06f, 0.97f, 0.90f, 1.06f, 0.99f, 0.90f, 0.020f, 0.014f, 0.010f,
            0.16f, 0.12f, 0.09f, 8.0f, 0.026f, 0.38f, 0.32f, 0.34f, 0.035f);
    /** How strongly the fever look shows at each stage (0..4). */
    private static final float[] MOOD_WEIGHT = {0.0f, 0.22f, 0.32f, 0.42f, 0.55f};
    /** Head sway (degrees of roll) at each stage. */
    private static final float[] SWAY = {0.0f, 0.25f, 0.6f, 1.0f, 1.5f};

    private static final RandomSource RANDOM = RandomSource.create();
    private static long nextChills;
    private static long nextHeart;
    private static long nextWhisper;
    private static long nextPhantom;

    private HydrolitisClient() {}

    /** 0 healthy, 1 fever .. 4 delirium. */
    public static int stage() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        MobEffectInstance e = player.getEffect(Level39AnomalyRegistry.HYDROLITIS);
        return e == null ? 0 : Math.min(4, e.getAmplifier() + 1);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        int s = stage();
        if (s > 0) {
            AtmosphereGrade.setAnomalyMood(FEVER, MOOD_WEIGHT[s]);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        int s = stage();
        if (s == 0) {
            return;
        }
        double t = event.getRenderer().getMinecraft().level == null ? 0.0
                : (event.getRenderer().getMinecraft().level.getGameTime() + event.getPartialTick()) / 20.0;
        float roll = (float) (Math.sin(t * 0.9) + 0.35 * Math.sin(t * 2.3 + 1.0)) * SWAY[s];
        event.setRoll(event.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        int s = stage();
        if (s >= 2 && event.usedConfiguredFov()) {
            double t = System.currentTimeMillis() / 1000.0;
            event.setFOV(event.getFOV() * (1.0 + 0.006 * s * Math.sin(t * 0.7)));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        int s = stage();
        long now = System.currentTimeMillis();
        if (s == 0) {
            nextChills = nextHeart = nextWhisper = nextPhantom = 0L;
            return;
        }
        if (now >= nextChills) {
            if (nextChills != 0L) {
                self(mc, Level39AnomalyRegistry.HYDROLITIS_CHILLS.get(), 0.7f);
            }
            nextChills = now + 22_000L + RANDOM.nextInt(26_000);
        }
        if (s >= 2 && now >= nextHeart) {
            if (nextHeart != 0L) {
                self(mc, Level39AnomalyRegistry.HYDROLITIS_HEARTBEAT.get(), 0.8f);
            }
            nextHeart = now + 14_000L + RANDOM.nextInt(16_000);
        }
        if (s >= 4 && now >= nextWhisper) {
            if (nextWhisper != 0L) {
                around(mc, Level39AnomalyRegistry.HYDROLITIS_WHISPER.get(), 3.0, 6.0, 0.8f);
            }
            nextWhisper = now + 12_000L + RANDOM.nextInt(20_000);
        }
        if (s >= 4 && now >= nextPhantom) {
            if (nextPhantom != 0L) {
                around(mc, Level39Mobs.HOUND_GROWL.get(), 10.0, 16.0, 0.9f);
            }
            nextPhantom = now + 40_000L + RANDOM.nextInt(40_000);
        }
    }

    /** A sound from your own body (right at your head). */
    private static void self(Minecraft mc, SoundEvent sound, float volume) {
        LocalPlayer p = mc.player;
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), sound, SoundSource.PLAYERS, volume, 0.95f + RANDOM.nextFloat() * 0.1f, false);
    }

    /** A sound that isn't there, somewhere around you. */
    private static void around(Minecraft mc, SoundEvent sound, double min, double max, float volume) {
        LocalPlayer p = mc.player;
        double a = RANDOM.nextDouble() * Math.PI * 2.0;
        double d = Mth.lerp(RANDOM.nextDouble(), min, max);
        mc.level.playLocalSound(p.getX() + Math.cos(a) * d, p.getEyeY(), p.getZ() + Math.sin(a) * d, sound,
                SoundSource.AMBIENT, volume, 0.9f + RANDOM.nextFloat() * 0.15f, false);
    }
}
