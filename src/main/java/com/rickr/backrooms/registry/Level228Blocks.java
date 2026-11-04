package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.Level228BulbBlock;
import com.rickr.backrooms.block.Level228LightBlock;
import com.rickr.backrooms.block.Level228MirrorBlock;
import com.rickr.backrooms.block.Level228PostBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The blocks of Level 228, "Maze of Mirrors" (world/layout/Level228Plan): mirror glass in dark
 * frames, dark panels, a polished black floor, a low ceiling with coloured light panels, and the
 * exit mirror. Models, blockstates and the plain textures come from tools/level_assets/level228.py;
 * the glass, light and exit textures are the level's look (tools/level_assets/level228_look.py).
 */
public final class Level228Blocks {
    /** A pane of mirror glass: collidable, see-through (what you "see in it" is built behind it). */
    public static final DeferredBlock<Level228MirrorBlock> MIRROR = block("l228_mirror", Level228MirrorBlock::new,
            glass(MapColor.COLOR_LIGHT_GRAY));
    /** The dark metal frame: posts between panes and the lintel strip above them. */
    public static final DeferredBlock<Block> MIRROR_FRAME = structural("l228_mirror_frame", MapColor.COLOR_BLACK, SoundType.METAL);
    /** Dark opaque panel: the walls that are not mirrors, and everything solid between the corridors. */
    public static final DeferredBlock<Block> PANEL = structural("l228_panel", MapColor.COLOR_BLACK, SoundType.STONE);
    /** Dark polished floor. */
    public static final DeferredBlock<Block> FLOOR = structural("l228_floor", MapColor.COLOR_BLACK, SoundType.STONE);
    /** The low ceiling. */
    public static final DeferredBlock<Block> CEILING = structural("l228_ceiling", MapColor.COLOR_GRAY, SoundType.STONE);
    /** A coloured light panel in the ceiling (COLOR blue/green/red, SHADE 0..3, glows 12 + SHADE). */
    public static final DeferredBlock<Level228LightBlock> LIGHT = block("l228_light", Level228LightBlock::new,
            unbreakable(MapColor.COLOR_BLUE, SoundType.GLASS).lightLevel(Level228LightBlock::emission));
    /**
     * The way out: a mirror with no reflection in it, black, nothing behind the glass. It has no
     * collision, so you walk into it, and through it you come out in Level 369 (world/Level228Rules).
     */
    public static final DeferredBlock<Level228MirrorBlock> EXIT_MIRROR = block("l228_exit_mirror", Level228MirrorBlock::new,
            glass(MapColor.COLOR_BLACK).noCollission());
    /**
     * v2: the light of a live or failing bulb, sconce, marquee run, EXIT sign or exit-hallway
     * fixture. Invisible and walk-through (the bulb itself is a prop); only its block light and
     * the client's Veil light (client/light/Level228Bulbs). No item: the layout places it.
     */
    public static final DeferredBlock<Level228BulbBlock> BULB = block("l228_bulb", Level228BulbBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).noCollission().noOcclusion().replaceable()
                    .strength(-1.0f, 3_600_000.0f).sound(SoundType.EMPTY).noLootTable()
                    .lightLevel(Level228BulbBlock::emission)
                    .isValidSpawn((state, level, pos, type) -> false).pushReaction(PushReaction.DESTROY));

    /**
     * v2: the invisible collision of a maze column or the ticket booth (the column itself is a
     * prop). No item: the layout places it.
     */
    public static final DeferredBlock<Level228PostBlock> POST = block("l228_post", Level228PostBlock::new,
            glass(MapColor.NONE).sound(SoundType.WOOD));

    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {MIRROR, MIRROR_FRAME, PANEL, FLOOR, CEILING, LIGHT, EXIT_MIRROR}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
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

    /** Glass: the view and light pass, nothing suffocates in it or spawns on it. */
    private static BlockBehaviour.Properties glass(MapColor color) {
        return unbreakable(color, SoundType.GLASS).noOcclusion()
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

    private Level228Blocks() {}
}
