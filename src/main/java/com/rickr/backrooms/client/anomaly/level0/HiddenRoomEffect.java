package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.client.audio.Level0Soundscape;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0HiddenRooms;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Level 0's hidden rooms on the client: each one has its own sound, coming from inside the room,
 * faint through the walls when you're close, which is often the first hint that the block of wall
 * beside you is hollow (the sounds and their ranges are client/audio/Level0Soundscape's):
 * <ul>
 *   <li>the tenant's room behind the slit: a clock ticking, though there's no clock in it;</li>
 *   <li>the stockroom: almost no sound at all (the room tone ducks away) and, now and then, the
 *       creak of a chair, as if someone had just got up;</li>
 *   <li>the crawlspace: the old radio, still playing to nobody;</li>
 *   <li>the sealed room: the pressure-and-ringing tone of air that has been shut in for years.</li>
 * </ul>
 */
final class HiddenRoomEffect implements AnomalyClientEffect {
    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (mc.player == null) {
            return;
        }
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        double[] c = Level0HiddenRooms.roomCentre(site, layout.floorY(), layout.ceilingY());
        Level0Soundscape.HiddenTone tone = switch (site.kind()) {
            case Level0HiddenRooms.SLIT -> Level0Soundscape.HiddenTone.CLOCK;
            case Level0HiddenRooms.STOCKROOM -> Level0Soundscape.HiddenTone.STILL;
            case Level0HiddenRooms.CRAWLSPACE -> Level0Soundscape.HiddenTone.RADIO;
            default -> Level0Soundscape.HiddenTone.SEALED;
        };
        long key = BlockPos.asLong((int) Math.floor(c[0]), (int) Math.floor(c[1]), (int) Math.floor(c[2]));
        Level0Soundscape.hiddenRoom(key, tone, c[0], c[1], c[2]);
    }
}
