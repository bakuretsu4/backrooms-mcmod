package com.rickr.backrooms.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VoidBubble;
import com.rickr.backrooms.network.Level227Payload;
import com.rickr.backrooms.registry.Level227Atmos;
import com.rickr.backrooms.world.layout.Level227Tree;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The tree of Level 227 is never still: it moves at random in the void. Two things come of that
 * (server side; the client plays them in {@code client/Level227Audio}):
 * <ul>
 *   <li><b>Lurches.</b> Every {@value #LURCH_MIN_MINUTES}-{@value #LURCH_MAX_MINUTES} minutes the
 *       whole tree heaves: everyone in the level hears one vast groan, leaves rain down and the
 *       camera shakes very slightly. Same moment for everyone (it is one tree).</li>
 *   <li><b>Instability.</b> Canon: wanderers suddenly noclip to random levels, often more dangerous
 *       ones, and that is the ONLY way out. After a random {@value #MIN_MINUTES}-{@value #MAX_MINUTES}
 *       minutes in the level, each player gets {@value #WARN_TICKS} ticks of warning (a groan
 *       building from below, the world shuddering harder and harder), then noclips (the filmed
 *       "datamosh" transition) to a random level weighted toward the dangerous ones
 *       ({@link Level227Noclip#dangerousLevel}). The clock is per player and starts over every
 *       time you arrive.</li>
 * </ul>
 * Also the test command {@code /backrooms l227 lurch|unstable|drag|bubble}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Instability {
    private static final int MIN_MINUTES = 10;
    private static final int MAX_MINUTES = 30;
    /** The warning before the noclip: 6 s of shuddering. */
    public static final int WARN_TICKS = 120;
    private static final int LURCH_MIN_MINUTES = 4;
    private static final int LURCH_MAX_MINUTES = 10;
    /** A lurch lasts this long on the client (the groan, the leaves, the shake). */
    private static final int LURCH_TICKS = 180;

    private static final RandomSource RANDOM = RandomSource.create();
    /** Server tick at which each player in Level 227 gets shaken loose. */
    private static final Map<UUID, Long> DEADLINE = new HashMap<>();
    /** Players whose warning has been sent (so it is sent once). */
    private static final Map<UUID, Boolean> WARNED = new HashMap<>();
    /** Server tick of the next lurch (0 = not scheduled yet). */
    private static long nextLurch;

    private static boolean inLevel227(ServerPlayer player) {
        return BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_227;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!inLevel227(player) || player.isSpectator()) {
            // Left the tree (or watching as a spectator): the clock starts over next time.
            if (DEADLINE.remove(id) != null) {
                WARNED.remove(id);
            }
            return;
        }
        long now = player.server.getTickCount();
        Long deadline = DEADLINE.get(id);
        if (deadline == null) {
            DEADLINE.put(id, now + Mth.nextInt(RANDOM, MIN_MINUTES * 1200, MAX_MINUTES * 1200));
            return;
        }
        if (!player.isAlive() || Level227Noclip.isPending(player)) {
            return; // dead, or already on the way somewhere: hold the clock
        }
        if (now >= deadline - WARN_TICKS && WARNED.putIfAbsent(id, Boolean.TRUE) == null) {
            warn(player, (int) Math.max(1, deadline - now));
        }
        if (now >= deadline) {
            DEADLINE.remove(id);
            WARNED.remove(id);
            Level227Noclip.start(player, Level227Noclip.dangerousLevel(RANDOM), Level227Noclip.Cause.UNSTABLE);
        }
    }

    /** The shudder before the drop: the client builds the shake and the groan over {@code ticks}. */
    private static void warn(ServerPlayer player, int ticks) {
        PacketDistributor.sendToPlayer(player, new Level227Payload(Level227Payload.UNSTABLE, ticks, 1.0f));
        player.playNotifySound(Level227Atmos.UNSTABLE.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
    }

    /** Lurches: one clock for the whole level. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_227) {
            return;
        }
        long now = level.getServer().getTickCount();
        if (nextLurch == 0L) {
            nextLurch = now + nextLurchDelay();
            return;
        }
        if (now < nextLurch) {
            return;
        }
        nextLurch = now + nextLurchDelay();
        if (!level.players().isEmpty()) {
            lurch(level, 0.35f + RANDOM.nextFloat() * 0.65f);
        }
    }

    private static int nextLurchDelay() {
        return Mth.nextInt(RANDOM, LURCH_MIN_MINUTES * 1200, LURCH_MAX_MINUTES * 1200);
    }

    /** Everyone in the level feels the tree heave ({@code strength} 0..1). */
    public static void lurch(ServerLevel level, float strength) {
        Level227Payload payload = new Level227Payload(Level227Payload.LURCH, LURCH_TICKS, strength);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DEADLINE.remove(event.getEntity().getUUID());
        WARNED.remove(event.getEntity().getUUID());
    }

    // ---- /backrooms l227 lurch|unstable|drag|bubble|beam ------------------------------------------

    /** Brigadier merges this into the existing /backrooms command (same trick as Level39Spawner). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("l227")
                        .then(Commands.literal("lurch").executes(Level227Instability::lurchCommand))
                        .then(Commands.literal("unstable")
                                .executes(ctx -> unstableCommand(ctx, 10))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(7, 3600))
                                        .executes(ctx -> unstableCommand(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("drag").executes(Level227Instability::dragCommand))
                        .then(Commands.literal("bubble").executes(Level227Instability::bubbleCommand))
                        .then(Commands.literal("beam").executes(Level227Instability::beamCommand))));
    }

    /** How far outside the beam's edge the test command puts you (blocks). */
    private static final double BEAM_STANDOFF = 6.0;

    /**
     * Puts you on the forest floor {@value #BEAM_STANDOFF} blocks outside the nearest sunbeam, facing
     * it, to test the shaft and the burn rule. Tries the side you are on first, then walks round the
     * beam in 15 degree steps until it finds branch top with room to stand.
     */
    private static int beamCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!inLevel227(player)) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 227."));
            return 0;
        }
        Level227Tree.Beam nearest = null;
        double best = Double.MAX_VALUE;
        for (Level227Tree.Beam b : Level227Tree.beams()) {
            double d = (b.x() - player.getX()) * (b.x() - player.getX()) + (b.z() - player.getZ()) * (b.z() - player.getZ());
            if (d < best) {
                best = d;
                nearest = b;
            }
        }
        if (nearest == null) {
            ctx.getSource().sendFailure(Component.literal("The tree has no sunbeams."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        double start = Math.atan2(player.getZ() - nearest.z(), player.getX() - nearest.x());
        double reach = nearest.radius() + BEAM_STANDOFF;
        for (int step = 0; step < 24; step++) {
            // 0, +15, -15, +30, -30 ... degrees from your own side of the beam.
            double angle = start + Math.toRadians(15.0 * ((step + 1) / 2) * (step % 2 == 0 ? 1 : -1));
            double x = Math.floor(nearest.x() + Math.cos(angle) * reach) + 0.5;
            double z = Math.floor(nearest.z() + Math.sin(angle) * reach) + 0.5;
            int feet = Level227Tree.topSurfaceY(x, z);
            if (feet == Integer.MIN_VALUE || Level227Tree.beamAt(x, z) != null) {
                continue;
            }
            // Room to stand (a trunk or a fern may be in the way; loads the chunk if needed).
            if (!level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(x, feet, z))) {
                continue;
            }
            // Facing the beam's axis (Minecraft yaw: 0 = +Z, 90 = -X).
            float yaw = (float) Math.toDegrees(Math.atan2(-(nearest.x() - x), nearest.z() - z));
            player.fallDistance = 0;
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.teleportTo(level, x, feet, z, yaw, 0.0f);
            Level227Tree.Beam beam = nearest;
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "Facing the sunbeam at %.0f, %.0f (radius %.0f), %.0f blocks from its edge.",
                    beam.x(), beam.z(), beam.radius(), BEAM_STANDOFF)), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("No free forest floor around the nearest sunbeam at "
                + Math.round(nearest.x()) + ", " + Math.round(nearest.z()) + "."));
        return 0;
    }

    private static int lurchCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!inLevel227(player)) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 227."));
            return 0;
        }
        lurch(player.serverLevel(), 1.0f);
        ctx.getSource().sendSuccess(() -> Component.literal("The tree lurches."), true);
        return 1;
    }

    /** Sets your instability clock so the warning starts in a few seconds. */
    private static int unstableCommand(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!inLevel227(player)) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 227."));
            return 0;
        }
        DEADLINE.put(player.getUUID(), player.server.getTickCount() + seconds * 20L);
        WARNED.remove(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("The tree will shake you loose in " + seconds + " s."), true);
        return 1;
    }

    private static int dragCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (inLevel227(player)) {
            ctx.getSource().sendFailure(Component.literal("You are already in Level 227."));
            return 0;
        }
        Level227Entrances.drag(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Something takes hold of you."), true);
        return 1;
    }

    private static int bubbleCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!inLevel227(player)) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 227."));
            return 0;
        }
        VoidBubble bubble = VoidBubble.spawnNearEdge(player.serverLevel(), player, RANDOM, true);
        if (bubble == null) {
            ctx.getSource().sendFailure(Component.literal("No branch edge within 96 blocks."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("A bubble drifts by the edge at "
                + bubble.blockPosition().toShortString() + " (mirroring " + bubble.target().getSerializedName() + ")."), true);
        return 1;
    }

    private Level227Instability() {}
}
