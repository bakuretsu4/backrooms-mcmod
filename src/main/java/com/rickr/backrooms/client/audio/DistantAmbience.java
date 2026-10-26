package com.rickr.backrooms.client.audio;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level94Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Small one-off sounds far away, so a long stay on a level never sounds like the same loop playing
 * over and over. Every level has a short list of sounds that fit it (taken from its wiki page, see
 * {@code snd_dist_amb_*} in tools/gen_assets.py): a fluorescent tube popping somewhere on Level 0,
 * a tram rumbling behind the walls of Level 2, a dog barking on Level 9, and so on.
 *
 * <p>One shared timer does all levels: every 18-55 seconds (each level has its own range) it picks
 * one sound from the current level's list, never the same one twice in a row, and plays it at a
 * random spot 14-40 blocks away at about ear height. It is a normal sound out in the world (not
 * stuck to your head), so it comes from a direction, fades with distance and gets the same room
 * reverb as every other world sound.
 *
 * <p>The timer holds still while the game is paused or you are dead, and starts over when you
 * change level. In Level 2's void nothing extra is needed: {@code Level2Sounds} already drops every
 * new world sound while the void asks for silence, these included.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class DistantAmbience {
    /** How far away the sound is placed, in blocks. sounds.json gives these events a 48-block range. */
    private static final double MIN_DISTANCE = 14.0;
    private static final double MAX_DISTANCE = 40.0;
    /** Pitch varies by up to 6% either way, so even the same file sounds a little different each time. */
    private static final float PITCH_JITTER = 0.06f;

    /** When a sound may play. Only Level 94 has a day and a night; everything else is ANY. */
    private enum When { ANY, DAY, NIGHT }

    /** One sound in a level's list: how often it is picked compared with the others, and how loud. */
    private record Entry(DeferredHolder<SoundEvent, SoundEvent> sound, int weight, float volume, When when) {}

    /** A level's list and how many seconds pass between two sounds (picked at random in between). */
    private record Schedule(int minSeconds, int maxSeconds, List<Entry> entries) {}

    private static final Map<BackroomsLevel, Schedule> SCHEDULES = new EnumMap<>(BackroomsLevel.class);

    // Volumes: 1.0 is the loudest a sound can play. With the 48-block range in sounds.json a sound
    // 14 blocks off arrives at ~70% of its volume and one 40 blocks off at ~17%, so these stay
    // clearly audible but quieter than a light or machine right next to you. Unsettling sounds get
    // lower weights so they stay rare. On the two outdoor levels (9 and 94) some volumes are above
    // 1.0: that does NOT make them louder (Minecraft caps the loudness at 1.0), it only stretches
    // the range (1.4 x 48 = 67 blocks), so they still carry when placed 40 blocks away across open
    // streets and hills, where nothing would muffle them.
    static {
        SCHEDULES.put(BackroomsLevel.LEVEL_0, new Schedule(22, 50, List.of(
                new Entry(ModSounds.DISTANT_L0_BALLAST_POP, 4, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_L0_LAYOUT_SHIFT, 3, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L0_CARPET_DRIP, 3, 0.7f, When.ANY),
                new Entry(ModSounds.DISTANT_L0_WALL_SCRATCH, 2, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_L0_FAR_CALL, 1, 0.7f, When.ANY))));
        SCHEDULES.put(BackroomsLevel.LEVEL_1, new Schedule(20, 45, List.of(
                new Entry(ModSounds.DISTANT_L1_PIPE_CLANG, 4, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_L1_PIPE_RUMBLE, 4, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L1_SHUTTER_RATTLE, 3, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_L1_CEILING_VIBRATE, 2, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_L1_DOOR_SCRAPE, 1, 0.7f, When.ANY))));
        SCHEDULES.put(BackroomsLevel.LEVEL_2, new Schedule(18, 40, List.of(
                new Entry(ModSounds.DISTANT_L2_PIPE_KNOCK, 4, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_L2_PRESSURE_GROAN, 3, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L2_MACHINE_WHIR, 3, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_L2_TRAM_RUMBLE, 2, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L2_TUBE_BURST, 1, 0.8f, When.ANY))));
        SCHEDULES.put(BackroomsLevel.LEVEL_4, new Schedule(25, 55, List.of(
                new Entry(ModSounds.DISTANT_L4_VENDING_COMPRESSOR, 4, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L4_COPIER, 3, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_L4_FOUNTAIN_RUN, 3, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_L4_ELEVATOR_CLUNK, 2, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L4_PHONE_RING, 2, 0.7f, When.ANY))));
        // The pool levels' water swallows sound, so these are quiet and spaced further apart.
        SCHEDULES.put(BackroomsLevel.POOLROOMS, new Schedule(30, 55, List.of(
                new Entry(ModSounds.DISTANT_POOL_WATER_LAP, 4, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_POOL_TILE_DRIP, 3, 0.7f, When.ANY),
                new Entry(ModSounds.DISTANT_POOL_DRAIN_GURGLE, 3, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_POOL_PUMP_THUNK, 2, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_POOL_CUTOFF_SPLASH, 1, 0.7f, When.ANY))));
        SCHEDULES.put(BackroomsLevel.LEVEL_37, new Schedule(30, 55, List.of(
                new Entry(ModSounds.DISTANT_L37_RIPPLE_WASH, 4, 0.7f, When.ANY),
                new Entry(ModSounds.DISTANT_L37_WATER_BUBBLE, 3, 0.6f, When.ANY),
                new Entry(ModSounds.DISTANT_L37_TRICKLE, 3, 0.6f, When.ANY),
                new Entry(ModSounds.DISTANT_L37_SOFT_BREEZE, 2, 0.6f, When.ANY),
                new Entry(ModSounds.DISTANT_L37_WARM_HUM, 2, 0.8f, When.ANY))));
        SCHEDULES.put(BackroomsLevel.LEVEL_9, new Schedule(24, 50, List.of(
                new Entry(ModSounds.DISTANT_L9_WIND_CHIME, 4, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L9_GUTTER_DRIP, 3, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_L9_SCREEN_DOOR, 3, 1.4f, When.ANY),
                new Entry(ModSounds.DISTANT_L9_BIN_RATTLE, 2, 1.4f, When.ANY),
                new Entry(ModSounds.DISTANT_L9_DOG_BARK, 2, 1.4f, When.ANY))));
        // Level 7: the sea under the ceiling. Waves slapping against rock and concrete, drips from
        // the ceiling, the giant pipes thumping and groaning, now and then bubbles rising from below.
        // (Shared with the pool levels and Level 2, whose sounds fit here too.) Spaced out like the
        // pool levels, and quieter than the Thing, which has its own sounds (client/Level7Client).
        SCHEDULES.put(BackroomsLevel.LEVEL_7, new Schedule(25, 50, List.of(
                new Entry(ModSounds.DISTANT_POOL_WATER_LAP, 4, 0.9f, When.ANY),
                new Entry(ModSounds.DISTANT_POOL_TILE_DRIP, 3, 0.8f, When.ANY),
                new Entry(ModSounds.DISTANT_POOL_PUMP_THUNK, 2, 1.0f, When.ANY),
                new Entry(ModSounds.DISTANT_L37_WATER_BUBBLE, 2, 0.7f, When.ANY),
                new Entry(ModSounds.DISTANT_L2_PRESSURE_GROAN, 1, 0.9f, When.ANY))));
        // Level 94: town sounds by day, eerie ones at night (and dawn, still night for the monsters);
        // the water tower creaks any time, so dusk is not completely empty.
        SCHEDULES.put(BackroomsLevel.LEVEL_94, new Schedule(20, 45, List.of(
                new Entry(ModSounds.DISTANT_L94_CAR_HORN, 3, 1.4f, When.DAY),
                new Entry(ModSounds.DISTANT_L94_MILK_BOTTLES, 3, 0.8f, When.DAY),
                new Entry(ModSounds.DISTANT_L94_WATER_TOWER_CREAK, 2, 1.4f, When.ANY),
                new Entry(ModSounds.DISTANT_L94_RAT_SKITTER, 3, 0.8f, When.NIGHT),
                new Entry(ModSounds.DISTANT_L94_CARTOON_TIPTOE, 2, 1.0f, When.NIGHT))));
    }

    private static final RandomSource RANDOM = RandomSource.create();
    /** The level the timer is running for; null outside the Backrooms. */
    @Nullable
    private static BackroomsLevel floor;
    private static int ticksToNext;
    /** The sound played last, so it is not picked again straight away. */
    @Nullable
    private static Entry last;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BackroomsLevel now = level == null || mc.player == null ? null : BackroomsLevel.fromDimension(level.dimension());
        Schedule schedule = now == null ? null : SCHEDULES.get(now);
        if (now != floor) {
            // New level (or left the Backrooms): forget the old one and start counting afresh.
            floor = now;
            last = null;
            ticksToNext = schedule == null ? 0 : nextDelay(schedule);
        }
        // Paused or dead: hold the timer where it is.
        if (schedule == null || mc.isPaused() || !mc.player.isAlive()) {
            return;
        }
        if (--ticksToNext > 0) {
            return;
        }
        ticksToNext = nextDelay(schedule);
        Entry entry = pick(schedule, level);
        if (entry != null) {
            last = entry;
            play(mc, entry);
        }
    }

    private static int nextDelay(Schedule schedule) {
        return Mth.nextInt(RANDOM, schedule.minSeconds() * 20, schedule.maxSeconds() * 20);
    }

    /** A weighted random pick among the sounds allowed right now, leaving out the last one played. */
    @Nullable
    private static Entry pick(Schedule schedule, ClientLevel level) {
        int total = 0;
        for (Entry e : schedule.entries()) {
            if (allowed(e, level)) {
                total += e.weight();
            }
        }
        if (total == 0) {
            return null;
        }
        int roll = RANDOM.nextInt(total);
        for (Entry e : schedule.entries()) {
            if (allowed(e, level)) {
                roll -= e.weight();
                if (roll < 0) {
                    return e;
                }
            }
        }
        return null;
    }

    private static boolean allowed(Entry e, ClientLevel level) {
        if (e == last) {
            return false;
        }
        return switch (e.when()) {
            case ANY -> true;
            case DAY -> Level94Clock.phase(level) == Level94Clock.Phase.DAY;
            case NIGHT -> Level94Clock.isNight(level);
        };
    }

    /** Plays the sound out in the world, 14-40 blocks away in a random direction, about at ear height. */
    private static void play(Minecraft mc, Entry entry) {
        Vec3 eye = mc.player.getEyePosition();
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double distance = Mth.nextDouble(RANDOM, MIN_DISTANCE, MAX_DISTANCE);
        double x = eye.x + Mth.cos(angle) * distance;
        double y = eye.y + Mth.nextDouble(RANDOM, -1.0, 1.5);
        double z = eye.z + Mth.sin(angle) * distance;
        float pitch = 1.0f + (RANDOM.nextFloat() * 2.0f - 1.0f) * PITCH_JITTER;
        // LINEAR attenuation and relative = false: a real position in the world that fades with
        // distance (relative = true would glue the sound to the player's head).
        mc.getSoundManager().play(new SimpleSoundInstance(entry.sound().get().getLocation(), SoundSource.AMBIENT,
                entry.volume(), pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false));
    }

    private DistantAmbience() {}
}
