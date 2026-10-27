package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The sounds of hiding in furniture ({@code player/HidingService}). Registered through the shared
 * {@link ModSounds#SOUNDS} register; {@link #bootstrap()} (called from {@code Backrooms}) only
 * makes Java load this class so the fields below exist before the register fires.
 *
 * <p>The .ogg files are made by {@code tools/anomaly_assets/hiding_audio.py} and mapped (with
 * their subtitles) in {@code assets/backrooms/sounds.json}. All quiet: other players nearby hear
 * them, but they're no louder than a footstep.
 */
public final class HidingAudio {
    // A metal locker door creaking shut behind you, and creaking open again.
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_IN = register("hide.locker_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_OUT = register("hide.locker_out");
    // The same for a wooden wardrobe: a softer, lower creak and a wooden knock.
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDROBE_IN = register("hide.wardrobe_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDROBE_OUT = register("hide.wardrobe_out");
    // Crawling under a desk or a bed: clothes scuffing on the floor.
    public static final DeferredHolder<SoundEvent, SoundEvent> SHUFFLE_IN = register("hide.shuffle_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHUFFLE_OUT = register("hide.shuffle_out");
    // Letting out a held breath: a gasp, cut from the same breathing recordings as client/Breathing.
    public static final DeferredHolder<SoundEvent, SoundEvent> GASP = register("hide.gasp");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers the sounds above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private HidingAudio() {}
}
