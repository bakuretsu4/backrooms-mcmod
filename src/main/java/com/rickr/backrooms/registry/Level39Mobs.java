package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Faceling;
import com.rickr.backrooms.entity.Hound;
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
 * Everything that lives in Level 39 "Megalophobia": the entity types, their sounds and their spawn
 * eggs. They go through the shared registers ({@link ModEntities#ENTITIES}, {@link ModSounds#SOUNDS},
 * {@link ModItems#ITEMS}) so nothing new has to be wired into the mod constructor except
 * {@link #bootstrap()}, which only makes Java load this class (and with it every field below)
 * before the registers fire.
 *
 * <p>The attributes (health, speed, damage) are handed to the game by {@link #registerAttributes},
 * which NeoForge finds through the {@code @EventBusSubscriber} annotation.
 *
 * <p>Who spawns where is decided by {@code entity/Level39Spawner}, not by vanilla mob spawning.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Mobs {

    // ================================================================== Hounds (Entity 8)

    /**
     * Hounds: a starved human body running on all fours. The hitbox is low and long-ish (1.0 wide,
     * 1.15 tall) so it fits the tunnel rims and squeezes under things a standing person can't, and
     * its eyes sit low, behind the curtain of hair. Not saved: the spawner keeps the tunnels stocked.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Hound>> HOUND = ModEntities.ENTITIES.register("hound",
            () -> EntityType.Builder.<Hound>of(Hound::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.15f)
                    .eyeHeight(0.95f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(Backrooms.id("hound").toString()));

    // Hound sounds. The growl is the important one: it always plays before an attack, so it is
    // the player's cue to hide or back off.
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_GROWL = sound("entity.hound.growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_SNARL = sound("entity.hound.snarl");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_BREATH = sound("entity.hound.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_STEP = sound("entity.hound.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_SPLASH = sound("entity.hound.splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_BITE = sound("entity.hound.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_WHINE = sound("entity.hound.whine");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_HURT = sound("entity.hound.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_DEATH = sound("entity.hound.death");

    // ================================================================== Facelings (Entity 9)

    /**
     * Facelings: adult-sized, player-shaped mannequins without a face. Player-sized hitbox so they
     * use the same doors and paths you do. Not saved: the spawner places them.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Faceling>> FACELING = ModEntities.ENTITIES.register("faceling",
            () -> EntityType.Builder.<Faceling>of(Faceling::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.9f)
                    .eyeHeight(1.7f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(Backrooms.id("faceling").toString()));

    // Faceling sounds: no voice, only breath through a mouth that isn't there.
    public static final DeferredHolder<SoundEvent, SoundEvent> FACELING_WHEEZE = sound("entity.faceling.wheeze");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACELING_RASP = sound("entity.faceling.rasp");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACELING_STEP = sound("entity.faceling.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACELING_HURT = sound("entity.faceling.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACELING_DEATH = sound("entity.faceling.death");

    // ================================================================== Spawn eggs (creative tab lists every item)

    public static final DeferredItem<DeferredSpawnEggItem> HOUND_SPAWN_EGG = ModItems.ITEMS.registerItem("hound_spawn_egg",
            props -> new DeferredSpawnEggItem(HOUND, 0x1A1714, 0xB9A58C, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> FACELING_SPAWN_EGG = ModItems.ITEMS.registerItem("faceling_spawn_egg",
            props -> new DeferredSpawnEggItem(FACELING, 0xD8CFC4, 0x4A4C52, props), new Item.Properties());

    // --- L39Mobs2 (Clumps, Skin-stealers, Wretches) ---

    // ================================================================== Clumps (Entity 5)

    /**
     * Clumps: a person-sized knot of tangled limbs wedged into a drain or an alcove. 1.6 wide so
     * it fills a 2x2 drain shaft; it never walks, so the hitbox only has to match the body. Its
     * long arm (6 blocks) is drawn outside the hitbox. Not saved: the spawner hides new ones.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.rickr.backrooms.entity.Clump>> CLUMP = ModEntities.ENTITIES.register("clump",
            () -> EntityType.Builder.<com.rickr.backrooms.entity.Clump>of(com.rickr.backrooms.entity.Clump::new, MobCategory.MONSTER)
                    .sized(1.6f, 1.8f)
                    .eyeHeight(0.9f)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSave()
                    .build(Backrooms.id("clump").toString()));

    // Clump sounds. The knuckle crack is the tell: it always comes right before the strike.
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_CREAK = sound("entity.clump.creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_CRACK = sound("entity.clump.crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_CLAP = sound("entity.clump.clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_POUND = sound("entity.clump.pound");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_STRIKE = sound("entity.clump.strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_GRAB = sound("entity.clump.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_DRAG = sound("entity.clump.drag");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_HURT = sound("entity.clump.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUMP_DEATH = sound("entity.clump.death");

    // ================================================================== Skin-stealers (Entity 10)

    /**
     * Skin-stealers: a tall, gaunt humanoid (about 2.8 blocks) in a loose suit of stolen skin.
     * The hitbox is 2.7 tall so it still fits the tunnel rims under the vault. Not saved.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.rickr.backrooms.entity.SkinStealer>> SKIN_STEALER = ModEntities.ENTITIES.register("skin_stealer",
            () -> EntityType.Builder.<com.rickr.backrooms.entity.SkinStealer>of(com.rickr.backrooms.entity.SkinStealer::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.7f)
                    .eyeHeight(2.45f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(Backrooms.id("skin_stealer").toString()));

    // Skin-stealer sounds. The mimic is the lure: garbled "words" called from somewhere unseen.
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_MIMIC = sound("entity.skin_stealer.mimic");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_BREATH = sound("entity.skin_stealer.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_STEP = sound("entity.skin_stealer.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_HUNT = sound("entity.skin_stealer.hunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_TEAR = sound("entity.skin_stealer.tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_HURT = sound("entity.skin_stealer.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKIN_STEALER_DEATH = sound("entity.skin_stealer.death");

    // ================================================================== Wretches (Entity 15 / Phenomenon 35)

    /** Wretches: emaciated, hunched people (1.6 tall bent over) in the last stage of Roth syndrome. Not saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.rickr.backrooms.entity.Wretch>> WRETCH = ModEntities.ENTITIES.register("wretch",
            () -> EntityType.Builder.<com.rickr.backrooms.entity.Wretch>of(com.rickr.backrooms.entity.Wretch::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.6f)
                    .eyeHeight(1.35f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(Backrooms.id("wretch").toString()));

    // Wretch sounds: pain and panic. The scream means it is running from you; the sobbing means
    // it is cornered and about to lash out.
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_MOAN = sound("entity.wretch.moan");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_SOB = sound("entity.wretch.sob");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_SCREAM = sound("entity.wretch.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_SPLASH = sound("entity.wretch.splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_DRINK = sound("entity.wretch.drink");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_STEP = sound("entity.wretch.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_HURT = sound("entity.wretch.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRETCH_DEATH = sound("entity.wretch.death");

    public static final DeferredItem<DeferredSpawnEggItem> CLUMP_SPAWN_EGG = ModItems.ITEMS.registerItem("clump_spawn_egg",
            props -> new DeferredSpawnEggItem(CLUMP, 0xB59C8C, 0x4A2A28, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> SKIN_STEALER_SPAWN_EGG = ModItems.ITEMS.registerItem("skin_stealer_spawn_egg",
            props -> new DeferredSpawnEggItem(SKIN_STEALER, 0xE3D58E, 0xC99A86, props), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> WRETCH_SPAWN_EGG = ModItems.ITEMS.registerItem("wretch_spawn_egg",
            props -> new DeferredSpawnEggItem(WRETCH, 0xA89186, 0x8E2A22, props), new Item.Properties());
    // --- end L39Mobs2 ---

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HOUND.get(), Hound.createAttributes().build());
        event.put(FACELING.get(), Faceling.createAttributes().build());
        // --- L39Mobs2 attributes ---
        event.put(CLUMP.get(), com.rickr.backrooms.entity.Clump.createAttributes().build());
        event.put(SKIN_STEALER.get(), com.rickr.backrooms.entity.SkinStealer.createAttributes().build());
        event.put(WRETCH.get(), com.rickr.backrooms.entity.Wretch.createAttributes().build());
        // --- end L39Mobs2 attributes ---
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    private Level39Mobs() {}
}
