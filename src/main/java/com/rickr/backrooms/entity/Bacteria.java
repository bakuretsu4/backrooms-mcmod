package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Bacteria (a.k.a. "the Lifeform") from Kane Pixels' Backrooms.
 *
 * <p>It is a hunter with real senses, not a homing missile. It only knows where you are if it
 * sees you (in the light from 24 blocks, in the dark only from 8) or hears you sprint. So the
 * way to survive is the same as in the footage: break line of sight, get somewhere dark, keep
 * still or creep, and it loses you.
 *
 * <p>The behaviour is a small state machine ({@link Mode}) run by the server in
 * {@link #customServerAiStep()}:
 * <pre>
 * WANDER  --sees you-->           SPOTTED --15 ticks--> CHASE --3 s without seeing you--> SEARCH
 * WANDER  --hears a sprint / every 30-60 s a rough guess--> DRIFT --arrives--> WANDER
 * SEARCH  --sees or hears you--> CHASE      SEARCH --8 s, nothing--> WANDER ("lost" sigh)
 * </pre>
 * The mode is synced to clients so the model can pose and the camcorder can glitch.
 *
 * <p>Safe rooms ({@code world/SafeRooms}) are off limits: it never walks into one, and it can't
 * see or hear anyone inside. Reaching a safe room in a chase counts as escaping.
 */
public class Bacteria extends Monster {
    /** What the Bacteria is doing right now. The order matters: it is synced as a byte. */
    public enum Mode { WANDER, DRIFT, SPOTTED, CHASE, SEARCH }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.BYTE);

    // --- Senses ------------------------------------------------------------------------------
    /** How far it can spot you when you stand in the light (lamps on, no blackout). */
    private static final double SIGHT_LIT = 24.0;
    /** How far it can spot you in the dark. Your flashlight does not change this. */
    private static final double SIGHT_DARK = 8.0;
    /** Crouching shrinks both sight ranges. */
    private static final double CROUCH_FACTOR = 0.6;
    /** Block light at your eyes needed to count as "in the light". Ceiling lamps give 12. */
    private static final int LIT_LEVEL = 7;
    /** Sprinting is loud: it hears it through walls from this far... */
    private static final double HEAR_SPRINT = 16.0;
    /** ...and a bit less far while it is busy searching. */
    private static final double HEAR_SPRINT_SEARCHING = 12.0;
    /** Nobody is checked beyond this, which keeps the sense loop cheap. */
    private static final double SENSE_RANGE = 48.0;
    /** The senses run every this many ticks, not every tick, to keep big servers light. */
    private static final int SENSE_INTERVAL = 5;

    // --- Timings (ticks; 20 ticks = 1 second) -----------------------------------------------
    /** The freeze between spotting you and running at you: your warning. */
    private static final int SPOTTED_TICKS = 15;
    /** How long it keeps running to where it last saw you before it starts searching. */
    private static final int LOST_SIGHT_TICKS = 60;
    /** How long it searches before giving up. */
    private static final int SEARCH_TICKS = 160;
    /** How often the search sniffing plays. */
    private static final int SEARCH_SOUND_TICKS = 40;
    /** A drift that takes longer than this is abandoned. */
    private static final int DRIFT_MAX_TICKS = 600;
    /** Every 30-60 s while wandering it takes a rough guess at where someone is. */
    private static final int GUESS_MIN_TICKS = 600;
    private static final int GUESS_MAX_TICKS = 1200;
    private static final double GUESS_RANGE = 64.0;
    /** The guess is off by up to this many blocks, so it never walks straight to you. */
    private static final double GUESS_ERROR = 8.0;
    /** Path updates while chasing: at most twice a second. Pathfinding is the expensive part. */
    private static final int CHASE_REPATH_TICKS = 10;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    /** Chasing someone further than this makes it give up and search instead. */
    private static final double CHASE_GIVE_UP = 48.0;
    /** Within this many blocks (and in sight) it stops pathfinding and goes straight for you. */
    private static final double LUNGE_RANGE = 3.0;
    /** How many blocks up the headroom check looks; more than this counts as "plenty". */
    private static final float HEADROOM_CHECK = 5.0f;
    /** With nobody within {@link #LONELY_RANGE} for 3 minutes, it slips away when unseen. */
    private static final int LONELY_DESPAWN_TICKS = 3 * 60 * 20;
    private static final double LONELY_RANGE = 32.0;

    // --- Speeds (multipliers of MOVEMENT_SPEED) ----------------------------------------------
    // Mob speed works out to roughly (attribute x multiplier)^2, so 0.31 at 1.0 is about 4.2
    // blocks/s: faster than your Backrooms walk (3.5), slower than your sprint (5.6).
    private static final double CHASE_SPEED = 1.0;
    private static final double SEARCH_SPEED = 0.75;
    private static final double DRIFT_SPEED = 0.6;
    private static final double WANDER_SPEED = 0.45;

    // Server-side brain state. Not saved: after a reload it simply starts wandering again.
    private Mode mode = Mode.WANDER;
    private int modeTicks;
    @Nullable
    private Player prey;
    private Vec3 lastSeen = Vec3.ZERO;
    @Nullable
    private Vec3 pathGoal;
    private int unseenTicks;
    private int repathIn;
    private int attackCooldown;
    private int searchLegs;
    private int nextGuessIn = GUESS_MIN_TICKS;
    private int lonelyTicks;

    // Client-only, purely visual: free space above it (blocks), smoothed, for the model's stoop.
    private float headroom = HEADROOM_CHECK;
    private float headroomO = HEADROOM_CHECK;
    private boolean headroomMeasured;
    // Client-only, purely visual: heartbeat and breathing, counted in beats/breaths so far. Kept
    // here (not worked out from the clock) so the rhythm speeds up smoothly when the mode changes.
    private float heartPhase;
    private float heartPhaseO;
    private float breathPhase;
    private float breathPhaseO;

    public Bacteria(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.WANDER.ordinal());
    }

    /**
     * Only the "always on" basics are goals. Everything that decides where it goes lives in
     * {@link #customServerAiStep()}, because the modes hand over to each other and that is much
     * easier to follow as one state machine than as competing goals.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Current mode; works on both server and client (synced). */
    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.WANDER;
    }

    public boolean isChasing() {
        return mode() == Mode.CHASE;
    }

    /**
     * How strongly this Bacteria glitches the camcorder, before distance is applied. The freeze
     * before a chase counts as full danger: that spike is part of the warning.
     */
    public float dangerWeight() {
        return switch (mode()) {
            case SPOTTED, CHASE -> 1.0f;
            case SEARCH -> 0.75f;
            case WANDER, DRIFT -> 0.55f;
        };
    }

    // ------------------------------------------------------------------ headroom (client visual)

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            float measured = measureHeadroom();
            headroomO = headroomMeasured ? headroom : measured;
            // Ease toward the new value so it ducks smoothly rather than snapping.
            headroom = headroomMeasured ? headroom + (measured - headroom) * 0.3f : measured;
            headroomMeasured = true;
            tickOrgans();
        }
    }

    /**
     * The heart races once it has seen you (one beat every 7 ticks while frozen, 9 while
     * chasing), still runs fast while it searches, and slows to about one beat a second while it
     * wanders. Breathing follows the same mood. You can see this through the wires when you
     * shine a light on it.
     */
    private void tickOrgans() {
        Mode current = mode();
        float beatTicks = switch (current) {
            case SPOTTED -> 7.0f;
            case CHASE -> 9.0f;
            case SEARCH -> 12.0f;
            case WANDER, DRIFT -> 22.0f;
        };
        float breathTicks = switch (current) {
            case SPOTTED, CHASE -> 18.0f;
            case SEARCH -> 30.0f;
            case WANDER, DRIFT -> 70.0f;
        };
        heartPhaseO = heartPhase;
        breathPhaseO = breathPhase;
        heartPhase += 1.0f / beatTicks;
        breathPhase += 1.0f / breathTicks;
        // Only the fraction matters; keep the numbers small so floats stay precise.
        if (heartPhase > 1000.0f) {
            heartPhase -= 1000.0f;
            heartPhaseO -= 1000.0f;
        }
        if (breathPhase > 1000.0f) {
            breathPhase -= 1000.0f;
            breathPhaseO -= 1000.0f;
        }
    }

    /** Heartbeats so far, interpolated for this frame (only the fraction matters). */
    public float heartPhase(float partialTick) {
        return Mth.lerp(partialTick, heartPhaseO, heartPhase);
    }

    /** Breaths so far, interpolated for this frame (only the fraction matters). */
    public float breathPhase(float partialTick) {
        return Mth.lerp(partialTick, breathPhaseO, breathPhase);
    }

    /**
     * Free height above its feet in blocks (up to {@link #HEADROOM_CHECK}), interpolated for this
     * frame. The model stands about 3.4 blocks tall but the hitbox is 2.9 so it fits through the
     * 3-block doorways; the model uses this to stoop under them instead of poking through.
     */
    public float headroom(float partialTick) {
        return Mth.lerp(partialTick, headroomO, headroom);
    }

    /**
     * Checks the column it stands in and the one about a block ahead, so it already ducks as it
     * walks up to a doorway, not only once its head is inside the lintel.
     */
    private float measureHeadroom() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        BlockPos feet = BlockPos.containing(getX(), getY() + 0.01, getZ());
        BlockPos ahead = BlockPos.containing(getX() - Mth.sin(yaw) * 1.2, getY() + 0.01, getZ() + Mth.cos(yaw) * 1.2);
        int free = Math.min(freeAbove(feet), freeAbove(ahead));
        return (float) (free - (getY() - feet.getY()));
    }

    private int freeAbove(BlockPos feet) {
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int i = 0; i < (int) HEADROOM_CHECK; i++) {
            pos.setY(feet.getY() + i);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return i;
            }
        }
        return (int) HEADROOM_CHECK;
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (keepOutOfSafeRooms()) {
            return;
        }
        // Spread the sense checks of many Bacteria over different ticks.
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case WANDER, DRIFT -> tickCalm(senseNow);
            case SPOTTED -> tickSpotted();
            case CHASE -> tickChase();
            case SEARCH -> tickSearch(senseNow);
        }
    }

    /**
     * Safe rooms are off limits: if the next step of its path (or the spot it stands on) is
     * inside one, it stops at the door. A hunt ends there as if it lost you (the "lost" sigh
     * tells you you made it). True if it stopped this tick.
     */
    private boolean keepOutOfSafeRooms() {
        Path path = getNavigation().getPath();
        boolean headingIn = path != null && !path.isDone() && SafeRooms.isSafe(level(), path.getNextNodePos());
        boolean inside = SafeRooms.isSafe(this);
        if (!headingIn && !inside) {
            return false;
        }
        getNavigation().stop();
        if (mode == Mode.SPOTTED || mode == Mode.CHASE || mode == Mode.SEARCH) {
            giveUp(true);
        } else if (mode == Mode.DRIFT) {
            setMode(Mode.WANDER);
        }
        if (inside && !anyPlayerCanSee()) {
            discard(); // somehow got in (e.g. pushed): it slips away unseen
            return true;
        }
        return inside;
    }

    private void tickCalm(boolean senseNow) {
        if (senseNow) {
            Player seen = findVisible();
            if (seen != null) {
                spot(seen);
                return;
            }
            Player heard = findHeard(HEAR_SPRINT);
            if (heard != null) {
                drift(heard.position(), SEARCH_SPEED);
            }
            if (tickLonely()) {
                return;
            }
        }

        if (mode == Mode.DRIFT) {
            if (getNavigation().isDone() || modeTicks > DRIFT_MAX_TICKS) {
                setMode(Mode.WANDER);
            }
            return;
        }

        // WANDER: now and then a rough guess at where someone is, otherwise short strolls.
        if (--nextGuessIn <= 0) {
            nextGuessIn = GUESS_MIN_TICKS + random.nextInt(GUESS_MAX_TICKS - GUESS_MIN_TICKS);
            // (Never someone hiding in a safe room: it doesn't know they're there.)
            Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), GUESS_RANGE,
                    EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(entity -> !SafeRooms.isSafe(entity)));
            if (nearest != null) {
                double ox = (random.nextDouble() * 2.0 - 1.0) * GUESS_ERROR;
                double oz = (random.nextDouble() * 2.0 - 1.0) * GUESS_ERROR;
                drift(nearest.position().add(ox, 0.0, oz), DRIFT_SPEED);
                return;
            }
        }
        if (getNavigation().isDone() && random.nextInt(120) == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 10, 3);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED);
            }
        }
    }

    private void tickSpotted() {
        if (!isHuntable(prey)) {
            giveUp(false);
            return;
        }
        // Frozen, staring: this pause (plus the start of its long inhale) is the player's warning.
        getNavigation().stop();
        getLookControl().setLookAt(prey, 180.0f, 180.0f);
        if (modeTicks >= SPOTTED_TICKS) {
            setMode(Mode.CHASE);
            repathIn = 0;
            pathGoal = null;
        }
    }

    private void tickChase() {
        if (prey != null && prey.isDeadOrDying()) {
            // Got them. No "lost" sigh: that sound must only ever mean "you escaped".
            giveUp(false);
            return;
        }
        if (prey != null && SafeRooms.isSafe(prey)) {
            // You made it into a safe room: it gives up at once ("lost" sigh, quiet minute).
            giveUp(true);
            return;
        }
        if (!isHuntable(prey) || distanceToSqr(prey) > CHASE_GIVE_UP * CHASE_GIVE_UP) {
            startSearch();
            return;
        }
        if (getSensing().hasLineOfSight(prey)) {
            if (unseenTicks > 0) {
                repathIn = 0; // just got eyes on you again: react now
            }
            unseenTicks = 0;
            lastSeen = prey.position();
            getLookControl().setLookAt(prey, 30.0f, 30.0f);
            if (distanceToSqr(prey) < LUNGE_RANGE * LUNGE_RANGE) {
                // Close and in plain view: walk straight at you. A path would stop a block short
                // (paths end next to the target's block), leaving it hovering just out of reach.
                getNavigation().stop();
                getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), CHASE_SPEED);
                repathIn = 0;
            } else if (--repathIn <= 0) {
                // Re-path at most twice a second, and only if you actually moved.
                repathIn = CHASE_REPATH_TICKS;
                if (pathGoal == null || pathGoal.distanceToSqr(lastSeen) > 2.25 || getNavigation().isDone()) {
                    getNavigation().moveTo(prey, CHASE_SPEED);
                    pathGoal = lastSeen;
                }
            }
            if (attackCooldown <= 0 && isWithinMeleeAttackRange(prey)) {
                attackCooldown = ATTACK_COOLDOWN_TICKS;
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(prey);
            }
            return;
        }
        // Can't see you: run to where you were LAST seen (not where you really are now).
        if (unseenTicks++ == 0) {
            getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, CHASE_SPEED);
            pathGoal = lastSeen;
        }
        if (unseenTicks >= LOST_SIGHT_TICKS) {
            startSearch();
        }
    }

    private void tickSearch(boolean senseNow) {
        if (senseNow) {
            Player found = findVisible();
            if (found == null) {
                found = findHeard(HEAR_SPRINT_SEARCHING);
            }
            if (found != null) {
                reacquire(found);
                return;
            }
        }
        // Quiet sniffing while it looks for you: you can hear that it is close.
        if (modeTicks % SEARCH_SOUND_TICKS == 1) {
            playSound(ModSounds.BACTERIA_SEARCH.get(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
        }
        if (getNavigation().isDone() && searchLegs < 2) {
            // After reaching the last-seen spot, check two random spots close by.
            searchLegs++;
            Vec3 to = DefaultRandomPos.getPos(this, 6, 2);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, SEARCH_SPEED);
            }
        }
        if (modeTicks >= SEARCH_TICKS) {
            giveUp(true);
        }
    }

    // ------------------------------------------------------------------ transitions

    private void spot(Player player) {
        prey = player;
        lastSeen = player.position();
        unseenTicks = 0;
        setTarget(player);
        getNavigation().stop();
        setMode(Mode.SPOTTED);
        playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
    }

    /** Found you again while searching: no freeze this time, but it still breathes in (the tell). */
    private void reacquire(Player player) {
        prey = player;
        lastSeen = player.position();
        unseenTicks = 0;
        repathIn = 0;
        pathGoal = null;
        setTarget(player);
        setMode(Mode.CHASE);
        playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void startSearch() {
        setMode(Mode.SEARCH);
        searchLegs = 0;
        getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, SEARCH_SPEED);
    }

    /** Back to wandering. {@code escaped}: someone got away, so play the "lost" sigh and give them a quiet minute. */
    private void giveUp(boolean escaped) {
        prey = null;
        setTarget(null);
        getNavigation().stop();
        setMode(Mode.WANDER);
        // No guessing for at least 30 s after losing someone.
        nextGuessIn = GUESS_MIN_TICKS + random.nextInt(GUESS_MAX_TICKS - GUESS_MIN_TICKS);
        if (escaped) {
            // The "lost" sigh tells players they made it.
            playSound(ModSounds.BACTERIA_LOST.get(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
            if (level() instanceof ServerLevel serverLevel) {
                BacteriaSpawner.quietAround(serverLevel, position());
            }
        }
    }

    private void drift(Vec3 to, double speed) {
        // A new noise right next to the current goal: keep the current path (no re-pathing).
        if (mode == Mode.DRIFT && pathGoal != null && pathGoal.distanceToSqr(to) < 9.0) {
            return;
        }
        setMode(Mode.DRIFT);
        pathGoal = to;
        getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    /** Ticks the "nobody around" timer. True if the Bacteria despawned. */
    private boolean tickLonely() {
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), LONELY_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        lonelyTicks = near == null ? lonelyTicks + SENSE_INTERVAL : 0;
        if (lonelyTicks >= LONELY_DESPAWN_TICKS && !anyPlayerCanSee()) {
            discard(); // never vanish in front of someone
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ senses

    /** Closest player it can actually spot right now, or null. */
    @Nullable
    private Player findVisible() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : level().players()) {
            if (!isHuntable(player)) {
                continue;
            }
            double distance = distanceToSqr(player);
            if (distance < bestDistance && distance < SENSE_RANGE * SENSE_RANGE && canSpot(player, distance)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Closest sprinting player within {@code range}, heard through walls. */
    @Nullable
    private Player findHeard(double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : level().players()) {
            if (isHuntable(player) && player.isSprinting()) {
                double distance = distanceToSqr(player);
                if (distance < bestDistance) {
                    best = player;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * Light decides how far it sees you, the flashlight does not: in a lit room you are exposed,
     * in the dark you are nearly invisible. The cheap distance test runs before the raycast.
     */
    private boolean canSpot(Player player, double distanceSqr) {
        double range = isLit(player) ? SIGHT_LIT : SIGHT_DARK;
        if (player.isCrouching()) {
            range *= CROUCH_FACTOR;
        }
        return distanceSqr <= range * range && getSensing().hasLineOfSight(player);
    }

    /**
     * "In the light" = the lamps light the spot where your head is. A blackout turns every lamp
     * off for this purpose, even though the light blocks themselves stay in the world.
     */
    private boolean isLit(Player player) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level().dimension());
        if (floor != null && LevelEvents.isBlackout(floor)) {
            return false;
        }
        return level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) >= LIT_LEVEL;
    }

    /** Someone it may notice and chase. Players inside a safe room are out of its reach: it can't see or hear them. */
    private boolean isHuntable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player);
    }

    private boolean anyPlayerCanSee() {
        for (Player player : level().players()) {
            if (!player.isSpectator() && player.distanceToSqr(this) < 64.0 * 64.0 && player.hasLineOfSight(this)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Hitting it is the surest way to get its attention. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof Player attacker && isHuntable(attacker)) {
            if (mode == Mode.SEARCH) {
                reacquire(attacker);
            } else if (mode == Mode.WANDER || mode == Mode.DRIFT) {
                spot(attacker);
            }
        }
        return hurt;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BACTERIA_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BACTERIA_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BACTERIA_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.BACTERIA_STEP.get(), isChasing() ? 0.9f : 0.4f, 0.9f + random.nextFloat() * 0.25f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
