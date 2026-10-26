package com.rickr.backrooms.client.anomaly.level37;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The picture going white (a void opening) or black (a dark opening), drawn over everything.
 *
 * <p>The effects ask for how white / black they want the picture each tick ({@link #requestWhite},
 * {@link #requestBlack}); the overlay eases toward it. An effect asking for all of it means "you
 * are in it now" (in the gap, in the darkness). If you leave Level 37 right after that, the next
 * level starts the same way and fades in: you "come to" in Level 0 out of the white, or back in
 * the Poolrooms out of the dark. (Leaving any other way, e.g. dying, shows nothing.)
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level37AnomalyOverlay implements LayeredDraw.Layer {
    /** Overexposed warm white, the same as the sunlit archway's glow. */
    private static final int WHITE_RGB = 0xFFF6E2;
    /** Most the picture changes per tick toward what is asked: from nothing to full in ~12 ticks. */
    private static final float RISE_PER_TICK = 0.085f;
    private static final float FALL_PER_TICK = 0.1f;
    /** Leaving Level 37 this soon after being "in it" counts as being taken (ms). */
    private static final long TAKEN_WITHIN_MS = 3000L;
    /** After the void: white held this long, then Level 0 fades in over ... */
    private static final long WHITE_HOLD_MS = 700L;
    private static final float WHITE_FADE_MS = 4500.0f;
    /** After the dark: black held this long, then the Poolrooms fade in over ... */
    private static final long BLACK_HOLD_MS = 500L;
    private static final float BLACK_FADE_MS = 2500.0f;

    private static float white;
    private static float black;
    private static float whiteWant;
    private static float blackWant;
    private static long inWhiteMs;
    private static long inBlackMs;
    private static long arriveWhiteMs;
    private static long arriveBlackMs;
    private static BackroomsLevel lastFloor;

    /** An effect wants the picture at least this white (0..1) this tick; 1 = you are in the void. */
    static void requestWhite(float amount) {
        whiteWant = Math.max(whiteWant, amount);
    }

    /** An effect wants the picture at least this black (0..1) this tick; 1 = you are in the dark. */
    static void requestBlack(float amount) {
        blackWant = Math.max(blackWant, amount);
    }

    /**
     * Before the anomaly effects run (they run after the tick): take last tick's requests, then
     * start collecting this tick's.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (whiteWant >= 1.0f) {
            inWhiteMs = now;
        }
        if (blackWant >= 1.0f) {
            inBlackMs = now;
        }
        white = ease(white, whiteWant);
        black = ease(black, blackWant);
        whiteWant = 0.0f;
        blackWant = 0.0f;
        if (mc.level == null) {
            return; // between levels: keep the last one
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(mc.level.dimension());
        if (lastFloor == BackroomsLevel.LEVEL_37 && floor != BackroomsLevel.LEVEL_37) {
            if (floor == BackroomsLevel.LEVEL_0 && now - inWhiteMs < TAKEN_WITHIN_MS) {
                arriveWhiteMs = now;
            } else if (floor == BackroomsLevel.POOLROOMS && now - inBlackMs < TAKEN_WITHIN_MS) {
                arriveBlackMs = now;
            }
            white = 0.0f;
            black = 0.0f;
        }
        lastFloor = floor;
    }

    private static float ease(float shown, float want) {
        return shown + Mth.clamp(want - shown, -FALL_PER_TICK, RISE_PER_TICK);
    }

    /** Mod bus: drawn over everything else. */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Backrooms.id("level37_anomaly_fade"), new Level37AnomalyOverlay());
    }

    /** 0..1 how much an arrival fade still covers the picture, {@code since} ms after arriving. */
    private static float arrival(long since, long hold, float fade) {
        if (since < 0 || since > hold + fade) {
            return 0.0f;
        }
        if (since < hold) {
            return 1.0f;
        }
        float t = 1.0f - (since - hold) / fade;
        return t * t;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        long now = System.currentTimeMillis();
        float b = Math.max(black, arrival(now - arriveBlackMs, BLACK_HOLD_MS, BLACK_FADE_MS));
        float w = Math.max(white, arrival(now - arriveWhiteMs, WHITE_HOLD_MS, WHITE_FADE_MS));
        if (b > 0.003f) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha(b) << 24);
        }
        if (w > 0.003f) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha(w) << 24) | WHITE_RGB);
        }
    }

    private static int alpha(float amount) {
        return Mth.clamp((int) (amount * 255.0f), 0, 255);
    }
}
