package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.PoleBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.registry.ModSounds;
import java.util.function.Function;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Everything the Crimson Field chairlift (the side entrance to Level 39) is made of: the frosted
 * station and tower blocks, the chair, the seat entity you ride in and the lift's sounds.
 *
 * <p>Like every Backrooms registration they go through the shared registers
 * ({@link ModBlocks#BLOCKS}, {@link ModItems#ITEMS}, {@link ModEntities#ENTITIES},
 * {@link ModSounds#SOUNDS}); the mod constructor only calls {@link #bootstrap()}, which makes Java
 * load this class (and run every field below) before those registers fire. Textures, models and
 * sounds come from tools/anomaly_assets/level39_gondola.py.
 */
public final class GondolaRegistry {

    // ================================================================== Blocks
    /** The chair (see {@link GondolaChairBlock}); FACING = the way you look sitting in it. */
    public static final DeferredBlock<GondolaChairBlock> CHAIR = block("l39_gondola_chair", GondolaChairBlock::new,
            unbreakable(MapColor.COLOR_BLUE, SoundType.METAL).noOcclusion());
    /** Painted steel tower lattice with rime ice feathering off one side (open: you see through it). */
    public static final DeferredBlock<Block> LATTICE = block("l39_gondola_lattice", Block::new,
            seeThrough(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL));
    /** The concrete pier a tower or the station frame stands on, with the mast's base plate and bolts. */
    public static final DeferredBlock<Block> FOOTING = block("l39_gondola_footing", Block::new,
            unbreakable(MapColor.STONE, SoundType.STONE));
    /** A leaning brace from a tower's foot up to its mast; FACING points toward the mast. */
    public static final DeferredBlock<FurnitureBlock> LEG = block("l39_gondola_leg",
            props -> new FurnitureBlock(props, Block.box(5, 0, 1, 11, 16, 15)), seeThrough(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL));
    /** A tower's head: the top of the mast with the crossarm beam across AXIS. */
    public static final DeferredBlock<AxisShapeBlock> CROSSARM = block("l39_gondola_crossarm",
            props -> new AxisShapeBlock(props, Block.box(3, 0, 3, 13, 16, 13)), seeThrough(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL));
    /** The end of a crossarm with its train of sheave wheels the cable runs over; FACING = outward. */
    public static final DeferredBlock<FurnitureBlock> SHEAVE = block("l39_gondola_sheave",
            props -> new FurnitureBlock(props, Shapes.or(Block.box(0, 6, 0, 16, 16, 5), Block.box(0, 12, 0, 16, 16, 16))),
            seeThrough(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL));
    /** The station's big horizontal bull wheel (three blocks across) on its shaft. */
    public static final DeferredBlock<PoleBlock> BULLWHEEL = block("l39_gondola_bullwheel", props -> new PoleBlock(props, 3),
            seeThrough(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL));
    /** Riveted, frost-streaked sheet steel of the bull-wheel housing. */
    public static final DeferredBlock<Block> HOOD = block("l39_gondola_hood", Block::new,
            unbreakable(MapColor.COLOR_RED, SoundType.METAL));
    /** Frosted timber boards of the loading deck (a slab: you step straight up onto it). */
    public static final DeferredBlock<SlabBlock> DECK = block("l39_gondola_deck", SlabBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.WOOD));
    /** The station's enamel sign on two posts, half hidden under rime; FACING = the side you read. */
    public static final DeferredBlock<FurnitureBlock> SIGN = block("l39_gondola_sign",
            props -> new FurnitureBlock(props, Block.box(1, 0, 7, 15, 16, 9)), seeThrough(MapColor.SNOW, SoundType.METAL));

    // ================================================================== The seat entity
    /**
     * The chair while you ride it. Sized like the chair; the rider sits {@link GondolaLine#SEAT_HEIGHT}
     * above its bottom. Not saved (see {@link GondolaChair}); tracked from far enough away that
     * the chair stays visible until the fog has it.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GondolaChair>> GONDOLA_CHAIR = ModEntities.ENTITIES.register("gondola_chair",
            () -> EntityType.Builder.<GondolaChair>of(GondolaChair::new, MobCategory.MISC)
                    .sized(0.9f, 1.0f)
                    .passengerAttachments(GondolaLine.SEAT_HEIGHT)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build(Backrooms.id("gondola_chair").toString()));

    // ================================================================== Sounds
    /** Wind gusting through the cable and the chair (loop; the ride and near the line). */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("gondola.wind");
    /** The cable creaking under tension, grips ticking over it (loop). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CABLE_CREAK = sound("gondola.cable_creak");
    /** A chair's grip clattering over a tower's sheave train. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHEAVE_CLACK = sound("gondola.sheave_clack");
    /** Sitting down: the chair's clunk and swing, then the safety bar latching. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOARD = sound("gondola.board");
    /** The last seconds of the ride: a rising, airy tone as everything goes white. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT = sound("gondola.whiteout");

    static {
        // Block items so builders can use every lift block in creative (the creative tab lists every item).
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {CHAIR, LATTICE, FOOTING, LEG, CROSSARM, SHEAVE, BULLWHEEL, HOOD, DECK, SIGN}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in Level39Blocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** An open steel frame: solid, but light and the eye go straight through. */
    private static BlockBehaviour.Properties seeThrough(MapColor color, SoundType sound) {
        return unbreakable(color, sound).noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(sound)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private GondolaRegistry() {}
}
