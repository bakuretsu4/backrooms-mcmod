package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Squirrel;
import com.rickr.backrooms.entity.L227Stag;
import com.rickr.backrooms.entity.L227Wolf;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
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
 * The predators of Level 227 "Megaflora": the wolves of the high branches, the male deer (stags)
 * and the fluorescent squirrels. Their entity types, sounds and spawn eggs go through the shared
 * registers ({@link ModEntities#ENTITIES}, {@link ModSounds#SOUNDS}, {@link ModItems#ITEMS}); the
 * mod constructor only calls {@link #bootstrap()} so Java loads this class in time.
 *
 * <p>None of them is saved with the world: {@code entity/Level227PredatorSpawner} keeps the
 * branches stocked around players, so a reload simply starts fresh.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Predators {

    // ================================================================== Wolves

    /**
     * Wolves: bigger than a vanilla wolf (shoulders at about 1.2 blocks), with a long snout that
     * splits sideways. Vanilla hitboxes are square seen from above, so it is 1.1 wide; the head and
     * tail reach past it.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<L227Wolf>> WOLF = ModEntities.ENTITIES.register("l227_wolf",
            () -> EntityType.Builder.<L227Wolf>of(L227Wolf::new, MobCategory.MONSTER)
                    .sized(1.1f, 1.35f)
                    .eyeHeight(1.15f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(Backrooms.id("l227_wolf").toString()));

    // The howl is the pack's alarm: when you hear it, the rest are on their way.
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_HOWL = sound("entity.l227_wolf.howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_GROWL = sound("entity.l227_wolf.growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_YIP = sound("entity.l227_wolf.yip");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_SNAP = sound("entity.l227_wolf.snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_PANT = sound("entity.l227_wolf.pant");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_STEP = sound("entity.l227_wolf.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_HURT = sound("entity.l227_wolf.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_DEATH = sound("entity.l227_wolf.death");

    // ================================================================== Stags (male deer)

    /**
     * Stags: a deer the size of a horse and then some (withers at 1.7 blocks). The long horns stick
     * far out of the hitbox; the piercing hit is worked out from the horn tips, not from the box.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<L227Stag>> STAG = ModEntities.ENTITIES.register("l227_stag",
            () -> EntityType.Builder.<L227Stag>of(L227Stag::new, MobCategory.MONSTER)
                    .sized(1.4f, 2.3f)
                    .eyeHeight(2.15f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(Backrooms.id("l227_stag").toString()));

    // The bellow and the pawing are the warning: a charge always follows.
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_BELLOW = sound("entity.l227_stag.bellow");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_SNORT = sound("entity.l227_stag.snort");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_PAW = sound("entity.l227_stag.paw");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_CHARGE = sound("entity.l227_stag.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_IMPALE = sound("entity.l227_stag.impale");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_HORN_HIT = sound("entity.l227_stag.horn_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_STEP = sound("entity.l227_stag.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_HURT = sound("entity.l227_stag.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_DEATH = sound("entity.l227_stag.death");

    // ================================================================== Squirrels

    /** Squirrels: small (half a block), fast, climbing, glowing. They come as a swarm. */
    public static final DeferredHolder<EntityType<?>, EntityType<L227Squirrel>> SQUIRREL = ModEntities.ENTITIES.register("l227_squirrel",
            () -> EntityType.Builder.<L227Squirrel>of(L227Squirrel::new, MobCategory.MONSTER)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.38f)
                    .clientTrackingRange(6)
                    .noSave()
                    .build(Backrooms.id("l227_squirrel").toString()));

    // The swarm is heard long before it is seen: thousands of claws in the undergrowth, chattering.
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_CHITTER = sound("entity.l227_squirrel.chitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_SWARM = sound("entity.l227_squirrel.swarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_SCREECH = sound("entity.l227_squirrel.screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_BITE = sound("entity.l227_squirrel.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_STEP = sound("entity.l227_squirrel.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_HURT = sound("entity.l227_squirrel.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUIRREL_DEATH = sound("entity.l227_squirrel.death");

    // ================================================================== Damage (data/backrooms/damage_type)

    /** Run through by a stag's horns. */
    public static final ResourceKey<DamageType> STAG_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("l227_stag"));
    /** Eaten alive by the swarm (no attacker, so the bites don't knock you around). */
    public static final ResourceKey<DamageType> SQUIRREL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("l227_squirrels"));

    // ================================================================== Spawn eggs (the creative tab lists every item)

    public static final DeferredItem<DeferredSpawnEggItem> WOLF_SPAWN_EGG = ModItems.ITEMS.registerItem("l227_wolf_spawn_egg",
            props -> new DeferredSpawnEggItem(WOLF, 0x2A2C27, 0x8C9A7A, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> STAG_SPAWN_EGG = ModItems.ITEMS.registerItem("l227_stag_spawn_egg",
            props -> new DeferredSpawnEggItem(STAG, 0x4A3326, 0xC0201A, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> SQUIRREL_SPAWN_EGG = ModItems.ITEMS.registerItem("l227_squirrel_spawn_egg",
            props -> new DeferredSpawnEggItem(SQUIRREL, 0x5CFF7A, 0xE8FF3A, props), new Item.Properties());

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WOLF.get(), L227Wolf.createAttributes().build());
        event.put(STAG.get(), L227Stag.createAttributes().build());
        event.put(SQUIRREL.get(), L227Squirrel.createAttributes().build());
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    private Level227Predators() {}
}
