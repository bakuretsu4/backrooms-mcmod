package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level0TubeGlow;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.FlashlightStatesPayload;
import com.rickr.backrooms.block.Level2EmergencyLampBlock;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.Level2LightBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import com.rickr.backrooms.config.ClientSettings;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import com.rickr.backrooms.client.Level39Lights;
import org.jetbrains.annotations.Nullable;

/**
 * Real dynamic lighting for the Backrooms, drawn by Veil.
 *
 * <p>Every client tick in a Backrooms dimension we:
 * <ol>
 *   <li>keep {@link LightIndex} (all fixture blocks in loaded chunks) up to date,</li>
 *   <li>turn the fixtures around you into lights ({@link FixtureClusters}: a strip emits once),</li>
 *   <li>apply the floor event to each one ({@link FixturePower}: blackout, flicker, alarm), and
 *       on Level 0 the faulty tubes' own stutter or dim dying glow (their glowing faces go dark
 *       with them, see block/Level0TubeGlow),</li>
 *   <li>hand the nearest lights (how many, and how far out, depends on the QUALITY setting: 32
 *       within 28 blocks on HIGH) to Veil as shadowed point lights ({@link VeilFixtures}) and let
 *       the nearest few hum ({@link FixtureSounds}).</li>
 * </ol>
 * Every frame we also aim the flashlights ({@link Flashlights}) and spin the alarm beacons.
 *
 * <p>Veil's 3D shadow grid only covers 32 blocks around the camera, which is why lights further
 * than ~28 blocks are left out (they'd shine through walls). The vanilla lightmap keeps a very dim
 * fill everywhere else ({@code ClientLighting}). Level 0 is different: its lamps are our own light
 * type with shadows from a top-down wall map ({@link Level0WallGrid}, {@link Level0LightRenderer}),
 * cheaper and reaching ~48 blocks, so there twice as many lamps reach 1.5 times as far. Level 2's
 * lamps use the same wall map (1.5 times as many lamps, 1.4 times as far) plus gloss highlights.
 *
 * <p>When Veil isn't usable (not installed, or Iris is loaded: Veil's lights don't work under
 * Iris) nothing here touches Veil, {@link #isVeilLightingActive()} is false and the old lightmap +
 * screen-space flashlight look takes over. The hum sounds work either way.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class BackroomsLights {
    /**
     * Lights are clustered this far around the player: a bit beyond the longest light range (HIGH
     * quality, {@link ClientSettings.Quality#lightRange}, times Level 0's longer reach, see
     * {@link #LEVEL0_RANGE_SCALE}). The per-frame budget and range come from the QUALITY setting:
     * every shadowed Veil light costs graphics-card time each frame.
     */
    private static final int BUILD_RANGE = (int) Math.ceil(ClientSettings.Quality.HIGH.lightRange * 1.5) + 10;
    /**
     * Level 0's lamps take their shadows from the wall map (client/light/Level0WallGrid), which
     * is much cheaper than Veil's voxel shadows and reaches ~48 blocks: there the budget is twice
     * the quality's light count and reaches 1.5 times as far (HIGH: 64 lamps within 42 blocks),
     * so lit rooms far down a dark run still glow as beacons.
     */
    private static final int LEVEL0_BUDGET_SCALE = 2;
    private static final float LEVEL0_RANGE_SCALE = 1.5f;
    /**
     * Level 2's lamps take their shadows from the same wall map (client/light/Level0WallGrid), so
     * they can be many and far too: 1.5 times the quality's light count reaching 1.4 times as far
     * (HIGH: 48 lamps within 39 blocks, about six pools down the corridor in each direction).
     */
    private static final float LEVEL2_BUDGET_SCALE = 1.5f;
    private static final float LEVEL2_RANGE_SCALE = 1.4f;
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
    /**
     * Level 0: the blocks of faulty tubes that are dark right now (their glow and grey tint, see
     * block/Level0TubeGlow). Read by the chunk-building threads, hence a thread-safe set.
     */
    private static final Set<Long> DARK_TUBES = ConcurrentHashMap.newKeySet();
    /** Level 0: which of the lights around you are faulty tubes (key -> FixturePower.TUBE_*); healthy ones are left out. */
    private static final Long2IntOpenHashMap FAULTY_TUBES = new Long2IntOpenHashMap();
    /** Level 0: tubes within this many blocks of the camera count for {@link #level0TubeShare()}. */
    private static final double TUBE_SHARE_RANGE = 7.0;
    /** How fast {@link #level0TubeShare()} follows a tube going dark or coming back (per tick). */
    private static final float TUBE_SHARE_EASE = 0.35f;
    private static float level0TubeShare = 1.0f;
    /**
     * A Level 0 tube this close (blocks, sideways) to an exit sign, a safe-room or office lamp or a
     * Red Rooms lamp is never faulty: those places must stay steady and trustworthy.
     */
    private static final double FAULT_CLEARANCE = 10.0;

    static {
        // Faulty Level 0 tubes switch their glow off while they're dark.
        Level0TubeGlow.dark = pos -> !DARK_TUBES.isEmpty() && DARK_TUBES.contains(pos.asLong());
        // (Dying tubes need no hook: their age is in the block state, see Level0TubeGlow.)
    }

    /**
     * 0..1 (Level 0): how much of the tube light right around you is lit, 1 unless a faulty
     * tube near you is in a dark moment (nearer and stronger tubes count more). The vanilla
     * light of a stuttering tube never changes, so {@code ClientLighting} dims the lightmap fill
     * by this: standing under a tube that cuts out, the room around you dims with it (in both
     * Veil and no-Veil modes), eased so it shimmers instead of strobing.
     */
    public static float level0TubeShare() {
        return level0TubeShare;
    }

    /**
     * Level 0: the fault of the light with this key, one of FixturePower.TUBE_* (TUBE_OK for
     * healthy tubes and every non-Level 0 light). Read by FixtureSounds for the per-tube buzz.
     */
    static int tubeFault(long key) {
        return FAULTY_TUBES.get(key);
    }

    /** The Level 0 ceiling tubes: the troffer panel and the corridor strip. */
    private static boolean level0Tube(FixtureType type) {
        return type == FixtureType.FLUORESCENT_LIGHT || type == FixtureType.L0_TROFFER_STRIP;
    }

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
                ThrownLights.freeAll();
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
        boolean level0 = floor == BackroomsLevel.LEVEL_0;
        long gameTime = level.getGameTime();
        // Level 0: how much of the tube light right around you is out because a faulty tube is
        // dark (see level0TubeShare()). Weighted like light: nearer and stronger tubes count more.
        double tubesAll = 0.0;
        double tubesLit = 0.0;
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
            // A faulty Level 0 tube: a stuttering one goes dark in short bursts on its own rhythm,
            // one with a lazy starter cuts out and re-strikes now and then (both on top of any
            // event), a dying one only glows dimly. Only Level 0 lights are in FAULTY_TUBES, so
            // everywhere else this is TUBE_OK.
            int fault = FAULTY_TUBES.get(light.key);
            boolean toggles = fault == FixturePower.TUBE_STUTTER || fault == FixturePower.TUBE_RESTART;
            boolean tubeDark = false;
            if (toggles) {
                boolean tubeOn = fault == FixturePower.TUBE_STUTTER
                        ? FixturePower.faultyTubeOn(light.key, now) : FixturePower.restartTubeOn(light.key, now);
                on = on && tubeOn;
                tubeDark = !tubeOn;
            }
            if (level0 && light.distSq < TUBE_SHARE_RANGE * TUBE_SHARE_RANGE && level0Tube(light.type)) {
                double weight = light.tubeGlow / (1.0 + light.distSq);
                tubesAll += weight;
                if (!tubeDark) {
                    tubesLit += weight;
                }
            }
            boolean restriking = false;
            // Level 2's caged lamps hang on series circuits: when one fails (with a pop), all of
            // its lamps go dark together for a while (same for every player: game time). Not the
            // copy of the lamp by a void door, down in the void below the floor: it is the only
            // light there and must never go out.
            if (level2 && level2CircuitLamp(light.type) && FixturePower.level2OnCircuit(BlockPos.getY(light.key))) {
                // From the block, not the light (which sits a little in front of it), so it
                // matches Level2LampGlow and the far sweep below exactly.
                long circuit = FixturePower.level2Circuit(BlockPos.getX(light.key) + 0.5, BlockPos.getZ(light.key) + 0.5);
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
            if (on != light.on && (flickering || lampWave || light.broken || restriking || toggles)) {
                FixtureSounds.blink(light, on);
            }
            light.on = on;
            light.level = FixturePower.levelOf(light);
            if (fault == FixturePower.TUBE_DYING) {
                light.level *= FixturePower.dyingTubePulse(light.key, now);
            }
            if (level2 && level2CircuitLamp(light.type)) {
                // A failing bulb browns down now and then on its own (see FixturePower#level2BulbSag).
                light.level *= FixturePower.level2BulbSag(light.key, now);
            }
            if (level2 && level2Emergency(light.type) && !light.alwaysOn) {
                // A standby emergency lamp's lens glows only while it is on (see the tint in
                // onRegisterBlockColors): redraw it when its relay switches.
                boolean lit = light.level > 0.0f;
                if (lit ? LIT_STANDBY.add(light.key) : LIT_STANDBY.remove(light.key)) {
                    redraw(mc, light.key);
                }
            }
            // The tube's glowing face goes dark with it: when a faulty tube toggles off, and on
            // Level 0 when the power is out at it (the blackout wave, a local outage). Not for
            // the FLICKER event, whose strobe the lightmap handles (redrawing the blocks that
            // fast would only stutter the chunk updates).
            boolean powerDark = level0 && level0Tube(light.type) && !flickering && light.level <= 0.02f;
            if (toggles || (level0 && level0Tube(light.type))) {
                setTubeDark(mc, light, (toggles && tubeDark) || powerDark);
            }
        }
        // Eased, so a stutter makes the fill shimmer rather than strobe.
        float tubeTarget = tubesAll <= 1.0e-6 ? 1.0f : (float) (tubesLit / tubesAll);
        level0TubeShare += (tubeTarget - level0TubeShare) * TUBE_SHARE_EASE;

        lights.sort(BY_DISTANCE);
        updateMercuryShare();
        if (level2) {
            updateLevel2Shares();
            if (++bulbSweepTick >= BULB_SWEEP_EVERY) {
                bulbSweepTick = 0;
                sweepDarkBulbs(mc, gameTime, eye);
            }
        }
        FixtureSounds.tick(lights);
        if (veil) {
            // Thrown flares/glowsticks first: the fixtures get the slots they leave free.
            ThrownLights.tick(level, mc.gameRenderer.getMainCamera().getPosition());
            int budget = select(floor);
            VeilFixtures.sync(SELECTED, CameraClientState.battery <= 0.0f ? 0.25f : 1.0f, floor, budget);
        }
        // Level 0 and Level 2 with Veil: the wall map (and on Level 0 the power map) for our light shaders.
        Level0Shading.wanted = veil && level0;
        Level2Shading.wanted = veil && level2;
        if (veil && level0) {
            Level0WallGrid.tick(level, eye.x, eye.z, false);
            Level0PowerMap.tick(eye.x, eye.z, now);
        } else if (veil && level2) {
            Level0WallGrid.tick(level, eye.x, eye.z, true);
        }
    }

    /** F3 screen: the Level 0 / Level 2 lights' GPU time (see Level0Shading, Level2Shading). */
    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        String line = Level0Shading.debugLine();
        if (line == null) {
            line = Level2Shading.debugLine();
        }
        if (line != null) {
            event.getLeft().add(line);
        }
    }

    /** Level 2's caged lamps on the series circuits: the amber bulkhead lamp and the mercury lamp. */
    private static boolean level2CircuitLamp(FixtureType type) {
        return type == FixtureType.BULKHEAD_LAMP || type == FixtureType.L2_MERCURY_LAMP;
    }

    /** Level 2's battery lamps: the brownout's violet light and the red/amber emergency lamps. */
    private static boolean level2Emergency(FixtureType type) {
        return type == FixtureType.L2_EMERGENCY_LIGHT || type == FixtureType.L2_EMERGENCY_RED || type == FixtureType.L2_EMERGENCY_AMBER;
    }

    /**
     * Level 2: the standby emergency lamps that are switched on right now (their lens glows, see
     * onRegisterBlockColors). Read by the chunk-building threads, hence a thread-safe set.
     */
    private static final Set<Long> LIT_STANDBY = ConcurrentHashMap.newKeySet();

    /**
     * How far the fixture lights reach on this floor (blocks): the quality's light range, longer on
     * Level 0 and Level 2 whose lamps take their shadows from the cheap wall map.
     */
    public static double lightRange(@Nullable BackroomsLevel floor) {
        double range = ClientSettings.quality().lightRange;
        if (floor == BackroomsLevel.LEVEL_0) {
            return range * LEVEL0_RANGE_SCALE;
        }
        return floor == BackroomsLevel.LEVEL_2 ? range * LEVEL2_RANGE_SCALE : range;
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

    /**
     * 0..1 (Level 2): how much of the light around you comes from the brownout's violet
     * emergency lights instead of the warm caged lamps. Like {@link #mercuryShare()}, it lets
     * {@code ClientLighting} give the one-colour lightmap fill the colour of where you stand.
     */
    public static float emergencyShare() {
        return emergencyShare;
    }

    /**
     * 0..1 (Level 2): how much of the light around you is lit right now (nearer fixtures count
     * more). When a series circuit pops, the section you stand in drops toward 0, and
     * {@code ClientLighting} takes the lightmap fill down with it: the vanilla block light of a
     * dead lamp never changes, so without this the "dark" section would keep a faint glow. Every
     * fixture counts, not only the caged lamps: a side room whose caged lamp died with the
     * corridor's section is still lit by its tube, and must keep its fill.
     */
    public static float level2LampShare() {
        return level2LampShare;
    }

    private static float emergencyShare;
    private static float level2LampShare = 1.0f;
    /** Level 2: the colour of the battery lights around you (violet, red or amber), eased. */
    private static float emergencyRed = FixtureType.L2_EMERGENCY_LIGHT.red;
    private static float emergencyGreen = FixtureType.L2_EMERGENCY_LIGHT.green;
    private static float emergencyBlue = FixtureType.L2_EMERGENCY_LIGHT.blue;
    /** Level 2: every caged lamp in the loaded chunks is checked against its circuit this often (ticks). */
    private static final int BULB_SWEEP_EVERY = 5;
    private static int bulbSweepTick;

    /**
     * Level 2: the colour (red, green, blue) of the battery lights near you: the brownout's
     * violet, the emergency lamps' red or amber, mixed by how much each one lights your spot.
     * {@code ClientLighting} tints the fill toward it by {@link #emergencyShare()}.
     */
    public static float emergencyRed() {
        return emergencyRed;
    }

    public static float emergencyGreen() {
        return emergencyGreen;
    }

    public static float emergencyBlue() {
        return emergencyBlue;
    }

    private static void updateLevel2Shares() {
        double emergency = 0.0;
        double er = 0.0;
        double eg = 0.0;
        double eb = 0.0;
        double lampsLit = 0.0;
        double lampsAll = 0.0;
        for (FixtureLight light : lights) {
            if (light.distSq > TINT_RANGE * TINT_RANGE) {
                break; // sorted nearest first
            }
            double weight = 1.0 / (1.0 + light.distSq);
            if (level2Emergency(light.type)) {
                // Battery lights: they colour the fill but never count as "the corridor is lit".
                double w = weight * light.level * light.type.brightness;
                emergency += weight * light.level;
                er += w * light.type.red;
                eg += w * light.type.green;
                eb += w * light.type.blue;
            } else {
                lampsAll += weight;
                if (light.on) {
                    // On or off only: blackouts and the alarm already dim the fill themselves.
                    lampsLit += weight;
                }
            }
        }
        float emergencyTarget = emergency + lampsLit <= 1e-6 ? emergencyShare : (float) (emergency / (emergency + lampsLit));
        // No other fixtures near (only emergency lights, or nothing): leave the fill alone.
        float lampTarget = lampsAll <= 1e-6 ? 1.0f : (float) Math.min(1.0, lampsLit / lampsAll);
        // Eased, so a flicker or the restrike stutter doesn't make the fill jump.
        emergencyShare += (emergencyTarget - emergencyShare) * 0.2f;
        level2LampShare += (lampTarget - level2LampShare) * 0.25f;
        double sum = er + eg + eb;
        if (sum > 1e-6) {
            // Normalised to the brightest channel, so only the hue follows the lamps.
            double top = Math.max(er, Math.max(eg, eb));
            emergencyRed += ((float) (er / top) - emergencyRed) * 0.2f;
            emergencyGreen += ((float) (eg / top) - emergencyGreen) * 0.2f;
            emergencyBlue += ((float) (eb / top) - emergencyBlue) * 0.2f;
        }
    }

    /**
     * Level 2 is one long straight corridor, so you see caged bulbs a hundred blocks away, far
     * beyond the lights handled every tick (those within {@link #BUILD_RANGE}). When a section's
     * circuit fails out there, its bulbs must go dark too, or the gap in the row of lights would
     * only appear once you walked up to it. So a few times a second every caged lamp in the loaded
     * chunks is checked (a couple of hashes each, a few hundred lamps) and its block redrawn when
     * its circuit changed. The near lamps were already done this tick, so they don't redraw twice.
     * Far standby emergency lamps light their lenses the same way (the near ones follow their own
     * relay delay in the tick).
     */
    private static void sweepDarkBulbs(Minecraft mc, long gameTime, Vec3 eye) {
        double nearSq = sq(BUILD_RANGE - REBUILD_MOVE - 1);
        var it = INDEX.all().long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            long key = entry.getLongKey();
            int y = BlockPos.getY(key);
            FixtureType type = FixtureType.of(entry.getValue());
            if (type == null || !FixturePower.level2OnCircuit(y)) {
                continue; // the void's doorway lamp far below the floor never goes out
            }
            int x = BlockPos.getX(key);
            int z = BlockPos.getZ(key);
            if (level2CircuitLamp(type)) {
                boolean powered = FixturePower.level2CircuitPowered(FixturePower.level2Circuit(x + 0.5, z + 0.5), gameTime);
                if (powered ? DARK_BULBS.remove(key) : DARK_BULBS.add(key)) {
                    mc.levelRenderer.setBlocksDirty(x, y, z, x, y, z);
                }
            } else if ((type == FixtureType.L2_EMERGENCY_RED || type == FixtureType.L2_EMERGENCY_AMBER)
                    && sq(x + 0.5 - eye.x) + sq(z + 0.5 - eye.z) > nearSq
                    && !entry.getValue().getValue(Level2EmergencyLampBlock.LIT)) {
                boolean lit = FixturePower.level2StandbyOn(x, z, gameTime);
                if (lit ? LIT_STANDBY.add(key) : LIT_STANDBY.remove(key)) {
                    mc.levelRenderer.setBlocksDirty(x, y, z, x, y, z);
                }
            }
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
        ThrownLights.frame(partialTick, System.currentTimeMillis());
        Level0Shading.armed = true; // time this frame's world light pass (not the hand's)
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

    /** Mod bus: the grey tint of a faulty Level 0 tube while it is dark (see block/Level0TubeGlow). */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tintIndex) -> Level0TubeGlow.tint(state, pos),
                ModBlocks.FLUORESCENT_LIGHT.get(), Level0Blocks.TROFFER_STRIP.get());
        // Level 2's emergency lamp lens: its red or amber, bright while the lamp is on, a dark
        // glass while it waits on standby (the lens faces are full-bright, so the tint is all).
        event.register((state, world, pos, tintIndex) -> level2EmergencyTint(state, pos),
                Level2LightBlocks.EMERGENCY_LAMP.get());
    }

    private static int level2EmergencyTint(BlockState state, @Nullable BlockPos pos) {
        boolean amber = state.getValue(Level2EmergencyLampBlock.TONE) == Level2EmergencyLampBlock.Tone.AMBER;
        boolean on = state.getValue(Level2EmergencyLampBlock.LIT) || (pos != null && LIT_STANDBY.contains(pos.asLong()));
        if (amber) {
            return on ? 0xFFA428 : 0x3A2410;
        }
        return on ? 0xFF2A1C : 0x3A1210;
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
        LIT_STANDBY.clear();
        DARK_TUBES.clear();
        FAULTY_TUBES.clear();
        level0TubeShare = 1.0f;
        Level0WallGrid.reset();
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
        Level0Shading.wanted = false;
        Level2Shading.wanted = false;
    }

    private static void freeLights() {
        if (VEIL_LOADED && !IRIS_LOADED) {
            VeilFixtures.freeAll();
            Flashlights.freeAll();
            ThrownLights.freeAll();
        }
    }

    private static void rebuild(ClientLevel level, BlockPos center) {
        List<FixtureLight> built = FixtureClusters.build(INDEX, level, center.getX(), center.getZ(), BUILD_RANGE);
        if (BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_39) {
            // Level 39's facade windows are lit from its bay grid, not from blocks (far too many).
            Level39Lights.addFacadeLights(built, center.getX(), center.getZ());
        }
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
                light.relayAtMs = previous.relayAtMs;
            }
            if (light.type == FixtureType.L0_EMERGENCY_LAMP || light.type == FixtureType.L2_EMERGENCY_RED
                    || light.type == FixtureType.L2_EMERGENCY_AMBER) {
                // lit=true: always on; lit=false: standby (see FixturePower#levelOf).
                BlockState state = INDEX.get(light.key);
                light.alwaysOn = state == null || !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
            }
        }
        if (BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_0) {
            findFaultyTubes(built);
        }
        lights = built;
        builtRevision = INDEX.revision();
        builtX = center.getX();
        builtZ = center.getZ();
    }

    /**
     * Level 0: reads each tube's age from its blocks (its Veil strength, see block/Level0TubeGlow),
     * decides which of the freshly built lights are faulty tubes (by position, see
     * {@link FixturePower#level0Fault}; dying ones by their block), and lets go of the dark glow
     * of tubes that are no longer among them (they moved out of range), so nothing stays grey by
     * mistake.
     *
     * <p>Only the plain single ceiling troffers and the corridor strips can be faulty. Not the
     * two-panel lights (the exit lobby's panels and the pillar hall's double rows), and nothing
     * near an exit sign, a safe-room or office lamp or a Red Rooms lamp.
     */
    private static void findFaultyTubes(List<FixtureLight> built) {
        FAULTY_TUBES.clear();
        for (FixtureLight light : built) {
            if (!level0Tube(light.type) || light.blocks == null) {
                continue;
            }
            // The tube's age: the average over its blocks (a strip's three blocks share one age).
            float veil = 0.0f;
            boolean dying = false;
            for (long pos : light.blocks) {
                BlockState state = INDEX.get(pos);
                Level0TubeGlow.Glow glow = state == null ? Level0TubeGlow.Glow.FULL : Level0TubeGlow.glowOf(state);
                veil += glow.veil;
                dying |= glow == Level0TubeGlow.Glow.DYING;
            }
            light.tubeGlow = veil / light.blocks.length * FixturePower.tubeBrightness(light.key);
            boolean troffer = light.type == FixtureType.FLUORESCENT_LIGHT && light.blocks.length == 1;
            if (!troffer && light.type != FixtureType.L0_TROFFER_STRIP) {
                continue;
            }
            int fault = dying ? FixturePower.TUBE_DYING : FixturePower.level0Fault(light.key);
            if (fault != FixturePower.TUBE_OK && !nearSteadyLamp(light, built)) {
                FAULTY_TUBES.put(light.key, fault);
            }
        }
        if (DARK_TUBES.isEmpty()) {
            return;
        }
        // Tubes still around you keep their dark glow (faulty ones toggling, dead ones in an
        // outage): the tick right after this sets each one right again.
        LongOpenHashSet keep = new LongOpenHashSet();
        for (FixtureLight light : built) {
            if (level0Tube(light.type) && light.blocks != null) {
                for (long pos : light.blocks) {
                    keep.add(pos);
                }
            }
        }
        Minecraft mc = Minecraft.getInstance();
        var darkIt = DARK_TUBES.iterator();
        while (darkIt.hasNext()) {
            long pos = darkIt.next();
            if (!keep.contains(pos)) {
                darkIt.remove();
                redraw(mc, pos);
            }
        }
    }

    /** True if an exit sign, safe-room or office lamp or Red Rooms lamp is within {@link #FAULT_CLEARANCE}. */
    private static boolean nearSteadyLamp(FixtureLight tube, List<FixtureLight> built) {
        for (FixtureLight other : built) {
            FixtureType type = other.type;
            if (type != FixtureType.L0_EXIT_SIGN && type != FixtureType.EXIT_SIGN && type != FixtureType.SAFE_LAMP
                    && type != FixtureType.RED_ALARM_LAMP && type != FixtureType.RED_ROOM_LIGHT) {
                continue;
            }
            if (sq(other.x - tube.x) + sq(other.z - tube.z) < FAULT_CLEARANCE * FAULT_CLEARANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Switches the glow of a faulty tube's blocks off ({@code dark}) or back on. The glow is baked
     * into the chunk mesh, so the blocks are redrawn, but only when their state really changed.
     */
    private static void setTubeDark(Minecraft mc, FixtureLight light, boolean dark) {
        if (light.blocks == null) {
            return;
        }
        for (long pos : light.blocks) {
            if (dark ? DARK_TUBES.add(pos) : DARK_TUBES.remove(pos)) {
                redraw(mc, pos);
            }
        }
    }

    private static void redraw(Minecraft mc, long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        mc.levelRenderer.setBlocksDirty(x, y, z, x, y, z);
    }

    /**
     * Picks the nearest lights within the quality's light range (lights already shown get a small
     * bonus). In Level 9 a few slots of the budget are kept free for the streetlamps' glow cones
     * ({@link VeilFixtures#LAMP_CONES}), so the total never exceeds the quality's maxLights. Level 0
     * gets more lamps reaching further ({@link #LEVEL0_BUDGET_SCALE}). Returns the budget.
     */
    private static int select(BackroomsLevel floor) {
        ClientSettings.Quality quality = ClientSettings.quality();
        boolean level0 = floor == BackroomsLevel.LEVEL_0;
        int budget = floor == BackroomsLevel.LEVEL_9 ? quality.maxLights - VeilFixtures.LAMP_CONES
                : level0 ? quality.maxLights * LEVEL0_BUDGET_SCALE
                : floor == BackroomsLevel.LEVEL_2 ? Math.round(quality.maxLights * LEVEL2_BUDGET_SCALE) : quality.maxLights;
        budget -= ThrownLights.reserved(); // flares and glowsticks share the same budget
        SHOWN.clear();
        for (FixtureLight light : SELECTED) {
            SHOWN.add(light.key);
        }
        SELECTED.clear();
        CANDIDATES.clear();
        double range = level0 ? quality.lightRange * LEVEL0_RANGE_SCALE : lightRange(floor);
        double maxSq = range * range;
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
        return budget;
    }

    private static double sq(double v) {
        return v * v;
    }

    private BackroomsLights() {}
}
