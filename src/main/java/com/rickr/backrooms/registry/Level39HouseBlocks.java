package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.Level39BulbBlock;
import com.rickr.backrooms.block.Level39FurnitureBlock;
import com.rickr.backrooms.block.Level39HouseWallBlock;
import com.rickr.backrooms.block.Level39KnockWallBlock;
import com.rickr.backrooms.block.Level39PairFurnitureBlock;
import com.rickr.backrooms.block.Level39LowRoofBlock;
import com.rickr.backrooms.block.Level39RoofBlock;
import com.rickr.backrooms.block.Level39SupplyFurnitureBlock;
import com.rickr.backrooms.block.Level39SurfaceBlock;
import com.rickr.backrooms.block.PoleBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import java.util.function.Function;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The insides (and the finishing touches outside) of the Level 39 houses, built by
 * world/layout/Level39Houses: 1920s wallpapers in Level-0-like tones, wainscot and beadboard,
 * floorboards and linoleum, the popcorn ceilings studded with upside-down bare bulbs (the level's
 * reference photo), the cracked wall sections you knock down to get in, and the period furniture,
 * every piece of which can also stand upside down (INVERTED) for the inverted houses and the
 * "furniture in odd places" anomaly.
 *
 * <p>Registered through the shared {@link ModBlocks#BLOCKS} / {@link ModItems#ITEMS} /
 * {@link ModSounds#SOUNDS} registers; {@link #bootstrap()} (called from the mod constructor) only
 * makes Java load this class in time. Furniture shapes are given facing north (front looking
 * north, back against a wall on the south side). Textures, models and the crack sound come from
 * tools/anomaly_assets/level39_houses.py and level39_houses_furniture.py.
 */
public final class Level39HouseBlocks {

    // ================================================================== Sounds
    /** A rotten stretch of house wall giving way: a splintering crack the whole town echoes back. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WALL_CRACK = ModSounds.SOUNDS.register("level39.wall_crack",
            () -> SoundEvent.createVariableRangeEvent(Backrooms.id("level39.wall_crack")));

    // ================================================================== Walls
    // 1920s wallpapers in the tones of Level 0 (mustard, cream, faded olive, dusty rose, sage), each
    // also with a skirting board along the bottom for the lowest row of a room.
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_DAMASK = surface("l39_paper_damask", MapColor.COLOR_YELLOW, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_STRIPE = surface("l39_paper_stripe", MapColor.SAND, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_FLORAL = surface("l39_paper_floral", MapColor.TERRACOTTA_WHITE, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_ROSE = surface("l39_paper_rose", MapColor.TERRACOTTA_PINK, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_LATTICE = surface("l39_paper_lattice", MapColor.TERRACOTTA_LIGHT_GREEN, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_DAMASK_BASE = surface("l39_paper_damask_base", MapColor.COLOR_YELLOW, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_STRIPE_BASE = surface("l39_paper_stripe_base", MapColor.SAND, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_FLORAL_BASE = surface("l39_paper_floral_base", MapColor.TERRACOTTA_WHITE, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_ROSE_BASE = surface("l39_paper_rose_base", MapColor.TERRACOTTA_PINK, SoundType.WOOD);
    public static final DeferredBlock<Level39SurfaceBlock> PAPER_LATTICE_BASE = surface("l39_paper_lattice_base", MapColor.TERRACOTTA_LIGHT_GREEN, SoundType.WOOD);
    /** White painted tongue-and-groove boards: the bottom row of kitchen and bathroom walls. */
    public static final DeferredBlock<Level39SurfaceBlock> BEADBOARD = surface("l39_beadboard", MapColor.SNOW, SoundType.WOOD);
    /** Dark stained panelled wainscot with a chair rail along the top: the bottom row of the best rooms. */
    public static final DeferredBlock<Level39SurfaceBlock> WAINSCOT = surface("l39_wainscot", MapColor.COLOR_BROWN, SoundType.WOOD);
    /** Bare cracked plaster: attics, basements, garages, and the cut faces of the halved houses. */
    public static final DeferredBlock<Level39SurfaceBlock> PLASTER = surface("l39_plaster", MapColor.TERRACOTTA_WHITE, SoundType.STONE);
    /**
     * An outside wall: light-blue clapboard on the FACING side, the room's finish on the others
     * (INNER, see {@link Level39HouseWallBlock#INNERS}), SHADE = how weathered the paint is.
     */
    public static final DeferredBlock<Level39HouseWallBlock> HOUSE_WALL = block("l39_house_wall", Level39HouseWallBlock::new,
            unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.WOOD));
    /**
     * A rotten, cracked stretch of outside wall (FACING = outside). The only house block that
     * breaks: a few seconds by hand, nothing drops, and the crack can start an echo quake.
     */
    public static final DeferredBlock<Level39KnockWallBlock> KNOCK_WALL = block("l39_knock_wall", Level39KnockWallBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f, 3.0f).sound(SoundType.WOOD)
                    .noLootTable().isValidSpawn((state, level, pos, type) -> false).pushReaction(PushReaction.BLOCK));
    /** The 6/6 sash window with a cream roller shade pulled all the way down. */
    public static final DeferredBlock<WallDecalBlock> SASH_WINDOW_SHADE = block("l39_sash_window_shade", WallDecalBlock::new,
            unbreakable(MapColor.SNOW, SoundType.GLASS));

    // ================================================================== Roofs
    /** Smooth 45-degree shingle roof pieces (slopes, hips, valleys, ridges; gable and eave trim). */
    public static final DeferredBlock<Level39RoofBlock> ROOF = block("l39_roof", Level39RoofBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.STONE).noOcclusion());
    /** The same for low roofs that rise half a block per block (ranches, porches, lean-tos). */
    public static final DeferredBlock<Level39LowRoofBlock> ROOF_LOW = block("l39_roof_low", Level39LowRoofBlock::new,
            unbreakable(MapColor.COLOR_GRAY, SoundType.STONE).noOcclusion());

    // ================================================================== Floors and ceilings
    /** Honey oak floorboards (popcorn ceiling underneath, for the room below). */
    public static final DeferredBlock<Level39SurfaceBlock> FLOORBOARDS = surface("l39_floorboards", MapColor.WOOD, SoundType.WOOD);
    /** Darker, worn walnut boards of the older houses. */
    public static final DeferredBlock<Level39SurfaceBlock> FLOORBOARDS_DARK = surface("l39_floorboards_dark", MapColor.COLOR_BROWN, SoundType.WOOD);
    /** Kitchen linoleum: a worn cream and oxblood check. */
    public static final DeferredBlock<Level39SurfaceBlock> LINOLEUM = surface("l39_linoleum", MapColor.TERRACOTTA_WHITE, SoundType.WOOD);
    /** Bathroom floor: small white hexagon tiles with a black flower now and then. */
    public static final DeferredBlock<Level39SurfaceBlock> BATH_TILE = surface("l39_bath_tile", MapColor.SNOW, SoundType.STONE);
    /** White popcorn (stippled) ceiling, all faces: the ceiling of the top floor. */
    public static final DeferredBlock<Level39SurfaceBlock> POPCORN_CEILING = surface("l39_popcorn_ceiling", MapColor.SNOW, SoundType.STONE);
    /**
     * A bare bulb screwed upside down into the ceiling above it, socket flush with the plaster.
     * LIT = one of the few still glowing, dim and warm. HANGING false = sticking up out of the floor.
     */
    public static final DeferredBlock<Level39BulbBlock> CEILING_BULB = block("l39_ceiling_bulb", Level39BulbBlock::new,
            unbreakable(MapColor.SNOW, SoundType.GLASS).noCollission().noOcclusion()
                    .lightLevel(state -> state.getValue(Level39BulbBlock.LIT) ? Level39BulbBlock.LIGHT : 0)
                    .emissiveRendering((state, level, pos) -> state.getValue(Level39BulbBlock.LIT)));

    // ================================================================== Doors, stairs, porches
    /** A white four-panel interior door with a brass knob. It opens. */
    public static final DeferredBlock<HouseDoorBlock> INTERIOR_DOOR = block("l39_interior_door",
            props -> new HouseDoorBlock(BlockSetType.OAK, props), unbreakable(MapColor.SNOW, SoundType.WOOD).noOcclusion());
    /** Oak treads of the staircases. */
    public static final DeferredBlock<StairBlock> OAK_STAIRS = block("l39_oak_stairs",
            props -> new StairBlock(FLOORBOARDS.get().defaultBlockState(), props), unbreakable(MapColor.WOOD, SoundType.WOOD));
    /** Grey weathered porch steps. */
    public static final DeferredBlock<StairBlock> PORCH_STAIRS = block("l39_porch_stairs",
            props -> new StairBlock(Level39Blocks.PORCH_PLANKS.get().defaultBlockState(), props), unbreakable(MapColor.COLOR_GRAY, SoundType.WOOD));
    /** White turned spindles under a dark rail: banisters indoors, porch railings outside. */
    public static final DeferredBlock<FenceBlock> SPINDLE_RAIL = block("l39_spindle_rail", FenceBlock::new,
            unbreakable(MapColor.SNOW, SoundType.WOOD).noOcclusion().forceSolidOn());
    /** A Craftsman porch column: a white tapered post (stack two on a fieldstone pier). */
    public static final DeferredBlock<PoleBlock> CRAFTSMAN_POST = block("l39_craftsman_post", props -> new PoleBlock(props, 4),
            unbreakable(MapColor.SNOW, SoundType.WOOD).noOcclusion());

    // ================================================================== Furniture (FACING = the way the front looks)
    /** A wingback armchair in faded green velvet. */
    public static final DeferredBlock<Level39FurnitureBlock> ARMCHAIR = furniture("l39_armchair", MapColor.COLOR_GREEN, SoundType.WOOL, Shapes.or(
            Block.box(1.5, 0, 1.5, 14.5, 8, 14.5),
            Block.box(1.5, 0, 11.5, 14.5, 16, 15.5),
            Block.box(1, 0, 1.5, 3.5, 11, 15.5),
            Block.box(12.5, 0, 1.5, 15, 11, 15.5)));
    /** A camelback sofa, two blocks wide (facing north: the second half is to the east). */
    public static final DeferredBlock<Level39PairFurnitureBlock> SOFA = pair("l39_sofa", MapColor.COLOR_RED, SoundType.WOOL,
            Level39PairFurnitureBlock.Layout.WIDE,
            Shapes.or(Block.box(0, 0, 2, 16, 8, 15), Block.box(0, 0, 12, 16, 15, 16), Block.box(0.5, 0, 2, 3, 11, 16)),
            Shapes.or(Block.box(0, 0, 2, 16, 8, 15), Block.box(0, 0, 12, 16, 15, 16), Block.box(13, 0, 2, 15.5, 11, 16)));
    /** A small round side table with a fringed table lamp on it (the bulb long dead). */
    public static final DeferredBlock<Level39FurnitureBlock> SIDE_TABLE = furniture("l39_side_table", MapColor.COLOR_BROWN, SoundType.WOOD, Shapes.or(
            Block.box(3, 0, 3, 13, 11, 13),
            Block.box(5, 11, 5, 11, 16, 11)));
    /** A walnut console radio with a cloth grille and an arched top. Radios are useless here. */
    public static final DeferredBlock<Level39FurnitureBlock> RADIO = furniture("l39_radio", MapColor.COLOR_BROWN, SoundType.WOOD,
            Block.box(2.5, 0, 5, 13.5, 16, 14));
    /** A shallow bookcase of old, sun-faded books. */
    public static final DeferredBlock<Level39FurnitureBlock> BOOKSHELF = furniture("l39_bookshelf", MapColor.COLOR_BROWN, SoundType.WOOD,
            Block.box(0, 0, 7, 16, 16, 16));
    /** An upright piano, two blocks wide (facing north: the second half is to the east). */
    public static final DeferredBlock<Level39PairFurnitureBlock> PIANO = pair("l39_piano", MapColor.COLOR_BLACK, SoundType.WOOD,
            Level39PairFurnitureBlock.Layout.WIDE,
            Shapes.or(Block.box(0, 0, 8, 16, 16, 16), Block.box(0, 9, 3, 16, 11, 8), Block.box(0.5, 0, 4, 2, 9, 6)),
            Shapes.or(Block.box(0, 0, 8, 16, 16, 16), Block.box(0, 9, 3, 16, 11, 8), Block.box(14, 0, 4, 15.5, 9, 6)));
    /** A tall wardrobe, two blocks high (the second half on top). */
    public static final DeferredBlock<Level39PairFurnitureBlock> WARDROBE = pair("l39_wardrobe", MapColor.COLOR_BROWN, SoundType.WOOD,
            Level39PairFurnitureBlock.Layout.TALL,
            Block.box(1, 0, 4, 15, 16, 16),
            Block.box(1, 0, 4, 15, 15, 16));
    /** A chest of drawers with a swing mirror on top. */
    public static final DeferredBlock<Level39FurnitureBlock> DRESSER = furniture("l39_dresser", MapColor.COLOR_BROWN, SoundType.WOOD,
            Block.box(1, 0, 6, 15, 13, 16));
    /** An iron bed with a faded quilt, two long: foot at P, head and headboard at P + FACING. */
    public static final DeferredBlock<Level39PairFurnitureBlock> BED = pair("l39_bed", MapColor.COLOR_LIGHT_GRAY, SoundType.WOOL,
            Level39PairFurnitureBlock.Layout.LONG,
            Shapes.or(Block.box(0, 2, 0, 16, 9, 15), Block.box(0, 0, 15, 16, 11, 16)),
            Shapes.or(Block.box(0, 2, 1, 16, 9, 16), Block.box(2, 9, 1.5, 14, 10.5, 5.5), Block.box(0, 0, 0, 16, 15, 1)));
    /** A clawfoot bathtub, two long (taps at the first end, the rolled back at P + FACING). */
    public static final DeferredBlock<Level39PairFurnitureBlock> CLAWFOOT_TUB = pair("l39_clawfoot_tub", MapColor.SNOW, SoundType.STONE,
            Level39PairFurnitureBlock.Layout.LONG,
            Block.box(1, 1.5, 0, 15, 11, 16),
            Block.box(1, 1.5, 0, 15, 11, 15));
    /** A porcelain pedestal washbasin. */
    public static final DeferredBlock<Level39FurnitureBlock> PEDESTAL_SINK = furniture("l39_pedestal_sink", MapColor.SNOW, SoundType.STONE, Shapes.or(
            Block.box(6, 0, 8, 10, 10, 12),
            Block.box(2.5, 10, 6, 13.5, 14, 16)));
    /** An old toilet with a wooden seat and a tank behind it. */
    public static final DeferredBlock<Level39FurnitureBlock> TOILET = furniture("l39_toilet", MapColor.SNOW, SoundType.STONE, Shapes.or(
            Block.box(4, 0, 2, 12, 9, 14),
            Block.box(3, 9, 11, 13, 16, 16)));
    /**
     * A 1930s monitor-top refrigerator. Occasionally (canon) it still holds Almond Water: LEFT
     * bottles, one per right-click.
     */
    public static final DeferredBlock<Level39SupplyFurnitureBlock> ICEBOX = block("l39_icebox",
            props -> new Level39SupplyFurnitureBlock(props, Block.box(2, 0, 3, 14, 16, 15), "message.backrooms.l39.fridge_empty", true),
            unbreakable(MapColor.SNOW, SoundType.METAL).noOcclusion());
    /** A pantry shelf of Almond Water bottles (canon: "on shelves"). The model shows the LEFT bottles. */
    public static final DeferredBlock<Level39SupplyFurnitureBlock> ALMOND_SHELF = block("l39_almond_shelf",
            props -> new Level39SupplyFurnitureBlock(props, Block.box(0, 0, 10, 16, 16, 16), "message.backrooms.l39.shelf_empty", false),
            unbreakable(MapColor.WOOD, SoundType.WOOD).noOcclusion());
    /** A cream-and-green enamel kitchen range on legs, with a warming shelf at the back. */
    public static final DeferredBlock<Level39FurnitureBlock> STOVE = furniture("l39_stove", MapColor.TERRACOTTA_WHITE, SoundType.METAL,
            Block.box(0, 0, 2, 16, 14, 16));
    /** A painted kitchen cupboard with a white tile top. */
    public static final DeferredBlock<Level39FurnitureBlock> COUNTER = furniture("l39_counter", MapColor.SNOW, SoundType.WOOD, Shapes.or(
            Block.box(0, 0, 1, 16, 14, 16),
            Block.box(0, 14, 0, 16, 16, 16)));
    /** The same cupboard with a deep farmhouse sink and a gooseneck tap. */
    public static final DeferredBlock<Level39FurnitureBlock> KITCHEN_SINK = furniture("l39_kitchen_sink", MapColor.SNOW, SoundType.STONE, Shapes.or(
            Block.box(0, 0, 1, 16, 14, 16),
            Block.box(0, 14, 0, 16, 16, 16)));
    /** A "Hoosier" kitchen dresser, two tall: flour bin and enamel worktop below, glass cupboard above. */
    public static final DeferredBlock<Level39PairFurnitureBlock> KITCHEN_DRESSER = pair("l39_kitchen_dresser", MapColor.TERRACOTTA_WHITE, SoundType.WOOD,
            Level39PairFurnitureBlock.Layout.TALL,
            Shapes.or(Block.box(0.5, 0, 2, 15.5, 14, 16), Block.box(0, 14, 1, 16, 15, 16)),
            Block.box(1, 0, 8, 15, 15, 16));
    /** One block of a dark oak dining table on turned legs (tops join up). */
    public static final DeferredBlock<Level39FurnitureBlock> DINING_TABLE = furniture("l39_dining_table", MapColor.COLOR_BROWN, SoundType.WOOD, Shapes.or(
            Block.box(0, 12.5, 0, 16, 14.5, 16),
            Block.box(1.5, 0, 1.5, 3.5, 12.5, 3.5),
            Block.box(12.5, 0, 1.5, 14.5, 12.5, 3.5),
            Block.box(1.5, 0, 12.5, 3.5, 12.5, 14.5),
            Block.box(12.5, 0, 12.5, 14.5, 12.5, 14.5)));
    /** A spindle-back dining chair; FACING = the way you'd sit. */
    public static final DeferredBlock<Level39FurnitureBlock> DINING_CHAIR = furniture("l39_dining_chair", MapColor.COLOR_BROWN, SoundType.WOOD, Shapes.or(
            Block.box(3, 7, 3, 13, 8.5, 13),
            Block.box(3, 8.5, 11.5, 13, 16, 13),
            Block.box(3, 0, 3, 4.5, 7, 4.5),
            Block.box(11.5, 0, 3, 13, 7, 4.5),
            Block.box(3, 0, 11.5, 4.5, 7, 13),
            Block.box(11.5, 0, 11.5, 13, 7, 13)));
    /** A dark framed picture on the wall (FACING = away from the wall); a faded portrait. You walk through it. */
    public static final DeferredBlock<Level39FurnitureBlock> PICTURE_FRAME = block("l39_picture_frame",
            props -> new Level39FurnitureBlock(props, Block.box(2, 3, 15, 14, 13, 16)),
            unbreakable(MapColor.COLOR_BROWN, SoundType.WOOD).noCollission().noOcclusion());
    /** A round cast-iron coal boiler in the basement. */
    public static final DeferredBlock<Level39FurnitureBlock> BOILER = furniture("l39_boiler", MapColor.COLOR_BLACK, SoundType.METAL,
            Block.box(2, 0, 2, 14, 16, 14));

    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {
                PAPER_DAMASK, PAPER_STRIPE, PAPER_FLORAL, PAPER_ROSE, PAPER_LATTICE,
                PAPER_DAMASK_BASE, PAPER_STRIPE_BASE, PAPER_FLORAL_BASE, PAPER_ROSE_BASE, PAPER_LATTICE_BASE,
                BEADBOARD, WAINSCOT, PLASTER, HOUSE_WALL, KNOCK_WALL, SASH_WINDOW_SHADE, ROOF, ROOF_LOW,
                FLOORBOARDS, FLOORBOARDS_DARK, LINOLEUM, BATH_TILE, POPCORN_CEILING, CEILING_BULB,
                OAK_STAIRS, PORCH_STAIRS, SPINDLE_RAIL, CRAFTSMAN_POST,
                ARMCHAIR, SOFA, SIDE_TABLE, RADIO, BOOKSHELF, PIANO, WARDROBE, DRESSER, BED, CLAWFOOT_TUB,
                PEDESTAL_SINK, TOILET, ICEBOX, ALMOND_SHELF, STOVE, COUNTER, KITCHEN_SINK, KITCHEN_DRESSER,
                DINING_TABLE, DINING_CHAIR, PICTURE_FRAME, BOILER}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
        ModItems.ITEMS.registerItem("l39_interior_door", props -> new DoubleHighBlockItem(INTERIOR_DOOR.get(), props), new Item.Properties());
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in Level39Blocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    /** A surface with a plain version and two worn ones (chances 6 : 2 : 1). */
    private static DeferredBlock<Level39SurfaceBlock> surface(String name, MapColor color, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(name, props -> new Level39SurfaceBlock(props, 6, 2, 1), unbreakable(color, sound));
    }

    private static DeferredBlock<Level39FurnitureBlock> furniture(String name, MapColor color, SoundType sound, VoxelShape shapeFacingNorth) {
        return block(name, props -> new Level39FurnitureBlock(props, shapeFacingNorth), unbreakable(color, sound).noOcclusion());
    }

    private static DeferredBlock<Level39PairFurnitureBlock> pair(String name, MapColor color, SoundType sound,
                                                                Level39PairFurnitureBlock.Layout layout, VoxelShape first, VoxelShape second) {
        return block(name, props -> new Level39PairFurnitureBlock(props, layout, first, second), unbreakable(color, sound).noOcclusion());
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

    private Level39HouseBlocks() {}
}
