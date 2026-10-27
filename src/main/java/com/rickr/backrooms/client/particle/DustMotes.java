package com.rickr.backrooms.client.particle;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.registry.ModParticles;
import com.rickr.backrooms.world.Travel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Fine dust in the air of the Backrooms, where there is light to see it by: a few specks a tick
 * appear around you near the lamps, and in the cone of your flashlight. They are only as bright as
 * the light they float in, so the dark stays dark.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class DustMotes {
    /** Tries per tick: a random spot around you, kept only if it is lit (or in the beam). */
    private static final int TRIES = 6;
    private static final double RADIUS = 7.0;
    private static final int MIN_LIGHT = 7;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || !Travel.inBackrooms(mc.level)) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        boolean flashlight = CameraClientState.flashlight && CameraClientState.battery > 0.0f;
        for (int i = 0; i < TRIES; i++) {
            Vec3 at;
            boolean beam = flashlight && i % 2 == 0;
            if (beam) {
                // Somewhere in the flashlight's cone, 1.5 to 8 blocks ahead.
                double d = 1.5 + RANDOM.nextDouble() * 6.5;
                at = eye.add(look.scale(d)).add((RANDOM.nextDouble() - 0.5) * d * 0.35, (RANDOM.nextDouble() - 0.5) * d * 0.35,
                        (RANDOM.nextDouble() - 0.5) * d * 0.35);
            } else {
                at = eye.add((RANDOM.nextDouble() * 2 - 1) * RADIUS, (RANDOM.nextDouble() * 2 - 1) * 2.5, (RANDOM.nextDouble() * 2 - 1) * RADIUS);
            }
            CURSOR.set(at.x, at.y, at.z);
            if (!mc.level.getBlockState(CURSOR).isAir()) {
                continue;
            }
            if (!beam && mc.level.getBrightness(LightLayer.BLOCK, CURSOR) < MIN_LIGHT) {
                continue;
            }
            mc.level.addParticle(ModParticles.DUST_MOTE.get(), at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
    }

    private DustMotes() {}
}
