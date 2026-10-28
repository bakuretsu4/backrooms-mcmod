package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.SanityClientEffects;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.registry.Level2Audio;
import com.rickr.backrooms.registry.Level2ChamberBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client side of Level 2's oddities (world/layout/Level2Oddities). All quiet, all positional:
 * <ul>
 *   <li><b>heart_gauge</b>: the needle is drawn by {@link HeartGaugeRenderer}; here the gauge
 *       ticks softly with each of your heartbeats while you stand within a few blocks.</li>
 *   <li><b>warm_door</b>: a low boiler-room breath behind the door, and the picture warms a
 *       little as you come close (no shimmer, no blur: just warmer light and air).</li>
 *   <li><b>wrong_valves</b>: every valve in the gallery is shut, yet now and then water rushes
 *       through the header over the manifold.</li>
 *   <li><b>inverted_pipe</b>: now and then something knocks inside the pipe on one wall, and a
 *       moment later the same knock answers from the pipe on the other wall.</li>
 * </ul>
 * The repeat_section needs nothing here: it is all stencils, dead bulbs and chalk.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2OddityEffects {
    /** Warm: a slightly warmer, softer picture with a little warm haze. No breath (exposure drift). */
    private static final AtmosphereGrade.Mood WARM = new AtmosphereGrade.Mood(1.03f, 1.04f, 0.92f,
            1.05f, 0.97f, 0.88f, 1.06f, 1.0f, 0.88f,
            0.012f, 0.008f, 0.004f, 0.30f, 0.20f, 0.12f, 4.0f, 0.035f, 0.30f, 0.45f, 0.30f, 0.0f);
    /** The warm door: heard from this far (blocks), felt from this far, and how strongly at most. */
    private static final double WARM_HEAR = 10.0;
    private static final double WARM_FEEL = 4.5;
    private static final float WARM_WEIGHT = 0.35f;
    /** The heart gauge ticks while you're this close. */
    private static final double GAUGE_RANGE = 6.0;
    /** The knocking pipe and the rushing gallery are heard from this far. */
    private static final double KNOCK_RANGE = 10.0;
    /** Ticks between the knock on one wall and its answer on the other. */
    private static final long ECHO_DELAY = 11;

    private static final Map<AnomalySite, MachineLoopSound> WARM_LOOPS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static float lastPulse;

    private Level2OddityEffects() {}

    /** Called from {@link Level2AnomalyEffects#register}. */
    static void register() {
        AnomalyClientEffects.register(Level2Sites.HEART_GAUGE, Level2OddityEffects::heartGauge);
        AnomalyClientEffects.register(Level2Sites.WARM_DOOR, Level2OddityEffects::warmDoor);
        AnomalyClientEffects.register(Level2Sites.WRONG_VALVES, Level2OddityEffects::wrongValves);
        AnomalyClientEffects.register(Level2Sites.INVERTED_PIPE, Level2OddityEffects::invertedPipe);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Level2ChamberBlocks.HEART_GAUGE_ENTITY.get(), HeartGaugeRenderer::new);
    }

    /** One soft tick of the gauge's needle on each beat (the pulse jumps from low to high). */
    private static void heartGauge(Minecraft mc, AnomalySite site, boolean inside, float seconds) {
        float pulse = SanityClientEffects.heartbeatPulse();
        boolean beat = pulse > 0.8f && lastPulse <= 0.8f;
        lastPulse = pulse;
        double gx = site.anchorX() + 0.5;
        double gy = Level2Sites.FLOOR_Y + 2.55;
        double gz = site.anchorZ() + 0.5;
        if (beat && eye(mc).distanceTo(new Vec3(gx, gy, gz)) <= GAUGE_RANGE) {
            play(mc, Level2Audio.GAUGE_TICK.get(), gx, gy, gz, 0.25f, 0.95f + RANDOM.nextFloat() * 0.1f);
        }
    }

    /** The breath behind the door (a loop kept alive while you're near) and the warmth in front of it. */
    private static void warmDoor(Minecraft mc, AnomalySite site, boolean inside, float seconds) {
        double dx = site.anchorX() + 0.5;
        double dy = Level2Sites.FLOOR_Y + 1.5;
        double dz = site.anchorZ() + 0.5;
        double dist = eye(mc).distanceTo(new Vec3(dx, Level2Sites.FLOOR_Y + 1.62, dz));
        MachineLoopSound loop = WARM_LOOPS.get(site);
        if (dist <= WARM_HEAR) {
            if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
                loop = new MachineLoopSound(Level2Audio.WARM_DOOR.get(), dx, dy, dz, site.salt(), 0.5f, false);
                mc.getSoundManager().play(loop);
                WARM_LOOPS.put(site, loop);
                if (WARM_LOOPS.size() > 8) {
                    WARM_LOOPS.values().removeIf(MachineLoopSound::isStopped);
                }
            }
            loop.keepAlive();
        }
        if (dist < WARM_FEEL) {
            AtmosphereGrade.setAnomalyMood(WARM, (float) (WARM_WEIGHT * (1.0 - dist / WARM_FEEL)));
        }
    }

    /** Water rushing through the header of a gallery whose every valve is shut. */
    private static void wrongValves(Minecraft mc, AnomalySite site, boolean inside, float seconds) {
        if (!inside || mc.level == null) {
            return;
        }
        long period = 160 + Math.floorMod(site.salt() >>> 9, 120);
        if (Math.floorMod(mc.level.getGameTime() + site.salt(), period) != 0) {
            return;
        }
        int side = backSide(site);
        double bz = (side < 0 ? site.minZ() : site.maxZ()) + 0.5;
        play(mc, Level2Audio.PIPE_RUSH.get(), site.anchorX() + 0.5, Level2Sites.FLOOR_Y + 4.5, bz, 0.55f,
                0.9f + RANDOM.nextFloat() * 0.15f);
    }

    /** A knock in the pipe on one wall, then the same knock from the pipe across the corridor. */
    private static void invertedPipe(Minecraft mc, AnomalySite site, boolean inside, float seconds) {
        if (mc.level == null) {
            return;
        }
        double x = site.anchorX() + 0.5;
        if (Math.abs(eye(mc).x - x) > KNOCK_RANGE || Math.abs(eye(mc).z - site.anchorZ()) > KNOCK_RANGE) {
            return;
        }
        long period = 260 + Math.floorMod(site.salt() >>> 11, 200);
        long phase = Math.floorMod(mc.level.getGameTime() + site.salt(), period);
        int side = backSide(site); // the wall the pipe goes into
        double za = site.anchorZ() + 0.5;
        double zb = site.anchorZ() - side * 6 + 0.5;
        if (phase == 0) {
            play(mc, Level2Audio.PIPE_KNOCK.get(), x, Level2Sites.FLOOR_Y + 2.5, za, 0.6f, 1.0f);
        } else if (phase == ECHO_DELAY) {
            play(mc, Level2Audio.PIPE_KNOCK.get(), x, Level2Sites.FLOOR_Y + 4.5, zb, 0.6f, 1.0f);
        }
    }

    /** The corridor wall a site's facing points at: -1 north, +1 south. */
    private static int backSide(AnomalySite site) {
        return site.facing() == net.minecraft.core.Direction.NORTH.get2DDataValue() ? -1 : 1;
    }

    private static Vec3 eye(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    private static void play(Minecraft mc, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.BLOCKS, volume, pitch, RANDOM, x, y, z));
    }
}
