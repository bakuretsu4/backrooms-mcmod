package com.rickr.backrooms.block;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * How a Level 0 tube (the {@code fluorescent_light} troffer or a {@code l0_troffer_strip}) glows.
 *
 * <p><b>Age ({@link #GLOW}).</b> Real office tubes are never all equally bright: they were
 * replaced at different times and fade as they age. The only way to make one tube light the room
 * less than its neighbour WITHOUT Veil is to give it a lower vanilla light level, so each tube
 * block carries its age as a block state property, chosen by the layout through
 * {@code world/layout/Level0Tubes}. The same property also sets the Veil light's strength (see
 * client/light/BackroomsLights) and how bright the lens itself looks, so the lens, the pool of
 * Veil light and the vanilla light under it always agree.
 *
 * <p><b>Colour ({@link #tone}).</b> Each tube also has a colour temperature: most are as made,
 * some yellowed, a few cool. (No green ones: even a faint green read as clearly mint on the lens,
 * and the hall must never look green.) It is a pure
 * function of the tube's position (no world seed), so the client can work it out for the lens
 * tint and for the Veil light colour without any extra block states.
 *
 * <p><b>Stutter.</b> A faulty tube goes dark for a moment when it stutters. Which tubes are
 * faulty, and when, is only known on the client (it depends on the clock, see
 * client/light/FixturePower), so the client installs the answer in {@link #dark}; everywhere
 * else, and on every other level, tubes always glow.
 *
 * <p>Two things change for a dark tube: its faces stop being full-bright (they then take the
 * light of the room like the ceiling tiles around them), and its texture is tinted (the block
 * colour, through the model's tint index) grey, so it reads as switched off. A dying tube is
 * never full-bright: it only glows weakly, so its lens gets a lighter warm grey instead.
 */
public final class Level0TubeGlow {
    /**
     * A tube's age. Each has its own vanilla light level (what lights the room without Veil and
     * the lightmap fill with it), its Veil light strength and its lens tint.
     */
    public enum Glow implements StringRepresentable {
        /** A fairly new tube: the full light the panels were designed for. */
        FULL("full", 12, 1.06f, 0xFFFFFF),
        /** A few years old: a little dimmer, lens a touch greyer. */
        WORN("worn", 11, 0.94f, 0xF2EFE6),
        /** Old: clearly dimmer than its neighbours, lens greyish. */
        TIRED("tired", 10, 0.80f, 0xDCD8CA),
        /**
         * On its way out: a weak glow that slowly swells and fades (client/light/FixturePower),
         * never full-bright. Light 6 still leaves a faint patch on the floor under it.
         */
        DYING("dying", 6, 0.28f, 0xB8B49A);

        private final String name;
        /** Vanilla block light level of the tube block. */
        public final int light;
        /** Multiplier on the Veil light's brightness. */
        public final float veil;
        /** Lens tint (RGB) while it glows. */
        public final int lens;

        Glow(String name, int light, float veil, int lens) {
            this.name = name;
            this.light = light;
            this.veil = veil;
            this.lens = lens;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** The age of a Level 0 tube block. Default (and on every other level): {@link Glow#FULL}. */
    public static final EnumProperty<Glow> GLOW = EnumProperty.create("glow", Glow.class);
    /** {@code Glow.values()} copies the array every call; this copy is made once. */
    public static final Glow[] GLOWS = Glow.values();

    /** Set by the client at startup: is the tube block at this position dark right now (stuttering)? */
    public static volatile Predicate<BlockPos> dark = pos -> false;

    /** Tint of a dark tube's lens: a dull grey, like the lens of a tube that's off. */
    private static final int DARK_TINT = 0x6A6A66;

    // --- Colour temperature ----------------------------------------------------------------------
    /** {@link #tone}: as made (a neutral white; the room's yellow comes from the walls and carpet). */
    public static final int TONE_NEUTRAL = 0;
    /** {@link #tone}: an old tube, yellowed. */
    public static final int TONE_WARM = 1;
    /** {@link #tone}: a cooler tube (a different brand put in at some point). */
    public static final int TONE_COOL = 2;
    /**
     * Out of 256, where each tone ends: neutral 0-151 (~59%), warm to 227 (~30%), cool the rest
     * (~11%).
     */
    private static final int TONE_NEUTRAL_END = 152;
    private static final int TONE_WARM_END = 228;
    /** Colour multipliers per tone (red, green, blue). Small shifts: the hall still reads as one light. */
    private static final float[] TONE_R = {1.00f, 1.00f, 0.94f};
    private static final float[] TONE_G = {1.00f, 0.96f, 0.98f};
    private static final float[] TONE_B = {1.00f, 0.86f, 1.00f};
    /**
     * Tubes come in fixtures every 3 blocks, and a strip's blocks are grouped in threes, so the
     * tone is decided per 3x3-block cell: every block of one fixture gets the same colour.
     */
    private static final int FIXTURE_CELL = 3;

    /** For the block properties: {@code .lightLevel(Level0TubeGlow::lightOf)}. */
    public static int lightOf(BlockState state) {
        return state.hasProperty(GLOW) ? state.getValue(GLOW).light : Glow.FULL.light;
    }

    /** The tube's age (FULL for any block without the property). */
    public static Glow glowOf(BlockState state) {
        return state.hasProperty(GLOW) ? state.getValue(GLOW) : Glow.FULL;
    }

    /** For the block properties: {@code .emissiveRendering(Level0TubeGlow::emissive)}. */
    public static boolean emissive(BlockState state, BlockGetter level, BlockPos pos) {
        return glowOf(state) != Glow.DYING && !dark.test(pos);
    }

    /**
     * The colour temperature of the tube at block (x, z): one of the TONE_* values. A pure hash of
     * the fixture cell, with no world seed, so client and lens agree everywhere.
     */
    public static int tone(int x, int z) {
        long h = (long) Math.floorDiv(x, FIXTURE_CELL) * 0x632BE59BD9B4E019L
                ^ (long) Math.floorDiv(z, FIXTURE_CELL) * 0x9E3779B97F4A7C15L ^ 0x6C8E9CF570932BD5L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        int roll = (int) ((h ^ (h >>> 31)) & 0xFF);
        if (roll < TONE_NEUTRAL_END) {
            return TONE_NEUTRAL;
        }
        return roll < TONE_WARM_END ? TONE_WARM : TONE_COOL;
    }

    public static float toneRed(int tone) {
        return TONE_R[tone];
    }

    public static float toneGreen(int tone) {
        return TONE_G[tone];
    }

    public static float toneBlue(int tone) {
        return TONE_B[tone];
    }

    /** The block colour of the tube's tinted faces (see the class comment). */
    public static int tint(BlockState state, @Nullable BlockPos pos) {
        Glow glow = glowOf(state);
        if (pos != null && glow != Glow.DYING && dark.test(pos)) {
            return DARK_TINT;
        }
        int lens = glow.lens;
        if (pos == null) {
            return lens;
        }
        // The lens shows the tube's colour temperature too (the only per-tube colour without Veil).
        int tone = tone(pos.getX(), pos.getZ());
        int r = Math.round(((lens >> 16) & 0xFF) * TONE_R[tone]);
        int g = Math.round(((lens >> 8) & 0xFF) * TONE_G[tone]);
        int b = Math.round((lens & 0xFF) * TONE_B[tone]);
        return r << 16 | g << 8 | b;
    }

    private Level0TubeGlow() {}
}
