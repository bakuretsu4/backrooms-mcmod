package com.rickr.backrooms.client.anomaly.level9;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The cold of Level 9's anomalies, on screen: frost creeping in from the edges of the picture and
 * your breath steaming in front of you, plus the red (and then black) of the Gloom taking you.
 *
 * <p>Canon: Level 9.1 announces itself by the temperature dropping ("you will know it is Level
 * 9.1 if the temperature drops", http://backrooms-mghc.wikidot.com/level-9-1), and the Mangled's
 * fog is preceded by "a sudden drop in temperature" (https://backrooms-wiki.wikidot.com/entity-63).
 *
 * <p>Several things ask for frost at once (the fog event, a walkway, the field), each through its
 * own slot, every tick; the strongest recent request wins. Frost grows quickly and thaws slowly.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level9Frost implements LayeredDraw.Layer {
    public static final int FOG = 0;
    public static final int WALKWAY = 1;
    public static final int FIELD = 2;
    private static final int SLOTS = 3;

    private static final ResourceLocation FROST_TEXTURE = Backrooms.id("textures/misc/l9_frost.png");
    /** A request counts for this long (a little over two ticks). */
    private static final long HOLD_MS = 150L;
    /** Frost spreads this fast (per second) and thaws this fast. */
    private static final float GROW_PER_S = 1.4f;
    private static final float THAW_PER_S = 0.3f;
    /** Breath shows from this much frost on. */
    private static final float BREATH_FROM = 0.3f;
    /** Opacity of the frost texture at full frost (kept low: a heavy overlay hides the level). */
    private static final float MAX_ALPHA = 0.55f;

    private static final float[] WANT = new float[SLOTS];
    private static final long[] WANT_AT = new long[SLOTS];
    private static float frost;
    private static float wantRed;
    private static float wantBlack;
    private static long redAt;
    private static float red;
    private static float black;
    private static long lastFrameMs;
    private static int breathIn = 40;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Ask for {@code level} (0..1) of frost from {@code slot} this tick. */
    public static void request(int slot, float level) {
        WANT[slot] = Mth.clamp(level, 0.0f, 1.0f);
        WANT_AT[slot] = System.currentTimeMillis();
    }

    /** The Gloom: red over the picture, and black on top of it. Call every tick while it applies. */
    public static void requestGloom(float redLevel, float blackLevel) {
        wantRed = Mth.clamp(redLevel, 0.0f, 1.0f);
        wantBlack = Mth.clamp(blackLevel, 0.0f, 1.0f);
        redAt = System.currentTimeMillis();
    }

    /** How frosty the picture is right now (0..1). */
    public static float frost() {
        return frost;
    }

    private static float target(long now) {
        float best = 0.0f;
        for (int i = 0; i < SLOTS; i++) {
            if (now - WANT_AT[i] <= HOLD_MS) {
                best = Math.max(best, WANT[i]);
            }
        }
        return best;
    }

    /** Mod bus: under the HUD, over the world. */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Backrooms.id("level9_frost"), new Level9Frost());
    }

    /** Breath: a small puff in front of your face every few seconds while it's cold enough. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()
                || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_9) {
            return;
        }
        if (frost < BREATH_FROM || --breathIn > 0) {
            return;
        }
        breathIn = 45 + RANDOM.nextInt(35);
        Vec3 look = mc.player.getLookAngle();
        Vec3 mouth = mc.player.getEyePosition().add(look.scale(0.45)).add(0.0, -0.18, 0.0);
        int puffs = 3 + RANDOM.nextInt(3);
        for (int i = 0; i < puffs; i++) {
            mc.level.addParticle(ParticleTypes.WHITE_SMOKE,
                    mouth.x + (RANDOM.nextDouble() - 0.5) * 0.12, mouth.y + (RANDOM.nextDouble() - 0.5) * 0.08,
                    mouth.z + (RANDOM.nextDouble() - 0.5) * 0.12,
                    look.x * 0.025, 0.012 + RANDOM.nextDouble() * 0.01, look.z * 0.025);
        }
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0.0f : Math.min(now - lastFrameMs, 250L) / 1000.0f;
        lastFrameMs = now;
        boolean here = mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9;
        float want = here ? target(now) : 0.0f;
        frost = want > frost ? Math.min(want, frost + GROW_PER_S * dt) : Math.max(want, frost - THAW_PER_S * dt);
        boolean gloom = here && now - redAt <= HOLD_MS;
        red = approach(red, gloom ? wantRed : 0.0f, 2.0f * dt);
        black = approach(black, gloom ? wantBlack : 0.0f, 1.5f * dt);
        if (!here) {
            frost = 0.0f;
            red = 0.0f;
            black = 0.0f;
        }

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        if (frost > 0.003f) {
            // Frost stays a rim on the lens, never a wall: even full frost leaves most of it clear.
            float a = MAX_ALPHA * frost * frost * (3.0f - 2.0f * frost);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1.0f, 1.0f, 1.0f, a);
            graphics.blit(FROST_TEXTURE, 0, 0, w, h, 0.0f, 0.0f, 256, 256, 256, 256);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
        if (red > 0.003f) {
            graphics.fill(0, 0, w, h, (Mth.clamp((int) (red * 200.0f), 0, 255) << 24) | 0x8A0A08);
        }
        if (black > 0.003f) {
            graphics.fill(0, 0, w, h, Mth.clamp((int) (black * 255.0f), 0, 255) << 24);
        }
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
