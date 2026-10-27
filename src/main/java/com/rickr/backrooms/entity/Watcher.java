package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraData;
import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.LevelLayout;
import com.rickr.backrooms.world.SafeRooms;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A member of the Neighborhood Watch (Level 9): a car-sized floating eyeball that glides silently
 * over the streets, sweeping a dim searchlight from its pupil. Whoever it catches in the light for
 * too long is turned to dust.
 *
 * <p>State machine (server side, {@link #customServerAiStep()}):
 * <pre>
 * PATROL ──hears sprinting / sees a flashlight──> INVESTIGATE ──nothing found──> PATROL
 *   │                                                │
 *   └──player inside the vision cone──> STARE <──────┘   (SEARCH can also spot you again)
 *                                         │ 1 s in sight (it turns, the pupil narrows)
 *                                         v
 *                                       CHARGE ──2.5 s in sight──> you turn to dust, back to PATROL
 *        line of sight broken (STARE or CHARGE) ──> SEARCH the last seen spot ──12 s──> PATROL
 * </pre>
 * It always flies at "cruise altitude" (about 9 blocks above the road), only over open air: it never
 * flies into houses or trees, and patrols by following road columns.
 *
 * <p>It neither sees nor hears anyone hiding in furniture ({@code player/HidingService}), unless it
 * saw them get in: then it keeps them in its light as if they were standing in the open.
 */
public class Watcher extends FlyingMob implements HidingService.Seeker {
    // ---- Tweakables ------------------------------------------------------------------------
    /** How far it can see you, in blocks. */
    public static final double VISION_RANGE = 28.0;
    /** ...and how much further when your flashlight is on (it is drawn to electronics). */
    public static final double FLASHLIGHT_VISION_MULTIPLIER = 2.0;
    /** Half the opening angle of its vision cone (and its searchlight): 25 degrees = a 50-degree cone. */
    public static final float VISION_HALF_ANGLE = 25.0f;
    private static final double VISION_COS = Math.cos(Math.toRadians(VISION_HALF_ANGLE));
    /** Sprinting players within this range are heard (the "keep quiet" notices are serious). */
    public static final double HEARING_RANGE = 20.0;
    /** A switched-on flashlight within this range draws it in too. */
    public static final double ELECTRONICS_RANGE = 16.0;
    /** Electronics within this range go haywire: camcorder static, flashlight stutter, battery drain. */
    public static final double INTERFERENCE_RANGE = 24.0;
    /** Battery drains this many times faster within {@link #INTERFERENCE_RANGE}. */
    public static final float BATTERY_DRAIN_MULTIPLIER = 3.0f;
    /** Ticks of continuous sight while staring before the beam starts charging (1 s). */
    public static final int STARE_TICKS = 20;
    /** Ticks the beam charges before you turn to dust (2.5 s, the length of the beam sound). */
    public static final int CHARGE_TICKS = 50;
    /** Out of sight this many ticks in a row = it lost you (a short grace, so a lamp post doesn't save you). */
    private static final int LOST_SIGHT_GRACE = 6;
    /** How long it searches the last place it saw you. */
    private static final int SEARCH_TICKS = 12 * 20;
    /** How long it hovers and looks around a noise it came to check. */
    private static final int INVESTIGATE_LINGER = 8 * 20;
    /** Gives up walking to a noise after this long (blocked route). */
    private static final int INVESTIGATE_TIMEOUT = 20 * 20;
    /** Don't play the "spotted" sting more often than this. */
    private static final int SPOT_SOUND_COOLDOWN = 60;

    /** The eye is this many blocks across: car-sized, so it still reads as huge 15-25 blocks away. */
    public static final float SIZE = 3.8f;
    /** Half its size: the eye's centre is this far above its position (and its radius). */
    public static final float HALF_SIZE = SIZE / 2.0f;
    /** Bottom of its hitbox while gliding: eye centre ~9 blocks above the road surface (y41). */
    public static final double CRUISE_Y = LevelLayout.FLOOR_Y + 1 + 9.0 - HALF_SIZE;
    /** Anything taller than this in a column (roofs, trees) blocks its way. Lamp heads (~y48) don't. */
    private static final int OPEN_AIR_MAX_HEIGHT = (int) CRUISE_Y + 1;

    // Speeds in blocks per tick: slow and silent.
    private static final double PATROL_SPEED = 0.07;
    private static final double INVESTIGATE_SPEED = 0.12;
    private static final double SEARCH_SPEED = 0.09;
    /** Degrees per tick it can turn while patrolling / while locked on to someone. */
    private static final float TURN_SPEED = 3.0f;
    private static final float STARE_TURN_SPEED = 7.0f;

    /** Damage type of being turned to dust: data/backrooms/damage_type/dust.json. */
    public static final ResourceKey<DamageType> DUST_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("dust"));

    // ---- Synced to clients (renderer, camcorder static) -----------------------------------
    public enum State { PATROL, INVESTIGATE, STARE, CHARGE, SEARCH }

    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(Watcher.class, EntityDataSerializers.BYTE);
    /** Network id of the player it's locked on to, or -1. */
    private static final EntityDataAccessor<Integer> TARGET =
            SynchedEntityData.defineId(Watcher.class, EntityDataSerializers.INT);
    /** 0..1 how far the beam has charged. */
    private static final EntityDataAccessor<Float> CHARGE =
            SynchedEntityData.defineId(Watcher.class, EntityDataSerializers.FLOAT);

    // ---- Server-only state -----------------------------------------------------------------
    private State state = State.PATROL;
    private int stateTicks;
    @Nullable
    private UUID targetId;
    private int lostTicks;
    private int chargeTicks;
    private int lastSpotSoundTick = -SPOT_SOUND_COOLDOWN;
    /** Where it's flying to (always at cruise altitude), or null to hover. */
    @Nullable
    private Vec3 goal;
    /** Where it heard/saw something (INVESTIGATE / SEARCH). */
    @Nullable
    private Vec3 pointOfInterest;
    private boolean arrived;
    /** Ticks since it reached its goal (0 while still on the way). */
    private int arrivedTicks;
    /** Patrol direction in radians (x = cos, z = sin). */
    private double heading;
    private float yaw;
    private float pitch;

    /**
     * Client only: the pupil size the renderer showed last frame (radians, -1 = not drawn yet). Kept
     * on the entity so the pupil eases between sizes instead of snapping when the state changes.
     */
    public float renderPupil = -1.0f;
    public long renderPupilNanos;

    public Watcher(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
        noPhysics = true;
        heading = random.nextDouble() * Math.PI * 2.0;
        yaw = getYRot();
        // We aim the eye ourselves every tick; vanilla's look control would pull it back level.
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, (byte) State.PATROL.ordinal());
        builder.define(TARGET, -1);
        builder.define(CHARGE, 0.0f);
    }

    // ---- Read by the client ----------------------------------------------------------------

    public State state() {
        byte i = entityData.get(STATE);
        State[] all = State.values();
        return i >= 0 && i < all.length ? all[i] : State.PATROL;
    }

    /** The entity it's staring at / charging at (client or server), or null. */
    @Nullable
    public Entity lockedTarget() {
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    public int lockedTargetId() {
        return entityData.get(TARGET);
    }

    /** 0..1 beam charge. */
    public float charge() {
        return entityData.get(CHARGE);
    }

    /** Centre of the eye. */
    public Vec3 eyeCentre() {
        return position().add(0.0, HALF_SIZE, 0.0);
    }

    // ---- A floating, untouchable eye --------------------------------------------------------

    @Override
    public void tick() {
        noPhysics = true; // glides through lamp arms and branch tips instead of snagging on them
        super.tick();
    }

    /** No vanilla flight physics: the AI sets the exact velocity each tick. */
    @Override
    public void travel(Vec3 input) {
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // The Neighborhood Watch can't be fought; only an operator's /kill removes one.
        return source.is(DamageTypes.GENERIC_KILL) && super.hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
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
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /**
     * Vanilla removes monsters at random once they're 32+ blocks from everyone; a Watcher popping out
     * of existence mid-street would look wrong, so only far-away ones (> 96 blocks) may go.
     * (The argument is the squared distance to the nearest player.)
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSq) {
        return distanceToClosestPlayerSq > 96.0 * 96.0;
    }

    // ---- Electronics interference (server side, used by CameraService) ----------------------

    /** True if a Watcher is close enough to {@code player} to mess with their camcorder. */
    public static boolean interferesWith(ServerPlayer player) {
        double r = INTERFERENCE_RANGE;
        return !player.level().getEntitiesOfClass(Watcher.class, player.getBoundingBox().inflate(r),
                watcher -> watcher.eyeCentre().distanceToSqr(player.getEyePosition()) <= r * r).isEmpty();
    }

    // ---- The behaviour ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        stateTicks++;
        switch (state) {
            case PATROL -> tickPatrol(serverLevel);
            case INVESTIGATE -> tickInvestigate(serverLevel);
            case STARE, CHARGE -> tickLockedOn(serverLevel);
            case SEARCH -> tickSearch(serverLevel);
        }
        steer();
    }

    private void tickPatrol(ServerLevel level) {
        if (lookForPrey(level) || listen(level)) {
            return;
        }
        if (goal == null || arrived) {
            // Follow the road; if it isn't over a road at all (e.g. spawned by command), any open air will do.
            if (!pickPatrolGoal(level, true) && !pickPatrolGoal(level, false)) {
                heading += Math.PI * (0.75 + random.nextDouble() * 0.5); // dead end: turn around
                goal = null;
            }
        }
        // The searchlight sweeps lazily from side to side ahead of it, angled down at the street.
        float forward = (float) Math.toDegrees(heading) - 90.0f;
        aim(forward + 35.0f * Mth.sin(stateTicks * 0.025f), 38.0f + 8.0f * Mth.sin(stateTicks * 0.017f), TURN_SPEED);
    }

    private void tickInvestigate(ServerLevel level) {
        if (lookForPrey(level)) {
            return;
        }
        listen(level); // a newer noise moves the point of interest
        if (pointOfInterest == null) {
            setState(State.PATROL);
            return;
        }
        if (arrived || stateTicks > INVESTIGATE_TIMEOUT) {
            // At the spot: hover and sweep the light around it, then give up.
            lookAround(pointOfInterest, 60.0f);
            if (arrivedTicks > INVESTIGATE_LINGER || stateTicks > INVESTIGATE_TIMEOUT + INVESTIGATE_LINGER) {
                setState(State.PATROL);
            }
        } else {
            aimAt(pointOfInterest, TURN_SPEED * 1.5f);
        }
    }

    private void tickSearch(ServerLevel level) {
        if (lookForPrey(level)) {
            return;
        }
        if (pointOfInterest == null || stateTicks > SEARCH_TICKS) {
            setState(State.PATROL);
            return;
        }
        lookAround(pointOfInterest, 75.0f);
    }

    /** STARE and CHARGE: locked on to one player. */
    private void tickLockedOn(ServerLevel level) {
        ServerPlayer target = targetId == null ? null : level.getServer().getPlayerList().getPlayer(targetId);
        if (target == null || !isValidPrey(target) || target.level() != level) {
            startSearch(pointOfInterest);
            return;
        }
        goal = null; // hover in place while staring
        aimAt(target.getEyePosition(), STARE_TURN_SPEED);
        boolean inSight = canSee(target, false) && facingWithin(target.getEyePosition(), 15.0f);
        if (!inSight) {
            if (++lostTicks > LOST_SIGHT_GRACE) {
                startSearch(pointOfInterest); // you broke line of sight: the charge is cancelled
            }
            return;
        }
        lostTicks = 0;
        pointOfInterest = target.position();
        if (state == State.STARE) {
            if (stateTicks >= STARE_TICKS) {
                setState(State.CHARGE);
                chargeTicks = 0;
                level.playSound(null, getX(), getY() + HALF_SIZE, getZ(), ModSounds.WATCHER_BEAM.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
            }
            return;
        }
        chargeTicks++;
        entityData.set(CHARGE, Math.min(1.0f, chargeTicks / (float) CHARGE_TICKS));
        if (chargeTicks >= CHARGE_TICKS) {
            turnToDust(level, target);
            setState(State.PATROL);
        }
    }

    // ---- Senses ----------------------------------------------------------------------------

    /** Anyone inside the vision cone and in line of sight? Then lock on. */
    private boolean lookForPrey(ServerLevel level) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (canSee(player, true)) {
                double d = player.distanceToSqr(this);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = player;
                }
            }
        }
        if (best == null) {
            return false;
        }
        targetId = best.getUUID();
        pointOfInterest = best.position();
        lostTicks = 0;
        setState(State.STARE);
        entityData.set(TARGET, best.getId());
        if (tickCount - lastSpotSoundTick > SPOT_SOUND_COOLDOWN) {
            lastSpotSoundTick = tickCount;
            level.playSound(null, getX(), getY() + HALF_SIZE, getZ(), ModSounds.WATCHER_SPOT.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        }
        return true;
    }

    /** Every 5 ticks: anyone sprinting nearby, or a flashlight on close by? Go and look. */
    private boolean listen(ServerLevel level) {
        if (tickCount % 5 != 0) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (!isValidPrey(player)) {
                continue;
            }
            double d = player.distanceTo(this);
            boolean loud = player.isSprinting() && d <= HEARING_RANGE;
            CameraData camera = CameraService.get(player);
            boolean glowing = camera.flashlight() && camera.battery() > 0.0f && d <= ELECTRONICS_RANGE;
            if (loud || glowing) {
                startInvestigate(level, player.position());
                return true;
            }
        }
        // A burning flare draws it like a flashlight does: it comes over to look at it.
        FlareEntity flare = FlareEntity.nearestBurning(level, position(), ELECTRONICS_RANGE);
        if (flare != null) {
            startInvestigate(level, flare.position());
            return true;
        }
        return false;
    }

    /**
     * Someone it may watch and turn to dust. Players inside a safe room are left alone (that also
     * cancels a charging beam), and so is anyone hiding in furniture, unless it saw them get in.
     */
    private boolean isValidPrey(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !SafeRooms.isSafe(player)
                && !HidingService.isHiddenFrom(player, this);
    }

    /** It dragged someone out of their hiding place (only possible when low enough): stare them down. */
    @Override
    public void onPulledOut(ServerPlayer player) {
        if (state == State.STARE || state == State.CHARGE) {
            return;
        }
        targetId = player.getUUID();
        pointOfInterest = player.position();
        lostTicks = 0;
        setState(State.STARE);
        entityData.set(TARGET, player.getId());
    }

    /** Within range (longer with a flashlight), optionally inside the cone, and nothing in between. */
    private boolean canSee(ServerPlayer player, boolean needCone) {
        if (!isValidPrey(player) || player.level() != level()) {
            return false;
        }
        CameraData camera = CameraService.get(player);
        double range = VISION_RANGE * (camera.flashlight() && camera.battery() > 0.0f ? FLASHLIGHT_VISION_MULTIPLIER : 1.0);
        Vec3 eye = eyeCentre();
        Vec3 target = player.getEyePosition();
        Vec3 to = target.subtract(eye);
        double distance = to.length();
        if (distance > range || distance < 1.0e-3) {
            return false;
        }
        if (needCone && lookVector().dot(to) / distance < VISION_COS) {
            return false;
        }
        // Either the head or the middle of the body showing is enough.
        return clearLine(eye, target) || clearLine(eye, player.position().add(0.0, 0.9, 0.0));
    }

    private boolean facingWithin(Vec3 point, float degrees) {
        Vec3 to = point.subtract(eyeCentre());
        double length = to.length();
        return length < 1.0e-3 || lookVector().dot(to) / length >= Math.cos(Math.toRadians(degrees));
    }

    /** No block between the two points (starting just outside the eye's own surface). */
    private boolean clearLine(Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length <= HALF_SIZE + 0.2) {
            return true;
        }
        Vec3 start = from.add(dir.scale((HALF_SIZE + 0.1) / length));
        return level().clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** Where the pupil points. */
    public Vec3 lookVector() {
        return calculateViewVector(getXRot(), getYHeadRot());
    }

    // ---- State changes ----------------------------------------------------------------------

    private void setState(State next) {
        state = next;
        stateTicks = 0;
        arrived = false;
        entityData.set(STATE, (byte) next.ordinal());
        if (next != State.STARE && next != State.CHARGE) {
            entityData.set(TARGET, -1);
            entityData.set(CHARGE, 0.0f);
            targetId = null;
        }
        if (next == State.PATROL) {
            goal = null;
            pointOfInterest = null;
        }
    }

    private void startInvestigate(ServerLevel level, Vec3 where) {
        boolean newPlace = pointOfInterest == null || pointOfInterest.distanceToSqr(where) > 16.0;
        pointOfInterest = where;
        if (state != State.INVESTIGATE) {
            setState(State.INVESTIGATE);
        }
        if (newPlace || goal == null) {
            goal = openSpotNear(level, Mth.floor(where.x), Mth.floor(where.z), 10);
            arrived = false;
        }
    }

    /** Lost sight of its target: a charging beam fizzles out, and it goes to look where you were. */
    private void startSearch(@Nullable Vec3 lastSeen) {
        if (state == State.CHARGE) {
            stopBeamSound();
        }
        setState(State.SEARCH);
        pointOfInterest = lastSeen;
        if (lastSeen != null && level() instanceof ServerLevel level) {
            goal = openSpotNear(level, Mth.floor(lastSeen.x), Mth.floor(lastSeen.z), 10);
        }
    }

    /** The beam sound is a 2.5 s one-shot: cut it on nearby clients when the charge is cancelled. */
    private void stopBeamSound() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(ModSounds.WATCHER_BEAM.get().getLocation(), SoundSource.HOSTILE);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 64.0 * 64.0) {
                player.connection.send(stop);
            }
        }
    }

    // ---- Turning someone to dust -----------------------------------------------------------

    private void turnToDust(ServerLevel level, ServerPlayer victim) {
        Vec3 at = victim.position();
        level.playSound(null, at.x, at.y, at.z, ModSounds.WATCHER_DUST.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
        // A column of falling grey dust where they stood, plus a puff of fine grey specks.
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState()),
                at.x, at.y + 1.0, at.z, 160, 0.3, 0.8, 0.3, 0.0);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.55f, 0.55f, 0.55f), 1.6f),
                at.x, at.y + 1.0, at.z, 80, 0.35, 0.8, 0.35, 0.02);
        placeDustPile(level, victim.blockPosition());
        DamageSource dust = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DUST_DAMAGE), this);
        victim.hurt(dust, Float.MAX_VALUE);
    }

    /** Leaves a little heap of grey dust where the victim stood (on the floor block or the one above). */
    private static void placeDustPile(ServerLevel level, BlockPos feet) {
        BlockState pile = ModBlocks.DUST_PILE.get().defaultBlockState();
        for (BlockPos pos : new BlockPos[] {feet, feet.above()}) {
            if (level.getBlockState(pos).canBeReplaced() && pile.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, pile);
                return;
            }
        }
    }

    // ---- Moving and aiming -----------------------------------------------------------------

    /** Glide toward the goal (with gentle easing and a slow bob), or hover. */
    private void steer() {
        double speed = switch (state) {
            case INVESTIGATE -> INVESTIGATE_SPEED;
            case SEARCH -> SEARCH_SPEED;
            default -> PATROL_SPEED;
        };
        double bob = 0.3 * Math.sin(tickCount * 0.05);
        Vec3 wanted = Vec3.ZERO;
        if (goal != null) {
            Vec3 to = new Vec3(goal.x - getX(), CRUISE_Y + bob - getY(), goal.z - getZ());
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            if (horizontal < 1.2) {
                arrived = true;
            }
            double length = to.length();
            if (length > 1.0e-3) {
                wanted = to.scale(Math.min(speed, length * 0.15) / length);
            }
        } else {
            wanted = new Vec3(0.0, (CRUISE_Y + bob - getY()) * 0.05, 0.0);
        }
        arrivedTicks = arrived ? arrivedTicks + 1 : 0;
        // Ease into the new velocity: it has the inertia of something big.
        setDeltaMovement(getDeltaMovement().lerp(wanted, 0.08));
    }

    private void aimAt(Vec3 point, float speed) {
        Vec3 to = point.subtract(eyeCentre());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        float wantYaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0f;
        float wantPitch = (float) -Math.toDegrees(Math.atan2(to.y, horizontal));
        aim(wantYaw, wantPitch, speed);
    }

    /** Sweep the light around a point: centred on it, swinging +/- {@code sweepDegrees}. */
    private void lookAround(Vec3 point, float sweepDegrees) {
        Vec3 to = point.subtract(eyeCentre());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        float centreYaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0f;
        float centrePitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.max(horizontal, 0.5)));
        aim(centreYaw + sweepDegrees * Mth.sin(stateTicks * 0.04f),
                Mth.clamp(centrePitch + 10.0f * Mth.sin(stateTicks * 0.031f), 10.0f, 80.0f), TURN_SPEED * 1.5f);
    }

    private void aim(float wantYaw, float wantPitch, float speed) {
        yaw = Mth.approachDegrees(yaw, wantYaw, speed);
        pitch = Mth.approach(pitch, Mth.clamp(wantPitch, -60.0f, 85.0f), speed * 0.7f);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setXRot(pitch);
    }

    // ---- Where it may fly ------------------------------------------------------------------

    /**
     * Picks the next patrol point along the road: 11 or 16 blocks ahead, preferring straight on,
     * sometimes turning at junctions, with open air all the way and mostly road underneath.
     */
    private boolean pickPatrolGoal(ServerLevel level, boolean needRoad) {
        double[] turns = random.nextFloat() < 0.35
                ? new double[] {40, -40, 0, 75, -75, 110, -110, 150, -150}
                : new double[] {0, 25, -25, 50, -50, 80, -80, 120, -120};
        for (double turn : turns) {
            double angle = heading + Math.toRadians(turn + (random.nextDouble() - 0.5) * 16.0);
            for (double distance : new double[] {16.0, 11.0}) {
                double x = getX() + Math.cos(angle) * distance;
                double z = getZ() + Math.sin(angle) * distance;
                boolean ok = needRoad
                        ? isStreet(level, Mth.floor(x), Mth.floor(z)) && routeOpen(level, x, z, 0.6)
                        : routeOpen(level, x, z, 0.0);
                if (ok) {
                    heading = angle;
                    goal = new Vec3(Mth.floor(x) + 0.5, CRUISE_Y, Mth.floor(z) + 0.5);
                    arrived = false;
                    return true;
                }
            }
        }
        return false;
    }

    /** Open air on the straight line from here to (x, z), sampled every 2 blocks; and enough road under it. */
    private boolean routeOpen(ServerLevel level, double x, double z, double minRoadFraction) {
        double dx = x - getX();
        double dz = z - getZ();
        int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dz * dz) / 2.0));
        int road = 0;
        for (int i = 1; i <= steps; i++) {
            int sx = Mth.floor(getX() + dx * i / steps);
            int sz = Mth.floor(getZ() + dz * i / steps);
            if (!isOpenAir(level, sx, sz)) {
                return false;
            }
            if (isStreet(level, sx, sz)) {
                road++;
            }
        }
        return road >= steps * minRoadFraction;
    }

    /**
     * Nearest spot within {@code radius} of (x, z) where the eye fits at cruise altitude (roads
     * preferred), or null if there's none (e.g. deep in a forest).
     */
    @Nullable
    private Vec3 openSpotNear(ServerLevel level, int x, int z, int radius) {
        Vec3 bestOpen = null;
        double bestOpenDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                double d = dx * dx + dz * dz;
                if (d > radius * radius || d >= bestOpenDistance || !isOpenAir(level, x + dx, z + dz)) {
                    continue;
                }
                // Roads count as a bit closer, so it hovers over the street when it can.
                double score = isStreet(level, x + dx, z + dz) ? d * 0.5 : d;
                if (score < bestOpenDistance) {
                    bestOpenDistance = score;
                    bestOpen = new Vec3(x + dx + 0.5, CRUISE_Y, z + dz + 0.5);
                }
            }
        }
        return bestOpen;
    }

    /**
     * A road column: solid asphalt at the floor layer (y40) with nothing solid on top of it. Lawns,
     * yards, sidewalks-on-lawns and house floors are a block higher, so they don't count.
     */
    public static boolean isStreet(ServerLevel level, int x, int z) {
        BlockPos floor = new BlockPos(x, LevelLayout.FLOOR_Y, z);
        if (!level.isLoaded(floor)) {
            return false;
        }
        BlockPos above = floor.above();
        return !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    /**
     * Room for the 3-block eye at cruise altitude over (x, z): this column and its four neighbours
     * have nothing taller than a streetlamp (no roofs, no trees). Uses the world's height map, so it's
     * five cheap lookups.
     */
    public static boolean isOpenAir(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, LevelLayout.FLOOR_Y, z))) {
            return false;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) <= OPEN_AIR_MAX_HEIGHT
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 1, z) <= OPEN_AIR_MAX_HEIGHT
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 1, z) <= OPEN_AIR_MAX_HEIGHT
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z + 1) <= OPEN_AIR_MAX_HEIGHT
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z - 1) <= OPEN_AIR_MAX_HEIGHT;
    }

    /** Called by the spawner: start gliding along the street in a random direction. */
    public void startPatrol(double headingRadians) {
        heading = headingRadians;
        yaw = (float) Math.toDegrees(headingRadians) - 90.0f;
        pitch = 35.0f;
        aim(yaw, pitch, 360.0f);
    }
}
