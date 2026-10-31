package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The looping stairwells in Level 490's spire (P.T.-like, {@link Level490Spire#LOOPS}).
 *
 * <p>Each looping stretch is three identical turns of the stair. Climb into the third and you are
 * quietly moved down one turn ({@link Teleports#shift}: same view, same step, nothing jumps), so
 * you climb the same turn again, and again. Every time round something is different, and it gets
 * worse: a lantern has gone out, an alcove is bricked up, someone is counting on the wall, shackles
 * lie on the steps, footsteps above you, whispers, "GO BACK DOWN". The way out is to do that: walk
 * back down to where you came in, and the loop breaks (everything is back as it was, a door creaks
 * far above); climb again and the stair goes on.
 *
 * <p>The changes are only shown to the one player going round (block updates sent to them alone),
 * the world itself never changes, so other players and the saved world are untouched.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Loops {
    /** Further than this from the spire's middle you are not in its stairwell. */
    private static final double SHAFT = 5.0;

    /** One player's progress through one looping stretch. */
    private static final class Lap {
        int lap;
        boolean armed;
        boolean released;
        double lastY = Double.NaN;
    }

    private static final Map<UUID, Lap[]> LAPS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Lap[] laps = LAPS.get(player.getUUID());
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490 || player.isSpectator()) {
            if (laps != null) {
                LAPS.remove(player.getUUID());
            }
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        Level490Spire spire = Level490Layout.spireAt(seed, player.getBlockX(), player.getBlockZ());
        double r = Math.hypot(player.getX() - spire.centreX() - 0.5, player.getZ() - spire.centreZ() - 0.5);
        if (r > 40) {
            if (laps != null) {
                // Left the spire (fell, or climbed out): whatever they were shown goes back.
                for (int i = 0; i < laps.length; i++) {
                    if (laps[i].lap > 0 && !laps[i].released) {
                        restore(player, spire, i);
                    }
                }
                LAPS.remove(player.getUUID());
            }
            return;
        }
        if (laps == null) {
            laps = new Lap[Level490Spire.LOOPS.length];
            for (int i = 0; i < laps.length; i++) {
                laps[i] = new Lap();
            }
            LAPS.put(player.getUUID(), laps);
        }
        if (r > SHAFT || Travel.isMoving(player)) {
            for (Lap lap : laps) {
                lap.lastY = Double.NaN;
            }
            return;
        }
        double y = player.getY();
        for (int i = 0; i < laps.length; i++) {
            Level490Spire.Loop loop = Level490Spire.LOOPS[i];
            Lap st = laps[i];
            int period = Level490Spire.RISE;
            if (y > loop.bottom() + 3 * period + 12) {
                // Out at the top: done with this one (until you come back down into it).
                if (st.lap > 0 && !st.released) {
                    restore(player, spire, i);
                }
                laps[i] = new Lap();
                continue;
            }
            if (y >= loop.bottom() - 12 && y < loop.bottom() + period) {
                st.armed = true; // came in from below
            }
            if (st.armed && !st.released && st.lastY < loop.trigger() && y >= loop.trigger()) {
                Teleports.shift(player, 0, -period, 0);
                st.lap++;
                st.lastY = y - period;
                show(player, spire, i, st.lap);
                cue(player, spire, st.lap);
                continue;
            }
            if (st.lap > 0 && !st.released && y < loop.release()) {
                st.released = true;
                restore(player, spire, i);
                play(player, ModSounds.DISTANT_CREAK, spire, y + 24, 1.0f, 0.8f);
            }
            st.lastY = y;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAPS.remove(event.getEntity().getUUID());
    }

    /** Sends the player how loop i looks on this lap (and how it really is wherever this lap changes nothing). */
    private static void show(ServerPlayer player, Level490Spire spire, int i, int lap) {
        Map<BlockPos, BlockState> view = spire.loopView(i, lap);
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : spire.loopCells(i)) {
            BlockState state = view.get(pos);
            player.connection.send(new ClientboundBlockUpdatePacket(pos, state != null ? state : level.getBlockState(pos)));
        }
    }

    private static void restore(ServerPlayer player, Level490Spire spire, int i) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : spire.loopCells(i)) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        }
    }

    /** What you hear each time round: something above you in the stairwell, then closer. */
    private static void cue(ServerPlayer player, Level490Spire spire, int lap) {
        double y = player.getY();
        switch (lap) {
            case 1 -> play(player, ModSounds.DISTANT_KNOCK, spire, y + 9, 0.7f, 0.8f);
            case 2 -> play(player, ModSounds.L4_STAIRWELL_ECHO, spire, y + 8, 0.9f, 0.9f);
            case 3 -> play(player, ModSounds.SANITY_WHISPER, spire, y + 1, 0.6f, 1.0f);
            case 4 -> play(player, Level39Mobs.CLUMP_DRAG, spire, y + 7, 0.8f, 0.7f);
            case 5 -> play(player, ModSounds.SANITY_KNOCK, spire, y - 3, 0.9f, 0.9f);
            default -> {
                play(player, ModSounds.SANITY_HEARTBEAT, spire, y + 1, 0.8f, 1.0f);
                play(player, ModSounds.DISTANT_L0_WALL_SCRATCH, spire, y + 6, 0.8f, 0.8f);
            }
        }
    }

    private static void play(ServerPlayer player, Holder<SoundEvent> sound, Level490Spire spire, double y, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.AMBIENT,
                spire.centreX() + 0.5, y, spire.centreZ() + 0.5, volume, pitch, player.getRandom().nextLong()));
    }

    private Level490Loops() {}
}
