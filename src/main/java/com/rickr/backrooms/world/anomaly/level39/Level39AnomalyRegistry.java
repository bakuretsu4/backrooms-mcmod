package com.rickr.backrooms.world.anomaly.level39;

import com.mojang.serialization.Codec;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything Level 39's anomalies add to the game: their sounds, the Window Shadow entity that
 * lives in the superstructure's glass, the Hydrolitis status effect and the infection counter
 * behind it. Sounds, entities and player data go through the shared registers; status effects
 * have no shared register in this mod yet, so this class has its own small one and hooks it up in
 * {@link #bootstrap(IEventBus)} (called once from the mod constructor).
 *
 * <p>The .ogg files and textures are made by {@code tools/anomaly_assets/level39_anoms.py}.
 */
public final class Level39AnomalyRegistry {
    // ---- Windows (Entity 2) ----------------------------------------------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_WAKE = sound("level39_anoms.window_wake");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_LURE = sound("level39_anoms.window_lure");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_BLINDS = sound("level39_anoms.window_blinds");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_SHRIEK = sound("level39_anoms.window_shriek");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_SHARDS = sound("level39_anoms.glass_shards");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_GRAB = sound("level39_anoms.window_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_FUMES = sound("level39_anoms.window_fumes");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINDOW_NOCLIP = sound("level39_anoms.window_noclip");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASH_SMELL = sound("level39_anoms.ash_smell");
    // ---- Houses ----------------------------------------------------------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUSE_SCREAM = sound("level39_anoms.house_scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_VANISH = sound("level39_anoms.door_vanish");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIGGER_INSIDE = sound("level39_anoms.bigger_inside");
    // ---- Holes -----------------------------------------------------------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLE_SCRAPE = sound("level39_anoms.hole_scrape");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLE_BREATH = sound("level39_anoms.hole_breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLE_GROWL = sound("level39_anoms.hole_growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLE_CLIMB = sound("level39_anoms.hole_climb");
    // ---- Hydrolitis ------------------------------------------------------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDROLITIS_CHILLS = sound("level39_anoms.hydrolitis_chills");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDROLITIS_HEARTBEAT = sound("level39_anoms.hydrolitis_heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDROLITIS_RETCH = sound("level39_anoms.hydrolitis_retch");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDROLITIS_WHISPER = sound("level39_anoms.hydrolitis_whisper");
    // ---- The M.E.G. camp -------------------------------------------------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMP_STASH = sound("level39_anoms.camp_stash");

    /**
     * The Window Shadow: a flat, invisible-to-touch presence pressed into one mill window (3 wide,
     * 5 tall). It never moves; it wakes, lures, throws glass, grabs and lets go. Not saved: the
     * facade wakes its windows again whenever someone comes close.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<WindowShadow>> WINDOW_SHADOW = ModEntities.ENTITIES.register(
            "window_shadow", () -> EntityType.Builder.<WindowShadow>of(WindowShadow::new, MobCategory.MISC)
                    .sized(3.0f, 5.0f)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build(Backrooms.id("window_shadow").toString()));

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Backrooms.MOD_ID);

    /** Hydrolitis, the plague of the stagnant water. Its amplifier is the stage (0 fever .. 3 delirium). */
    public static final DeferredHolder<MobEffect, HydrolitisEffect> HYDROLITIS = EFFECTS.register("hydrolitis", HydrolitisEffect::new);

    /**
     * How infected you are (0 = healthy .. {@link Hydrolitis#MAX}). Saved with the player, so it
     * survives a relog; not copied on death (dying cures you).
     */
    public static final Supplier<AttachmentType<Integer>> INFECTION = ModAttachments.ATTACHMENTS.register("hydrolitis",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    /** Death by a Window (its shards and its grip) and by the Hydrolitis fever (data/backrooms/damage_type). */
    public static final ResourceKey<DamageType> WINDOW_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("window"));
    public static final ResourceKey<DamageType> HYDROLITIS_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("hydrolitis"));

    public static DamageSource damage(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (registering everything above) and hooks the status-effect register to the mod bus. */
    public static void bootstrap(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    private Level39AnomalyRegistry() {}
}
