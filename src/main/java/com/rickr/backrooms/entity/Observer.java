package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Observer (Level 9): a tall, thin, featureless dark figure standing in the darkest spots,
 * 25-40 blocks from one player, watching them. Only that player can see it (same trick as
 * {@link PhantomFriend}: {@link #broadcastToPlayer} says yes for one player only).
 *
 * <p>It never moves and never hurts anyone. It vanishes when you stare straight at it for a moment,
 * when you walk towards it, or after a while on its own.
 */
public class Observer extends PathfinderMob {
    /** Closer than this and it's gone. */
    private static final double VANISH_DISTANCE = 14.0;
    /** "Looking straight at it": within this many degrees of the centre of your view... */
    private static final double STARE_ANGLE = 7.0;
    private static final double STARE_COS = Math.cos(Math.toRadians(STARE_ANGLE));
    /** ...for this long (0.6 s). */
    private static final int STARE_TICKS = 12;
    /** It lingers 40..80 s if nobody notices it. */
    private static final int MIN_LIFETIME = 40 * 20;
    private static final int EXTRA_LIFETIME = 40 * 20;
    /** Ticks between "vanish" (client stops drawing it) and removal. */
    private static final int VANISH_DELAY = 2;
    /** Where its head is, above its feet. */
    public static final double HEAD_HEIGHT = 2.55;

    /** True once it vanished: the client stops drawing it at once. */
    private static final EntityDataAccessor<Boolean> VANISHING =
            SynchedEntityData.defineId(Observer.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private UUID victimId;
    private int lifetime = MIN_LIFETIME;
    private int stareTicks;
    private int vanishTicks = -1;

    public Observer(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setNoGravity(true); // placed exactly on the ground and never moves
        setSilent(true);
        noPhysics = true;
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
                // we turn the head ourselves (always toward the victim)
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VANISHING, false);
    }

    /** Called once by the spawner, before the entity is added to the world. */
    public void setup(ServerPlayer victim) {
        victimId = victim.getUUID();
        lifetime = MIN_LIFETIME + random.nextInt(EXTRA_LIFETIME + 1);
        faceVictim(victim);
    }

    public boolean isVanishing() {
        return entityData.get(VANISHING);
    }

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

    // ---- Untouchable, motionless, never saved -----------------------------------------------

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
    }

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
    protected void pushEntities() {
    }

    @Override
    public void travel(Vec3 input) {
        // stands perfectly still
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // its own rules decide (see customServerAiStep)
    }

    @Override
    public void checkDespawn() {
    }

    // ---- Behaviour -------------------------------------------------------------------------

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
        if (victim == null || !victim.isAlive() || victim.isSpectator() || victim.level() != level()) {
            discard();
            return;
        }
        faceVictim(victim);

        if (distanceTo(victim) < VANISH_DISTANCE) {
            vanish(true); // walk toward it and there's nothing there
            return;
        }
        stareTicks = isStaredAtBy(victim) ? stareTicks + 1 : Math.max(0, stareTicks - 1);
        if (stareTicks >= STARE_TICKS) {
            vanish(true);
            return;
        }
        if (tickCount > lifetime) {
            // Leaves quietly if you aren't looking its way; with a breath if you are.
            vanish(isRoughlyInView(victim));
        }
    }

    /** True if the victim looks almost exactly at its head and nothing is in between. */
    private boolean isStaredAtBy(ServerPlayer victim) {
        Vec3 eye = victim.getEyePosition();
        Vec3 head = position().add(0.0, HEAD_HEIGHT, 0.0);
        Vec3 to = head.subtract(eye);
        double distance = to.length();
        if (distance < 1.0e-3 || victim.getLookAngle().dot(to) / distance < STARE_COS) {
            return false;
        }
        return level().clip(new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim))
                .getType() == HitResult.Type.MISS;
    }

    private boolean isRoughlyInView(ServerPlayer victim) {
        Vec3 to = position().add(0.0, HEAD_HEIGHT * 0.5, 0.0).subtract(victim.getEyePosition());
        double distance = to.length();
        return distance > 1.0e-3 && victim.getLookAngle().dot(to) / distance > 0.6;
    }

    private void faceVictim(ServerPlayer victim) {
        float yaw = (float) Math.toDegrees(Math.atan2(victim.getZ() - getZ(), victim.getX() - getX())) - 90.0f;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    /** Disappears: the victim's client stops drawing it at once; optionally a soft exhale for them alone. */
    public void vanish(boolean audible) {
        if (level().isClientSide || vanishTicks >= 0) {
            return;
        }
        vanishTicks = VANISH_DELAY;
        entityData.set(VANISHING, true);
        ServerPlayer victim = victim();
        if (audible && victim != null && victim.level() == level()) {
            float volume = (float) Math.max(1.0, distanceTo(victim) / 14.0); // >1 so it carries 25-40 blocks
            victim.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.OBSERVER_VANISH.get()),
                    SoundSource.HOSTILE, getX(), getY() + HEAD_HEIGHT, getZ(), volume, 0.9f + random.nextFloat() * 0.2f, random.nextLong()));
        }
    }
}
