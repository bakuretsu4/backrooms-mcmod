package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.RealWorldPayload;
import com.rickr.backrooms.registry.ModItems;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ways out of reality and into the Backrooms (all of them end in {@link NoclipIntro}). Besides
 * the old one (suffocating inside a block, {@code ServerEvents}):
 * <ul>
 *   <li><b>Noclipping through a wall:</b> keep walking into a wall somewhere dark and enclosed and
 *       now and then it simply isn't there. Your client tells us while you push
 *       ({@code WallPushPayload}); after about 2 s of pushing, each half second has a 1 in 24
 *       chance (so usually somewhere around a quarter of a minute).</li>
 *   <li><b>Too long alone in the dark:</b> dark, enclosed, nobody within 24 blocks. Reality wears
 *       thin over about 3 minutes: a fluorescent hum fades in, the picture yellows a little, and
 *       then you're through. Back in the light (or company) it heals again over ~10 s.</li>
 *   <li><b>Filming it:</b> the same, with a camcorder recording in your hand (right-click it): the
 *       camera finds the seams four times faster (~45 s) and the tape breaks up as it does.</li>
 *   <li><b>A bad night:</b> one night's sleep in eight, you don't wake up in your bed.</li>
 * </ul>
 * "Dark" is light level 3 or less where your eyes are; "enclosed" is no open sky above you.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class RealWorldEntries {
    private static final int MAX_DARK_LIGHT = 3;
    private static final double ALONE_RANGE = 24.0;
    private static final float ALONE_TICKS = 180 * 20;
    private static final float FILMING_TICKS = 45 * 20;
    private static final float HEAL_TICKS = 10 * 20;
    /** Wall pushes arrive about every 10 ticks; a gap longer than this ends the push. */
    private static final int PUSH_GAP_TICKS = 16;
    private static final int PUSHES_BEFORE_CHANCE = 4;
    private static final int PUSH_ONE_IN = 24;
    private static final int SLEEP_ONE_IN = 8;
    /** The client is told the thinness again once it has changed this much. */
    private static final float SEND_STEP = 0.02f;

    /** Per player: recording or not, how thin reality is around them, and their current wall push. */
    private static final class State {
        boolean recording;
        float thin;
        float sentThin = -1.0f;
        boolean sentRecording;
        int pushStreak;
        int lastPushTick = -1000;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        State s = STATES.get(player.getUUID());
        boolean real = !Travel.inBackrooms(player.level()) && !player.isSpectator();
        if (!real) {
            if (s != null && (s.recording || s.thin > 0.0f)) {
                s.recording = false;
                s.thin = 0.0f;
                sync(player, s);
            }
            return;
        }
        if (s == null) {
            if (!darkAndEnclosed(player)) {
                return; // nothing going on: don't even keep a record
            }
            s = state(player);
        }
        if (s.recording && !holdingCamcorder(player)) {
            s.recording = false; // put the camera down: the recording stops
        }
        if (!NoclipIntro.isPending(player)) {
            if (darkAndEnclosed(player) && alone(player)) {
                s.thin += 1.0f / (s.recording ? FILMING_TICKS : ALONE_TICKS);
            } else {
                s.thin = Math.max(0.0f, s.thin - 1.0f / HEAL_TICKS);
            }
            if (s.thin >= 1.0f) {
                s.thin = 0.0f;
                s.recording = false;
                NoclipIntro.start(player);
            }
        }
        sync(player, s);
    }

    /** Right-click with a camcorder: start or stop recording. Returns whether it's recording now. */
    public static boolean toggleRecording(ServerPlayer player) {
        State s = state(player);
        s.recording = !s.recording;
        sync(player, s);
        return s.recording;
    }

    /** From the client: still walking into a wall. */
    public static void onWallPush(ServerPlayer player) {
        if (Travel.inBackrooms(player.level()) || NoclipIntro.isPending(player) || !darkAndEnclosed(player)
                || !wallInFront(player)) {
            return;
        }
        State s = state(player);
        s.pushStreak = player.tickCount - s.lastPushTick <= PUSH_GAP_TICKS ? s.pushStreak + 1 : 1;
        s.lastPushTick = player.tickCount;
        if (s.pushStreak > PUSHES_BEFORE_CHANCE && player.getRandom().nextInt(PUSH_ONE_IN) == 0) {
            s.pushStreak = 0;
            NoclipIntro.start(player);
        }
    }

    /** A full night's sleep (not getting up in the middle of it): sometimes you never wake up at home. */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.wakeImmediately() && !event.updateLevel()
                && !Travel.inBackrooms(player.level()) && player.getRandom().nextInt(SLEEP_ONE_IN) == 0) {
            NoclipIntro.start(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private static void sync(ServerPlayer player, State s) {
        if (s.recording != s.sentRecording || Math.abs(s.thin - s.sentThin) >= SEND_STEP || (s.thin == 0.0f && s.sentThin != 0.0f)) {
            s.sentRecording = s.recording;
            s.sentThin = s.thin;
            PacketDistributor.sendToPlayer(player, new RealWorldPayload(s.recording, s.thin));
        }
    }

    private static boolean darkAndEnclosed(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        return level.getMaxLocalRawBrightness(eye) <= MAX_DARK_LIGHT && !level.canSeeSky(eye);
    }

    private static boolean alone(ServerPlayer player) {
        return player.serverLevel().players().stream().noneMatch(other -> other != player && !other.isSpectator()
                && other.distanceToSqr(player) < ALONE_RANGE * ALONE_RANGE);
    }

    private static boolean holdingCamcorder(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.CAMCORDER.get()) || player.getOffhandItem().is(ModItems.CAMCORDER.get());
    }

    /** A solid block right in front of you, at your feet or your eyes. */
    private static boolean wallInFront(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition().relative(player.getDirection());
        BlockPos head = feet.above();
        return !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    private RealWorldEntries() {}
}
