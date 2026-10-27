package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.anomaly.AnomalyService;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The M.E.G. survey camp (canon: the M.E.G. sends a ten-person team into Level 39 every couple of
 * months). Its notes are read by right-clicking them (the client shows the page, see
 * {@code client/anomaly/level39/SurveyNoteScreen}). The crates look empty, but one of them has a false
 * bottom: the team's last few bottles of almond water, wrapped in an armband. Each player can take
 * them once per camp.
 *
 * <p>Supplies you carried in vanish on arrival ({@code world/Level39Rules}), so this is a real find.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class MegCamp {
    /** Bottles of almond water in each camp's stash. */
    private static final int STASH_BOTTLES = 2;
    private static final String CLAIMED_TAG = "backrooms_l39_camp_stashes";

    private MegCamp() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Level39Blocks.MEG_CRATE.get())) {
            return;
        }
        AnomalySite site = AnomalyService.siteAt(level, pos.getX(), pos.getZ());
        if (site == null || !site.kind().equals(Level39Sites.MEG_CAMP)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        long key = BlockPos.asLong(site.anchorX(), 0, site.anchorZ());
        // The "persisted" part of the player's data survives death, so a stash can't be taken twice.
        CompoundTag root = player.getPersistentData();
        CompoundTag data = root.getCompound(Player.PERSISTED_NBT_TAG);
        ListTag claimed = data.getList(CLAIMED_TAG, Tag.TAG_LONG);
        for (Tag t : claimed) {
            if (t instanceof LongTag l && l.getAsLong() == key) {
                player.displayClientMessage(Component.translatable("message.backrooms.l39_camp.empty")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                return;
            }
        }
        claimed.add(LongTag.valueOf(key));
        data.put(CLAIMED_TAG, claimed);
        root.put(Player.PERSISTED_NBT_TAG, data);
        level.playSound(null, pos, Level39AnomalyRegistry.CAMP_STASH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
        ItemStack bottles = new ItemStack(ModItems.ALMOND_WATER.get(), STASH_BOTTLES);
        if (!player.getInventory().add(bottles)) {
            player.drop(bottles, false);
        }
        player.displayClientMessage(Component.translatable("message.backrooms.l39_camp.stash")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
    }
}
