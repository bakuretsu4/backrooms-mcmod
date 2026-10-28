package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.Level227Grazers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The boars of Level 227: the largest animals in the level, a hairy hill of a pig with four
 * tusks, about 5.5 blocks long.
 *
 * <p>The wiki: they often lounge face up on the forest floor, their meat is safe to eat once
 * cooked, and when hurt they thrash and flail, and the tusks can kill. So:
 * <ul>
 *   <li><b>Lounging.</b> Most of the time a boar lies on its back in the undergrowth, legs in the
 *       air, snoring. Now and then it rolls over, gets up, roots around with its tusks and wanders
 *       a little, then lies down and rolls onto its back again. It ignores you.</li>
 *   <li><b>Hurt, it thrashes.</b> Every hit sets off three seconds of thrashing: it squeals,
 *       spins, kicks and swings its head, and anyone within about {@value #THRASH_RANGE} blocks
 *       is gored again and again (about 4.5 hearts a hit on Normal). Hit it, then get back. After
 *       a thrash it runs off for a few seconds; hit it again and it thrashes again.</li>
 *   <li><b>Meat.</b> It has a lot of health, so hunting one with a sword is hit and step back,
 *       hit and step back. A bow is the safe way. It drops 3-6 raw boar meat (see the loot table
 *       {@code entities/l227_boar}).</li>
 * </ul>
 *
 * <p>Brain ({@link Mode}), run in {@link #customServerAiStep()}:
 * <pre>
 * LOUNGE --a long while--> GET_UP --> ROOT &lt;--&gt; WANDER --a while--> LIE_DOWN --> LOUNGE
 * any --hurt--> THRASH --3 s--> FLEE --5 s--> ROOT
 * </pre>
 */
public class L227Boar extends PathfinderMob {
    /** What the boar is doing. The order matters: it is synced as a byte. */
    public enum Mode { LOUNGE, GET_UP, ROOT, WANDER, LIE_DOWN, THRASH, FLEE }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(L227Boar.class, EntityDataSerializers.BYTE);
    /** Which way it rolls onto its back (+1 or -1), so a herd doesn't all roll the same way. */
    private static final EntityDataAccessor<Byte> ROLL_SIDE = SynchedEntityData.defineId(L227Boar.class, EntityDataSerializers.BYTE);

    // --- Timings -------------------------------------------------------------------------------
    /** Lounging lasts 40-120 s, then it gets up for a while. */
    private static final int LOUNGE_MIN = 800;
    private static final int LOUNGE_EXTRA = 1600;
    /** Getting up or lying down: rolling over and heaving the bulk around. */
    public static final int ROLL_TICKS = 36;
    /** Up and about for 30-75 s before it lies down again. */
    private static final int AWAKE_MIN = 600;
    private static final int AWAKE_EXTRA = 900;
    private static final int THRASH_TICKS = 60;
    private static final int FLEE_TICKS = 100;

    // --- The thrash -------------------------------------------------------------------------------
    /** Everything this far from its middle (plus the victim's own half-width) gets gored. */
    private static final double THRASH_RANGE = 3.6;
    /** Each victim is gored at most this often (the tusks swing past again and again). */
    private static final int GORE_INTERVAL = 12;
    /** How hard the tusks throw you (sideways and a little up). */
    private static final double GORE_THROW = 0.9;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.22) ---------------------------------------------
    private static final double WANDER_SPEED = 0.6;
    private static final double FLEE_SPEED = 1.7;

    // Server-side brain state (never saved: the spawner keeps the forest stocked).
    private Mode mode = Mode.LOUNGE;
    private int modeTicks;
    private int modeLength = LOUNGE_MIN;
    /** Ticks left before it lies down again, while it is up. */
    private int awakeLeft;
    /** Ticks until the thrash may gore again, per victim slot (shared: it only matters roughly). */
    private int goreCooldown;
    @Nullable
    private Vec3 fleeFrom;
    /** Spinning while it thrashes: degrees per tick, flipping direction now and then. */
    private float spin;

    // Client-only visuals, eased each tick: how far it lies (0 standing, 1 on its belly), how far it
    // has rolled onto its back (0..1), how hard it is thrashing (0..1) and how far its head is down
    // rooting (0..1). Rolling is timed from the mode's start, so both ends stay in sync.
    private float lie;
    private float lieO;
    private float roll;
    private float rollO;
    private float thrash;
    private float thrashO;
    private float rootHead;
    private float rootHeadO;
    @Nullable
    private Mode clientMode;
    private int clientModeTicks;

    public L227Boar(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 20.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.LOUNGE.ordinal());
        builder.define(ROLL_SIDE, (byte) 1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.LOUNGE;
    }

    /** +1 or -1: which side it rolls over onto its back. */
    public int rollSide() {
        return entityData.get(ROLL_SIDE) < 0 ? -1 : 1;
    }

    /** Puts it on its feet (the spawner does this for the ones it places awake). */
    public void standUp() {
        awakeLeft = AWAKE_MIN + random.nextInt(AWAKE_EXTRA);
        startRoot();
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide && mode == Mode.LOUNGE) {
            entityData.set(ROLL_SIDE, (byte) (random.nextBoolean() ? 1 : -1));
            modeLength = LOUNGE_MIN + random.nextInt(LOUNGE_EXTRA);
        }
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode current = mode();
            if (clientMode == null) {
                // First sight of it: show it as it is (on its back or on its feet), no roll.
                boolean down = current == Mode.LOUNGE || current == Mode.GET_UP;
                lie = lieO = down ? 1.0f : 0.0f;
                roll = rollO = down ? 1.0f : 0.0f;
                clientMode = current;
                clientModeTicks = 0;
            } else if (current != clientMode) {
                clientMode = current;
                clientModeTicks = 0;
            } else {
                clientModeTicks++;
            }
            lieO = lie;
            rollO = roll;
            thrashO = thrash;
            rootHeadO = rootHead;
            float f = Math.min(1.0f, clientModeTicks / (float) ROLL_TICKS);
            switch (current) {
                case LOUNGE -> {
                    lie = 1.0f;
                    roll = 1.0f;
                }
                case LIE_DOWN -> {
                    // First the legs fold and it drops onto its belly, then it rolls onto its back.
                    lie = Math.max(lie, Mth.clamp(f * 2.5f, 0.0f, 1.0f));
                    roll = Math.max(roll, smooth(Mth.clamp((f - 0.35f) / 0.65f, 0.0f, 1.0f)));
                }
                case GET_UP -> {
                    // Rolls back onto its belly with a heave, then gets its legs under it.
                    roll = Math.min(roll, 1.0f - smooth(Mth.clamp(f / 0.6f, 0.0f, 1.0f)));
                    lie = Math.min(lie, 1.0f - Mth.clamp((f - 0.55f) / 0.45f, 0.0f, 1.0f));
                }
                default -> {
                    // Thrashing on its back stays on its back; anything else is on its feet.
                    if (current != Mode.THRASH || roll < 0.5f) {
                        roll += (0.0f - roll) * 0.25f;
                        lie += (0.0f - lie) * 0.25f;
                    }
                }
            }
            float thrashTarget = current == Mode.THRASH ? 1.0f : 0.0f;
            thrash += (thrashTarget - thrash) * (thrashTarget > thrash ? 0.5f : 0.1f);
            float rootTarget = current == Mode.ROOT ? 1.0f : 0.0f;
            rootHead += (rootTarget - rootHead) * 0.08f;
        }
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    public float lie(float partialTick) {
        return Mth.lerp(partialTick, lieO, lie);
    }

    public float roll(float partialTick) {
        return Mth.lerp(partialTick, rollO, roll);
    }

    public float thrash(float partialTick) {
        return Mth.lerp(partialTick, thrashO, thrash);
    }

    public float rootHead(float partialTick) {
        return Mth.lerp(partialTick, rootHeadO, rootHead);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (goreCooldown > 0) {
            goreCooldown--;
        }
        if (awakeLeft > 0) {
            awakeLeft--;
        }
        switch (mode) {
            case LOUNGE -> {
                getNavigation().stop();
                if (modeTicks >= modeLength) {
                    setMode(Mode.GET_UP);
                    playSound(Level227Grazers.BOAR_GRUNT.get(), 1.4f, 0.8f);
                }
            }
            case GET_UP -> {
                getNavigation().stop();
                if (modeTicks >= ROLL_TICKS) {
                    awakeLeft = AWAKE_MIN + random.nextInt(AWAKE_EXTRA);
                    startRoot();
                }
            }
            case ROOT -> {
                getNavigation().stop();
                if (modeTicks >= modeLength) {
                    if (awakeLeft <= 0) {
                        setMode(Mode.LIE_DOWN);
                    } else {
                        startWander();
                    }
                }
            }
            case WANDER -> {
                if (getNavigation().isDone() || modeTicks >= modeLength) {
                    startRoot();
                }
            }
            case LIE_DOWN -> {
                getNavigation().stop();
                if (modeTicks >= ROLL_TICKS) {
                    setMode(Mode.LOUNGE);
                    modeLength = LOUNGE_MIN + random.nextInt(LOUNGE_EXTRA);
                }
            }
            case THRASH -> tickThrash();
            case FLEE -> {
                if (modeTicks >= FLEE_TICKS) {
                    awakeLeft = Math.max(awakeLeft, AWAKE_MIN);
                    startRoot();
                } else if (getNavigation().isDone()) {
                    runAway();
                }
            }
        }
    }

    /** Spinning, kicking, swinging the tusks: everyone in range is gored now and then. */
    private void tickThrash() {
        getNavigation().stop();
        if (modeTicks % 9 == 0) {
            spin = (random.nextBoolean() ? 1 : -1) * (14.0f + random.nextFloat() * 18.0f);
            playSound(Level227Grazers.BOAR_THRASH.get(), 1.6f, 0.85f + random.nextFloat() * 0.3f);
        }
        float yaw = getYRot() + spin;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw + Mth.sin(modeTicks * 0.9f) * 40.0f;
        if (goreCooldown <= 0) {
            gore();
        }
        if (modeTicks >= THRASH_TICKS) {
            setMode(Mode.FLEE);
            runAway();
        }
    }

    private void gore() {
        boolean hit = false;
        AABB area = getBoundingBox().inflate(THRASH_RANGE - getBbWidth() * 0.5 + 1.0, 0.5, THRASH_RANGE - getBbWidth() * 0.5 + 1.0);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            if (victim instanceof L227Boar || (victim instanceof Player player && (player.isCreative() || player.isSpectator()))) {
                continue;
            }
            Vec3 to = victim.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            if (to.length() > THRASH_RANGE + victim.getBbWidth() * 0.5) {
                continue;
            }
            if (doHurtTarget(victim)) {
                hit = true;
                // Thrown away from the boar by the swinging head.
                Vec3 away = to.lengthSqr() > 1.0e-4 ? to.normalize() : new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5).normalize();
                victim.push(away.x * GORE_THROW, 0.35, away.z * GORE_THROW);
                victim.hurtMarked = true;
            }
        }
        if (hit) {
            goreCooldown = GORE_INTERVAL;
            playSound(Level227Grazers.BOAR_GORE.get(), 1.6f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    private void runAway() {
        Vec3 to = fleeFrom != null ? DefaultRandomPos.getPosAway(this, 16, 5, fleeFrom) : DefaultRandomPos.getPos(this, 16, 5);
        if (to != null) {
            getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
        }
    }

    private void startRoot() {
        setMode(Mode.ROOT);
        modeLength = 100 + random.nextInt(160);
    }

    private void startWander() {
        Vec3 to = DefaultRandomPos.getPos(this, 12, 3);
        if (to != null && getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED)) {
            setMode(Mode.WANDER);
            modeLength = 240;
        } else {
            startRoot();
        }
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Every hit starts (or restarts) the thrash, wherever it was: lying, up, running. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            Entity attacker = source.getEntity();
            fleeFrom = attacker != null ? attacker.position() : null;
            if (mode != Mode.THRASH) {
                setMode(Mode.THRASH);
                goreCooldown = 4; // a heartbeat before the first swing: just enough to step back
            } else {
                modeTicks = Math.max(0, modeTicks - THRASH_TICKS / 2);
            }
            // The squeal is its hurt sound (getHurtSound): the cue to get away.
        }
        return hurt;
    }

    /** A hill of a pig: players can't shove it around. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return switch (mode) {
            case LOUNGE -> Level227Grazers.BOAR_SNORE.get();
            case THRASH -> null;
            default -> Level227Grazers.BOAR_GRUNT.get();
        };
    }

    @Override
    protected float getSoundVolume() {
        return 1.5f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227Grazers.BOAR_SQUEAL.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227Grazers.BOAR_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Level227Grazers.BOAR_STEP.get(), mode == Mode.FLEE ? 0.9f : 0.55f, 0.85f + random.nextFloat() * 0.2f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
