package com.rickr.backrooms.entity;

import com.rickr.backrooms.camera.CameraData;
import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wretches (Entity 15 / Phenomenon 35): people in the last stage of Roth syndrome. Starved to
 * skin and bone, they have picked and torn patches of their own skin away, and they are in
 * constant pain and panic. They drink from the tunnel water and moan and sob to themselves.
 *
 * <p>They are driven by fear, not hunger:
 * <ul>
 *   <li>A light (your flashlight, or a torch or lantern in hand) within {@value #LIGHT_RANGE}
 *       blocks, a loud noise (sprinting, splashing), or someone walking up to them sends them
 *       running with a scream.</li>
 *   <li>Cornered (nowhere to run, or you stay right on top of them) they lash out wildly,
 *       sobbing, until they get a gap to run through again.</li>
 *   <li>Leave them alone (keep your light off, keep your distance, move quietly) and they leave
 *       you alone.</li>
 * </ul>
 *
 * <p>State machine on the server ({@link #customServerAiStep()}), synced so the model can pose:
 * <pre>
 * WANDER --water nearby--> DRINK --> WANDER
 * WANDER/DRINK --light, noise or approach--> FLEE --nowhere to go / you stay close--> CORNERED
 * CORNERED --you back off--> FLEE --calm for 5 s--> WANDER
 * </pre>
 */
public class Wretch extends Monster {
    public enum Mode { WANDER, DRINK, FLEE, CORNERED }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Wretch.class, EntityDataSerializers.BYTE);

    // --- What frightens it (blocks) -----------------------------------------------------------
    public static final double LIGHT_RANGE = 14.0;
    private static final double SPRINT_RANGE = 14.0;
    private static final double SPLASH_RANGE = 9.0;
    private static final double WALK_RANGE = 5.0;
    /** Someone walking toward it from this close counts as approaching. */
    private static final double APPROACH_RANGE = 7.0;
    /** Closer than this is always too close. */
    private static final double TOO_CLOSE = 3.5;
    private static final double SENSE_RANGE = 16.0;

    // --- Timings (ticks) ----------------------------------------------------------------------
    private static final int SENSE_INTERVAL = 5;
    private static final int CALM_DOWN_TICKS = 100;
    /** If you stay within {@link #CORNER_RANGE} this long while it flees, it is cornered. */
    private static final int CORNER_TICKS = 20;
    private static final double CORNER_RANGE = 2.5;
    /** It stops lashing out and runs again once you are this far away... */
    private static final double BREAK_FREE_RANGE = 5.0;
    /** ...or after this long at most. */
    private static final int CORNERED_MAX_TICKS = 120;
    private static final int ATTACK_COOLDOWN = 15;
    private static final int SCREAM_COOLDOWN = 120;
    private static final int LONELY_DESPAWN_TICKS = 90 * 20;
    private static final double LONELY_RANGE = 80.0;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.27) ------------------------------------------
    private static final double WANDER_SPEED = 0.5;
    /** Panic gives it a burst of speed: about as fast as your sprint, but only in short runs. */
    private static final double FLEE_SPEED = 1.45;
    private static final double LUNGE_SPEED = 1.2;

    private Mode mode = Mode.WANDER;
    private int modeTicks;
    @Nullable
    private Player threat;
    private int calmTicks;
    private int closeTicks;
    private int attackCooldown;
    private int screamCooldown;
    private int drinkFor;
    @Nullable
    private BlockPos drinkFrom;
    private int lonelyTicks;

    // Client-only visual: how much each mode's pose shows (0..1), eased so poses blend smoothly.
    private final float[] poseWeight = new float[MODES.length];
    private final float[] poseWeightO = new float[MODES.length];

    public Wretch(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.poseWeight[Mode.WANDER.ordinal()] = 1.0f;
        this.poseWeightO[Mode.WANDER.ordinal()] = 1.0f;
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, SENSE_RANGE)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.WANDER.ordinal());
    }

    @Override
    protected void registerGoals() {
        // Keeps its head above the tunnel water (it is shorter than the water is deep).
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.WANDER;
    }

    /** How far into the given mode's pose the body is this frame (0..1). Client side. */
    public float poseWeight(Mode which, float partialTick) {
        return Mth.lerp(partialTick, poseWeightO[which.ordinal()], poseWeight[which.ordinal()]);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int now = mode().ordinal();
            for (int i = 0; i < poseWeight.length; i++) {
                poseWeightO[i] = poseWeight[i];
                // Panic sets in fast; calming down takes longer.
                float rate = i == now && (i == Mode.FLEE.ordinal() || i == Mode.CORNERED.ordinal()) ? 0.35f : 0.12f;
                poseWeight[i] += ((i == now ? 1.0f : 0.0f) - poseWeight[i]) * rate;
            }
        }
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (screamCooldown > 0) {
            screamCooldown--;
        }
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case WANDER -> tickWander(senseNow);
            case DRINK -> tickDrink(senseNow);
            case FLEE -> tickFlee(senseNow);
            case CORNERED -> tickCornered();
        }
    }

    private void tickWander(boolean senseNow) {
        if (senseNow) {
            Player scare = findThreat();
            if (scare != null) {
                startFlee(scare);
                return;
            }
            if (tickLonely()) {
                return;
            }
        }
        if (drinkFrom != null) {
            if (position().distanceToSqr(Vec3.atCenterOf(drinkFrom)) < 2.6 * 2.6) {
                getNavigation().stop();
                drinkFor = 100 + random.nextInt(80);
                setMode(Mode.DRINK);
            } else if (getNavigation().isDone()) {
                drinkFrom = null; // couldn't get there
            }
            return;
        }
        if (getNavigation().isDone()) {
            if (random.nextInt(260) == 0) {
                drinkFrom = findWater();
                if (drinkFrom != null) {
                    getNavigation().moveTo(drinkFrom.getX() + 0.5, drinkFrom.getY() + 1.0, drinkFrom.getZ() + 0.5, WANDER_SPEED);
                    return;
                }
            }
            if (random.nextInt(140) == 0) {
                Vec3 to = DefaultRandomPos.getPos(this, 8, 2);
                if (to != null) {
                    getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED);
                }
            }
        }
    }

    /** Kneeling at the water's edge, lapping it up; anything scary breaks it off. */
    private void tickDrink(boolean senseNow) {
        if (senseNow) {
            Player scare = findThreat();
            if (scare != null) {
                drinkFrom = null;
                startFlee(scare);
                return;
            }
        }
        getNavigation().stop();
        if (drinkFrom != null) {
            getLookControl().setLookAt(drinkFrom.getX() + 0.5, drinkFrom.getY() + 0.9, drinkFrom.getZ() + 0.5);
            if (modeTicks % 30 == 10 && level() instanceof ServerLevel server) {
                playSound(Level39Mobs.WRETCH_DRINK.get(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
                server.sendParticles(ParticleTypes.SPLASH, drinkFrom.getX() + 0.5, drinkFrom.getY() + 1.0, drinkFrom.getZ() + 0.5,
                        5, 0.2, 0.05, 0.2, 0.0);
            }
        }
        if (modeTicks >= drinkFor) {
            drinkFrom = null;
            setMode(Mode.WANDER);
        }
    }

    private void startFlee(Player scare) {
        threat = scare;
        calmTicks = 0;
        closeTicks = 0;
        setMode(Mode.FLEE);
        if (screamCooldown <= 0) {
            screamCooldown = SCREAM_COOLDOWN;
            playSound(Level39Mobs.WRETCH_SCREAM.get(), 1.8f, 0.95f + random.nextFloat() * 0.15f);
        }
        runFrom(scare);
    }

    /** Runs: a fresh spot away from the threat every second. No way out means it is cornered. */
    private void tickFlee(boolean senseNow) {
        if (senseNow) {
            Player scare = findThreat();
            if (scare != null) {
                threat = scare;
                calmTicks = 0;
            } else {
                calmTicks += SENSE_INTERVAL;
                if (calmTicks >= CALM_DOWN_TICKS) {
                    threat = null;
                    setMode(Mode.WANDER);
                    return;
                }
            }
        }
        if (threat == null || !isThreat(threat)) {
            return;
        }
        closeTicks = distanceTo(threat) < CORNER_RANGE ? closeTicks + 1 : 0;
        if (closeTicks >= CORNER_TICKS) {
            corner();
            return;
        }
        if (modeTicks % 20 == 0 || getNavigation().isDone()) {
            if (!runFrom(threat) && distanceTo(threat) < BREAK_FREE_RANGE) {
                corner(); // nowhere to run
            }
        }
    }

    private boolean runFrom(Player scare) {
        Vec3 away = DefaultRandomPos.getPosAway(this, 14, 4, scare.position());
        if (away == null || away.distanceToSqr(scare.position()) < distanceToSqr(scare)) {
            return false;
        }
        return getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
    }

    private void corner() {
        setMode(Mode.CORNERED);
        getNavigation().stop();
        playSound(Level39Mobs.WRETCH_SOB.get(), 1.4f, 1.0f + random.nextFloat() * 0.2f);
    }

    /** Lashes out at whoever cornered it, sobbing, until they back off. */
    private void tickCornered() {
        Player scare = threat;
        if (scare == null || !isThreat(scare)) {
            threat = null;
            setMode(Mode.WANDER);
            return;
        }
        double distance = distanceTo(scare);
        getLookControl().setLookAt(scare, 40.0f, 40.0f);
        if (distance > BREAK_FREE_RANGE || modeTicks > CORNERED_MAX_TICKS) {
            startFlee(scare);
            return;
        }
        if (distance < 4.0) {
            getMoveControl().setWantedPosition(scare.getX(), scare.getY(), scare.getZ(), LUNGE_SPEED);
        }
        if (attackCooldown <= 0 && isWithinMeleeAttackRange(scare)) {
            attackCooldown = ATTACK_COOLDOWN + random.nextInt(8);
            swing(random.nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            doHurtTarget(scare);
        }
        if (modeTicks % 35 == 0) {
            playSound(random.nextInt(3) == 0 ? Level39Mobs.WRETCH_SCREAM.get() : Level39Mobs.WRETCH_SOB.get(),
                    1.3f, 1.0f + random.nextFloat() * 0.25f);
        }
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private boolean tickLonely() {
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), LONELY_RANGE, EntitySelector.NO_SPECTATORS);
        lonelyTicks = near == null ? lonelyTicks + SENSE_INTERVAL : 0;
        if (lonelyTicks >= LONELY_DESPAWN_TICKS) {
            discard();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ senses

    /** The nearest player who frightens it right now, or null. */
    @Nullable
    private Player findThreat() {
        Player best = null;
        double bestDistance = SENSE_RANGE * SENSE_RANGE;
        for (Player player : level().players()) {
            if (!isThreat(player)) {
                continue;
            }
            double distanceSqr = distanceToSqr(player);
            if (distanceSqr < bestDistance && frightens(player, Math.sqrt(distanceSqr))) {
                best = player;
                bestDistance = distanceSqr;
            }
        }
        return best;
    }

    /** Light, noise, walking toward it, or simply being too close. */
    private boolean frightens(Player player, double distance) {
        if (distance < TOO_CLOSE) {
            return true;
        }
        if (distance < LIGHT_RANGE && carriesLight(player) && getSensing().hasLineOfSight(player)) {
            return true;
        }
        double noise = Clump.noiseRange(player);
        if (noise >= Clump.HEAR_SPRINT ? distance < SPRINT_RANGE
                : noise >= Clump.HEAR_SPLASH ? distance < SPLASH_RANGE
                : noise > 0.0 && distance < WALK_RANGE) {
            return true;
        }
        if (distance < APPROACH_RANGE) {
            Vec3 move = player.getKnownMovement();
            Vec3 toMe = position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
            return move.x * toMe.x + move.z * toMe.z > 0.04;
        }
        return false;
    }

    /** The head-mounted flashlight (with battery left), or anything that glows in either hand. */
    public static boolean carriesLight(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            CameraData camera = CameraService.get(serverPlayer);
            if (camera.flashlight() && camera.battery() > 0.0f) {
                return true;
            }
        }
        return glows(player.getMainHandItem()) || glows(player.getOffhandItem());
    }

    private static boolean glows(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().getLightEmission() > 0;
    }

    /**
     * Someone that can frighten it. A player hiding in furniture ({@code player/HidingService})
     * is neither seen nor heard, so doesn't. (It never hunts anyone, so it never looks for hiders.)
     */
    private boolean isThreat(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player)
                && !HidingService.isHiddenFrom(player, this);
    }

    /** A water block next to (or just below) where it stands, within a few blocks, to drink from. */
    @Nullable
    private BlockPos findWater() {
        BlockPos feet = blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    pos.set(feet.getX() + dx, feet.getY() - 1, feet.getZ() + dz);
                    if (level().getFluidState(pos).is(FluidTags.WATER) && level().getBlockState(pos.above()).isAir()) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Hit from close by, it fights back; hit from further off, it runs. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && source.getEntity() instanceof Player attacker && isThreat(attacker)) {
            threat = attacker;
            if (distanceTo(attacker) < 4.0 && mode != Mode.CORNERED) {
                corner();
            } else if (mode != Mode.CORNERED) {
                startFlee(attacker);
            }
        }
        return hurt;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return switch (mode) {
            case FLEE -> null; // it already screamed
            case CORNERED -> Level39Mobs.WRETCH_SOB.get();
            case DRINK, WANDER -> random.nextInt(3) == 0 ? Level39Mobs.WRETCH_SOB.get() : Level39Mobs.WRETCH_MOAN.get();
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level39Mobs.WRETCH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level39Mobs.WRETCH_DEATH.get();
    }

    @Override
    protected SoundEvent getSwimSound() {
        return Level39Mobs.WRETCH_SPLASH.get();
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return Level39Mobs.WRETCH_SPLASH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Level39Mobs.WRETCH_STEP.get(), mode == Mode.FLEE ? 0.7f : 0.35f, 0.9f + random.nextFloat() * 0.25f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
