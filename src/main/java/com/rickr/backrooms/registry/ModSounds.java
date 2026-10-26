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

    // Level 37, Sublimity: the calm tiled-pool ambience, and the soft swell as you walk into the light.
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL37_AMBIENCE = register("ambient.level37_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL37_ARRIVE = register("level37.arrive");

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
    // Smilers (blackouts on Levels 0-2): silent apart from a faint breath when close.
    public static final DeferredHolder<SoundEvent, SoundEvent> SMILER_BREATH = register("entity.smiler.breath");

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
    /** Wading through shallow water (Level 37's flooded floors, the Poolrooms' 1-deep shallows). */
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_WATER_WADE_WALK = register("step.water_wade_walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_WATER_WADE_RUN = register("step.water_wade_run");
    /** Soft splash that replaces vanilla's loud one when you drop into the pools of the Poolrooms / Level 37. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_SPLASH = register("water.splash");

    // Noclip intro: reality glitching apart, the rush of falling, crashing through the ceiling.
    public static final DeferredHolder<SoundEvent, SoundEvent> INTRO_GLITCH = register("intro.glitch");
    public static final DeferredHolder<SoundEvent, SoundEvent> INTRO_WIND = register("intro.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> INTRO_IMPACT = register("intro.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH_STING = register("death.sting");
    // Low sanity: things you hear that aren't there (only you hear them; played client side).
    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_WHISPER = register("sanity.whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_HEARTBEAT = register("sanity.heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_KNOCK = register("sanity.knock");
    // Supplies: backpack zipper, locker latch, pill bottle rattle, bandage being wrapped.
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_OPEN = register("block.backpack.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_CLOSE = register("block.backpack.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_OPEN = register("block.supply_locker.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_CLOSE = register("block.supply_locker.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> PILLS_RATTLE = register("item.calm_pills.rattle");
    public static final DeferredHolder<SoundEvent, SoundEvent> BANDAGE_WRAP = register("item.bandage.wrap");
    // Safe rooms: the field radio crackling to life and chiming when you save a checkpoint.
    public static final DeferredHolder<SoundEvent, SoundEvent> CHECKPOINT_SAVE = register("block.checkpoint_radio.save");
    // Level 4, the Abandoned Office: heavy rain on the windows (biome loop), now and then a rumble
    // in the pipes (biome additions), thunder after the lightning, the music behind door 6, and the
    // vending machine and water cooler.
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL4_RAIN = register("ambient.level4_rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL4_PIPES = register("ambient.level4_pipes");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL4_THUNDER = register("level4.thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL4_DOOR_MUSIC = register("level4.door_music");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENDING_MACHINE_VEND = register("block.vending_machine.vend");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_COOLER_GLUG = register("block.water_cooler.glug");
    // Level 94 "Motion" (Props slice): the happy cartoon tune the siren poles play all day.
    public static final DeferredHolder<SoundEvent, SoundEvent> L94_SIREN_SONG = register("level94.siren_song");
    // Level 94 "Motion" (Sky slice): the soft wind over the hills (the biome's ambient loop, day and
    // night), faint synthetic birdsong by day and far-off cartoon laughter at night (one-shots
    // scheduled by client/Level94Atmosphere).
    public static final DeferredHolder<SoundEvent, SoundEvent> L94_WIND = register("ambient.level94_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> L94_BIRDS = register("level94.birds");
    public static final DeferredHolder<SoundEvent, SoundEvent> L94_LAUGH = register("level94.laugh");
    // Music (original pieces from tools/gen_assets.py, played by client/BackroomsMusic on the music
    // channel): the main theme on the title menu, a mix of the theme and a dark ambient piece in
    // most levels, and a dreamier piece (or the theme) in the Poolrooms and Level 37.
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MENU = register("music.menu");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BACKROOMS = register("music.backrooms");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DREAM = register("music.dream");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    private ModSounds() {}
}
