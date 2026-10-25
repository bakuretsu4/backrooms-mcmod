package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Render types for the Level 9 entities. Extends RenderType only to reach its protected building
 * blocks ("shards"); it's never instantiated.
 *
 * <p>The Watcher and Observer are drawn from code-built geometry with per-vertex colours on a plain
 * white texture, so they need no texture files.
 */
final class WatcherRenderTypes extends RenderType {
    /** Vanilla's 1x1 white texture: the vertex colours are the whole look. */
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** Lit, opaque, both sides drawn (the tendrils are thin tubes seen from any side). */
    static final RenderType SOLID = entityCutoutNoCull(WHITE);

    /**
     * Additive glow without writing depth: the searchlight cone and the charging beam. Additive means
     * it only ever brightens what's behind it, like light in fog; no depth write means it never hides
     * anything drawn after it.
     */
    static final RenderType GLOW = create("backrooms_watcher_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
            false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    private WatcherRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
