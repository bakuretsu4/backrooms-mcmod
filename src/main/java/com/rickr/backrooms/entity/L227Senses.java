package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The few rules every Level 227 predator shares about who it may hunt and see, kept in one place so
 * the wolves, the stags and the squirrel swarm can't drift apart.
 */
final class L227Senses {
    /** Crouching in the undergrowth shrinks how far a predator spots you. */
    static final double CROUCH_FACTOR = 0.6;

    /**
     * Someone a predator may go for: alive, playing survival or adventure, in the same world, not in
     * a safe room, and not hiding in furniture (unless {@code mob} saw them get in).
     */
    static boolean isHuntable(@Nullable Player player, Mob mob) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == mob.level() && !SafeRooms.isSafe(player)
                && !HidingService.isHiddenFrom(player, mob);
    }

    /**
     * The nearest huntable player {@code mob} can see within {@code range} blocks (less for someone
     * crouching). The forest is dense and misty, so sight needs a clear line from its eyes.
     */
    @Nullable
    static Player nearestVisible(Mob mob, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : mob.level().players()) {
            if (!isHuntable(player, mob)) {
                continue;
            }
            double distance = mob.distanceToSqr(player);
            double reach = player.isCrouching() ? range * CROUCH_FACTOR : range;
            if (distance < bestDistance && distance < reach * reach && mob.getSensing().hasLineOfSight(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The nearest huntable player who is sprinting within {@code range} blocks (heard, not seen). */
    @Nullable
    static Player nearestSprinting(Mob mob, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : mob.level().players()) {
            if (!player.isSprinting() || !isHuntable(player, mob)) {
                continue;
            }
            double distance = mob.distanceToSqr(player);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** True inside Level 227 (the predators' home; elsewhere they come from a spawn egg). */
    static boolean inLevel227(Level level) {
        return BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_227;
    }

    /**
     * The wolves' ground: the higher sections of the tree. Outside Level 227 (a wolf from a spawn
     * egg, for testing) everywhere counts, so it still hunts.
     */
    static boolean isHighGround(Level level, double y) {
        return !inLevel227(level) || Level227Tree.isLevelHigh(y);
    }

    private L227Senses() {}
}
