package com.rickr.backrooms.client.light;

import com.rickr.backrooms.block.Level0TubeGlow;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.world.BackroomsLevel;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The Veil lights for the fixtures picked by {@link BackroomsLights}: one occluded point light
 * per fixture, plus two rotating red cones on each alarm beacon while the alarm runs, plus (Level
 * 9) a faint downward "glow cone" under the nearest few streetlamps so they shine into the fog.
 *
 * <p>Handles are kept per fixture key and only re-created when a fixture appears, moves or
 * Veil threw its renderer away (resource reload), because changing the set of lights makes Veil
 * re-upload all of them. Brightness is only pushed when it changed, for the same reason.
 */
final class VeilFixtures {
    /** Beacon cones: 20 degrees per tick = 400 degrees per second. */
    private static final double BEACON_RAD_PER_MS = Math.toRadians(20.0) / 50.0;
    private static final float BEACON_HALF_ANGLE = 0.45f;
    private static final float BEACON_SOFT_EDGE = 0.3f;
    private static final float BEACON_DISTANCE = 15.0f;
    private static final float BEACON_BRIGHTNESS = 1.1f;
    /**
     * Level 0's rotating beacon (the Front Office) turns once a second: with its two cones back
     * to back, a red sweep passes any spot twice a second. Narrower, longer and brighter cones
     * than the alarm lamps, because it is the only light in a big dark room.
     */
    private static final double L0_BEACON_RAD_PER_MS = Math.PI * 2.0 / 1000.0;
    private static final float L0_BEACON_HALF_ANGLE = 0.35f;
    private static final float L0_BEACON_SOFT_EDGE = 0.22f;
    private static final float L0_BEACON_DISTANCE = 16.0f;
    private static final float L0_BEACON_BRIGHTNESS = 2.6f;

    /**
     * How many of the nearest streetlamps get a glow cone. These count against
     * the quality's light budget (ClientSettings.Quality#maxLights; that many slots are kept free in Level 9).
     * Set to 0 to switch the cones off.
     */
    static final int LAMP_CONES = 4;
    /** Half-angle of the cone (about 50 degrees): a wide bell of light down to the road. */
    private static final float LAMP_CONE_HALF_ANGLE = 0.87f;
    private static final float LAMP_CONE_SOFT_EDGE = 0.5f;
    /** The head hangs ~6.5 blocks over the road; the cone reaches a little past it. */
    private static final float LAMP_CONE_DISTANCE = 9.0f;
    /** Kept low: the point light already makes the pool, the cone mostly adds the haze in the air. */
    private static final float LAMP_CONE_BRIGHTNESS = 0.3f;
    /** How visible the beam is in the air (the flashlight uses 0.3). */
    private static final float LAMP_CONE_INSCATTER = 0.45f;

    /**
     * Level 0 has a troffer every 3 blocks, so the light budget only reaches about 10 blocks out
     * and lights would pop on and off at that edge as you walk. There, each light fades out over
     * the outer part of the picked set instead: from this fraction of the farthest picked light's
     * distance it starts to dim, and the farthest is dark. (The light further out comes from the
     * lightmap fill, see ClientLighting.)
     */
    private static final float LEVEL0_FADE_FROM = 0.6f;
    /**
     * Level 2 is one long straight corridor: you look straight down the row of lamps, so a light
     * switching on at the edge of the light range (a whole new pool appearing 28 blocks ahead as
     * you walk) would be obvious. There every light fades in over the outer half of the range
     * (or of the picked set, when the budget is full), and past it the far pools are carried by
     * the lightmap fill and the glowing bulbs alone, so the corridor dims smoothly into the
     * distance instead of popping. (With its wall-map shadows the range is longer there, see
     * BackroomsLights#LEVEL2_RANGE_SCALE.)
     */
    private static final float LEVEL2_FADE_FROM = 0.5f;

    private static final class Entry {
        FixtureLight light;
        LightRenderHandle<PointLightData> point;
        @Nullable
        LightRenderHandle<SpotLightData> coneA;
        @Nullable
        LightRenderHandle<SpotLightData> coneB;
        /** Level 9 streetlamps only: the downward glow cone (null when this lamp has none). */
        @Nullable
        LightRenderHandle<SpotLightData> lampCone;
        float lastBrightness = -1.0f;
        float lastConeBrightness = -1.0f;
        float lastLampConeBrightness = -1.0f;
        boolean seen;

        Entry(FixtureLight light, LightRenderHandle<PointLightData> point) {
            this.light = light;
            this.point = point;
        }

        boolean valid() {
            return point.isValid() && (coneA == null || coneA.isValid()) && (coneB == null || coneB.isValid())
                    && (lampCone == null || lampCone.isValid());
        }

        void freeCones() {
            if (coneA != null) {
                coneA.free();
                coneA = null;
            }
            if (coneB != null) {
                coneB.free();
                coneB = null;
            }
            lastConeBrightness = -1.0f;
        }

        void freeLampCone() {
            if (lampCone != null) {
                lampCone.free();
                lampCone = null;
            }
            lastLampConeBrightness = -1.0f;
        }

        void free() {
            point.free();
            freeCones();
            freeLampCone();
        }
    }

    private static final Long2ObjectOpenHashMap<Entry> ACTIVE = new Long2ObjectOpenHashMap<>();
    /** Battery dimming applied to every fixture light (a dead camcorder sees almost nothing). */
    private static float scale = 1.0f;

    /**
     * Makes the Veil lights match {@code selected}: adds the new ones, frees the ones no longer
     * picked, and updates brightness. {@code floor} lets a fixture shine differently per level
     * (see {@link FixtureType#brightnessOn}); {@code budget} is how many lights could be picked.
     */
    static void sync(List<FixtureLight> selected, float batteryScale, @Nullable BackroomsLevel floor, int budget) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        LightRenderer lights = renderer.getLightRenderer();
        scale = batteryScale;
        for (Entry entry : ACTIVE.values()) {
            entry.seen = false;
        }
        int conesLeft = floor == BackroomsLevel.LEVEL_9 ? LAMP_CONES : 0;
        // Level 0: how far away the farthest picked light is (for the edge fade). Only when the
        // budget is full: otherwise every light in range is on and nothing pops at the edge.
        // Level 2: always the light range (or the farthest picked light if the budget is full).
        double fadeEnd = 0.0;
        float fadeFrom = LEVEL0_FADE_FROM;
        boolean budgetFull = selected.size() >= budget;
        if ((floor == null || floor == BackroomsLevel.LEVEL_0) && budgetFull) {
            for (FixtureLight light : selected) {
                fadeEnd = Math.max(fadeEnd, Math.sqrt(light.distSq));
            }
        } else if (floor == BackroomsLevel.LEVEL_2) {
            fadeFrom = LEVEL2_FADE_FROM;
            fadeEnd = BackroomsLights.lightRange(floor);
            if (budgetFull) {
                double farthest = 0.0;
                for (FixtureLight light : selected) {
                    farthest = Math.max(farthest, Math.sqrt(light.distSq));
                }
                fadeEnd = Math.min(fadeEnd, farthest);
            }
        }
        for (FixtureLight light : selected) {
            Entry entry = ACTIVE.get(light.key);
            if (entry != null && (!entry.valid() || !entry.light.samePlace(light))) {
                // Veil dropped it (resource reload) or the strip changed shape: start over.
                entry.free();
                ACTIVE.remove(light.key);
                entry = null;
            }
            if (entry == null) {
                entry = new Entry(light, lights.addLight(newPoint(light, floor)));
                ACTIVE.put(light.key, entry);
            }
            entry.light = light;
            entry.seen = true;
            if (!isBeacon(light.type)) {
                setBrightness(entry, light.type.brightnessOn(floor) * tubeBrightness(light, floor) * level2Age(light, floor)
                        * light.level * scale * edgeFade(light, fadeEnd, fadeFrom));
            }
            // The nearest few streetlamps (selected is nearest first) get their glow cone.
            if (light.type.isStreetlamp() && conesLeft > 0) {
                conesLeft--;
                if (entry.lampCone == null) {
                    entry.lampCone = lights.addLight(newLampCone(light));
                }
                float coneBrightness = LAMP_CONE_BRIGHTNESS * light.level * scale;
                if (coneBrightness != entry.lastLampConeBrightness) {
                    entry.lampCone.getLightData().setBrightness(coneBrightness);
                    entry.lastLampConeBrightness = coneBrightness;
                }
            } else {
                entry.freeLampCone();
            }
        }
        var it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (!entry.seen) {
                entry.free();
                it.remove();
            }
        }
        // Level 0: keep our light renderer alive for its full-screen pass even with no lamp on
        // screen (a blackout, a dark wing): one invisible lamp far below the world.
        boolean level0 = floor == null || floor == BackroomsLevel.LEVEL_0;
        if (level0 && (anchor == null || !anchor.isValid())) {
            anchor = lights.addLight(new Level0LightData().setPosition(0.0, -10_000.0, 0.0).setRadius(0.01f).setBrightness(0.0f));
        } else if (!level0) {
            freeAnchor();
        }
    }

    /** See the end of {@link #sync}. */
    @Nullable
    private static LightRenderHandle<? extends PointLightData> anchor;

    private static void freeAnchor() {
        if (anchor != null) {
            anchor.free();
            anchor = null;
        }
    }

    /** Lamps whose light spins (their point glow is set every frame in {@link #frame}). */
    private static boolean isBeacon(FixtureType type) {
        return type == FixtureType.RED_ALARM_LAMP || type == FixtureType.L0_BEACON;
    }

    /** Per-frame work: spin the alarm beacons smoothly (per tick would look steppy). */
    static void frame(long nowMs) {
        float alarm = FixturePower.alarm();
        LightRenderer lights = null;
        for (Entry entry : ACTIVE.values()) {
            FixtureLight light = entry.light;
            if (!isBeacon(light.type)) {
                continue;
            }
            // Level 0's beacon always turns (it only exists while switched on); the alarm lamps
            // keep a steady dim glow and only turn during the alarm.
            boolean level0Beacon = light.type == FixtureType.L0_BEACON;
            float spin = level0Beacon ? 1.0f : alarm;
            double phase = ((FixturePower.mix(light.key) >>> 11) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
            double angle = (nowMs % 3_600_000L) * (level0Beacon ? L0_BEACON_RAD_PER_MS : BEACON_RAD_PER_MS) + phase;
            float pulse = 0.5f + 0.5f * (float) Math.sin(angle * 2.0);
            setBrightness(entry, (light.type.brightness + 0.45f * spin * pulse) * light.level * scale);

            float coneBrightness = (level0Beacon ? L0_BEACON_BRIGHTNESS : BEACON_BRIGHTNESS) * spin * light.level * scale;
            if (coneBrightness <= 0.001f) {
                entry.freeCones();
                continue;
            }
            if (entry.coneA == null || entry.coneB == null) {
                if (lights == null) {
                    VeilRenderer renderer = VeilRenderSystem.renderer();
                    if (renderer == null) {
                        return;
                    }
                    lights = renderer.getLightRenderer();
                }
                entry.freeCones();
                entry.coneA = lights.addLight(newCone(light));
                entry.coneB = lights.addLight(newCone(light));
            }
            // Two cones back to back, sweeping round the vertical axis like a police beacon.
            float dx = (float) Math.cos(angle);
            float dz = (float) Math.sin(angle);
            aim(entry.coneA.getLightData(), dx, dz);
            aim(entry.coneB.getLightData(), -dx, -dz);
            if (coneBrightness != entry.lastConeBrightness) {
                entry.coneA.getLightData().setBrightness(coneBrightness);
                entry.coneB.getLightData().setBrightness(coneBrightness);
                entry.lastConeBrightness = coneBrightness;
            }
        }
    }

    /** Frees every fixture light (dimension change, logout, leaving the Backrooms). */
    static void freeAll() {
        for (Entry entry : ACTIVE.values()) {
            entry.free();
        }
        ACTIVE.clear();
        freeAnchor();
    }

    private static void setBrightness(Entry entry, float brightness) {
        if (brightness != entry.lastBrightness) {
            entry.point.getLightData().setBrightness(brightness);
            entry.lastBrightness = brightness;
        }
    }

    /**
     * 1 for lights well inside the picked set, easing to 0 for the farthest one (Level 0 and
     * Level 2; {@code fadeEnd} is 0 elsewhere, which means no fade). The fade starts at
     * {@code fadeFrom} of the way out. Rounded to sixteenths so a light that is fading doesn't
     * push a new brightness to Veil every single tick.
     */
    private static float edgeFade(FixtureLight light, double fadeEnd, float fadeFrom) {
        if (fadeEnd <= 0.0) {
            return 1.0f;
        }
        double from = fadeEnd * fadeFrom;
        double t = Math.min(1.0, Math.max(0.0, (fadeEnd - Math.sqrt(light.distSq)) / (fadeEnd - from)));
        float smooth = (float) (t * t * (3.0 - 2.0 * t));
        return Math.round(smooth * 16.0f) / 16.0f;
    }

    /** True for the Level 0 ceiling tubes, which each get their own small brightness and colour difference. */
    private static boolean level0Tube(FixtureLight light, @Nullable BackroomsLevel floor) {
        boolean level0 = floor == null || floor == BackroomsLevel.LEVEL_0;
        return level0 && (light.type == FixtureType.FLUORESCENT_LIGHT || light.type == FixtureType.L0_TROFFER_STRIP);
    }

    /** A Level 0 tube's own brightness for its age (FixtureLight#tubeGlow); 1 for everything else. */
    private static float tubeBrightness(FixtureLight light, @Nullable BackroomsLevel floor) {
        return level0Tube(light, floor) ? light.tubeGlow : 1.0f;
    }

    /** Level 2's caged bulbs: each its own brightness for its age (FixturePower#level2BulbLevel); 1 for everything else. */
    private static float level2Age(FixtureLight light, @Nullable BackroomsLevel floor) {
        return floor == BackroomsLevel.LEVEL_2 && level2Bulb(light.type) ? FixturePower.level2BulbLevel(light.key) : 1.0f;
    }

    /** The caged incandescent-style lamps of Level 2 (amber bulkhead and mercury). */
    private static boolean level2Bulb(FixtureType type) {
        return type == FixtureType.BULKHEAD_LAMP || type == FixtureType.L2_MERCURY_LAMP;
    }

    // Level 2 bulb colours by age (multipliers on the fixture colour): a new bulb burns a touch
    // whiter, old ones browner, a failing one deep orange. Index = FixturePower#level2BulbAge.
    private static final float[] BULB_GREEN = {1.07f, 1.0f, 0.91f, 0.83f};
    private static final float[] BULB_BLUE = {1.16f, 1.0f, 0.80f, 0.66f};
    // Level 2's fluorescent strips (side rooms, chambers): some tubes cooler, some a little green.
    private static final float[][] TUBE_TONES = {{1.0f, 1.0f, 1.0f}, {0.94f, 0.98f, 1.05f}, {0.97f, 1.02f, 0.93f}, {1.0f, 0.97f, 0.92f}};

    private static PointLightData newPoint(FixtureLight light, @Nullable BackroomsLevel floor) {
        FixtureType type = light.type;
        float r = type.red;
        float g = type.green;
        float b = type.blue;
        if (level0Tube(light, floor)) {
            // Tubes age differently: some yellowed, some cooler. The
            // same colour as the tube's lens (block/Level0TubeGlow), so lens and light agree.
            int tone = Level0TubeGlow.tone(BlockPos.getX(light.key), BlockPos.getZ(light.key));
            r *= Level0TubeGlow.toneRed(tone);
            g *= Level0TubeGlow.toneGreen(tone);
            b *= Level0TubeGlow.toneBlue(tone);
        }
        boolean level2 = floor == BackroomsLevel.LEVEL_2;
        if (level2 && type == FixtureType.BULKHEAD_LAMP) {
            int age = FixturePower.level2BulbAge(light.key);
            g *= BULB_GREEN[age];
            b *= BULB_BLUE[age];
        } else if (level2 && type == FixtureType.CEILING_TUBE) {
            float[] tone = TUBE_TONES[(int) ((FixturePower.mix(light.key ^ 0x2B992DDFA23249D6L) >>> 30) & 3L)];
            r *= tone[0];
            g *= tone[1];
            b *= tone[2];
        }
        // Level 0's and Level 2's lamps are our own light types: our shaders, shadows from the wall map.
        PointLightData point = floor == null || floor == BackroomsLevel.LEVEL_0 ? new Level0LightData()
                : level2 ? new Level2LightData() : new PointLightData();
        return point
                .setPosition(light.x, light.y, light.z)
                .setRadius(type.radiusOn(floor))
                .setColor(r, g, b)
                .setBrightness(0.0f)
                // Shadows: without this a lamp lights the far side of the wall into the next room.
                .setOcclusionEnabled(true);
    }

    private static SpotLightData newCone(FixtureLight light) {
        boolean level0Beacon = light.type == FixtureType.L0_BEACON;
        SpotLightData cone = new SpotLightData()
                .setSize(level0Beacon ? L0_BEACON_HALF_ANGLE : BEACON_HALF_ANGLE)
                .setAngle(level0Beacon ? L0_BEACON_SOFT_EDGE : BEACON_SOFT_EDGE)
                .setDistance(level0Beacon ? L0_BEACON_DISTANCE : BEACON_DISTANCE)
                .setColor(1.0f, 0.04f, 0.02f)
                .setBrightness(0.0f)
                .setOcclusionEnabled(true)
                .setInscatteringStrength(level0Beacon ? 0.3f : 0.2f); // faint red beams in the dust
        cone.getPositionMutable().set(light.x, light.y, light.z);
        return cone;
    }

    /** A cone hanging under a streetlamp head, pointing straight down, in the lamp's colour. */
    private static SpotLightData newLampCone(FixtureLight light) {
        FixtureType type = light.type;
        SpotLightData cone = new SpotLightData()
                .setSize(LAMP_CONE_HALF_ANGLE)
                .setAngle(LAMP_CONE_SOFT_EDGE)
                .setDistance(LAMP_CONE_DISTANCE)
                .setColor(type.red, type.green, type.blue)
                .setBrightness(0.0f)
                .setOcclusionEnabled(true)
                .setInscatteringStrength(LAMP_CONE_INSCATTER);
        cone.getPositionMutable().set(light.x, light.y, light.z);
        // Veil cones shine along local +Z; lookAlong makes -Z face the direction given, so pass
        // "up" to make +Z face down. The up hint must not be parallel to that, hence +Z.
        cone.getOrientationMutable().identity().lookAlong(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return cone;
    }

    /** Points a cone along (dx, -0.2, dz). Veil cones shine along their local +Z axis. */
    private static void aim(SpotLightData cone, float dx, float dz) {
        float dy = -0.2f;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        // lookAlong makes -Z face the given direction, so pass the reverse to get +Z facing it.
        cone.getOrientationMutable().identity().lookAlong(-dx / len, -dy / len, -dz / len, 0.0f, 1.0f, 0.0f);
    }

    private VeilFixtures() {}
}
