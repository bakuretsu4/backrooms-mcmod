package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.registry.Level227FloraSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A giant Venus flytrap (canon: "hidden in the ground where the grass is taller. Step on the open
 * mouth and it closes, lifts into the air and digests you").
 *
 * <p>It lies almost flush with the forest floor, its two huge lobes spread open like a dish four
 * blocks across, its teeth a ring of spikes round the rim, hidden in the two-block tall grass. Step
 * into the mouth and:
 * <ol>
 *   <li>SNAP: the lobes slam shut round you in a quarter of a second. You are inside (riding it).</li>
 *   <li>LIFT: a thick stalk shoots up out of the rosette and lifts the closed trap five blocks.</li>
 *   <li>HOLD: it digests you, slowly: damage every second, a little more each second (about 20
 *       seconds from full health to death; canon says it takes days, but this is a game).</li>
 *   <li>RELEASE: if you struggle free (attack or jump presses; see {@link #struggle}) or die, the
 *       lobes pop open and you drop out.</li>
 *   <li>SINK: the stalk sinks back down, the lobes spread open again, and after a while it is
 *       ready for the next one.</li>
 * </ol>
 * Struggling always works: 12 presses free you at first, rising to 18 by the end. Someone else
 * hitting the trap also loosens its grip (each hit counts for 4 presses).
 *
 * <p>The phase is synced to clients, which time the animation themselves from when the phase
 * changed (client/VenusFlytrapModel draws it). The spawner is entity/Level227FloraSpawner.
 */
public class VenusFlytrap extends Mob {
    public enum Phase { IDLE, SNAP, LIFT, HOLD, RELEASE, SINK }

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VenusFlytrap.class, EntityDataSerializers.INT);

    // Timings, in ticks.
    public static final int SNAP_TICKS = 5;
    public static final int LIFT_TICKS = 40;
    public static final int RELEASE_TICKS = 12;
    public static final int SINK_TICKS = 70;
    /** After it sinks back, it lies open this long before it can snap again. */
    private static final int REARM_TICKS = 200;

    /** Height of the lobes' hinge above the ground: lying open, and lifted on the stalk. */
    public static final float HINGE_REST = 0.2f;
    public static final float HINGE_TOP = 5.4f;
    /** How far from the middle a foot in the mouth sets it off (the open lobes are about 4 blocks across). */
    private static final double TRIGGER_RADIUS = 1.5;

    // The struggle, counted in presses. 12 free you at first, rising to 18 as digestion goes on
    // (over DIGEST_TICKS). Presses only fade if you stop: after a second without one, a press'
    // worth drains away each second, so mashing at any speed gets you out.
    private static final int PRESSES_EARLY = 12;
    private static final int PRESSES_LATE = 18;
    private static final float FRIEND_HIT_PRESSES = 4.0f;
    private static final int GRIP_PAUSE = 20;
    private static final float GRIP_DRAIN = 1.0f / 20.0f;
    /** About this long in the trap from full health to death (digest damage ramps over it). */
    private static final int DIGEST_TICKS = 400;
    /** Entity event: the lobes jerk (someone is struggling inside). */
    private static final byte SHAKE_EVENT = 60;

    /** Ticks since the phase changed (counted on both sides; the client resets it when the phase arrives). */
    private int phaseTicks;
    private int holdTicks;
    /** Struggle presses counted so far (they drain away only if the captive stops pressing). */
    private float grip;
    private int lastPress;
    private int rearm;
    /** Client: ticks left of a struggle shake. */
    private int shakeTicks;

    public VenusFlytrap(EntityType<? extends VenusFlytrap> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, Phase.IDLE.ordinal());
    }

    public Phase phase() {
        return Phase.values()[entityData.get(PHASE)];
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int shakeTicks() {
        return shakeTicks;
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, phase.ordinal());
        phaseTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key)) {
            phaseTicks = 0;
        }
    }

    /**
     * The hitbox stays the low, wide box round the rosette (EntityType size) even when the trap is
     * lifted: a box that grew upward would be shoved aside by the canopy over it. The model reaches
     * well past it (the open lobes, the lifted head), so cull by a bigger box.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - 2.5, getY() - 0.5, getZ() - 2.5, getX() + 2.5, getY() + HINGE_TOP + 3.0, getZ() + 2.5);
    }

    // ------------------------------------------------------------------ the shape over time (both sides)

    /** Height of the hinge (the bottom of the closed trap) above the ground. */
    public float hingeHeight(float partialTick) {
        float t = phaseTicks + partialTick;
        return switch (phase()) {
            case IDLE, SNAP -> HINGE_REST;
            case LIFT -> HINGE_REST + (HINGE_TOP - HINGE_REST) * easeOutBack(Math.min(1.0f, t / LIFT_TICKS));
            case HOLD, RELEASE -> HINGE_TOP;
            case SINK -> HINGE_REST + (HINGE_TOP - HINGE_REST) * (1.0f - smooth(Math.min(1.0f, t / SINK_TICKS)));
        };
    }

    /** How open the lobes are: 1 = spread flat on the ground, 0 = shut. */
    public float openness(float partialTick) {
        float t = phaseTicks + partialTick;
        return switch (phase()) {
            // A slow, barely visible breathing while it waits.
            case IDLE -> 0.97f + 0.03f * Mth.sin((tickCount + partialTick) * 0.05f);
            // Shut in three ticks: the snap.
            case SNAP -> 1.0f - smooth(Math.min(1.0f, t / 3.0f));
            case LIFT -> 0.0f;
            // Chewing: small rhythmic squeezes of the lobes.
            case HOLD -> 0.03f * Math.max(0.0f, Mth.sin(t * 0.32f));
            case RELEASE -> 0.45f * smooth(Math.min(1.0f, t / 4.0f));
            case SINK -> 0.45f + 0.55f * smooth(Mth.clamp((t - SINK_TICKS * 0.6f) / (SINK_TICKS * 0.4f), 0.0f, 1.0f));
        };
    }

    private static float smooth(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    /** Overshoots a little and settles: the stalk springs up and sways back. */
    private static float easeOutBack(float x) {
        float c = 1.4f;
        float y = x - 1.0f;
        return 1.0f + (c + 1.0f) * y * y * y + c * y * y;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        phaseTicks++;
        super.tick();
        if (level().isClientSide) {
            if (shakeTicks > 0) {
                shakeTicks--;
            }
            return;
        }
        if (tickCount - lastPress > GRIP_PAUSE) {
            grip = Math.max(0.0f, grip - GRIP_DRAIN);
        }
        switch (phase()) {
            case IDLE -> {
                if (rearm > 0) {
                    rearm--;
                } else if (tickCount % 2 == 0) {
                    Player victim = findVictim();
                    if (victim != null) {
                        snap(victim);
                    }
                }
            }
            case SNAP -> {
                Player captive = holdCaptive();
                if (captive != null && phaseTicks == 3) {
                    // A tick after vanilla's "press shift to dismount" line, so this replaces it.
                    captive.displayClientMessage(Component.translatable("message.backrooms.flytrap.struggle"), true);
                }
                if (phaseTicks >= SNAP_TICKS) {
                    if (captive != null) {
                        setPhase(Phase.LIFT);
                        playSound(Level227FloraSounds.FLYTRAP_LIFT.get(), 1.4f, 0.9f + random.nextFloat() * 0.2f);
                    } else {
                        setPhase(Phase.RELEASE);
                    }
                }
            }
            case LIFT -> {
                if (holdCaptive() == null) {
                    setPhase(Phase.RELEASE);
                } else if (phaseTicks >= LIFT_TICKS) {
                    setPhase(Phase.HOLD);
                }
            }
            case HOLD -> {
                Player captive = holdCaptive();
                if (captive == null) {
                    setPhase(Phase.RELEASE);
                } else {
                    digest(captive);
                }
            }
            case RELEASE -> {
                if (phaseTicks >= RELEASE_TICKS) {
                    setPhase(Phase.SINK);
                }
            }
            case SINK -> {
                if (phaseTicks >= SINK_TICKS) {
                    setPhase(Phase.IDLE);
                    rearm = REARM_TICKS;
                }
            }
        }
    }

    /** A player standing in the open mouth (not hiding, not in creative or spectator). */
    private Player findVictim() {
        AABB mouth = new AABB(getX() - TRIGGER_RADIUS, getY() - 0.3, getZ() - TRIGGER_RADIUS,
                getX() + TRIGGER_RADIUS, getY() + 1.0, getZ() + TRIGGER_RADIUS);
        for (Player player : level().getEntitiesOfClass(Player.class, mouth)) {
            if (player.isSpectator() || player.isCreative() || player.isPassenger() || !player.isAlive()
                    || HidingService.isHidden(player)) {
                continue;
            }
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            if (dx * dx + dz * dz <= TRIGGER_RADIUS * TRIGGER_RADIUS) {
                return player;
            }
        }
        return null;
    }

    private void snap(Player victim) {
        setPhase(Phase.SNAP);
        holdTicks = 0;
        grip = 0.0f;
        playSound(Level227FloraSounds.FLYTRAP_SNAP.get(), 2.0f, 0.9f + random.nextFloat() * 0.15f);
        // In peaceful it still snaps (and gives you the fright) but lets you go.
        if (level().getDifficulty() != Difficulty.PEACEFUL) {
            victim.startRiding(this, true);
        }
    }

    /**
     * The player held inside, or null once there is none (it died, logged out or was teleported
     * away). Also cancels the vanilla way out: shift would make a rider climb off, so the shift key
     * is let go before the rider's own tick looks at it.
     */
    private Player holdCaptive() {
        if (!(getFirstPassenger() instanceof Player captive)) {
            return null;
        }
        if (!captive.isAlive()) {
            captive.stopRiding();
            return null;
        }
        captive.setShiftKeyDown(false);
        return captive;
    }

    private void digest(Player captive) {
        holdTicks++;
        if (holdTicks % 20 == 0) {
            // 0.6 health after the first second, 0.04 more each second after: about 20 seconds
            // from full health (20) to death, gentle at first.
            float damage = Math.min(3.0f, 0.6f + 0.04f * (holdTicks / 20));
            captive.hurt(new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(Level227Flora.FLYTRAP_DAMAGE), this), damage);
        }
        if (holdTicks % 36 == 1) {
            playSound(Level227FloraSounds.FLYTRAP_DIGEST.get(), 1.0f, 0.85f + random.nextFloat() * 0.3f);
        }
    }

    /** Presses needed to get out right now: 12, rising to 18 as digestion goes on. */
    private float pressesNeeded() {
        return PRESSES_EARLY + (PRESSES_LATE - PRESSES_EARLY) * Math.min(1.0f, holdTicks / (float) DIGEST_TICKS);
    }

    /**
     * One struggle press (attack or jump) from the player inside: sent by the client (see
     * network/FlytrapStrugglePayload), or an attack that reached the server anyway (see hurt).
     */
    public void struggle(Player player) {
        Phase phase = phase();
        if (player.getVehicle() != this || (phase != Phase.SNAP && phase != Phase.LIFT && phase != Phase.HOLD)) {
            return;
        }
        lastPress = tickCount;
        level().broadcastEntityEvent(this, SHAKE_EVENT);
        playSound(Level227FloraSounds.FLYTRAP_STRUGGLE.get(), 0.8f, 0.8f + random.nextFloat() * 0.4f);
        loosen(1.0f);
    }

    private void loosen(float presses) {
        grip += presses;
        if (grip >= pressesNeeded()) {
            letGo();
        }
    }

    /** The lobes pop open and the captive drops out (outwards, see getDismountLocationForPassenger). */
    private void letGo() {
        Entity captive = getFirstPassenger();
        setPhase(Phase.RELEASE);
        playSound(Level227FloraSounds.FLYTRAP_RELEASE.get(), 1.4f, 0.9f + random.nextFloat() * 0.2f);
        if (captive != null) {
            captive.stopRiding();
            Vec3 out = captive.position().subtract(position()).multiply(1, 0, 1);
            if (out.lengthSqr() > 1.0e-4) {
                out = out.normalize().scale(0.25);
            }
            captive.setDeltaMovement(out.x, 0.25, out.z);
            captive.hurtMarked = true;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == SHAKE_EVENT) {
            shakeTicks = 6;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ the captive

    /** The captive stands inside the closed trap, feet at the hinge (so the eyes are in the head). */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        callback.accept(passenger, getX(), getY() + hingeHeight(1.0f) + 0.05, getZ());
    }

    /** Standing, not sitting, inside the head. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** No mount hearts on the captive's screen (its food bar stays). */
    @Override
    public boolean showVehicleHealth() {
        return false;
    }

    /**
     * Where the captive drops out: beside the open head, the way they were facing (then they fall
     * the five blocks). If something is in the way, the other sides are tried; only if all are
     * blocked do they land on the ground next to the rosette (vanilla's choice).
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double y = getY() + hingeHeight(1.0f) + 0.2;
        float yaw = passenger.getYRot() * Mth.DEG_TO_RAD;
        for (int i = 0; i < 8; i++) {
            float a = yaw + i * Mth.PI / 4.0f;
            Vec3 spot = new Vec3(getX() - Mth.sin(a) * 1.9, y, getZ() + Mth.cos(a) * 1.9);
            if (level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(spot))) {
                return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ------------------------------------------------------------------ being hurt

    /**
     * The captive can't hurt it from inside: an attack of theirs that reaches the server counts as
     * a struggle instead. Anyone else's blow makes it flinch and loosens the grip a lot (4 presses'
     * worth): a friend can cut you out.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker.getVehicle() == this) {
            if (!level().isClientSide && attacker instanceof Player player) {
                struggle(player);
            }
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && getFirstPassenger() != null) {
            loosen(FRIEND_HIT_PRESSES);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && getFirstPassenger() != null) {
            letGo();
        }
        super.die(source);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    /** A plant: it never drowns if its hollow floods. */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    /** It is rooted: never despawned by distance at random. The spawner removes far ones itself. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227FloraSounds.FLYTRAP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227FloraSounds.FLYTRAP_DEATH.get();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Rearm", rearm);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        rearm = tag.getInt("Rearm");
    }
}
