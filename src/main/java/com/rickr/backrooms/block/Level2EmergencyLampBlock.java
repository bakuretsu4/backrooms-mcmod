package com.rickr.backrooms.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Level 2's battery emergency lamp: a small steel box with a domed red or amber lens and a wire
 * guard, bolted to a wall beside bulkheads, chamber doors and exits (registry/Level2LightBlocks).
 * FACING points away from the wall, into the room, like every {@link WallMountBlock}.
 *
 * <ul>
 *   <li>LIT true: always on (its battery), e.g. over an exit.</li>
 *   <li>LIT false: STANDBY. Dark while the corridor section around it has power; when that
 *       section's series circuit fails (or the floor blacks out) its relay clicks and it comes on
 *       until the power is back. The light itself is made by client/light (FixturePower).</li>
 *   <li>TONE: the lens colour, red or amber.</li>
 * </ul>
 * The lens glows only while the lamp is on: the client tints it bright or dark (see
 * BackroomsLights), so the block never changes on the server.
 */
public class Level2EmergencyLampBlock extends WallMountBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);

    /** The lens colour. */
    public enum Tone implements StringRepresentable {
        RED, AMBER;

        @Override
        public String getSerializedName() {
            return this == RED ? "red" : "amber";
        }
    }

    public Level2EmergencyLampBlock(Properties properties) {
        // A 10 x 10 px box on the wall, sticking out 6 px (the lens dome and its guard).
        super(properties, 3, 3, 10, 13, 13, 16);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(TONE, Tone.RED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, TONE);
    }
}
