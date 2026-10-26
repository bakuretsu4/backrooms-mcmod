package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.client.anomaly.level9.CrimsonFieldClient;
import com.rickr.backrooms.client.light.FixturePower;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 9's midnight sky: pitch black overhead, fading into the fog colour at the horizon, no
 * moon, and a handful of faint stars that slowly blink in and out at random. Stare straight at
 * one for a couple of seconds and it goes out, as if it noticed you looking.
 *
 * <p>Drawn by hand each frame (a few dozen vertices) instead of vanilla's sky, so the stars can
 * each have their own brightness. Everything sits on a sphere 100 blocks around the camera and
 * moves with the view only (not with your position), like vanilla's sky.
 */
final class Level9Sky {
    private static final float RADIUS = 100.0f;
    /** "A handful": about a third of these are visible at any moment. */
    private static final int STAR_COUNT = 42;
    /** Stars sit between these elevations (degrees above the horizon) ... */
    private static final float MIN_ELEVATION = 18.0f;
    private static final float MAX_ELEVATION = 85.0f;
    /** ... and fade out toward the lower one, where the fog swallows them. */
    private static final float FADE_ELEVATION = 32.0f;
    /** A star fades in or out over this long when it blinks. */
    private static final float BLINK_FADE_MS = 2500.0f;
    /** Looking within this angle of a star (cosine of ~3.5 degrees) counts as staring at it. */
    private static final float STARE_COS = 0.9981f;
    /** Stare this long and the star goes out ... */
    private static final float STARE_MS = 2500.0f;
    /** ... fading over this long ... */
    private static final float STARE_FADE_MS = 700.0f;
    /** ... and stays gone at least this long (plus up to 12 s more). */
    private static final long STARE_HIDE_MS = 8000L;
    /** The sky gradient: elevation (degrees) where it has become fully black. */
    private static final float BLACK_ELEVATION = 40.0f;

    private static final class Star {
        /** Unit direction and two sideways vectors spanning the little quad. */
        final Vector3f dir = new Vector3f();
        final Vector3f side = new Vector3f();
        final Vector3f up = new Vector3f();
        float size;
        /** Brightest it gets (they're all faint). */
        float peak;
        /** Fade with elevation (low stars drown in the fog). */
        float elevationFade;
        /** Its own blink cycle: visible for {@code onMs} out of every {@code periodMs}. */
        long periodMs;
        long onMs;
        long offsetMs;
        /** Slight colour: some a bit bluer, some a bit warmer. */
        float r;
        float g;
        float b;
        // "Stares back" state.
        float staredMs;
        long hideStartMs;
        long hideUntilMs;
    }

    private static final Star[] STARS = new Star[STAR_COUNT];
    private static long lastFrameMs;

    static {
        for (int i = 0; i < STAR_COUNT; i++) {
            long seed = FixturePower.mix(0x1F2E3D4C5B6A7988L + i * 0x9E3779B97F4A7C15L);
            Star star = new Star();
            float azimuth = unit(seed, 0) * Mth.TWO_PI;
            // Uniform over the band of sky (more stars near the horizon than straight up, like
            // a real sky seen from the ground).
            float sinLow = Mth.sin(MIN_ELEVATION * Mth.DEG_TO_RAD);
            float sinHigh = Mth.sin(MAX_ELEVATION * Mth.DEG_TO_RAD);
            float sinElevation = Mth.lerp(unit(seed, 10), sinLow, sinHigh);
            float elevation = (float) Math.asin(sinElevation);
            float cosElevation = Mth.cos(elevation);
            star.dir.set(cosElevation * Mth.cos(azimuth), sinElevation, cosElevation * Mth.sin(azimuth));
            // Two vectors at right angles to the direction: the quad's sides.
            star.side.set(-Mth.sin(azimuth), 0.0f, Mth.cos(azimuth));
            star.dir.cross(star.side, star.up).normalize();
            float elevationDeg = elevation * Mth.RAD_TO_DEG;
            float fade = Mth.clamp((elevationDeg - MIN_ELEVATION) / (FADE_ELEVATION - MIN_ELEVATION), 0.0f, 1.0f);
            star.elevationFade = fade * fade * (3.0f - 2.0f * fade);
            star.size = 0.22f + unit(seed, 20) * 0.16f;
            star.peak = 0.22f + unit(seed, 30) * 0.30f;
            star.periodMs = 25000L + (long) (unit(seed, 40) * 35000.0f);
            star.onMs = (long) (star.periodMs * (0.3f + unit(seed, 50) * 0.35f));
            star.offsetMs = (long) (unit(seed, 54) * star.periodMs);
            float tint = unit(seed, 58) - 0.5f;
            star.r = 0.85f + tint * 0.2f;
            star.g = 0.88f;
            star.b = 0.95f - tint * 0.2f;
            STARS[i] = star;
        }
    }

    /** 0..1 from 10 bits of the seed, starting at bit {@code shift}. */
    private static float unit(long seed, int shift) {
        return ((seed >>> shift) & 0x3FF) / 1023.0f;
    }

    /**
     * Draws the sky. {@code view} is the camera rotation (no position), {@code projection} as
     * vanilla's sky gets it.
     */
    static void render(Matrix4f view, Camera camera, long nowMs) {
        float roll = Level9Atmosphere.fogRoll(nowMs);
        float[] fog = Level9Atmosphere.fogColour(nowMs);
        float dt = lastFrameMs == 0L ? 0.0f : Math.min(nowMs - lastFrameMs, 200L);
        lastFrameMs = nowMs;

        // The sky is "infinitely far": no fog on it (we fade it into the fog colour ourselves),
        // no depth writes, both sides of every triangle.
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        drawGradient(view, fog[0], fog[1], fog[2]);
        // Thick rolled-in fog hides the stars too.
        drawStars(view, camera, nowMs, dt, 1.0f - roll);
        if (Level9Atmosphere.inField()) {
            drawGloom(view, CrimsonFieldClient.gloomPulse(0.0f) * (0.45f + 0.55f * CrimsonFieldClient.proximity()));
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /**
     * Level 9.1's Gloom: a red glow low on the eastern horizon (http://backrooms-mghc.wikidot.com/level-9-1),
     * brightest just above the horizon and dead east, fading upward and to the sides. Added on
     * top of the sky, so when it is off nothing is left of it.
     */
    private static void drawGloom(Matrix4f view, float strength) {
        if (strength <= 0.003f) {
            return;
        }
        int columns = 16;
        int rows = 6;
        float halfWidth = 55.0f * Mth.DEG_TO_RAD;
        float top = 16.0f * Mth.DEG_TO_RAD;
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < columns; i++) {
            for (int j = 0; j < rows; j++) {
                gloomVertex(quads, view, i, j, columns, rows, halfWidth, top, strength);
                gloomVertex(quads, view, i + 1, j, columns, rows, halfWidth, top, strength);
                gloomVertex(quads, view, i + 1, j + 1, columns, rows, halfWidth, top, strength);
                gloomVertex(quads, view, i, j + 1, columns, rows, halfWidth, top, strength);
            }
        }
        // Additive: the glow only ever adds red light to the dark.
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Grid point (i, j) of the glow: azimuth across the east, elevation up from just below the horizon. */
    private static void gloomVertex(BufferBuilder quads, Matrix4f view, int i, int j, int columns, int rows,
                                    float halfWidth, float top, float strength) {
        float side = i / (float) columns * 2.0f - 1.0f; // -1..1 across the glow
        float up = j / (float) rows;                     // 0 at the horizon, 1 at the top
        float azimuth = side * halfWidth;
        float elevation = -2.0f * Mth.DEG_TO_RAD + up * top;
        float across = 1.0f - side * side;
        float alpha = strength * across * across * (1.0f - up) * (1.0f - up);
        float x = Mth.cos(elevation) * Mth.cos(azimuth) * RADIUS; // +x is east
        float y = Mth.sin(elevation) * RADIUS;
        float z = Mth.cos(elevation) * Mth.sin(azimuth) * RADIUS;
        quads.addVertex(view, x, y, z).setColor(0.85f, 0.10f, 0.06f, alpha);
    }

    /**
     * Black overhead, fog colour at (and below) the horizon: a cap fan from the zenith down to
     * {@link #BLACK_ELEVATION}, then a band down to the horizon, then a band below it.
     */
    private static void drawGradient(Matrix4f view, float fogR, float fogG, float fogB) {
        int segments = 24;
        float blackY = Mth.sin(BLACK_ELEVATION * Mth.DEG_TO_RAD) * RADIUS;
        float blackRing = Mth.cos(BLACK_ELEVATION * Mth.DEG_TO_RAD) * RADIUS;
        Tesselator tesselator = Tesselator.getInstance();

        BufferBuilder cap = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        cap.addVertex(view, 0.0f, RADIUS, 0.0f).setColor(0.0f, 0.0f, 0.0f, 1.0f);
        for (int i = 0; i <= segments; i++) {
            float a = i * Mth.TWO_PI / segments;
            cap.addVertex(view, Mth.cos(a) * blackRing, blackY, Mth.sin(a) * blackRing).setColor(0.0f, 0.0f, 0.0f, 1.0f);
        }
        BufferUploader.drawWithShader(cap.buildOrThrow());

        BufferBuilder band = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float a0 = i * Mth.TWO_PI / segments;
            float a1 = (i + 1) * Mth.TWO_PI / segments;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            // From black down to the fog colour at the horizon.
            band.addVertex(view, c0 * blackRing, blackY, s0 * blackRing).setColor(0.0f, 0.0f, 0.0f, 1.0f);
            band.addVertex(view, c1 * blackRing, blackY, s1 * blackRing).setColor(0.0f, 0.0f, 0.0f, 1.0f);
            band.addVertex(view, c1 * RADIUS, 0.0f, s1 * RADIUS).setColor(fogR, fogG, fogB, 1.0f);
            band.addVertex(view, c0 * RADIUS, 0.0f, s0 * RADIUS).setColor(fogR, fogG, fogB, 1.0f);
            // Below the horizon: plain fog colour (you only see it over a rooftop edge).
            band.addVertex(view, c0 * RADIUS, 0.0f, s0 * RADIUS).setColor(fogR, fogG, fogB, 1.0f);
            band.addVertex(view, c1 * RADIUS, 0.0f, s1 * RADIUS).setColor(fogR, fogG, fogB, 1.0f);
            band.addVertex(view, c1 * RADIUS, -RADIUS, s1 * RADIUS).setColor(fogR, fogG, fogB, 1.0f);
            band.addVertex(view, c0 * RADIUS, -RADIUS, s0 * RADIUS).setColor(fogR, fogG, fogB, 1.0f);
        }
        BufferUploader.drawWithShader(band.buildOrThrow());
    }

    /** Each star a small quad, added on top of the black sky with its own blink brightness. */
    private static void drawStars(Matrix4f view, Camera camera, long nowMs, float dtMs, float visibility) {
        if (visibility <= 0.001f) {
            return;
        }
        Vector3f look = camera.getLookVector();
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f center = new Vector3f();
        for (Star star : STARS) {
            float alpha = blink(star, nowMs) * stareFade(star, look, nowMs, dtMs) * star.elevationFade * star.peak * visibility;
            if (alpha <= 0.003f) {
                continue;
            }
            star.dir.mul(RADIUS, center);
            float s = star.size;
            quad(quads, view, center, star, -s, -s, alpha);
            quad(quads, view, center, star, s, -s, alpha);
            quad(quads, view, center, star, s, s, alpha);
            quad(quads, view, center, star, -s, s, alpha);
        }
        MeshData mesh = quads.build();
        if (mesh == null) {
            return; // every star is dark right now
        }
        // Additive: a star only ever brightens the black behind it.
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder quads, Matrix4f view, Vector3f center, Star star, float u, float v, float alpha) {
        quads.addVertex(view,
                        center.x + star.side.x * u + star.up.x * v,
                        center.y + star.side.y * u + star.up.y * v,
                        center.z + star.side.z * u + star.up.z * v)
                .setColor(star.r, star.g, star.b, alpha);
    }

    /** 0..1 where the star is in its own slow on/off cycle (soft fades at both ends). */
    private static float blink(Star star, long nowMs) {
        long phase = Math.floorMod(nowMs + star.offsetMs, star.periodMs);
        if (phase >= star.onMs) {
            return 0.0f;
        }
        float in = phase / BLINK_FADE_MS;
        float out = (star.onMs - phase) / BLINK_FADE_MS;
        float t = Mth.clamp(Math.min(in, out), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * 1 normally. Look straight at a visible star for {@link #STARE_MS} and it fades out and stays
     * gone for 8-20 s, then comes back with its normal blink.
     */
    private static float stareFade(Star star, Vector3f look, long nowMs, float dtMs) {
        if (nowMs < star.hideUntilMs) {
            float out = 1.0f - (nowMs - star.hideStartMs) / STARE_FADE_MS;
            return Mth.clamp(out, 0.0f, 1.0f);
        }
        float back = Mth.clamp((nowMs - star.hideUntilMs) / BLINK_FADE_MS, 0.0f, 1.0f);
        boolean visible = blink(star, nowMs) > 0.5f && star.elevationFade > 0.5f;
        if (visible && star.dir.dot(look) > STARE_COS) {
            star.staredMs += dtMs;
            if (star.staredMs >= STARE_MS) {
                star.staredMs = 0.0f;
                star.hideStartMs = nowMs;
                star.hideUntilMs = nowMs + STARE_HIDE_MS + (long) (Math.random() * 12000.0);
            }
        } else {
            star.staredMs = Math.max(0.0f, star.staredMs - dtMs);
        }
        return back;
    }

    private Level9Sky() {}
}
