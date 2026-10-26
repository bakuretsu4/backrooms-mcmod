package com.rickr.backrooms.client.anomaly.level37;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * What you notice in a looping tunnel ({@code level_37/looping_tunnel}; the journal's "looping
 * hallways", https://backrooms-wiki.wikidot.com/level-37): nothing at first, the server moves
 * you back so smoothly that the picture doesn't jump. But every time round, the same single drip
 * falls the same few steps ahead of you, and the light dims a little more.
 *
 * <p>The client counts the rounds itself: inside the tunnel, a jump of a whole cell from one tick
 * to the next can only be the server bringing you round.
 */
final class LoopingTunnelEffect implements AnomalyClientEffect {
    /** Each round dims the picture this much more (as a share of the full "dim" mood) ... */
    private static final float DIM_PER_ROUND = 0.22f;
    /** ... up to this much. */
    private static final float DIM_MAX = 0.9f;
    /** The drip falls this far ahead of you (blocks). */
    private static final double DRIP_AHEAD = 9.0;
    /** Away from the tunnel this long (ticks) and it forgets the rounds. */
    private static final int FORGET_TICKS = 40;
    /** Darker, flatter, the air thick and olive-black: the tunnel's dark creeping in. */
    private static final AtmosphereGrade.Mood DIM = new AtmosphereGrade.Mood(0.7f, 1.04f, 0.72f,
            0.96f, 0.96f, 0.9f, 0.95f, 0.94f, 0.88f,
            0.004f, 0.004f, 0.003f, 0.07f, 0.075f, 0.05f, 2.0f, 0.075f, 0.65f, 0.45f, 0.1f, 0.02f);

    @Nullable
    private AnomalySite tunnel;
    private int rounds;
    private double lastAlong;
    private int lastTick;
    /** The same pitch every time: it is the same drip. */
    private final RandomSource dripRandom = RandomSource.create(37L);

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (!inside) {
            return;
        }
        LocalPlayer player = mc.player;
        double along = Level37AnomalyPlan.loopAlong(site, player.getX(), player.getZ());
        if (!site.equals(tunnel) || player.tickCount - lastTick > FORGET_TICKS) {
            tunnel = site;
            rounds = 0;
        } else if (Math.abs(along - lastAlong) > Level37AnomalyPlan.LOOP_SHIFT / 2.0) {
            rounds++;
            // You were moved back against the way you walk: the drip falls ahead, the way you walk.
            drip(mc, site, player, along < lastAlong ? 1 : -1);
        }
        lastAlong = along;
        lastTick = player.tickCount;
        if (rounds > 0) {
            AtmosphereGrade.setAnomalyMood(DIM, Math.min(DIM_MAX, rounds * DIM_PER_ROUND));
        }
    }

    private void drip(Minecraft mc, AnomalySite site, LocalPlayer player, int dir) {
        boolean alongX = Level37AnomalyPlan.loopAlongX(site);
        double x = player.getX() + (alongX ? dir * DRIP_AHEAD : 0.0);
        double z = player.getZ() + (alongX ? 0.0 : dir * DRIP_AHEAD);
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.L37_LOOP_DRIP.get(), SoundSource.AMBIENT,
                0.7f, 1.0f, dripRandom, x, player.getY() + 2.5, z));
    }
}
