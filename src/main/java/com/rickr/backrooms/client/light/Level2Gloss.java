package com.rickr.backrooms.client.light;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Which Level 2 surfaces are shiny, and how: the "gloss class" of every block state.
 *
 * <p>Level 2's lamps add a small highlight (a reflection of the lamp) on wet floors, puddles,
 * sweating pipes and bare metal. That happens in the lamp shader
 * (pinwheel/shaders/program/light/level2_point.fsh), which only sees the picture: it needs to know
 * what each block is made of. So when the wall map reads a chunk (Level0WallGrid), it also stores
 * the gloss class of every block, two bits each, and the shader looks it up for the block under
 * the pixel. No screen-space reflection pass: just one extra texture read per lit pixel.
 *
 * <p>The classes:
 * <ul>
 *   <li>{@link #MATTE} (0): no highlight. Dry concrete, paint, rubber, cardboard. The default.</li>
 *   <li>{@link #DAMP} (1): a weak, broad sheen. Damp concrete floors, condensation streaks, painted
 *       pipes, the tide lines at the foot of a wall.</li>
 *   <li>{@link #WET} (2): a tight, bright reflection of the lamp, strongest at a grazing angle (look
 *       down the corridor and the wet patches catch the lamps). Puddles, leaks, ooze, gutters.</li>
 *   <li>{@link #METAL} (3): a medium highlight tinted by the metal's own colour. Bare steel and
 *       copper pipes, flanges, grating, valve wheels.</li>
 * </ul>
 *
 * <p>The table below says which blocks are which. An entry is either a block id ("pipe_floor":
 * every state of that block) or a block id with one property value ("pipe_floor[variant=3]": only
 * the states with that value). The more specific entry wins. Ids without a namespace are ours
 * (backrooms:). Unknown ids and properties are simply skipped, so an entry for a block that isn't
 * there (yet) does no harm.
 */
public final class Level2Gloss {
    public static final byte MATTE = 0;
    public static final byte DAMP = 1;
    public static final byte WET = 2;
    public static final byte METAL = 3;

    // ======================================================================== TABLE
    // Other Level 2 agents: add one line per block (or per variant) here.
    private static final String[][] TABLE = {
            // Floors: damp poured concrete; L2Materials' variant 3 is a wet patch.
            {"pipe_floor", "damp"},
            {"pipe_floor[variant=3]", "wet"},
            {"l2_floor_line", "damp"},
            // Walls: condensation streaks (variant 2) and the wet tide-line base of the kicker.
            {"pipe_wall[variant=2]", "damp"},
            {"l2_wall_base[variant=2]", "damp"},
            // Ceiling: the damp/efflorescence variant.
            {"pipe_ceiling[variant=1]", "damp"},
            // Water and wet muck.
            {"l2_leak_puddle", "wet"},
            {"l2_gutter", "wet"},
            {"l2_drip", "wet"},
            {"l2_black_ooze", "wet"},
            // Grating and fittings.
            {"l2_grating", "metal"},
            {"floor_grate", "metal"},
            {"l2_valve_wheel", "metal"},
            {"pipe_valve", "metal"},
            {"l2_pump", "damp"},
            // Pipes: bare metal shines, insulation is matte, paint sweats a little.
            {"l2_flange", "damp"},
            {"l2_flange[kind=steel]", "metal"},
            {"l2_flange[kind=copper]", "metal"},
            {"l2_flange[kind=conduit]", "metal"},
            {"l2_flange[kind=insulated]", "matte"},
            {"l2_heart_gauge", "metal"},
            {"minecraft:water", "wet"},
            {"l2_chamber_prop[prop=kettle]", "damp"},
            {"l2_chamber_prop[prop=mug]", "damp"},
            {"l2_pipe_end", "metal"},
            {"l2_feed_pipe", "metal"},
            {"l2_beam", "damp"},
            {"l2_valve_lever", "metal"},
            {"l2_cable_tray", "metal"},
            {"l2_pipe", "damp"},
            {"l2_pipe[kind=steel]", "metal"},
            {"l2_pipe[kind=copper]", "metal"},
            {"l2_pipe[kind=conduit]", "metal"},
            {"l2_pipe[kind=insulated]", "matte"},
            {"l2_pipe_elbow", "damp"},
            {"l2_pipe_elbow[kind=steel]", "metal"},
            {"l2_pipe_elbow[kind=copper]", "metal"},
            {"l2_pipe_elbow[kind=conduit]", "metal"},
            {"l2_pipe_elbow[kind=insulated]", "matte"},
            {"l2_pipe_tee", "damp"},
            {"l2_pipe_tee[kind=steel]", "metal"},
            {"l2_pipe_tee[kind=copper]", "metal"},
            {"l2_pipe_tee[kind=conduit]", "metal"},
            {"l2_pipe_tee[kind=insulated]", "matte"},
    };
    // ==================================================================== END TABLE

    /** "id" or "id[prop=value]" -> class, parsed once from {@link #TABLE}. */
    private static final Map<String, Byte> ENTRIES = new HashMap<>();
    /** Block state -> class (+1, so 0 means "not looked at yet"); client thread only. */
    private static final Reference2ByteOpenHashMap<BlockState> CACHE = new Reference2ByteOpenHashMap<>();

    static {
        for (String[] row : TABLE) {
            ENTRIES.put(normalise(row[0]), parse(row[1]));
        }
    }

    private Level2Gloss() {}

    /** The gloss class of this block state (one of MATTE, DAMP, WET, METAL). */
    public static byte classOf(BlockState state) {
        byte known = CACHE.getByte(state);
        if (known != 0) {
            return (byte) (known - 1);
        }
        byte gloss = lookUp(state);
        CACHE.put(state, (byte) (gloss + 1));
        return gloss;
    }

    private static byte lookUp(BlockState state) {
        if (state.isAir()) {
            return MATTE;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String name = id.toString();
        // A property-specific entry beats the plain one.
        for (Property<?> property : state.getProperties()) {
            Byte hit = ENTRIES.get(name + "[" + property.getName() + "=" + valueName(state.getValue(property)) + "]");
            if (hit != null) {
                return hit;
            }
        }
        Byte plain = ENTRIES.get(name);
        return plain == null ? MATTE : plain;
    }

    private static String valueName(Object value) {
        return value instanceof StringRepresentable named ? named.getSerializedName() : String.valueOf(value).toLowerCase();
    }

    /** Adds our namespace to bare ids: "pipe_floor[variant=3]" -> "backrooms:pipe_floor[variant=3]". */
    private static String normalise(String key) {
        int bracket = key.indexOf('[');
        String id = bracket < 0 ? key : key.substring(0, bracket);
        if (!id.contains(":")) {
            key = "backrooms:" + key;
        }
        return key;
    }

    private static byte parse(String name) {
        return switch (name) {
            case "damp" -> DAMP;
            case "wet" -> WET;
            case "metal" -> METAL;
            default -> MATTE;
        };
    }
}
