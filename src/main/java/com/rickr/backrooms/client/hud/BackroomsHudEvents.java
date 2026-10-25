package com.rickr.backrooms.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.CamcorderEffect;
import com.rickr.backrooms.entity.PhantomFriend;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;

/**
 * The vanilla HUD, Backrooms style (like the reference): no crosshair or XP bar, hearts and hunger
 * drawn faint, a hotbar that slides out of view when you're not using it, and no player nametags.
 * Outside the Backrooms nothing here does anything.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class BackroomsHudEvents {
    /** Layers not drawn at all in the Backrooms. */
    private static final Set<ResourceLocation> HIDDEN = Set.of(
            VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.JUMP_METER);
    /** Layers drawn see-through at {@link #FADED_ALPHA}. */
    private static final Set<ResourceLocation> FADED = Set.of(
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH);
    /** Hearts / hunger / armour opacity: present if you look for them, invisible in the footage. */
    private static final float FADED_ALPHA = 0.35f;

    /** Hotbar: hides after this long without a slot change... */
    private static final long HOTBAR_HOLD_MS = 3000L;
    /** ...taking this long to slide away or back... */
    private static final float HOTBAR_SLIDE_MS = 500.0f;
    /** ...by this many GUI pixels (the hotbar is 22 tall, so this is fully off-screen). */
    private static final float HOTBAR_SLIDE_PX = 30.0f;

    private static int lastSlot = -1;
    private static long lastSlotChangeMs;
    /** 0 = hotbar fully shown, 1 = fully hidden. Moves linearly; the easing is applied when drawing. */
    private static float hotbarHidden;
    private static long lastHotbarFrameNs;

    /** Which layer we changed render state for in Pre, so Post undoes exactly that. */
    private static ResourceLocation pendingRestore;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !CamcorderEffect.wanted(mc)) {
            lastSlot = -1; // show the hotbar again right away next time we arrive
            hotbarHidden = 0.0f;
            return;
        }
        int slot = mc.player.getInventory().selected;
        if (slot != lastSlot) {
            lastSlot = slot;
            lastSlotChangeMs = System.currentTimeMillis();
        }
    }

    /** Lowest priority, so if another mod cancels the layer we never push state we can't pop. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLayerPre(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!CamcorderEffect.wanted(mc)) {
            return;
        }
        ResourceLocation name = event.getName();
        GuiGraphics g = event.getGuiGraphics();
        if (HIDDEN.contains(name)) {
            event.setCanceled(true);
        } else if (FADED.contains(name)) {
            // These icons are drawn straight away with the shader colour as a multiplier, so an
            // alpha below 1 here makes them translucent. Flush first so earlier layers aren't affected.
            g.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, FADED_ALPHA);
            pendingRestore = name;
        } else if (name.equals(VanillaGuiLayers.HOTBAR)) {
            float offset = hotbarOffset();
            if (offset >= HOTBAR_SLIDE_PX - 0.01f) {
                event.setCanceled(true); // fully off-screen: skip drawing it at all
                return;
            }
            g.pose().pushPose();
            g.pose().translate(0.0f, offset, 0.0f);
            pendingRestore = name;
        }
    }

    @SubscribeEvent
    public static void onLayerPost(RenderGuiLayerEvent.Post event) {
        ResourceLocation name = event.getName();
        if (pendingRestore == null || !pendingRestore.equals(name)) {
            return;
        }
        pendingRestore = null;
        GuiGraphics g = event.getGuiGraphics();
        if (name.equals(VanillaGuiLayers.HOTBAR)) {
            g.pose().popPose();
        } else {
            g.flush();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /** Hotbar slide: 0 shown .. {@link #HOTBAR_SLIDE_PX} hidden, eased in and out (smoothstep). */
    private static float hotbarOffset() {
        long nowNs = System.nanoTime();
        float dtMs = lastHotbarFrameNs == 0L ? 0.0f : Math.min((nowNs - lastHotbarFrameNs) / 1.0e6f, 100.0f);
        lastHotbarFrameNs = nowNs;
        boolean hide = System.currentTimeMillis() - lastSlotChangeMs > HOTBAR_HOLD_MS;
        float step = dtMs / HOTBAR_SLIDE_MS;
        hotbarHidden = hide ? Math.min(1.0f, hotbarHidden + step) : Math.max(0.0f, hotbarHidden - step);
        float x = hotbarHidden;
        return x * x * (3.0f - 2.0f * x) * HOTBAR_SLIDE_PX;
    }

    /** No floating player names in the Backrooms: you only know who's who by finding them. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        // The phantom friend hides its tag too, or it would be the only "player" wearing one.
        boolean playerLike = event.getEntity() instanceof Player || event.getEntity() instanceof PhantomFriend;
        if (playerLike && CamcorderEffect.wanted(Minecraft.getInstance())) {
            event.setCanRender(TriState.FALSE);
        }
    }

    private BackroomsHudEvents() {}
}
