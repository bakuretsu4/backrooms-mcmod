package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.DuckedLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Two things Level 2's anomalies do to what you hear, client side:
 * <ul>
 *   <li><b>The machinery falls silent</b> in a brownout (and goes quiet in the office hallway):
 *       the level's ambient loop is the low drone of its machines, so we fade it down. Minecraft
 *       plays that loop from the biome and offers no volume knob, so when it starts we wrap it in
 *       {@link DuckedLoopSound}, which plays it at its own volume times our "duck" factor.</li>
 *   <li><b>No sound at all</b> in the void: while {@link #silence()} is asked for, every new world
 *       sound is dropped before it plays (music and menu clicks excepted), and the drone fades to
 *       nothing.</li>
 * </ul>
 * Effects ask every tick; when they stop asking, everything eases back within a second or two.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Sounds {
    private static long tick;
    private static long duckTick = Long.MIN_VALUE;
    private static float duckRequest = 1.0f;
    private static long silenceTick = Long.MIN_VALUE;

    /** Ask for the machine drone at {@code level} (0..1) this tick. The quietest request wins. */
    public static void duckAmbience(float level) {
        if (duckTick != tick) {
            duckTick = tick;
            duckRequest = level;
        } else {
            duckRequest = Math.min(duckRequest, level);
        }
    }

    /** Ask for complete silence this tick (the void). */
    public static void silence() {
        silenceTick = tick;
        duckAmbience(0.0f);
    }

    // "Asked for within the last 2 ticks". Written as tick <= askedTick + 2, NOT tick - askedTick <= 2:
    // the ask ticks start at Long.MIN_VALUE ("never"), and tick - Long.MIN_VALUE overflows to a huge
    // negative number, which counted as "just asked" and silenced every sound in the game.
    private static boolean silenced() {
        return tick <= silenceTick + 2;
    }

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
        if (sound == null || sound instanceof DuckedLoopSound) {
            return;
        }
        if (sound instanceof TickableSoundInstance loop
                && sound.getLocation().equals(ModSounds.LEVEL2_AMBIENCE.getId())) {
            // About a second and a half to fade either way.
            event.setSound(new DuckedLoopSound(loop, Level2Sounds::duckTarget, 0.06f, 1.0f));
            return;
        }
        if (silenced() && sound.getSource() != SoundSource.MASTER && sound.getSource() != SoundSource.MUSIC
                && sound.getSource() != SoundSource.VOICE) {
            event.setSound(null);
        }
    }

    // (The wrapper itself, client/audio/DuckedLoopSound, is shared with Level 0's room tone.)

    private Level2Sounds() {}
}
