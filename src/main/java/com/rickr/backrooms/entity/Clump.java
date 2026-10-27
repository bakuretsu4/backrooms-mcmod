package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Clumps (Entity 5): a bulky knot of tangled human limbs, about as tall as a person, that waits in
 * the tight places of the Level 39 tunnels (a drain in the vault, a dry alcove beside the water)
 * with only a few arms hanging out as bait. It can wait there, motionless, for as long as it
 * takes. When prey comes close it whips out one very long arm (6 blocks), coils it around them and
 * drags them into its hole.
 *
 * <p>It is blind; it HUNTS BY EAR. That is the whole trick to surviving it:
 * <ul>
 *   <li>Sprinting is loud: it hears that from {@value #HEAR_SPRINT} blocks. Wading or swimming
 *       splashes: {@value #HEAR_SPLASH} blocks. Plain walking only from {@value #HEAR_WALK}.</li>
 *   <li>Sneaking (or standing still) is silent. Creep past, or back away slowly, and it never
 *       knows you were there... unless you touch the bait (within {@value #TOUCH} blocks).</li>
 *   <li>When it hears someone it can't reach yet it tries to lure them closer: it claps two hands
 *       together or pounds the wall.</li>
 *   <li>The tell: the dangling arms go dead still and its knuckles crack for a moment before it
 *       strikes ({@link State#TENSE}). Freeze and sneak right then, and it loses you.</li>
 * </ul>
 *
 * <p>If it catches you it pulls you in and bites every {@value #BITE_INTERVAL} ticks. Hit it
 * ({@value #RELEASE_DAMAGE} damage while it holds you) and it lets go; it also gives up on its
 * own after {@value #MAX_HOLD_TICKS} ticks. Then it needs {@value #RETRACT_TICKS} ticks to coil
 * back up: that is your chance to run.
 *
 * <p>State machine, run on the server in {@link #customServerAiStep()}; the state is synced so the
 * model can pose:
 * <pre>
 * LURK --hears someone--> LISTEN (lures) --noisy prey in reach / touches bait--> TENSE --> STRIKE
 * STRIKE --hit--> DRAG (pull, then bite) --hurt / timeout--> RETRACT --> LURK
 * STRIKE --miss--> RETRACT
 * </pre>
 */
public class Clump extends Monster {
    /** What the Clump is doing. The order matters: it is synced as a byte. */
    public enum State { LURK, LISTEN, TENSE, STRIKE, DRAG, RETRACT }

    /** Where it hides: hanging out of a drain in the vault, or sitting at the back of a side alcove. */
    public enum Hide { CEILING, ALCOVE }

    private static final State[] STATES = State.values();
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Clump.class, EntityDataSerializers.BYTE);
    /** Entity id of whoever the long arm is reaching for or holding (-1: nobody). */
    private static final EntityDataAccessor<Integer> PREY_ID = SynchedEntityData.defineId(Clump.class, EntityDataSerializers.INT);
    /** Which way the hiding place opens (yaw, degrees). The whole body is drawn facing this way. */
    private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(Clump.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> CEILING = SynchedEntityData.defineId(Clump.class, EntityDataSerializers.BOOLEAN);

    /** Entity events (sent to clients to start a lure animation). Vanilla uses ids below 70. */
    public static final byte EVENT_CLAP = 71;
    public static final byte EVENT_POUND = 72;

    // --- The arm and the bait -----------------------------------------------------------------
    /** How far the long arm reaches from the mouth of its hole (canon: 6 m). */
    public static final double REACH = 6.0;
    /** Touching the dangling bait arms gives you away, however quiet you are. */
    public static final double TOUCH = 1.8;
    /** Model-space length of the long arm, so the arm drawn is exactly as long as the real reach. */
    public static final float ARM_LENGTH_PX = (float) (REACH * 16.0);

    // --- Hearing (blocks) ---------------------------------------------------------------------
    public static final double HEAR_SPRINT = 22.0;
    public static final double HEAR_SPLASH = 14.0;
    public static final double HEAR_WALK = 6.5;
    /** Below this many blocks per tick of sideways movement a player counts as standing still. */
    private static final double STILL_SPEED = 0.03;
    private static final int SENSE_INTERVAL = 4;

    // --- Timings (ticks; 20 = 1 s) -----------------------------------------------------------
    /** The warning before a strike: arms freeze, knuckles crack. */
    private static final int TENSE_TICKS = 10;
    /** Shorter if you bumped right into the bait. */
    private static final int TENSE_TOUCH_TICKS = 4;
    /** How long the arm takes to fly out to you. */
    private static final int STRIKE_TICKS = 4;
    private static final int BITE_INTERVAL = 25;
    /**
     * It lets go on its own after this long. Kept under 4 s on purpose: a player held up in the air
     * for 80 ticks would be kicked by servers that don't allow flying.
     */
    private static final int MAX_HOLD_TICKS = 70;
    private static final int RETRACT_TICKS = 100;
    /** LISTEN gives up after this long without hearing anyone. */
    private static final int LISTEN_GIVE_UP = 200;
    /** Someone it just let go of is ignored for this long (so they can really get away). */
    private static final int IGNORE_AFTER_RELEASE = 80;
    private static final int LONELY_DESPAWN_TICKS = 60 * 20;
    private static final double LONELY_RANGE = 80.0;

    // --- Damage -------------------------------------------------------------------------------
    /** The arm hitting you (the ATTACK_DAMAGE attribute, so difficulty scales it as usual). */
    private static final float GRAB_DAMAGE = 2.0f;
    private static final float BITE_DAMAGE = 2.0f;
    /** Damage it must take while holding someone before it lets go. A few punches do it. */
    private static final float RELEASE_DAMAGE = 3.0f;
    /** Blocks per tick it reels its prey in. */
    private static final double PULL_SPEED = 0.34;

    // --- Bursts of speed -----------------------------------------------------------------------
    /** An alcove clump lurches this far out of its alcove just before it strikes. */
    private static final double ALCOVE_LUNGE = 1.1;
    /** A drain clump drops this far out of its drain before it strikes. */
    private static final double CEILING_LUNGE = 0.6;
    /** Shot from out of reach, a drain clump yanks itself this far up its drain to hide. */
    private static final double CEILING_WITHDRAW = 1.6;

    // Server-side brain. Not saved (the type is noSave): the spawner places new ones.
    private State state = State.LURK;
    private int stateTicks;
    @Nullable
    private Vec3 anchor;
    @Nullable
    private Player prey;
    private int quietTicks;
    private int lureIn = 60;
    private boolean touchStrike;
    private float heldDamage;
    private int holdTicks;
    private int atMouthTicks;
    private double lunge;
    private double lungeGoal;
    private int lonelyTicks;
    @Nullable
    private Player ignored;
    private int ignoreTicks;

    // Client-only, purely visual.
    private float armExt;
    private float armExtO;
    @Nullable
    private Vec3 lastArmTarget;
    private State clientState = State.LURK;
    private int clientStateTicks;
    private int lureKind;
    private int lureStart = -1000;
    private float stillness;
    private float stillnessO;

    public Clump(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, GRAB_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, HEAR_SPRINT)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, (byte) State.LURK.ordinal());
        builder.define(PREY_ID, -1);
        builder.define(FACING, 0.0f);
        builder.define(CEILING, false);
    }

    /** No goals: it never wanders. Everything happens in {@link #customServerAiStep()}. */
    @Override
    protected void registerGoals() {
    }

    // ------------------------------------------------------------------ placement

    /**
     * Puts a new Clump into its hiding place. {@code feet} is where its feet (the bottom of its
     * hitbox) go; {@code facingYaw} is the way the hole opens (toward the tunnel).
     */
    @Nullable
    public static Clump place(ServerLevel level, Hide hide, Vec3 feet, float facingYaw) {
        Clump clump = Level39Mobs.CLUMP.get().create(level);
        if (clump == null) {
            return null;
        }
        clump.moveTo(feet.x, feet.y, feet.z, facingYaw, 0.0f);
        clump.anchorAt(hide, feet, facingYaw);
        clump.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(feet)), MobSpawnType.EVENT, null);
        return level.addFreshEntity(clump) ? clump : null;
    }

    private void anchorAt(Hide hide, Vec3 feet, float facingYaw) {
        anchor = feet;
        entityData.set(CEILING, hide == Hide.CEILING);
        entityData.set(FACING, facingYaw);
        setNoGravity(hide == Hide.CEILING);
    }

    /**
     * A Clump from a spawn egg or /summon hides where it stands: under a ceiling if there is one
     * within 3 blocks above its head (it hangs from it), otherwise sitting on the floor.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, type, data);
        if (anchor == null) {
            BlockPos.MutableBlockPos pos = BlockPos.containing(getX(), getY() + getBbHeight(), getZ()).mutable();
            for (int i = 0; i < 4; i++) {
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    // Hang so the top of the body tucks right under that ceiling.
                    anchorAt(Hide.CEILING, new Vec3(getX(), pos.getY() - getBbHeight() + 0.2, getZ()), getYRot());
                    return result;
                }
                pos.move(0, 1, 0);
            }
            anchorAt(Hide.ALCOVE, position(), getYRot());
        }
        return result;
    }

    // ------------------------------------------------------------------ synced getters

    public State state() {
        byte b = entityData.get(STATE);
        return b >= 0 && b < STATES.length ? STATES[b] : State.LURK;
    }

    public boolean isCeiling() {
        return entityData.get(CEILING);
    }

    /** Yaw (degrees) the hiding place opens toward; the renderer turns the body this way. */
    public float facingYaw() {
        return entityData.get(FACING);
    }

    /**
     * The mouth of its hole, where the long arm comes out and where prey ends up: the bottom of
     * the body for a drain clump, the front of the body for an alcove clump. The model puts the
     * root of the arm at exactly this point ({@link #mouthOffset}).
     */
    public Vec3 mouth() {
        return position().add(mouthOffset(isCeiling(), facingYaw()));
    }

    /** {@link #mouth()} relative to the feet. Shared with the model so arm and hitbox agree. */
    public static Vec3 mouthOffset(boolean ceiling, float facingYaw) {
        if (ceiling) {
            return new Vec3(0.0, 0.15, 0.0);
        }
        float yaw = facingYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.75, 0.55, Mth.cos(yaw) * 0.75);
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickVisuals();
        }
    }

    private void tickVisuals() {
        State now = state();
        if (now != clientState) {
            clientState = now;
            clientStateTicks = 0;
        } else {
            clientStateTicks++;
        }
        // The long arm snaps out fast (about 4 ticks) and coils back slowly.
        armExtO = armExt;
        boolean out = now == State.STRIKE || now == State.DRAG;
        armExt = out ? Math.min(1.0f, armExt + 0.28f) : Math.max(0.0f, armExt - 0.06f);
        // The bait arms: swaying while it waits, dead still once it has decided to strike.
        stillnessO = stillness;
        float goal = now == State.TENSE || now == State.STRIKE ? 1.0f : now == State.LISTEN ? 0.45f : 0.0f;
        stillness += (goal - stillness) * (goal > stillness ? 0.5f : 0.08f);
        Entity target = armTarget();
        if (target != null) {
            lastArmTarget = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        }
    }

    /** Long-arm extension 0 (coiled away) .. 1 (fully out), for this frame. */
    public float armExtension(float partialTick) {
        return Mth.lerp(partialTick, armExtO, armExt);
    }

    /** 0 = bait arms swaying freely, 1 = frozen (the tell before a strike). */
    public float stillness(float partialTick) {
        return Mth.lerp(partialTick, stillnessO, stillness);
    }

    /** The entity the long arm is reaching for or holding, if any (client or server). */
    @Nullable
    public Entity armTarget() {
        int id = entityData.get(PREY_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    /**
     * Where the tip of the long arm should be this frame (world space): the chest of whoever it
     * reaches for, or the last such point while it coils back. Null if it never reached for anyone.
     */
    @Nullable
    public Vec3 armTargetPoint(float partialTick) {
        Entity target = armTarget();
        if (target != null) {
            return target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.55, 0.0);
        }
        return lastArmTarget;
    }

    public State clientState() {
        return clientState;
    }

    public int clientStateTicks() {
        return clientStateTicks;
    }

    /** Which lure animation is playing (0 none, 1 clap, 2 pound) and since which tick. */
    public int lureKind() {
        return lureKind;
    }

    public int lureStart() {
        return lureStart;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CLAP || id == EVENT_POUND) {
            lureKind = id == EVENT_CLAP ? 1 : 2;
            lureStart = tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** The long arm reaches 6 blocks out of the hitbox: keep drawing it when the body is off screen. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(REACH + 1.0);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (anchor == null) {
            anchorAt(isCeiling() ? Hide.CEILING : Hide.ALCOVE, position(), getYRot());
        }
        stateTicks++;
        if (ignoreTicks > 0 && --ignoreTicks == 0) {
            ignored = null;
        }
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (state) {
            case LURK -> tickLurk(senseNow);
            case LISTEN -> tickListen(senseNow);
            case TENSE -> tickTense();
            case STRIKE -> tickStrike();
            case DRAG -> tickDrag();
            case RETRACT -> tickRetract();
        }
        holdPosition();
        if (senseNow && state == State.LURK) {
            tickLonely();
        }
    }

    /**
     * It never walks: every tick it is put back into its hole, plus the "lunge" (how far it has
     * lurched out of the hole, or pulled itself further in). The lunge changes fast: that is its
     * burst of speed.
     */
    private void holdPosition() {
        if (anchor == null) {
            return;
        }
        double step = lungeGoal > lunge ? 0.35 : 0.12;
        lunge = lungeGoal > lunge ? Math.min(lungeGoal, lunge + step) : Math.max(lungeGoal, lunge - step);
        Vec3 at = anchor;
        if (lunge != 0.0) {
            if (isCeiling()) {
                at = at.add(0.0, -lunge, 0.0); // positive lunge = dropping down out of the drain
            } else {
                float yaw = facingYaw() * Mth.DEG_TO_RAD;
                at = at.add(-Mth.sin(yaw) * lunge, 0.0, Mth.cos(yaw) * lunge);
            }
        }
        setDeltaMovement(Vec3.ZERO);
        if (distanceToSqr(at) > 1.0e-5) {
            setPos(at.x, at.y, at.z);
        }
        float yaw = facingYaw();
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        getNavigation().stop();
    }

    private void tickLurk(boolean senseNow) {
        lungeGoal = 0.0;
        if (!senseNow) {
            return;
        }
        Player heard = listen();
        if (heard != null) {
            prey = heard;
            quietTicks = 0;
            lureIn = 20 + random.nextInt(40);
            setState(State.LISTEN);
            playSound(Level39Mobs.CLUMP_CREAK.get(), 0.8f, 0.8f + random.nextFloat() * 0.2f);
        }
    }

    private void tickListen(boolean senseNow) {
        lungeGoal = 0.0;
        if (senseNow) {
            Player heard = listen();
            if (heard != null) {
                prey = heard;
                quietTicks = 0;
                boolean touching = touching(heard);
                if (touching || (inReach(heard) && canReach(heard))) {
                    startTense(touching);
                    return;
                }
            } else {
                quietTicks += SENSE_INTERVAL;
                if (quietTicks >= LISTEN_GIVE_UP) {
                    prey = null;
                    setState(State.LURK);
                    return;
                }
            }
        }
        // Someone is out there but out of reach: lure them closer. Only while it can still hear
        // them, so the clapping always means "it knows roughly where you are".
        if (quietTicks < 60 && --lureIn <= 0) {
            lureIn = 50 + random.nextInt(60);
            boolean clap = random.nextBoolean();
            level().broadcastEntityEvent(this, clap ? EVENT_CLAP : EVENT_POUND);
            playSound(clap ? Level39Mobs.CLUMP_CLAP.get() : Level39Mobs.CLUMP_POUND.get(), 1.8f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    private void startTense(boolean touching) {
        touchStrike = touching;
        setState(State.TENSE);
        playSound(Level39Mobs.CLUMP_CRACK.get(), 1.2f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void tickTense() {
        if (!isPrey(prey)) {
            prey = null;
            setState(State.LURK);
            return;
        }
        // The burst: it lurches out of its hole, ready to strike.
        lungeGoal = isCeiling() ? CEILING_LUNGE : ALCOVE_LUNGE;
        // Freezing and sneaking right now saves you (unless you are touching it).
        if (!touchStrike && !touching(prey) && (noiseRange(prey) <= 0.0 || !inReach(prey))) {
            if (stateTicks >= 2) {
                setState(State.LISTEN);
                quietTicks = 0;
            }
            return;
        }
        if (stateTicks >= (touchStrike ? TENSE_TOUCH_TICKS : TENSE_TICKS)) {
            entityData.set(PREY_ID, prey.getId());
            setState(State.STRIKE);
            playSound(Level39Mobs.CLUMP_STRIKE.get(), 1.6f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    private void tickStrike() {
        if (stateTicks < STRIKE_TICKS) {
            return;
        }
        if (isPrey(prey) && mouth().distanceTo(grabPoint(prey)) <= REACH + 0.6 && canReach(prey)) {
            grab(prey);
        } else {
            retract(); // missed: whatever it reached for got away
        }
    }

    private void grab(Player victim) {
        heldDamage = 0.0f;
        holdTicks = 0;
        atMouthTicks = 0;
        victim.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        playSound(Level39Mobs.CLUMP_GRAB.get(), 1.6f, 0.9f + random.nextFloat() * 0.2f);
        setState(State.DRAG);
    }

    /**
     * Reels the prey in: their velocity is set every tick toward the mouth of the hole (plus
     * enough lift to cancel gravity, so a drain clump hauls you up out of the water). Once you are
     * at the mouth it bites now and then until it lets go.
     */
    private void tickDrag() {
        Player victim = prey;
        if (!isPrey(victim) || victim.isDeadOrDying()) {
            release(false);
            return;
        }
        holdTicks++;
        Vec3 to = holdPoint(victim).subtract(victim.position());
        double distance = to.length();
        if (distance > REACH + 3.0 || holdTicks > MAX_HOLD_TICKS) {
            release(true);
            return;
        }
        double speed = Math.min(PULL_SPEED, distance * 0.5);
        Vec3 pull = distance > 1.0e-3 ? to.scale(speed / distance) : Vec3.ZERO;
        victim.setDeltaMovement(pull.x, pull.y + (victim.isNoGravity() ? 0.0 : 0.08), pull.z);
        victim.hurtMarked = true; // sends the new velocity to the player's client this tick
        victim.resetFallDistance();
        if (distance < 1.2) {
            if (atMouthTicks++ % BITE_INTERVAL == BITE_INTERVAL / 2) {
                victim.hurt(damageSources().mobAttack(this), BITE_DAMAGE);
                playSound(Level39Mobs.CLUMP_GRAB.get(), 1.2f, 0.7f + random.nextFloat() * 0.2f);
            }
        } else if (holdTicks % 16 == 1) {
            playSound(Level39Mobs.CLUMP_DRAG.get(), 1.2f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    /** Where the held prey's feet end up: hanging under the drain, or in front of the alcove clump. */
    private Vec3 holdPoint(Player victim) {
        Vec3 mouth = mouth();
        if (isCeiling()) {
            return mouth.add(0.0, -victim.getBbHeight() * 0.9, 0.0);
        }
        float yaw = facingYaw() * Mth.DEG_TO_RAD;
        return new Vec3(mouth.x - Mth.sin(yaw) * 0.5, getY(), mouth.z + Mth.cos(yaw) * 0.5);
    }

    /**
     * Lets go. {@code shove}: the prey is flung away from the hole (it flinched or gave up), so it
     * lands back in the water or on the rim rather than right under the arms again.
     */
    private void release(boolean shove) {
        Player victim = prey;
        if (shove && victim != null && victim.isAlive()) {
            Vec3 away = victim.position().subtract(mouth()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0e-4) {
                float yaw = facingYaw() * Mth.DEG_TO_RAD;
                away = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            }
            away = away.normalize().scale(0.45);
            victim.setDeltaMovement(away.x, isCeiling() ? -0.1 : 0.25, away.z);
            victim.hurtMarked = true;
            ignored = victim;
            ignoreTicks = IGNORE_AFTER_RELEASE;
        }
        retract();
    }

    private void retract() {
        prey = null;
        entityData.set(PREY_ID, -1);
        setState(State.RETRACT);
    }

    private void tickRetract() {
        if (lungeGoal > 0.0) {
            lungeGoal = 0.0;
        }
        if (stateTicks >= RETRACT_TICKS) {
            lungeGoal = 0.0;
            setState(State.LURK);
        }
    }

    private void setState(State next) {
        state = next;
        stateTicks = 0;
        entityData.set(STATE, (byte) next.ordinal());
    }

    private void tickLonely() {
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), LONELY_RANGE, EntitySelector.NO_SPECTATORS);
        lonelyTicks = near == null ? lonelyTicks + SENSE_INTERVAL : 0; // called every SENSE_INTERVAL ticks
        if (lonelyTicks >= LONELY_DESPAWN_TICKS) {
            discard();
        }
    }

    // ------------------------------------------------------------------ hearing

    /**
     * How far away this player can be heard right now (0 = silent). Sneaking and standing still
     * are silent; walking, splashing and sprinting are progressively louder. Movement is what the
     * player's own client reported, so it is exactly what they are doing.
     */
    public static double noiseRange(Player player) {
        if (player.isShiftKeyDown() || player.isCrouching()) {
            return 0.0;
        }
        Vec3 move = player.getKnownMovement();
        double speed = Math.sqrt(move.x * move.x + move.z * move.z);
        boolean moving = speed > STILL_SPEED || Math.abs(move.y) > 0.2;
        if (!moving) {
            return 0.0;
        }
        if (player.isSprinting() || player.isSwimming()) {
            return HEAR_SPRINT;
        }
        return player.isInWater() ? HEAR_SPLASH : HEAR_WALK;
    }

    /** The nearest player it can hear (or who is touching its bait), or null. */
    @Nullable
    private Player listen() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 mouth = mouth();
        for (Player player : level().players()) {
            if (!isPrey(player) || player == ignored) {
                continue;
            }
            double distance = mouth.distanceTo(grabPoint(player));
            double range = Math.max(noiseRange(player), touching(player) ? TOUCH + 1.0 : 0.0);
            if (distance <= range && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean touching(Player player) {
        return mouth().distanceTo(grabPoint(player)) <= TOUCH || player.getBoundingBox().intersects(getBoundingBox().inflate(0.4));
    }

    private boolean inReach(Player player) {
        return mouth().distanceTo(grabPoint(player)) <= REACH;
    }

    /** A clear line from the mouth of its hole to you: it can't reach through rock. */
    private boolean canReach(Player player) {
        return level().clip(new ClipContext(mouth(), grabPoint(player), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    private static Vec3 grabPoint(Player player) {
        return player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
    }

    private boolean isPrey(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player);
    }

    // ------------------------------------------------------------------ vanilla hooks

    /**
     * Hitting it: while it holds you, enough damage makes it let go. Hit from within reach, it
     * strikes back at once; shot from further away, a drain clump yanks itself up its drain and
     * stays hidden for a while (its other burst of speed).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || level().isClientSide || !isAlive()) {
            return hurt;
        }
        if (state == State.DRAG) {
            heldDamage += amount;
            if (heldDamage >= RELEASE_DAMAGE) {
                release(true);
            }
            return true;
        }
        if (source.getEntity() instanceof Player attacker && isPrey(attacker)
                && (state == State.LURK || state == State.LISTEN)) {
            prey = attacker;
            if (inReach(attacker) && canReach(attacker)) {
                touchStrike = true;
                setState(State.TENSE);
                playSound(Level39Mobs.CLUMP_CRACK.get(), 1.2f, 1.1f);
            } else if (isCeiling()) {
                lungeGoal = -CEILING_WITHDRAW;
                setState(State.RETRACT);
            }
        }
        return true;
    }

    /** Killed: whatever held it up is gone, so a drain clump drops into the water. */
    @Override
    public void die(DamageSource source) {
        setNoGravity(false);
        release(false);
        super.die(source);
    }

    @Override
    public boolean isInWall() {
        return false; // it lives wedged into holes in the rock
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
        // Wedged into the rock: nothing shoves it out of its hole.
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // it handles that itself (tickLonely)
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Level39Mobs.CLUMP_CREAK.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.7f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level39Mobs.CLUMP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level39Mobs.CLUMP_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // It doesn't walk.
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Ceiling", isCeiling());
        tag.putFloat("Facing", facingYaw());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CEILING, tag.getBoolean("Ceiling"));
        entityData.set(FACING, tag.getFloat("Facing"));
        setNoGravity(isCeiling());
    }
}
