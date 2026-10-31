package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Penitent;
import com.rickr.backrooms.entity.Pilgrim;
import com.rickr.backrooms.registry.Level490Mobs;
import com.rickr.backrooms.world.layout.Level490Layout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Puts Level 490's figures where they belong as you come near (after the play-test: "add NPCs"):
 * <ul>
 *   <li>at the set pieces along the walk (world/layout/Level490Layout#spots): a Penitent praying
 *       at each shrine and chapel altar, one kneeling at the graves, the gibbets, the fallen statue;
 *       the pilgrim at his camp;</li>
 *   <li>at the colossal chains near you, one kneeling in chains on the island ground beside it;</li>
 *   <li>on the bridges ahead of you, a few walking slowly toward the spire;</li>
 *   <li>now and then one standing still at the edge of an island, watching you.</li>
 * </ul>
 * About half the Penitents vanish when you come within a few blocks. A spot whose figure has
 * vanished stays empty for a while. Figures are never saved: each takes itself away when nobody
 * is near (entity/Penitent, entity/Pilgrim).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Figures {
    /** Spots are filled within this distance, and not closer than NOT_CLOSER (no popping up beside you). */
    private static final int REACH = 90;
    private static final double NOT_CLOSER = 20;
    private static final int MAX_WALKERS = 3;
    private static final int MAX_WATCHERS = 2;
    /** A spot whose figure vanished stays empty this long. */
    private static final long EMPTY_FOR = 6000;

    /** Spot key -> game time until which it stays empty. */
    private static final Map<Long, Long> EMPTY = new HashMap<>();

    private Level490Figures() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 7 || player.isSpectator()) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (player.getY() < Level490Layout.DECK_Y - 20 || player.getY() > Level490Layout.DECK_Y + 40) {
            return; // falling, or up the spire
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        long now = level.getGameTime();
        EMPTY.values().removeIf(t -> t < now);
        // The set pieces.
        for (Level490Layout.Spot spot : Level490Layout.spots(seed, px, pz, REACH)) {
            double d = Math.hypot(spot.x() + 0.5 - player.getX(), spot.z() + 0.5 - player.getZ());
            if (d > REACH || d < NOT_CLOSER) {
                continue;
            }
            long key = BlockPos.asLong(spot.x(), spot.y(), spot.z());
            if (EMPTY.containsKey(key)) {
                continue;
            }
            AABB around = new AABB(spot.x() - 2, spot.y() - 2, spot.z() - 2, spot.x() + 3, spot.y() + 3, spot.z() + 3);
            if (spot.kind() == Level490Layout.SPOT_PILGRIM) {
                if (level.getEntitiesOfClass(Pilgrim.class, around).isEmpty()) {
                    Pilgrim p = Level490Mobs.PILGRIM.get().create(level);
                    if (p != null) {
                        p.moveTo(spot.x() + 0.5, spot.y(), spot.z() + 0.5, spot.yaw(), 0);
                        p.setHomeYaw(spot.yaw());
                        level.addFreshEntity(p);
                    }
                }
                continue;
            }
            if (level.getEntitiesOfClass(Penitent.class, around).isEmpty()) {
                int pose = spot.kind() == Level490Layout.SPOT_PRAY ? Penitent.PRAY : Penitent.KNEEL;
                place(level, spot.x() + 0.5, spot.y(), spot.z() + 0.5, spot.yaw(), pose, key, seed, 0, 0);
            }
        }
        // By the colossal chains: one kneeling in chains on the ground beside each.
        Level490Layout.chainsNear(seed, px, pz, REACH, (cx, cz, phase) -> {
            double a = Hash.unit(Hash.of(seed, cx, cz, 4990)) * Math.PI * 2;
            for (int t = 0; t < 8; t++) {
                double ang = a + t * Math.PI / 4;
                int x = (int) Math.round(cx + Math.cos(ang) * 14);
                int z = (int) Math.round(cz + Math.sin(ang) * 14);
                int y = ground(level, x, z);
                if (y < 0) {
                    continue;
                }
                double d = Math.hypot(x + 0.5 - player.getX(), z + 0.5 - player.getZ());
                long key = BlockPos.asLong(x, y, z);
                if (d < NOT_CLOSER || d > REACH || EMPTY.containsKey(key)) {
                    return;
                }
                AABB around = new AABB(x - 3, y - 2, z - 3, x + 4, y + 3, z + 4);
                if (level.getEntitiesOfClass(Penitent.class, around).isEmpty()) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-(cx - x), cz - z)); // facing the chain
                    place(level, x + 0.5, y, z + 0.5, yaw, Penitent.KNEEL, key, seed, 0, 0);
                }
                return;
            }
        });
        RandomSource random = level.getRandom();
        int[] site = Level490Layout.siteCentre(seed, px, pz);
        AABB near = player.getBoundingBox().inflate(REACH, 40, REACH);
        // Walkers on the bridges ahead, heading for the spire.
        long walkers = level.getEntitiesOfClass(Penitent.class, near, p -> p.pose() == Penitent.WALK).size();
        if (walkers < MAX_WALKERS && random.nextInt(4) == 0) {
            double toward = Math.atan2(site[1] - player.getZ(), site[0] - player.getX());
            double ang = toward + (random.nextDouble() - 0.5) * 1.2;
            double dist = 35 + random.nextDouble() * 30;
            int x = (int) Math.round(player.getX() + Math.cos(ang) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(ang) * dist);
            int y = ground(level, x, z);
            if (y >= 0 && Math.hypot(site[0] - x, site[1] - z) > 50) {
                place(level, x + 0.5, y, z + 0.5, (float) Math.toDegrees(ang) - 90, Penitent.WALK, 0, seed, site[0] + 0.5, site[1] + 0.5);
            }
        }
        // Now and then one standing still, out on an island, watching you.
        long watchers = level.getEntitiesOfClass(Penitent.class, near, p -> p.pose() == Penitent.STAND).size();
        if (watchers < MAX_WATCHERS && random.nextInt(10) == 0) {
            double ang = random.nextDouble() * Math.PI * 2;
            double dist = 30 + random.nextDouble() * 40;
            int x = (int) Math.round(player.getX() + Math.cos(ang) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(ang) * dist);
            int y = ground(level, x, z);
            if (y >= 0) {
                float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - x), player.getZ() - z));
                place(level, x + 0.5, y, z + 0.5, yaw, Penitent.STAND, 0, seed, 0, 0);
            }
        }
    }

    /** Feet height on solid ground at (x, z) near the islands' level, or -1 (over the void or loaded only partly). */
    private static int ground(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return -1;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y < Level490Layout.DECK_Y - 12 || y > Level490Layout.DECK_Y + 14) {
            return -1;
        }
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
            return -1;
        }
        return y;
    }

    private static void place(ServerLevel level, double x, int y, double z, float yaw, int pose, long key, long seed, double gx, double gz) {
        Penitent p = Level490Mobs.PENITENT.get().create(level);
        if (p == null) {
            return;
        }
        // About half of them aren't there when you come close (never the walkers: you see them go).
        boolean vanishes = pose != Penitent.WALK && level.getRandom().nextBoolean();
        p.setup(pose, vanishes, gx, gz);
        p.moveTo(x, y, z, yaw, 0);
        p.setYHeadRot(yaw);
        p.setYBodyRot(yaw);
        if (vanishes && key != 0) {
            p.onVanish(() -> EMPTY.put(key, level.getGameTime() + EMPTY_FOR));
        }
        level.addFreshEntity(p);
    }
}
