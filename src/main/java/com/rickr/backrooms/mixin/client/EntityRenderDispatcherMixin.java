package com.rickr.backrooms.mixin.client;

import com.rickr.backrooms.client.level228.Level228Reflections;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Level 228: a real body standing beyond a mirror pane must not be seen through the glass, or the
 * twin space behind the mirror (real, walkable maze in a kaleidoscope room) would show the person
 * where their reflection can't be. The reflections draw them where the mirror puts them instead
 * (client/level228/Level228Reflections). Hooked at the dispatcher's visibility test so the whole
 * entity goes (model, name tag, shadow, fire, hitbox) and Iris's shadow pass agrees with the
 * main pass.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void backrooms$hideBehindMirror(E entity, Frustum frustum, double camX, double camY, double camZ,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (Level228Reflections.hiddenBehindMirror(entity)) {
            cir.setReturnValue(false);
        }
    }
}
