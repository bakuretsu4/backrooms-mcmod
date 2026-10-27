package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our own particles (drawn by client/particle). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Backrooms.MOD_ID);

    /** Soft wisps of steam rising off warm puddles (Level 1). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM = PARTICLES.register("steam",
            () -> new SimpleParticleType(false));
    /** Fine dust drifting in the light. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_MOTE = PARTICLES.register("dust_mote",
            () -> new SimpleParticleType(false));

    private ModParticles() {}
}
