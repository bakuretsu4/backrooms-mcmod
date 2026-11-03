package com.rickr.backrooms.world;

import com.mojang.serialization.Codec;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.layout.Level1Layout;
import com.rickr.backrooms.world.layout.Level2Layout;
import com.rickr.backrooms.world.layout.Level369Layout;
import com.rickr.backrooms.world.layout.Level37Layout;
import com.rickr.backrooms.world.layout.Level4Layout;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level94Layout;
import com.rickr.backrooms.world.layout.Level7Layout;
import com.rickr.backrooms.world.layout.Level227Layout;
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
 * <p>One path through every floor ({@link #next()}), in the order of the wiki's numbers:
 * Overworld --noclip--> Level 0 --stairwell--> Level 1 --stairwell--> Level 2 --exit door-->
 * Level 4 --emergency stairwell--> Level 7 --the door on the sea floor--> Level 9 --the
 * underpass--> Poolrooms --the exit hall--> Level 37 --a deep pool--> Level 39's flooded tunnels
 * --a superstructure window--> Level 94 --a dark-doored house--> Level 369 --the arch of
 * vines--> Level 490 --the leap from the structure--> Overworld. Every move between floors
 * datamoshes ({@link Travel}); only the last one, back to reality, stops the tape.
 *
 * <p>Rare side ways off the line. The rule for them: the level you land in always has a way back
 * to the level you left (going back down the line needs nothing: you walk it forward again).
 * Ahead: the Poolrooms' sunlit archway to Level 39's tunnels (back: a dead end of the tunnels
 * that glows with the same daylight); a clay door on a Level 9 house to Level 94, and the gondola
 * in Level 9's Crimson Field to Level 39's sky (back: a rare clay front door in Level 94 and in
 * Level 39's town, both opening onto Level 9; see {@link ClayDoors}). Back: Level 94's flooded
 * houses down to Level 7 and the light behind its castle's throne to Level 1; Level 37's void
 * and dark openings to Level 0 and the Poolrooms; Level 9's foxholes to Level 2 and its gloom to
 * Level 0. Level 227 is off the line: you are only dragged in (from Level 9 and Level 94), and it
 * only drops you where the line still leads back to those, up to Level 94 ({@link Level227Noclip}).
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
    /**
     * No monsters here (cap 0): the danger is the water itself, which makes you hallucinate. Never
     * left by falling (its exit hall's pool lies at the bottom of the world): you climb the hall's
     * stair into the daylight at the top ({@code PoolroomsRules}).
     */
    POOLROOMS("poolrooms", new PoolroomsLayout(), Integer.MIN_VALUE, 0, 1),
    /**
     * The Suburbs. No Bacteria (cap 0): the Neighborhood Watch patrols here instead. You leave by
     * walking deep into a highway underpass ({@code Level9Rules}), never by falling.
     */
    LEVEL_9("level_9", new Level9Layout(), Integer.MIN_VALUE, 0, 1),
    /** The Abandoned Office: a safe haven, no monsters. Leave down an emergency stairwell. */
    LEVEL_4("level_4", new Level4Layout(), 30, 0, 1),
    /**
     * "Sublimity": bright, pristine, calm. No monsters. Diving down a deep pool shaft (below
     * {@link Level37Layout#TRANSFER_BELOW_Y}) takes you on to Level 39's flooded tunnels.
     */
    LEVEL_37("level_37", new Level37Layout(), Level37Layout.TRANSFER_BELOW_Y, 0, 1),
    /**
     * "Motion": endless sunny hills, a toy-like town, siren poles playing cartoon music and a castle
     * on a glass mountain. Day/night cycle ({@link Level94Clock}); Smilers come out at night. No
     * Bacteria. Its dark-doored houses lead on to Level 369. Never left by falling: see {@code Level94Rules}.
     */
    LEVEL_94("level_94", new Level94Layout(), Integer.MIN_VALUE, 0, 1),
    /**
     * "Thalassophobia": an endless dark sea under a concrete ceiling. Reached down Level 4's
     * stairwells (or through a flooded house in Level 94); the door in the stone platform deep
     * under the sea leads on to Level 9's suburbs (see {@code Level7Rules}). No monsters that
     * hunt you: the Thing swims past, closer the longer you stay, but never attacks (client/Level7Thing).
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
     * heart of each region: the last exit, back to reality. See {@code docs/level-490-penance.md}.
     */
    LEVEL_490("level_490", new Level490Layout(), Integer.MIN_VALUE, 0, 1),
    /**
     * "Megaflora": a planet-sized tree hanging upside down over a black void, a rainforest on every
     * branch. No Bacteria (cap 0): it has its own flora and fauna. It has no exit: falling off a
     * branch is death (Level227Rules, below Level227Tree.VOID_DEATH_Y, so transferBelowY is never
     * reached), and the only way out is the tree's motion noclipping you to a random level.
     */
    LEVEL_227("level_227", new Level227Layout(), Integer.MIN_VALUE, 0, 1),
    /**
     * "Comely": an endless indoor garden of pastel rooms with flowers for floors. No monsters (cap
     * 0): the level itself makes you forget. You fall in through a skylight from Level 94's
     * dark-doored houses and leave through an arch of vines to Level 490 ({@code Level369Rules});
     * never by falling. See
     * {@code docs/level-369-comely.md}.
     */
    LEVEL_369("level_369", new Level369Layout(), Integer.MIN_VALUE, 0, 1);

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
     * Going below this Y sends you to {@link #next()}: walking down the exit stairwell (Levels 0, 1
     * and 4: floor y40, stairs reach y17) or diving down a deep pool (Level 37). Integer.MIN_VALUE
     * for the floors left some other way (Level 2's exit door, the Poolrooms' hall, ...).
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
     * Where this floor's main exit leads: the next floor of the one path through all of them, in
     * the order of the wiki's level numbers (the Poolrooms, unnumbered, sit between 9 and 37), or
     * null when it leads back to reality.
     */
    @Nullable
    public BackroomsLevel next() {
        return switch (this) {
            case LEVEL_0 -> LEVEL_1;
            case LEVEL_1 -> LEVEL_2;
            case LEVEL_2 -> LEVEL_4;
            case LEVEL_4 -> LEVEL_7;
            case LEVEL_7 -> LEVEL_9;
            case LEVEL_9 -> POOLROOMS;
            case POOLROOMS -> LEVEL_37;
            case LEVEL_37 -> LEVEL_39;
            case LEVEL_39 -> LEVEL_94;
            case LEVEL_94 -> LEVEL_369;
            case LEVEL_369 -> LEVEL_490;
            // Level 227 has no exit of its own (you leave by noclipping to a random level); this is
            // only a fallback should anything ever call useExit there.
            case LEVEL_227 -> LEVEL_0;
            case LEVEL_490 -> null;
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
