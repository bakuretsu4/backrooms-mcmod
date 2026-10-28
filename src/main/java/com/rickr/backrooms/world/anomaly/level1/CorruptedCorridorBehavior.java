package com.rickr.backrooms.world.anomaly.level1;

import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The Corrupted Corridor, Level 1.1 (<a href="https://backrooms-wiki.wikidot.com/level-1-1">level-1-1</a>):
 * a straight white-cinderblock, blue-tile corridor that "gets more unstable the further you go".
 * The blocks are built by {@link Level1AnomalyPlan#fillCorridor}; this makes each sector behave:
 * <ol>
 *   <li>Humming lights and a crate. Nothing wrong yet, except the hum.</li>
 *   <li>Slanted floor, colours shifting to wrong hues (client), identical sections repeating:
 *       walking past the middle of the second copy of a stretch quietly puts you back in the first,
 *       two to four times, before it lets you on. Two spots of wall can be walked into, and they
 *       throw you out somewhere else in Level 1 (a few hundred blocks away).</li>
 *   <li>Darkness that lights can't break: no fixtures at all, the Darkness effect and a very dark
 *       picture (client), with a few unexplained bright patches.</li>
 *   <li>Black and white, undulating walls, and stretches of floor with no collision. Falling
 *       through, you wake up back at the entrance, dazed.</li>
 *   <li>The "beckoning light" at the end: walking into it takes you to Level 2.</li>
 * </ol>
 * Never a trap: the doorway you came in by is always open, and every way of leaving the corridor
 * puts you on solid floor.
 */
public final class CorruptedCorridorBehavior implements AnomalyBehavior {
    /** Where each player in a corridor was along it last tick, and how often it looped them. */
    private static final Map<UUID, Walker> WALKERS = new HashMap<>();

    private static final class Walker {
        double lastT;
        int loops;
    }

    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        Walker walker = new Walker();
        walker.lastT = Level1AnomalyPlan.corridorTExact(site, player.getX(), player.getZ());
        WALKERS.put(player.getUUID(), walker);
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        WALKERS.remove(player.getUUID());
        // Drifted out sideways while falling through Sector 4's floor: still a fall, still a wake-up.
        if (player.level().dimension() == BackroomsLevel.LEVEL_1.dimension() && player.getY() < LevelLayout.FLOOR_Y - 1
                && site.distanceTo(player.getX(), player.getZ()) < 6.0) {
            wakeAtEntrance(player, site);
        }
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        Walker walker = WALKERS.computeIfAbsent(player.getUUID(), id -> new Walker());
        double t = Level1AnomalyPlan.corridorTExact(site, player.getX(), player.getZ());
        int blockT = (int) Math.floor(t);
        int a = Level1AnomalyPlan.corridorA(site, player.getBlockX(), player.getBlockZ());

        if (player.getY() < LevelLayout.FLOOR_Y - 1) {
            wakeAtEntrance(player, site);
            walker.loops = 0; // and the corridor plays its tricks on you all over again
            walker.lastT = Level1AnomalyPlan.DOOR_T + 3.5;
            return;
        }
        if (Level1AnomalyPlan.corridorNoclip(blockT, a)) {
            noclipOut(player);
            return;
        }
        if (blockT >= Level1AnomalyPlan.LIGHT_TRIGGER && a >= 2 && a <= 4) {
            intoTheLight(player);
            return;
        }

        int sector = Level1AnomalyPlan.corridorSector(blockT);
        if (sector == 1 && ticksInside % 80 == 0) {
            player.playNotifySound(ModSounds.LIGHT_HUM.get(), SoundSource.AMBIENT, 0.35f, 1.0f);
        } else if (sector == 3 && ticksInside % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
        } else if (sector == 4 && ticksInside % 120 == 0) {
            player.playNotifySound(ModSounds.L1_CORRIDOR_DRONE.get(), SoundSource.AMBIENT, 0.6f, 1.0f);
        }

        // The repeating section: crossing the trigger line walking forward (not teleporting or
        // walking back) shifts you one copy back. The copies are identical, so nothing on screen jumps.
        if (walker.loops < maxLoops(site) && walker.lastT < Level1AnomalyPlan.LOOP_TRIGGER
                && t >= Level1AnomalyPlan.LOOP_TRIGGER && t - walker.lastT < 1.5) {
            Direction facing = Level1AnomalyPlan.corridorFacing(site);
            int back = Level1AnomalyPlan.LOOP_LENGTH;
            Teleports.shift(player, -facing.getStepX() * back, 0.0, -facing.getStepZ() * back);
            walker.loops++;
            t -= back;
        }
        walker.lastT = t;
    }

    /** How many times this corridor repeats its section before letting you on (2-4). */
    private static int maxLoops(AnomalySite site) {
        return 2 + (int) ((site.salt() >>> 8) % 3);
    }

    /** Through the wall and out: somewhere else in Level 1, a few hundred blocks away. */
    private static void noclipOut(ServerPlayer player) {
        RandomSource random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 160.0 + random.nextInt(240);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        // The arrival search never picks a spot inside an anomaly (Level1Layout.isSafeArrival).
        // Still Level 1, so no arrival card: just the datamosh and a different place.
        Travel.moshTo(player, p -> Travel.sendToQuietly(p, BackroomsLevel.LEVEL_1, x, z));
    }

    /** The beckoning light: on to Level 2, like taking the exit stairwell. */
    private static void intoTheLight(ServerPlayer player) {
        Checkpoints.clear(player);
        Travel.sendTo(player, BackroomsLevel.LEVEL_2, player.getBlockX(), player.getBlockZ());
    }

    /** Fell through the floor of Sector 4: you wake up just inside the entrance, dazed. */
    private static void wakeAtEntrance(ServerPlayer player, AnomalySite site) {
        BlockPos spot = Level1AnomalyPlan.corridorEntrance(site);
        float yaw = Level1AnomalyPlan.corridorFacing(site).toYRot();
        Teleports.to(player, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 10.0f);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 1, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        player.playNotifySound(ModSounds.WAKE_UP.get(), SoundSource.MASTER, 1.0f, 1.0f);
    }
}
