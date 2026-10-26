package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Two things Level 2's anomalies do to what you hear, client side:
 * <ul>
 *   <li><b>The machinery falls silent</b> in a brownout (and goes quiet in the office hallway):
 *       the level's ambient loop is the low drone of its machines, so we fade it down. Minecraft
 *       plays that loop from the biome and offers no volume knob, so when it starts we wrap it in
 *       {@link DuckedLoop}, which plays it at its own volume times our "duck" factor.</li>
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
        if (sound == null || sound instanceof DuckedLoop) {
            return;
        }
        if (sound instanceof TickableSoundInstance loop
                && sound.getLocation().equals(ModSounds.LEVEL2_AMBIENCE.getId())) {
            event.setSound(new DuckedLoop(loop));
            return;
        }
        if (silenced() && sound.getSource() != SoundSource.MASTER && sound.getSource() != SoundSource.MUSIC
                && sound.getSource() != SoundSource.VOICE) {
            event.setSound(null);
        }
    }

    /** The level's ambient loop, played at its own volume times the duck factor. */
    private static final class DuckedLoop implements TickableSoundInstance {
        private final TickableSoundInstance inner;
        private float duck = 1.0f;

        DuckedLoop(TickableSoundInstance inner) {
            this.inner = inner;
        }

        @Override
        public void tick() {
            inner.tick(); // the biome code's own fade in/out
            // About a second and a half to fade either way.
            duck += (duckTarget() - duck) * 0.06f;
        }

        @Override
        public boolean isStopped() {
            return inner.isStopped();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * duck;
        }

        @Override
        public boolean canStartSilent() {
            return true; // must start even while ducked to nothing, or it would never come back
        }

        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        @Nullable
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }
    }

    private Level2Sounds() {}
}
