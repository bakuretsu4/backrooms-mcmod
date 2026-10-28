package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The sounds of Level 2's long pipe corridor (the linear revamp), played by
 * {@code client/anomaly/level2/Level2Emitters}. Registered through the shared
 * {@link ModSounds#SOUNDS} register; {@link #bootstrap()} (called from {@code Backrooms}) only
 * makes Java load this class so the fields below exist before the register fires.
 *
 * <p>The .ogg files are made by {@code tools/anomaly_assets/level2_audio.py} and mapped (with
 * their subtitles) in {@code assets/backrooms/sounds.json}. The level's ambience loop, the
 * owner's friend's recorded lamp hums and the older Level 2 sounds in {@link ModSounds} stay
 * as they were.
 */
public final class Level2Audio {
    // Pressure in the pipes, heard up and down the corridor: a water-hammer knock (several in a
    // row, running along the pipes), a heavy ringing clank, a long creak of hot metal stretching,
    // and water rushing through the overhead bundle past you.
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_KNOCK = register("level2.pipe_knock");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_CLANK = register("level2.pipe_clank");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_CREAK = register("level2.pipe_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_RUSH = register("level2.pipe_rush");
    // Loops placed on things in the corridor: the steady hiss of a leaking steam valve, the hum
    // and slow thump of a pump in a pump bay, the deep bed of heavy machinery (fans, machines and
    // the plant behind the walls) and the buzz of a live breaker panel.
    public static final DeferredHolder<SoundEvent, SoundEvent> STEAM_LEAK = register("level2.steam_leak");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_HUM = register("level2.pump_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANT_HUM = register("level2.plant_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PANEL_BUZZ = register("level2.panel_buzz");
    // Far metal and vibration: a pipe struck with a tool somewhere down the line (a few at a
    // time, creeping closer along the pipes), and a shudder running along a pipe run past you.
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_TAP = register("level2.pipe_tap");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_SHUDDER = register("level2.pipe_shudder");
    // A relief valve lifting now and then: a clunk and a burst of steam.
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESSURE_RELEASE = register("level2.pressure_release");
    // A drop of water from a leak landing: in a puddle or the wet gutter, on steel (a grating, a
    // pipe), or on bare wet concrete. (In black ooze it is ModSounds.L2_OOZE_DRIP.)
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP_PUDDLE = register("level2.drip_puddle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP_METAL = register("level2.drip_metal");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP_CONCRETE = register("level2.drip_concrete");
    // The chambers' oddities (played by client/anomaly/level2/Level2OddityEffects): the dry tick of
    // a gauge needle that keeps time with your heart, and the warm breath behind a door to nowhere.
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUGE_TICK = register("level2.gauge_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARM_DOOR = register("level2.warm_door");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers the sounds above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private Level2Audio() {}
}
