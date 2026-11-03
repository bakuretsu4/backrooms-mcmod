package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level227HangingBlock;
import com.rickr.backrooms.block.Level227PlantBlock;
import com.rickr.backrooms.registry.Level227Blocks;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 369, "Comely": an endless indoor garden of pastel rooms with lawns and flowers for floors.
 * The floor plan lives in {@link Level369Plan}; this class only turns its codes into blocks.
 *
 * <p>Nothing lives here. You fall in through a skylight from Level 94's dark-doored houses
 * ({@link #preferredArrival}) and leave through an arch of vines to Level 490
 * ({@code Level369Rules}). See {@code docs/level-369-comely.md}.
 */
public final class Level369Layout extends LevelLayout {
    private static final Level369Plan PLAN = new Level369Plan();
    /** One 64-high code buffer per world-generation thread. */
    private static final ThreadLocal<int[]> BUFFER = ThreadLocal.withInitial(() -> new int[64]);

    public Level369Layout() {
        // 5 = air layers of a hallway; the real heights vary per room (see Level369Plan).
        super(5, Level369Plan.EXITS);
    }

    @Override
    public int arrivalExitClearance() {
        return 400;
    }

    /** The plan, for the Level 369 rules. */
    public static Level369Plan plan() {
        return PLAN;
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        int[] codes = BUFFER.get();
        PLAN.column(seed, x, z, codes);
        for (int y = 0; y < codes.length; y++) {
            BlockState state = stateOf(codes[y]);
            if (state != null) {
                out.set(y, state);
            }
        }
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return PLAN.isWalkable(seed, x, z);
    }

    @Override
    public int highestCeilingY() {
        return Level369Plan.HIGHEST_Y;
    }

    /** /backrooms locate (and the exit hints) point at the floor in front of the arch. */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return PLAN.archFront(seed, x, z);
    }

    /** Through Level 94's dark doors you fall in through a skylight; from anywhere else, the usual search. */
    @Override
    @Nullable
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        return from == BackroomsLevel.LEVEL_94 ? PLAN.skylightArrival(seed, x, z) : null;
    }

    /** The block for one plan code, or null for air. */
    @Nullable
    private static BlockState stateOf(int code) {
        int data = Level369Plan.data(code);
        return switch (Level369Plan.type(code)) {
            case Level369Plan.GRASS -> Level369Blocks.GRASS.get().defaultBlockState();
            case Level369Plan.SOIL -> Level369Blocks.SOIL.get().defaultBlockState();
            case Level369Plan.PLASTER -> plaster(data);
            case Level369Plan.CEIL -> (data == 1 ? Level369Blocks.CEILING_ROSE : Level369Blocks.CEILING).get().defaultBlockState();
            case Level369Plan.PANEL -> Level369Blocks.PANEL.get().defaultBlockState();
            case Level369Plan.SKYLIGHT -> Level369Blocks.SKYLIGHT.get().defaultBlockState();
            case Level369Plan.WINDOW -> Level369Blocks.WINDOW.get().defaultBlockState();
            case Level369Plan.BRICK -> Level369Blocks.BRICK.get().defaultBlockState();
            case Level369Plan.FLOWER -> Level369Blocks.FLOWER.get().defaultBlockState().setValue(Level227PlantBlock.KIND, data);
            case Level369Plan.TUFT -> Level369Blocks.TUFT.get().defaultBlockState().setValue(Level227PlantBlock.KIND, data);
            case Level369Plan.GLOW -> Level369Blocks.GLOW.get().defaultBlockState().setValue(Level227PlantBlock.KIND, data);
            case Level369Plan.VINE -> Level369Blocks.GLOW_VINE.get().defaultBlockState().setValue(Level227HangingBlock.TIP, data == 1);
            case Level369Plan.LEAVES -> Level369Blocks.LEAVES.get().defaultBlockState();
            case Level369Plan.LOG -> Level227Blocks.TREE_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            case Level369Plan.DOOR -> ModBlocks.LOBBY_DOOR.get().defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.from2DDataValue(data & 3))
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                    .setValue(DoorBlock.OPEN, false)
                    .setValue(DoorBlock.HALF, (data & 4) != 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            case Level369Plan.LIGHT -> Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, data);
            case Level369Plan.VINE_WALL -> Level369Blocks.VINE_WALL.get().defaultBlockState();
            case Level369Plan.VEIL -> Level369Blocks.VEIL.get().defaultBlockState();
            default -> null;
        };
    }

    private static BlockState plaster(int colour) {
        return (switch (colour) {
            case Level369Plan.PEACH -> Level369Blocks.PLASTER_PEACH;
            case Level369Plan.MINT -> Level369Blocks.PLASTER_MINT;
            case Level369Plan.LAVENDER -> Level369Blocks.PLASTER_LAVENDER;
            case Level369Plan.BUTTER -> Level369Blocks.PLASTER_BUTTER;
            case Level369Plan.ROSE -> Level369Blocks.PLASTER_ROSE;
            case Level369Plan.DUSK -> Level369Blocks.PLASTER_DUSK;
            default -> Level369Blocks.PLASTER_WHITE;
        }).get().defaultBlockState();
    }
}
