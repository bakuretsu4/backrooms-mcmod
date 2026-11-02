package com.rickr.backrooms.world.anomaly.level4;

import com.rickr.backrooms.block.ElevatorDoorBlock;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level4AnomalyBlocks;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.LobbyFrame;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The elevator exit: canon says Level 4's exits are office stairways "or occasional elevators"
 * (https://backrooms-wiki.wikidot.com/level-4). In some elevator lobbies one elevator stands open and
 * lit under a green EXIT sign (see Level4AnomalyPlan#ELEVATOR_EXIT for where). Stand in its car for a
 * moment and the doors close; in the dark you feel it go down, and you arrive where the stairwells
 * lead (the next level, through {@link Travel#useExit}). It is an ordinary exit, not a trap.
 *
 * <p>Nobody is ever shut in: once the car is empty its doors open again (checked once a second), so
 * the next person finds it open, and a player standing in a closed car who isn't riding it (logged
 * out mid-ride, server restart) gets the doors opened for them.
 */
final class ElevatorExit implements AnomalyBehavior {
    /** Standing this long in the car takes you down (so stepping in to look is safe). */
    private static final int STEP_IN_TICKS = 30;
    /** The ride in the dark before you arrive. */
    private static final int RIDE_TICKS = 70;

    private static final int FLOOR = LevelLayout.FLOOR_Y;

    private static final Map<UUID, Integer> IN_CAR = new HashMap<>();
    private static final Set<UUID> RIDING = new HashSet<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        LobbyFrame f = Level4AnomalyPlan.lobbyFrame(site);
        int door = Level4AnomalyPlan.exitElevatorDoor(site);
        UUID id = player.getUUID();
        if (!inCar(f, door, player)) {
            IN_CAR.remove(id);
            return;
        }
        if (RIDING.contains(id)) {
            return;
        }
        int t = IN_CAR.merge(id, 1, Integer::sum);
        if (t < STEP_IN_TICKS) {
            return;
        }
        IN_CAR.remove(id);
        // The doors close on everyone in the car, and down it goes.
        ServerLevel level = player.serverLevel();
        setDoors(level, f, door, false);
        BlockPos face = doorPos(f, door, 0, FLOOR + 2);
        level.playSound(null, face, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);
        for (ServerPlayer rider : level.players()) {
            if (rider.isSpectator() || !inCar(f, door, rider) || RIDING.contains(rider.getUUID())) {
                continue;
            }
            RIDING.add(rider.getUUID());
            rider.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, RIDE_TICKS + 20, 0, false, false, false));
            rider.playNotifySound(ModSounds.L4_ELEVATOR_RIDE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            Level4Anomalies.later(rider, RIDE_TICKS, p -> {
                RIDING.remove(p.getUUID());
                p.playNotifySound(ModSounds.L4_ELEVATOR_DING.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
                Travel.useExit(p, BackroomsLevel.LEVEL_4);
            });
        }
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        IN_CAR.remove(player.getUUID());
    }

    /** Once nobody is riding from it, the car opens again for the next one. */
    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        LobbyFrame f = Level4AnomalyPlan.lobbyFrame(site);
        int door = Level4AnomalyPlan.exitElevatorDoor(site);
        BlockPos lower = doorPos(f, door, 0, FLOOR + 1);
        if (!level.isLoaded(lower) || !(level.getBlockState(lower).getBlock() instanceof ElevatorDoorBlock)) {
            return; // unloaded, or already open
        }
        for (ServerPlayer p : level.players()) {
            if (RIDING.contains(p.getUUID()) && inCar(f, door, p)) {
                return; // still on the way down
            }
        }
        setDoors(level, f, door, true);
        level.playSound(null, lower.above(), ModSounds.L4_ELEVATOR_DING.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
    }

    private static boolean inCar(LobbyFrame f, int door, ServerPlayer p) {
        if (p.getY() < FLOOR + 0.5 || p.getY() > FLOOR + 3.5) {
            return false;
        }
        int x = p.getBlockX();
        int z = p.getBlockZ();
        return f.carAt(f.u(x, z), f.v(x, z)) == door;
    }

    private static BlockPos doorPos(LobbyFrame f, int door, int column, int y) {
        int u = f.doorU(door) + column;
        int v = f.doorV(door);
        return new BlockPos(f.worldX(u, v), y, f.worldZ(u, v));
    }

    /** Opens (air under the header) or shuts (both door halves) the exit elevator's two columns. */
    private static void setDoors(ServerLevel level, LobbyFrame f, int door, boolean open) {
        for (int column = 0; column < 2; column++) {
            BlockPos lower = doorPos(f, door, column, FLOOR + 1);
            if (!level.isLoaded(lower)) {
                continue;
            }
            BlockState low = open ? Blocks.AIR.defaultBlockState()
                    : Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.LOWER);
            BlockState high = open ? low : Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.UPPER);
            level.setBlock(lower, low, Block.UPDATE_ALL);
            level.setBlock(lower.above(), high, Block.UPDATE_ALL);
            level.setBlock(lower.above(2), Level4AnomalyBlocks.elevatorDoor(f, door, column, ElevatorDoorBlock.Part.HEADER), Block.UPDATE_ALL);
        }
    }

    static void forget(UUID id) {
        IN_CAR.remove(id);
        RIDING.remove(id);
    }
}
