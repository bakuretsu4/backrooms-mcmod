package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import net.minecraft.resources.ResourceLocation;

/**
 * The only place that switches Veil's G-buffers. Only called when Veil is loaded (see
 * {@link BackroomsLights}), so this class never loads without it.
 *
 * <p>Veil lights need two extra buffers (the unlit colour and the surface direction of every
 * pixel). Turning them on or off recompiles every world shader, which hitches for a moment. Veil
 * would do that each time the first light appears or the last one leaves the screen, so while
 * we're in the Backrooms we keep them switched on under our own name: Veil then never sees a
 * change and never recompiles mid-game.
 */
final class VeilBridge {
    private static final ResourceLocation BUFFERS_OWNER = Backrooms.id("lights");

    /** True once Veil's renderer exists (after the loading screen). */
    static boolean ready() {
        return VeilRenderSystem.renderer() != null;
    }

    static void pinBuffers() {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer != null) {
            renderer.enableBuffers(BUFFERS_OWNER, DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL);
        }
    }

    static void unpinBuffers() {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer != null) {
            renderer.disableBuffers(BUFFERS_OWNER);
        }
    }

    private VeilBridge() {}
}
