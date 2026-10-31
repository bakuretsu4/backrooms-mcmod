package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490HuntPayload;
import com.rickr.backrooms.network.Level490ReachPayload;
import com.rickr.backrooms.world.Level490Hunt;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 490's creature as you see it (world/Level490Hunt decides what it does). After the
 * play-tests: "copy the Mind Flayer pretty closely" (Stranger Things' shadow over Hawkins: a tall
 * spindle of smoke with very long legs out of its middle rising to a sharp knee and down), then
 * "way too noticeable... more smoky, not so solid... more eldritch":
 * <ul>
 *   <li>It is made of smoke: thousands of dark ash particles drifting along its body and limbs,
 *       flowing slowly, its edges fraying, wisps rising off it; nothing solid.</li>
 *   <li>Hidden, but not quite: in the dark it is only a faint smudge in the fog; lightning shows it
 *       whole; and it fades when you look straight at it (it is clearest at the edge of your
 *       sight).</li>
 *   <li>Eldritch: no face, but a scatter of small dim cold eyes that blink out of step; a crown of
 *       long curved spines where a head would be; its limbs fray at their ends into tendrils (its
 *       hands are bunches of them); real chains hang inside the smoke, glimpsed in the flashes.</li>
 * </ul>
 * Its poses: far off in the clouds on the walk ({@link Level490Hunt#DISTANT}); hanging in the sky
 * off the tower as you climb, its legs trailing down into the clouds ({@link Level490Hunt#CLIMB});
 * holding the top's edge, tendril-hands over the rim ({@link Level490Hunt#HOLD}); floating after you
 * as you climb the chain ({@link Level490Hunt#FLOAT}). Its hands reach out of the sky to slam the
 * stair or grab at you, or up out of the clouds over an island's edge.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Creature {
    private static final ResourceLocation PUFF = Backrooms.id("textures/environment/l490_puff.png");
    private static final double SEA = 140.0;
    /** Everything is drawn this much bigger than the numbers below (it should dwarf the tower). */
    private static final float SIZE = 1.6f;
    /** The spindle: how long, and how far above the head's middle its point is. */
    private static final float BODY = 230f * SIZE;
    private static final float HEAD_R = 11f * SIZE;

    private static int stage = Level490Hunt.NONE;
    private static float near;
    /** Where its head should be, and where it is (gliding there), last tick and this tick. */
    private static final double[] TARGET = new double[3];
    private static final double[] HEAD = new double[3];
    private static final double[] HEAD_O = new double[3];
    /** The tower's grips for the climbing and holding poses, worked out each tick: {x, y, z} per limb. */
    private static final double[][] GRIP = new double[6][];
    private static final double[][] GRIP_O = new double[6][];
    private static double[] towerCentre;

    /** A hand on its way (see Level490ReachPayload). */
    private static Level490ReachPayload reach;
    private static long reachSeen;
    private static long shakeUntilMs;
    private static float shake;

    private Level490Creature() {}

    /** Network: what it is doing. */
    public static void receive(Level490HuntPayload p) {
        boolean jump = stage == Level490Hunt.NONE || Math.abs(p.x() - TARGET[0]) + Math.abs(p.z() - TARGET[2]) > 250;
        stage = p.stage();
        near = p.near();
        TARGET[0] = p.x();
        TARGET[1] = p.y();
        TARGET[2] = p.z();
        if (jump) {
            System.arraycopy(TARGET, 0, HEAD, 0, 3);
            System.arraycopy(TARGET, 0, HEAD_O, 0, 3);
            for (int i = 0; i < 6; i++) {
                GRIP[i] = null;
                GRIP_O[i] = null;
            }
        }
    }

    /** Network: a hand coming for you. */
    public static void reach(Level490ReachPayload p) {
        reach = p;
        Minecraft mc = Minecraft.getInstance();
        reachSeen = mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** 0..1: how near it is to having you (for the camcorder's distress). */
    public static float danger() {
        if (!Level490Atmosphere.inLevel490() || stage == Level490Hunt.NONE) {
            return 0;
        }
        float d = near;
        Minecraft mc = Minecraft.getInstance();
        if (reach != null && mc.level != null && mc.player != null && mc.level.getGameTime() < reach.lift()) {
            double dist = Math.hypot(mc.player.getX() - reach.tx(), mc.player.getZ() - reach.tz());
            d = Math.max(d, (float) Math.max(0, 1 - dist / 14.0));
        }
        if (stage == Level490Hunt.HOLD || stage == Level490Hunt.FLOAT) {
            d = Math.max(d, 0.35f);
        }
        return d;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !Level490Atmosphere.inLevel490()) {
            stage = Level490Hunt.NONE;
            reach = null;
            return;
        }
        System.arraycopy(HEAD, 0, HEAD_O, 0, 3);
        double k = stage == Level490Hunt.DISTANT ? 0.02 : stage == Level490Hunt.HOLD ? 0.04 : 0.1;
        for (int i = 0; i < 3; i++) {
            HEAD[i] += (TARGET[i] - HEAD[i]) * k;
        }
        long t = mc.level.getGameTime();
        if (reach != null && t > reach.lift() + 40) {
            reach = null;
        }
        if (reach != null && t == reach.land()) {
            double d = Math.hypot(mc.player.getX() - reach.tx(), mc.player.getZ() - reach.tz());
            if (d < 40) {
                shakeUntilMs = System.currentTimeMillis() + 700;
                shake = (float) Math.max(0.3, 1.0 - d / 40.0);
            }
        }
        grips(mc, t);
    }

    /** Where its legs and hands take hold of the tower when it holds the top, worked out once a tick. */
    private static void grips(Minecraft mc, long t) {
        for (int i = 0; i < 6; i++) {
            GRIP_O[i] = GRIP[i];
        }
        if (stage != Level490Hunt.HOLD || !Level490ClientState.hasSeed()) {
            for (int i = 0; i < 6; i++) {
                GRIP[i] = null;
            }
            towerCentre = null;
            return;
        }
        Level490Spire spire = Level490Layout.spireAt(Level490ClientState.seed(), (int) HEAD[0], (int) HEAD[2]);
        double cx = spire.centreX() + 0.5;
        double cz = spire.centreZ() + 0.5;
        towerCentre = new double[] {cx, cz};
        double a = Math.atan2(HEAD[2] - cz, HEAD[0] - cx);
        double top = Level490Spire.TOP;
        double[][] want = {
                {a - 0.42, top + 1}, {a + 0.42, top + 1},          // hands on the rim
                {a - 0.8, top - 30}, {a + 0.8, top - 30},          // upper legs
                {a - 0.55, top - 95}, {a + 0.55, top - 95}};       // lower legs
        for (int i = 0; i < 6; i++) {
            double gy = want[i][1];
            double[] s = spire.surface(cx + Math.cos(want[i][0]) * 100, Math.min(gy, Level490Spire.TOP - 0.5), cz + Math.sin(want[i][0]) * 100);
            GRIP[i] = new double[] {s[0], gy, s[1]};
            if (GRIP_O[i] == null) {
                GRIP_O[i] = GRIP[i];
            }
        }
    }

    /** The slam shakes the view. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        long now = System.currentTimeMillis();
        if (now >= shakeUntilMs) {
            return;
        }
        float f = (shakeUntilMs - now) / 700f * shake;
        event.setPitch(event.getPitch() + (float) Math.sin(now * 0.09) * 2.2f * f);
        event.setRoll(event.getRoll() + (float) Math.sin(now * 0.07 + 1) * 1.6f * f);
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    /** A point of a curve through its body: position (camera-relative, unscaled) and how thick it is there. */
    private record P(float x, float y, float z, float r) {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || stage == Level490Hunt.NONE
                || !Level490Atmosphere.inLevel490()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        double hx = HEAD_O[0] + (HEAD[0] - HEAD_O[0]) * pt;
        double hy = HEAD_O[1] + (HEAD[1] - HEAD_O[1]) * pt;
        double hz = HEAD_O[2] + (HEAD[2] - HEAD_O[2]) * pt;
        Vector3f h = new Vector3f((float) (hx - cam.x), (float) (hy - cam.y), (float) (hz - cam.z));
        float dist = h.length();
        float far = mc.gameRenderer.getDepthFar();
        float scale = Math.min(1f, 0.85f * far / (dist + 260f));
        float flash = Math.min(1f, Level490Lightning.cloudGlow(hx, hz) + Level490Atmosphere.flash() * 0.8f);
        float[] fog = Level490Atmosphere.fogColour(new float[3]);
        double seconds = t / 20.0;

        // How much of it shows: a faint smudge in the dark, whole in a flash; looking straight at it, it fades.
        Vector3f look = new Vector3f(event.getCamera().getLookVector());
        float straight = Math.max(0f, look.dot(new Vector3f(h).normalize()));
        float stare = 1f - 0.45f * smooth01((straight - 0.906f) / (0.985f - 0.906f)); // 25 degrees off .. 10 degrees off
        float vis = (0.5f + 0.5f * flash) * stare;

        // Its frame: facing you (level), right, up.
        Vector3f f = new Vector3f(-h.x, 0, -h.z);
        if (f.lengthSquared() < 1e-4f) {
            f.set(1, 0, 0);
        }
        f.normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(f).cross(up).normalize();

        List<List<P>> curves = new ArrayList<>();
        // The spindle: from the crown's foot down through the head, a neck, the chest, trailing off below.
        Vector3f crown = new Vector3f(h).add(new Vector3f(up).mul(HEAD_R * 0.7f));
        List<P> body = new ArrayList<>();
        int n = 46;
        float lean = stage == Level490Hunt.HOLD ? -14f : -6f;
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            float sway = (float) Math.sin(seconds * 0.25 + s * 3) * 7 * s * s;
            Vector3f p = new Vector3f(crown).sub(new Vector3f(up).mul(BODY * s)).add(new Vector3f(f).mul(lean * s * s))
                    .add(new Vector3f(right).mul(sway));
            body.add(new P(p.x, p.y, p.z, spindle(s)));
        }
        curves.add(body);
        // The crown: long curved spines out of the top, leaning back and out.
        for (int k = 0; k < 7; k++) {
            double a = k * 2 * Math.PI / 7 + 0.3 * Math.sin(k * 3.1);
            Vector3f out = new Vector3f(right).mul((float) Math.cos(a)).add(new Vector3f(f).mul((float) Math.sin(a) * 0.6f - 0.4f)).normalize();
            float len = (24 + 16 * (float) Math.abs(Math.sin(k * 2.3))) * SIZE;
            List<P> spine = new ArrayList<>();
            for (int i = 0; i <= 10; i++) {
                float u = i / 10f;
                Vector3f p = new Vector3f(crown).add(new Vector3f(out).mul(HEAD_R * 0.5f + len * 0.6f * u))
                        .add(new Vector3f(up).mul(len * (float) Math.sin(u * 1.4f)))
                        .add(new Vector3f(f).mul(-len * 0.35f * u * u));
                spine.add(new P(p.x, p.y, p.z, (2.6f - 2.2f * u) * SIZE));
            }
            curves.add(spine);
        }
        Vector3f chest = at(body, 0.27f);
        Vector3f armRoot = at(body, 0.2f);

        // The four legs, fraying into tendrils at their ends.
        double[] grip = gripsNow(pt);
        float[] legAngles = stage == Level490Hunt.HOLD ? new float[] {-55, 55, -125, 125} : new float[] {-80, 80, -145, 145};
        for (int i = 0; i < 4; i++) {
            Vector3f d = rotate(f, right, legAngles[i]);
            Vector3f s = new Vector3f(chest).add(new Vector3f(d).mul(12 * SIZE));
            Vector3f knee;
            Vector3f foot;
            float writhe = (float) Math.sin(seconds * 0.3 + i * 1.7);
            if (grip != null) {
                foot = rel(grip, 2 + i, cam);
                Vector3f out = new Vector3f(foot).sub(towerRel(cam, foot.y)).normalize();
                knee = new Vector3f(s).add(foot).mul(0.5f).add(new Vector3f(out).mul(38 * SIZE)).add(new Vector3f(up).mul(20 * SIZE));
            } else {
                float reachOut = (stage == Level490Hunt.DISTANT ? 160 : 110) * SIZE;
                // Down into the clouds.
                float drop = (float) Math.max(60 * SIZE, hy - SEA + 60);
                knee = new Vector3f(s).add(new Vector3f(d).mul(reachOut * 0.5f)).add(new Vector3f(up).mul((65 + 8 * writhe) * SIZE));
                foot = new Vector3f(s).add(new Vector3f(d).mul(reachOut)).sub(new Vector3f(up).mul(drop))
                        .add(new Vector3f(right).mul(writhe * 10));
            }
            curves.add(limb(s, knee, foot, 6.5f * SIZE, 4.2f * SIZE, 1.6f * SIZE));
            Vector3f dir = new Vector3f(foot).sub(knee).normalize();
            tendrils(curves, foot, dir, up, 5, 26 * SIZE, 0.2f, seconds, i * 7);
        }
        // The two arms; their hands are bunches of tendrils.
        for (int side = -1; side <= 1; side += 2) {
            int idx = side < 0 ? 0 : 1;
            Vector3f d = rotate(f, right, side * 75);
            Vector3f s = new Vector3f(armRoot).add(new Vector3f(d).mul(10 * SIZE));
            Vector3f hand;
            Vector3f handFwd;
            float curl;
            Vector3f slam = slamHand(side, cam, t, s, right);
            if (slam != null) {
                hand = slam;
                handFwd = new Vector3f(slam).sub(s).normalize();
                curl = 0.35f;
            } else if (grip != null) {
                hand = rel(grip, idx, cam);
                Vector3f inward = new Vector3f(towerRel(cam, hand.y)).sub(hand);
                inward.y = 0;
                handFwd = inward.normalize();
                curl = 0.9f;
            } else {
                // Reaching out a little toward you, slowly opening and closing.
                hand = new Vector3f(s).add(new Vector3f(f).mul(45 * SIZE)).add(new Vector3f(right).mul(side * 38 * SIZE)).add(new Vector3f(up).mul(-12 * SIZE));
                handFwd = new Vector3f(f);
                curl = 0.3f + 0.2f * (float) Math.sin(seconds * 0.4 + side);
            }
            Vector3f elbow = new Vector3f(s).add(hand).mul(0.5f).add(new Vector3f(d).mul(28 * SIZE)).add(new Vector3f(up).mul(-14 * SIZE));
            curves.add(limb(s, elbow, hand, 5f * SIZE, 3.6f * SIZE, 2.2f * SIZE));
            tendrils(curves, hand, handFwd, up, 7, 20 * SIZE, curl, seconds, 40 + side);
        }
        // An arm out of the clouds over the edge (the walk's reach).
        edgeArm(curves, cam, t, up, seconds);

        Matrix4f pose = event.getPoseStack().last().pose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Chains hanging inside the smoke, glimpsed in the flashes.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder links = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float iron = 0.03f + 0.3f * flash;
        float ironA = (0.1f + 0.8f * flash) * stare;
        for (int k = 0; k < 3; k++) {
            float ox = (k - 1) * 9 * SIZE;
            float[][] pts = new float[7][];
            for (int i = 0; i < 7; i++) {
                Vector3f p = new Vector3f(at(body, 0.12f + 0.12f * i)).add(new Vector3f(right).mul(ox + (float) Math.sin(seconds * 0.2 + k + i) * 3))
                        .add(new Vector3f(f).mul((float) Math.sin(i * 0.8 + k) * 4));
                pts[i] = new float[] {p.x, Math.max(p.y, (float) (SEA - cam.y)), p.z};
            }
            Level490SkyChains.drape(links, pose, pts, 0.9f, scale, iron, iron * 1.03f, iron * 1.15f, ironA, true);
        }
        MeshData linkMesh = links.build();
        if (linkMesh != null) {
            BufferUploader.drawWithShader(linkMesh);
        }

        // The smoke: particles flowing slowly along every curve, frayed at the edges, wisps rising off.
        List<float[]> puffs = new ArrayList<>();
        for (int ci = 0; ci < curves.size(); ci++) {
            smoke(curves.get(ci), ci, seconds, puffs);
        }
        puffs.sort((a, b) -> Float.compare(b[0] * b[0] + b[1] * b[1] + b[2] * b[2], a[0] * a[0] + a[1] * a[1] + a[2] * a[2]));
        float haze = (float) (0.55 * (1 - Math.exp(-dist / 1200.0)));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PUFF);
        BufferBuilder smoke = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] p : puffs) {
            float worldY = (float) (p[1] + cam.y);
            if (worldY < SEA - 20) {
                continue; // down in the cloud sea
            }
            float sea = Math.min(1f, (float) ((worldY - (SEA - 20)) / 50.0));
            float d = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            float a = p[4] * vis * sea * Math.min(1f, d / 30f);
            if (a < 0.004f) {
                continue;
            }
            // Near-black ash: a silhouette against the flash (only a little of it catches the light),
            // lost in the haze far off.
            float lum = 0.012f + 0.035f * p[5] * flash;
            float r = lum * (1 - haze) + fog[0] * haze;
            float g = lum * (1 - haze) + fog[1] * haze;
            float b = lum * 1.1f * (1 - haze) + fog[2] * haze;
            quad(smoke, pose, new Vector3f(p[0], p[1], p[2]), p[3], scale, up, r, g, b, a);
        }
        MeshData smokeMesh = smoke.build();
        if (smokeMesh != null) {
            BufferUploader.drawWithShader(smokeMesh);
        }

        // Many small dim eyes, scattered over where a face would be, cold, blinking out of step.
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        BufferBuilder eyes = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f face = new Vector3f(h).negate().normalize();
        Vector3f faceRight = new Vector3f(face).cross(up).normalize();
        Vector3f faceUp = new Vector3f(faceRight).cross(face).normalize();
        Vector3f head = at(body, 0.07f);
        float eyesLit = (0.3f + 0.7f * flash) * (0.5f + 0.5f * stare);
        for (int k = 0; k < 11; k++) {
            float ex = (float) Math.sin(k * 12.9898) * 0.55f;
            float ey = (float) Math.sin(k * 78.233 + 1) * 0.45f;
            Vector3f e = new Vector3f(head).add(new Vector3f(face).mul(HEAD_R * 0.95f)).add(new Vector3f(faceRight).mul(ex * HEAD_R))
                    .add(new Vector3f(faceUp).mul(ey * HEAD_R));
            // Each blinks on its own clock.
            double period = 70 + (k * 37) % 90;
            double phase = (t + k * 53) % period;
            float open = phase < 6 ? 0f : 1f;
            float size = (0.45f + 0.35f * (float) Math.abs(Math.sin(k * 4.7))) * SIZE;
            float a = eyesLit * open * (0.55f + 0.25f * (float) Math.sin(k * 2.1));
            quad(eyes, pose, e, size, scale, up, 0.72f, 0.8f, 0.9f, a);
            quad(eyes, pose, e, size * 2.6f, scale, up, 0.6f, 0.68f, 0.8f, a * 0.18f);
        }
        MeshData eyeMesh = eyes.build();
        if (eyeMesh != null) {
            BufferUploader.drawWithShader(eyeMesh);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static float smooth01(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return c * c * (3 - 2 * c);
    }

    /**
     * Smoke along one curve: particles spaced along it, each drifting slowly along it (so the smoke
     * flows), scattered within its thickness, a few thrown out further (the frayed edge), and wisps
     * rising off it. {x, y, z, size, alpha, how much lightning catches it}.
     */
    private static void smoke(List<P> curve, int ci, double seconds, List<float[]> out) {
        int n = curve.size();
        for (int i = 0; i + 1 < n; i++) {
            P a = curve.get(i);
            P b = curve.get(i + 1);
            float seg = (float) Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z));
            float r = (a.r + b.r) * 0.5f;
            int count = Math.max(1, Math.min(6, Math.round(seg / Math.max(0.8f, r * 0.55f))));
            for (int k = 0; k < count; k++) {
                int id = ci * 7919 + i * 31 + k;
                float h1 = hash(id);
                float h2 = hash(id + 1);
                float h3 = hash(id + 2);
                // Flowing along the curve.
                float u = (float) ((k + h1 + seconds * 0.12) / count % 1.0);
                float x = a.x + (b.x - a.x) * u;
                float y = a.y + (b.y - a.y) * u;
                float z = a.z + (b.z - a.z) * u;
                float rr = a.r + (b.r - a.r) * u;
                // Within its thickness; now and then further out, fraying.
                float spread = h3 > 0.85f ? 1.4f : 0.7f;
                float ox = (float) Math.sin(id * 1.7 + seconds * 0.3) * rr * spread * (h2 - 0.5f) * 2;
                float oy = (float) Math.cos(id * 2.3 + seconds * 0.25) * rr * spread * (h1 - 0.5f) * 2;
                float oz = (float) Math.sin(id * 0.9 - seconds * 0.35) * rr * spread * (h3 - 0.5f) * 2;
                float size = rr * (0.9f + 0.6f * h2);
                float alpha = h3 > 0.85f ? 0.18f : 0.5f;
                out.add(new float[] {x + ox, y + oy, z + oz, size, alpha, h1});
                if (k == 0 && (i % 3) == 0 && rr > 1.5f) {
                    // A wisp coming off, rising and thinning.
                    float w = (float) ((seconds * 0.15 + h2) % 1.0);
                    out.add(new float[] {x + ox * 1.6f, y + w * rr * 4, z + oz * 1.6f, rr * (0.8f + w * 1.5f), 0.15f * (1 - w), h2});
                }
            }
        }
    }

    private static float hash(int i) {
        int x = i * 0x27d4eb2d;
        x ^= x >>> 15;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        return (x & 0xffff) / 65535f;
    }

    /** The spindle's thickness at s (0 its top, 1 its end below): the head, a neck, the chest, trailing off. */
    private static float spindle(float s) {
        if (s < 0.04f) {
            return HEAD_R * (0.55f + 0.45f * (float) Math.sqrt(s / 0.04f));
        }
        if (s < 0.1f) {
            return HEAD_R;
        }
        if (s < 0.15f) {
            return HEAD_R - 3.5f * SIZE * (s - 0.1f) / 0.05f;
        }
        if (s < 0.33f) {
            return HEAD_R - 3.5f * SIZE + 10.5f * SIZE * (float) Math.sin((s - 0.15f) / 0.18f * Math.PI / 2);
        }
        return (float) (18 * SIZE * Math.pow(1 - (s - 0.33f) / 0.67f, 1.4) + 0.6);
    }

    private static Vector3f at(List<P> curve, float s) {
        P p = curve.get(Math.round(s * (curve.size() - 1)));
        return new Vector3f(p.x, p.y, p.z);
    }

    /** f turned by deg (degrees) about the vertical, toward right. */
    private static Vector3f rotate(Vector3f f, Vector3f right, float deg) {
        double a = Math.toRadians(deg);
        return new Vector3f(f).mul((float) Math.cos(a)).add(new Vector3f(right).mul((float) Math.sin(a))).normalize();
    }

    /** The grips this frame (between last tick's and this tick's), or null. */
    private static double[] gripsNow(float pt) {
        if (GRIP[0] == null) {
            return null;
        }
        double[] out = new double[18];
        for (int i = 0; i < 6; i++) {
            double[] a = GRIP_O[i] == null ? GRIP[i] : GRIP_O[i];
            for (int k = 0; k < 3; k++) {
                out[i * 3 + k] = a[k] + (GRIP[i][k] - a[k]) * pt;
            }
        }
        return out;
    }

    private static Vector3f rel(double[] grip, int i, Vec3 cam) {
        return new Vector3f((float) (grip[i * 3] - cam.x), (float) (grip[i * 3 + 1] - cam.y), (float) (grip[i * 3 + 2] - cam.z));
    }

    /** The tower's axis at height y (camera-relative). */
    private static Vector3f towerRel(Vec3 cam, float y) {
        double[] c = towerCentre == null ? new double[] {cam.x, cam.z} : towerCentre;
        return new Vector3f((float) (c[0] - cam.x), y, (float) (c[1] - cam.z));
    }

    /** A limb through three joints, tapering, a little knobbly. */
    private static List<P> limb(Vector3f a, Vector3f b, Vector3f c, float ra, float rb, float rc) {
        List<P> out = new ArrayList<>();
        int n = 24;
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            Vector3f p;
            float r;
            if (s < 0.5f) {
                float u = s / 0.5f;
                p = new Vector3f(a).mul(1 - u).add(new Vector3f(b).mul(u));
                r = ra + (rb - ra) * u;
            } else {
                float u = (s - 0.5f) / 0.5f;
                p = new Vector3f(b).mul(1 - u).add(new Vector3f(c).mul(u));
                r = rb + (rc - rb) * u;
            }
            r *= 1 + 0.12f * (float) Math.sin(i * 1.9);
            out.add(new P(p.x, p.y, p.z, r));
        }
        return out;
    }

    /**
     * A limb's end fraying into tendrils: {@code count} thin wavering strands out of p along dir,
     * spreading, curling toward the ground by {@code curl} (gripping hands curl most).
     */
    private static void tendrils(List<List<P>> curves, Vector3f p, Vector3f dir, Vector3f up, int count, float len, float curl,
                                 double seconds, int salt) {
        Vector3f side = new Vector3f(dir).cross(up);
        if (side.lengthSquared() < 1e-4f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3f other = new Vector3f(dir).cross(side).normalize();
        for (int k = 0; k < count; k++) {
            float a = (k - (count - 1) / 2f) / Math.max(1, count - 1);
            float h = hash(salt * 97 + k);
            Vector3f d = new Vector3f(dir).add(new Vector3f(side).mul(a * 1.1f)).add(new Vector3f(other).mul((h - 0.5f) * 0.8f)).normalize();
            float l = len * (0.7f + 0.6f * h);
            List<P> strand = new ArrayList<>();
            for (int i = 0; i <= 9; i++) {
                float u = i / 9f;
                float wave = (float) Math.sin(seconds * 0.8 + k * 1.3 + u * 5) * l * 0.08f * u;
                Vector3f q = new Vector3f(p).add(new Vector3f(d).mul(l * u)).add(new Vector3f(other).mul(wave))
                        .sub(new Vector3f(up).mul(curl * l * u * u * 0.8f));
                strand.add(new P(q.x, q.y, q.z, (1.4f - 1.2f * u) * SIZE));
            }
            curves.add(strand);
        }
    }

    /** During a slam or a grab: where the arm on that side has its hand now, or null. */
    private static Vector3f slamHand(int side, Vec3 cam, double t, Vector3f shoulder, Vector3f right) {
        if (reach == null || reach.kind() != Level490ReachPayload.SLAM) {
            return null;
        }
        Vector3f target = new Vector3f((float) (reach.tx() - cam.x), (float) (reach.ty() - cam.y), (float) (reach.tz() - cam.z));
        int want = new Vector3f(target).sub(shoulder).dot(right) < 0 ? -1 : 1;
        if (want != side) {
            return null;
        }
        double lead = Math.max(20, reach.lift() - reach.land() > 0 ? Math.min(50, reach.land() - reachSeen) : 50);
        double start = reach.land() - lead;
        if (t < start || t > reach.lift() + 30) {
            return null;
        }
        Vector3f above = new Vector3f(target).add(0, 30, 0).add(new Vector3f(target).sub(shoulder).mul(-0.15f));
        double rise = lead - 12;
        if (t < reach.land() - 12) {
            float u = (float) ((t - start) / rise);
            u = u * u * (3 - 2 * u);
            Vector3f from = new Vector3f(shoulder).add(new Vector3f(right).mul(side * 30)).add(0, 10, 0);
            return from.mul(1 - u).add(new Vector3f(above).mul(u));
        }
        if (t < reach.land()) {
            float u = (float) ((t - (reach.land() - 12)) / 12.0);
            return new Vector3f(above).mul(1 - u * u).add(new Vector3f(target).add(0, 1.5f, 0).mul(u * u));
        }
        if (t < reach.lift()) {
            return new Vector3f(target).add(0, 1.5f, 0);
        }
        float u = (float) ((t - reach.lift()) / 30.0);
        return new Vector3f(target).add(0, 1.5f + u * 40, 0);
    }

    /** The walk's reach: an arm up out of the clouds, over the edge, its hand coming down on you. */
    private static void edgeArm(List<List<P>> curves, Vec3 cam, double t, Vector3f up, double seconds) {
        if (reach == null || reach.kind() != Level490ReachPayload.OVER_EDGE) {
            return;
        }
        double start = reach.land() - 60;
        if (t < start || t > reach.lift() + 40) {
            return;
        }
        Vector3f edge = new Vector3f((float) (reach.fx() - cam.x), (float) (reach.fy() - cam.y), (float) (reach.fz() - cam.z));
        Vector3f target = new Vector3f((float) (reach.tx() - cam.x), (float) (reach.ty() - cam.y), (float) (reach.tz() - cam.z));
        Vector3f across = new Vector3f(target).sub(edge);
        across.y = 0;
        Vector3f dir = across.lengthSquared() < 1e-3f ? new Vector3f(1, 0, 0) : new Vector3f(across).normalize();
        Vector3f base = new Vector3f(edge).sub(new Vector3f(dir).mul(30)).sub(0, (float) (reach.fy() - SEA + 30), 0);
        Vector3f hand;
        Vector3f elbow;
        if (t < reach.land() - 15) {
            float u = (float) ((t - start) / 45.0);
            u = u * u * (3 - 2 * u);
            elbow = new Vector3f(edge).sub(new Vector3f(dir).mul(4)).add(0, -40 + 50 * u, 0);
            hand = new Vector3f(elbow).add(new Vector3f(dir).mul(6 * u)).add(0, 14, 0);
        } else if (t < reach.land()) {
            float u = (float) ((t - (reach.land() - 15)) / 15.0);
            elbow = new Vector3f(edge).sub(new Vector3f(dir).mul(4)).add(0, 10, 0);
            Vector3f above = new Vector3f(elbow).add(new Vector3f(dir).mul(6)).add(0, 14, 0);
            hand = above.mul(1 - u * u).add(new Vector3f(target).add(0, 1.5f, 0).mul(u * u));
        } else if (t < reach.lift()) {
            elbow = new Vector3f(edge).sub(new Vector3f(dir).mul(4)).add(0, 10, 0);
            hand = new Vector3f(target).add(0, 1.5f, 0);
        } else {
            float u = (float) ((t - reach.lift()) / 40.0);
            elbow = new Vector3f(edge).sub(new Vector3f(dir).mul(4)).add(0, 10 - 60 * u, 0);
            hand = new Vector3f(elbow).add(new Vector3f(dir).mul(4)).add(0, 6, 0);
        }
        curves.add(limb(base, elbow, hand, 6f * SIZE, 4.5f * SIZE, 2.2f * SIZE));
        tendrils(curves, hand, dir, up, 7, 16 * SIZE, 0.5f, seconds, 77);
    }

    /** A soft quad facing the camera at p (camera-relative), size half-width, drawn scaled toward the camera. */
    private static void quad(BufferBuilder q, Matrix4f pose, Vector3f p, float size, float scale, Vector3f up, float r, float g, float b, float a) {
        Vector3f dir = new Vector3f(p).normalize();
        Vector3f rr = new Vector3f(dir).cross(up);
        if (rr.lengthSquared() < 1e-6f) {
            rr.set(1, 0, 0);
        }
        rr.normalize();
        Vector3f uu = new Vector3f(rr).cross(dir).normalize();
        for (int k = 0; k < 4; k++) {
            float sx = (k == 1 || k == 2) ? 1 : -1;
            float sy = k >= 2 ? 1 : -1;
            q.addVertex(pose, (p.x + (rr.x * sx + uu.x * sy) * size) * scale, (p.y + (rr.y * sx + uu.y * sy) * size) * scale,
                            (p.z + (rr.z * sx + uu.z * sy) * size) * scale)
                    .setUv(sx > 0 ? 1 : 0, sy > 0 ? 0 : 1).setColor(r, g, b, a);
        }
    }
}
