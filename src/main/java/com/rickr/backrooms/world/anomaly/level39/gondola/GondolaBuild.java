package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.LevelLayout.Column;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The real blocks of a chairlift line, one world column at a time (called from Level 9's
 * {@code fillField} for columns inside a gondola site). Pure and allocation-free: everything comes
 * from the cached {@link GondolaLine} and plain arithmetic.
 *
 * <p><b>The station</b> (a = -3..8 along the line, s = -4..4 across): a timber deck on the grass;
 * at the back a sheet-steel housing over the big bull wheel the cable turns round; the waiting
 * chair at a = 3 on the right-hand (up) line; a rime-covered sign at the front corner; and at the
 * deck's front edge a short frame tower whose sheaves lead the cable out into the field.
 *
 * <p><b>The towers</b>: a concrete pier, four leaning braces, a lattice mast and a crossarm head
 * with a sheave train at each end. They get taller one after another; Level 9 is only 64 blocks
 * high, so the blocks stop at {@link GondolaLine#BUILD_TOP} and the client draws the rest of a
 * tall tower ({@code client/anomaly/level39/gondola/GondolaLineRenderer}).
 */
public final class GondolaBuild {
    /**
     * Draws column (x, z) if it is part of the line's structures; returns false for the open
     * field in between (which then gets the field's usual grass).
     */
    public static boolean fill(AnomalySite site, int x, int z, Column out) {
        GondolaLine line = GondolaLine.of(site);
        int a = line.localA(x, z);
        int s = line.localS(x, z);
        if (a >= GondolaLine.DECK_A0 && a <= GondolaLine.FRAME_A && Math.abs(s) <= GondolaLine.DECK_HALF) {
            station(line, a, s, out);
            return true;
        }
        return tower(line, a, s, out);
    }

    // ==========================================================================================
    // The station
    // ==========================================================================================

    private static void station(GondolaLine line, int a, int s, Column out) {
        int deckY = GondolaLine.DECK_Y;
        int wheelY = GondolaLine.STATION_CABLE_Y - GondolaLine.CABLE_ABOVE_HEAD; // the cable runs on the wheel's rim
        int hoodY = GondolaLine.STATION_CABLE_Y + 1;
        BlockState hood = GondolaRegistry.HOOD.get().defaultBlockState();

        // The back wall of the housing: from the deck up to its roof.
        if (a == GondolaLine.DECK_A0 && Math.abs(s) <= 2) {
            for (int y = deckY; y <= hoodY; y++) {
                out.set(y, hood);
            }
            return;
        }
        // Everything else stands on the deck.
        out.set(deckY, GondolaRegistry.DECK.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        // The housing's roof over the bull wheel (the cable runs in under it).
        if (a >= GondolaLine.DECK_A0 + 1 && a <= GondolaLine.WHEEL_A + 1 && Math.abs(s) <= 2) {
            out.set(hoodY, hood);
        }
        if (a == GondolaLine.WHEEL_A && s == 0) {
            // The bull wheel on its shaft, on a concrete pier.
            out.set(deckY, GondolaRegistry.FOOTING.get().defaultBlockState());
            for (int y = deckY + 1; y < wheelY; y++) {
                out.set(y, GondolaRegistry.LATTICE.get().defaultBlockState());
            }
            out.set(wheelY, GondolaRegistry.BULLWHEEL.get().defaultBlockState());
            return;
        }
        if (a == GondolaLine.CHAIR_A && s == GondolaLine.CHAIR_S) {
            out.set(GondolaLine.CHAIR_Y, GondolaRegistry.CHAIR.get().defaultBlockState()
                    .setValue(GondolaChairBlock.FACING, line.dir));
            return;
        }
        if (a == GondolaLine.FRAME_A - 2 && s == -GondolaLine.DECK_HALF) {
            // The sign, read from the side you walk up on.
            out.set(GondolaLine.CHAIR_Y, GondolaRegistry.SIGN.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, line.right.getOpposite()));
            return;
        }
        if (a == GondolaLine.FRAME_A) {
            // The frame tower at the front edge: a short mast whose sheaves lead the cable out.
            if (s == 0) {
                out.set(deckY, GondolaRegistry.FOOTING.get().defaultBlockState());
                mast(line, 0, deckY + 1, out);
            } else if (Math.abs(s) == 1) {
                out.set(GondolaLine.STATION_CABLE_Y - GondolaLine.CABLE_ABOVE_HEAD, sheave(line, s));
            }
        }
    }

    // ==========================================================================================
    // The towers
    // ==========================================================================================

    private static boolean tower(GondolaLine line, int a, int s, Column out) {
        if (Math.abs(s) > 1) {
            return false;
        }
        for (int k = 1; k <= line.towers; k++) {
            int da = a - line.towerA(k);
            if (Math.abs(da) > 1) {
                continue;
            }
            int foot = GondolaLine.DECK_Y;
            if (da == 0 && s == 0) {
                out.set(foot, GondolaRegistry.FOOTING.get().defaultBlockState());
                mast(line, k, foot + 1, out);
            } else if (da == 0 || s == 0) {
                // A brace leaning in from each side; FACING points at the mast.
                Direction toMast = da == 0 ? (s > 0 ? line.right.getOpposite() : line.right)
                        : (da > 0 ? line.dir.getOpposite() : line.dir);
                out.set(foot, GondolaRegistry.LEG.get().defaultBlockState().setValue(WallMountBlock.FACING, toMast));
                int head = line.towerHeadY(k);
                if (da == 0 && head <= GondolaLine.BUILD_TOP) {
                    out.set(head, sheave(line, s));
                }
            }
            return true; // the corners stay bare: frozen, trampled ground round the pier
        }
        return false;
    }

    /** Lattice from {@code fromY} up to tower k's head (the crossarm), as far as the world is tall. */
    private static void mast(GondolaLine line, int k, int fromY, Column out) {
        int head = line.towerHeadY(k);
        BlockState lattice = GondolaRegistry.LATTICE.get().defaultBlockState();
        int top = Math.min(head - 1, GondolaLine.BUILD_TOP);
        for (int y = fromY; y <= top; y++) {
            out.set(y, lattice);
        }
        if (head <= GondolaLine.BUILD_TOP) {
            out.set(head, crossarm(line));
        }
    }

    /** The head block of every tower: crossarm beam across the line. */
    public static BlockState crossarm(GondolaLine line) {
        return GondolaRegistry.CROSSARM.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, line.right.getAxis());
    }

    /** The sheave train on the side {@code s} (+1 right / -1 left) of a crossarm. */
    public static BlockState sheave(GondolaLine line, int s) {
        return GondolaRegistry.SHEAVE.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, s > 0 ? line.right : line.right.getOpposite());
    }

    private GondolaBuild() {}
}
