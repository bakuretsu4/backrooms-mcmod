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
    // Level 7 "Thalassophobia": the hollow sea ambience (the biome's loop), and the Thing, which you
    // only ever hear: its groans from far below, the swell when it passes under you, and the gut-drop
    // lurch when you fall through the hatch (one-shots from client/Level7Client).
    public static final DeferredHolder<SoundEvent, SoundEvent> L7_AMBIENT = register("ambient.level7");
    public static final DeferredHolder<SoundEvent, SoundEvent> L7_GROAN = register("level7.groan");
    public static final DeferredHolder<SoundEvent, SoundEvent> L7_HEAVE = register("level7.heave");
    public static final DeferredHolder<SoundEvent, SoundEvent> L7_LURCH = register("level7.lurch");
    // Exit hints: the faint loop each level's exit gives off (client/hint/ExitHints plays it at the
    // nearest exit; no subtitle, it is meant to be noticed, not announced).
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_0 = register("exit_hint.level_0");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_1 = register("exit_hint.level_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_2 = register("exit_hint.level_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_4 = register("exit_hint.level_4");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_POOLROOMS = register("exit_hint.poolrooms");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_37 = register("exit_hint.level_37");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_9 = register("exit_hint.level_9");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_94 = register("exit_hint.level_94");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXIT_HINT_LEVEL_7 = register("exit_hint.level_7");
    // Music (original pieces from tools/gen_assets.py, played by client/BackroomsMusic on the music
    // channel): the main theme on the title menu, a mix of the theme and a dark ambient piece in
    // most levels, and a dreamier piece (or the theme) in the Poolrooms and Level 37.
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MENU = register("music.menu");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BACKROOMS = register("music.backrooms");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DREAM = register("music.dream");
    // Level 94 anomalies: the high, glassy hum of the blinding hall's light (looped by the client
    // while you are in it) and the coin rattling in a furniture-pile sink.
    public static final DeferredHolder<SoundEvent, SoundEvent> L94_BLINDING_HUM = register("level94.blinding_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> L94_COIN = register("level94.coin");
    // Poolrooms anomalies (client/anomaly/poolrooms): the "obtrusive" water that now and then swells
    // over every other sound in both pool levels, the faint chorus of chants of a dark corner and
    // the Sanguine Reservoir, its slithering and whimpers, the dull swell as you drift into it, and
    // the rush of a current in a stair pit.
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_OBTRUSIVE_WATER = register("anomaly.poolrooms.obtrusive_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_CHANT = register("anomaly.poolrooms.chant");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_SLITHER = register("anomaly.poolrooms.slither");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_WHIMPER = register("anomaly.poolrooms.whimper");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_DRIFT = register("anomaly.poolrooms.drift");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_CURRENT = register("anomaly.poolrooms.current");
    // Level 37 anomalies (client/anomaly/level37): the high soft tone of a void opening (looped by
    // the client as you near it) and the bright swell as it takes you, the cold draught breathing
    // out of a dark opening and the muffled hush as you pass through it, and the single drip that
    // repeats each time a looping tunnel brings you round again.
    public static final DeferredHolder<SoundEvent, SoundEvent> L37_VOID_TONE = register("level37.void_tone");
    public static final DeferredHolder<SoundEvent, SoundEvent> L37_VOID_VANISH = register("level37.void_vanish");
    public static final DeferredHolder<SoundEvent, SoundEvent> L37_DARK_DRAFT = register("level37.dark_draft");
    public static final DeferredHolder<SoundEvent, SoundEvent> L37_DARK_PASS = register("level37.dark_pass");
    public static final DeferredHolder<SoundEvent, SoundEvent> L37_LOOP_DRIP = register("level37.loop_drip");
    // Level 1 anomalies (world/anomaly/level1): water dripping into the fog's puddles, a crate
    // settling somewhere behind you, and the warbling drone of the Corrupted Corridor's
    // black-and-white sector.
    public static final DeferredHolder<SoundEvent, SoundEvent> L1_DRIP = register("level1.drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> L1_CRATE_THUD = register("level1.crate_thud");
    public static final DeferredHolder<SoundEvent, SoundEvent> L1_CORRIDOR_DRONE = register("level1.corridor_drone");
    // Level 4 anomalies (world/anomaly/level4, client/anomaly/level4): footsteps and a door far up or
    // down a looping stairwell, the Window's soft whispers and its grab, an elevator ride down and
    // its chime, and the Cubicles' work ambience (paper, pencils, murmurs, muffled rain) and its
    // nearby desk noises.
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_STAIRWELL_ECHO = register("level4.stairwell_echo");
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_WINDOW_WHISPER = register("level4.window_whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_WINDOW_GRAB = register("level4.window_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_ELEVATOR_RIDE = register("level4.elevator_ride");
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_ELEVATOR_DING = register("level4.elevator_ding");
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_CUBICLES_AMBIENCE = register("level4.cubicles_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> L4_CUBICLES_WORK = register("level4.cubicles_work");
    // Level 2 anomalies (world/anomaly/level2, client/anomaly/level2): the pop and fizz of a series
    // circuit failing, the faulty whirring machinery of a hot zone (looped by the client), steam
    // venting from its pipes, and black ooze dripping from burst pipes in a brownout.
    public static final DeferredHolder<SoundEvent, SoundEvent> L2_CIRCUIT_POP = register("level2.circuit_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> L2_MACHINE_LOOP = register("level2.machine_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> L2_STEAM_HISS = register("level2.steam_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> L2_OOZE_DRIP = register("level2.ooze_drip");
    // Level 9 anomalies (world/anomaly/level9, client/anomaly/level9): the Mangled fog's sudden
    // cold, the booms of its black pillar, the garbled voice that follows them and the quake when
    // it goes; Level 9.1's radio chatter, warped elevator music and backwards talking from
    // nowhere, the Gloom's faint throb and its pull.
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_FROST_ONSET = register("level9.frost_onset");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_PILLAR_BOOM = register("level9.pillar_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_GARBLED_SPEECH = register("level9.garbled_speech");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_QUAKE = register("level9.quake");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_FIELD_RADIO = register("level9.field_radio");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_FIELD_ELEVATOR = register("level9.field_elevator");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_FIELD_REVERSED = register("level9.field_reversed");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_GLOOM_THROB = register("level9.gloom_throb");
    public static final DeferredHolder<SoundEvent, SoundEvent> L9_GLOOM_DRAG = register("level9.gloom_drag");
    // Level 0 anomalies (world/anomaly/level0, client/anomaly/level0): the Red Rooms' "klaxon synth"
    // hum (looped in each room), the Manila Room's quiet piano from nowhere, and the fluorescent buzz
    // in a Blackout Zone that seems to lead the way out but never stays put.
    public static final DeferredHolder<SoundEvent, SoundEvent> L0_RED_ROOMS_HUM = register("level0.red_rooms_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> L0_MANILA_PIANO = register("level0.manila_piano");
    public static final DeferredHolder<SoundEvent, SoundEvent> L0_BLACKOUT_BUZZ = register("level0.blackout_buzz");
    // Distant ambience (client/audio/DistantAmbience): one-off sounds far away, a few per level, so a
    // long stay never sounds like the same loop. One is picked every half minute or so and placed
    // 14-40 blocks off in a random direction. Files: sounds/ambient/distant/<level>/<name>1..3.ogg.
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L0_BALLAST_POP = register("distant.level0.ballast_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L0_WALL_SCRATCH = register("distant.level0.wall_scratch");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L0_LAYOUT_SHIFT = register("distant.level0.layout_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L0_CARPET_DRIP = register("distant.level0.carpet_drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L0_FAR_CALL = register("distant.level0.far_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L1_PIPE_CLANG = register("distant.level1.pipe_clang");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L1_PIPE_RUMBLE = register("distant.level1.pipe_rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L1_SHUTTER_RATTLE = register("distant.level1.shutter_rattle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L1_CEILING_VIBRATE = register("distant.level1.ceiling_vibrate");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L1_DOOR_SCRAPE = register("distant.level1.door_scrape");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L2_PIPE_KNOCK = register("distant.level2.pipe_knock");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L2_PRESSURE_GROAN = register("distant.level2.pressure_groan");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L2_MACHINE_WHIR = register("distant.level2.machine_whir");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L2_TRAM_RUMBLE = register("distant.level2.tram_rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L2_TUBE_BURST = register("distant.level2.tube_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L4_PHONE_RING = register("distant.level4.phone_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L4_VENDING_COMPRESSOR = register("distant.level4.vending_compressor");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L4_FOUNTAIN_RUN = register("distant.level4.fountain_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L4_ELEVATOR_CLUNK = register("distant.level4.elevator_clunk");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L4_COPIER = register("distant.level4.copier");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_POOL_WATER_LAP = register("distant.poolrooms.water_lap");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_POOL_DRAIN_GURGLE = register("distant.poolrooms.drain_gurgle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_POOL_TILE_DRIP = register("distant.poolrooms.tile_drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_POOL_PUMP_THUNK = register("distant.poolrooms.pump_thunk");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_POOL_CUTOFF_SPLASH = register("distant.poolrooms.cutoff_splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L37_RIPPLE_WASH = register("distant.level37.ripple_wash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L37_WARM_HUM = register("distant.level37.warm_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L37_WATER_BUBBLE = register("distant.level37.water_bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L37_TRICKLE = register("distant.level37.trickle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L37_SOFT_BREEZE = register("distant.level37.soft_breeze");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L9_DOG_BARK = register("distant.level9.dog_bark");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L9_WIND_CHIME = register("distant.level9.wind_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L9_SCREEN_DOOR = register("distant.level9.screen_door");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L9_GUTTER_DRIP = register("distant.level9.gutter_drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L9_BIN_RATTLE = register("distant.level9.bin_rattle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L94_CAR_HORN = register("distant.level94.car_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L94_MILK_BOTTLES = register("distant.level94.milk_bottles");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L94_WATER_TOWER_CREAK = register("distant.level94.water_tower_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L94_RAT_SKITTER = register("distant.level94.rat_skitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_L94_CARTOON_TIPTOE = register("distant.level94.cartoon_tiptoe");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    private ModSounds() {}
}
