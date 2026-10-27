package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.AxisBlock;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.WallLampBlock;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * What "separate realities" ({@code world/RealityDrift}) needs registered: the dead twins of
 * Level 1's three light fixtures, and the soft thump you hear when your reality snaps back.
 *
 * <p>Level 0 already has dead fixtures (FLUORESCENT_LIGHT_OFF, l0_troffer_strip_off) and Level 4's
 * office panel has a "dead" mode, but Level 1's lamps only ever exist lit. The drift needs a dead
 * version to show ONE player (it is only ever sent in a fake block update, never placed in the
 * world): same shape and model as the lit lamp, with a grey lens and no light at all.
 *
 * <p>Textures and the sound come from {@code tools/anomaly_assets/reality_drift.py}. Like every
 * Backrooms block they go through the shared {@link ModBlocks#BLOCKS} register; {@link #bootstrap()}
 * (called from {@code Backrooms}) only makes Java load this class in time. No items: nobody needs
 * to place these.
 */
public final class DriftRegistry {
    /** {@code l1_hanging_light} with its tubes out: the diffuser is grey and gives no light. */
    public static final DeferredBlock<AxisBlock> L1_HANGING_LIGHT_OFF = ModBlocks.BLOCKS.registerBlock("l1_hanging_light_off",
            props -> new AxisBlock(props, 0, 4, 4, 16, 16, 12), dead(SoundType.GLASS));
    /** {@code warehouse_light} (the vertical wall tube) with a dead grey tube. */
    public static final DeferredBlock<WallLampBlock> WAREHOUSE_LIGHT_OFF = ModBlocks.BLOCKS.registerBlock("warehouse_light_off",
            props -> new WallLampBlock(props, 4, 0, 16, 3), dead(SoundType.METAL));
    /** {@code ceiling_tube} (the corridor blocks' strip) with a dead grey tube. */
    public static final DeferredBlock<CeilingTubeBlock> CEILING_TUBE_OFF = ModBlocks.BLOCKS.registerBlock("ceiling_tube_off",
            CeilingTubeBlock::new, dead(SoundType.GLASS));

    /**
     * The snap back: a very soft, low thump of pressure, like your ears popping in a lift. Only
     * the player whose room was different hears it, right where they stand.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_BACK = ModSounds.SOUNDS.register("drift.snap_back",
            () -> SoundEvent.createVariableRangeEvent(Backrooms.id("drift.snap_back")));

    /** Loads this class (and so registers everything above). Called once from the mod constructor. */
    public static void bootstrap() {}

    /** Like the lit lamps (unbreakable, you walk through them) but with no light and no glow. */
    private static BlockBehaviour.Properties dead(SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .strength(-1.0f, 3_600_000.0f)
                .sound(sound)
                .noLootTable()
                .noCollission()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private DriftRegistry() {}
}
