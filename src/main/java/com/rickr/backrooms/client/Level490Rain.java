package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 490's rain, our own (after the play-test: "too vanilla looking, less heavy"): thin
 * translucent streaks round you, slanted by a wind that slowly turns and gusts, each falling to the
 * ground under it (none under roofs) and splashing there; and far off, pale sheets of rain drifting
 * across the islands. Lit like the level: dim blue-grey, white in a lightning flash. How hard it
 * rains comes from {@link Level490Weather}; Minecraft's own rain is kept off here.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Rain {
    private static final ResourceLocation STREAK = Backrooms.id("textures/environment/l490_rain_streak.png");
    private static final ResourceLocation SHEET = Backrooms.id("textures/environment/l490_rain_sheet.png");
    /** Drops round you (all of them at full strength; fewer when it eases). */
    private static final int DROPS = 1300;
    private static final float BOX = 26.0f;
    private static final float ABOVE = 20.0f;
    private static final float BELOW = 18.0f;
    /** Falling speed, blocks a second. */
    private static final float FALL = 34.0f;
    private static final int SPLASHES = 160;
    private static final int SHEETS = 9;

    private static final float[] X = new float[DROPS];
    private static final float[] Y = new float[DROPS];
    private static final float[] Z = new float[DROPS];
    private static final float[] LEN = new float[DROPS];
    private static final float[] SHADE = new float[DROPS];
    private static final float[] FLOOR = new float[DROPS];
    private static final float[][] SPLASH = new float[SPLASHES][];
    private static int nextSplash;
    private static final Random RANDOM = new Random();
    private static boolean seeded;
    private static long lastNs;
    private static double baseX;
    private static double baseZ;

    private Level490Rain() {}

    /** The wind: {x per block fallen, z per block fallen} slant, slowly turning, gusting. */
    private static float[] wind(double seconds) {
        double heading = Math.toRadians(35 + 40 * Math.sin(seconds / 47.0));
        double gust = 0.5 + 0.5 * Math.sin(seconds / 6.3) * Math.sin(seconds / 11.7 + 1.0);
        double slant = 0.12 + 0.28 * gust;
        return new float[] {(float) (Math.cos(heading) * slant), (float) (Math.sin(heading) * slant)};
    }

    private static void respawn(int i, Vec3 cam, ClientLevel level, boolean anywhere) {
        X[i] = (float) (cam.x - baseX + (RANDOM.nextFloat() * 2 - 1) * BOX);
        Z[i] = (float) (cam.z - baseZ + (RANDOM.nextFloat() * 2 - 1) * BOX);
        Y[i] = (float) (cam.y + (anywhere ? (RANDOM.nextFloat() * (ABOVE + BELOW) - BELOW) : ABOVE));
        LEN[i] = 1.0f + RANDOM.nextFloat() * 1.6f;
        SHADE[i] = 0.7f + RANDOM.nextFloat() * 0.3f;
        int wx = (int) Math.floor(X[i] + baseX);
        int wz = (int) Math.floor(Z[i] + baseZ);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
        FLOOR[i] = top <= level.getMinBuildHeight() ? Float.NEGATIVE_INFINITY : top;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !Level490Atmosphere.inLevel490()) {
            seeded = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        float strength = Level490Weather.rain();
        if (level == null || strength <= 0.01f) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        if (mc.isPaused()) {
            dt = 0;
        }
        if (!seeded || Math.abs(cam.x - baseX) > 2000 || Math.abs(cam.z - baseZ) > 2000) {
            // Positions are kept relative to a base near the camera (floats lose precision far out).
            baseX = Math.floor(cam.x);
            baseZ = Math.floor(cam.z);
            for (int i = 0; i < DROPS; i++) {
                respawn(i, cam, level, true);
            }
            for (int i = 0; i < SPLASHES; i++) {
                SPLASH[i] = null;
            }
            seeded = true;
        }
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        float[] w = wind(seconds);
        float flash = Level490Atmosphere.flash();
        int active = (int) (DROPS * strength);
        float cx = (float) (cam.x - baseX);
        float cz = (float) (cam.z - baseZ);
        for (int i = 0; i < active; i++) {
            float fall = FALL * dt;
            Y[i] -= fall;
            X[i] += w[0] * fall;
            Z[i] += w[1] * fall;
            if (Y[i] < FLOOR[i]) {
                // A splash where it hits, if near.
                if (Math.abs(X[i] - cx) < 14 && Math.abs(Z[i] - cz) < 14) {
                    SPLASH[nextSplash] = new float[] {X[i], FLOOR[i] + 0.02f, Z[i], 0};
                    nextSplash = (nextSplash + 1) % SPLASHES;
                }
                respawn(i, cam, level, false);
            } else if (Y[i] < cam.y - BELOW || Math.abs(X[i] - cx) > BOX || Math.abs(Z[i] - cz) > BOX) {
                respawn(i, cam, level, false);
            }
        }
        Matrix4f pose = event.getPoseStack().last().pose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        // The streaks: each a thin quad along its fall, turned to face you.
        RenderSystem.setShaderTexture(0, STREAK);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float ox = (float) (baseX - cam.x);
        float oz = (float) (baseZ - cam.z);
        float oy = (float) -cam.y;
        Vector3f along = new Vector3f(w[0], -1, w[1]).normalize();
        Vector3f side = new Vector3f();
        float lum = 0.52f + 0.6f * flash;
        for (int i = 0; i < active; i++) {
            float px = X[i] + ox;
            float py = Y[i] + oy;
            float pz = Z[i] + oz;
            float d2 = px * px + py * py + pz * pz;
            if (d2 < 0.8f) {
                continue;
            }
            // Across the streak: perpendicular to its direction and to the line of sight.
            along.cross(px, py, pz, side);
            float sl = side.length();
            if (sl < 1e-4f) {
                continue;
            }
            float width = 0.022f + 0.0025f * (float) Math.sqrt(d2);
            side.mul(width / sl);
            float l = LEN[i];
            float a = 0.28f * SHADE[i] * Math.min(1.0f, d2 / 9.0f);
            float r = lum * 0.86f;
            float g = lum * 0.9f;
            float b = lum;
            float tx = px - along.x * l;
            float ty = py - along.y * l;
            float tz = pz - along.z * l;
            quads.addVertex(pose, px - side.x, py - side.y, pz - side.z).setUv(0, 1).setColor(r, g, b, a);
            quads.addVertex(pose, px + side.x, py + side.y, pz + side.z).setUv(1, 1).setColor(r, g, b, a);
            quads.addVertex(pose, tx + side.x, ty + side.y, tz + side.z).setUv(1, 0).setColor(r, g, b, a);
            quads.addVertex(pose, tx - side.x, ty - side.y, tz - side.z).setUv(0, 0).setColor(r, g, b, a);
        }
        // The splashes: small rings spreading and fading on the stone.
        for (int i = 0; i < SPLASHES; i++) {
            float[] sp = SPLASH[i];
            if (sp == null) {
                continue;
            }
            sp[3] += dt;
            if (sp[3] > 0.22f) {
                SPLASH[i] = null;
                continue;
            }
            float f = sp[3] / 0.22f;
            float rr = 0.05f + 0.22f * f;
            float a = 0.35f * (1 - f);
            float px = sp[0] + ox;
            float py = sp[1] + oy;
            float pz = sp[2] + oz;
            quads.addVertex(pose, px - rr, py, pz - rr).setUv(0, 0.5f).setColor(lum, lum, lum, a);
            quads.addVertex(pose, px + rr, py, pz - rr).setUv(1, 0.5f).setColor(lum, lum, lum, a);
            quads.addVertex(pose, px + rr, py, pz + rr).setUv(1, 0.6f).setColor(lum, lum, lum, a);
            quads.addVertex(pose, px - rr, py, pz + rr).setUv(0, 0.6f).setColor(lum, lum, lum, a);
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        // Far off: sheets of rain drifting across, each a tall faint curtain facing you.
        RenderSystem.setShaderTexture(0, SHEET);
        BufferBuilder sheets = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int k = 0; k < SHEETS; k++) {
            double heading = k * 2.399 + seconds * 0.012 * (k % 2 == 0 ? 1 : -1);
            double dist = 70 + 22 * k;
            float sx = (float) (Math.cos(heading) * dist);
            float sz = (float) (Math.sin(heading) * dist);
            float half = 30 + 6 * (k % 3);
            float height = 140;
            float bottom = (float) (Math.max(150, cam.y - 60) - cam.y);
            float lx = -sz / (float) dist;
            float lz = sx / (float) dist;
            float pulse = (float) (0.5 + 0.5 * Math.sin(seconds * 0.15 + k * 1.7));
            float a = (0.06f + 0.06f * pulse) * strength;
            float scroll = (float) ((seconds * 1.6) % 1.0);
            float slant = w[0] * lx + w[1] * lz;
            float topShift = slant * height;
            float c = 0.55f + 0.5f * flash;
            sheets.addVertex(pose, sx - lx * half, bottom, sz - lz * half).setUv(0, 1 + scroll).setColor(c, c, c * 1.08f, a);
            sheets.addVertex(pose, sx + lx * half, bottom, sz + lz * half).setUv(1, 1 + scroll).setColor(c, c, c * 1.08f, a);
            sheets.addVertex(pose, sx + lx * (half - topShift), bottom + height, sz + lz * (half - topShift)).setUv(1, scroll).setColor(c, c, c * 1.08f, 0);
            sheets.addVertex(pose, sx - lx * (half + topShift), bottom + height, sz - lz * (half + topShift)).setUv(0, scroll).setColor(c, c, c * 1.08f, 0);
        }
        BufferUploader.drawWithShader(sheets.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
