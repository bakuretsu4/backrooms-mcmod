package com.rickr.backrooms.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.registry.ModParticles;
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
 * A drop of Level 227's pond water thrown up by a splash: it flies out along the speed it was given,
 * falls back under gravity and vanishes the moment it drops into the water again (now and then
 * leaving a tiny ring where it lands). Tea-coloured and see-through, lit like the scene around it.
 */
public class WaterDropletParticle extends TextureSheetParticle {
    /** Share of drops that leave a small ring where they fall back in. */
    private static final float RING_CHANCE = 0.2f;

    private int light;

    protected WaterDropletParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                   SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 30 + random.nextInt(16);
        this.gravity = 0.95f;
        this.friction = 0.985f;
        this.hasPhysics = true;
        this.quadSize = 0.035f + random.nextFloat() * 0.035f;
        // Clear tannin water: a pale amber, a little brighter or darker drop to drop.
        float k = 0.88f + random.nextFloat() * 0.12f;
        setColor(0.88f * k, 0.86f * k, 0.68f * k);
        this.alpha = 0.85f;
        pickSprite(sprites);
        this.light = WaterQuads.light(level, x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        light = WaterQuads.light(level, x, y, z);
        if (onGround) {
            remove(); // landed on the bank: soaks in
            return;
        }
        if (age > 2 && yd < 0.0) {
            double top = WaterQuads.waterTop(level, x, y, z);
            if (!Double.isNaN(top)) {
                if (random.nextFloat() < RING_CHANCE) {
                    level.addParticle(ModParticles.L227_WATER_RING.get(), x, top + 0.015, z, 0.08, 0.0, 0.0);
                }
                remove();
            }
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        WaterQuads.billboard(buffer, camera, Mth.lerp(partialTicks, xo, x), Mth.lerp(partialTicks, yo, y),
                Mth.lerp(partialTicks, zo, z), quadSize, getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, alpha, light);
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return getBoundingBox(); // the drop's own small box: no new box every frame
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new WaterDropletParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
