package com.rickr.backrooms.player;

import com.rickr.backrooms.block.CheckpointRadioBlock;
import com.rickr.backrooms.network.SharedCheckpointSavedPayload;
import com.rickr.backrooms.registry.ModGameRules;
import com.rickr.backrooms.world.Travel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Shared checkpoints (server side). When someone saves at a radio, their friends on that level
 * can wake up there too, so a group isn't scattered back to the real world one by one.
 *
 * <p>"Friends" means the same vanilla scoreboard team ({@code /team add}, {@code /team join}).
 * Players without a team all share one "everyone" bucket. Per team, each level remembers the
 * LATEST save of EACH player (so your own save never pushes your friend's out), newest first, at
 * most {@link #MAX_PER_BUCKET} of them. It lives in the level's own save data, so it survives
 * restarts and the saver logging out. Leaving the level does not delete it: the others may still
 * be down there.
 *
 * <p>All of this only happens while the game rule {@code backroomsSharedCheckpoints} is on.
 * {@link Checkpoints} decides on death which checkpoint you actually wake up at.
 */
public final class SharedCheckpoints {
    /**
     * The bucket for players without a team. Team names typed in {@code /team add} can't contain
     * '#', so no real team can ever be called this.
     */
    private static final String EVERYONE = "#everyone";
    /** File name of the save data (one per level, inside that level's own data folder). */
    private static final String DATA_NAME = "backrooms_shared_checkpoints";
    /**
     * How many savers' checkpoints one team keeps per level. Only the newest usable one is ever
     * used, so this is just a safety limit on a busy server: the oldest saver drops off.
     */
    private static final int MAX_PER_BUCKET = 20;

    /**
     * One shared checkpoint: who saved it, where the radio stands, the floor spot in front of it
     * (where you wake up), the way you face, and when (game time in ticks, the same clock on
     * every level, so two saves can be compared).
     */
    public record Entry(UUID saver, String saverName, ResourceKey<Level> dimension, BlockPos radioPos,
                        BlockPos spot, float yaw, long gameTime) {}

    /** True while the game rule is on (it's stored per world, so any level of the server tells). */
    public static boolean enabled(ServerLevel level) {
        return level.getGameRules().getBoolean(ModGameRules.SHARED_CHECKPOINTS);
    }

    /** Which bucket a player's checkpoints go into and come from: their team's name, or "everyone". */
    private static String bucket(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        return team == null ? EVERYONE : team.getName();
    }

    /**
     * The radio was just used: remember this as the saver's shared checkpoint on this level
     * (replacing their older one; other players' saves stay), and tell the saver's teammates
     * standing on the same level.
     */
    public static void record(ServerPlayer saver, BlockPos radioPos, BlockPos spot, float yaw) {
        ServerLevel level = saver.serverLevel();
        if (!enabled(level)) {
            return;
        }
        String team = bucket(saver);
        String name = saver.getGameProfile().getName();
        UUID id = saver.getUUID();
        Data data = data(level);
        List<Entry> saves = data.buckets.computeIfAbsent(team, t -> new ArrayList<>());
        saves.removeIf(e -> e.saver().equals(id)); // only your latest save counts
        saves.add(0, new Entry(id, name, level.dimension(), radioPos.immutable(), spot.immutable(), yaw, level.getGameTime()));
        if (saves.size() > MAX_PER_BUCKET) {
            saves.remove(saves.size() - 1); // the list is newest first, so this is the oldest
        }
        data.setDirty(); // tells Minecraft to write the file at the next save

        SharedCheckpointSavedPayload payload = new SharedCheckpointSavedPayload(name);
        for (ServerPlayer other : level.players()) {
            if (other != saver && team.equals(bucket(other))) {
                PacketDistributor.sendToPlayer(other, payload);
            }
        }
    }

    /**
     * The shared checkpoint {@code player} may wake up at on the level they are on (where they
     * just died), or null if there is none: the NEWEST one in the player's own bucket that was
     * saved by SOMEONE ELSE and is still usable (the radio still stands there and the floor spot
     * in front of it, feet and head, is free to stand in). A blocked one is skipped and the next
     * newest is tried.
     */
    @Nullable
    public static Entry usableFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!enabled(level) || !Travel.inBackrooms(level)) {
            return null;
        }
        List<Entry> saves = data(level).buckets.get(bucket(player));
        if (saves == null) {
            return null;
        }
        UUID id = player.getUUID();
        for (Entry entry : saves) { // newest first
            if (!entry.saver().equals(id) && standable(level, entry)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Is the radio still there and the spot in front of it clear? This is the same "free to stand
     * in" test Minecraft itself uses for a forced respawn point (no solid block, no liquid), so if
     * we say yes, Minecraft won't turn around and send the player to world spawn instead.
     */
    private static boolean standable(ServerLevel level, Entry entry) {
        if (!(level.getBlockState(entry.radioPos()).getBlock() instanceof CheckpointRadioBlock)) {
            return false;
        }
        BlockState feet = level.getBlockState(entry.spot());
        BlockState head = level.getBlockState(entry.spot().above());
        return feet.getBlock().isPossibleToRespawnInThis(feet) && head.getBlock().isPossibleToRespawnInThis(head);
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), DATA_NAME);
    }

    /**
     * What is written to disk: team bucket -> the saves in it, newest first, one per player.
     *
     * <p>File layout: a compound "#buckets" holding one list per team. The first version of this
     * file had one entry per team written straight at the top level; {@link #load} still reads
     * that (each becomes a one-entry list), so older worlds keep their shared checkpoints.
     */
    private static final class Data extends SavedData {
        /** '#' again, so it can never clash with a team name from the old layout. */
        private static final String BUCKETS = "#buckets";

        final Map<String, List<Entry>> buckets = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            if (tag.contains(BUCKETS, Tag.TAG_COMPOUND)) {
                CompoundTag all = tag.getCompound(BUCKETS);
                for (String team : all.getAllKeys()) {
                    List<Entry> saves = new ArrayList<>();
                    for (Tag t : all.getList(team, Tag.TAG_COMPOUND)) {
                        Entry entry = read((CompoundTag) t);
                        if (entry != null) {
                            saves.add(entry);
                        }
                    }
                    // Newest first, whatever order the file had.
                    saves.sort(Comparator.comparingLong(Entry::gameTime).reversed());
                    data.buckets.put(team, saves);
                }
            } else {
                // Old layout: team -> its single entry.
                for (String team : tag.getAllKeys()) {
                    Entry entry = read(tag.getCompound(team));
                    if (entry != null) {
                        data.buckets.put(team, new ArrayList<>(List.of(entry)));
                    }
                }
            }
            return data;
        }

        /** One entry from the file, or null if it is damaged (skipped rather than crash the world load). */
        @Nullable
        private static Entry read(CompoundTag c) {
            ResourceLocation dimension = ResourceLocation.tryParse(c.getString("dimension"));
            if (dimension == null || !c.hasUUID("saver")) {
                return null;
            }
            return new Entry(c.getUUID("saver"), c.getString("name"),
                    ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(c.getLong("radio")),
                    BlockPos.of(c.getLong("spot")), c.getFloat("yaw"), c.getLong("time"));
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag all = new CompoundTag();
            for (Map.Entry<String, List<Entry>> bucket : buckets.entrySet()) {
                ListTag list = new ListTag();
                for (Entry entry : bucket.getValue()) {
                    CompoundTag c = new CompoundTag();
                    c.putUUID("saver", entry.saver());
                    c.putString("name", entry.saverName());
                    c.putString("dimension", entry.dimension().location().toString());
                    c.putLong("radio", entry.radioPos().asLong());
                    c.putLong("spot", entry.spot().asLong());
                    c.putFloat("yaw", entry.yaw());
                    c.putLong("time", entry.gameTime());
                    list.add(c);
                }
                all.put(bucket.getKey(), list);
            }
            tag.put(BUCKETS, all);
            return tag;
        }
    }

    private SharedCheckpoints() {}
}
