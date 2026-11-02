package com.rickr.backrooms.block.level9;

import com.rickr.backrooms.block.WallMountBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sheet of paper stapled to something on Level 9: the missing-person posters and the
 * Neighborhood Watch flyers. FACING points away from what it is stuck to (like every
 * wall-mounted block), MOUNT says what that is (see {@link PaperMount}). You walk through it.
 *
 * <p>Right-clicking it shows the paper close up. That happens on the client only
 * (client/level9/Level9StoryClient opens the reading screen), because one poster shows the
 * player's own name and that must not be decided on the server. The server just says "handled",
 * so the hand swings and nothing else happens.
 */
public class PinnedPaperBlock extends WallMountBlock {
    public static final EnumProperty<PaperMount> MOUNT = EnumProperty.create("mount", PaperMount.class);
    /** How thick the paper's click box is (pixels): thin, but easy to hit. */
    private static final double THICKNESS = 0.5;

    /** Click shapes by mount, then by facing. */
    private final Map<PaperMount, Map<Direction, VoxelShape>> shapes = new EnumMap<>(PaperMount.class);

    /**
     * @param sheets the paper's rectangle {x0, y0, x1, y1} (pixels, seen from the front when it
     *               faces north) for each mount, in {@link PaperMount} order
     */
    protected PinnedPaperBlock(Properties properties, double[][] sheets) {
        super(properties, 0, 0, 15.5, 16, 16, 16); // replaced by the per-mount shapes below
        for (PaperMount mount : PaperMount.values()) {
            double[] r = sheets[mount.ordinal()];
            double back = 16 + mount.depth();
            // The box may reach into the pole's block: that is where the paper really is, and a
            // click ray coming from the street still meets this block first.
            VoxelShape north = Block.box(r[0], r[1], back - THICKNESS, r[2], r[3], back);
            Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                byFacing.put(facing, turn(north, facing));
            }
            shapes.put(mount, byFacing);
        }
        registerDefaultState(defaultBlockState().setValue(MOUNT, PaperMount.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(MOUNT)).get(state.getValue(FACING));
    }

    /** No collision at all: paper never blocks the sidewalk. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    /** In creative: click the side of a wall and the paper sticks to it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(MOUNT, PaperMount.WALL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
