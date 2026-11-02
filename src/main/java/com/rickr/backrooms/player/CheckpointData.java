package com.rickr.backrooms.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The last checkpoint radio a player used (a NeoForge data attachment, see
 * {@code ModAttachments.CHECKPOINT}): which level it is in, the floor spot in front of it where
 * you wake up, and which way you face then. Empty until you use a radio, and emptied again when
 * you leave that level through its exit (see {@code world/Checkpoints}).
 *
 * <p>Saved with the player and copied on death, so it survives dying (that's its whole point).
 */
public final class CheckpointData {
    // optionalFieldOf(..., default): players saved before a field existed load with the default.
    public static final Codec<CheckpointData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(d -> Optional.ofNullable(d.dimension)),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(d -> Optional.ofNullable(d.pos)),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(d -> d.yaw),
            Codec.LONG.optionalFieldOf("saved_at", 0L).forGetter(d -> d.savedAt),
            Codec.BOOL.optionalFieldOf("shared_respawn", false).forGetter(d -> d.sharedRespawn)
    ).apply(i, (dimension, pos, yaw, savedAt, sharedRespawn) ->
            new CheckpointData(dimension.orElse(null), pos.orElse(null), yaw, savedAt, sharedRespawn)));

    @Nullable
    private ResourceKey<Level> dimension;
    @Nullable
    private BlockPos pos;
    private float yaw;
    /** When you saved (game time in ticks), to tell whether a friend's shared checkpoint is newer. */
    private long savedAt;
    /**
     * True while your vanilla respawn point is a friend's SHARED checkpoint (we set it there when
     * you died). It lets us take that respawn point away again later, e.g. when the game rule is
     * switched off, without touching a respawn point an operator set by hand.
     */
    private boolean sharedRespawn;

    /** What the client last heard about "am I in a safe room?" (not saved; -1 = never sent). */
    int lastSentSafe = -1;

    public CheckpointData() {
        this(null, null, 0.0f, 0L, false);
    }

    private CheckpointData(@Nullable ResourceKey<Level> dimension, @Nullable BlockPos pos, float yaw, long savedAt,
                           boolean sharedRespawn) {
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
        this.savedAt = savedAt;
        this.sharedRespawn = sharedRespawn;
    }

    /** True once a radio has been used (and the level not left since). */
    public boolean isSet() {
        return dimension != null && pos != null;
    }

    @Nullable
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** The block your feet are in when you respawn (the floor spot in front of the radio). */
    @Nullable
    public BlockPos pos() {
        return pos;
    }

    /** Which way you look when you respawn (Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east). */
    public float yaw() {
        return yaw;
    }

    /** Game time (ticks) of the save; 0 for checkpoints saved before this was recorded. */
    public long savedAt() {
        return savedAt;
    }

    public boolean sharedRespawn() {
        return sharedRespawn;
    }

    public void setSharedRespawn(boolean sharedRespawn) {
        this.sharedRespawn = sharedRespawn;
    }

    public void set(ResourceKey<Level> dimension, BlockPos pos, float yaw, long savedAt) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.yaw = yaw;
        this.savedAt = savedAt;
    }

    public void clear() {
        dimension = null;
        pos = null;
        yaw = 0.0f;
        savedAt = 0L;
        sharedRespawn = false;
    }
}
