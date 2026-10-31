package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level490Layout;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Level 490's hung ruins (stage 3, after Crumbling Farum Azula's floating fortresses): you are not
 * the only thing hung here. Kilometres off in the haze, chunks of rock carrying ruins hang from
 * colossal chains going up into the storm: a tiered domed rotunda among dark firs, a broken
 * fortress, a broken tower, a span of a great bridge. Debris drifts round them. They are only ever
 * sky (never blocks): dark shapes in the haze, their moon side lit, lit up by lightning near them.
 *
 * <p>Now and then one's chains snap and it falls into the cloud sea, tilting as it goes; you hear
 * the chains break, and later the fall, as late as sound takes to come that far
 * (Level490ChainEvents#hear). Each ruin's life is worked out from the layout seed and the game
 * clock alone, so everyone sees the same ruin fall at the same moment, nothing needs sending, and a
 * fallen ruin stays gone; much later another hangs where it was, emerging from the haze.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490SkyHung {
    /** One ruin (or none) per cell this big. */
    private static final int CELL = 1500;
    /** Nearer than this the haze hides them; they come clear by CLEAR, and fade out past FAR. */
    private static final double NEAR = 500.0;
    private static final double CLEAR = 900.0;
    private static final double FAR = 7000.0;
    /** Haze thickens over this many blocks (e-folding). */
    private static final double HAZE = 2600.0;
    private static final double HOUR = 72000.0;
    /** A fall lasts this long (ticks); a new ruin emerges from the haze over APPEAR. */
    private static final double FALL = 900.0;
    private static final double APPEAR = 6000.0;
    /** Seconds a fall takes to reach well below the top of the cloud sea. */
    private static final double FALL_SECONDS = 26.0;
    private static final float TOP = 640.0f;
    private static final Vector3f MOON = new Vector3f((float) (Math.cos(Math.toRadians(24)) * Math.cos(Math.toRadians(35))),
            (float) Math.sin(Math.toRadians(24)), (float) (Math.cos(Math.toRadians(24)) * Math.sin(Math.toRadians(35))));

    // Farum Azula's pale old stone, darker domes, dark weathered rock, near-black firs, a few red trees.
    private static final float[] STONE = {0.62f, 0.60f, 0.56f};
    private static final float[] DOME = {0.48f, 0.50f, 0.52f};
    private static final float[] ROCK = {0.33f, 0.32f, 0.31f};
    private static final float[] FIR = {0.11f, 0.12f, 0.10f};
    private static final float[] RED = {0.36f, 0.09f, 0.07f};
    /** Window openings: dark, read as rows of arches at a distance. */
    private static final float[] OPENING = {0.07f, 0.07f, 0.08f};

    /** One convex piece: triangles (9 floats each) and their normals (3 each), in the ruin's frame. */
    private record Part(float[] tris, float[] normals, float[] colour, float cx, float cy, float cz) {}

    /** A drifting chunk of debris round a ruin. */
    private record Debris(float orbit, float angle, float y, float size, float speed) {}

    private record Shape(List<Part> parts, float[][] anchors, List<Debris> debris, float radius, Vector3f tiltAxis, float tilt) {}

    /** Where cell (i, j)'s ruin is in its life at game time t (ticks), or null if the cell is empty now. */
    record Life(long id, double x, double z, double y, double age, double falls) {
        boolean falling() {
            return age >= falls;
        }

        double fallSeconds() {
            return (age - falls) / 20.0;
        }
    }

    private static final Map<Long, Shape> SHAPES = new HashMap<>();
    private static final Set<Long> HEARD = new HashSet<>();

    private Level490SkyHung() {}

    /** Cell (i, j)'s ruin at game time t, from the layout seed and the clock alone. */
    static Life life(long seed, int i, int j, double t) {
        long h = Hash.of(seed, i, j, 4980);
        if (Hash.unit(h) > 0.55) {
            return null;
        }
        // Each ruin hangs for hours, then falls; some time later another hangs here.
        double period = HOUR * (4 + 6 * Hash.unit(Hash.mix(h)));
        double gap = 1200 * (20 + 30 * Hash.unit(Hash.mix(h + 1)));
        double phase = t + Hash.unit(Hash.mix(h + 2)) * period;
        long gen = (long) Math.floor(phase / period);
        double age = phase - gen * period;
        double falls = period - gap;
        if (age > falls + FALL) {
            return null;
        }
        long g = Hash.of(h, (int) gen, (int) (gen >>> 32), 4981);
        double x = (i + 0.15 + 0.7 * Hash.unit(g)) * CELL;
        double z = (j + 0.15 + 0.7 * Hash.unit(Hash.mix(g))) * CELL;
        // Never over a spire's island: that is where you are going.
        int[] site = Level490Layout.siteCentre(seed, (int) x, (int) z);
        if (Math.hypot(x - site[0], z - site[1]) < 450) {
            return null;
        }
        return new Life(g, x, z, 170 + 260 * Hash.unit(Hash.mix(g + 1)), age, falls);
    }

    private static double now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    static void render(Matrix4f view, Vec3 eye, float far) {
        Minecraft mc = Minecraft.getInstance();
        if (!Level490ClientState.hasSeed() || mc.level == null) {
            return;
        }
        long seed = Level490ClientState.seed();
        double t = now();
        List<Life> lives = new ArrayList<>();
        int ci = Math.floorDiv((int) eye.x, CELL);
        int cj = Math.floorDiv((int) eye.z, CELL);
        int reach = (int) Math.ceil(FAR / CELL) + 1;
        for (int i = ci - reach; i <= ci + reach; i++) {
            for (int j = cj - reach; j <= cj + reach; j++) {
                Life life = life(seed, i, j, t);
                if (life != null) {
                    double d = Math.hypot(life.x - eye.x, life.z - eye.z);
                    if (d > NEAR && d < FAR) {
                        lives.add(life);
                    }
                }
            }
        }
        if (lives.isEmpty()) {
            return;
        }
        // Farthest first: no depth buffer up here, so nearer ruins simply paint over farther ones.
        lives.sort((a, b) -> Double.compare(Math.hypot(b.x - eye.x, b.z - eye.z), Math.hypot(a.x - eye.x, a.z - eye.z)));
        float[] fog = Level490Atmosphere.fogColour(new float[3]);
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        for (Life life : lives) {
            Shape shape = shape(life.id);
            double d = Math.hypot(life.x - eye.x, life.z - eye.z);
            float vis = (float) (smooth((d - NEAR) / (CLEAR - NEAR)) * (1.0 - smooth((d - FAR * 0.8) / (FAR * 0.2)))
                    * Math.min(1.0, life.age / APPEAR));
            if (vis < 0.01f) {
                continue;
            }
            float glow = Level490Lightning.cloudGlow(life.x, life.z);
            float haze = (float) (1.0 - Math.exp(-d / HAZE)) * (1.0f - 0.6f * glow);
            float scale = (float) Math.min(1.0, 0.9 * far / (d + 600.0));
            // Falling: it drops faster and faster into the cloud sea, tilting over.
            double fs = life.falling() ? life.fallSeconds() : 0.0;
            double g = 2.0 * (life.y - 40.0) / (FALL_SECONDS * FALL_SECONDS);
            float dy = (float) (-0.5 * g * fs * fs);
            Quaternionf tilt = new Quaternionf().fromAxisAngleRad(shape.tiltAxis, (float) (shape.tilt * (1.0 - Math.exp(-fs / 6.0))));
            RenderSystem.enableCull();
            BufferBuilder tris = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            List<Part> parts = new ArrayList<>(shape.parts);
            for (Debris b : shape.debris) {
                float a = (float) (b.angle + b.speed * seconds);
                float orbit = (float) (b.orbit * (1.0 + 0.05 * fs));
                parts.add(box(orbit * (float) Math.cos(a), b.y, orbit * (float) Math.sin(a), b.size, b.size, b.size, a, ROCK));
            }
            Vector3f origin = new Vector3f((float) (life.x - eye.x), (float) (life.y - eye.y) + dy, (float) (life.z - eye.z));
            parts.sort((p, q) -> Float.compare(dist(q, tilt, origin), dist(p, tilt, origin)));
            Vector3f v = new Vector3f();
            Vector3f n = new Vector3f();
            for (Part p : parts) {
                for (int k = 0; k < p.normals.length / 3; k++) {
                    tilt.transform(n.set(p.normals[k * 3], p.normals[k * 3 + 1], p.normals[k * 3 + 2]));
                    float shade = 0.30f + 0.55f * Math.max(0.0f, n.dot(MOON)) + 0.8f * glow;
                    float r = p.colour[0] * shade * (1 - haze) + fog[0] * haze;
                    float gg = p.colour[1] * shade * (1 - haze) + fog[1] * haze;
                    float bl = p.colour[2] * shade * (1 - haze) + fog[2] * haze;
                    for (int c = 0; c < 3; c++) {
                        int o = k * 9 + c * 3;
                        tilt.transform(v.set(p.tris[o], p.tris[o + 1], p.tris[o + 2])).add(origin).mul(scale);
                        tris.addVertex(view, v.x, v.y, v.z).setColor(r, gg, bl, vis);
                    }
                }
            }
            BufferUploader.drawWithShader(tris.buildOrThrow());
            RenderSystem.disableCull();
            // Its chains, up into the storm; snapped, they whip up and are gone.
            BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int[] count = {0};
            for (float[] a : shape.anchors) {
                double ax = life.x + a[0];
                double az = life.z + a[2];
                float bottom = (float) (life.y + a[1]);
                float alpha = vis * (1 - haze * 0.7f);
                if (life.falling()) {
                    bottom += (float) (25 + 50 * (1 - Math.exp(-fs * 1.5)));
                    alpha *= (float) Math.max(0.0, 1.0 - fs / 5.0);
                }
                if (alpha > 0.02f) {
                    Level490SkyChains.chain(quads, view, eye, far, ax, az, 0.55f, (float) Math.abs((a[0] * 13 + a[2] * 7) % 22), bottom, TOP, alpha, d,
                            0.06f, count);
                }
            }
            if (count[0] > 0) {
                BufferUploader.drawWithShader(quads.buildOrThrow());
            } else {
                quads.build();
            }
        }
        RenderSystem.disableBlend();
    }

    private static float dist(Part p, Quaternionf tilt, Vector3f origin) {
        return tilt.transform(new Vector3f(p.cx, p.cy, p.cz)).add(origin).lengthSquared();
    }

    private static double smooth(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return c * c * (3 - 2 * c);
    }

    // ==========================================================================================
    // Falls: the sounds
    // ==========================================================================================

    /** A ruin that has just started falling: its chains' snap, then its fall, each as late as sound takes. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !Level490Atmosphere.inLevel490() || !Level490ClientState.hasSeed()) {
            return;
        }
        long seed = Level490ClientState.seed();
        double t = mc.level.getGameTime();
        int ci = Math.floorDiv(mc.player.getBlockX(), CELL);
        int cj = Math.floorDiv(mc.player.getBlockZ(), CELL);
        int reach = (int) Math.ceil(FAR / CELL) + 1;
        for (int i = ci - reach; i <= ci + reach; i++) {
            for (int j = cj - reach; j <= cj + reach; j++) {
                Life life = life(seed, i, j, t);
                if (life == null || !life.falling() || life.fallSeconds() > 3.0 || !HEARD.add(life.id)) {
                    continue;
                }
                double d = Math.hypot(life.x - mc.player.getX(), life.z - mc.player.getZ());
                if (d > 5000) {
                    continue;
                }
                float volume = (float) Math.max(0.3, Math.min(1.0, 1.3 - d / 4000.0));
                Level490ChainEvents.hear(ModSounds.LEVEL490_CHAIN_SNAP.get(), life.x, life.z, volume, 0.9f, 0);
                // It reaches the top of the cloud sea (y 148) this long after the snap.
                double g = 2.0 * (life.y - 40.0) / (FALL_SECONDS * FALL_SECONDS);
                double hit = Math.sqrt(2.0 * Math.max(0.0, life.y - 148.0) / g) - life.fallSeconds();
                Level490ChainEvents.hear(ModSounds.LEVEL490_COLLAPSE.get(), life.x, life.z, volume, 0.9f, (long) (hit * 1000));
            }
        }
        if (HEARD.size() > 512) {
            HEARD.clear();
        }
    }

    // ==========================================================================================
    // The shapes (after the references: Farum Azula's rotunda, fortress, tower and great bridge)
    // ==========================================================================================

    private static Shape shape(long id) {
        Shape s = SHAPES.get(id);
        if (s == null) {
            if (SHAPES.size() > 256) {
                SHAPES.clear();
            }
            s = build(id);
            SHAPES.put(id, s);
        }
        return s;
    }

    private static Shape build(long id) {
        Random rnd = new Random(id);
        float radius = 90 + 110 * rnd.nextFloat();
        List<Part> parts = new ArrayList<>();
        List<float[]> anchors = new ArrayList<>();
        int kind = rnd.nextInt(4);
        float rr = radius;
        switch (kind) {
            case 0 -> { // A tiered rotunda under a dome on a terrace, firs round it (reference: the domed temple)
                rock(parts, rr, rr * (1.4f + rnd.nextFloat()), rnd);
                parts.add(frustum(0, 0, 0, rr * 0.82f, rr * 0.82f, rr * 0.07f, 16, STONE));
                float y = rr * 0.07f;
                float[] tierR = {0.55f, 0.43f, 0.31f};
                float[] tierH = {0.36f, 0.28f, 0.20f};
                for (int k = 0; k < 3; k++) {
                    parts.add(frustum(0, y, 0, rr * tierR[k], rr * tierR[k] * 0.97f, rr * tierH[k], 16, STONE));
                    arcade(parts, y, rr * tierR[k], rr * tierH[k], 16 - 3 * k);
                    y += rr * tierH[k];
                    parts.add(frustum(0, y, 0, rr * (tierR[k] + 0.03f), rr * (tierR[k] + 0.03f), rr * 0.025f, 16, STONE)); // a cornice
                    y += rr * 0.025f;
                }
                parts.add(dome(0, y, 0, rr * 0.30f, DOME));
                parts.add(frustum(0, y + rr * 0.28f, 0, rr * 0.06f, rr * 0.06f, rr * 0.12f, 8, STONE));
                parts.add(frustum(0, y + rr * 0.40f, 0, rr * 0.07f, 0, rr * 0.10f, 8, DOME));
                for (int k = 0; k < 2; k++) { // turrets on the first tier
                    double a = rnd.nextDouble() * Math.PI * 2;
                    float tx = (float) (Math.cos(a) * rr * 0.52f);
                    float tz = (float) (Math.sin(a) * rr * 0.52f);
                    parts.add(frustum(tx, rr * 0.07f, tz, rr * 0.08f, rr * 0.08f, rr * 0.55f, 10, STONE));
                    parts.add(dome(tx, rr * 0.62f, tz, rr * 0.08f, DOME));
                }
                firs(parts, rr, 5 + rnd.nextInt(4), rnd);
                ring(anchors, rr * 0.72f, 3, rnd);
            }
            case 1 -> { // A broken fortress: walls round the rim, a keep, round towers (the torn-apart fortress)
                rock(parts, rr, rr * (1.6f + rnd.nextFloat()), rnd);
                int walls = 7;
                for (int k = 0; k < walls; k++) {
                    if (rnd.nextFloat() < 0.2f) {
                        continue; // fallen away
                    }
                    double a0 = Math.PI * 2 * k / walls;
                    double a1 = Math.PI * 2 * (k + 1) / walls;
                    float x0 = (float) Math.cos(a0) * rr * 0.72f;
                    float z0 = (float) Math.sin(a0) * rr * 0.72f;
                    float x1 = (float) Math.cos(a1) * rr * 0.72f;
                    float z1 = (float) Math.sin(a1) * rr * 0.72f;
                    float len = (float) Math.hypot(x1 - x0, z1 - z0);
                    float h = rr * (0.22f + 0.4f * rnd.nextFloat());
                    float yaw = (float) Math.atan2(z1 - z0, x1 - x0);
                    parts.add(box((x0 + x1) / 2, h / 2, (z0 + z1) / 2, len / 2, h / 2, rr * 0.04f, yaw, STONE));
                    // Slit windows along it and a broken row of battlements on top.
                    float ox = (float) Math.sin(yaw) * rr * 0.045f;
                    float oz = (float) -Math.cos(yaw) * rr * 0.045f;
                    int slots = Math.max(2, (int) (len / (rr * 0.12f)));
                    for (int w = 0; w < slots; w++) {
                        float f = (w + 0.5f) / slots;
                        float wx = x0 + (x1 - x0) * f;
                        float wz = z0 + (z1 - z0) * f;
                        parts.add(box(wx - ox, h * 0.6f, wz - oz, rr * 0.018f, h * 0.15f, rr * 0.006f, yaw, OPENING));
                        parts.add(box(wx + ox, h * 0.6f, wz + oz, rr * 0.018f, h * 0.15f, rr * 0.006f, yaw, OPENING));
                        if (rnd.nextFloat() < 0.7f) {
                            parts.add(box(wx, h + rr * 0.025f, wz, rr * 0.025f, rr * 0.025f, rr * 0.045f, yaw, STONE));
                        }
                    }
                }
                float kh = rr * (0.6f + 0.4f * rnd.nextFloat());
                parts.add(box(rr * 0.1f, kh / 2, -rr * 0.05f, rr * 0.2f, kh / 2, rr * 0.18f, rnd.nextFloat(), STONE));
                for (int k = 0; k < 2; k++) {
                    double a = Math.PI * (k + rnd.nextDouble() * 0.6);
                    float tx = (float) Math.cos(a) * rr * 0.72f;
                    float tz = (float) Math.sin(a) * rr * 0.72f;
                    float th = rr * (0.6f + 0.5f * rnd.nextFloat());
                    parts.add(frustum(tx, 0, tz, rr * 0.13f, rr * 0.11f, th, 12, STONE));
                    if (rnd.nextBoolean()) {
                        parts.add(dome(tx, th, tz, rr * 0.11f, DOME));
                    }
                }
                // A piece of wall torn loose, leaning out over the edge.
                parts.add(box(-rr * 0.85f, rr * 0.1f, rr * 0.2f, rr * 0.18f, rr * 0.22f, rr * 0.04f, 0.9f, STONE));
                ring(anchors, rr * 0.8f, 4, rnd);
            }
            case 2 -> { // A tall broken tower on a long hanging rock (the ruin in the whirlwind)
                rock(parts, rr * 0.7f, rr * (2.4f + rnd.nextFloat()), rnd);
                float y = 0;
                float tr = rr * 0.2f;
                for (int k = 0; k < 3; k++) {
                    float h = rr * (0.45f + 0.15f * rnd.nextFloat());
                    parts.add(frustum(0, y, 0, tr, tr * 0.95f, h, 12, STONE));
                    arcade(parts, y, tr, h, 8);
                    y += h;
                    parts.add(frustum(0, y, 0, tr * 1.12f, tr * 1.12f, rr * 0.03f, 12, STONE));
                    y += rr * 0.03f;
                    tr *= 0.85f;
                }
                // Broken off: a jagged stump of its top tier, leaning.
                parts.add(box(tr * 0.3f, y + rr * 0.15f, 0, tr * 0.5f, rr * 0.15f, tr * 0.6f, 0.4f, STONE));
                parts.add(box(rr * 0.35f, rr * 0.1f, rr * 0.2f, rr * 0.12f, rr * 0.1f, rr * 0.1f, 0.2f, STONE));
                firs(parts, rr * 0.7f, 2, rnd);
                ring(anchors, rr * 0.55f, 2, rnd);
            }
            default -> { // A span of a great bridge, piers hanging under it, both ends broken off
                float len = rr * 1.6f;
                parts.add(box(0, 0, 0, len, rr * 0.08f, rr * 0.14f, 0, STONE));
                for (int k = -1; k <= 1; k++) {
                    float ph = rr * (0.5f + 0.4f * rnd.nextFloat());
                    parts.add(box(k * len * 0.6f, -ph / 2 - rr * 0.08f, 0, rr * 0.09f, ph / 2, rr * 0.11f, 0, STONE));
                    parts.add(box(k * len * 0.6f, -ph - rr * 0.15f, 0, rr * 0.14f, rr * 0.1f, rr * 0.14f, rnd.nextFloat(), ROCK));
                }
                parts.add(box(len * 0.2f, rr * 0.13f, rr * 0.12f, len * 0.6f, rr * 0.05f, rr * 0.015f, 0, STONE)); // a parapet
                anchors.add(new float[] {-len * 0.9f, 0, 0});
                anchors.add(new float[] {len * 0.9f, 0, 0});
            }
        }
        List<Debris> debris = new ArrayList<>();
        int n = 18 + rnd.nextInt(14);
        for (int k = 0; k < n; k++) {
            debris.add(new Debris(rr * (1.2f + 1.4f * rnd.nextFloat()), (float) (rnd.nextDouble() * Math.PI * 2),
                    rr * (-0.6f + 1.6f * rnd.nextFloat()), rr * (0.015f + 0.045f * rnd.nextFloat()),
                    (0.01f + 0.03f * rnd.nextFloat()) * (rnd.nextBoolean() ? 1 : -1)));
        }
        double ta = rnd.nextDouble() * Math.PI * 2;
        return new Shape(parts, anchors.toArray(new float[0][]), debris, rr,
                new Vector3f((float) Math.cos(ta), 0, (float) Math.sin(ta)), (float) Math.toRadians(18 + 20 * rnd.nextFloat()));
    }

    /**
     * Round a cylinder of radius r from y to y + h: {@code count} tall dark window openings, with a
     * buttress between each pair (the tiers of arched windows in the references).
     */
    private static void arcade(List<Part> parts, float y, float r, float h, int count) {
        for (int k = 0; k < count; k++) {
            double a = Math.PI * 2 * (k + 0.5) / count;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            float yaw = (float) (a + Math.PI / 2);
            float w = (float) (Math.PI * r / count) * 0.32f;
            parts.add(box(c * (r + 0.4f), y + h * 0.5f, s * (r + 0.4f), w, h * 0.28f, 0.4f, yaw, OPENING));
            double b = Math.PI * 2 * k / count;
            float bc = (float) Math.cos(b);
            float bs = (float) Math.sin(b);
            parts.add(box(bc * (r + r * 0.03f), y + h * 0.5f, bs * (r + r * 0.03f), r * 0.035f, h * 0.5f, r * 0.04f,
                    (float) (b + Math.PI / 2), STONE));
        }
    }

    /** Chain anchors: {@code count} points round the rim at radius r. */
    private static void ring(List<float[]> anchors, float r, int count, Random rnd) {
        double a0 = rnd.nextDouble() * Math.PI * 2;
        for (int k = 0; k < count; k++) {
            double a = a0 + Math.PI * 2 * k / count;
            anchors.add(new float[] {(float) Math.cos(a) * r, 0, (float) Math.sin(a) * r});
        }
    }

    /** Dark firs round the rim (some ruins have a red tree, as in the references). */
    private static void firs(List<Part> parts, float r, int count, Random rnd) {
        for (int k = 0; k < count; k++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float d = r * (0.6f + 0.25f * rnd.nextFloat());
            float h = r * (0.18f + 0.15f * rnd.nextFloat());
            parts.add(frustum((float) Math.cos(a) * d, 0, (float) Math.sin(a) * d, h * 0.28f, 0, h, 7,
                    rnd.nextFloat() < 0.15f ? RED : FIR));
        }
    }

    /** The rock everything stands on: a jagged flat top tapering to a ragged point far below. */
    private static void rock(List<Part> parts, float r, float depth, Random rnd) {
        int sides = 11;
        float[][] rim = new float[sides][];
        float[][] mid = new float[sides][];
        float[][] low = new float[sides][];
        for (int k = 0; k < sides; k++) {
            double a = Math.PI * 2 * k / sides + rnd.nextDouble() * 0.2;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            float rr = r * (0.88f + 0.22f * rnd.nextFloat());
            rim[k] = new float[] {c * rr, -r * 0.04f * rnd.nextFloat(), s * rr};
            float mr = r * (0.62f + 0.2f * rnd.nextFloat());
            mid[k] = new float[] {c * mr, -depth * (0.3f + 0.1f * rnd.nextFloat()), s * mr};
            float lr = r * (0.25f + 0.15f * rnd.nextFloat());
            low[k] = new float[] {c * lr, -depth * (0.65f + 0.1f * rnd.nextFloat()), s * lr};
        }
        float[] tip = {r * 0.1f * (rnd.nextFloat() - 0.5f), -depth, r * 0.1f * (rnd.nextFloat() - 0.5f)};
        Builder b = new Builder(0, -depth * 0.3f, 0, ROCK);
        float[] top = {0, 0, 0};
        for (int k = 0; k < sides; k++) {
            int k1 = (k + 1) % sides;
            b.tri(top, rim[k], rim[k1]);
            b.quad(rim[k], rim[k1], mid[k1], mid[k]);
            b.quad(mid[k], mid[k1], low[k1], low[k]);
            b.tri(low[k], low[k1], tip);
        }
        parts.add(b.part());
    }

    /** A cylinder or cone frustum from y to y + h, radius r0 at the bottom and r1 at the top. */
    private static Part frustum(float cx, float y, float cz, float r0, float r1, float h, int sides, float[] colour) {
        Builder b = new Builder(cx, y + h * 0.5f, cz, colour);
        float[] apex = {cx, y + h, cz};
        float[] base = {cx, y, cz};
        for (int k = 0; k < sides; k++) {
            double a0 = Math.PI * 2 * k / sides;
            double a1 = Math.PI * 2 * (k + 1) / sides;
            float[] p0 = {cx + r0 * (float) Math.cos(a0), y, cz + r0 * (float) Math.sin(a0)};
            float[] p1 = {cx + r0 * (float) Math.cos(a1), y, cz + r0 * (float) Math.sin(a1)};
            b.tri(base, p0, p1);
            if (r1 <= 0) {
                b.tri(p0, p1, apex);
            } else {
                float[] q0 = {cx + r1 * (float) Math.cos(a0), y + h, cz + r1 * (float) Math.sin(a0)};
                float[] q1 = {cx + r1 * (float) Math.cos(a1), y + h, cz + r1 * (float) Math.sin(a1)};
                b.quad(p0, p1, q1, q0);
                b.tri(apex, q0, q1);
            }
        }
        return b.part();
    }

    /** A dome: half a sphere of radius r on y. */
    private static Part dome(float cx, float y, float cz, float r, float[] colour) {
        Builder b = new Builder(cx, y + r * 0.3f, cz, colour);
        int sides = 14;
        int rings = 4;
        float[] base = {cx, y, cz};
        for (int k = 0; k < sides; k++) {
            double a0 = Math.PI * 2 * k / sides;
            double a1 = Math.PI * 2 * (k + 1) / sides;
            for (int ring = 0; ring < rings; ring++) {
                double e0 = Math.PI / 2 * ring / rings;
                double e1 = Math.PI / 2 * (ring + 1) / rings;
                float[] p00 = sphere(cx, y, cz, r, a0, e0);
                float[] p10 = sphere(cx, y, cz, r, a1, e0);
                float[] p01 = sphere(cx, y, cz, r, a0, e1);
                float[] p11 = sphere(cx, y, cz, r, a1, e1);
                if (ring == rings - 1) {
                    b.tri(p00, p10, p01);
                } else {
                    b.quad(p00, p10, p11, p01);
                }
            }
            b.tri(base, sphere(cx, y, cz, r, a0, 0), sphere(cx, y, cz, r, a1, 0));
        }
        return b.part();
    }

    private static float[] sphere(float cx, float y, float cz, float r, double a, double e) {
        return new float[] {cx + r * (float) (Math.cos(e) * Math.cos(a)), y + r * (float) Math.sin(e), cz + r * (float) (Math.cos(e) * Math.sin(a))};
    }

    /** A box of half sizes (hx, hy, hz) centred on (cx, cy, cz), turned yaw about the vertical. */
    private static Part box(float cx, float cy, float cz, float hx, float hy, float hz, float yaw, float[] colour) {
        Builder b = new Builder(cx, cy, cz, colour);
        float c = (float) Math.cos(yaw);
        float s = (float) Math.sin(yaw);
        float[][] p = new float[8][];
        for (int k = 0; k < 8; k++) {
            float x = (k & 1) == 0 ? -hx : hx;
            float y = (k & 2) == 0 ? -hy : hy;
            float z = (k & 4) == 0 ? -hz : hz;
            p[k] = new float[] {cx + x * c - z * s, cy + y, cz + x * s + z * c};
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] f : faces) {
            b.quad(p[f[0]], p[f[1]], p[f[2]], p[f[3]]);
        }
        return b.part();
    }

    /** Collects one convex part's triangles, each turned to face out from the part's middle (for culling). */
    private static final class Builder {
        private final FloatArrayList tris = new FloatArrayList();
        private final FloatArrayList normals = new FloatArrayList();
        private final float cx;
        private final float cy;
        private final float cz;
        private final float[] colour;

        Builder(float cx, float cy, float cz, float[] colour) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.colour = colour;
        }

        void tri(float[] a, float[] b, float[] c) {
            Vector3f n = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2])
                    .cross(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
            if (n.lengthSquared() < 1e-8f) {
                return;
            }
            float mx = (a[0] + b[0] + c[0]) / 3 - cx;
            float my = (a[1] + b[1] + c[1]) / 3 - cy;
            float mz = (a[2] + b[2] + c[2]) / 3 - cz;
            if (n.x * mx + n.y * my + n.z * mz < 0) {
                float[] t = b;
                b = c;
                c = t;
                n.negate();
            }
            n.normalize();
            for (float[] p : new float[][] {a, b, c}) {
                tris.add(p[0]);
                tris.add(p[1]);
                tris.add(p[2]);
            }
            normals.add(n.x);
            normals.add(n.y);
            normals.add(n.z);
        }

        void quad(float[] a, float[] b, float[] c, float[] d) {
            tri(a, b, c);
            tri(a, c, d);
        }

        Part part() {
            return new Part(tris.toFloatArray(), normals.toFloatArray(), colour, cx, cy, cz);
        }
    }
}
