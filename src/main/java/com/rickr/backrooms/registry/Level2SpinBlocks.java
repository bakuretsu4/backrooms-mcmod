package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.Level2SpinPropBlock;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Level 2's spinning props, part of the spin illusion (client/anomaly/level2/Level2Spin): things
 * in the corridor that visibly turn, all in step with the environment's virtual rotation.
 * <ul>
 *   <li>{@link #WALL_FAN}: a square duct fan set into a wall or the pipe rack; its blades turn
 *       about once a second, slowing, stopping and reversing with the spin.</li>
 *   <li>{@link #SPIN_BEACON}: a caged amber warning beacon set into the ceiling or a wall; its
 *       reflector and light cone sweep round, every beacon down the corridor a little behind the
 *       one before, so the sweep corkscrews off into the dark.</li>
 * </ul>
 * Both are full blocks with FACING ({@link Level2SpinPropBlock#FACING}, 6-way) pointing out of
 * the surface into the lane. Models: tools/anomaly_assets/level2_spin.py.
 */
public final class Level2SpinBlocks {
    /** The beacon's own (vanilla) light: amber, a small pool, so it reads with shader packs too. */
    public static final int BEACON_LIGHT = 8;

    public static final DeferredBlock<Level2SpinPropBlock> WALL_FAN = ModBlocks.BLOCKS.registerBlock("l2_wall_fan",
            props -> new Level2SpinPropBlock(props, () -> Level2SpinBlocks.WALL_FAN_ENTITY.get()),
            unbreakable(MapColor.METAL));
    public static final DeferredBlock<Level2SpinPropBlock> SPIN_BEACON = ModBlocks.BLOCKS.registerBlock("l2_spin_beacon",
            props -> new Level2SpinPropBlock(props, () -> Level2SpinBlocks.SPIN_BEACON_ENTITY.get()),
            unbreakable(MapColor.COLOR_ORANGE).lightLevel(state -> BEACON_LIGHT));

    @SuppressWarnings("DataFlowIssue") // build(null): we have no datafixer type, like most mods
    public static final Supplier<BlockEntityType<Level2SpinPropBlock.Entity>> WALL_FAN_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("l2_wall_fan", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new Level2SpinPropBlock.Entity(Level2SpinBlocks.WALL_FAN_ENTITY.get(), pos, state),
                    WALL_FAN.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<Level2SpinPropBlock.Entity>> SPIN_BEACON_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("l2_spin_beacon", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new Level2SpinPropBlock.Entity(Level2SpinBlocks.SPIN_BEACON_ENTITY.get(), pos, state),
                    SPIN_BEACON.get()).build(null));

    static {
        ModItems.ITEMS.registerSimpleBlockItem(WALL_FAN);
        ModItems.ITEMS.registerSimpleBlockItem(SPIN_BEACON);
    }

    /** A fan set into a surface, blowing out of it toward {@code facing}. */
    public static BlockState fan(Direction facing) {
        return WALL_FAN.get().defaultBlockState().setValue(Level2SpinPropBlock.FACING, facing);
    }

    /** A beacon set into a surface, shining out of it toward {@code facing}. */
    public static BlockState beacon(Direction facing) {
        return SPIN_BEACON.get().defaultBlockState().setValue(Level2SpinPropBlock.FACING, facing);
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(SoundType.METAL)
                .noLootTable()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private Level2SpinBlocks() {}
}
