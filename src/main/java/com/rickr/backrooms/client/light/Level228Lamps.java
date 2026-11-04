package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.level228.Level228Ambience;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 228's coloured light panels ({@code backrooms:l228_light}): which colour each one is, how
 * its face is tinted, and the colour of the light around you.
 *
 * <ul>
 *   <li><b>Colour.</b> The block's {@code color} (blue, green or red) picks the fixture type
 *       ({@link FixtureType#L228_BLUE} and friends); its {@code shade} (0..3) picks one of four
 *       shades of that colour, from deep and dim (an indigo, a dark emerald, a crimson) to pale and
 *       bright (ice blue, mint, rose), the "varying shades" of the wiki's lights. Both are read by
 *       name, so this works with whatever block class carries them.</li>
 *   <li><b>Face.</b> The panel's texture is its colour at the palest; the block tint
 *       ({@link #tint}) deepens and dims it to the shade, so the lit face and the Veil light have
 *       the same colour. The tint is plain vertex colour, which shader packs keep: under Iris the
 *       coloured faces are what carries the colour (the pack's block light is uncoloured).</li>
 *   <li><b>The light around you</b> ({@link #glowRed()} ...): the colours of the lit panels near
 *       you mixed by how much each lights your spot, eased. The lightmap fill, the fog and the
 *       camcorder's grade can only have one colour for the whole picture, so they take this one
 *       (client/level228/Level228Atmosphere): in a blue sector the dark between the lights is a
 *       blue dark, at a junction of blue and red it shifts as you walk. It is computed from the
 *       fixture list, which is kept with or without Veil, so it works under Iris too. Lights in
 *       the mirrors' image volumes count like real ones (they are the reflected light).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Lamps {
    /** The palest, brightest shade (and the default for a block without a {@code shade}). */
    public static final int PALEST = 3;

    // The light's colour per type (blue, green, red) and shade (0 deep .. 3 pale), brightest channel 1.
    // Never a pure primary: every shade keeps a quarter or more in its weak channels, so a lit
    // corridor shows its surfaces and your reflection instead of a single-channel glow on black
    // (the first, purer colours read as near-black on camera).
    private static final float[][][] COLOUR = {
            {{0.36f, 0.30f, 1.0f}, {0.26f, 0.42f, 1.0f}, {0.30f, 0.58f, 1.0f}, {0.55f, 0.75f, 1.0f}},
            {{0.25f, 1.0f, 0.42f}, {0.32f, 1.0f, 0.36f}, {0.28f, 1.0f, 0.66f}, {0.60f, 1.0f, 0.72f}},
            {{1.0f, 0.24f, 0.32f}, {1.0f, 0.28f, 0.24f}, {1.0f, 0.38f, 0.28f}, {1.0f, 0.55f, 0.55f}},
    };
    /** How strong each shade shines (Veil brightness and face), deep to pale. */
    private static final float[] STRENGTH = {0.80f, 0.87f, 0.94f, 1.0f};
    /**
     * How much of the face's own near-white core survives the tint, deep to pale: a deep shade
     * tints the whole face in its colour, the palest leaves the texture almost as drawn.
     */
    private static final float[] FACE_WHITE = {0.0f, 0.25f, 0.5f, 0.8f};

    /** Panels nearer than this count for the light around you (blocks). */
    private static final double GLOW_RANGE = 14.0;
    /** How much light (sum of strength / (1 + distance squared)) counts as "fully lit". */
    private static final double FULLY_LIT = 0.12;
    /** Easing per tick toward the measured colour, so it shifts smoothly as you walk. */
    private static final float GLOW_EASE = 0.12f;

    private static float glowR = 0.20f;
    private static float glowG = 0.42f;
    private static float glowB = 1.0f;
    private static float lit;

    private Level228Lamps() {}

    /** True for the three panel types (not the exit mirror). */
    public static boolean isPanel(FixtureType type) {
        return type == FixtureType.L228_BLUE || type == FixtureType.L228_GREEN || type == FixtureType.L228_RED;
    }

    /** The panel type for a {@code backrooms:l228_light} state, by its {@code color}. */
    static FixtureType typeOf(BlockState state) {
        String colour = valueName(state, "color");
        if ("green".equals(colour)) {
            return FixtureType.L228_GREEN;
        }
        if ("red".equals(colour)) {
            return FixtureType.L228_RED;
        }
        return FixtureType.L228_BLUE;
    }

    /** The panel's {@code shade}, 0..3 ({@link #PALEST} if it has none). */
    public static int shadeOf(@Nullable BlockState state) {
        if (state == null) {
            return PALEST;
        }
        Property<?> property = state.getBlock().getStateDefinition().getProperty("shade");
        if (property == null || !(state.getValue(property) instanceof Integer shade)) {
            return PALEST;
        }
        return Math.max(0, Math.min(PALEST, shade));
    }

    /** A property's value by name, as its serialized name (null when the state has no such property). */
    @Nullable
    private static String valueName(BlockState state, String name) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            return null;
        }
        Object value = state.getValue(property);
        return value instanceof StringRepresentable named ? named.getSerializedName() : value.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static int index(FixtureType type) {
        return type == FixtureType.L228_GREEN ? 1 : type == FixtureType.L228_RED ? 2 : 0;
    }

    /*
     * The Veil light's colour (VeilFixtures only): the shade's colour with its weak channels pushed
     * further down (raised to VEIL_SATURATION), because overlapping coloured Veil lights add up
     * toward white (round 2 read as washed-out pink). The face tint and the colour of the light
     * around you (fog, grade, the lightmap: what a shader pack shows) keep the softer COLOUR.
     */
    private static final double VEIL_SATURATION = 1.8;

    public static float red(FixtureType type, int shade) {
        return veil(COLOUR[index(type)][clampShade(shade)][0]);
    }

    public static float green(FixtureType type, int shade) {
        return veil(COLOUR[index(type)][clampShade(shade)][1]);
    }

    public static float blue(FixtureType type, int shade) {
        return veil(COLOUR[index(type)][clampShade(shade)][2]);
    }

    private static float veil(float channel) {
        return (float) Math.pow(channel, VEIL_SATURATION);
    }

    private static int clampShade(int shade) {
        return Math.max(0, Math.min(PALEST, shade));
    }

    /** How strong this light shines for its shade (1 for everything that isn't a Level 228 panel). */
    static float strength(FixtureLight light) {
        return isPanel(light.type) ? STRENGTH[clampShade(light.shade)] : 1.0f;
    }

    /**
     * The panel face's tint (0xRRGGBB): the shade's colour, lifted toward white by how pale it is,
     * times its strength. Called by the chunk builders: only reads the state.
     */
    static int tint(BlockState state) {
        FixtureType type = typeOf(state);
        int shade = shadeOf(state);
        float[] c = COLOUR[index(type)][shade];
        float white = FACE_WHITE[shade];
        float strength = STRENGTH[shade];
        int r = channel((c[0] + (1.0f - c[0]) * white) * strength);
        int g = channel((c[1] + (1.0f - c[1]) * white) * strength);
        int b = channel((c[2] + (1.0f - c[2]) * white) * strength);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(float v) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, v)) * 255.0f);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        // By id, like FixtureType: the panel block belongs to the level's block registry.
        BuiltInRegistries.BLOCK.getOptional(Backrooms.id("l228_light")).ifPresent(block ->
                event.register((state, world, pos, tintIndex) -> tint(state), block));
    }

    /** Level 228's sounds go quiet together when the thing in the mirrors is about to act (0..1 scale). */
    static float quiet() {
        return Level228Ambience.quiet();
    }

    // --- The light around you -----------------------------------------------------------------

    /** The colour of the light around you (brightest channel about 1), eased. */
    public static float glowRed() {
        return glowR;
    }

    public static float glowGreen() {
        return glowG;
    }

    public static float glowBlue() {
        return glowB;
    }

    /** 0..1: how lit your spot is by the panels (0 in the dark between sectors), eased. */
    public static float lit() {
        return lit;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_228) {
            return;
        }
        List<FixtureLight> lights = BackroomsLights.fixtures();
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        double sum = 0.0;
        for (int i = 0; i < lights.size(); i++) {
            FixtureLight light = lights.get(i);
            if (light.distSq > GLOW_RANGE * GLOW_RANGE) {
                break; // nearest first
            }
            if (!isPanel(light.type)) {
                continue;
            }
            // Light falls off with the distance squared; a dim shade counts less.
            double weight = light.level * STRENGTH[clampShade(light.shade)] / (1.0 + light.distSq);
            float[] c = COLOUR[index(light.type)][clampShade(light.shade)];
            r += c[0] * weight;
            g += c[1] * weight;
            b += c[2] * weight;
            sum += weight;
        }
        if (sum > 1.0e-6) {
            // Normalised so the brightest channel is 1: the colour of the mix, not its strength.
            double max = Math.max(r, Math.max(g, b));
            glowR += ((float) (r / max) - glowR) * GLOW_EASE;
            glowG += ((float) (g / max) - glowG) * GLOW_EASE;
            glowB += ((float) (b / max) - glowB) * GLOW_EASE;
        }
        float target = (float) Math.min(1.0, sum / FULLY_LIT);
        lit += (target - lit) * GLOW_EASE;
    }
}
