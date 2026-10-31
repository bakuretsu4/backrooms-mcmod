package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
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
 * roar; a constant low rumble; a deep, slow drone like distant voices, growing as you climb the
 * spire; the downpour's hiss, as hard as {@link Level490Weather} makes it rain. Inside the spire
 * the wind, the rain, the chains and the whirlwinds are muffled by the stone. The thunder is
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
    @Nullable
    private static FadingLoopSound rain;

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
        Vec3 eye = mc.player.getEyePosition();
        boolean indoors = Level490ClientState.hasSeed()
                && Level490Layout.insideSpire(Level490ClientState.seed(), eye.x, eye.y, eye.z);
        float outside = indoors ? 0.25f : 1.0f;
        wind = keep(wind, ModSounds.LEVEL490_WIND.get(), (0.45f + 0.45f * gust) * outside);
        double nearest = Double.MAX_VALUE;
        for (int[] c : Level490ClientState.chains(eye.x, eye.z)) {
            nearest = Math.min(nearest, Math.hypot(c[0] - eye.x, c[1] - eye.z));
        }
        float chain = (float) Math.max(0.0, Math.min(1.0, (WIRE_FAR - nearest) / (WIRE_FAR - WIRE_NEAR)));
        wire = keep(wire, ModSounds.LEVEL490_WIRE.get(), chain * (0.55f + 0.4f * gust) * outside);
        whirlwind = keep(whirlwind, ModSounds.LEVEL490_WHIRLWIND.get(), 0.55f * outside);
        rumble = keep(rumble, ModSounds.LEVEL490_RUMBLE.get(), 0.6f);
        float climbed = (float) Math.max(0.0, Math.min(1.0, (eye.y - Level490Layout.DECK_Y) / (Level490Spire.TOP - Level490Layout.DECK_Y)));
        drone = keep(drone, ModSounds.LEVEL490_DRONE.get(), 0.4f + 0.5f * climbed);
        rain = keep(rain, ModSounds.LEVEL490_RAIN.get(), 0.75f * Level490Weather.rain() * (indoors ? 0.2f : 1.0f));
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
