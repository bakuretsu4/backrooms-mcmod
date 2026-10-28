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
 * A leaf come loose from the Level 227 canopy: it sways and spins down slowly, side to side like a
 * real falling leaf, and settles on whatever it meets before fading out. One of a few leaf shapes
 * and greens (yellowed now and then).
 */
public class FallingLeafParticle extends TextureSheetParticle {
    private final float phase;
    private final float swayAmount;

    protected FallingLeafParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.lifetime = 200 + random.nextInt(120);
        this.quadSize = 0.10f + random.nextFloat() * 0.08f;
        this.gravity = 0.006f;
        this.friction = 0.92f;
        this.hasPhysics = true;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.swayAmount = 0.010f + random.nextFloat() * 0.008f;
        float tint = 0.85f + random.nextFloat() * 0.15f;
        setColor(tint, tint, tint);
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        if (onGround) {
            // Lying on the ground: fade out over the last second.
            xd = 0;
            zd = 0;
            if (lifetime - age > 20) {
                age = lifetime - 20;
            }
            alpha = Math.max(0.0f, (lifetime - age) / 20.0f);
            return;
        }
        // Side-to-side sway, the leaf rocking as it slides one way then the other.
        float w = age * 0.12f + phase;
        xd += Mth.cos(w) * swayAmount * 0.25f;
        zd += Mth.sin(w * 0.7f) * swayAmount * 0.25f;
        roll += Mth.sin(w) * 0.08f;
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
            return new FallingLeafParticle(level, x, y, z, sprites);
        }
    }
}
