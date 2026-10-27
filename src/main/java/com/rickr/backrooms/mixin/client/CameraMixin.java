package com.rickr.backrooms.mixin.client;

import com.rickr.backrooms.client.HidingClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While you hide in furniture, your view sits inside it instead of at your body's eyes (the body
 * waits outside, see {@code player/HidingService}). NeoForge has an event for the view's angles
 * but none for its position, hence this: after vanilla has placed the camera, move it.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void backrooms$hidingView(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                      float partialTick, CallbackInfo ci) {
        if (detached) {
            return;
        }
        Vec3 view = HidingClient.viewPosition(entity);
        if (view != null) {
            setPosition(view);
        }
    }
}
