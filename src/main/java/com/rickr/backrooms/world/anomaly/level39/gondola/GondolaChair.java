package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The chair you are riding: a seat entity that carries you up the line and into the whiteout.
 *
 * <p>Where it is never depends on physics: its position is a pure function of the line
 * ({@link GondolaLine}) and of how long the ride has been going, worked out the same way on the
 * server and on the client. So the server only has to tell the client WHICH line and WHEN the ride
 * started (synced data); the client then moves the chair (and you with it) itself, every tick,
 * smoothly, without waiting for position packets.
 *
 * <ul>
 *   <li>For the first second ({@link GondolaLine#DISMOUNT_TICKS}) you can still sneak to hop off
 *       onto the deck. After that the bar is locked: you can't drop out mid-air.</li>
 *   <li>After {@link GondolaLine#RIDE_TICKS} you are gone, out through the fog and into Level 39's
 *       sky ({@link Travel#sendToLevel39Sky}).</li>
 *   <li>It is never saved (a chair half-way up the line after a restart would make no sense) and
 *       vanishes the moment nobody sits in it. The station puts a new chair out a few seconds
 *       later ({@link GondolaStation}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public class GondolaChair extends Entity {
    private static final EntityDataAccessor<Integer> ANCHOR_X = SynchedEntityData.defineId(GondolaChair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANCHOR_Z = SynchedEntityData.defineId(GondolaChair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(GondolaChair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SALT = SynchedEntityData.defineId(GondolaChair.class, EntityDataSerializers.LONG);
    /** Game time the ride started; -1 until the server has set it up. */
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(GondolaChair.class, EntityDataSerializers.LONG);

    /** The line, rebuilt whenever the synced numbers change (once, on the client). */
    @Nullable
    private GondolaLine line;
    /** Set just before the server lets the rider go on purpose (arrival), so the lock doesn't stop it. */
    private boolean releasing;

    public GondolaChair(EntityType<? extends GondolaChair> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Right-clicked the station chair: the chair block becomes this seat, you sit down, the bar
     * comes down with a clunk and off you go.
     */
    static void board(ServerLevel level, ServerPlayer player, GondolaLine line, BlockPos chairPos) {
        GondolaChair chair = new GondolaChair(GondolaRegistry.GONDOLA_CHAIR.get(), level);
        chair.entityData.set(ANCHOR_X, line.anchorX);
        chair.entityData.set(ANCHOR_Z, line.anchorZ);
        chair.entityData.set(FACING, line.facing);
        chair.entityData.set(SALT, line.salt);
        chair.entityData.set(START, level.getGameTime());
        chair.line = line;
        chair.setYRot(line.dir.toYRot());
        chair.setPos(line.rideX(0), line.rideY(0), line.rideZ(0));
        level.setBlock(chairPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.addFreshEntity(chair);
        player.startRiding(chair, true);
        level.playSound(null, chairPos, GondolaRegistry.BOARD.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        GondolaStation.chairTaken(level, chairPos);
    }

    /** True while the player sits in a gondola chair. */
    public static boolean isRiding(Player player) {
        return player.getVehicle() instanceof GondolaChair;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ANCHOR_X, 0);
        builder.define(ANCHOR_Z, 0);
        builder.define(FACING, 0);
        builder.define(SALT, 0L);
        builder.define(START, -1L);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // Any of the line's numbers changed: forget the old line, it is rebuilt on demand.
        if (key == ANCHOR_X || key == ANCHOR_Z || key == FACING || key == SALT) {
            line = null;
        }
    }

    /** The line this chair runs on, or null before the server's numbers have arrived. */
    @Nullable
    public GondolaLine line() {
        if (line == null && entityData.get(START) >= 0) {
            line = new GondolaLine(entityData.get(ANCHOR_X), entityData.get(ANCHOR_Z), entityData.get(FACING), entityData.get(SALT));
        }
        return line;
    }

    /** Ticks since the ride started, with the fraction of the current tick (for smooth drawing). */
    public double rideTicks(float partialTick) {
        long start = entityData.get(START);
        return start < 0 ? 0.0 : level().getGameTime() - start + partialTick;
    }

    /** 0 at the station, 1 when you go through. */
    public float progress(float partialTick) {
        return (float) Math.min(1.0, rideTicks(partialTick) / GondolaLine.RIDE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        GondolaLine l = line();
        if (l != null) {
            double t = rideTicks(0.0f);
            setPos(l.rideX(t), l.rideY(t), l.rideZ(t));
            setYRot(l.dir.toYRot());
        }
        if (level().isClientSide) {
            return;
        }
        Entity rider = getFirstPassenger();
        if (rider == null || l == null) {
            if (tickCount > 2) {
                discard(); // nobody in it any more (hopped off at the station, logged out...)
            }
            return;
        }
        if (rider instanceof ServerPlayer riding) {
            // Sitting in a chair high on the cable is not "flying": don't let the server kick for it.
            com.rickr.backrooms.world.anomaly.level39.WindowShadow.notFloating(riding);
        }
        if (rideTicks(0.0f) >= GondolaLine.RIDE_TICKS && rider instanceof ServerPlayer player) {
            arrive(player, l);
        }
    }

    /** Through the whiteout: let go of the rider and drop them into Level 39's sky. */
    private void arrive(ServerPlayer player, GondolaLine l) {
        releasing = true;
        player.stopRiding();
        discard();
        if (!Travel.sendToLevel39Sky(player, l.arrivalX(), l.arrivalZ())) {
            // Level 39 isn't there (a broken install): back onto the station deck rather than a fall.
            BlockPos deck = l.stationChairPos();
            player.teleportTo(deck.getX() + 0.5, GondolaLine.DECK_Y + 0.5, deck.getZ() + 0.5);
        }
    }

    /**
     * Logged out mid-ride: the seat isn't saved, so without this you would log back in hanging in
     * the air and fall. Instead you are put back on the station deck, at the boarding spot, as if
     * you had never left. This event fires before the player is saved, so the deck is where you
     * come back.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.getVehicle() instanceof GondolaChair chair)) {
            return;
        }
        GondolaLine l = chair.line();
        chair.releasing = true;
        player.stopRiding();
        chair.discard();
        if (l != null) {
            BlockPos spot = l.stationChairPos();
            player.moveTo(spot.getX() + 0.5, GondolaLine.DECK_Y + 0.5, spot.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
        player.resetFallDistance();
    }

    /** Allowed to let its rider off right now? */
    private boolean mayRelease(Entity rider) {
        return releasing || isRemoved() || !rider.isAlive() || rideTicks(0.0f) < GondolaLine.DISMOUNT_TICKS;
    }

    /**
     * The lock: once the chair has left the station, getting off (sneaking) is refused. Only on the
     * server, which decides who rides what; the client simply follows.
     */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && !event.getLevel().isClientSide
                && event.getEntityBeingMounted() instanceof GondolaChair chair && !chair.mayRelease(event.getEntityMounting())) {
            event.setCanceled(true);
        }
    }

    // ---- The client follows the ride itself, not the server's position packets. -------------

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (line() == null) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
        }
    }

    // ---- A seat, not a thing: no pushing, no hitting, no saving. ------------------------------

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
