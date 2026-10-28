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
 * A puff of GammaPali gas: a big, very faint, glowing yellow-green blob that drifts and swirls
 * slowly round the plant, fading in and out over a few seconds. Many of them overlap into a soft
 * haze that marks exactly where the air is poisonous. Drawn full-bright (the gas is the glow), and
 * blended softly like the steam (SoftParticles), so there are no hard edges.
 */
public class SporeHazeParticle extends TextureSheetParticle {
    private final float phase;
    private final float peakAlpha;

    protected SporeHazeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.lifetime = 70 + random.nextInt(60);
        this.quadSize = 0.45f + random.nextFloat() * 0.55f;
        this.alpha = 0.0f;
        this.peakAlpha = 0.10f + random.nextFloat() * 0.08f;
        this.gravity = 0.0f;
        this.friction = 0.97f;
        this.hasPhysics = false;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        // From sickly yellow-green to a paler green-white near the core.
        float g = random.nextFloat();
        setColor(0.72f + 0.2f * g, 1.0f, 0.42f + 0.3f * g);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        alpha = peakAlpha * Mth.clamp(t / 0.3f, 0.0f, 1.0f) * Mth.clamp((1.0f - t) / 0.4f, 0.0f, 1.0f);
        // Slow swirling, and a slow turn of the blob itself.
        float w = (age + phase * 40.0f) * 0.04f;
        xd += Mth.sin(w) * 0.0003f;
        zd += Mth.cos(w * 0.8f) * 0.0003f;
        oRoll = roll;
        roll += 0.004f;
    }

    /** The gas is its own light. */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
            return new SporeHazeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
