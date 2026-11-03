package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.particle.PetalParticle;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.world.layout.Level369Plan;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Level 369: petals and pollen drifting through the light around you. Petals come from the room's
 * own flowers (blue cornflowers in the hallways, red daisies in the pillar halls, every colour in
 * the skylit rooms; none in the dark rooms) and ride the meadow's breeze; pollen hangs in the lit
 * air everywhere but the dark rooms. A few at a time, never a snowstorm.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level369Petals {
    private static final int TRIES = 3;
    private static final double RADIUS = 11.0;
    /** Only where it is properly lit. */
    private static final int MIN_LIGHT = 9;
    private static final float PETAL_CHANCE = 0.10f;
    private static final float POLLEN_CHANCE = 0.30f;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private Level369Petals() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || !Level369Atmosphere.inLevel369()
                || !Level369ClientState.hasSeed()) {
            return;
        }
        long seed = Level369ClientState.seed();
        for (int i = 0; i < TRIES; i++) {
            double x = mc.player.getX() + (RANDOM.nextDouble() * 2 - 1) * RADIUS;
            double z = mc.player.getZ() + (RANDOM.nextDouble() * 2 - 1) * RADIUS;
            double y = mc.player.getY() + 0.4 + RANDOM.nextDouble() * 3.0;
            CURSOR.set(x, y, z);
            if (!mc.level.getBlockState(CURSOR).isAir() || mc.level.getBrightness(LightLayer.BLOCK, CURSOR) < MIN_LIGHT) {
                continue;
            }
            int kind = Level369Plan.roomKind(seed, CURSOR.getX(), CURSOR.getZ());
            if (kind == Level369Plan.DARK) {
                continue;
            }
            float roll = RANDOM.nextFloat();
            int sprite;
            if (roll < PETAL_CHANCE) {
                int colour = switch (kind) {
                    case Level369Plan.HALL -> 1;                  // blue
                    case Level369Plan.PILLAR -> 0;                // red
                    default -> RANDOM.nextInt(4);                  // red, blue, pink or white
                };
                sprite = colour * 2 + RANDOM.nextInt(2);
            } else if (roll < PETAL_CHANCE + POLLEN_CHANCE) {
                sprite = 8 + RANDOM.nextInt(2);
            } else {
                continue;
            }
            mc.level.addParticle(Level369Blocks.PETAL.get(), x, y, z, sprite, 0.0, 0.0);
        }
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Level369Blocks.PETAL.get(), PetalParticle.Provider::new);
    }
}
