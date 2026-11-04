package com.rickr.backrooms.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Frustum test on a box given as six numbers. Vanilla only offers it for an AABB, and the Level
 * 228 mirror puppets (client/level228/Level228Reflections) test dozens of boxes every frame:
 * this keeps that free of allocation.
 */
@Mixin(Frustum.class)
public interface FrustumInvoker {
    @Invoker("cubeInFrustum")
    boolean backrooms$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
