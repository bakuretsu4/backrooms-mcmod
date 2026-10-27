package com.rickr.backrooms.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.rickr.backrooms.Backrooms;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * How the steam and dust particles are drawn: blended softly over the scene (shaders/core/
 * soft_particle keeps even the faintest pixels), without writing depth so overlapping wisps mix
 * instead of cutting holes in each other. If the shader fails to load they are drawn the vanilla
 * way (harder edges, but still there).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class SoftParticles {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static ShaderInstance shader;

    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            }
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }

        @Override
        public String toString() {
            return "BACKROOMS_SOFT";
        }
    };

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("soft_particle"),
                    DefaultVertexFormat.PARTICLE), loaded -> shader = loaded);
        } catch (Exception e) {
            LOGGER.error("[{}] Soft particle shader failed to load; steam and dust use the vanilla particle shader", Backrooms.MOD_ID, e);
        }
    }

    private SoftParticles() {}
}
