package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Render types of Level 228's props. Extends RenderType only to reach its protected building
 * blocks ("shards"); it's never instantiated (same trick as client/SmilerRenderTypes).
 *
 * <p>The static props use vanilla's {@code entityCutoutNoCull} with the sheet as is (so shader
 * packs run them through their entity program and light them from the lightmap like any mob).
 * The one custom type is the mirror surface's.
 */
final class Level228PropRenderTypes extends RenderType {
    /**
     * A mirror's surface (or a sheet of glass): lit by the lightmap, blended, both sides, and
     * <b>no depth write</b>. The depth matters twice: Veil works its lights out from the depth on
     * screen, and shader packs their fog and depth of field; a pane that wrote depth would make the
     * whole reflection behind it lit and fogged as if it were a flat picture at the glass. Without
     * it the reflection stays as deep as the twin room it really is, and the pane only adds its
     * silvering, dust and cracks on top.
     */
    static RenderType surface(ResourceLocation texture) {
        return create("backrooms_l228_mirror", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private Level228PropRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                    boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
