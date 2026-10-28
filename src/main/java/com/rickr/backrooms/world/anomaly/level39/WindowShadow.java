package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.mixin.ServerGamePacketListenerAccessor;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Window (Entity 2) living in one window of the superstructure: the most hostile species, the one
 * that infests Level 39's facade. It is a flat presence in the glass (3 wide, 5 tall) that you can't
 * touch or hurt; {@link HostileWindows} wakes it when someone comes near.
 *
 * <p>What it does, in order (all tunable below):
 * <ol>
 *   <li><b>Waking</b> ({@value #WAKE_TICKS} ticks): the lit window swells into a lustrous, pulsing
 *       light; the blinds tremble; a shadow takes shape behind the glass: long arms, small torso,
 *       bent legs (client: {@code WindowShadowRenderer}).</li>
 *   <li><b>Lurking</b>: it hums sweetly and pulls your gaze (the client drifts your camera toward it
 *       while you stare). Staring at it costs sanity. Every second you stay within
 *       {@value #NOTICE_RANGE} blocks makes it angrier, faster the closer you are.</li>
 *   <li><b>Hunting</b>: it shrieks, then throws glass shards at you every couple of seconds,
 *       breathes paralysing fumes when you are close (Slowness, Nausea), and if you come within
 *       {@value #GRAB_RANGE} blocks of a ground-floor window it grabs you.</li>
 *   <li><b>Grabbing</b>: you are dragged toward the glass and hurt. Pull free (get more than
 *       {@value #BREAK_FREE} blocks away) or hold on: survive {@value #NOCLIP_TICKS} ticks pressed
 *       against the glass and you noclip through it, out of Level 39 and on to Level 94.</li>
 * </ol>
 * More than {@value #PASSIVE_GROUP} people within {@value #GROUP_RANGE} blocks make it passive
 * (canon): the light sinks, the shadow just watches.
 */
public class WindowShadow extends Entity {
    // ---- States (synced to the client, which draws each one differently) ------------------------
    public static final byte WAKING = 0;
    public static final byte LURKING = 1;
    public static final byte HUNTING = 2;
    public static final byte GRABBING = 3;
    public static final byte PASSIVE = 4;
    public static final byte FADING = 5;

    // ---- Tunables -------------------------------------------------------------------------------
    public static final int WAKE_TICKS = 50;
    public static final int FADE_TICKS = 40;
    /** Within this distance (blocks, from your eyes to the middle of the glass) it pays attention. */
    public static final double NOTICE_RANGE = 10.0;
    /** Anger needed to start hunting (1 = full); per tick at the edge of notice range, and close up. */
    private static final float ANGER_FAR = 1.0f / 160.0f;
    private static final float ANGER_NEAR = 1.0f / 50.0f;
    /** ...and how fast it calms down while nobody is near. */
    private static final float CALM = 1.0f / 300.0f;
    /** Staring at it makes it come for you faster. */
    private static final float STARE_BONUS = 1.6f;
    /** Sanity lost per second while you stare at an awake window. */
    private static final float STARE_DRAIN = 0.5f;
    private static final double SHARD_RANGE = 14.0;
    private static final int SHARD_EVERY_MIN = 30;
    private static final int SHARD_EVERY_SPREAD = 30;
    private static final float SHARD_DAMAGE = 2.5f;
    private static final double FUME_RANGE = 6.0;
    private static final int FUME_EVERY = 160;
    /** Grab: within this many blocks in front of the glass (ground storey only)... */
    public static final double GRAB_RANGE = 3.2;
    /** ...dragged toward it this hard (blocks per tick added to your motion)... */
    private static final double PULL = 0.16;
    /** ...hurt this much every {@value #GRAB_HURT_EVERY} ticks... */
    private static final float GRAB_DAMAGE = 1.5f;
    private static final int GRAB_HURT_EVERY = 10;
    /** ...until you are this far from the glass (you broke free)... */
    public static final double BREAK_FREE = 4.4;
    /** ...or have been held this long within {@value #THROUGH_DISTANCE} blocks of it: you go through. */
    public static final int NOCLIP_TICKS = 60;
    private static final double THROUGH_DISTANCE = 2.6;
    private static final int GRAB_COOLDOWN = 80;
    public static final int PASSIVE_GROUP = 4;
    public static final double GROUP_RANGE = 16.0;
    /** With nobody within this range for {@value #IDLE_TICKS} ticks it sinks back into the glass. */
    private static final double KEEP_RANGE = 24.0;
    private static final int IDLE_TICKS = 200;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(WindowShadow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FACING = SynchedEntityData.defineId(WindowShadow.class, EntityDataSerializers.BYTE);
    /** The storey (0 = ground) the window is on. */
    private static final EntityDataAccessor<Byte> DATA_STOREY = SynchedEntityData.defineId(WindowShadow.class, EntityDataSerializers.BYTE);

    // ---- Server-side state ------------------------------------------------------------------------
    private int stateTicks;
    private float anger;
    private int idleTicks;
    private int shardCooldown = 20;
    private int fumeCooldown = 60;
    private int grabCooldown;
    private int lureCooldown = 40;
    private int blindsCooldown = 30;
    @Nullable
    private UUID grabbed;
    private int grabTicks;
    /** Game time since when the grabbed player has been pressed against the glass, or -1. */
    private long pressedSince = -1L;
    @Nullable
    private Vec3 lastHeld;

    // ---- Client-side state (for drawing) ---------------------------------------------------------
    private int clientStateTicks;
    private byte clientLastState = -1;

    public WindowShadow(EntityType<? extends WindowShadow> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    /**
     * Wakes the Window of bay {@code bay}, storey {@code storey} of a facade stretch: it appears flat
     * against the town side of the glass.
     */
    public static WindowShadow spawn(ServerLevel level, AnomalySite site, int bay, int storey) {
        WindowShadow w = new WindowShadow(Level39AnomalyRegistry.WINDOW_SHADOW.get(), level);
        Direction facing = Level39Sites.facing(site);
        double along = Level39Sites.bayCentre(site, bay);
        // A hair in front of the glass so it draws over it.
        double x = Level39Sites.worldX(site, along, 0.03);
        double z = Level39Sites.worldZ(site, along, 0.03);
        w.setPos(x, Level39Sites.glassBottom(storey), z);
        w.entityData.set(DATA_FACING, (byte) facing.get2DDataValue());
        w.entityData.set(DATA_STOREY, (byte) storey);
        w.setYRot(facing.toYRot());
        level.addFreshEntity(w);
        level.playSound(null, x, w.getY() + 2.5, z, Level39AnomalyRegistry.WINDOW_WAKE.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
        return w;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, WAKING);
        builder.define(DATA_FACING, (byte) 0);
        builder.define(DATA_STOREY, (byte) 0);
    }

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public Direction facing() {
        return Direction.from2DDataValue(entityData.get(DATA_FACING));
    }

    public int storey() {
        return entityData.get(DATA_STOREY);
    }

    /** The middle of the glass (on the town side). */
    public Vec3 glassCentre() {
        return new Vec3(getX(), getY() + Level39Sites.GLASS_HEIGHT / 2.0, getZ());
    }

    /** How far in front of the glass (blocks, toward the town) a point is. */
    public double inFront(Vec3 p) {
        Direction f = facing();
        return f.getAxis() == Direction.Axis.X ? (p.x - getX()) * f.getStepX() : (p.z - getZ()) * f.getStepZ();
    }

    /** How far along the facade from the window's middle a point is. */
    public double sideways(Vec3 p) {
        return facing().getAxis() == Direction.Axis.X ? p.z - getZ() : p.x - getX();
    }

    /** Ticks since the client saw the state change (for fades and pulses). */
    public int clientStateTicks() {
        return clientStateTicks;
    }

    private void setState(byte state) {
        if (state() != state) {
            entityData.set(DATA_STATE, state);
            stateTicks = 0;
        }
    }

    // ==========================================================================================
    // Ticking
    // ==========================================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            byte s = state();
            if (s != clientLastState) {
                clientLastState = s;
                clientStateTicks = 0;
            } else {
                clientStateTicks++;
            }
            clientAsh(s);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        stateTicks++;
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_39) {
            discard();
            return;
        }
        if (state() == FADING) {
            if (stateTicks >= FADE_TICKS) {
                discard();
            }
            return;
        }
        Vec3 centre = glassCentre();
        List<ServerPlayer> near = new ArrayList<>();
        int group = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            double d = p.distanceToSqr(centre);
            if (d < KEEP_RANGE * KEEP_RANGE) {
                near.add(p);
            }
            if (d < GROUP_RANGE * GROUP_RANGE) {
                group++;
            }
        }
        if (near.isEmpty()) {
            if (++idleTicks > IDLE_TICKS) {
                releaseGrab(level);
                setState(FADING);
            }
            return;
        }
        idleTicks = 0;
        if (state() == WAKING) {
            if (stateTicks >= WAKE_TICKS) {
                setState(LURKING);
            }
            return;
        }
        // Canon: in a group of more than four, the Windows leave you alone.
        if (group > PASSIVE_GROUP) {
            releaseGrab(level);
            anger = 0.0f;
            setState(PASSIVE);
            return;
        }
        if (state() == PASSIVE) {
            setState(LURKING);
        }
        ambience(level, centre);
        ServerPlayer target = nearestPrey(near, centre);
        switch (state()) {
            case LURKING -> lurk(level, target, centre);
            case HUNTING -> hunt(level, target, centre);
            case GRABBING -> grab(level, centre);
            default -> {
            }
        }
    }

    /** Coal ash drifting out of the window frame (canon: the windows show coal traces; you can smell it). */
    private void clientAsh(byte s) {
        float chance = switch (s) {
            case HUNTING, GRABBING -> 0.6f;
            case LURKING -> 0.3f;
            case WAKING -> 0.2f;
            default -> 0.05f;
        };
        if (random.nextFloat() >= chance) {
            return;
        }
        Direction f = facing();
        Direction side = f.getClockWise();
        double a = (random.nextDouble() - 0.5) * 3.0;
        double x = getX() + side.getStepX() * a + f.getStepX() * 0.1;
        double z = getZ() + side.getStepZ() * a + f.getStepZ() * 0.1;
        double y = getY() + random.nextDouble() * Level39Sites.GLASS_HEIGHT;
        level().addParticle(ParticleTypes.ASH, x, y, z, f.getStepX() * 0.02, -0.005, f.getStepZ() * 0.02);
    }

    /** Survival/adventure players in front of the window, nearest first. */
    @Nullable
    private ServerPlayer nearestPrey(List<ServerPlayer> near, Vec3 centre) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : near) {
            // Someone hiding in furniture (player/HidingService) isn't seen by the window either.
            if (p.isCreative() || HidingService.isHidden(p) || inFront(p.position()) < 0.0) {
                continue;
            }
            double d = p.getEyePosition().distanceTo(centre);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** The sweet hum and the trembling blinds. */
    private void ambience(ServerLevel level, Vec3 c) {
        if (--lureCooldown <= 0) {
            lureCooldown = 110 + random.nextInt(80);
            level.playSound(null, c.x, c.y, c.z, Level39AnomalyRegistry.WINDOW_LURE.get(), SoundSource.HOSTILE, 0.9f,
                    0.95f + random.nextFloat() * 0.1f);
        }
        if (--blindsCooldown <= 0) {
            blindsCooldown = 50 + random.nextInt(150);
            level.playSound(null, c.x, c.y + 1.0, c.z, Level39AnomalyRegistry.WINDOW_BLINDS.get(), SoundSource.HOSTILE, 0.7f,
                    0.9f + random.nextFloat() * 0.2f);
        }
    }

    private void lurk(ServerLevel level, @Nullable ServerPlayer target, Vec3 centre) {
        if (target == null) {
            anger = Math.max(0.0f, anger - CALM);
            return;
        }
        double d = target.getEyePosition().distanceTo(centre);
        boolean staring = tickCount % 20 == 0 && Observation.sees(target, centre, NOTICE_RANGE * 1.6, 22.0);
        if (staring) {
            SanityService.drain(target, STARE_DRAIN);
        }
        if (d < NOTICE_RANGE) {
            float t = (float) (1.0 - d / NOTICE_RANGE);
            float gain = Mth.lerp(t, ANGER_FAR, ANGER_NEAR);
            if (Observation.sees(target, centre, NOTICE_RANGE, 22.0)) {
                gain *= STARE_BONUS;
            }
            anger += gain;
        } else {
            anger = Math.max(0.0f, anger - CALM);
        }
        // Walking right up to it skips the waiting: it takes you.
        if (anger >= 1.0f || canGrab(target)) {
            setState(HUNTING);
            level.playSound(null, centre.x, centre.y, centre.z, Level39AnomalyRegistry.WINDOW_SHRIEK.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
            shardCooldown = 25;
        }
    }

    private boolean canGrab(ServerPlayer p) {
        if (storey() != 0 || grabCooldown > 0) {
            return false;
        }
        double front = inFront(p.position());
        return front >= -0.5 && front < GRAB_RANGE && Math.abs(sideways(p.position())) < 2.6
                && p.getY() < getY() + Level39Sites.GLASS_HEIGHT;
    }

    private void hunt(ServerLevel level, @Nullable ServerPlayer target, Vec3 centre) {
        if (grabCooldown > 0) {
            grabCooldown--;
        }
        if (target == null || target.getEyePosition().distanceTo(centre) > SHARD_RANGE * 1.4) {
            anger = Math.max(0.0f, anger - CALM * 4);
            if (anger <= 0.0f) {
                setState(LURKING);
            }
            return;
        }
        anger = 1.0f;
        if (canGrab(target)) {
            startGrab(level, target, centre);
            return;
        }
        double d = target.getEyePosition().distanceTo(centre);
        if (--shardCooldown <= 0 && d < SHARD_RANGE) {
            shardCooldown = SHARD_EVERY_MIN + random.nextInt(SHARD_EVERY_SPREAD);
            throwShards(level, target, centre);
        }
        if (--fumeCooldown <= 0 && d < FUME_RANGE + 2.0) {
            fumeCooldown = FUME_EVERY;
            breatheFumes(level, centre);
        }
    }

    /** A burst of glass shards out of the window at you (they hit if nothing is in the way). */
    private void throwShards(ServerLevel level, ServerPlayer target, Vec3 centre) {
        Direction f = facing();
        Vec3 from = centre.add(f.getStepX() * 0.6, 0.0, f.getStepZ() * 0.6);
        Vec3 to = target.getEyePosition().add(0.0, -0.4, 0.0);
        level.playSound(null, from.x, from.y, from.z, Level39AnomalyRegistry.GLASS_SHARDS.get(), SoundSource.HOSTILE, 1.3f,
                0.9f + random.nextFloat() * 0.2f);
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        Vec3 step = to.subtract(from);
        int points = Math.max(4, (int) (step.length() * 1.5));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(step.scale(i / (double) points));
            level.sendParticles(glass, p.x, p.y, p.z, 3, 0.12, 0.12, 0.12, 0.05);
        }
        level.sendParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 10, 0.8, 1.2, 0.8, 0.2);
        if (Observation.lineOfSight(level, from, to)) {
            target.hurt(windowDamage(level), SHARD_DAMAGE);
            Vec3 push = step.normalize().scale(0.35);
            target.push(push.x, 0.12, push.z);
            target.hurtMarked = true;
        }
    }

    /** Acrid fumes from the frame: everyone close is left slow and sick. */
    private void breatheFumes(ServerLevel level, Vec3 centre) {
        Direction f = facing();
        Vec3 base = new Vec3(getX(), getY() + 0.4, getZ());
        level.playSound(null, base.x, base.y, base.z, Level39AnomalyRegistry.WINDOW_FUMES.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
        for (int i = 0; i < 6; i++) {
            double out = 0.5 + i * 0.8;
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, base.x + f.getStepX() * out, base.y + i * 0.15,
                    base.z + f.getStepZ() * out, 2, 0.6, 0.3, 0.6, 0.01);
            level.sendParticles(ParticleTypes.ASH, base.x + f.getStepX() * out, base.y + 0.5, base.z + f.getStepZ() * out,
                    12, 0.8, 0.6, 0.8, 0.02);
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            double front = inFront(p.position());
            if (front >= -0.5 && front < FUME_RANGE && Math.abs(sideways(p.position())) < FUME_RANGE) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 2, false, true, true));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, true, true));
            }
        }
    }

    private void startGrab(ServerLevel level, ServerPlayer target, Vec3 centre) {
        grabbed = target.getUUID();
        grabTicks = 0;
        pressedSince = -1L;
        lastHeld = target.position();
        setState(GRABBING);
        level.playSound(null, centre.x, centre.y, centre.z, Level39AnomalyRegistry.WINDOW_GRAB.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
        level.playSound(null, centre.x, centre.y, centre.z, Level39AnomalyRegistry.WINDOW_SHRIEK.get(), SoundSource.HOSTILE, 1.0f, 1.2f);
    }

    /** Holding someone: drag them in, hurt them, and if they last, let them through. */
    private void grab(ServerLevel level, Vec3 centre) {
        ServerPlayer p = grabbed == null ? null : level.getServer().getPlayerList().getPlayer(grabbed);
        if (p == null || p.isRemoved() || p.level() != level || p.isCreative() || p.isSpectator()) {
            releaseGrab(level);
            return;
        }
        double front = inFront(p.position());
        if (front > BREAK_FREE || Math.abs(sideways(p.position())) > BREAK_FREE) {
            releaseGrab(level); // pulled free
            return;
        }
        grabTicks++;
        // The 3 seconds only count while you are pressed against the glass, held the whole time:
        // being dragged there doesn't count, and a jump in position (a teleport) starts them again.
        long now = level.getGameTime();
        boolean jumped = lastHeld != null && lastHeld.distanceToSqr(p.position()) > 2.0 * 2.0;
        lastHeld = p.position();
        if (front >= THROUGH_DISTANCE || jumped) {
            pressedSince = -1L;
        } else if (pressedSince < 0L) {
            pressedSince = now;
        }
        // Toward the bottom middle of the glass, and up over the sill.
        Vec3 hold = new Vec3(getX(), getY() + 0.2, getZ());
        Vec3 pull = hold.subtract(p.position());
        double len = pull.length();
        if (len > 0.05) {
            Vec3 dir = pull.scale(1.0 / len);
            p.setDeltaMovement(p.getDeltaMovement().scale(0.55).add(dir.x * PULL, Math.max(0.0, dir.y) * PULL + 0.04, dir.z * PULL));
            p.hurtMarked = true;
            p.resetFallDistance();
        }
        notFloating(p);
        if (grabTicks % GRAB_HURT_EVERY == 0) {
            p.hurt(windowDamage(level), GRAB_DAMAGE);
            BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
            level.sendParticles(glass, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
        }
        if (!p.isAlive()) {
            releaseGrab(level);
            return;
        }
        if (pressedSince >= 0L && now - pressedSince >= NOCLIP_TICKS && grabTicks >= NOCLIP_TICKS) {
            // It had you, and you held on: you slip through the glass into whatever is behind it.
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), Level39AnomalyRegistry.WINDOW_NOCLIP.get(),
                    SoundSource.HOSTILE, 1.2f, 1.0f);
            grabbed = null;
            anger = 0.0f;
            setState(FADING);
            Travel.useExit(p, BackroomsLevel.LEVEL_39);
        }
    }

    private void releaseGrab(ServerLevel level) {
        if (grabbed != null) {
            grabbed = null;
            grabCooldown = GRAB_COOLDOWN;
            if (state() == GRABBING) {
                setState(HUNTING);
            }
        }
    }

    /**
     * Clears the server's "floating too long" counters for a player we are holding in the air (a
     * Window's grip, a chairlift seat): without allow-flight, vanilla kicks anyone who stays off the
     * ground for 4 seconds, and being held up by us is not cheating. Call every tick while it lasts.
     */
    public static void notFloating(ServerPlayer player) {
        ServerGamePacketListenerAccessor connection = (ServerGamePacketListenerAccessor) player.connection;
        connection.backrooms$setAboveGroundTickCount(0);
        connection.backrooms$setAboveGroundVehicleTickCount(0);
    }

    private static DamageSource windowDamage(ServerLevel level) {
        return Level39AnomalyRegistry.damage(level, Level39AnomalyRegistry.WINDOW_DAMAGE);
    }

    // ==========================================================================================
    // It is part of the wall: no pushing, no hitting, no saving
    // ==========================================================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
