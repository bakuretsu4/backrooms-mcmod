package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level228LightBlock;
import com.rickr.backrooms.block.Level228MirrorBlock;
import com.rickr.backrooms.registry.Level228Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Level 228, "Maze of Mirrors": narrow corridors walled with mirrors in dark frames, lit in
 * sectors of blue, green and red. The floor plan, the image volumes behind the glass and the
 * exits live in {@link Level228Plan}; this class only turns its column codes into blocks.
 *
 * <p>Every column is the same stack: a panel under the floor, the dark floor (y40), three layers
 * of corridor, wall, glass or solid (y41-43), the ceiling with its light panels (y44) and a panel
 * over it. Image columns are the real column they reflect, every blockstate mirrored.
 *
 * <p>You come out of a mirror ({@link #preferredArrival}) and leave through the black exit mirror
 * to Level 369 ({@code Level228Rules}).
 */
public final class Level228Layout extends LevelLayout {
    /** Light colours by plan index (values() copies its array on every call). */
    private static final Level228LightBlock.Color[] COLORS = Level228LightBlock.Color.values();

    public Level228Layout() {
        super(3, Level228Plan.EXITS);
    }

    @Override
    public int arrivalExitClearance() {
        return 300;
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        int c = Level228Plan.column(seed, x, z);
        Mirror mirror = (c & Level228Plan.F_FLIP_X) != 0
                ? ((c & Level228Plan.F_FLIP_Z) != 0 ? null : Mirror.FRONT_BACK)
                : ((c & Level228Plan.F_FLIP_Z) != 0 ? Mirror.LEFT_RIGHT : Mirror.NONE);
        BlockState panel = Level228Blocks.PANEL.get().defaultBlockState();
        out.set(Level228Plan.FLOOR_Y - 1, panel);
        out.set(Level228Plan.FLOOR_Y, Level228Blocks.FLOOR.get().defaultBlockState());
        for (int y = Level228Plan.FEET_Y; y <= Level228Plan.LINTEL_Y; y++) {
            BlockState state = stateOf(Level228Plan.at(c, y));
            if (state != null) {
                out.set(y, mirrored(state, mirror));
            }
        }
        int light = Level228Plan.light(c);
        out.set(Level228Plan.CEILING_Y, light == 0
                ? Level228Blocks.CEILING.get().defaultBlockState()
                : Level228Blocks.LIGHT.get().defaultBlockState()
                        .setValue(Level228LightBlock.COLOR, COLORS[Level228Plan.lightColor(light)])
                        .setValue(Level228LightBlock.SHADE, Level228Plan.lightShade(light)));
        out.set(Level228Plan.CEILING_Y + 1, panel);
    }

    /**
     * The blockstate as seen in a mirror: flipped across x, z, or both (null: a half turn, two
     * mirrors). Every Level 228 block looks the same mirrored, but a mirrored copy must stay one
     * should anything with a facing ever be built here.
     */
    private static BlockState mirrored(BlockState state, @Nullable Mirror mirror) {
        if (mirror == null) {
            return state.mirror(Mirror.FRONT_BACK).mirror(Mirror.LEFT_RIGHT);
        }
        return mirror == Mirror.NONE ? state : state.mirror(mirror);
    }

    @Nullable
    static BlockState stateOf(int content) {
        return switch (content) {
            case Level228Plan.PANEL -> Level228Blocks.PANEL.get().defaultBlockState();
            case Level228Plan.FRAME -> Level228Blocks.MIRROR_FRAME.get().defaultBlockState();
            case Level228Plan.MIRROR_X -> Level228Blocks.MIRROR.get().defaultBlockState().setValue(Level228MirrorBlock.AXIS, Direction.Axis.X);
            case Level228Plan.MIRROR_Z -> Level228Blocks.MIRROR.get().defaultBlockState().setValue(Level228MirrorBlock.AXIS, Direction.Axis.Z);
            case Level228Plan.EXIT_X -> Level228Blocks.EXIT_MIRROR.get().defaultBlockState().setValue(Level228MirrorBlock.AXIS, Direction.Axis.X);
            case Level228Plan.EXIT_Z -> Level228Blocks.EXIT_MIRROR.get().defaultBlockState().setValue(Level228MirrorBlock.AXIS, Direction.Axis.Z);
            default -> null;
        };
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return Level228Plan.isWalkable(seed, x, z);
    }

    /** Corridors are two wide: a spot never has walkable space on all four sides. */
    @Override
    public boolean arrivalNeedsSpace() {
        return false;
    }

    /** /backrooms locate (and the exit hints) point at the corridor in front of the exit mirror. */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return Level228Plan.nearestExit(seed, x, z);
    }

    /**
     * You step out of a mirror: the corridor block against the nearest mirrored side wall (from
     * Level 94's dark doors and from anywhere else alike); the usual search if none is near.
     */
    @Override
    @Nullable
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        int[] spot = Level228Plan.mirrorArrival(seed, x, z, 4);
        return spot == null ? null : new BlockPos(spot[0], Level228Plan.FEET_Y, spot[1]);
    }
}
