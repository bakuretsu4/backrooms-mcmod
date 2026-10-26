package com.rickr.backrooms.client.menu;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.Backrooms;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.opengl.GL11;

/**
 * The animated "found footage" behind the main menu screens: a slideshow of screenshots from the
 * levels, each slowly panning and zooming (a "Ken Burns" move), with a VHS glitch cut between
 * scenes, fluorescent-light flicker, film grain, scanlines and a vignette on top.
 * <p>
 * Everything here is static so the tape keeps rolling when you move between the title screen and
 * the Field Notes. Timing uses the real clock ({@link Util#getMillis()}), not game ticks, so the
 * motion is smooth at any frame rate.
 */
public final class FootageBackground {
    /** Screenshots: 0 = Level 0, 1 = Level 1, 2 = Level 2, 3 = the Poolrooms, 4 = Level 9, 5 = Level 4, 6 = Level 37. */
    public static final int SCENE_COUNT = 7;
    private static final ResourceLocation[] SCENES = new ResourceLocation[SCENE_COUNT];
    static {
        for (int i = 0; i < SCENE_COUNT; i++) {
            SCENES[i] = texture("scene_" + i);
        }
    }
    private static final ResourceLocation NOISE = texture("noise");
    private static final ResourceLocation VIGNETTE = texture("vignette");

    // Texture sizes in pixels (blit needs to know them to compute texture coordinates).
    // The screenshots are 1280x720 (16:9); VHS footage is lo-fi anyway and it keeps the jar small.
    private static final int SCENE_W = 1280;
    private static final int SCENE_H = 720;
    private static final int NOISE_SIZE = 256;
    private static final int VIGNETTE_W = 512;
    private static final int VIGNETTE_H = 288;

    /** How long each scene is shown. */
    private static final long SCENE_MS = 14_000L;
    /** The glitch cut lasts 0.5 s: the last 0.25 s of the old scene and the first 0.25 s of the new one. */
    private static final long GLITCH_HALF_MS = 250L;
    /** A short black frame right at the cut. */
    private static final long BLACK_FRAME_MS = 60L;
    /** The picture is torn into this many horizontal bands during a glitch. */
    private static final int TEAR_BANDS = 12;
    /**
     * Opacity of the film grain. noise.png is already very faint (its own alpha is 0..60 of 255),
     * so it is drawn at full strength; lower this to make the grain subtler.
     */
    private static final float GRAIN_ALPHA = 1.0f;
    /** Scanlines: a 1-pixel dark line every 3 screen pixels at ~12% opacity. */
    private static final int SCANLINE_COLOR = 0x1F000000;
    private static final int SCANLINE_SPACING = 3;

    private static final RandomSource RANDOM = RandomSource.create();

    // Slideshow state. The tape starts on a random scene.
    private static int scene = RANDOM.nextInt(SCENE_COUNT);
    /** The scene before the last cut, or -1 at the very start (nothing to glitch in from). */
    private static int previousScene = -1;
    /** When the current scene started; -1 until the first frame is drawn. */
    private static long sceneStartMs = -1L;
    private static Move move = Move.random();

    // Fluorescent flicker: every 6-20 s a burst of 2-4 quick dips to dark.
    private static long nextFlickerBurstMs;
    private static int dipsLeft;
    private static long nextDipMs;
    private static int dipFramesLeft;
    private static float dipAlpha;

    /** The caption of the scene on screen, e.g. "LEVEL 0". */
    public static Component caption() {
        return VhsText.translatable("menu.backrooms.scene." + scene);
    }

    /** Draws the whole animated background over the screen (call from Screen#renderBackground). */
    public static void render(GuiGraphics g, int width, int height) {
        long now = Util.getMillis();
        advance(now);
        long elapsed = now - sceneStartMs;
        float progress = Mth.clamp(elapsed / (float) SCENE_MS, 0.0f, 1.0f);

        // The glitch cut: strength 0..1 peaking at the cut, plus the scene the torn bands borrow from.
        float glitch = 0.0f;
        int otherScene = -1;
        boolean blackFrame = false;
        if (elapsed > SCENE_MS - GLITCH_HALF_MS) {
            glitch = (elapsed - (SCENE_MS - GLITCH_HALF_MS)) / (float) GLITCH_HALF_MS;
            otherScene = (scene + 1) % SCENE_COUNT;
        } else if (previousScene >= 0 && elapsed < GLITCH_HALF_MS) {
            glitch = 1.0f - elapsed / (float) GLITCH_HALF_MS;
            otherScene = previousScene;
            blackFrame = elapsed < BLACK_FRAME_MS;
        }

        g.fill(0, 0, width, height, 0xFF000000);
        drawScene(g, width, height, progress, glitch, otherScene);
        drawFlicker(g, width, height, now);
        if (glitch > 0.0f) {
            // White-noise burst: wash the picture grey, then pile up the noise texture.
            g.fill(0, 0, width, height, VhsText.withAlpha(0x8A8A8A, 0.45f * glitch));
            for (int i = 0; i < 3; i++) {
                drawNoise(g, width, height, 1.0f);
            }
        }
        if (blackFrame) {
            g.fill(0, 0, width, height, 0xFF000000);
        }
        drawNoise(g, width, height, GRAIN_ALPHA);
        drawScanlines(g);
        drawVignette(g, width, height);
    }

    /** Moves the slideshow on to the next scene when the current one has been shown long enough. */
    private static void advance(long now) {
        if (sceneStartMs < 0L) {
            sceneStartMs = now;
            nextFlickerBurstMs = now + 6_000L + RANDOM.nextInt(14_001);
            return;
        }
        long elapsed = now - sceneStartMs;
        if (elapsed < SCENE_MS) {
            return;
        }
        previousScene = scene;
        scene = (scene + 1) % SCENE_COUNT;
        move = Move.random();
        // Normally the next scene starts exactly at the cut. If the menu wasn't drawn for a while
        // (you were in a world), just start the next scene now.
        sceneStartMs = elapsed < SCENE_MS * 2 ? sceneStartMs + SCENE_MS : now;
    }

    /**
     * Draws the current scene "cover-fitted" (it fills the window and keeps its 16:9 shape; the
     * parts that don't fit are cropped) with the slow pan/zoom. During a glitch the picture is
     * drawn as horizontal bands shifted sideways, some of them borrowed from the other scene.
     */
    private static void drawScene(GuiGraphics g, int width, int height, float progress, float glitch, int otherScene) {
        float cover = Math.max(width / (float) SCENE_W, height / (float) SCENE_H);
        float scale = cover * Mth.lerp(progress, move.zoomFrom, move.zoomTo);
        // How far the zoomed picture sticks out past each edge; panning stays inside that.
        float slackX = (SCENE_W * scale - width) / 2.0f;
        float slackY = (SCENE_H * scale - height) / 2.0f;
        float x = -slackX + Mth.lerp(progress, move.fromX, move.toX) * slackX;
        float y = -slackY + Mth.lerp(progress, move.fromY, move.toY) * slackY;

        ResourceLocation texture = SCENES[scene];
        prepare(texture, true, false);
        if (otherScene >= 0) {
            prepare(SCENES[otherScene], true, false);
        }
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        // Work in screenshot pixels: translate + scale with floats so the pan moves smoothly.
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        if (glitch <= 0.0f) {
            g.blit(texture, 0, 0, SCENE_W, SCENE_H, 0.0f, 0.0f, SCENE_W, SCENE_H, SCENE_W, SCENE_H);
        } else {
            int bandH = SCENE_H / TEAR_BANDS;
            for (int i = 0; i < TEAR_BANDS; i++) {
                int shift = 0;
                if (RANDOM.nextFloat() < 0.35f + 0.5f * glitch) {
                    shift = Math.round((RANDOM.nextFloat() * 2.0f - 1.0f) * SCENE_W * 0.12f * glitch);
                }
                ResourceLocation band = otherScene >= 0 && RANDOM.nextFloat() < 0.3f * glitch ? SCENES[otherScene] : texture;
                int v = i * bandH;
                g.blit(band, shift, v, SCENE_W, bandH, 0.0f, (float) v, SCENE_W, bandH, SCENE_W, SCENE_H);
            }
        }
        g.pose().popPose();
    }

    /** The lights stutter: a black veil for 1-3 frames, 2-4 times in quick succession. */
    private static void drawFlicker(GuiGraphics g, int width, int height, long now) {
        if (dipFramesLeft == 0) {
            if (dipsLeft > 0 && now >= nextDipMs) {
                dipsLeft--;
                dipFramesLeft = 1 + RANDOM.nextInt(3);
                dipAlpha = 0.4f + RANDOM.nextFloat() * 0.3f;
                nextDipMs = now + 50L + RANDOM.nextInt(111);
            } else if (dipsLeft == 0 && now >= nextFlickerBurstMs) {
                dipsLeft = 2 + RANDOM.nextInt(3);
                nextDipMs = now;
                nextFlickerBurstMs = now + 6_000L + RANDOM.nextInt(14_001);
            }
        }
        if (dipFramesLeft > 0) {
            dipFramesLeft--;
            g.fill(0, 0, width, height, VhsText.withAlpha(0x000000, dipAlpha));
        }
    }

    /** Tiles noise.png over the screen at a random offset every frame (one noise pixel = 2 screen pixels). */
    private static void drawNoise(GuiGraphics g, int width, int height, float alpha) {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        float noisePixelsPerGuiPixel = (float) (guiScale / 2.0);
        int uSpan = Math.max(1, Mth.ceil(width * noisePixelsPerGuiPixel));
        int vSpan = Math.max(1, Mth.ceil(height * noisePixelsPerGuiPixel));
        prepare(NOISE, false, true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        g.blit(NOISE, 0, 0, width, height, (float) RANDOM.nextInt(NOISE_SIZE), (float) RANDOM.nextInt(NOISE_SIZE),
                uSpan, vSpan, NOISE_SIZE, NOISE_SIZE);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /** Faint dark lines across the picture, in real screen pixels whatever the GUI scale. */
    @SuppressWarnings("deprecation") // drawManaged batches the few hundred fills into one draw call
    private static void drawScanlines(GuiGraphics g) {
        Window window = Minecraft.getInstance().getWindow();
        int screenW = window.getWidth();
        int screenH = window.getHeight();
        float toScreen = (float) (1.0 / window.getGuiScale());
        g.pose().pushPose();
        g.pose().scale(toScreen, toScreen, 1.0f);
        g.drawManaged(() -> {
            for (int y = 0; y < screenH; y += SCANLINE_SPACING) {
                g.fill(0, y, screenW, y + 1, SCANLINE_COLOR);
            }
        });
        g.pose().popPose();
    }

    private static void drawVignette(GuiGraphics g, int width, int height) {
        prepare(VIGNETTE, true, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.blit(VIGNETTE, 0, 0, width, height, 0.0f, 0.0f, VIGNETTE_W, VIGNETTE_H, VIGNETTE_W, VIGNETTE_H);
        RenderSystem.disableBlend();
    }

    /**
     * Sets how a menu texture is sampled. Minecraft draws textures "pixelated" by default, which
     * makes big stretched pictures shimmer while they pan; {@code smooth} turns on linear
     * filtering. {@code repeat} makes the texture tile when the coordinates go past its edge.
     * Done every frame because a resource reload (F3+T) resets the texture settings.
     */
    static void prepare(ResourceLocation location, boolean smooth, boolean repeat) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        texture.setFilter(smooth, false); // also binds the texture
        if (repeat) {
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        }
    }

    /** backrooms:textures/gui/menu/&lt;name&gt;.png */
    static ResourceLocation texture(String name) {
        return Backrooms.id("textures/gui/menu/" + name + ".png");
    }

    /**
     * One scene's pan/zoom: the zoom goes from 1.08 to 1.16 (or back), and the picture drifts from
     * one point to roughly the opposite one. Pan values are -1..1 of the room the zoom leaves.
     */
    private record Move(float zoomFrom, float zoomTo, float fromX, float fromY, float toX, float toY) {
        static Move random() {
            boolean zoomIn = RANDOM.nextBoolean();
            float fromX = randomPan();
            float fromY = randomPan();
            float toX = Mth.clamp(-fromX + (RANDOM.nextFloat() - 0.5f) * 0.4f, -0.9f, 0.9f);
            float toY = Mth.clamp(-fromY + (RANDOM.nextFloat() - 0.5f) * 0.4f, -0.9f, 0.9f);
            return new Move(zoomIn ? 1.08f : 1.16f, zoomIn ? 1.16f : 1.08f, fromX, fromY, toX, toY);
        }

        private static float randomPan() {
            return RANDOM.nextFloat() * 1.8f - 0.9f;
        }
    }

    private FootageBackground() {}
}
