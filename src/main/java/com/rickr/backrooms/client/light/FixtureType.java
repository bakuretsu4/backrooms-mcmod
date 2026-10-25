package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Every block that counts as a light fixture, and what kind of light it gives off.
 *
 * <p>We find the blocks by their registry id (e.g. {@code backrooms:ceiling_tube}) instead of
 * Java fields, so a fixture that doesn't exist (yet) is simply skipped.
 */
public enum FixtureType {
    // id, mount, maxRun, red, green, blue, brightness, radius, hums, flickers
    /** Level 0 ceiling panel: warm yellow pools (radius 13), like the reference footage. */
    FLUORESCENT_LIGHT("fluorescent_light", Mount.CEILING_BLOCK, 3, 1.0f, 0.94f, 0.39f, 1.1f, 13.0f, true, true),
    /** Level 1 tube strip hanging just under the concrete ceiling: neutral white (reference #EEECFA), long reach. */
    CEILING_TUBE("ceiling_tube", Mount.HANGING_TUBE, 5, 0.96f, 0.96f, 1.0f, 0.8f, 18.0f, true, true),
    /**
     * Level 1 vertical wall tube on the garage pillars and maze walls. Dimmer than the ceiling
     * strips: maze rooms are small, and lit concrete must stay mid-grey (reference #6A646B) or it
     * clips to white and the bloom hazes the whole picture.
     */
    WAREHOUSE_LIGHT("warehouse_light", Mount.WALL, 3, 0.96f, 0.96f, 1.0f, 0.65f, 14.0f, true, true),
    /** Level 2 ceiling strip in the pipe corridors. */
    PIPE_LIGHT("pipe_light", Mount.CEILING_BLOCK, 4, 0.88f, 0.87f, 1.0f, 0.55f, 13.0f, true, true),
    /** Level 2 caged wall lamp: warmer and shorter range than the tubes. */
    BULKHEAD_LAMP("bulkhead_lamp", Mount.WALL, 1, 1.0f, 0.82f, 0.55f, 1.0f, 9.0f, true, true),
    /**
     * Poolrooms atrium skylight: frosted daylight glass in 5x5 panels high above the big pool.
     * Strong and far-reaching (the panels hang 14 blocks above the walkway): the atriums are the
     * dazzling daylight courts of the footage, in contrast to the dim tiled rooms around them.
     * Silent and steady (no hum, no flicker): daylight doesn't buzz.
     */
    POOL_SKYLIGHT("pool_skylight", Mount.CEILING_BLOCK, 5, 0.90f, 0.96f, 1.0f, 2.6f, 28.0f, false, false),
    /** Underwater lamp in a pool wall: a small cyan glow that makes the water read in dark rooms. */
    POOL_LAMP("pool_lamp", Mount.INSET, 1, 0.30f, 0.92f, 1.0f, 0.7f, 8.0f, false, false),
    /** Emergency beacon: a dim red glow, rotating red cones during the alarm. */
    RED_ALARM_LAMP("red_alarm_lamp", Mount.WALL, 1, 1.0f, 0.05f, 0.03f, 0.35f, 7.0f, false, false),
    /** Green exit sign: a small glow so you can spot exits in the dark. */
    EXIT_SIGN("exit_sign", Mount.CUBE, 3, 0.20f, 1.0f, 0.35f, 0.6f, 4.5f, false, false);

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
        INSET
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
    /** Plays the positional fluorescent hum. */
    public final boolean hums;
    /** Takes part in FLICKER events. */
    public final boolean flickers;

    FixtureType(String id, Mount mount, int maxRun, float red, float green, float blue, float brightness, float radius,
                boolean hums, boolean flickers) {
        this.id = id;
        this.mount = mount;
        this.maxRun = maxRun;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.brightness = brightness;
        this.radius = radius;
        this.hums = hums;
        this.flickers = flickers;
    }

    /**
     * The Poolrooms reuse Level 1's ceiling tubes. White tile reflects more than grey concrete, so
     * this is the knob if lit tile starts clipping to white (bloom haze); at 0.55 the rooms read as
     * murky, so tubes run a little above Level 1 strength there.
     */
    private static final float POOLROOMS_TUBE_SCALE = 1.1f;

    /** How bright this fixture's Veil light is on {@code floor}. */
    public float brightnessOn(@Nullable BackroomsLevel floor) {
        if (this == CEILING_TUBE && floor == BackroomsLevel.POOLROOMS) {
            return brightness * POOLROOMS_TUBE_SCALE;
        }
        return brightness;
    }

    /** Block -> fixture type, built the first time it's needed (registries are frozen by then). */
    @Nullable
    private static Map<Block, FixtureType> byBlock;

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
        return map.get(state.getBlock());
    }

    public static boolean isFixture(BlockState state) {
        return of(state) != null;
    }
}
