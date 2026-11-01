package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Bacteria;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Little black specks that come off a Bacteria while it runs at you or holds you, and drift
 * slowly down, like dust off something falling apart (the reference video has them all through
 * the close-up). About one a second from each one, from random points along its arms and spine;
 * small, so they read as dust, not confetti.
 *
 * <p>Purely on the client and cheap: no particle type to register, just a particle showing the
 * black concrete texture (that is what makes it a solid black square). Off when the
 * particle setting is "Minimal".
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class BacteriaFlecks {
    /** Chance per tick for each Bacteria: 0.05 = about 1 fleck a second. */
    private static final float CHANCE = 0.05f;
    /** Only Bacteria this close to you shed flecks you could see. */
    private static final double RANGE = 32.0;
    private static final RandomSource RANDOM = RandomSource.create();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        for (Bacteria bacteria : mc.level.getEntitiesOfClass(Bacteria.class, mc.player.getBoundingBox().inflate(RANGE))) {
            Bacteria.AnimPose forced = bacteria.forcedPose();
            boolean caging = forced == Bacteria.AnimPose.AUTO ? bacteria.isCaging()
                    : forced == Bacteria.AnimPose.CAGE || forced == Bacteria.AnimPose.LUNGE;
            boolean hunting = forced == Bacteria.AnimPose.AUTO ? bacteria.isChasing() : caging || forced == Bacteria.AnimPose.CHASE;
            if (hunting && RANDOM.nextFloat() < CHANCE) {
                shed(mc, bacteria, caging);
            }
        }
    }

    /** One fleck from a random point on its spine or arms (where they roughly are in that pose). */
    private static void shed(Minecraft mc, Bacteria bacteria, boolean caging) {
        float yaw = bacteria.yBodyRot * Mth.DEG_TO_RAD;
        // Its forward and its left, flat on the ground.
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double lx = Mth.cos(yaw);
        double lz = Mth.sin(yaw);
        double r = RANDOM.nextDouble();
        double side;
        double up;
        double ahead;
        if (RANDOM.nextFloat() < 0.35f) {
            // The spine, leaning forward.
            side = (RANDOM.nextDouble() - 0.5) * 0.2;
            up = 1.3 + r * 1.2;
            ahead = r * (caging ? 0.45 : 0.7);
        } else {
            double s = RANDOM.nextBoolean() ? 1.0 : -1.0;
            if (caging) {
                // Arms: shoulder down to the elbow beside the victim, then forearm to the floor.
                if (r < 0.5) {
                    double k = r * 2.0;
                    side = s * (0.64 + 0.47 * k);
                    up = 2.65 - 1.15 * k;
                    ahead = 0.4 + 0.5 * k;
                } else {
                    double k = r * 2.0 - 1.0;
                    side = s * (1.11 - 0.36 * k);
                    up = 1.5 - 1.45 * k;
                    ahead = 0.9 + 0.35 * k;
                }
            } else {
                // Arms reaching out ahead at shoulder height.
                side = s * (0.65 + r * 0.6);
                up = 2.4 - r * 0.4;
                ahead = 0.7 + r * 2.0;
            }
        }
        double x = bacteria.getX() + lx * side + fx * ahead;
        double y = bacteria.getY() + up;
        double z = bacteria.getZ() + lz * side + fz * ahead;
        TextureAtlasSprite sprite = mc.getBlockRenderer().getBlockModelShaper()
                .getBlockModel(Blocks.BLACK_CONCRETE.defaultBlockState()).getParticleIcon(ModelData.EMPTY);
        mc.particleEngine.add(new Fleck(mc.level, x, y, z, sprite));
    }

    /** A small black square drifting down at about 0.02 blocks a tick for 1-2 s. */
    private static final class Fleck extends TextureSheetParticle {
        Fleck(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y, z);
            setSprite(sprite);
            lifetime = 20 + random.nextInt(21);
            quadSize = 0.018f + random.nextFloat() * 0.018f;
            gravity = 0.0f;
            friction = 1.0f;
            hasPhysics = false;
            xd = (random.nextFloat() - 0.5f) * 0.01f;
            yd = -0.02f;
            zd = (random.nextFloat() - 0.5f) * 0.01f;
            setColor(0.35f, 0.35f, 0.35f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            // The block texture sheet, because the sprite comes from a block.
            return ParticleRenderType.TERRAIN_SHEET;
        }
    }

    private BacteriaFlecks() {}
}
