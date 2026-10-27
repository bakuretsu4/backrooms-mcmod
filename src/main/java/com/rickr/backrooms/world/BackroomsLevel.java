package com.rickr.backrooms.world;

import com.mojang.serialization.Codec;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.layout.Level1Layout;
import com.rickr.backrooms.world.layout.Level2Layout;
import com.rickr.backrooms.world.layout.Level37Layout;
import com.rickr.backrooms.world.layout.Level4Layout;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level490Layout;
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
 * <p>One path through every floor ({@link #next()}): Overworld --noclip--> Level 0 --stairwell-->
 * Level 1 --stairwell--> Level 2 --exit door--> Level 4 --emergency stairwell--> Poolrooms --the
 * drain--> Level 37 --a deep pool--> Level 7 --the door on the sea floor--> Level 39's flooded
 * tunnels --a superstructure window--> Level 94 --a dark-doored house--> Level 490 --the leap from
 * the structure--> Level 9 --the underpass--> Overworld. Every move between floors datamoshes
 * ({@link Travel}).
 *
 * <p>Rare shortcuts stay as extra ways in and out: the Poolrooms' sunlit archway skips ahead to
 * Level 39's tunnels; a clay door on a Level 9 house opens onto Level 94; Level 94's flooded houses
 * lead down to Level 7 and the light behind its castle's throne back to Level 1; the gondola in
 * Level 9's Crimson Field drops you over Level 39's town, its foxholes into Level 2 and its gloom
 * back to Level 0.
 *
 * <p>New floors are added at the END of this enum: saved data and commands refer to floors by name,
 * but keeping the order stable avoids surprises.
 */
public enum BackroomsLevel implements StringRepresentable {
    /** The only floor with the Bacteria, and only ever one of it (see BacteriaSpawner). */
    LEVEL_0("level_0", new Level0Layout(), 30, 1, 90),
    /** No Bacteria (cap 0): the garage belongs to the Smilers (blackouts and its dead zones, see SmilerSpawner). */
    LEVEL_1("level_1", new Level1Layout(), 30, 0, 1),
    /** No Bacteria (cap 0): the Smilers come with the blackouts. */
    LEVEL_2("level_2", new Level2Layout(), Integer.MIN_VALUE, 0, 1),
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
     * {@link Level37Layout#TRANSFER_BELOW_Y}) takes you on to Level 7.
     */
    LEVEL_37("level_37", new Level37Layout(), Level37Layout.TRANSFER_BELOW_Y, 0, 1),
    /**
     * "Motion": endless sunny hills, a toy-like town, siren poles playing cartoon music and a castle
     * on a glass mountain. Day/night cycle ({@link Level94Clock}); Smilers come out at night. No
     * Bacteria. Its dark-doored houses lead on to Level 490. Never left by falling: see {@code Level94Rules}.
     */
    LEVEL_94("level_94", new Level94Layout(), Integer.MIN_VALUE, 0, 1),
    /**
     * "Thalassophobia": an endless dark sea under a concrete ceiling. Reached by diving down a deep
     * pool in Level 37 (or through a flooded house in Level 94); the door in the stone platform deep
     * under the sea leads on to Level 39's flooded tunnels (see {@code Level7Rules}). No monsters
     * you can see: the Thing is only ever felt.
     */
    LEVEL_7("level_7", new Level7Layout(), Integer.MIN_VALUE, 0, 1),
    /**
     * "Megalophobia": a foggy night town of light-blue houses, walled in on every side by a colossal
     * glowing mill facade, with flooded marble tunnels below. No Bacteria (cap 0): it has its own
     * creatures. The holes in town drop into the tunnels of this same level, so falling never takes
     * you out (transferBelowY sits far under the tunnel floor). The way on is through a window of the
     * superstructure, to Level 94.
     */
    LEVEL_39("level_39", new Level39Layout(), 20, 0, 1),
    /**
     * "Penance": platforms and fenced stone bridges hung from colossal chains over a sea of storm
     * cloud, at night. No Bacteria (cap 0). Falling never takes you out (Level490Rules sends you
     * back to your last checkpoint); the way on is the leap from the strange structure at the
     * heart of each region, to Level 9. See {@code docs/level-490-penance.md}.
     */
    LEVEL_490("level_490", new Level490Layout(), Integer.MIN_VALUE, 0, 1);

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
     * and 1: floor y40, stairs reach y17), being sucked down the drain (Poolrooms: shaft to y24) or
     * diving down a deep pool (Level 37).
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

    /**
     * Where this floor's main exit leads: the next floor of the one path through all of them
     * (each exit leads naturally into the next place: the drain into more water, the sea floor's
     * door into flooded tunnels...), or null when it leads back to reality.
     */
    @Nullable
    public BackroomsLevel next() {
        return switch (this) {
            case LEVEL_0 -> LEVEL_1;
            case LEVEL_1 -> LEVEL_2;
            case LEVEL_2 -> LEVEL_4;
            case LEVEL_4 -> POOLROOMS;
            case POOLROOMS -> LEVEL_37;
            case LEVEL_37 -> LEVEL_7;
            case LEVEL_7 -> LEVEL_39;
            case LEVEL_39 -> LEVEL_94;
            case LEVEL_94 -> LEVEL_490;
            case LEVEL_490 -> LEVEL_9;
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
