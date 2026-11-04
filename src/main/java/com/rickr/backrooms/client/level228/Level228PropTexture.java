package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * One of Level 228's prop texture sheets (textures/level228/*.png, 2048 x 2048), with a full
 * mipmap chain, trilinear and anisotropic filtering.
 *
 * <p>Why not a plain texture: Minecraft loads entity textures without mipmaps, and these sheets
 * carry fine detail (wood grain, carving, lettering) that you see from one block and from thirty,
 * and the floors at grazing angles; without mipmaps all of it shimmers and crawls as you walk.
 * Render types set a texture's filter every time they are set up
 * ({@code TextureStateShard} calls {@link #setFilter}), which would switch the mipmaps off again,
 * so that call is ignored here: the filter set at load stays.
 */
final class Level228PropTexture extends AbstractTexture {
    private static final float MAX_ANISOTROPY = 8.0f;
    private final ResourceLocation location;

    Level228PropTexture(ResourceLocation location) {
        this.location = location;
    }

    @Override
    public void load(ResourceManager manager) throws IOException {
        NativeImage image;
        try (InputStream in = manager.getResourceOrThrow(location).open()) {
            image = NativeImage.read(in);
        }
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> upload(image));
        } else {
            upload(image);
        }
    }

    private void upload(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int levels = 31 - Integer.numberOfLeadingZeros(Math.max(1, Math.min(w, h)));
        TextureUtil.prepareImage(getId(), levels, w, h);
        image.upload(0, 0, 0, 0, 0, w, h, false, true);
        GlStateManager._bindTexture(getId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    Math.min(MAX_ANISOTROPY, max));
        }
    }

    @Override
    public void setFilter(boolean blur, boolean mipmap) {
        // Kept as set at load (see the class comment).
    }
}
