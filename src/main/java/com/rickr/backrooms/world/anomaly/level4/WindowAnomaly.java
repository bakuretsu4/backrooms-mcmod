package com.rickr.backrooms.world.anomaly.level4;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Window (Entity 2): one clear pane in a run of rainy office windows, and through it a blurry,
 * sunny, familiar place. It is not a window. Canon (https://backrooms-wiki.wikidot.com/entity-2):
 * windows that aren't blacked out are to be avoided at all costs; each shows its viewer a familiar
 * landscape, sends comforting whispers to draw them closer, and grabs anyone who comes within a
 * metre or two. Bases cover their windows for this reason
 * (https://backrooms-wiki.wikidot.com/level-4), so here the panes on each side of it are taped over
 * with cardboard: the warning sign.
 *
 * <ul>
 *   <li>Looking at it from up close slowly drains your sanity ({@link #LOOK_DRAIN} a second).</li>
 *   <li>Within about 2 m it pulls you gently toward the glass (done on the client, which moves the
 *       player: see {@code client/anomaly/level4}); you can always walk away.</li>
 *   <li>Touch it and it grabs you: the screen goes dark, it hurts (never below half a heart) and
 *       takes a big bite of sanity, then throws you back across the room.</li>
 * </ul>
 * The whispers and the warm light are client effects.
 */
final class WindowAnomaly implements AnomalyBehavior {
    /** Sanity lost per second while you look at the window. */
    private static final float LOOK_DRAIN = 0.6f;
    private static final double LOOK_RANGE = 14.0;
    private static final double LOOK_HALF_ANGLE = 25.0;
    /** Closer than this (blocks, from the middle of the glass) and it has you. */
    private static final double GRAB_RANGE = 1.0;
    private static final float GRAB_DAMAGE = 6.0f;
    private static final float GRAB_SANITY = 25.0f;
    private static final double THROW_SPEED = 1.3;
    private static final double THROW_LIFT = 0.42;
    /** After a grab it leaves you be for a few seconds. */
    private static final long GRAB_COOLDOWN = 100;

    private static final Map<UUID, Long> LAST_GRAB = new HashMap<>();

    /** The middle of the window's glass (two panes tall, on the room side of the wall). */
    static Vec3 glass(AnomalySite site) {
        int facing = site.facing();
        return new Vec3(site.anchorX() + 0.5 + 0.5 * Level4AnomalyPlan.stepX(facing),
                LevelLayout.FLOOR_Y + 3.0,
                site.anchorZ() + 0.5 + 0.5 * Level4AnomalyPlan.stepZ(facing));
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        Vec3 glass = glass(site);
        if (ticksInside % 20 == 0 && !player.isCreative()
                && Observation.sees(player, glass, LOOK_RANGE, LOOK_HALF_ANGLE)) {
            SanityService.drain(player, LOOK_DRAIN);
        }
        double dx = player.getX() - glass.x;
        double dz = player.getZ() - glass.z;
        if (dx * dx + dz * dz < GRAB_RANGE * GRAB_RANGE) {
            long now = player.serverLevel().getGameTime();
            Long last = LAST_GRAB.get(player.getUUID());
            if (last == null || now - last >= GRAB_COOLDOWN) {
                LAST_GRAB.put(player.getUUID(), now);
                grab(player, site, glass);
            }
        }
    }

    private static void grab(ServerPlayer player, AnomalySite site, Vec3 glass) {
        player.serverLevel().playSound(null, glass.x, glass.y, glass.z, ModSounds.L4_WINDOW_GRAB.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false, false));
        // It hurts, but it never kills: you always keep at least half a heart.
        float damage = Math.min(GRAB_DAMAGE, player.getHealth() - 1.0f);
        if (damage > 0.0f) {
            player.hurt(player.damageSources().magic(), damage);
        }
        if (!player.isCreative()) {
            SanityService.drain(player, GRAB_SANITY);
        }
        // Flung back into the room, away from the glass.
        int facing = site.facing();
        player.setDeltaMovement(Level4AnomalyPlan.stepX(facing) * THROW_SPEED, THROW_LIFT,
                Level4AnomalyPlan.stepZ(facing) * THROW_SPEED);
        player.hurtMarked = true; // sends the new motion to the client
        player.resetFallDistance();
    }

    static void forget(UUID id) {
        LAST_GRAB.remove(id);
    }
}
