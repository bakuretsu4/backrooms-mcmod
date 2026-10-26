package com.rickr.backrooms.mixin;

import com.rickr.backrooms.player.PoolWaterSounds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps your feet on the pool floor while you run through the Poolrooms' shallows.
 *
 * <p>Vanilla switches gravity off in water for anyone sprinting (sprinting in water normally
 * means swimming). Since we let you run through knee-deep water ({@code mixin/client/LocalPlayerMixin}),
 * that would make you float: off a ledge you would glide along the surface instead of stepping
 * down, and you would never touch the floor, so there would be no footsteps. While wading in the
 * pool levels, gravity in water works as if you were walking.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(method = "getFluidFallingAdjustedMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"))
    private boolean backrooms$wadingRunnersStillFall(LivingEntity self) {
        return self.isSprinting() && !(self instanceof Player player && PoolWaterSounds.wadingInPoolLevel(player));
    }
}
