package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.HiddenNoteBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The few blocks Level 0's hidden rooms (world/layout/Level0HiddenRooms) need that the level
 * didn't have yet. None of them is a wall or a wallpaper: the hidden rooms are built from the
 * level's own wall blocks, and these only furnish them.
 * <ul>
 *   <li>PLENUM and DUCT: the dark concrete deck and the sheet-metal ducts of the crawlspace above
 *       the ceiling tiles. They reuse the textures already drawn inside the missing-ceiling-tile
 *       block (l0_plenum, l0_duct), so what you glimpse through a hole is what you crawl through.</li>
 *   <li>NOTE: a note someone pinned up in a hidden room (reuses the volunteer note's paper).</li>
 * </ul>
 * Registered through the shared {@link ModBlocks#BLOCKS} / {@link ModItems#ITEMS}; the mod
 * constructor only calls {@link #bootstrap()} so this class loads in time.
 */
public final class Level0HiddenBlocks {
    public static final DeferredBlock<Block> PLENUM = structural("l0_plenum_block", MapColor.COLOR_BLACK, SoundType.STONE);
    public static final DeferredBlock<Block> DUCT = structural("l0_plenum_duct", MapColor.METAL, SoundType.METAL);
    public static final DeferredBlock<HiddenNoteBlock> NOTE = ModBlocks.BLOCKS.registerBlock("l0_hidden_note",
            HiddenNoteBlock::new, unbreakable(MapColor.SNOW, SoundType.WOOD).noCollission().noOcclusion());

    static {
        ModItems.ITEMS.registerSimpleBlockItem(PLENUM);
        ModItems.ITEMS.registerSimpleBlockItem(DUCT);
        ModItems.ITEMS.registerSimpleBlockItem(NOTE);
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    private static DeferredBlock<Block> structural(String name, MapColor color, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(name, (Function<BlockBehaviour.Properties, Block>) Block::new, unbreakable(color, sound));
    }

    /** Like every Level 0 building block: can't be broken, drops nothing, nothing spawns on it. */
    private static BlockBehaviour.Properties unbreakable(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(sound)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private Level0HiddenBlocks() {}
}
