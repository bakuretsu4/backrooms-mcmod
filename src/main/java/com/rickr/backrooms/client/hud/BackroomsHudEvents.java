package com.rickr.backrooms.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.CamcorderEffect;
import com.rickr.backrooms.world.Travel;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;

/**
 * The vanilla HUD, Backrooms style: as little game on screen as possible, so it feels like
 * footage, not a game. No crosshair, block outline, XP bar or hand; hearts, hunger and air hidden until they
 * change (then they fade in for a few seconds, and stay faintly in view while your health is low);
 * a hotbar that slides out of view when you're not using it (switching items still shows the item's
 * name for a moment); no tutorial hints or advancement pop-ups; no name tags above anyone's head.
 * Outside the Backrooms (unless you're filming with a camcorder) nothing here does anything; the
 * name tags follow only the dimension, so in the overworld they are always vanilla.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class BackroomsHudEvents {
    /** Layers not drawn at all in the Backrooms. */
    private static final Set<ResourceLocation> HIDDEN = Set.of(
            VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.JUMP_METER);
    /** Status layers: hidden until something in them changes. */
    private static final Set<ResourceLocation> STATUS = Set.of(
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH);
    /**
     * Status icons after a change: this opacity for {@link OsdStyle#HOLD_MS}, then fading out with
     * the same timing as the camcorder readouts ({@link OsdStyle.Fade}).
     */
    private static final float STATUS_ALPHA = 0.6f;
    private static final OsdStyle.Fade STATUS_FADE = new OsdStyle.Fade();
    /**
     * At this health or below the hearts stay in view, slowly breathing between these two
     * opacities, so a low-health warning is never missed but never flashes.
     */
    private static final float LOW_HEALTH = 6.0f;
    private static final float LOW_HEALTH_MIN = 0.35f;
    private static final float LOW_HEALTH_MAX = 0.65f;
    private static final long LOW_HEALTH_PULSE_MS = 1400L;

    private static float lastHealth = -1.0f;
    private static int lastFood = -1;
    private static int lastAir = -1;
    private static int lastArmor = -1;

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
        float health = mc.player.getHealth();
        int food = mc.player.getFoodData().getFoodLevel();
        int air = mc.player.getAirSupply();
        int armor = mc.player.getArmorValue();
        if (health != lastHealth || food != lastFood || air != lastAir || armor != lastArmor) {
            STATUS_FADE.wake(System.currentTimeMillis());
            lastHealth = health;
            lastFood = food;
            lastAir = air;
            lastArmor = armor;
        }
        // No pop-ups over the footage: tutorial hints and advancement toasts.
        mc.getToasts().clear();
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
    }

    /** Status icons' opacity now: shown for a while after a change, breathing while health is low, else 0. */
    private static float statusAlpha(Minecraft mc) {
        long now = System.currentTimeMillis();
        float alpha = STATUS_FADE.update(now, STATUS_ALPHA, 0.0f);
        if (mc.player != null && mc.player.getHealth() <= LOW_HEALTH) {
            alpha = Math.max(alpha, Mth.lerp(OsdStyle.pulse(now, LOW_HEALTH_PULSE_MS), LOW_HEALTH_MIN, LOW_HEALTH_MAX));
        }
        return alpha;
    }

    /** No black outline round the block you look at: there's no crosshair to aim with either. */
    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (CamcorderEffect.wanted(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    /** No hand or held item in the shot: it's a camcorder, not your eyes. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (CamcorderEffect.wanted(Minecraft.getInstance())) {
            event.setCanceled(true);
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
        } else if (STATUS.contains(name)) {
            float alpha = statusAlpha(mc);
            if (alpha <= 0.01f) {
                event.setCanceled(true);
                return;
            }
            // These icons are drawn straight away with the shader colour as a multiplier, so an
            // alpha below 1 here makes them translucent. Flush first so earlier layers aren't affected.
            g.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
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

    /**
     * No floating names in the Backrooms: you only know who's who by finding them.
     *
     * <p>This hides EVERY entity's tag, not just players'. Impostors copy a player's name and skin
     * (the Poolrooms' PhantomFriend, Skin-stealers, anything added later): if real players had no
     * tag but an impostor had one, or the other way round, the tag alone would give it away.
     * Hiding them all keeps the rule simple and covers future impostors without a list to update.
     *
     * <p>Checked against the dimension you are in (not the camcorder), so the overworld stays
     * vanilla even while filming there. Only the floating tag is touched: chat, the tab list and
     * death messages still use the names.
     */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && Travel.inBackrooms(mc.level)) {
            event.setCanRender(TriState.FALSE);
        }
    }

    private BackroomsHudEvents() {}
}
