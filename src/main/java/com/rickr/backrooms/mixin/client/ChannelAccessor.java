package com.rickr.backrooms.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The OpenAL source behind a Minecraft sound channel (client/audio/Acoustics filters it). */
@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("source")
    int backrooms$source();
}
