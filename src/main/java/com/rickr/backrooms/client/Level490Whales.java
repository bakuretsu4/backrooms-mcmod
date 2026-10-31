package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 490's sky whales (inspired by Re:Zero's White Whale, our own design): bone-white, their
 * skin fine-haired, fog pouring off them so they swim wrapped in their own mist.
 * <ul>
 *   <li><b>Pods</b> of two to four, 60-110 blocks long, swim slow loops high under the storm, now
 *       and then diving into the cloud sea and rising out of it again (only well away from you: a
 *       dive near by would go through the islands).</li>
 *   <li><b>The Great Whale</b>, about 400 blocks long, wanders a much wider, slower loop, so now
 *       and then it passes right over you.</li>
 *   <li>At the top of the spire it comes down out of the storm to hang beside the tower, its eye on
 *       you, then swims back up into the storm ({@link #rise}, sent by world/Level490Falls).</li>
 * </ul>
 * Where every whale is follows from the layout seed and the game clock, so everyone sees the same
 * whales. They are drawn in the world after the terrain, with the depth buffer (islands in front
 * hide them), shaded by the moon, fading into the haze with distance, and lit by lightning.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Whales {
    private static final int POD_CELL = 2600;
    private static final int GREAT_CELL = 6000;
    private static final double MINUTE = 1200.0;
    /** How far whales are drawn. */
    private static final double REACH = 5000.0;
    private static final ResourceLocation PUFF = Backrooms.id("textures/environment/l490_puff.png");
    private static final Vector3f MOON = new Vector3f((float) (Math.cos(Math.toRadians(24)) * Math.cos(Math.toRadians(35))),
            (float) Math.sin(Math.toRadians(24)), (float) (Math.cos(Math.toRadians(24)) * Math.sin(Math.toRadians(35))));
    // Bone-white skin, a little greyer underneath.
    private static final float[] SKIN = {0.86f, 0.85f, 0.81f};
    private static final float[] BELLY = {0.72f, 0.72f, 0.71f};
    private static final float[] EYE = {0.05f, 0.04f, 0.05f};

    /** One whale right now: where its head points (yaw, pitch), how long it is, its swim phase. */
    record Whale(double x, double y, double z, double yaw, double pitch, float length, double swim, boolean great, long id) {}

    /** The Great Whale's visit at the top of the spire: where, when (game tick), looking along yaw toward the player. */
    private record Rise(double x, double y, double z, long start, double yaw) {}

    private static Rise rise;
    private static final double RISE_TICKS = 1300;

    private Level490Whales() {}

    /** Network: the Great Whale comes to (x, y, z) beside the spire at game tick {@code start}, facing {@code yaw}. */
    public static void rise(double x, double y, double z, long start, float yaw) {
        rise = new Rise(x, y, z, start, Math.toRadians(yaw));
    }

    // ==========================================================================================
    // Where the whales are
    // ==========================================================================================

    private static double smooth(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return c * c * (3 - 2 * c);
    }

    /** A loop round (cx, cz) of radius r, period p ticks, at time t: {x, y-offset, z}. */
    private static double[] loop(double cx, double cz, double r, double p, double t, double phase, double sign) {
        double a = phase + sign * 2 * Math.PI * t / p;
        // A wandering loop, not a circle: the radius breathes.
        double rr = r * (1 + 0.25 * Math.sin(a * 2 + phase));
        return new double[] {cx + rr * Math.cos(a), 0, cz + rr * Math.sin(a)};
    }

    /** Every whale within REACH of the camera at game time t. */
    static List<Whale> whales(long seed, double t, Vec3 eye) {
        List<Whale> out = new ArrayList<>();
        int ci = Math.floorDiv((int) eye.x, POD_CELL);
        int cj = Math.floorDiv((int) eye.z, POD_CELL);
        for (int i = ci - 2; i <= ci + 2; i++) {
            for (int j = cj - 2; j <= cj + 2; j++) {
                long h = Hash.of(seed, i, j, 4990);
                if (Hash.unit(h) > 0.55) {
                    continue;
                }
                double cx = (i + 0.5) * POD_CELL;
                double cz = (j + 0.5) * POD_CELL;
                double r = 500 + 600 * Hash.unit(Hash.mix(h));
                double p = MINUTE * (12 + 13 * Hash.unit(Hash.mix(h + 1)));
                double phase = Hash.unit(Hash.mix(h + 2)) * Math.PI * 2;
                double sign = Hash.unit(Hash.mix(h + 3)) < 0.5 ? 1 : -1;
                double baseY = 520 + 70 * Hash.unit(Hash.mix(h + 4));
                int n = 2 + Hash.range(Hash.mix(h + 5), 3);
                for (int k = 0; k < n; k++) {
                    long hk = Hash.of(h, k, 0, 4991);
                    double lag = k * 0.04 * p / (2 * Math.PI);
                    double side = (Hash.unit(hk) - 0.5) * 140;
                    double up = (Hash.unit(Hash.mix(hk)) - 0.5) * 50;
                    float length = (float) (60 + 50 * Hash.unit(Hash.mix(hk + 1)));
                    add(out, cx, cz, r + side, p, t - lag, phase, sign, baseY + up, length, false, eye, hk, true);
                }
            }
        }
        int gi = Math.floorDiv((int) eye.x, GREAT_CELL);
        int gj = Math.floorDiv((int) eye.z, GREAT_CELL);
        for (int i = gi - 1; i <= gi + 1; i++) {
            for (int j = gj - 1; j <= gj + 1; j++) {
                long h = Hash.of(seed, i, j, 4995);
                if (Hash.unit(h) > 0.7) {
                    continue;
                }
                double cx = (i + 0.5) * GREAT_CELL;
                double cz = (j + 0.5) * GREAT_CELL;
                double r = 1500 + 1100 * Hash.unit(Hash.mix(h));
                double p = MINUTE * (40 + 30 * Hash.unit(Hash.mix(h + 1)));
                add(out, cx, cz, r, p, t, Hash.unit(Hash.mix(h + 2)) * Math.PI * 2, Hash.unit(Hash.mix(h + 3)) < 0.5 ? 1 : -1,
                        545, 400, true, eye, h, false);
            }
        }
        if (rise != null) {
            double age = t - rise.start;
            if (age > RISE_TICKS) {
                rise = null;
            } else if (age > 0) {
                // Down out of the storm, hanging beside the tower a while, then back up into it.
                double down = smooth(age / 300);
                double up = smooth((age - 900) / 400);
                double y = 720 + (rise.y - 720) * down + (760 - rise.y) * up;
                double pitch = -0.35 * (1 - down) + 0.45 * up;
                out.add(new Whale(rise.x, y, rise.z, rise.yaw, pitch, 380, t / 20.0 * 0.1, true, 7L));
            }
        }
        out.removeIf(w -> Math.hypot(w.x - eye.x, w.z - eye.z) > REACH);
        return out;
    }

    private static void add(List<Whale> out, double cx, double cz, double r, double p, double t, double phase, double sign,
                            double baseY, float length, boolean great, Vec3 eye, long id, boolean dives) {
        double[] a = loop(cx, cz, r, p, t, phase, sign);
        double[] b = loop(cx, cz, r, p, t + 20, phase, sign);
        double y = baseY + 25 * Math.sin(2 * Math.PI * t / (p * 0.37) + phase);
        double y2 = baseY + 25 * Math.sin(2 * Math.PI * (t + 20) / (p * 0.37) + phase);
        if (dives) {
            // Once a loop: down into the cloud sea and back up, but only well away from you.
            double cycle = ((t / p + phase) % 1 + 1) % 1;
            double cycle2 = (((t + 20) / p + phase) % 1 + 1) % 1;
            double away = smooth((Math.hypot(a[0] - eye.x, a[2] - eye.z) - 450) / 400);
            y -= 470 * dive(cycle) * away;
            y2 -= 470 * dive(cycle2) * away;
        }
        double yaw = Math.atan2(b[2] - a[2], b[0] - a[0]);
        double pitch = Math.atan2(y2 - y, Math.hypot(b[0] - a[0], b[2] - a[2]));
        double swim = t / 20.0 * (great ? 0.11 : 0.17) + phase;
        out.add(new Whale(a[0], y, a[2], yaw, pitch, length, swim, great, id));
    }

    /** How deep into its dive a whale is at this point of its loop (0 = none, 1 = deepest). */
    private static double dive(double cycle) {
        if (cycle < 0.55 || cycle > 0.72) {
            return 0;
        }
        double f = (cycle - 0.55) / 0.17;
        return Math.sin(Math.PI * f);
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !Level490Atmosphere.inLevel490()
                || !Level490ClientState.hasSeed()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = event.getCamera().getPosition();
        List<Whale> whales = whales(Level490ClientState.seed(), t, eye);
        if (whales.isEmpty()) {
            return;
        }
        float far = mc.gameRenderer.getDepthFar();
        Matrix4f pose = event.getPoseStack().last().pose();
        float[] fog = Level490Atmosphere.fogColour(new float[3]);
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        // Bodies: solid, with the depth buffer.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder tris = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        List<float[]> puffs = new ArrayList<>();
        for (Whale w : whales) {
            body(tris, pose, w, eye, far, fog, puffs, seconds);
        }
        BufferUploader.drawWithShader(tris.buildOrThrow());
        // The fog pouring off them: soft puffs, farthest first, not hiding each other in the depth buffer.
        puffs.sort((a, b) -> Float.compare(b[6], a[6]));
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PUFF);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f dir = new Vector3f();
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f();
        for (float[] p : puffs) {
            dir.set(p[0], p[1], p[2]).normalize();
            right.set(-dir.z, 0, dir.x).normalize();
            right.cross(dir, up).normalize();
            float s = p[3];
            float c = p[4];
            for (int k = 0; k < 4; k++) {
                float sx = (k == 1 || k == 2) ? 1 : -1;
                float sy = k >= 2 ? 1 : -1;
                quads.addVertex(pose, p[0] + (right.x * sx + up.x * sy) * s, p[1] + (right.y * sx + up.y * sy) * s,
                                p[2] + (right.z * sx + up.z * sy) * s)
                        .setUv(sx > 0 ? 1 : 0, sy > 0 ? 0 : 1).setColor(c, c, c * 1.04f, p[5]);
            }
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * One whale's body: rings along its length (a blunt head, a long body, a narrow tail stock),
     * swimming in a slow wave that grows toward the tail; long pectoral fins; broad flukes; dark eyes;
     * grooves down its throat. Its fog puffs are added to {@code puffs}.
     */
    private static void body(BufferBuilder tris, Matrix4f pose, Whale w, Vec3 eye, float far, float[] fog, List<float[]> puffs,
                             double seconds) {
        double d = Math.sqrt((w.x - eye.x) * (w.x - eye.x) + (w.y - eye.y) * (w.y - eye.y) + (w.z - eye.z) * (w.z - eye.z));
        // Far off, everything is pulled in toward the camera to stay inside the far plane (it looks the same).
        float scale = (float) Math.min(1.0, 0.85 * far / Math.max(1.0, d + w.length * 0.6));
        float haze = (float) (0.45 * (1 - Math.exp(-d / 400.0)) + 0.4 * (1 - Math.exp(-d / 3000.0)));
        float glow = Level490Lightning.cloudGlow(w.x, w.z);
        haze *= 1 - 0.5f * glow;
        // Diving into the cloud sea it is lost in the cloud's grey (it is drawn over the clouds).
        haze = Math.max(haze, (float) smooth((175.0 - w.y) / 70.0));
        int rings = d > 1500 ? 14 : 26;
        int around = d > 1500 ? 10 : 16;
        float length = w.length;
        float r0 = length * (w.great ? 0.12f : 0.11f);
        // The body's frame: forward along its heading, up, and right.
        Vector3f fwd = new Vector3f((float) (Math.cos(w.pitch) * Math.cos(w.yaw)), (float) Math.sin(w.pitch),
                (float) (Math.cos(w.pitch) * Math.sin(w.yaw)));
        Vector3f rgt = new Vector3f(fwd).cross(0, 1, 0).normalize();
        Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
        Vector3f centre = new Vector3f((float) (w.x - eye.x), (float) (w.y - eye.y), (float) (w.z - eye.z));
        float[][][] ring = new float[rings + 1][around][];
        for (int k = 0; k <= rings; k++) {
            float t = k / (float) rings;
            float r = radius(t) * r0;
            // The swimming wave: up and down, stronger toward the tail.
            float wave = (float) (0.07 * length * Math.sin(2 * Math.PI * (t * 0.9) - w.swim * 2 * Math.PI) * t * t);
            float along = (0.5f - t) * length;
            for (int a = 0; a < around; a++) {
                double ang = 2 * Math.PI * a / around;
                float cs = (float) Math.cos(ang);
                float sn = (float) Math.sin(ang);
                // Wider than tall, the belly flatter.
                float lx = cs * r * 1.05f;
                float ly = sn * r * (sn < 0 ? 0.82f : 0.95f) + wave;
                Vector3f p = new Vector3f(fwd).mul(along).add(new Vector3f(rgt).mul(lx)).add(new Vector3f(upv).mul(ly)).add(centre);
                Vector3f n = new Vector3f(rgt).mul(cs).add(new Vector3f(upv).mul(sn)).normalize();
                float[] col = colour(t, ang, sn, w.id, k, a);
                ring[k][a] = new float[] {p.x, p.y, p.z, n.x, n.y, n.z, col[0], col[1], col[2]};
            }
        }
        for (int k = 0; k < rings; k++) {
            for (int a = 0; a < around; a++) {
                int a1 = (a + 1) % around;
                // Outward-facing: (k, a), (k, a+1), (k+1, a+1) and (k, a), (k+1, a+1), (k+1, a).
                tri(tris, pose, ring[k][a], ring[k + 1][a], ring[k + 1][a1], scale, haze, glow, fog);
                tri(tris, pose, ring[k][a], ring[k + 1][a1], ring[k][a1], scale, haze, glow, fog);
            }
        }
        // Nose and tail caps.
        cap(tris, pose, ring[0], new Vector3f(fwd).mul(length * 0.5f + r0 * 0.15f).add(centre), fwd, true, scale, haze, glow, fog);
        cap(tris, pose, ring[rings], new Vector3f(fwd).mul(-length * 0.5f).add(centre), new Vector3f(fwd).negate(), false, scale, haze, glow, fog);
        // Long pectoral fins, slowly sweeping; broad flukes at the tail.
        float flap = (float) Math.sin(w.swim * 2 * Math.PI + 1.0) * 0.25f;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f root = new Vector3f(fwd).mul(length * 0.22f).add(new Vector3f(rgt).mul(side * r0 * 0.95f))
                    .add(new Vector3f(upv).mul(-r0 * 0.35f)).add(centre);
            Vector3f out = new Vector3f(rgt).mul(side).add(new Vector3f(upv).mul(-0.55f + flap)).add(new Vector3f(fwd).mul(-0.45f)).normalize();
            fin(tris, pose, root, out, fwd, length * 0.32f, length * 0.07f, scale, haze, glow, fog);
        }
        float tailWave = (float) (0.07 * length * Math.sin(2 * Math.PI * 0.9 - w.swim * 2 * Math.PI));
        Vector3f tail = new Vector3f(fwd).mul(-length * 0.5f).add(new Vector3f(upv).mul(tailWave)).add(centre);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f out = new Vector3f(rgt).mul(side).add(new Vector3f(fwd).mul(-0.35f)).normalize();
            fin(tris, pose, tail, out, upv, length * 0.2f, length * 0.06f, scale, haze, glow, fog);
        }
        // Dark eyes low on the sides of the head.
        for (int side = -1; side <= 1; side += 2) {
            Vector3f c = new Vector3f(fwd).mul(length * 0.36f).add(new Vector3f(rgt).mul(side * r0 * 0.93f))
                    .add(new Vector3f(upv).mul(-r0 * 0.1f)).add(centre);
            eye(tris, pose, c, new Vector3f(rgt).mul(side), fwd, r0 * 0.12f, scale, haze, fog);
        }
        // Fog pouring off the skin and trailing behind: puffs drifting tailward, swelling and thinning.
        int count = w.great ? 90 : 26;
        for (int k = 0; k < count; k++) {
            double f = ((k * 0.618 + seconds * (w.great ? 0.02 : 0.035)) % 1.0);
            float t = (float) f;
            float ang = (float) (k * 2.39996);
            float r = radius(Math.min(1, t)) * r0 * (1.0f + 0.6f * t);
            float along = (0.5f - t * 1.25f) * length;
            Vector3f p = new Vector3f(fwd).mul(along).add(new Vector3f(rgt).mul((float) Math.cos(ang) * r))
                    .add(new Vector3f(upv).mul((float) Math.sin(ang) * r)).add(centre).mul(scale);
            float alpha = (float) (0.35 * Math.sin(Math.PI * f)) * (1 - haze * 0.6f);
            float size = (r0 * (0.6f + 1.4f * t)) * scale;
            float c = 0.62f * (1 - haze) + fog[0] * haze + 0.4f * glow;
            puffs.add(new float[] {p.x, p.y, p.z, size, c, alpha, p.lengthSquared()});
        }
    }

    /** The body's radius along its length (0 nose, 1 tail), as a fraction of its widest. */
    private static float radius(float t) {
        if (t < 0.1f) {
            return (float) (0.55 + 0.45 * Math.sqrt(t / 0.1));
        }
        if (t < 0.42f) {
            return 1.0f - 0.08f * (t - 0.1f) / 0.32f;
        }
        double f = (t - 0.42) / 0.58;
        return (float) (0.92 * Math.pow(1 - f, 1.3) + 0.05);
    }

    /** Skin colour at a point: pale, greyer on the belly, grooves down the throat, a dark mouth line, faint mottling. */
    private static float[] colour(float t, double ang, float sn, long id, int k, int a) {
        float[] base = sn < -0.3f ? BELLY : SKIN;
        float m = 0.94f + 0.06f * (float) Hash.unit(id, k, a, 4996);
        if (sn < -0.35f && t > 0.04f && t < 0.42f && (a % 2 == 0)) {
            m *= 0.86f; // a throat groove
        }
        if (t < 0.18f && Math.abs(sn + 0.15f) < 0.12f) {
            m *= 0.55f; // the mouth line
        }
        return new float[] {base[0] * m, base[1] * m, base[2] * m};
    }

    private static void tri(BufferBuilder tris, Matrix4f pose, float[] a, float[] b, float[] c, float scale, float haze, float glow,
                            float[] fog) {
        vertex(tris, pose, a, scale, haze, glow, fog);
        vertex(tris, pose, b, scale, haze, glow, fog);
        vertex(tris, pose, c, scale, haze, glow, fog);
    }

    private static void vertex(BufferBuilder tris, Matrix4f pose, float[] v, float scale, float haze, float glow, float[] fog) {
        float light = 0.32f + 0.5f * Math.max(0.0f, v[3] * MOON.x + v[4] * MOON.y + v[5] * MOON.z) + 0.12f * Math.max(0.0f, v[4])
                + 0.7f * glow;
        float r = v[6] * light * (1 - haze) + fog[0] * haze;
        float g = v[7] * light * (1 - haze) + fog[1] * haze;
        float b = v[8] * light * (1 - haze) + fog[2] * haze;
        tris.addVertex(pose, v[0] * scale, v[1] * scale, v[2] * scale).setColor(r, g, b, 1.0f);
    }

    /** Closes the end ring onto a point (the nose or the tail). */
    private static void cap(BufferBuilder tris, Matrix4f pose, float[][] ring, Vector3f tip, Vector3f out, boolean front, float scale,
                            float haze, float glow, float[] fog) {
        float[] p = {tip.x, tip.y, tip.z, out.x, out.y, out.z, ring[0][6], ring[0][7], ring[0][8]};
        for (int a = 0; a < ring.length; a++) {
            int a1 = (a + 1) % ring.length;
            if (front) {
                tri(tris, pose, p, ring[a], ring[a1], scale, haze, glow, fog);
            } else {
                tri(tris, pose, p, ring[a1], ring[a], scale, haze, glow, fog);
            }
        }
    }

    /** A fin: a flat tapering blade from root along {@code out}, its width across {@code across}, both faces drawn. */
    private static void fin(BufferBuilder tris, Matrix4f pose, Vector3f root, Vector3f out, Vector3f across, float len, float width,
                            float scale, float haze, float glow, float[] fog) {
        Vector3f n = new Vector3f(out).cross(across).normalize();
        Vector3f tip = new Vector3f(out).mul(len).add(root);
        Vector3f a = new Vector3f(across).mul(width).add(root);
        Vector3f b = new Vector3f(across).mul(-width * 0.6f).add(root);
        Vector3f mid = new Vector3f(out).mul(len * 0.55f).add(root).add(new Vector3f(across).mul(-width * 0.5f));
        for (int face = -1; face <= 1; face += 2) {
            float[] pa = {a.x, a.y, a.z, n.x * face, n.y * face, n.z * face, SKIN[0], SKIN[1], SKIN[2]};
            float[] pb = {b.x, b.y, b.z, n.x * face, n.y * face, n.z * face, SKIN[0], SKIN[1], SKIN[2]};
            float[] pm = {mid.x, mid.y, mid.z, n.x * face, n.y * face, n.z * face, SKIN[0] * 0.95f, SKIN[1] * 0.95f, SKIN[2] * 0.95f};
            float[] pt = {tip.x, tip.y, tip.z, n.x * face, n.y * face, n.z * face, SKIN[0] * 0.9f, SKIN[1] * 0.9f, SKIN[2] * 0.9f};
            if (face > 0) {
                tri(tris, pose, pa, pt, pm, scale, haze, glow, fog);
                tri(tris, pose, pa, pm, pb, scale, haze, glow, fog);
            } else {
                tri(tris, pose, pa, pm, pt, scale, haze, glow, fog);
                tri(tris, pose, pa, pb, pm, scale, haze, glow, fog);
            }
        }
    }

    /** A dark eye: a small diamond standing just off the skin. */
    private static void eye(BufferBuilder tris, Matrix4f pose, Vector3f c, Vector3f out, Vector3f fwd, float r, float scale, float haze,
                            float[] fog) {
        Vector3f up = new Vector3f(out).cross(fwd).normalize();
        Vector3f p = new Vector3f(out).mul(r * 0.3f).add(c);
        float[][] q = new float[4][];
        Vector3f[] offs = {new Vector3f(fwd).mul(r), new Vector3f(up).mul(r * 0.7f), new Vector3f(fwd).mul(-r), new Vector3f(up).mul(-r * 0.7f)};
        for (int k = 0; k < 4; k++) {
            Vector3f v = new Vector3f(p).add(offs[k]);
            q[k] = new float[] {v.x, v.y, v.z, out.x, out.y, out.z, EYE[0], EYE[1], EYE[2]};
        }
        for (int face = 0; face < 2; face++) {
            tri(tris, pose, q[0], face == 0 ? q[1] : q[3], q[2], scale, haze, 0, fog);
        }
    }
}
