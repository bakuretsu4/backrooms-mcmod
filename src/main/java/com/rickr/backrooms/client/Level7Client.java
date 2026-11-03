package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level7Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Level 7 on the client, apart from its light and fog ({@link Level7Atmosphere}):
 * <ul>
 *   <li><b>Depth:</b> how far under the surface the camera is, which darkens the light, the fog
 *       and the picture grade.</li>
 *   <li><b>The lurch:</b> drop through the hatch and the world swings round you for a moment, as if
 *       "down" had changed (the wikis: gravity feels wrong when you go through).</li>
 *   <li><b>The Thing:</b> something enormous lives in the sea and swims past you, closer and more
 *       often the longer you stay in. It never attacks. See {@link Level7Thing}. (Being in the
 *       water drains sanity on the server, more the deeper you go.)</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level7Client {
    /** Depth (blocks under the surface) where the water starts getting darker, and where it is darkest. */
    private static final double DARK_FROM = 4.0;
    private static final double DARK_AT = 110.0;

    // --- The lurch -------------------------------------------------------------------------------
    private static final int LURCH_TICKS = 22;
    private static final float LURCH_ROLL = 38.0f;
    private static final float LURCH_PITCH = 18.0f;

    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean underwater;
    private static float deepness;
    private static boolean wasInRoom;
    private static int lurchTick = -1;
    private static float lurchSide = 1.0f;

    private Level7Client() {}

    /** True while the camera is under the water in Level 7. */
    public static boolean underwater() {
        return underwater;
    }

    /** 0 near the surface (or above it) to 1 deep down. */
    public static float deepness() {
        return deepness;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !Level7Atmosphere.inLevel7()) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double depth = Level7Layout.SURFACE_Y + 1 - eye.y;
        underwater = mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
        // Darkening on a curve: the upper water stays murky-light for a while (the twilight where
        // the Thing is easiest to see), then it falls off fast toward the bottom.
        deepness = (float) Math.pow(Mth.clamp((depth - DARK_FROM) / (DARK_AT - DARK_FROM), 0.0, 1.0), 1.5);

        tickLurch(mc, player);
        Level7Thing.tick(mc, player, eye, depth, underwater);
    }

    private static void reset() {
        underwater = false;
        deepness = 0.0f;
        wasInRoom = false;
        lurchTick = -1;
        Level7Thing.reset();
    }

    /** Up in the room, then falling below the ceiling without holding the rope: lurch. */
    private static void tickLurch(Minecraft mc, Player player) {
        if (player.getY() >= Level7Layout.ROOM_FLOOR_Y) {
            wasInRoom = true;
        } else if (wasInRoom && player.getY() < Level7Layout.CEILING_BOTTOM_Y) {
            wasInRoom = false;
            if (!player.onClimbable()) {
                lurchTick = 0;
                lurchSide = RANDOM.nextBoolean() ? 1.0f : -1.0f;
                Level7Thing.playFlat(mc, ModSounds.L7_LURCH.get(), 0.9f, 0.95f + RANDOM.nextFloat() * 0.1f);
            }
        }
        if (lurchTick >= 0 && ++lurchTick > LURCH_TICKS) {
            lurchTick = -1;
        }
    }

    /** Camera: the lurch through the hatch, then the Thing's swells and wake; all scaled by the view-tilt setting. */
    public static void modifyAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!Level7Atmosphere.inLevel7()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        if (lurchTick >= 0) {
            float t = Mth.clamp((lurchTick + partial) / LURCH_TICKS, 0.0f, 1.0f);
            // Swings out fast, then eases back: sin over the first half, a long settle after.
            float swing = Mth.sin(t * Mth.PI) * (1.0f - 0.4f * t) * ClientSettings.tilt();
            event.setRoll(event.getRoll() + lurchSide * LURCH_ROLL * swing);
            event.setPitch(event.getPitch() + LURCH_PITCH * swing);
        }
        Level7Thing.modifyAngles(event, partial);
    }
}
