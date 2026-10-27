package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Facelings (Entity 9): adult-sized people without faces. Smooth, mannequin-like, too clean, in
 * plain everyday clothes. Most of them, the "Strangers", ignore you completely: they wander the
 * dirt paths of Level 39's town, stand still for a long time, turn their blank heads to follow
 * you, and touch their own missing faces as if checking something is still not there.
 *
 * <p>A minority are "Hostile without a cause". They look and act exactly the same, which is the
 * point: you can't tell them apart until one of them sees you. They come in groups of 3-7 (the
 * spawner places the group together), and when one spots you the whole group turns and runs at you
 * at once, wheezing. You can outrun them (they are a little slower than a sprint) and they give up
 * after losing sight of you for five seconds.
 *
 * <p>Some (about one in seven) are "Partial" facelings: a faint suggestion of features, like a
 * face pressed through a sheet. Purely visual ({@link #isPartial()}).
 *
 * <p>Whether one is hostile is decided when it spawns ({@link #setHostile}). The behaviour is a
 * small state machine ({@link Action}) synced to clients so the model can play the slow walk, the
 * face touch, the stare and the sprint.
 *
 * <p>Nobody hiding in furniture ({@code player/HidingService}) is seen or stared at. A hostile one
 * that saw you get in comes and drags you out; otherwise hiding counts as losing sight of you.
 */
public class Faceling extends Monster implements HidingService.Seeker {
    /** What it is doing now. The order matters: it is synced as a byte. */
    public enum Action { WANDER, STAND, STARE, TOUCH, FLEE, CHASE }

    private static final Action[] ACTIONS = Action.values();
    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(Faceling.class, EntityDataSerializers.BYTE);
    /** Bits 0-1: outfit (0-3); bit 2: Partial (faint features). */
    private static final EntityDataAccessor<Byte> LOOK = SynchedEntityData.defineId(Faceling.class, EntityDataSerializers.BYTE);
    public static final int OUTFITS = 4;

    /** Hostile ones notice you within this range with a clear view (no faces, yet they see fine). */
    private static final double SIGHT = 20.0;
    /** When one spots you, every hostile faceling this close joins in. */
    private static final double ALERT_RADIUS = 32.0;
    private static final double CHASE_GIVE_UP = 40.0;
    private static final int LOST_SIGHT_TICKS = 100;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final int CHASE_REPATH_TICKS = 10;
    private static final int FLEE_TICKS = 120;

    // 0.28 at 1.15 is just under 4.5 blocks/s: a sprint escapes them, a walk doesn't.
    private static final double WANDER_SPEED = 0.5;
    private static final double FLEE_SPEED = 0.95;
    private static final double CHASE_SPEED = 1.15;

    // Server-side state. Never saved (facelings aren't saved; the spawner places them).
    private boolean hostile;
    private Action action = Action.STAND;
    private int actionTicks;
    private int actionLength = 100;
    @Nullable
    private Player target;
    private Vec3 lastSeen = Vec3.ZERO;
    private int unseenTicks;
    private int repathIn;
    private int attackCooldown;

    // Client-only, purely visual: when the current action started (so the face touch plays from
    // its beginning), and eased 0..1 weights for the touch, stare and sprint poses so the model
    // blends into and out of them instead of snapping.
    private Action clientAction = Action.STAND;
    private int clientActionStart;
    private float touch, touchO;
    private float stare, stareO;
    private float run, runO;

    public Faceling(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, (byte) Action.STAND.ordinal());
        builder.define(LOOK, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Picks the outfit and (rarely) a Partial face. Hostile or not is up to the spawner. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        int look = random.nextInt(OUTFITS) | (random.nextInt(7) == 0 ? 4 : 0);
        entityData.set(LOOK, (byte) look);
        return data;
    }

    // ------------------------------------------------------------------ public API

    /** "Hostile without a cause". Decided once when it spawns. Server side only. */
    public void setHostile(boolean hostile) {
        this.hostile = hostile;
    }

    public boolean isHostile() {
        return hostile;
    }

    /** Current action; works on both server and client (synced). */
    public Action action() {
        byte b = entityData.get(ACTION);
        return b >= 0 && b < ACTIONS.length ? ACTIONS[b] : Action.STAND;
    }

    /** Which of the {@link #OUTFITS} clothes textures it wears. */
    public int outfit() {
        return entityData.get(LOOK) & 3;
    }

    /** A Partial faceling: faint features under the skin. */
    public boolean isPartial() {
        return (entityData.get(LOOK) & 4) != 0;
    }

    /** Ticks since the current action began, interpolated (client side, for the model). */
    public float actionTime(float partialTick) {
        return tickCount - clientActionStart + partialTick;
    }

    /** How far into the face-touch pose it is (0..1), this frame. */
    public float touchWeight(float partialTick) {
        return Mth.lerp(partialTick, touchO, touch);
    }

    /** How far into the staring pose (head tilted, following you) it is (0..1), this frame. */
    public float stareWeight(float partialTick) {
        return Mth.lerp(partialTick, stareO, stare);
    }

    /** How far into the hostile sprint it is (0..1), this frame. */
    public float runWeight(float partialTick) {
        return Mth.lerp(partialTick, runO, run);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Action current = action();
            if (current != clientAction) {
                clientAction = current;
                clientActionStart = tickCount;
            }
            touchO = touch;
            stareO = stare;
            runO = run;
            // The hand comes up slowly and goes down a little faster; the sprint starts at once.
            touch += ((current == Action.TOUCH ? 1.0f : 0.0f) - touch) * (current == Action.TOUCH ? 0.1f : 0.16f);
            stare += ((current == Action.STARE ? 1.0f : 0.0f) - stare) * 0.08f;
            run += ((current == Action.CHASE ? 1.0f : 0.0f) - run) * 0.25f;
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        actionTicks++;
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (action == Action.CHASE) {
            tickChase();
            return;
        }
        if (hostile && (tickCount + getId()) % 5 == 0) {
            Player seen = findVisible();
            if (seen != null) {
                alertGroup(seen);
                return;
            }
        }
        switch (action) {
            case WANDER -> {
                if (getNavigation().isDone() || actionTicks > 400) {
                    pickIdle();
                }
            }
            case STAND, TOUCH -> {
                getNavigation().stop();
                if (actionTicks >= actionLength) {
                    pickNext();
                }
            }
            case STARE -> tickStare();
            case FLEE -> {
                if (actionTicks >= FLEE_TICKS) {
                    pickIdle();
                }
            }
            default -> {
            }
        }
    }

    /** After a walk: stand, touch its face, or stare at someone nearby. */
    private void pickIdle() {
        getNavigation().stop();
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), 14.0,
                EntitySelector.NO_SPECTATORS.and(entity -> !HidingService.isHidden(entity)));
        int roll = random.nextInt(10);
        if (near != null && roll < 4 && getSensing().hasLineOfSight(near)) {
            setAction(Action.STARE, 80 + random.nextInt(120));
        } else if (roll < 6) {
            // Touching its face: the hand comes up, fingertips trace where a face should be.
            setAction(Action.TOUCH, 70 + random.nextInt(40));
        } else {
            // Standing perfectly still, sometimes for a long time.
            setAction(Action.STAND, 80 + random.nextInt(320));
        }
    }

    /** After standing: usually walk somewhere slowly, sometimes keep standing. */
    private void pickNext() {
        if (random.nextInt(3) == 0) {
            pickIdle();
            return;
        }
        Vec3 to = DefaultRandomPos.getPos(this, 14, 3);
        if (to != null) {
            setAction(Action.WANDER, 400);
            getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED);
        } else {
            setAction(Action.STAND, 100 + random.nextInt(200));
        }
    }

    /** The head follows you; the body turns slowly after it. It never comes closer. */
    private void tickStare() {
        getNavigation().stop();
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), 20.0,
                EntitySelector.NO_SPECTATORS.and(entity -> !HidingService.isHidden(entity)));
        if (near != null) {
            getLookControl().setLookAt(near, 4.0f, 10.0f);
        }
        if (near == null || actionTicks >= actionLength) {
            pickNext();
        }
    }

    private void tickChase() {
        if (target != null && target.isDeadOrDying()) {
            calmDown();
            return;
        }
        // Hiding in furniture (where it didn't see you get in) counts as being out of sight.
        boolean hid = HidingService.isHiddenFrom(target, this);
        if (!hid && !isHuntable(target) || distanceToSqr(target) > CHASE_GIVE_UP * CHASE_GIVE_UP) {
            calmDown();
            return;
        }
        if (!hid && getSensing().hasLineOfSight(target)) {
            unseenTicks = 0;
            lastSeen = target.position();
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (distanceToSqr(target) < 6.0) {
                getNavigation().stop();
                getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), CHASE_SPEED);
            } else if (--repathIn <= 0) {
                repathIn = CHASE_REPATH_TICKS;
                getNavigation().moveTo(target, CHASE_SPEED);
            }
            if (attackCooldown <= 0 && isWithinMeleeAttackRange(target)) {
                attackCooldown = ATTACK_COOLDOWN_TICKS;
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(target);
            }
            return;
        }
        if (unseenTicks++ == 0) {
            getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, CHASE_SPEED);
        }
        if (unseenTicks >= LOST_SIGHT_TICKS) {
            calmDown();
        }
    }

    // ------------------------------------------------------------------ transitions

    /** One saw you: the whole group (every hostile faceling nearby) comes at once. */
    private void alertGroup(Player player) {
        startChase(player, true);
        for (Faceling other : level().getEntitiesOfClass(Faceling.class, getBoundingBox().inflate(ALERT_RADIUS))) {
            if (other != this && other.hostile && other.action != Action.CHASE) {
                other.startChase(player, false);
            }
        }
    }

    private void startChase(Player player, boolean first) {
        target = player;
        setTarget(player);
        lastSeen = player.position();
        unseenTicks = 0;
        repathIn = 0;
        setAction(Action.CHASE, 0);
        // The first one rasps loudly; the rest join in more quietly, so a group sounds like a group.
        playSound(Level39Mobs.FACELING_RASP.get(), first ? 2.0f : 1.0f, 0.85f + random.nextFloat() * 0.3f);
    }

    private void calmDown() {
        target = null;
        setTarget(null);
        getNavigation().stop();
        pickIdle();
    }

    private void setAction(Action next, int length) {
        action = next;
        actionTicks = 0;
        actionLength = length;
        entityData.set(ACTION, (byte) next.ordinal());
    }

    @Nullable
    private Player findVisible() {
        Player best = null;
        double bestDistance = SIGHT * SIGHT;
        for (Player player : level().players()) {
            if (!isHuntable(player)) {
                continue;
            }
            double distance = distanceToSqr(player);
            double range = player.isCrouching() ? SIGHT * 0.6 : SIGHT;
            if (distance < bestDistance && distance < range * range && getSensing().hasLineOfSight(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Someone it may chase: not in a safe room, and not hiding in furniture (unless it saw them get in). */
    private boolean isHuntable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player)
                && !HidingService.isHiddenFrom(player, this);
    }

    /** Only the hostile ones look for hiders; Strangers don't care where you are. */
    @Override
    public boolean isSeeking() {
        return hostile;
    }

    /** It dragged someone out of their hiding place: the whole group comes at them. */
    @Override
    public void onPulledOut(ServerPlayer player) {
        alertGroup(player);
    }

    // ------------------------------------------------------------------ vanilla hooks

    /**
     * Strangers never fight back: they walk briskly away from whoever hurt them. Hostile ones
     * turn on the attacker, and bring their group.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && source.getEntity() instanceof Player attacker) {
            if (hostile) {
                if (action != Action.CHASE && isHuntable(attacker)) {
                    alertGroup(attacker);
                }
            } else {
                Vec3 away = position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0e-4) {
                    Vec3 to = position().add(away.normalize().scale(12.0));
                    setAction(Action.FLEE, FLEE_TICKS);
                    getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
                }
            }
        }
        return hurt;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 220;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return action == Action.CHASE ? Level39Mobs.FACELING_RASP.get() : Level39Mobs.FACELING_WHEEZE.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level39Mobs.FACELING_HURT.get();
    }

    /** The death rattle. */
    @Override
    protected SoundEvent getDeathSound() {
        return Level39Mobs.FACELING_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        float volume = action == Action.CHASE ? 0.6f : 0.25f;
        playSound(Level39Mobs.FACELING_STEP.get(), volume, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
