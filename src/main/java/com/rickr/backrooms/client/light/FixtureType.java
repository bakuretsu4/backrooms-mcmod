package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Every block that counts as a light fixture, and what kind of light it gives off.
 *
 * <p>We find the blocks by their registry id (e.g. {@code backrooms:ceiling_tube}) instead of
 * Java fields, so a fixture that doesn't exist (yet) is simply skipped.
 */
public enum FixtureType {
    // id, mount, maxRun, red, green, blue, brightness, radius, hum, flickers
    /** Level 0 ceiling panel: warm yellow pools (radius 13), like the reference footage. */
    FLUORESCENT_LIGHT("fluorescent_light", Mount.CEILING_BLOCK, 3, 1.0f, 0.94f, 0.39f, 1.1f, 13.0f, Hum.FLUORESCENT, true),
    /** Level 1 tube strip hanging just under the concrete ceiling: neutral white (reference #EEECFA), long reach. */
    CEILING_TUBE("ceiling_tube", Mount.HANGING_TUBE, 5, 0.96f, 0.96f, 1.0f, 0.8f, 18.0f, Hum.FLUORESCENT, true),
    /**
     * Level 1 vertical wall tube on the garage pillars and maze walls. Dimmer than the ceiling
     * strips: maze rooms are small, and lit concrete must stay mid-grey (reference #6A646B) or it
     * clips to white and the bloom hazes the whole picture.
     */
    WAREHOUSE_LIGHT("warehouse_light", Mount.WALL, 3, 0.96f, 0.96f, 1.0f, 0.65f, 14.0f, Hum.FLUORESCENT, true),
    /** Ceiling strip, now only in the middle of the Level 0/1 exit stairwells. */
    PIPE_LIGHT("pipe_light", Mount.CEILING_BLOCK, 4, 0.88f, 0.87f, 1.0f, 0.55f, 13.0f, Hum.FLUORESCENT, true),
    /**
     * Level 2 small caged bulb high on the plain corridor wall: cool white, reaching about half a
     * corridor stretch either way so you walk from pool of light into the dark.
     */
    BULKHEAD_LAMP("bulkhead_lamp", Mount.WALL, 1, 0.95f, 0.95f, 1.0f, 1.4f, 15.0f, Hum.FLUORESCENT, true),
    /**
     * Poolrooms atrium skylight: frosted daylight glass in 5x5 panels high above the big pool.
     * Strong and far-reaching (the panels hang 14 blocks above the walkway): the atriums are the
     * dazzling daylight courts of the footage, in contrast to the dim tiled rooms around them.
     * Silent and steady (no hum, no flicker): daylight doesn't buzz.
     */
    POOL_SKYLIGHT("pool_skylight", Mount.CEILING_BLOCK, 5, 0.90f, 0.96f, 1.0f, 2.6f, 28.0f, Hum.NONE, false),
    /** Underwater lamp in a pool wall: a small cyan glow that makes the water read in dark rooms. */
    POOL_LAMP("pool_lamp", Mount.INSET, 1, 0.30f, 0.92f, 1.0f, 0.7f, 8.0f, Hum.NONE, false),
    /** Emergency beacon: a dim red glow, rotating red cones during the alarm. */
    RED_ALARM_LAMP("red_alarm_lamp", Mount.WALL, 1, 1.0f, 0.05f, 0.03f, 0.35f, 7.0f, Hum.NONE, false),
    /** Green exit sign: a small glow so you can spot exits in the dark. */
    EXIT_SIGN("exit_sign", Mount.CUBE, 3, 0.20f, 1.0f, 0.35f, 0.6f, 4.5f, Hum.NONE, false),
    /**
     * Level 9 sodium-vapour streetlamp head (~6.5 blocks above the road): a deep orange pool about
     * 20 blocks across with pitch dark between lamps. Brightness 1.6 keeps lit asphalt and lawn
     * well below white (dark surfaces, saturated orange); pale house siding a few blocks away
     * peaks around 0.6-0.7. Buzzes quietly; takes part in the lamp-wave event.
     */
    SODIUM_LAMP("streetlamp_head", Mount.LAMP_HEAD, 1, 1.0f, 0.60f, 0.26f, 3.0f, 24.0f, Hum.SODIUM, true),
    /**
     * Level 9 mercury-vapour head, about one lamp in six: the cold greenish-white "monochrome"
     * light from the wiki. A little dimmer and shorter-reaching than sodium, and silent (the
     * buzz is a sodium ballast thing; one hum type keeps the street from sounding busy).
     */
    MERCURY_LAMP("streetlamp_head_mercury", Mount.LAMP_HEAD, 1, 0.72f, 0.95f, 0.82f, 2.4f, 22.0f, Hum.NONE, true),
    /** Level 9 porch light by a front door: a small warm glow over the porch steps, steady and silent. */
    PORCH_LIGHT("porch_light", Mount.WALL, 1, 1.0f, 0.78f, 0.50f, 1.3f, 10.0f, Hum.NONE, false),
    /**
     * Safe-room ceiling lamp: a warm incandescent bulb, steady and silent. Battery-backed like the
     * exit signs (see FixturePower): it stays on in blackouts and never flickers.
     */
    SAFE_LAMP("safe_room_lamp", Mount.CEILING_BLOCK, 1, 1.0f, 0.82f, 0.60f, 2.4f, 11.0f, Hum.NONE, false);

    /** Which looping buzz a fixture makes (positional, the nearest few lit ones play). */
    public enum Hum {
        NONE,
        /** The fluorescent tube buzz of Levels 0-2. */
        FLUORESCENT,
        /** The low, quiet ballast hum of an old sodium streetlamp (Level 9). */
        SODIUM
    }

    /**
     * The {@code mode} blockstate property of the Level 9 lamps. LIT shines steadily, FLICKER
     * stutters on its own broken rhythm all the time, DEAD gives no light at all (the block
     * isn't even treated as a fixture then).
     */
    public enum LampMode {
        LIT,
        FLICKER,
        DEAD
    }

    /** How the block hangs, which decides where the light sits and which neighbours merge. */
    public enum Mount {
        /** A full block that IS the ceiling (light shines from its bottom face). */
        CEILING_BLOCK,
        /** A thin tube in the air cell right under the ceiling, with an {@code axis} property. */
        HANGING_TUBE,
        /** A thin lamp in the air cell against a wall; {@code facing} points into the room. */
        WALL,
        /** A full cube set into a wall (exit sign): the light goes into the nearest air cell. */
        CUBE,
        /**
         * A full block set into a wall with the lamp on its {@code facing} side (pool lamps): the
         * light sits just in front of that face. Unlike CUBE this also works under water.
         */
        INSET,
        /**
         * A streetlamp head on the end of its arm, high above the road: the light hangs just
         * under the head's bottom face (in the open air below it), so the pool spreads over the
         * road and the head itself isn't in shadow.
         */
        LAMP_HEAD
    }

    private final String id;
    public final Mount mount;
    /** Longest run of neighbouring blocks merged into one light (1 = never merge). */
    public final int maxRun;
    public final float red;
    public final float green;
    public final float blue;
    public final float brightness;
    public final float radius;
    /** Which hum it plays, if any. */
    public final Hum hum;
    /** Takes part in FLICKER events (and Level 9's lamp wave). */
    public final boolean flickers;

    FixtureType(String id, Mount mount, int maxRun, float red, float green, float blue, float brightness, float radius,
                Hum hum, boolean flickers) {
        this.id = id;
        this.mount = mount;
        this.maxRun = maxRun;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.brightness = brightness;
        this.radius = radius;
        this.hum = hum;
        this.flickers = flickers;
    }

    /** The two kinds of Level 9 streetlamp head (they click with the lamp-flicker sound). */
    public boolean isStreetlamp() {
        return this == SODIUM_LAMP || this == MERCURY_LAMP;
    }

    /**
     * The Poolrooms reuse Level 1's ceiling tubes. White tile reflects more than grey concrete, so
     * this is the knob if lit tile starts clipping to white (bloom haze); at 0.55 the rooms read as
     * murky, so tubes run a little above Level 1 strength there.
     */
    private static final float POOLROOMS_TUBE_SCALE = 1.1f;
    /**
     * Level 2 reuses the tubes in its junction rooms; its pale walls are low and close, so at
     * full strength the wall right beside a tube would clip to white.
     */
    private static final float LEVEL_2_TUBE_SCALE = 0.7f;

    /** How bright this fixture's Veil light is on {@code floor}. */
    public float brightnessOn(@Nullable BackroomsLevel floor) {
        if (this == CEILING_TUBE && floor == BackroomsLevel.POOLROOMS) {
            return brightness * POOLROOMS_TUBE_SCALE;
        }
        if (this == CEILING_TUBE && floor == BackroomsLevel.LEVEL_2) {
            return brightness * LEVEL_2_TUBE_SCALE;
        }
        return brightness;
    }

    /** Block -> fixture type, built the first time it's needed (registries are frozen by then). */
    @Nullable
    private static Map<Block, FixtureType> byBlock;
    /**
     * Block state -> its lamp mode, filled in as states are first seen. Only touched on the
     * client thread (chunk events and client ticks), so a plain map is fine.
     */
    private static final Map<BlockState, LampMode> MODES = new IdentityHashMap<>();

    /** The fixture this block is, or null when it isn't one (or is a dead lamp, which gives no light). */
    @Nullable
    public static FixtureType of(BlockState state) {
        Map<Block, FixtureType> map = byBlock;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (FixtureType type : values()) {
                var block = BuiltInRegistries.BLOCK.getOptional(Backrooms.id(type.id));
                if (block.isPresent()) {
                    map.put(block.get(), type);
                }
            }
            byBlock = map;
        }
        FixtureType type = map.get(state.getBlock());
        if (type != null && modeOf(state) == LampMode.DEAD) {
            return null;
        }
        return type;
    }

    public static boolean isFixture(BlockState state) {
        return of(state) != null;
    }

    /**
     * Reads the lamp's {@code mode} property (lit / flicker / dead). We look it up by NAME instead
     * of importing the block class, so this works with any block that has such a property and
     * every other block simply counts as LIT.
     */
    public static LampMode modeOf(BlockState state) {
        LampMode cached = MODES.get(state);
        if (cached != null) {
            return cached;
        }
        LampMode mode = LampMode.LIT;
        Property<?> property = state.getBlock().getStateDefinition().getProperty("mode");
        if (property != null) {
            Object value = state.getValue(property);
            String name = value instanceof StringRepresentable named ? named.getSerializedName() : value.toString();
            if (name.equalsIgnoreCase("flicker")) {
                mode = LampMode.FLICKER;
            } else if (name.equalsIgnoreCase("dead")) {
                mode = LampMode.DEAD;
            }
        }
        MODES.put(state, mode);
        return mode;
    }
}
