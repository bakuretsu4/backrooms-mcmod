package com.rickr.backrooms.client.anomaly.level1;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The low fog of a fog patch ({@code level_1/fog_puddles}; archived Wikidot: "a low-hanging fog
 * with no discernable source", https://backrooms-wiki.wikidot.com/archived:level-1).
 *
 * <p>Two parts: slow, flat puffs of mist that hang just above the floor around you (only over the
 * patch, so you see the fog lying ahead before you walk into it), and a damp grey haze over the
 * whole picture that thickens as you go in. Now and then a drop of water forms on the ceiling
 * above a puddle and falls into it (the server plays the drip).
 */
final class FogPuddlesEffect implements AnomalyClientEffect {
    /** The haze starts this far (blocks) outside the patch. */
    private static final double SPILL = 8.0;
    /** Mist puffs per tick when inside. */
    private static final float MIST_RATE = 1.2f;
    /** Pale, damp and close: grey-white air from 1.5 blocks on, washed-out colour, a soft glow. */
    private static final AtmosphereGrade.Mood FOG = new AtmosphereGrade.Mood(1.02f, 0.94f, 0.62f,
            0.94f, 0.97f, 1.04f, 1.0f, 1.0f, 1.02f,
            0.030f, 0.032f, 0.036f, 0.46f, 0.48f, 0.52f, 1.5f, 0.10f, 0.72f, 0.40f, 0.20f, 0.010f);

    private final RandomSource random = RandomSource.create();

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        double distance = site.distanceTo(player.getX(), player.getZ());
        float near = inside ? 1.0f : (float) Mth.clamp(1.0 - distance / SPILL, 0.0, 1.0);
        near = near * near * (3.0f - 2.0f * near);
        if (near <= 0.0f) {
            return;
        }
        AtmosphereGrade.setAnomalyMood(FOG, 0.8f * near);
        if (mc.isPaused()) {
            return;
        }
        float rate = MIST_RATE * Math.max(near, 0.5f);
        int puffs = (int) rate + (random.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < puffs; i++) {
            mist(mc, site, player);
        }
        if (random.nextInt(4) == 0) {
            drip(mc, site, player);
        }
    }

    /** One flat puff of mist a few blocks away, lying just above the floor. */
    private void mist(Minecraft mc, AnomalySite site, LocalPlayer player) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = 2.5 + random.nextDouble() * 9.0;
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;
        if (site.distanceTo(x, z) > 2.0) {
            return; // the fog stays over its own patch
        }
        BlockPos pos = BlockPos.containing(x, LevelLayout.FLOOR_Y + 1, z);
        if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
            return; // not inside walls, pillars or crates
        }
        double y = LevelLayout.FLOOR_Y + 1.02 + random.nextDouble() * 0.5;
        Particle puff = mc.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                (random.nextDouble() - 0.5) * 0.006, 0.0, (random.nextDouble() - 0.5) * 0.006);
        if (puff != null) {
            // Campfire smoke would rise for 15 seconds; this just lingers and fades.
            puff.setLifetime(90 + random.nextInt(70));
            puff.setColor(0.80f, 0.83f, 0.87f);
        }
    }

    /** A drop forming on the ceiling right above a puddle. */
    private void drip(Minecraft mc, AnomalySite site, LocalPlayer player) {
        int x = player.getBlockX() + random.nextInt(25) - 12;
        int z = player.getBlockZ() + random.nextInt(25) - 12;
        if (!site.contains(x, z) || !mc.level.getBlockState(new BlockPos(x, LevelLayout.FLOOR_Y + 1, z)).is(ModBlocks.L1_CONDENSATION.get())) {
            return;
        }
        double ceiling = BackroomsLevel.LEVEL_1.layout().ceilingY();
        mc.level.addParticle(ParticleTypes.DRIPPING_WATER, x + 0.3 + random.nextDouble() * 0.4, ceiling - 0.05,
                z + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
    }
}
