package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraData;
import com.rickr.backrooms.entity.Bacteria;
import com.rickr.backrooms.entity.Observer;
import com.rickr.backrooms.entity.Smiler;
import com.rickr.backrooms.entity.Watcher;
import com.rickr.backrooms.network.SanityPayload;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.Level94Clock;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sanity, server side. Once a second, for every player in survival/adventure:
 * <ul>
 *   <li>outside the Backrooms it slowly comes back (+0.5/s);</li>
 *   <li>in a safe room it comes back fast (+1.0/s);</li>
 *   <li>otherwise it drains: a little all the time, more in the dark, alone, near monsters or
 *       during a blackout. Standing next to a friend in the light slowly restores it instead.</li>
 *   <li>Level 94 has its own day/night rules instead ({@link #level94Change}): it recovers in a
 *       house by day and drains slowly outside at night.</li>
 * </ul>
 * The client only displays it ({@link SanityClientState}) and does the scary stuff on its own.
 * Creative and spectator players are never affected.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class SanityService {
    // ---- Drain per second ------------------------------------------------------------------
    private static final float BASE_DRAIN = 0.05f;
    private static final float DARK_DRAIN = 0.25f;
    private static final float ALONE_DRAIN = 0.10f;
    private static final float BACTERIA_SEEN_DRAIN = 0.6f;
    private static final float BACTERIA_CHASE_DRAIN = 1.2f;
    private static final float WATCHER_DRAIN = 0.8f;
    private static final float OBSERVER_DRAIN = 0.3f;
    private static final float SMILER_DRAIN = 1.0f;
    private static final float BLACKOUT_DRAIN = 0.2f;

    // ---- Recovery per second ---------------------------------------------------------------
    private static final float SAFE_ROOM_RECOVERY = 1.0f;
    private static final float COMPANY_RECOVERY = 0.15f;
    private static final float OUTSIDE_RECOVERY = 0.5f;
    /** Level 37 ("Sublimity") is calming everywhere: warm light, still water, nothing hunting you. */
    private static final float LEVEL_37_RECOVERY = 0.6f;
    /** Level 94: indoors (a house) by day you get your breath back; at night you only hold steady. */
    private static final float LEVEL_94_HOUSE_RECOVERY = 0.6f;
    /** Level 94: out on the hills at night it slowly wears you down (about -45 over a night). */
    private static final float LEVEL_94_NIGHT_DRAIN = 0.25f;

    // ---- Distances (blocks) ----------------------------------------------------------------
    private static final double ALONE_RANGE = 24.0;
    private static final double COMPANY_RANGE = 8.0;
    private static final double BACTERIA_RANGE = 16.0;
    private static final double WATCHER_RANGE = 24.0;
    private static final double OBSERVER_RANGE = 16.0;
    private static final double SMILER_RANGE = 24.0;
    /** "Dark" = the brighter of block light and sky light at your eyes is this or less. */
    private static final int DARK_LIGHT = 3;

    /** Below this, monsters notice you more often (see {@link #spawnPressure}). */
    private static final float PRESSURE_BELOW = 30.0f;
    /** Resend at least this often (ticks) even if the number didn't change. */
    private static final int RESEND_TICKS = 100;

    // ---- Public API ------------------------------------------------------------------------

    /** Current sanity 0..100. Only meaningful on the server (clients read {@link SanityClientState}). */
    public static float get(Player p) {
        return p.getData(ModAttachments.SANITY).sanity;
    }

    /** Adds {@code amount} sanity (clamped to 0..100) and tells the client at once. Used by items. */
    public static void restore(ServerPlayer p, float amount) {
        SanityData data = p.getData(ModAttachments.SANITY);
        data.sanity = Mth.clamp(data.sanity + amount, 0.0f, SanityData.MAX);
        sync(p, data, true);
    }

    /** Removes {@code amount} sanity (clamped to 0..100). */
    public static void drain(ServerPlayer p, float amount) {
        SanityData data = p.getData(ModAttachments.SANITY);
        data.sanity = Mth.clamp(data.sanity - amount, 0.0f, SanityData.MAX);
        sync(p, data, false);
    }

    /** Sets sanity to an exact value (operator command). */
    public static void set(ServerPlayer p, float value) {
        SanityData data = p.getData(ModAttachments.SANITY);
        data.sanity = Mth.clamp(value, 0.0f, SanityData.MAX);
        sync(p, data, true);
    }

    /**
     * How much more likely a Bacteria is to find you: 1.0 normally, rising from 1 at sanity 30 to
     * 3 at sanity 0. The spawner multiplies its chance by the highest pressure in a group.
     */
    public static float spawnPressure(ServerPlayer p) {
        float sanity = get(p);
        if (sanity >= PRESSURE_BELOW) {
            return 1.0f;
        }
        return Mth.clamp(1.0f + 2.0f * (1.0f - sanity / PRESSURE_BELOW), 1.0f, 3.0f);
    }

    // ---- Ticking ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            tickSecond(player);
        }
    }

    /** Runs once per second per player. */
    private static void tickSecond(ServerPlayer player) {
        SanityData data = player.getData(ModAttachments.SANITY);
        if (!player.isCreative() && !player.isSpectator()) {
            float change = Travel.inBackrooms(player.level()) ? changePerSecond(player) : OUTSIDE_RECOVERY;
            data.sanity = Mth.clamp(data.sanity + change, 0.0f, SanityData.MAX);
        }
        sync(player, data, false);
    }

    /** Positive = recovering, negative = draining. Only called inside the Backrooms. */
    private static float changePerSecond(ServerPlayer player) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_94) {
            return level94Change(player);
        }
        if (SafeRooms.isSafe(player)) {
            return SAFE_ROOM_RECOVERY; // the hum stops, the lamp is warm: you can breathe
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_37) {
            return LEVEL_37_RECOVERY; // even in its dark tunnels: the whole level is calm
        }
        boolean dark = isDark(player);
        boolean company = !dark && otherPlayerWithin(player, COMPANY_RANGE);

        float change;
        if (company) {
            change = COMPANY_RECOVERY; // a friend in the light replaces the base + loneliness drain
        } else {
            change = -BASE_DRAIN;
            if (!otherPlayerWithin(player, ALONE_RANGE)) {
                change -= ALONE_DRAIN;
            }
        }
        if (dark) {
            change -= DARK_DRAIN;
        }
        change -= monsterDrain(player);
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        if (floor != null && LevelEvents.isBlackout(floor)) {
            change -= BLACKOUT_DRAIN;
        }
        return change;
    }

    /**
     * Level 94 runs on its own clock ({@link Level94Clock}): by day the sunny hills leave you be and
     * a house lets you recover; at night you hold steady indoors but slowly drain outside, faster
     * with a Smiler in view. Minecraft's own light can't tell day from night here (the dimension is
     * stuck at noon), so the dark/loneliness rules of the other levels don't apply.
     */
    private static float level94Change(ServerPlayer player) {
        boolean night = Level94Clock.isNight(player.level());
        if (SafeRooms.isSafe(player)) {
            return night ? 0.0f : LEVEL_94_HOUSE_RECOVERY;
        }
        return (night ? -LEVEL_94_NIGHT_DRAIN : 0.0f) - monsterDrain(player);
    }

    /** Dark at your eyes, and your camcorder flashlight isn't helping. */
    private static boolean isDark(ServerPlayer player) {
        CameraData camera = player.getData(ModAttachments.CAMERA);
        if (camera.flashlight() && !camera.dead()) {
            return false;
        }
        Level level = player.level();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, eyes), level.getBrightness(LightLayer.SKY, eyes));
        return light <= DARK_LIGHT;
    }

    /** True if another (non-spectator) player is within {@code range} blocks in this level. */
    private static boolean otherPlayerWithin(ServerPlayer player, double range) {
        double rangeSq = range * range;
        for (Player other : player.level().players()) {
            if (other != player && !other.isSpectator() && other.distanceToSqr(player) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    /** The strongest drain from each kind of monster near you (they add up across kinds). */
    private static float monsterDrain(ServerPlayer player) {
        Level level = player.level();
        float drain = 0.0f;

        float bacteria = 0.0f;
        AABB bacteriaBox = player.getBoundingBox().inflate(BACTERIA_RANGE);
        for (Bacteria b : level.getEntitiesOfClass(Bacteria.class, bacteriaBox,
                b -> b.distanceToSqr(player) <= BACTERIA_RANGE * BACTERIA_RANGE && player.hasLineOfSight(b))) {
            boolean huntingMe = b.isChasing() && b.getTarget() == player;
            bacteria = Math.max(bacteria, huntingMe ? BACTERIA_CHASE_DRAIN : BACTERIA_SEEN_DRAIN);
        }
        drain += bacteria;

        AABB watcherBox = player.getBoundingBox().inflate(WATCHER_RANGE);
        if (!level.getEntitiesOfClass(Watcher.class, watcherBox,
                w -> w.distanceToSqr(player) <= WATCHER_RANGE * WATCHER_RANGE && player.hasLineOfSight(w)).isEmpty()) {
            drain += WATCHER_DRAIN;
        }

        AABB observerBox = player.getBoundingBox().inflate(OBSERVER_RANGE);
        if (!level.getEntitiesOfClass(Observer.class, observerBox,
                o -> o.distanceToSqr(player) <= OBSERVER_RANGE * OBSERVER_RANGE).isEmpty()) {
            drain += OBSERVER_DRAIN;
        }

        // A Smiler's face in view (it glows, so seeing it is all it takes).
        AABB smilerBox = player.getBoundingBox().inflate(SMILER_RANGE);
        if (!level.getEntitiesOfClass(Smiler.class, smilerBox,
                s -> s.distanceToSqr(player) <= SMILER_RANGE * SMILER_RANGE && player.hasLineOfSight(s)).isEmpty()) {
            drain += SMILER_DRAIN;
        }
        return drain;
    }

    // ---- Sync ------------------------------------------------------------------------------

    /** Sends sanity when the rounded value moved by 1 or more, or every 5 s, or when forced. */
    private static void sync(ServerPlayer player, SanityData data, boolean force) {
        int rounded = Math.round(data.sanity);
        boolean changed = data.lastSentRounded < 0 || Math.abs(rounded - data.lastSentRounded) >= 1;
        boolean stale = player.tickCount - data.lastSentTick >= RESEND_TICKS || player.tickCount < data.lastSentTick;
        if (force || changed || stale) {
            data.lastSentRounded = rounded;
            data.lastSentTick = player.tickCount;
            PacketDistributor.sendToPlayer(player, new SanityPayload(data.sanity));
        }
    }

    /** Forces a resend, e.g. after login/respawn/dimension change when the client state was reset. */
    public static void forceSync(ServerPlayer player) {
        sync(player, player.getData(ModAttachments.SANITY), true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forceSync(player);
        }
    }

    /** After dying you come back shaken: 70. (Leaving the End isn't a death, so it's skipped.) */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!event.isEndConquered()) {
                player.getData(ModAttachments.SANITY).sanity = SanityData.RESPAWN;
            }
            forceSync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forceSync(player);
        }
    }

    private SanityService() {}
}
