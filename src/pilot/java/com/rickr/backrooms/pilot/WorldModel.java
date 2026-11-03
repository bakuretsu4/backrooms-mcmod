package com.rickr.backrooms.pilot;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Read-only view of the client's loaded world, reduced to what walking needs: where a 0.6 x 1.8
 * player can stand, and whether the space it sweeps through is free. Everything comes from the
 * blocks' real collision shapes, so slabs, stairs, fences, carpets and modded shapes are handled
 * the same way the physics engine handles them. Results are cached per plan because A* asks
 * about the same cells many times.
 */
final class WorldModel {
    // Per-block flags (cached).
    static final int UNLOADED = 1, COLLIDES = 1 << 1, FULL = 1 << 2, WATER = 1 << 3, LAVA = 1 << 4,
            HAZARD = 1 << 5, CLIMB = 1 << 6, SCAFFOLD = 1 << 7, OPENABLE = 1 << 8, SLOW = 1 << 9;
    // Highest collision top under the centred player footprint, in 1/64ths of a block (0..127).
    // 0 means the shape only sits beside the footprint (ladder plate, door, wall-side trapdoor).
    private static final int TOP_SHIFT = 16;

    // Node kinds.
    static final int STAND = 1, CLIMBING = 2, SWIM = 3;

    static final double HALF_WIDTH = 0.3;
    static final double HEIGHT = 1.8;
    private static final double MARGIN = 0.2; // player box sits 0.2..0.8 inside its cell

    final ClientLevel level;
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
    private final Long2IntOpenHashMap blockCache = new Long2IntOpenHashMap(8192);
    private final Long2LongOpenHashMap nodeCache = new Long2LongOpenHashMap(8192);
    /** Doors the follower failed to open; treated as walls until the next goto. */
    final LongOpenHashSet stuckDoors;

    private long lastChunkKey = Long.MIN_VALUE;
    private LevelChunk lastChunk;

    private final BoxTest boxTest = new BoxTest();
    /** Door found by the last {@link #boxClear} call, or Long.MIN_VALUE. */
    long lastDoor;

    WorldModel(ClientLevel level, LongOpenHashSet stuckDoors) {
        this.level = level;
        this.stuckDoors = stuckDoors;
        blockCache.defaultReturnValue(-1);
        nodeCache.defaultReturnValue(Long.MIN_VALUE);
    }

    private LevelChunk chunk(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        if (key != lastChunkKey) {
            lastChunkKey = key;
            lastChunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
        }
        return lastChunk;
    }

    BlockState state(int x, int y, int z) {
        LevelChunk c = chunk(x, z);
        if (c == null || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return null;
        }
        return c.getBlockState(mpos.set(x, y, z));
    }

    boolean loaded(int x, int z) {
        return chunk(x, z) != null;
    }

    int flags(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        int f = blockCache.get(key);
        if (f == -1) {
            f = computeFlags(x, y, z);
            blockCache.put(key, f);
        }
        return f;
    }

    static double top(int flags) {
        return ((flags >>> TOP_SHIFT) & 0x7F) / 64.0;
    }

    private int computeFlags(int x, int y, int z) {
        BlockState s = state(x, y, z);
        if (s == null) {
            // Below the world is a wall; above the build limit is open air; unloaded is unknown.
            if (loaded(x, z)) {
                return y < level.getMinBuildHeight() ? COLLIDES | FULL | (64 << TOP_SHIFT) : 0;
            }
            return UNLOADED;
        }
        int f = 0;
        FluidState fluid = s.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.is(FluidTags.LAVA)) f |= LAVA;
            else if (fluid.is(FluidTags.WATER)) f |= WATER;
        }
        Block b = s.getBlock();
        if (s.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(s) || b == Blocks.MAGMA_BLOCK || b == Blocks.CACTUS
                || b == Blocks.POWDER_SNOW || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.WITHER_ROSE
                || b == Blocks.LAVA_CAULDRON || b == Blocks.POINTED_DRIPSTONE) {
            f |= HAZARD;
        }
        if (b == Blocks.COBWEB || b == Blocks.HONEY_BLOCK || b == Blocks.SOUL_SAND) f |= SLOW;
        if (b == Blocks.SCAFFOLDING) {
            // Its collision depends on where the player is; treat it as a climbable column you can
            // also stand on top of (which is what it is for a player).
            return f | CLIMB | SCAFFOLD | (64 << TOP_SHIFT);
        }
        if (s.is(BlockTags.CLIMBABLE)) f |= CLIMB;
        if (isOpenable(s) && !isOpen(s) && !stuckDoors.contains(doorKey(x, y, z, s))) f |= OPENABLE;
        if (s.isAir()) return f;
        VoxelShape shape = s.getCollisionShape(level, mpos.set(x, y, z));
        if (shape.isEmpty()) return f;
        f |= COLLIDES;
        if (shape == Shapes.block()) return f | FULL | (64 << TOP_SHIFT);
        double top = boxTest.topUnderFootprint(shape, MARGIN, 1 - MARGIN);
        return f | ((int) Math.round(Math.min(top, 1.98) * 64.0) << TOP_SHIFT);
    }

    static boolean isOpenable(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof DoorBlock door) return door.type().canOpenByHand();
        if (b instanceof TrapDoorBlock) return b != Blocks.IRON_TRAPDOOR;
        return b instanceof FenceGateBlock;
    }

    static boolean isOpen(BlockState s) {
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    /** Both halves of a door share one key (the lower half), so a door is opened once. */
    static long doorKey(int x, int y, int z, BlockState s) {
        if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return BlockPos.asLong(x, y - 1, z);
        }
        return BlockPos.asLong(x, y, z);
    }

    /**
     * Height the player's feet rest at when centred in cell (x,c,z), or NaN when there is
     * nothing to stand on inside that cell's height range [c, c+1).
     */
    double support(int x, int c, int z) {
        int fb = flags(x, c, z);
        if ((fb & UNLOADED) != 0) return Double.NaN;
        double h = Double.NaN;
        if ((fb & COLLIDES) != 0 && (fb & SCAFFOLD) == 0) {
            double t = top(fb);
            // A low shape (slab, carpet, snow layer) is a floor. A tall one (door, pane, ladder, full
            // block) is not; whether the body still fits beside it is the box test's job.
            if (t > 0 && t < 1.0) h = c + t;
        }
        int bb = flags(x, c - 1, z);
        if ((bb & UNLOADED) == 0 && ((bb & COLLIDES) != 0 || (bb & SCAFFOLD) != 0)) {
            double t = top(bb);
            if (t >= 1.0 && (Double.isNaN(h) || c - 1 + t > h)) {
                h = c - 1 + t;
                if ((bb & HAZARD) != 0) return Double.NaN; // magma, cactus, lit campfire underfoot
            }
        }
        return h;
    }

    /**
     * Classify cell (x,c,z) as a place the player can be: standing, climbing or swimming.
     * Returns packed (kind << 32 | floatBits(h)) or 0 when invalid.
     */
    long node(int x, int c, int z) {
        long key = BlockPos.asLong(x, c, z);
        long v = nodeCache.get(key);
        if (v == Long.MIN_VALUE) {
            v = computeNode(x, c, z);
            nodeCache.put(key, v);
        }
        return v;
    }

    static int kind(long node) {
        return (int) (node >>> 32);
    }

    static double height(long node) {
        return Float.intBitsToFloat((int) node);
    }

    private static long pack(int kind, double h) {
        return ((long) kind << 32) | (Float.floatToRawIntBits((float) h) & 0xFFFFFFFFL);
    }

    private long computeNode(int x, int c, int z) {
        int fb = flags(x, c, z);
        if ((fb & (UNLOADED | LAVA | HAZARD)) != 0) return 0;
        double h = support(x, c, z);
        int kind;
        if ((fb & WATER) != 0 && (fb & COLLIDES) == 0) {
            kind = SWIM;
            if (Double.isNaN(h)) h = c;
        } else if ((fb & CLIMB) != 0) {
            kind = CLIMBING;
            if (Double.isNaN(h)) h = c;
        } else if (!Double.isNaN(h)) {
            kind = STAND;
        } else {
            return 0;
        }
        // A closed door/gate/trapdoor may occupy the cell; the move into it carries the door so the
        // follower opens it first.
        if (!boxClear(x + MARGIN, z + MARGIN, x + 1 - MARGIN, z + 1 - MARGIN, h + 0.001, h + HEIGHT, true)) {
            return 0;
        }
        return pack(kind, h);
    }

    /**
     * True when the axis-aligned box is free of collision, lava and harmful blocks. With
     * allowDoor, one closed door/gate/trapdoor the player could open is tolerated and reported in
     * {@link #lastDoor}; a second different one fails the test.
     */
    boolean boxClear(double minX, double minZ, double maxX, double maxZ, double minY, double maxY, boolean allowDoor) {
        lastDoor = Long.MIN_VALUE;
        int x0 = Mth.floor(minX), x1 = Mth.floor(maxX - 1e-7);
        int z0 = Mth.floor(minZ), z1 = Mth.floor(maxZ - 1e-7);
        int y0 = Mth.floor(minY), y1 = Mth.floor(maxY - 1e-7);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    int f = flags(x, y, z);
                    if ((f & (UNLOADED | LAVA)) != 0) return false;
                    if ((f & HAZARD) != 0 && (f & COLLIDES) == 0) return false; // fire, berry bush, powder snow
                    if ((f & COLLIDES) == 0 || (f & SCAFFOLD) != 0) continue;
                    if ((f & FULL) == 0) {
                        BlockState s = state(x, y, z);
                        VoxelShape shape = s.getCollisionShape(level, mpos.set(x, y, z));
                        if (!boxTest.hits(shape, minX - x, minY - y, minZ - z, maxX - x, maxY - y, maxZ - z)) continue;
                        if (allowDoor && (f & OPENABLE) != 0) {
                            long key = doorKey(x, y, z, s);
                            if (lastDoor == Long.MIN_VALUE || lastDoor == key) {
                                lastDoor = key;
                                continue;
                            }
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Allocation-free shape queries: "does any box overlap the query box" and "highest top among
     * boxes overlapping the query's horizontal area" (the query's Y range is left fully open).
     */
    private static final class BoxTest implements Shapes.DoubleLineConsumer {
        private double qx0, qy0, qz0, qx1, qy1, qz1;
        private boolean hit;
        private double top;

        boolean hits(VoxelShape shape, double x0, double y0, double z0, double x1, double y1, double z1) {
            qx0 = x0; qy0 = y0; qz0 = z0; qx1 = x1; qy1 = y1; qz1 = z1;
            hit = false;
            top = 0;
            shape.forAllBoxes(this);
            return hit;
        }

        double topUnderFootprint(VoxelShape shape, double lo, double hi) {
            hits(shape, lo, Double.NEGATIVE_INFINITY, lo, hi, Double.POSITIVE_INFINITY, hi);
            return top;
        }

        @Override
        public void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if (minX < qx1 && maxX > qx0 && minY < qy1 && maxY > qy0 && minZ < qz1 && maxZ > qz0) {
                hit = true;
                if (maxY > top) top = maxY;
            }
        }
    }
}
