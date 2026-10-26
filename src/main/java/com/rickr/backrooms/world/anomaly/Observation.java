package com.rickr.backrooms.world.anomaly;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Is anybody looking?" Many anomalies only change when nobody sees them (rooms that rearrange,
 * crates that appear behind you, a door that moves). Server side; checks every player of the level.
 */
public final class Observation {
    /** Half of a generous field of view: a little wider than the screen, so changes happen safely out of sight. */
    public static final double DEFAULT_HALF_FOV_DEG = 70.0;

    /**
     * True if some player within {@code maxDistance} could see {@code point}: it is inside their view
     * cone and no solid block is in the way.
     */
    public static boolean seenByAnyone(ServerLevel level, Vec3 point, double maxDistance) {
        return seenByAnyone(level, point, maxDistance, DEFAULT_HALF_FOV_DEG);
    }

    public static boolean seenByAnyone(ServerLevel level, Vec3 point, double maxDistance, double halfFovDeg) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && sees(player, point, maxDistance, halfFovDeg)) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code point} is in front of the player (inside the view cone) and in line of sight. */
    public static boolean sees(ServerPlayer player, Vec3 point, double maxDistance, double halfFovDeg) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = point.subtract(eye);
        double dist = to.length();
        if (dist > maxDistance) {
            return false;
        }
        if (dist < 1.0e-3) {
            return true;
        }
        double cos = player.getLookAngle().dot(to.scale(1.0 / dist));
        if (cos < Math.cos(Math.toRadians(halfFovDeg))) {
            return false;
        }
        return lineOfSight(player.serverLevel(), eye, point);
    }

    /** True if no solid block lies between {@code from} and {@code to}. */
    public static boolean lineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 1.0;
    }

    private Observation() {}
}
