package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The sounds of Level 39 "Megalophobia": the air of the town and the tunnels, the indoor storm,
 * the echo quakes and the far-off one-off noises. Registered through the shared
 * {@link ModSounds#SOUNDS} register; {@link #bootstrap()} (called from {@code Backrooms}) only makes
 * Java load this class so the fields below exist before the register fires.
 *
 * <p>The .ogg files are made by {@code tools/anomaly_assets/level39_audio.py} and mapped (with
 * their subtitles) in {@code assets/backrooms/sounds.json}.
 */
public final class Level39Sounds {
    // Ambient loops, switched by where you are (client/Level39Audio): the enclosed town (a very
    // low thrum of the superstructure, wind in the wires, faraway echoes), the marble tunnels
    // (still water lapping, soft drips, damped), and the indoor storm's rain on everything.
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWN_AMBIENCE = register("ambient.level39_town");
    public static final DeferredHolder<SoundEvent, SoundEvent> TUNNEL_AMBIENCE = register("ambient.level39_tunnel");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_INSIDE = register("ambient.level39_rain");

    // Echo quakes (client/Level39QuakeEffects): the boom that starts it (also each of its echoes),
    // the deep rumble of the quake, and every window in town rattling at its peak.
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_BOOM = register("level39.echo_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUAKE_RUMBLE = register("level39.quake_rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_RATTLE = register("level39.window_rattle");
    // Radios are useless here: any radio plays this garbled, echoing mush instead.
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_GARBLED = register("level39.radio_garbled");

    // Far-off one-off sounds (client/audio/DistantAmbience). In town: the superstructure groaning,
    // a metal door slamming somewhere inside it with a very long echo, a dog barking that echoes
    // back off the facades, a screen door, a wind chime, a telephone ringing in an empty house and
    // coal ash rattling against the facade glass. In the tunnels: drips and something moving in
    // the water far down the tunnel. (House screams and hole noises belong to the anomaly code.)
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_GROAN = register("distant.level39.superstructure_groan");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_METAL_SLAM = register("distant.level39.metal_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_DOG_BARK = register("distant.level39.dog_bark");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_SCREEN_DOOR = register("distant.level39.screen_door");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_WIND_CHIME = register("distant.level39.wind_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_TELEPHONE = register("distant.level39.telephone");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_ASH_RATTLE = register("distant.level39.ash_rattle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_TUNNEL_DRIP = register("distant.level39.tunnel_drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_TUNNEL_SPLASH = register("distant.level39.tunnel_splash");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers the sounds above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private Level39Sounds() {}
}
