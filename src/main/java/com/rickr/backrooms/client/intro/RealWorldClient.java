package com.rickr.backrooms.client.intro;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.network.WallPushPayload;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Travel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The real world wearing thin, on the client (the rules are in world/RealWorldEntries):
 * <ul>
 *   <li>Walking into a wall: while you press forward against one, we tell the server twice a
 *       second (only it knows whether this wall may give way).</li>
 *   <li>The thinner reality gets around you, the louder Level 0's fluorescent hum fades in, from
 *       nowhere, and (unless you're filming, where the tape breaking up says it instead) the
 *       picture takes on a faint stale yellow, as if the walls were starting to look wrong.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class RealWorldClient implements LayeredDraw.Layer {
    public static final ResourceLocation LAYER_ID = Backrooms.id("real_world_thin");
    private static final int PUSH_EVERY = 10;
    private static final float HUM_VOLUME = 0.7f;
    private static final float TINT_ALPHA = 0.16f;
    private static final int TINT_RGB = 0xD8C46A;

    private static int pushTicks;
    @Nullable
    private static FadingLoopSound hum;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || Travel.inBackrooms(mc.level) || NoclipTransition.active()) {
            pushTicks = 0;
            return;
        }
        boolean pushing = player.horizontalCollision && player.input.forwardImpulse > 0.0f;
        if (pushing && ++pushTicks % PUSH_EVERY == 0) {
            PacketDistributor.sendToServer(WallPushPayload.INSTANCE);
        } else if (!pushing) {
            pushTicks = 0;
        }
        float thin = CameraClientState.realThin;
        if (thin > 0.02f) {
            if (hum == null || !mc.getSoundManager().isActive(hum)) {
                hum = new FadingLoopSound(ModSounds.LEVEL0_HUM.get(), true, 0, 0, 0, 0.02f);
                mc.getSoundManager().play(hum);
            }
            hum.feed(HUM_VOLUME * thin * Mth.sqrt(thin)); // starts barely there, swells at the end
        }
    }

    /** The faint yellowing, over the world (not while filming, and never in the Backrooms). */
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float thin = CameraClientState.realThin;
        if (thin <= 0.0f || CameraClientState.realRecording || mc.level == null || Travel.inBackrooms(mc.level)) {
            return;
        }
        int alpha = Mth.clamp((int) (thin * thin * TINT_ALPHA * 255.0f), 0, 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | TINT_RGB);
    }
}
