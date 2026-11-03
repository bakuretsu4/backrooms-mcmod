package com.rickr.backrooms.world;

import com.rickr.backrooms.player.Checkpoints;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Every way into and out of Level 227 that is NOT an exit: the tree shaking you loose into a
 * random level, a fallen bubble taking you to the level it mirrors, and being dragged in from a
 * forest somewhere else. All of them are filmed like every other move between levels
 * ({@link Travel#moshTo}, the "datamosh"): the picture freezes and crumbles, we move the player,
 * and the old picture smears into the new place.
 */
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

    /**
     * Where the tree drops you when it shakes you loose. Canon: "often more dangerous ones", so the
     * levels with something hunting you weigh most (Level 39's hounds and creatures, Level 9's
     * Watch, the Bacteria of Level 0, the Thing of Level 7, Level 1's Smilers), the calm ones least.
     * A level not listed here (a newer one) gets weight 1.
     *
     * <p>Weight 0: never there. Level 227 is off the path (you are only dragged in, from Level 9's
     * woods and Level 94's hills), and a level you are sent to off the path must have a way back to
     * the one you left: from every level up to Level 94 the path still reaches Level 9 or 94, but
     * from 369 and 490 it only goes on to the end. (The void bubbles mirror the same levels, see
     * VoidBubble.MIRRORS.)
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
        DANGER.put(BackroomsLevel.LEVEL_369, 0); // past Level 94 on the path: no way back
        DANGER.put(BackroomsLevel.LEVEL_490, 0); // the same
        DANGER.put(BackroomsLevel.LEVEL_227, 0); // never back into the tree itself
    }

    /** A random level weighted toward the dangerous ones (never Level 227, 369 or 490). */
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

    /** Starts a noclip to {@code target}. Does nothing if this player is already on the way somewhere. */
    public static void start(ServerPlayer player, BackroomsLevel target, Cause cause) {
        Travel.moshTo(player, p -> {
            Checkpoints.clear(p); // a move between floors: the old checkpoint no longer applies
            if (!Travel.sendToQuietly(p, target, p.getBlockX(), p.getBlockZ())) {
                return; // the level is missing: the client gives the picture back by itself
            }
            // One arrival card: why you fell ("The tree lurched...") over the level you landed in.
            Travel.showArrival(p, target, cause.message);
        });
    }

    /** True while this player's picture is breaking up before a move (any kind, not only ours). */
    public static boolean isPending(ServerPlayer player) {
        return Travel.isMoving(player);
    }

    private Level227Noclip() {}
}
