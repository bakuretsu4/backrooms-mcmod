package com.rickr.backrooms.mixin.client;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets client/audio/Acoustics find the OpenAL channel of a sound that is playing (to muffle it as
 * walls come between you and it) and run work on the sound thread (where OpenAL is driven).
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    /** Every sound playing right now and its channel. Only read it on the client (main) thread. */
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> backrooms$instanceToChannel();

    @Accessor("executor")
    SoundEngineExecutor backrooms$executor();
}
