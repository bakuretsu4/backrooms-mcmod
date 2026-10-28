package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.block.SupplyContainerBlockEntity;
import com.rickr.backrooms.item.WandererNoteItem;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ChalkRegistry;
import com.rickr.backrooms.registry.LightSupplies;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0HiddenRooms;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Level 0's hidden rooms on the server (floor plans: {@link Level0HiddenRooms}). One behaviour for
 * all four kinds:
 * <ul>
 *   <li><b>What the finder left.</b> The first time anyone steps into the room, its supply
 *       containers get their usual loot plus the things whoever found the room first put by
 *       for the next person (chalk and flares by the tenant's bed, flares and chalk in the
 *       stockroom, medical supplies in the crawlspace's backpack, an honest note to the exit in the
 *       sealed room). Containers remember that they've been filled, so this happens once per world.</li>
 *   <li><b>How the room feels.</b> The tenant's room and the crawlspace are quiet and lived in:
 *       sitting in them slowly calms you. The sealed room has been shut for years and wears on
 *       you a little. The stockroom does nothing to you at all; it just watches.</li>
 * </ul>
 * The crawlspace's field radio is the ordinary checkpoint radio: its dial saves your checkpoint.
 */
public final class HiddenRoomBehavior implements AnomalyBehavior {
    private static final float CALM_PER_SECOND = 0.15f;
    private static final float SEALED_DRAIN_PER_SECOND = 0.05f;

    /** Sites whose containers we've already checked this session (the containers remember for good). */
    private final Set<AnomalySite> stocked = new HashSet<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 20 != 19) {
            return;
        }
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        if (!Level0HiddenRooms.inRoom(site, player.getBlockX(), player.getY(), player.getBlockZ(), layout.ceilingY())) {
            return;
        }
        if (stocked.add(site)) {
            if (stocked.size() > 64) {
                stocked.clear();
                stocked.add(site);
            }
            stock(player.serverLevel(), site, layout);
        }
        if (player.isCreative()) {
            return;
        }
        switch (site.kind()) {
            case Level0HiddenRooms.SLIT, Level0HiddenRooms.CRAWLSPACE -> SanityService.restore(player, CALM_PER_SECOND);
            case Level0HiddenRooms.SEALED -> SanityService.drain(player, SEALED_DRAIN_PER_SECOND);
            default -> {
                // The stockroom: nothing.
            }
        }
    }

    /** Fills every container of the room that nobody has opened yet, and adds the finder's things. */
    private static void stock(ServerLevel level, AnomalySite site, LevelLayout layout) {
        for (Level0HiddenRooms.Stash stash : Level0HiddenRooms.stashes(site, layout.floorY(), layout.ceilingY())) {
            if (!level.isLoaded(stash.pos())) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(stash.pos());
            if (!(entity instanceof SupplyContainerBlockEntity box) || box.isLootFilled()) {
                continue;
            }
            box.fillLootIfNeeded(level, null);
            for (ItemStack stack : extras(level, site.kind(), stash)) {
                put(box, stack);
            }
            box.setChanged();
        }
    }

    /** What the person who found the room first left in this container for the next one. */
    private static List<ItemStack> extras(ServerLevel level, String kind, Level0HiddenRooms.Stash stash) {
        return switch (kind) {
            case Level0HiddenRooms.SLIT -> List.of(new ItemStack(ChalkRegistry.CHALK.get()), new ItemStack(LightSupplies.FLARE.get(), 2));
            case Level0HiddenRooms.STOCKROOM -> stash.what() == 'L'
                    ? List.of(new ItemStack(ChalkRegistry.CHALK.get()), new ItemStack(ModItems.CAMCORDER_BATTERY.get()))
                    : List.of(new ItemStack(LightSupplies.FLARE.get(), 3), new ItemStack(LightSupplies.GLOWSTICK.get(), 2));
            case Level0HiddenRooms.CRAWLSPACE -> List.of(new ItemStack(ModItems.ALMOND_WATER.get(), 2),
                    new ItemStack(ModItems.BANDAGE.get(), 2), new ItemStack(ModItems.ENERGY_BAR.get()));
            default -> stash.what() == 'C'
                    // The sealed room's note never lies: "I checked".
                    ? List.of(WandererNoteItem.create(level, BackroomsLevel.LEVEL_0, stash.pos(), level.getRandom(), true),
                            new ItemStack(LightSupplies.GLOWSTICK.get(), 3))
                    : List.of(new ItemStack(ModItems.ALMOND_WATER.get()), new ItemStack(ModItems.CALM_PILLS.get()),
                            new ItemStack(ModItems.CAMCORDER_BATTERY.get()));
        };
    }

    /** Puts a stack in the first empty slot (a full container just doesn't get it). */
    private static void put(SupplyContainerBlockEntity box, ItemStack stack) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            if (box.getItem(slot).isEmpty()) {
                box.setItem(slot, stack);
                return;
            }
        }
    }
}
