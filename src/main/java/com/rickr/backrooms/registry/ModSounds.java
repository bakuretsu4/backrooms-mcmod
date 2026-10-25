package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events; the actual .ogg files are mapped in assets/backrooms/sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Backrooms.MOD_ID);

    // Biome ambient loops (referenced by the level biomes' "ambient_sound").
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL0_HUM = register("ambient.level0_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL1_AMBIENCE = register("ambient.level1_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_AMBIENCE = register("ambient.level2_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOLROOMS_AMBIENCE = register("ambient.poolrooms_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL9_AMBIENCE = register("ambient.level9_ambience");

    // The Poolrooms: the dreamy hum of a hallucination, the fake friend fading away, the drain.
    public static final DeferredHolder<SoundEvent, SoundEvent> HALLUCINATION_LOOP = register("poolrooms.hallucination_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHANTOM_VANISH = register("poolrooms.phantom_vanish");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAIN_PULL = register("poolrooms.drain_pull");

    // Level 9, the Suburbs: buzzing sodium streetlamps, lamps stuttering, The Mangled moving far off.
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_SODIUM_HUM = register("light.sodium_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL9_LAMP_FLICKER = register("level9.lamp_flicker");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL9_DISTANT_RUMBLE = register("level9.distant_rumble");
    // The Neighborhood Watch (Watchers) and the Observer.
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_HUM = register("entity.watcher.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_SPOT = register("entity.watcher.spot");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_BEAM = register("entity.watcher.beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_DUST = register("entity.watcher.dust");
    public static final DeferredHolder<SoundEvent, SoundEvent> OBSERVER_VANISH = register("entity.observer.vanish");

    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_AMBIENT = register("entity.bacteria.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_SCREECH = register("entity.bacteria.screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_STEP = register("entity.bacteria.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_HURT = register("entity.bacteria.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_DEATH = register("entity.bacteria.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_SEARCH = register("entity.bacteria.search");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACTERIA_LOST = register("entity.bacteria.lost");

    public static final DeferredHolder<SoundEvent, SoundEvent> REC_BEEP = register("camcorder.rec_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERY_LOW = register("camcorder.battery_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOCLIP = register("camcorder.noclip");
    public static final DeferredHolder<SoundEvent, SoundEvent> ESCAPE = register("camcorder.escape");

    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHLIGHT_CLICK = register("camcorder.flashlight_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAKE_UP = register("camcorder.wake_up");

    // Floor-wide events.
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_DOWN = register("event.power_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_UP = register("event.power_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM = register("event.alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> INTERCOM = register("event.intercom");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_KNOCK = register("event.distant_knock");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_CREAK = register("event.distant_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER = register("event.thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_STATIC = register("event.radio_static");

    // Light fixtures (S1 plays these positionally per lamp).
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_HUM = register("light.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_BLINK = register("light.blink");

    // Player footsteps: quiet walk vs loud run, per floor material.
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_CARPET_WALK = register("step.carpet_walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_CARPET_RUN = register("step.carpet_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_CONCRETE_WALK = register("step.concrete_walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_CONCRETE_RUN = register("step.concrete_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_GRASS_WALK = register("step.grass_walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_GRASS_RUN = register("step.grass_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_WOOD_WALK = register("step.wood_walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_WOOD_RUN = register("step.wood_run");
    /** Dry dead leaves underfoot (one sound for walking and running; it is played louder when running). */
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_LEAVES = register("step.leaves");

    // Noclip intro: reality glitching apart, the rush of falling, crashing through the ceiling.
    public static final DeferredHolder<SoundEvent, SoundEvent> INTRO_GLITCH = register("intro.glitch");
    public static final DeferredHolder<SoundEvent, SoundEvent> INTRO_WIND = register("intro.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> INTRO_IMPACT = register("intro.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH_STING = register("death.sting");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    private ModSounds() {}
}
