package com.rickr.backrooms.client.anomaly.level39.gondola;

import com.rickr.backrooms.client.anomaly.level9.Level9Frost;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChair;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaLine;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * Walking up to a chairlift in the Crimson Field (client): long before you see it in the dark you
 * hear it. The cable creaks somewhere overhead, wind hums in it, and now and then a sheave train
 * clatters as an empty chair goes over a tower. The air near the line is colder than the field
 * around it: the frost on the picture thickens as you get close.
 *
 * <p>Registered for {@code level_9/gondola} in Level9AnomalyEffects. While you ride, the ride's
 * own effects take over ({@link GondolaRideClient}).
 */
public final class GondolaSiteEffect {
    /** You start to hear and feel the line this far from it (blocks). */
    private static final double REACH = 32.0;
    /** The field asks for 0.42 frost everywhere; right at the line it's this much more. */
    private static final float FIELD_FROST = 0.42f;
    private static final float EXTRA_FROST = 0.33f;
    private static final float CREAK_VOLUME = 0.6f;
    private static final float WIND_VOLUME = 0.3f;
    /** A sheave clatters every 8-15 s when you're this close to a tower (blocks). */
    private static final double CLACK_REACH = 28.0;

    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private static GondolaLoopSound creak;
    @Nullable
    private static GondolaLoopSound wind;
    private static long nextClackMs;

    public static void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (mc.player == null || GondolaChair.isRiding(mc.player)) {
            return;
        }
        GondolaLine line = GondolaLine.of(site);
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        float close = (float) Mth.clamp(1.0 - line.distanceTo(px, pz) / REACH, 0.0, 1.0);
        if (close <= 0.0f) {
            return;
        }
        Level9Frost.request(Level9Frost.WALKWAY, FIELD_FROST + EXTRA_FROST * close);
        if (mc.isPaused()) {
            return;
        }
        // The creak comes from the nearest bit of cable overhead, so you can tell which way the line is.
        double a = line.nearestA(px, pz);
        double cy = line.cableY(a);
        creak = GondolaLoopSound.feed(creak, GondolaRegistry.CABLE_CREAK.get(), false,
                line.worldX(a, 0.0), cy, line.worldZ(a, 0.0), CREAK_VOLUME * close * close, 0.9f);
        wind = GondolaLoopSound.feed(wind, GondolaRegistry.WIND.get(), true, 0, 0, 0, WIND_VOLUME * close, 0.85f);

        long now = System.currentTimeMillis();
        if (now < nextClackMs) {
            return;
        }
        nextClackMs = now + 8000L + RANDOM.nextInt(7000);
        // The nearest tower head (the station frame counts too).
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k <= line.towers; k++) {
            double d = Math.abs(line.localA(px, pz) - line.towerA(k));
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        if (bestD > CLACK_REACH) {
            return;
        }
        int ta = line.towerA(best);
        double side = RANDOM.nextBoolean() ? GondolaLine.LINE_HALF : -GondolaLine.LINE_HALF;
        // Volume above 1 widens the hearing range (16 blocks per 1.0): faint, but carries a little.
        mc.getSoundManager().play(new SimpleSoundInstance(GondolaRegistry.SHEAVE_CLACK.get(), SoundSource.AMBIENT,
                2.0f, 0.9f + RANDOM.nextFloat() * 0.15f, RANDOM, line.worldX(ta, side), line.towerCableY(best), line.worldZ(ta, side)));
    }

    private GondolaSiteEffect() {}
}
