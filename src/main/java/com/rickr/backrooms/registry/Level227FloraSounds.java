package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The sounds of Level 227's plants: the leaves and grass, the GammaPali's gas, the Palette's drop
 * and the giant Venus flytrap. (The level's ambience, wind over the void and the sun's voice are
 * in Level227Sounds.) Registered through the shared {@link ModSounds#SOUNDS} register.
 *
 * <p>The .ogg files are made by {@code tools/anomaly_assets/level227_flora.py} and mapped (with
 * their subtitles) in {@code assets/backrooms/sounds.json}.
 */
public final class Level227FloraSounds {
    // The forest: a gust hissing through a canopy of big leaves (Level227LeavesBlock), and the dry
    // swish of pushing through tall grass or picking Lestron.
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAF_WIND = register("l227.flora.leaf_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUSTLE = register("l227.flora.rustle");
    // GammaPali: the soft hiss of its gas (the plant puffing), and the choking you do when you breathe it.
    public static final DeferredHolder<SoundEvent, SoundEvent> GAMMAPALI_HISS = register("l227.gammapali.hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAMMAPALI_CHOKE = register("l227.gammapali.choke");
    // The Palette: the creak as it lets go (the warning), and the crash and crunch when it lands.
    public static final DeferredHolder<SoundEvent, SoundEvent> PALETTE_CREAK = register("l227.palette.creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> PALETTE_CRUSH = register("l227.palette.crush");
    // The giant Venus flytrap: the snap shut, the stalk straining as it lifts you, the wet churn of
    // digestion, the squelch of struggling inside, the lobes popping open, and its hurt/death sounds.
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_SNAP = register("entity.venus_flytrap.snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_LIFT = register("entity.venus_flytrap.lift");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_DIGEST = register("entity.venus_flytrap.digest");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_STRUGGLE = register("entity.venus_flytrap.struggle");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_RELEASE = register("entity.venus_flytrap.release");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_HURT = register("entity.venus_flytrap.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYTRAP_DEATH = register("entity.venus_flytrap.death");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers the sounds above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private Level227FloraSounds() {}
}
