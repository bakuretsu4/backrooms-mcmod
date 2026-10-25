package com.rickr.backrooms.client.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Turns fixture blocks into lights. Neighbouring blocks of the same fixture (a strip of tubes, a
 * 2-block-tall wall tube, a double exit sign) become ONE light, so a strip doesn't stack ten
 * lights on top of each other. Long strips are cut into runs of {@link FixtureType#maxRun}
 * blocks, each lit from its middle, so they still light evenly along their length.
 */
final class FixtureClusters {
    /** A flood fill never grows past this many blocks (keeps one giant lit hall from stalling a tick). */
    private static final int MAX_CLUSTER = 96;

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * All lights whose blocks lie within {@code range} blocks (horizontally) of (x, z).
     *
     * @param world used to find the open side of cube fixtures (exit signs)
     */
    static List<FixtureLight> build(LightIndex index, BlockGetter world, int centerX, int centerZ, int range) {
        List<FixtureLight> out = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList stack = new LongArrayList();
        LongArrayList members = new LongArrayList();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int minCx = SectionPos.blockToSectionCoord(centerX - range);
        int maxCx = SectionPos.blockToSectionCoord(centerX + range);
        int minCz = SectionPos.blockToSectionCoord(centerZ - range);
        int maxCz = SectionPos.blockToSectionCoord(centerZ + range);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long[] positions = index.inChunk(cx, cz);
                if (positions == null) {
                    continue;
                }
                for (long start : positions) {
                    if (visited.contains(start)
                            || Math.abs(BlockPos.getX(start) - centerX) > range
                            || Math.abs(BlockPos.getZ(start) - centerZ) > range) {
                        continue;
                    }
                    BlockState startState = index.get(start);
                    FixtureType type = startState == null ? null : FixtureType.of(startState);
                    if (type == null) {
                        continue;
                    }
                    collect(index, start, startState, type, visited, stack, members);
                    split(type, startState, members, world, cursor, out);
                }
            }
        }
        return out;
    }

    /** Flood fill from {@code start} over neighbours that belong to the same fixture. */
    private static void collect(LightIndex index, long start, BlockState startState, FixtureType type,
                                LongOpenHashSet visited, LongArrayList stack, LongArrayList members) {
        members.clear();
        stack.clear();
        stack.add(start);
        visited.add(start);
        Direction[] steps = steps(type, startState);
        while (!stack.isEmpty()) {
            long pos = stack.popLong();
            members.add(pos);
            if (members.size() >= MAX_CLUSTER) {
                // Too big: leave the rest for their own clusters (they start fresh later).
                for (int i = 0; i < stack.size(); i++) {
                    visited.remove(stack.getLong(i));
                }
                break;
            }
            for (Direction step : steps) {
                long next = BlockPos.offset(pos, step);
                if (visited.contains(next)) {
                    continue;
                }
                BlockState state = index.get(next);
                if (state != null && FixtureType.of(state) == type && sameMount(type, startState, state)) {
                    visited.add(next);
                    stack.add(next);
                }
            }
        }
    }

    /** Which neighbours can join a fixture's cluster. */
    private static Direction[] steps(FixtureType type, BlockState state) {
        if (type.maxRun <= 1) {
            return new Direction[0];
        }
        return switch (type.mount) {
            case CEILING_BLOCK -> HORIZONTAL;
            case HANGING_TUBE -> {
                // A tube strip only continues along its own axis.
                Direction.Axis axis = state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                        ? state.getValue(BlockStateProperties.HORIZONTAL_AXIS) : null;
                if (axis == Direction.Axis.X) {
                    yield new Direction[] {Direction.WEST, Direction.EAST};
                }
                if (axis == Direction.Axis.Z) {
                    yield new Direction[] {Direction.NORTH, Direction.SOUTH};
                }
                yield HORIZONTAL;
            }
            case WALL -> {
                // Stacked up the wall, or side by side along it.
                Direction facing = facingOf(state);
                if (facing == null || !facing.getAxis().isHorizontal()) {
                    yield new Direction[] {Direction.UP, Direction.DOWN};
                }
                yield new Direction[] {Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()};
            }
            case CUBE -> Direction.values();
            // Pool lamps sit alone in their wall (maxRun 1), so this is never reached in practice.
            case INSET -> new Direction[0];
        };
    }

    private static boolean sameMount(FixtureType type, BlockState a, BlockState b) {
        return switch (type.mount) {
            case WALL -> facingOf(a) == facingOf(b);
            case HANGING_TUBE -> !a.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                    || a.getValue(BlockStateProperties.HORIZONTAL_AXIS) == b.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(null);
            default -> true;
        };
    }

    /** Cuts a cluster into runs of at most maxRun blocks per axis and makes one light per run. */
    private static void split(FixtureType type, BlockState state, LongArrayList members, BlockGetter world,
                              BlockPos.MutableBlockPos cursor, List<FixtureLight> out) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int i = 0; i < members.size(); i++) {
            long pos = members.getLong(i);
            minX = Math.min(minX, BlockPos.getX(pos));
            minY = Math.min(minY, BlockPos.getY(pos));
            minZ = Math.min(minZ, BlockPos.getZ(pos));
        }
        int run = Math.max(1, type.maxRun);
        // Group members by which run-sized box they fall in.
        Long2ObjectOpenHashMap<LongArrayList> runs = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < members.size(); i++) {
            long pos = members.getLong(i);
            long box = BlockPos.asLong((BlockPos.getX(pos) - minX) / run, (BlockPos.getY(pos) - minY) / run,
                    (BlockPos.getZ(pos) - minZ) / run);
            LongArrayList group = runs.get(box);
            if (group == null) {
                group = new LongArrayList();
                runs.put(box, group);
            }
            group.add(pos);
        }
        Direction facing = type.mount == FixtureType.Mount.WALL || type.mount == FixtureType.Mount.INSET
                ? facingOf(state) : null;
        for (LongArrayList blocks : runs.values()) {
            out.add(makeLight(type, facing, blocks, world, cursor));
        }
    }

    private static FixtureLight makeLight(FixtureType type, @Nullable Direction facing, LongArrayList blocks,
                                          BlockGetter world, BlockPos.MutableBlockPos cursor) {
        long key = Long.MAX_VALUE;
        double sx = 0;
        double sy = 0;
        double sz = 0;
        for (int i = 0; i < blocks.size(); i++) {
            long pos = blocks.getLong(i);
            key = Math.min(key, pos);
            sx += BlockPos.getX(pos) + 0.5;
            sy += BlockPos.getY(pos) + 0.5;
            sz += BlockPos.getZ(pos) + 0.5;
        }
        int n = blocks.size();
        double cx = sx / n;
        double cy = sy / n;
        double cz = sz / n;

        // Put the light in open air next to the fixture (Veil's shadow march needs that).
        double lx = cx;
        double ly = cy;
        double lz = cz;
        switch (type.mount) {
            // Full ceiling block: one block down, the middle of the air cell below it.
            case CEILING_BLOCK -> ly = cy - 1.0;
            // Tube hangs at the top of its own air cell: just under it.
            case HANGING_TUBE -> ly = cy - 0.05;
            // Thin wall lamp: a little out from the wall, still in its own cell.
            case WALL -> {
                if (facing != null) {
                    lx += facing.getStepX() * 0.2;
                    lz += facing.getStepZ() * 0.2;
                }
            }
            // Cube in a wall: three quarters of a block out into whichever side is open.
            case CUBE -> {
                Direction open = openSide(BlockPos.of(key), world, cursor);
                lx += open.getStepX() * 0.75;
                ly += open.getStepY() * 0.75;
                lz += open.getStepZ() * 0.75;
            }
            // Full block with the lamp on one face: three quarters of a block out of that face
            // (into the water for a pool lamp), where nothing blocks it.
            case INSET -> {
                if (facing != null) {
                    lx += facing.getStepX() * 0.75;
                    lz += facing.getStepZ() * 0.75;
                }
            }
        }
        return new FixtureLight(key, type, lx, ly, lz, cx, cy, cz, facing);
    }

    private static Direction openSide(BlockPos pos, BlockGetter world, BlockPos.MutableBlockPos cursor) {
        for (Direction side : HORIZONTAL) {
            if (world.getBlockState(cursor.setWithOffset(pos, side)).isAir()) {
                return side;
            }
        }
        return Direction.DOWN;
    }

    @Nullable
    static Direction facingOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return null;
    }

    private FixtureClusters() {}
}
