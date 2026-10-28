package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Bear;
import com.rickr.backrooms.entity.L227Boar;
import com.rickr.backrooms.entity.L227Doe;
import com.rickr.backrooms.item.ModFoods;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The big animals of Level 227 "Megaflora" that are not hunters: the many-eyed bears asleep in
 * the branch hollows, the scarred does, and the giant boars. Their entity types, sounds, spawn
 * eggs and the boar meat all go through the shared registers ({@link ModEntities#ENTITIES},
 * {@link ModSounds#SOUNDS}, {@link ModItems#ITEMS}); {@link #bootstrap()} only makes Java load this
 * class before the registers fire.
 *
 * <p>Nothing here spawns by itself: {@code entity/Level227GrazerSpawner} places them. None of them
 * is saved with the chunk ({@code noSave}): the spawner keeps the forest stocked around players,
 * so a world never fills up with them.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Grazers {

    // ================================================================== Bears

    /**
     * Bears: fifteen eyes and six legs, about 4 blocks nose to tail. The hitbox is wide and a bit
     * lower than the standing back (1.9) so it fits the bear caves World carves into the branches.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<L227Bear>> BEAR = ModEntities.ENTITIES.register("l227_bear",
            () -> EntityType.Builder.<L227Bear>of(L227Bear::new, MobCategory.CREATURE)
                    .sized(2.2f, 1.9f)
                    .eyeHeight(1.45f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(Backrooms.id("l227_bear").toString()));

    // Bear sounds. The snore says "asleep, keep it that way"; the stir is the warning (the first
    // eyes are opening); the roar means it is up and coming.
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_SNORE = sound("entity.l227_bear.snore");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_STIR = sound("entity.l227_bear.stir");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_ROAR = sound("entity.l227_bear.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_BREATH = sound("entity.l227_bear.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_MAUL = sound("entity.l227_bear.maul");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_STEP = sound("entity.l227_bear.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_HURT = sound("entity.l227_bear.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_DEATH = sound("entity.l227_bear.death");

    // ================================================================== Does (female deer)

    /** Does: a slender deer, a little over a block at the shoulder, the head up at 1.75. */
    public static final DeferredHolder<EntityType<?>, EntityType<L227Doe>> DOE = ModEntities.ENTITIES.register("l227_doe",
            () -> EntityType.Builder.<L227Doe>of(L227Doe::new, MobCategory.CREATURE)
                    .sized(0.9f, 1.7f)
                    .eyeHeight(1.55f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(Backrooms.id("l227_doe").toString()));

    // Doe sounds: almost silent animals. The breath is the moment she notices you; the slip is the
    // sound of the branch letting you through.
    public static final DeferredHolder<SoundEvent, SoundEvent> DOE_AMBIENT = sound("entity.l227_doe.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOE_NOTICE = sound("entity.l227_doe.notice");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOE_SLIP = sound("entity.l227_doe.slip");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOE_STEP = sound("entity.l227_doe.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOE_HURT = sound("entity.l227_doe.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOE_DEATH = sound("entity.l227_doe.death");

    // ================================================================== Boars

    /**
     * Boars: the largest animals in the level, about 5.5 blocks long and 2.5 tall at the mane.
     * The hitbox stays 3.2 wide so it still walks between the forest's trunks.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<L227Boar>> BOAR = ModEntities.ENTITIES.register("l227_boar",
            () -> EntityType.Builder.<L227Boar>of(L227Boar::new, MobCategory.CREATURE)
                    .sized(3.2f, 2.4f)
                    .eyeHeight(1.6f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(Backrooms.id("l227_boar").toString()));

    // Boar sounds. The squeal is the "it's hurt, get away" cue: the thrash comes with it.
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_GRUNT = sound("entity.l227_boar.grunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_SNORE = sound("entity.l227_boar.snore");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_SQUEAL = sound("entity.l227_boar.squeal");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_THRASH = sound("entity.l227_boar.thrash");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_GORE = sound("entity.l227_boar.gore");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_STEP = sound("entity.l227_boar.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOAR_DEATH = sound("entity.l227_boar.death");

    // ================================================================== Items

    /** Raw boar meat: dark, coarse and gamey. Safe once cooked (lore), a bad idea raw. */
    public static final DeferredItem<Item> BOAR_MEAT = ModItems.ITEMS.registerSimpleItem("boar_meat",
            new Item.Properties().food(ModFoods.BOAR_MEAT));
    /** Cooked in a furnace, smoker or on a campfire: the best meal in the Backrooms. */
    public static final DeferredItem<Item> COOKED_BOAR_MEAT = ModItems.ITEMS.registerSimpleItem("cooked_boar_meat",
            new Item.Properties().food(ModFoods.COOKED_BOAR_MEAT));

    // Spawn eggs (the creative tab lists every item).
    public static final DeferredItem<DeferredSpawnEggItem> BEAR_SPAWN_EGG = ModItems.ITEMS.registerItem("l227_bear_spawn_egg",
            props -> new DeferredSpawnEggItem(BEAR, 0x231C17, 0xC8A85A, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> DOE_SPAWN_EGG = ModItems.ITEMS.registerItem("l227_doe_spawn_egg",
            props -> new DeferredSpawnEggItem(DOE, 0x9C8A74, 0x0A0909, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> BOAR_SPAWN_EGG = ModItems.ITEMS.registerItem("l227_boar_spawn_egg",
            props -> new DeferredSpawnEggItem(BOAR, 0x3E3631, 0xD9CDB0, props), new Item.Properties());

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BEAR.get(), L227Bear.createAttributes().build());
        event.put(DOE.get(), L227Doe.createAttributes().build());
        event.put(BOAR.get(), L227Boar.createAttributes().build());
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    private Level227Grazers() {}
}
