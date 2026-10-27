package com.rickr.backrooms.player;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.PhantomFriend;
import com.rickr.backrooms.network.HallucinationPayload;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Poolrooms hallucination, server side. Every tick, for every player:
 * <ol>
 *   <li>the wet meter goes up in the water and down out of it (reset outside the Poolrooms);</li>
 *   <li>the "dream" strength (from the meter) is sent to the player's screen shader;</li>
 *   <li>once soaked long enough, a {@link PhantomFriend} appears for them, then a cooldown.</li>
 * </ol>
 * All the numbers are in {@link HallucinationData}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class HallucinationService {
    /** Your friend appears 18..28 blocks away: far enough to be uncanny, close enough to notice. */
    private static final double SPAWN_MIN_DISTANCE = 18.0;
    private static final double SPAWN_EXTRA_DISTANCE = 10.0;
    /** Spawn spots are picked within this angle (each side) of "directly away from the exit". */
    private static final double SPAWN_SPREAD_DEGREES = 70.0;
    /** Other players further than this (or in another dimension) are the best ones to copy. */
    private static final double FAR_FRIEND_DISTANCE = 48.0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    private static void tick(ServerPlayer player) {
        HallucinationData data = player.getData(ModAttachments.HALLUCINATION);
        if (data.phantomCooldown > 0) {
            data.phantomCooldown--;
        }
        boolean inPoolrooms = BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.POOLROOMS;
        if (!inPoolrooms || player.isSpectator()) {
            // Out of the Poolrooms you wake up at once.
            data.wet = 0;
            if (data.phantomId != null) {
                removePhantom(player, data);
            }
        } else {
            data.wet = player.isInWater()
                    ? Math.min(HallucinationData.WET_MAX, data.wet + 1)
                    : Math.max(0, data.wet - 1);
            if (data.phantomId != null) {
                PhantomFriend phantom = findPhantom(player, data);
                if (phantom == null) {
                    phantomEnded(data); // it vanished (or its chunk unloaded): start the cooldown
                } else if (data.wet == 0) {
                    phantom.vanish(true); // completely dried off: the hallucination fades
                }
            } else if (data.phantomCooldown == 0 && data.wet >= HallucinationData.PHANTOM_WET && player.tickCount % 20 == 0) {
                // Once a second is plenty; the spot search does a few dozen block lookups.
                trySpawnPhantom(player, data);
            }
        }
        sync(player, data);
    }

    /** Sends the dream value when it changed noticeably (about every 10 ticks while it moves). */
    private static void sync(ServerPlayer player, HallucinationData data) {
        float dream = data.dream();
        float last = data.lastSentDream;
        if (dream == last) {
            return;
        }
        // Reaching exactly 0 or 1, or a first send, goes out at once; small drifts wait for the next 10th tick.
        boolean important = last < 0.0f || dream == 0.0f || dream == 1.0f;
        if (!important && (Math.abs(dream - last) < 0.004f || player.tickCount % 10 != 0)) {
            return;
        }
        data.lastSentDream = dream;
        PacketDistributor.sendToPlayer(player, new HallucinationPayload(dream));
    }

    /**
     * Almond Water: instantly clear-headed. Wet meter to 0, your phantom vanishes, the dream
     * shader is told right away.
     */
    public static void cure(ServerPlayer player) {
        HallucinationData data = player.getData(ModAttachments.HALLUCINATION);
        data.wet = 0;
        if (data.phantomId != null) {
            PhantomFriend phantom = findPhantom(player, data);
            if (phantom != null) {
                phantom.vanish(true);
            }
        }
        sync(player, data);
    }

    /**
     * Operator test hook: soak the player to the maximum and clear the cooldown, so a phantom shows
     * up within a second (if they're in the Poolrooms). Returns false when not in the Poolrooms.
     */
    public static boolean forceHallucination(ServerPlayer player) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.POOLROOMS) {
            return false;
        }
        HallucinationData data = player.getData(ModAttachments.HALLUCINATION);
        data.wet = HallucinationData.WET_MAX;
        data.phantomCooldown = 0;
        if (data.phantomId == null) {
            trySpawnPhantom(player, data);
        }
        sync(player, data);
        return true;
    }

    // ---- Phantom bookkeeping ---------------------------------------------------------------

    @Nullable
    private static PhantomFriend findPhantom(ServerPlayer player, HallucinationData data) {
        Entity entity = data.phantomId == null ? null : player.serverLevel().getEntity(data.phantomId);
        return entity instanceof PhantomFriend phantom && phantom.isAlive() ? phantom : null;
    }

    private static void phantomEnded(HallucinationData data) {
        data.phantomId = null;
        data.phantomCooldown = HallucinationData.PHANTOM_COOLDOWN;
    }

    /** The player left the Poolrooms: remove their phantom there right away (silently). */
    private static void removePhantom(ServerPlayer player, HallucinationData data) {
        ServerLevel poolrooms = player.server.getLevel(BackroomsLevel.POOLROOMS.dimension());
        if (poolrooms != null && poolrooms.getEntity(data.phantomId) instanceof PhantomFriend phantom) {
            phantom.discard();
        }
        phantomEnded(data);
    }

    /**
     * Looks for a spot 18..28 blocks away, on the far side of the player from the nearest exit,
     * preferring spots the player can actually see and that are in front of them. Then spawns the
     * phantom there, facing the player. Returns false if no good spot was found (try again later).
     */
    private static boolean trySpawnPhantom(ServerPlayer victim, HallucinationData data) {
        ServerLevel level = victim.serverLevel();
        BackroomsLevel floor = BackroomsLevel.POOLROOMS;
        LevelLayout layout = floor.layout();
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        RandomSource random = victim.getRandom();

        int[] exit = layout.exitPosition(seed, victim.getBlockX(), victim.getBlockZ());
        double awayAngle = Math.atan2(victim.getZ() - exit[1], victim.getX() - exit[0]);
        Vec3 look = victim.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        Vec3 eyes = victim.getEyePosition();

        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = awayAngle + (random.nextDouble() * 2.0 - 1.0) * Math.toRadians(SPAWN_SPREAD_DEGREES);
            double distance = SPAWN_MIN_DISTANCE + random.nextDouble() * SPAWN_EXTRA_DISTANCE;
            int x = (int) Math.floor(victim.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(victim.getZ() + Math.sin(angle) * distance);
            BlockPos stand = PhantomFriend.findStandPos(level, layout, seed, x, z);
            if (stand == null) {
                continue;
            }
            Vec3 chest = Vec3.atBottomCenterOf(stand).add(0.0, 1.4, 0.0);
            double score = random.nextDouble() * 0.5; // a little randomness between equally good spots
            if (canSee(victim, eyes, chest)) {
                score += 2.0; // "is that... them? over there?"
            }
            Vec3 toSpot = chest.subtract(eyes).multiply(1.0, 0.0, 1.0).normalize();
            if (toSpot.dot(look) > 0.5) {
                score += 1.0; // roughly in front of you
            }
            if (score > bestScore) {
                bestScore = score;
                best = stand;
            }
        }
        if (best == null) {
            return false;
        }

        PhantomFriend phantom = ModEntities.PHANTOM_FRIEND.get().create(level);
        if (phantom == null) {
            return false;
        }
        phantom.setup(victim, pickMimic(victim));
        double px = best.getX() + 0.5;
        double pz = best.getZ() + 0.5;
        // Face the victim: Minecraft yaw 0 looks toward +Z, hence the -90.
        float yaw = (float) (Math.toDegrees(Math.atan2(victim.getZ() - pz, victim.getX() - px)) - 90.0);
        phantom.moveTo(px, best.getY(), pz, yaw, 0.0f);
        phantom.setYHeadRot(yaw);
        phantom.setYBodyRot(yaw);
        if (!level.addFreshEntity(phantom)) {
            return false;
        }
        data.phantomId = phantom.getUUID();
        return true;
    }

    private static boolean canSee(ServerPlayer victim, Vec3 from, Vec3 to) {
        return victim.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim))
                .getType() == HitResult.Type.MISS;
    }

    /**
     * Whose face it wears: another online player, ideally one who is far away or in another
     * dimension (so the real one isn't standing right next to you). Alone? Then it's... you.
     */
    private static ServerPlayer pickMimic(ServerPlayer victim) {
        List<ServerPlayer> others = new ArrayList<>();
        List<ServerPlayer> farAway = new ArrayList<>();
        for (ServerPlayer other : victim.server.getPlayerList().getPlayers()) {
            if (other == victim) {
                continue;
            }
            others.add(other);
            if (other.level() != victim.level() || other.distanceTo(victim) > FAR_FRIEND_DISTANCE) {
                farAway.add(other);
            }
        }
        List<ServerPlayer> pool = !farAway.isEmpty() ? farAway : others;
        return pool.isEmpty() ? victim : pool.get(victim.getRandom().nextInt(pool.size()));
    }

    // ---- /backrooms hallucinate [targets] --------------------------------------------------

    /**
     * Adds {@code /backrooms hallucinate [targets]} to the existing /backrooms command. Brigadier
     * merges two registrations of the same literal, so this doesn't touch BackroomsCommand.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(hallucinateCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hallucinateCommand() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("hallucinate")
                        .executes(ctx -> hallucinate(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> hallucinate(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
    }

    private static int hallucinate(CommandSourceStack source, Collection<ServerPlayer> players) {
        int count = 0;
        for (ServerPlayer player : players) {
            if (forceHallucination(player)) {
                count++;
            } else {
                source.sendFailure(Component.literal(player.getGameProfile().getName() + " is not in the Poolrooms."));
            }
        }
        int done = count;
        if (done > 0) {
            source.sendSuccess(() -> Component.literal("Soaked " + done + " player(s): full dream, a friend appears within a second."), true);
        }
        return done;
    }

    private HallucinationService() {}
}
