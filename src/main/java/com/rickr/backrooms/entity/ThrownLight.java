package com.rickr.backrooms.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shared base of the thrown flare and glowstick: a small object that flies in an arc, lands,
 * rolls a little and then lies still, counting down its {@link #life()} until it disappears.
 *
 * <p>Not a vanilla projectile on purpose: it never hits or hurts anything, it only needs to
 * fall, bump off walls and keep lying where it landed (in water too).
 *
 * <p>The physics run only on the server. Clients glide their copy smoothly to each position the
 * server sends (see {@link #lerpTo}), so everyone's copy, and with it the box you aim at to pick
 * a glowstick up, always ends up exactly where the server has it.
 *
 * <p>The remaining life is synced once a second (and on every phase change). In between, each
 * client counts down by itself, so the light can fade smoothly without a packet every tick.
 */
public abstract class ThrownLight extends Entity {
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ThrownLight.class, EntityDataSerializers.INT);
    /** Lying still on the floor (the renderer stops the tumble then). */
    private static final EntityDataAccessor<Boolean> LYING = SynchedEntityData.defineId(ThrownLight.class, EntityDataSerializers.BOOLEAN);
    /** How often (ticks) the server re-sends the remaining life, to correct the client's own count. */
    private static final int SYNC_EVERY = 20;
    /** Gravity in air and in water: a bit like a snowball in air, slow sinking in water. */
    private static final double GRAVITY = 0.03;
    private static final double WATER_GRAVITY = 0.006;
    /** Smaller differences than this between where it should have moved and where it did are just rounding. */
    private static final double BLOCKED = 1.0e-4;

    /** Ticks left until it disappears. */
    private int life;
    // Client only: the latest position from the server and how many ticks are left to glide there.
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private int lerpSteps;

    protected ThrownLight(EntityType<? extends ThrownLight> type, Level level) {
        super(type, level);
        this.life = maxLife();
    }

    /** Total life in ticks of a fresh one. */
    protected abstract int maxLife();

    /** Ticks left until it disappears (counted down on both sides). */
    public int life() {
        return life;
    }

    /**
     * Starts it at the thrower's hand, flying the way they look. Only the thrower's sideways
     * movement is added, not their jump, so a throw while jumping doesn't go up into the ceiling.
     */
    public void throwFrom(Player player, double speed, double lift) {
        Vec3 look = player.getLookAngle();
        moveTo(player.getX(), player.getEyeY() - 0.2, player.getZ(), player.getYRot(), 0.0f);
        Vec3 moving = player.getDeltaMovement();
        setDeltaMovement(look.scale(speed).add(moving.x, lift, moving.z));
    }

    /** Starts it with an exact amount of life left (a glowstick that was picked up and thrown again). */
    public void setLife(int ticks) {
        life = Mth.clamp(ticks, 1, maxLife());
        entityData.set(LIFE, life);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIFE, maxLife());
        builder.define(LYING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LIFE.equals(key) && level().isClientSide) {
            life = entityData.get(LIFE);
        }
    }

    @Override
    public void tick() {
        super.tick(); // water state, fire, etc.
        life--;
        if (level().isClientSide) {
            glideToServerPosition();
            return;
        }
        move();
        if (life <= 0) {
            discard();
            return;
        }
        if (life % SYNC_EVERY == 0) {
            entityData.set(LIFE, life);
        }
    }

    /**
     * Server only. Falls, bumps off walls, and once on the floor rolls a short way and stops.
     *
     * <p>We keep our own velocity instead of reading back what Minecraft's move() leaves behind:
     * flying along just under a ceiling, the stick scrapes the next ceiling block and Minecraft
     * reports that as running into a wall (and keeps cancelling the fall). So:
     * <ul>
     *   <li>ceiling: the upward speed is dropped and the forward speed damped a little, never reversed;</li>
     *   <li>floor: the downward speed is dropped;</li>
     *   <li>wall: only when a slightly thinner box really can't move that way does it bounce back.</li>
     * </ul>
     */
    private void move() {
        boolean water = isInWater();
        Vec3 v = getDeltaMovement().add(0.0, water ? -WATER_GRAVITY : -GRAVITY, 0.0);
        Vec3 from = position();
        move(MoverType.SELF, v);
        Vec3 moved = position().subtract(from);
        double vx = v.x;
        double vy = v.y;
        double vz = v.z;
        if (vy > 0.0 && moved.y < vy - BLOCKED) {
            vy = 0.0; // hit the ceiling: from here it falls with normal gravity
            vx *= 0.8;
            vz *= 0.8;
        } else if (vy < 0.0 && moved.y > vy + BLOCKED) {
            vy = 0.0; // on the floor
        }
        if (Math.abs(moved.x - v.x) > BLOCKED && againstWall(Math.signum(v.x), 0.0)) {
            vx = -vx * 0.3;
        }
        if (Math.abs(moved.z - v.z) > BLOCKED && againstWall(0.0, Math.signum(v.z))) {
            vz = -vz * 0.3;
        }
        if (water) {
            vx *= 0.8; // water drag: it drifts down slowly instead of flying on
            vy *= 0.8;
            vz *= 0.8;
        } else if (onGround()) {
            vx *= 0.6; // the short roll after landing
            vz *= 0.6;
        } else {
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
        }
        boolean still = onGround() && vx * vx + vz * vz < 1.0e-4;
        if (still) {
            vx = 0.0; // lying still: stop moving so it doesn't creep
            vz = 0.0;
        }
        entityData.set(LYING, still);
        setDeltaMovement(vx, vy, vz);
    }

    /** True when a block really stands in the way sideways (not just a ceiling or floor it touches). */
    private boolean againstWall(double dx, double dz) {
        return !level().noCollision(this, getBoundingBox().deflate(0.0, 0.02, 0.0).move(dx * 0.05, 0.0, dz * 0.05));
    }

    /** Client: remember the server's position and glide there over the next few ticks. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, steps);
        setRot(yRot, xRot);
    }

    private void glideToServerPosition() {
        if (lerpSteps > 0) {
            setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps);
            lerpSteps--;
        }
    }

    /** Lying still on the floor (synced from the server); the renderer tumbles it otherwise. */
    public boolean lying() {
        return entityData.get(LYING);
    }

    /** Where the light comes from: just above the stick (so it isn't inside the floor block). */
    public Vec3 lightPosition(float partialTick) {
        return getPosition(partialTick).add(0.0, 0.2, 0.0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Life")) {
            setLife(tag.getInt("Life"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0 * 64.0; // the glow is visible from further than a stick usually would be
    }
}
