package com.rickr.backrooms.client.audio;

import com.mojang.logging.LogUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

/**
 * The OpenAL "EFX" side of {@link Acoustics}: one shared room reverb, and per-sound filters.
 *
 * <p>OpenAL (the audio library under Minecraft) can run sounds through effects. We make one
 * reverb (an "auxiliary effect slot") whose size and tone follow the room you are in, and send
 * every world sound into it. Each sound also gets a low-pass filter on its direct path: when walls
 * are between you and the sound, the filter takes away its highs (and some volume), so it sounds
 * muffled, like through a wall. Filters are copied onto a sound when attached, so one scratch
 * filter object serves every sound.
 *
 * <p><b>Only call this on the sound thread</b> (inside a channel's execute, a sound-source event,
 * or the sound engine's executor): OpenAL's objects here are not shared with other threads.
 * If the sound card's driver has no EFX, everything here quietly does nothing.
 */
final class AcousticsEfx {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The OpenAL context the objects below belong to (they die with it when sound restarts). */
    private long context;
    private boolean available;
    private int slot;
    private int reverb;
    private int directFilter;
    private int sendFilter;

    /** Makes sure the effect objects exist for the current OpenAL context; false if EFX is missing. */
    boolean ready() {
        long current = ALC10.alcGetCurrentContext();
        if (current == 0L) {
            return false;
        }
        if (current == context) {
            return available;
        }
        // A new context (first sound, or Minecraft restarted its sound system): build everything.
        context = current;
        available = false;
        try {
            if (!AL.getCapabilities().ALC_EXT_EFX) {
                LOGGER.info("Backrooms acoustics: OpenAL EFX not available, no reverb or wall muffling");
                return false;
            }
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            reverb = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverb, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            directFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(directFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            sendFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, 0.0f);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.warn("Backrooms acoustics: OpenAL error {} while setting up EFX; disabled", error);
                return false;
            }
            available = true;
            LOGGER.info("Backrooms acoustics: OpenAL EFX ready (room reverb and wall muffling on)");
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Backrooms acoustics: could not set up OpenAL EFX; disabled", e);
        }
        return available;
    }

    /** Sets the room reverb (shared by every sound). {@code wet} 0 switches it off. */
    void setRoom(Acoustics.Room room) {
        if (!ready()) {
            return;
        }
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DENSITY, room.density());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DIFFUSION, room.diffusion());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_GAIN, 0.6f);
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_GAINHF, room.gainHf());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DECAY_TIME, room.decay());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DECAY_HFRATIO, room.decayHfRatio());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, room.reflections());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, room.reflectionsDelay());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, room.late());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, room.lateDelay());
        EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_AIR_ABSORPTION_GAINHF, 0.994f);
        // Changed parameters only reach the slot when the effect is attached again.
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverb);
        EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, room.wet());
        AL10.alGetError(); // a bad value must not leave an error behind for Minecraft's own checks
    }

    /**
     * Applies wall muffling to one sound: its direct path keeps {@code gain} of its volume and
     * {@code gainHf} of its highs; its share of the reverb is muffled a little less (sound finds
     * its way round corners into the room you are in).
     */
    void applyToSource(int source, float gain, float gainHf, boolean reverbSend) {
        if (!ready()) {
            return;
        }
        EXTEfx.alFilterf(directFilter, EXTEfx.AL_LOWPASS_GAIN, gain);
        EXTEfx.alFilterf(directFilter, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, directFilter);
        if (reverbSend) {
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, (float) Math.sqrt(gain));
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, (float) Math.sqrt(gainHf));
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, sendFilter);
        } else {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
        }
        AL10.alGetError(); // the source may have just been deleted: ignore, never let it leak
    }
}
