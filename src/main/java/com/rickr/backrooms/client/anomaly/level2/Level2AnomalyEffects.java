package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.block.Level2LampGlow;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client effects of Level 2's anomalies (sources in {@link Level2Sites}):
 * <ul>
 *   <li><b>brownout</b>: the machines are silent (the level's machine drone fades out) and the
 *       picture cools toward the violet of the emergency lights: the warm grade of the lit
 *       corridor would turn their violet muddy brown.</li>
 * </ul>
 * The series-circuit lamp failures are drawn by client/light (FixturePower, BackroomsLights); the
 * dead bulbs of a failed circuit through block/Level2LampGlow, installed in {@link #register}.
 * The quiet oddities are in {@link Level2OddityEffects}.
 */
public final class Level2AnomalyEffects {
    /**
     * Brownout: the warm lamps are dead and the violet emergency lights are all there is. Cooler,
     * violet-tinged shadows and air, a little more colour so the violet holds, deeper corners.
     */
    private static final AtmosphereGrade.Mood BROWNOUT = new AtmosphereGrade.Mood(0.98f, 1.12f, 0.94f,
            0.97f, 0.92f, 1.08f, 1.0f, 0.97f, 1.04f,
            0.006f, 0.004f, 0.010f, 0.06f, 0.04f, 0.09f, 8.0f, 0.025f, 0.30f, 0.62f, 0.16f, 0.010f);

    public static void register() {
        AnomalyClientEffects.register(Level2Sites.BROWNOUT, (mc, site, inside, seconds) -> {
            if (inside) {
                Level2Sounds.duckAmbience(0.1f);
                AtmosphereGrade.setAnomalyMood(BROWNOUT, 0.75f);
            }
        });
        // Bulbs of a failed series circuit are drawn dead.
        Level2LampGlow.lit = (level, pos) -> {
            ClientLevel world = Minecraft.getInstance().level;
            if (world == null || !FixturePower.level2OnCircuit(pos.getY())
                    || BackroomsLevel.fromDimension(world.dimension()) != BackroomsLevel.LEVEL_2) {
                return true;
            }
            return FixturePower.level2CircuitPowered(FixturePower.level2Circuit(pos.getX() + 0.5, pos.getZ() + 0.5),
                    world.getGameTime());
        };
        // The quiet oddities (heart gauge, wrong valves, inverted pipe).
        Level2OddityEffects.register();
    }

    private Level2AnomalyEffects() {}
}
