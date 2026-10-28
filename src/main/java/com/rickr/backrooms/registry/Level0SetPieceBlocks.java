package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.FallenTileBlock;
import com.rickr.backrooms.block.WallMountBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The two new (non-wall) blocks of Level 0's set pieces. Both reuse existing textures (the ceiling
 * tile and the fluorescent lens), so they add nothing to the block atlas. Neither has an item: one
 * is evidence and one is a light that only the server places.
 * <ul>
 *   <li>{@link #FALLEN_TILE}: a broken ceiling tile lying on the carpet of the Shaft Hall, and what
 *       falls down a shaft when a tile works loose.</li>
 *   <li>{@link #DOOR_GLOW}: a thin line of light at the foot of the Front Office's drawn door,
 *       placed by the server only while the door is real (world/anomaly/level0/FrontOfficeBehavior).
 *       It sits in the air block in front of the door; FACING points away from the wall, like every
 *       wall mount.</li>
 * </ul>
 * Loaded by the one bootstrap() line in the mod constructor.
 */
public final class Level0SetPieceBlocks {
    public static final DeferredBlock<FallenTileBlock> FALLEN_TILE = ModBlocks.BLOCKS.registerBlock("l0_fallen_tile",
            FallenTileBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(-1.0f, 3_600_000.0f)
                    .sound(SoundType.WOOD)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .pushReaction(PushReaction.DESTROY));

    /** Light level 6: a faint warm spill on the rug in front of the door, nothing more. */
    public static final DeferredBlock<WallMountBlock> DOOR_GLOW = ModBlocks.BLOCKS.registerBlock("l0_door_glow",
            props -> new WallMountBlock(props, 1.5, 0, 15.4, 14.5, 0.6, 16), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(-1.0f, 3_600_000.0f)
                    .sound(SoundType.GLASS)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 6)
                    .emissiveRendering((state, level, pos) -> true)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .pushReaction(PushReaction.DESTROY));

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    private Level0SetPieceBlocks() {}
}
