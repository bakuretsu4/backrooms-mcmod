package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.mixin.ServerGamePacketListenerAccessor;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.layout.Level39Layout;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 39 ("Megalophobia"):
 * <ul>
 *   <li><b>Supplies vanish.</b> Canon says whatever supplies a wanderer brings disappear on the way
 *       in. On arrival every food item and every bottle of Almond Water / Almond Milk is gone from
 *       your inventory (the camcorder, batteries and other gear stay: a gameplay choice, and the
 *       message says so). You have to find almond water in the houses.</li>
 *   <li><b>Falling into the tunnels never kills.</b> The holes in town drop about 30 blocks into
 *       the flooded tunnels of this same level. The water already breaks the fall, but a fall that
 *       ends anywhere down in the tunnels is forgiven as well.</li>
 *   <li><b>The gondola's sky arrival.</b> Riders appear high above the town on Slow Falling
 *       ({@link Travel#sendToLevel39Sky}); the landing never hurts, however long the drop, and the
 *       server doesn't kick them for "floating too long" on the way down.</li>
 *   <li><b>Never stuck in the sky.</b> Logging in, arriving or respawning in mid-air (a player
 *       disconnected while drifting down is saved hanging there) gives the same protection: high
 *       over the town, Slow Falling and a soft landing; otherwise 10 s of grace while the client
 *       loads the chunks.</li>
 * </ul>
 * The way on (a superstructure window, to Level 9) lives with the Window entities.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Rules {
    /**
     * A player protected while in the air: since when (wall-clock ms) and for how long at most.
     * While protected, the server's "floating too long" counters are cleared every tick and the
     * landing does no damage.
     */
    private record Drift(long since, long limitMs) {
    }

    /** Players coming down out of the sky (or still in the air after logging in or arriving). */
    private static final Map<UUID, Drift> DRIFTING = new ConcurrentHashMap<>();

    /**
     * The drift down from the sky takes about half a minute; the first seconds are spent hanging
     * still while the client loads the town. Vanilla kicks a player who stays off the ground for
     * 4 seconds on a server without allow-flight ("floating too long"), and Slow Falling doesn't
     * excuse them, so while a drift lasts the server's floating counters are cleared every tick.
     * This long at most, in case they never land.
     */
    private static final long SKY_FLOAT_MS = 60 * 1000L;
    /**
     * Anyone in the air just after logging in or arriving in Level 39 (the client may not have the
     * chunks under them yet and holds them still) is protected this long.
     */
    private static final long ARRIVAL_GRACE_MS = 10 * 1000L;
    /** Landed (on the ground or in water) this long after arriving: the drift is over. */
    private static final long SKY_SETTLE_MS = 3 * 1000L;
    /** Slow Falling given to someone found high over the town: enough to reach the ground gently. */
    private static final int RESUME_FALL_TICKS = 30 * 20;
    /** Higher than this over the ground (and not in a house or a tower) counts as up in the sky. */
    private static final int HIGH_ABOVE_GROUND = Level39Layout.GROUND_Y + 3;

    /** The next landing of this player in Level 39 does no damage (see {@link Travel#sendToLevel39Sky}). */
    public static void markSkyArrival(ServerPlayer player) {
        startDrift(player, SKY_FLOAT_MS);
    }

    /** Protects the player for {@code limitMs} from now, never shortening a longer drift already running. */
    private static void startDrift(ServerPlayer player, long limitMs) {
        long now = System.currentTimeMillis();
        DRIFTING.merge(player.getUUID(), new Drift(now, limitMs),
                (old, fresh) -> old.since() + old.limitMs() >= now + limitMs ? old : fresh);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Drift drift = DRIFTING.get(player.getUUID());
        if (drift == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - drift.since();
        if (elapsed > drift.limitMs() || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_39) {
            DRIFTING.remove(player.getUUID());
            return;
        }
        // (Right after a teleport or a login the "on the ground" flag can still be a stale one.)
        if (elapsed > SKY_SETTLE_MS && (player.onGround() || player.isInWater())) {
            DRIFTING.remove(player.getUUID());
            return;
        }
        ServerGamePacketListenerAccessor connection = (ServerGamePacketListenerAccessor) player.connection;
        connection.backrooms$setAboveGroundTickCount(0);
        connection.backrooms$setAboveGroundVehicleTickCount(0);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTo().equals(BackroomsLevel.LEVEL_39.dimension())) {
            takeSupplies(player);
            protectIfAirborne(player);
        }
    }

    @SubscribeEvent
    public static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            protectIfAirborne(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            protectIfAirborne(player);
        }
    }

    /**
     * A player who logs in, arrives or respawns in Level 39 in mid-air (say they were disconnected
     * while drifting down from the sky, and the save kept them hanging there) is not kicked for
     * floating while the client loads the town. If they are high over the town they get Slow
     * Falling for the rest of the way down and a soft landing, as at the gondola arrival.
     */
    private static void protectIfAirborne(ServerPlayer player) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_39) {
            return;
        }
        boolean airborne = !player.onGround() && !player.isInWater();
        if (highOverTown(player)) {
            startDrift(player, SKY_FLOAT_MS);
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, RESUME_FALL_TICKS, 0, false, false, true));
        } else if (airborne) {
            startDrift(player, ARRIVAL_GRACE_MS);
        }
    }

    /** Above the town's rooftops level and not inside a house or a stair tower. */
    private static boolean highOverTown(ServerPlayer player) {
        if (player.getY() <= HIGH_ABOVE_GROUND) {
            return false;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        if (!Level39Layout.inTown(x, z) || Level39Layout.inStairTower(x, z)) {
            return false; // the superstructure's galleries and towers have floors of their own
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_39);
        return Level39Layout.houseAt(seed, x, z) == null;
    }

    /** Removes the food and almond water a player carries in, and tells them (once, quietly). */
    private static void takeSupplies(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int taken = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && isSupply(stack, player)) {
                taken += stack.getCount();
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (taken > 0) {
            inventory.setChanged();
            player.sendSystemMessage(Component.translatable("message.backrooms.level39.supplies_gone").withStyle(s -> s.withItalic(true)));
            player.playNotifySound(SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.4f, 0.6f);
        }
    }

    /** Food of any kind, Almond Water and Almond Milk. */
    private static boolean isSupply(ItemStack stack, ServerPlayer player) {
        return stack.is(ModItems.ALMOND_WATER.get())
                || stack.is(ModItems.ALMOND_MILK.get())
                || stack.getFoodProperties(player) != null;
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_39) {
            return;
        }
        Drift drift = DRIFTING.remove(player.getUUID());
        if (drift != null) {
            event.setCanceled(true); // drifted down from the clouds: a soft landing
            return;
        }
        // Down a hole (or the rungs of a shaft) into the tunnels: forgiven.
        if (player.getY() <= Level39Layout.TUNNEL_VAULT_TOP
                && Level39Layout.tunnelAt(BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_39),
                        player.getBlockX(), player.getBlockZ())) {
            event.setCanceled(true);
        }
    }

    private Level39Rules() {}
}
