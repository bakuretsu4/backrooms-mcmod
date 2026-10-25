package com.rickr.backrooms.client;

import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * How the Backrooms dimensions render: no sky, no clouds, no distance fog, and our own lighting
 * curve ({@link ClientLighting}): a dim fill under Veil's real lights, or the full vanilla-light
 * look when Veil isn't available.
 */
public final class BackroomsDimensionEffects extends DimensionSpecialEffects {
    public BackroomsDimensionEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        // pixelX is the block light level (0..15) of this lightmap cell. There is no sky light down
        // here, so a sky level of 15 only ever comes from emissive (full-bright) blocks: the fixtures.
        ClientLighting.apply(colors, pixelX, pixelY == 15, BackroomsLevel.fromDimension(level.dimension()));
    }
}
