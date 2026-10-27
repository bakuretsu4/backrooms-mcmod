package com.rickr.backrooms.client.anomaly.level39;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.world.anomaly.level39.Level39AnomalyRegistry;
import com.rickr.backrooms.world.anomaly.level39.WindowShadow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client glue for Level 39's anomalies: the Window Shadow's renderer, the pull of an awake window
 * on your gaze, and opening the M.E.G. survey notes as a page you can read.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39AnomalyClient {
    /** You must keep an awake window within this half-angle (degrees) of your view... */
    private static final double LURE_CONE = 24.0;
    /** ...for this many ticks before it starts drawing your eyes in... */
    private static final int LURE_AFTER = 15;
    /** ...by this fraction of the remaining angle per tick (gentle: you can always look away). */
    private static final float LURE_PULL = 0.035f;
    private static final double LURE_RANGE = 18.0;

    private static int stareTicks;

    private Level39AnomalyClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Level39AnomalyRegistry.WINDOW_SHADOW.get(), WindowShadowRenderer::new);
    }

    /** An awake window you keep looking at slowly pulls your view into its light. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || player.isSpectator()) {
            stareTicks = 0;
            return;
        }
        Vec3 eye = player.getEyePosition();
        WindowShadow best = null;
        double bestCos = Math.cos(Math.toRadians(LURE_CONE));
        for (WindowShadow w : mc.level.getEntitiesOfClass(WindowShadow.class, player.getBoundingBox().inflate(LURE_RANGE))) {
            byte s = w.state();
            if (s != WindowShadow.LURKING && s != WindowShadow.HUNTING) {
                continue;
            }
            Vec3 to = w.glassCentre().subtract(eye);
            double len = to.length();
            if (len < 1.0e-3 || len > LURE_RANGE) {
                continue;
            }
            double cos = player.getLookAngle().dot(to.scale(1.0 / len));
            if (cos > bestCos) {
                bestCos = cos;
                best = w;
            }
        }
        if (best == null) {
            stareTicks = 0;
            return;
        }
        if (++stareTicks < LURE_AFTER) {
            return;
        }
        Vec3 to = best.glassCentre().subtract(eye);
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
        float wantPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        float dYaw = Mth.wrapDegrees(wantYaw - player.getYRot());
        float dPitch = wantPitch - player.getXRot();
        player.setYRot(player.getYRot() + dYaw * LURE_PULL);
        player.setXRot(Mth.clamp(player.getXRot() + dPitch * LURE_PULL, -90.0f, 90.0f));
    }

    /** Right-clicking a survey note opens it as a page (the chat keeps a copy too). */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(Level39Blocks.SURVEY_NOTE.get())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int note = state.getValue(com.rickr.backrooms.block.SurveyNoteBlock.NOTE);
        mc.execute(() -> mc.setScreen(new SurveyNoteScreen(note)));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }
}
