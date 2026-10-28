package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.block.Level2LampGlow;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client effects of Level 2's anomalies (sources in {@link Level2Sites}):
 * <ul>
 *   <li><b>brownout</b>: the machines are silent (the level's machine drone fades out) and the
 *       picture cools toward the violet of the emergency lights: the warm grade of the lit
 *       corridor would turn their violet muddy brown.</li>
 *   <li><b>hot zone</b>: the loud, faulty whirring of the machinery (a positional loop heard from
 *       down the corridor), steam venting from the pipes in bursts with a hiss, and inside a hot,
 *       shimmering picture.</li>
 *   <li><b>void door</b>: in the void there is no sound at all and no light but the doorway: the
 *       picture loses its haze, so the black is truly black and the doorway glows.</li>
 *   <li><b>loop hall</b>: the office hallway is quiet; the machine drone of the tunnels fades.</li>
 * </ul>
 * The series-circuit lamp failures are drawn by client/light (FixturePower, BackroomsLights); the
 * dead bulbs of a failed circuit through block/Level2LampGlow, installed in {@link #register}.
 */
public final class Level2AnomalyEffects {
    /**
     * Heat: warm, slightly over-exposed and washed out, with a thick warm haze close in, a soft
     * glow and a gentle "breath" (the exposure wavers a little, like air over hot metal; kept to
     * 2%, the old 6% pulsed the whole picture).
     */
    private static final AtmosphereGrade.Mood HEAT = new AtmosphereGrade.Mood(1.06f, 1.04f, 0.86f,
            1.08f, 0.96f, 0.84f, 1.08f, 1.0f, 0.86f,
            0.014f, 0.009f, 0.004f, 0.32f, 0.22f, 0.13f, 2.0f, 0.07f, 0.5f, 0.45f, 0.30f, 0.02f);
    /** The void: no haze at all (nothing to light it), crushed blacks, the doorway's light blooming. */
    private static final AtmosphereGrade.Mood VOID = new AtmosphereGrade.Mood(0.96f, 1.25f, 0.70f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            0.002f, 0.002f, 0.003f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.2f, 0.35f, 0.004f);
    /**
     * Brownout: the warm lamps are dead and the violet emergency lights are all there is. Cooler,
     * violet-tinged shadows and air, a little more colour so the violet holds, deeper corners.
     */
    private static final AtmosphereGrade.Mood BROWNOUT = new AtmosphereGrade.Mood(0.98f, 1.12f, 0.94f,
            0.97f, 0.92f, 1.08f, 1.0f, 0.97f, 1.04f,
            0.006f, 0.004f, 0.010f, 0.06f, 0.04f, 0.09f, 8.0f, 0.025f, 0.30f, 0.62f, 0.16f, 0.010f);
    /** Hot zones heard from this far (blocks to the room). */
    private static final double MACHINE_RANGE = 30.0;
    /** Steam is puffed for hot zones this close. */
    private static final double STEAM_RANGE = 24.0;

    private static final Map<AnomalySite, MachineLoopSound> MACHINES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static void register() {
        AnomalyClientEffects.register(Level2Sites.BROWNOUT, (mc, site, inside, seconds) -> {
            if (inside) {
                Level2Sounds.duckAmbience(0.1f);
                AtmosphereGrade.setAnomalyMood(BROWNOUT, 0.75f);
            }
        });
        AnomalyClientEffects.register(Level2Sites.HOT_ZONE, Level2AnomalyEffects::hotZone);
        AnomalyClientEffects.register(Level2Sites.VOID_DOOR, (mc, site, inside, seconds) -> {
            if (inside && mc.player.getY() < Level2Sites.FLOOR_Y - 1 && mc.player.getY() > Level2Sites.FLOOR_Y - Level2Sites.VOID_DROP) {
                Level2Sounds.silence();
                AtmosphereGrade.setAnomalyMood(VOID, 1.0f);
            }
        });
        AnomalyClientEffects.register(Level2Sites.LOOP_HALL, (mc, site, inside, seconds) -> {
            if (inside && mc.player.getY() > Level2Sites.FLOOR_Y + Level2Sites.LOOP_RISE) {
                Level2Sounds.duckAmbience(0.3f);
            }
        });
        // Bulbs of a failed series circuit are drawn dead (not the void's doorway lamp, below the floor).
        Level2LampGlow.lit = (level, pos) -> {
            ClientLevel world = Minecraft.getInstance().level;
            if (world == null || !FixturePower.level2OnCircuit(pos.getY())
                    || BackroomsLevel.fromDimension(world.dimension()) != BackroomsLevel.LEVEL_2) {
                return true;
            }
            return FixturePower.level2CircuitPowered(FixturePower.level2Circuit(pos.getX() + 0.5, pos.getZ() + 0.5),
                    world.getGameTime());
        };
        // The quiet oddities (heart gauge, warm door, wrong valves, inverted pipe).
        Level2OddityEffects.register();
    }

    private static void hotZone(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double dist = site.distanceTo(eye.x, eye.z);
        // The machinery: one loop per room, started when you come within earshot.
        MachineLoopSound loop = MACHINES.get(site);
        if (dist <= MACHINE_RANGE) {
            if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
                loop = new MachineLoopSound(site.anchorX() + 0.5, Level2Sites.FLOOR_Y + 2.0, site.anchorZ() + 0.5, site.salt());
                mc.getSoundManager().play(loop);
                MACHINES.put(site, loop);
                if (MACHINES.size() > 16) {
                    MACHINES.values().removeIf(MachineLoopSound::isStopped);
                }
            }
            loop.keepAlive();
        }
        if (dist <= STEAM_RANGE) {
            steam(mc, site);
        }
        // Heat haze: a hint of it at the doorway, full strength after a few seconds inside.
        float weight = inside ? Math.min(1.0f, 0.4f + secondsInside / 6.0f) : (float) Math.max(0.0, 0.35 * (1.0 - dist / 6.0));
        if (weight > 0.0f) {
            AtmosphereGrade.setAnomalyMood(HEAT, weight);
        }
    }

    /**
     * Each vent blows steam in bursts: 1.5-2.5 s every 4-8 s, each vent on its own rhythm (from the
     * site and the game time, so everyone sees the same bursts). A hiss when a burst starts.
     */
    private static void steam(Minecraft mc, AnomalySite site) {
        long time = mc.level.getGameTime();
        Level2Sites.Vent[] vents = Level2Sites.vents(site);
        for (int i = 0; i < vents.length; i++) {
            Level2Sites.Vent vent = vents[i];
            long h = site.salt() * 31 + i * 0x9E3779B97F4A7C15L;
            long period = 80 + Math.floorMod(h >>> 8, 80);
            long length = 30 + Math.floorMod(h >>> 20, 20);
            long phase = Math.floorMod(time + Math.floorMod(h >>> 32, period), period);
            if (phase >= length) {
                continue;
            }
            double fx = vent.facing().getStepX();
            double fz = vent.facing().getStepZ();
            double px = vent.x() + 0.5 + fx * 0.45;
            double py = vent.y() + 0.45;
            double pz = vent.z() + 0.5 + fz * 0.45;
            if (phase == 0) {
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.L2_STEAM_HISS.get(), SoundSource.BLOCKS,
                        0.7f, 0.9f + RANDOM.nextFloat() * 0.2f, RANDOM, px, py, pz));
            }
            for (int k = 0; k < 2; k++) {
                double speed = 0.12 + RANDOM.nextDouble() * 0.1;
                mc.level.addParticle(ParticleTypes.CLOUD, px, py, pz,
                        fx * speed + (RANDOM.nextDouble() - 0.5) * 0.04,
                        0.01 + RANDOM.nextDouble() * 0.03,
                        fz * speed + (RANDOM.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    private Level2AnomalyEffects() {}
}
