package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.DuckedLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * The level's machine drone, made duckable: Level 2's ambient loop is the low drone of its
 * machines, and it fades down in a brownout and in a dead section. Minecraft plays that loop from
 * the biome and offers no volume knob, so when it starts we wrap it in {@link DuckedLoopSound},
 * which plays it at its own volume times our "duck" factor. Effects ask every tick; when they
 * stop asking, it eases back within a second or two.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Sounds {
    private static long tick;
    private static long duckTick = Long.MIN_VALUE;
    private static float duckRequest = 1.0f;

    /** Ask for the machine drone at {@code level} (0..1) this tick. The quietest request wins. */
    public static void duckAmbience(float level) {
        if (duckTick != tick) {
            duckTick = tick;
            duckRequest = level;
        } else {
            duckRequest = Math.min(duckRequest, level);
        }
    }

    // "Asked for within the last 2 ticks". Written as tick <= askedTick + 2, NOT tick - askedTick <= 2:
    // the ask tick starts at Long.MIN_VALUE ("never"), and tick - Long.MIN_VALUE overflows to a huge
    // negative number, which would count as "just asked".
    private static float duckTarget() {
        return tick <= duckTick + 2 ? duckRequest : 1.0f;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        tick++;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound instanceof TickableSoundInstance loop && !(sound instanceof DuckedLoopSound)
                && sound.getLocation().equals(ModSounds.LEVEL2_AMBIENCE.getId())) {
            // About a second and a half to fade either way.
            event.setSound(new DuckedLoopSound(loop, Level2Sounds::duckTarget, 0.06f, 1.0f));
        }
    }

    // (The wrapper itself, client/audio/DuckedLoopSound, is shared with Level 0's room tone.)

    private Level2Sounds() {}
}
