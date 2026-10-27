package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ChalkRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A chalk mark a player drew with {@link com.rickr.backrooms.item.ChalkItem}: an arrow, a cross or
 * a circle on a wall, the floor or the ceiling. Like {@link Level0TapeBlock} it sits in the AIR
 * block in front of the surface, flat against it, and you walk straight through it.
 *
 * <p>FACING points away from the surface, into the room: NORTH = drawn on the wall to the south,
 * UP = drawn on the floor below, DOWN = drawn on the ceiling above.
 *
 * <p>ROTATION is the way the drawing points as you look at it, in quarter turns clockwise:
 * 0 = up, 1 = right, 2 = down, 3 = left (only arrows care; crosses and circles ignore it). The
 * blockstate file picks an arrow texture drawn that way round; see {@link #rotationOnFloor} and
 * {@link #rotationOnCeiling} for how a compass direction becomes one of these on the floor and
 * ceiling.
 *
 * <p>Which of the 2-3 hand-drawn versions of a symbol you see comes from the blockstate's
 * weighted model list: Minecraft picks one from the block's position, so marks next to each other
 * don't look stamped. Textures and models: tools/anomaly_assets/chalk.py.
 *
 * <p>One punch wipes it away (no drop, a soft brush sound), and it vanishes on its own when the
 * block it was drawn on is gone.
 */
public class ChalkMarkBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Symbol> SYMBOL = EnumProperty.create("symbol", Symbol.class);
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);

    /** What the chalk draws. The item cycles through them in this order. */
    public enum Symbol implements StringRepresentable {
        ARROW, CROSS, CIRCLE;

        /** The next symbol when the player cycles (arrow -> cross -> circle -> arrow). */
        public Symbol next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Looks a symbol up by its saved name; anything unknown (old or broken data) is an arrow. */
        public static Symbol byName(String name) {
            for (Symbol s : values()) {
                if (s.getSerializedName().equals(name)) {
                    return s;
                }
            }
            return ARROW;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // What you see outlined when you look at the mark: a paper-thin plate against the surface
    // (the same plates as the tape), so you can aim at it to wipe it.
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);

    static {
        PLATES.put(Direction.NORTH, Block.box(0, 0, 15.5, 16, 16, 16));
        PLATES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 0.5));
        PLATES.put(Direction.EAST, Block.box(0, 0, 0, 0.5, 16, 16));
        PLATES.put(Direction.WEST, Block.box(15.5, 0, 0, 16, 16, 16));
        PLATES.put(Direction.DOWN, Block.box(0, 15.5, 0, 16, 16, 16));
        PLATES.put(Direction.UP, Block.box(0, 0, 0, 16, 0.5, 16));
    }

    public ChalkMarkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(SYMBOL, Symbol.ARROW).setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SYMBOL, ROTATION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PLATES.get(state.getValue(FACING));
    }

    /**
     * Rotation for an arrow on the FLOOR pointing towards {@code dir} (a compass direction).
     * The floor model shows the texture's top towards the south and its right towards the west,
     * which happens to be Minecraft's own numbering of the four directions (south 0, west 1,
     * north 2, east 3).
     */
    public static int rotationOnFloor(Direction dir) {
        return dir.get2DDataValue();
    }

    /**
     * Rotation for an arrow on the CEILING pointing towards {@code dir}. Seen from below the
     * texture's top is north and its right is west (you look at it upside down), so the order is
     * north 0, west 1, south 2, east 3.
     */
    public static int rotationOnCeiling(Direction dir) {
        return switch (dir) {
            case WEST -> 1;
            case SOUTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** The mark needs a solid face behind it: the block it was drawn on. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /** The wall, floor or ceiling under the mark went away: the mark goes with it, dropping nothing. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** Middle-click (pick block) in creative gives you a piece of chalk, not an unplaceable mark. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ChalkRegistry.CHALK.get());
    }

    /**
     * Structures turned by a quarter turn (templates): a mark on the wall keeps its arrow (the wall
     * turns with it); on the floor and ceiling the arrow has to turn with the compass.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = state.getValue(FACING);
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        int r = state.getValue(ROTATION);
        if (facing == Direction.UP) {
            r = (r + turns) % 4;           // south -> west -> north -> east is +1 per clockwise turn
        } else if (facing == Direction.DOWN) {
            r = (r + 4 - turns) % 4;       // seen from below the numbering runs the other way
        }
        return state.setValue(FACING, rotation.rotate(facing)).setValue(ROTATION, r);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
