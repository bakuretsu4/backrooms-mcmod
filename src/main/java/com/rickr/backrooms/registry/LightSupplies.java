package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.FlareEntity;
import com.rickr.backrooms.entity.GlowstickEntity;
import com.rickr.backrooms.item.FlareItem;
import com.rickr.backrooms.item.GlowstickItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Flares and glowsticks: the two items, the thrown entities they become, and their sounds.
 * Registered through the shared {@link ModItems#ITEMS}, {@link ModEntities#ENTITIES} and
 * {@link ModSounds#SOUNDS} registers; {@link #bootstrap()} (called from {@code Backrooms}) only
 * makes Java load this class so the fields below exist before the registers fire.
 *
 * <p>The .ogg files and item icons are made by {@code tools/anomaly_assets/light_supplies.py}.
 */
public final class LightSupplies {
    public static final DeferredItem<FlareItem> FLARE = ModItems.ITEMS.registerItem("flare", FlareItem::new,
            new Item.Properties().stacksTo(8));
    public static final DeferredItem<GlowstickItem> GLOWSTICK = ModItems.ITEMS.registerItem("glowstick", GlowstickItem::new,
            new Item.Properties().stacksTo(16));

    // Small, saved with the world (they keep burning/glowing where they lie), visible from far
    // enough that friends see the light of a flare down the corridor.
    public static final DeferredHolder<EntityType<?>, EntityType<FlareEntity>> FLARE_ENTITY = ModEntities.ENTITIES.register("flare",
            () -> EntityType.Builder.<FlareEntity>of(FlareEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.1f).clientTrackingRange(6).updateInterval(2).noSummon()
                    .build(Backrooms.id("flare").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<GlowstickEntity>> GLOWSTICK_ENTITY = ModEntities.ENTITIES.register("glowstick",
            () -> EntityType.Builder.<GlowstickEntity>of(GlowstickEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.1f).clientTrackingRange(6).updateInterval(2).noSummon()
                    .build(Backrooms.id("glowstick").toString()));

    // Striking a flare, its burning hiss (in air, and muffled underwater) and the sputter at the end.
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_STRIKE = register("item.flare.strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_HISS = register("entity.flare.hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_HISS_WATER = register("entity.flare.hiss_underwater");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_SPUTTER = register("entity.flare.sputter");
    // Cracking a glowstick and shaking it to mix the liquids.
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOWSTICK_CRACK = register("item.glowstick.crack");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers everything above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private LightSupplies() {}
}
