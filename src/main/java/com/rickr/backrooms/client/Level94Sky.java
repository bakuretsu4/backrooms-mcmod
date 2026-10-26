package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.Level94Clock;
import com.rickr.backrooms.world.layout.Level94Castle;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 94's painted sky: too blue, too perfect, like a theatre backdrop (see the reference
 * screenshots). Drawn by hand every frame instead of vanilla's sky:
 * <ul>
 *   <li><b>the dome:</b> deep blue overhead fading to pale blue-white at the horizon by day; orange
 *       and pink at dusk (pinker at dawn); a very dark navy at night;</li>
 *   <li><b>the sun:</b> a bright soft disc with a wide glow. It climbs from the east in the morning
 *       and sinks in the west through dusk. There is no moon, ever;</li>
 *   <li><b>stars:</b> a few, only at night;</li>
 *   <li><b>clouds:</b> big fluffy painted clouds (pictures from {@code tex_l94_sky_clouds} in
 *       tools/gen_assets.py) drifting very slowly round the sky, tinted by the time of day;</li>
 *   <li><b>the castle on the horizon:</b> a picture of the castle on its glass mountain
 *       ({@code tex_l94_castle_far}) in the direction of your nearest castle, as big as the real
 *       one would look from where you stand. Once you're close enough for its real blocks to be
 *       drawn, the picture fades out and the real castle takes over. At night it's a silhouette.</li>
 * </ul>
 * Everything sits on a sphere {@value #RADIUS} blocks around the camera and turns with the view
 * only (not with your position), like vanilla's sky; the terrain is drawn over it afterwards, so
 * hills naturally hide the low clouds and the castle's foot.
 *
 * <p>All colours come from the shared clock ({@link Level94Clock}), so the fog
 * ({@link Level94Atmosphere}) can use the exact same horizon colour and the two meet seamlessly.
 */
final class Level94Sky {
    private static final float RADIUS = 100.0f;

    // --- Sky colours (r, g, b) -------------------------------------------------------------------
    private static final float[] DAY_ZENITH = {0.16f, 0.38f, 0.80f};
    private static final float[] DAY_HORIZON = {0.78f, 0.87f, 0.96f};
    private static final float[] SUNSET_ZENITH = {0.30f, 0.27f, 0.52f};
    private static final float[] SUNSET_HORIZON = {1.00f, 0.56f, 0.34f};
    private static final float[] SUNRISE_HORIZON = {0.98f, 0.64f, 0.62f};
    private static final float[] NIGHT_ZENITH = {0.006f, 0.010f, 0.034f};
    private static final float[] NIGHT_HORIZON = {0.024f, 0.038f, 0.085f};
    /** How strongly dusk/dawn colours take over at their peak (half-way through). */
    private static final float SUNSET_STRENGTH = 0.85f;

    // --- Sun -------------------------------------------------------------------------------------
    /** Angular sizes on the sky sphere (blocks at RADIUS): bright disc, its soft edge, the glow. */
    private static final float SUN_DISC = 4.0f;
    private static final float SUN_EDGE = 6.5f;
    private static final float SUN_GLOW = 30.0f;
    private static final float SUN_HIGHEST_DEG = 62.0f;
    private static final float SUN_BELOW_DEG = 8.0f;

    // --- Stars -----------------------------------------------------------------------------------
    private static final int STAR_COUNT = 60;
    private static final float[][] STARS = new float[STAR_COUNT][];

    // --- Clouds ----------------------------------------------------------------------------------
    private static final ResourceLocation[] CLOUD_TEXTURES = {
            Backrooms.id("textures/environment/l94_cloud_0.png"),
            Backrooms.id("textures/environment/l94_cloud_1.png"),
            Backrooms.id("textures/environment/l94_cloud_2.png"),
            Backrooms.id("textures/environment/l94_cloud_3.png"),
    };
    private static final int CLOUD_COUNT = 16;
    /** Degrees per second the clouds drift round the sky: very slowly. */
    private static final float CLOUD_DRIFT_MIN = 0.06f;
    private static final float CLOUD_DRIFT_MAX = 0.14f;

    /** One painted cloud: which picture, where it sits, how big, how fast it drifts. */
    private record Cloud(int texture, float azimuthDeg, float elevationDeg, float widthDeg, float driftDeg, boolean flip) {}

    /** Lowest (furthest away) first, so the higher, nearer clouds are painted over them. */
    private static final Cloud[] CLOUDS = new Cloud[CLOUD_COUNT];

    // --- Castle on the horizon -------------------------------------------------------------------
    private static final ResourceLocation CASTLE_TEXTURE = Backrooms.id("textures/environment/l94_castle_far.png");
    /** The picture covers this many blocks across (the mountain's foot) ... */
    private static final float CASTLE_HALF_WIDTH = Level94Castle.MOUNTAIN_RADIUS;
    /** ... and from the ground (bottom row) to just above the highest spire (top row). */
    private static final float CASTLE_BOTTOM_Y = 40.0f;
    private static final float CASTLE_TOP_Y = Level94Castle.CASTLE_TOP_Y + 1.0f;
    /** Far away the castle fades a little into the haze (at least this much of it stays). */
    private static final float CASTLE_MIN_CLARITY = 0.55f;
    private static final float CASTLE_HAZE_DISTANCE = 1500.0f;

    static {
        RandomSource random = RandomSource.create(94_0094L);
        for (int i = 0; i < STAR_COUNT; i++) {
            float azimuth = random.nextFloat() * Mth.TWO_PI;
            float elevation = (12.0f + random.nextFloat() * 76.0f) * Mth.DEG_TO_RAD;
            float size = 0.25f + random.nextFloat() * 0.35f;
            float brightness = 0.35f + random.nextFloat() * 0.65f;
            float twinkle = random.nextFloat() * Mth.TWO_PI;
            STARS[i] = new float[] {Mth.cos(elevation) * Mth.cos(azimuth), Mth.sin(elevation),
                    Mth.cos(elevation) * Mth.sin(azimuth), size, brightness, twinkle};
        }
        for (int i = 0; i < CLOUD_COUNT; i++) {
            // Most clouds sit low over the hills (like the screenshots); a few drift overhead.
            float elevation = i < 11 ? 4.0f + random.nextFloat() * 16.0f : 24.0f + random.nextFloat() * 26.0f;
            float width = 26.0f + random.nextFloat() * 22.0f;
            CLOUDS[i] = new Cloud(i % CLOUD_TEXTURES.length, i * (360.0f / CLOUD_COUNT) + random.nextFloat() * 14.0f,
                    elevation, width, Mth.lerp(random.nextFloat(), CLOUD_DRIFT_MIN, CLOUD_DRIFT_MAX), random.nextBoolean());
        }
        Arrays.sort(CLOUDS, Comparator.comparingDouble(Cloud::elevationDeg));
    }

    /** Scratch colours (render thread only). */
    private static final Vector3f ZENITH = new Vector3f();
    private static final Vector3f HORIZON = new Vector3f();

    private Level94Sky() {}

    // ==========================================================================================
    // Colours of the moment (shared with the fog)
    // ==========================================================================================

    /** 0..1: how much of the sunset/sunrise colour there is (peaks half-way through dusk and dawn). */
    static float sunsetAmount(ClientLevel level, float partialTick) {
        float day = Level94Clock.daylight(level, partialTick);
        return 4.0f * day * (1.0f - day) * SUNSET_STRENGTH;
    }

    /** The colour at the horizon right now; the fog uses it too. */
    static Vector3f horizonColor(ClientLevel level, float partialTick, Vector3f out) {
        return mix(level, partialTick, DAY_HORIZON, NIGHT_HORIZON,
                Level94Clock.phase(level) == Level94Clock.Phase.DAWN ? SUNRISE_HORIZON : SUNSET_HORIZON, out);
    }

    private static Vector3f zenithColor(ClientLevel level, float partialTick, Vector3f out) {
        return mix(level, partialTick, DAY_ZENITH, NIGHT_ZENITH, SUNSET_ZENITH, out);
    }

    private static Vector3f mix(ClientLevel level, float partialTick, float[] day, float[] night, float[] sunset, Vector3f out) {
        float d = Level94Clock.daylight(level, partialTick);
        float s = sunsetAmount(level, partialTick);
        out.set(Mth.lerp(d, night[0], day[0]), Mth.lerp(d, night[1], day[1]), Mth.lerp(d, night[2], day[2]));
        return out.lerp(new Vector3f(sunset[0], sunset[1], sunset[2]), s);
    }

    /**
     * Where the sun is (a unit vector, y up). It rises in the east at the start of dawn, swings
     * through the south and sets in the west at the end of dusk; at night it's below the horizon.
     */
    private static Vector3f sunDirection(ClientLevel level, float partialTick, Vector3f out) {
        // Count from the start of dawn (the end of the previous cycle) to the end of dusk.
        float t = Level94Clock.cycleTick(level) + partialTick;
        float sinceDawn = t >= Level94Clock.CYCLE - Level94Clock.DAWN_TICKS ? t - (Level94Clock.CYCLE - Level94Clock.DAWN_TICKS)
                : t + Level94Clock.DAWN_TICKS;
        float u = Mth.clamp(sinceDawn / (Level94Clock.DAWN_TICKS + Level94Clock.DAY_TICKS), 0.0f, 1.0f);
        // sqrt(sin): up quickly in the morning, a long high afternoon, sinking fast through dusk.
        float elevation = (-SUN_BELOW_DEG + (SUN_HIGHEST_DEG + SUN_BELOW_DEG) * (float) Math.sqrt(Mth.sin(Mth.PI * u)))
                * Mth.DEG_TO_RAD;
        float azimuth = Mth.PI * u; // 0 = east (+x), half-way = south (+z), 1 = west (-x)
        return out.set(Mth.cos(elevation) * Mth.cos(azimuth), Mth.sin(elevation), Mth.cos(elevation) * Mth.sin(azimuth));
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    /** Draws the whole sky. {@code view} is the camera rotation (no position). */
    static void render(ClientLevel level, float partialTick, Matrix4f view, Camera camera) {
        float day = Level94Clock.daylight(level, partialTick);
        float sunset = sunsetAmount(level, partialTick);
        zenithColor(level, partialTick, ZENITH);
        horizonColor(level, partialTick, HORIZON);
        Vector3f sun = sunDirection(level, partialTick, new Vector3f());
        float seconds = (level.getGameTime() + partialTick) / 20.0f;

        // The sky is "infinitely far": no fog on it (it fades into the fog colour by itself), no
        // depth writes, both sides of every triangle.
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        drawDome(view);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); // additive
        drawStars(view, (1.0f - day) * (1.0f - day), seconds);
        drawSun(view, sun, sunset);
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        drawClouds(view, day, sunset, seconds);
        drawCastle(view, camera, day, sunset);

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** The gradient: rings from the zenith down to the horizon, plain horizon colour below it. */
    private static void drawDome(Matrix4f view) {
        int segments = 32;
        // Elevations (degrees) of the rings; the colour follows (1 - sin(elevation))^2, so the deep
        // blue holds overhead and the pale band hugs the horizon.
        float[] rings = {90.0f, 65.0f, 45.0f, 30.0f, 20.0f, 12.0f, 6.0f, 2.0f, 0.0f, -90.0f};
        BufferBuilder band = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int r = 0; r < rings.length - 1; r++) {
            float e0 = rings[r] * Mth.DEG_TO_RAD;
            float e1 = rings[r + 1] * Mth.DEG_TO_RAD;
            float t0 = gradient(e0);
            float t1 = gradient(e1);
            for (int i = 0; i < segments; i++) {
                float a0 = i * Mth.TWO_PI / segments;
                float a1 = (i + 1) * Mth.TWO_PI / segments;
                domeVertex(band, view, a0, e0, t0);
                domeVertex(band, view, a1, e0, t0);
                domeVertex(band, view, a1, e1, t1);
                domeVertex(band, view, a0, e1, t1);
            }
        }
        BufferUploader.drawWithShader(band.buildOrThrow());
    }

    private static float gradient(float elevation) {
        float h = 1.0f - Math.max(0.0f, Mth.sin(elevation));
        return h * h;
    }

    private static void domeVertex(BufferBuilder buffer, Matrix4f view, float azimuth, float elevation, float t) {
        float ring = Mth.cos(elevation) * RADIUS;
        buffer.addVertex(view, Mth.cos(azimuth) * ring, Mth.sin(elevation) * RADIUS, Mth.sin(azimuth) * ring)
                .setColor(Mth.lerp(t, ZENITH.x, HORIZON.x), Mth.lerp(t, ZENITH.y, HORIZON.y), Mth.lerp(t, ZENITH.z, HORIZON.z), 1.0f);
    }

    /** A few stars at night, each a tiny quad gently twinkling (added on top of the dark sky). */
    private static void drawStars(Matrix4f view, float visibility, float seconds) {
        if (visibility <= 0.01f) {
            return;
        }
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f centre = new Vector3f();
        Vector3f side = new Vector3f();
        Vector3f up = new Vector3f();
        for (float[] star : STARS) {
            centre.set(star[0], star[1], star[2]).mul(RADIUS);
            basis(centre, side, up);
            float alpha = visibility * star[4] * (0.75f + 0.25f * Mth.sin(seconds * 1.7f + star[5]));
            square(quads, view, centre, side, up, star[3], 0.85f, 0.9f, 1.0f, alpha);
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
    }

    /** The sun: a wide soft glow (bigger and orange at dusk) and a bright disc with a soft rim. */
    private static void drawSun(Matrix4f view, Vector3f direction, float sunset) {
        // Fades out as it sinks below the horizon (the hills hide it before that anyway).
        float above = Mth.clamp((direction.y + 0.12f) / 0.12f, 0.0f, 1.0f);
        if (above <= 0.0f) {
            return;
        }
        float r = 1.0f;
        float g = Mth.lerp(sunset, 0.97f, 0.62f);
        float b = Mth.lerp(sunset, 0.86f, 0.32f);
        Vector3f centre = new Vector3f(direction).mul(RADIUS);
        Vector3f side = new Vector3f();
        Vector3f up = new Vector3f();
        basis(centre, side, up);
        float glow = SUN_GLOW * (1.0f + sunset * 0.8f);
        disc(view, centre, side, up, 0.0f, glow, r, g, b, 0.45f * above, 0.0f);
        disc(view, centre, side, up, 0.0f, SUN_DISC, 1.0f, Mth.lerp(0.5f, g, 1.0f), Mth.lerp(0.5f, b, 1.0f), above, above);
        disc(view, centre, side, up, SUN_DISC, SUN_EDGE, 1.0f, Mth.lerp(0.5f, g, 1.0f), Mth.lerp(0.5f, b, 1.0f), above, 0.0f);
    }

    /**
     * A flat ring (or disc, when {@code inner} is 0) round {@code centre}: alpha {@code innerAlpha}
     * at the inner edge fading to {@code outerAlpha} at the outer one.
     */
    private static void disc(Matrix4f view, Vector3f centre, Vector3f side, Vector3f up, float inner, float outer,
                             float r, float g, float b, float innerAlpha, float outerAlpha) {
        int segments = 32;
        BufferBuilder strip = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float a = i * Mth.TWO_PI / segments;
            float c = Mth.cos(a);
            float s = Mth.sin(a);
            strip.addVertex(view, centre.x + (side.x * c + up.x * s) * inner, centre.y + (side.y * c + up.y * s) * inner,
                    centre.z + (side.z * c + up.z * s) * inner).setColor(r, g, b, innerAlpha);
            strip.addVertex(view, centre.x + (side.x * c + up.x * s) * outer, centre.y + (side.y * c + up.y * s) * outer,
                    centre.z + (side.z * c + up.z * s) * outer).setColor(r, g, b, outerAlpha);
        }
        BufferUploader.drawWithShader(strip.buildOrThrow());
    }

    /**
     * The clouds, one picture at a time: white by day, glowing orange-pink at dusk, dark grey-blue
     * shapes at night. Low ones are a little hazier.
     */
    private static void drawClouds(Matrix4f view, float day, float sunset, float seconds) {
        float r = Mth.lerp(day, 0.10f, 1.0f);
        float g = Mth.lerp(day, 0.12f, 1.0f);
        float b = Mth.lerp(day, 0.20f, 1.0f);
        r = Mth.lerp(sunset, r, 1.0f);
        g = Mth.lerp(sunset, g, 0.70f);
        b = Mth.lerp(sunset, b, 0.62f);
        float alpha = Mth.lerp(day, 0.55f, 1.0f);
        Vector3f centre = new Vector3f();
        Vector3f side = new Vector3f();
        Vector3f up = new Vector3f();
        for (int texture = 0; texture < CLOUD_TEXTURES.length; texture++) {
            BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Cloud cloud : CLOUDS) {
                if (cloud.texture() != texture) {
                    continue;
                }
                float azimuth = (cloud.azimuthDeg() + cloud.driftDeg() * seconds) * Mth.DEG_TO_RAD;
                float elevation = cloud.elevationDeg() * Mth.DEG_TO_RAD;
                centre.set(Mth.cos(elevation) * Mth.cos(azimuth), Mth.sin(elevation), Mth.cos(elevation) * Mth.sin(azimuth)).mul(RADIUS);
                basis(centre, side, up);
                float halfWidth = RADIUS * Mth.sin(cloud.widthDeg() * 0.5f * Mth.DEG_TO_RAD);
                float halfHeight = halfWidth * 0.5f; // the pictures are 2:1
                // The cloud's flat base sits at its elevation, so lift the picture by half its height.
                centre.add(up.x * halfHeight * 0.6f, up.y * halfHeight * 0.6f, up.z * halfHeight * 0.6f);
                float haze = Mth.lerp(Mth.clamp(cloud.elevationDeg() / 20.0f, 0.0f, 1.0f), 0.7f, 1.0f);
                float u0 = cloud.flip() ? 1.0f : 0.0f;
                float u1 = 1.0f - u0;
                texturedQuad(quads, view, centre, side, up, halfWidth, halfHeight, u0, u1, r, g, b, alpha * haze);
            }
            MeshData mesh = quads.build();
            if (mesh != null) {
                RenderSystem.setShaderTexture(0, CLOUD_TEXTURES[texture]);
                BufferUploader.drawWithShader(mesh);
            }
        }
    }

    /**
     * The far-off castle: a picture card standing where the castle is, scaled so it covers exactly
     * what the real mountain and castle would from here.
     */
    private static void drawCastle(Matrix4f view, Camera camera, float day, float sunset) {
        if (!Level94ClientState.castleKnown) {
            return;
        }
        Vec3 eye = camera.getPosition();
        double dx = Level94ClientState.castleX + 0.5 - eye.x;
        double dz = Level94ClientState.castleZ + 0.5 - eye.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0f) {
            return;
        }
        // Fade out once the real castle's chunks are being drawn (they are in front of the sky).
        float renderBlocks = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
        float fadeEnd = renderBlocks - 16.0f;
        float fadeStart = Math.min(renderBlocks * 0.55f, fadeEnd - 32.0f);
        float visible = Mth.clamp((distance - fadeStart) / (fadeEnd - fadeStart), 0.0f, 1.0f);
        if (visible <= 0.001f) {
            return;
        }
        // A card at the castle's distance, shrunk onto the sky sphere: everything scales by RADIUS / distance.
        float scale = RADIUS / distance;
        float bx = (float) (dx / distance);
        float bz = (float) (dz / distance);
        Vector3f centre = new Vector3f(bx * RADIUS, 0.0f, bz * RADIUS);
        Vector3f side = new Vector3f(-bz, 0.0f, bx);
        float halfWidth = CASTLE_HALF_WIDTH * scale;
        float bottom = (CASTLE_BOTTOM_Y - (float) eye.y) * scale;
        float top = (CASTLE_TOP_Y - (float) eye.y) * scale;

        // Sunlit white by day, warm at dusk, a dark silhouette at night; a little hazy far away.
        float r = Mth.lerp(day, 0.035f, 1.0f);
        float g = Mth.lerp(day, 0.045f, 1.0f);
        float b = Mth.lerp(day, 0.085f, 1.0f);
        r = Mth.lerp(sunset, r, 1.0f);
        g = Mth.lerp(sunset, g, 0.74f);
        b = Mth.lerp(sunset, b, 0.60f);
        float clarity = Mth.lerp(Mth.clamp(distance / CASTLE_HAZE_DISTANCE, 0.0f, 1.0f), 1.0f, CASTLE_MIN_CLARITY);
        float alpha = visible * clarity;

        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quad.addVertex(view, centre.x - side.x * halfWidth, top, centre.z - side.z * halfWidth).setUv(0.0f, 0.0f).setColor(r, g, b, alpha);
        quad.addVertex(view, centre.x + side.x * halfWidth, top, centre.z + side.z * halfWidth).setUv(1.0f, 0.0f).setColor(r, g, b, alpha);
        quad.addVertex(view, centre.x + side.x * halfWidth, bottom, centre.z + side.z * halfWidth).setUv(1.0f, 1.0f).setColor(r, g, b, alpha);
        quad.addVertex(view, centre.x - side.x * halfWidth, bottom, centre.z - side.z * halfWidth).setUv(0.0f, 1.0f).setColor(r, g, b, alpha);
        RenderSystem.setShaderTexture(0, CASTLE_TEXTURE);
        BufferUploader.drawWithShader(quad.buildOrThrow());
    }

    // ---- small geometry helpers ----------------------------------------------------------------

    /** Two unit vectors spanning the plane facing the camera at {@code centre} (side is level). */
    private static void basis(Vector3f centre, Vector3f side, Vector3f up) {
        side.set(-centre.z, 0.0f, centre.x);
        if (side.lengthSquared() < 1.0e-4f) {
            side.set(1.0f, 0.0f, 0.0f); // straight overhead
        }
        side.normalize();
        centre.cross(side, up).normalize();
        if (up.y < 0.0f) {
            up.negate();
        }
    }

    private static void square(BufferBuilder quads, Matrix4f view, Vector3f centre, Vector3f side, Vector3f up, float half,
                               float r, float g, float b, float alpha) {
        for (int i = 0; i < 4; i++) {
            float su = (i == 1 || i == 2) ? half : -half;
            float sv = (i >= 2) ? half : -half;
            quads.addVertex(view, centre.x + side.x * su + up.x * sv, centre.y + side.y * su + up.y * sv,
                    centre.z + side.z * su + up.z * sv).setColor(r, g, b, alpha);
        }
    }

    private static void texturedQuad(BufferBuilder quads, Matrix4f view, Vector3f centre, Vector3f side, Vector3f up,
                                     float halfWidth, float halfHeight, float u0, float u1, float r, float g, float b, float alpha) {
        corner(quads, view, centre, side, up, -halfWidth, halfHeight, u0, 0.0f, r, g, b, alpha);
        corner(quads, view, centre, side, up, halfWidth, halfHeight, u1, 0.0f, r, g, b, alpha);
        corner(quads, view, centre, side, up, halfWidth, -halfHeight, u1, 1.0f, r, g, b, alpha);
        corner(quads, view, centre, side, up, -halfWidth, -halfHeight, u0, 1.0f, r, g, b, alpha);
    }

    private static void corner(BufferBuilder quads, Matrix4f view, Vector3f centre, Vector3f side, Vector3f up,
                               float s, float t, float u, float v, float r, float g, float b, float alpha) {
        quads.addVertex(view, centre.x + side.x * s + up.x * t, centre.y + side.y * s + up.y * t, centre.z + side.z * s + up.z * t)
                .setUv(u, v).setColor(r, g, b, alpha);
    }
}
