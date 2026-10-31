package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490StrikePayload;
import com.rickr.backrooms.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 490's lightning (client side), from the strikes the server picks (world/Level490Storm).
 * <ul>
 *   <li><b>The bolt:</b> a jagged channel from the storm's underside down into the cloud sea (or
 *       onto a colossal chain), split again and again into a crooked line with forks, the same on
 *       every client (built from the strike's seed). It flickers: two to four return strokes a few
 *       dozen milliseconds apart, then a fading afterglow.</li>
 *   <li><b>Its light:</b> the clouds glow around the strike, the sky and the fog brighten, and a
 *       close strike lights the whole level for an instant (the lightmap and the camcorder).</li>
 *   <li><b>Thunder:</b> sound covers 343 blocks a second, so it comes as late as the strike is
 *       far: a close strike cracks and roars a moment later, a far one rolls in seconds after,
 *       from its direction. Lightning inside the clouds rumbles without a bolt.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Lightning {
    /** The storm's underside, where the bolts come out, and the cloud sea they vanish into. */
    private static final float BOLT_TOP = 600.0f;
    private static final float BOLT_BOTTOM = 120.0f;
    /** The speed of sound, blocks per second (a block is a metre). */
    private static final double SOUND_SPEED = 343.0;
    /** How long a strike shows at all (ms). */
    private static final long LIFE_MS = 1600;

    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final List<Thunder> THUNDERS = new ArrayList<>();

    /** One strike: where, when, its flicker and (for bolts) the channel's segments. */
    private static final class Strike {
        final int x;
        final int z;
        final int kind;
        final long startMs;
        final long[] strokeMs;
        final float[] strokePower;
        /** x0 y0 z0 x1 y1 z1 width brightness, per segment (world coordinates). */
        final List<float[]> segments = new ArrayList<>();

        Strike(Level490StrikePayload p, long now) {
            this.x = p.x();
            this.z = p.z();
            this.kind = p.kind();
            this.startMs = now;
            Random r = new Random(p.seed());
            int strokes = 2 + r.nextInt(3);
            strokeMs = new long[strokes];
            strokePower = new float[strokes];
            long t = 0;
            for (int k = 0; k < strokes; k++) {
                strokeMs[k] = t;
                strokePower[k] = k == 0 ? 1.0f : 0.45f + 0.5f * r.nextFloat();
                t += 50 + r.nextInt(140);
            }
            if (kind != Level490StrikePayload.CLOUD) {
                float bottom = kind == Level490StrikePayload.CHAIN ? 420.0f + r.nextInt(60) : BOLT_BOTTOM;
                float sx = x + (kind == Level490StrikePayload.CHAIN ? (r.nextFloat() - 0.5f) * 240 : (r.nextFloat() - 0.5f) * 160);
                float sz = z + (kind == Level490StrikePayload.CHAIN ? (r.nextFloat() - 0.5f) * 240 : (r.nextFloat() - 0.5f) * 160);
                channel(r, new Vector3f(sx, BOLT_TOP, sz), new Vector3f(x, bottom, z), 7.0f, 1.0f, 9);
            }
        }

        /**
         * A crooked line from a to b: split at the middle, the middle pushed sideways by a share of
         * the length, both halves split again (depth times); now and then a fork leaves a midpoint.
         */
        private void channel(Random r, Vector3f a, Vector3f b, float width, float bright, int depth) {
            if (depth == 0) {
                segments.add(new float[] {a.x, a.y, a.z, b.x, b.y, b.z, width, bright});
                return;
            }
            float len = a.distance(b);
            Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
            mid.add((r.nextFloat() - 0.5f) * len * 0.42f, (r.nextFloat() - 0.5f) * len * 0.12f, (r.nextFloat() - 0.5f) * len * 0.42f);
            channel(r, a, mid, width, bright, depth - 1);
            channel(r, mid, b, width, bright, depth - 1);
            if (depth >= 2 && depth <= 7 && r.nextFloat() < 0.45f) {
                // A fork: off to the side and downward, thinner and dimmer, dying out.
                Vector3f dir = new Vector3f(b).sub(a);
                Vector3f end = new Vector3f(mid).add(dir.x * 0.7f + (r.nextFloat() - 0.5f) * len * 0.9f,
                        dir.y * (0.4f + 0.5f * r.nextFloat()), dir.z * 0.7f + (r.nextFloat() - 0.5f) * len * 0.9f);
                channel(r, mid, end, width * 0.5f, bright * 0.55f, depth - 2);
            }
        }

        /** 0..1: how brightly it is shining at time {@code now}. */
        float intensity(long now) {
            long t = now - startMs;
            float best = 0.0f;
            for (int k = 0; k < strokeMs.length; k++) {
                long s = t - strokeMs[k];
                if (s < 0) {
                    continue;
                }
                float v = s < 45 ? 1.0f : (float) Math.exp(-(s - 45) / 70.0);
                best = Math.max(best, v * strokePower[k]);
            }
            float after = t >= 0 ? 0.12f * (float) Math.exp(-t / 450.0) : 0.0f;
            return Math.max(best, after);
        }
    }

    private record Thunder(long atMs, SoundEvent sound, float volume, float pitch, double dirX, double dirZ) {}

    private Level490Lightning() {}

    /** Network (main thread): a strike. */
    public static void receive(Level490StrikePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Level490Atmosphere.inLevel490()) {
            return;
        }
        if (payload.kind() == Level490StrikePayload.JOLT) {
            Level490ChainEvents.jolt(payload);
            return;
        }
        long now = System.currentTimeMillis();
        STRIKES.add(new Strike(payload, now));
        Vec3 eye = mc.player.getEyePosition();
        double dx = payload.x() - eye.x;
        double dz = payload.z() - eye.z;
        double d = Math.hypot(dx, dz);
        boolean cloud = payload.kind() == Level490StrikePayload.CLOUD;
        // Close strikes crack and roar, middling ones roll, far ones only rumble.
        SoundEvent sound = (cloud ? d < 900 ? ModSounds.LEVEL490_THUNDER_ROLL : ModSounds.LEVEL490_THUNDER_FAR
                : d < 450 ? ModSounds.LEVEL490_THUNDER_CLOSE : d < 1300 ? ModSounds.LEVEL490_THUNDER_ROLL : ModSounds.LEVEL490_THUNDER_FAR).get();
        float volume = (float) Math.max(0.3, Math.min(1.0, 1.25 - d / 2600.0)) * (cloud ? 0.75f : 1.0f);
        float pitch = 0.9f + new Random(payload.seed()).nextFloat() * 0.2f;
        long delay = (long) (d / SOUND_SPEED * 1000.0);
        THUNDERS.add(new Thunder(now + delay, sound, volume, pitch, dx / Math.max(1, d), dz / Math.max(1, d)));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        STRIKES.removeIf(s -> now - s.startMs > LIFE_MS);
        if (mc.player == null || !Level490Atmosphere.inLevel490()) {
            THUNDERS.clear();
            return;
        }
        Iterator<Thunder> it = THUNDERS.iterator();
        while (it.hasNext()) {
            Thunder t = it.next();
            if (now >= t.atMs) {
                it.remove();
                // From the strike's direction: the sound sits a few blocks off that way.
                Vec3 eye = mc.player.getEyePosition();
                mc.getSoundManager().play(new SimpleSoundInstance(t.sound, SoundSource.WEATHER, t.volume, t.pitch,
                        RandomSource.create(), eye.x + t.dirX * 10, eye.y + 4, eye.z + t.dirZ * 10));
            }
        }
    }

    /**
     * 0..1: how much the strikes light the level up right now: a close bolt fully, a far one or
     * lightning inside the clouds only a little (for the lightmap, the fog and the camcorder).
     */
    public static float flash() {
        Minecraft mc = Minecraft.getInstance();
        if (STRIKES.isEmpty() || mc.player == null) {
            return 0.0f;
        }
        long now = System.currentTimeMillis();
        Vec3 eye = mc.player.getEyePosition();
        float best = 0.0f;
        for (Strike s : STRIKES) {
            double d = Math.hypot(s.x - eye.x, s.z - eye.z);
            float near = (float) Math.max(0.12, Math.min(1.0, 380.0 / Math.max(1.0, d)));
            float kind = s.kind == Level490StrikePayload.CLOUD ? 0.55f : 1.0f;
            best = Math.max(best, s.intensity(now) * near * kind);
        }
        return best;
    }

    /** How much lightning lights the cloud at world (x, z) right now (0..~1). */
    static float cloudGlow(double x, double z) {
        if (STRIKES.isEmpty()) {
            return 0.0f;
        }
        long now = System.currentTimeMillis();
        float sum = 0.0f;
        for (Strike s : STRIKES) {
            double reach = s.kind == Level490StrikePayload.CLOUD ? 800.0 : 550.0;
            double dx = x - s.x;
            double dz = z - s.z;
            sum += s.intensity(now) * (float) Math.exp(-(dx * dx + dz * dz) / (2 * reach * reach));
        }
        return Math.min(1.2f, sum);
    }

    /**
     * Draws the bolts in the sky pass: each segment a ribbon facing the camera, a thin white-blue
     * core and a wide faint glow, added onto the sky. {@code scale} shrinks everything toward the
     * camera to fit inside the far plane (as the clouds are).
     */
    static void render(Matrix4f view, Vec3 eye, float scale) {
        if (STRIKES.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Strike s : STRIKES) {
            float in = s.intensity(now);
            if (in < 0.02f || s.segments.isEmpty()) {
                continue;
            }
            for (float[] seg : s.segments) {
                // Glow first, then the core over it.
                any |= ribbon(quads, view, eye, scale, seg, seg[6] * 6.0f, 0.55f, 0.6f, 0.95f, 0.22f * in * seg[7]);
                any |= ribbon(quads, view, eye, scale, seg, seg[6], 0.9f, 0.93f, 1.0f, in * seg[7]);
            }
        }
        if (any) {
            BufferUploader.drawWithShader(quads.buildOrThrow());
        } else {
            quads.build();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean ribbon(BufferBuilder quads, Matrix4f view, Vec3 eye, float scale, float[] seg, float width,
                                  float r, float g, float b, float a) {
        if (a < 0.01f) {
            return false;
        }
        Vector3f p0 = new Vector3f((float) (seg[0] - eye.x), (float) (seg[1] - eye.y), (float) (seg[2] - eye.z));
        Vector3f p1 = new Vector3f((float) (seg[3] - eye.x), (float) (seg[4] - eye.y), (float) (seg[5] - eye.z));
        Vector3f dir = new Vector3f(p1).sub(p0);
        Vector3f toEye = new Vector3f(p0).add(p1).mul(-0.5f);
        Vector3f side = dir.cross(toEye, new Vector3f());
        if (side.lengthSquared() < 1e-6f) {
            return false;
        }
        side.normalize().mul(width * 0.5f);
        p0.mul(scale);
        p1.mul(scale);
        side.mul(scale);
        quads.addVertex(view, p0.x - side.x, p0.y - side.y, p0.z - side.z).setColor(r, g, b, a);
        quads.addVertex(view, p0.x + side.x, p0.y + side.y, p0.z + side.z).setColor(r, g, b, a);
        quads.addVertex(view, p1.x + side.x, p1.y + side.y, p1.z + side.z).setColor(r, g, b, a);
        quads.addVertex(view, p1.x - side.x, p1.y - side.y, p1.z - side.z).setColor(r, g, b, a);
        return true;
    }
}
