package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VenusFlytrap;
import com.rickr.backrooms.item.LestronItem;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The living things of Level 227's flora that aren't plain blocks: the giant Venus flytrap (its
 * entity type and spawn egg), the Lestron herb item, the GammaPali spore haze and falling-leaf
 * particles, and the damage types of the plants that kill (data/backrooms/damage_type).
 * The blocks are in {@link Level227Blocks}, the sounds in {@link Level227FloraSounds}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Flora {

    /**
     * The giant Venus flytrap. The hitbox is the low rosette (3 wide, 0.6 tall): it lies in the
     * grass and never moves; the open lobes and the lifted head are drawn outside it. Not saved:
     * entity/Level227FloraSpawner plants them at the layout's flytrap spots near players.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VenusFlytrap>> VENUS_FLYTRAP = ModEntities.ENTITIES.register("venus_flytrap",
            () -> EntityType.Builder.<VenusFlytrap>of(VenusFlytrap::new, MobCategory.MONSTER)
                    .sized(3.0f, 0.6f)
                    .eyeHeight(0.4f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(Backrooms.id("venus_flytrap").toString()));

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_FLYTRAP_SPAWN_EGG = ModItems.ITEMS.registerItem("venus_flytrap_spawn_egg",
            props -> new DeferredSpawnEggItem(VENUS_FLYTRAP, 0x3E6B1F, 0xB0262A, props), new Item.Properties());

    /** A few sprigs of Lestron, picked from the plant at a branch's edge. */
    public static final DeferredItem<LestronItem> LESTRON = ModItems.ITEMS.registerItem("lestron",
            LestronItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));

    /** The faint glowing yellow-green haze round a GammaPali: its toxic gas, drawn full-bright. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPORE_HAZE = ModParticles.PARTICLES.register("l227_spore_haze",
            () -> new SimpleParticleType(false));
    /** A leaf come loose from the canopy, spinning down. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FALLING_LEAF = ModParticles.PARTICLES.register("l227_falling_leaf",
            () -> new SimpleParticleType(false));

    /** "was crushed by a Palette". */
    public static final ResourceKey<DamageType> PALETTE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("palette"));
    /** "breathed the glow of a GammaPali". */
    public static final ResourceKey<DamageType> GAMMAPALI_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("gammapali"));
    /** "was digested by a giant Venus flytrap". */
    public static final ResourceKey<DamageType> FLYTRAP_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("venus_flytrap"));

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(VENUS_FLYTRAP.get(), VenusFlytrap.createAttributes().build());
    }

    private Level227Flora() {}
}
