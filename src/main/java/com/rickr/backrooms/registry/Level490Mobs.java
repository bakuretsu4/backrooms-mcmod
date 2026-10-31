package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Penitent;
import com.rickr.backrooms.entity.Pilgrim;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The figures of Level 490 "Penance": the silent Penitents and the talking pilgrim. Registered
 * through the shared {@link ModEntities#ENTITIES} like {@link Level39Mobs}; {@link #bootstrap()}
 * only makes Java load this class in time. Placed by world/Level490Figures, never saved (it puts
 * them back as you come).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Mobs {
    /** Player-sized. Tracked from 8 chunks so the walkers on the bridges show from far off. */
    public static final DeferredHolder<EntityType<?>, EntityType<Penitent>> PENITENT = ModEntities.ENTITIES.register("penitent",
            () -> EntityType.Builder.<Penitent>of(Penitent::new, MobCategory.MISC)
                    .sized(0.6f, 1.9f)
                    .eyeHeight(1.6f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(Backrooms.id("penitent").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<Pilgrim>> PILGRIM = ModEntities.ENTITIES.register("pilgrim",
            () -> EntityType.Builder.<Pilgrim>of(Pilgrim::new, MobCategory.MISC)
                    .sized(0.6f, 1.85f)
                    .eyeHeight(1.6f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(Backrooms.id("pilgrim").toString()));

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PENITENT.get(), Penitent.createAttributes().build());
        event.put(PILGRIM.get(), Pilgrim.createAttributes().build());
    }

    private Level490Mobs() {}
}
