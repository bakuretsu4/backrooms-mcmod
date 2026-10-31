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
 * Level 490's sky whales (inspired by Re:Zero's White Whale, our own humpback-like design): pale
 * grey, barnacled and scarred (a painted skin), fog pouring off them so they swim wrapped in their
 * own mist.
 * <ul>
 *   <li><b>Pods</b> of two to four, 60-110 blocks long, swim slow loops high under the storm, now
 *       and then diving into the cloud sea and rising out of it again (only well away from you: a
 *       dive near by would go through the islands).</li>
 *   <li><b>The Great Whale</b>, about 400 blocks long, wanders a much wider, slower loop, so now
 *       and then it passes right over you.</li>
 *   <li>Every minute or two one swims past close, a little above you ({@link #visitor}).</li>
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
    private static final ResourceLocation SKIN = Backrooms.id("textures/entity/l490_whale.png");
    private static final Vector3f MOON = new Vector3f((float) (Math.cos(Math.toRadians(24)) * Math.cos(Math.toRadians(35))),
            (float) Math.sin(Math.toRadians(24)), (float) (Math.cos(Math.toRadians(24)) * Math.sin(Math.toRadians(35))));

    /** One whale right now: where its head points (yaw, pitch), how long it is, its swim phase. */
    record Whale(double x, double y, double z, double yaw, double pitch, float length, double swim, boolean great, long id) {}

    private Level490Whales() {}

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
        Whale v = visitor(seed, t, eye);
        if (v != null) {
            out.add(v);
        }
        if (pinned != null) {
            out.add(new Whale(pinned[0], pinned[1], pinned[2], pinned[3], 0, 80, t / 20.0 * 0.17, false, 7));
        }
        out.removeIf(w -> Math.hypot(w.x - eye.x, w.z - eye.z) > REACH);
        return out;
    }

    // ---- the whales that come close (after the play-test: "some slightly up close") ----

    /** A whale passing close: where it starts (game tick), its line {x0, y, z0, dx, dz}, its length. */
    private record Visit(double start, double x0, double y, double z0, double dx, double dz, float length, long id) {}

    private static Visit visit;
    private static double nextVisit = -1;
    /** Where the passing whale was last drawn {x, y, z} (for the scripted tests), or null. */
    private static double[] visitorAt;

    /** For the scripted tests: a whale held still at {x, y, z, yaw}, or null. */
    private static double[] pinned;

    static void pin(double x, double y, double z, double yaw) {
        pinned = new double[] {x, y, z, yaw};
    }

    static void unpin() {
        pinned = null;
    }

    /** For the scripted tests: send a whale past now. */
    static void visitNow() {
        visit = null;
        nextVisit = 0;
    }

    static double[] visitorAt() {
        return visit == null ? null : visitorAt;
    }
    private static final java.util.Random VISIT_RANDOM = new java.util.Random();
    /** Blocks a tick it swims, and how far its line runs either side of you. */
    private static final double VISIT_SPEED = 0.45;
    private static final double VISIT_HALF = 260;

    /**
     * Now and then (every minute or two) a whale swims by close: 35-70 blocks to one side of you,
     * 25-55 above, along a line that keeps clear of the colossal chains and the spire. Each player
     * gets their own (it follows where you are when it comes).
     */
    private static Whale visitor(long seed, double t, Vec3 eye) {
        if (nextVisit < 0) {
            nextVisit = t + 1200 + VISIT_RANDOM.nextInt(1200);
        }
        if (visit != null && (t - visit.start) * VISIT_SPEED > 2 * VISIT_HALF) {
            visit = null;
            nextVisit = t + 1200 + VISIT_RANDOM.nextInt(1800);
        }
        if (visit == null && t >= nextVisit) {
            visit = planVisit(seed, t, eye);
            if (visit == null) {
                nextVisit = t + 200;
            }
        }
        if (visit == null) {
            return null;
        }
        double along = (t - visit.start) * VISIT_SPEED - VISIT_HALF;
        double x = visit.x0 + visit.dx * along;
        double z = visit.z0 + visit.dz * along;
        double y = visit.y + 6 * Math.sin(along / 60.0);
        double yaw = Math.atan2(visit.dz, visit.dx);
        visitorAt = new double[] {x, y, z};
        return new Whale(x, y, z, yaw, 0.05 * Math.cos(along / 60.0), visit.length, t / 20.0 * 0.17, false, visit.id);
    }

    private static Visit planVisit(long seed, double t, Vec3 eye) {
        java.util.List<int[]> chains = Level490ClientState.chains(eye.x, eye.z);
        int[] site = com.rickr.backrooms.world.layout.Level490Layout.siteCentre(seed, (int) eye.x, (int) eye.z);
        for (int tries = 0; tries < 12; tries++) {
            double heading = VISIT_RANDOM.nextDouble() * Math.PI * 2;
            double dx = Math.cos(heading);
            double dz = Math.sin(heading);
            double side = (35 + 35 * VISIT_RANDOM.nextDouble()) * (VISIT_RANDOM.nextBoolean() ? 1 : -1);
            double x0 = eye.x - dz * side;
            double z0 = eye.z + dx * side;
            boolean clear = true;
            for (double a = -VISIT_HALF; a <= VISIT_HALF && clear; a += 8) {
                double x = x0 + dx * a;
                double z = z0 + dz * a;
                for (int[] c : chains) {
                    if (Math.hypot(c[0] - x, c[1] - z) < 30) {
                        clear = false;
                        break;
                    }
                }
                if (Math.hypot(site[0] - x, site[1] - z) < 75) {
                    clear = false;
                }
            }
            if (clear) {
                double y = Math.max(eye.y, 205) + 25 + 30 * VISIT_RANDOM.nextDouble();
                return new Visit(t, x0, y, z0, dx, dz, (float) (70 + 30 * VISIT_RANDOM.nextDouble()), VISIT_RANDOM.nextLong());
            }
        }
        return null;
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
        // Bodies: solid, textured, with the depth buffer.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SKIN);
        BufferBuilder tris = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        List<float[]> puffs = new ArrayList<>();
        for (Whale w : whales) {
            body(tris, pose, w, eye, far, fog, puffs, seconds);
        }
        BufferUploader.drawWithShader(tris.buildOrThrow());
        // The fog pouring off them: soft puffs, farthest first, not hiding each other in the depth buffer.
        puffs.sort((p, q) -> Float.compare(q[6], p[6]));
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
            float sz = p[3];
            float c = p[4];
            for (int k = 0; k < 4; k++) {
                float sx = (k == 1 || k == 2) ? 1 : -1;
                float sy = k >= 2 ? 1 : -1;
                quads.addVertex(pose, p[0] + (right.x * sx + up.x * sy) * sz, p[1] + (right.y * sx + up.y * sy) * sz,
                                p[2] + (right.z * sx + up.z * sy) * sz)
                        .setUv(sx > 0 ? 1 : 0, sy > 0 ? 0 : 1).setColor(c, c, c * 1.04f, p[5]);
            }
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** The texture's rows: the body wraps the top three quarters, the fins the last quarter. */
    private static final float BODY_V = 0.75f;

    /**
     * One whale's body (after the play-test: "a different design, higher quality"): a humpback-like
     * whale. A broad, flattened head with knobs along its top; a long body swelling behind the head
     * and narrowing to the tail stock, swimming in a slow wave that grows toward the tail; throat
     * pleats as real grooves under the chin; a small hump of a dorsal fin; very long pale pectoral
     * fins with a scalloped leading edge; broad flukes with a notch and a ragged trailing edge; dark
     * eyes under a ridge. Its skin is a painted texture (textures/entity/l490_whale). Finer near
     * you, coarser far off. Fog pours off it.
     */
    private static void body(BufferBuilder tris, Matrix4f pose, Whale w, Vec3 eye, float far, float[] fog, List<float[]> puffs,
                             double seconds) {
        double d = Math.sqrt((w.x - eye.x) * (w.x - eye.x) + (w.y - eye.y) * (w.y - eye.y) + (w.z - eye.z) * (w.z - eye.z));
        float scale = (float) Math.min(1.0, 0.85 * far / Math.max(1.0, d + w.length * 0.6));
        float haze = (float) (0.35 * (1 - Math.exp(-d / 500.0)) + 0.45 * (1 - Math.exp(-d / 3000.0)));
        float glow = Level490Lightning.cloudGlow(w.x, w.z);
        haze *= 1 - 0.5f * glow;
        // Diving into the cloud sea it is lost in the cloud's grey (it is drawn over the clouds).
        haze = Math.max(haze, (float) smooth((175.0 - w.y) / 70.0));
        boolean near = d < 600;
        int rings = near ? 44 : d < 2000 ? 24 : 14;
        int around = near ? 28 : d < 2000 ? 18 : 10;
        float length = w.length;
        float r0 = length * 0.115f;
        Vector3f fwd = new Vector3f((float) (Math.cos(w.pitch) * Math.cos(w.yaw)), (float) Math.sin(w.pitch),
                (float) (Math.cos(w.pitch) * Math.sin(w.yaw)));
        Vector3f rgt = new Vector3f(fwd).cross(0, 1, 0).normalize();
        Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
        Vector3f centre = new Vector3f((float) (w.x - eye.x), (float) (w.y - eye.y), (float) (w.z - eye.z));
        float[][][] ring = new float[rings + 1][around + 1][];
        for (int k = 0; k <= rings; k++) {
            float t = k / (float) rings;
            float r = radius(t) * r0;
            float wave = wave(w, t, length);
            float along = (0.5f - t) * length;
            for (int a = 0; a <= around; a++) {
                double ang = 2 * Math.PI * a / around - Math.PI / 2; // a = 0 at the top of the back
                float cs = (float) Math.cos(ang);
                float sn = (float) -Math.sin(ang); // +1 at the top
                // Wider than tall, the head flattened; the lower jaw full under the chin.
                float flat = t < 0.28f ? 0.72f + 0.28f * t / 0.28f : 1.0f;
                float rr = r;
                if (sn < -0.3f && t > 0.04f && t < 0.5f) {
                    rr *= 1.0f - 0.03f * (float) Math.abs(Math.sin(ang * 34)); // the throat pleats
                }
                if (sn > 0.6f && t < 0.16f) {
                    rr *= 1.0f + 0.05f * (float) Hash.unit(w.id, k, a, 4997); // knobs along the head
                }
                float lx = cs * rr * 1.08f;
                float ly = sn * rr * (sn > 0 ? 0.92f * flat : 0.98f) + wave;
                Vector3f p = new Vector3f(fwd).mul(along).add(new Vector3f(rgt).mul(lx)).add(new Vector3f(upv).mul(ly)).add(centre);
                Vector3f n = new Vector3f(rgt).mul(cs).add(new Vector3f(upv).mul(sn)).normalize();
                ring[k][a] = new float[] {p.x, p.y, p.z, n.x, n.y, n.z, t, a / (float) around * BODY_V};
            }
        }
        for (int k = 0; k < rings; k++) {
            for (int a = 0; a < around; a++) {
                tri(tris, pose, ring[k][a], ring[k + 1][a], ring[k + 1][a + 1], scale, haze, glow, fog);
                tri(tris, pose, ring[k][a], ring[k + 1][a + 1], ring[k][a + 1], scale, haze, glow, fog);
            }
        }
        cap(tris, pose, ring[0], new Vector3f(fwd).mul(length * 0.5f + r0 * 0.1f).add(centre), fwd, true, scale, haze, glow, fog);
        cap(tris, pose, ring[rings], new Vector3f(fwd).mul(-length * 0.5f).add(new Vector3f(upv).mul(wave(w, 1, length))).add(centre),
                new Vector3f(fwd).negate(), false, scale, haze, glow, fog);
        // The dorsal hump, two thirds of the way back.
        float dt = 0.66f;
        Vector3f hump = new Vector3f(fwd).mul((0.5f - dt) * length).add(new Vector3f(upv).mul(radius(dt) * r0 * 0.88f + wave(w, dt, length))).add(centre);
        blade(tris, pose, hump, new Vector3f(upv).add(new Vector3f(fwd).mul(-0.9f)).normalize(), fwd, length * 0.05f, length * 0.035f,
                0.0f, near ? 6 : 3, scale, haze, glow, fog);
        // The long pectoral fins, slowly sweeping, a scalloped leading edge.
        float flap = (float) Math.sin(w.swim * 2 * Math.PI + 1.0) * 0.25f;
        for (int side = -1; side <= 1; side += 2) {
            float ft = 0.27f;
            Vector3f root = new Vector3f(fwd).mul((0.5f - ft) * length).add(new Vector3f(rgt).mul(side * radius(ft) * r0 * 0.95f))
                    .add(new Vector3f(upv).mul(-radius(ft) * r0 * 0.35f + wave(w, ft, length))).add(centre);
            Vector3f out = new Vector3f(rgt).mul(side).add(new Vector3f(upv).mul(-0.5f + flap)).add(new Vector3f(fwd).mul(-0.35f)).normalize();
            blade(tris, pose, root, out, fwd, length * 0.3f, length * 0.085f, 0.35f, near ? 14 : 5, scale, haze, glow, fog);
        }
        // The flukes: wide, swept back, a notch in the middle.
        Vector3f tail = new Vector3f(fwd).mul(-length * 0.5f + r0 * 0.1f).add(new Vector3f(upv).mul(wave(w, 1, length))).add(centre);
        float tilt = (float) Math.cos(2 * Math.PI * 0.9 - w.swim * 2 * Math.PI) * 0.35f;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f out = new Vector3f(rgt).mul(side).add(new Vector3f(fwd).mul(-0.45f)).add(new Vector3f(upv).mul(tilt)).normalize();
            blade(tris, pose, tail, out, new Vector3f(fwd).negate(), length * 0.2f, length * 0.075f, 0.2f, near ? 10 : 4, scale, haze, glow, fog);
        }
        // Dark eyes low on the sides of the head, under a ridge.
        for (int side = -1; side <= 1; side += 2) {
            float et = 0.14f;
            Vector3f c = new Vector3f(fwd).mul((0.5f - et) * length).add(new Vector3f(rgt).mul(side * radius(et) * r0 * 1.0f))
                    .add(new Vector3f(upv).mul(-radius(et) * r0 * 0.12f)).add(centre);
            eye(tris, pose, c, new Vector3f(rgt).mul(side), fwd, r0 * 0.07f, scale, haze, fog);
        }
        // Fog pouring off the skin and trailing behind: puffs drifting tailward, swelling and thinning.
        int count = w.great ? 70 : near ? 18 : 10;
        for (int k = 0; k < count; k++) {
            double f = ((k * 0.618 + seconds * (w.great ? 0.02 : 0.035)) % 1.0);
            float t = (float) f;
            float ang = (float) (k * 2.39996);
            float r = radius(Math.min(1, t)) * r0 * (1.0f + 0.6f * t);
            float along = (0.5f - t * 1.25f) * length;
            Vector3f p = new Vector3f(fwd).mul(along).add(new Vector3f(rgt).mul((float) Math.cos(ang) * r))
                    .add(new Vector3f(upv).mul((float) Math.sin(ang) * r)).add(centre).mul(scale);
            float alpha = (float) ((w.great ? 0.22 : near ? 0.07 : 0.16) * Math.sin(Math.PI * f)) * (1 - haze * 0.6f);
            float size = (r0 * (0.6f + 1.4f * t)) * scale;
            float c = 0.62f * (1 - haze) + fog[0] * haze + 0.4f * glow;
            puffs.add(new float[] {p.x, p.y, p.z, size, c, alpha, p.lengthSquared()});
        }
    }

    /** The swimming wave's height at t along the body (0 nose .. 1 tail): stronger toward the tail. */
    private static float wave(Whale w, float t, float length) {
        return (float) (0.06 * length * Math.sin(2 * Math.PI * (t * 0.9) - w.swim * 2 * Math.PI) * t * t);
    }

    /** The body's radius along its length (0 nose, 1 tail), as a fraction of its widest: humpback-like. */
    private static float radius(float t) {
        if (t < 0.12f) {
            return (float) (0.45 + 0.55 * Math.sqrt(t / 0.12));
        }
        if (t < 0.4f) {
            return 1.0f - 0.04f * (t - 0.12f) / 0.28f;
        }
        double f = (t - 0.4) / 0.6;
        return (float) (0.96 * Math.pow(1 - f, 1.25) + 0.045);
    }

    private static void tri(BufferBuilder tris, Matrix4f pose, float[] a, float[] b, float[] c, float scale, float haze, float glow,
                            float[] fog) {
        vertex(tris, pose, a, scale, haze, glow, fog);
        vertex(tris, pose, b, scale, haze, glow, fog);
        vertex(tris, pose, c, scale, haze, glow, fog);
    }

    /** v = {x, y, z, nx, ny, nz, texture u, texture v}: the texture times the moonlight, into the haze. */
    private static void vertex(BufferBuilder tris, Matrix4f pose, float[] v, float scale, float haze, float glow, float[] fog) {
        float light = 0.16f + 0.72f * Math.max(0.0f, v[3] * MOON.x + v[4] * MOON.y + v[5] * MOON.z) + 0.12f * Math.max(0.0f, v[4])
                + 0.7f * glow;
        // The texture is multiplied by this colour; the haze can't be added that way, so it is
        // approximated by fading the colour toward the fog's brightness over the skin's mid grey.
        float k = light * (1 - haze);
        float add = haze * 1.4f;
        tris.addVertex(pose, v[0] * scale, v[1] * scale, v[2] * scale).setUv(v[6], v[7])
                .setColor(Math.min(1.5f, k + fog[0] * add), Math.min(1.5f, k + fog[1] * add), Math.min(1.5f, k + fog[2] * add * 1.05f), 1.0f);
    }

    /** Closes the end ring onto a point (the nose or the tail). */
    private static void cap(BufferBuilder tris, Matrix4f pose, float[][] ring, Vector3f tip, Vector3f out, boolean front, float scale,
                            float haze, float glow, float[] fog) {
        float[] p = {tip.x, tip.y, tip.z, out.x, out.y, out.z, front ? 0.0f : 1.0f, BODY_V * 0.5f};
        for (int a = 0; a + 1 < ring.length; a++) {
            if (front) {
                tri(tris, pose, p, ring[a], ring[a + 1], scale, haze, glow, fog);
            } else {
                tri(tris, pose, p, ring[a + 1], ring[a], scale, haze, glow, fog);
            }
        }
    }

    /**
     * A fin or a fluke: a tapering blade with some thickness from root along {@code out}, its
     * width across {@code across} (the leading edge on the + side), bumps along that edge
     * ({@code scallop}), {@code segs} segments along; both faces drawn.
     */
    private static void blade(BufferBuilder tris, Matrix4f pose, Vector3f root, Vector3f out, Vector3f across, float len, float width,
                              float scallop, int segs, float scale, float haze, float glow, float[] fog) {
        Vector3f n = new Vector3f(out).cross(across).normalize();
        Vector3f ac = new Vector3f(n).cross(out).normalize();
        if (ac.dot(across) < 0) {
            ac.negate();
        }
        float[][] lead = new float[segs + 1][];
        float[][] trail = new float[segs + 1][];
        float[][] topMid = new float[segs + 1][];
        float[][] botMid = new float[segs + 1][];
        for (int k = 0; k <= segs; k++) {
            float t = k / (float) segs;
            // Broad at the root, rounded to a tip, curving back a little.
            float wdt = width * (float) Math.sqrt(Math.max(0, 1 - t * t)) * (1 - 0.35f * t);
            float bumps = scallop > 0 ? 1 + scallop * 0.25f * (float) Math.abs(Math.sin(t * Math.PI * segs * 0.5)) : 1;
            float thick = width * 0.12f * (1 - t);
            Vector3f c = new Vector3f(out).mul(t * len).add(new Vector3f(ac).mul(-t * t * width * 0.8f)).add(root);
            Vector3f l = new Vector3f(ac).mul(wdt * 0.45f * bumps).add(c);
            Vector3f tr = new Vector3f(ac).mul(-wdt * 0.55f).add(c);
            Vector3f mu = new Vector3f(n).mul(thick).add(c);
            Vector3f md = new Vector3f(n).mul(-thick).add(c);
            float u = 0.05f + 0.9f * t;
            lead[k] = new float[] {l.x, l.y, l.z, ac.x, ac.y, ac.z, u, BODY_V + 0.02f};
            trail[k] = new float[] {tr.x, tr.y, tr.z, -ac.x, -ac.y, -ac.z, u, 0.99f};
            topMid[k] = new float[] {mu.x, mu.y, mu.z, n.x, n.y, n.z, u, BODY_V + 0.12f};
            botMid[k] = new float[] {md.x, md.y, md.z, -n.x, -n.y, -n.z, u, BODY_V + 0.12f};
        }
        for (int k = 0; k < segs; k++) {
            // Upper face: lead -> mid -> trail; lower face reversed.
            quadTri(tris, pose, lead[k], lead[k + 1], topMid[k + 1], topMid[k], scale, haze, glow, fog);
            quadTri(tris, pose, topMid[k], topMid[k + 1], trail[k + 1], trail[k], scale, haze, glow, fog);
            quadTri(tris, pose, botMid[k], botMid[k + 1], lead[k + 1], lead[k], scale, haze, glow, fog);
            quadTri(tris, pose, trail[k], trail[k + 1], botMid[k + 1], botMid[k], scale, haze, glow, fog);
        }
    }

    private static void quadTri(BufferBuilder tris, Matrix4f pose, float[] a, float[] b, float[] c, float[] d, float scale, float haze,
                                float glow, float[] fog) {
        tri(tris, pose, a, b, c, scale, haze, glow, fog);
        tri(tris, pose, a, c, d, scale, haze, glow, fog);
    }

    /** A dark eye under a ridge: a small lens just off the skin. */
    private static void eye(BufferBuilder tris, Matrix4f pose, Vector3f c, Vector3f out, Vector3f fwd, float r, float scale, float haze,
                            float[] fog) {
        Vector3f up = new Vector3f(out).cross(fwd).normalize();
        Vector3f p = new Vector3f(out).mul(r * 0.4f).add(c);
        int segs = 10;
        float[] centre = {p.x, p.y, p.z, out.x, out.y, out.z, 0.02f, 0.5f * BODY_V};
        for (int k = 0; k < segs; k++) {
            double a0 = 2 * Math.PI * k / segs;
            double a1 = 2 * Math.PI * (k + 1) / segs;
            Vector3f v0 = new Vector3f(fwd).mul((float) Math.cos(a0) * r).add(new Vector3f(up).mul((float) Math.sin(a0) * r * 0.6f)).add(c);
            Vector3f v1 = new Vector3f(fwd).mul((float) Math.cos(a1) * r).add(new Vector3f(up).mul((float) Math.sin(a1) * r * 0.6f)).add(c);
            float[] e0 = {v0.x, v0.y, v0.z, out.x, out.y, out.z, 0.02f, 0.5f * BODY_V};
            float[] e1 = {v1.x, v1.y, v1.z, out.x, out.y, out.z, 0.02f, 0.5f * BODY_V};
            // Nearly black: the texture times a very dark colour.
            dark(tris, pose, centre, e0, e1, scale);
            dark(tris, pose, centre, e1, e0, scale);
        }
    }

    private static void dark(BufferBuilder tris, Matrix4f pose, float[] a, float[] b, float[] c, float scale) {
        for (float[] v : new float[][] {a, b, c}) {
            tris.addVertex(pose, v[0] * scale, v[1] * scale, v[2] * scale).setUv(v[6], v[7]).setColor(0.05f, 0.045f, 0.05f, 1.0f);
        }
    }
}
