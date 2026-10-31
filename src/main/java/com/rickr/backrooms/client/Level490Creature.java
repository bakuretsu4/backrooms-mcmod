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
 * play-test, "copy the Mind Flayer pretty closely" (Stranger Things' shadow over Hawkins: a tall
 * spindle of smoke, pointed at the top and trailing off below, with very long thick legs coming out
 * of its middle, rising up and out to a sharp knee and down) with what the user asked for: a huge head
 * at the top of the spindle with two white eyes glaring, and arms ending in huge hands.
 * <ul>
 *   <li><b>Far off</b> on the walk ({@link Level490Hunt#DISTANT}): standing in the cloud sea, its
 *       legs planted far out in the clouds, a black shape you see when lightning flashes behind it,
 *       its eyes faint.</li>
 *   <li><b>Climbing</b> ({@link Level490Hunt#CLIMB}): clinging to the outside of the tower below you,
 *       its legs wrapped round the tower, its hands taking hold above its head one after the other,
 *       its head turned up at you; a hand slams down on the stair ahead.</li>
 *   <li><b>Holding the top</b> ({@link Level490Hunt#HOLD}): its hands on the top's edge, fingers over
 *       the rim, its head over the edge, staring.</li>
 *   <li><b>Floating</b> ({@link Level490Hunt#FLOAT}): it lets go and hangs in the sky off the tower,
 *       its legs trailing, following you up the chain, staring.</li>
 * </ul>
 * Drawn in the world (behind the tower when it is behind the tower) from smooth tubes, near-black,
 * edged with a little light and lit up when lightning flashes, with ash and smoke drifting off it.
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
        double k = stage == Level490Hunt.DISTANT ? 0.02 : 0.2;
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

    /** Where its legs and hands take hold of the tower (climbing, holding the top), worked out once a tick. */
    private static void grips(Minecraft mc, long t) {
        for (int i = 0; i < 6; i++) {
            GRIP_O[i] = GRIP[i];
        }
        if (stage != Level490Hunt.CLIMB && stage != Level490Hunt.HOLD || !Level490ClientState.hasSeed()) {
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
        double y = HEAD[1];
        double[][] want;
        if (stage == Level490Hunt.HOLD) {
            double top = Level490Spire.TOP;
            want = new double[][] {
                    {a - 0.42, top + 1}, {a + 0.42, top + 1},          // hands on the rim
                    {a - 0.8, top - 30}, {a + 0.8, top - 30},          // upper legs
                    {a - 0.55, top - 95}, {a + 0.55, top - 95}};       // lower legs
        } else {
            // Climbing: the hands take hold above the head, one then the other.
            double phase = t * 0.06;
            want = new double[][] {
                    {a - 0.38, y + 14 + 6 * Math.sin(phase)}, {a + 0.38, y + 14 + 6 * Math.sin(phase + Math.PI)},
                    {a - 0.9, y + 20}, {a + 0.9, y + 20},
                    {a - 0.6, y - 55}, {a + 0.6, y - 55}};
        }
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

    /** A point of a tube: position (camera-relative, unscaled) and radius. */
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
        float glow = Level490Lightning.cloudGlow(hx, hz) + Level490Atmosphere.flash() * 0.6f;
        float[] fog = Level490Atmosphere.fogColour(new float[3]);
        double seconds = t / 20.0;

        // Its frame: facing you (level), right, up.
        Vector3f f = new Vector3f(-h.x, 0, -h.z);
        if (f.lengthSquared() < 1e-4f) {
            f.set(1, 0, 0);
        }
        f.normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(f).cross(up).normalize();

        List<List<P>> tubes = new ArrayList<>();
        List<float[]> puffs = new ArrayList<>();
        int smoking;

        // The spindle: its point above the head, the head, a neck, the chest, trailing off below.
        Vector3f tip = new Vector3f(h).add(new Vector3f(up).mul(18f * SIZE));
        List<P> body = new ArrayList<>();
        int n = 46;
        float lean = stage == Level490Hunt.CLIMB || stage == Level490Hunt.HOLD ? -14f : -6f;
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            float sway = (float) Math.sin(seconds * 0.25 + s * 3) * 7 * s * s;
            Vector3f p = new Vector3f(tip).sub(new Vector3f(up).mul(BODY * s)).add(new Vector3f(f).mul(lean * s * s))
                    .add(new Vector3f(right).mul(sway));
            body.add(new P(p.x, p.y, p.z, spindle(s)));
        }
        tubes.add(body);
        smoking = 1;
        // Shoulders, where the limbs leave the chest.
        Vector3f chest = at(body, 0.27f);
        Vector3f armRoot = at(body, 0.2f);

        // The four legs and the two arms.
        double[] grip = gripsNow(pt);
        float[] legAngles = stage == Level490Hunt.FLOAT ? new float[] {-80, 80, -145, 145} : new float[] {-55, 55, -125, 125};
        for (int i = 0; i < 4; i++) {
            Vector3f d = rotate(f, right, legAngles[i]);
            Vector3f s = new Vector3f(chest).add(new Vector3f(d).mul(12 * SIZE));
            Vector3f knee;
            Vector3f foot;
            float writhe = (float) Math.sin(seconds * 0.3 + i * 1.7);
            if (grip != null) {
                int g = i < 2 ? 2 + i : 4 + i - 2;
                foot = rel(grip, g, cam);
                Vector3f out = new Vector3f(foot).sub(towerRel(cam, foot.y)).normalize();
                knee = new Vector3f(s).add(foot).mul(0.5f).add(new Vector3f(out).mul(38 * SIZE)).add(new Vector3f(up).mul(20 * SIZE));
            } else {
                float reachOut = (stage == Level490Hunt.FLOAT ? 105 : 160) * SIZE;
                float drop = stage == Level490Hunt.FLOAT ? 150 * SIZE : (float) (hy - SEA + 120);
                knee = new Vector3f(s).add(new Vector3f(d).mul(reachOut * 0.5f)).add(new Vector3f(up).mul((65 + 8 * writhe) * SIZE));
                foot = new Vector3f(s).add(new Vector3f(d).mul(reachOut)).sub(new Vector3f(up).mul(drop))
                        .add(new Vector3f(right).mul(writhe * 10));
            }
            tubes.add(limb(s, knee, foot, 6.5f * SIZE, 4.2f * SIZE, 1.0f * SIZE));
        }
        smoking = tubes.size();
        for (int side = -1; side <= 1; side += 2) {
            int idx = side < 0 ? 0 : 1;
            Vector3f d = rotate(f, right, side * 75);
            Vector3f s = new Vector3f(armRoot).add(new Vector3f(d).mul(10 * SIZE));
            Vector3f hand;
            Vector3f handFwd;
            boolean grab = false;
            Vector3f slam = slamHand(side, cam, t, s, f, right);
            if (slam != null) {
                hand = slam;
                handFwd = new Vector3f(f);
                grab = false;
            } else if (grip != null) {
                hand = rel(grip, idx, cam);
                Vector3f inward = new Vector3f(towerRel(cam, hand.y)).sub(hand);
                inward.y = 0;
                handFwd = inward.normalize();
                grab = true;
            } else if (stage == Level490Hunt.FLOAT) {
                // Reaching out a little toward you, open.
                hand = new Vector3f(s).add(new Vector3f(f).mul(45 * SIZE)).add(new Vector3f(right).mul(side * 38 * SIZE)).add(new Vector3f(up).mul(-12 * SIZE));
                handFwd = new Vector3f(f);
            } else {
                hand = new Vector3f(s).add(new Vector3f(d).mul(40 * SIZE)).sub(new Vector3f(up).mul(90 * SIZE));
                handFwd = new Vector3f(up).negate();
            }
            Vector3f out = new Vector3f(hand).sub(s);
            Vector3f elbow = new Vector3f(s).add(hand).mul(0.5f).add(new Vector3f(d).mul(28 * SIZE)).add(new Vector3f(up).mul(-14 * SIZE));
            if (out.length() < 20 * SIZE) {
                elbow.add(new Vector3f(up).mul(10 * SIZE));
            }
            tubes.add(limb(s, elbow, hand, 5f * SIZE, 3.6f * SIZE, 2.0f * SIZE));
            hand(tubes, hand, handFwd, up, grab);
        }
        // An arm out of the clouds over the edge (the walk's reach).
        edgeArm(tubes, cam, t, up);

        // Ash smoking thinly off the body and the limbs (not the hands: they stay sharp).
        for (int ti = 0; ti < smoking; ti++) {
            List<P> tube = tubes.get(ti);
            for (int i = 0; i < tube.size(); i += 3) {
                P p = tube.get(i);
                if (p.r < 2.0f) {
                    continue;
                }
                float drift = (float) ((seconds * 2.5 + i * 3.7 + ti * 11) % 14.0);
                float ox = (float) Math.sin(i * 2.3 + ti) * p.r * 0.9f;
                float oz = (float) Math.cos(i * 1.7 + ti * 2) * p.r * 0.9f;
                float a = 0.13f * (1 - drift / 14f);
                // Thinner near you, so it never fogs your view.
                float d = (float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
                a *= Math.min(1f, d / 40f);
                puffs.add(new float[] {p.x + ox, p.y + drift, p.z + oz, p.r * (0.8f + drift * 0.05f), a});
            }
        }

        Matrix4f pose = event.getPoseStack().last().pose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder tris = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float haze = (float) (0.75 * (1 - Math.exp(-dist / 900.0)));
        for (List<P> tube : tubes) {
            tube(tris, pose, tube, scale, glow, haze, fog, cam);
        }
        MeshData mesh = tris.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        // The eyes: white, glaring, glowing (faint far off unless lightning shows it).
        float eyesLit = stage == Level490Hunt.DISTANT ? 0.25f + 0.75f * Math.min(1f, glow) : 1f;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PUFF);
        BufferBuilder glowQuads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f faceDir = new Vector3f(h).negate().normalize();
        Vector3f eyeRight = new Vector3f(faceDir).cross(up).normalize();
        Vector3f head = at(body, 0.075f);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f e = new Vector3f(head).add(new Vector3f(faceDir).mul(HEAD_R * 1.35f)).add(new Vector3f(eyeRight).mul(side * 4.2f * SIZE))
                    .add(new Vector3f(up).mul(1.5f * SIZE));
            // A hard white core and a wide soft glare.
            billboard(glowQuads, pose, e, 2.0f * SIZE, 1.0f, 1.0f, 1.0f, eyesLit, scale, up, eyeRight, 0.3f);
            billboard(glowQuads, pose, e, 2.0f * SIZE, 1.0f, 1.0f, 1.0f, eyesLit, scale, up, eyeRight, 0.3f);
            billboard(glowQuads, pose, e, 4.5f * SIZE, 0.85f, 0.88f, 1.0f, 0.14f * eyesLit, scale, up, eyeRight, 0.4f);
        }
        MeshData glowMesh = glowQuads.build();
        if (glowMesh != null) {
            BufferUploader.drawWithShader(glowMesh);
        }
        // The smoke, farthest first.
        RenderSystem.defaultBlendFunc();
        puffs.sort((a, b) -> Float.compare(b[0] * b[0] + b[1] * b[1] + b[2] * b[2], a[0] * a[0] + a[1] * a[1] + a[2] * a[2]));
        BufferBuilder smoke = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float c = 0.03f + 0.12f * Math.min(1f, glow);
        for (float[] p : puffs) {
            Vector3f at = new Vector3f(p[0], p[1], p[2]);
            if (p[1] + cam.y < SEA - 10) {
                continue;
            }
            Vector3f dir = new Vector3f(at).normalize();
            Vector3f r = new Vector3f(dir).cross(up).normalize();
            Vector3f u = new Vector3f(r).cross(dir).normalize();
            float s = p[3];
            for (int k = 0; k < 4; k++) {
                float sx = (k == 1 || k == 2) ? 1 : -1;
                float sy = k >= 2 ? 1 : -1;
                smoke.addVertex(pose, (at.x + (r.x * sx + u.x * sy) * s) * scale, (at.y + (r.y * sx + u.y * sy) * s) * scale,
                                (at.z + (r.z * sx + u.z * sy) * s) * scale)
                        .setUv(sx > 0 ? 1 : 0, sy > 0 ? 0 : 1).setColor(c, c, c * 1.05f, p[4] * (1 - haze));
            }
        }
        MeshData smokeMesh = smoke.build();
        if (smokeMesh != null) {
            BufferUploader.drawWithShader(smokeMesh);
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    /** The spindle's radius at s (0 its point, 1 its end below): point, head, neck, chest, trailing off. */
    private static float spindle(float s) {
        if (s < 0.05f) {
            return 2 * SIZE + (HEAD_R - 2 * SIZE) * (float) Math.sqrt(s / 0.05f);
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

    private static Vector3f at(List<P> tube, float s) {
        P p = tube.get(Math.round(s * (tube.size() - 1)));
        return new Vector3f(p.x, p.y, p.z);
    }

    /** d turned by deg (degrees) about the vertical, from f toward right. */
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

    /** A limb through three joints, tapering: a smooth curve sampled into a tube. */
    private static List<P> limb(Vector3f a, Vector3f b, Vector3f c, float ra, float rb, float rc) {
        List<P> out = new ArrayList<>();
        int n = 30;
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            // A quadratic through b at the middle (b is the knee: pulled toward, sharp-ish).
            Vector3f p;
            float r;
            if (s < 0.5f) {
                float u = s / 0.5f;
                p = new Vector3f(a).mul(1 - u).add(new Vector3f(b).mul(u));
                r = ra + (rb - ra) * u;
                p.add(new Vector3f(b).sub(new Vector3f(a).add(c).mul(0.5f)).mul(0.12f * (float) Math.sin(u * Math.PI)));
            } else {
                float u = (s - 0.5f) / 0.5f;
                p = new Vector3f(b).mul(1 - u).add(new Vector3f(c).mul(u));
                r = rb + (rc - rb) * u;
            }
            // Knobbly, as if gnarled.
            r *= 1 + 0.12f * (float) Math.sin(i * 1.9);
            out.add(new P(p.x, p.y, p.z, r));
        }
        return out;
    }

    /**
     * A huge hand at the wrist p, fingers toward fwd: long and bony like a spider's legs, the bones of
     * the back of the hand fanning out from the wrist to the knuckles, then four long jointed fingers
     * and a thumb; curled over and down when it grips (the rim, the tower), spread when it slams.
     */
    private static void hand(List<List<P>> tubes, Vector3f p, Vector3f fwd, Vector3f up, boolean grab) {
        Vector3f fw = new Vector3f(fwd).normalize();
        Vector3f side = new Vector3f(fw).cross(up);
        if (side.lengthSquared() < 1e-4f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3f down = new Vector3f(side).cross(fw).normalize();
        if (down.y > 0) {
            down.negate();
        }
        float palm = 9 * SIZE;
        for (int k = 0; k < 5; k++) {
            boolean thumb = k == 4;
            float spread = grab ? 0.16f : 0.32f;
            float across = thumb ? -1.1f : (k - 1.5f) * spread;
            Vector3f dir = new Vector3f(fw).add(new Vector3f(side).mul(across)).normalize();
            // The bone of the back of the hand, wrist to knuckle (the thumb's starts lower and short).
            Vector3f knuckle = new Vector3f(p).add(new Vector3f(dir).mul(thumb ? palm * 0.45f : palm)).add(new Vector3f(down).mul(grab ? 0.5f : 0));
            tubes.add(limb(p, new Vector3f(p).add(knuckle).mul(0.5f).add(new Vector3f(down).mul(-0.6f * SIZE)), knuckle, 1.7f * SIZE, 1.5f * SIZE,
                    1.3f * SIZE));
            // The finger: two long joints, curling over and down when it grips.
            float len = (thumb ? 8 : 15 - 2 * Math.abs(k - 1.5f)) * SIZE;
            Vector3f mid = new Vector3f(knuckle).add(new Vector3f(dir).mul(len * 0.55f)).add(new Vector3f(down).mul((grab ? 1.5f : -0.6f) * SIZE));
            Vector3f tipP = new Vector3f(mid).add(new Vector3f(dir).mul(len * (grab ? 0.2f : 0.45f)))
                    .add(new Vector3f(down).mul((grab ? 6f : 1.5f) * SIZE));
            tubes.add(limb(knuckle, mid, tipP, 1.3f * SIZE, 1.0f * SIZE, 0.35f * SIZE));
        }
    }

    /** During a slam on the tower: where the arm on that side has its hand now, or null. */
    private static Vector3f slamHand(int side, Vec3 cam, double t, Vector3f shoulder, Vector3f f, Vector3f right) {
        if (reach == null || reach.kind() != Level490ReachPayload.SLAM) {
            return null;
        }
        Vector3f target = new Vector3f((float) (reach.tx() - cam.x), (float) (reach.ty() - cam.y), (float) (reach.tz() - cam.z));
        // The arm on the target's side.
        int want = new Vector3f(target).sub(shoulder).dot(right) < 0 ? -1 : 1;
        if (want != side) {
            return null;
        }
        double start = reach.land() - 50;
        if (t < start || t > reach.lift() + 30) {
            return null;
        }
        Vector3f above = new Vector3f(target).add(0, 30, 0).add(new Vector3f(target).sub(shoulder).mul(-0.15f));
        if (t < reach.land() - 12) {
            // Rising up and over: from beside the shoulder to high above the stair.
            float u = (float) ((t - start) / 38.0);
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
    private static void edgeArm(List<List<P>> tubes, Vec3 cam, double t, Vector3f up) {
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
            // Rising out of the clouds up past the edge.
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
        tubes.add(limb(base, elbow, hand, 6f * SIZE, 4.5f * SIZE, 2.0f * SIZE));
        hand(tubes, hand, dir, up, false);
    }

    /** A tube along pts: rings round it, joined; dark, edged with light, lit by lightning, into the haze. */
    private static void tube(BufferBuilder tris, Matrix4f pose, List<P> pts, float scale, float glow, float haze, float[] fog, Vec3 cam) {
        int around = 12;
        int n = pts.size();
        float[][][] ring = new float[n][around + 1][];
        Vector3f prevN = null;
        for (int i = 0; i < n; i++) {
            P p = pts.get(i);
            P a = pts.get(Math.max(0, i - 1));
            P b = pts.get(Math.min(n - 1, i + 1));
            Vector3f tan = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z);
            if (tan.lengthSquared() < 1e-6f) {
                tan.set(0, 1, 0);
            }
            tan.normalize();
            Vector3f n1;
            if (prevN == null) {
                n1 = new Vector3f(tan).cross(Math.abs(tan.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            } else {
                // Carry the frame along so the rings don't twist.
                n1 = new Vector3f(prevN).sub(new Vector3f(tan).mul(prevN.dot(tan)));
                if (n1.lengthSquared() < 1e-6f) {
                    n1 = new Vector3f(tan).cross(new Vector3f(0, 1, 0));
                }
                n1.normalize();
            }
            prevN = n1;
            Vector3f n2 = new Vector3f(tan).cross(n1).normalize();
            for (int k = 0; k <= around; k++) {
                double ang = 2 * Math.PI * k / around;
                Vector3f nn = new Vector3f(n1).mul((float) Math.cos(ang)).add(new Vector3f(n2).mul((float) Math.sin(ang)));
                // Rough, as if made of churning ash.
                float rough = 1 + 0.08f * (float) Math.sin(i * 2.7 + k * 1.9) + 0.06f * (float) Math.sin(i * 0.9 - k * 2.3);
                Vector3f q = new Vector3f(p.x, p.y, p.z).add(new Vector3f(nn).mul(p.r * rough));
                ring[i][k] = new float[] {q.x, q.y, q.z, nn.x, nn.y, nn.z};
            }
        }
        for (int i = 0; i + 1 < n; i++) {
            for (int k = 0; k < around; k++) {
                vertex(tris, pose, ring[i][k], scale, glow, haze, fog, cam);
                vertex(tris, pose, ring[i + 1][k], scale, glow, haze, fog, cam);
                vertex(tris, pose, ring[i + 1][k + 1], scale, glow, haze, fog, cam);
                vertex(tris, pose, ring[i][k], scale, glow, haze, fog, cam);
                vertex(tris, pose, ring[i + 1][k + 1], scale, glow, haze, fog, cam);
                vertex(tris, pose, ring[i][k + 1], scale, glow, haze, fog, cam);
            }
        }
    }

    private static void vertex(BufferBuilder tris, Matrix4f pose, float[] v, float scale, float glow, float haze, float[] fog, Vec3 cam) {
        Vector3f view = new Vector3f(-v[0], -v[1], -v[2]).normalize();
        float facing = Math.abs(v[3] * view.x + v[4] * view.y + v[5] * view.z);
        float rim = (float) Math.pow(1 - facing, 2.5);
        float g = Math.min(1.2f, glow);
        float l = 0.03f + rim * (0.13f + 0.55f * g) + Math.max(0, v[4]) * 0.03f + g * 0.08f;
        // Below the cloud sea's top it is lost in the cloud's grey.
        float worldY = (float) (v[1] + cam.y);
        float hz = Math.max(haze, Math.max(0, Math.min(1, (float) ((SEA + 10 - worldY) / 40.0))));
        float r = l * (1 - hz) + fog[0] * hz;
        float gg = l * (1 - hz) + fog[1] * hz;
        float b = l * 1.08f * (1 - hz) + fog[2] * hz;
        tris.addVertex(pose, v[0] * scale, v[1] * scale, v[2] * scale).setColor(r, gg, b, 1.0f);
    }

    /** A soft glowing quad facing the camera, squashed to an almond (squash < 1 makes it wider than tall). */
    private static void billboard(BufferBuilder q, Matrix4f pose, Vector3f at, float size, float r, float g, float b, float a, float scale,
                                  Vector3f up, Vector3f right, float squash) {
        Vector3f dir = new Vector3f(at).normalize();
        Vector3f rr = new Vector3f(dir).cross(up).normalize();
        Vector3f uu = new Vector3f(rr).cross(dir).normalize().mul(squash);
        for (int k = 0; k < 4; k++) {
            float sx = (k == 1 || k == 2) ? 1 : -1;
            float sy = k >= 2 ? 1 : -1;
            q.addVertex(pose, (at.x + (rr.x * sx + uu.x * sy) * size) * scale, (at.y + (rr.y * sx + uu.y * sy) * size) * scale,
                            (at.z + (rr.z * sx + uu.z * sy) * size) * scale)
                    .setUv(sx > 0 ? 1 : 0, sy > 0 ? 0 : 1).setColor(r, g, b, a);
        }
    }
}
