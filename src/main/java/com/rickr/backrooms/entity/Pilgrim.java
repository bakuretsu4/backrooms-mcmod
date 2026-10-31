package com.rickr.backrooms.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The pilgrim at the camp (Level 490; after the play-test: "one that talks"): an old traveller bent
 * under an enormous pack, by a fire beside the walk to the spire. He greets you when you come near;
 * right-click him and he says the next thing he knows, about the chains, the whales, the clouds and
 * the spire, and how the way out is at the top. What he says shows in a speech bubble over his head
 * (client/PilgrimRenderer) for everyone near, not in the chat. His lines are in the lang file
 * ({@code entity.backrooms.pilgrim.line.N}). Placed by world/Level490Figures; can't be hurt.
 */
public class Pilgrim extends PathfinderMob {
    /** How many lines he has (entity.backrooms.pilgrim.line.0 .. LINES - 1). */
    public static final int LINES = 11;
    /** The greeting, as a line number. */
    public static final int GREET = 255;
    private static final double GREET_DISTANCE = 7.0;
    private static final double KEEP_DISTANCE = 110.0;

    /**
     * What he is saying: (a count, so saying the same line again shows again) << 8 | the line (0..LINES-1,
     * or GREET); -1 before he first speaks.
     */
    private static final EntityDataAccessor<Integer> SPEECH = SynchedEntityData.defineId(Pilgrim.class, EntityDataSerializers.INT);

    private final Set<UUID> greeted = new HashSet<>();
    private int nextLine;
    private int said;
    private float homeYaw;
    /** Client: the tick the current speech started (set when it arrives). */
    private int speechStart;

    public Pilgrim(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPEECH, -1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPEECH.equals(key)) {
            speechStart = tickCount;
        }
    }

    /** The lang key of what he is saying, or null. */
    public String speechKey() {
        int s = entityData.get(SPEECH);
        if (s < 0) {
            return null;
        }
        int line = s & 0xff;
        return line == GREET ? "entity.backrooms.pilgrim.greet" : "entity.backrooms.pilgrim.line." + line;
    }

    /** Ticks since he started saying it (client). */
    public int speechAge() {
        return tickCount - speechStart;
    }

    public void setHomeYaw(float yaw) {
        homeYaw = yaw;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    private void say(int line) {
        said++;
        entityData.set(SPEECH, (said << 8) | line);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Player near = level.getNearestPlayer(this, KEEP_DISTANCE);
        if (near == null) {
            discard();
            return;
        }
        if (distanceTo(near) < 10) {
            getLookControl().setLookAt(near, 20, 20);
        } else {
            setYBodyRot(homeYaw);
            setYHeadRot(homeYaw);
        }
        if (distanceTo(near) < GREET_DISTANCE && greeted.add(near.getUUID())) {
            say(GREET);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            say(nextLine);
            nextLine = (nextLine + 1) % LINES;
            greeted.add(player.getUUID());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
