package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.Level227Atmos;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level227Noclip;
import com.rickr.backrooms.world.layout.Level227Tree;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A bubble that fell out of the Sun over the Void's orbit (canon: bubbles circle the sun on beams
 * of light, each one's surface reflects another level, and entering one takes you there). Very
 * rarely one drifts down by the edge of a branch, a few blocks out over the void at about head
 * height: an iridescent sphere 3 blocks across whose surface rushes with the colours of the level
 * it mirrors ({@code client/VoidBubbleRenderer}). Leap into it and you noclip to that level. Miss,
 * and there is only the void.
 *
 * <p>It drifts in a slow loop around the spot it fell to and bobs, for {@value #LIFE_TICKS} ticks
 * (3 minutes); then it sinks away into the void and is gone. Its motion is worked out from the
 * game time, the same on the server and every client, so it glides smoothly without needing a
 * position update every tick.
 */
public class VoidBubble extends Entity {
    /** How long it hangs by the edge before it sinks away (3 minutes). */
    public static final int LIFE_TICKS = 3 * 60 * 20;
    /** Radius of the sphere (blocks); the entity is 3 x 3 x 3, the sphere centred in it. */
    public static final float RADIUS = 1.5f;
    /** How far out over the void it hangs from the edge (blocks, to its centre). */
    private static final double OUT_FROM_EDGE = 3.2;
    /** Bubbles mirror the levels with a glimpse picture (textures/entity/void_bubble/&lt;level&gt;.png). */
    private static final BackroomsLevel[] MIRRORS = {
            BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_2, BackroomsLevel.LEVEL_4,
            BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_37, BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_94,
            BackroomsLevel.LEVEL_7, BackroomsLevel.LEVEL_39};

    /** The centre of its drifting loop. */
    private static final EntityDataAccessor<Vector3f> ANCHOR = SynchedEntityData.defineId(VoidBubble.class, EntityDataSerializers.VECTOR3);
    /** Which level it mirrors (BackroomsLevel ordinal). */
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(VoidBubble.class, EntityDataSerializers.INT);
    /** Game time it started sinking, or 0 while it still hangs by the edge. */
    private static final EntityDataAccessor<Long> SINK_START = SynchedEntityData.defineId(VoidBubble.class, EntityDataSerializers.LONG);

    private int life;

    public VoidBubble(EntityType<? extends VoidBubble> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ANCHOR, new Vector3f());
        builder.define(TARGET, BackroomsLevel.LEVEL_0.ordinal());
        builder.define(SINK_START, 0L);
    }

    /** The level this bubble's surface reflects (and takes you to). */
    public BackroomsLevel target() {
        BackroomsLevel[] all = BackroomsLevel.values();
        int i = entityData.get(TARGET);
        return i >= 0 && i < all.length ? all[i] : BackroomsLevel.LEVEL_0;
    }

    /** Where the sphere's centre is at this moment (game time + partial tick): smooth on every side. */
    public Vec3 centreAt(float partialTick) {
        Vector3f a = entityData.get(ANCHOR);
        double t = (level().getGameTime() + partialTick + (getUUID().hashCode() & 1023)) / 20.0;
        double x = a.x + Math.cos(t * 0.21) * 1.4 + Math.cos(t * 0.53 + 1.1) * 0.45;
        double z = a.z + Math.sin(t * 0.17) * 1.4 + Math.sin(t * 0.47 + 0.3) * 0.45;
        double y = a.y + Math.sin(t * 0.6) * 0.4 + Math.sin(t * 1.3 + 2.0) * 0.1;
        long sink = entityData.get(SINK_START);
        if (sink != 0L) {
            double s = Math.max(0.0, (level().getGameTime() + partialTick - sink) / 20.0);
            y -= 0.6 * s * s; // slow at first, then falling away faster and faster
        }
        return new Vec3(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 c = centreAt(0.0f);
        setPos(c.x, c.y - RADIUS, c.z);
        if (level().isClientSide) {
            return;
        }
        life++;
        if (life >= LIFE_TICKS && entityData.get(SINK_START) == 0L) {
            entityData.set(SINK_START, level().getGameTime());
        }
        if (getY() < Level227Tree.VOID_DEATH_Y || life > LIFE_TICKS * 2) {
            discard();
            return;
        }
        // Anyone who touches the sphere is taken to the level it mirrors.
        List<Player> near = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.4));
        for (Player p : near) {
            if (p instanceof ServerPlayer player && !player.isSpectator() && player.isAlive() && touches(player.getBoundingBox(), c)) {
                enter(player);
                return;
            }
        }
    }

    /** Does a player's box reach into the sphere? (Closest point of the box to the centre.) */
    private static boolean touches(AABB box, Vec3 c) {
        double dx = c.x - Mth.clamp(c.x, box.minX, box.maxX);
        double dy = c.y - Mth.clamp(c.y, box.minY, box.maxY);
        double dz = c.z - Mth.clamp(c.z, box.minZ, box.maxZ);
        return dx * dx + dy * dy + dz * dz < RADIUS * RADIUS;
    }

    /** The bubble takes the player and bursts. */
    private void enter(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        Vec3 c = centreAt(0.0f);
        level.playSound(null, c.x, c.y, c.z, Level227Atmos.BUBBLE_ENTER.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.9, 0.9, 0.9, 0.08);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 30, 1.0, 1.0, 1.0, 0.05);
        // You are falling through it: no fall damage on the other side (sendToQuietly also zeroes it).
        player.fallDistance = 0;
        Level227Noclip.start(player, target(), Level227Noclip.Cause.BUBBLE);
        discard();
    }

    /**
     * Looks for a branch edge near {@code player} and lets a bubble drift down by it: walks out
     * from the player in random directions along the plan of the tree until the branch top falls
     * away (void, or a drop of more than 24 blocks), then hangs the bubble a few blocks out over
     * the drop at about head height. Returns null when there's no edge within 96 blocks.
     *
     * @param anyDistance also allow an edge right next to the player (the test command)
     */
    @Nullable
    public static VoidBubble spawnNearEdge(ServerLevel level, ServerPlayer player, RandomSource random, boolean anyDistance) {
        double px = player.getX();
        double pz = player.getZ();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int lastFeet = Level227Tree.topSurfaceY(px, pz);
            if (lastFeet == Integer.MIN_VALUE) {
                return null; // over the void ourselves (falling): no edge to hang it by
            }
            for (double r = 3.0; r <= 96.0; r += 3.0) {
                int feet = Level227Tree.topSurfaceY(px + dx * r, pz + dz * r);
                if (feet != Integer.MIN_VALUE && feet > lastFeet - 24) {
                    lastFeet = feet;
                    continue;
                }
                // The branch ends between r - 3 and r: hang the bubble out beyond it.
                if (!anyDistance && r < 16.0) {
                    break; // too close to see it come; try another direction
                }
                double cx = px + dx * (r - 3.0 + OUT_FROM_EDGE);
                double cz = pz + dz * (r - 3.0 + OUT_FROM_EDGE);
                double cy = lastFeet + 1.6;
                // Where a branch meets the trunk the plan also reads "no branch top": that is a
                // wall of bark, not the void. Hang bubbles only by real edges.
                double trunkClear = Level227Tree.TRUNK_RADIUS + 80.0;
                if (cx * cx + cz * cz < trunkClear * trunkClear || !level.hasChunkAt(BlockPos.containing(cx, cy, cz))) {
                    break;
                }
                VoidBubble bubble = Level227Atmos.VOID_BUBBLE.get().create(level);
                if (bubble == null) {
                    return null;
                }
                BackroomsLevel target = MIRRORS[random.nextInt(MIRRORS.length)];
                bubble.entityData.set(ANCHOR, new Vector3f((float) cx, (float) cy, (float) cz));
                bubble.entityData.set(TARGET, target.ordinal());
                Vec3 c = bubble.centreAt(0.0f);
                bubble.moveTo(c.x, c.y - RADIUS, c.z, 0.0f, 0.0f);
                level.addFreshEntity(bubble);
                return bubble;
            }
        }
        return null;
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
        return true; // a bubble you saw stays if the chunk unloads (it sinks away on its own clock)
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(ANCHOR, new Vector3f(tag.getFloat("AnchorX"), tag.getFloat("AnchorY"), tag.getFloat("AnchorZ")));
        entityData.set(TARGET, tag.getInt("Target"));
        life = tag.getInt("Life");
        // A bubble that was sinking when saved starts sinking again from the top.
        if (tag.getBoolean("Sinking")) {
            entityData.set(SINK_START, level().getGameTime());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        Vector3f a = entityData.get(ANCHOR);
        tag.putFloat("AnchorX", a.x);
        tag.putFloat("AnchorY", a.y);
        tag.putFloat("AnchorZ", a.z);
        tag.putInt("Target", entityData.get(TARGET));
        tag.putInt("Life", life);
        tag.putBoolean("Sinking", entityData.get(SINK_START) != 0L);
    }

    /** Position updates from the server are not needed: the client works out the same path itself. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }
}
