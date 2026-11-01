package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490ReachPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
 * What the creature's violence looks and feels like on the client (world/Level490Hunt; after the
 * play-test: "more violent"):
 * <ul>
 *   <li><b>A thrown chunk of masonry</b> arcs in from its hand out in the sky, tumbling, and bursts
 *       into pieces where it lands.</li>
 *   <li><b>It shaking the tower:</b> a tremor first, then the view lurches about for a few seconds
 *       while masonry falls past from the tower above.</li>
 *   <li><b>The silence before it acts</b> ({@link #hush}): from the moment it starts an attack until
 *       just after it lands, the rain, the wind and the whales fade nearly out.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Attacks {
    /** Gravity on the pieces, blocks a tick squared. */
    private static final double G = 0.08;
    /** How long a shake is felt coming before it starts (ticks); must match world/Level490Hunt.SHAKE_LEAD. */
    private static final int SHAKE_LEAD = 40;

    /** A tumbling piece: where it was thrown from and how, when, how big, how long it lasts. */
    private record Piece(double x, double y, double z, double vx, double vy, double vz, long born, long dies, float size, float spin) {
        Vector3f at(double t, Vec3 cam) {
            double a = t - born;
            return new Vector3f((float) (x + vx * a - cam.x), (float) (y + vy * a - 0.5 * G * a * a - cam.y), (float) (z + vz * a - cam.z));
        }
    }

    /** A thrown chunk on its way: it bursts at {@code land} at (tx, ty, tz). */
    private record Throw(Piece chunk, long land, double tx, double ty, double tz) {}

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final List<Throw> THROWS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long shakeFrom = Long.MIN_VALUE;
    private static long shakeUntil = Long.MIN_VALUE;
    private static double gripX;
    private static double gripZ;
    private static long hushUntil;
    private static float hush;

    private Level490Attacks() {}

    /** From Level490Creature: an attack has begun; the world goes quiet until just after it lands. */
    static void attack(Level490ReachPayload p) {
        hushUntil = Math.max(hushUntil, (p.kind() == Level490ReachPayload.SHAKE ? p.lift() : p.land()) + 15);
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (p.kind() == Level490ReachPayload.THROW) {
            // An arc from its hand to where it lands, in the ticks there are.
            double n = Math.max(10, p.land() - now);
            double vx = (p.tx() - p.fx()) / n;
            double vz = (p.tz() - p.fz()) / n;
            double vy = (p.ty() - p.fy()) / n + 0.5 * G * n;
            THROWS.add(new Throw(new Piece(p.fx(), p.fy(), p.fz(), vx, vy, vz, now, p.land(), 1.6f, 0.15f), p.land(), p.tx(), p.ty(), p.tz()));
        } else if (p.kind() == Level490ReachPayload.SHAKE) {
            shakeFrom = p.land();
            shakeUntil = p.lift();
            gripX = p.fx();
            gripZ = p.fz();
        }
    }

    /** 0..1: how quiet the world has gone (Level490Ambience and Level490WhaleSong turn down by it). */
    public static float hush() {
        return hush;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !Level490Atmosphere.inLevel490()) {
            PIECES.clear();
            THROWS.clear();
            hush = 0;
            return;
        }
        long t = mc.level.getGameTime();
        hush += ((t < hushUntil ? 1f : 0f) - hush) * (t < hushUntil ? 0.12f : 0.03f);
        // Thrown chunks burst where they land.
        THROWS.removeIf(th -> {
            if (t < th.land) {
                return false;
            }
            for (int i = 0; i < 9; i++) {
                PIECES.add(new Piece(th.tx, th.ty + 0.5, th.tz, (RANDOM.nextDouble() - 0.5) * 0.5, 0.15 + RANDOM.nextDouble() * 0.35,
                        (RANDOM.nextDouble() - 0.5) * 0.5, t, t + 40, 0.3f + RANDOM.nextFloat() * 0.4f, RANDOM.nextFloat() * 0.4f));
            }
            return true;
        });
        // While it shakes the tower, masonry falls past from above.
        if (t >= shakeFrom && t <= shakeUntil && t % 4 == 0) {
            Vec3 p = mc.player.position();
            double cx = gripX + (p.x - gripX) * 0.7;
            double cz = gripZ + (p.z - gripZ) * 0.7;
            double ox = p.x - gripX;
            double oz = p.z - gripZ;
            double ol = Math.max(1, Math.hypot(ox, oz));
            PIECES.add(new Piece(cx + (RANDOM.nextDouble() - 0.5) * 8, p.y + 10 + RANDOM.nextDouble() * 10, cz + (RANDOM.nextDouble() - 0.5) * 8,
                    ox / ol * 0.05, 0, oz / ol * 0.05, t, t + 70, 0.3f + RANDOM.nextFloat() * 0.6f, RANDOM.nextFloat() * 0.3f));
        }
        PIECES.removeIf(pc -> t > pc.dies);
    }

    /** The shake: a tremor as it takes hold, then the view lurching about. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Level490Atmosphere.inLevel490()) {
            return;
        }
        double t = mc.level.getGameTime() + event.getPartialTick();
        float amp;
        if (t >= shakeFrom - SHAKE_LEAD && t < shakeFrom) {
            amp = 0.25f * (float) ((t - (shakeFrom - SHAKE_LEAD)) / SHAKE_LEAD);
        } else if (t >= shakeFrom && t <= shakeUntil) {
            float left = (float) ((shakeUntil - t) / (shakeUntil - shakeFrom));
            amp = 1.0f * Math.min(1f, left * 3f);
        } else {
            return;
        }
        double s = t * 0.9;
        event.setPitch(event.getPitch() + amp * 2.5f * (float) (Math.sin(s * 1.7) + 0.5 * Math.sin(s * 3.1 + 1)));
        event.setYaw(event.getYaw() + amp * 1.5f * (float) Math.sin(s * 1.3 + 2));
        event.setRoll(event.getRoll() + amp * 4.0f * (float) (Math.sin(s * 1.1) + 0.4 * Math.sin(s * 2.7)));
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || (PIECES.isEmpty() && THROWS.isEmpty())
                || !Level490Atmosphere.inLevel490()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        float glow = Level490Atmosphere.flash();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Throw th : THROWS) {
            // The chunk: a few blocks of masonry stuck together, tumbling.
            Vector3f c = th.chunk.at(t, cam);
            float a = (float) ((t - th.chunk.born) * th.chunk.spin);
            cube(quads, pose, c, th.chunk.size, a, glow);
            cube(quads, pose, new Vector3f(c).add(rot(0.9f, 0.4f, 0.2f, a)), th.chunk.size * 0.8f, a + 1, glow);
            cube(quads, pose, new Vector3f(c).add(rot(-0.6f, -0.5f, 0.7f, a)), th.chunk.size * 0.7f, a + 2, glow);
        }
        for (Piece pc : PIECES) {
            cube(quads, pose, pc.at(t, cam), pc.size, (float) ((t - pc.born) * pc.spin), glow);
        }
        MeshData mesh = quads.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
    }

    private static Vector3f rot(float x, float y, float z, float a) {
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        return new Vector3f(x * c - z * s, y, x * s + z * c);
    }

    /** A cube of dark stone centred at c (camera-relative), half-size h, turned by a about two axes. */
    private static void cube(BufferBuilder q, Matrix4f pose, Vector3f c, float h, float a, float glow) {
        float ca = (float) Math.cos(a);
        float sa = (float) Math.sin(a);
        float cb = (float) Math.cos(a * 0.7f);
        float sb = (float) Math.sin(a * 0.7f);
        Vector3f ex = new Vector3f(ca, 0, sa).mul(h);
        Vector3f ey = new Vector3f(-sa * sb, cb, ca * sb).mul(h);
        Vector3f ez = new Vector3f(ex).cross(ey).normalize().mul(h);
        float base = 0.11f + 0.25f * glow;
        face(q, pose, c, ex, ey, ez, base * 1.0f);
        face(q, pose, c, new Vector3f(ex).negate(), ey, new Vector3f(ez).negate(), base * 0.7f);
        face(q, pose, c, ez, ey, new Vector3f(ex).negate(), base * 0.85f);
        face(q, pose, c, new Vector3f(ez).negate(), ey, ex, base * 0.8f);
        face(q, pose, c, ey, ez, ex, base * 1.15f);
        face(q, pose, c, new Vector3f(ey).negate(), ex, ez, base * 0.55f);
    }

    /** The face of the cube on the n side, spanned by u and v. */
    private static void face(BufferBuilder q, Matrix4f pose, Vector3f c, Vector3f n, Vector3f u, Vector3f v, float l) {
        Vector3f m = new Vector3f(c).add(n);
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] k : corners) {
            Vector3f p = new Vector3f(m).add(new Vector3f(u).mul(k[0])).add(new Vector3f(v).mul(k[1]));
            q.addVertex(pose, p.x, p.y, p.z).setColor(l, l * 0.98f, l * 1.04f, 1.0f);
        }
    }
}
