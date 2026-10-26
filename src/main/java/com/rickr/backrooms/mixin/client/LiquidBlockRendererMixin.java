package com.rickr.backrooms.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.client.water.VanillaWaterHider;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the Poolrooms and Level 37 the water surface is drawn by our own renderer
 * ({@code client/water/WaterRenderer}), so vanilla's water faces must not be built into the
 * chunk meshes there. Water blocks themselves are untouched: swimming, "is in water", the
 * hallucination wet meter and the drain all keep working.
 *
 * <p>Runs on the chunk-building worker threads, so it only asks a cheap, thread-safe question.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void backrooms$skipPoolWater(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer,
                                         BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (fluidState.getType().isSame(Fluids.WATER) && VanillaWaterHider.hideVanillaWater()) {
            ci.cancel();
        }
    }
}
