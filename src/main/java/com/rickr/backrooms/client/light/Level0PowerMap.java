package com.rickr.backrooms.client.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * Level 0's power map for the screen pass (pinwheel level0_screen.fsh): {@link Level0Power#powerAt}
 * sampled every {@value #STEP} blocks over the {@value #EXTENT} x {@value #EXTENT} blocks around
 * you, refreshed every tick. The graphics card blends between the samples, so the bounce fill
 * and the far ceiling panels fade smoothly where a dying wave or an outage has passed. While no
 * outage and no blackout is in play it is all 1 and isn't re-sent.
 */
public final class Level0PowerMap {
    static final int STEP = 4;
    private static final int TEXELS = 64;
    static final int EXTENT = STEP * TEXELS;

    private static int texture;
    private static ByteBuffer data;
    private static int originX;
    private static int originZ;
    /** The power beyond the map (far districts). */
    private static float far = 1.0f;
    private static boolean allOn;

    private Level0PowerMap() {}

    static int texture() {
        return texture;
    }

    static int originX() {
        return originX;
    }

    static int originZ() {
        return originZ;
    }

    static float far() {
        return far;
    }

    /** Recomputes and uploads the map around the camera. Client tick, Level 0 only. */
    static void tick(double camX, double camZ, long now) {
        RenderSystem.assertOnRenderThread();
        if (texture == 0) {
            create();
        }
        originX = Math.floorDiv((int) Math.floor(camX), STEP) * STEP - EXTENT / 2;
        originZ = Math.floorDiv((int) Math.floor(camZ), STEP) * STEP - EXTENT / 2;
        boolean anything = Level0Power.anything();
        if (!anything) {
            far = 1.0f;
            if (allOn) {
                return;
            }
        }
        data.clear();
        for (int tz = 0; tz < TEXELS; tz++) {
            double z = originZ + (tz + 0.5) * STEP;
            for (int tx = 0; tx < TEXELS; tx++) {
                double x = originX + (tx + 0.5) * STEP;
                float p = anything ? Level0Power.powerAt(x, z, now) : 1.0f;
                data.put((byte) Math.round(Math.max(0.0f, Math.min(1.0f, p)) * 255.0f));
            }
        }
        data.flip();
        // Beyond the map: the power a little past its edge in the direction away from the wave.
        far = anything ? Level0Power.powerAt(camX + EXTENT, camZ, now) : 1.0f;
        allOn = !anything;
        GlStateManager._bindTexture(texture);
        Level0WallGrid.unpackDefaults();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, TEXELS, TEXELS, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, data);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private static void create() {
        texture = GlStateManager._genTexture();
        data = MemoryUtil.memAlloc(TEXELS * TEXELS);
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        Level0WallGrid.unpackDefaults();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, TEXELS, TEXELS, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        allOn = false; // the first tick fills it
    }
}
