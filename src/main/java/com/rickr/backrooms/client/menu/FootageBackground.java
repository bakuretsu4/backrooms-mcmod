package com.rickr.backrooms.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "found footage" behind the main menu screens: cinematic shots of the levels played like a
 * camcorder tape, level by level, each shot slowly drifting, with soft analog cuts between them and
 * a light VHS finish (fine grain, faint scanlines, a mild vignette, rare tape imperfections).
 * <p>
 * <b>Shots.</b> {@code textures/gui/menu/shots/shots.json} lists the shots (JPEG, 1920x1080, built
 * by {@code tools/menu_shots.py}):
 * <pre>[{"id": "level0_a", "level": "level_0", "caption": "menu.backrooms.scene.level_0",
 *   "focus": [0.55, 0.5], "move": "in"}, ...]</pre>
 * {@code id} names {@code shots/<id>.jpg}; {@code level} groups shots (one visit per level per
 * round, levels shuffled each round, never the same level twice in a row, each visit showing that
 * level's next shot); {@code caption} is the lang key shown after "PLAY" (default
 * {@code menu.backrooms.scene.<level>}); {@code focus} is the most important point as fractions of
 * the image; {@code move} is the camera drift: in / out (push in, pull back) or left / right / up /
 * down (the camera pans that way). Vanilla's image loader only reads PNG, so the JPEGs are decoded
 * with stb_image on a background thread a few seconds before their cut; only the shot on screen
 * and the next one are kept in memory. Unreadable shots are skipped.
 * <p>
 * <b>Framing.</b> Screens call {@link #setLayout} (or {@link #setLayoutEitherSide}) from
 * {@code init()} with the point where the focus should sit and the rectangles of their own UI. The
 * shot is zoomed (cover-fit x 1.00-1.12) and offset so its focus lands on that point without ever
 * showing past the picture's edges, and its drift is kept small enough that the focus never
 * slides under the UI.
 * <p>
 * Everything here is static so the tape keeps rolling between the title screen, the Field Notes
 * and the option screens. Timing uses the real clock ({@link Util#getMillis()}), not game ticks,
 * so the motion is smooth at any frame rate. Nothing is allocated per frame except what vanilla's
 * own drawing allocates.
 */
public final class FootageBackground {
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/menu");

    private static final ResourceLocation MANIFEST = Backrooms.id("textures/gui/menu/shots/shots.json");
    /**
     * Two texture slots, used in turn: the shot on screen and the next one. Fixed names (not the
     * shot ids) so a level with a single shot can follow itself without the two clashing.
     */
    private static final ResourceLocation[] SLOTS = {Backrooms.id("dynamic/menu_shot_a"), Backrooms.id("dynamic/menu_shot_b")};
    private static final ResourceLocation NOISE = texture("noise");
    private static final ResourceLocation VIGNETTE = texture("vignette");
    private static final int NOISE_SIZE = 256;
    private static final int VIGNETTE_W = 512;
    private static final int VIGNETTE_H = 288;

    // --- Timing -----------------------------------------------------------------------------
    /** Each shot runs 12-14 s (varied so the cuts don't fall into a beat). */
    private static final long SHOT_MIN_MS = 12_000L;
    private static final long SHOT_EXTRA_MS = 2_000L;
    /** The next shot starts decoding this long before its cut (a 1080p JPEG takes ~20-60 ms). */
    private static final long PRELOAD_LEAD_MS = 4_000L;
    /** The menu wasn't drawn for this long (you're in a world): give the textures back. */
    private static final long IDLE_RELEASE_MS = 3_000L;
    /** A layout change (title screen -> Field Notes) re-frames the shot over about this long. */
    private static final float REFRAME_MS = 450.0f;

    // --- Framing ----------------------------------------------------------------------------
    /** Most the picture is ever enlarged past cover-fit (more would show the JPEG's softness). */
    private static final float ZOOM_MAX = 1.12f;
    /** Push-in / pull-back over a whole shot: 3%. */
    private static final float ZOOM_DRIFT = 0.03f;
    /** Pan over a whole shot: 2.5% of the window width. */
    private static final float PAN_FRACTION = 0.025f;
    /** 0 = constant-speed drift, 1 = full ease in/out; a little easing hides the start and stop. */
    private static final float DRIFT_EASE = 0.35f;
    /** The focus keeps at least this far (GUI px) from the UI rectangles. */
    private static final float UI_MARGIN = 4.0f;

    // --- The cut ----------------------------------------------------------------------------
    /** The tracking band rolls up through the old shot during the last 300 ms before the cut. */
    private static final long ROLL_MS = 300L;
    /** Right at the cut the picture drops to near-black for one or two frames. */
    private static final long DIP_MS = 40L;
    private static final float DIP_ALPHA = 0.88f;
    /** Then the new shot settles with a faint horizontal wobble. */
    private static final long SETTLE_MS = 150L;
    private static final float WOBBLE_PX = 1.5f;
    /** The band's height (share of the window) and how far its rows slip sideways (screen px). */
    private static final float BAND_HEIGHT = 0.09f;
    private static final float STRONG_BAND_HEIGHT = 0.12f;
    private static final float BAND_SHIFT_MIN_PX = 2.0f;
    private static final float BAND_SHIFT_MAX_PX = 8.0f;
    /** Rows are drawn in strips this tall (screen px) inside the band, and during the wobble. */
    private static final int BAND_STRIP_PX = 2;
    private static final int WOBBLE_STRIP_PX = 4;
    /** Light lift and noise inside the band (a mistracked signal is brighter and noisier). */
    private static final float BAND_LIFT = 0.06f;
    private static final float BAND_NOISE = 0.9f;
    /** About one cut in four mistracks harder: the band rolls again over the new shot and the colour bleeds. */
    private static final float STRONG_CUT_CHANCE = 0.25f;
    /** Faint red/cyan double image, only around a cut (screen px offset, opacity). */
    private static final float CUT_CHROMA_ALPHA = 0.06f;
    private static final float STRONG_CHROMA_ALPHA = 0.10f;
    /** Colour bleed inside the band on strong cuts. */
    private static final float BLEED_ALPHA = 0.16f;

    // --- The VHS finish ---------------------------------------------------------------------
    /**
     * Grain: noise.png (grey, alpha 0..60 of 255) drawn at 40%, one noise pixel per screen pixel
     * so it reads as fine tape noise rather than blotches over the picture.
     */
    private static final float GRAIN_ALPHA = 0.4f;
    /** Scanlines: a 1-pixel dark line every 3 screen pixels at ~6% opacity. */
    private static final int SCANLINE_COLOR = 0x0F000000;
    private static final int SCANLINE_SPACING = 3;
    /** Fluorescent flicker: every 15-40 s, 1-3 quick dips of 10-22% black. */
    private static final long FLICKER_MIN_GAP_MS = 15_000L;
    private static final int FLICKER_EXTRA_GAP_MS = 25_000;
    private static final float FLICKER_MIN_ALPHA = 0.10f;
    private static final float FLICKER_EXTRA_ALPHA = 0.12f;
    /** Luma wobble: every 25-60 s the brightness breathes for 0.6-1.2 s, by up to 5%. */
    private static final long LUMA_MIN_GAP_MS = 25_000L;
    private static final int LUMA_EXTRA_GAP_MS = 35_000;
    private static final float LUMA_DEPTH = 0.05f;
    /** Dropouts: every 4-12 s one or two short white streaks, 1 screen px tall, for a frame or two. */
    private static final long DROPOUT_MIN_GAP_MS = 4_000L;
    private static final int DROPOUT_EXTRA_GAP_MS = 8_000;
    private static final int DROPOUT_COLOR = 0x73E8E8E0;
    /** Head-switching noise: a 2-4 screen px strip along the bottom edge whose rows slip sideways. */
    private static final int HEAD_SWITCH_MIN_PX = 2;
    private static final int HEAD_SWITCH_EXTRA_PX = 3;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Component NO_CAPTION = Component.empty();
    private static final Rect[] NO_RECTS = new Rect[0];

    // --- The shot list (from shots.json) -----------------------------------------------------
    private static boolean manifestLoaded;
    private static Shot[] shots = new Shot[0];
    /** For each level, its shots (indexes into {@link #shots}) in manifest order. */
    private static int[][] levelShots = new int[0][];
    /** For each level, which of its shots is shown on its next visit. */
    private static int[] levelCursor = new int[0];
    /** The shuffled order of the levels in this round, and how far through it we are. */
    private static int[] roundOrder = new int[0];
    private static int roundPos;
    /** Level of the shot on screen (-1 = none yet): the next shot never comes from the same level. */
    private static int lastLevel = -1;
    /** Shots that failed to load; they are skipped from then on. */
    private static boolean[] bad = new boolean[0];
    /** Set when no shot at all can be shown: the tape just plays black with its noise. */
    private static boolean exhausted;

    // --- The shot on screen and the next one --------------------------------------------------
    private static int curShot = -1;
    private static int curSlot = -1;
    private static int curW;
    private static int curH;
    private static int nextShot = -1;
    @Nullable
    private static CompletableFuture<NativeImage> nextDecode;
    private static boolean nextReady;
    private static int nextW;
    private static int nextH;
    private static long shotStartMs;
    private static long shotEndMs;
    /** Whether the current shot came in through a cut (the dip and the settle wobble play). */
    private static boolean cutIn;
    /** Whether the cut into the current shot / out of it is a strong one. */
    private static boolean cutInStrong;
    private static boolean cutOutStrong;
    private static long lastFrameMs = -1L;

    // --- Framing ------------------------------------------------------------------------------
    private static float layoutTargetX;
    private static float layoutTargetY;
    private static boolean layoutMirror;
    private static Rect[] layoutAvoid = NO_RECTS;
    private static int layoutW = -1;
    private static int layoutH = -1;
    private static int layoutVersion;
    /** The current shot's drift: zoom and the focus's screen position at its start and end. */
    private static float planZoom0;
    private static float planZoom1;
    private static float planX0;
    private static float planY0;
    private static float planX1;
    private static float planY1;
    private static int planShot = -1;
    private static int planSlot = -1;
    private static int planW = -1;
    private static int planH = -1;
    private static int planVersion = -1;
    /**
     * The picture as drawn: GUI px per image px and the top-left corner. Eased toward the plan so
     * a layout change glides instead of jumping (any blend of two edge-safe framings is edge-safe).
     */
    private static float viewScale;
    private static float viewX;
    private static float viewY;
    private static boolean viewSnap = true;

    // --- This frame's cut effects (set by cutEffects, read by the row drawing) -----------------
    private static boolean bandActive;
    private static float bandCenter;
    private static float bandHalf;
    private static float bandStrength;
    private static float bandBleed;
    private static float wobblePx;
    private static float wobblePhase;
    private static float wobbleFreq;
    private static float chromaAlpha;
    private static float chromaPx;
    private static float dipAlpha;
    private static int rowSeed;

    // --- Imperfection timers ------------------------------------------------------------------
    private static boolean timersStarted;
    private static long nextFlickerBurstMs;
    private static int dipsLeft;
    private static long nextDipMs;
    private static long flickerUntilMs;
    private static float flickerAlpha;
    private static long nextLumaMs;
    private static long lumaStartMs;
    private static long lumaEndMs;
    private static long nextDropoutMs;
    private static final float[] DROPOUT_Y = new float[2];
    private static final float[] DROPOUT_X0 = new float[2];
    private static final float[] DROPOUT_X1 = new float[2];
    private static final long[] DROPOUT_UNTIL = new long[2];

    /** A rectangle of screen UI (GUI px) the shot's focal point should stay out of. */
    public record Rect(float x0, float y0, float x1, float y1) {
        boolean contains(float x, float y, float margin) {
            return x >= this.x0 - margin && x <= this.x1 + margin && y >= this.y0 - margin && y <= this.y1 + margin;
        }
    }

    /** One shot from shots.json. */
    private record Shot(int level, ResourceLocation file, Component caption, float focusX, float focusY, Move move) {}

    /** The camera's drift during a shot: push in, pull back, or pan that way. */
    private enum Move { IN, OUT, LEFT, RIGHT, UP, DOWN }

    /**
     * Where the shot's focal point should sit (GUI px) and the UI to keep it clear of. Call from
     * {@code Screen#init()} (it runs again on resize). Screens that don't call it keep the last
     * layout while the window size matches it, otherwise the focus is centred.
     */
    public static void setLayout(float targetX, float targetY, Rect... avoid) {
        applyLayout(targetX, targetY, false, avoid);
    }

    /**
     * Like {@link #setLayout}, for screens with a free strip on both sides of a central panel:
     * {@code targetX} is the left-hand target, mirrored to the right for shots whose focus is in
     * the right half of the picture.
     */
    public static void setLayoutEitherSide(float targetX, float targetY, Rect... avoid) {
        applyLayout(targetX, targetY, true, avoid);
    }

    private static void applyLayout(float targetX, float targetY, boolean mirror, Rect[] avoid) {
        Window window = Minecraft.getInstance().getWindow();
        layoutW = window.getGuiScaledWidth();
        layoutH = window.getGuiScaledHeight();
        layoutTargetX = targetX;
        layoutTargetY = targetY;
        layoutMirror = mirror;
        List<Rect> rects = new ArrayList<>(avoid.length);
        for (Rect rect : avoid) {
            if (rect != null) {
                rects.add(rect);
            }
        }
        layoutAvoid = rects.toArray(NO_RECTS);
        layoutVersion++;
    }

    /** The caption of the shot on screen, e.g. "LEVEL 0". */
    public static Component caption() {
        return curShot >= 0 ? shots[curShot].caption() : NO_CAPTION;
    }

    /**
     * Gives the shot textures back once the menu hasn't been drawn for a few seconds (you're
     * playing). Called every client tick; the tape picks up with a fresh shot when the menu returns.
     */
    public static void releaseIfIdle() {
        if ((curSlot >= 0 || nextDecode != null || nextReady) && Util.getMillis() - lastFrameMs > IDLE_RELEASE_MS) {
            releaseAll();
        }
    }

    /** Draws the whole animated background over the screen (call from Screen#renderBackground). */
    public static void render(GuiGraphics g, int width, int height) {
        long now = Util.getMillis();
        long dt = lastFrameMs < 0L ? 0L : Mth.clamp(now - lastFrameMs, 0L, 100L);
        lastFrameMs = now;
        if (!manifestLoaded) {
            loadManifest();
        }
        if (!timersStarted) {
            startTimers(now);
        }
        if (curSlot < 0) {
            startTape(now);
        } else {
            advance(now);
        }

        Window window = Minecraft.getInstance().getWindow();
        float px = (float) (1.0 / window.getGuiScale()); // one screen pixel, in GUI px
        g.fill(0, 0, width, height, 0xFF000000);
        cutEffects(now, height);
        if (curSlot >= 0) {
            updateView(width, height, dt, now);
            drawPicture(g, width, height, px);
            drawHeadSwitching(g, window);
        }
        drawFlicker(g, width, height, now);
        drawLumaWobble(g, width, height, now);
        if (dipAlpha > 0.0f) {
            g.fill(0, 0, width, height, VhsText.withAlpha(0x000000, dipAlpha));
        }
        drawNoise(g, 0, 0, width, height, GRAIN_ALPHA);
        drawScanlines(g, window);
        drawDropouts(g, window, now);
        drawVignette(g, width, height);
    }

    // ===================================================================================== shots

    /** Reads shots.json once. Bad entries are skipped with a warning; the rest still play. */
    private static void loadManifest() {
        manifestLoaded = true;
        List<Shot> list = new ArrayList<>();
        Map<String, Integer> levels = new HashMap<>();
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MANIFEST);
        if (resource.isEmpty()) {
            LOGGER.warn("No menu shots: {} is missing", MANIFEST);
        } else {
            try (Reader reader = resource.get().openAsReader()) {
                JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement element : array) {
                    try {
                        list.add(parseShot(element.getAsJsonObject(), levels));
                    } catch (RuntimeException e) {
                        LOGGER.warn("Skipping menu shot {}: {}", element, e.getMessage());
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Couldn't read {}", MANIFEST, e);
            }
        }

        shots = list.toArray(new Shot[0]);
        bad = new boolean[shots.length];
        int levelCount = levels.size();
        levelShots = new int[levelCount][];
        levelCursor = new int[levelCount];
        roundOrder = new int[levelCount];
        roundPos = levelCount; // start with a fresh shuffle
        int[] counts = new int[levelCount];
        for (Shot shot : shots) {
            counts[shot.level()]++;
        }
        for (int level = 0; level < levelCount; level++) {
            levelShots[level] = new int[counts[level]];
            levelCursor[level] = RANDOM.nextInt(counts[level]); // each level starts on a random shot
            counts[level] = 0;
        }
        for (int i = 0; i < shots.length; i++) {
            int level = shots[i].level();
            levelShots[level][counts[level]++] = i;
        }
        exhausted = shots.length == 0;
        LOGGER.debug("Menu footage: {} shots of {} levels", shots.length, levelCount);
    }

    private static Shot parseShot(JsonObject json, Map<String, Integer> levels) {
        String id = GsonHelper.getAsString(json, "id");
        if (!ResourceLocation.isValidPath(id)) {
            throw new IllegalArgumentException("bad id " + id);
        }
        String level = GsonHelper.getAsString(json, "level");
        String caption = GsonHelper.getAsString(json, "caption", "menu.backrooms.scene." + level);
        float fx = 0.5f;
        float fy = 0.5f;
        JsonArray focus = GsonHelper.getAsJsonArray(json, "focus", null);
        if (focus != null) {
            fx = Mth.clamp(focus.get(0).getAsFloat(), 0.0f, 1.0f);
            fy = Mth.clamp(focus.get(1).getAsFloat(), 0.0f, 1.0f);
        }
        Move move = Move.valueOf(GsonHelper.getAsString(json, "move", "in").toUpperCase(Locale.ROOT));
        int levelIndex = levels.computeIfAbsent(level, key -> levels.size());
        return new Shot(levelIndex, Backrooms.id("textures/gui/menu/shots/" + id + ".jpg"),
                VhsText.translatable(caption), fx, fy, move);
    }

    /**
     * The next shot to show: the next level of this round (a new shuffled round when it runs out),
     * and that level's next shot. -1 if nothing is left that can be shown.
     */
    private static int pickNextShot() {
        int levelCount = levelShots.length;
        if (levelCount == 0) {
            return -1;
        }
        for (int attempt = 0; attempt < 2 * levelCount + 1; attempt++) {
            if (roundPos >= levelCount) {
                newRound();
            }
            int level = roundOrder[roundPos++];
            // Only possible after an unreadable shot was skipped: still no level twice in a row.
            if (level == lastLevel && levelCount > 1) {
                continue;
            }
            int shot = takeShot(level);
            if (shot >= 0) {
                return shot;
            }
        }
        // Only the level on screen still has readable shots: better it again than black.
        return lastLevel >= 0 ? takeShot(lastLevel) : -1;
    }

    /** Shuffles the levels for a new round; the first one is never the level just shown. */
    private static void newRound() {
        int n = roundOrder.length;
        for (int i = 0; i < n; i++) {
            roundOrder[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            int swap = roundOrder[i];
            roundOrder[i] = roundOrder[j];
            roundOrder[j] = swap;
        }
        if (n > 1 && roundOrder[0] == lastLevel) {
            int j = 1 + RANDOM.nextInt(n - 1);
            roundOrder[0] = roundOrder[j];
            roundOrder[j] = lastLevel;
        }
        roundPos = 0;
    }

    /** The level's next readable shot (moving its cursor on), or -1 if none of them load. */
    private static int takeShot(int level) {
        int[] list = levelShots[level];
        for (int i = 0; i < list.length; i++) {
            int shot = list[levelCursor[level]];
            levelCursor[level] = (levelCursor[level] + 1) % list.length;
            if (!bad[shot]) {
                return shot;
            }
        }
        return -1;
    }

    /** First frame (or back from a world): load a shot right away and start it with a cut-in. */
    private static void startTape(long now) {
        while (!exhausted && !nextReady) {
            int shot = pickNextShot();
            if (shot < 0) {
                exhausted = true;
                break;
            }
            NativeImage image = decode(Minecraft.getInstance().getResourceManager(), shots[shot].file());
            if (image == null) {
                bad[shot] = true;
            } else {
                nextShot = shot;
                uploadNext(image);
            }
        }
        if (nextReady) {
            cutOutStrong = RANDOM.nextFloat() < STRONG_CUT_CHANCE;
            commitNext(now);
        }
    }

    /** Preloads the next shot when its cut is near, and cuts to it when the current one is over. */
    private static void advance(long now) {
        if (!exhausted && nextShot < 0 && now >= shotEndMs - PRELOAD_LEAD_MS) {
            int shot = pickNextShot();
            if (shot < 0) {
                exhausted = true;
            } else {
                nextShot = shot;
                ResourceManager resources = Minecraft.getInstance().getResourceManager();
                ResourceLocation file = shots[shot].file();
                nextDecode = CompletableFuture.supplyAsync(() -> decode(resources, file), Util.backgroundExecutor());
            }
        }
        // Upload as soon as the decode is done (seconds before the cut), so the cut itself is free.
        if (nextDecode != null && nextDecode.isDone()) {
            receiveNext(nextDecode.getNow(null));
        }
        if (now < shotEndMs) {
            return;
        }

        // The cut. Normally the next shot is long uploaded; if its decode is still running (very
        // slow disk), wait for it, and if it failed, load other shots right here.
        if (nextDecode != null) {
            receiveNext(nextDecode.join());
        }
        while (!exhausted && !nextReady) {
            int shot = pickNextShot();
            if (shot < 0) {
                exhausted = true;
                break;
            }
            NativeImage image = decode(Minecraft.getInstance().getResourceManager(), shots[shot].file());
            if (image == null) {
                bad[shot] = true;
            } else {
                nextShot = shot;
                uploadNext(image);
            }
        }
        // Normally the next shot starts exactly at the cut. If the menu wasn't drawn for a while,
        // it just starts now.
        long start = now - shotEndMs < 1_000L ? shotEndMs : now;
        if (nextReady) {
            Minecraft.getInstance().getTextureManager().release(SLOTS[curSlot]);
            commitNext(start);
        } else {
            // Nothing else can be shown: keep rolling the shot on screen, with its cuts.
            startShot(start);
        }
    }

    /** A finished background decode: upload it, or mark the shot bad so another is picked. */
    private static void receiveNext(@Nullable NativeImage image) {
        nextDecode = null;
        if (image != null) {
            uploadNext(image);
        } else {
            bad[nextShot] = true;
            nextShot = -1;
        }
    }

    /** Uploads the decoded next shot into the free texture slot. */
    private static void uploadNext(NativeImage image) {
        ResourceLocation slot = SLOTS[curSlot == 0 ? 1 : 0];
        nextW = image.getWidth();
        nextH = image.getHeight();
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(slot, texture);
        // DynamicTexture leaves GL's default REPEAT wrapping; with smooth filtering the picture's
        // edge rows would then blend with the opposite edge, a faint seam along the window edges.
        texture.bind();
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        nextReady = true;
    }

    /** The next shot becomes the current one. */
    private static void commitNext(long startMs) {
        curSlot = curSlot == 0 ? 1 : 0;
        curShot = nextShot;
        curW = nextW;
        curH = nextH;
        lastLevel = shots[curShot].level();
        nextShot = -1;
        nextReady = false;
        startShot(startMs);
    }

    private static void startShot(long startMs) {
        shotStartMs = startMs;
        shotEndMs = startMs + SHOT_MIN_MS + RANDOM.nextInt((int) SHOT_EXTRA_MS + 1);
        cutIn = true;
        cutInStrong = cutOutStrong;
        cutOutStrong = RANDOM.nextFloat() < STRONG_CUT_CHANCE;
        planShot = -1;
        viewSnap = true;
    }

    /** Frees both texture slots and drops a running decode (its image is freed when it finishes). */
    private static void releaseAll() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation slot : SLOTS) {
            textures.release(slot);
        }
        if (nextDecode != null) {
            nextDecode.thenAccept(image -> {
                if (image != null) {
                    image.close();
                }
            });
            nextDecode = null;
        }
        curSlot = -1;
        curShot = -1;
        nextShot = -1;
        nextReady = false;
        planShot = -1;
    }

    /**
     * Decodes a shot JPEG into an RGBA image (any thread). Vanilla's {@code NativeImage.read} only
     * accepts PNG, so this calls stb_image (bundled with LWJGL) directly. Null if it is missing or
     * unreadable.
     */
    @Nullable
    private static NativeImage decode(ResourceManager resources, ResourceLocation file) {
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            LOGGER.warn("Menu shot {} is missing", file);
            return null;
        }
        ByteBuffer data = null;
        try (InputStream in = resource.get().open()) {
            data = TextureUtil.readResource(in);
            data.flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, channels, 4);
                if (pixels == null) {
                    LOGGER.warn("Couldn't decode menu shot {}: {}", file, STBImage.stbi_failure_reason());
                    return null;
                }
                try {
                    int width = w.get(0);
                    int height = h.get(0);
                    // NativeImage stores RGBA bytes and reads them as native-order ints, exactly
                    // what stb returns; it has no public pointer for a bulk copy, so copy by pixel
                    // (~2 M ints, a few ms on this background thread).
                    pixels.order(ByteOrder.nativeOrder());
                    NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                    for (int y = 0; y < height; y++) {
                        int row = y * width;
                        for (int x = 0; x < width; x++) {
                            image.setPixelRGBA(x, y, pixels.getInt((row + x) << 2));
                        }
                    }
                    return image;
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Couldn't load menu shot {}", file, e);
            return null;
        } finally {
            if (data != null) {
                MemoryUtil.memFree(data);
            }
        }
    }

    // =================================================================================== framing

    /**
     * Plans the current shot's drift for this window and layout: a zoom range and where the focus
     * sits on screen at the start and end of the shot.
     */
    private static void plan(int width, int height) {
        Shot shot = shots[curShot];
        boolean own = width == layoutW && height == layoutH;
        float tx = own ? layoutTargetX : width * 0.5f;
        float ty = own ? layoutTargetY : height * 0.5f;
        if (own && layoutMirror && shot.focusX() > 0.5f) {
            tx = width - tx;
        }
        Rect[] avoid = own ? layoutAvoid : NO_RECTS;
        float cover = Math.max(width / (float) curW, height / (float) curH);
        float imageW = curW * cover;
        float imageH = curH * cover;

        Move move = shot.move();
        if (move != Move.IN && move != Move.OUT) {
            // The focus slides along the pan; "left" = the camera turns left, so the picture
            // slides right. Halve the pan until both ends of the slide need no more than the
            // zoom limit and stay clear of the UI.
            float pan = PAN_FRACTION * width;
            float dx = move == Move.LEFT ? pan : move == Move.RIGHT ? -pan : 0.0f;
            float dy = move == Move.UP ? pan : move == Move.DOWN ? -pan : 0.0f;
            for (int i = 0; i < 4; i++) {
                float ax = tx - dx * 0.5f;
                float ay = ty - dy * 0.5f;
                float bx = tx + dx * 0.5f;
                float by = ty + dy * 0.5f;
                float zoom = Math.max(zoomFor(shot, ax, ay, imageW, imageH, width, height),
                        zoomFor(shot, bx, by, imageW, imageH, width, height));
                if (zoom <= ZOOM_MAX && !blocked(avoid, ax, ay) && !blocked(avoid, bx, by)) {
                    planZoom0 = planZoom1 = zoom;
                    planX0 = ax;
                    planY0 = ay;
                    planX1 = bx;
                    planY1 = by;
                    return;
                }
                dx *= 0.5f;
                dy *= 0.5f;
            }
            move = Move.IN; // no room to pan here: push in instead
        }
        // Zoom about the focus. If the focus can't reach the target within the zoom limit, the
        // edge clamp in updateView parks it as close as it gets.
        float low = Mth.clamp(zoomFor(shot, tx, ty, imageW, imageH, width, height), 1.0f, ZOOM_MAX / (1.0f + ZOOM_DRIFT));
        float high = low * (1.0f + ZOOM_DRIFT);
        planZoom0 = move == Move.IN ? low : high;
        planZoom1 = move == Move.IN ? high : low;
        planX0 = planX1 = tx;
        planY0 = planY1 = ty;
    }

    /**
     * The smallest zoom (1 = cover-fit) at which the focus can sit at (x, y) with the picture still
     * covering the whole window: each edge of the picture must stay outside the window's edge.
     */
    private static float zoomFor(Shot shot, float x, float y, float imageW, float imageH, int width, int height) {
        float fx = Mth.clamp(shot.focusX(), 0.02f, 0.98f);
        float fy = Mth.clamp(shot.focusY(), 0.02f, 0.98f);
        float zoom = 1.0f;
        zoom = Math.max(zoom, x / (fx * imageW));
        zoom = Math.max(zoom, (width - x) / ((1.0f - fx) * imageW));
        zoom = Math.max(zoom, y / (fy * imageH));
        zoom = Math.max(zoom, (height - y) / ((1.0f - fy) * imageH));
        return zoom;
    }

    private static boolean blocked(Rect[] avoid, float x, float y) {
        for (Rect rect : avoid) {
            if (rect.contains(x, y, UI_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    /** Where the picture is this frame: the planned drift, eased toward after layout changes. */
    private static void updateView(int width, int height, long dt, long now) {
        if (planShot != curShot || planSlot != curSlot || planW != width || planH != height || planVersion != layoutVersion) {
            if (planW != width || planH != height) {
                viewSnap = true; // the old framing means nothing in a resized window
            }
            plan(width, height);
            planShot = curShot;
            planSlot = curSlot;
            planW = width;
            planH = height;
            planVersion = layoutVersion;
        }
        Shot shot = shots[curShot];
        float p = Mth.clamp((now - shotStartMs) / (float) (shotEndMs - shotStartMs), 0.0f, 1.0f);
        float e = p + DRIFT_EASE * (p * p * (3.0f - 2.0f * p) - p);
        float cover = Math.max(width / (float) curW, height / (float) curH);
        float scale = cover * Mth.lerp(e, planZoom0, planZoom1);
        float shownW = curW * scale;
        float shownH = curH * scale;
        // Focus on its planned point, then clamped so no edge of the picture is ever inside the window.
        float x = Mth.clamp(Mth.lerp(e, planX0, planX1) - shot.focusX() * shownW, width - shownW, 0.0f);
        float y = Mth.clamp(Mth.lerp(e, planY0, planY1) - shot.focusY() * shownH, height - shownH, 0.0f);
        if (viewSnap) {
            viewSnap = false;
            viewScale = scale;
            viewX = x;
            viewY = y;
        } else {
            float k = 1.0f - (float) Math.exp(-dt / REFRAME_MS);
            viewScale += (scale - viewScale) * k;
            viewX += (x - viewX) * k;
            viewY += (y - viewY) * k;
        }
    }

    // ================================================================================== the cut

    /** Works out this frame's cut effects from where we are relative to the last and next cut. */
    private static void cutEffects(long now, int height) {
        bandActive = false;
        bandBleed = 0.0f;
        wobblePx = 0.0f;
        chromaAlpha = 0.0f;
        chromaPx = 0.0f;
        dipAlpha = 0.0f;
        rowSeed = (int) (now / 33L); // the band's row jitter changes ~30 times a second at any frame rate
        if (curSlot < 0) {
            return;
        }
        long toCut = shotEndMs - now;
        if (toCut >= 0L && toCut <= ROLL_MS) {
            // The tracking band rolls up through the old shot.
            float r = 1.0f - toCut / (float) ROLL_MS;
            band(r, height, cutOutStrong ? STRONG_BAND_HEIGHT : BAND_HEIGHT, 1.0f);
            chromaAlpha = (cutOutStrong ? STRONG_CHROMA_ALPHA : CUT_CHROMA_ALPHA) * r;
            chromaPx = cutOutStrong ? 2.0f : 1.0f;
            bandBleed = cutOutStrong ? BLEED_ALPHA : 0.0f;
        }
        long since = now - shotStartMs;
        if (cutIn && since >= 0L) {
            if (since < DIP_MS) {
                dipAlpha = DIP_ALPHA;
            }
            if (since < SETTLE_MS) {
                float k = 1.0f - since / (float) SETTLE_MS;
                wobblePx = WOBBLE_PX * k * (cutInStrong ? 1.5f : 1.0f);
                wobblePhase = since * 0.045f;
                wobbleFreq = Mth.TWO_PI * 5.0f / height; // five gentle waves down the frame
                chromaAlpha = Math.max(chromaAlpha, (cutInStrong ? STRONG_CHROMA_ALPHA : CUT_CHROMA_ALPHA) * k);
                chromaPx = cutInStrong ? 2.0f : 1.0f;
            }
            if (cutInStrong && since < ROLL_MS) {
                // A strong mistrack: the band rolls through once more, over the new shot.
                float r = since / (float) ROLL_MS;
                band(r, height, STRONG_BAND_HEIGHT, 0.75f);
                bandBleed = BLEED_ALPHA * (1.0f - r);
            } else if (since >= ROLL_MS) {
                cutIn = false;
            }
        }
    }

    /** Puts the band at roll position r (0 = just below the frame, 1 = just above it). */
    private static void band(float r, int height, float heightShare, float strength) {
        bandHalf = height * heightShare * 0.5f;
        bandCenter = Mth.lerp(r, height + bandHalf, -bandHalf);
        bandStrength = strength;
        bandActive = true;
    }

    /** How far (screen px) the picture row at GUI y slips sideways this frame. */
    private static float rowShift(float y, int row) {
        float shift = 0.0f;
        if (wobblePx > 0.0f) {
            shift += wobblePx * Mth.sin(y * wobbleFreq + wobblePhase);
        }
        if (bandActive) {
            float d = (y - bandCenter) / bandHalf;
            if (d > -1.0f && d < 1.0f) {
                float w = 1.0f - d * d;
                w *= w; // soft-edged band, strongest in its middle
                shift += w * bandStrength * (BAND_SHIFT_MIN_PX + (BAND_SHIFT_MAX_PX - BAND_SHIFT_MIN_PX) * hash01(row, rowSeed));
            }
        }
        return shift;
    }

    private static float hash01(int a, int b) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA6B;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h >>> 8) * 0x1.0p-24f;
    }

    // ================================================================================== drawing

    /** The shot, with the cut's slipping rows and faint colour fringes when a cut is near. */
    private static void drawPicture(GuiGraphics g, int width, int height, float px) {
        ResourceLocation slot = SLOTS[curSlot];
        prepare(slot, true, false);
        Matrix4f pose = g.pose().last().pose();
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, slot);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        if (wobblePx > 0.0f) {
            rows(buffer, pose, 0.0f, height, WOBBLE_STRIP_PX * px, px, 0.0f);
        } else {
            imageQuad(buffer, pose, viewY, viewY + curH * viewScale, 0.0f);
            if (bandActive) {
                // Drawn again on top: the band's rows, slipped sideways.
                rows(buffer, pose, bandCenter - bandHalf, bandCenter + bandHalf, BAND_STRIP_PX * px, px, 0.0f);
            }
        }
        draw(buffer);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (chromaAlpha > 0.0f) {
            // Faint red/cyan double image, only around cuts.
            g.setColor(1.0f, 0.2f, 0.2f, chromaAlpha);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            imageQuad(buffer, pose, viewY, viewY + curH * viewScale, chromaPx * px);
            draw(buffer);
            g.setColor(0.2f, 1.0f, 1.0f, chromaAlpha);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            imageQuad(buffer, pose, viewY, viewY + curH * viewScale, -chromaPx * px);
            draw(buffer);
        }
        if (bandActive && bandBleed > 0.0f) {
            // Strong cuts: the colour bleeds sideways inside the band.
            g.setColor(1.0f, 0.25f, 0.2f, bandBleed);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            rows(buffer, pose, bandCenter - bandHalf, bandCenter + bandHalf, BAND_STRIP_PX * px, px, 3.0f * px);
            draw(buffer);
            g.setColor(0.2f, 0.9f, 1.0f, bandBleed * 0.8f);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            rows(buffer, pose, bandCenter - bandHalf, bandCenter + bandHalf, BAND_STRIP_PX * px, px, -2.0f * px);
            draw(buffer);
        }
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();

        if (bandActive) {
            drawBandNoise(g, width);
        }
    }

    /** Inside the band a mistracked signal is a little brighter and noisier, with bright specks. */
    private static void drawBandNoise(GuiGraphics g, int width) {
        int top = Mth.floor(bandCenter - bandHalf);
        int mid = Mth.floor(bandCenter);
        int bottom = Mth.ceil(bandCenter + bandHalf);
        int lift = VhsText.withAlpha(0xFFFFFF, BAND_LIFT * bandStrength);
        g.fillGradient(0, top, width, mid, 0x00FFFFFF, lift);
        g.fillGradient(0, mid, width, bottom, lift, 0x00FFFFFF);
        drawNoise(g, 0, top, width, bottom - top, BAND_NOISE * bandStrength);

        // Two short bright specks near the band's middle (screen px, so they stay one pixel thin).
        Window window = Minecraft.getInstance().getWindow();
        float toScreen = (float) window.getGuiScale();
        int screenW = window.getWidth();
        g.pose().pushPose();
        g.pose().scale(1.0f / toScreen, 1.0f / toScreen, 1.0f);
        for (int i = 0; i < 2; i++) {
            float y = (bandCenter + (hash01(i * 31 + 7, rowSeed) - 0.5f) * bandHalf) * toScreen;
            float x0 = hash01(i * 31 + 11, rowSeed) * screenW * 0.85f;
            float len = (0.04f + 0.12f * hash01(i * 31 + 13, rowSeed)) * screenW;
            g.fill(Math.round(x0), Math.round(y), Math.round(x0 + len), Math.round(y) + 1,
                    VhsText.withAlpha(0xF0F0E8, 0.35f * bandStrength));
        }
        g.pose().popPose();
    }

    /** The whole picture as one quad, shifted sideways by {@code shift} GUI px. */
    private static void imageQuad(BufferBuilder buffer, Matrix4f pose, float y0, float y1, float shift) {
        float x0 = viewX + shift;
        quad(buffer, pose, x0, y0, x0 + curW * viewScale, y1, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * The picture between GUI y0 and y1 as horizontal strips {@code step} tall, each slipped
     * sideways by {@link #rowShift} (plus {@code extraShift} GUI px).
     */
    private static void rows(BufferBuilder buffer, Matrix4f pose, float y0, float y1, float step, float px, float extraShift) {
        float shownW = curW * viewScale;
        float shownH = curH * viewScale;
        float top = Math.max(y0, viewY);
        float bottom = Math.min(y1, viewY + shownH);
        int row = (int) (top / px);
        int rowStep = Math.max(1, Math.round(step / px));
        for (float y = top; y < bottom; y += step, row += rowStep) {
            float yEnd = Math.min(y + step, bottom);
            float shift = rowShift((y + yEnd) * 0.5f, row) * px + extraShift;
            quad(buffer, pose, viewX + shift, y, viewX + shift + shownW, yEnd,
                    0.0f, (y - viewY) / shownH, 1.0f, (yEnd - viewY) / shownH);
        }
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1) {
        buffer.addVertex(pose, x0, y0, 0.0f).setUv(u0, v0);
        buffer.addVertex(pose, x0, y1, 0.0f).setUv(u0, v1);
        buffer.addVertex(pose, x1, y1, 0.0f).setUv(u1, v1);
        buffer.addVertex(pose, x1, y0, 0.0f).setUv(u1, v0);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * Head-switching noise: on real tapes the bottom few lines are where the video heads swap, so
     * they jitter sideways in a thin noisy strip. 2-4 screen px, different every frame.
     */
    private static void drawHeadSwitching(GuiGraphics g, Window window) {
        float toScreen = (float) window.getGuiScale();
        int screenW = window.getWidth();
        int screenH = window.getHeight();
        int strip = HEAD_SWITCH_MIN_PX + RANDOM.nextInt(HEAD_SWITCH_EXTRA_PX);
        int top = screenH - strip;
        float shift = 6.0f + RANDOM.nextInt(14);
        float shownW = curW * viewScale * toScreen;
        float shownH = curH * viewScale * toScreen;
        float x = viewX * toScreen + shift;
        float imageTop = viewY * toScreen;

        g.pose().pushPose();
        g.pose().scale(1.0f / toScreen, 1.0f / toScreen, 1.0f);
        g.fill(0, top, screenW, screenH, 0xFF000000);
        Matrix4f pose = g.pose().last().pose();
        ResourceLocation slot = SLOTS[curSlot];
        prepare(slot, true, false);
        RenderSystem.setShaderTexture(0, slot);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        g.setColor(0.85f, 0.85f, 0.85f, 1.0f);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        quad(buffer, pose, x, top, x + shownW, screenH, 0.0f, (top - imageTop) / shownH, 1.0f, (screenH - imageTop) / shownH);
        draw(buffer);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        prepare(NOISE, false, true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(NOISE, 0, top, screenW, strip, RANDOM.nextInt(NOISE_SIZE), RANDOM.nextInt(NOISE_SIZE),
                screenW, strip, NOISE_SIZE, NOISE_SIZE);
        g.blit(NOISE, 0, top, screenW, strip, RANDOM.nextInt(NOISE_SIZE), RANDOM.nextInt(NOISE_SIZE),
                screenW, strip, NOISE_SIZE, NOISE_SIZE);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    // ============================================================================ imperfections

    private static void startTimers(long now) {
        timersStarted = true;
        nextFlickerBurstMs = now + FLICKER_MIN_GAP_MS + RANDOM.nextInt(FLICKER_EXTRA_GAP_MS + 1);
        nextLumaMs = now + LUMA_MIN_GAP_MS + RANDOM.nextInt(LUMA_EXTRA_GAP_MS + 1);
        nextDropoutMs = now + DROPOUT_MIN_GAP_MS + RANDOM.nextInt(DROPOUT_EXTRA_GAP_MS + 1);
    }

    /** The lights stutter: a faint dark veil for 30-70 ms, 1-3 times in quick succession. */
    private static void drawFlicker(GuiGraphics g, int width, int height, long now) {
        if (now >= flickerUntilMs) {
            if (dipsLeft > 0 && now >= nextDipMs) {
                dipsLeft--;
                flickerUntilMs = now + 30L + RANDOM.nextInt(41);
                flickerAlpha = FLICKER_MIN_ALPHA + RANDOM.nextFloat() * FLICKER_EXTRA_ALPHA;
                nextDipMs = flickerUntilMs + 60L + RANDOM.nextInt(101);
            } else if (dipsLeft == 0 && now >= nextFlickerBurstMs) {
                dipsLeft = 1 + RANDOM.nextInt(3);
                nextDipMs = now;
                nextFlickerBurstMs = now + FLICKER_MIN_GAP_MS + RANDOM.nextInt(FLICKER_EXTRA_GAP_MS + 1);
            }
        }
        if (now < flickerUntilMs) {
            g.fill(0, 0, width, height, VhsText.withAlpha(0x000000, flickerAlpha));
        }
    }

    /** Now and then the picture's brightness breathes for a moment (a worn tape's unstable luma). */
    private static void drawLumaWobble(GuiGraphics g, int width, int height, long now) {
        if (now >= lumaEndMs && now >= nextLumaMs) {
            lumaStartMs = now;
            lumaEndMs = now + 600L + RANDOM.nextInt(601);
            nextLumaMs = lumaEndMs + LUMA_MIN_GAP_MS + RANDOM.nextInt(LUMA_EXTRA_GAP_MS + 1);
        }
        if (now >= lumaEndMs) {
            return;
        }
        float t = (now - lumaStartMs) / (float) (lumaEndMs - lumaStartMs);
        float wave = Mth.sin((now - lumaStartMs) * 0.035f) * Mth.sin(t * Mth.PI); // ~5.5 Hz, faded in and out
        if (wave > 0.0f) {
            g.fill(0, 0, width, height, VhsText.withAlpha(0xFFFFFF, LUMA_DEPTH * 0.6f * wave));
        } else {
            g.fill(0, 0, width, height, VhsText.withAlpha(0x000000, -LUMA_DEPTH * wave));
        }
    }

    /** Rare dropouts: a short white streak one scan line tall, gone after a frame or two. */
    private static void drawDropouts(GuiGraphics g, Window window, long now) {
        if (now >= nextDropoutMs) {
            int count = 1 + RANDOM.nextInt(2);
            for (int i = 0; i < count; i++) {
                DROPOUT_Y[i] = 0.05f + 0.9f * RANDOM.nextFloat();
                DROPOUT_X0[i] = 0.7f * RANDOM.nextFloat();
                DROPOUT_X1[i] = DROPOUT_X0[i] + 0.08f + 0.3f * RANDOM.nextFloat();
                DROPOUT_UNTIL[i] = now + 30L + RANDOM.nextInt(41);
            }
            nextDropoutMs = now + DROPOUT_MIN_GAP_MS + RANDOM.nextInt(DROPOUT_EXTRA_GAP_MS + 1);
        }
        float toScreen = (float) window.getGuiScale();
        int screenW = window.getWidth();
        int screenH = window.getHeight();
        boolean pushed = false;
        for (int i = 0; i < DROPOUT_UNTIL.length; i++) {
            if (now >= DROPOUT_UNTIL[i]) {
                continue;
            }
            if (!pushed) {
                pushed = true;
                g.pose().pushPose();
                g.pose().scale(1.0f / toScreen, 1.0f / toScreen, 1.0f);
            }
            int y = Math.round(DROPOUT_Y[i] * screenH);
            g.fill(Math.round(DROPOUT_X0[i] * screenW), y, Math.round(Math.min(1.0f, DROPOUT_X1[i]) * screenW), y + 1, DROPOUT_COLOR);
        }
        if (pushed) {
            g.pose().popPose();
        }
    }

    /**
     * Tiles noise.png over a region at a random offset every frame, one noise pixel per screen
     * pixel (fine grain that doesn't hide detail).
     */
    private static void drawNoise(GuiGraphics g, int x, int y, int width, int height, float alpha) {
        if (width <= 0 || height <= 0) {
            return;
        }
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int uSpan = Math.max(1, Mth.ceil(width * guiScale));
        int vSpan = Math.max(1, Mth.ceil(height * guiScale));
        prepare(NOISE, false, true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        g.blit(NOISE, x, y, width, height, (float) RANDOM.nextInt(NOISE_SIZE), (float) RANDOM.nextInt(NOISE_SIZE),
                uSpan, vSpan, NOISE_SIZE, NOISE_SIZE);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /** Faint dark lines across the picture, in real screen pixels whatever the GUI scale. */
    @SuppressWarnings("deprecation") // drawManaged batches the few hundred fills into one draw call
    private static void drawScanlines(GuiGraphics g, Window window) {
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

    /** Dark corners, clean middle (vignette.png is clear over the central half of the frame). */
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

    private FootageBackground() {}
}
