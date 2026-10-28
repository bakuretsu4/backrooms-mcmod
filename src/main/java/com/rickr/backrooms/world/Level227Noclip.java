package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.IntroCutscenePayload;
import com.rickr.backrooms.player.Checkpoints;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every way into and out of Level 227 that is NOT an exit: the tree shaking you loose into a
 * random level, a fallen bubble taking you to the level it mirrors, and being dragged in from a
 * forest somewhere else. All of them use the same filmed noclip as entering the Backrooms
 * (client/intro/NoclipTransition, the "datamosh"): the picture freezes and crumbles for half a
 * second, we move the player, and the old picture smears into the new place.
 *
 * <p>This is a copy of {@link NoclipIntro}'s timing (freeze, wait {@link NoclipIntro#LAG_TICKS},
 * move, "arrived") with any target level instead of always Level 0.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Noclip {
    /** Why someone is noclipping: decides the line they read when they land. */
    public enum Cause {
        /** The tree moved and you fell through it (Level 227's only way out). */
        UNSTABLE("message.backrooms.l227.unstable"),
        /** You stepped into a bubble's reflection. */
        BUBBLE("message.backrooms.l227.bubble"),
        /** Something in a forest of another level pulled you in. */
        DRAGGED("message.backrooms.l227.dragged");

        final String message;

        Cause(String message) {
            this.message = message;
        }
    }

    private record Pending(BackroomsLevel target, Cause cause, int ticksLeft) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /**
     * Where the tree drops you when it shakes you loose. Canon: "often more dangerous ones", so the
     * levels with something hunting you weigh most (Level 39's hounds and creatures, Level 9's
     * Watch, the Bacteria of Level 0, the Thing of Level 7, Level 1's Smilers), the calm ones least.
     * A level not listed here (a newer one) gets weight 1.
     */
    private static final Map<BackroomsLevel, Integer> DANGER = new EnumMap<>(BackroomsLevel.class);

    static {
        DANGER.put(BackroomsLevel.LEVEL_39, 5);
        DANGER.put(BackroomsLevel.LEVEL_9, 4);
        DANGER.put(BackroomsLevel.LEVEL_0, 3);
        DANGER.put(BackroomsLevel.LEVEL_7, 3);
        DANGER.put(BackroomsLevel.LEVEL_1, 3);
        DANGER.put(BackroomsLevel.LEVEL_2, 2);
        DANGER.put(BackroomsLevel.LEVEL_94, 2);
        DANGER.put(BackroomsLevel.POOLROOMS, 1);
        DANGER.put(BackroomsLevel.LEVEL_4, 1);
        DANGER.put(BackroomsLevel.LEVEL_37, 1);
        DANGER.put(BackroomsLevel.LEVEL_227, 0); // never back into the tree itself
    }

    /** A random level weighted toward the dangerous ones (never Level 227). */
    public static BackroomsLevel dangerousLevel(RandomSource random) {
        int total = 0;
        for (BackroomsLevel level : BackroomsLevel.values()) {
            total += DANGER.getOrDefault(level, 1);
        }
        int roll = random.nextInt(total);
        for (BackroomsLevel level : BackroomsLevel.values()) {
            roll -= DANGER.getOrDefault(level, 1);
            if (roll < 0) {
                return level;
            }
        }
        return BackroomsLevel.LEVEL_39;
    }

    /** Starts a noclip to {@code target}. Does nothing if this player is already mid-noclip (any kind). */
    public static void start(ServerPlayer player, BackroomsLevel target, Cause cause) {
        if (isPending(player)) {
            return;
        }
        PENDING.put(player.getUUID(), new Pending(target, cause, NoclipIntro.LAG_TICKS));
        PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.FREEZE));
    }

    /** True while this player's picture is freezing before a noclip (ours or the way in from reality). */
    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID()) || NoclipIntro.isPending(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!player.isAlive()) {
            PENDING.remove(player.getUUID()); // died while the picture froze: no trip
            return;
        }
        if (pending.ticksLeft() > 0) {
            PENDING.put(player.getUUID(), new Pending(pending.target(), pending.cause(), pending.ticksLeft() - 1));
            return;
        }
        PENDING.remove(player.getUUID());
        Checkpoints.clear(player); // a move between floors: the old checkpoint no longer applies
        if (!Travel.sendToQuietly(player, pending.target(), player.getBlockX(), player.getBlockZ())) {
            return; // the level is missing: the client gives the picture back by itself (LAG_TIMEOUT)
        }
        PacketDistributor.sendToPlayer(player, new IntroCutscenePayload(IntroCutscenePayload.ARRIVED));
        player.sendSystemMessage(Component.translatable(pending.cause().message).withStyle(s -> s.withItalic(true).withColor(0xB8D49A)));
        // Level 0 has no arrival line of its own (entering from reality uses the noclip line above).
        if (pending.target() != BackroomsLevel.LEVEL_0) {
            player.sendSystemMessage(Component.translatable("message.backrooms.arrive." + pending.target().getSerializedName())
                    .withStyle(s -> s.withItalic(true)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    private Level227Noclip() {}
}
