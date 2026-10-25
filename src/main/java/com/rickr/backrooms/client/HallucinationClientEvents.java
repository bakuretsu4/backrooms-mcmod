package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.HallucinationClientState;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The client half of the Poolrooms hallucination: smooths the dream value the server sends, feeds
 * it to the camcorder shader ({@link #shaderDream()}) and plays the hallucination loop while you
 * are dreaming.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class HallucinationClientEvents {
    /** The loop starts above this dream value and fades out below {@link #LOOP_STOP}. */
    private static final float LOOP_START = 0.15f;
    private static final float LOOP_STOP = 0.12f;
    private static final float LOOP_MAX_VOLUME = 0.6f;
    /** The "pop" when your phantom friend vanishes: a short wobble of the dream shader. */
    private static final float PULSE_MS = 1200.0f;
    private static final float PULSE_STRENGTH = 0.7f;

    /** Dream as shown on screen: follows the server value smoothly (it only arrives every ~10 ticks). */
    private static float shownDream;
    private static HallucinationLoopSound loop;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // Disconnected: forget everything so the next world starts clear-headed.
            HallucinationClientState.dream = 0.0f;
            shownDream = 0.0f;
            if (loop != null) {
                loop.release();
                loop = null;
            }
            return;
        }
        float target = HallucinationClientState.dream;
        shownDream += (target - shownDream) * 0.08f; // about a second to catch up
        if (target == 0.0f && shownDream < 0.002f) {
            shownDream = 0.0f; // land exactly on 0 so the shader is exactly untouched
        }

        if (shownDream > LOOP_START && (loop == null || loop.released())) {
            loop = new HallucinationLoopSound();
            mc.getSoundManager().play(loop);
        } else if (shownDream < LOOP_STOP && loop != null) {
            loop.release();
            loop = null;
        }
    }

    /** Dream strength for the shader (0..1): the smoothed value plus the brief vanish pulse. */
    public static float shaderDream() {
        long since = System.currentTimeMillis() - HallucinationClientState.vanishPulseMs;
        float pulse = 0.0f;
        if (since >= 0 && since < PULSE_MS) {
            float k = since / PULSE_MS;
            pulse = Math.min(1.0f, k * 8.0f) * (1.0f - k) * (1.0f - k); // fast rise, slow fade
        }
        return Mth.clamp(shownDream + pulse * PULSE_STRENGTH, 0.0f, 1.0f);
    }

    /** Loop volume: silent at {@link #LOOP_START}, full at dream 1. */
    private static float loopVolume() {
        float t = Mth.clamp((shownDream - LOOP_START) / (1.0f - LOOP_START), 0.0f, 1.0f);
        return LOOP_MAX_VOLUME * t;
    }

    /**
     * The hallucination loop: plays "inside your head" (no position), volume following the dream,
     * fades out smoothly when released instead of cutting off.
     */
    private static final class HallucinationLoopSound extends AbstractTickableSoundInstance {
        private boolean released;

        HallucinationLoopSound() {
            super(ModSounds.HALLUCINATION_LOOP.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true; // follows the listener: not a sound in the world
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.volume = 0.001f;
        }

        void release() {
            released = true;
        }

        boolean released() {
            return released;
        }

        @Override
        public boolean canStartSilent() {
            return true; // starts at ~0 volume and fades in
        }

        @Override
        public void tick() {
            float goal = released ? 0.0f : loopVolume();
            volume += (goal - volume) * 0.1f;
            if (released && volume < 0.005f) {
                stop();
            }
        }
    }

    private HallucinationClientEvents() {}
}
