package com.rickr.backrooms.mixin.client;

import com.rickr.backrooms.client.water.WaterSurfaces;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the pool water renderer when a block changes on the client, so the water surface over
 * that spot is rebuilt (vanilla calls {@code blockChanged} for every block update the client
 * receives, single changes and multi-block packets alike).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "blockChanged", at = @At("TAIL"))
    private void backrooms$waterBlockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState,
                                             int flags, CallbackInfo ci) {
        WaterSurfaces.onBlockChanged(level, pos, oldState, newState);
    }
}
