package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.SirenBlockEntity;
import com.rickr.backrooms.world.Level94Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays Level 94's siren music. Every siren on the client reports itself each tick
 * ({@link SirenBlockEntity#clientSirens}); here we pick the nearest two within earshot and let
 * only those sing ({@link SirenSongSound}). A town full of sirens would otherwise stack a dozen
 * copies of the tune into a deafening wall of sound.
 *
 * <p>The volume follows {@link Level94Clock#musicVolume}: full all day, faltering at dusk, then
 * silent all night. All sirens sing the same broadcast in step (see {@link #songPosition}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class SirenSongs {
    /** How many sirens sing at once (the nearest ones). */
    private static final int MAX_SINGING = 2;
    /** Heard from this far (matches "attenuation_distance": 64 in sounds.json). */
    private static final double RANGE = 64.0;
    /** A siren that hasn't ticked for this long was unloaded (or broken). */
    private static final int GONE_AFTER_TICKS = 20;
    /**
     * Keep the sounds alive through this many ticks of silence: the dusk dropouts last 10 ticks,
     * and restarting the stream for every one would stutter. After that the night is silent.
     */
    private static final int SILENT_TICKS_BEFORE_STOP = 60;
    /**
     * A siren that's already singing counts as this much closer, so two sirens about the same
     * distance away don't keep swapping as you walk between them.
     */
    private static final double KEEP_BONUS = 6.0;
    /**
     * Length of siren_song.ogg in samples at 44.1 kHz. Keep in sync with snd_l94_siren_song in
     * tools/gen_assets.py (it prints the length). Only used to keep the sirens in step.
     */
    static final long SONG_SAMPLES = 1_764_000L;
    private static final double SONG_SECONDS = SONG_SAMPLES / 44100.0;
    /** The sound engine needs a moment to start a stream; aim that far ahead. */
    private static final double START_LEAD_SECONDS = 0.1;

    private static final Map<BlockPos, SirenSongSound> SINGING = new HashMap<>();
    private static final BlockPos[] chosen = new BlockPos[MAX_SINGING];
    private static final double[] chosenDist = new double[MAX_SINGING];
    private static ClientLevel lastLevel;
    private static int silentTicks = SILENT_TICKS_BEFORE_STOP;
    /** The song clock: the level's game time at the last tick, and when that tick ran. */
    private static volatile long clockGameTime;
    private static volatile long clockNanos;

    private SirenSongs() {}

    /**
     * Where (seconds into the loop) the town's song is right now. It runs on the level's game
     * time, so every siren, and every player on a server, hears the same bar at the same moment.
     * Safe to call from the sound loader thread.
     */
    static double songPosition() {
        double seconds = clockGameTime / 20.0 + (System.nanoTime() - clockNanos) / 1.0e9 + START_LEAD_SECONDS;
        return seconds % SONG_SECONDS;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Map<BlockPos, Long> sirens = SirenBlockEntity.clientSirens();
        if (level != lastLevel) {
            // New world or new dimension: the old positions mean nothing here.
            sirens.clear();
            stopAll();
            lastLevel = level;
        }
        Player player = mc.player;
        if (level == null || player == null) {
            return;
        }
        long now = level.getGameTime();
        clockGameTime = now;
        clockNanos = System.nanoTime();
        sirens.values().removeIf(lastTick -> now - lastTick > GONE_AFTER_TICKS);

        float music = Level94Clock.musicVolume(level);
        silentTicks = music > 0.0f ? 0 : silentTicks + 1;
        if (silentTicks >= SILENT_TICKS_BEFORE_STOP || sirens.isEmpty()) {
            stopAll();
            return;
        }

        pickNearest(sirens, player);
        SoundManager sounds = mc.getSoundManager();
        // Release the ones that are no longer among the nearest (or that the engine dropped).
        Iterator<Map.Entry<BlockPos, SirenSongSound>> it = SINGING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, SirenSongSound> entry = it.next();
            if (!isChosen(entry.getKey()) || entry.getValue().isStopped()) {
                entry.getValue().release();
                it.remove();
            }
        }
        for (BlockPos pos : chosen) {
            if (pos == null) {
                continue;
            }
            SirenSongSound song = SINGING.get(pos);
            if (song == null) {
                song = new SirenSongSound(pos, music);
                SINGING.put(pos, song);
                sounds.play(song);
            }
            song.target = music;
        }
    }

    /** Fills {@link #chosen} with the nearest sirens in range (nulls if there are fewer). */
    private static void pickNearest(Map<BlockPos, Long> sirens, Player player) {
        for (int i = 0; i < MAX_SINGING; i++) {
            chosen[i] = null;
            chosenDist[i] = Double.MAX_VALUE;
        }
        double px = player.getX();
        double py = player.getEyeY();
        double pz = player.getZ();
        for (BlockPos pos : sirens.keySet()) {
            double d = Math.sqrt(pos.distToCenterSqr(px, py, pz));
            if (d > RANGE) {
                continue;
            }
            if (SINGING.containsKey(pos)) {
                d -= KEEP_BONUS;
            }
            // Insert into the short sorted list, nearest first.
            for (int i = 0; i < MAX_SINGING; i++) {
                if (d < chosenDist[i]) {
                    for (int j = MAX_SINGING - 1; j > i; j--) {
                        chosen[j] = chosen[j - 1];
                        chosenDist[j] = chosenDist[j - 1];
                    }
                    chosen[i] = pos;
                    chosenDist[i] = d;
                    break;
                }
            }
        }
    }

    private static boolean isChosen(BlockPos pos) {
        for (BlockPos c : chosen) {
            if (pos.equals(c)) {
                return true;
            }
        }
        return false;
    }

    private static void stopAll() {
        for (SirenSongSound song : SINGING.values()) {
            song.release();
        }
        SINGING.clear();
        for (int i = 0; i < MAX_SINGING; i++) {
            chosen[i] = null;
        }
    }
}
