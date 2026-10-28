package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Predators;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The male deer of Level 227: a stag far bigger than any real one, with very long horns, a bunch of
 * thin whip-like tails, and red eyes. It attacks on sight by running you through with its horns,
 * and it is fast.
 *
 * <p>What a player can learn about it:
 * <ul>
 *   <li><b>It warns you once.</b> When it sees you it throws its head up, then bellows and paws
 *       the ground with its horns lowered for about a second and a half. That is your time.</li>
 *   <li><b>The charge is a straight line.</b> Once it runs it can barely steer: step aside late
 *       and it thunders past. Then it has to slow down, turn and set up again.</li>
 *   <li><b>Stand in front of a tree.</b> If it misses you and hits a trunk, its horns stick in the
 *       wood for a few seconds while it thrashes to free them: that's the moment to run (or hit
 *       back).</li>
 *   <li><b>It never runs off a branch.</b> It brakes hard at an edge, so the edge is no trick.</li>
 * </ul>
 *
 * <p>State machine ({@link Mode}), run by the server in {@link #customServerAiStep()}:
 * <pre>
 * GRAZE --sees you--> ALERT --0.7 s--> THREAT --1.5 s--> CHARGE --hit you--> RECOVER --> THREAT (shorter)
 *                                                       CHARGE --hit a tree--> STUCK --2.5 s--> RECOVER
 *                                                       CHARGE --missed / edge--> RECOVER
 * THREAT/RECOVER --out of sight 5 s--> SEARCH --6 s--> GRAZE
 * </pre>
 * The mode is synced so the model can pose: grazing head-down, the head flung up, pawing with the
 * horns low, the flat-out charge, thrashing stuck in a tree.
 */
public class L227Stag extends Monster implements HidingService.Seeker {
    /** What the stag is doing right now. The order matters: it is synced as a byte. */
    public enum Mode { GRAZE, ALERT, THREAT, CHARGE, STUCK, RECOVER, SEARCH }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(L227Stag.class, EntityDataSerializers.BYTE);

    // --- Senses ------------------------------------------------------------------------------
    /** How far it spots you through the forest. Its head is high, so it sees over the undergrowth. */
    private static final double SIGHT = 34.0;
    private static final double HEAR_SPRINT = 14.0;
    private static final int SENSE_INTERVAL = 5;

    // --- Timings -----------------------------------------------------------------------------
    private static final int ALERT_TICKS = 14;
    /** The warning before the first charge, and before every charge after that. */
    private static final int THREAT_TICKS = 30;
    private static final int THREAT_TICKS_AGAIN = 20;
    private static final int CHARGE_MAX_TICKS = 45;
    /** It needs a run-up: it gets to full speed over this many ticks. */
    private static final int CHARGE_RAMP_TICKS = 8;
    private static final int STUCK_TICKS = 50;
    private static final int RECOVER_TICKS = 36;
    private static final int LOST_SIGHT_TICKS = 100;
    private static final int SEARCH_TICKS = 120;
    private static final double GIVE_UP_RANGE = 40.0;

    // --- The charge --------------------------------------------------------------------------
    /** Top speed in blocks per tick: 0.55 is 11 blocks a second, twice a sprinting wanderer. */
    private static final double CHARGE_SPEED = 0.55;
    private static final double CHARGE_START_SPEED = 0.18;
    /** How much it can steer while charging (radians per tick, about 2 degrees). */
    private static final float CHARGE_TURN = 0.035f;
    /** It runs on this far past where you were before it gives up the charge. */
    private static final double OVERSHOOT = 6.0;
    /** The horns reach this far in front of its body. */
    private static final double HORN_REACH = 1.3;
    private static final float IMPALE_DAMAGE = 10.0f;
    /** How hard a hit throws you (blocks per tick, sideways and up). */
    private static final double FLING = 1.2;
    private static final double FLING_UP = 0.55;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.3) ------------------------------------------
    private static final double GRAZE_SPEED = 0.45;
    private static final double RECOVER_SPEED = 1.1;
    private static final double SEARCH_SPEED = 0.8;

    // Server-side brain state.
    private Mode mode = Mode.GRAZE;
    private int modeTicks;
    private int modeLength;
    @Nullable
    private Player prey;
    private Vec3 lastSeen = Vec3.ZERO;
    private int unseenTicks;
    /** The charge's direction (flat, unit length) and the point it aimed at. */
    private Vec3 chargeDir = Vec3.ZERO;
    private Vec3 chargeAim = Vec3.ZERO;
    private boolean warned;

    // Client-only visuals (0..1), eased each tick.
    private float lower;
    private float lowerO;
    private float alert;
    private float alertO;

    public L227Stag(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.GRAZE.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Current mode; works on both sides (synced). */
    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.GRAZE;
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode current = mode();
            float lowerTarget = switch (current) {
                case THREAT, CHARGE, STUCK -> 1.0f;
                case RECOVER -> 0.3f;
                default -> 0.0f;
            };
            float alertTarget = current == Mode.ALERT || current == Mode.SEARCH ? 1.0f : 0.0f;
            lowerO = lower;
            alertO = alert;
            lower += (lowerTarget - lower) * (lowerTarget > lower ? 0.3f : 0.12f);
            alert += (alertTarget - alert) * 0.25f;
        }
    }

    /** How far the horns are lowered to charge (0..1) this frame. */
    public float lower(float partialTick) {
        return Mth.lerp(partialTick, lowerO, lower);
    }

    /** How far the head is flung up, listening (0..1) this frame. */
    public float alert(float partialTick) {
        return Mth.lerp(partialTick, alertO, alert);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (SafeRooms.isSafe(this) && mode != Mode.GRAZE) {
            loseInterest();
            return;
        }
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case GRAZE -> tickGraze(senseNow);
            case ALERT -> tickAlert();
            case THREAT -> tickThreat();
            case CHARGE -> tickCharge();
            case STUCK -> tickStuck();
            case RECOVER -> tickRecover();
            case SEARCH -> tickSearch(senseNow);
        }
    }

    private void tickGraze(boolean senseNow) {
        if (senseNow) {
            Player seen = L227Senses.nearestVisible(this, SIGHT);
            if (seen != null) {
                startAlert(seen);
                return;
            }
            Player heard = L227Senses.nearestSprinting(this, HEAR_SPRINT);
            if (heard != null) {
                // A crash in the undergrowth: it looks that way. If it can see you, you're found.
                getLookControl().setLookAt(heard, 30.0f, 30.0f);
            }
        }
        // Slow ambles between long spells of grazing with the head down.
        if (getNavigation().isDone() && random.nextInt(140) == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 10, 3);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, GRAZE_SPEED);
            }
        }
    }

    private void tickAlert() {
        if (!L227Senses.isHuntable(prey, this)) {
            lostOrHidden();
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(prey, 40.0f, 40.0f);
        if (modeTicks >= ALERT_TICKS) {
            startThreat(THREAT_TICKS);
        }
    }

    /** Rooted to the spot, turning to face you, horns low, pawing the wood. Then it goes. */
    private void tickThreat() {
        if (!L227Senses.isHuntable(prey, this)) {
            lostOrHidden();
            return;
        }
        getNavigation().stop();
        if (getSensing().hasLineOfSight(prey)) {
            lastSeen = prey.position();
            unseenTicks = 0;
        } else if (++unseenTicks > LOST_SIGHT_TICKS / 2) {
            startSearch();
            return;
        }
        if (distanceToSqr(prey) > GIVE_UP_RANGE * GIVE_UP_RANGE) {
            startSearch();
            return;
        }
        faceTowards(lastSeen, 12.0f);
        if (modeTicks % 10 == 5) {
            playSound(Level227Predators.STAG_PAW.get(), 1.0f, 0.85f + random.nextFloat() * 0.3f);
        }
        if (modeTicks >= modeLength && unseenTicks == 0) {
            startCharge();
        }
    }

    private void tickCharge() {
        // Ramp up to full speed, steer only a little towards the prey, and keep the body facing
        // the run so the model's horns point where it is going.
        double speed = Mth.lerp(Math.min(1.0, modeTicks / (double) CHARGE_RAMP_TICKS), CHARGE_START_SPEED, CHARGE_SPEED);
        if (prey != null && prey.isAlive()) {
            Vec3 want = prey.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            if (want.lengthSqr() > 1.0e-4) {
                float have = (float) Mth.atan2(chargeDir.z, chargeDir.x);
                float target = (float) Mth.atan2(want.z, want.x);
                float turn = Mth.clamp(Mth.wrapDegrees((target - have) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -CHARGE_TURN, CHARGE_TURN);
                chargeDir = new Vec3(Mth.cos(have + turn), 0.0, Mth.sin(have + turn));
            }
        }
        getNavigation().stop();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(chargeDir.x * speed, motion.y, chargeDir.z * speed);
        float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (modeTicks % 6 == 0) {
            playSound(Level227Predators.STAG_STEP.get(), 1.3f, 0.8f + random.nextFloat() * 0.15f);
        }

        // 1. The horns: anyone in front of it gets run through.
        AABB horns = getBoundingBox().move(chargeDir.x * HORN_REACH, 0.0, chargeDir.z * HORN_REACH).inflate(0.15, -0.25, 0.15);
        for (Player player : level().players()) {
            if (L227Senses.isHuntable(player, this) && horns.intersects(player.getBoundingBox())) {
                impale(player);
                startRecover();
                return;
            }
        }
        // 2. A wall or a tree trunk: the horns bite into the wood and stick.
        if (modeTicks > 3 && horizontalCollision) {
            playSound(Level227Predators.STAG_HORN_HIT.get(), 2.0f, 0.9f + random.nextFloat() * 0.15f);
            setMode(Mode.STUCK);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        // 3. The edge of the branch: it digs in and stops rather than going over.
        if (!groundAhead(2.5)) {
            setDeltaMovement(motion.x * 0.2, motion.y, motion.z * 0.2);
            startRecover();
            return;
        }
        // 4. Ran past where you were, or ran out of steam.
        double past = chargeAim.subtract(position()).dot(chargeDir);
        if (past < -OVERSHOOT || modeTicks >= CHARGE_MAX_TICKS) {
            startRecover();
        }
    }

    /** Horns stuck in a trunk: it thrashes its head, unable to move or attack. */
    private void tickStuck() {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (modeTicks % 12 == 0) {
            playSound(Level227Predators.STAG_SNORT.get(), 1.4f, 0.75f + random.nextFloat() * 0.2f);
        }
        if (modeTicks >= STUCK_TICKS) {
            playSound(Level227Predators.STAG_HORN_HIT.get(), 1.2f, 1.2f);
            startRecover();
        }
    }

    /** Slowing down and wheeling round at a distance, ready to set up the next charge. */
    private void tickRecover() {
        if (prey != null && prey.isDeadOrDying()) {
            loseInterest();
            return;
        }
        if (!L227Senses.isHuntable(prey, this)) {
            lostOrHidden();
            return;
        }
        if (modeTicks == 1) {
            // Carry on a little the way it ran, then turn: it needs room to charge again.
            Vec3 to = position().add(chargeDir.scale(6.0));
            getNavigation().moveTo(to.x, to.y, to.z, RECOVER_SPEED);
        }
        if (modeTicks > RECOVER_TICKS / 2) {
            getLookControl().setLookAt(prey, 20.0f, 20.0f);
        }
        if (modeTicks >= RECOVER_TICKS) {
            if (getSensing().hasLineOfSight(prey)) {
                startThreat(THREAT_TICKS_AGAIN);
            } else {
                lastSeen = prey.position();
                startSearch();
            }
        }
    }

    private void tickSearch(boolean senseNow) {
        if (senseNow) {
            Player seen = L227Senses.nearestVisible(this, SIGHT);
            if (seen != null) {
                prey = seen;
                setTarget(seen);
                lastSeen = seen.position();
                startThreat(THREAT_TICKS_AGAIN);
                return;
            }
        }
        if (getNavigation().isDone() && modeTicks % 30 == 0) {
            Vec3 to = DefaultRandomPos.getPosTowards(this, 8, 3, lastSeen, Mth.HALF_PI);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, SEARCH_SPEED);
            }
        }
        if (modeTicks >= SEARCH_TICKS) {
            loseInterest();
        }
    }

    // ------------------------------------------------------------------ transitions

    private void startAlert(Player player) {
        prey = player;
        setTarget(player);
        lastSeen = player.position();
        warned = false;
        setMode(Mode.ALERT);
        playSound(Level227Predators.STAG_SNORT.get(), 1.6f, 0.9f + random.nextFloat() * 0.15f);
    }

    private void startThreat(int ticks) {
        setMode(Mode.THREAT);
        modeLength = ticks;
        unseenTicks = 0;
        getNavigation().stop();
        if (!warned) {
            // The bellow: a deep, wrong roar that carries far. It comes once per hunt.
            warned = true;
            playSound(Level227Predators.STAG_BELLOW.get(), 4.0f, 0.9f + random.nextFloat() * 0.15f);
        } else {
            playSound(Level227Predators.STAG_SNORT.get(), 1.6f, 0.8f + random.nextFloat() * 0.15f);
        }
    }

    private void startCharge() {
        // Aim a little ahead of a moving target, as a real animal would.
        chargeAim = prey.position().add(prey.getDeltaMovement().multiply(6.0, 0.0, 6.0));
        Vec3 dir = chargeAim.subtract(position()).multiply(1.0, 0.0, 1.0);
        if (dir.lengthSqr() < 1.0e-4) {
            dir = Vec3.directionFromRotation(0.0f, getYRot());
        }
        chargeDir = dir.normalize();
        setMode(Mode.CHARGE);
        getNavigation().stop();
        playSound(Level227Predators.STAG_CHARGE.get(), 2.5f, 0.9f + random.nextFloat() * 0.15f);
    }

    private void startRecover() {
        setMode(Mode.RECOVER);
    }

    private void startSearch() {
        setMode(Mode.SEARCH);
        getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, SEARCH_SPEED);
    }

    private void loseInterest() {
        prey = null;
        setTarget(null);
        getNavigation().stop();
        setMode(Mode.GRAZE);
    }

    private void lostOrHidden() {
        if (HidingService.isHidden(prey)) {
            startSearch();
        } else {
            loseInterest();
        }
    }

    /** Pulled someone out of hiding: no warning this time, straight into the charge. */
    @Override
    public void onPulledOut(ServerPlayer player) {
        prey = player;
        setTarget(player);
        lastSeen = player.position();
        warned = true;
        startThreat(8);
    }

    @Override
    public boolean isSeeking() {
        return mode != Mode.GRAZE;
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    /**
     * Run through: heavy damage (our own damage type, so the death message says what happened),
     * thrown forward and up off the horns, and slowed for a moment by the wound.
     */
    private void impale(Player player) {
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(Level227Predators.STAG_DAMAGE), this);
        if (player.hurt(source, IMPALE_DAMAGE)) {
            player.setDeltaMovement(chargeDir.x * FLING, FLING_UP, chargeDir.z * FLING);
            player.hurtMarked = true; // sends the throw to the player's client
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
            playSound(Level227Predators.STAG_IMPALE.get(), 2.0f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    /** Turns the body towards a point by at most {@code maxDegrees} per tick. */
    private void faceTowards(Vec3 at, float maxDegrees) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0e-4) {
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float yaw = Mth.approachDegrees(getYRot(), want, maxDegrees);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = Mth.approachDegrees(yHeadRot, want, maxDegrees * 2.0f);
    }

    /** True if there is wood (anything solid) within 4 blocks below the spot {@code distance} ahead. */
    private boolean groundAhead(double distance) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(getX() + chargeDir.x * distance);
        int z = Mth.floor(getZ() + chargeDir.z * distance);
        int y = Mth.floor(getY());
        for (int dy = 1; dy >= -4; dy--) {
            pos.set(x, y + dy, z);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Hurting it only makes it come sooner. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && source.getEntity() instanceof Player player
                && L227Senses.isHuntable(player, this) && (mode == Mode.GRAZE || mode == Mode.SEARCH || mode == Mode.ALERT)) {
            prey = player;
            setTarget(player);
            lastSeen = player.position();
            warned = true;
            startThreat(THREAT_TICKS_AGAIN);
        }
        return hurt;
    }

    /** The charge pushes through other mobs; nothing shoves a charging stag aside. */
    @Override
    public boolean isPushable() {
        return mode != Mode.CHARGE && super.isPushable();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return mode == Mode.GRAZE;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 320;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return mode == Mode.GRAZE ? Level227Predators.STAG_SNORT.get() : null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227Predators.STAG_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227Predators.STAG_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Level227Predators.STAG_STEP.get(), mode == Mode.RECOVER ? 0.6f : 0.35f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
