package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.entity.Observer;
import com.rickr.backrooms.entity.Watcher;
import com.rickr.backrooms.registry.ModSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What Level 9's entities do to your camcorder and ears, on the client:
 * <ul>
 *   <li>{@link #interference()}: 0..1, how close the nearest Watcher is (within 24 blocks), maxed
 *       out while one charges its beam at you. Drives the burnt static / tearing in the shader.</li>
 *   <li>{@link #danger()}: feeds the camcorder's danger value (glitches, shake). Being stared at is
 *       high, being charged at is maximum, a Watcher merely nearby is moderate, an Observer is a
 *       little unease.</li>
 *   <li>{@link #flashlightFactor(long)}: makes the flashlight stutter near a Watcher (read by the
 *       light code).</li>
 *   <li>the Watcher's hum: a looping sound that follows each Watcher within 32 blocks.</li>
 *   <li>the Watchers' real searchlights when Veil lighting is on ({@code WatcherSearchlights}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class WatcherInterference {
    private static final double SCAN_RANGE = 64.0;
    private static final double HUM_RANGE = 32.0;
    /** Unease added by a visible Observer within this range. */
    private static final double OBSERVER_RANGE = 48.0;
    private static final float OBSERVER_UNEASE = 0.12f;
    /** Danger from a Watcher that is merely close (scaled by closeness). */
    private static final float NEARBY_DANGER = 0.45f;
    private static final float STARE_DANGER = 0.75f;

    private static float interference;
    private static float danger;
    private static float charge;
    private static final Int2ObjectOpenHashMap<WatcherHumSound> HUMS = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet SEEN = new IntOpenHashSet();
    @Nullable
    private static ClientLevel boundLevel;
    private static boolean searchlightsUsed;

    /** 0..1 electronics interference at the camera. */
    public static float interference() {
        return Math.max(interference, charge);
    }

    /** 0..1 contribution to the camcorder's danger value. */
    public static float danger() {
        return danger;
    }

    /**
     * Flashlight brightness multiplier: 1 normally; near a Watcher the bulb stutters in 50 ms
     * steps, more often the closer it is, with the odd full dropout.
     */
    public static float flashlightFactor(long nowMs) {
        float amount = interference();
        if (amount < 0.02f) {
            return 1.0f;
        }
        long slot = nowMs / 50;
        float roll = Mth.frac(Mth.sin(slot * 12.9898f) * 43758.547f);
        float chance = 0.12f + 0.5f * amount;
        if (roll < chance * 0.35f) {
            return 0.0f;
        }
        if (roll < chance) {
            return 0.25f + 0.5f * Mth.frac(roll * 91.7f);
        }
        return 1.0f;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != boundLevel) {
            stopAllHums();
            boundLevel = level;
        }
        float interferenceTarget = 0.0f;
        float dangerTarget = 0.0f;
        float chargeTarget = 0.0f;
        SEEN.clear();
        if (level != null && mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            AABB area = mc.player.getBoundingBox().inflate(SCAN_RANGE);
            for (Watcher watcher : level.getEntitiesOfClass(Watcher.class, area)) {
                double distance = watcher.eyeCentre().distanceTo(eye);
                float closeness = (float) Mth.clamp(1.0 - distance / Watcher.INTERFERENCE_RANGE, 0.0, 1.0);
                interferenceTarget = Math.max(interferenceTarget, closeness);
                dangerTarget = Math.max(dangerTarget, closeness * NEARBY_DANGER);
                if (watcher.lockedTargetId() == mc.player.getId()) {
                    Watcher.State state = watcher.state();
                    if (state == Watcher.State.CHARGE) {
                        chargeTarget = Math.max(chargeTarget, 0.5f + 0.5f * watcher.charge());
                        dangerTarget = 1.0f;
                    } else if (state == Watcher.State.STARE) {
                        dangerTarget = Math.max(dangerTarget, STARE_DANGER);
                    }
                }
                if (distance < HUM_RANGE) {
                    SEEN.add(watcher.getId());
                    WatcherHumSound hum = HUMS.get(watcher.getId());
                    if (hum == null || hum.isStopped()) {
                        hum = new WatcherHumSound(watcher);
                        HUMS.put(watcher.getId(), hum);
                        mc.getSoundManager().play(hum);
                    }
                }
            }
            for (Observer observer : level.getEntitiesOfClass(Observer.class, mc.player.getBoundingBox().inflate(OBSERVER_RANGE))) {
                if (!observer.isVanishing()) {
                    dangerTarget = Math.max(dangerTarget, OBSERVER_UNEASE);
                }
            }
        }
        // Hums of Watchers that left (or were removed) fade out on their own.
        Iterator<Int2ObjectOpenHashMap.Entry<WatcherHumSound>> it = HUMS.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Int2ObjectOpenHashMap.Entry<WatcherHumSound> entry = it.next();
            if (!SEEN.contains(entry.getIntKey())) {
                entry.getValue().release();
                it.remove();
            }
        }
        // Rises quickly, settles slowly.
        interference += (interferenceTarget - interference) * (interferenceTarget > interference ? 0.3f : 0.06f);
        danger += (dangerTarget - danger) * (dangerTarget > danger ? 0.3f : 0.06f);
        charge += (chargeTarget - charge) * (chargeTarget > charge ? 0.5f : 0.2f);
        if (interference < 0.002f && interferenceTarget == 0.0f) {
            interference = 0.0f;
        }
        if (charge < 0.002f && chargeTarget == 0.0f) {
            charge = 0.0f;
        }
    }

    /** Per frame, before Veil draws its lights: aim the Watchers' searchlights. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (BackroomsLights.isVeilLightingActive() && mc.level != null) {
            // Only touch the Veil-based class when Veil is really running (it imports Veil types).
            WatcherSearchlights.frame(event.getPartialTick().getGameTimeDeltaPartialTick(false), mc.level, event.getCamera().getPosition());
            searchlightsUsed = true;
        } else if (searchlightsUsed) {
            WatcherSearchlights.freeAll();
            searchlightsUsed = false;
        }
    }

    private static void stopAllHums() {
        for (WatcherHumSound hum : HUMS.values()) {
            hum.release();
        }
        HUMS.clear();
        interference = 0.0f;
        danger = 0.0f;
        charge = 0.0f;
        if (searchlightsUsed) {
            WatcherSearchlights.freeAll();
            searchlightsUsed = false;
        }
    }

    /**
     * The deep electrical drone of one Watcher. Follows it through the air; volume 1.5 makes it
     * audible to about 24 blocks (linear fade), the same range as the interference.
     */
    private static final class WatcherHumSound extends AbstractTickableSoundInstance {
        private static final float VOLUME = 1.5f;
        private final Watcher watcher;
        private boolean released;

        WatcherHumSound(Watcher watcher) {
            super(ModSounds.WATCHER_HUM.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.watcher = watcher;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 0.01f;
            this.pitch = 0.95f + (watcher.getId() % 7) * 0.015f;
            follow();
        }

        void release() {
            released = true;
        }

        private void follow() {
            Vec3 centre = watcher.eyeCentre();
            this.x = centre.x;
            this.y = centre.y;
            this.z = centre.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (watcher.isRemoved()) {
                released = true;
            } else {
                follow();
            }
            float goal = released ? 0.0f : VOLUME;
            volume += (goal - volume) * 0.1f;
            if (released && volume < 0.01f) {
                stop();
            }
        }
    }

    private WatcherInterference() {}
}
