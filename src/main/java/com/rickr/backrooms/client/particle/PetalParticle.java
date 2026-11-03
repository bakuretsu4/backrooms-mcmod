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
 * Level 369: a petal or a speck of pollen drifting through the light (spawned by
 * client/Level369Petals). A petal rides the same breeze the meadow sways in (along +x, a little
 * +z), sinking slowly and turning as it flutters, and settles on whatever it meets before fading.
 * Pollen is a tiny warm speck that hangs in the air, wandering, faintly glowing, and fades.
 */
public class PetalParticle extends TextureSheetParticle {
    /** Sprites 8 and 9 of the sheet are pollen (see tools/anomaly_assets/level369.py). */
    private static final int FIRST_POLLEN = 8;
    private static final int SPRITES = 10;

    private final boolean pollen;
    private final float phase;
    private final float flutter;

    protected PetalParticle(ClientLevel level, double x, double y, double z, int sprite, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.pollen = sprite >= FIRST_POLLEN;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.flutter = 0.6f + random.nextFloat() * 0.8f;
        this.hasPhysics = true;
        this.friction = 0.94f;
        if (pollen) {
            lifetime = 120 + random.nextInt(100);
            quadSize = 0.018f + random.nextFloat() * 0.016f;
            gravity = 0.0f;
            xd = (random.nextFloat() - 0.5f) * 0.004f;
            yd = (random.nextFloat() - 0.4f) * 0.003f;
            zd = (random.nextFloat() - 0.5f) * 0.004f;
        } else {
            lifetime = 180 + random.nextInt(120);
            quadSize = 0.045f + random.nextFloat() * 0.03f;
            gravity = 0.0012f;
            xd = 0.012f + random.nextFloat() * 0.01f;
            zd = 0.005f + random.nextFloat() * 0.006f;
        }
        alpha = 0.0f;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setSprite(sprites.get(Mth.clamp(sprite, 0, SPRITES - 1), SPRITES - 1));
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        // Fade in over a second, out over the last two.
        float in = Math.min(1.0f, age / 20.0f);
        float out = Math.min(1.0f, (lifetime - age) / 40.0f);
        alpha = Math.max(0.0f, Math.min(in, out)) * (pollen ? 0.85f : 1.0f);
        if (pollen) {
            // A slow random wander.
            xd += (random.nextFloat() - 0.5f) * 0.0006f;
            yd += (random.nextFloat() - 0.5f) * 0.0005f;
            zd += (random.nextFloat() - 0.5f) * 0.0006f;
            return;
        }
        if (onGround) {
            xd = 0.0;
            zd = 0.0;
            if (lifetime - age > 40) {
                age = lifetime - 40;
            }
            return;
        }
        // The breeze, rising and falling, and the petal rocking and tumbling in it.
        float w = age * 0.09f * flutter + phase;
        xd += (0.0006f + Mth.sin(w * 0.37f) * 0.0004f);
        zd += Mth.sin(w * 0.6f) * 0.0003f;
        yd += Mth.cos(w) * 0.0008f;
        roll += Mth.sin(w) * 0.06f * flutter;
    }

    /** Pollen glows faintly, like dust in a sunbeam; petals take the room's light. */
    @Override
    protected int getLightColor(float partialTick) {
        return pollen ? 0xF000F0 : super.getLightColor(partialTick);
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
            return new PetalParticle(level, x, y, z, (int) Math.round(vx), sprites);
        }
    }
}
