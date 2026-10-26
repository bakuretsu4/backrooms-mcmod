package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.FlashlightStatesPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.LevelLayout;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Real dynamic lighting for the Backrooms, drawn by Veil.
 *
 * <p>Every client tick in a Backrooms dimension we:
 * <ol>
 *   <li>keep {@link LightIndex} (all fixture blocks in loaded chunks) up to date,</li>
 *   <li>turn the fixtures around you into lights ({@link FixtureClusters}: a strip emits once),</li>
 *   <li>apply the floor event to each one ({@link FixturePower}: blackout, flicker, alarm),</li>
 *   <li>hand the nearest {@value #MAX_LIGHTS} within {@value #LIGHT_RANGE} blocks to Veil as shadowed
 *       point lights ({@link VeilFixtures}) and let the nearest few hum ({@link FixtureSounds}).</li>
 * </ol>
 * Every frame we also aim the flashlights ({@link Flashlights}) and spin the alarm beacons.
 *
 * <p>Veil's 3D shadow grid only covers 32 blocks around the camera, which is why lights further
 * than ~28 blocks are left out (they'd shine through walls). The vanilla lightmap keeps a very dim
 * fill everywhere else ({@code ClientLighting}).
 *
 * <p>When Veil isn't usable (not installed, or Iris is loaded: Veil's lights don't work under
 * Iris) nothing here touches Veil, {@link #isVeilLightingActive()} is false and the old lightmap +
 * screen-space flashlight look takes over. The hum sounds work either way.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class BackroomsLights {
    /** Most Veil fixture lights at once: shadowed lights are costly on the GPU. */
    public static final int MAX_LIGHTS = 32;
    /** Furthest fixture that gets a Veil light (blocks). */
    public static final int LIGHT_RANGE = 28;
    /** Lights are clustered this far around the player, a bit beyond LIGHT_RANGE. */
    private static final int BUILD_RANGE = LIGHT_RANGE + 10;
    /** Rebuild the cluster list after walking this far (or when fixtures changed). */
    private static final int REBUILD_MOVE = 6;
    /** Lights already on get this much "closer" when ranking, so they don't swap at the edge. */
    private static final double KEEP_BONUS = 0.8;

    private static final boolean VEIL_LOADED = ModList.get().isLoaded("veil");
    /** Veil switches its lights off whenever Iris is installed, even with no shader pack (same check as Veil.IRIS). */
    private static final boolean IRIS_LOADED = ModList.get().isLoaded("iris");

    private static final LightIndex INDEX = new LightIndex();
    private static final List<FixtureLight> SELECTED = new ArrayList<>();
    private static final List<FixtureLight> CANDIDATES = new ArrayList<>();
    private static final LongOpenHashSet SHOWN = new LongOpenHashSet();
    private static final Comparator<FixtureLight> BY_DISTANCE = Comparator.comparingDouble(l -> l.distSq);
    /** Nearest first, but lights already on screen count as a bit closer (no swapping at the edge). */
    private static final Comparator<FixtureLight> BY_RANK =
            Comparator.comparingDouble(l -> SHOWN.contains(l.key) ? l.distSq * KEEP_BONUS : l.distSq);
    /** Level 2 lamps whose circuit we last saw without power (their bulbs are drawn dark). */
    private static final LongOpenHashSet DARK_BULBS = new LongOpenHashSet();

    @Nullable
    private static ClientLevel boundLevel;
    private static List<FixtureLight> lights = new ArrayList<>();
    private static int builtRevision = -1;
    private static int builtX;
    private static int builtZ;
    /** Veil's G-buffers are pinned on under our name (see VeilBridge). */
    private static boolean buffersPinned;
    private static boolean veilActive;
    private static volatile boolean reloadPending;

    /**
     * True while Veil's dynamic lights are lighting the scene: Veil loaded, no Iris, and you're in
     * a Backrooms dimension. The camcorder shader turns its fake flashlight off when this is true.
     */
    public static boolean isVeilLightingActive() {
        return veilActive;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != boundLevel) {
            bind(level);
        }
        FixturePower.tick();
        BackroomsLevel floor = level == null ? null : BackroomsLevel.fromDimension(level.dimension());
        if (floor == null || mc.player == null) {
            leaveBackrooms();
            return;
        }

        boolean veil = VEIL_LOADED && !IRIS_LOADED && VeilBridge.ready();
        if (veil && !buffersPinned) {
            VeilBridge.pinBuffers();
            buffersPinned = true;
        }
        if (reloadPending) {
            // Veil rebuilt its renderers: drop our handles, they get re-created below / next frame.
            reloadPending = false;
            if (veil) {
                VeilFixtures.freeAll();
                Flashlights.freeAll();
            }
        }
        veilActive = veil;

        BlockPos playerPos = mc.player.blockPosition();
        INDEX.tickRescan(playerPos);
        if (INDEX.revision() != builtRevision
                || Math.abs(playerPos.getX() - builtX) > REBUILD_MOVE
                || Math.abs(playerPos.getZ() - builtZ) > REBUILD_MOVE) {
            rebuild(level, playerPos);
        }

        // Event state for every light, and the blink click when a flickering one toggles.
        long now = System.currentTimeMillis();
        boolean flickering = FixturePower.flickering(now);
        boolean lampWave = FixturePower.lampWave(now);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        boolean level2 = floor == BackroomsLevel.LEVEL_2;
        long gameTime = level.getGameTime();
        for (FixtureLight light : lights) {
            light.distSq = sq(light.x - eye.x) + sq(light.y - eye.y) + sq(light.z - eye.z);
            boolean on = true;
            if (light.type.flickers) {
                if (flickering) {
                    on = FixturePower.flickerOn(light.key, now);
                } else if (lampWave) {
                    on = FixturePower.waveOn(light, now);
                }
            }
            if (light.broken) {
                // A broken Level 9 lamp stutters on top of whatever the event does.
                on = on && FixturePower.brokenLampOn(light.key, now);
            }
            boolean restriking = false;
            // Level 2's caged lamps hang on series circuits: when one fails (with a pop), all of
            // its lamps go dark together for a while (same for every player: game time). Not the
            // copy of the lamp by a void door, down in the void below the floor: it is the only
            // light there and must never go out.
            if (level2 && light.type == FixtureType.BULKHEAD_LAMP && light.y > LevelLayout.FLOOR_Y) {
                long circuit = FixturePower.level2Circuit(light.x, light.z);
                if (FixturePower.level2CircuitPop(circuit, gameTime)) {
                    FixtureSounds.circuitPop(light);
                }
                restriking = FixturePower.level2CircuitRestriking(circuit, gameTime);
                on = on && FixturePower.level2CircuitOn(circuit, gameTime);
                // The bulb's glowing faces are baked into the chunk mesh: redraw it when its
                // circuit loses or regains power (see Level2LampGlow).
                boolean powered = FixturePower.level2CircuitPowered(circuit, gameTime);
                if (powered ? DARK_BULBS.remove(light.key) : DARK_BULBS.add(light.key)) {
                    BlockPos bulb = BlockPos.of(light.key);
                    mc.levelRenderer.setBlocksDirty(bulb.getX(), bulb.getY(), bulb.getZ(), bulb.getX(), bulb.getY(), bulb.getZ());
                }
            }
            if (on != light.on && (flickering || lampWave || light.broken || restriking)) {
                FixtureSounds.blink(light, on);
            }
            light.on = on;
            light.level = FixturePower.levelOf(light);
        }

        lights.sort(BY_DISTANCE);
        updateMercuryShare();
        FixtureSounds.tick(lights);
        if (veil) {
            select(floor);
            VeilFixtures.sync(SELECTED, CameraClientState.battery <= 0.0f ? 0.25f : 1.0f, floor);
        }
    }

    /**
     * 0..1: how much of the lamp light around you (within {@value #TINT_RANGE} blocks) comes from
     * cold mercury lamps instead of orange sodium ones. The lightmap fill can only have ONE colour
     * for the whole screen, so {@code ClientLighting} tints it by this: under a mercury lamp the
     * soft bounce turns cold green-white, under sodium it stays orange.
     */
    public static float mercuryShare() {
        return mercuryShare;
    }

    private static final double TINT_RANGE = 16.0;
    private static float mercuryShare;

    private static void updateMercuryShare() {
        double sodium = 0.0;
        double mercury = 0.0;
        for (FixtureLight light : lights) {
            if (light.distSq > TINT_RANGE * TINT_RANGE) {
                break; // sorted nearest first
            }
            // Nearer, brighter lamps count more (light falls off with distance squared).
            double weight = light.level / (1.0 + light.distSq);
            if (light.type == FixtureType.MERCURY_LAMP) {
                mercury += weight;
            } else if (light.type == FixtureType.SODIUM_LAMP || light.type == FixtureType.PORCH_LIGHT) {
                sodium += weight;
            }
        }
        float target = mercury + sodium <= 1e-6 ? mercuryShare : (float) (mercury / (mercury + sodium));
        // Ease toward it so the tint doesn't jump when a lamp flickers.
        mercuryShare += (target - mercuryShare) * 0.2f;
    }

    /** Per-frame: flashlights follow the view smoothly, beacons spin. Runs before Veil draws its lights. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!veilActive || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Flashlights.frame(event.getCamera(), partialTick, mc.level, mc.player);
        VeilFixtures.frame(System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            if (chunk.getLevel() != boundLevel && chunk.getLevel() instanceof ClientLevel level) {
                bind(level); // chunks of a new dimension can arrive before our next tick
            }
            INDEX.onChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            INDEX.onChunkUnload(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        bind(null);
        leaveBackrooms();
        FlashlightStatesPayload.CLIENT_ON.clear();
    }

    /** Mod bus: resource reloads (F3+T, pack changes) can make Veil drop all lights. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> reloadPending = true);
    }

    /** New level (or none): forget all fixtures and free every light that belonged to the old one. */
    private static void bind(@Nullable ClientLevel level) {
        boundLevel = level;
        freeLights();
        FixtureSounds.stopAll();
        FixturePower.reset();
        INDEX.reset(level);
        lights = new ArrayList<>();
        SELECTED.clear();
        DARK_BULBS.clear();
        builtRevision = -1;
        Minecraft mc = Minecraft.getInstance();
        if (level != null && mc.player != null && mc.player.level() == level) {
            INDEX.scanAround(mc.player.blockPosition(), BUILD_RANGE / 16 + 1);
        }
    }

    /** Not in the Backrooms: no Veil lights, buffers unpinned. */
    private static void leaveBackrooms() {
        if (veilActive || buffersPinned) {
            freeLights();
            if (buffersPinned) {
                VeilBridge.unpinBuffers();
                buffersPinned = false;
            }
        }
        veilActive = false;
    }

    private static void freeLights() {
        if (VEIL_LOADED && !IRIS_LOADED) {
            VeilFixtures.freeAll();
            Flashlights.freeAll();
        }
    }

    private static void rebuild(ClientLevel level, BlockPos center) {
        List<FixtureLight> built = FixtureClusters.build(INDEX, level, center.getX(), center.getZ(), BUILD_RANGE);
        // Carry the flicker state over so a rebuild mid-flicker doesn't click every lamp.
        Long2ObjectOpenHashMap<FixtureLight> old = new Long2ObjectOpenHashMap<>(lights.size());
        for (FixtureLight light : lights) {
            old.put(light.key, light);
        }
        for (FixtureLight light : built) {
            FixtureLight previous = old.get(light.key);
            if (previous != null) {
                light.on = previous.on;
                light.level = previous.level;
            }
        }
        lights = built;
        builtRevision = INDEX.revision();
        builtX = center.getX();
        builtZ = center.getZ();
    }

    /**
     * Picks the nearest lights within LIGHT_RANGE (lights already shown get a small bonus). In
     * Level 9 a few slots of the MAX_LIGHTS budget are kept free for the streetlamps' glow cones
     * ({@link VeilFixtures#LAMP_CONES}), so the total never exceeds MAX_LIGHTS.
     */
    private static void select(BackroomsLevel floor) {
        int budget = floor == BackroomsLevel.LEVEL_9 ? MAX_LIGHTS - VeilFixtures.LAMP_CONES : MAX_LIGHTS;
        SHOWN.clear();
        for (FixtureLight light : SELECTED) {
            SHOWN.add(light.key);
        }
        SELECTED.clear();
        CANDIDATES.clear();
        double maxSq = (double) LIGHT_RANGE * LIGHT_RANGE;
        boolean blackedOut = FixturePower.mainsPower() <= 0.0f;
        for (FixtureLight light : lights) {
            if (light.distSq > maxSq) {
                break; // sorted nearest first
            }
            // In a full blackout a dead lamp would only cost GPU time: leave it out.
            if (blackedOut && light.level <= 0.0f) {
                continue;
            }
            CANDIDATES.add(light);
        }
        CANDIDATES.sort(BY_RANK);
        for (int i = 0; i < CANDIDATES.size() && i < budget; i++) {
            SELECTED.add(CANDIDATES.get(i));
        }
        CANDIDATES.clear();
    }

    private static double sq(double v) {
        return v * v;
    }

    private BackroomsLights() {}
}
