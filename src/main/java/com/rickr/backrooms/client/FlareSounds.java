package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.FlareEntity;
import com.rickr.backrooms.registry.LightSupplies;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The hiss of every burning flare near you: a quiet loop that follows the flare, swaps to a
 * muffled bubbling hiss while the flare lies in water, and fades out as it sputters. Each flare
 * gets two loops (air and water) and only the matching one is audible, so going in or out of
 * water doesn't restart the sound.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class FlareSounds {
    /** Flares further than this get no loop (the sound's own range is 16 blocks). */
    private static final double HEAR_RANGE = 20.0;
    private static final Int2ObjectOpenHashMap<Hiss[]> PLAYING = new Int2ObjectOpenHashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.clear();
            return;
        }
        // Forget loops that have stopped (flare gone, burnt out, or out of range).
        PLAYING.int2ObjectEntrySet().removeIf(e -> e.getValue()[0].isStopped() && e.getValue()[1].isStopped());
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof FlareEntity flare && flare.lit() && !PLAYING.containsKey(flare.getId())
                    && flare.distanceToSqr(mc.player) < HEAR_RANGE * HEAR_RANGE) {
                Hiss air = new Hiss(flare, LightSupplies.FLARE_HISS.get(), false);
                Hiss water = new Hiss(flare, LightSupplies.FLARE_HISS_WATER.get(), true);
                mc.getSoundManager().play(air);
                mc.getSoundManager().play(water);
                PLAYING.put(flare.getId(), new Hiss[] {air, water});
            }
        }
    }

    private static final class Hiss extends AbstractTickableSoundInstance {
        private final FlareEntity flare;
        private final boolean underwater;

        Hiss(FlareEntity flare, SoundEvent sound, boolean underwater) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.flare = flare;
            this.underwater = underwater;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            this.x = flare.getX();
            this.y = flare.getY();
            this.z = flare.getZ();
            this.volume = 0.0f;
        }

        @Override
        public boolean canStartSilent() {
            return true; // the water loop starts silent while the flare is in air (and vice versa)
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (flare.isRemoved() || !flare.lit() || mc.player == null
                    || flare.distanceToSqr(mc.player) > HEAR_RANGE * HEAR_RANGE) {
                stop();
                return;
            }
            x = flare.getX();
            y = flare.getY();
            z = flare.getZ();
            boolean matches = flare.isInWater() == underwater;
            volume = matches ? flare.sputterLeft(0.0f) : 0.0f;
            // Sputtering: the flame catches and drops, so the hiss wobbles in pitch.
            pitch = flare.sputtering() ? 0.85f + flare.level().getRandom().nextFloat() * 0.3f : 1.0f;
        }
    }

    private FlareSounds() {}
}
