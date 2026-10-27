package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hounds (Entity 8), the hunters of Level 39's flooded tunnels.
 *
 * <p>A starved human body that runs on all fours: skin over bone, long limbs, hooked claws, and a
 * curtain of long black hair hiding a mouth far too big for its head. It is hostile the moment it
 * sees you, but it is not a homing missile. The rules a player can learn:
 * <ul>
 *   <li><b>It growls before it attacks.</b> After spotting you it creeps closer and then stops to
 *       growl for a second and a half. That growl is the warning: break line of sight NOW.</li>
 *   <li><b>Stare it down.</b> Look straight at it (within 16 blocks, about 2 seconds) and it
 *       hesitates and backs off for a few seconds. Only a few: it soon comes again, and every time
 *       it takes a longer stare to work.</li>
 *   <li><b>Don't hit it unless you mean it.</b> Hurting it makes it furious for 15 seconds: no more
 *       hesitating, a shorter growl, faster, harder bites.</li>
 *   <li><b>Stay out of the water.</b> Hounds run faster in water than on land, and splashing is
 *       loud: they hear you wading from far off.</li>
 * </ul>
 *
 * <p>The brain is one small state machine ({@link Mode}), run by the server in
 * {@link #customServerAiStep()}, the same way the Bacteria's is:
 * <pre>
 * PROWL --sees you--> STALK --close or 3 s--> GROWL --1.5 s--> CHASE --4.5 blocks--> LUNGE --> CHASE
 * PROWL --hears you--> SEARCH          CHASE/STALK --you stare it down--> COWED --3-4 s--> GROWL
 * CHASE --4 s out of sight / too far--> SEARCH --8 s, nothing--> PROWL
 * </pre>
 * The mode is synced to clients so the model can pose (low creeping stalk, quivering growl with
 * the jaw half open, full gallop, the pounce, the cringe).
 */
public class Hound extends Monster {
    /** What the Hound is doing right now. The order matters: it is synced as a byte. */
    public enum Mode { PROWL, STALK, GROWL, CHASE, LUNGE, COWED, SEARCH }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Hound.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(Hound.class, EntityDataSerializers.BOOLEAN);

    // --- Senses ------------------------------------------------------------------------------
    /** How far it spots you where there is light (the tunnels are evenly lit by their orbs). */
    private static final double SIGHT_LIT = 20.0;
    /** How far it spots you in the dark (the dim middle of town at night). */
    private static final double SIGHT_DARK = 10.0;
    /** Block light at your eyes that counts as "lit". */
    private static final int LIT_LEVEL = 6;
    /** Crouching shrinks the sight range. */
    private static final double CROUCH_FACTOR = 0.6;
    /** It hears sprinting through rock from this far... */
    private static final double HEAR_SPRINT = 12.0;
    /** ...and wading or swimming (the splashing) even further. */
    private static final double HEAR_SPLASH = 14.0;
    private static final double SENSE_RANGE = 40.0;
    private static final int SENSE_INTERVAL = 5;

    // --- Eye contact -------------------------------------------------------------------------
    /** Stares only count within this distance. */
    private static final double STARE_RANGE = 16.0;
    /** How exactly you must look at it: the cosine of the cone around your crosshair (about 13 degrees). */
    private static final double STARE_COS = 0.974;
    /** The first stare-down takes 2 seconds... */
    private static final int STARE_TICKS = 40;
    /** ...and every one after that in the same hunt takes a second longer (it gets used to it). */
    private static final int STARE_TICKS_PER_COW = 20;
    private static final int STARE_TICKS_MAX = 100;
    /** How long it backs off once cowed: 3 to 4.5 seconds. */
    private static final int COWED_MIN_TICKS = 60;
    private static final int COWED_EXTRA_TICKS = 30;
    /** After backing off it can't be stared down again for 10 seconds. */
    private static final int COW_COOLDOWN_TICKS = 200;

    // --- Timings -----------------------------------------------------------------------------
    /** Stalking ends in a growl after this long, or once it is within {@link #GROWL_RANGE}. */
    private static final int STALK_MAX_TICKS = 60;
    private static final double GROWL_RANGE = 11.0;
    /** The growl before an attack: the warning. Shorter once it is furious, and after a stare-down. */
    private static final int GROWL_TICKS = 30;
    private static final int GROWL_TICKS_ENRAGED = 10;
    private static final int GROWL_TICKS_AFTER_COWED = 16;
    /** It keeps chasing this long without seeing you before it starts searching. */
    private static final int LOST_SIGHT_TICKS = 50;
    private static final double CHASE_GIVE_UP = 24.0;
    private static final int SEARCH_TICKS = 100;
    private static final int CHASE_REPATH_TICKS = 8;
    private static final int BITE_COOLDOWN_TICKS = 24;
    private static final int BITE_COOLDOWN_ENRAGED = 16;
    /** Pounces start within this range and need this long to recover. */
    private static final double LUNGE_RANGE = 4.5;
    private static final double LUNGE_MIN_RANGE = 1.8;
    private static final int LUNGE_COOLDOWN_TICKS = 50;
    private static final int LUNGE_MAX_TICKS = 14;
    /** Furious for 30 seconds after being hurt. */
    private static final int ENRAGED_TICKS = 300;

    // --- Speeds (multipliers of MOVEMENT_SPEED) ----------------------------------------------
    // 0.32 at 1.0 is a little over 4 blocks/s: faster than walking, slower than a fresh sprint,
    // so you can outrun it on dry land if you start running at the growl.
    private static final double PROWL_SPEED = 0.45;
    private static final double STALK_SPEED = 0.6;
    private static final double SEARCH_SPEED = 0.8;
    private static final double CHASE_SPEED = 1.0;
    private static final double CHASE_SPEED_ENRAGED = 1.15;
    private static final double COWED_SPEED = 0.7;

    /** In water it gets this much faster (canon: entities swim faster than wanderers). */
    private static final ResourceLocation WATER_BOOST_ID = Backrooms.id("hound_water_boost");
    private static final AttributeModifier WATER_BOOST = new AttributeModifier(WATER_BOOST_ID, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    /** Bites harder while furious. */
    private static final ResourceLocation RAGE_ID = Backrooms.id("hound_rage");
    private static final AttributeModifier RAGE_DAMAGE = new AttributeModifier(RAGE_ID, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    // Server-side brain state. Not saved: Hounds are never saved (the spawner keeps the tunnels stocked).
    private Mode mode = Mode.PROWL;
    private int modeTicks;
    private int modeLength;
    @Nullable
    private Player prey;
    private Vec3 lastSeen = Vec3.ZERO;
    @Nullable
    private Vec3 pathGoal;
    private int unseenTicks;
    private int repathIn;
    private int biteCooldown;
    private int lungeCooldown;
    private boolean bitThisLunge;
    private int stareTicks;
    private int timesCowed;
    private int cowCooldown;
    private int enragedTicks;
    private int searchLegs;

    // Client-only, purely visual: how far the jaw hangs open and how low it crouches (0..1),
    // eased each tick so the model never snaps.
    private float jaw;
    private float jawO;
    private float crouch;
    private float crouchO;

    public Hound(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        // Water is its element: it paths straight through the flooded tunnel floor instead of
        // treating water as something to avoid like most land mobs do.
        setPathfindingMalus(PathType.WATER, 0.0f);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
        getNavigation().setCanFloat(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0)
                // 1.0 = it moves through water as freely as over land (no water drag), before
                // the extra water boost on top.
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.PROWL.ordinal());
        builder.define(ENRAGED, false);
    }

    /** Only the always-on basic is a goal; the hunt itself is the state machine below. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Current mode; works on both server and client (synced). */
    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.PROWL;
    }

    /** True for 30 s after being hurt (synced, so the model can bristle). */
    public boolean isEnraged() {
        return entityData.get(ENRAGED);
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode current = mode();
            float jawTarget = switch (current) {
                case LUNGE -> 1.0f;
                case GROWL -> 0.45f;
                case CHASE -> 0.3f;
                case COWED -> 0.1f;
                default -> 0.0f;
            };
            float crouchTarget = switch (current) {
                case STALK -> 1.0f;
                case GROWL -> 0.8f;
                case COWED -> 0.6f;
                case SEARCH -> 0.35f;
                default -> 0.0f;
            };
            jawO = jaw;
            crouchO = crouch;
            // The jaw snaps open fast for a pounce and closes more slowly.
            jaw += (jawTarget - jaw) * (jawTarget > jaw ? 0.55f : 0.2f);
            crouch += (crouchTarget - crouch) * 0.15f;
        }
    }

    /** How far the jaw is open (0..1) this frame. */
    public float jaw(float partialTick) {
        return Mth.lerp(partialTick, jawO, jaw);
    }

    /** How low it crouches (0..1) this frame. */
    public float crouch(float partialTick) {
        return Mth.lerp(partialTick, crouchO, crouch);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        if (lungeCooldown > 0) {
            lungeCooldown--;
        }
        if (cowCooldown > 0) {
            cowCooldown--;
        }
        tickRage();
        tickWaterBoost();
        if (keepOutOfSafeRooms()) {
            return;
        }
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case PROWL -> tickProwl(senseNow);
            case STALK -> tickStalk();
            case GROWL -> tickGrowl();
            case CHASE -> tickChase();
            case LUNGE -> tickLunge();
            case COWED -> tickCowed();
            case SEARCH -> tickSearch(senseNow);
        }
    }

    /** Faster in water: a speed bonus while it is in water, removed again on land. */
    private void tickWaterBoost() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean wet = isInWater();
        if (wet && !speed.hasModifier(WATER_BOOST_ID)) {
            speed.addTransientModifier(WATER_BOOST);
        } else if (!wet && speed.hasModifier(WATER_BOOST_ID)) {
            speed.removeModifier(WATER_BOOST_ID);
        }
    }

    private void tickRage() {
        if (enragedTicks > 0 && --enragedTicks == 0) {
            entityData.set(ENRAGED, false);
            AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.removeModifier(RAGE_ID);
            }
        }
    }

    /** Safe rooms are off limits, exactly like for the Bacteria. True if it stopped this tick. */
    private boolean keepOutOfSafeRooms() {
        var path = getNavigation().getPath();
        boolean headingIn = path != null && !path.isDone() && SafeRooms.isSafe(level(), path.getNextNodePos());
        boolean inside = SafeRooms.isSafe(this);
        if (!headingIn && !inside) {
            return false;
        }
        getNavigation().stop();
        if (mode != Mode.PROWL) {
            loseInterest();
        }
        return inside;
    }

    private void tickProwl(boolean senseNow) {
        if (senseNow) {
            Player seen = findVisible();
            if (seen != null) {
                startStalk(seen);
                return;
            }
            Player heard = findHeard();
            if (heard != null) {
                lastSeen = heard.position();
                startSearch();
                return;
            }
        }
        // Short restless trots, often stopping to breathe.
        if (getNavigation().isDone() && random.nextInt(80) == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 12, 3);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, PROWL_SPEED);
            }
        }
    }

    private void tickStalk() {
        if (!isHuntable(prey)) {
            loseInterest();
            return;
        }
        if (tickStare()) {
            return;
        }
        boolean visible = getSensing().hasLineOfSight(prey);
        if (visible) {
            lastSeen = prey.position();
            unseenTicks = 0;
        } else if (++unseenTicks > LOST_SIGHT_TICKS / 2) {
            startSearch();
            return;
        }
        getLookControl().setLookAt(prey, 20.0f, 20.0f);
        if (--repathIn <= 0) {
            repathIn = CHASE_REPATH_TICKS * 2;
            getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, STALK_SPEED);
        }
        if (visible && (modeTicks >= STALK_MAX_TICKS || distanceToSqr(prey) < GROWL_RANGE * GROWL_RANGE)) {
            startGrowl(GROWL_TICKS);
        }
    }

    private void tickGrowl() {
        if (!isHuntable(prey)) {
            loseInterest();
            return;
        }
        if (tickStare()) {
            return;
        }
        // Rooted to the spot, head low, staring: the growl is the warning.
        getNavigation().stop();
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (modeTicks >= modeLength) {
            startChase();
        }
    }

    private void tickChase() {
        if (prey != null && prey.isDeadOrDying()) {
            loseInterest();
            return;
        }
        if (!isHuntable(prey) || distanceToSqr(prey) > CHASE_GIVE_UP * CHASE_GIVE_UP) {
            startSearch();
            return;
        }
        if (tickStare()) {
            return;
        }
        if (getSensing().hasLineOfSight(prey)) {
            if (unseenTicks > 0) {
                repathIn = 0;
            }
            unseenTicks = 0;
            lastSeen = prey.position();
            getLookControl().setLookAt(prey, 30.0f, 30.0f);
            double distanceSqr = distanceToSqr(prey);
            if (lungeCooldown <= 0 && distanceSqr < LUNGE_RANGE * LUNGE_RANGE && distanceSqr > LUNGE_MIN_RANGE * LUNGE_MIN_RANGE
                    && (onGround() || isInWater())) {
                startLunge();
                return;
            }
            if (distanceSqr < 9.0) {
                // Close and in plain view: straight at you (a path would stop a block short).
                getNavigation().stop();
                getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), chaseSpeed());
            } else if (--repathIn <= 0) {
                repathIn = CHASE_REPATH_TICKS;
                if (pathGoal == null || pathGoal.distanceToSqr(lastSeen) > 2.25 || getNavigation().isDone()) {
                    getNavigation().moveTo(prey, chaseSpeed());
                    pathGoal = lastSeen;
                }
            }
            tryBite();
            return;
        }
        // Lost sight: run to where it last saw you, then search.
        if (unseenTicks++ == 0) {
            getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, chaseSpeed());
            pathGoal = lastSeen;
        }
        if (unseenTicks >= LOST_SIGHT_TICKS) {
            startSearch();
        }
    }

    /**
     * The pounce: one hard leap at you with the jaw wide open. It can bite once in the air;
     * then it lands, and needs a moment before it can pounce again.
     */
    private void tickLunge() {
        if (prey != null && !bitThisLunge && prey.isAlive()
                && getBoundingBox().inflate(0.45).intersects(prey.getBoundingBox())) {
            bitThisLunge = true;
            bite(prey);
        }
        boolean landed = modeTicks > 3 && (onGround() || isInWater());
        if (landed || modeTicks >= LUNGE_MAX_TICKS) {
            lungeCooldown = LUNGE_COOLDOWN_TICKS;
            if (isHuntable(prey)) {
                setMode(Mode.CHASE);
                repathIn = 0;
                pathGoal = null;
            } else {
                loseInterest();
            }
        }
    }

    /** Backing off with its head low and turned away, still keeping you in the corner of its eye. */
    private void tickCowed() {
        if (!isHuntable(prey)) {
            loseInterest();
            return;
        }
        getLookControl().setLookAt(prey, 10.0f, 10.0f);
        if (modeTicks % 20 == 1) {
            // Retreat a few blocks straight away from you.
            Vec3 away = position().subtract(prey.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0e-4) {
                Vec3 to = position().add(away.normalize().scale(5.0));
                getNavigation().moveTo(to.x, to.y, to.z, COWED_SPEED);
            }
        }
        if (modeTicks >= modeLength) {
            cowCooldown = COW_COOLDOWN_TICKS;
            stareTicks = 0;
            if (getSensing().hasLineOfSight(prey)) {
                startGrowl(GROWL_TICKS_AFTER_COWED);
            } else {
                lastSeen = prey.position();
                startSearch();
            }
        }
    }

    private void tickSearch(boolean senseNow) {
        if (senseNow) {
            Player seen = findVisible();
            if (seen != null) {
                // Found you again: straight into a short growl (it already knows you're here).
                prey = seen;
                setTarget(seen);
                lastSeen = seen.position();
                startGrowl(GROWL_TICKS_AFTER_COWED);
                return;
            }
            Player heard = findHeard();
            if (heard != null && heard.position().distanceToSqr(lastSeen) > 9.0) {
                lastSeen = heard.position();
                searchLegs = 0;
                getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, SEARCH_SPEED);
            }
        }
        if (getNavigation().isDone() && searchLegs < 3) {
            // At the spot: cast about nearby, nose down.
            searchLegs++;
            Vec3 to = DefaultRandomPos.getPos(this, 7, 2);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, SEARCH_SPEED * 0.8);
            }
        }
        if (modeTicks >= SEARCH_TICKS) {
            loseInterest();
        }
    }

    // ------------------------------------------------------------------ eye contact

    /**
     * Counts how long the prey has been looking it in the face. True if that just made it back
     * off (the mode changed this tick). Furious Hounds and pouncing Hounds don't care.
     */
    private boolean tickStare() {
        if (prey == null || enragedTicks > 0 || cowCooldown > 0) {
            stareTicks = 0;
            return false;
        }
        if (isStaredAtBy(prey)) {
            stareTicks++;
        } else {
            stareTicks = Math.max(0, stareTicks - 2);
        }
        int needed = Math.min(STARE_TICKS_MAX, STARE_TICKS + timesCowed * STARE_TICKS_PER_COW);
        if (stareTicks < needed) {
            return false;
        }
        timesCowed++;
        stareTicks = 0;
        setMode(Mode.COWED);
        modeLength = COWED_MIN_TICKS + random.nextInt(COWED_EXTRA_TICKS);
        getNavigation().stop();
        playSound(Level39Mobs.HOUND_WHINE.get(), 1.2f, 0.9f + random.nextFloat() * 0.2f);
        return true;
    }

    /** True if {@code player} is looking straight at its head from within 16 blocks, with a clear view. */
    public boolean isStaredAtBy(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 head = getEyePosition();
        Vec3 to = head.subtract(eye);
        double distance = to.length();
        if (distance > STARE_RANGE || distance < 1.0e-3) {
            return false;
        }
        double dot = player.getViewVector(1.0f).dot(to.scale(1.0 / distance));
        // Close up, the head fills more of your view, so the cone widens a little.
        double needed = Math.min(STARE_COS, 1.0 - 0.6 / (distance * distance + 10.0));
        return dot > needed && player.hasLineOfSight(this);
    }

    // ------------------------------------------------------------------ transitions

    private void startStalk(Player player) {
        prey = player;
        setTarget(player);
        lastSeen = player.position();
        unseenTicks = 0;
        repathIn = 0;
        timesCowed = 0;
        stareTicks = 0;
        setMode(Mode.STALK);
        // A wet, ragged intake of breath as it notices you.
        playSound(Level39Mobs.HOUND_BREATH.get(), 1.4f, 0.8f + random.nextFloat() * 0.15f);
    }

    private void startGrowl(int ticks) {
        setMode(Mode.GROWL);
        modeLength = enragedTicks > 0 ? Math.min(ticks, GROWL_TICKS_ENRAGED) : ticks;
        getNavigation().stop();
        // Loud on purpose: it carries down the tunnel so you know one is about to come.
        playSound(Level39Mobs.HOUND_GROWL.get(), 2.5f, 0.85f + random.nextFloat() * 0.2f);
    }

    private void startChase() {
        setMode(Mode.CHASE);
        repathIn = 0;
        pathGoal = null;
        unseenTicks = 0;
    }

    private void startLunge() {
        setMode(Mode.LUNGE);
        bitThisLunge = false;
        getNavigation().stop();
        Vec3 to = prey.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize();
        double power = isInWater() ? 0.55 : 0.8;
        setDeltaMovement(flat.x * power, isInWater() ? 0.15 : 0.36, flat.z * power);
        hasImpulse = true;
        // Face the leap so the model flies head first.
        float yaw = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        playSound(Level39Mobs.HOUND_SNARL.get(), 2.0f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void startSearch() {
        setMode(Mode.SEARCH);
        searchLegs = 0;
        getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, SEARCH_SPEED);
    }

    private void loseInterest() {
        prey = null;
        setTarget(null);
        getNavigation().stop();
        stareTicks = 0;
        setMode(Mode.PROWL);
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private double chaseSpeed() {
        return enragedTicks > 0 ? CHASE_SPEED_ENRAGED : CHASE_SPEED;
    }

    private void tryBite() {
        if (biteCooldown <= 0 && prey != null && isWithinMeleeAttackRange(prey)) {
            bite(prey);
        }
    }

    private void bite(Player target) {
        biteCooldown = enragedTicks > 0 ? BITE_COOLDOWN_ENRAGED : BITE_COOLDOWN_TICKS;
        swing(InteractionHand.MAIN_HAND);
        if (doHurtTarget(target)) {
            playSound(Level39Mobs.HOUND_BITE.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    // ------------------------------------------------------------------ senses

    @Nullable
    private Player findVisible() {
        Player best = null;
        double bestDistance = SENSE_RANGE * SENSE_RANGE;
        for (Player player : level().players()) {
            if (!isHuntable(player)) {
                continue;
            }
            double distance = distanceToSqr(player);
            if (distance < bestDistance && canSpot(player, distance)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Sprinting, or moving through water (splashing), carries through the tunnels. */
    @Nullable
    private Player findHeard() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : level().players()) {
            if (!isHuntable(player) || player.isCrouching()) {
                continue;
            }
            double range;
            if (player.isInWater() && player.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
                range = HEAR_SPLASH;
            } else if (player.isSprinting()) {
                range = HEAR_SPRINT;
            } else {
                continue;
            }
            double distance = distanceToSqr(player);
            if (distance < range * range && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean canSpot(Player player, double distanceSqr) {
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        double range = level().getBrightness(LightLayer.BLOCK, eyes) >= LIT_LEVEL ? SIGHT_LIT : SIGHT_DARK;
        if (player.isCrouching()) {
            range *= CROUCH_FACTOR;
        }
        return distanceSqr <= range * range && getSensing().hasLineOfSight(player);
    }

    private boolean isHuntable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player);
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Hurting it makes it furious: no more hesitating, faster, harder bites, a much shorter growl. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            if (enragedTicks <= 0) {
                AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
                if (damage != null && !damage.hasModifier(RAGE_ID)) {
                    damage.addTransientModifier(RAGE_DAMAGE);
                }
            }
            enragedTicks = ENRAGED_TICKS;
            entityData.set(ENRAGED, true);
            Entity attacker = source.getEntity();
            if (attacker instanceof Player player && isHuntable(player)) {
                prey = player;
                setTarget(player);
                lastSeen = player.position();
                if (mode == Mode.PROWL || mode == Mode.SEARCH || mode == Mode.STALK || mode == Mode.COWED) {
                    startGrowl(GROWL_TICKS_ENRAGED);
                }
            }
        }
        return hurt;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        // The growl is played on purpose before attacks; ambient is only the breathing.
        return mode == Mode.GROWL || mode == Mode.LUNGE ? null : Level39Mobs.HOUND_BREATH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level39Mobs.HOUND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level39Mobs.HOUND_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        boolean running = mode == Mode.CHASE || mode == Mode.LUNGE;
        playSound(Level39Mobs.HOUND_STEP.get(), running ? 0.7f : 0.3f, 0.9f + random.nextFloat() * 0.25f);
    }

    /** Running through the tunnel water: heavy, fast splashing instead of a swimmer's paddle. */
    @Override
    protected SoundEvent getSwimSound() {
        return Level39Mobs.HOUND_SPLASH.get();
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return Level39Mobs.HOUND_SPLASH.get();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
