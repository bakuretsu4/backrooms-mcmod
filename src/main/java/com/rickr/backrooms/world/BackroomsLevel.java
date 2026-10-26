package com.rickr.backrooms.world;

import com.mojang.serialization.Codec;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.layout.Level1Layout;
import com.rickr.backrooms.world.layout.Level2Layout;
import com.rickr.backrooms.world.layout.Level37Layout;
import com.rickr.backrooms.world.layout.Level4Layout;
import com.rickr.backrooms.world.layout.Level94Layout;
import com.rickr.backrooms.world.layout.Level7Layout;
import com.rickr.backrooms.world.layout.Level9Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The floors of the Backrooms. Each is its own dimension (data/backrooms/dimension/&lt;name&gt;.json)
 * using {@link BackroomsChunkGenerator} with a different {@link LevelLayout}.
 *
 * <p>Order of escape: Overworld --noclip--> Level 0 --stairwell--> Level 1 --stairwell--> Level 2
 * --exit door--> Level 4 --emergency stairwell--> Poolrooms --the drain--> Level 9 --the underpass-->
 * Overworld. Secret branch: a rare sunlit archway in the Poolrooms leads to Level 37 ("Sublimity"),
 * and diving into one of its deep pools brings you out in Level 9. Second secret: a rare clay door
 * on a Level 9 house opens onto Level 94 ("Motion"); its dark-doored houses lead back to Level 9 and
 * the throne room of its castle sends you to Level 1.
 *
 * <p>New floors are added at the END of this enum: saved data and commands refer to floors by name,
 * but keeping the order stable avoids surprises.
 */
public enum BackroomsLevel implements StringRepresentable {
    LEVEL_0("level_0", new Level0Layout(), 30, 1, 90),
    LEVEL_1("level_1", new Level1Layout(), 30, 2, 45),
    LEVEL_2("level_2", new Level2Layout(), Integer.MIN_VALUE, 2, 60),
    /** No monsters here (cap 0): the danger is the water itself, which makes you hallucinate. */
    POOLROOMS("poolrooms", new PoolroomsLayout(), 30, 0, 1),
    /**
     * The Suburbs. No Bacteria (cap 0): the Neighborhood Watch patrols here instead. You leave by
     * walking deep into a highway underpass ({@code Level9Rules}), never by falling.
     */
    LEVEL_9("level_9", new Level9Layout(), Integer.MIN_VALUE, 0, 1),
    /** The Abandoned Office: a safe haven, no monsters. Leave down an emergency stairwell. */
    LEVEL_4("level_4", new Level4Layout(), 30, 0, 1),
    /**
     * "Sublimity": bright, pristine, calm. No monsters. Diving down a deep pool shaft (below
     * {@link Level37Layout#TRANSFER_BELOW_Y}) takes you on to Level 9.
     */
    LEVEL_37("level_37", new Level37Layout(), Level37Layout.TRANSFER_BELOW_Y, 0, 1),
    /**
     * "Motion": endless sunny hills, a toy-like town, siren poles playing cartoon music and a castle
     * on a glass mountain. Day/night cycle ({@link Level94Clock}); Smilers come out at night. No
     * Bacteria. Never left by falling: see {@code Level94Rules}.
     */
    LEVEL_94("level_94", new Level94Layout(), Integer.MIN_VALUE, 0, 1),
    /**
     * "Thalassophobia": an endless dark sea under a concrete ceiling. Reached through a flooded
     * house in Level 94; the door in the stone platform deep under the sea leads on to Level 9
     * (see {@code Level7Rules}). No monsters you can see: the Thing is only ever felt.
     */
    LEVEL_7("level_7", new Level7Layout(), Integer.MIN_VALUE, 0, 1);

    public static final Codec<BackroomsLevel> CODEC = StringRepresentable.fromEnum(BackroomsLevel::values);

    private final String name;
    private final LevelLayout layout;
    private final int transferBelowY;
    private final int maxBacteriaPerPlayer;
    private final int bacteriaSpawnChance;
    private final ResourceKey<Level> dimension;

    BackroomsLevel(String name, LevelLayout layout, int transferBelowY, int maxBacteriaPerPlayer, int bacteriaSpawnChance) {
        this.name = name;
        this.layout = layout;
        this.transferBelowY = transferBelowY;
        this.maxBacteriaPerPlayer = maxBacteriaPerPlayer;
        this.bacteriaSpawnChance = bacteriaSpawnChance;
        this.dimension = ResourceKey.create(Registries.DIMENSION, Backrooms.id(name));
    }

    public LevelLayout layout() {
        return layout;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /**
     * Going below this Y sends you to {@link #next()}: walking down the exit stairwell (Levels 0
     * and 1: floor y40, stairs reach y17) or being sucked down the drain (Poolrooms: shaft to y24).
     * Level 2 leaves through its exit door instead.
     */
    public int transferBelowY() {
        return transferBelowY;
    }

    /** Most Bacteria around one player at a time; 0 means they never spawn on this floor. */
    public int maxBacteriaPerPlayer() {
        return maxBacteriaPerPlayer;
    }

    /** One-in-N chance per second, per group of players (see BacteriaSpawner), to spawn a Bacteria when under the cap. */
    public int bacteriaSpawnChance() {
        return bacteriaSpawnChance;
    }

    /** The next floor down, or null when the exit leads back to reality. */
    @Nullable
    public BackroomsLevel next() {
        return switch (this) {
            case LEVEL_0 -> LEVEL_1;
            case LEVEL_1 -> LEVEL_2;
            case LEVEL_2 -> LEVEL_4;
            case LEVEL_4 -> POOLROOMS;
            case POOLROOMS, LEVEL_37, LEVEL_94, LEVEL_7 -> LEVEL_9;
            case LEVEL_9 -> null;
        };
    }

    /** Looks a floor up by its name (e.g. "level_37"), or null. Used by /backrooms enter. */
    @Nullable
    public static BackroomsLevel byName(String name) {
        for (BackroomsLevel level : values()) {
            if (level.name.equals(name)) {
                return level;
            }
        }
        return null;
    }

    @Nullable
    public static BackroomsLevel fromDimension(ResourceKey<Level> key) {
        for (BackroomsLevel level : values()) {
            if (level.dimension.equals(key)) {
                return level;
            }
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
