package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Predators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One of Level 227's squirrels: small, fast, with fur that glows a sickly fluorescent green. Alone
 * it is nothing. They are never alone: they come as a swarm ({@link SquirrelSwarm}), circle a
 * wanderer, and then eat them alive.
 *
 * <p>A swarm can be dozens strong, so each squirrel's brain is deliberately tiny: no path finding,
 * no goals, no line-of-sight checks and no pushing. The swarm works out the plan once per tick (the
 * ring, its centre, who to go for) and each squirrel only steers straight at its own spot in it,
 * hopping small obstacles and scrambling a couple of blocks up roots and logs.
 *
 * <p>States (synced, so the model can pose):
 * <ul>
 *   <li>{@code RUN}: scampering in the bounding squirrel gallop;</li>
 *   <li>{@code SIT}: stopped dead, sitting up on its haunches, staring at you (they do this in the
 *       ring, a few at a time: the worst moment of the whole thing);</li>
 *   <li>{@code LEAP}: flying at you, claws out;</li>
 *   <li>{@code CLING}: hanging on your body and biting. It rides along with you: the server keeps it
 *       at its spot on your body ({@link #clingOffset}) and the renderer draws it exactly there, so
 *       it never lags behind.</li>
 * </ul>
 * Any hit knocks a clinging squirrel off; three hits kill one. There are always more.
 */
public class L227Squirrel extends Monster implements HidingService.Seeker {
    public static final byte RUN = 0;
    public static final byte SIT = 1;
    public static final byte LEAP = 2;
    public static final byte CLING = 3;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(L227Squirrel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> CLING_TO = SynchedEntityData.defineId(L227Squirrel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> CLING_SLOT = SynchedEntityData.defineId(L227Squirrel.class, EntityDataSerializers.BYTE);

    /**
     * Where clinging squirrels hold on, around a player's body: the angle round from the front
     * (degrees, towards the player's left) and the height above the feet. Back and shoulders
     * first, the way an animal climbs.
     */
    private static final float[][] CLING_SPOTS = {
            {180.0f, 1.30f}, {0.0f, 1.15f}, {90.0f, 1.00f}, {270.0f, 1.05f}, {140.0f, 0.70f},
            {220.0f, 0.75f}, {40.0f, 0.55f}, {320.0f, 0.60f}, {165.0f, 0.95f}, {15.0f, 1.45f},
    };
    public static final int CLING_SPOTS_COUNT = CLING_SPOTS.length;
    /** How far from the player's middle a clinging squirrel's origin sits (their half-width plus a squirrel's belly). */
    private static final double CLING_RADIUS = 0.42;

    /** Leaps start this close to the target and need this long between them. */
    private static final double LEAP_RANGE = 2.6;
    private static final int LEAP_COOLDOWN_TICKS = 24;
    private static final int LEAP_MAX_TICKS = 16;
    /** It scrambles up roots and logs, but never more than this far above where it is going. */
    private static final double CLIMB_ABOVE_GOAL = 2.5;
    /** Stuck against something too tall to climb this long: run round it instead. */
    private static final int STUCK_DETOUR_TICKS = 8;
    private static final int DETOUR_TICKS = 14;
    /** A squirrel with no swarm and nobody near vanishes after this long (10 s). */
    private static final int STRAY_TICKS = 200;

    /** The swarm this squirrel belongs to (0 = none yet). Server only. */
    int swarmId;
    /** Its own spot in the ring: an angle offset, a little extra radius, and which way it runs. */
    float ringOffset;
    float ringJitter;
    private int stateTicks;
    private int sitFor;
    private int leapCooldown;
    private int stuckTicks;
    private int detourTicks;
    private int detourSide;
    private boolean climbing;
    private double goalY;
    private int strayTicks;
    @Nullable
    private Player clingTarget;

    public L227Squirrel(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1;
        this.ringOffset = random.nextFloat() * Mth.TWO_PI;
        this.ringJitter = (random.nextFloat() - 0.5f) * 2.4f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, RUN);
        builder.define(CLING_TO, -1);
        builder.define(CLING_SLOT, (byte) 0);
    }

    /** No goals at all: the swarm thinks for it (see the class comment). */
    @Override
    protected void registerGoals() {
    }

    public byte state() {
        return entityData.get(STATE);
    }

    private void setState(byte state) {
        if (entityData.get(STATE) != state) {
            entityData.set(STATE, state);
        }
        stateTicks = 0;
    }

    /** The player this squirrel hangs on, if any (works on both sides). */
    @Nullable
    public Player clingTarget() {
        if (state() != CLING) {
            return null;
        }
        return level().getEntity(entityData.get(CLING_TO)) instanceof Player player ? player : null;
    }

    public int clingSlot() {
        return entityData.get(CLING_SLOT);
    }

    /**
     * Where a squirrel in cling spot {@code slot} sits relative to the player's feet, for a player
     * whose body faces {@code bodyYaw} degrees. Shared by the server (which keeps it there) and the
     * renderer (which draws it there), so both agree exactly.
     */
    public static Vec3 clingOffset(int slot, float bodyYaw) {
        float[] spot = CLING_SPOTS[Mth.clamp(slot, 0, CLING_SPOTS.length - 1)];
        float yaw = (bodyYaw + spot[0]) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * CLING_RADIUS, spot[1], Mth.cos(yaw) * CLING_RADIUS);
    }

    /** The way a squirrel in cling spot {@code slot} faces: into the player's body. */
    public static float clingYaw(int slot, float bodyYaw) {
        return bodyYaw + CLING_SPOTS[Mth.clamp(slot, 0, CLING_SPOTS.length - 1)][0] + 180.0f;
    }

    // ------------------------------------------------------------------ the tiny brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        stateTicks++;
        if (leapCooldown > 0) {
            leapCooldown--;
        }
        if (state() == CLING) {
            tickCling();
            return;
        }
        SquirrelSwarm swarm = SquirrelSwarm.get(swarmId);
        if (swarm == null) {
            tickStray();
            return;
        }
        strayTicks = 0;
        if (state() == LEAP) {
            tickLeap(swarm);
            return;
        }
        Vec3 goal = swarm.goalFor(this);
        Player target = swarm.target();
        if (state() == SIT) {
            // Sitting up on its haunches, perfectly still, staring.
            getNavigation().stop();
            if (target != null) {
                getLookControl().setLookAt(target, 60.0f, 60.0f);
            }
            if (stateTicks >= sitFor || swarm.phase() == SquirrelSwarm.Phase.SWARM) {
                setState(RUN);
            }
            return;
        }
        if (swarm.mayPauseToStare() && onGround() && random.nextInt(140) == 0) {
            sitFor = 14 + random.nextInt(26);
            setState(SIT);
            return;
        }
        if (swarm.phase() == SquirrelSwarm.Phase.SWARM && target != null && onGround() && leapCooldown <= 0
                && distanceToSqr(target) < LEAP_RANGE * LEAP_RANGE) {
            leapAt(target);
            return;
        }
        steerTo(goal, swarm.speedFor(this));
        if (target != null && (tickCount + getId()) % 4 == 0) {
            getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
    }

    /**
     * Straight at the goal, no path. Small steps are hopped (step height and the move control's
     * own jump); taller roots and logs are scrambled up; anything taller than that (a tree trunk)
     * is run around.
     */
    private void steerTo(Vec3 goal, double speed) {
        goalY = goal.y;
        if (horizontalCollision && !canClimbHere()) {
            if (++stuckTicks > STUCK_DETOUR_TICKS) {
                stuckTicks = 0;
                detourTicks = DETOUR_TICKS;
                detourSide = random.nextBoolean() ? 1 : -1;
            }
        } else {
            stuckTicks = 0;
        }
        Vec3 to = goal;
        if (detourTicks > 0) {
            detourTicks--;
            // Sideways round the obstacle, still edging towards the goal.
            Vec3 toward = goal.subtract(position()).multiply(1.0, 0.0, 1.0);
            if (toward.lengthSqr() > 1.0e-4) {
                toward = toward.normalize();
                Vec3 side = new Vec3(-toward.z * detourSide, 0.0, toward.x * detourSide);
                to = position().add(side.scale(3.0)).add(toward);
            }
        }
        getMoveControl().setWantedPosition(to.x, to.y, to.z, speed);
        climbing = horizontalCollision && canClimbHere();
    }

    private boolean canClimbHere() {
        return getY() < goalY + CLIMB_ABOVE_GOAL;
    }

    private void leapAt(Player target) {
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0, to.z);
        if (flat.lengthSqr() < 1.0e-4) {
            return;
        }
        flat = flat.normalize();
        setDeltaMovement(flat.x * 0.5, 0.42, flat.z * 0.5);
        hasImpulse = true;
        float yaw = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(yaw);
        yBodyRot = yaw;
        setState(LEAP);
        leapCooldown = LEAP_COOLDOWN_TICKS;
        if (random.nextInt(3) == 0) {
            playSound(Level227Predators.SQUIRREL_SCREECH.get(), 0.6f, 1.3f + random.nextFloat() * 0.4f);
        }
    }

    /** In the air: grab on if it reaches the target and there is room on them, else drop. */
    private void tickLeap(SquirrelSwarm swarm) {
        Player target = swarm.target();
        if (target != null && getBoundingBox().inflate(0.35).intersects(target.getBoundingBox())) {
            int slot = swarm.claimClingSpot(this);
            if (slot >= 0) {
                startCling(target, slot);
                return;
            }
        }
        if ((stateTicks > 3 && onGround()) || stateTicks >= LEAP_MAX_TICKS) {
            setState(RUN);
        }
    }

    private void startCling(Player target, int slot) {
        clingTarget = target;
        entityData.set(CLING_TO, target.getId());
        entityData.set(CLING_SLOT, (byte) slot);
        setState(CLING);
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        playSound(Level227Predators.SQUIRREL_BITE.get(), 0.8f, 1.0f + random.nextFloat() * 0.3f);
    }

    /** Riding along on the player's body. The swarm does the biting damage (one sum for all). */
    private void tickCling() {
        Player target = clingTarget;
        SquirrelSwarm swarm = SquirrelSwarm.get(swarmId);
        if (target == null || !target.isAlive() || target.level() != level() || swarm == null
                || HidingService.isHidden(target) || target.isSpectator() || target.isCreative()) {
            letGo(false);
            return;
        }
        Vec3 at = target.position().add(clingOffset(clingSlot(), target.yBodyRot));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        float yaw = clingYaw(clingSlot(), target.yBodyRot);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (stateTicks % 9 == (getId() & 7)) {
            playSound(Level227Predators.SQUIRREL_BITE.get(), 0.5f, 0.9f + random.nextFloat() * 0.5f);
        }
    }

    /** Lets go of the player: knocked off ({@code flung}) or because they are gone. */
    void letGo(boolean flung) {
        SquirrelSwarm swarm = SquirrelSwarm.get(swarmId);
        if (swarm != null) {
            swarm.releaseClingSpot(this);
        }
        Player from = clingTarget;
        clingTarget = null;
        entityData.set(CLING_TO, -1);
        noPhysics = false;
        setNoGravity(false);
        setState(RUN);
        leapCooldown = LEAP_COOLDOWN_TICKS;
        if (from != null && from.level() == level()) {
            // Drop to where the player stands: clinging ignored walls, and the spot on their back
            // may be inside a block they are leaning on. Their feet never are.
            Vec3 away = position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
            setPos(from.getX(), from.getY(), from.getZ());
            if (!flung) {
                return;
            }
            if (away.lengthSqr() < 1.0e-4) {
                away = new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
            }
            away = away.normalize();
            setDeltaMovement(away.x * 0.6, 0.3, away.z * 0.6);
            hasImpulse = true;
        }
    }

    /**
     * No swarm: one from a spawn egg, or a straggler. It joins a swarm nearby, or starts a new one
     * on the nearest wanderer it can find; with nobody around it soon slips away.
     */
    private void tickStray() {
        if ((tickCount + getId()) % 20 == 0 && level() instanceof ServerLevel server) {
            SquirrelSwarm near = SquirrelSwarm.nearest(server, position(), 32.0);
            if (near != null && near.adopt(this)) {
                return;
            }
            Player player = level().getNearestPlayer(this, 24.0);
            if (player != null && L227Senses.isHuntable(player, this)) {
                SquirrelSwarm swarm = SquirrelSwarm.start(server, player, 0, false);
                if (swarm != null) {
                    swarm.adopt(this);
                    return;
                }
            }
        }
        getNavigation().stop();
        if (++strayTicks > STRAY_TICKS && level().getNearestPlayer(this, 16.0) == null) {
            discard();
        }
    }

    /** Pulled a hider out of furniture (heard them breathing): the swarm goes for them. */
    @Override
    public void onPulledOut(ServerPlayer player) {
        SquirrelSwarm swarm = SquirrelSwarm.get(swarmId);
        if (swarm != null) {
            swarm.resume(player);
        }
    }

    @Override
    public boolean isSeeking() {
        SquirrelSwarm swarm = SquirrelSwarm.get(swarmId);
        return swarm != null && swarm.isHunting();
    }

    /** They find you by smell and numbers, not by watching you get in: nobody "saw" you hide. */
    @Override
    public boolean seesHiding() {
        return false;
    }

    // ------------------------------------------------------------------ cheap physics

    /** Scrambling up roots and logs a little, like a spider but never up a whole trunk. */
    @Override
    public boolean onClimbable() {
        return climbing || super.onClimbable();
    }

    /** Dozens of them in a heap: nobody pushes anybody (and nobody pays for the collision checks). */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    /** Squirrels drop out of trees all day: no fall damage. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** Any hit knocks a clinging squirrel off you. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && state() == CLING) {
            letGo(true);
        }
        return hurt;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && state() == CLING) {
            SquirrelSwarm swarm = SquirrelSwarm.get(swarmId);
            if (swarm != null) {
                swarm.releaseClingSpot(this);
            }
        }
        super.remove(reason);
    }

    /** The swarm decides when they go (see SquirrelSwarm); vanilla only clears far-off strays. */
    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return swarmId == 0 || SquirrelSwarm.get(swarmId) == null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return Level227Predators.SQUIRREL_CHITTER.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.55f;
    }

    @Override
    public float getVoicePitch() {
        return 0.9f + random.nextFloat() * 0.35f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227Predators.SQUIRREL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227Predators.SQUIRREL_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Level227Predators.SQUIRREL_STEP.get(), 0.18f, 0.9f + random.nextFloat() * 0.3f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
