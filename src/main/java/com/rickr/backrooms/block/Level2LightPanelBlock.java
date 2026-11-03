package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Level 2's corridor light: a square panel recessed into the concrete, cold blue-white, the only
 * light in the corridor every 12 blocks (every 6 in a twist stretch). Normally it sits in the
 * lowered ceiling (FACING down); in a twist stretch it rides round the cross-section with the rest
 * of the dressing onto the walls (see world/layout/Level2Layout), so successive panels seem to turn.
 *
 * <p>LIT = false is a dead tube (dark segments, brownouts): no light, not drawn glowing, and the
 * client's light code (client/light/FixtureType L2_LIGHT_PANEL) skips it. A lit panel is on its
 * corridor section's series circuit like the caged lamps were: when the circuit fails it goes dark
 * with them ({@link Level2LampGlow}).
 */
public class Level2LightPanelBlock extends Level2FaceBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public Level2LightPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /** Light level for the block properties (the vanilla light, without a shader pack's lights). */
    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? 10 : 0;
    }

    /** For the block properties: drawn full-bright only while lit and its circuit has power. */
    public static boolean emissive(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) && Level2LampGlow.emissive(state, level, pos);
    }
}
