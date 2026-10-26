package com.rickr.backrooms.mixin.client;

import com.rickr.backrooms.player.PoolWaterSounds;
import java.util.function.BiPredicate;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets you run through the knee-deep water of the Poolrooms.
 *
 * <p>Vanilla stops you sprinting the moment you are "in water" without being fully under it
 * (the idea being that you either walk through water or swim). That makes sense for rivers,
 * but the Poolrooms' shallows only reach your knees: you should be able to run through them,
 * splashing. So, only while you are wading in the Poolrooms or Level 37, the sprint checks in
 * {@code LocalPlayer.aiStep} are told you are not in water. Everything else (water physics,
 * swimming, "is in water" anywhere else) is untouched, and the water still slows you down.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isInWater()Z"))
    private boolean backrooms$wadingIsNotInWater(LocalPlayer self) {
        return self.isInWater() && !PoolWaterSounds.wadingInPoolLevel(self);
    }

    /** NeoForge also asks "in a swimmable fluid?" (water counts) in the same sprint checks. */
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isInFluidType(Ljava/util/function/BiPredicate;)Z"))
    private boolean backrooms$wadingIsNotInFluid(LocalPlayer self, BiPredicate<FluidType, Double> predicate) {
        return self.isInFluidType(predicate) && !PoolWaterSounds.wadingInPoolLevel(self);
    }
}
