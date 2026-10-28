package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level0Blackout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A Blackout Zone on the client (https://backrooms-wiki.wikidot.com/level-0). The wiki's advice is
 * to follow the buzzing out, "but the sound doesn't stay in one place... then suddenly it's behind
 * you". So: a fluorescent buzz that seems to come from just beyond the edge of the zone, roughly in
 * the direction that would lead you out, fades and turns up somewhere else every few seconds, and
 * now and then turns up close behind you (quieter, and it fades in over a second or so: unsettling,
 * never a jump). It is fair: the longer you're in, the more truly it points the way out (its
 * wander narrows from 70 to 25 degrees over the first minute). The picture gets darker the deeper
 * in you go. (The lights behind you dying as you step in is the server's: BlackoutBehavior.)
 */
final class BlackoutEffect implements AnomalyClientEffect {
    /**
     * Deep dark: about 30% under Level 0's exposure, washed-out colour that keeps the level's
     * yellow-green cast, a black haze that swallows the far end of a hall, crushed blacks.
     */
    private static final AtmosphereGrade.Mood DARK = new AtmosphereGrade.Mood(0.75f, 1.10f, 0.65f,
            0.95f, 1.0f, 0.78f, 0.98f, 1.0f, 0.88f,
            0.002f, 0.002f, 0.002f, 0.02f, 0.02f, 0.015f, 2.0f, 0.085f, 0.70f, 0.50f, 0.08f, 0.025f);
    private static final float BUZZ_VOLUME = 0.8f;
    /** Close behind you it is quieter, and comes in slowly. */
    private static final float BEHIND_VOLUME = 0.45f;
    private static final float BEHIND_FADE = 0.05f;
    private static final float NORMAL_FADE = 0.08f;
    /** How far either side of "straight out" the buzz may wander: wide at first, narrow after a minute. */
    private static final double WANDER_FIRST_DEG = 70.0;
    private static final double WANDER_LAST_DEG = 25.0;
    /** How far under the ceiling the buzz sits: up among the dead tubes. */
    private static final double BUZZ_BELOW_CEILING = 0.4;

    /** Where one zone's buzz is and what it is doing. */
    private static final class Buzz {
        Level0LoopSound sound;
        /** When it next moves (ms). */
        long nextMoveMs;
        /** While fading out before a move: where it goes next. */
        Vec3 pending;
        /** True while it is (or is going to be) close behind you: quieter, slower to fade in. */
        boolean behind;
        boolean pendingBehind;
    }

    private final Map<AnomalySite, Buzz> buzzes = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player == null || !inside) {
            return; // an untouched buzz fades away by itself
        }
        long now = System.currentTimeMillis();
        Buzz buzz = buzzes.get(site);
        if (buzz == null || buzz.sound == null || buzz.sound.dead()) {
            if (buzzes.size() > 16) {
                buzzes.clear();
            }
            buzz = new Buzz();
            Vec3 at = wayOut(site, player, secondsInside);
            buzz.sound = new Level0LoopSound(ModSounds.L0_BLACKOUT_BUZZ.get(), SoundSource.BLOCKS, at.x, at.y, at.z, false).play();
            buzz.nextMoveMs = now + 4000 + random.nextInt(5000);
            buzzes.put(site, buzz);
        }
        Level0LoopSound sound = buzz.sound;
        sound.touch();
        if (buzz.pending != null) {
            // Fading out; once quiet, it turns up at the new place (and fades in there).
            sound.target = 0.0f;
            sound.fade = NORMAL_FADE;
            if (sound.currentVolume() < 0.05f) {
                sound.moveTo(buzz.pending.x, buzz.pending.y, buzz.pending.z);
                buzz.pending = null;
                buzz.behind = buzz.pendingBehind;
            }
        } else {
            sound.target = buzz.behind ? BEHIND_VOLUME : BUZZ_VOLUME;
            sound.fade = buzz.behind ? BEHIND_FADE : NORMAL_FADE;
            if (now >= buzz.nextMoveMs) {
                if (!buzz.behind && random.nextFloat() < 0.25f) {
                    // Close behind you. It slips out and comes back there, softly.
                    Vec3 look = player.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
                    buzz.pending = new Vec3(player.getX() - flat.x * 2.5, buzzHeight(), player.getZ() - flat.z * 2.5);
                    buzz.pendingBehind = true;
                    buzz.nextMoveMs = now + 3500 + random.nextInt(1500);
                } else {
                    buzz.pending = wayOut(site, player, secondsInside);
                    buzz.pendingBehind = false;
                    buzz.nextMoveMs = now + 5000 + random.nextInt(6000);
                }
            }
        }

        double depth = Level0Blackout.depth(site, player.getX(), player.getZ());
        AtmosphereGrade.setAnomalyMood(DARK, (float) Math.min(1.0, 0.45 + depth * 1.2));
    }

    /**
     * A spot just beyond the zone's edge, roughly the way out from where you are: the direction from
     * the middle of the zone through you, give or take 70 degrees at first and 25 after a minute.
     */
    private Vec3 wayOut(AnomalySite site, LocalPlayer player, float secondsInside) {
        double cx = site.anchorX() + 0.5;
        double cz = site.anchorZ() + 0.5;
        double base = Math.atan2(player.getZ() - cz, player.getX() - cx);
        double t = Math.min(1.0, secondsInside / 60.0);
        double wander = WANDER_FIRST_DEG + (WANDER_LAST_DEG - WANDER_FIRST_DEG) * t;
        double angle = base + (random.nextDouble() * 2.0 - 1.0) * Math.toRadians(wander);
        double rx = (site.maxX() - site.minX()) / 2.0 + 3.0;
        double rz = (site.maxZ() - site.minZ()) / 2.0 + 3.0;
        return new Vec3(cx + Math.cos(angle) * rx, buzzHeight(), cz + Math.sin(angle) * rz);
    }

    /** Height of the buzz, from Level 0's own ceiling, so it follows the layout if that changes. */
    private static double buzzHeight() {
        return BackroomsLevel.LEVEL_0.layout().ceilingY() - BUZZ_BELOW_CEILING;
    }
}
