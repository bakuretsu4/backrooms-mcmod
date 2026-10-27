package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Sounds;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sound of Level 39's air (client side).
 *
 * <ul>
 *   <li><b>Ambient loops by zone.</b> The town: a vast enclosed space, a very low industrial thrum
 *       from the superstructure, faint wind in the wires and far echoes; quiet, huge. The tunnels:
 *       still water lapping, soft drips, everything damped by the marble. Dropping down a hole
 *       cross-fades from one to the other (the level's biome has no ambient sound of its own, so
 *       this is the only loop).</li>
 *   <li><b>Rain inside</b> while the indoor storm runs ({@link Level39Weather}): the hiss of rain
 *       on the whole enormous town and on the superstructure's glass; through the rock in the
 *       tunnels only a faint murmur.</li>
 *   <li><b>Radios are useless</b> (canon): any radio sound (the checkpoint radio, radio static,
 *       radio chatter) is swapped for a garbled, echoing mush.</li>
 * </ul>
 * The far-off one-off sounds (groans, slams, a telephone in an empty house...) come from
 * {@code client/audio/DistantAmbience}; the quake sounds from {@link Level39QuakeEffects}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39Audio {
    /** Loudest volumes of the loops (1 = full). Quiet: the level is about silence and size. */
    private static final float TOWN_VOLUME = 0.75f;
    /** The tunnels are damped (canon), but not the quietest place in the Backrooms: full volume here. */
    private static final float TUNNEL_VOLUME = 1.0f;
    private static final float RAIN_VOLUME = 0.8f;
    /** In the tunnels the storm overhead is only a murmur through the rock. */
    private static final float RAIN_IN_TUNNEL = 0.15f;
    /** While it rains the town's own thrum sinks a little under the rain. */
    private static final float TOWN_UNDER_RAIN = 0.6f;
    /** Fade speed of the loops (volume per tick): about 2.5 s from silence to full. */
    private static final float FADE_PER_TICK = 0.02f;

    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private static Level39LoopSound town;
    @Nullable
    private static Level39LoopSound tunnel;
    @Nullable
    private static Level39LoopSound rainLoop;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Level39ClientState.inLevel39()) {
            return; // the loops starve and fade out by themselves
        }
        float inTunnel = Level39Atmosphere.tunnel();
        float rain = Level39Weather.rain();
        town = feed(mc, town, Level39Sounds.TOWN_AMBIENCE.get(),
                TOWN_VOLUME * (1.0f - inTunnel) * (1.0f - (1.0f - TOWN_UNDER_RAIN) * rain));
        tunnel = feed(mc, tunnel, Level39Sounds.TUNNEL_AMBIENCE.get(), TUNNEL_VOLUME * inTunnel);
        rainLoop = feed(mc, rainLoop, Level39Sounds.RAIN_INSIDE.get(),
                RAIN_VOLUME * rain * (1.0f - inTunnel * (1.0f - RAIN_IN_TUNNEL)));
    }

    /** Keeps one loop playing (restarting it if the sound engine dropped it) and feeds its volume. */
    @Nullable
    private static Level39LoopSound feed(Minecraft mc, @Nullable Level39LoopSound loop, SoundEvent sound, float volume) {
        if (volume <= 0.001f && (loop == null || loop.isStopped())) {
            return loop; // nothing to play and nothing playing
        }
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
            loop = new Level39LoopSound(sound, FADE_PER_TICK);
            mc.getSoundManager().play(loop);
        }
        loop.feed(volume);
        return loop;
    }

    /** Radios are useless here: their sound comes out as garbled echoing mush. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !isRadio(sound.getLocation()) || !Level39ClientState.inLevel39()) {
            return;
        }
        event.setSound(new SimpleSoundInstance(Level39Sounds.RADIO_GARBLED.get().getLocation(), sound.getSource(),
                1.0f, 0.9f + RANDOM.nextFloat() * 0.2f, RANDOM, false, 0, sound.getAttenuation(),
                sound.getX(), sound.getY(), sound.getZ(), sound.isRelative()));
    }

    private static boolean isRadio(ResourceLocation id) {
        return id.equals(ModSounds.CHECKPOINT_SAVE.getId()) || id.equals(ModSounds.RADIO_STATIC.getId())
                || id.equals(ModSounds.L9_FIELD_RADIO.getId());
    }

    private Level39Audio() {}
}
