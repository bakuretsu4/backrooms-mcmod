package com.rickr.backrooms.world;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Level 228's floors, by ear: every zone of the carnival maze has its own floor (spec §1), and
 * the floor is drawn by the prop renderer over one plain block, so the step sound can't come from
 * the block. It comes from the zone instead (Level228Plan#zoneAt, pure, so the server, which plays
 * your steps to everyone else, and your own client, which plays them to you, always agree):
 * <ul>
 *   <li>foyer, Crystal Maze, final chamber: worn old floorboards;</li>
 *   <li>Alhambra Hall: faded patterned tile;</li>
 *   <li>Funhouse: peeling checker linoleum;</li>
 *   <li>backstage passages: plywood over joists;</li>
 *   <li>Glass Labyrinth and the exit hallway: bare concrete (the mod's own concrete steps).</li>
 * </ul>
 * Used by player/PlayerGameplay on both sides.
 */
public final class Level228Footsteps {
    /** The layout seed on the client (set every tick by client/level228/Level228Ambience). */
    private static volatile long clientSeed;
    private static volatile boolean clientHasSeed;
    /** The server's layout seed, cached for the level it was computed for. */
    @Nullable
    private static ServerLevel seedLevel;
    private static long serverSeed;

    private Level228Footsteps() {}

    /** Client thread: the layout seed the client knows (from network/Level228StatePayload). */
    public static void setClientSeed(long seed, boolean known) {
        clientSeed = seed;
        clientHasSeed = known;
    }

    /**
     * The step sound for a player in Level 228 (null anywhere else, or where the zone is unknown:
     * the caller's usual choice then applies).
     */
    @Nullable
    public static SoundEvent step(Player player, boolean running) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            return null;
        }
        long seed;
        if (player.level() instanceof ServerLevel server) {
            if (server != seedLevel) {
                serverSeed = BackroomsSeeds.layoutSeed(server, BackroomsLevel.LEVEL_228);
                seedLevel = server;
            }
            seed = serverSeed;
        } else if (clientHasSeed) {
            seed = clientSeed;
        } else {
            return null;
        }
        return switch (Level228Plan.zoneAt(seed, Mth.floor(player.getX()), Mth.floor(player.getZ()))) {
            case FOYER, CRYSTAL, CHAMBER -> ModSounds.LEVEL228_STEP_BOARDS.get();
            case ALHAMBRA -> ModSounds.LEVEL228_STEP_TILE.get();
            case FUNHOUSE -> ModSounds.LEVEL228_STEP_LINO.get();
            case BACKSTAGE -> ModSounds.LEVEL228_STEP_PLYWOOD.get();
            case GLASS, EXIT_HALL -> running ? ModSounds.STEP_CONCRETE_RUN.get() : ModSounds.STEP_CONCRETE_WALK.get();
            case NONE -> null;
        };
    }
}
