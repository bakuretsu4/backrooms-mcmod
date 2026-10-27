package com.rickr.backrooms.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A speck of dust hanging in the air: tiny, drifting on slow wandering currents and barely sinking.
 * It is only as bright as the light where it floats, so you see it near the tubes and in your
 * flashlight beam, and not at all in the dark.
 */
public class DustMoteParticle extends TextureSheetParticle {
    private final float phase;

    protected DustMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.lifetime = 120 + random.nextInt(120);
        this.quadSize = 0.012f + random.nextFloat() * 0.014f;
        this.alpha = 0.0f;
        this.gravity = 0.0004f;
        this.friction = 0.96f;
        this.hasPhysics = false;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        float warm = 0.9f + random.nextFloat() * 0.1f;
        setColor(warm, warm * 0.97f, warm * 0.92f);
        this.xd = (random.nextFloat() - 0.5f) * 0.004f;
        this.yd = (random.nextFloat() - 0.5f) * 0.002f;
        this.zd = (random.nextFloat() - 0.5f) * 0.004f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        alpha = 0.75f * Mth.clamp(t / 0.2f, 0.0f, 1.0f) * Mth.clamp((1.0f - t) / 0.3f, 0.0f, 1.0f);
        // Slow swirling currents.
        float w = (age + phase * 40.0f) * 0.05f;
        xd += Mth.sin(w) * 0.00012f;
        zd += Mth.cos(w * 0.8f) * 0.00012f;
        yd += Mth.sin(w * 0.6f + phase) * 0.00006f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return SoftParticles.RENDER_TYPE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new DustMoteParticle(level, x, y, z, sprites);
        }
    }
}
