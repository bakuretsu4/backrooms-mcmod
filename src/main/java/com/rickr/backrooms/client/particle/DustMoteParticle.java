package com.rickr.backrooms.client.particle;

import com.rickr.backrooms.world.BackroomsLevel;
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
 * flashlight beam, and not at all in the dark. On Level 0 the specks are about 30% smaller and
 * half as opaque: there they should be something you notice now and then, not a field of dots.
 */
public class DustMoteParticle extends TextureSheetParticle {
    /** How opaque a speck gets at most (it fades in and out over its life). */
    private static final float MAX_ALPHA = 0.75f;
    /** Level 0: size and opacity of a speck compared with the other levels. */
    private static final float LEVEL0_SIZE = 0.7f;
    private static final float LEVEL0_ALPHA = 0.5f;

    private final float phase;
    private final float maxAlpha;

    protected DustMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        boolean level0 = BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_0;
        this.lifetime = 120 + random.nextInt(120);
        this.quadSize = (0.012f + random.nextFloat() * 0.014f) * (level0 ? LEVEL0_SIZE : 1.0f);
        this.maxAlpha = MAX_ALPHA * (level0 ? LEVEL0_ALPHA : 1.0f);
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
        alpha = maxAlpha * Mth.clamp(t / 0.2f, 0.0f, 1.0f) * Mth.clamp((1.0f - t) / 0.3f, 0.0f, 1.0f);
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
