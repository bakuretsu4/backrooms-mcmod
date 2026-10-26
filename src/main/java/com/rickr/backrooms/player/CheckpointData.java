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
    public static final Codec<CheckpointData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(d -> Optional.ofNullable(d.dimension)),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(d -> Optional.ofNullable(d.pos)),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(d -> d.yaw)
    ).apply(i, (dimension, pos, yaw) -> new CheckpointData(dimension.orElse(null), pos.orElse(null), yaw)));

    @Nullable
    private ResourceKey<Level> dimension;
    @Nullable
    private BlockPos pos;
    private float yaw;

    /** What the client last heard about "am I in a safe room?" (not saved; -1 = never sent). */
    int lastSentSafe = -1;

    public CheckpointData() {
        this(null, null, 0.0f);
    }

    private CheckpointData(@Nullable ResourceKey<Level> dimension, @Nullable BlockPos pos, float yaw) {
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
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

    public void set(ResourceKey<Level> dimension, BlockPos pos, float yaw) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.yaw = yaw;
    }

    public void clear() {
        dimension = null;
        pos = null;
        yaw = 0.0f;
    }
}
