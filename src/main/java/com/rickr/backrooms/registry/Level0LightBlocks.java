package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.Level0WallLampBlock;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Level 0's extra light fixtures for the set pieces (the Front Office, the Atrium, hidden rooms).
 * Their light is made by client/light (FixtureType L0_BEACON, L0_EMERGENCY_LAMP, L0_PENDANT);
 * these are just the blocks, with a little vanilla light so the bounce fill and the mobs see them.
 * Only their lenses glow full-bright (the block models mark those faces), not the metal bodies.
 *
 * <ul>
 *   <li>BEACON: a red rotating beacon on a wall bracket, in the AIR block in front of the wall,
 *       FACING pointing into the room. LIT (BlockStateProperties.LIT) on = two red cones
 *       sweeping, off = dark. Put it just under the ceiling.</li>
 *   <li>EMERGENCY_LAMP: a twin-head battery lamp, placed like the beacon. LIT on = always lit;
 *       off = standby: it switches itself on when the power at its spot fails (blackout wave,
 *       local outages).</li>
 *   <li>PENDANT_LIGHT: a warm lamp shade hanging in an air block; hang it from vanilla chains.
 *       On the mains (dies with the power around it).</li>
 * </ul>
 */
public final class Level0LightBlocks {
    public static final DeferredBlock<Level0WallLampBlock> BEACON = block("l0_beacon",
            props -> new Level0WallLampBlock(props, Block.box(4, 1, 8, 12, 12, 16)),
            lamp(MapColor.COLOR_RED, SoundType.METAL).lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 4 : 0));
    public static final DeferredBlock<Level0WallLampBlock> EMERGENCY_LAMP = block("l0_emergency_lamp",
            props -> new Level0WallLampBlock(props, Block.box(1, 4, 11, 15, 12, 16)),
            lamp(MapColor.SNOW, SoundType.METAL).lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 8 : 0));
    public static final DeferredBlock<FurnitureBlock> PENDANT_LIGHT = block("l0_pendant_light",
            props -> new FurnitureBlock(props, Block.box(4, 4, 4, 12, 16, 12)),
            lamp(MapColor.COLOR_BLACK, SoundType.METAL).lightLevel(state -> 10).noCollission());

    static {
        ModItems.ITEMS.registerSimpleBlockItem(BEACON);
        ModItems.ITEMS.registerSimpleBlockItem(EMERGENCY_LAMP);
        ModItems.ITEMS.registerSimpleBlockItem(PENDANT_LIGHT);
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    /** Unbreakable like the rest of the Backrooms, and see-through for the wall map (noOcclusion). */
    private static BlockBehaviour.Properties lamp(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(sound)
                .noLootTable()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private Level0LightBlocks() {}
}
