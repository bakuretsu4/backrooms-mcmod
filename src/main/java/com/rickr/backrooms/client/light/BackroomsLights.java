package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.FlashlightStatesPayload;
import com.rickr.backrooms.world.BackroomsLevel;
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
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (FixtureLight light : lights) {
            light.distSq = sq(light.x - eye.x) + sq(light.y - eye.y) + sq(light.z - eye.z);
            boolean on = !flickering || !light.type.flickers || FixturePower.flickerOn(light.key, now);
            if (on != light.on && flickering) {
                FixtureSounds.blink(light);
            }
            light.on = on;
            light.level = FixturePower.levelOf(light);
        }

        lights.sort(BY_DISTANCE);
        FixtureSounds.tick(lights);
        if (veil) {
            select();
            VeilFixtures.sync(SELECTED, CameraClientState.battery <= 0.0f ? 0.25f : 1.0f, floor);
        }
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

    /** Picks the nearest MAX_LIGHTS within LIGHT_RANGE (lights already shown get a small bonus). */
    private static void select() {
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
        for (int i = 0; i < CANDIDATES.size() && i < MAX_LIGHTS; i++) {
            SELECTED.add(CANDIDATES.get(i));
        }
        CANDIDATES.clear();
    }

    private static double sq(double v) {
        return v * v;
    }

    private BackroomsLights() {}
}
