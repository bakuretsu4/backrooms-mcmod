package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Grazers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The bears of Level 227: fifteen eyes, six legs, asleep in the hollows of the branches.
 *
 * <p>The wiki says they hibernate in caves and are not aggressive unless provoked. Here that is a
 * rule a player can learn and play with:
 * <ul>
 *   <li><b>Let sleeping bears lie.</b> A sleeping bear slowly notices noise nearby: someone
 *       walking close, sprinting or jumping a little further off, anyone right next to it. Crouch
 *       past and don't linger, and it sleeps on.</li>
 *   <li><b>The eyes are the warning.</b> When it is close to waking it stirs: a low groan, and a
 *       few of its fifteen eyes open and look around. Back off and they close again.</li>
 *   <li><b>Provoked is deadly.</b> Wake it fully, or hit it at all, and it heaves itself up with
 *       every eye open, roars and comes for you on six legs, a little slower than a sprint. It
 *       rears up on its back legs and brings four paws down: about 8 hearts on Normal.</li>
 *   <li><b>It goes home.</b> Lose it (out of sight long enough, or far enough) and it lumbers
 *       back to its hollow and lies down again.</li>
 * </ul>
 *
 * <p>The brain is a small state machine ({@link Mode}) run by the server in
 * {@link #customServerAiStep()}, like the Hound's:
 * <pre>
 * SLEEP --noise--> STIR --more noise--> WAKE --1.5 s--> HUNT --close--> MAUL --> HUNT
 * STIR --quiet--> SLEEP        any --hurt--> WAKE (or straight to HUNT if up)
 * HUNT --lost you--> RETURN --home--> SETTLE --2 s--> SLEEP
 * </pre>
 * The mode is synced so the model can pose (curled asleep, the eyes opening, rising, rearing).
 */
public class L227Bear extends PathfinderMob {
    /** What the bear is doing. The order matters: it is synced as a byte. */
    public enum Mode { SLEEP, STIR, WAKE, HUNT, MAUL, RETURN, SETTLE }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(L227Bear.class, EntityDataSerializers.BYTE);

    // --- Waking up -------------------------------------------------------------------------
    /** How often (ticks) it listens for noise while asleep. */
    private static final int LISTEN_INTERVAL = 5;
    /** Right next to it: anyone this close disturbs it fast, however quietly they move. */
    private static final double TOUCH_RANGE = 2.8;
    /** Close: walking here disturbs it, crouching only a little. */
    private static final double NEAR_RANGE = 5.5;
    /** Further off only sprinting and jumping carry. */
    private static final double NOISE_RANGE = 9.0;
    /** Noise points per listen: right next to it, walking near, crouching near, loud further off. */
    private static final int TOUCH_NOISE = 12;
    private static final int WALK_NOISE = 5;
    private static final int CROUCH_NOISE = 2;
    private static final int LOUD_NOISE = 6;
    /** Quiet listens let it settle back down. */
    private static final int QUIET_DECAY = 3;
    /** At this much noise it stirs (the first eyes open)... */
    private static final int STIR_AT = 30;
    /** ...and at this much it wakes. Walking at 4 blocks: about 4.5 s; crouching there: about 11 s. */
    private static final int WAKE_AT = 90;

    // --- Timings -------------------------------------------------------------------------------
    private static final int WAKE_TICKS = 30;
    /** Hit while lying down: it is up much faster. */
    private static final int WAKE_TICKS_HURT = 16;
    private static final int ROAR_AT = 8;
    private static final int MAUL_TICKS = 22;
    /** The paws come down on this tick of the maul: the moment it hurts. */
    private static final int MAUL_HIT_AT = 11;
    private static final int MAUL_COOLDOWN = 12;
    private static final int SETTLE_TICKS = 40;
    private static final int REPATH_TICKS = 10;
    /** It gives up after this long out of sight, this far away, or this long in total. */
    private static final int LOST_SIGHT_TICKS = 200;
    private static final double GIVE_UP_DISTANCE = 40.0;
    private static final int MAX_HUNT_TICKS = 1200;
    /** Unable to get home for this long: it lies down where it is. */
    private static final int MAX_RETURN_TICKS = 600;

    // --- The attack ---------------------------------------------------------------------------
    /** It rears up once you are this close (from its middle; the body is long). */
    private static final double MAUL_START_RANGE = 3.2;
    /** The slam hits everything this far in front of it... */
    private static final double MAUL_REACH = 3.8;
    /** ...within this cone (cosine; about 60 degrees either side). */
    private static final double MAUL_CONE = 0.5;
    /** A mauled player is knocked down and slowed for a moment: hard to run after the first hit. */
    private static final int MAUL_SLOW_TICKS = 40;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.3) ---------------------------------------------
    // 1.35 is about 5.2 blocks/s: faster than walking, a little slower than a fresh sprint, so you
    // escape only if you run the moment it roars.
    private static final double HUNT_SPEED = 1.35;
    private static final double RETURN_SPEED = 0.7;

    // Server-side brain state. Not saved: bears are never saved (the spawner re-fills the caves).
    private Mode mode = Mode.SLEEP;
    private int modeTicks;
    private int modeLength;
    private int noise;
    @Nullable
    private Player prey;
    private int unseenTicks;
    private int huntTicks;
    private int repathIn;
    private int maulCooldown;
    @Nullable
    private BlockPos home;
    private float homeYaw;

    // Client-only, purely visual (eased each tick so the model never snaps): how far it has risen
    // off the floor and how many of its eyes are open (both 0..1), and how long the current mode
    // has run on this client (the maul and the eye ripple are timed from it).
    private float rise;
    private float riseO;
    private float eyes;
    private float eyesO;
    private Mode clientMode = Mode.SLEEP;
    private int clientModeTicks;

    public L227Bear(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.SLEEP.ordinal());
    }

    /** Only the always-on basic is a goal; everything else is the state machine below. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Current mode; works on both server and client (synced). */
    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.SLEEP;
    }

    /**
     * Where it sleeps and which way it lies. The spawner calls this for the bear it puts in a
     * cave, so a hunting bear knows where to go back to.
     */
    public void setHome(BlockPos pos, float yaw) {
        home = pos.immutable();
        homeYaw = yaw;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Its hollow (null until it has one): the spawner uses it to know which caves are taken. */
    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Wakes it at once (the {@code /backrooms spawn l227_bear awake} test command). */
    public void wakeNow() {
        startWake(WAKE_TICKS_HURT);
    }

    private boolean lyingDown() {
        return mode == Mode.SLEEP || mode == Mode.STIR || mode == Mode.SETTLE;
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode current = mode();
            if (current != clientMode) {
                clientMode = current;
                clientModeTicks = 0;
            } else {
                clientModeTicks++;
            }
            float riseTarget = switch (current) {
                case SLEEP, STIR, SETTLE -> 0.0f;
                // It heaves itself up after the first eyes are open.
                case WAKE -> clientModeTicks > 4 ? 1.0f : 0.0f;
                default -> 1.0f;
            };
            float eyesTarget = switch (current) {
                case SLEEP, SETTLE -> 0.0f;
                case STIR -> 0.34f;     // five eyes of fifteen
                default -> 1.0f;
            };
            riseO = rise;
            eyesO = eyes;
            rise += (riseTarget - rise) * (riseTarget > rise ? 0.12f : 0.06f);
            // Waking, the eyes open in a fast ripple; stirring, slowly; closing, slower still.
            float eyeRate = current == Mode.WAKE ? 0.09f : eyesTarget > eyes ? 0.035f : 0.025f;
            eyes += Mth.clamp(eyesTarget - eyes, -eyeRate, eyeRate);
        }
    }

    /** 0 lying on the floor, 1 standing (this frame). */
    public float rise(float partialTick) {
        return Mth.lerp(partialTick, riseO, rise);
    }

    /** 0 every eye shut, 1 all fifteen open (this frame). */
    public float eyes(float partialTick) {
        return Mth.lerp(partialTick, eyesO, eyes);
    }

    /** Ticks (with the partial tick) since the current mode started, as this client saw it. */
    public float modeTime(float partialTick) {
        return clientModeTicks + partialTick;
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (maulCooldown > 0) {
            maulCooldown--;
        }
        if (home == null) {
            setHome(blockPosition(), getYRot());
        }
        switch (mode) {
            case SLEEP, STIR -> tickAsleep();
            case WAKE -> tickWake();
            case HUNT -> tickHunt();
            case MAUL -> tickMaul();
            case RETURN -> tickReturn();
            case SETTLE -> tickSettle();
        }
    }

    private void tickAsleep() {
        // Asleep it does not turn or drift: it lies exactly as it lay down.
        getNavigation().stop();
        setYRot(homeYaw);
        yBodyRot = homeYaw;
        yHeadRot = homeYaw;
        if ((tickCount + getId()) % LISTEN_INTERVAL != 0) {
            return;
        }
        int heard = listen();
        noise = heard > 0 ? noise + heard : Math.max(0, noise - QUIET_DECAY);
        if (noise >= WAKE_AT) {
            startWake(WAKE_TICKS);
        } else if (mode == Mode.SLEEP && noise >= STIR_AT) {
            setMode(Mode.STIR);
            playSound(Level227Grazers.BEAR_STIR.get(), 1.4f, 0.9f + random.nextFloat() * 0.15f);
        } else if (mode == Mode.STIR && noise < STIR_AT / 2) {
            setMode(Mode.SLEEP); // it settles back; the eyes close again
        }
    }

    /** How much noise reached it in this listen, from every player nearby. */
    private int listen() {
        int total = 0;
        for (Player player : level().players()) {
            if (!canDisturb(player)) {
                continue;
            }
            double distance = Math.sqrt(distanceToSqr(player));
            boolean loud = player.isSprinting() || (!player.onGround() && player.getDeltaMovement().y > 0.05);
            boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 1.0e-3;
            if (distance < TOUCH_RANGE) {
                total += TOUCH_NOISE;
            } else if (distance < NEAR_RANGE) {
                total += loud ? LOUD_NOISE + WALK_NOISE : player.isCrouching() ? CROUCH_NOISE : moving ? WALK_NOISE : CROUCH_NOISE;
            } else if (distance < NOISE_RANGE && loud) {
                total += LOUD_NOISE;
            }
        }
        return total;
    }

    private void tickWake() {
        getNavigation().stop();
        if (modeTicks == ROAR_AT) {
            // Loud on purpose: the whole cave shakes with it, and you know to run.
            playSound(Level227Grazers.BEAR_ROAR.get(), 3.0f, 0.85f + random.nextFloat() * 0.1f);
        }
        if (prey == null || !isHuntable(prey)) {
            prey = nearestHuntable(NOISE_RANGE * 2.0);
        }
        if (prey != null) {
            getLookControl().setLookAt(prey, 20.0f, 20.0f);
        }
        if (modeTicks >= modeLength) {
            if (prey != null) {
                startHunt(prey);
            } else {
                startReturn();
            }
        }
    }

    private void tickHunt() {
        huntTicks++;
        if (prey == null || !isHuntable(prey)) {
            startReturn();
            return;
        }
        double distanceSqr = distanceToSqr(prey);
        if (getSensing().hasLineOfSight(prey)) {
            unseenTicks = 0;
        } else {
            unseenTicks++;
        }
        if (unseenTicks > LOST_SIGHT_TICKS || distanceSqr > GIVE_UP_DISTANCE * GIVE_UP_DISTANCE || huntTicks > MAX_HUNT_TICKS) {
            startReturn();
            return;
        }
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (maulCooldown <= 0 && distanceSqr < MAUL_START_RANGE * MAUL_START_RANGE) {
            startMaul();
            return;
        }
        if (--repathIn <= 0) {
            repathIn = REPATH_TICKS;
            getNavigation().moveTo(prey, HUNT_SPEED);
        }
    }

    private void tickMaul() {
        getNavigation().stop();
        if (prey != null && modeTicks < MAUL_HIT_AT) {
            // Rearing up it still turns to face you; once the paws come down, it is committed.
            faceTowards(prey.position());
        }
        if (modeTicks == MAUL_HIT_AT) {
            slam();
        }
        if (modeTicks >= MAUL_TICKS) {
            maulCooldown = MAUL_COOLDOWN;
            if (prey != null && isHuntable(prey)) {
                setMode(Mode.HUNT);
                repathIn = 0;
            } else {
                startReturn();
            }
        }
    }

    /** The paws come down: everyone in front of it within reach is hit. */
    private void slam() {
        playSound(Level227Grazers.BEAR_MAUL.get(), 2.0f, 0.9f + random.nextFloat() * 0.15f);
        Vec3 forward = Vec3.directionFromRotation(0.0f, getYRot());
        AABB area = getBoundingBox().inflate(MAUL_REACH, 1.0, MAUL_REACH);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            if (victim instanceof L227Bear || (victim instanceof Player player && !isHuntable(player))) {
                continue;
            }
            Vec3 to = victim.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double distance = to.length();
            if (distance > MAUL_REACH + victim.getBbWidth() * 0.5 + getBbWidth() * 0.5) {
                continue;
            }
            if (distance > 1.0 && to.normalize().dot(forward) < MAUL_CONE) {
                continue;
            }
            if (doHurtTarget(victim) && victim instanceof Player) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MAUL_SLOW_TICKS, 1, false, false, true));
            }
        }
    }

    private void tickReturn() {
        if (home == null || modeTicks > MAX_RETURN_TICKS) {
            settleHere();
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(home);
        if (position().distanceToSqr(target) < 2.25) {
            startSettle();
            return;
        }
        if (--repathIn <= 0 || getNavigation().isDone()) {
            repathIn = 40;
            if (!getNavigation().moveTo(target.x, target.y, target.z, RETURN_SPEED) && modeTicks > 100) {
                settleHere(); // no way back (the way is blocked): sleep here instead
            }
        }
    }

    private void tickSettle() {
        getNavigation().stop();
        setYRot(Mth.approachDegrees(getYRot(), homeYaw, 6.0f));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (modeTicks >= SETTLE_TICKS) {
            noise = 0;
            setMode(Mode.SLEEP);
        }
    }

    // ------------------------------------------------------------------ transitions

    private void startWake(int ticks) {
        noise = 0;
        setMode(Mode.WAKE);
        modeLength = ticks;
        getNavigation().stop();
    }

    private void startHunt(Player player) {
        prey = player;
        setTarget(player);
        unseenTicks = 0;
        huntTicks = 0;
        repathIn = 0;
        setMode(Mode.HUNT);
    }

    private void startMaul() {
        setMode(Mode.MAUL);
        getNavigation().stop();
        // A deep chuff as it rears.
        playSound(Level227Grazers.BEAR_BREATH.get(), 2.0f, 0.75f);
    }

    private void startReturn() {
        prey = null;
        setTarget(null);
        repathIn = 0;
        setMode(Mode.RETURN);
    }

    private void startSettle() {
        getNavigation().stop();
        setMode(Mode.SETTLE);
    }

    private void settleHere() {
        home = blockPosition();
        homeYaw = getYRot();
        startSettle();
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private void faceTowards(Vec3 target) {
        double dx = target.x - getX();
        double dz = target.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(Mth.approachDegrees(getYRot(), yaw, 25.0f));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    // ------------------------------------------------------------------ senses

    /** A player whose noise counts: in the same level, alive, in survival/adventure, not hiding. */
    private boolean canDisturb(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !HidingService.isHidden(player)
                && distanceToSqr(player) < NOISE_RANGE * NOISE_RANGE;
    }

    private boolean isHuntable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !HidingService.isHiddenFrom(player, this);
    }

    @Nullable
    private Player nearestHuntable(double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : level().players()) {
            double distance = distanceToSqr(player);
            if (distance < bestDistance && isHuntable(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Any hurt from a player provokes it: up at once if it was lying down, and after them. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            Entity attacker = source.getEntity();
            Player player = attacker instanceof Player p && isHuntable(p) ? p : null;
            if (player != null) {
                prey = player;
            }
            if (lyingDown()) {
                startWake(WAKE_TICKS_HURT);
            } else if (player != null && (mode == Mode.RETURN || mode == Mode.HUNT)) {
                startHunt(player);
            }
        }
        return hurt;
    }

    /** Lying in its hollow it is a heavy, still heap: it can't be shoved around. */
    @Override
    public boolean isPushable() {
        return !lyingDown();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return switch (mode) {
            case SLEEP, STIR -> Level227Grazers.BEAR_SNORE.get();
            case HUNT, RETURN -> Level227Grazers.BEAR_BREATH.get();
            default -> null;
        };
    }

    @Override
    protected float getSoundVolume() {
        return 1.6f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227Grazers.BEAR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227Grazers.BEAR_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Level227Grazers.BEAR_STEP.get(), mode == Mode.HUNT ? 1.0f : 0.6f, 0.85f + random.nextFloat() * 0.2f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
