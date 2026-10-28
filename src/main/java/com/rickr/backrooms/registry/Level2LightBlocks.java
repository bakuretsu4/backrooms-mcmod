package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.Level2EmergencyLampBlock;
import com.rickr.backrooms.block.Level2LampGlow;
import com.rickr.backrooms.block.WallLampBlock;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Level 2's extra light fixtures (the industrial lighting overhaul). The light itself comes from
 * client/light (FixtureType L2_EMERGENCY_RED / L2_EMERGENCY_AMBER and L2_MERCURY_LAMP); these are
 * just the blocks. Both hang in the AIR block in front of a wall, FACING pointing into the room.
 *
 * <ul>
 *   <li>EMERGENCY_LAMP: a battery lamp with a red or amber lens (see
 *       {@link Level2EmergencyLampBlock}). Standby (lit=false) is the normal choice: it comes on
 *       by itself when its corridor section's circuit dies. Put it beside bulkheads, chamber doors
 *       and exits, at floor + 2 or floor + 3. Use {@link #emergency}.</li>
 *   <li>MERCURY_LAMP: the caged bulkhead lamp with a cold mercury-white bulb, for machine areas
 *       (pump bays, pump and boiler rooms). Same shape and height as the amber bulkhead lamp
 *       (floor + 3) and on the same series circuits. Use {@link #mercury}.</li>
 * </ul>
 */
public final class Level2LightBlocks {
    /** Vanilla light only while switched to always-on: a standby lamp can't change its block light. */
    public static final DeferredBlock<Level2EmergencyLampBlock> EMERGENCY_LAMP = block("l2_emergency_lamp",
            Level2EmergencyLampBlock::new,
            lamp(MapColor.COLOR_RED).noCollission().lightLevel(state -> state.getValue(Level2EmergencyLampBlock.LIT) ? 5 : 0));
    /** Same block shape as ModBlocks.BULKHEAD_LAMP; its bulb goes dark with its circuit (Level2LampGlow). */
    public static final DeferredBlock<WallLampBlock> MERCURY_LAMP = block("l2_mercury_lamp",
            props -> new WallLampBlock(props, 6, 3, 13, 6),
            lamp(MapColor.SNOW).noCollission().lightLevel(state -> 10).emissiveRendering(Level2LampGlow::emissive));

    static {
        ModItems.ITEMS.registerSimpleBlockItem(EMERGENCY_LAMP);
        ModItems.ITEMS.registerSimpleBlockItem(MERCURY_LAMP);
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    /**
     * An emergency lamp on a wall.
     *
     * @param facing  away from the wall, into the room
     * @param standby true = dark until its section's power fails; false = always on
     * @param amber   amber lens instead of red
     */
    public static BlockState emergency(Direction facing, boolean standby, boolean amber) {
        return EMERGENCY_LAMP.get().defaultBlockState()
                .setValue(Level2EmergencyLampBlock.FACING, facing)
                .setValue(Level2EmergencyLampBlock.LIT, !standby)
                .setValue(Level2EmergencyLampBlock.TONE, amber ? Level2EmergencyLampBlock.Tone.AMBER : Level2EmergencyLampBlock.Tone.RED);
    }

    /** A mercury caged lamp on a wall; {@code facing} = away from the wall, into the room. */
    public static BlockState mercury(Direction facing) {
        return MERCURY_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, facing);
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    /** Unbreakable like the rest of the Backrooms, and see-through for the wall map (noOcclusion). */
    private static BlockBehaviour.Properties lamp(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(SoundType.METAL)
                .noLootTable()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private Level2LightBlocks() {}
}
