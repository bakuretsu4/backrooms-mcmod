package com.rickr.backrooms.escape;

import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.SeaDoorBlock;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
import com.rickr.backrooms.world.anomaly.level39.WindowShadow;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import com.rickr.backrooms.world.layout.Level9Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Shared pieces of the escapability tests: arriving on a level exactly as {@link Travel} does it,
 * what each level's exit trigger is, and the search from the one to the other.
 */
final class Escape {
    /** World seeds tried for every level (each level derives its own layout seed from them). */
    static final long[] SEEDS = {1L, 42L, 0x5EEDL, -7L, 123456789L, 0xBACC00L, 2024L, -987654321L, 31337L,
            Long.MAX_VALUE, Long.MIN_VALUE + 1, 777L};

    /**
     * Extra blocks of window around the arrival and the exit it heads for. Level 94 gets more: a
     * third of its exit houses are flooded (the way back to Level 7), and then the nearest dark
     * door is in a neighbouring 400-block exit region.
     */
    private static int margin(BackroomsLevel level) {
        return level == BackroomsLevel.LEVEL_94 ? 520 : 160;
    }
    /** Largest window half-size (keeps memory bounded on the big outdoor levels). */
    private static final int MAX_HALF = 900;

    /** A level's own layout seed for one test seed (any long works; real worlds hash the world seed). */
    static long layoutSeed(long worldSeed, BackroomsLevel level) {
        return new SplittableRandom(worldSeed * 31 + level.ordinal()).nextLong();
    }

    /** Where Travel puts a player sent to {@code target} at (x, z) from {@code from}. */
    record Arrival(BackroomsLevel level, long seed, int x, int z, int feetBlock, double feet, boolean preferred, boolean fallback) {
        String describe() {
            return level.getSerializedName() + " arrival at " + x + " " + String.format("%.1f", feet) + " " + z
                    + (preferred ? " (level's preferred arrival)" : fallback ? " (UNCHECKED FALLBACK: no safe column found)" : "");
        }
    }

    /**
     * Where {@code Travel.sendToQuietly} puts a player sent to {@code target} at (x, z) from
     * {@code from}: {@link Travel#arrival} itself (the level's preferred arrival, else the nearest
     * safe column, kept clear of the exits when coming from another level).
     */
    static Arrival arrive(BackroomsLevel target, long seed, int x, int z, @Nullable BackroomsLevel from) {
        LevelLayout layout = target.layout();
        Travel.Arrival a = Travel.arrival(layout, seed, x, z, from, from != target);
        BlockPos spot = a.spot();
        if (a.exact()) {
            return new Arrival(target, seed, spot.getX(), spot.getZ(), spot.getY(), spot.getY(), true, false);
        }
        double feet = layout.feetY(seed, spot.getX(), spot.getZ());
        return new Arrival(target, seed, spot.getX(), spot.getZ(), (int) Math.floor(feet + 1e-6), feet, false, a.fallback());
    }

    /**
     * The (x, z) a player is sent on to when they use {@code from}'s exit somewhere: exactly what
     * {@code Travel.useExit} passes on, the nearest exit centre of {@code from}'s exit locator.
     */
    static int[] exitCentreOf(BackroomsLevel from, long worldSeed, SplittableRandom random) {
        long seed = layoutSeed(worldSeed, from);
        return from.layout().exits().nearest(seed, random.nextInt(-20_000, 20_000), random.nextInt(-20_000, 20_000));
    }

    /** One level's exit trigger: the blocks that make it (if any) and the test on a player position. */
    record Exit(String description, Predicate<BlockState> blocks, Reach.Goal goal) {}

    static Exit exitOf(BackroomsLevel level) {
        return switch (level) {
            case LEVEL_0, LEVEL_1, LEVEL_4, LEVEL_37 -> {
                int below = level.transferBelowY();
                yield new Exit("feet below y" + below + " (transferBelowY: stairs/pit)", s -> false,
                        (w, x, y, z, fromY) -> y < below);
            }
            // PoolroomsRules: feet in the daylit niche at the top of the exit hall's stair.
            case POOLROOMS -> {
                PoolroomsLayout layout = (PoolroomsLayout) level.layout();
                yield new Exit("feet in the daylit niche at the top of the exit hall's stair", s -> false,
                        (w, x, y, z, fromY) -> layout.isHallExit(w.seed, x, y, z));
            }
            // Right-click the bulkhead: stand next to it (either half).
            case LEVEL_2 -> new Exit("standing next to the exit bulkhead", s -> s.getBlock() instanceof ExitDoorBlock,
                    (w, x, y, z, fromY) -> touchesGoal(w, x, y, z, true));
            // Swim into the door on the platform (it has no collision).
            case LEVEL_7 -> new Exit("inside the sea door block", s -> s.getBlock() instanceof SeaDoorBlock,
                    (w, x, y, z, fromY) -> touchesGoal(w, x, y, z, false));
            case LEVEL_94 -> new Exit("inside a dark-door house's dark (l94_dark_void)", s -> s.is(ModBlocks.L94_DARK_VOID.get()),
                    (w, x, y, z, fromY) -> touchesGoal(w, x, y, z, false));
            case LEVEL_9 -> {
                Level9Layout layout = (Level9Layout) level.layout();
                // Level9Rules: feet below y47 in the middle of an underpass.
                yield new Exit("feet below y47 in an underpass escape zone", s -> false,
                        (w, x, y, z, fromY) -> y < 47 && y >= 0 && layout.isEscapeZone(w.seed, x, z));
            }
            // Only a lit, inhabited ground-storey window can grab you and pull you through: stand
            // within its grab reach (upper-storey Windows just throw glass).
            case LEVEL_39 -> new Exit("within grab reach in front of an inhabited, lit ground-storey window (hostile_windows)", s -> false,
                    (w, x, y, z, fromY) -> y >= Level39Layout.GROUND_Y - 1 && y < Level39Sites.glassBottom(0) + Level39Sites.GLASS_HEIGHT
                            && grabWindowAt(w.layout, w.seed, x, z) != Integer.MIN_VALUE);
            case LEVEL_369 -> new Exit("inside the veil of an arch of vines", s -> s.is(Level369Blocks.VEIL.get()),
                    (w, x, y, z, fromY) -> touchesGoal(w, x, y, z, false));
            // Level228Rules: walk into the accurate one of the final chamber's three mirrors (the
            // exit hallway behind it and its door are checked in ExitTriggerServerTest).
            case LEVEL_228 -> new Exit("pressed against the accurate final-chamber mirror", s -> false,
                    (w, x, y, z, fromY) -> {
                        int z0 = 41 + 3 * com.rickr.backrooms.world.layout.Level228Plan.correctExitMirror(w.seed);
                        return x == 14 && (z == z0 || z == z0 + 1) && y == com.rickr.backrooms.world.layout.Level228Plan.FEET_Y;
                    });
            // Level490Rules: airborne after leaving the collar (fall started at or above LEAP_Y - 2),
            // now below LEAP_Y - 3.
            case LEVEL_490 -> new Exit("leaping off the upper collar (fall from y>=" + (Level490Spire.LEAP_Y - 2) + ")", s -> false,
                    (w, x, y, z, fromY) -> fromY >= Level490Spire.LEAP_Y - 2 && y < Level490Spire.LEAP_Y - 3);
            case LEVEL_227 -> throw new IllegalArgumentException("Level 227 has no exit");
        };
    }

    /**
     * The bay of the lit, inhabited ground-storey window whose Window can grab a player standing in
     * column (x, z) (same reach as WindowShadow.canGrab, from the middle of the column), or
     * MIN_VALUE when none can.
     */
    static int grabWindowAt(LevelLayout layout, long seed, int x, int z) {
        AnomalySite site = layout.anomalyAt(seed, x, z);
        if (site == null || !Level39Sites.HOSTILE_WINDOWS.equals(site.kind())) {
            return Integer.MIN_VALUE;
        }
        double front = Level39Sites.inFront(site, x + 0.5, z + 0.5);
        if (front < -0.5 || front >= WindowShadow.GRAB_RANGE) {
            return Integer.MIN_VALUE;
        }
        double along = Level39Sites.along(site, x + 0.5, z + 0.5);
        int near = Level39Sites.bayAt(site, along);
        for (int bay = Math.max(near - 1, Level39Sites.firstBay(site)); bay <= Math.min(near + 1, Level39Sites.lastBay(site)); bay++) {
            if (Math.abs(along - Level39Sites.bayCentre(site, bay)) < WindowShadow.GRAB_SIDEWAYS
                    && Level39AnomalyPlan.windowLit(seed, site, bay, 0) && Level39AnomalyPlan.windowInhabited(seed, site, bay, 0)) {
                return bay;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The body at feet block y overlaps an exit block, or (reach) one is right next to it. */
    private static boolean touchesGoal(VoxelWorld w, int x, int y, int z, boolean reach) {
        if (y < 0) {
            return false;
        }
        if ((w.flags(x, y, z) & VoxelWorld.GOAL) != 0 || (w.flags(x, y + 1, z) & VoxelWorld.GOAL) != 0) {
            return true;
        }
        if (!reach) {
            return false;
        }
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
            int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
            if (w.inside(nx, nz) && ((w.flags(nx, y, nz) & VoxelWorld.GOAL) != 0 || (w.flags(nx, y + 1, nz) & VoxelWorld.GOAL) != 0)) {
                return true;
            }
        }
        return false;
    }

    /** Outcome of one arrival-to-exit check. */
    record Outcome(Arrival arrival, int[] heading, boolean arrivalSafe, int drop, Reach.Result result, String around) {
        /**
         * Standable arrival and a way to the exit. An unchecked fallback arrival (Travel found no
         * safe column) only fails when it really is unstandable; otherwise it is a warning.
         */
        boolean ok() {
            return arrivalSafe && result.reached();
        }

        String describe() {
            return arrival.describe() + (drop > 0 ? " (drops " + drop + " blocks on arrival)" : "")
                    + "; heading for exit near " + heading[0] + " " + heading[1] + "; "
                    + (arrivalSafe ? "" : "ARRIVAL NOT SAFE (inside a wall, nothing under the feet, or a harmful drop); ")
                    + result.describe() + around;
        }
    }

    /** Levels where a fall never hurts (Level369Rules / Level490Rules cancel fall damage). */
    private static boolean fallsAreHarmless(BackroomsLevel level) {
        return level == BackroomsLevel.LEVEL_369 || level == BackroomsLevel.LEVEL_490;
    }

    /** Builds the window round the arrival and the exit it heads for, and searches it. */
    static Outcome check(Arrival arrival, Exit exit) {
        LevelLayout layout = arrival.level().layout();
        int[] heading = layout.exitPosition(arrival.seed(), arrival.x(), arrival.z());
        int cx = (arrival.x() + heading[0]) >> 1;
        int cz = (arrival.z() + heading[1]) >> 1;
        int half = Math.min(MAX_HALF, Math.max(Math.abs(arrival.x() - heading[0]), Math.abs(arrival.z() - heading[1])) / 2
                + margin(arrival.level()));
        VoxelWorld world = new VoxelWorld(layout, arrival.seed(), cx, cz, half, exit.blocks());
        // Some arrivals are meant to be in the air (Level 369: you fall in through a skylight).
        // Let the player fall to whatever holds them, and count how far.
        int feet = arrival.feetBlock();
        int drop = 0;
        boolean body = feet >= 0 && (world.flags(arrival.x(), feet, arrival.z()) & VoxelWorld.PASS) != 0
                && (world.flags(arrival.x(), feet + 1, arrival.z()) & VoxelWorld.PASS) != 0;
        while (body && feet > 0 && Reach.kind(world, arrival.x(), feet, arrival.z()) == 0
                && (world.flags(arrival.x(), feet - 1, arrival.z()) & VoxelWorld.PASS) != 0) {
            feet--;
            drop++;
        }
        // Vanilla fall damage starts after 3 blocks.
        boolean safe = Reach.kind(world, arrival.x(), feet, arrival.z()) != 0 && (drop <= 3 || fallsAreHarmless(arrival.level()));
        Reach.Result result = Reach.search(world, arrival.x(), feet, arrival.z(), heading[0], heading[1], exit.goal(),
                Reach.jumpsOn(arrival.level()));
        String around = "";
        if (!safe) {
            around += "\n    at the arrival: " + blocksAround(world, arrival.x(), arrival.feetBlock(), arrival.z());
        }
        if (!result.reached()) {
            around += "\n    where the search got stuck: " + blocksAround(world, result.nearX(), result.nearY(), result.nearZ());
        }
        return new Outcome(arrival, heading, safe, drop, result, around);
    }

    /** The blocks from just under the feet to above the head in a column and its four neighbours. */
    static String blocksAround(VoxelWorld world, int x, int y, int z) {
        StringBuilder out = new StringBuilder();
        String[] names = {"here", "+x", "-x", "+z", "-z"};
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < offsets.length; i++) {
            out.append(i == 0 ? "" : " | ").append(names[i]).append(':');
            for (int yy = y - 1; yy <= y + 2; yy++) {
                BlockState state = world.state(x + offsets[i][0], z + offsets[i][1], yy);
                out.append(" y").append(yy).append('=').append(state == null ? "air" : shortName(state));
            }
        }
        return out.toString();
    }

    private static String shortName(BlockState state) {
        String s = state.toString();
        return s.startsWith("Block{") ? s.substring(6).replace("}", "") : s;
    }

    private Escape() {}
}
