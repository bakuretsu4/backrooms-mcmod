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
    // Loops placed on things in the corridor: the steady hiss of a leaking steam valve, and the
    // hum and slow thump of a pump in a pump bay.
    public static final DeferredHolder<SoundEvent, SoundEvent> STEAM_LEAK = register("level2.steam_leak");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_HUM = register("level2.pump_hum");
    // A drop of water from a leaking pipe landing in the gutter.
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP = register("level2.drip");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers the sounds above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private Level2Audio() {}
}
