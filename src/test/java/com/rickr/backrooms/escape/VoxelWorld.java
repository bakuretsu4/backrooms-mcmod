package com.rickr.backrooms.escape;

import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blocks a level's chunk generator would place, rebuilt column by column from the layout's own
 * {@link LevelLayout#fillColumn} (exactly what BackroomsChunkGenerator does), and boiled down to
 * what matters for a player moving around: can a body be here, can you stand on it, is it water,
 * a ladder, a hazard, or the exit itself.
 *
 * <p>Only a square window of columns is ever built (lazily, as the search reaches it), so a test
 * can look at a few hundred blocks around an arrival without generating a whole world.
 */
final class VoxelWorld {
    /** A body can occupy this block (air, no collision, a door you can open by hand, a ladder...). */
    static final int PASS = 1;
    static final int WATER = 2;
    static final int CLIMB = 4;
    /** Solid with its top at most a full block up: you can stand on top of it. */
    static final int SUPPORT = 8;
    /** Low collision (slab, carpet, half step): you stand inside this block, half a block up. */
    static final int LOW = 16;
    /** One of the blocks that make up the exit (the trigger the test is looking for). */
    static final int GOAL = 32;
    /** Lava, fire, magma, cactus: never stand in or on it. */
    static final int HAZARD = 64;
    /** Taller than a block (fences, walls): also blocks the block above it, and is never a floor. */
    static final int TALL = 128;
    /** The bottom half of a stair block: walked up without jumping, like a half step then another. */
    static final int STAIR = 256;

    final LevelLayout layout;
    final long seed;
    final int height;
    final int originX;
    final int originZ;
    final int size;
    private final Predicate<BlockState> goalBlock;
    private final short[][] columns;
    private final Map<BlockState, Short> classes = new IdentityHashMap<>();
    private final BlockState[] scratch;
    int built;

    VoxelWorld(LevelLayout layout, long seed, int centreX, int centreZ, int halfSize, Predicate<BlockState> goalBlock) {
        this.layout = layout;
        this.seed = seed;
        this.height = layout.worldHeight();
        this.size = 2 * halfSize + 1;
        this.originX = centreX - halfSize;
        this.originZ = centreZ - halfSize;
        this.goalBlock = goalBlock;
        this.columns = new short[size * size][];
        this.scratch = new BlockState[height];
    }

    boolean inside(int x, int z) {
        int ix = x - originX;
        int iz = z - originZ;
        return ix >= 0 && iz >= 0 && ix < size && iz < size;
    }

    /** Flags of block (x, y, z); outside the world height it is open air (above) or nothing (below). */
    int flags(int x, int y, int z) {
        if (y < 0) {
            return 0;
        }
        short[] column = column(x, z);
        return y < column.length ? column[y] & 0xFFFF : PASS;
    }

    private short[] column(int x, int z) {
        int i = (x - originX) * size + (z - originZ);
        short[] column = columns[i];
        if (column == null) {
            column = build(x, z);
            columns[i] = column;
            built++;
        }
        return column;
    }

    /** The real column (as the chunk generator would write it), kept only up to its last block. */
    private short[] build(int x, int z) {
        Arrays.fill(scratch, null);
        int[] top = {-1};
        layout.fillColumn(seed, x, z, (y, state) -> {
            if (y >= 0 && y < height) {
                scratch[y] = state;
                if (!state.isAir() && y > top[0]) {
                    top[0] = y;
                }
            }
        });
        // Up to the last block and one air above it (everything higher is open air), within the world.
        short[] column = new short[Math.min(top[0] + 2, height)];
        for (int y = 0; y < column.length; y++) {
            BlockState state = scratch[y];
            column[y] = state == null ? PASS : classOf(state);
        }
        return column;
    }

    /** The block itself, for messages ("what blocked"). */
    BlockState state(int x, int z, int y) {
        BlockState[] found = new BlockState[1];
        layout.fillColumn(seed, x, z, (yy, state) -> {
            if (yy == y) {
                found[0] = state;
            }
        });
        return found[0];
    }

    short classOf(BlockState state) {
        return classes.computeIfAbsent(state, this::computeClass);
    }

    private short computeClass(BlockState state) {
        int f = 0;
        if (goalBlock.test(state)) {
            f |= GOAL;
        }
        if (state.isAir()) {
            return (short) (f | PASS);
        }
        Block block = state.getBlock();
        FluidState fluid = state.getFluidState();
        boolean water = !fluid.isEmpty() && fluid.getType().isSame(Fluids.WATER);
        if (!fluid.isEmpty() && !water || block instanceof BaseFireBlock || block instanceof MagmaBlock
                || block instanceof CactusBlock) {
            f |= HAZARD;
        }
        if (water) {
            f |= WATER;
        }
        boolean climb = climbable(state);
        if (climb) {
            f |= CLIMB;
        }
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        if (shape.isEmpty() || climb || openableByHand(state)) {
            return (short) (f | PASS);
        }
        double maxY = shape.max(Direction.Axis.Y);
        if (maxY <= 0.5 + 1e-6) {
            return (short) (f | LOW);
        }
        if (maxY <= 1.0 + 1e-6) {
            if (block instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
                f |= STAIR;
            }
            return (short) (f | SUPPORT);
        }
        return (short) (f | TALL);
    }

    private static boolean openableByHand(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock door) {
            if (state.getValue(DoorBlock.OPEN)) {
                return true; // already open: only a thin panel at the edge
            }
            if (state.hasProperty(HouseDoorBlock.LOCKED) && state.getValue(HouseDoorBlock.LOCKED)) {
                return false; // Level 9's locked front doors only rattle
            }
            return door.type().canOpenByHand();
        }
        if (block instanceof TrapDoorBlock trapdoor) {
            return trapdoorOpensByHand(trapdoor);
        }
        return block instanceof FenceGateBlock;
    }

    /** TrapDoorBlock keeps its BlockSetType behind a protected getter; read it the way the block does. */
    static boolean trapdoorOpensByHand(TrapDoorBlock trapdoor) {
        try {
            java.lang.reflect.Method getType = TrapDoorBlock.class.getDeclaredMethod("getType");
            getType.setAccessible(true);
            return ((net.minecraft.world.level.block.state.properties.BlockSetType) getType.invoke(trapdoor)).canOpenByHand();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("can't read a trapdoor's BlockSetType", e);
        }
    }

    private static boolean climbable(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LadderBlock || block instanceof VineBlock || block instanceof ScaffoldingBlock) {
            return true;
        }
        if (block instanceof TrapDoorBlock && openableByHand(state)) {
            // Vanilla lets you climb on through an open trapdoor at the top of a ladder (Level 39's
            // shaft hatches). Elsewhere this changes nothing: climbing only ever moves you to
            // another position you could stand, swim or climb in.
            return true;
        }
        try {
            // The mod's ropes, climbing frames and hanging vines say so themselves (they ignore the level).
            return block.isLadder(state, null, BlockPos.ZERO, null);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
