package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level227Tree;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Sun over the Void and what circles it, drawn into the far picture each frame (a few dozen
 * camera-facing quads; megaflora_glow.fsh shapes and colours them):
 * <ul>
 *   <li><b>the Sun</b> high above the roots (y 9000 over the trunk): a tiny, blinding white star
 *       inside a wide glare, ringed by a faint iridescent halo, like light through cloud
 *       (the reference photo);</li>
 *   <li><b>the bubbles</b>: a dozen spheres racing round it on tilted orbits, each tied to the
 *       Sun by a hair-thin beam of light with pulses running along it. Each bubble's surface
 *       mirrors another level (Atmos' blurred glimpse panoramas: Level 0's yellow, the
 *       Poolrooms' tiles, Level 9's night, Level 37's cream...);</li>
 *   <li><b>far sunbeams</b>: the deadly shafts beyond the loaded terrain, as soft white columns
 *       (nearer ones are Atmos' own volumetric shafts, {@code Level227Sunbeams}).</li>
 * </ul>
 * <b>The tree moves:</b> the lore says the tree drifts constantly in the void. We ride it, so what
 * we see is the Sun and its bubbles wandering slowly across the sky ({@link #sunX} ...), a couple
 * of degrees over minutes. The far tree itself stays put, so it always lines up with the blocks.
 */
final class Level227Heavens {
    /** Glow kinds (vertex colour alpha); megaflora_glow.fsh decodes them. */
    private static final int KIND_GLARE = 10;
    private static final int KIND_HALO = 20;
    private static final int KIND_CORE = 30;
    private static final int KIND_BEAM = 40;
    private static final int KIND_BUBBLE = 50;
    private static final int KIND_SHAFT = 60;

    /**
     * Radius of the Sun's white disc (blocks): about one degree from the branches, small but
     * unmistakable, a star too bright to look at.
     */
    static final float SUN_RADIUS = 150.0f;
    /** Quad half-sizes (blocks, at the Sun): glare, halo, the star itself (its disc fills 0.34 of the quad). */
    private static final float GLARE_SIZE = 4200.0f;
    private static final float HALO_SIZE = 2400.0f;
    private static final float CORE_SIZE = SUN_RADIUS / 0.34f;
    /** Far sunbeams: from where Atmos' near shafts end, fading by e every SHAFT_FADE blocks, none past SHAFT_REACH, at most this many. */
    private static final double SHAFT_FROM = 300.0;
    private static final double SHAFT_FADE = 800.0;
    private static final double SHAFT_REACH = 2400.0;
    private static final int SHAFTS_SHOWN = 5;
    /** The Sun's slow wander (blocks) and how long its swings take (seconds). */
    private static final double DRIFT_SIDE = 230.0;
    private static final double DRIFT_UP = 90.0;
    /** Beams stay about this many radians wide on screen (~2 pixels), however far they are. */
    private static final float BEAM_ANGLE = 0.0011f;
    private static final float BEAM_MIN = 5.0f;

    private static final ResourceLocation[] GLIMPSES = {
            Backrooms.id("textures/entity/void_bubble/level_0.png"),
            Backrooms.id("textures/entity/void_bubble/poolrooms.png"),
            Backrooms.id("textures/entity/void_bubble/level_9.png"),
            Backrooms.id("textures/entity/void_bubble/level_37.png"),
            Backrooms.id("textures/entity/void_bubble/level_94.png"),
            Backrooms.id("textures/entity/void_bubble/level_39.png"),
    };

    /** One bubble: its orbit (radius, tilt, node), where it starts, seconds per lap, size, which level. */
    private record Bubble(double orbit, double tilt, double node, double phase, double period, double radius, int glimpse) {}

    private static final Bubble[] BUBBLES = new Bubble[14];

    static {
        Random rnd = new Random(0x227_5011L);
        for (int i = 0; i < BUBBLES.length; i++) {
            // "Moves very fast": a lap every 14-48 s at 1300-3400 blocks out (hundreds of blocks a second).
            BUBBLES[i] = new Bubble(1300.0 + rnd.nextDouble() * 2100.0, Math.toRadians(rnd.nextDouble() * 70.0),
                    rnd.nextDouble() * Math.PI * 2.0, rnd.nextDouble() * Math.PI * 2.0, 14.0 + rnd.nextDouble() * 34.0,
                    150.0 + rnd.nextDouble() * 170.0, i % GLIMPSES.length);
        }
    }

    /** Bottom of each far sunbeam (the ground under it), worked out once. */
    private static double[] beamBottom;
    /** Scratch: each beam's distance this frame (-1 once drawn). */
    private static double[] beamDistance;

    private Level227Heavens() {}

    static double sunX(double seconds) {
        return Level227Tree.SUN_X + DRIFT_SIDE * Math.sin(seconds * 0.0131);
    }

    static double sunY(double seconds) {
        return Level227Tree.SUN_Y + DRIFT_UP * Math.sin(seconds * 0.0077);
    }

    static double sunZ(double seconds) {
        return Level227Tree.SUN_Z + DRIFT_SIDE * Math.cos(seconds * 0.0093);
    }

    /**
     * Draws the heavens into the far picture, which is bound with the tree already in it (so the
     * roots hide the Sun where they cross it). {@code glow} has its uniforms set by the caller.
     */
    static void draw(ShaderInstance glow, Matrix4f view, Matrix4f projection, Camera camera, double seconds, float loaded) {
        Vec3 eye = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float sx = (float) (sunX(seconds) - eye.x);
        float sy = (float) (sunY(seconds) - eye.y);
        float sz = (float) (sunZ(seconds) - eye.z);

        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        billboard(quads, left, up, sx, sy, sz, GLARE_SIZE, KIND_GLARE, 255, 255, 255);
        billboard(quads, left, up, sx, sy, sz, HALO_SIZE, KIND_HALO, (int) ((seconds * 7.0) % 255.0), 0, 0);
        for (int i = 0; i < BUBBLES.length; i++) {
            Vector3f b = bubble(BUBBLES[i], seconds, eye);
            ribbon(quads, sx, sy, sz, b.x, b.y, b.z, 0.0f, KIND_BEAM, i * 37 % 255, 0, 0);
        }
        billboard(quads, left, up, sx, sy, sz, CORE_SIZE, KIND_CORE, 255, 255, 255);
        shafts(quads, eye, loaded);
        draw(glow, quads.build(), view, projection);

        for (int g = 0; g < GLIMPSES.length; g++) {
            BufferBuilder spheres = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < BUBBLES.length; i++) {
                if (BUBBLES[i].glimpse() == g) {
                    Vector3f b = bubble(BUBBLES[i], seconds, eye);
                    // Red carries a per-bubble offset so two bubbles of one level don't look alike.
                    billboard(spheres, left, up, b.x, b.y, b.z, (float) BUBBLES[i].radius(), KIND_BUBBLE, i * 53 % 255, 0, 0);
                }
            }
            MeshData mesh = spheres.build();
            if (mesh != null) {
                RenderSystem.setShaderTexture(0, GLIMPSES[g]);
                draw(glow, mesh, view, projection);
            }
        }
    }

    private static void draw(ShaderInstance glow, MeshData mesh, Matrix4f view, Matrix4f projection) {
        if (mesh == null) {
            return;
        }
        glow.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, Minecraft.getInstance().getWindow());
        glow.apply();
        BufferUploader.draw(mesh);
        glow.clear();
    }

    /** Where a bubble is now, relative to the camera. */
    private static Vector3f bubble(Bubble b, double seconds, Vec3 eye) {
        double a = b.phase() + Math.PI * 2.0 * seconds / b.period();
        double px = b.orbit() * Math.cos(a);
        double py = b.orbit() * Math.sin(a) * Math.sin(b.tilt());
        double pz = b.orbit() * Math.sin(a) * Math.cos(b.tilt());
        double x = px * Math.cos(b.node()) - pz * Math.sin(b.node());
        double z = px * Math.sin(b.node()) + pz * Math.cos(b.node());
        return new Vector3f((float) (sunX(seconds) + x - eye.x), (float) (sunY(seconds) + py - eye.y),
                (float) (sunZ(seconds) + z - eye.z));
    }

    /**
     * The sunbeams beyond Atmos' near shafts (they draw up to ~320 blocks away): soft, wide glows of
     * light standing in the mist, the more faded the further they are, and only the few nearest
     * (from afar a crowd of thin bright lines would look like rain).
     */
    private static void shafts(BufferBuilder quads, Vec3 eye, float loaded) {
        List<Level227Tree.Beam> beams = Level227Tree.beams();
        if (beamBottom == null || beamBottom.length != beams.size()) {
            beamBottom = new double[beams.size()];
            beamDistance = new double[beams.size()];
            for (int i = 0; i < beams.size(); i++) {
                int ground = Level227Tree.topSurfaceY(beams.get(i).x(), beams.get(i).z());
                beamBottom[i] = ground == Integer.MIN_VALUE ? beams.get(i).topY() - 200.0 : ground;
            }
        }
        for (int i = 0; i < beams.size(); i++) {
            beamDistance[i] = Math.hypot(beams.get(i).x() - eye.x, beams.get(i).z() - eye.z);
        }
        for (int n = 0; n < SHAFTS_SHOWN; n++) {
            // The nearest beam not drawn yet (a couple of dozen beams: a plain search).
            int best = -1;
            for (int i = 0; i < beams.size(); i++) {
                if (beamDistance[i] >= 0.0 && (best < 0 || beamDistance[i] < beamDistance[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                return;
            }
            double d = beamDistance[best];
            beamDistance[best] = -1.0;
            float fade = smooth((float) ((d - Math.max(SHAFT_FROM, loaded)) / 120.0))
                    * (float) Math.exp(-Math.max(0.0, d - SHAFT_FROM) / SHAFT_FADE);
            if (d > SHAFT_REACH || fade < 0.02f) {
                continue;
            }
            Level227Tree.Beam b = beams.get(best);
            int bright = Math.round(255.0f * fade);
            ribbon(quads, (float) (b.x() - eye.x), (float) (b.topY() - eye.y), (float) (b.z() - eye.z),
                    (float) (b.x() - eye.x), (float) (beamBottom[best] - eye.y), (float) (b.z() - eye.z),
                    (float) (b.radius() * 4.0 + d * 0.012), KIND_SHAFT, bright, bright, bright);
        }
    }

    /** A camera-facing square round (x, y, z), uv -1..1. */
    private static void billboard(BufferBuilder quads, Vector3f left, Vector3f up, float x, float y, float z, float size,
                                  int kind, int r, int g, int b) {
        float lx = left.x * size;
        float ly = left.y * size;
        float lz = left.z * size;
        float ux = up.x * size;
        float uy = up.y * size;
        float uz = up.z * size;
        // "left" points left, so -left is +u.
        quads.addVertex(x + lx - ux, y + ly - uy, z + lz - uz).setUv(-1.0f, -1.0f).setColor(r, g, b, kind);
        quads.addVertex(x - lx - ux, y - ly - uy, z - lz - uz).setUv(1.0f, -1.0f).setColor(r, g, b, kind);
        quads.addVertex(x - lx + ux, y - ly + uy, z - lz + uz).setUv(1.0f, 1.0f).setColor(r, g, b, kind);
        quads.addVertex(x + lx + ux, y + ly + uy, z + lz + uz).setUv(-1.0f, 1.0f).setColor(r, g, b, kind);
    }

    /**
     * A flat strip from point 0 to point 1 turned to face the camera; uv.x runs -1..1 across,
     * uv.y 0..1 along. {@code halfWidth} 0 = keep it about two pixels wide at any distance.
     */
    private static void ribbon(BufferBuilder quads, float x0, float y0, float z0, float x1, float y1, float z1,
                               float halfWidth, int kind, int r, int g, int b) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float mx = (x0 + x1) * 0.5f;
        float my = (y0 + y1) * 0.5f;
        float mz = (z0 + z1) * 0.5f;
        float cx = dy * mz - dz * my;
        float cy = dz * mx - dx * mz;
        float cz = dx * my - dy * mx;
        float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1e-3f) {
            return;
        }
        float w = halfWidth > 0.0f ? halfWidth : Math.max(BEAM_MIN, Mth.sqrt(mx * mx + my * my + mz * mz) * BEAM_ANGLE);
        cx *= w / len;
        cy *= w / len;
        cz *= w / len;
        quads.addVertex(x0 - cx, y0 - cy, z0 - cz).setUv(-1.0f, 0.0f).setColor(r, g, b, kind);
        quads.addVertex(x0 + cx, y0 + cy, z0 + cz).setUv(1.0f, 0.0f).setColor(r, g, b, kind);
        quads.addVertex(x1 + cx, y1 + cy, z1 + cz).setUv(1.0f, 1.0f).setColor(r, g, b, kind);
        quads.addVertex(x1 - cx, y1 - cy, z1 - cz).setUv(-1.0f, 1.0f).setColor(r, g, b, kind);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
