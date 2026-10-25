package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * The Watcher's eyeball, built from code instead of a texture + box model, so it can be properly
 * round and veiny. Everything is in "eye space": centre at the origin, the pupil looking down +Z,
 * +Y up, sizes in blocks (the eye is 3 blocks across).
 *
 * <ul>
 *   <li><b>Eyeball</b>: a sphere of {@value #RINGS}x{@value #SEGMENTS} quads. Each corner has its own
 *       colour: the iris (amber, with radial streaks and a dark rim), the pale sclera turning pink and
 *       bloodshot toward the iris and bruised purple at the back, plus fine vein lines.</li>
 *   <li><b>Thick veins</b>: ribbons laid just above the surface, crawling from the back toward the
 *       iris, with branches. Generated once from a fixed seed, so every Watcher looks the same.</li>
 *   <li><b>Pupil</b>: a black disc on top of the iris whose size is chosen each frame (dilation).</li>
 *   <li><b>Optic nerve and tendrils</b>: tapering 6-sided tubes trailing down and back, swaying.</li>
 * </ul>
 */
public final class WatcherModel {
    /** Radius of the eyeball in model units; the renderer scales it to the Watcher's real size. */
    public static final float RADIUS = 1.5f;
    /**
     * The eye is drawn full-bright (it must read in the pitch-black street), so these colours ARE the
     * brightness. The sclera is held at about half brightness: pale, as if catching some faint light,
     * not glowing. The iris is left at full strength: it is the eye's sickly amber glow.
     */
    private static final float SCLERA_SELF_LIGHT = 0.5f;
    private static final float TENDRIL_SELF_LIGHT = 0.45f;
    /** Angular radius of the iris, seen from the eye centre (radians): about half the front. */
    public static final float IRIS_ANGLE = 0.52f;
    private static final int RINGS = 40;
    private static final int SEGMENTS = 56;
    private static final int TUBE_SIDES = 6;
    private static final int TENDRIL_SEGMENTS = 14;

    /** One thick vein: points on the unit sphere and the ribbon width at each. */
    private record Vein(Vector3f[] points, float[] widths, int colour) {}

    /** One trailing tendril: where it leaves the eye, which way it heads, its size and sway timing. */
    private record Tendril(Vector3f root, Vector3f heading, float length, float thickness, float phase, float swaySpeed) {}

    // The sphere, precomputed: unit directions and colours of every grid corner.
    private final float[] dirX = new float[(RINGS + 1) * (SEGMENTS + 1)];
    private final float[] dirY = new float[dirX.length];
    private final float[] dirZ = new float[dirX.length];
    private final int[] colour = new int[dirX.length];
    private final List<Vein> veins = new ArrayList<>();
    private final List<Tendril> tendrils = new ArrayList<>();

    // Scratch vectors so drawing a frame allocates nothing.
    private final Vector3f[] ringA = newRing();
    private final Vector3f[] ringB = newRing();
    private final Vector3f centre = new Vector3f();
    private final Vector3f next = new Vector3f();
    private final Vector3f tangent = new Vector3f();
    private final Vector3f sideA = new Vector3f();
    private final Vector3f sideB = new Vector3f();

    public WatcherModel() {
        buildSphere();
        RandomSource random = RandomSource.create(0x5EE5_7A7CL);
        buildVeins(random);
        buildTendrils(random);
    }

    // ---- Building (once) ---------------------------------------------------------------------

    private void buildSphere() {
        for (int i = 0; i <= RINGS; i++) {
            float theta = Mth.PI * i / RINGS; // 0 = centre of the pupil, PI = the very back
            for (int j = 0; j <= SEGMENTS; j++) {
                float phi = Mth.TWO_PI * j / SEGMENTS;
                int k = index(i, j);
                dirX[k] = Mth.sin(theta) * Mth.cos(phi);
                dirY[k] = Mth.sin(theta) * Mth.sin(phi);
                dirZ[k] = Mth.cos(theta);
                colour[k] = surfaceColour(theta, phi);
            }
        }
    }

    /** The painted look of the eyeball at angle theta from the pupil and phi around it. */
    private static int surfaceColour(float theta, float phi) {
        float r;
        float g;
        float b;
        if (theta < IRIS_ANGLE) {
            // Iris: sickly amber in the middle fading to a dark red-brown, with radial fibres.
            float t = theta / IRIS_ANGLE;
            float fibres = 0.5f + 0.5f * Mth.sin(phi * 23.0f + 2.0f * Mth.sin(phi * 7.0f));
            r = Mth.lerp(t, 0.62f, 0.30f) * (0.8f + 0.25f * fibres);
            g = Mth.lerp(t, 0.42f, 0.10f) * (0.75f + 0.3f * fibres);
            b = Mth.lerp(t, 0.12f, 0.06f);
            float rim = smoothstep(IRIS_ANGLE - 0.1f, IRIS_ANGLE, theta); // dark limbal ring
            r *= 1.0f - 0.7f * rim;
            g *= 1.0f - 0.7f * rim;
            b *= 1.0f - 0.7f * rim;
        } else {
            // Sclera: pale, yellowing, pink and irritated around the iris, bruised at the back.
            r = 0.88f;
            g = 0.85f;
            b = 0.76f;
            float irritated = (float) Math.exp(-(theta - IRIS_ANGLE) * 2.5f) * 0.55f;
            r = Mth.lerp(irritated, r, 0.86f);
            g = Mth.lerp(irritated, g, 0.52f);
            b = Mth.lerp(irritated, b, 0.52f);
            float back = smoothstep(2.2f, 3.0f, theta);
            r = Mth.lerp(back, r, 0.42f);
            g = Mth.lerp(back, g, 0.12f);
            b = Mth.lerp(back, b, 0.20f);
            // Fine capillaries: thin wavy lines running roughly from the back toward the iris.
            float wobble = 0.6f * Mth.sin(theta * 9.0f + phi * 3.0f);
            float lines = Math.max(sharp(Mth.sin(phi * 17.0f + wobble)), sharp(Mth.sin(phi * 29.0f + 1.7f + wobble * 1.4f)) * 0.7f);
            float fineVeins = lines * smoothstep(IRIS_ANGLE + 0.15f, 1.6f, theta) * 0.85f;
            // Dark red so the capillaries stand out against the pale white.
            r = Mth.lerp(fineVeins, r, 0.50f);
            g = Mth.lerp(fineVeins, g, 0.03f);
            b = Mth.lerp(fineVeins, b, 0.06f);
            r *= SCLERA_SELF_LIGHT;
            g *= SCLERA_SELF_LIGHT;
            b *= SCLERA_SELF_LIGHT;
        }
        return argb(r, g, b);
    }

    /** 1 on the crest of a sine wave, falling off fast: thin bright lines. */
    private static float sharp(float sine) {
        float x = 1.0f - Math.abs(sine);
        x *= x;
        x *= x;
        return x * x;
    }

    /** Thick veins crawling from the back of the eye toward the iris, each with a couple of branches. */
    private void buildVeins(RandomSource random) {
        for (int i = 0; i < 16; i++) {
            float phi = random.nextFloat() * Mth.TWO_PI;
            float theta = 2.3f + random.nextFloat() * 0.6f;
            float endTheta = IRIS_ANGLE + 0.12f + random.nextFloat() * 0.5f;
            float width = 0.07f + random.nextFloat() * 0.05f;
            List<float[]> path = crawl(random, theta, phi, endTheta, 0.08f);
            veins.add(toVein(path, width, argb(0.20f, 0.01f, 0.07f))); // near-black purple-red: strong contrast
            // Branches split off somewhere along the way and wander off sideways.
            for (int b = 0; b < 2; b++) {
                float[] from = path.get(1 + random.nextInt(Math.max(1, path.size() - 2)));
                float drift = (random.nextBoolean() ? 1 : -1) * (0.08f + random.nextFloat() * 0.1f);
                List<float[]> branch = crawlWithDrift(random, from[0], from[1], from[0] - 0.4f - random.nextFloat() * 0.6f, drift);
                veins.add(toVein(branch, width * 0.55f, argb(0.30f, 0.02f, 0.04f)));
            }
        }
    }

    private static List<float[]> crawl(RandomSource random, float theta, float phi, float endTheta, float jitter) {
        List<float[]> path = new ArrayList<>();
        while (theta > endTheta && path.size() < 40) {
            path.add(new float[] {theta, phi});
            theta -= 0.08f;
            phi += (random.nextFloat() - 0.5f) * jitter * 2.0f / Math.max(0.3f, Mth.sin(theta));
        }
        path.add(new float[] {theta, phi});
        return path;
    }

    private static List<float[]> crawlWithDrift(RandomSource random, float theta, float phi, float endTheta, float drift) {
        List<float[]> path = new ArrayList<>();
        endTheta = Math.max(endTheta, IRIS_ANGLE + 0.1f);
        while (theta > endTheta && path.size() < 20) {
            path.add(new float[] {theta, phi});
            theta -= 0.06f;
            phi += (drift + (random.nextFloat() - 0.5f) * 0.1f) / Math.max(0.3f, Mth.sin(theta));
        }
        path.add(new float[] {theta, phi});
        return path;
    }

    private static Vein toVein(List<float[]> path, float startWidth, int colour) {
        Vector3f[] points = new Vector3f[path.size()];
        float[] widths = new float[path.size()];
        for (int i = 0; i < points.length; i++) {
            float theta = path.get(i)[0];
            float phi = path.get(i)[1];
            points[i] = new Vector3f(Mth.sin(theta) * Mth.cos(phi), Mth.sin(theta) * Mth.sin(phi), Mth.cos(theta));
            widths[i] = startWidth * (1.0f - 0.75f * i / Math.max(1, points.length - 1)); // tapers toward the tip
        }
        return new Vein(points, widths, colour);
    }

    /** The thick optic nerve straight out the back, and a fan of thinner tendrils hanging below it. */
    private void buildTendrils(RandomSource random) {
        tendrils.add(new Tendril(new Vector3f(0.0f, -0.25f, -1.0f).normalize(), new Vector3f(0.0f, -0.45f, -1.0f).normalize(),
                2.6f, 0.42f, 0.0f, 0.6f));
        for (int i = 0; i < 7; i++) {
            float phi = Mth.TWO_PI * i / 7.0f + random.nextFloat() * 0.4f;
            float theta = 2.45f + random.nextFloat() * 0.45f; // around the back of the eye
            Vector3f root = new Vector3f(Mth.sin(theta) * Mth.cos(phi), Mth.sin(theta) * Mth.sin(phi), Mth.cos(theta));
            root.y = Math.min(root.y, 0.1f); // hang from the lower half
            root.normalize();
            Vector3f heading = new Vector3f(root.x * 0.6f, -1.0f, root.z - 0.4f).normalize();
            tendrils.add(new Tendril(root, heading, 3.4f + random.nextFloat() * 2.4f, 0.14f + random.nextFloat() * 0.1f,
                    random.nextFloat() * Mth.TWO_PI, 0.7f + random.nextFloat() * 0.6f));
        }
    }

    // ---- Drawing (every frame) ----------------------------------------------------------------

    /**
     * Draws the whole eye in the current pose (eye space).
     *
     * @param ageInTicks  smooth time for the tendril sway
     * @param pupilAngle  angular radius of the pupil (radians): ~0.12 pinpoint .. ~0.34 wide open
     * @param swayPhase   per-entity offset so two Watchers don't sway in step
     */
    public void render(PoseStack poseStack, VertexConsumer out, int light, float ageInTicks, float pupilAngle, float swayPhase) {
        PoseStack.Pose pose = poseStack.last();
        drawSphere(pose, out, light);
        for (Vein vein : veins) {
            drawVein(pose, out, light, vein);
        }
        drawPupil(pose, out, light, pupilAngle);
        for (Tendril tendril : tendrils) {
            drawTendril(pose, out, light, tendril, ageInTicks, swayPhase);
        }
    }

    /**
     * The iris's sickly amber glow, drawn additively over the eye: brightest at the pupil's edge,
     * fading out to the iris rim. Uses a POSITION_COLOR consumer (the glow render type).
     *
     * @param strength 0..1 (it burns brighter while staring / charging)
     */
    public void renderIrisGlow(PoseStack poseStack, VertexConsumer glow, float pupilAngle, float strength) {
        PoseStack.Pose pose = poseStack.last();
        int sides = 40;
        float lift = RADIUS * 1.008f;
        float[] angles = {pupilAngle, (pupilAngle + IRIS_ANGLE) * 0.5f, IRIS_ANGLE + 0.04f};
        float[] alphas = {0.55f * strength, 0.3f * strength, 0.0f};
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < sides; i++) {
                float a0 = Mth.TWO_PI * i / sides;
                float a1 = Mth.TWO_PI * (i + 1) / sides;
                glowVertex(pose, glow, angles[ring], a0, lift, alphas[ring]);
                glowVertex(pose, glow, angles[ring + 1], a0, lift, alphas[ring + 1]);
                glowVertex(pose, glow, angles[ring + 1], a1, lift, alphas[ring + 1]);
                glowVertex(pose, glow, angles[ring], a1, lift, alphas[ring]);
            }
        }
    }

    private static void glowVertex(PoseStack.Pose pose, VertexConsumer glow, float theta, float phi, float radius, float alpha) {
        float s = Mth.sin(theta) * radius;
        glow.addVertex(pose, s * Mth.cos(phi), s * Mth.sin(phi), Mth.cos(theta) * radius)
                .setColor(255, 170, 60, Mth.clamp((int) (alpha * 255.0f), 0, 255));
    }

    private void drawSphere(PoseStack.Pose pose, VertexConsumer out, int light) {
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                sphereVertex(pose, out, light, index(i, j));
                sphereVertex(pose, out, light, index(i + 1, j));
                sphereVertex(pose, out, light, index(i + 1, j + 1));
                sphereVertex(pose, out, light, index(i, j + 1));
            }
        }
    }

    private void sphereVertex(PoseStack.Pose pose, VertexConsumer out, int light, int k) {
        vertex(pose, out, dirX[k] * RADIUS, dirY[k] * RADIUS, dirZ[k] * RADIUS, colour[k], light, dirX[k], dirY[k], dirZ[k]);
    }

    /** A flat ribbon a hair above the surface, following the vein's points. */
    private void drawVein(PoseStack.Pose pose, VertexConsumer out, int light, Vein vein) {
        float lift = RADIUS * 1.006f;
        Vector3f[] p = vein.points();
        for (int i = 0; i + 1 < p.length; i++) {
            Vector3f a = p[i];
            Vector3f b = p[i + 1];
            tangent.set(b).sub(a);
            sideA.set(tangent).cross(a).normalize().mul(vein.widths()[i] * 0.5f);
            sideB.set(tangent).cross(b).normalize().mul(vein.widths()[i + 1] * 0.5f);
            vertex(pose, out, a.x * lift - sideA.x, a.y * lift - sideA.y, a.z * lift - sideA.z, vein.colour(), light, a.x, a.y, a.z);
            vertex(pose, out, a.x * lift + sideA.x, a.y * lift + sideA.y, a.z * lift + sideA.z, vein.colour(), light, a.x, a.y, a.z);
            vertex(pose, out, b.x * lift + sideB.x, b.y * lift + sideB.y, b.z * lift + sideB.z, vein.colour(), light, b.x, b.y, b.z);
            vertex(pose, out, b.x * lift - sideB.x, b.y * lift - sideB.y, b.z * lift - sideB.z, vein.colour(), light, b.x, b.y, b.z);
        }
    }

    /** The pupil: a near-black cap over the centre of the iris, sized by {@code angle}. */
    private void drawPupil(PoseStack.Pose pose, VertexConsumer out, int light, float angle) {
        int black = argb(0.025f, 0.012f, 0.02f);
        float lift = RADIUS * 1.003f;
        int sides = 28;
        float s = Mth.sin(angle) * lift;
        float c = Mth.cos(angle) * lift;
        for (int i = 0; i < sides; i++) {
            float a0 = Mth.TWO_PI * i / sides;
            float a1 = Mth.TWO_PI * (i + 1) / sides;
            // Triangle fan from the centre, written as quads with the centre doubled.
            vertex(pose, out, 0.0f, 0.0f, lift, black, light, 0.0f, 0.0f, 1.0f);
            vertex(pose, out, s * Mth.cos(a0), s * Mth.sin(a0), c, black, light, 0.0f, 0.0f, 1.0f);
            vertex(pose, out, s * Mth.cos(a1), s * Mth.sin(a1), c, black, light, 0.0f, 0.0f, 1.0f);
            vertex(pose, out, 0.0f, 0.0f, lift, black, light, 0.0f, 0.0f, 1.0f);
        }
    }

    /** A tapering tube along a curve that bends from its heading toward straight down, swaying more at the tip. */
    private void drawTendril(PoseStack.Pose pose, VertexConsumer out, int light, Tendril t, float age, float swayPhase) {
        float step = t.length() / TENDRIL_SEGMENTS;
        centre.set(t.root()).mul(RADIUS * 0.96f);
        float time = age * 0.05f * t.swaySpeed() + t.phase() + swayPhase;
        ringAround(centre, t.heading(), t.thickness(), ringA);
        for (int k = 1; k <= TENDRIL_SEGMENTS; k++) {
            float along = k / (float) TENDRIL_SEGMENTS;
            // Direction: from the heading, curling down; plus a slow sway that grows toward the tip.
            float bend = Math.min(1.0f, along * 1.3f);
            float sway = along * along * 0.9f;
            next.set(t.heading()).lerp(DOWN, bend * 0.7f);
            next.x += Mth.sin(time + along * 2.2f) * sway;
            next.z += Mth.cos(time * 0.8f + along * 1.7f) * sway * 0.6f;
            next.normalize();
            tangent.set(next);
            next.mul(step).add(centre);
            float radius = t.thickness() * (1.0f - 0.85f * along);
            ringAround(next, tangent, radius, ringB);
            int colourA = tendrilColour((k - 1) / (float) TENDRIL_SEGMENTS);
            int colourB = tendrilColour(along);
            for (int s = 0; s < TUBE_SIDES; s++) {
                int s1 = (s + 1) % TUBE_SIDES;
                tubeVertex(pose, out, light, ringA[s], centre, colourA);
                tubeVertex(pose, out, light, ringB[s], next, colourB);
                tubeVertex(pose, out, light, ringB[s1], next, colourB);
                tubeVertex(pose, out, light, ringA[s1], centre, colourA);
            }
            centre.set(next);
            copyRing(ringB, ringA); // this segment's end ring is the next one's start
        }
    }

    private static final Vector3f DOWN = new Vector3f(0.0f, -1.0f, -0.25f).normalize();

    private static void copyRing(Vector3f[] from, Vector3f[] to) {
        for (int i = 0; i < from.length; i++) {
            to[i].set(from[i]);
        }
    }

    /** Points of a ring of the tube around {@code c}, perpendicular to {@code axis}. */
    private void ringAround(Vector3f c, Vector3f axis, float radius, Vector3f[] ring) {
        // Any vector not parallel to the axis gives a frame; (1, 0, 0) unless the axis is nearly x.
        if (Math.abs(axis.x) < 0.9f) {
            sideA.set(1.0f, 0.0f, 0.0f);
        } else {
            sideA.set(0.0f, 1.0f, 0.0f);
        }
        sideA.cross(axis).normalize();
        sideB.set(axis).cross(sideA).normalize();
        for (int s = 0; s < TUBE_SIDES; s++) {
            float a = Mth.TWO_PI * s / TUBE_SIDES;
            float cos = Mth.cos(a) * radius;
            float sin = Mth.sin(a) * radius;
            ring[s].set(c.x + sideA.x * cos + sideB.x * sin, c.y + sideA.y * cos + sideB.y * sin, c.z + sideA.z * cos + sideB.z * sin);
        }
    }

    private void tubeVertex(PoseStack.Pose pose, VertexConsumer out, int light, Vector3f p, Vector3f axisPoint, int colour) {
        float nx = p.x - axisPoint.x;
        float ny = p.y - axisPoint.y;
        float nz = p.z - axisPoint.z;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-5f) {
            length = 1.0f;
        }
        vertex(pose, out, p.x, p.y, p.z, colour, light, nx / length, ny / length, nz / length);
    }

    /** Dark crimson at the root, bruised purple in the middle, a greyish pink at the tip (dimmed: self-lit). */
    private static int tendrilColour(float along) {
        float k = TENDRIL_SELF_LIGHT;
        if (along < 0.5f) {
            float t = along / 0.5f;
            return argb(Mth.lerp(t, 0.36f, 0.30f) * k, Mth.lerp(t, 0.04f, 0.08f) * k, Mth.lerp(t, 0.10f, 0.24f) * k);
        }
        float t = (along - 0.5f) / 0.5f;
        return argb(Mth.lerp(t, 0.30f, 0.52f) * k, Mth.lerp(t, 0.08f, 0.38f) * k, Mth.lerp(t, 0.24f, 0.42f) * k);
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, int colour, int light,
                               float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static int index(int ring, int segment) {
        return ring * (SEGMENTS + 1) + segment;
    }

    private static Vector3f[] newRing() {
        Vector3f[] ring = new Vector3f[TUBE_SIDES];
        for (int i = 0; i < ring.length; i++) {
            ring[i] = new Vector3f();
        }
        return ring;
    }

    static int argb(float r, float g, float b) {
        return 0xFF000000 | (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    private static int clampByte(float v) {
        return Mth.clamp((int) (v * 255.0f + 0.5f), 0, 255);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
