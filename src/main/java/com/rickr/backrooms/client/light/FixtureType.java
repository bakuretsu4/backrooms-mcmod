package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
    /**
     * Level 0 ceiling troffer. With Veil this is the real light of the Lobby: a pool under each
     * panel that falls off as (1 - d/12)^3 and is cut block-exact by every wall, post and door
     * jamb (the wall map, client/light/Level0WallGrid), while the vanilla lightmap only adds a
     * weak bounce underneath (ClientLighting). Numbers (lamp 2.5 blocks above the carpet, carpet
     * colour ~0.48, grade exposure 1.18): right under a lone panel the carpet gets 1.9 x 0.50 =
     * 0.94 of the panel colour and reads ~0.53 on camera (~0.63 with the bounce), 5 blocks off
     * to the side ~0.09, and it is dark from ~9 blocks; a wall facing it 3 blocks away reads
     * ~0.6. So lit rooms read as warm pools with dark stretches, corners and doorways between
     * them. A warm white like a real ~3500 K office tube (1.0, 0.96, 0.88). Each tube on Level 0
     * also gets its own strength for its age and its own colour temperature (FixtureLight#tubeGlow,
     * block/Level0TubeGlow: 1.06 full to 0.80 tired). (Outside Level 0 it is used alone; see
     * {@link #LONE_PANEL_BRIGHTNESS}.)
     */
    FLUORESCENT_LIGHT("fluorescent_light", Mount.CEILING_BLOCK, 3, 1.0f, 0.96f, 0.88f, 1.9f, 12.0f, Hum.FLUORESCENT, true),
    /**
     * Level 0 film corridor light strip: a row of these reads as one continuous strip down the
     * middle of a narrow corridor. Runs of 3 blocks make one light. A little brighter than a
     * panel (a narrow corridor has no neighbouring rows of panels) and a little shorter, so a
     * corridor's light stays in the corridor.
     */
    L0_TROFFER_STRIP("l0_troffer_strip", Mount.CEILING_BLOCK, 3, 1.0f, 0.96f, 0.88f, 2.0f, 11.0f, Hum.FLUORESCENT, true),
    /**
     * Level 0 EXIT sign over a door alcove: Kane's green running-man sign. Only a faint green
     * glow on the wall around it (it is a sign, not a lamp), silent and battery-backed like the
     * other exit signs, so it still shows in a blackout.
     */
    L0_EXIT_SIGN("l0_exit_sign", Mount.WALL, 2, 0.30f, 1.0f, 0.45f, 0.30f, 3.5f, Hum.NONE, false),
    /**
     * Level 0 rotating red beacon (the Front Office): the dome glows dimly red, and two red cones
     * sweep round it once a second (two sweeps a second past any spot), see VeilFixtures. On its
     * own battery; the block's lit=false switches it off.
     */
    L0_BEACON("l0_beacon", Mount.WALL, 1, 1.0f, 0.06f, 0.03f, 0.8f, 6.0f, Hum.NONE, false),
    /**
     * Level 0 twin-head emergency lamp: a cold white pool about 7 blocks across under it. lit=true
     * keeps it on (its battery); lit=false is standby, it switches itself on when the power at its
     * spot fails (see FixturePower#levelOf). Silent (L0Audio plays its relay clunk).
     */
    L0_EMERGENCY_LAMP("l0_emergency_lamp", Mount.WALL, 1, 0.90f, 0.95f, 1.0f, 1.9f, 9.0f, Hum.NONE, false),
    /**
     * Level 0 pendant lamp on a chain (the Atrium): a warm incandescent shade hanging in the dark
     * void, its light just under the shade. On the mains, so it dies in outages and blackouts.
     */
    L0_PENDANT("l0_pendant_light", Mount.LAMP_HEAD, 1, 1.0f, 0.84f, 0.62f, 2.4f, 12.0f, Hum.NONE, false),
    /** Level 1 tube strip hanging just under the concrete ceiling: neutral white (reference #EEECFA), long reach. */
    CEILING_TUBE("ceiling_tube", Mount.HANGING_TUBE, 5, 0.96f, 0.96f, 1.0f, 0.8f, 18.0f, Hum.FLUORESCENT, true),
    /**
     * Level 1's hanging fixtures (the halls are 7 high now): a touch brighter and longer reaching
     * than the old strips, so a row of them still pools light on the floor far below.
     */
    L1_HANGING_LIGHT("l1_hanging_light", Mount.HANGING_TUBE, 3, 0.96f, 0.97f, 1.0f, 0.95f, 21.0f, Hum.FLUORESCENT, true),
    /**
     * Level 1 vertical wall tube on the garage pillars and maze walls. Dimmer than the ceiling
     * strips: maze rooms are small, and lit concrete must stay mid-grey (reference #6A646B) or it
     * clips to white and the bloom hazes the whole picture.
     */
    WAREHOUSE_LIGHT("warehouse_light", Mount.WALL, 3, 0.96f, 0.96f, 1.0f, 0.65f, 14.0f, Hum.FLUORESCENT, true),
    /** Ceiling strip, now only in the middle of the Level 0/1 exit stairwells. */
    PIPE_LIGHT("pipe_light", Mount.CEILING_BLOCK, 4, 0.88f, 0.87f, 1.0f, 0.55f, 13.0f, Hum.FLUORESCENT, true),
    /**
     * Level 2 caged bulkhead lamp, hung on a pier in the pipe chase (floor + 3, on the shoulder
     * beside the lane) every 6 blocks, alternating sides. Warm sodium-ish amber-white (the bulbs
     * are old and run hot). The lamps are close together and low, so each one only needs a short
     * reach: a small radius makes a warm patch of floor and wall at the lamp's foot and lets the
     * far side of the corridor drop off, so looking down the straight line you see a zig-zag of
     * pools receding into the distance instead of one evenly lit tube. Veil's light falls off
     * steeply ((1 - distance / radius)^3); at this height the lane under a lamp sits around 0.4,
     * the lamp's foot around 0.6, the far shoulder around 0.15. Every bulb has its own age on top
     * (FixturePower#level2BulbLevel, 0.62-1.06, ~0.94 on average, hence 1.8 rather than the old
     * 1.7) and colour (VeilFixtures). On Level 2 its shadows come from the wall map, with gloss
     * highlights on wet and metal surfaces (pinwheel light/level2_point).
     */
    BULKHEAD_LAMP("bulkhead_lamp", Mount.WALL, 1, 1.0f, 0.80f, 0.56f, 1.8f, 8.5f, Hum.FLUORESCENT, true),
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
    SAFE_LAMP("safe_room_lamp", Mount.CEILING_BLOCK, 1, 1.0f, 0.82f, 0.60f, 2.4f, 11.0f, Hum.NONE, false),
    /**
     * Level 4 ceiling troffer (two blocks make one 1x2 panel, so maxRun 2 = one light per panel):
     * cool office white (~4500 K fluorescent) in a pool about 10 blocks across, so lit islands
     * stand out against the dark open floors. Buzzes like every tube; panels in mode=flicker are
     * failing tubes that stutter now and then.
     */
    OFFICE_PANEL("office_panel", Mount.CEILING_BLOCK, 2, 0.93f, 0.96f, 1.0f, 1.0f, 10.0f, Hum.FLUORESCENT, true),
    /**
     * Level 4 vending machine: the glowing front throws a small cold glow into the dark break room.
     * Only the upper half counts (see {@link #of}), so a machine is one light.
     */
    VENDING_MACHINE("vending_machine", Mount.INSET, 1, 0.80f, 0.90f, 1.0f, 0.45f, 6.0f, Hum.NONE, false),
    /** Level 94 odd floor lamp in the toy houses: a small warm pool, steady and silent. */
    L94_LAMP("l94_lamp", Mount.WALL, 1, 1.0f, 0.86f, 0.62f, 1.2f, 9.0f, Hum.NONE, false),
    // Poolrooms anomalies: the Sanguine Reservoir's only lights. A red underwater lamp (the red
    // tint in its water) and the glowing red sprouts on its walkways: small, deep red pools of
    // light with darkness between them. Silent and steady.
    RESERVOIR_LAMP("reservoir_lamp", Mount.INSET, 1, 1.0f, 0.14f, 0.10f, 0.8f, 8.0f, Hum.NONE, false),
    RESERVOIR_SPROUT("reservoir_sprout", Mount.WALL, 1, 1.0f, 0.12f, 0.08f, 0.9f, 7.0f, Hum.NONE, false),
    // Level 94 anomalies: the blinding hall's ceiling, all light panels. Far too bright on purpose
    // (the hall's "blinding levels of light"): cold white pools that clip walls and floor to white
    // and spill out of its doorways. Silent and steady. Long merged runs keep the 9 x 9 ceiling to
    // a handful of lights, so it doesn't eat the whole light budget.
    L94_GLARE_PANEL("l94_glare_panel", Mount.CEILING_BLOCK, 5, 0.96f, 0.98f, 1.0f, 3.2f, 16.0f, Hum.NONE, false),
    // Level 0 anomalies. The Red Rooms' ceiling lamp: darkroom red, enough to fill a room so you can
    // see the walls (a tiny dim pool read as plain black on camera), silent (their "klaxon synth"
    // hum is a sound of the rooms, see client/anomaly/level0). The flickering wall (the way to
    // Level 1): a weak yellow glow that buzzes and stutters like a dying tube, all the time (its
    // block has mode=flicker).
    // (Both retuned for Level 0's Veil-first lighting: the lightmap there is only a weak bounce,
    // so these lamps must light their rooms themselves.)
    RED_ROOM_LIGHT("red_room_light", Mount.CEILING_BLOCK, 1, 1.0f, 0.16f, 0.10f, 2.4f, 11.0f, Hum.NONE, false),
    FLICKERING_WALL("flickering_wall", Mount.CUBE, 3, 1.0f, 0.93f, 0.62f, 0.9f, 7.0f, Hum.FLUORESCENT, true),
    // Level 0 set pieces: the glowing outline of the Front Office's drawn door when it turns real
    // (a warm light from the room behind it), and the safe rooms' checkpoint radio dial (a faint
    // green glow on the shelf). Both battery-backed, steady and silent.
    L0_DOOR_GLOW("l0_door_glow", Mount.WALL, 1, 1.0f, 0.88f, 0.70f, 1.3f, 7.0f, Hum.NONE, false),
    CHECKPOINT_RADIO("checkpoint_radio", Mount.WALL, 1, 0.55f, 1.0f, 0.60f, 0.35f, 4.0f, Hum.NONE, false),
    // Level 4 anomalies: the Window (Entity 2). Warm late-afternoon sunlight spilling from its glass
    // into the cold night office: the one inviting light in the room. Silent and steady.
    L4_CLEAR_WINDOW("l4_clear_window", Mount.INSET, 2, 1.0f, 0.80f, 0.52f, 1.5f, 9.0f, Hum.NONE, false),
    // Level 2 anomalies: the brownout's battery emergency light, the violet glow that came on after
    // the Blackout. Hung on the chase a block under each dead lamp, close to the floor, so it is
    // kept dim: a soft violet pool on the floor and the lane (~0.5 under it), so a brownout reads
    // as violet-lit corridor rather than black (with the warm lamps gone the violet is the only
    // colour there); silent and steady; battery-backed (see FixturePower), so it never takes part
    // in blackouts, flickers or the lamp circuits failing.
    L2_EMERGENCY_LIGHT("l2_emergency_light", Mount.WALL, 1, 0.60f, 0.28f, 1.0f, 1.0f, 10.0f, Hum.NONE, false),
    // Level 2 industrial lighting. The caged mercury lamp of the machine areas (pump bays, pump and
    // boiler rooms): the same cage as the amber bulkhead lamp with a cold blue-green-white mercury
    // bulb, a little stronger and wider (machine bays are bigger), on the same series circuits.
    L2_MERCURY_LAMP("l2_mercury_lamp", Mount.WALL, 1, 0.72f, 0.93f, 0.95f, 1.9f, 9.5f, Hum.FLUORESCENT, true),
    // The battery emergency lamps beside bulkheads, chamber doors and exits (lit=false: standby, on
    // only while its corridor section's circuit is dead or the floor is blacked out; see
    // FixturePower#levelOf). A small, deep red or amber pool on the floor and wall under it: enough
    // to find the door by, never enough to light the corridor. Silent.
    // Both share one block (backrooms:l2_emergency_lamp); its tone=amber states map to the amber
    // type in of(). The amber type's id names no block (on purpose), so no block maps to it directly.
    L2_EMERGENCY_RED("l2_emergency_lamp", Mount.WALL, 1, 1.0f, 0.10f, 0.06f, 1.5f, 8.0f, Hum.NONE, false),
    L2_EMERGENCY_AMBER("l2_emergency_lamp_amber_tone", Mount.WALL, 1, 1.0f, 0.52f, 0.12f, 1.4f, 8.0f, Hum.NONE, false),
    // Level 39. The tunnels' "invisible orbs": a lightless-looking block at the vault apex every 10
    // blocks; cool white with a hint of the water's turquoise, silent and steady. The lightmap
    // already lights the tunnels bright and evenly (client/Level39Atmosphere), so the orbs only
    // add soft pools and shadows on top; stronger would clip the white marble.
    L39_ORB("l39_orb_light", Mount.HANGING_TUBE, 1, 0.90f, 1.0f, 1.0f, 0.5f, 16.0f, Hum.NONE, false),
    // The occasional lit house window (about 1 house in 12): a dim warm lamp somewhere inside.
    L39_LIT_WINDOW("l39_sash_window_lit", Mount.CUBE, 1, 1.0f, 0.74f, 0.45f, 0.7f, 7.0f, Hum.NONE, false),
    // The superstructure's windows near you: not a block (there are far too many window blocks),
    // the lights are placed from the facade grid by client/Level39Lights. Cool white pools along
    // the wall; moderate, so up close the brick, stone and muntins stay visible.
    L39_FACADE_WINDOW("l39_facade_window_light", Mount.CUBE, 1, 0.86f, 0.92f, 1.0f, 0.8f, 14.0f, Hum.NONE, false),
    // The houses' bare bulbs screwed upside down into the popcorn ceilings: about a third still
    // glow, warm and dim (only the lit ones count, see of()). Neighbouring lit bulbs of the dense
    // ceilings merge into one light, so a bulb-studded room doesn't eat the whole light budget.
    L39_BULB("l39_ceiling_bulb", Mount.HANGING_TUBE, 3, 1.0f, 0.80f, 0.56f, 0.55f, 7.0f, Hum.NONE, false),
    // Level 490. The old iron lanterns on the railings that still burn (about half; the dark ones,
    // lit = false, give no light): a small warm flame, the only warmth out on the bridges.
    L490_LANTERN("l490_lantern", Mount.HANGING_TUBE, 1, 1.0f, 0.70f, 0.40f, 0.9f, 9.0f, Hum.NONE, false),
    // Level 227. Resin seeping from the bark of the undersides: a faint warm amber glow on the wood
    // around it (the block is a full cube, so the light sits in the open air next to it).
    // Neighbouring seeps merge, so a long weeping crack is one light.
    L227_RESIN("l227_resin", Mount.CUBE, 3, 1.0f, 0.67f, 0.24f, 0.4f, 5.0f, Hum.NONE, false),
    // GammaPali: the little plants glowing on the branches, a sickly yellow-green (their glow is the
    // toxic gas). The plant faces away from the bark (all six ways); a cluster merges into one light.
    L227_GAMMAPALI("l227_gammapali", Mount.WALL, 3, 0.75f, 1.0f, 0.47f, 0.7f, 6.0f, Hum.NONE, false);

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
     * Level 2 hangs tubes in its side rooms, pump bays and exit bays (a cold fluorescent contrast
     * to the warm corridor lamps), one block under a low ceiling. A little under full strength:
     * the floor under a tube then sits around 0.45, like the floor in a lamp's pool in the
     * corridor, without the wall right beside the tube clipping to white.
     */
    private static final float LEVEL_2_TUBE_SCALE = 0.95f;
    /**
     * Outside Level 0 the Level 0 panel is used alone (Level 7's flooded room has exactly one),
     * with no grid of neighbours and no bright Level 0 fill around it, so there it keeps the old
     * strong, far-reaching pool.
     */
    private static final float LONE_PANEL_BRIGHTNESS = 1.1f;
    private static final float LONE_PANEL_RADIUS = 13.0f;
    /**
     * Level 0's exit stairwell (world/layout/LevelLayout#stairwell): its two wall tubes hang in
     * the shaft just below the landing, the only cool-white light on the floor. Strong and far
     * reaching there, so the landing, the doorway and a wedge of the corridor outside glow cold
     * white and the stairwell reads from down a dark corridor as a beacon that "looks different"
     * from the warm panels.
     */
    private static final float LEVEL0_STAIRWELL_BRIGHTNESS = 2.6f;
    private static final float LEVEL0_STAIRWELL_RADIUS = 16.0f;
    /** The green EXIT sign over the stairwell door: enough to tint the doorway around it green. */
    private static final float LEVEL0_STAIR_SIGN_BRIGHTNESS = 1.0f;
    private static final float LEVEL0_STAIR_SIGN_RADIUS = 6.0f;

    /** Level 0 (null counts as Level 0, like in ClientLighting). */
    private static boolean level0(@Nullable BackroomsLevel floor) {
        return floor == null || floor == BackroomsLevel.LEVEL_0;
    }

    /** How bright this fixture's Veil light is on {@code floor}. */
    public float brightnessOn(@Nullable BackroomsLevel floor) {
        if (this == CEILING_TUBE && floor == BackroomsLevel.POOLROOMS) {
            return brightness * POOLROOMS_TUBE_SCALE;
        }
        if (this == CEILING_TUBE && floor == BackroomsLevel.LEVEL_2) {
            return brightness * LEVEL_2_TUBE_SCALE;
        }
        if (this == FLUORESCENT_LIGHT && !level0(floor)) {
            return LONE_PANEL_BRIGHTNESS;
        }
        if (level0(floor) && this == WAREHOUSE_LIGHT) {
            return LEVEL0_STAIRWELL_BRIGHTNESS;
        }
        if (level0(floor) && this == EXIT_SIGN) {
            return LEVEL0_STAIR_SIGN_BRIGHTNESS;
        }
        return brightness;
    }

    /** How far this fixture's Veil light reaches on {@code floor} (blocks). */
    public float radiusOn(@Nullable BackroomsLevel floor) {
        if (this == FLUORESCENT_LIGHT && !level0(floor)) {
            return LONE_PANEL_RADIUS;
        }
        if (level0(floor) && this == WAREHOUSE_LIGHT) {
            return LEVEL0_STAIRWELL_RADIUS;
        }
        if (level0(floor) && this == EXIT_SIGN) {
            return LEVEL0_STAIR_SIGN_RADIUS;
        }
        return radius;
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
        // Level 39's house bulbs, Level 490's lanterns and a switched-off Level 0 beacon: the dark
        // ones (lit = false) give no light at all.
        if ((type == L39_BULB || type == L490_LANTERN || type == L0_BEACON) && state.hasProperty(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        // Level 2's emergency lamp: its amber states are the amber fixture.
        if (type == L2_EMERGENCY_RED && state.hasProperty(com.rickr.backrooms.block.Level2EmergencyLampBlock.TONE)
                && state.getValue(com.rickr.backrooms.block.Level2EmergencyLampBlock.TONE) == com.rickr.backrooms.block.Level2EmergencyLampBlock.Tone.AMBER) {
            return L2_EMERGENCY_AMBER;
        }
        // A two-tall vending machine glows once, from its upper half.
        if (type == VENDING_MACHINE && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
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
