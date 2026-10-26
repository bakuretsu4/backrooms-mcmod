package com.rickr.backrooms.client.anomaly.poolrooms;

import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.mojang.blaze3d.audio.Channel;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;

/**
 * The strange acoustics of the two pool levels (the Poolrooms and Level 37), on the client.
 *
 * <p>Canon (both wikis, Level 37 "Sublimity": https://backrooms-wiki.wikidot.com/level-37,
 * https://backrooms.fandom.com/wiki/Level_37): noises "suddenly drop off", nothing reverberates
 * properly and speech sounds muted; the water itself is usually calm and quiet, but sometimes it
 * becomes "obtrusive" and drowns out every other sound.
 * <ul>
 *   <li><b>Dampening</b>, always: every sound placed in the world (steps, doors, voices,
 *       anything that isn't water) carries only half as far as normal and plays a bit quieter,
 *       so sounds die out a few blocks away. The water's own sounds are left alone.</li>
 *   <li><b>Obtrusive water</b>, now and then: the server starts a floor event
 *       ({@link LevelEventPayload#OBTRUSIVE_WATER}, every 6-12 minutes, 20-40 s; force it with
 *       {@code /backrooms event water}). A loud rush of lapping, sloshing water swells up
 *       around you and every other sound shrinks to a murmur until it calms down again.</li>
 * </ul>
 *
 * <p>How: Minecraft hands each sound to OpenAL as a "channel"; right after a sound starts,
 * NeoForge tells us its channel (PlaySoundSourceEvent, on the sound thread). We shorten how far
 * the channel carries (its linear fall-off distance) and turn its volume down. Looping sounds that
 * update their own volume every tick (lamp hums) keep their volume but still lose their reach.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class PoolAcoustics {
    /** Normal dampening: sounds carry half as far ... */
    private static final float RANGE = 0.5f;
    /** ... and play at 70% volume. */
    private static final float VOLUME = 0.7f;
    /** While the water is obtrusive: 30% of the range and a quarter of the volume. */
    private static final float OBTRUSIVE_RANGE = 0.3f;
    private static final float OBTRUSIVE_VOLUME = 0.25f;
    /** No sound is cut shorter than this (blocks), so a voice right next to you is still heard. */
    private static final float MIN_RANGE = 4.0f;
    /** The obtrusive water swells in and calms down over this long (ms). */
    private static final float SWELL_MS = 5000.0f;
    /** Loudest the obtrusive water gets. */
    private static final float OBTRUSIVE_MAX_VOLUME = 1.0f;

    /**
     * Read on the sound thread, written on the client thread: plain volatile numbers. 1 = no
     * change (outside the pool levels).
     */
    private static volatile float rangeScale = 1.0f;
    private static volatile float volumeScale = 1.0f;

    private static FadingLoopSound obtrusive;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BackroomsLevel floor = mc.level == null ? null : BackroomsLevel.fromDimension(mc.level.dimension());
        if (floor != BackroomsLevel.POOLROOMS && floor != BackroomsLevel.LEVEL_37) {
            rangeScale = 1.0f;
            volumeScale = 1.0f;
            return; // the obtrusive loop, if any, fades out by itself
        }
        float swell = obtrusiveSwell(System.currentTimeMillis());
        rangeScale = Mth.lerp(swell, RANGE, OBTRUSIVE_RANGE);
        volumeScale = Mth.lerp(swell, VOLUME, OBTRUSIVE_VOLUME);
        if (swell > 0.0f) {
            if (obtrusive == null || obtrusive.isStopped() || !mc.getSoundManager().isActive(obtrusive)) {
                obtrusive = new FadingLoopSound(ModSounds.POOL_OBTRUSIVE_WATER.get(), true, 0.0, 0.0, 0.0, 0.05f);
                mc.getSoundManager().play(obtrusive);
            }
            obtrusive.feed(swell * OBTRUSIVE_MAX_VOLUME);
        }
    }

    /** 0..1: how loud the obtrusive water is right now (fading in at the start, out at the end). */
    private static float obtrusiveSwell(long nowMs) {
        if (CameraClientState.eventKind != LevelEventPayload.OBTRUSIVE_WATER || nowMs >= CameraClientState.eventEndMs) {
            return 0.0f;
        }
        float in = (nowMs - CameraClientState.eventStartMs) / SWELL_MS;
        float out = (CameraClientState.eventEndMs - nowMs) / SWELL_MS;
        float t = Mth.clamp(Math.min(in, out), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @SubscribeEvent
    public static void onPlaySource(PlaySoundSourceEvent event) {
        dampen(event.getSound(), event.getChannel());
    }

    @SubscribeEvent
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        dampen(event.getSound(), event.getChannel());
    }

    /** Runs on the sound thread, right after the sound started playing on {@code channel}. */
    private static void dampen(SoundInstance sound, Channel channel) {
        float range = rangeScale;
        float volume = volumeScale;
        if (range >= 1.0f || sound.isRelative() || sound.getAttenuation() != SoundInstance.Attenuation.LINEAR
                || isExempt(sound.getLocation().getPath())) {
            return; // outside the pool levels; music, UI and ambience loops "in your head"; water
        }
        // The same reach Minecraft gave it (louder than 1 = carries further), then cut short.
        float reach = Math.max(sound.getVolume(), 1.0f) * sound.getSound().getAttenuationDistance();
        channel.linearAttenuation(Math.max(MIN_RANGE, reach * range));
        if (!(sound instanceof TickableSoundInstance)) {
            // Minecraft's own volume for it (sound volume x its category slider), turned down.
            SoundSource source = sound.getSource();
            float category = source == SoundSource.MASTER ? 1.0f : Minecraft.getInstance().options.getSoundSourceVolume(source);
            channel.setVolume(Mth.clamp(sound.getVolume() * category, 0.0f, 1.0f) * volume);
        }
    }

    /**
     * Sounds that keep their full reach: the water's own (it is the one thing that stays clear,
     * and the one that turns loud), our anomaly sounds (the faint chants must carry) and
     * Level 37's warning cues ({@code level37.*}, played client-side by that level).
     */
    private static boolean isExempt(String path) {
        return path.startsWith("anomaly.poolrooms") || path.startsWith("level37.")
                || path.contains("water") || path.contains("swim") || path.contains("splash")
                || path.contains("wade") || path.contains("bubble") || path.contains("drip")
                || path.contains("drain") || path.contains("pool");
    }

    private PoolAcoustics() {}
}
