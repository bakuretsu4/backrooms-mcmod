package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.anomaly.AnomalyClientState;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.registry.Level2Audio;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sounds of Level 2's long pipe corridor, played on the client around you. Everything is
 * placed in the world (it comes from a direction and fades with distance), nothing is stuck to
 * your head:
 * <ul>
 *   <li><b>The machine bed</b>: a deep hum from the machinery itself (pumps thump, fan housings
 *       and machine cabinets drone, breaker panels buzz) and from plant rooms behind the
 *       corridor walls, one every ~48 blocks, so the hum swells as you pass one and thins out
 *       between them.</li>
 *   <li><b>Dead sections go quiet</b>: everything on the mains runs on the section's series
 *       circuit. When a circuit fails (or in a stretch whose lamps died for good) its machines
 *       coast down (the hum sinks in pitch and fades) and spin back up when the power returns;
 *       the level's background drone dips about 7 dB while you stand in the dark.</li>
 *   <li><b>Pressure in the pipes</b> every 7-18 seconds (sparser in the dark), always somewhere
 *       up or down the straight line, on one wall or overhead: a water-hammer knock running along
 *       the pipes, a heavy clank, a long creak of hot metal, water rushing over your head, a
 *       shudder running along a pipe run past you, or far metal tapping: a few knocks of a tool on
 *       a pipe, a pause, the same knocks again a little closer along the same pipe. While the
 *       corridor TWISTs the pipes creak far more often.</li>
 *   <li><b>Leaks and valves</b>: a leaking steam valve hisses steadily, wisps steam and now and then
 *       blows a jet; every minute or so a relief valve somewhere near you lifts with a clunk and
 *       vents a burst of steam (the cloud blows out of that valve).</li>
 *   <li><b>Drips</b>: every ceiling drip lets a drop fall on its own slow rhythm; what you hear
 *       when it lands depends on what it hits: a plink in a puddle or the wet gutter, a bright
 *       tick on a grating or a pipe, a dull tap on bare concrete, a gloopy plop in black ooze.</li>
 * </ul>
 * The client has no layout seed for Level 2, so the machines, valves, drips and lamps are found by
 * looking at the blocks around you: a slab of the corridor 65 blocks long is swept a few slices
 * per tick, and the things found are used until the next sweep finishes (under a second later).
 * The plant rooms behind the walls need no blocks: their places come from the corridor's grid.
 *
 * <p>Nothing plays in the void below the floor or the loop hall above it, and Level2Sounds already
 * drops every sound while the void asks for silence. The loudness of each file is set in
 * tools/anomaly_assets/level2_audio.py (in line with the rest of the mix).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Emitters {
    private static final int FLOOR = Level2Sites.FLOOR_Y;
    /** One corridor per this many blocks of Z (Level2Line.BAND); a band's middle is at band * 48. */
    private static final int CORRIDOR_BAND = 48;

    // ---- The block sweep ---------------------------------------------------------------------
    /** Blocks swept either way along the corridor (X) ... */
    private static final int SCAN_ALONG = 32;
    /** ... and across it (Z): the corridor, its pump bays and the side rooms and chambers. */
    private static final int SCAN_ACROSS = 14;
    /** The floor (a flooded sump's pump stands at the floor's height) up to the ceiling band. */
    private static final int SCAN_Y_MIN = FLOOR;
    private static final int SCAN_Y_MAX = FLOOR + 5;
    /** Slices (one x each) swept per tick: 65 slices, so a full sweep takes 13 ticks. */
    private static final int SLICES_PER_TICK = 5;
    /** Blocks of one multi-block thing closer than this count as the same thing (one sound each). */
    private static final int PUMP_MERGE = 3;
    private static final int MACHINE_MERGE = 2;
    private static final int PANEL_MERGE = 1;

    // ---- Machinery, leaks, drips -------------------------------------------------------------
    private static final double PUMP_RANGE = 26.0;
    private static final int MAX_PUMPS = 2;
    private static final float PUMP_VOLUME = 0.9f;
    /** Fan housings and machine cabinets (outside the hot zones, which run their own machines). */
    private static final double MACHINE_RANGE = 30.0;
    private static final int MAX_MACHINES = 2;
    private static final float MACHINE_VOLUME = 0.75f;
    /** Breaker panels and junction boxes: only heard up close. */
    private static final double PANEL_RANGE = 11.0;
    private static final int MAX_PANELS = 2;
    private static final float PANEL_VOLUME = 0.8f;
    private static final double LEAK_RANGE = 16.0;
    private static final int MAX_LEAKS = 3;
    private static final float LEAK_VOLUME = 0.8f;
    /** A leak blows a jet every 5-11 s, for 1-2 s. */
    private static final int JET_PERIOD_MIN = 100;
    private static final int JET_PERIOD_SPREAD = 120;
    private static final int JET_LENGTH_MIN = 20;
    private static final int JET_LENGTH_SPREAD = 20;
    private static final double DRIP_RANGE = 14.0;
    private static final int MAX_DRIPS = 6;
    /** Each drip lets a drop go every 1.5-5 s. */
    private static final int DRIP_PERIOD_MIN = 30;
    private static final int DRIP_PERIOD_SPREAD = 70;

    // ---- The plant behind the walls ----------------------------------------------------------
    /** One plant room per this many blocks along each corridor ... */
    private static final int PLANT_SPACING = 48;
    /** ... moved up to this far either way, so they don't sit on a visible grid ... */
    private static final int PLANT_JITTER = 12;
    /** ... this far from the corridor's middle (behind the wall at |dz| = 4, past the pipe chase) ... */
    private static final double PLANT_BEHIND = 7.5;
    /** ... at mid-height. sounds.json gives the hum a 40-block range; the walls muffle it too. */
    private static final double PLANT_Y = FLOOR + 2.5;
    private static final double PLANT_RANGE = 40.0;
    private static final float PLANT_VOLUME = 0.9f;

    // ---- Dead sections -----------------------------------------------------------------------
    /** A stretch counts as dead for good when the nearest corridor lamp within this many blocks is a dead one. */
    private static final double DARK_REACH = 9.0;
    /** How far the level's background drone dips in a fully dead section (0.45 = about -7 dB). */
    private static final float DEAD_DUCK = 0.55f;
    /** Pressure sounds come this much less often while you stand in the dark. */
    private static final float DEAD_SPARSER = 1.5f;

    // ---- Relief valves -----------------------------------------------------------------------
    /** A relief valve lifts somewhere near you every 45-105 s ... */
    private static final int RELEASE_MIN = 900;
    private static final int RELEASE_SPREAD = 1200;
    /** ... on a valve or vent between these distances (never right in your face, never out of earshot) ... */
    private static final double RELEASE_NEAR = 6.0;
    private static final double RELEASE_FAR = 22.0;
    /** ... and blows steam for 1.6-2.4 s (about as long as the hiss in the recording). */
    private static final int RELEASE_STEAM_MIN = 32;
    private static final int RELEASE_STEAM_SPREAD = 16;
    private static final float RELEASE_VOLUME = 0.8f;

    // ---- Pressure in the pipes ---------------------------------------------------------------
    /** Ticks between two pressure sounds normally (7-18 s) ... */
    private static final int PRESSURE_MIN = 140;
    private static final int PRESSURE_SPREAD = 220;
    /** ... and while the corridor twists (1.5-3.5 s, creaks only). */
    private static final int TWIST_MIN = 30;
    private static final int TWIST_SPREAD = 40;
    /** Overhead pipes run at about this height, the wall racks a block lower. */
    private static final double CEILING_PIPES_Y = FLOOR + 4.5;
    private static final double RACK_Y = FLOOR + 3.5;
    /** The pipe chases are about this far either side of the lane. */
    private static final double CHASE_OFFSET = 2.5;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos SCAN_POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    /** Scratch for the nearest few things (no allocation per tick). */
    private static final long[] NEAREST = new long[8];

    /**
     * One kind of thing the sweep looks for: what the last finished sweep found (packed block
     * positions) and what the sweep in progress has found so far. The two lists swap when a sweep
     * finishes, so they are reused rather than made anew.
     */
    private static final class Found {
        LongArrayList now = new LongArrayList();
        LongArrayList next = new LongArrayList();

        /** Adds {@code pos}, unless something found already lies within {@code merge} blocks (one entry per multi-block thing). */
        void add(long pos, int merge) {
            if (merge > 0) {
                int x = BlockPos.getX(pos);
                int y = BlockPos.getY(pos);
                int z = BlockPos.getZ(pos);
                for (int i = 0; i < next.size(); i++) {
                    long other = next.getLong(i);
                    if (Math.abs(BlockPos.getX(other) - x) <= merge && Math.abs(BlockPos.getZ(other) - z) <= merge
                            && Math.abs(BlockPos.getY(other) - y) <= merge) {
                        return;
                    }
                }
            }
            next.add(pos);
        }

        void finish() {
            LongArrayList swap = now;
            now = next;
            next = swap;
            next.clear();
        }

        void clear() {
            now.clear();
            next.clear();
        }
    }

    private static final Found PUMPS = new Found();
    private static final Found MACHINES = new Found();
    private static final Found PANELS = new Found();
    private static final Found VENTS = new Found();
    private static final Found VALVES = new Found();
    private static final Found DRIPS = new Found();
    /** Corridor lamps that still have a bulb, and the dead ones of a stretch whose lamps died for good. */
    private static final Found LAMPS = new Found();
    private static final Found DEAD_LAMPS = new Found();
    private static final Found[] ALL_FOUND = {PUMPS, MACHINES, PANELS, VENTS, VALVES, DRIPS, LAMPS, DEAD_LAMPS};
    private static boolean sweeping;
    private static int sweepX;
    private static int sweepEndX;
    private static int sweepZ;

    /** The machine and leak loops playing, by the block (or plant room) they belong to. */
    private static final Long2ObjectOpenHashMap<MachineLoopSound> LOOPS = new Long2ObjectOpenHashMap<>();

    /**
     * A one-off sound waiting for its moment (the next knock of a run, a drop landing).
     * {@code variant} non-zero: every sound with the same value plays the same recording (one
     * pipe tapped several times sounds like the same pipe).
     */
    private record Pending(long tick, SoundEvent sound, SoundSource source, double x, double y, double z, float volume,
                           float pitch, long variant) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static long tick;
    private static int ticksToPressure = PRESSURE_MIN;
    private static int ticksToRelease = RELEASE_MIN;
    private static boolean active;
    /** 0..1, eased: how dead the spot you stand in is (its lamps out, its machines stopped). */
    private static float deadness;

    /** The relief valve venting right now (Long.MIN_VALUE: none), where and which way its steam blows, and for how long. */
    private static long releasePos = Long.MIN_VALUE;
    private static long releaseUntil;
    private static double releaseX;
    private static double releaseY;
    private static double releaseZ;
    private static Direction releaseFacing = Direction.UP;

    // Blocks, looked up once by id (some belong to the Level 2 block sets, which may be missing in
    // an older world or not exist yet: then that kind of emitter simply never finds anything).
    private static boolean resolved;
    @Nullable
    private static Block pumpBlock;
    @Nullable
    private static Block dripBlock;
    @Nullable
    private static Block gratingBlock;
    @Nullable
    private static Block gutterBlock;
    @Nullable
    private static Block puddleBlock;
    @Nullable
    private static Block fanBlock;
    @Nullable
    private static Block breakerBlock;
    @Nullable
    private static Block junctionBlock;
    @Nullable
    private static Block valveWheelBlock;
    @Nullable
    private static Block valveLeverBlock;
    @Nullable
    private static Block mercuryLampBlock;
    private static Block ventBlock;
    private static Block machineBlock;
    private static Block oozeBlock;
    private static Block floorGrateBlock;
    private static Block pipeValveBlock;
    private static Block lampBlock;
    private static Block deadLampBlock;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_2) {
            if (active) {
                reset();
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        active = true;
        tick++;
        resolveBlocks();
        sweep(level, mc.player.blockPosition());
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        long time = level.getGameTime();
        // Only in the corridor itself: not down in the void, not up in the loop hall's office.
        if (eye.y > FLOOR && eye.y < FLOOR + Level2Sites.LOOP_RISE) {
            dead(eye, time);
            pumps(mc, eye, time);
            machines(mc, eye, time);
            plants(mc, eye, time);
            leaks(mc, level, eye);
            releases(mc, level, eye);
            drips(level, eye);
            pressure(mc, eye);
        }
        playPending(mc);
        if (tick % 100 == 0) {
            LOOPS.values().removeIf(MachineLoopSound::isStopped);
        }
    }

    /** Left Level 2: forget everything (the loops fade out by themselves once nobody keeps them). */
    private static void reset() {
        active = false;
        sweeping = false;
        for (Found found : ALL_FOUND) {
            found.clear();
        }
        PENDING.clear();
        LOOPS.clear();
        ticksToPressure = PRESSURE_MIN;
        ticksToRelease = RELEASE_MIN;
        releasePos = Long.MIN_VALUE;
        deadness = 0.0f;
    }

    private static void resolveBlocks() {
        if (resolved) {
            return;
        }
        resolved = true;
        pumpBlock = byId("l2_pump");
        dripBlock = byId("l2_drip");
        gratingBlock = byId("l2_grating");
        gutterBlock = byId("l2_gutter");
        puddleBlock = byId("l2_leak_puddle");
        fanBlock = byId("l2_fan_housing");
        breakerBlock = byId("l2_breaker_panel");
        junctionBlock = byId("l2_junction_box");
        valveWheelBlock = byId("l2_valve_wheel");
        valveLeverBlock = byId("l2_valve_lever");
        mercuryLampBlock = byId("l2_mercury_lamp");
        ventBlock = ModBlocks.L2_STEAM_VENT.get();
        machineBlock = ModBlocks.L2_MACHINE.get();
        oozeBlock = ModBlocks.L2_BLACK_OOZE.get();
        floorGrateBlock = ModBlocks.FLOOR_GRATE.get();
        pipeValveBlock = ModBlocks.PIPE_VALVE.get();
        lampBlock = ModBlocks.BULKHEAD_LAMP.get();
        deadLampBlock = ModBlocks.L2_DEAD_LAMP.get();
    }

    @Nullable
    private static Block byId(String id) {
        return BuiltInRegistries.BLOCK.getOptional(Backrooms.id(id)).orElse(null);
    }

    // ==========================================================================================
    // The sweep
    // ==========================================================================================

    /** Sweeps a few more slices of the slab around where you stood when this sweep began. */
    private static void sweep(ClientLevel level, BlockPos center) {
        if (!sweeping) {
            sweeping = true;
            sweepX = center.getX() - SCAN_ALONG;
            sweepEndX = center.getX() + SCAN_ALONG;
            sweepZ = center.getZ();
        }
        for (int slice = 0; slice < SLICES_PER_TICK && sweepX <= sweepEndX; slice++, sweepX++) {
            for (int z = sweepZ - SCAN_ACROSS; z <= sweepZ + SCAN_ACROSS; z++) {
                for (int y = SCAN_Y_MIN; y <= SCAN_Y_MAX; y++) {
                    BlockState state = level.getBlockState(SCAN_POS.set(sweepX, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    Block block = state.getBlock();
                    long pos = SCAN_POS.asLong();
                    if (block == lampBlock || block == mercuryLampBlock) {
                        LAMPS.add(pos, 0);
                    } else if (block == deadLampBlock) {
                        DEAD_LAMPS.add(pos, 0);
                    } else if (block == dripBlock) {
                        DRIPS.add(pos, 0);
                    } else if (block == pumpBlock) {
                        PUMPS.add(pos, PUMP_MERGE);
                    } else if (block == fanBlock || block == machineBlock) {
                        MACHINES.add(pos, MACHINE_MERGE);
                    } else if (block == breakerBlock || block == junctionBlock) {
                        PANELS.add(pos, PANEL_MERGE);
                    } else if (block == valveWheelBlock || block == valveLeverBlock || block == pipeValveBlock) {
                        VALVES.add(pos, 0);
                    } else if (block == ventBlock) {
                        if (!onMachine(level, state)) {
                            VENTS.add(pos, 0);
                        }
                    }
                }
            }
        }
        if (sweepX > sweepEndX) {
            // Done: what we found replaces the last sweep's lists (the old ones are reused next time).
            for (Found found : ALL_FOUND) {
                found.finish();
            }
            sweeping = false;
        }
    }

    /** A steam vent bolted to a hot zone's machine: Level2AnomalyEffects runs those. (A pump's vent is a leak here.) */
    private static boolean onMachine(ClientLevel level, BlockState vent) {
        Direction back = vent.getValue(WallMountBlock.FACING).getOpposite();
        Block behind = level.getBlockState(PROBE.setWithOffset(SCAN_POS, back)).getBlock();
        return behind == machineBlock;
    }

    /**
     * Fills {@link #NEAREST} with the (up to {@code max}) positions in {@code list} nearest to
     * {@code eye} within {@code range}, nearest first; returns how many.
     */
    private static int nearest(LongArrayList list, Vec3 eye, double range, int max) {
        int count = 0;
        double limit = range * range;
        double lastDist = -1.0;
        long lastPos = Long.MIN_VALUE;
        while (count < max) {
            long best = Long.MIN_VALUE;
            double bestDist = limit;
            for (int i = 0; i < list.size(); i++) {
                long pos = list.getLong(i);
                double d = distSq(pos, eye);
                // Strictly after the last one picked (ties broken by position), so none is picked twice.
                if (d < bestDist && (d > lastDist || (d == lastDist && pos > lastPos))) {
                    best = pos;
                    bestDist = d;
                }
            }
            if (best == Long.MIN_VALUE) {
                break;
            }
            NEAREST[count++] = best;
            lastDist = bestDist;
            lastPos = best;
        }
        return count;
    }

    private static double distSq(long pos, Vec3 eye) {
        double dx = BlockPos.getX(pos) + 0.5 - eye.x;
        double dy = BlockPos.getY(pos) + 0.5 - eye.y;
        double dz = BlockPos.getZ(pos) + 0.5 - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    // ==========================================================================================
    // Power: dead sections
    // ==========================================================================================

    /**
     * Is the mains on at (x, z)? Off while that stretch's series circuit is out (the same
     * outages that kill its lamps, see FixturePower), and for good in a stretch whose lamps are
     * all dead (the DARK segments: the nearest corridor lamp is a dead one).
     */
    private static boolean powered(double x, double z, long time) {
        return FixturePower.level2CircuitPowered(FixturePower.level2Circuit(x, z), time) && !darkStretch(x, z);
    }

    /** True when the nearest corridor lamp to (x, z), within {@link #DARK_REACH} along the corridor, has no bulb. */
    private static boolean darkStretch(double x, double z) {
        double best = DARK_REACH * DARK_REACH;
        boolean dead = false;
        LongArrayList lamps = LAMPS.now;
        for (int i = 0; i < lamps.size(); i++) {
            double d = lampDistSq(lamps.getLong(i), x, z);
            if (d < best) {
                best = d;
                dead = false;
            }
        }
        LongArrayList deadLamps = DEAD_LAMPS.now;
        for (int i = 0; i < deadLamps.size(); i++) {
            double d = lampDistSq(deadLamps.getLong(i), x, z);
            if (d < best) {
                best = d;
                dead = true;
            }
        }
        return dead;
    }

    /** Distance along the corridor to a lamp of the same corridor (lamps of another band don't count). */
    private static double lampDistSq(long lamp, double x, double z) {
        if (Math.abs(BlockPos.getZ(lamp) + 0.5 - z) > PLANT_BEHIND + 2.0) {
            return Double.MAX_VALUE;
        }
        double dx = BlockPos.getX(lamp) + 0.5 - x;
        return dx * dx;
    }

    /**
     * How dead the spot you stand in is: the share of the light around you that is out (a popped
     * circuit; BackroomsLights works it out for the lightmap fill), or 1 in a stretch whose lamps
     * died for good. The level's background drone (the biome loop, see Level2Sounds) dips with it.
     */
    private static void dead(Vec3 eye, long time) {
        float target = 1.0f - BackroomsLights.level2LampShare();
        if (darkStretch(eye.x, eye.z)) {
            target = 1.0f;
        }
        // About 2 s to settle: the drone sinks with the dark, it doesn't switch.
        deadness += (Mth.clamp(target, 0.0f, 1.0f) - deadness) * 0.05f;
        if (deadness > 0.01f) {
            Level2Sounds.duckAmbience(1.0f - DEAD_DUCK * deadness);
        }
    }

    // ==========================================================================================
    // Machinery: pumps, fans and cabinets, panels, the plant behind the walls
    // ==========================================================================================

    /** Keeps a hum loop going on the nearest pumps. */
    private static void pumps(Minecraft mc, Vec3 eye, long time) {
        int n = nearest(PUMPS.now, eye, PUMP_RANGE, MAX_PUMPS);
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            // The sweep meets a pump at its lowest-x, lowest-z, lowest cell first (it runs up x,
            // then z, then y), and the pump is 2x2x2: its centre is the far corner of that cell.
            loop(mc, pos, Level2Audio.PUMP_HUM.get(), BlockPos.getX(pos) + 1.0, BlockPos.getY(pos) + 1.0,
                    BlockPos.getZ(pos) + 1.0, PUMP_VOLUME, true, time);
        }
    }

    /** The deep drone of the nearest fan housings and machine cabinets, and the buzz of the nearest live panels. */
    private static void machines(Minecraft mc, Vec3 eye, long time) {
        int n = nearest(MACHINES.now, eye, MACHINE_RANGE, MAX_MACHINES);
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            if (inHotZone(BlockPos.getX(pos), BlockPos.getZ(pos))) {
                continue; // a hot zone's own faulty machinery: Level2AnomalyEffects plays that
            }
            loop(mc, pos, Level2Audio.PLANT_HUM.get(), BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.5,
                    BlockPos.getZ(pos) + 0.5, MACHINE_VOLUME, true, time);
        }
        n = nearest(PANELS.now, eye, PANEL_RANGE, MAX_PANELS);
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            loop(mc, pos, Level2Audio.PANEL_BUZZ.get(), BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.8,
                    BlockPos.getZ(pos) + 0.5, PANEL_VOLUME, true, time);
        }
    }

    /**
     * The plant rooms behind the corridor walls: one per {@link #PLANT_SPACING} blocks of each
     * corridor, on one side or the other (a pure function of the corridor and the slot, so every
     * player hears them in the same places). You hear the one or two in range, muffled by the
     * wall, swelling as you walk past each.
     */
    private static void plants(Minecraft mc, Vec3 eye, long time) {
        long band = Math.floorDiv(Mth.floor(eye.z) + CORRIDOR_BAND / 2, CORRIDOR_BAND);
        int slot = Math.floorDiv(Mth.floor(eye.x), PLANT_SPACING);
        for (int k = slot - 1; k <= slot + 1; k++) {
            long h = FixturePower.mix(band * 0x632BE59BD9B4E019L ^ k * 0x9E3779B97F4A7C15L ^ 0x3C6EF372FE94F82BL);
            int x = k * PLANT_SPACING + PLANT_SPACING / 2 + (int) Math.floorMod(h, 2 * PLANT_JITTER + 1) - PLANT_JITTER;
            double z = band * CORRIDOR_BAND + 0.5 + (((h >>> 40) & 1L) == 0L ? PLANT_BEHIND : -PLANT_BEHIND);
            double dx = x + 0.5 - eye.x;
            double dz = z - eye.z;
            if (dx * dx + dz * dz > PLANT_RANGE * PLANT_RANGE) {
                continue;
            }
            loop(mc, BlockPos.asLong(x, Mth.floor(PLANT_Y), Mth.floor(z)), Level2Audio.PLANT_HUM.get(), x + 0.5, PLANT_Y, z,
                    PLANT_VOLUME, true, time);
        }
    }

    /**
     * Keeps the loop for {@code key} playing (starting it if needed). {@code mains}: it runs on
     * the section's power, and coasts down while that is out.
     */
    private static void loop(Minecraft mc, long key, SoundEvent sound, double x, double y, double z, float volume,
                             boolean mains, long time) {
        MachineLoopSound loop = LOOPS.get(key);
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
            loop = new MachineLoopSound(sound, x, y, z, FixturePower.mix(key), volume, false);
            if (mains) {
                loop.powered(powered(x, z, time)); // a machine already dead when you arrive starts silent
            }
            mc.getSoundManager().play(loop);
            LOOPS.put(key, loop);
        }
        loop.keepAlive();
        if (mains) {
            loop.powered(powered(x, z, time));
        }
    }

    /** Is (x, z) in or right by a hot zone? */
    private static boolean inHotZone(int x, int z) {
        for (AnomalySite site : AnomalyClientState.nearby()) {
            if (Level2Sites.HOT_ZONE.equals(site.kind()) && site.near(x, z, 2)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================================
    // Leaks, relief valves and drips
    // ==========================================================================================

    /** The nearest leaking valves: a steady hiss, wisps of steam, and now and then a jet. */
    private static void leaks(Minecraft mc, ClientLevel level, Vec3 eye) {
        int n = nearest(VENTS.now, eye, LEAK_RANGE, MAX_LEAKS);
        long time = level.getGameTime();
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            BlockState state = level.getBlockState(PROBE.set(pos));
            if (state.getBlock() != ventBlock || hotZoneVent(pos)) {
                continue; // gone since the sweep, or a hot zone's vent (Level2AnomalyEffects blows those)
            }
            Direction facing = state.getValue(WallMountBlock.FACING);
            double fx = facing.getStepX();
            double fz = facing.getStepZ();
            // The nozzle: the front of the vent block (as in Level2AnomalyEffects' steam).
            double px = BlockPos.getX(pos) + 0.5 + fx * 0.45;
            double py = BlockPos.getY(pos) + 0.45;
            double pz = BlockPos.getZ(pos) + 0.5 + fz * 0.45;
            // Steam pressure, not the mains: a leak keeps hissing in a dead section.
            loop(mc, pos, Level2Audio.STEAM_LEAK.get(), px, py, pz, LEAK_VOLUME, false, time);
            // Each leak has its own jet rhythm (from its position and the game time: everyone sees the same).
            long h = FixturePower.mix(pos ^ 0x4C1E5A7D3B9F2E61L);
            long period = JET_PERIOD_MIN + Math.floorMod(h, JET_PERIOD_SPREAD);
            long length = JET_LENGTH_MIN + Math.floorMod(h >>> 16, JET_LENGTH_SPREAD);
            long phase = Math.floorMod(time + (h >>> 32), period);
            if (phase == 0) {
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.L2_STEAM_HISS.get(), SoundSource.BLOCKS,
                        0.55f, 0.9f + RANDOM.nextFloat() * 0.25f, RANDOM, px, py, pz));
            }
            if (phase < length) {
                // The jet: a fast puff straight out from the wall.
                for (int k = 0; k < 2; k++) {
                    double speed = 0.12 + RANDOM.nextDouble() * 0.1;
                    level.addParticle(ParticleTypes.CLOUD, px, py, pz,
                            fx * speed + (RANDOM.nextDouble() - 0.5) * 0.04,
                            0.01 + RANDOM.nextDouble() * 0.03,
                            fz * speed + (RANDOM.nextDouble() - 0.5) * 0.04);
                }
            } else if ((time + i) % 4 == 0) {
                // Between jets a thin wisp curls up from the valve.
                level.addParticle(ParticleTypes.CLOUD, px, py, pz,
                        fx * 0.02 + (RANDOM.nextDouble() - 0.5) * 0.01, 0.02 + RANDOM.nextDouble() * 0.015,
                        fz * 0.02 + (RANDOM.nextDouble() - 0.5) * 0.01);
            }
        }
    }

    /**
     * Is this one of a nearby hot zone's own vents (Level2Sites.vents)? Level2AnomalyEffects blows
     * the steam for those. The vents along a hot zone's approach are ordinary leaks.
     */
    private static boolean hotZoneVent(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        for (AnomalySite site : AnomalyClientState.nearby()) {
            if (!Level2Sites.HOT_ZONE.equals(site.kind()) || !site.near(x, z, 2)) {
                continue;
            }
            for (Level2Sites.Vent vent : Level2Sites.vents(site)) {
                if (vent.x() == x && vent.y() == y && vent.z() == z) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Now and then a relief valve near you lifts: a clunk, a swelling burst of steam out of that
     * valve (or leaking vent) for about two seconds, the valve slapping shut. Picked among the
     * valves and vents between {@link #RELEASE_NEAR} and {@link #RELEASE_FAR} blocks away, so
     * it's close enough to see the cloud but never goes off in your face.
     */
    private static void releases(Minecraft mc, ClientLevel level, Vec3 eye) {
        if (releasePos != Long.MIN_VALUE) {
            if (tick >= releaseUntil) {
                releasePos = Long.MIN_VALUE;
            } else {
                vent(level);
            }
        }
        if (--ticksToRelease > 0) {
            return;
        }
        long pos = pickRelease(eye);
        if (pos == Long.MIN_VALUE) {
            ticksToRelease = 100; // nothing in reach: look again in 5 s
            return;
        }
        ticksToRelease = RELEASE_MIN + RANDOM.nextInt(RELEASE_SPREAD);
        BlockState state = level.getBlockState(PROBE.set(pos));
        releaseFacing = state.hasProperty(WallMountBlock.FACING) ? state.getValue(WallMountBlock.FACING) : Direction.UP;
        releaseX = BlockPos.getX(pos) + 0.5 + releaseFacing.getStepX() * 0.45;
        releaseY = BlockPos.getY(pos) + 0.5 + releaseFacing.getStepY() * 0.45;
        releaseZ = BlockPos.getZ(pos) + 0.5 + releaseFacing.getStepZ() * 0.45;
        releasePos = pos;
        // The steam starts a tick after the clunk (the recording's hiss swells in after 60 ms).
        releaseUntil = tick + 1 + RELEASE_STEAM_MIN + RANDOM.nextInt(RELEASE_STEAM_SPREAD);
        mc.getSoundManager().play(new SimpleSoundInstance(Level2Audio.PRESSURE_RELEASE.get(), SoundSource.BLOCKS,
                RELEASE_VOLUME, 0.92f + RANDOM.nextFloat() * 0.16f, RANDOM, releaseX, releaseY, releaseZ));
    }

    /** A random valve or (non hot zone) vent in the release ring, or Long.MIN_VALUE. */
    private static long pickRelease(Vec3 eye) {
        int valves = VALVES.now.size();
        int total = valves + VENTS.now.size();
        if (total == 0) {
            return Long.MIN_VALUE;
        }
        int start = RANDOM.nextInt(total);
        for (int i = 0; i < total; i++) {
            int index = (start + i) % total;
            long pos = index < valves ? VALVES.now.getLong(index) : VENTS.now.getLong(index - valves);
            double d = distSq(pos, eye);
            if (d >= RELEASE_NEAR * RELEASE_NEAR && d <= RELEASE_FAR * RELEASE_FAR && !hotZoneVent(pos)) {
                return pos;
            }
        }
        return Long.MIN_VALUE;
    }

    /** One tick of a relief valve's steam: a thick, fast cloud out of the valve, thinning out at the end. */
    private static void vent(ClientLevel level) {
        long left = releaseUntil - tick;
        int puffs = left > 8 ? 3 : 1;
        double fx = releaseFacing.getStepX();
        double fy = releaseFacing.getStepY();
        double fz = releaseFacing.getStepZ();
        for (int k = 0; k < puffs; k++) {
            double speed = 0.16 + RANDOM.nextDouble() * 0.12;
            level.addParticle(ParticleTypes.CLOUD, releaseX, releaseY, releaseZ,
                    fx * speed + (RANDOM.nextDouble() - 0.5) * 0.06,
                    fy * speed + 0.015 + RANDOM.nextDouble() * 0.03,
                    fz * speed + (RANDOM.nextDouble() - 0.5) * 0.06);
        }
    }

    /** The nearest drips each let a drop fall now and then; the sound comes when it lands. */
    private static void drips(ClientLevel level, Vec3 eye) {
        int n = nearest(DRIPS.now, eye, DRIP_RANGE, MAX_DRIPS);
        long time = level.getGameTime();
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            long h = FixturePower.mix(pos ^ 0x1D8E4E27C47D124FL);
            long period = DRIP_PERIOD_MIN + Math.floorMod(h, DRIP_PERIOD_SPREAD);
            if (Math.floorMod(time + (h >>> 32), period) == 0) {
                drop(level, pos);
            }
        }
    }

    /** One drop: a falling water particle now, and the sound of what it hits when it lands. */
    private static void drop(ClientLevel level, long drip) {
        int x = BlockPos.getX(drip);
        int y = BlockPos.getY(drip);
        int z = BlockPos.getZ(drip);
        if (level.getBlockState(PROBE.set(x, y, z)).getBlock() != dripBlock) {
            return; // gone since the sweep
        }
        double startY = y + 0.1;
        // Find what it lands on: the first block below with a top to hit, or something wet to fall
        // into (a puddle, black ooze, water) even if you can walk through it.
        double landY = FLOOR + 1.0;
        BlockState landed = null;
        for (int by = y - 1; by >= FLOOR - 2; by--) {
            BlockState state = level.getBlockState(PROBE.set(x, by, z));
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            VoxelShape shape = state.getCollisionShape(level, PROBE);
            boolean wet = block == oozeBlock || block == puddleBlock || !state.getFluidState().isEmpty();
            if (wet || !shape.isEmpty()) {
                if (shape.isEmpty()) {
                    VoxelShape outline = state.getShape(level, PROBE);
                    landY = by + (outline.isEmpty() ? 0.1 : outline.max(Direction.Axis.Y));
                } else {
                    landY = by + shape.max(Direction.Axis.Y);
                }
                landed = state;
                break;
            }
        }
        double px = x + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2;
        double pz = z + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2;
        level.addParticle(ParticleTypes.FALLING_WATER, px, startY, pz, 0.0, 0.0, 0.0);
        long lands = tick + fallTicks(startY - landY);
        SoundEvent sound;
        float volume;
        float pitch;
        if (landed == null) {
            return; // fell into the dark below the trench: nothing to hear
        } else if (landed.getBlock() == oozeBlock) {
            sound = ModSounds.L2_OOZE_DRIP.get();
            volume = 0.35f;
            pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        } else if (landed.getBlock() == puddleBlock || landed.getBlock() == gutterBlock || !landed.getFluidState().isEmpty()) {
            // Into standing water: the plink of the bubble it traps.
            sound = Level2Audio.DRIP_PUDDLE.get();
            volume = 0.5f;
            pitch = 0.88f + RANDOM.nextFloat() * 0.24f;
        } else if (metal(landed)) {
            // On steel (a grating, the top of a pipe): a bright little tick and ping.
            sound = Level2Audio.DRIP_METAL.get();
            volume = 0.45f;
            pitch = 0.92f + RANDOM.nextFloat() * 0.18f;
        } else {
            // Bare wet concrete: a soft dull tap.
            sound = Level2Audio.DRIP_CONCRETE.get();
            volume = 0.5f;
            pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        }
        PENDING.add(new Pending(lands, sound, SoundSource.BLOCKS, px, landY, pz, volume, pitch, 0L));
    }

    /** Steel, iron or copper underneath: the gratings, and any block that sounds like metal when you walk on it (pipes, plates). */
    private static boolean metal(BlockState state) {
        Block block = state.getBlock();
        if (block == floorGrateBlock || block == gratingBlock) {
            return true;
        }
        SoundType type = state.getSoundType();
        return type == SoundType.METAL || type == SoundType.COPPER || type == SoundType.COPPER_GRATE
                || type == SoundType.CHAIN || type == SoundType.NETHERITE_BLOCK || type == SoundType.LANTERN
                || type == SoundType.ANVIL;
    }

    /**
     * Ticks a falling water particle takes to drop {@code height} blocks (Minecraft's drip
     * particles speed up by 0.06 blocks per tick each tick and lose 2% to drag).
     */
    private static int fallTicks(double height) {
        double v = 0.0;
        double fallen = 0.0;
        int ticks = 0;
        while (fallen < height && ticks < 60) {
            v += 0.06;
            fallen += v;
            v *= 0.98;
            ticks++;
        }
        return ticks;
    }

    // ==========================================================================================
    // Pressure in the pipes
    // ==========================================================================================

    private static void pressure(Minecraft mc, Vec3 eye) {
        boolean twist = CameraClientState.eventKind == LevelEventPayload.TWIST
                && System.currentTimeMillis() < CameraClientState.eventEndMs;
        if (twist && ticksToPressure > TWIST_MIN + TWIST_SPREAD) {
            ticksToPressure = TWIST_MIN + RANDOM.nextInt(TWIST_SPREAD); // the twist just began
        }
        if (--ticksToPressure > 0) {
            return;
        }
        if (twist) {
            ticksToPressure = TWIST_MIN + RANDOM.nextInt(TWIST_SPREAD);
            creak(eye, true);
            return;
        }
        boolean dark = deadness > 0.5f;
        int next = PRESSURE_MIN + RANDOM.nextInt(PRESSURE_SPREAD);
        // In the dark the pipes are quieter, and what you do hear is more often the tapping.
        ticksToPressure = dark ? Math.round(next * DEAD_SPARSER) : next;
        if (dark && RANDOM.nextInt(3) == 0) {
            tapRun(eye);
            return;
        }
        int roll = RANDOM.nextInt(20);
        if (roll < 5) {
            knockRun(eye);
        } else if (roll < 7) {
            clank(eye);
        } else if (roll < 11) {
            creak(eye, false);
        } else if (roll < 14) {
            mc.getSoundManager().play(new TravellingSound(Level2Audio.PIPE_RUSH.get(), eye, CEILING_PIPES_Y, eye.z,
                    22.0, 26.0, 0.52, 0.85f));
        } else if (roll < 17) {
            tapRun(eye);
        } else {
            shudder(mc, eye);
        }
    }

    /** -1 or +1: ahead of you or behind, along the corridor. */
    private static double side() {
        return RANDOM.nextBoolean() ? 1.0 : -1.0;
    }

    /** One of the two pipe chases (or the overhead bundle, half the time for knocks). */
    private static double chaseZ(Vec3 eye) {
        return eye.z + side() * CHASE_OFFSET;
    }

    /**
     * Water hammer: a slug of water slams through the pipes and every bracket it passes knocks,
     * so a row of 3-7 knocks runs along the corridor at 14-26 blocks a second, usually toward you
     * (sometimes past you), sometimes away.
     */
    private static void knockRun(Vec3 eye) {
        double from = side();
        double startX = eye.x + from * Mth.nextDouble(RANDOM, 10.0, 28.0);
        double travel = RANDOM.nextFloat() < 0.65f ? -from : from;
        double spacing = Mth.nextDouble(RANDOM, 2.0, 4.0);
        double speed = Mth.nextDouble(RANDOM, 14.0, 26.0);
        int count = 3 + RANDOM.nextInt(5);
        double y = RANDOM.nextBoolean() ? CEILING_PIPES_Y : RACK_Y;
        double z = y == CEILING_PIPES_Y ? eye.z : chaseZ(eye);
        float pitch = 0.85f + RANDOM.nextFloat() * 0.3f;
        for (int i = 0; i < count; i++) {
            long delay = Math.round(spacing * i / speed * 20.0);
            // The first knock is the hardest; the slug loses a little each time.
            float volume = 0.85f * (1.0f - 0.07f * i);
            PENDING.add(new Pending(tick + delay, Level2Audio.PIPE_KNOCK.get(), SoundSource.AMBIENT,
                    startX + travel * spacing * i, y, z, volume, pitch * (0.97f + RANDOM.nextFloat() * 0.06f), 0L));
        }
    }

    /**
     * Far metal tapping: 2-4 knocks of a tool on one pipe, steady like a hand, a pause of 2-5 s,
     * and the same number of knocks again, 4-8 blocks closer along the same pipe; two to four
     * times. It never comes nearer than 10 blocks, and nothing is ever there.
     */
    private static void tapRun(Vec3 eye) {
        double from = side();
        double distance = Mth.nextDouble(RANDOM, 26.0, 44.0);
        double step = Mth.nextDouble(RANDOM, 4.0, 8.0);
        double y = RANDOM.nextBoolean() ? CEILING_PIPES_Y : RACK_Y;
        double z = y == CEILING_PIPES_Y ? eye.z : chaseZ(eye);
        int groups = 2 + RANDOM.nextInt(3);
        int taps = 2 + RANDOM.nextInt(3);
        float pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        long variant = RANDOM.nextLong() | 1L; // the same pipe every time
        long at = tick;
        for (int g = 0; g < groups; g++) {
            double x = eye.x + from * Math.max(10.0, distance - g * step);
            for (int t = 0; t < taps; t++) {
                PENDING.add(new Pending(at, Level2Audio.PIPE_TAP.get(), SoundSource.AMBIENT, x, y, z, 0.8f,
                        pitch * (0.99f + RANDOM.nextFloat() * 0.02f), variant));
                at += 7 + RANDOM.nextInt(4);
            }
            at += 40 + RANDOM.nextInt(60);
        }
    }

    /** A shudder running along a pipe run: from ~25 blocks one way, past you, to ~26 blocks the other, in about 7.5 s. */
    private static void shudder(Minecraft mc, Vec3 eye) {
        double y = RANDOM.nextBoolean() ? CEILING_PIPES_Y : RACK_Y;
        double z = y == CEILING_PIPES_Y ? eye.z : chaseZ(eye);
        mc.getSoundManager().play(new TravellingSound(Level2Audio.PIPE_SHUDDER.get(), eye, y, z, 25.0, 26.0, 0.34, 0.8f));
    }

    /** A single heavy clank somewhere along the line, sometimes answered by a smaller knock. */
    private static void clank(Vec3 eye) {
        double x = eye.x + side() * Mth.nextDouble(RANDOM, 8.0, 30.0);
        double z = chaseZ(eye);
        PENDING.add(new Pending(tick, Level2Audio.PIPE_CLANK.get(), SoundSource.AMBIENT, x, CEILING_PIPES_Y, z,
                0.9f, 0.9f + RANDOM.nextFloat() * 0.2f, 0L));
        if (RANDOM.nextBoolean()) {
            PENDING.add(new Pending(tick + 6 + RANDOM.nextInt(9), Level2Audio.PIPE_KNOCK.get(), SoundSource.AMBIENT,
                    x + side() * Mth.nextDouble(RANDOM, 1.0, 2.5), RACK_Y, z, 0.5f, 0.8f + RANDOM.nextFloat() * 0.15f, 0L));
        }
    }

    /** Hot metal stretching: a long creak from a pipe a little way off (closer during a twist). */
    private static void creak(Vec3 eye, boolean twist) {
        double x = eye.x + side() * (twist ? Mth.nextDouble(RANDOM, 3.0, 14.0) : Mth.nextDouble(RANDOM, 5.0, 20.0));
        double y = RANDOM.nextBoolean() ? CEILING_PIPES_Y : RACK_Y;
        PENDING.add(new Pending(tick, Level2Audio.PIPE_CREAK.get(), SoundSource.AMBIENT, x, y, chaseZ(eye),
                twist ? 0.8f : 0.7f, (twist ? 0.75f : 0.85f) + RANDOM.nextFloat() * 0.3f, 0L));
    }

    private static void playPending(Minecraft mc) {
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (p.tick() > tick) {
                continue;
            }
            PENDING.remove(i);
            // The recording is picked with this random: the same seed picks the same one.
            RandomSource random = p.variant() != 0L ? RandomSource.create(p.variant()) : RANDOM;
            mc.getSoundManager().play(new SimpleSoundInstance(p.sound(), p.source(), p.volume(), p.pitch(), random,
                    p.x(), p.y(), p.z()));
        }
    }

    /**
     * A sound that really moves along the pipes while it plays: from {@code startDistance}
     * blocks up the corridor (or down it), past you, to {@code endDistance} blocks the other way,
     * at {@code speed} blocks per tick. Water rushing through the overhead bundle (44 blocks in
     * ~4.2 s, the length of the recording), or a shudder running along a pipe run.
     */
    private static final class TravellingSound extends AbstractTickableSoundInstance {
        private final double step;
        private final double endX;

        TravellingSound(SoundEvent sound, Vec3 eye, double y, double z, double startDistance, double endDistance,
                        double speed, float volume) {
            super(sound, SoundSource.AMBIENT, RandomSource.create(RANDOM.nextLong()));
            double from = side();
            this.x = eye.x + from * startDistance;
            this.y = y;
            this.z = z;
            this.step = -from * speed;
            this.endX = eye.x - from * endDistance;
            this.volume = volume;
            this.pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
            this.looping = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
        }

        @Override
        public void tick() {
            x += step;
            if ((step > 0 && x > endX) || (step < 0 && x < endX)) {
                stop();
            }
        }
    }

    private Level2Emitters() {}
}
