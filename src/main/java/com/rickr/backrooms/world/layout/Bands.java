package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;

/**
 * Cuts one axis (all X values, or all Z values) into alternating "bands" and "gaps" of random
 * widths, as a pure function of (seed, coordinate).
 *
 * <p>Level 0 uses it twice: bands along X are walls running north-south, bands along Z are walls
 * running east-west, and the gaps between them are rooms.
 *
 * <p>How it stays a pure function: the axis is split into fixed-length "macro" blocks. Each macro
 * block always starts with a band, and its sequence of widths comes only from a hash of the macro
 * index, so any coordinate can find its band by replaying the (few) steps of its own macro block.
 *
 * <p>A query returns a packed {@code long} instead of an object so the chunk generator does not
 * allocate millions of tiny objects. Read it with {@link #isBand}, {@link #index}, {@link #start}
 * and {@link #length}.
 */
final class Bands {
    private static final int MAX_STEPS = 16;

    private final int macro;
    private final int bandMin;
    private final int bandMax;
    private final int gapMin;
    private final int gapMax;
    /** The odd sizes (see the long constructor): a narrow gap, and how wide a wide one may get. */
    private final int narrowGap;
    private final double narrowChance;
    private final int wideMax;
    private final double wideChance;
    /** Narrowest gap of any kind, so a band never eats the room the smallest gap needs. */
    private final int smallestGap;
    private final int salt;

    /**
     * @param macro   macro block length; must fit at least one band plus one gap
     * @param bandMin narrowest band
     * @param bandMax widest band
     * @param gapMin  narrowest gap (the last gap of a macro block may be wider, never narrower)
     * @param gapMax  widest regular gap
     * @param salt    makes different axes/levels independent
     */
    Bands(int macro, int bandMin, int bandMax, int gapMin, int gapMax, int salt) {
        this(macro, bandMin, bandMax, gapMin, gapMax, gapMin, 0.0, gapMax, 0.0, salt);
    }

    /**
     * Like the short constructor, plus odd sizes: with chance {@code narrowChance} a gap is only
     * {@code narrowGap} wide (a tight room or a passage), and with chance {@code wideChance} it is
     * wider than usual, up to {@code wideMax}. Mixing a few of those into the usual sizes is what
     * keeps a grid of rooms from looking like a grid.
     */
    Bands(int macro, int bandMin, int bandMax, int gapMin, int gapMax,
          int narrowGap, double narrowChance, int wideMax, double wideChance, int salt) {
        this.macro = macro;
        this.bandMin = bandMin;
        this.bandMax = bandMax;
        this.gapMin = gapMin;
        this.gapMax = gapMax;
        this.narrowGap = narrowGap;
        this.narrowChance = narrowChance;
        this.wideMax = wideMax;
        this.wideChance = wideChance;
        this.smallestGap = Math.min(gapMin, narrowGap);
        this.salt = salt;
    }

    /** Finds the band or gap containing coordinate {@code v}. */
    long span(long seed, int v) {
        int m = Math.floorDiv(v, macro);
        int local = v - m * macro;
        long h = Hash.of(seed, m, salt, 0x5A17);
        int pos = 0;
        for (int k = 0; k < MAX_STEPS; k++) {
            long hk = Hash.mix(h + (k + 1) * 0x9E3779B97F4A7C15L);
            // Never let a band eat the room needed for the gap after it.
            int band = Math.min(bandMin + Hash.range(hk, bandMax - bandMin + 1), macro - smallestGap - pos);
            int bandEnd = pos + band;
            int gap = gapWidth(Hash.mix(hk ^ 0x2545F4914F6CDD1DL));
            // If another band + gap would not fit after this gap, this gap runs to the end of the block.
            boolean last = bandEnd + gap + bandMin + smallestGap > macro || k == MAX_STEPS - 1;
            int gapEnd = last ? macro : bandEnd + gap;
            if (local < bandEnd) {
                return pack(true, k, pos, band, m);
            }
            if (local < gapEnd) {
                return pack(false, k, bandEnd, gapEnd - bandEnd, m);
            }
            pos = gapEnd;
        }
        throw new IllegalStateException("unreachable: macro block not covered");
    }

    /** The width of a gap from its hash: usually gapMin-gapMax, now and then narrow or wide. */
    private int gapWidth(long h) {
        if (narrowChance > 0 || wideChance > 0) {
            double r = Hash.unit(Hash.mix(h ^ 0x3C6EF372FE94F82BL));
            if (r < narrowChance) {
                return narrowGap;
            }
            if (r < narrowChance + wideChance) {
                return gapMax + 1 + Hash.range(h, wideMax - gapMax);
            }
        }
        return gapMin + Hash.range(h, gapMax - gapMin + 1);
    }

    // Packing: bit 63 = band flag, bits 56-59 = step k, bits 48-55 = local start,
    // bits 40-47 = length, bits 0-31 = macro index m.
    private static long pack(boolean band, int k, int localStart, int length, int m) {
        return (band ? 1L << 63 : 0L) | ((long) k << 56) | ((long) localStart << 48) | ((long) length << 40) | (m & 0xFFFFFFFFL);
    }

    static boolean isBand(long span) {
        return span < 0; // bit 63 set
    }

    /** Unique id of this band (or of this gap; band k and the gap right after it share an id). */
    static int index(long span) {
        int m = (int) span;
        int k = (int) (span >>> 56) & 0xF;
        return m * MAX_STEPS + k;
    }

    /** The macro block a band or gap with this {@link #index} lies in. */
    static int macroOf(int index) {
        return Math.floorDiv(index, MAX_STEPS);
    }

    /** Which band or gap of its macro block (0 = the first) a band or gap with this {@link #index} is. */
    static int stepOf(int index) {
        return Math.floorMod(index, MAX_STEPS);
    }

    /** First world coordinate of the band or gap. */
    int start(long span) {
        int m = (int) span;
        return m * macro + (int) ((span >>> 48) & 0xFF);
    }

    static int length(long span) {
        return (int) ((span >>> 40) & 0xFF);
    }

    /** Last world coordinate + 1. */
    int end(long span) {
        return start(span) + length(span);
    }
}
