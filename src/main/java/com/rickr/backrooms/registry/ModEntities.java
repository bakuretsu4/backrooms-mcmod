package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Bacteria;
import com.rickr.backrooms.entity.Observer;
import com.rickr.backrooms.entity.PhantomFriend;
import com.rickr.backrooms.entity.Smiler;
import com.rickr.backrooms.entity.Watcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Backrooms.MOD_ID);

    /**
     * Kane Pixels' "Bacteria": very tall, wire-thin, black. The hitbox is only 2.9 blocks tall so it
     * fits through the 3-block doorways of Levels 0 and 1 and the corridors of Level 2; the
     * model stands about 3.4 blocks tall in open rooms and stoops under anything lower.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Bacteria>> BACTERIA = ENTITIES.register("bacteria",
            () -> EntityType.Builder.of(Bacteria::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.9f)
                    .eyeHeight(2.6f)
                    .clientTrackingRange(12)
                    .build(Backrooms.id("bacteria").toString()));

    /**
     * The Poolrooms hallucination: a fake copy of a friend that only one player can see. Player-sized
     * hitbox and eye height so it moves and looks exactly like a real player. Never saved or summoned:
     * it only exists while it haunts someone.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomFriend>> PHANTOM_FRIEND = ENTITIES.register("phantom_friend",
            () -> EntityType.Builder.<PhantomFriend>of(PhantomFriend::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .build(Backrooms.id("phantom_friend").toString()));

    /**
     * Level 9's Neighborhood Watch: a car-sized floating eyeball (3.8 blocks across, "eyes" in its centre).
     * Tracked from far away (10 chunks) so its searchlight shows up down long streets. Not saved:
     * {@code Level9Spawner} keeps the streets watched.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Watcher>> WATCHER = ENTITIES.register("watcher",
            () -> EntityType.Builder.<Watcher>of(Watcher::new, MobCategory.MONSTER)
                    .sized(Watcher.SIZE, Watcher.SIZE)
                    .eyeHeight(Watcher.HALF_SIZE)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSave()
                    .build(Backrooms.id("watcher").toString()));

    /**
     * Level 9's Observer: a tall thin silhouette that only one player can see. Taller than a player
     * (2.7 blocks). Never saved; summoned only by the spawner or /backrooms spawn observer.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Observer>> OBSERVER = ENTITIES.register("observer",
            () -> EntityType.Builder.<Observer>of(Observer::new, MobCategory.MISC)
                    .sized(0.5f, 2.7f)
                    .eyeHeight((float) Observer.HEAD_HEIGHT)
                    .clientTrackingRange(4)
                    .noSave()
                    .noSummon()
                    .build(Backrooms.id("observer").toString()));

    /**
     * Smilers: a glowing face in the dark during blackouts (Levels 0-2). Player-sized hitbox with the
     * face at eye height, so it walks the same corridors you do. Tracked from 6 chunks away because
     * the glow is visible from far off in the dark. Not saved: they only live for one blackout.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Smiler>> SMILER = ENTITIES.register("smiler",
            () -> EntityType.Builder.<Smiler>of(Smiler::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.9f)
                    .eyeHeight(Smiler.FACE_HEIGHT)
                    .clientTrackingRange(6)
                    .noSave()
                    .build(Backrooms.id("smiler").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BACTERIA.get(), Bacteria.createAttributes().build());
        event.put(PHANTOM_FRIEND.get(), PhantomFriend.createAttributes().build());
        event.put(WATCHER.get(), Watcher.createAttributes().build());
        event.put(OBSERVER.get(), Observer.createAttributes().build());
        event.put(SMILER.get(), Smiler.createAttributes().build());
    }

    private ModEntities() {}
}
