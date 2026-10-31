package com.rickr.backrooms.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Penitent (Level 490): a silent hooded figure in a long dark robe (after the play-test: "add
 * NPCs"). Placed by world/Level490Figures in one of four poses: kneeling in chains, praying at a
 * shrine or an altar, walking slowly toward the spire along the bridges, or standing still,
 * watching you go by. They never speak, never fight, can't be hurt. Some of them are not there
 * when you come close: they are gone between one step and the next.
 */
public class Penitent extends PathfinderMob {
    public static final int KNEEL = 0;
    public static final int PRAY = 1;
    public static final int WALK = 2;
    public static final int STAND = 3;
    /** Closer than this, one that vanishes is gone. */
    private static final double VANISH_DISTANCE = 6.0;
    /** With nobody this near it is taken away (the spawner puts figures back as you come). */
    private static final double KEEP_DISTANCE = 110.0;

    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Penitent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> VANISHING = SynchedEntityData.defineId(Penitent.class, EntityDataSerializers.BOOLEAN);

    private boolean vanishes;
    private double goalX;
    private double goalZ;
    private int vanishTicks = -1;
    private Runnable onVanish = () -> { };

    public Penitent(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setSilent(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.16);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POSE, STAND);
        builder.define(VANISHING, false);
    }

    /** Sets it up: its pose, whether it vanishes as you near it, and where a walker walks to. */
    public void setup(int pose, boolean vanishes, double goalX, double goalZ) {
        entityData.set(POSE, pose);
        this.vanishes = vanishes;
        this.goalX = goalX;
        this.goalZ = goalZ;
        setNoGravity(pose != WALK);
        noPhysics = pose != WALK;
    }

    /** Runs when it vanishes (the spawner leaves its spot empty for a while). */
    public void onVanish(Runnable action) {
        onVanish = action;
    }

    public int pose() {
        return entityData.get(POSE);
    }

    public boolean isVanishing() {
        return entityData.get(VANISHING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (vanishTicks >= 0) {
            if (++vanishTicks > 2) {
                discard();
            }
            return;
        }
        Player near = level.getNearestPlayer(this, KEEP_DISTANCE);
        if (near == null) {
            discard();
            return;
        }
        if (vanishes && distanceTo(near) < VANISH_DISTANCE) {
            entityData.set(VANISHING, true); // the client stops drawing it at once
            vanishTicks = 0;
            onVanish.run();
            return;
        }
        int pose = pose();
        if (pose == WALK) {
            // Toward the spire, slowly; a new path now and then (the bridges are long).
            if (tickCount % 80 == 0 || getNavigation().isDone()) {
                double dx = goalX - getX();
                double dz = goalZ - getZ();
                double d = Math.hypot(dx, dz);
                if (d < 30) {
                    discard(); // it reached the spire's island
                    return;
                }
                double step = Math.min(24, d);
                getNavigation().moveTo(getX() + dx / d * step, getY(), getZ() + dz / d * step, 0.7);
            }
        } else if (pose == STAND && distanceTo(near) < 16) {
            // The standing ones turn their heads to follow you.
            getLookControl().setLookAt(near, 10, 10);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // it takes itself away (see tick)
    }
}
