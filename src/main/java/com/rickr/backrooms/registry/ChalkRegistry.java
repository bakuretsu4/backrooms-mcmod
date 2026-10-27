package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.ChalkMarkBlock;
import com.rickr.backrooms.item.ChalkItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Chalk: the {@code backrooms:chalk} item, the {@code backrooms:chalk_mark} block it draws and their
 * two sounds. Registered through the shared {@link ModBlocks#BLOCKS}, {@link ModItems#ITEMS} and
 * {@link ModSounds#SOUNDS} registers; {@link #bootstrap()} (called from {@code Backrooms}) only makes
 * Java load this class so the fields below exist before the registers fire.
 *
 * <p>Textures, models and sounds come from tools/anomaly_assets/chalk.py. The chalk shows up in
 * the creative tab by itself (the tab lists every item in ModItems.ITEMS); the mark has no item
 * of its own, you draw it with chalk.
 */
public final class ChalkRegistry {
    // A short scratch of chalk on a wall, and the soft brush of a sleeve wiping a mark away.
    public static final DeferredHolder<SoundEvent, SoundEvent> CHALK_DRAW = sound("item.chalk.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHALK_WIPE = sound("block.chalk_mark.wipe");

    /**
     * The mark: walk-through, no light, no drop, gone with one punch (strength 0), and any block
     * placed there simply replaces it. Its "break" sound is the wipe.
     */
    public static final DeferredBlock<ChalkMarkBlock> CHALK_MARK = ModBlocks.BLOCKS.registerBlock("chalk_mark",
            ChalkMarkBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instabreak()
                    .noCollission()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .sound(new DeferredSoundType(1.0f, 1.0f, CHALK_WIPE, CHALK_WIPE, CHALK_DRAW, CHALK_WIPE, CHALK_WIPE)));

    /** 64 marks to a stick; it doesn't stack (each piece wears down and remembers its symbol). */
    public static final DeferredItem<ChalkItem> CHALK = ModItems.ITEMS.registerItem("chalk",
            ChalkItem::new, new Item.Properties().durability(64));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Backrooms.id(name)));
    }

    /** Loads this class (and so registers everything above). Called once from the mod constructor. */
    public static void bootstrap() {}

    private ChalkRegistry() {}
}
