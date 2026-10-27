package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One piece of the smooth marble shell of Level 39's tunnels. O is the piece's column offset
 * from the tunnel's centre line and ROW its height above the walkway (0 = feet level, y41):
 * <ul>
 *   <li>O 0..4: the barrel vault, whose underside follows the arc of the vault as one smooth
 *       curved surface (an OBJ mesh, not stacked boxes);</li>
 *   <li>O 5: the straight wall under the springing of the arc (rows 0 and 1), a plain marble
 *       block lit exactly like the vault pieces, so wall and vault read as one white shell.</li>
 * </ul>
 * All pieces take the light of their own cell, with no directional shading and no ambient
 * occlusion. FACING says which way the offset grows (the model is drawn for SOUTH: the offset
 * grows toward +z, the vault runs along x). The arc and the piece models are worked out the same
 * way in {@code Level39Vault} and in tools/anomaly_assets/level39.py.
 */
public class VaultBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty O = IntegerProperty.create("o", 0, 5);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 7);

    public VaultBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(O, 0).setValue(ROW, 6));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, O, ROW);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
