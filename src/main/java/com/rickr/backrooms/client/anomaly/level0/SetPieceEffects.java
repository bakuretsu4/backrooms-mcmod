package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.client.audio.Level0Soundscape;
import com.rickr.backrooms.registry.Level0LightBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0Atrium;
import com.rickr.backrooms.world.layout.Level0FrontOffice;
import com.rickr.backrooms.world.layout.Level0SetPieces;
import com.rickr.backrooms.world.layout.Level0ShaftHall;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The three Level 0 set pieces on the client: their sounds (through client/audio/Level0Soundscape,
 * which owns the actual loops) and the look of the picture inside them.
 */
final class SetPieceEffects {
    private SetPieceEffects() {}

    // exposure, contrast, saturation | shadow tint | highlight tint | lift | haze colour, start, density,
    // max | contact shadows, glow, breath (see AtmosphereGrade.Mood)

    /**
     * Under the atrium's void: a little colder and deeper than the halls, a faint grey haze that
     * starts far out so the top of the shaft melts into black, and the camera's breath a touch
     * stronger (a space too big for the eye).
     */
    private static final AtmosphereGrade.Mood VOID = new AtmosphereGrade.Mood(1.15f, 1.10f, 0.86f,
            0.96f, 0.98f, 1.04f, 1.0f, 0.99f, 0.95f,
            0.003f, 0.003f, 0.004f, 0.10f, 0.10f, 0.12f, 18.0f, 0.018f, 0.22f, 0.55f, 0.16f, 0.014f);
    /** The shaft hall: dark and dusty, a dry beige haze, strong corners. */
    private static final AtmosphereGrade.Mood DUST = new AtmosphereGrade.Mood(1.18f, 1.10f, 0.88f,
            0.98f, 0.98f, 1.0f, 1.0f, 0.99f, 0.95f,
            0.004f, 0.0035f, 0.003f, 0.12f, 0.11f, 0.09f, 14.0f, 0.02f, 0.18f, 0.55f, 0.15f, 0.012f);
    /** The front office: the dark leans red from the beacon's sweep, glow up so the lamps bloom. */
    private static final AtmosphereGrade.Mood ALARM = new AtmosphereGrade.Mood(1.12f, 1.10f, 0.95f,
            1.06f, 0.92f, 0.90f, 1.04f, 0.97f, 0.94f,
            0.006f, 0.002f, 0.002f, 0.14f, 0.05f, 0.05f, 12.0f, 0.02f, 0.20f, 0.50f, 0.20f, 0.012f);
    /** The hidden reception room: warm, soft, safe. */
    private static final AtmosphereGrade.Mood RECEPTION = new AtmosphereGrade.Mood(1.20f, 1.0f, 1.0f,
            1.02f, 0.98f, 0.80f, 1.05f, 1.0f, 0.88f,
            0.010f, 0.008f, 0.004f, 0.40f, 0.34f, 0.20f, 30.0f, 0.010f, 0.10f, 0.26f, 0.20f, 0.006f);

    private static LevelLayout layout() {
        return BackroomsLevel.LEVEL_0.layout();
    }

    /** The atrium: its cold air high in the void, and its mood under the void and upstairs. */
    static final class Atrium implements AnomalyClientEffect {
        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            LocalPlayer player = mc.player;
            if (player == null) {
                return;
            }
            double cx = site.centerX();
            double cz = site.centerZ();
            double dx = player.getX() - cx;
            double dz = player.getZ() - cz;
            if (dx * dx + dz * dz < 56.0 * 56.0) {
                Level0Soundscape.atrium(cx, layout().floorY(), Level0SetPieces.ROOF_Y, cz);
            }
            if (!inside) {
                return;
            }
            if (Level0Atrium.upstairs(site, player.getX(), player.getY(), player.getZ())) {
                AtmosphereGrade.setAnomalyMood(VOID, 0.35f);
            } else if (Level0Atrium.underVoid(site, player.getBlockX(), player.getBlockZ())) {
                AtmosphereGrade.setAnomalyMood(VOID, 0.7f);
            } else {
                AtmosphereGrade.setAnomalyMood(VOID, 0.3f);
            }
        }
    }

    /** The shaft hall: every shaft within 40 blocks may sound from up there; dusty mood inside. */
    static final class ShaftHall implements AnomalyClientEffect {
        private final Map<AnomalySite, List<Level0ShaftHall.Shaft>> shafts = new HashMap<>();

        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            LocalPlayer player = mc.player;
            if (player == null) {
                return;
            }
            if (shafts.size() > 16 && !shafts.containsKey(site)) {
                shafts.clear();
            }
            for (Level0ShaftHall.Shaft shaft : shafts.computeIfAbsent(site, Level0ShaftHall::shafts)) {
                double dx = player.getX() - shaft.centerX();
                double dz = player.getZ() - shaft.centerZ();
                if (dx * dx + dz * dz < 40.0 * 40.0) {
                    Level0Soundscape.shaftHole(shaft.centerX(), Level0SetPieces.ROOF_Y, shaft.centerZ());
                }
            }
            if (inside && Level0ShaftHall.inside(site, player.getBlockX(), player.getBlockZ())) {
                AtmosphereGrade.setAnomalyMood(DUST, 0.55f);
            }
        }
    }

    /**
     * The front office: the alarm from the beacon (heard ~80 blocks off) while the beacon turns,
     * silence once the drawn door is real (the server stops the beacon); the red mood in the dark
     * room; the warm mood and the room's tone (a clock, a live microphone) in the hidden room.
     */
    static final class FrontOffice implements AnomalyClientEffect {
        private static final double ALARM_RANGE = 96.0;

        @Override
        public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
            LocalPlayer player = mc.player;
            ClientLevel level = mc.level;
            if (player == null || level == null) {
                return;
            }
            BlockPos beacon = Level0FrontOffice.beacon(site, layout().ceilingY());
            boolean inRoom = Level0FrontOffice.inRoom(site, player.getX(), player.getY(), player.getZ());
            boolean turning = beaconTurning(level, beacon);
            if (!inRoom && turning && player.distanceToSqr(beacon.getX() + 0.5, beacon.getY() + 0.5, beacon.getZ() + 0.5)
                    < ALARM_RANGE * ALARM_RANGE) {
                Level0Soundscape.frontOfficeAlarm(beacon.getX() + 0.5, beacon.getY() + 0.5, beacon.getZ() + 0.5, 1.0f);
            }
            BlockPos centre = Level0FrontOffice.roomCentre(site);
            if (player.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5) < 16.0 * 16.0) {
                Level0Soundscape.hiddenRoom(centre.asLong(), Level0Soundscape.HiddenTone.OFFICE,
                        centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
            }
            if (inRoom) {
                AtmosphereGrade.setAnomalyMood(RECEPTION, 0.6f);
            } else if (inside && Level0FrontOffice.inOffice(site, player.getBlockX(), player.getBlockZ())) {
                AtmosphereGrade.setAnomalyMood(ALARM, turning ? 0.45f : 0.2f);
            }
        }

        /** The beacon's own block says whether it turns (unloaded = assume it does: the alarm is heard far off). */
        private static boolean beaconTurning(ClientLevel level, BlockPos beacon) {
            if (!level.isLoaded(beacon)) {
                return true;
            }
            BlockState state = level.getBlockState(beacon);
            return !state.is(Level0LightBlocks.BEACON.get()) || state.getValue(BlockStateProperties.LIT);
        }
    }
}
