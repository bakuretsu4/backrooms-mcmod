package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Bacteria;
import com.rickr.backrooms.entity.PhantomFriend;
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
     * fits through the 3-block doorways of Levels 0 and 1 and the 3-block corridors of Level 2; the
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

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BACTERIA.get(), Bacteria.createAttributes().build());
        event.put(PHANTOM_FRIEND.get(), PhantomFriend.createAttributes().build());
    }

    private ModEntities() {}
}
