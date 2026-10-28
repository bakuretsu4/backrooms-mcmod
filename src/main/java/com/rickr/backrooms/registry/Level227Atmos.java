package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VoidBubble;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Level 227 "Megaflora": the atmosphere's sounds and music, the fallen void bubble, and the
 * sunlight that kills. Registered through the shared {@link ModSounds#SOUNDS} and
 * {@link ModEntities#ENTITIES} registers; {@link #bootstrap()} (called from {@code Backrooms}) only
 * makes Java load this class so the fields below exist before the registers fire.
 *
 * <p>The .ogg files are made by {@code tools/anomaly_assets/level227_audio.py} and mapped (with
 * their subtitles) in {@code assets/backrooms/sounds.json}. The creatures' and plants' own sounds
 * live with them (Level227FloraSounds and the mob holders).
 */
public final class Level227Atmos {
    // Ambient loops (client/Level227Audio): the forest on a branch (wind in an ocean of leaves,
    // wrong birdsong, insects), the hollow wind rising out of the void past the edges (played at
    // the edge itself, so you hear where the drop is), and the sun's faint choir in your head.
    public static final DeferredHolder<SoundEvent, SoundEvent> FOREST = register("ambient.level227_forest");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_WIND = register("ambient.level227_void_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUN_VOICE = register("ambient.level227_sun_voice");

    // Sunbeams: the hum of a shaft of light (played at the nearest beam), the moment one finds you,
    // and turning to ash.
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM_HUM = register("level227.beam_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUN_BURN = register("level227.sun_burn");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISINTEGRATE = register("level227.disintegrate");
    /** A whispered phrase, played with the sun's rare line in the action bar. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUN_WHISPER = register("level227.sun_whisper");

    // The tree moving: a lurch everyone feels, the shudder before it shakes you loose into another
    // level, and the pull that drags you in from a forest somewhere else.
    public static final DeferredHolder<SoundEvent, SoundEvent> LURCH = register("level227.lurch");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNSTABLE = register("level227.unstable");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAG = register("level227.drag");

    // A fallen void bubble: its glassy shimmer and stepping into it.
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE_HUM = register("entity.void_bubble.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE_ENTER = register("entity.void_bubble.enter");

    // Far-off one-off sounds (client/audio/DistantAmbience): a far branch groaning as the tree
    // moves, a giant limb cracking in the canopy, a bird call that is wrong, something big calling
    // across the forest, a leaf as big as a sail sliding down through the canopy, the void howling.
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_TREE_GROAN = register("distant.level227.tree_groan");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_BRANCH_CRACK = register("distant.level227.branch_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_WRONG_BIRD = register("distant.level227.wrong_bird");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_ANIMAL_CALL = register("distant.level227.animal_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_LEAF_FALL = register("distant.level227.leaf_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_VOID_HOWL = register("distant.level227.void_howl");

    /** Level 227's own piece of music (client/BackroomsMusic): wonder turning into dread. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MEGAFLORA = register("music.megaflora");

    /**
     * A bubble that fell out of the sun's orbit and drifts by a branch edge (entity/VoidBubble).
     * Big (3 blocks across), never saved for long (it sinks into the void after a few minutes),
     * seen from far off: tracked 8 chunks away so it shows over the void long before you reach it.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidBubble>> VOID_BUBBLE = ModEntities.ENTITIES.register("void_bubble",
            () -> EntityType.Builder.<VoidBubble>of(VoidBubble::new, MobCategory.MISC)
                    .sized(3.0f, 3.0f).clientTrackingRange(8).updateInterval(2).fireImmune()
                    .build(Backrooms.id("void_bubble").toString()));

    /** The Sun over the Void's light (data/backrooms/damage_type/sunlight.json): armour, shields and potions don't help. */
    public static final ResourceKey<DamageType> SUNLIGHT = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("sunlight"));

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers everything above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private Level227Atmos() {}
}
