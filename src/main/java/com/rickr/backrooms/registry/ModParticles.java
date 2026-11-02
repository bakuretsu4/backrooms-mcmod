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

    // Level 227's pond splashes (client/water/WaterSplashes draws them from what each client sees
    // entities do; textures from tools/level227_water/gen_water.py). The spawn speed arguments carry
    // a size for the two flat ones (see client/particle/WaterSurfaceParticle).
    /** A drop thrown up by a splash: flies, falls back and vanishes into the water. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> L227_WATER_DROPLET = PARTICLES.register("l227_water_droplet",
            () -> new SimpleParticleType(false));
    /** A ripple ring spreading on the surface. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> L227_WATER_RING = PARTICLES.register("l227_water_ring",
            () -> new SimpleParticleType(false));
    /** A patch of foam lying on the surface. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> L227_WATER_FOAM = PARTICLES.register("l227_water_foam",
            () -> new SimpleParticleType(false));
    /** A bubble dragged down by a body, then rising back to the surface. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> L227_WATER_BUBBLE = PARTICLES.register("l227_water_bubble",
            () -> new SimpleParticleType(false));

    private ModParticles() {}
}
