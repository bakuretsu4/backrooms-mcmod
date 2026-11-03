package com.rickr.backrooms.client.light;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.water.VanillaWaterHider;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * Level 1's wet floor: the garage's lamps reflected in it as long bright streaks, sparkling where
 * they cross a puddle, the floor between them dark (docs/reference/level1-garage/wet_1, wet_2).
 *
 * <p><b>Why geometry, not a reflection pass.</b> The look has to hold with Veil's lights, with
 * plain vanilla lighting and under Iris shader packs (Complementary), and a pack ignores our
 * shaders. So each reflection is a few flat strips laid on the floor and drawn with vanilla's
 * glowing-eyes render type: added on top of the picture, full-bright, depth-tested (a pillar in
 * front hides it), and a shader pack draws it with its own glowing-eyes program. Nothing reads
 * the screen, so it costs the same in every renderer.
 *
 * <p><b>Where a streak goes.</b> A mirror floor shows a lamp where the line from your eye to the
 * lamp's mirror image (as far under the floor as the lamp is above it) meets the floor. Real wet
 * concrete is rough, so it also catches the lamp a little in front of and behind that spot; the
 * microfacet tilt that takes is small sideways but changes slowly along the line of sight, which
 * is what stretches a reflection into a streak toward you. Per lamp, every frame:
 * <ul>
 *   <li>the mirror image of the lamp's ends (the top and bottom of a pillar's vertical tube, the
 *       two ends of a hanging strip) gives the sharp core;</li>
 *   <li>rows toward you and away from it get the brightness of the tilt they need ({@link #lobe}:
 *       a sharp mirror core ({@link #SIGMA}) over a faint long tail ({@link #SIGMA_TAIL})), so a
 *       pillar tube becomes a thin streak under it, as wide as the tube looks, brightest toward
 *       the pillar's foot and fading toward you, and a ceiling strip a short narrow bar;</li>
 *   <li>a Fresnel term makes reflections faint at your feet and strong at a grazing angle;</li>
 *   <li>each row looks at the floor it lies on: puddles (wet concrete, steaming puddles,
 *       condensation) reflect fully, dry concrete and its markings at {@link #DRY}, anything
 *       else (carpet, tile, a ramp, a crate in the way) not at all;</li>
 *   <li>the texture (tools/anomaly_assets/level1.py, textures/misc/l1_wet_glints.png) is a dim
 *       sheen spattered with glints, pinned to the floor and drifting slowly, so the streaks
 *       sparkle and the glints crawl as you move.</li>
 * </ul>
 * A lamp's reflection follows its power ({@link FixtureLight#level}: blackout, flicker, alarm),
 * so the floor goes dark with the lights. Wall tubes only reflect in front of their face, and
 * once a tick each reflection checks that no wall or pillar stands between the lamp and its spot
 * on the floor (eased in and out, no popping).
 *
 * <p>Cost: up to {@link #budget} lamps (the nearest, within {@value #RANGE} blocks), each a strip
 * of about 10-25 rows of three quads, built into one buffer per frame; no allocation per frame in
 * this class. The lamps come from {@link BackroomsLights#fixtures()}, which is kept in every
 * renderer (Veil or not).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level1WetFloor {
    private static final ResourceLocation GLINTS = Backrooms.id("textures/misc/l1_wet_glints.png");
    /**
     * The same glints pre-brightened for shader packs. A pack (Complementary) turns colours to
     * linear light before it adds them, which roughly squares them: the dim sheen (~0.3) became
     * ~0.09 and the strips all but vanished, leaving only the glints. This copy (and the vertex
     * brightness, see {@link #vertex}) is raised by the square root to land where Veil's does.
     */
    private static final ResourceLocation GLINTS_PACK = Backrooms.id("textures/misc/l1_wet_glints_pack.png");
    /**
     * Vanilla's "eyes" type: textured, full-bright, added (one, one), no depth write. Made on first
     * use, not when the event bus loads this class during mod start-up.
     */
    @Nullable
    private static RenderType type;
    @Nullable
    private static RenderType typePack;
    /** An Iris shader pack draws the world (asked once a tick). */
    private static boolean packDraws;
    /**
     * Under a pack, on top of the square root: 1, the root alone brings the pack's picture to
     * Veil's (in-game comparison, round 2).
     */
    private static final float PACK_GAIN = 1.0f;

    /** Lamps further than this (sideways, from the camera) get no reflection ... */
    private static final double RANGE = 40.0;
    /** ... and they fade in over this many blocks inside it. */
    private static final double RANGE_FADE = 8.0;
    /** Roughness along the line of sight: the sharp core of the lobe (radians of microfacet tilt). */
    private static final double SIGMA = 0.03;
    /** ... and its faint long tail, which carries the streak on toward the lamp's foot. */
    private static final double SIGMA_TAIL = 0.09;
    /** Share of the lobe in its tail. */
    private static final float TAIL_SHARE = 0.3f;
    /**
     * Beyond the mirror spot (toward the lamp's foot) the lobe is this much wider: wet concrete's
     * streaks run on most of the way to the base of the pillar, brightening toward it.
     */
    private static final double FAR_STRETCH = 1.8;
    /**
     * Sideways the reflection is as wide as the tube looks (its mirror image) plus a soft edge of
     * this share of that width and this much per block of distance (so a far streak doesn't break
     * up into pixels): no halo or fan.
     */
    private static final double EDGE_SHARE = 0.5;
    private static final double EDGE_PER_BLOCK = 0.003;
    /** The middle of a streak is never thinner than this (half width, blocks). */
    private static final double MIN_HALF = 0.02;
    /** A tail row is dropped once the lobe is below this. */
    private static final double TAIL_CUTOFF = 0.01;
    /** First tail row this far (blocks) from the core, each next one this much further. */
    private static final double TAIL_STEP = 0.25;
    private static final double TAIL_GROWTH = 1.35;
    /** Closest to the camera (blocks, sideways) a row may be. */
    private static final double S_MIN = 0.15;
    /** Your eye must be at least this high above a floor to see a lamp in it. */
    private static final double MIN_EYE = 0.3;
    /**
     * How far above the floor surface the strips lie: clear of the floor in the depth buffer at 40
     * blocks even with a shader pack's depth, still under the condensation film.
     */
    private static final float LIFT = 0.02f;
    /**
     * Overall brightness, and the share of it dry concrete (and its paint) reflects. A pillar tube
     * 12 blocks off puts ~0.4 into a puddle at the core (its glints there ~0.4, its sheen ~0.1)
     * and a third of that into dry floor; far lamps, seen at a grazing angle, come out brighter
     * (Fresnel). Thin and crisp, and the floor between the streaks stays dark (round 2 in game:
     * twice this, with a wide lateral spread, flooded the floor white).
     */
    private static final float GAIN = 1.4f;
    private static final float DRY = 0.3f;
    /** Fresnel: the reflectance looking straight down, rising toward 1 at a grazing angle. */
    private static final double F0 = 0.15;
    /** The glint texture: one tile per this many blocks, drifting this fast (tiles per second). */
    private static final double GLINT_BLOCKS = 4.0;
    private static final double DRIFT_U = 0.011;
    private static final double DRIFT_V = 0.006;
    /** How fast a reflection fades in or out when its path to the lamp opens or closes (per tick). */
    private static final float VIS_EASE = 0.35f;
    /** Steps (blocks) of the once-a-tick march from the floor spot to the lamp. */
    private static final double MARCH_STEP = 0.5;

    private static final int MAX_SOURCES = 96;
    private static final int MAX_ROWS = 48;
    private static final int NO_PLANE = Integer.MIN_VALUE;
    private static final int UNKNOWN = Integer.MIN_VALUE + 1;

    // ---- The lamps reflected this tick (parallel arrays: nothing allocated per tick or frame) ----
    private static final FixtureLight[] LIGHT = new FixtureLight[MAX_SOURCES];
    /** The lamp as a segment A-B (world): a vertical tube top to bottom, a strip end to end. */
    private static final double[] AX = new double[MAX_SOURCES];
    private static final double[] AY = new double[MAX_SOURCES];
    private static final double[] AZ = new double[MAX_SOURCES];
    private static final double[] BX = new double[MAX_SOURCES];
    private static final double[] BY = new double[MAX_SOURCES];
    private static final double[] BZ = new double[MAX_SOURCES];
    /** The lamp's thickness across the segment (blocks) and its brightness in the floor. */
    private static final float[] WIDTH = new float[MAX_SOURCES];
    private static final float[] STRENGTH = new float[MAX_SOURCES];
    /** The floor surface under the lamp (y of the top of the floor block). */
    private static final int[] PLANE = new int[MAX_SOURCES];
    /** 0..1 eased: nothing stands between the lamp and its spot on the floor. */
    private static final float[] VIS = new float[MAX_SOURCES];
    private static int count;

    /** Last tick's visibility per lamp (key), so the easing carries over when the list is rebuilt. */
    private static final Long2FloatOpenHashMap PREV_VIS = new Long2FloatOpenHashMap();
    /** The floor under each lamp (key -> plane y, or NO_PLANE), found once. */
    private static final Long2IntOpenHashMap PLANES = new Long2IntOpenHashMap();
    @Nullable
    private static ClientLevel planesLevel;

    // ---- Per-frame scratch ----
    private static final double[] ROW_S = new double[MAX_ROWS];
    private static final double[] ROW_DELTA = new double[MAX_ROWS];
    private static final float[] ROW_I = new float[MAX_ROWS];
    private static final double[] ROW_C = new double[MAX_ROWS];
    private static final double[] ROW_IN = new double[MAX_ROWS];
    private static final double[] ROW_OUT = new double[MAX_ROWS];
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Vector3f UP = new Vector3f();
    private static ClientLevel level;
    private static BufferBuilder buffer;
    private static Matrix4f view;
    private static double eyeX;
    private static double eyeY;
    private static double eyeZ;
    private static double lookX;
    private static double lookZ;
    private static float uOff;
    private static float vOff;

    // The floor blocks, looked up once (DeferredBlock#get each time would be a map lookup).
    @Nullable
    private static Block concreteFloor;
    private static Block wetFloor;
    private static Block steamingPuddle;
    private static Block floorMark;
    private static Block crackedConcrete;
    private static Block condensation;

    private Level1WetFloor() {}

    /** How many lamps may reflect at once, by the QUALITY setting. */
    private static int budget() {
        return switch (ClientSettings.quality()) {
            case LOW -> 32;
            case MEDIUM -> 64;
            case HIGH -> MAX_SOURCES;
        };
    }

    // =============================================================== once a tick: which lamps

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null || mc.player == null || BackroomsLevel.fromDimension(world.dimension()) != BackroomsLevel.LEVEL_1) {
            count = 0;
            if (planesLevel != null) {
                planesLevel = null;
                PLANES.clear();
                PREV_VIS.clear();
            }
            return;
        }
        if (world != planesLevel || PLANES.size() > 8192) {
            planesLevel = world;
            PLANES.clear();
        }
        lookUpBlocks();
        packDraws = VanillaWaterHider.shaderPackInUse();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        PREV_VIS.clear();
        for (int i = 0; i < count; i++) {
            PREV_VIS.put(LIGHT[i].key, VIS[i]);
        }
        int budget = budget();
        count = 0;
        double reach = RANGE + 12.0; // the list is sorted by 3D distance: lamps hang up to ~7 above you
        for (FixtureLight light : BackroomsLights.fixtures()) {
            if (count >= budget || light.distSq > reach * reach) {
                break;
            }
            double dx = light.x - eye.x;
            double dz = light.z - eye.z;
            if (dx * dx + dz * dz > RANGE * RANGE || light.blocks == null || !shape(count, light)) {
                continue;
            }
            int plane = planeOf(world, light);
            if (plane == NO_PLANE) {
                continue;
            }
            PLANE[count] = plane;
            LIGHT[count] = light;
            float target = pathClear(world, count, eye.x, eye.y, eye.z) ? 1.0f : 0.0f;
            float prev = PREV_VIS.getOrDefault(light.key, -1.0f);
            VIS[count] = prev < 0.0f ? target : prev + (target - prev) * VIS_EASE;
            count++;
        }
    }

    private static void lookUpBlocks() {
        if (concreteFloor != null) {
            return;
        }
        concreteFloor = ModBlocks.CONCRETE_FLOOR.get();
        wetFloor = ModBlocks.WET_CONCRETE_FLOOR.get();
        steamingPuddle = ModBlocks.L1_STEAMING_PUDDLE.get();
        floorMark = ModBlocks.L1_FLOOR_MARK.get();
        crackedConcrete = ModBlocks.L1_CRACKED_CONCRETE.get();
        condensation = ModBlocks.L1_CONDENSATION.get();
    }

    /**
     * Fills slot {@code i} with the lamp's segment, thickness and strength; false for lamps that
     * don't reflect. Pillar and wall tubes are vertical (the two-block tube top to bottom); the
     * hanging fixtures and tube strips are horizontal, their glowing underside end to end.
     */
    private static boolean shape(int i, FixtureLight light) {
        long[] blocks = light.blocks;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long pos : blocks) {
            minX = Math.min(minX, BlockPos.getX(pos));
            minY = Math.min(minY, BlockPos.getY(pos));
            minZ = Math.min(minZ, BlockPos.getZ(pos));
            maxX = Math.max(maxX, BlockPos.getX(pos));
            maxY = Math.max(maxY, BlockPos.getY(pos));
            maxZ = Math.max(maxZ, BlockPos.getZ(pos));
        }
        switch (light.type) {
            // The pillar tubes: a 4-pixel tube the height of its blocks.
            case WAREHOUSE_LIGHT -> vertical(i, light, maxY + 0.95, minY + 0.05, 0.25f, 0.85f);
            // Small lamps: their light point, a little above and below it.
            case RED_ALARM_LAMP -> vertical(i, light, light.y + 0.2, light.y - 0.2, 0.4f, 0.5f);
            case EXIT_SIGN -> vertical(i, light, light.y + 0.25, light.y - 0.25, 0.7f, 0.22f);
            // The hanging fixtures: the diffuser, a quarter block up from the cell's bottom, 8 px wide.
            case L1_HANGING_LIGHT -> horizontal(i, light, minX, maxX, minZ, maxZ, minY + 0.25, 0.5f, 1.0f);
            case CEILING_TUBE -> horizontal(i, light, minX, maxX, minZ, maxZ, light.y, 0.3f, 0.9f);
            // The stairwell's ceiling strip: the lamp is the underside of a ceiling block.
            case PIPE_LIGHT -> horizontal(i, light, minX, maxX, minZ, maxZ, minY, 0.8f, 0.7f);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void vertical(int i, FixtureLight light, double top, double bottom, float width, float strength) {
        AX[i] = light.x;
        AY[i] = top;
        AZ[i] = light.z;
        BX[i] = light.x;
        BY[i] = bottom;
        BZ[i] = light.z;
        WIDTH[i] = width;
        STRENGTH[i] = strength;
    }

    private static void horizontal(int i, FixtureLight light, int minX, int maxX, int minZ, int maxZ, double y,
                                   float width, float strength) {
        boolean alongX = maxX - minX >= maxZ - minZ;
        // The glowing part stops a little short of the fixture's ends.
        AX[i] = alongX ? minX + 0.1 : light.x;
        AZ[i] = alongX ? light.z : minZ + 0.1;
        BX[i] = alongX ? maxX + 0.9 : light.x;
        BZ[i] = alongX ? light.z : maxZ + 0.9;
        AY[i] = y;
        BY[i] = y;
        WIDTH[i] = width;
        STRENGTH[i] = strength;
    }

    /** The floor surface under a lamp: the first garage floor block below it (crates, racks and air skipped). */
    private static int planeOf(ClientLevel world, FixtureLight light) {
        int known = PLANES.getOrDefault(light.key, UNKNOWN);
        if (known != UNKNOWN) {
            return known;
        }
        int x = Mth.floor(light.x);
        int z = Mth.floor(light.z);
        int top = BlockPos.getY(light.key);
        int plane = NO_PLANE;
        for (int y = top - 1; y >= top - 14; y--) {
            if (floorShine(world.getBlockState(CURSOR.set(x, y, z))) > 0.0f) {
                plane = y + 1;
                break;
            }
        }
        PLANES.put(light.key, plane);
        return plane;
    }

    /** How much of a lamp a floor block reflects: puddles fully, dry concrete and its paint some, anything else none. */
    private static float floorShine(BlockState state) {
        Block block = state.getBlock();
        if (block == wetFloor || block == steamingPuddle) {
            return 1.0f;
        }
        if (block == concreteFloor || block == floorMark) {
            return DRY;
        }
        return block == crackedConcrete ? DRY * 0.8f : 0.0f;
    }

    /**
     * True if nothing solid stands between the lamp and the spot on the floor where you see it
     * (from your eye at the time of the tick): a lamp behind a pillar or a wall leaves no streak.
     */
    private static boolean pathClear(ClientLevel world, int i, double ex, double ey, double ez) {
        double plane = PLANE[i];
        double he = ey - plane;
        double cx = 0.5 * (AX[i] + BX[i]);
        double cy = 0.5 * (AY[i] + BY[i]);
        double cz = 0.5 * (AZ[i] + BZ[i]);
        double hc = cy - plane;
        if (he < MIN_EYE || hc <= 0.0) {
            return false;
        }
        double t = he / (he + hc);
        double sx = ex + (cx - ex) * t;
        double sz = ez + (cz - ez) * t;
        double sy = plane + 0.2;
        double dx = cx - sx;
        double dy = cy - sy;
        double dz = cz - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (length / MARCH_STEP);
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int k = 1; k < steps; k++) {
            double f = k / (double) steps;
            if ((1.0 - f) * length < 0.8) {
                break; // the lamp's own block and the wall it hangs on
            }
            int bx = Mth.floor(sx + dx * f);
            int by = Mth.floor(sy + dy * f);
            int bz = Mth.floor(sz + dz * f);
            if (bx == lastX && by == lastY && bz == lastZ) {
                continue;
            }
            lastX = bx;
            lastY = by;
            lastZ = bz;
            if (world.getBlockState(CURSOR.set(bx, by, bz)).canOcclude()) {
                return false;
            }
        }
        return true;
    }

    // =============================================================== every frame: the strips

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || count == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_1) {
            return;
        }
        level = mc.level;
        Vec3 eye = event.getCamera().getPosition();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        Vector3f look = event.getCamera().getLookVector();
        double lookLen = Math.sqrt(look.x() * look.x() + look.z() * look.z());
        lookX = lookLen > 1e-4 ? look.x() / lookLen : 1.0;
        lookZ = lookLen > 1e-4 ? look.z() / lookLen : 0.0;
        view = event.getModelViewMatrix();
        view.transformDirection(0.0f, 1.0f, 0.0f, UP);
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        uOff = (float) frac(eyeX / GLINT_BLOCKS + seconds * DRIFT_U);
        vOff = (float) frac(eyeZ / GLINT_BLOCKS + seconds * DRIFT_V);

        // Camera-relative vertices through the frame's view matrix, as the other world passes do.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        if (type == null) {
            type = RenderType.eyes(GLINTS);
            typePack = RenderType.eyes(GLINTS_PACK);
        }
        RenderType drawn = packDraws ? typePack : type;
        buffer = Tesselator.getInstance().begin(drawn.mode(), drawn.format());
        for (int i = 0; i < count; i++) {
            drawLamp(i);
        }
        MeshData mesh = buffer.build(); // null when nothing was drawn
        if (mesh != null) {
            drawn.draw(mesh);
        }
        buffer = null;
        level = null;
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /**
     * The tilt (radians) a floor facet at distance {@code s} along the line of sight must have to
     * show a lamp point {@code h} above the floor and {@code d} away to an eye {@code he} above
     * it: 0 at the mirror spot, negative nearer you, positive beyond it.
     */
    private static double tilt(double s, double h, double d, double he) {
        return 0.5 * (Math.atan2(h, d - s) - Math.atan2(he, s));
    }

    /** The glossy lobe: a sharp core (1 at the mirror spot) over a faint, slowly fading tail. */
    private static float lobe(double tilt) {
        double q = tilt / SIGMA;
        double k = 1.0 + q * q;
        double qt = tilt / SIGMA_TAIL;
        double kt = 1.0 + qt * qt;
        return (float) ((1.0 - TAIL_SHARE) / (k * k) + TAIL_SHARE / (kt * kt));
    }

    private static void drawLamp(int i) {
        FixtureLight light = LIGHT[i];
        float power = light.level * VIS[i];
        if (power < 0.01f) {
            return;
        }
        double plane = PLANE[i];
        double he = eyeY - plane;
        double hA = AY[i] - plane;
        double hB = BY[i] - plane;
        if (he < MIN_EYE || hA < 0.3 || hB < 0.3) {
            return; // you are below this floor (or on a lower one), or the lamp sits on it
        }
        // Camera-relative, sideways only.
        double ax = AX[i] - eyeX;
        double az = AZ[i] - eyeZ;
        double bx = BX[i] - eyeX;
        double bz = BZ[i] - eyeZ;
        double fade = Mth.clamp((RANGE - Math.sqrt(0.25 * ((ax + bx) * (ax + bx) + (az + bz) * (az + bz)))) / RANGE_FADE, 0.0, 1.0);
        // Where the line to each end's mirror image meets the floor.
        double tA = he / (he + hA);
        double tB = he / (he + hB);
        double pax = ax * tA;
        double paz = az * tA;
        double pbx = bx * tB;
        double pbz = bz * tB;
        double pcx = 0.5 * (pax + pbx);
        double pcz = 0.5 * (paz + pbz);
        if (light.facing != null) {
            // A wall lamp only lights (and shows in) the floor in front of its face.
            double front = (pcx - (light.x - eyeX)) * light.facing.getStepX() + (pcz - (light.z - eyeZ)) * light.facing.getStepZ();
            fade *= Mth.clamp(front * 2.5, 0.0, 1.0);
        }
        if (fade <= 0.0) {
            return;
        }
        // Frame on the floor: D along the line of sight to the image, N to its right.
        double len = Math.sqrt(pcx * pcx + pcz * pcz);
        double dx = len > 1e-3 ? pcx / len : lookX;
        double dz = len > 1e-3 ? pcz / len : lookZ;
        double nx = -dz;
        double nz = dx;
        double sA = pax * dx + paz * dz;
        double nA = pax * nx + paz * nz;
        double sB = pbx * dx + pbz * dz;
        double nB = pbx * nx + pbz * nz;
        // The near end of the image (and the lamp end it mirrors) and the far end.
        boolean aNear = sA <= sB;
        double s0 = Math.max(S_MIN, aNear ? sA : sB);
        double s1 = Math.max(s0, aNear ? sB : sA);
        double n0 = aNear ? nA : nB;
        double n1 = aNear ? nB : nA;
        double hNear = aNear ? hA : hB;
        double hFar = aNear ? hB : hA;
        double dNear = s0 / (aNear ? tA : tB);
        double dFar = s1 / (aNear ? tB : tA);
        double tMid = 0.5 * (tA + tB);
        // Seen side-on (a strip across the line of sight) the image is a wide patch; end-on (a
        // vertical tube, a strip pointing at you) a line along it.
        boolean sideOn = Math.abs(n1 - n0) > s1 - s0;
        double thick = Math.max(MIN_HALF, 0.5 * WIDTH[i] * tMid);

        // Rows: the near tail (built outward from the core, then turned round), the core, the far tail.
        int rows = 0;
        double step = TAIL_STEP;
        for (double s = s0 - step; s > S_MIN && rows < MAX_ROWS / 2 - 2; s -= step) {
            double t = tilt(s, hNear, dNear, he);
            if (lobe(t) < TAIL_CUTOFF) {
                break;
            }
            ROW_S[rows] = s;
            ROW_DELTA[rows] = t;
            rows++;
            step *= TAIL_GROWTH;
        }
        for (int a = 0, b = rows - 1; a < b; a++, b--) {
            double ts = ROW_S[a];
            ROW_S[a] = ROW_S[b];
            ROW_S[b] = ts;
            double td = ROW_DELTA[a];
            ROW_DELTA[a] = ROW_DELTA[b];
            ROW_DELTA[b] = td;
        }
        ROW_S[rows] = s0;
        ROW_DELTA[rows] = 0.0;
        rows++;
        if (s1 > s0 + 0.02) {
            ROW_S[rows] = s1;
            ROW_DELTA[rows] = 0.0;
            rows++;
        }
        step = TAIL_STEP;
        for (double s = s1 + step; s < dFar - 0.05 && rows < MAX_ROWS; s += step) {
            // Toward the lamp's foot the lobe is wider (FAR_STRETCH): the streak runs on longer there.
            double t = tilt(s, hFar, dFar, he) / FAR_STRETCH;
            if (lobe(t) < TAIL_CUTOFF) {
                break;
            }
            ROW_S[rows] = s;
            ROW_DELTA[rows] = t;
            rows++;
            step *= TAIL_GROWTH;
        }
        if (rows < 2) {
            return;
        }

        float strength = GAIN * STRENGTH[i] * power * (float) fade;
        for (int r = 0; r < rows; r++) {
            double s = ROW_S[r];
            // Sideways: the image's own width (plus the patch, side-on), then the rough edge.
            double centre;
            double inner;
            if (sideOn) {
                centre = 0.5 * (n0 + n1);
                inner = 0.5 * Math.abs(n1 - n0) + thick;
            } else {
                double k = s1 > s0 ? Mth.clamp((s - s0) / (s1 - s0), 0.0, 1.0) : 0.5;
                centre = n0 + (n1 - n0) * k;
                inner = thick;
            }
            double re = Math.sqrt(s * s + he * he);
            ROW_C[r] = centre;
            ROW_IN[r] = inner;
            ROW_OUT[r] = inner * (1.0 + EDGE_SHARE) + EDGE_PER_BLOCK * re;
            // Faint at your feet, strong at a grazing angle.
            double grazing = 1.0 - he / re;
            double fresnel = F0 + (1.0 - F0) * grazing * grazing * grazing;
            float shine = shineAt(eyeX + s * dx + centre * nx, plane, eyeZ + s * dz + centre * nz);
            ROW_I[r] = strength * lobe(ROW_DELTA[r]) * (float) fresnel * shine;
        }

        float red = light.type.red;
        float green = light.type.green;
        float blue = light.type.blue;
        float y = (float) (plane - eyeY) + LIFT;
        for (int r = 0; r + 1 < rows; r++) {
            float i0 = ROW_I[r];
            float i1 = ROW_I[r + 1];
            if (i0 < 0.004f && i1 < 0.004f) {
                continue;
            }
            double s0r = ROW_S[r];
            double s1r = ROW_S[r + 1];
            // Left soft edge, the middle, right soft edge (up-facing: left to right, then onward).
            quad(dx, dz, nx, nz, y, s0r, ROW_C[r] - ROW_OUT[r], ROW_C[r] - ROW_IN[r], 0.0f, i0,
                    s1r, ROW_C[r + 1] - ROW_OUT[r + 1], ROW_C[r + 1] - ROW_IN[r + 1], 0.0f, i1, red, green, blue);
            if (ROW_IN[r] > 0.01 || ROW_IN[r + 1] > 0.01) {
                quad(dx, dz, nx, nz, y, s0r, ROW_C[r] - ROW_IN[r], ROW_C[r] + ROW_IN[r], i0, i0,
                        s1r, ROW_C[r + 1] - ROW_IN[r + 1], ROW_C[r + 1] + ROW_IN[r + 1], i1, i1, red, green, blue);
            }
            quad(dx, dz, nx, nz, y, s0r, ROW_C[r] + ROW_IN[r], ROW_C[r] + ROW_OUT[r], i0, 0.0f,
                    s1r, ROW_C[r + 1] + ROW_IN[r + 1], ROW_C[r + 1] + ROW_OUT[r + 1], i1, 0.0f, red, green, blue);
        }
    }

    /**
     * How much the floor at (x, z) reflects: by its block ({@link #floorShine}), full on a film of
     * condensation, nothing where something stands on it.
     */
    private static float shineAt(double x, double plane, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = (int) plane;
        BlockState above = level.getBlockState(CURSOR.set(bx, top, bz));
        if (above.getBlock() == condensation) {
            return 1.0f;
        }
        if (!above.isAir() && above.blocksMotion()) {
            return 0.0f;
        }
        return floorShine(level.getBlockState(CURSOR.set(bx, top - 1, bz)));
    }

    /**
     * One quad between two rows: on row 0 from lateral {@code l0} to {@code r0} (brightness
     * {@code il0} to {@code ir0}), on row 1 likewise. Wound so it faces up (the type culls).
     */
    private static void quad(double dx, double dz, double nx, double nz, float y,
                             double s0, double l0, double r0, float il0, float ir0,
                             double s1, double l1, double r1, float il1, float ir1,
                             float red, float green, float blue) {
        vertex(s0 * dx + l0 * nx, y, s0 * dz + l0 * nz, il0, red, green, blue);
        vertex(s0 * dx + r0 * nx, y, s0 * dz + r0 * nz, ir0, red, green, blue);
        vertex(s1 * dx + r1 * nx, y, s1 * dz + r1 * nz, ir1, red, green, blue);
        vertex(s1 * dx + l1 * nx, y, s1 * dz + l1 * nz, il1, red, green, blue);
    }

    private static void vertex(double x, float y, double z, float intensity, float red, float green, float blue) {
        // Under a shader pack the colour is squared on the way into linear light: pre-brighten it.
        float k = packDraws ? (float) Math.sqrt(Math.min(intensity * PACK_GAIN, 1.0f)) : Math.min(intensity, 1.0f);
        buffer.addVertex(view, (float) x, y, (float) z)
                .setColor(Math.min(red * k, 1.0f), Math.min(green * k, 1.0f), Math.min(blue * k, 1.0f), 1.0f)
                .setUv(uOff + (float) (x / GLINT_BLOCKS), vOff + (float) (z / GLINT_BLOCKS))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(UP.x, UP.y, UP.z);
    }
}
