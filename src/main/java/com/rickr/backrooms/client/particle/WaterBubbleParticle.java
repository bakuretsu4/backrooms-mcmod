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
 * Air dragged under by a body entering Level 227's pond water: the bubble starts with the speed it
 * was given (downward, with the body), the water brakes it, it turns and rises, wobbling, and
 * bursts as soon as it reaches the surface (or after a few seconds).
 */
public class WaterBubbleParticle extends TextureSheetParticle {
    /** Lift per tick, the water's drag per tick, and the fastest a bubble rises (blocks/tick). */
    private static final double LIFT = 0.014;
    private static final double DRAG = 0.86;
    private static final double MAX_RISE = 0.09;

    private int light;

    protected WaterBubbleParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                  SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 40 + random.nextInt(40);
        this.gravity = 0.0f;
        this.hasPhysics = true;
        this.quadSize = 0.025f + random.nextFloat() * 0.035f;
        setColor(0.86f, 0.9f, 0.78f);
        this.alpha = 0.75f;
        pickSprite(sprites);
        this.light = WaterQuads.light(level, x, y, z);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || Double.isNaN(WaterQuads.waterTop(level, x, y, z))) {
            remove(); // burst at the surface (or out of water altogether)
            return;
        }
        yd = Math.min(yd + LIFT, MAX_RISE);
        xd += (random.nextFloat() - 0.5f) * 0.004f;
        zd += (random.nextFloat() - 0.5f) * 0.004f;
        move(xd, yd, zd);
        xd *= DRAG;
        yd *= DRAG;
        zd *= DRAG;
        light = WaterQuads.light(level, x, y, z);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        WaterQuads.billboard(buffer, camera, Mth.lerp(partialTicks, xo, x), Mth.lerp(partialTicks, yo, y),
                Mth.lerp(partialTicks, zo, z), quadSize, getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, alpha, light);
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return getBoundingBox(); // the bubble's own small box: no new box every frame
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
            return new WaterBubbleParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
