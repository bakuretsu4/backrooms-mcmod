package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.player.EnergyClientState;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Travel;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Your own breathing, close to the microphone: heavy panting after a sprint (it eases off as your
 * stamina comes back) and quick, shaky breaths when something is near (the same danger the
 * camcorder picture reacts to). Silent when you're rested and nothing is around. Hiding in
 * furniture you always hear yourself breathe, until you hold your breath (see HidingClient).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Breathing {
    private static final float EXERTION_VOLUME = 0.55f;
    private static final float FEAR_VOLUME = 0.45f;
    private static final float FADE = 0.03f;
    /** Hiding in furniture, your breathing is always a little audible (until you hold it). */
    private static final float HIDING_FEAR = 0.5f;
    @Nullable
    private static FadingLoopSound panting;
    @Nullable
    private static FadingLoopSound scared;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || !Travel.inBackrooms(mc.level) || !mc.player.isAlive()) {
            return; // not fed: any breathing fades away
        }
        // Out of breath: how much of the stamina bar is used up (from 70% down), full when exhausted.
        float exertion = Mth.clamp((0.7f - EnergyClientState.energy) / 0.7f, 0.0f, 1.0f);
        if (EnergyClientState.exhausted) {
            exertion = 1.0f;
        }
        float fear = Mth.clamp((CamcorderEffect.danger() - 0.2f) / 0.6f, 0.0f, 1.0f);
        if (HidingClient.holdingBreath()) {
            return; // hiding and holding your breath: not a sound
        }
        if (HidingClient.hiding()) {
            fear = Math.max(fear, HIDING_FEAR); // hiding, you hear yourself breathe (and so can a monster close by)
        }
        if (exertion > 0.02f) {
            panting = feed(mc, panting, ModSounds.BREATH_HEAVY.get(), EXERTION_VOLUME * exertion);
        }
        if (fear > 0.02f) {
            scared = feed(mc, scared, ModSounds.BREATH_SCARED.get(), FEAR_VOLUME * fear);
        }
    }

    private static FadingLoopSound feed(Minecraft mc, @Nullable FadingLoopSound sound, SoundEvent event, float volume) {
        if (sound == null || !mc.getSoundManager().isActive(sound)) {
            sound = new FadingLoopSound(event, true, 0, 0, 0, FADE);
            mc.getSoundManager().play(sound);
        }
        sound.feed(volume);
        return sound;
    }

    private Breathing() {}
}
