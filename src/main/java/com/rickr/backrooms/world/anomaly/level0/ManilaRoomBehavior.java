package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.block.SupplyContainerBlockEntity;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0ManilaRoom;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Manila Room (https://backrooms-wiki.wikidot.com/manila-room): the one place in Level 0 where
 * the hum fades, a piano plays from nowhere (client side) and the Isolation Effect lets up, kept
 * stocked by volunteers. Its blocks are unbreakable, which fits "the interior never changes".
 * Here on the server:
 * <ul>
 *   <li><b>A real pause.</b> Sitting in it calms you, quickly (faster than anywhere else in
 *       Level 0; a safe room still does more, and keeps monsters out, which this room doesn't
 *       promise). Level 0's local light events never happen in it, and its one warm bulb is on a
 *       battery, so a blackout doesn't reach it either.</li>
 *   <li><b>Somebody keeps it stocked.</b> Come back after a while and a cupboard you emptied has
 *       something in it again: one thing per cupboard, at most every twenty minutes. You never see
 *       who does it: it only happens as you walk in.</li>
 * </ul>
 * The guestbook on the wall (VolunteerNoteBlock.GUESTBOOK) ends with your own name.
 */
public final class ManilaRoomBehavior implements AnomalyBehavior {
    private static final float SANITY_PER_SECOND = 0.5f;
    /** The volunteers come round this often at most (game ticks). */
    private static final long RESTOCK_EVERY = 20L * 60 * 20;

    /** Game time each room was last restocked. */
    private final Map<AnomalySite, Long> restocked = new HashMap<>();

    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = restocked.get(site);
        if (last != null && now - last < RESTOCK_EVERY) {
            return;
        }
        if (restocked.size() > 16) {
            restocked.clear();
        }
        restocked.put(site, now);
        Item[] gifts = {ModItems.ALMOND_WATER.get(), ModItems.CANNED_FOOD.get(), ModItems.BANDAGE.get(), ModItems.CAMCORDER_BATTERY.get()};
        for (BlockPos pos : Level0ManilaRoom.containers(site, BackroomsLevel.LEVEL_0.layout().floorY())) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            // Only cupboards someone has opened and emptied: a full one doesn't need anything.
            if (entity instanceof SupplyContainerBlockEntity box && box.isLootFilled() && box.isEmpty()) {
                box.setItem(level.getRandom().nextInt(box.getContainerSize()), new ItemStack(gifts[level.getRandom().nextInt(gifts.length)]));
                box.setChanged();
            }
        }
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 20 == 19 && Level0ManilaRoom.isInside(site, player.getBlockX(), player.getBlockZ())) {
            SanityService.restore(player, SANITY_PER_SECOND);
        }
    }
}
