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
 * A wisp of steam: a big, soft, very faint billboard that rises slowly, swells and stretches, turns
 * a little and fades in and out over several seconds. Many overlapping wisps make a low, drifting
 * layer over a warm puddle. It takes the light where it is (bright under a tube, lost in the dark).
 */
public class SteamParticle extends TextureSheetParticle {
    /** Peak opacity of one wisp; many overlap, so the layer as a whole is denser than this. */
    private static final float MAX_ALPHA = 0.3f;
    private final float startSize;
    private final float endSize;
    private final float spin;

    protected SteamParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 90 + random.nextInt(60);
        this.startSize = 0.25f + random.nextFloat() * 0.2f;
        this.endSize = 1.4f + random.nextFloat() * 1.0f;
        this.quadSize = startSize;
        this.spin = (random.nextFloat() - 0.5f) * 0.012f;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        float grey = 0.86f + random.nextFloat() * 0.1f;
        setColor(grey, grey, grey * 1.02f);
        this.alpha = 0.0f;
        this.gravity = -0.0015f; // keeps rising, gently
        this.friction = 0.985f;
        this.hasPhysics = false;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        quadSize = Mth.lerp(1.0f - (1.0f - t) * (1.0f - t), startSize, endSize);
        // Fade in quickly, hold, fade out slowly.
        alpha = MAX_ALPHA * Mth.clamp(t / 0.15f, 0.0f, 1.0f) * Mth.clamp((1.0f - t) / 0.55f, 0.0f, 1.0f);
        oRoll = roll;
        roll += spin;
        // A faint sideways drift that wanders.
        xd += (random.nextFloat() - 0.5f) * 0.0006f;
        zd += (random.nextFloat() - 0.5f) * 0.0006f;
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
            return new SteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
