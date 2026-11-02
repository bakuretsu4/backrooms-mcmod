package com.rickr.backrooms.client.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.SignKind;
import com.rickr.backrooms.block.StreetSignBlock;
import com.rickr.backrooms.block.level9.KidsDrawingBlock;
import com.rickr.backrooms.block.level9.MissingPosterBlock;
import com.rickr.backrooms.block.level9.WatchFlyerBlock;
import com.rickr.backrooms.item.WatchFlyerItem;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.Hash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Reading Level 9's papers (client side). Right-click opens the close-up page
 * ({@link Level9PaperScreen}) for:
 * <ul>
 *   <li>a missing-person poster: one poster in {@link #OWN_POSTER_ONE_IN} shows YOUR name and
 *       face instead. That is decided here from the poster's position, on your own client only,
 *       so another player looking at the same poster sees the normal one (or their own name);</li>
 *   <li>a pinned Neighborhood Watch flyer (sneak-click takes it instead, on the server);</li>
 *   <li>a Neighborhood Watch / Quiet Zone notice board on a sign post: a printed copy of the rules;</li>
 *   <li>a child's drawing;</li>
 *   <li>and right-clicking a Watch flyer item in your hand.</li>
 * </ul>
 * The server side of these blocks only answers "handled" (so the hand swings and nothing else
 * happens); the reading itself never needs the server.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level9StoryClient {
    /** One poster in this many (picked by its position) has the player's own name and face on it. */
    private static final int OWN_POSTER_ONE_IN = 20;

    private Level9StoryClient() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        Level9PaperScreen screen = null;
        if (state.is(ModBlocks.MISSING_POSTER.get())) {
            boolean own = Hash.range(Hash.of(0x9F05_7E25L, pos.getX(), pos.getZ(), pos.getY()), OWN_POSTER_ONE_IN) == 0;
            screen = Level9PaperScreen.poster(state.getValue(MissingPosterBlock.PERSON), own);
        } else if (state.is(ModBlocks.WATCH_FLYER_PINNED.get())) {
            if (event.getEntity().isShiftKeyDown()) {
                return; // pulling it off the post: the server handles that
            }
            screen = Level9PaperScreen.flyer("flyer.backrooms.l9." + state.getValue(WatchFlyerBlock.FLYER));
        } else if (state.is(ModBlocks.KIDS_DRAWING.get())) {
            screen = Level9PaperScreen.drawing(state.getValue(KidsDrawingBlock.DRAWING));
        } else if (state.is(ModBlocks.STREET_SIGN.get())) {
            SignKind kind = state.getValue(StreetSignBlock.KIND);
            if (kind == SignKind.WATCH_NOTICE) {
                // The board repeats one of the flyers (the same one every time for this board).
                int flyer = Hash.range(Hash.of(0x3A7C_11E9L, pos.getX(), pos.getZ(), pos.getY()), WatchFlyerItem.FLYERS);
                screen = Level9PaperScreen.flyer("flyer.backrooms.l9." + flyer);
            } else if (kind == SignKind.QUIET_NOTICE) {
                screen = Level9PaperScreen.flyer("flyer.backrooms.l9.quiet");
            } else {
                return;
            }
            // Street signs do nothing on the server: stop here, but still swing the hand.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
        if (screen != null) {
            open(screen);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!event.getLevel().isClientSide() || !stack.is(ModItems.WATCH_FLYER.get())) {
            return;
        }
        // A flyer that has not picked its text yet (it does within a tick of reaching an inventory) reads as the first.
        open(Level9PaperScreen.flyer("flyer.backrooms.l9." + Math.max(0, WatchFlyerItem.flyerOf(stack))));
    }

    private static void open(Level9PaperScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(screen));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }
}
