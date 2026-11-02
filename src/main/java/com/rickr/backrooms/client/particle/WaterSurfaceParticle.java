package com.rickr.backrooms.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Something lying flat on Level 227's pond water: a ripple ring spreading out from where a body or
 * a drop went in, or a patch of foam left by a splash. Spawn it just above the surface; the first
 * spawn speed argument is its starting size (blocks from centre to edge).
 *
 * <ul>
 *   <li><b>Ring:</b> grows fast at first and slower later (like a real ripple losing its push),
 *       fading out as it spreads; bigger rings spread further and last longer.</li>
 *   <li><b>Foam:</b> creamy beige (tannin water foams a pale brown), spreads a little, then fades.</li>
 * </ul>
 */
public class WaterSurfaceParticle extends TextureSheetParticle {
    private final boolean ring;
    private final float startSize;
    private final float growth;
    private final float startAlpha;
    private final float cos;
    private final float sin;
    private float size;
    private float oSize;
    private int light;
    /** Box for culling, sized to the particle; rebuilt once per tick (not per frame). */
    private AABB renderBox;

    protected WaterSurfaceParticle(ClientLevel level, double x, double y, double z, double size, boolean ring,
                                   SpriteSet sprites) {
        super(level, x, y, z);
        this.ring = ring;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.startSize = (float) Math.max(0.05, size);
        if (ring) {
            this.lifetime = (int) (14 + 14 * Math.min(1.0, size)) + random.nextInt(6);
            this.growth = 0.6f + 0.9f * startSize;
            this.startAlpha = 0.5f;
            setColor(0.92f, 0.93f, 0.86f);
        } else {
            this.lifetime = 50 + random.nextInt(40);
            this.growth = 0.15f * startSize;
            this.startAlpha = 0.75f;
            float k = 0.92f + random.nextFloat() * 0.08f;
            setColor(0.9f * k, 0.86f * k, 0.72f * k);
        }
        float angle = random.nextFloat() * Mth.TWO_PI;
        this.cos = Mth.cos(angle);
        this.sin = Mth.sin(angle);
        this.size = startSize;
        this.oSize = startSize;
        this.alpha = startAlpha;
        pickSprite(sprites);
        this.light = WaterQuads.light(level, x, y + 0.1, z);
        this.renderBox = box();
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oSize = size;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = (float) age / lifetime;
        float eased = 1.0f - (1.0f - t) * (1.0f - t); // fast at first, slowing down
        size = startSize + growth * eased;
        if (ring) {
            alpha = startAlpha * (float) Math.pow(1.0f - t, 1.5);
        } else {
            alpha = t < 0.6f ? startAlpha : startAlpha * (1.0f - (t - 0.6f) / 0.4f);
        }
        light = WaterQuads.light(level, x, y + 0.1, z);
        renderBox = box();
    }

    private AABB box() {
        return new AABB(x - size, y - 0.05, z - size, x + size, y + 0.05, z + size);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        WaterQuads.flat(buffer, camera, x, y, z, Mth.lerp(partialTicks, oSize, size), cos, sin,
                getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, alpha, light);
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return renderBox;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Ripple rings ({@code l227_water_ring}). */
    public static final class RingProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public RingProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double size, double unusedY, double unusedZ) {
            return new WaterSurfaceParticle(level, x, y, z, size, true, sprites);
        }
    }

    /** Foam patches ({@code l227_water_foam}). */
    public static final class FoamProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public FoamProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double size, double unusedY, double unusedZ) {
            return new WaterSurfaceParticle(level, x, y, z, size, false, sprites);
        }
    }
}
