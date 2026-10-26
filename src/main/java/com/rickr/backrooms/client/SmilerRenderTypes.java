package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render type for the Smiler's smoke. Extends RenderType only to reach its protected building
 * blocks ("shards"); it's never instantiated (same trick as {@link WatcherRenderTypes}).
 */
final class SmilerRenderTypes extends RenderType {
    /**
     * See-through and unlit, and it doesn't write depth. The no-depth part matters: the flashlight
     * light (Veil) is worked out from the depth of what's on screen, so smoke that wrote depth got
     * lit up like a solid object. Without it the beam lands on the wall behind and the smoke just
     * darkens what you see there, like a veil of smoke would.
     */
    static final RenderType SMOKE = create("backrooms_smiler_smoke", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(SmilerRenderer.TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    private SmilerRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                              boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
