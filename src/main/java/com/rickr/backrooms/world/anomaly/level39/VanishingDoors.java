package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39HouseSpec;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Canon: "doors that vanish once you look behind them". In a VANISHING_DOOR house, walk through a
 * door, and the moment it is out of your sight (and nobody else's) the doorway quietly becomes wall.
 * Turn round: there was never a door there. Once nobody is near (or after a long while, unseen) the
 * door is quietly put back, so the house is the same for the next visitor.
 *
 * <p>The doors are found from the house's own blocks as the world generator built them (the layout
 * is a pure function), so this works for any door the house builder places, and a server restart
 * in the middle simply means the door comes back the next time nobody is looking.
 */
final class VanishingDoors implements AnomalyBehavior {
    /** You must be this far past the door (blocks from its plane) before it can go. */
    private static final double GONE_DISTANCE = 2.2;
    /** A passed door can vanish for this long afterwards (ticks); then it stops waiting. */
    private static final long PASS_WINDOW = 30 * 20;
    /** Restore once nobody is within this range and nobody sees it... */
    private static final double RESTORE_CLEAR = 12.0;
    /** ...or, unseen, after this long anyway (so you are never walled in for good). */
    private static final long RESTORE_AFTER = 45 * 20;
    private static final int CACHE_LIMIT = 64;

    /** One door as the generator built it. */
    record Door(BlockPos lower, Direction facing, BlockState lowerState, BlockState upperState) {
        Vec3 centre() {
            return new Vec3(lower.getX() + 0.5, lower.getY() + 1.0, lower.getZ() + 0.5);
        }

        /** Which side of the door's plane a point is on (+1 / -1), or 0 right in it. */
        int side(Vec3 p) {
            double d = facing.getAxis() == Direction.Axis.X ? p.x - (lower.getX() + 0.5) : p.z - (lower.getZ() + 0.5);
            return Math.abs(d) < 0.15 ? 0 : d > 0 ? 1 : -1;
        }

        double planeDistance(Vec3 p) {
            return Math.abs(facing.getAxis() == Direction.Axis.X ? p.x - (lower.getX() + 0.5) : p.z - (lower.getZ() + 0.5));
        }

        double sideways(Vec3 p) {
            return Math.abs(facing.getAxis() == Direction.Axis.X ? p.z - (lower.getZ() + 0.5) : p.x - (lower.getX() + 0.5));
        }
    }

    private final Map<AnomalySite, List<Door>> doors = new LinkedHashMap<>();
    /** Per player: the side of each nearby door they were on last tick, and doors they walked through (tick). */
    private final Map<UUID, Map<BlockPos, Integer>> sides = new HashMap<>();
    private final Map<UUID, Map<BlockPos, Long>> passed = new HashMap<>();
    /** When each currently vanished door went (game time). */
    private final Map<BlockPos, Long> vanishedAt = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        ServerLevel level = player.serverLevel();
        List<Door> list = doorsOf(seed, site);
        if (list.isEmpty()) {
            return;
        }
        Vec3 feet = player.position();
        long now = level.getGameTime();
        Map<BlockPos, Integer> mySides = sides.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Map<BlockPos, Long> myPassed = passed.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        for (Door door : list) {
            Vec3 c = door.centre();
            if (Math.abs(feet.y - door.lower().getY()) > 2.5 || c.distanceToSqr(feet.x, c.y, feet.z) > 8.0 * 8.0) {
                mySides.remove(door.lower());
                continue;
            }
            int side = door.side(feet);
            Integer before = mySides.get(door.lower());
            if (side != 0) {
                mySides.put(door.lower(), side);
                // Crossed the door's plane right where the doorway is: you went through it.
                if (before != null && before != side && door.sideways(feet) < 1.0 && isDoor(level, door)) {
                    myPassed.put(door.lower(), now);
                }
            }
        }
        myPassed.entrySet().removeIf(e -> now - e.getValue() > PASS_WINDOW);
        for (Door door : list) {
            Long when = myPassed.get(door.lower());
            if (when == null || now - when < 6) {
                continue;
            }
            Vec3 c = door.centre();
            if (door.planeDistance(feet) < GONE_DISTANCE || c.distanceTo(feet) > 16.0 || !isDoor(level, door)) {
                continue;
            }
            // Only while nobody at all can see the doorway, and nobody is standing in it.
            if (Observation.sees(player, c, 24.0, 80.0) || Observation.seenByAnyone(level, c, 32.0) || someoneIn(level, door)) {
                continue;
            }
            vanish(level, door);
            myPassed.remove(door.lower());
            whisperBehind(player, c);
        }
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        sides.remove(player.getUUID());
        passed.remove(player.getUUID());
    }

    /** Puts doors back once the coast is clear. */
    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        List<Door> list = doorsOf(seed, site);
        long now = level.getGameTime();
        for (Door door : list) {
            if (!level.isLoaded(door.lower()) || isDoor(level, door)) {
                continue;
            }
            Vec3 c = door.centre();
            if (Observation.seenByAnyone(level, c, 40.0)) {
                continue;
            }
            boolean clear = true;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && p.position().distanceTo(c) < RESTORE_CLEAR) {
                    clear = false;
                    break;
                }
            }
            long gone = vanishedAt.getOrDefault(door.lower(), Long.MIN_VALUE / 2);
            if (clear || now - gone > RESTORE_AFTER) {
                restore(level, door);
            }
        }
    }

    private static boolean isDoor(ServerLevel level, Door door) {
        return level.getBlockState(door.lower()).getBlock() instanceof DoorBlock;
    }

    private static boolean someoneIn(ServerLevel level, Door door) {
        Vec3 c = door.centre();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c.x, p.getY(), c.z) < 1.6 * 1.6 && Math.abs(p.getY() - door.lower().getY()) < 2.0) {
                return true;
            }
        }
        return false;
    }

    /** The doorway becomes the wall it stands in. */
    private void vanish(ServerLevel level, Door door) {
        BlockPos lower = door.lower();
        BlockPos upper = lower.above();
        BlockState wallLow = wallBeside(level, lower, door.facing());
        BlockState wallHigh = wallBeside(level, upper, door.facing());
        // No neighbour updates: the two door halves must not notice each other going (no dropped door).
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(upper, wallHigh, flags);
        level.setBlock(lower, wallLow, flags);
        vanishedAt.put(lower, level.getGameTime());
    }

    private void restore(ServerLevel level, Door door) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(door.lower(), door.lowerState(), flags);
        level.setBlock(door.lower().above(), door.upperState(), flags);
        vanishedAt.remove(door.lower());
    }

    /** The wall the door is set in: the solid block beside it, on either side. */
    private static BlockState wallBeside(ServerLevel level, BlockPos pos, Direction facing) {
        for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockState s = level.getBlockState(pos.relative(side));
            if (!s.isAir() && !(s.getBlock() instanceof DoorBlock) && s.isCollisionShapeFullBlock(level, pos.relative(side))) {
                return s;
            }
        }
        BlockState above = level.getBlockState(pos.above(2));
        return above.isAir() ? level.getBlockState(pos.relative(facing.getClockWise())) : above;
    }

    /** A soft creak behind you, for your ears only. */
    private static void whisperBehind(ServerPlayer player, Vec3 at) {
        player.connection.send(new ClientboundSoundPacket(Level39AnomalyRegistry.DOOR_VANISH, SoundSource.BLOCKS,
                at.x, at.y, at.z, 0.6f, 0.9f + player.getRandom().nextFloat() * 0.2f, player.getRandom().nextLong()));
    }

    // ==========================================================================================
    // The house's doors, from the generator's own blocks
    // ==========================================================================================

    private List<Door> doorsOf(long seed, AnomalySite site) {
        List<Door> list = doors.get(site);
        if (list != null) {
            return list;
        }
        if (doors.size() > CACHE_LIMIT) {
            doors.clear();
        }
        list = scan(seed, site);
        doors.put(site, list);
        return list;
    }

    private static List<Door> scan(long seed, AnomalySite site) {
        List<Door> out = new ArrayList<>();
        Level39HouseSpec h = Level39AnomalyPlan.houseOf(seed, site);
        if (h == null) {
            return out;
        }
        LevelLayout layout = BackroomsLevel.LEVEL_39.layout();
        BlockState[] column = new BlockState[layout.worldHeight()];
        LevelLayout.Column sink = (y, state) -> {
            if (y >= 0 && y < column.length) {
                column[y] = state;
            }
        };
        int yMin = Math.max(0, h.baseY() - 1);
        int yMax = Math.min(column.length - 2, h.baseY() + 7 * (h.storeys() + 1));
        for (int x = h.x0(); x <= h.x1(); x++) {
            for (int z = h.z0(); z <= h.z1(); z++) {
                java.util.Arrays.fill(column, null);
                layout.fillColumn(seed, x, z, sink);
                for (int y = yMin; y <= yMax; y++) {
                    BlockState s = column[y];
                    if (s != null && s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                        BlockState up = column[y + 1];
                        if (up != null && up.getBlock() instanceof DoorBlock) {
                            out.add(new Door(new BlockPos(x, y, z), s.getValue(DoorBlock.FACING), s, up));
                        }
                    }
                }
            }
        }
        return out;
    }
}
