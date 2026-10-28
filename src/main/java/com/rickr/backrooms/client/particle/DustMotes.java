package com.rickr.backrooms.client.particle;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.registry.ModParticles;
import com.rickr.backrooms.world.BackroomsLevel;
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
 *
 * <p>Level 0 has far less: under its low ceiling the specks were a field of bright dots
 * everywhere. There only about 3 in 10 tries spawn, and away from the flashlight only right
 * under a tube (block light {@value #LEVEL0_MIN_LIGHT}+), so you see the odd speck in your beam or
 * drifting through the light under a panel, and the specks themselves are smaller and fainter
 * (see DustMoteParticle).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class DustMotes {
    /** Tries per tick: a random spot around you, kept only if it is lit (or in the beam). */
    private static final int TRIES = 6;
    private static final double RADIUS = 7.0;
    private static final int MIN_LIGHT = 7;
    /** Level 0: the share of tries that may spawn at all (about 70% fewer specks) ... */
    private static final float LEVEL0_SPAWN_SHARE = 0.3f;
    /** ... and how lit a spot away from the flashlight must be: just under a tube (it is 11 right below one). */
    private static final int LEVEL0_MIN_LIGHT = 10;
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
        boolean level0 = BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_0;
        int minLight = level0 ? LEVEL0_MIN_LIGHT : MIN_LIGHT;
        for (int i = 0; i < TRIES; i++) {
            Vec3 at;
            boolean beam = flashlight && i % 2 == 0;
            if (level0 && RANDOM.nextFloat() >= LEVEL0_SPAWN_SHARE) {
                continue;
            }
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
            if (!beam && mc.level.getBrightness(LightLayer.BLOCK, CURSOR) < minLight) {
                continue;
            }
            mc.level.addParticle(ModParticles.DUST_MOTE.get(), at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
    }

    private DustMotes() {}
}
