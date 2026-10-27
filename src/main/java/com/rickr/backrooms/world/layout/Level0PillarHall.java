package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The pillar hall that rearranges itself: Level 0's "Peripheral Shift" ("whenever not directly
 * observed, the layout of the level can warp, stretch, or rearrange itself") in a "Pillar Variation"
 * room (a lattice of pillars; paths behind you shift into asymmetric layouts), both from
 * https://backrooms-wiki.wikidot.com/level-0.
 *
 * <p>A big lit 35x35 hall with three doorways in each wall. Pillars stand on "slots": the regular
 * lattice (every 4 blocks) and the in-between points. Each {@link #ARRANGEMENTS arrangement} says
 * which slots hold a pillar: number 0 is the neat lattice the hall is generated with, the others are
 * lopsided. The server ({@code Level0Anomalies}) now and then picks another arrangement and moves
 * each pillar only while nobody can see it. Two slots are never next to each other (not even
 * diagonally), so whatever the arrangement every spot of the hall stays reachable.
 *
 * <p>Across the middle stands a pale wall with archways: the wiki's "Arch Variation", the most
 * stable of the rooms, which doesn't shift. It is the one landmark you can steer by. The carpet
 * along it is wetter, as the wiki says. Under Level 0's low ceiling each archway is 3 wide: its
 * middle is open to the ceiling, and its two top corners are rounded off, so only the middle
 * counts as walkable (the corners leave less than 3 blocks of headroom).
 */
public final class Level0PillarHall {
    public static final int BOX = 35;
    public static final int MOAT = 2;
    /** How many arrangements the pillars move between. */
    public static final int ARRANGEMENTS = 3;
    /** Interior size (inside the outer walls). */
    private static final int INNER = BOX - 2;
    private static final int MIDDLE = INNER / 2;
    /** The arch wall covers along = ARCH_FROM..ARCH_TO of the middle line. */
    private static final int ARCH_FROM = 8;
    private static final int ARCH_TO = 24;

    // What one column is.
    private static final int MOAT_FLOOR = 0;
    private static final int WALL = 1;
    private static final int DOORWAY = 2;
    private static final int FLOOR = 3;
    private static final int SLOT = 4;
    private static final int ARCH_PIER = 5;
    private static final int ARCH_SHOULDER_LOW = 6;  // first column of an archway (next to the lower pier)
    private static final int ARCH_SHOULDER_HIGH = 7; // last column of an archway
    private static final int ARCH_CENTRE = 8;

    private Level0PillarHall() {}

    /** Interior coordinates (p, q) of a world column; -1 .. INNER for the walls. */
    private static int p(AnomalySite site, int x) {
        return x - site.minX() - MOAT - 1;
    }

    private static int q(AnomalySite site, int z) {
        return z - site.minZ() - MOAT - 1;
    }

    private static int role(AnomalySite site, int p, int q) {
        if (p < -1 || p > INNER || q < -1 || q > INNER) {
            return MOAT_FLOOR;
        }
        if (p == -1 || p == INNER || q == -1 || q == INNER) {
            int along = p == -1 || p == INNER ? q : p;
            boolean door = along >= 0 && along < INNER && (Math.abs(along - 7) <= 1 || Math.abs(along - MIDDLE) <= 1 || Math.abs(along - 25) <= 1);
            return door ? DOORWAY : WALL;
        }
        int arch = archRole(site, p, q);
        if (arch != FLOOR) {
            return arch;
        }
        return isSlot(site, p, q) ? SLOT : FLOOR;
    }

    /** The arch wall's part at (p, q), or FLOOR when not on it. */
    private static int archRole(AnomalySite site, int p, int q) {
        boolean eastWest = site.facing() == 0;
        int along = eastWest ? p : q;
        int across = eastWest ? q : p;
        if (across != MIDDLE || along < ARCH_FROM || along > ARCH_TO) {
            return FLOOR;
        }
        return switch ((along - ARCH_FROM) % 4) {
            case 0 -> ARCH_PIER;
            case 1 -> ARCH_SHOULDER_LOW;
            case 3 -> ARCH_SHOULDER_HIGH;
            default -> ARCH_CENTRE;
        };
    }

    /** Lattice points (p, q = 2 mod 4) and in-between points (0 mod 4), away from the arch wall and the walls. */
    private static boolean isSlot(AnomalySite site, int p, int q) {
        boolean lattice = p % 4 == 2 && q % 4 == 2;
        boolean between = p % 4 == 0 && q % 4 == 0 && p >= 4 && p <= INNER - 5 && q >= 4 && q <= INNER - 5;
        if (!lattice && !between) {
            return false;
        }
        boolean eastWest = site.facing() == 0;
        int along = eastWest ? p : q;
        int across = eastWest ? q : p;
        // Keep the arch wall (and a strip either side) clear of pillars.
        return !(Math.abs(across - MIDDLE) <= 2 && along >= ARCH_FROM - 2 && along <= ARCH_TO + 2);
    }

    /** True if arrangement {@code n} puts a pillar on slot (p, q). */
    private static boolean pillarIn(AnomalySite site, int n, int p, int q) {
        boolean lattice = p % 4 == 2;
        if (n == 0) {
            return lattice; // the neat lattice the hall starts with
        }
        double r = Hash.unit(site.salt(), p, q, 40 + n);
        return lattice ? r < 0.55 : r < 0.4;
    }

    /** One pillar slot: its world column and which arrangements fill it. */
    public record Slot(int x, int z, boolean[] filled) {}

    /** Every pillar slot of the hall, with the arrangements that fill it (computed once per site by the server). */
    public static List<Slot> slots(AnomalySite site) {
        List<Slot> out = new ArrayList<>();
        for (int p = 0; p < INNER; p++) {
            for (int q = 0; q < INNER; q++) {
                if (role(site, p, q) != SLOT) {
                    continue;
                }
                boolean[] filled = new boolean[ARRANGEMENTS];
                for (int n = 0; n < ARRANGEMENTS; n++) {
                    filled[n] = pillarIn(site, n, p, q);
                }
                out.add(new Slot(site.minX() + MOAT + 1 + p, site.minZ() + MOAT + 1 + q, filled));
            }
        }
        return out;
    }

    /** True inside the hall's walls (where the shifting happens). */
    public static boolean isInside(AnomalySite site, int x, int z) {
        int p = p(site, x);
        int q = q(site, z);
        return p >= 0 && p < INNER && q >= 0 && q < INNER;
    }

    /** As generated (arrangement 0). Pillars can move, so slots never count as walkable. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        int p = p(site, x);
        int q = q(site, z);
        int r = role(site, p, q);
        return r == WALL || r == ARCH_PIER || (r == SLOT && pillarIn(site, 0, p, q));
    }

    static boolean isWalkable(AnomalySite site, int x, int z) {
        int r = role(site, p(site, x), q(site, z));
        return r != WALL && r != ARCH_PIER && r != SLOT && r != ARCH_SHOULDER_LOW && r != ARCH_SHOULDER_HIGH;
    }

    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int p = p(site, x);
        int q = q(site, z);
        BlockState carpet = ModBlocks.LOBBY_CARPET.get().defaultBlockState();
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        BlockState baseboard = ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState();
        BlockState arch = ModBlocks.ARCH_WALL.get().defaultBlockState();
        out.set(floor, carpet);
        out.set(ceiling, ModBlocks.CEILING_TILE.get().defaultBlockState());
        boolean eastWest = site.facing() == 0;
        switch (role(site, p, q)) {
            case WALL, SLOT -> {
                if (role(site, p, q) == SLOT && !pillarIn(site, 0, p, q)) {
                    break; // an empty slot: plain floor for now
                }
                out.set(floor + 1, baseboard);
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, wallpaper);
                }
            }
            case DOORWAY -> {
                // Full height: the ceiling just carries on (a lintel would leave too little headroom).
            }
            case ARCH_PIER -> {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, arch);
                }
            }
            case ARCH_SHOULDER_LOW, ARCH_SHOULDER_HIGH -> {
                // Upside-down stairs round off the top corners of the archway, right under the
                // ceiling. Their lower half sits on the side of the pier next to them, and the pale
                // arch carries on across the ceiling above the opening.
                boolean low = role(site, p, q) == ARCH_SHOULDER_LOW;
                Direction towardPier = eastWest ? (low ? Direction.WEST : Direction.EAST) : (low ? Direction.NORTH : Direction.SOUTH);
                out.set(ceiling - 1, ModBlocks.ARCH_WALL_STAIRS.get().defaultBlockState()
                        .setValue(StairBlock.FACING, towardPier)
                        .setValue(StairBlock.HALF, Half.TOP));
                out.set(ceiling, arch);
                wetCarpet(floor, out);
            }
            case ARCH_CENTRE -> {
                out.set(ceiling, arch);
                wetCarpet(floor, out);
            }
            case FLOOR -> {
                int along = eastWest ? p : q;
                int across = eastWest ? q : p;
                if (Math.abs(across - MIDDLE) == 1 && along >= ARCH_FROM - 1 && along <= ARCH_TO + 1) {
                    wetCarpet(floor, out); // "the carpet is deeper and wetter" by the arches
                }
                // A regular grid of lit panels: the hall is bright, so there's nothing to hide behind.
                if (p % 4 == 0 && (q % 8 == 1 || q % 8 == 2)) {
                    out.set(ceiling, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState());
                }
            }
            default -> {
                // MOAT_FLOOR: plain carpet.
            }
        }
    }

    private static void wetCarpet(int floor, LevelLayout.Column out) {
        out.set(floor + 1, ModBlocks.CARPET_FLUID.get().defaultBlockState());
    }
}
