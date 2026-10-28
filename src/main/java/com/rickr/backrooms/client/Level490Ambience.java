package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 490's air (client side): looping layers "in your head", fed every tick while you are there
 * (they fade out by themselves when you leave). Wind howling over the void in slow gusts; the
 * colossal chains singing in the wind, louder the closer you are to one; the far whirlwinds'
 * roar; a constant low rumble; a deep, slow drone like distant voices. The thunder is
 * {@link Level490Lightning}'s.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Ambience {
    /** A chain sings at full strength within this distance, fading out by the next. */
    private static final double WIRE_NEAR = 40.0;
    private static final double WIRE_FAR = 260.0;
    private static final float FADE = 0.02f;

    @Nullable
    private static FadingLoopSound wind;
    @Nullable
    private static FadingLoopSound wire;
    @Nullable
    private static FadingLoopSound whirlwind;
    @Nullable
    private static FadingLoopSound rumble;
    @Nullable
    private static FadingLoopSound drone;

    private Level490Ambience() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !Level490Atmosphere.inLevel490()) {
            return;
        }
        double s = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        // Gusts: two slow swells that rarely line up.
        float gust = (float) (0.5 + 0.5 * Math.sin(s / 6.3) * Math.sin(s / 11.7 + 1.0));
        wind = keep(wind, ModSounds.LEVEL490_WIND.get(), 0.45f + 0.45f * gust);
        Vec3 eye = mc.player.getEyePosition();
        double nearest = Double.MAX_VALUE;
        for (int[] c : Level490ClientState.chains(eye.x, eye.z)) {
            nearest = Math.min(nearest, Math.hypot(c[0] - eye.x, c[1] - eye.z));
        }
        float chain = (float) Math.max(0.0, Math.min(1.0, (WIRE_FAR - nearest) / (WIRE_FAR - WIRE_NEAR)));
        wire = keep(wire, ModSounds.LEVEL490_WIRE.get(), chain * (0.55f + 0.4f * gust));
        whirlwind = keep(whirlwind, ModSounds.LEVEL490_WHIRLWIND.get(), 0.55f);
        rumble = keep(rumble, ModSounds.LEVEL490_RUMBLE.get(), 0.6f);
        drone = keep(drone, ModSounds.LEVEL490_DRONE.get(), 0.55f);
    }

    /** Keeps a loop playing at {@code volume} (starting it if it isn't). */
    private static FadingLoopSound keep(@Nullable FadingLoopSound loop, SoundEvent sound, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (loop == null || !mc.getSoundManager().isActive(loop)) {
            loop = new FadingLoopSound(sound, true, 0, 0, 0, FADE);
            mc.getSoundManager().play(loop);
        }
        loop.feed(volume);
        return loop;
    }
}
