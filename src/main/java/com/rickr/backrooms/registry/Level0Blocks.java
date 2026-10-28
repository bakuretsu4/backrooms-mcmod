package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.Level0ConduitBlock;
import com.rickr.backrooms.block.Level0EmbeddedBlock;
import com.rickr.backrooms.block.Level0GraffitiBlock;
import com.rickr.backrooms.block.Level0MannequinBlock;
import com.rickr.backrooms.block.Level0TubeGlow;
import com.rickr.backrooms.block.Level0TubeStripBlock;
import com.rickr.backrooms.block.Level0TapeBlock;
import com.rickr.backrooms.block.SurfaceBlock;
import com.rickr.backrooms.block.Level0SurfaceBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallMountBlock;
import java.util.function.Function;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The extra blocks of the Level 0 revamp, which makes the Lobby look like Kane Pixels' series and
 * the A24 film "Backrooms": the film's chevron paper and chest-high partitions, carpeted ramps,
 * the long corridor light strip, missing ceiling tiles, the exit-sign alcove, switch boxes, wall
 * scrawls, tape lines and the odd objects left lying about (mannequins, the throne, a hazmat case).
 * The lobby's own wallpaper, carpet, ceiling tile and troffer stay in {@link ModBlocks}.
 *
 * <p>Like every Backrooms block they go through the shared {@link ModBlocks#BLOCKS} register, and
 * their items through {@link ModItems#ITEMS}. The mod constructor only calls {@link #bootstrap()},
 * which makes Java load this class in time. Textures and models come from
 * tools/anomaly_assets/level0_revamp.py.
 *
 * <p>Where things go (the world generator, world/layout/Level0Layout, places them):
 * <ul>
 *   <li>FULL blocks that take the place of a wall block: the chevron papers, GRAFFITI, WALL_HOLE,
 *       HAZARD_FRAME. GRAFFITI and WALL_HOLE show their drawing/recess on the FACING side.</li>
 *   <li>Full blocks that take the place of a ceiling tile: TROFFER_STRIP(_OFF), CEILING_HOLE.</li>
 *   <li>Thin things in the AIR block in front of a wall, FACING pointing away from the wall into
 *       the room: EXIT_SIGN, SWITCH_BOX, CONDUIT, TAPE (TAPE also on the ceiling/floor), LADDER.</li>
 *   <li>Free-standing: HALF_WALL_CAP (on top of a wall block), the carpet ramps, MANNEQUIN (two
 *       blocks), THRONE, HAZMAT_CASE, DANGLING_CABLE (in the air block under the ceiling).</li>
 *   <li>EMBEDDED: furniture fused into the building, in the AIR block next to the wall, floor or
 *       ceiling it has noclipped into (see block/Level0EmbeddedBlock).</li>
 * </ul>
 */
public final class Level0Blocks {

    // ================================================================== Walls
    /**
     * The film's "Classic" paper: tone-on-tone vertical chevron columns (used only in some zones).
     * Three versions: plain, faintly discoloured, scuffed; aged ones cluster in worn patches (see
     * block/Level0SurfaceBlock). Counts must match gen_assets SURFACE_VARIANTS.
     */
    public static final DeferredBlock<SurfaceBlock> CHEVRON_WALLPAPER = ModBlocks.level0Surface("l0_wallpaper_chevron", MapColor.COLOR_YELLOW, SoundType.WOOD,
            new int[] {14, 1, 2}, new int[] {7, 4, 2}, Level0SurfaceBlock.ANY_ROW, Level0SurfaceBlock.ANY_ROW, Level0SurfaceBlock.ANY_ROW);
    /** The same paper with the slim matching baseboard: plain, rising damp, kick marks. */
    public static final DeferredBlock<SurfaceBlock> CHEVRON_WALLPAPER_BASEBOARD = ModBlocks.level0Surface("l0_wallpaper_chevron_baseboard", MapColor.COLOR_YELLOW, SoundType.WOOD,
            new int[] {14, 1, 2}, new int[] {7, 5, 2}, Level0SurfaceBlock.ANY_ROW, Level0SurfaceBlock.ANY_ROW, Level0SurfaceBlock.ANY_ROW);
    /**
     * The top block of a chest-high partition (the film's half-walls): lobby wallpaper up to 8 px,
     * then a dark wooden cap rail from 8 to 10 px, nothing above. Put it on top of a wall block.
     * FACING = one of the two faces of the partition; the rail runs across FACING, along the wall.
     */
    public static final DeferredBlock<FurnitureBlock> HALF_WALL_CAP = block("l0_half_wall_cap",
            props -> new FurnitureBlock(props, Block.box(0, 0, 0, 16, 10, 16)), unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD).noOcclusion());
    /** Wall with a frameless dark square hole sunk into its FACING side (the hole at the top of the ladder). */
    public static final DeferredBlock<WallDecalBlock> WALL_HOLE = block("l0_wall_hole", WallDecalBlock::new,
            unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD).noOcclusion());
    /** Wall with scrawled words on its FACING side; VARIANT = which drawing, PART = left/right half. */
    public static final DeferredBlock<Level0GraffitiBlock> GRAFFITI = block("l0_graffiti", Level0GraffitiBlock::new,
            unbreakable(MapColor.COLOR_YELLOW, SoundType.WOOD));
    /** Green and white diagonal hazard stripes lining an exit alcove (all six sides striped). */
    public static final DeferredBlock<Block> HAZARD_FRAME = structural("l0_hazard_frame", MapColor.COLOR_GREEN, SoundType.METAL);

    // ================================================================== Floor
    /** Carpeted ramp pieces for the film's steep ramps into low passages (lobby carpet all over). */
    public static final DeferredBlock<StairBlock> CARPET_STAIRS = block("l0_carpet_stairs",
            props -> new StairBlock(ModBlocks.LOBBY_CARPET.get().defaultBlockState(), props), unbreakable(MapColor.TERRACOTTA_YELLOW, SoundType.WOOL));
    public static final DeferredBlock<SlabBlock> CARPET_SLAB = block("l0_carpet_slab", SlabBlock::new,
            unbreakable(MapColor.TERRACOTTA_YELLOW, SoundType.WOOL));

    // ================================================================== Ceiling
    /**
     * One block of the film's long narrow corridor light: a strip of lens running along AXIS down
     * the middle of a ceiling block, so a row of them reads as one continuous strip. Its GLOW (age)
     * sets its light level, 12 for a full tube down to 6 for a dying one. Glows full-bright, except
     * while a faulty strip is dark or dying (see block/Level0TubeGlow).
     */
    public static final DeferredBlock<Level0TubeStripBlock> TROFFER_STRIP = block("l0_troffer_strip",
            props -> new Level0TubeStripBlock(props, Shapes.block()),
            fixture(MapColor.SNOW, SoundType.GLASS, 12).lightLevel(Level0TubeGlow::lightOf).emissiveRendering(Level0TubeGlow::emissive));
    /** The same strip, dead: grey lens, the tube faintly visible. */
    public static final DeferredBlock<AxisShapeBlock> TROFFER_STRIP_OFF = block("l0_troffer_strip_off",
            props -> new AxisShapeBlock(props, Shapes.block()), unbreakable(MapColor.COLOR_LIGHT_GRAY, SoundType.GLASS));
    /** A missing ceiling tile: the T-bar grid frames a dark recess with a duct and a joist up in it. */
    public static final DeferredBlock<Block> CEILING_HOLE = block("l0_ceiling_hole", Block::new,
            unbreakable(MapColor.COLOR_BLACK, SoundType.WOOD).noOcclusion());
    /** A loose cable hanging out of the ceiling (walk-through); FACING turns its curl. */
    public static final DeferredBlock<FurnitureBlock> DANGLING_CABLE = block("l0_dangling_cable",
            props -> new FurnitureBlock(props, Block.box(6, 2, 6, 10, 16, 10)),
            unbreakable(MapColor.COLOR_BLACK, SoundType.WOOL).noOcclusion().noCollission());

    // ================================================================== On the walls
    /** The green running-man EXIT sign over the exit alcove; lit, light 6. */
    public static final DeferredBlock<WallMountBlock> EXIT_SIGN = block("l0_exit_sign",
            props -> new WallMountBlock(props, 1, 4, 15, 15, 11, 16), fixture(MapColor.COLOR_GREEN, SoundType.METAL, 6).noOcclusion());
    /** A grey wall switch box (two toggles) with a conduit stub coming out of its bottom. */
    public static final DeferredBlock<WallMountBlock> SWITCH_BOX = block("l0_switch_box",
            props -> new WallMountBlock(props, 5, 0, 14.5, 11, 13, 16), unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());
    /** Surface conduit on a wall; VERTICAL = runs up and down, otherwise along the wall. Walk-through. */
    public static final DeferredBlock<Level0ConduitBlock> CONDUIT = block("l0_conduit", Level0ConduitBlock::new,
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion().noCollission());
    /** Teal tape on a wall, the ceiling or the floor (FACING = away from the surface; SHAPE = its run). */
    public static final DeferredBlock<Level0TapeBlock> TAPE = block("l0_tape", Level0TapeBlock::new,
            unbreakable(MapColor.COLOR_CYAN, SoundType.WOOL).noOcclusion().noCollission().replaceable());
    /** A plain painted steel ladder against a wall (climbable). */
    public static final DeferredBlock<LadderBlock> LADDER = block("l0_ladder", LadderBlock::new,
            unbreakable(MapColor.METAL, SoundType.METAL).noOcclusion());

    // ================================================================== Things left lying about
    /** A featureless grey mannequin on a stand, two blocks tall (HALF lower/upper); FACING = where it looks. */
    public static final DeferredBlock<Level0MannequinBlock> MANNEQUIN = block("l0_mannequin", Level0MannequinBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_GRAY, SoundType.WOOD).noOcclusion());
    /** The lone throne: a gilded frame chair with a purple seat and back; FACING = the way you'd sit. */
    public static final DeferredBlock<FurnitureBlock> THRONE = block("l0_throne",
            props -> new FurnitureBlock(props, Shapes.or(Block.box(2, 0, 2, 14, 8, 14), Block.box(2, 8, 11.5, 14, 16, 14))),
            unbreakable(MapColor.COLOR_PURPLE, SoundType.WOOD).noOcclusion());
    /** An abandoned Async Research hard equipment case; FACING = its latch side. */
    public static final DeferredBlock<FurnitureBlock> HAZMAT_CASE = block("l0_hazmat_case",
            props -> new FurnitureBlock(props, Block.box(1.5, 0, 4, 14.5, 7, 12.5)), unbreakable(MapColor.COLOR_BLACK, SoundType.METAL).noOcclusion());
    /**
     * Furniture that has noclipped into the building (the film, Kane's "Static Dead End"): MOUNT =
     * wall/floor/ceiling it is fused with, PROP = which piece, FACING turns it.
     */
    public static final DeferredBlock<Level0EmbeddedBlock> EMBEDDED = block("l0_embedded", Level0EmbeddedBlock::new,
            unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());

    static {
        // Block items so builders can use every Level 0 block in creative (the mannequin needs the tall item).
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {
                CHEVRON_WALLPAPER, CHEVRON_WALLPAPER_BASEBOARD, HALF_WALL_CAP, WALL_HOLE, GRAFFITI, HAZARD_FRAME,
                CARPET_STAIRS, CARPET_SLAB, TROFFER_STRIP, TROFFER_STRIP_OFF, CEILING_HOLE, DANGLING_CABLE,
                EXIT_SIGN, SWITCH_BOX, CONDUIT, TAPE, LADDER, THRONE, HAZMAT_CASE, EMBEDDED}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
        ModItems.ITEMS.registerItem("l0_mannequin", props -> new DoubleHighBlockItem(MANNEQUIN.get(), props), new Item.Properties());
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in ModBlocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    private static DeferredBlock<Block> structural(String name, MapColor color, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(name, (Function<BlockBehaviour.Properties, Block>) Block::new, unbreakable(color, sound));
    }

    /** A glowing block: vanilla light, and its faces always render full-bright. */
    private static BlockBehaviour.Properties fixture(MapColor color, SoundType sound, int light) {
        return unbreakable(color, sound)
                .lightLevel(state -> light)
                .emissiveRendering((state, level, pos) -> true);
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

    private Level0Blocks() {}
}
