package com.rickr.backrooms.client.anomaly.level39;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.Level39ClientState;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.Level39AnomalyRegistry;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * Client effects of Level 39's anomalies (registered in {@code AnomalyClientEffects.bootstrap}).
 * All kept quiet and small on purpose: no overlays, nothing that hides the level.
 * <ul>
 *   <li>{@code hostile_windows}: coal ash drifting out of the lower windows, and now and then the
 *       smell of it (a faint rustle with the subtitle "Smell of coal ash").</li>
 *   <li>{@code hole_sounds}: cold air breathing up out of the hole: a few pale motes rising.</li>
 *   <li>{@code hydrolitis}: a sickly green haze of motes over the stagnant water.</li>
 *   <li>{@code bigger_inside}: inside the big interior the house sounds and feels far too big.</li>
 * </ul>
 */
public final class Level39AnomalyEffects {
    private Level39AnomalyEffects() {}

    public static void register() {
        AnomalyClientEffects.register(Level39Sites.HOSTILE_WINDOWS, new WindowsEffect());
        AnomalyClientEffects.register(Level39Sites.HOLE_SOUNDS, new HoleEffect());
        AnomalyClientEffects.register(Level39Sites.HYDROLITIS, new StagnantEffect());
        AnomalyClientEffects.register(Level39Sites.BIGGER_INSIDE, new BiggerInsideEffect());
    }

    // ==========================================================================================
    // The facade: coal ash
    // ==========================================================================================

    private static final class WindowsEffect implements AnomalyClientEffect {
        /** Ash only drifts this far out of the windows (blocks in front of the glass). */
        private static final double ASH_RANGE = 16.0;
        private static final long SMELL_MIN_MS = 25_000L;
        private static final int SMELL_SPREAD_MS = 25_000;
        private final RandomSource random = RandomSource.create();
        private long nextSmell;

        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            LocalPlayer player = mc.player;
            if (mc.isPaused() || player.getY() > Level39Sites.glassBottom(3)) {
                return;
            }
            double front = Level39Sites.inFront(site, player.getX(), player.getZ());
            double along = Level39Sites.along(site, player.getX(), player.getZ());
            if (front < -1.0 || front > ASH_RANGE || along < Level39Sites.alongMin(site) - 8 || along > Level39Sites.alongMax(site) + 8) {
                return;
            }
            // A few specks from the windows of the lowest three storeys around you.
            if (random.nextFloat() < 0.5f) {
                int bay = Level39Sites.bayAt(site, along) + random.nextInt(7) - 3;
                if (bay >= Level39Sites.firstBay(site) && bay <= Level39Sites.lastBay(site)) {
                    double a = Level39Sites.bayCentre(site, bay) + (random.nextDouble() - 0.5) * 3.0;
                    int storey = random.nextInt(3);
                    double y = Level39Sites.glassBottom(storey) + random.nextDouble() * Level39Sites.GLASS_HEIGHT;
                    double out = 0.2 + random.nextDouble() * 0.5;
                    double x = Level39Sites.worldX(site, a, out);
                    double z = Level39Sites.worldZ(site, a, out);
                    double vx = (Level39Sites.worldX(site, a, 1.0) - Level39Sites.worldX(site, a, 0.0)) * 0.03;
                    double vz = (Level39Sites.worldZ(site, a, 1.0) - Level39Sites.worldZ(site, a, 0.0)) * 0.03;
                    mc.level.addParticle(ParticleTypes.ASH, x, y, z, vx, -0.01, vz);
                }
            }
            long now = System.currentTimeMillis();
            if (nextSmell == 0L) {
                nextSmell = now + 6_000L + random.nextInt(SMELL_SPREAD_MS);
            } else if (now >= nextSmell && front < 10.0) {
                nextSmell = now + SMELL_MIN_MS + random.nextInt(SMELL_SPREAD_MS);
                double a = along + (random.nextDouble() - 0.5) * 6.0;
                mc.level.playLocalSound(Level39Sites.worldX(site, a, 0.5), player.getEyeY() + 1.0, Level39Sites.worldZ(site, a, 0.5),
                        Level39AnomalyRegistry.ASH_SMELL.get(), SoundSource.AMBIENT, 0.5f, 0.9f + random.nextFloat() * 0.2f, false);
            }
        }
    }

    // ==========================================================================================
    // Holes and stagnant water
    // ==========================================================================================

    private static final class HoleEffect implements AnomalyClientEffect {
        private final RandomSource random = RandomSource.create();

        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            LocalPlayer player = mc.player;
            if (mc.isPaused() || random.nextFloat() > 0.3f
                    || site.distanceTo(player.getX(), player.getZ()) > 20.0 || player.getY() < Level39Layout.GROUND_Y - 8) {
                return;
            }
            double r = Level39Sites.holeRadius(site) * 0.7;
            double a = random.nextDouble() * Math.PI * 2.0;
            double x = site.anchorX() + 0.5 + Math.cos(a) * r * random.nextDouble();
            double z = site.anchorZ() + 0.5 + Math.sin(a) * r * random.nextDouble();
            mc.level.addParticle(ParticleTypes.WHITE_ASH, x, Level39Layout.GROUND_Y - 1.0, z, 0.0, 0.03, 0.0);
        }
    }

    private static final class StagnantEffect implements AnomalyClientEffect {
        private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(0.36f, 0.52f, 0.30f), 0.7f);
        private final RandomSource random = RandomSource.create();

        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            LocalPlayer player = mc.player;
            if (mc.isPaused() || player.getY() > Level39ClientState.TUNNEL_TOP || random.nextFloat() > 0.5f) {
                return;
            }
            double x = player.getX() + (random.nextDouble() - 0.5) * 16.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 16.0;
            if (!site.contains(x, z)) {
                return;
            }
            double y = Level39Layout.TUNNEL_WATER_Y + 1.1 + random.nextDouble() * 1.5;
            mc.level.addParticle(SPORE, x, y, z, 0.0, 0.004, 0.0);
        }
    }

    // ==========================================================================================
    // Bigger on the inside
    // ==========================================================================================

    private static final class BiggerInsideEffect implements AnomalyClientEffect {
        /** A touch darker and thinner air: the room goes on further than the eye expects. */
        private static final AtmosphereGrade.Mood VAST = new AtmosphereGrade.Mood(0.97f, 1.06f, 0.78f,
                0.92f, 0.95f, 1.04f, 1.0f, 0.99f, 0.97f, 0.012f, 0.012f, 0.015f,
                0.12f, 0.12f, 0.13f, 18.0f, 0.012f, 0.30f, 0.38f, 0.24f, 0.012f);
        private AnomalySite lastInterior;
        private float lastSeconds;

        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            if (!inside || !Level39AnomalyPlan.isCopy(site.minX())) {
                return;
            }
            AtmosphereGrade.setAnomalyMood(VAST, Mth.clamp(secondsInside / 3.0f, 0.0f, 1.0f) * 0.6f);
            // Once per visit: a new interior, or the clock restarted because you came back in.
            boolean entered = !site.equals(lastInterior) || secondsInside < lastSeconds;
            lastSeconds = secondsInside;
            if (entered) {
                lastInterior = site;
                LocalPlayer p = mc.player;
                mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), Level39AnomalyRegistry.BIGGER_INSIDE.get(),
                        SoundSource.AMBIENT, 0.8f, 1.0f, false);
            }
        }
    }
}
