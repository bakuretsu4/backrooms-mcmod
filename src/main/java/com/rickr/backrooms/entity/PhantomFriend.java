package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HallucinationClientState;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Poolrooms hallucination: a copy of one of your friends (their skin, their name) that only
 * YOU can see. It waves at you, then walks off, always staying a little ahead, leading you
 * somewhere pointless and away from the exit. Then it vanishes. It never attacks.
 *
 * <p><b>How only one player sees it:</b> the server decides which clients hear about each entity
 * in {@code ChunkMap.TrackedEntity.updatePlayer}, which asks {@link #broadcastToPlayer(ServerPlayer)}.
 * We answer "yes" only for our victim, so no other client ever receives the spawn packet. Every
 * sound it makes is also sent to the victim alone ({@link #playSound}).
 *
 * <p><b>Behaviour</b> (all on the server, in {@link #customServerAiStep()}):
 * <ul>
 *   <li>WAIT: stands still facing you, waving now and then, until you come within
 *       {@link #START_LURE_DISTANCE} blocks.</li>
 *   <li>LURE: beckons once, then walks from waypoint to waypoint, each one further from the nearest
 *       exit. It hurries when you get close, slows when you lag, and stops to wave when you fall far
 *       behind.</li>
 *   <li>It vanishes after a while, after a long walk, if you give up on it, if you touch it, if you
 *       drink Almond Water, dry off completely or leave the Poolrooms.</li>
 * </ul>
 */
public class PhantomFriend extends PathfinderMob {
    // ---- Tweakables ------------------------------------------------------------------------
    /** WAIT ends and the luring starts once you are this close. */
    private static final double START_LURE_DISTANCE = 14.0;
    /** Closer than this while luring: it hurries away so you can't catch up. */
    private static final double HURRY_DISTANCE = 6.0;
    /** Between {@link #HURRY_DISTANCE} and this: a normal walk (it stays 6..14 blocks ahead). */
    private static final double COMFORT_DISTANCE = 14.0;
    /** Further than this: it stops, turns around and waves for you to catch up. */
    private static final double LAG_DISTANCE = 22.0;
    /** Further than this for {@link #ABANDON_TICKS}: you gave up on it, so it fades away. */
    private static final double ABANDON_DISTANCE = 40.0;
    private static final int ABANDON_TICKS = 15 * 20;
    /** Give up waiting to be noticed after 90 s. */
    private static final int MAX_WAIT_TICKS = 90 * 20;
    /** Luring lasts 60..90 s... */
    private static final int MIN_LURE_TICKS = 60 * 20;
    private static final int EXTRA_LURE_TICKS = 30 * 20;
    /** ...or until it has walked this many blocks. */
    private static final double MAX_TRAVEL = 150.0;
    /** The beckoning wave at the start of LURE, before it turns and walks. */
    private static final int BECKON_TICKS = 25;

    /**
     * Movement speed attribute. Mobs and players use different speed maths: this value with
     * {@link #SPEED_WALK} gives about a Backrooms player's walking speed (~3.5 blocks/s).
     */
    private static final double BASE_SPEED = 0.30;
    private static final double SPEED_HURRY = 1.3;   // ~6.5 blocks/s: faster than you can sprint
    private static final double SPEED_WALK = 0.95;   // ~3.5 blocks/s: your walking pace
    private static final double SPEED_SLOW = 0.7;    // ~2 blocks/s: dawdling so you can catch up
    /** Water deeper than this (blocks above its feet) and it swims up, like a player holding jump. */
    private static final double DEEP_WATER = 1.2;
    /** Walking but moved less than 1 block in this many ticks (3 s): stuck, pick another waypoint. */
    private static final int STUCK_TICKS = 60;
    /** How far it looks for dry/shallow ground when it ended up in a deep pool. */
    private static final int ESCAPE_SEARCH_RADIUS = 8;
    /** Still in a deep pool after 15 s of trying to climb out: give up and vanish. */
    private static final int MAX_DEEP_WATER_TICKS = 15 * 20;
    /** Ticks between "it vanished" (flag synced, sound played) and actually removing the entity. */
    private static final int VANISH_DELAY = 4;
    /** Give up after this many waypoint searches in a row that found nothing (cornered). */
    private static final int MAX_FAILED_WAYPOINTS = 10;

    // ---- Synced to the victim's client (for the renderer) ----------------------------------
    /** UUID of the player being copied: the client looks up their skin with it. */
    private static final EntityDataAccessor<Optional<UUID>> MIMIC_ID =
            SynchedEntityData.defineId(PhantomFriend.class, EntityDataSerializers.OPTIONAL_UUID);
    /** Their name, for the name tag (works even if they log off later). */
    private static final EntityDataAccessor<String> MIMIC_NAME =
            SynchedEntityData.defineId(PhantomFriend.class, EntityDataSerializers.STRING);
    /** True while the right arm is raised and waving. */
    private static final EntityDataAccessor<Boolean> WAVING =
            SynchedEntityData.defineId(PhantomFriend.class, EntityDataSerializers.BOOLEAN);
    /** True once it vanished: the client stops drawing it and pulses the dream shader. */
    private static final EntityDataAccessor<Boolean> VANISHING =
            SynchedEntityData.defineId(PhantomFriend.class, EntityDataSerializers.BOOLEAN);

    private enum Phase { WAIT, LURE }

    // ---- Server-only state -----------------------------------------------------------------
    @Nullable
    private UUID victimId;
    private Phase phase = Phase.WAIT;
    private int phaseTicks;
    private int lureDuration = MIN_LURE_TICKS;
    private double travelled;
    private double lastX;
    private double lastZ;
    private int abandonTicks;
    private int waveTicks;
    private int nextWaveIn = 40;
    private int glanceTicks;
    @Nullable
    private BlockPos waypoint;
    private int failedWaypoints;
    private int retryIn;
    /** Where it was {@link #progressTicks} ago; if it barely moved since, it's stuck. */
    private double progressX;
    private double progressZ;
    private int progressTicks;
    /** After getting stuck: the direction (radians) of the old waypoint, avoided for the next pick. */
    private double avoidAngle = Double.NaN;
    /** Ticks spent in a deep pool trying to climb out. */
    private int deepWaterTicks;
    @Nullable
    private BlockPos escapeTarget;
    /** Current speed modifier on top of BASE_SPEED (one of the SPEED_* values). */
    private double pace = SPEED_WALK;
    /** -1 = present; otherwise ticks until removal. */
    private int vanishTicks = -1;

    public PhantomFriend(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        // It wades through shallow water like a person, so water is as good as dry tile for paths.
        setPathfindingMalus(PathType.WATER, 0.0f);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
        getNavigation().setCanFloat(true);
    }

    /** Our own navigation, whose pathfinder refuses to go through deep pools (see {@link ShallowWaterEvaluator}). */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new PoolNavigation(this, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MIMIC_ID, Optional.empty());
        builder.define(MIMIC_NAME, "");
        builder.define(WAVING, false);
        builder.define(VANISHING, false);
    }

    /** Called once by the spawner, before the entity is added to the world. */
    public void setup(ServerPlayer victim, ServerPlayer mimic) {
        victimId = victim.getUUID();
        entityData.set(MIMIC_ID, Optional.of(mimic.getUUID()));
        entityData.set(MIMIC_NAME, mimic.getGameProfile().getName());
        lureDuration = MIN_LURE_TICKS + random.nextInt(EXTRA_LURE_TICKS + 1);
        nextWaveIn = 20 + random.nextInt(40);
    }

    public Optional<UUID> mimicId() {
        return entityData.get(MIMIC_ID);
    }

    public boolean isWaving() {
        return entityData.get(WAVING);
    }

    public boolean isVanishing() {
        return entityData.get(VANISHING);
    }

    // ---- Visible to exactly one player -----------------------------------------------------

    /** Only the victim's client is ever told this entity exists. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return victimId != null && victimId.equals(player.getUUID());
    }

    @Nullable
    private ServerPlayer victim() {
        if (victimId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(victimId);
    }

    /**
     * Every sound (wading splashes, footsteps) goes through here. Normally the server would send it
     * to everyone nearby; other players must not hear a friend who isn't there, so only the victim
     * gets the packet.
     */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent()) {
            playToVictim(sound, volume, pitch);
        }
    }

    private void playToVictim(SoundEvent sound, float volume, float pitch) {
        if (level().isClientSide) {
            return;
        }
        ServerPlayer victim = victim();
        if (victim != null && victim.level() == level()) {
            victim.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    getSoundSource(), getX(), getY(), getZ(), volume, pitch, random.nextLong()));
        }
    }

    // ---- Behaves like a player, but can't be touched, hurt, pushed or saved -----------------

    /** Shows the copied player's name (name tag, and anywhere else the game names it). */
    @Override
    public Component getName() {
        String name = entityData.get(MIMIC_NAME);
        return name.isEmpty() ? super.getName() : Component.literal(name);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // nothing real can hurt a hallucination
    }

    @Override
    public boolean isPickable() {
        return false; // your crosshair (and arrows) go straight through
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // walks through people instead of shoving them
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // its own rules decide when it goes (see customServerAiStep)
    }

    @Override
    public void checkDespawn() {
        // never despawns by vanilla rules
    }

    // Sounds a person makes: their splashes, and our Backrooms footsteps on dry tile.

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        boolean hurrying = pace >= SPEED_HURRY;
        SoundEvent step = hurrying ? ModSounds.STEP_CONCRETE_RUN.get() : ModSounds.STEP_CONCRETE_WALK.get();
        playSound(step, hurrying ? 0.6f : 0.15f, 0.92f + random.nextFloat() * 0.16f);
    }

    /** Same stride as real players' footsteps in the Backrooms (see PlayerGameplay). */
    @Override
    protected float nextStep() {
        return moveDist + (pace >= SPEED_HURRY ? 2.3f : 1.7f);
    }

    /**
     * Mobs normally crawl through water (their "input" is tiny compared to a player's). While
     * wading we turn it into a player-sized input, so it moves through the shallow pools at the
     * same pace you do.
     */
    @Override
    public void travel(Vec3 travelVector) {
        double horizontal = travelVector.horizontalDistance();
        if (isInWater() && horizontal > 1.0e-4) {
            double input = Math.min(1.0, getSpeed() / (BASE_SPEED * SPEED_WALK) * 0.98);
            travelVector = new Vec3(travelVector.x / horizontal * input, travelVector.y, travelVector.z / horizontal * input);
        }
        super.travel(travelVector);
    }

    // ---- The client side: start the shader pulse when it vanishes ---------------------------

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && VANISHING.equals(key) && isVanishing()) {
            HallucinationClientState.vanishPulseMs = System.currentTimeMillis();
        }
    }

    // ---- The behaviour ---------------------------------------------------------------------

    /**
     * Makes it vanish: the victim's client stops drawing it at once (and pulses the dream shader),
     * the entity is removed a few ticks later. The victim's {@code HallucinationService} notices
     * it's gone and starts the cooldown.
     */
    public void vanish(boolean audible) {
        if (level().isClientSide || vanishTicks >= 0) {
            return;
        }
        vanishTicks = VANISH_DELAY;
        entityData.set(VANISHING, true);
        entityData.set(WAVING, false);
        getNavigation().stop();
        ServerPlayer victim = victim();
        if (audible && victim != null && victim.level() == level()) {
            // Volume above 1 widens the hearing range (16 blocks x volume), so you hear it from afar too.
            float volume = (float) Math.max(1.0, distanceTo(victim) / 12.0);
            playToVictim(ModSounds.PHANTOM_VANISH.get(), volume, 1.0f);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (vanishTicks >= 0) {
            if (vanishTicks-- == 0) {
                discard();
            }
            return;
        }
        ServerPlayer victim = victim();
        // Victim gone, dead, elsewhere, or haunted by a newer phantom (e.g. after relogging): just go.
        if (victim == null || !victim.isAlive() || victim.isSpectator() || victim.level() != level()
                || !getUUID().equals(victim.getData(ModAttachments.HALLUCINATION).phantomId())) {
            discard();
            return;
        }

        // Deep pool: paddle up so the head stays above water, like a swimming player.
        if (isInWater() && getFluidHeight(FluidTags.WATER) > DEEP_WATER) {
            getJumpControl().jump();
        }
        if (waveTicks > 0 && --waveTicks == 0) {
            entityData.set(WAVING, false);
        }

        double distance = distanceTo(victim);
        // Reaching out and touching it breaks the illusion.
        if (getBoundingBox().inflate(0.15).intersects(victim.getBoundingBox())) {
            vanish(true);
            return;
        }
        abandonTicks = distance > ABANDON_DISTANCE ? abandonTicks + 1 : 0;
        if (abandonTicks >= ABANDON_TICKS) {
            vanish(true);
            return;
        }

        // Somehow ended up in a deep pool (or the drain): swim to the nearest place to stand and climb out.
        if (inDeepWater()) {
            tickEscapeDeepWater();
            return;
        }
        deepWaterTicks = 0;
        escapeTarget = null;

        phaseTicks++;
        if (phase == Phase.WAIT) {
            tickWait(victim, distance);
        } else {
            tickLure(victim, distance);
        }
    }

    /** Water under its feet as well as around them: that's a deep pool, not the 1-deep shallows. */
    private boolean inDeepWater() {
        return isInWater() && level().getFluidState(blockPosition().below()).is(FluidTags.WATER);
    }

    /**
     * Paddles toward the nearest standable column (searched every second, up to 8 blocks away) and
     * keeps "holding jump": at the pool edge vanilla water physics then hops it out, like a player.
     */
    private void tickEscapeDeepWater() {
        getNavigation().stop();
        lastX = getX(); // swimming back doesn't count toward the luring distance
        lastZ = getZ();
        if (++deepWaterTicks > MAX_DEEP_WATER_TICKS) {
            vanish(true);
            return;
        }
        if (escapeTarget == null || deepWaterTicks % 20 == 1) {
            escapeTarget = nearestStandPos(ESCAPE_SEARCH_RADIUS);
        }
        if (escapeTarget != null) {
            getMoveControl().setWantedPosition(escapeTarget.getX() + 0.5, escapeTarget.getY(), escapeTarget.getZ() + 0.5, SPEED_WALK);
        }
        getJumpControl().jump();
    }

    /** Closest standable column (by straight-line distance) within {@code radius} blocks, or null. */
    @Nullable
    private BlockPos nearestStandPos(int radius) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        LevelLayout layout = BackroomsLevel.POOLROOMS.layout();
        long seed = BackroomsSeeds.layoutSeed(serverLevel, BackroomsLevel.POOLROOMS);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = getBlockX() + dx;
                int z = getBlockZ() + dz;
                double d = dx * dx + dz * dz;
                if (d < bestDistance) {
                    BlockPos stand = findStandPos(serverLevel, layout, seed, x, z);
                    if (stand != null) {
                        best = stand;
                        bestDistance = d;
                    }
                }
            }
        }
        return best;
    }

    /** Standing still, facing you, waving now and then. */
    private void tickWait(ServerPlayer victim, double distance) {
        getNavigation().stop();
        getLookControl().setLookAt(victim, 20.0f, 40.0f);
        maybeWave(60, 80);
        if (distance < START_LURE_DISTANCE) {
            phase = Phase.LURE;
            phaseTicks = 0;
            lastX = getX();
            lastZ = getZ();
            wave(BECKON_TICKS); // "come on, this way!"
        } else if (phaseTicks > MAX_WAIT_TICKS) {
            vanish(true);
        }
    }

    /** Walking ahead of you, away from the exit. */
    private void tickLure(ServerPlayer victim, double distance) {
        travelled += Math.sqrt((getX() - lastX) * (getX() - lastX) + (getZ() - lastZ) * (getZ() - lastZ));
        lastX = getX();
        lastZ = getZ();
        if (phaseTicks > lureDuration || travelled > MAX_TRAVEL) {
            vanish(true);
            return;
        }
        if (phaseTicks < BECKON_TICKS) {
            getLookControl().setLookAt(victim, 20.0f, 40.0f);
            return;
        }
        if (distance > LAG_DISTANCE) {
            // You fell behind: stop, turn around and wave you over.
            getNavigation().stop();
            getLookControl().setLookAt(victim, 20.0f, 40.0f);
            maybeWave(40, 60);
            resetProgress();
            return;
        }

        pace = distance < HURRY_DISTANCE ? SPEED_HURRY : distance < COMFORT_DISTANCE ? SPEED_WALK : SPEED_SLOW;
        if (waypoint != null && horizontalDistanceTo(waypoint) < 1.5) {
            waypoint = null; // arrived
        }
        // Stuck check: supposed to be walking, but moved less than a block in 3 s? Then give up on this
        // waypoint and pick a new one in a different direction right away.
        if (++progressTicks >= STUCK_TICKS) {
            double movedX = getX() - progressX;
            double movedZ = getZ() - progressZ;
            if (waypoint != null && movedX * movedX + movedZ * movedZ < 1.0) {
                avoidAngle = Math.atan2(waypoint.getZ() + 0.5 - getZ(), waypoint.getX() + 0.5 - getX());
                waypoint = null;
                retryIn = 0;
                getNavigation().stop();
            }
            resetProgress();
        }
        if (waypoint == null || getNavigation().isDone()) {
            if (retryIn > 0) {
                retryIn--;
                getLookControl().setLookAt(victim, 20.0f, 40.0f);
                return;
            }
            Path path = waypoint != null ? walkablePath(waypoint) : null;
            if (path == null) {
                path = pickWaypoint(failedWaypoints >= 3);
            }
            if (path == null) {
                // Cornered (dead end, or every way leads back toward the exit). Look at you, try again soon.
                waypoint = null;
                retryIn = 10;
                if (++failedWaypoints >= MAX_FAILED_WAYPOINTS) {
                    vanish(true);
                }
                return;
            }
            failedWaypoints = 0;
            avoidAngle = Double.NaN;
            getNavigation().moveTo(path, pace);
            resetProgress();
        }
        getNavigation().setSpeedModifier(pace);

        // Glance back over the shoulder now and then, to check you're still following.
        if (glanceTicks > 0) {
            glanceTicks--;
            getLookControl().setLookAt(victim, 20.0f, 40.0f);
        } else if (random.nextInt(120) == 0) {
            glanceTicks = 15 + random.nextInt(15);
        }
    }

    private void resetProgress() {
        progressX = getX();
        progressZ = getZ();
        progressTicks = 0;
    }

    /**
     * Picks the next spot to walk to: 8..16 blocks away, roughly in the direction away from the
     * nearest exit, further from it than we are now, standable and reachable by pathfinding.
     * Right after getting stuck, directions within 60 degrees of the stuck one are skipped.
     *
     * @param relaxed after a few failures, accept any direction that isn't much closer to the exit
     */
    @Nullable
    private Path pickWaypoint(boolean relaxed) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        BackroomsLevel floor = BackroomsLevel.POOLROOMS;
        LevelLayout layout = floor.layout();
        long seed = BackroomsSeeds.layoutSeed(serverLevel, floor);
        int[] exit = layout.exits().nearest(seed, getBlockX(), getBlockZ());
        double myExitDistance = exitDistance(layout, seed, getBlockX(), getBlockZ());
        double awayAngle = Math.atan2(getZ() - exit[1], getX() - exit[0]);
        boolean avoiding = !Double.isNaN(avoidAngle);
        double spread = Math.toRadians(relaxed || avoiding ? 180.0 : 75.0);
        double mustGain = relaxed ? -3.0 : 2.0;

        int pathSearches = 0;
        for (int attempt = 0; attempt < 24 && pathSearches < 4; attempt++) {
            double angle = awayAngle + (random.nextDouble() * 2.0 - 1.0) * spread;
            if (avoiding && Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - avoidAngle))) < 60.0) {
                continue;
            }
            double reach = 8.0 + random.nextDouble() * 8.0;
            int x = (int) Math.floor(getX() + Math.cos(angle) * reach);
            int z = (int) Math.floor(getZ() + Math.sin(angle) * reach);
            if (exitDistance(layout, seed, x, z) < myExitDistance + mustGain) {
                continue;
            }
            BlockPos stand = findStandPos(serverLevel, layout, seed, x, z);
            if (stand == null) {
                continue;
            }
            pathSearches++; // pathfinding is the expensive part, so only a few per tick
            Path path = walkablePath(stand);
            if (path != null) {
                waypoint = stand;
                return path;
            }
        }
        return null;
    }

    /**
     * A path to {@code target} that really gets there and only steps on walkable columns of the
     * layout (dry tile or shallow water), or null. The pathfinder already refuses deep water; this
     * double-checks against the floor plan.
     */
    @Nullable
    private Path walkablePath(BlockPos target) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Path path = getNavigation().createPath(target, 0);
        if (path == null || !path.canReach()) {
            return null;
        }
        LevelLayout layout = BackroomsLevel.POOLROOMS.layout();
        long seed = BackroomsSeeds.layoutSeed(serverLevel, BackroomsLevel.POOLROOMS);
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (!layout.isWalkable(seed, node.x, node.z)) {
                return null;
            }
        }
        return path;
    }

    /** Straight-line distance from (x, z) to the exit nearest to it. */
    private static double exitDistance(LevelLayout layout, long seed, int x, int z) {
        int[] exit = layout.exits().nearest(seed, x, z);
        return Math.sqrt((double) (x - exit[0]) * (x - exit[0]) + (double) (z - exit[1]) * (z - exit[1]));
    }

    private double horizontalDistanceTo(BlockPos pos) {
        double dx = pos.getX() + 0.5 - getX();
        double dz = pos.getZ() + 0.5 - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void maybeWave(int minGap, int extraGap) {
        if (waveTicks == 0 && --nextWaveIn <= 0) {
            wave(30 + random.nextInt(20));
            nextWaveIn = minGap + random.nextInt(extraGap);
        }
    }

    private void wave(int ticks) {
        waveTicks = ticks;
        entityData.set(WAVING, true);
    }

    /**
     * Where a person could stand in column (x, z) of the Poolrooms, or null. The layout says where
     * the floor is; the real world gets the last word (blocks may have been broken or placed).
     * Spots with water over head height (deep pools) are refused.
     */
    @Nullable
    public static BlockPos findStandPos(ServerLevel level, LevelLayout layout, long seed, int x, int z) {
        if (!layout.isWalkable(seed, x, z)) {
            return null;
        }
        BlockPos feet = new BlockPos(x, layout.standY(seed, x, z), z);
        if (!level.isLoaded(feet)) {
            return null;
        }
        BlockPos head = feet.above();
        BlockPos below = feet.below();
        boolean clear = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
        boolean floor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
        boolean headDry = level.getFluidState(head).isEmpty();
        return clear && floor && headDry ? feet : null;
    }

    // ---- Pathfinding that stays out of deep pools -------------------------------------------

    /** Normal ground navigation, but with {@link ShallowWaterEvaluator} as its map of where it can step. */
    private static final class PoolNavigation extends GroundPathNavigation {
        PoolNavigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new ShallowWaterEvaluator();
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    /**
     * Vanilla's walking pathfinder treats all water the same. Here water with more water under it
     * (a deep pool or the drain) is a wall, while the 1-deep shallows (water over a tile floor) stay
     * as cheap as dry tile. So paths go around deep pools instead of swimming through them.
     */
    private static final class ShallowWaterEvaluator extends WalkNodeEvaluator {
        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            PathType type = super.getPathType(context, x, y, z);
            if (type == PathType.WATER && context.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().is(FluidTags.WATER)) {
                return PathType.BLOCKED;
            }
            return type;
        }
    }
}
