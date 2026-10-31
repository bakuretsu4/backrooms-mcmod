package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490ShacklePayload;
import com.rickr.backrooms.network.Level490StrugglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Chained on a punishment platform (Level 490, world/Level490Hunt): you can't move; two heavy chains
 * run from the pillar to your wrists. Press jump to pull at them: each pull rattles them and jerks
 * the view; enough and they give (the server counts). A line at the bottom of the screen says so.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Shackles {
    /** Must match world/Level490Hunt.PULLS. */
    private static final int PULLS = 12;
    private static final int IRON = FigureBoxes.rgb(0.16f, 0.16f, 0.18f);

    private static boolean on;
    private static int[] pillar;
    private static int pulls;
    private static long jerkMs;

    private Level490Shackles() {}

    /** Network: chained to the pillar at (x, y, z), or free. */
    public static void receive(Level490ShacklePayload p) {
        on = p.on();
        pillar = new int[] {p.x(), p.y(), p.z()};
        pulls = 0;
        // Jumps pressed before you were chained don't count as pulls.
        while (on && Minecraft.getInstance().options.keyJump.consumeClick()) {
            pulls = 0;
        }
    }

    /** Nothing moves you while you are chained. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!on) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null) {
            if (mc.player == null) {
                on = false;
            }
            return;
        }
        if (!Level490Atmosphere.inLevel490()) {
            on = false;
            return;
        }
        while (mc.options.keyJump.consumeClick()) {
            pulls++;
            jerkMs = System.currentTimeMillis();
            PacketDistributor.sendToServer(new Level490StrugglePayload());
        }
    }

    /** Each pull jerks the view a little. */
    @SubscribeEvent
    public static void onCamera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (!on) {
            return;
        }
        long since = System.currentTimeMillis() - jerkMs;
        if (since < 250) {
            float f = 1 - since / 250f;
            event.setPitch(event.getPitch() + 3.5f * f);
            event.setRoll(event.getRoll() + (float) Math.sin(since * 0.08) * 2.0f * f);
        }
    }

    /** The chains from the pillar's top to your wrists. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!on || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 me = mc.player.getPosition(pt);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(WatcherRenderTypes.SOLID);
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(me.x, me.y + 1, me.z));
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double rx = Math.cos(yaw);
        double rz = Math.sin(yaw);
        float sag = (float) Math.sin((System.currentTimeMillis() - jerkMs) * 0.02) * (System.currentTimeMillis() - jerkMs < 400 ? 0.2f : 0f);
        for (int side = -1; side <= 1; side += 2) {
            float wx = (float) (me.x + rx * side * 0.35);
            float wz = (float) (me.z + rz * side * 0.35);
            float px = pillar[0] + 0.5f + (float) rx * side * 0.3f;
            float pz = pillar[2] + 0.5f + (float) rz * side * 0.3f;
            float py = pillar[1] + 0.2f;
            float my = (float) (me.y + 0.9f);
            // Two runs, sagging in the middle.
            float mx = (px + wx) / 2;
            float mz = (pz + wz) / 2;
            float mid = Math.min(py, my) - 0.6f + sag;
            FigureBoxes.chain(poses, out, px, py, pz, mx, mid, mz, IRON, light);
            FigureBoxes.chain(poses, out, mx, mid, mz, wx, my, wz, IRON, light);
        }
        poses.popPose();
        buffers.endBatch(WatcherRenderTypes.SOLID);
    }

    /** What to do: a line at the bottom of the screen, with how far you are from tearing free. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!on) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        Component line = Component.translatable("hud.backrooms.level490.struggle", mc.options.keyJump.getTranslatedKeyMessage());
        int tw = mc.font.width(line);
        int y = h - 72;
        g.fill(w / 2 - tw / 2 - 6, y - 4, w / 2 + tw / 2 + 6, y + 18, 0x99000000);
        g.drawString(mc.font, line, w / 2 - tw / 2, y, 0xFFE6E2D6, false);
        float f = Math.min(1f, pulls / (float) PULLS);
        int bx = w / 2 - tw / 2;
        g.fill(bx, y + 12, bx + tw, y + 14, 0x40FFFFFF);
        g.fill(bx, y + 12, bx + (int) (tw * f), y + 14, 0xFFC9B94A);
    }
}
