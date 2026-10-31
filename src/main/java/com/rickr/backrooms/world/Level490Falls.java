package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490FallPayload;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The falls you are sure to see in Level 490 (server side). Besides the ruins falling on their own
 * every few minutes (world/Level490Hung), at three moments of the way a hung ruin in front of you
 * breaks loose and falls: as you near the spire's island, at the half-way room of the climb, and
 * when you reach the top. The ruin that falls is chosen among those hanging 700-3000 blocks away,
 * the nearer the better, ahead of where you are looking if any are. Every client is told; the fall
 * is remembered in the save (so it stays fallen) and sent to anyone who arrives later. At the top
 * the Great Whale also comes down beside the spire to look at you (client/Level490Whales).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Falls {
    /** The moments each player has already had on this visit. */
    private static final Map<UUID, Set<String>> GIVEN = new HashMap<>();
    /** A ruin must have hung this long to be picked (so it isn't still emerging from the haze). */
    private static final double SETTLED = 6000;
    /** Forced falls are forgotten this long after they started (by then that ruin's life is over anyway). */
    private static final long FORGET = 72000L * 3;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490 || player.isSpectator()) {
            GIVEN.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        int[] site = Level490Layout.siteCentre(seed, player.getBlockX(), player.getBlockZ());
        double d = Math.hypot(player.getX() - site[0] - 0.5, player.getZ() - site[1] - 0.5);
        double y = player.getY();
        String moment = null;
        if (d < 160 && y > 185 && y < 215) {
            moment = "approach";
        } else if (d < 12 && y >= 307 && y < 330) {
            moment = "half-way";
        } else if (d < 40 && y >= Level490Spire.TOP - 2) {
            moment = "top";
        }
        if (moment != null && GIVEN.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(moment)) {
            fallInFrontOf(level, player, seed);
            if (moment.equals("top")) {
                whaleRises(level, player, site);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GIVEN.remove(event.getEntity().getUUID());
    }

    /** Makes the best hung ruin in front of the player fall a second from now. */
    public static void fallInFrontOf(ServerLevel level, ServerPlayer player, long seed) {
        Map<Long, long[]> forced = forced(level);
        long t = level.getGameTime();
        double yaw = Math.toRadians(player.getYRot());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        Level490Hung.Life best = null;
        double bestScore = -Double.MAX_VALUE;
        int ci = Math.floorDiv(player.getBlockX(), Level490Hung.CELL);
        int cj = Math.floorDiv(player.getBlockZ(), Level490Hung.CELL);
        for (int i = ci - 2; i <= ci + 2; i++) {
            for (int j = cj - 2; j <= cj + 2; j++) {
                Level490Hung.Life life = Level490Hung.life(seed, i, j, t, forced);
                if (life == null || life.falling() || life.age() < SETTLED) {
                    continue;
                }
                double dx = life.x() - player.getX();
                double dz = life.z() - player.getZ();
                double d = Math.hypot(dx, dz);
                if (d < 700 || d > 3000) {
                    continue;
                }
                double ahead = (dx * lookX + dz * lookZ) / d;
                double score = (ahead > 0.4 ? 2.0 : 0.0) + ahead - d / 3000.0;
                if (score > bestScore) {
                    bestScore = score;
                    best = life;
                }
            }
        }
        if (best == null) {
            return;
        }
        long start = t + 20;
        forced.put(Level490Hung.key(best.i(), best.j()), new long[] {best.gen(), start});
        data(level).setDirty();
        Level490FallPayload payload = new Level490FallPayload(best.i(), best.j(), best.gen(), start);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    /**
     * At the top: the Great Whale comes down out of the storm on the far side of the spire from the
     * player, about 130 blocks out, broadside on, so its eye looks at them.
     */
    private static void whaleRises(ServerLevel level, ServerPlayer player, int[] site) {
        double away = Math.atan2(player.getZ() - site[1], player.getX() - site[0]) + Math.PI + 0.5;
        int x = (int) Math.round(site[0] + Math.cos(away) * 130);
        int z = (int) Math.round(site[1] + Math.sin(away) * 130);
        // Its side to the player: heading at right angles to the line between them.
        float yaw = (float) Math.toDegrees(Math.atan2(z - player.getZ(), x - player.getX()) + Math.PI / 2);
        com.rickr.backrooms.network.Level490WhalePayload payload = new com.rickr.backrooms.network.Level490WhalePayload(
                x, Level490Spire.TOP + 10, z, level.getGameTime() + 40, yaw);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    /** Sends a player arriving in Level 490 every fall the server still remembers. */
    static void sendAll(ServerPlayer player) {
        for (Map.Entry<Long, long[]> e : forced(player.serverLevel()).entrySet()) {
            int i = (int) (e.getKey() >> 32);
            int j = (int) (long) e.getKey();
            PacketDistributor.sendToPlayer(player, new Level490FallPayload(i, j, e.getValue()[0], e.getValue()[1]));
        }
    }

    /** The server's forced falls for this level, from the save (old ones dropped). */
    private static Map<Long, long[]> forced(ServerLevel level) {
        Data data = data(level);
        long t = level.getGameTime();
        if (data.falls.entrySet().removeIf(e -> t - e.getValue()[1] > FORGET)) {
            data.setDirty();
        }
        return data.falls;
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "backrooms_level490_falls");
    }

    private static final class Data extends SavedData {
        final Map<Long, long[]> falls = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (Tag t : tag.getList("falls", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                data.falls.put(Level490Hung.key(c.getInt("i"), c.getInt("j")), new long[] {c.getLong("gen"), c.getLong("start")});
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<Long, long[]> e : falls.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putInt("i", (int) (e.getKey() >> 32));
                c.putInt("j", (int) (long) e.getKey());
                c.putLong("gen", e.getValue()[0]);
                c.putLong("start", e.getValue()[1]);
                list.add(c);
            }
            tag.put("falls", list);
            return tag;
        }
    }

    private Level490Falls() {}
}
