package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.AxisBlock;
import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.OfficePanelBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.network.DriftPayload;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.DriftRegistry;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Separate realities: now and then, two friends standing a few blocks apart see different
 * versions of the same room. One of them (the "drifted" one) sees a doorway walled up, a door in a
 * wall that has none, or the lights in the room dead; the other sees the real room. When they walk
 * up to each other ("close the gap") it snaps back.
 *
 * <p><b>How it works.</b> The server simply tells the drifted player's game that a few blocks are
 * something else ({@link ClientboundBlockUpdatePacket}, sent to that one player only). Their game
 * draws, lights and collides with those fake blocks like real ones. Nothing is ever written to the
 * world: the server's own blocks never change, so nothing fake can be saved, and after a restart
 * everything is simply real again. To end it, we send the real blocks back.
 *
 * <p><b>Staying in sync with the server.</b> Your game moves you and the server checks each move
 * against ITS blocks ("moved wrongly" puts you back). So we only fake in ways where your game can
 * never let you somewhere the server says is solid:
 * <ul>
 *   <li>an open doorway becomes wall (you just stop in front of it: the server is fine with that);</li>
 *   <li>a wall becomes a closed door, with the door's thin slab flush with the wall's face (you can't
 *       step into it). If the wall is only one block thick, the door's back is a shallow recess on
 *       the other side; should the drifted player walk round to it, the drift ends first;</li>
 *   <li>a lamp becomes its dead twin (you walk through lamps either way).</li>
 * </ul>
 * Never solid to air, and never a block someone is standing in or touching.
 *
 * <p><b>When.</b> Two or more players on Level 0, 1 or 4, 3-12 blocks apart, both outside safe rooms
 * and not hiding, no blackout or alarm: about once per 25 minutes they spend like that, at most once
 * per 10 minutes per pair. Walls and doors change only where the drifted player isn't looking. It
 * ends after 60-120 s, or when they come within 2 blocks of each other, either of them goes 24
 * blocks away, dies, changes level, hides or logs out, or the chunk unloads for the drifted player.
 * At the end the drifted player alone hears a soft low thump, like their ears popping.
 *
 * <p>Operators can force one with {@code /backrooms drift <player> [sealed|door|lights|end]}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class RealityDrift {
    /** The three ways a room can be different. */
    public enum Variant {
        SEALED("a sealed doorway"),
        DOOR("a door in a wall"),
        LIGHTS("the lights out");

        private final String description;

        Variant(String description) {
            this.description = description;
        }
    }

    /** How far apart the two friends must be: close enough to talk, far enough to see different things. */
    private static final double MIN_APART = 3.0;
    private static final double MAX_APART = 12.0;
    /** The changed spot is at most this far from each of them. */
    private static final double SPOT_RANGE = 8.0;
    /** Coming this close to each other snaps it back. */
    private static final double GAP_CLOSED = 2.0;
    /** Either of them wandering this far from the spot ends it. */
    private static final double LEAVE_RANGE = 24.0;
    /** How long a drift lasts at most (ticks): 60-120 s. */
    private static final int MIN_TICKS = 60 * 20;
    private static final int MAX_TICKS = 120 * 20;
    /** On average one drift per this many seconds that a pair spends close together (~25 minutes). */
    private static final int MEAN_SECONDS = 25 * 60;
    /** The same two players never drift again within this time (ticks). */
    private static final long PAIR_COOLDOWN = 10 * 60 * 20;
    /** After the dice say "now", how long we keep trying to find a spot the drifted one isn't looking at. */
    private static final int PENDING_TICKS = 30 * 20;
    /**
     * A wall or doorway only changes where the drifted player isn't looking: at least 75 degrees
     * away from where they look (cos 75 = 0.26). Lights can't hide like that: a room going dark is
     * seen everywhere, just like a tube failing.
     */
    private static final double OFF_SCREEN_DOT = 0.26;
    /** Players on the same storey (Level 1's lower floor sits well below). */
    private static final int SAME_STOREY = 3;
    /** Lights out: the lamps within this many blocks of the spot (sideways; up and down a little). */
    private static final int LIGHTS_RADIUS = 8;
    private static final int LIGHTS_HEIGHT = 6;
    private static final int MAX_FIXTURES = 64;
    /** Never fake a block within this distance of anyone's body. */
    private static final double TOUCH_MARGIN = 0.3;

    /** What a drifted player sees differently, worked out before it starts. */
    private record Spot(Variant variant, Map<BlockPos, BlockState> fakes, List<BlockPos> keepOut, Vec3 focus) {}

    /** One drift in progress. */
    private static final class Drift {
        final UUID drifted;
        /** The friend who still sees the real room (null when an operator forced a drift on someone alone). */
        @Nullable
        final UUID partner;
        final ResourceKey<Level> dimension;
        final Spot spot;
        /** The real block at every faked position when it started (to notice the real one changing). */
        final Map<BlockPos, BlockState> real = new HashMap<>();
        final long endTick;

        Drift(UUID drifted, @Nullable UUID partner, ResourceKey<Level> dimension, Spot spot, long endTick) {
            this.drifted = drifted;
            this.partner = partner;
            this.dimension = dimension;
            this.spot = spot;
            this.endTick = endTick;
        }
    }

    /** Drifts in progress, by the drifted player. */
    private static final Map<UUID, Drift> DRIFTS = new HashMap<>();
    /** Pair (see {@link #pairKey}) -> server tick before which they can't drift again. */
    private static final Map<String, Long> COOLDOWN_UNTIL = new HashMap<>();
    /**
     * A pair the dice picked: until when we keep trying, and which variant. The variant is chosen
     * once, at the roll: otherwise "lights out" (which needn't wait for you to look away) would
     * win almost every time.
     */
    private record Pending(long until, Variant variant) {}

    /** Pair -> the drift the dice already rolled for, still looking for its moment. */
    private static final Map<String, Pending> PENDING = new HashMap<>();

    // ================================================================== ticking

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (!DRIFTS.isEmpty()) {
            for (Drift drift : new ArrayList<>(DRIFTS.values())) {
                tickDrift(server, drift, now);
            }
        }
        if (now % 20 == 0) {
            PENDING.values().removeIf(pending -> pending.until() < now);
            COOLDOWN_UNTIL.values().removeIf(until -> until < now);
            for (BackroomsLevel floor : new BackroomsLevel[] {BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_4}) {
                ServerLevel level = server.getLevel(floor.dimension());
                if (level != null && level.players().size() >= 2 && !LevelEvents.isBlackoutOrAlarm(floor)) {
                    considerPairs(level, floor, now);
                }
            }
        }
    }

    /** Checks every end condition of one running drift. */
    private static void tickDrift(MinecraftServer server, Drift drift, long now) {
        ServerPlayer drifted = server.getPlayerList().getPlayer(drift.drifted);
        if (drifted == null) {
            DRIFTS.remove(drift.drifted); // logged out: their game threw the fake blocks away with the world
            return;
        }
        if (drifted.level().dimension() != drift.dimension) {
            forget(drifted, drift); // another level: the old blocks aren't on their screen any more
            return;
        }
        ServerLevel level = drifted.serverLevel();
        if (now >= drift.endTick || !stillThere(drifted, drift) || touchesKeepOut(drifted, drift)) {
            end(drifted, drift, true);
            return;
        }
        if (drift.partner != null) {
            ServerPlayer partner = server.getPlayerList().getPlayer(drift.partner);
            if (partner == null || partner.level() != level || !stillThere(partner, drift)
                    || partner.distanceTo(drifted) < GAP_CLOSED) {
                end(drifted, drift, true); // gone, or the two closed the gap: snap back
                return;
            }
        }
        // Someone changed a real block we are faking (a door opened, an anomaly moved something):
        // the server has already sent them the real one, so end it cleanly everywhere.
        if (now % 20 == 0) {
            for (Map.Entry<BlockPos, BlockState> entry : drift.real.entrySet()) {
                if (level.getBlockState(entry.getKey()) != entry.getValue()) {
                    end(drifted, drift, true);
                    return;
                }
            }
        }
    }

    /** Still alive, playing, not hiding and near the spot. */
    private static boolean stillThere(ServerPlayer player, Drift drift) {
        return player.isAlive() && !player.isSpectator() && !HidingService.isHidden(player)
                && player.position().distanceTo(drift.spot.focus()) <= LEAVE_RANGE;
    }

    /**
     * True when the drifted player comes close to the back of a fake door in a one-block wall. There
     * the door's thin slab leaves a shallow recess their game would let them step into, but the
     * server knows it's solid wall: we end the drift before that can happen.
     */
    private static boolean touchesKeepOut(ServerPlayer player, Drift drift) {
        if (drift.spot.keepOut().isEmpty()) {
            return false;
        }
        AABB body = player.getBoundingBox().inflate(0.5);
        for (BlockPos pos : drift.spot.keepOut()) {
            if (body.intersects(new AABB(pos))) {
                return true;
            }
        }
        return false;
    }

    /** Once a second per floor: every pair that could drift rolls the dice. */
    private static void considerPairs(ServerLevel level, BackroomsLevel floor, long now) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && !HidingService.isHidden(player)
                    && !SafeRooms.isSafe(player) && !busy(player.getUUID())) {
                candidates.add(player);
            }
        }
        RandomSource random = level.getRandom();
        Set<UUID> startedNow = new HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                ServerPlayer a = candidates.get(i);
                ServerPlayer b = candidates.get(j);
                if (startedNow.contains(a.getUUID()) || startedNow.contains(b.getUUID())) {
                    continue;
                }
                double apart = a.distanceTo(b);
                if (apart < MIN_APART || apart > MAX_APART || Math.abs(a.getBlockY() - b.getBlockY()) > SAME_STOREY) {
                    continue;
                }
                String key = pairKey(a.getUUID(), b.getUUID());
                if (COOLDOWN_UNTIL.getOrDefault(key, 0L) > now) {
                    continue;
                }
                Pending pending = PENDING.get(key);
                if (pending == null) {
                    // The rare part: a 1-in-1500 chance each second they spend like this.
                    if (random.nextInt(MEAN_SECONDS) != 0) {
                        continue;
                    }
                    pending = new Pending(now + PENDING_TICKS, Variant.values()[random.nextInt(Variant.values().length)]);
                    PENDING.put(key, pending);
                }
                // The rolled variant; in the last second, if it never found a spot, any variant that fits.
                Variant variant = pending.until() - now < 20 ? null : pending.variant();
                // Either of them may be the one who drifts; try both, in random order.
                ServerPlayer first = random.nextBoolean() ? a : b;
                ServerPlayer second = first == a ? b : a;
                Spot spot = findSpot(level, floor, first, second, variant, true);
                ServerPlayer drifted = first;
                if (spot == null) {
                    spot = findSpot(level, floor, second, first, variant, true);
                    drifted = second;
                }
                if (spot != null) {
                    PENDING.remove(key);
                    start(level, drifted, drifted == a ? b : a, spot);
                    startedNow.add(a.getUUID());
                    startedNow.add(b.getUUID());
                }
            }
        }
    }

    /** True while this player is drifted, or is the friend of someone drifted. */
    private static boolean busy(UUID player) {
        if (DRIFTS.containsKey(player)) {
            return true;
        }
        for (Drift drift : DRIFTS.values()) {
            if (player.equals(drift.partner)) {
                return true;
            }
        }
        return false;
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    // ================================================================== start / end

    private static void start(ServerLevel level, ServerPlayer drifted, @Nullable ServerPlayer partner, Spot spot) {
        long now = level.getServer().getTickCount();
        int length = MIN_TICKS + level.getRandom().nextInt(MAX_TICKS - MIN_TICKS + 1);
        Drift drift = new Drift(drifted.getUUID(), partner == null ? null : partner.getUUID(), level.dimension(), spot, now + length);
        for (Map.Entry<BlockPos, BlockState> fake : spot.fakes().entrySet()) {
            drift.real.put(fake.getKey(), level.getBlockState(fake.getKey()));
            // To this one player only. Their game recomputes the light around a faked lamp itself.
            drifted.connection.send(new ClientboundBlockUpdatePacket(fake.getKey(), fake.getValue()));
        }
        PacketDistributor.sendToPlayer(drifted, new DriftPayload(List.copyOf(spot.fakes().keySet())));
        DRIFTS.put(drifted.getUUID(), drift);
        // No sound on start: the strangeness is that the room just IS different.
    }

    /**
     * Snaps a drift back: sends the drifted player the real blocks again (whatever the server has
     * there now) and, if {@code thump}, plays the soft pressure thump for them alone.
     */
    private static void end(ServerPlayer drifted, Drift drift, boolean thump) {
        DRIFTS.remove(drift.drifted);
        if (drift.partner != null) {
            COOLDOWN_UNTIL.put(pairKey(drift.drifted, drift.partner), drifted.getServer().getTickCount() + PAIR_COOLDOWN);
        }
        if (drifted.level().dimension() != drift.dimension) {
            forget(drifted, drift);
            return;
        }
        ServerLevel level = drifted.serverLevel();
        for (BlockPos pos : drift.spot.fakes().keySet()) {
            // A position whose chunk their game has already dropped is simply ignored by it.
            drifted.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
        }
        PacketDistributor.sendToPlayer(drifted, new DriftPayload(List.of()));
        if (thump) {
            drifted.connection.send(new ClientboundSoundPacket(DriftRegistry.SNAP_BACK, SoundSource.AMBIENT,
                    drifted.getX(), drifted.getEyeY(), drifted.getZ(), 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f,
                    level.getRandom().nextLong()));
        }
    }

    /**
     * Drops a drift without sending any blocks: used when the drifted player is already in another
     * level, where block updates for the old positions would change the wrong world on their screen.
     */
    private static void forget(ServerPlayer drifted, Drift drift) {
        DRIFTS.remove(drift.drifted);
        if (drift.partner != null) {
            COOLDOWN_UNTIL.put(pairKey(drift.drifted, drift.partner), drifted.getServer().getTickCount() + PAIR_COOLDOWN);
        }
        PacketDistributor.sendToPlayer(drifted, new DriftPayload(List.of()));
    }

    /** Ends every drift this player takes part in (as the drifted one or as the friend). */
    private static void endAllFor(ServerPlayer player, boolean sameLevel) {
        Drift own = DRIFTS.get(player.getUUID());
        if (own != null) {
            if (sameLevel) {
                end(player, own, false);
            } else {
                forget(player, own);
            }
        }
        for (Drift drift : new ArrayList<>(DRIFTS.values())) {
            if (player.getUUID().equals(drift.partner)) {
                ServerPlayer drifted = player.getServer().getPlayerList().getPlayer(drift.drifted);
                if (drifted != null) {
                    end(drifted, drift, true); // the friend is gone: the room snaps back
                } else {
                    DRIFTS.remove(drift.drifted);
                }
            }
        }
    }

    /**
     * From the packet mixin, right after vanilla re-sent the real block at {@code pos} (and its
     * neighbour) to a player who clicked it: send our fakes there again.
     */
    public static void reassert(ServerPlayer player, BlockPos pos) {
        Drift drift = DRIFTS.get(player.getUUID());
        if (drift == null || player.level().dimension() != drift.dimension) {
            return;
        }
        resend(player, drift, pos);
        for (Direction direction : Direction.values()) {
            resend(player, drift, pos.relative(direction));
        }
    }

    private static void resend(ServerPlayer player, Drift drift, BlockPos pos) {
        BlockState fake = drift.spot.fakes().get(pos);
        if (fake != null) {
            player.connection.send(new ClientboundBlockUpdatePacket(pos, fake));
        }
    }

    /** True if this player sees something fake at {@code pos} right now. */
    private static boolean isFakeFor(Object entity, BlockPos pos) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        Drift drift = DRIFTS.get(player.getUUID());
        return drift != null && drift.spot.fakes().containsKey(pos);
    }

    // ================================================================== events

    /**
     * Clicking a fake block does nothing on the server either: otherwise a right-click on a fake
     * wall (really air) could put a chalk mark in thin air, and a creative player's punch on a fake
     * door could break the real wall. (The client ignores the click too, see client/DriftClient.)
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isFakeFor(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isFakeFor(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endAllFor(player, true); // still in the level: give their screen the real blocks back
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Drift own = DRIFTS.remove(player.getUUID());
            if (own != null && own.partner != null) {
                COOLDOWN_UNTIL.put(pairKey(own.drifted, own.partner), player.getServer().getTickCount() + PAIR_COOLDOWN);
            }
            endAllFor(player, false);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endAllFor(player, false);
        }
    }

    /** A chunk with fake blocks unloads for the drifted player: when it comes back it's real again. */
    @SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ServerPlayer player = event.getPlayer();
        Drift drift = DRIFTS.get(player.getUUID());
        if (drift == null || event.getLevel().dimension() != drift.dimension) {
            return;
        }
        for (BlockPos pos : drift.spot.fakes().keySet()) {
            if ((pos.getX() >> 4) == event.getPos().x && (pos.getZ() >> 4) == event.getPos().z) {
                if (player.level() == event.getLevel()) {
                    end(player, drift, false);
                } else {
                    forget(player, drift); // on their way to another level
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Nothing to undo: no fake block was ever written to the world.
        DRIFTS.clear();
        COOLDOWN_UNTIL.clear();
        PENDING.clear();
    }

    // ================================================================== operator command

    /** What {@code /backrooms drift} reports back. */
    public record CommandResult(boolean success, String message) {}

    /**
     * {@code /backrooms drift <player> [variant]}: starts a drift for {@code player} right now, with
     * the nearest other player as the friend, or alone if nobody is near (handy for looking at the
     * visuals). {@code variant} null = whichever fits. No dice, cooldowns or "not while looking".
     */
    public static CommandResult force(ServerPlayer player, @Nullable Variant variant) {
        ServerLevel level = player.serverLevel();
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor != BackroomsLevel.LEVEL_0 && floor != BackroomsLevel.LEVEL_1 && floor != BackroomsLevel.LEVEL_4) {
            return new CommandResult(false, "Separate realities only happen on Level 0, Level 1 and Level 4.");
        }
        if (player.isSpectator()) {
            return new CommandResult(false, player.getGameProfile().getName() + " is a spectator.");
        }
        Drift old = DRIFTS.get(player.getUUID());
        if (old != null) {
            end(player, old, false);
        }
        ServerPlayer partner = null;
        double best = 2 * SPOT_RANGE; // farther apart than this, no spot can be within reach of both
        for (ServerPlayer other : level.players()) {
            double distance = other.distanceTo(player);
            if (other != player && !other.isSpectator() && other.isAlive() && distance <= best) {
                partner = other;
                best = distance;
            }
        }
        Spot spot = findSpot(level, floor, player, partner, variant, false);
        if (spot == null && partner != null) {
            partner = null; // no spot both can see: try one just for them
            spot = findSpot(level, floor, player, null, variant, false);
        }
        if (spot == null) {
            return new CommandResult(false, "No spot for " + (variant == null ? "any drift" : variant.description)
                    + " near " + player.getGameProfile().getName() + ". Try another room.");
        }
        start(level, player, partner, spot);
        BlockPos at = BlockPos.containing(spot.focus());
        return new CommandResult(true, player.getGameProfile().getName() + " now sees " + spot.variant().description
                + " at " + at.getX() + " " + at.getY() + " " + at.getZ() + " (" + spot.fakes().size() + " blocks)"
                + (partner == null ? ", alone." : "; " + partner.getGameProfile().getName() + " sees the real room."));
    }

    /** {@code /backrooms drift <player> end}: snap it back now (with the thump). */
    public static CommandResult endByCommand(ServerPlayer player) {
        Drift drift = DRIFTS.get(player.getUUID());
        if (drift == null) {
            return new CommandResult(false, player.getGameProfile().getName() + " isn't drifting.");
        }
        end(player, drift, true);
        return new CommandResult(true, "Reality snapped back for " + player.getGameProfile().getName() + ".");
    }

    // ================================================================== finding a spot

    /**
     * Picks the variant and the spot: {@code only} if given, otherwise the three in random order
     * until one fits. {@code natural}: the dice started it, so walls and doorways must change out of
     * the drifted player's sight. Null when nothing fits here.
     */
    @Nullable
    private static Spot findSpot(ServerLevel level, BackroomsLevel floor, ServerPlayer drifted, @Nullable ServerPlayer partner,
                                 @Nullable Variant only, boolean natural) {
        List<ServerPlayer> viewers = partner == null ? List.of(drifted) : List.of(drifted, partner);
        // Ideally between the two; alone, a few steps in front of you.
        Vec3 center = partner == null
                ? drifted.position().add(drifted.getLookAngle().multiply(1, 0, 1).normalize().scale(3))
                : drifted.position().add(partner.position()).scale(0.5);
        List<Variant> order = new ArrayList<>(List.of(Variant.values()));
        if (only != null) {
            order = List.of(only);
        } else {
            Collections.shuffle(order, new java.util.Random(level.getRandom().nextLong()));
        }
        for (Variant variant : order) {
            Spot spot = switch (variant) {
                case SEALED -> findSealedDoorway(level, drifted, viewers, center, natural);
                case DOOR -> findDoorInWall(level, floor, drifted, viewers, center, natural);
                case LIGHTS -> findLightsOut(level, viewers);
            };
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** The lowest and highest feet height to look for doorways and walls at (a step up or down). */
    private static int minFeetY(List<ServerPlayer> viewers) {
        int y = Integer.MAX_VALUE;
        for (ServerPlayer viewer : viewers) {
            y = Math.min(y, viewer.getBlockY());
        }
        return y - 1;
    }

    private static int maxFeetY(List<ServerPlayer> viewers) {
        int y = Integer.MIN_VALUE;
        for (ServerPlayer viewer : viewers) {
            y = Math.max(y, viewer.getBlockY());
        }
        return y + 1;
    }

    /**
     * a) A real doorway or opening, 1-4 wide, in a wall: walled up with copies of the wall blocks
     * right beside it, height by height, so wallpaper and baseboard line up.
     *
     * <p>We read the real blocks: an opening is a row of 1-4 open cells (two high, open in front and
     * behind) with solid wall at both ends, where the wall at each end is a wall end (open in front
     * of or behind it) rather than the side wall of a corridor.
     */
    @Nullable
    private static Spot findSealedDoorway(ServerLevel level, ServerPlayer drifted, List<ServerPlayer> viewers, Vec3 center, boolean natural) {
        Spot best = null;
        double bestScore = Double.MAX_VALUE;
        int cx = (int) Math.floor(center.x);
        int cz = (int) Math.floor(center.z);
        int range = (int) SPOT_RANGE;
        for (int y = minFeetY(viewers); y <= maxFeetY(viewers); y++) {
            for (int x = cx - range; x <= cx + range; x++) {
                for (int z = cz - range; z <= cz + range; z++) {
                    for (Direction along : new Direction[] {Direction.EAST, Direction.SOUTH}) {
                        Spot spot = sealedAt(level, new BlockPos(x, y, z), along);
                        if (spot == null) {
                            continue;
                        }
                        double score = spot.focus().distanceTo(center);
                        if (score < bestScore && fitsViewers(level, drifted, viewers, spot.focus(), natural)) {
                            best = spot;
                            bestScore = score;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The sealed doorway whose opening starts at {@code start} and runs {@code along}, or null. */
    @Nullable
    private static Spot sealedAt(ServerLevel level, BlockPos start, Direction along) {
        BlockPos capBefore = start.relative(along.getOpposite());
        if (!wall(level, capBefore) || !wall(level, capBefore.above()) || !passable(level, start) || !passable(level, start.above())) {
            return null;
        }
        int width = 0;
        while (width < 5 && passable(level, start.relative(along, width)) && passable(level, start.relative(along, width).above())) {
            width++;
        }
        BlockPos capAfter = start.relative(along, width);
        if (width > 4 || !wall(level, capAfter) || !wall(level, capAfter.above())) {
            return null;
        }
        Direction across = along.getClockWise();
        // Both ends must be wall ENDS (open in front or behind), not the side walls of a corridor.
        for (BlockPos cap : new BlockPos[] {capBefore, capAfter}) {
            if (!passable(level, cap.relative(across)) && !passable(level, cap.relative(across.getOpposite()))) {
                return null;
            }
        }
        Map<BlockPos, BlockState> fakes = new LinkedHashMap<>();
        for (int i = 0; i < width; i++) {
            BlockPos cell = start.relative(along, i);
            // You can walk through it: a floor, and open on both sides.
            if (!level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)
                    || !passable(level, cell.relative(across)) || !passable(level, cell.relative(across).above())
                    || !passable(level, cell.relative(across.getOpposite())) || !passable(level, cell.relative(across.getOpposite()).above())) {
                return null;
            }
            // Fill it up to the lintel (2-5 high; anything taller isn't a doorway).
            int height = 0;
            while (height <= 5 && fakeable(level, cell.above(height))) {
                height++;
            }
            if (height < 2 || height > 5 || passable(level, cell.above(height))) {
                return null;
            }
            for (int dy = 0; dy < height; dy++) {
                // The wall beside the doorway at this height: baseboard low down, wallpaper above.
                BlockPos left = capBefore.above(dy);
                BlockPos right = capAfter.above(dy);
                BlockPos source = wall(level, left) ? left : wall(level, right) ? right : null;
                if (source == null) {
                    return null;
                }
                BlockPos pos = cell.above(dy);
                if (touched(level, pos) || SafeRooms.isSafe(level, pos)) {
                    return null;
                }
                fakes.put(pos, level.getBlockState(source));
            }
        }
        Vec3 focus = Vec3.atCenterOf(start.relative(along, width / 2).above());
        return new Spot(Variant.SEALED, fakes, List.of(), focus);
    }

    /**
     * b) A closed door in a real flat wall, using this level's own door: Level 0's lobby door,
     * Level 1's grey fire door, Level 4's office door. Its thin slab sits flush with the wall's
     * face on the room side, so walking into it feels exactly like walking into the wall.
     */
    @Nullable
    private static Spot findDoorInWall(ServerLevel level, BackroomsLevel floor, ServerPlayer drifted, List<ServerPlayer> viewers,
                                       Vec3 center, boolean natural) {
        Block door = switch (floor) {
            case LEVEL_1 -> ModBlocks.FIRE_DOOR.get();
            case LEVEL_4 -> ModBlocks.OFFICE_DOOR.get();
            default -> ModBlocks.LOBBY_DOOR.get();
        };
        Spot best = null;
        double bestScore = Double.MAX_VALUE;
        int cx = (int) Math.floor(center.x);
        int cz = (int) Math.floor(center.z);
        int range = (int) SPOT_RANGE;
        for (int y = minFeetY(viewers); y <= maxFeetY(viewers); y++) {
            for (int x = cx - range; x <= cx + range; x++) {
                for (int z = cz - range; z <= cz + range; z++) {
                    BlockPos wall = new BlockPos(x, y, z);
                    if (!wall(level, wall) || !wall(level, wall.above()) || passable(level, wall.above(2))) {
                        continue;
                    }
                    for (Direction room : Direction.Plane.HORIZONTAL) {
                        Spot spot = doorAt(level, door, wall, room);
                        if (spot == null) {
                            continue;
                        }
                        double score = spot.focus().distanceTo(center);
                        if (score < bestScore && fitsViewers(level, drifted, viewers, spot.focus(), natural)) {
                            best = spot;
                            bestScore = score;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** A door in the wall block {@code wall} facing the room on its {@code room} side, or null. */
    @Nullable
    private static Spot doorAt(ServerLevel level, Block door, BlockPos wall, Direction room) {
        BlockPos front = wall.relative(room);
        if (!passable(level, front) || !passable(level, front.above())
                || !level.getBlockState(front.below()).isFaceSturdy(level, front.below(), Direction.UP)) {
            return null;
        }
        // A flat stretch of wall: wall on both sides of the door, with the room in front of them too
        // (not a corner, a pillar edge or the side of a doorway).
        Direction side = room.getClockWise();
        for (Direction s : new Direction[] {side, side.getOpposite()}) {
            BlockPos next = wall.relative(s);
            if (!wall(level, next) || !wall(level, next.above()) || !passable(level, next.relative(room))
                    || !passable(level, next.relative(room).above())) {
                return null;
            }
        }
        if (touched(level, wall) || touched(level, wall.above()) || SafeRooms.isSafe(level, wall)) {
            return null;
        }
        // A closed door's slab sits on the side opposite its FACING, so facing away from the room
        // puts the slab flush with the room-side face of the wall.
        BlockState state = door.defaultBlockState()
                .setValue(DoorBlock.FACING, room.getOpposite())
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        Map<BlockPos, BlockState> fakes = new LinkedHashMap<>();
        fakes.put(wall, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        fakes.put(wall.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // A one-block wall: the door's back is a shallow recess on the far side. Keep the drifted
        // player out of the cells in front of it (the drift ends if they get there).
        List<BlockPos> keepOut = new ArrayList<>();
        BlockPos back = wall.relative(room.getOpposite());
        if (passable(level, back) || passable(level, back.above())) {
            keepOut.add(back);
            keepOut.add(back.above());
        }
        // What you look at: the middle of the door's face, just in front of the wall.
        Vec3 focus = Vec3.atCenterOf(wall.above()).add(room.getStepX() * 0.55, 0, room.getStepZ() * 0.55);
        return new Spot(Variant.DOOR, fakes, keepOut, focus);
    }

    /**
     * c) Every lamp within about 8 blocks of the spot between them shows its dead twin, so the
     * drifted player's room is dark while their friend's is lit. Needs at least two lamps.
     */
    @Nullable
    private static Spot findLightsOut(ServerLevel level, List<ServerPlayer> viewers) {
        // The spot: halfway between their eyes, which both of them must see (so: each other).
        Vec3 focus = Vec3.ZERO;
        for (ServerPlayer viewer : viewers) {
            focus = focus.add(viewer.getEyePosition());
        }
        focus = focus.scale(1.0 / viewers.size());
        for (ServerPlayer viewer : viewers) {
            if (!sees(level, viewer, focus)) {
                return null;
            }
        }
        BlockPos anchor = BlockPos.containing(focus);
        Map<BlockPos, BlockState> fakes = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -LIGHTS_RADIUS; dx <= LIGHTS_RADIUS; dx++) {
            for (int dz = -LIGHTS_RADIUS; dz <= LIGHTS_RADIUS; dz++) {
                if (dx * dx + dz * dz > LIGHTS_RADIUS * LIGHTS_RADIUS) {
                    continue;
                }
                for (int dy = -LIGHTS_HEIGHT; dy <= LIGHTS_HEIGHT && fakes.size() < MAX_FIXTURES; dy++) {
                    pos.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    BlockState dead = deadTwin(level.getBlockState(pos));
                    if (dead != null && !touched(level, pos) && !SafeRooms.isSafe(level, pos)) {
                        fakes.put(pos.immutable(), dead);
                    }
                }
            }
        }
        return fakes.size() < 2 ? null : new Spot(Variant.LIGHTS, fakes, List.of(), focus);
    }

    /**
     * The dead version of a lit lamp, or null if this isn't one. Level 0: the troffer and the film's
     * light strip and their _off blocks; Level 1: the hanging fixture, the wall tube and the
     * corridor strip and their dead twins in {@link DriftRegistry}; Level 4: the office panel in
     * its "dead" mode. Exit signs, alarm lamps and safe-room lamps are battery-backed and stay on.
     */
    @Nullable
    private static BlockState deadTwin(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.FLUORESCENT_LIGHT.get()) {
            return ModBlocks.FLUORESCENT_LIGHT_OFF.get().defaultBlockState();
        }
        if (block == Level0Blocks.TROFFER_STRIP.get()) {
            return Level0Blocks.TROFFER_STRIP_OFF.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, state.getValue(AxisShapeBlock.AXIS));
        }
        if (block == ModBlocks.L1_HANGING_LIGHT.get()) {
            return DriftRegistry.L1_HANGING_LIGHT_OFF.get().defaultBlockState().setValue(AxisBlock.AXIS, state.getValue(AxisBlock.AXIS));
        }
        if (block == ModBlocks.WAREHOUSE_LIGHT.get()) {
            return DriftRegistry.WAREHOUSE_LIGHT_OFF.get().defaultBlockState().setValue(WallLampBlock.FACING, state.getValue(WallLampBlock.FACING));
        }
        if (block == ModBlocks.CEILING_TUBE.get()) {
            return DriftRegistry.CEILING_TUBE_OFF.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, state.getValue(CeilingTubeBlock.AXIS));
        }
        if (block == ModBlocks.OFFICE_PANEL.get() && state.getValue(OfficePanelBlock.MODE) != LampMode.DEAD) {
            return state.setValue(OfficePanelBlock.MODE, LampMode.DEAD);
        }
        return null;
    }

    // ================================================================== block and sight tests

    /**
     * Within reach of every viewer and seen by all of them (nothing solid in between); for a
     * natural drift also out of the drifted player's sight, so the change itself is never seen.
     */
    private static boolean fitsViewers(ServerLevel level, ServerPlayer drifted, List<ServerPlayer> viewers, Vec3 point,
                                       boolean natural) {
        for (ServerPlayer viewer : viewers) {
            if (viewer.getEyePosition().distanceTo(point) > SPOT_RANGE || !sees(level, viewer, point)) {
                return false;
            }
        }
        if (natural) {
            Vec3 toPoint = point.subtract(drifted.getEyePosition()).normalize();
            return drifted.getLookAngle().dot(toPoint) < OFF_SCREEN_DOT;
        }
        return true;
    }

    /** True if nothing solid stands between the viewer's eyes and {@code point}. */
    private static boolean sees(ServerLevel level, ServerPlayer viewer, Vec3 point) {
        BlockHitResult hit = level.clip(new ClipContext(viewer.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 0.25;
    }

    /** Nothing to bump into: air, a decal, a hanging lamp. */
    private static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Open space we may wall up: passable, and not a lamp, water or a block with stored contents. */
    private static boolean fakeable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getLightEmission(level, pos) == 0
                && !state.hasBlockEntity() && state.getFluidState().isEmpty();
    }

    /** A plain full wall block: solid all through, opaque, no lamp, no door, no stored contents. */
    private static boolean wall(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos) && state.canOcclude() && !state.hasBlockEntity()
                && state.getLightEmission(level, pos) == 0 && !(state.getBlock() instanceof DoorBlock);
    }

    /** True if any player stands in or touches this block (faking it would trap or shove them). */
    private static boolean touched(ServerLevel level, BlockPos pos) {
        AABB cell = new AABB(pos).inflate(TOUCH_MARGIN);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.getBoundingBox().intersects(cell)) {
                return true;
            }
        }
        return false;
    }

    private RealityDrift() {}
}
