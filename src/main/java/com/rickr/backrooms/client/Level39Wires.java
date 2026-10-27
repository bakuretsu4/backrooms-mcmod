package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level39Layout;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The overhead wires between Level 39's utility poles: thin dark conductors sagging from crossarm
 * to crossarm down the dirt paths, swaying very slightly (more while the ground quakes). In the
 * glow near the facades they stand out as black threads against the lit fog, like in the photo.
 *
 * <p>The poles come from the layout ({@link Level39Layout#polesNear}): each pole tells us the next
 * pole its wires run to. The spans around the camera are looked up once whenever the camera moves
 * into a new {@value #REGION}-block region and kept as plain numbers; each frame they are drawn
 * as a few hundred line segments (Minecraft's own line shader, so the terrain fog fades them
 * with distance like everything else). Not drawn in the tunnels or before the seed has arrived.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39Wires {
    /** The span list is rebuilt when the camera enters a new region of this size (blocks) ... */
    private static final int REGION = 64;
    /** ... and holds the poles this far around the region's centre. */
    private static final int RADIUS = 160;
    /** The four insulators along the crossarm, from the crossarm block's west/north edge (Level39Layout.Pole). */
    private static final double[] INSULATORS = {-0.7, -0.2, 1.2, 1.7};
    /** Insulator tops sit this far above the crossarm block's bottom. */
    private static final double INSULATOR_Y = 0.9;
    /** Sag at mid-span: a fixed part plus a share of the span length (a 28-block span sags ~1.2). */
    private static final double SAG_BASE = 0.2;
    private static final double SAG_PER_BLOCK = 0.035;
    /** Each wire is drawn as this many straight pieces. */
    private static final int PIECES = 12;
    /** Sideways sway at mid-span (blocks), and how much more while the ground quakes. */
    private static final double SWAY = 0.06;
    private static final double QUAKE_SWAY = 0.45;
    /** Wire colour: near-black. */
    private static final float WIRE_R = 0.035f;
    private static final float WIRE_G = 0.035f;
    private static final float WIRE_B = 0.04f;
    /** Line width on a 1920-pixel-wide window (scaled with the window). */
    private static final float LINE_WIDTH = 1.6f;

    /** Cached spans: 6 numbers each (start x, y, z, end x, y, z), one per conductor. */
    private static final DoubleArrayList SPANS = new DoubleArrayList();
    private static long cachedRegion = Long.MIN_VALUE;
    private static long cachedSeed;

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !Level39ClientState.inLevel39() || !Level39ClientState.hasSeed()) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        if (Level39ClientState.inTunnels(eye)) {
            return;
        }
        refresh(eye);
        if (SPANS.isEmpty()) {
            return;
        }
        draw(event.getModelViewMatrix(), eye);
    }

    /** Looks the poles up again if the camera entered another region (or the seed changed). */
    private static void refresh(Vec3 eye) {
        int rx = Math.floorDiv(Mth.floor(eye.x), REGION);
        int rz = Math.floorDiv(Mth.floor(eye.z), REGION);
        long region = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
        long seed = Level39ClientState.seed();
        if (region == cachedRegion && seed == cachedSeed) {
            return;
        }
        cachedRegion = region;
        cachedSeed = seed;
        SPANS.clear();
        int cx = rx * REGION + REGION / 2;
        int cz = rz * REGION + REGION / 2;
        Level39Layout.polesNear(seed, cx, cz, RADIUS, pole -> {
            if (!pole.linked()) {
                return;
            }
            for (double offset : INSULATORS) {
                addInsulator(pole.armAxis(), pole.x(), pole.y(), pole.z(), offset);
                addInsulator(pole.armAxis(), pole.linkX(), pole.linkY(), pole.linkZ(), offset);
            }
        });
    }

    /** One end of a conductor: the insulator {@code offset} along the crossarm of the pole at (x, y, z). */
    private static void addInsulator(Direction.Axis arm, int x, int y, int z, double offset) {
        boolean alongX = arm == Direction.Axis.X;
        SPANS.add(alongX ? x + offset : x + 0.5);
        SPANS.add(y + INSULATOR_Y);
        SPANS.add(alongX ? z + 0.5 : z + offset);
    }

    private static void draw(Matrix4f view, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        double sway = SWAY + QUAKE_SWAY * Level39QuakeEffects.shaking();
        BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int i = 0; i + 5 < SPANS.size(); i += 6) {
            double x0 = SPANS.getDouble(i) - eye.x;
            double y0 = SPANS.getDouble(i + 1) - eye.y;
            double z0 = SPANS.getDouble(i + 2) - eye.z;
            double x1 = SPANS.getDouble(i + 3) - eye.x;
            double y1 = SPANS.getDouble(i + 4) - eye.y;
            double z1 = SPANS.getDouble(i + 5) - eye.z;
            double dx = x1 - x0;
            double dz = z1 - z0;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0e-3) {
                continue;
            }
            double sag = SAG_BASE + SAG_PER_BLOCK * length;
            // Sideways (horizontal, across the span) for the sway; each span sways in its own time.
            double sideX = -dz / length;
            double sideZ = dx / length;
            double phase = (SPANS.getDouble(i) * 0.37 + SPANS.getDouble(i + 2) * 0.61) % Mth.TWO_PI;
            double swing = sway * Math.sin(seconds * 0.9 + phase) + sway * 0.4 * Math.sin(seconds * 2.3 + phase * 1.7);
            float px = 0;
            float py = 0;
            float pz = 0;
            for (int s = 0; s <= PIECES; s++) {
                double u = s / (double) PIECES;
                double bow = 4.0 * u * (1.0 - u); // 0 at the poles, 1 at mid-span
                float x = (float) (x0 + dx * u + sideX * swing * bow);
                float y = (float) (y0 + (y1 - y0) * u - sag * bow);
                float z = (float) (z0 + dz * u + sideZ * swing * bow);
                if (s > 0) {
                    float nx = x - px;
                    float ny = y - py;
                    float nz = z - pz;
                    float inv = Mth.invSqrt(nx * nx + ny * ny + nz * nz + 1.0e-9f);
                    nx *= inv;
                    ny *= inv;
                    nz *= inv;
                    // Camera-relative world positions; the line shader needs the direction (normal)
                    // in the same space, so the view rotation goes on the model-view stack below.
                    lines.addVertex(px, py, pz).setColor(WIRE_R, WIRE_G, WIRE_B, 1.0f).setNormal(nx, ny, nz);
                    lines.addVertex(x, y, z).setColor(WIRE_R, WIRE_G, WIRE_B, 1.0f).setNormal(nx, ny, nz);
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        MeshData mesh = lines.build();
        if (mesh == null) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.lineWidth(Math.max(1.0f, LINE_WIDTH * mc.getWindow().getWidth() / 1920.0f));
        BufferUploader.drawWithShader(mesh);
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private Level39Wires() {}
}
