package com.rickr.backrooms.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The limb swing behind a walking body. A Level 228 reflection that lies (lags behind you, stands
 * still, walks away on its own: client/level228/Level228Lies) is drawn with its own swing, set
 * here for the one draw and put back right after.
 */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld")
    float backrooms$speedOld();

    @Accessor("speedOld")
    void backrooms$setSpeedOld(float value);

    @Accessor("speed")
    float backrooms$speed();

    @Accessor("speed")
    void backrooms$setSpeed(float value);

    @Accessor("position")
    float backrooms$position();

    @Accessor("position")
    void backrooms$setPosition(float value);
}
