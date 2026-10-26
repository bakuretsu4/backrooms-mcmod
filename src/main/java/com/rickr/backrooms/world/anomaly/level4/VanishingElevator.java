package com.rickr.backrooms.world.anomaly.level4;

import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.OfficePanelBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level4AnomalyBlocks;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.LobbyFrame;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The vanishing elevator. In an elevator lobby whose elevators never come, one of them stands open,
 * its car lit. Look away for a few seconds and it is gone: plain wall where its doors were, and the
 * open, lit car is now at another door of the lobby (you may hear its chime). Step inside and the
 * doors close behind you; in the dark you feel a long, long descent, then the doors open onto an
 * identical-looking lobby somewhere else on Level 4. Sometimes there is nowhere else to go, and the
 * doors simply open again where you got in.
 *
 * <p>Canon: Level 4's exits (stairways, elevators) "may disappear unexpectedly" once you lose
 * direct sight of them (https://backrooms-wiki.wikidot.com/level-4); its elevators mostly lead
 * "seemingly nowhere" (https://backrooms.fandom.com/wiki/Level_4); and they go to other floors that
 * look the same (https://backrooms-wiki.wikidot.com/desolation-level-4). The ride stays on Level 4.
 *
 * <p>All the state lives in the blocks (which doors are open, closed or walled over), so it
 * survives restarts; the server only remembers when somebody last looked at the open one.
 * Nobody is ever shut in: if a player stands in a car whose doors are closed and isn't riding it
 * (logged out mid-ride, server restart...), the doors open again.
 */
final class VanishingElevator implements AnomalyBehavior {
    /** Standing this long in an open car takes you down (so peeking in is safe). */
    private static final int STEP_IN_TICKS = 15;
    /** The ride in the dark. */
    private static final int RIDE_TICKS = 100;
    /** After arriving, the doors open this much later. */
    private static final int OPEN_DELAY = 14;
    /** The open elevator moves once nobody has looked at it for this long. */
    private static final long UNSEEN_TICKS = 80;
    private static final double SEEN_RANGE = 48.0;
    /** Nobody this close to the doors: then (and only then) it may move. */
    private static final double NEAR = 4.0;
    /** How far away the other lobby is looked for. */
    private static final int RIDE_DISTANCE = 320;

    private static final int FLOOR = LevelLayout.FLOOR_Y;

    private enum State { OPEN, CLOSED, BLANK, UNLOADED }

    /** One player's trip: from which car, to which (null = nowhere: back where they started). */
    private record Ride(AnomalySite from, int fromDoor, @Nullable AnomalySite to, int toDoor) {}

    private static final Map<UUID, Ride> RIDES = new HashMap<>();
    private static final Map<UUID, Integer> IN_CAR = new HashMap<>();
    /** Just arrived: they must step out of the car before it can take them again. */
    private static final Set<UUID> ARRIVED = new HashSet<>();
    private static final Map<AnomalySite, Long> LAST_SEEN = new HashMap<>();

    // ==========================================================================================
    // Riding
    // ==========================================================================================

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        LobbyFrame f = Level4AnomalyPlan.lobbyFrame(site);
        UUID id = player.getUUID();
        int car = carOf(f, player);
        if (car < 0) {
            ARRIVED.remove(id);
            IN_CAR.remove(id);
            return;
        }
        if (RIDES.containsKey(id) || ARRIVED.contains(id)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (state(level, f, car) != State.OPEN) {
            IN_CAR.remove(id);
            return;
        }
        int t = IN_CAR.merge(id, 1, Integer::sum);
        if (t >= STEP_IN_TICKS) {
            IN_CAR.remove(id);
            startRide(level, site, f, car, seed);
        }
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        IN_CAR.remove(player.getUUID());
    }

    /** The doors close on everyone in the car, the light goes, and down it goes. */
    private static void startRide(ServerLevel level, AnomalySite site, LobbyFrame f, int car, long seed) {
        List<ServerPlayer> riders = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && carOf(f, p) == car && site.contains(p.getBlockX(), p.getBlockZ())) {
                riders.add(p);
            }
        }
        if (riders.isEmpty()) {
            return;
        }
        setDoor(level, f, car, State.CLOSED);
        setCarLight(level, f, car, LampMode.DEAD);
        Vec3 face = doorFace(f, car);
        level.playSound(null, face.x, face.y, face.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);

        AnomalySite to = destination(level, site, seed);
        int toDoor = to == null ? -1 : Hash.range(Hash.mix(to.salt() ^ level.getGameTime()), Level4AnomalyPlan.ELEVATOR_DOORS);
        Ride ride = new Ride(site, car, to, toDoor);
        for (ServerPlayer rider : riders) {
            RIDES.put(rider.getUUID(), ride);
            rider.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, RIDE_TICKS + OPEN_DELAY + 20, 0, false, false, false));
            rider.playNotifySound(ModSounds.L4_ELEVATOR_RIDE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            Level4Anomalies.later(rider, RIDE_TICKS, VanishingElevator::arrive);
        }
    }

    /**
     * Another vanishing elevator about {@link #RIDE_DISTANCE} blocks away in a direction that
     * changes every ten seconds, or null if the nearest one there is this one (a ride to nowhere).
     */
    @Nullable
    private static AnomalySite destination(ServerLevel level, AnomalySite from, long seed) {
        long h = Hash.mix(from.salt() ^ (level.getGameTime() / 200) * 0x9E3779B97F4A7C15L);
        double angle = Hash.unit(h) * Math.PI * 2.0;
        int tx = (int) Math.round(from.centerX() + Math.cos(angle) * RIDE_DISTANCE);
        int tz = (int) Math.round(from.centerZ() + Math.sin(angle) * RIDE_DISTANCE);
        AnomalySite to = BackroomsLevel.LEVEL_4.layout().nearestAnomaly(seed, tx, tz, Level4AnomalyPlan.VANISHING_ELEVATOR);
        return to == null || to.equals(from) ? null : to;
    }

    /** The ride is over: the doors open somewhere else (or, for a ride to nowhere, right here). */
    private static void arrive(ServerPlayer player) {
        Ride ride = RIDES.remove(player.getUUID());
        if (ride == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LobbyFrame from = Level4AnomalyPlan.lobbyFrame(ride.from());
        if (ride.to() == null) {
            setCarLight(level, from, ride.fromDoor(), LampMode.LIT);
            setDoor(level, from, ride.fromDoor(), State.OPEN);
            ding(level, from, ride.fromDoor());
            ARRIVED.add(player.getUUID());
            player.removeEffect(MobEffects.BLINDNESS);
            return;
        }
        LobbyFrame to = Level4AnomalyPlan.lobbyFrame(ride.to());
        int door = ride.toDoor();
        // Make sure the far lobby exists before touching its blocks (loads it if nobody is there).
        level.getChunk(to.worldX(to.doorU(door), to.carLightV(door)) >> 4, to.worldZ(to.doorU(door), to.carLightV(door)) >> 4);
        level.getChunk(to.worldX(to.doorU(door), to.doorV(door)) >> 4, to.worldZ(to.doorU(door), to.doorV(door)) >> 4);
        setDoor(level, to, door, State.CLOSED);
        setCarLight(level, to, door, LampMode.LIT);
        // Into the middle of the car, facing the doors.
        int lv = to.carLightV(door);
        double x = (to.worldX(to.doorU(door), lv) + to.worldX(to.doorU(door) + 1, lv)) / 2.0 + 0.5;
        double z = (to.worldZ(to.doorU(door), lv) + to.worldZ(to.doorU(door) + 1, lv)) / 2.0 + 0.5;
        float yaw = Direction.from2DDataValue(to.out(door)).toYRot();
        Teleports.to(player, x, FLOOR + 1, z, yaw, 0.0f);
        ARRIVED.add(player.getUUID());
        // The car you left gets its light back (its doors stay shut until the lobby opens another).
        setCarLight(level, from, ride.fromDoor(), LampMode.LIT);
        Level4Anomalies.later(player, OPEN_DELAY, p -> {
            setDoor(level, to, door, State.OPEN);
            ding(level, to, door);
            p.removeEffect(MobEffects.BLINDNESS);
        });
    }

    // ==========================================================================================
    // The lobby rearranging itself
    // ==========================================================================================

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        LobbyFrame f = Level4AnomalyPlan.lobbyFrame(site);
        int n = Level4AnomalyPlan.ELEVATOR_DOORS;
        State[] states = new State[n];
        for (int i = 0; i < n; i++) {
            states[i] = state(level, f, i);
            if (states[i] == State.UNLOADED) {
                return;
            }
        }
        boolean[] busy = new boolean[n];
        for (ServerPlayer p : level.players()) {
            int car = p.isSpectator() || !site.contains(p.getBlockX(), p.getBlockZ()) ? -1 : carOf(f, p);
            if (car < 0) {
                continue;
            }
            busy[car] = true;
            // Never shut anyone in: someone standing in a closed (or walled-over) car who isn't on a
            // ride gets out.
            if (states[car] != State.OPEN && !RIDES.containsKey(p.getUUID())) {
                setDoor(level, f, car, State.OPEN);
                setCarLight(level, f, car, LampMode.LIT);
                states[car] = State.OPEN;
            }
        }
        for (Ride ride : RIDES.values()) {
            if (ride.from().equals(site)) busy[ride.fromDoor()] = true;
            if (site.equals(ride.to())) busy[ride.toDoor()] = true;
        }
        long now = level.getGameTime();
        if (LAST_SEEN.size() > 512) {
            LAST_SEEN.clear();
        }

        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (states[i] == State.OPEN) open.add(i);
        }
        if (open.isEmpty()) {
            // After a ride (or at first) no car stands open: one quietly opens where nobody looks.
            int pick = pickUnseen(level, f, states, busy, -1);
            if (pick >= 0) {
                setDoor(level, f, pick, State.OPEN);
                setCarLight(level, f, pick, LampMode.LIT);
                ding(level, f, pick);
                LAST_SEEN.put(site, now);
            }
            return;
        }
        int primary = open.get(0);
        // More than one open (an arrival opened a second one): the extra ones close when unseen.
        for (int k = 1; k < open.size(); k++) {
            int extra = open.get(k);
            if (!busy[extra] && !seen(level, f, extra)) {
                setDoor(level, f, extra, State.CLOSED);
            }
        }
        if (busy[primary] || seen(level, f, primary) || someoneNear(level, f, primary)) {
            LAST_SEEN.put(site, now);
            return;
        }
        long last = LAST_SEEN.computeIfAbsent(site, k -> now);
        if (now - last < UNSEEN_TICKS) {
            return;
        }
        int next = pickUnseen(level, f, states, busy, primary);
        if (next < 0) {
            return; // every other door is in somebody's view: wait
        }
        // Gone: wall where it stood. The door walled over last time comes back (when unseen)...
        for (int i = 0; i < n; i++) {
            if (states[i] == State.BLANK && i != next && !seen(level, f, i)) {
                setDoor(level, f, i, State.CLOSED);
            }
        }
        setDoor(level, f, primary, State.BLANK);
        // ...and the open, lit car is now somewhere else.
        setDoor(level, f, next, State.OPEN);
        setCarLight(level, f, next, LampMode.LIT);
        ding(level, f, next);
        LAST_SEEN.put(site, now);
    }

    /** A random closed door that nobody can see and nobody is using (not {@code except}), or -1. */
    private static int pickUnseen(ServerLevel level, LobbyFrame f, State[] states, boolean[] busy, int except) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            if (i != except && states[i] == State.CLOSED && !busy[i] && !seen(level, f, i)) {
                candidates.add(i);
            }
        }
        return candidates.isEmpty() ? -1 : candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /** Which car (0..7) the player stands in, or -1. */
    private static int carOf(LobbyFrame f, ServerPlayer p) {
        if (p.getY() < FLOOR + 0.5 || p.getY() > FLOOR + 3.5) {
            return -1;
        }
        int x = p.getBlockX();
        int z = p.getBlockZ();
        return f.carAt(f.u(x, z), f.v(x, z));
    }

    private static BlockPos doorPos(LobbyFrame f, int door, int column, int y) {
        int u = f.doorU(door) + column;
        int v = f.doorV(door);
        return new BlockPos(f.worldX(u, v), y, f.worldZ(u, v));
    }

    /** The hall side of the door's wall, between its two columns, at eye height. */
    private static Vec3 doorFace(LobbyFrame f, int door) {
        BlockPos a = doorPos(f, door, 0, FLOOR + 2);
        BlockPos b = doorPos(f, door, 1, FLOOR + 2);
        int out = f.out(door);
        return new Vec3((a.getX() + b.getX()) / 2.0 + 0.5 + 0.55 * Level4AnomalyPlan.stepX(out),
                FLOOR + 2.0,
                (a.getZ() + b.getZ()) / 2.0 + 0.5 + 0.55 * Level4AnomalyPlan.stepZ(out));
    }

    private static boolean seen(ServerLevel level, LobbyFrame f, int door) {
        return Observation.seenByAnyone(level, doorFace(f, door), SEEN_RANGE);
    }

    private static boolean someoneNear(ServerLevel level, LobbyFrame f, int door) {
        Vec3 face = doorFace(f, door);
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.position().distanceToSqr(face.x, p.getY(), face.z) < NEAR * NEAR) {
                return true;
            }
        }
        return false;
    }

    private static State state(ServerLevel level, LobbyFrame f, int door) {
        BlockPos pos = doorPos(f, door, 0, FLOOR + 1);
        if (!level.isLoaded(pos) || !level.isLoaded(doorPos(f, door, 1, FLOOR + 1))) {
            return State.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return State.OPEN;
        if (state.getBlock() instanceof ElevatorDoorBlock) return State.CLOSED;
        return State.BLANK;
    }

    /** Opens, shuts or walls over elevator {@code door} (both columns, three blocks high). */
    private static void setDoor(ServerLevel level, LobbyFrame f, int door, State state) {
        for (int column = 0; column < 2; column++) {
            BlockPos lower = doorPos(f, door, column, FLOOR + 1);
            if (!level.isLoaded(lower)) {
                continue;
            }
            BlockState low;
            BlockState high;
            BlockState header;
            switch (state) {
                case OPEN -> {
                    low = Blocks.AIR.defaultBlockState();
                    high = low;
                    header = Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.HEADER);
                }
                case CLOSED -> {
                    low = Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.LOWER);
                    high = Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.UPPER);
                    header = Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.HEADER);
                }
                default -> {
                    // The lobby's own wall, as if there had never been a door.
                    low = ModBlocks.OFFICE_WALL_RAIL.get().defaultBlockState();
                    high = ModBlocks.OFFICE_WALL.get().defaultBlockState();
                    header = high;
                }
            }
            level.setBlock(lower, low, Block.UPDATE_ALL);
            level.setBlock(lower.above(), high, Block.UPDATE_ALL);
            level.setBlock(lower.above(2), header, Block.UPDATE_ALL);
        }
    }

    /** Switches the car's ceiling panel on or off. */
    private static void setCarLight(ServerLevel level, LobbyFrame f, int door, LampMode mode) {
        for (int column = 0; column < 2; column++) {
            int u = f.doorU(door) + column;
            int v = f.carLightV(door);
            BlockPos pos = new BlockPos(f.worldX(u, v), FLOOR + 4, f.worldZ(u, v));
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof OfficePanelBlock && state.getValue(OfficePanelBlock.MODE) != mode) {
                level.setBlock(pos, state.setValue(OfficePanelBlock.MODE, mode), Block.UPDATE_ALL);
            }
        }
    }

    /** The chime of a car arriving (heard around a corner, it gives the new spot away). */
    private static void ding(ServerLevel level, LobbyFrame f, int door) {
        Vec3 face = doorFace(f, door);
        level.playSound(null, face.x, face.y, face.z, ModSounds.L4_ELEVATOR_DING.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
    }

    static void forget(UUID id) {
        RIDES.remove(id);
        IN_CAR.remove(id);
        ARRIVED.remove(id);
    }
}
