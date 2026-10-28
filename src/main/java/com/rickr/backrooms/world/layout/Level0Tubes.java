package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level0TubeGlow;
import com.rickr.backrooms.block.Level0TubeGlow.Glow;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which ceiling fixture block a Level 0 light-grid cell gets: a lit troffer of some age, or a
 * dead one. Real office ceilings are never even: tubes were replaced at different times, so some
 * are bright and new, most a little worn, some clearly tired, the odd one dying. And they are
 * replaced a few at a time, so whole stretches of ceiling share an "era": in a freshly re-lamped
 * area most tubes are bright, in a neglected one most are old. That gives the halls large, slow
 * changes of brightness as well as tube-to-tube differences.
 *
 * <p>The age is a block state ({@link Level0TubeGlow#GLOW}) with its own vanilla light level, so
 * it changes the light in the room with or without Veil (see block/Level0TubeGlow).
 *
 * <p>Pure function of (seed, x, z) and allocation-free: it runs for every ceiling block the
 * layout fills. Whether a tube is dead is the layout's decision (the {@code dead} flag); this only
 * picks the age of the tubes that are lit. The faulty tubes that stutter are chosen on the client
 * (client/light/FixturePower) and are always among the lit ones.
 */
public final class Level0Tubes {
    /**
     * Fixtures sit on a 3-block grid and a strip's blocks are grouped in threes, so the age is
     * decided per 3x3-block cell: every block of one fixture gets the same age.
     */
    private static final int FIXTURE_CELL = 3;
    /** A re-lamping era covers this many blocks square (5 x 5 fixtures). */
    private static final int ERA_CELL = 15;
    private static final int SALT_TUBE = 5101;
    private static final int SALT_ERA = 5102;
    /** Share of the ceiling that was re-lamped recently, and that is mid-life; the rest is old. */
    private static final double ERA_FRESH = 0.35;
    private static final double ERA_MID = 0.75;
    /**
     * Per era, where each age ends on the per-tube roll (dying, then tired, then worn; the rest is
     * full). Overall about 45% full, 34% worn, 18% tired and 3% dying.
     */
    private static final double FRESH_DYING = 0.01;
    private static final double FRESH_TIRED = 0.08;
    private static final double FRESH_WORN = 0.33;
    private static final double MID_DYING = 0.03;
    private static final double MID_TIRED = 0.20;
    private static final double MID_WORN = 0.58;
    private static final double OLD_DYING = 0.07;
    private static final double OLD_TIRED = 0.42;
    private static final double OLD_WORN = 0.80;

    /** Slots in {@link #states}: panels by Glow ordinal, then strips by Glow ordinal, then the two dead ones. */
    private static final int STRIPS = Level0TubeGlow.GLOWS.length;
    private static final int PANEL_OFF = 2 * STRIPS;
    private static final int STRIP_OFF = PANEL_OFF + 1;
    /**
     * Every state this can return, looked up once (the blocks exist only after registration, and
     * world generation runs on several threads: building the table twice is harmless).
     */
    private static volatile BlockState[] states;

    /**
     * The ceiling fixture block for a light-grid cell. {@code dead} = the layout already decided
     * this tube is out (dark wing, DEAD_LIGHT roll). {@code corridor} = a troffer-strip run instead
     * of a panel; the strip comes along AXIS X, the caller turns it to the run's axis.
     */
    public static BlockState pick(long seed, int x, int z, boolean dead, boolean corridor) {
        BlockState[] table = states;
        if (table == null) {
            table = build();
        }
        if (dead) {
            return table[corridor ? STRIP_OFF : PANEL_OFF];
        }
        return table[(corridor ? STRIPS : 0) + glow(seed, x, z).ordinal()];
    }

    /** The age of a lit tube at block (x, z). */
    public static Glow glow(long seed, int x, int z) {
        int cx = Math.floorDiv(x, FIXTURE_CELL);
        int cz = Math.floorDiv(z, FIXTURE_CELL);
        double era = Hash.unit(seed, Math.floorDiv(x, ERA_CELL), Math.floorDiv(z, ERA_CELL), SALT_ERA);
        double roll = Hash.unit(seed, cx, cz, SALT_TUBE);
        double dying;
        double tired;
        double worn;
        if (era < ERA_FRESH) {
            dying = FRESH_DYING;
            tired = FRESH_TIRED;
            worn = FRESH_WORN;
        } else if (era < ERA_MID) {
            dying = MID_DYING;
            tired = MID_TIRED;
            worn = MID_WORN;
        } else {
            dying = OLD_DYING;
            tired = OLD_TIRED;
            worn = OLD_WORN;
        }
        if (roll < dying) {
            return Glow.DYING;
        }
        if (roll < tired) {
            return Glow.TIRED;
        }
        return roll < worn ? Glow.WORN : Glow.FULL;
    }

    private static BlockState[] build() {
        Glow[] glows = Level0TubeGlow.GLOWS;
        BlockState[] table = new BlockState[2 * STRIPS + 2];
        BlockState panel = ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState();
        BlockState strip = Level0Blocks.TROFFER_STRIP.get().defaultBlockState();
        for (Glow glow : glows) {
            table[glow.ordinal()] = panel.setValue(Level0TubeGlow.GLOW, glow);
            table[STRIPS + glow.ordinal()] = strip.setValue(Level0TubeGlow.GLOW, glow);
        }
        table[PANEL_OFF] = ModBlocks.FLUORESCENT_LIGHT_OFF.get().defaultBlockState();
        table[STRIP_OFF] = Level0Blocks.TROFFER_STRIP_OFF.get().defaultBlockState();
        states = table;
        return table;
    }

    private Level0Tubes() {}
}
