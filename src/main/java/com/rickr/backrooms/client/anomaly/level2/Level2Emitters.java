package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.registry.Level2Audio;
import com.rickr.backrooms.registry.Level2SpinBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import com.rickr.backrooms.world.layout.Level2Twist;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sounds of Level 2's long, one-block-wide pipe corridor, played on the client around you.
 * Everything is placed in the world (it comes from a direction and fades with distance), nothing
 * is stuck to your head:
 * <ul>
 *   <li><b>Pressure in the pipes</b> every 7-18 seconds (sparser in the dark), always in the pipe
 *       bundle right beside the lane, somewhere up or down the straight line: a water-hammer knock
 *       running along the pipes, a heavy clank, a long creak of hot metal, water rushing through
 *       the bundle past you, a shudder running along a pipe run, or far metal tapping: a few
 *       knocks of a tool on a pipe, a pause, the same knocks again a little closer along the same
 *       pipe. The bundle corkscrews round the lane ({@link Level2Twist#helix}), so a run of knocks
 *       or a rush that travels along it also turns round you as it passes. While the corridor
 *       TWISTs the pipes creak far more often.</li>
 *   <li><b>Wall fans</b>: the nearest fans set into the walls drone.</li>
 *   <li><b>Dead sections</b>: in a stretch whose lamps are out (a popped series circuit, or lamps
 *       dead for good) the level's background drone dips about 7 dB.</li>
 *   <li><b>Leaks and valves</b>: a leaking steam valve hisses steadily, wisps steam and now and then
 *       blows a jet; every minute or so a relief valve somewhere near you lifts with a clunk and
 *       vents a burst of steam (the cloud blows out of that valve).</li>
 *   <li><b>Drips</b>: every ceiling drip lets a drop fall on its own slow rhythm; what you hear
 *       when it lands depends on what it hits: a plink in a puddle, a bright tick on a grating or
 *       a pipe, a dull tap on bare concrete, a gloopy plop in black ooze.</li>
 * </ul>
 * The fans, valves, drips and lamps are found by looking at the blocks around you: a slab of the
 * corridor 65 blocks long is swept a few slices per tick, and the things found are used until the
 * next sweep finishes (a quarter of a second later).
 *
 * <p>The slow orbiting soundscape of the level turning is {@link Level2Orbit}. The loudness of each
 * file is set in tools/anomaly_assets/level2_audio.py (in line with the rest of the mix).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Emitters {
    private static final int FLOOR = Level2Sites.FLOOR_Y;
    /** One corridor per this many blocks of Z (Level2Line.BAND). */
    private static final int CORRIDOR_BAND = 48;

    // ---- The block sweep ---------------------------------------------------------------------
    /** Blocks swept either way along the corridor (X) ... */
    private static final int SCAN_ALONG = 32;
    /** ... and across it (Z): the walls beside the lane, and the exit recess (2 deep, bulkhead at 3). */
    private static final int SCAN_ACROSS = 3;
    /** The lane's two clear blocks and the ceiling cell over it (drips, light panels, beacons). */
    private static final int SCAN_Y_MIN = FLOOR + 1;
    private static final int SCAN_Y_MAX = FLOOR + 3;
    /** Slices (one x each) swept per tick: 65 slices, so a full sweep takes 5 ticks. */
    private static final int SLICES_PER_TICK = 13;

    // ---- Where the sounds can be ---------------------------------------------------------------
    /** Above the floor and under the ceiling cell's top (y44 and up is solid rock). */
    private static final double HEAR_MIN_Y = FLOOR;
    private static final double HEAR_MAX_Y = FLOOR + 4;
    /**
     * The pipe bundle wraps the one-block lane: a pipe in it sits this far across from the lane's
     * middle (in the wall cells beside you) ...
     */
    private static final double BUNDLE_ACROSS = 1.3;
    /** ... or this far above or below it (the ceiling cell at y43, the run under the floor grating) ... */
    private static final double BUNDLE_UP = 1.5;
    /** ... measured from the lane's middle height (the clear y41-42). */
    private static final double LANE_MID_Y = FLOOR + 2.0;
    /** The bundle fills cells up to 67.5 degrees round from its centre: a sound sits within this. */
    private static final double BUNDLE_SPREAD = Math.toRadians(60.0);
    /** Where the bundle is taken to be before the layout seed has arrived: overhead. */
    private static final double NO_SEED_HELIX = Math.PI / 2.0;

    // ---- Fans, leaks, drips ------------------------------------------------------------------
    /** The wall fans' drone: the nearest two within this many blocks. */
    private static final double FAN_RANGE = 24.0;
    private static final int MAX_FANS = 2;
    private static final float FAN_VOLUME = 0.6f;
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

    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos SCAN_POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    /** Scratch for the nearest few things (no allocation per tick). */
    private static final long[] NEAREST = new long[8];
    /** Scratch: where {@link #bundleAt} put the pipe. */
    private static double bundleY;
    private static double bundleZ;

    /**
     * One kind of thing the sweep looks for: what the last finished sweep found (packed block
     * positions) and what the sweep in progress has found so far. The two lists swap when a sweep
     * finishes, so they are reused rather than made anew.
     */
    private static final class Found {
        LongArrayList now = new LongArrayList();
        LongArrayList next = new LongArrayList();

        void add(long pos) {
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

    private static final Found FANS = new Found();
    private static final Found VENTS = new Found();
    private static final Found VALVES = new Found();
    private static final Found DRIPS = new Found();
    /** Corridor lamps that still have a bulb, and the dead ones of a stretch whose lamps died for good. */
    private static final Found LAMPS = new Found();
    private static final Found DEAD_LAMPS = new Found();
    private static final Found[] ALL_FOUND = {FANS, VENTS, VALVES, DRIPS, LAMPS, DEAD_LAMPS};
    private static boolean sweeping;
    private static int sweepX;
    private static int sweepEndX;
    private static int sweepZ;

    /** The fan and leak loops playing, by the block they belong to. */
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
    /** 0..1, eased: how dead the spot you stand in is (its lamps out). */
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
    private static Block dripBlock;
    @Nullable
    private static Block gratingBlock;
    @Nullable
    private static Block puddleBlock;
    @Nullable
    private static Block valveWheelBlock;
    @Nullable
    private static Block valveLeverBlock;
    @Nullable
    private static Block mercuryLampBlock;
    /** The corridor's recessed light panel; unlit = dead for good. */
    @Nullable
    private static Block lightPanelBlock;
    private static Block fanBlock;
    private static Block ventBlock;
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
        // Only in the corridor itself, not noclipping through the rock.
        if (eye.y > HEAR_MIN_Y && eye.y < HEAR_MAX_Y) {
            dead(eye);
            fans(mc, eye);
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
        dripBlock = byId("l2_drip");
        gratingBlock = byId("l2_grating");
        puddleBlock = byId("l2_leak_puddle");
        valveWheelBlock = byId("l2_valve_wheel");
        valveLeverBlock = byId("l2_valve_lever");
        mercuryLampBlock = byId("l2_mercury_lamp");
        lightPanelBlock = byId("l2_light_panel");
        fanBlock = Level2SpinBlocks.WALL_FAN.get();
        ventBlock = ModBlocks.L2_STEAM_VENT.get();
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
                        LAMPS.add(pos);
                    } else if (block == deadLampBlock) {
                        DEAD_LAMPS.add(pos);
                    } else if (block == lightPanelBlock) {
                        boolean lit = !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
                        (lit ? LAMPS : DEAD_LAMPS).add(pos);
                    } else if (block == dripBlock) {
                        DRIPS.add(pos);
                    } else if (block == fanBlock) {
                        FANS.add(pos);
                    } else if (block == valveWheelBlock || block == valveLeverBlock || block == pipeValveBlock) {
                        VALVES.add(pos);
                    } else if (block == ventBlock) {
                        VENTS.add(pos);
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
    // Dead sections
    // ==========================================================================================

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
        if (Math.abs(BlockPos.getZ(lamp) + 0.5 - z) > CORRIDOR_BAND / 2.0) {
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
    private static void dead(Vec3 eye) {
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
    // Wall fans, leaks, relief valves and drips
    // ==========================================================================================

    /**
     * The drone of the nearest wall fans. They are not on the mains (their blades turn with the
     * level, see Level2SpinProps), so they keep droning in a dead section.
     */
    private static void fans(Minecraft mc, Vec3 eye) {
        int n = nearest(FANS.now, eye, FAN_RANGE, MAX_FANS);
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            loop(mc, pos, Level2Audio.PLANT_HUM.get(), BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.5,
                    BlockPos.getZ(pos) + 0.5, FAN_VOLUME);
        }
    }

    /** Keeps the loop for {@code key} playing (starting it if needed). */
    private static void loop(Minecraft mc, long key, SoundEvent sound, double x, double y, double z, float volume) {
        MachineLoopSound loop = LOOPS.get(key);
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
            loop = new MachineLoopSound(sound, x, y, z, FixturePower.mix(key), volume);
            mc.getSoundManager().play(loop);
            LOOPS.put(key, loop);
        }
        loop.keepAlive();
    }

    /** The nearest leaking valves: a steady hiss, wisps of steam, and now and then a jet. */
    private static void leaks(Minecraft mc, ClientLevel level, Vec3 eye) {
        int n = nearest(VENTS.now, eye, LEAK_RANGE, MAX_LEAKS);
        long time = level.getGameTime();
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            BlockState state = level.getBlockState(PROBE.set(pos));
            if (state.getBlock() != ventBlock) {
                continue; // gone since the sweep
            }
            Direction facing = state.getValue(WallMountBlock.FACING);
            double fx = facing.getStepX();
            double fz = facing.getStepZ();
            // The nozzle: the front of the vent block.
            double px = BlockPos.getX(pos) + 0.5 + fx * 0.45;
            double py = BlockPos.getY(pos) + 0.45;
            double pz = BlockPos.getZ(pos) + 0.5 + fz * 0.45;
            // Steam pressure, not the mains: a leak keeps hissing in a dead section.
            loop(mc, pos, Level2Audio.STEAM_LEAK.get(), px, py, pz, LEAK_VOLUME);
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

    /** A random valve or vent in the release ring, or Long.MIN_VALUE. */
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
            if (d >= RELEASE_NEAR * RELEASE_NEAR && d <= RELEASE_FAR * RELEASE_FAR) {
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
            return; // nothing below it within reach: nothing to hear
        } else if (landed.getBlock() == oozeBlock) {
            sound = ModSounds.L2_OOZE_DRIP.get();
            volume = 0.35f;
            pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        } else if (landed.getBlock() == puddleBlock || !landed.getFluidState().isEmpty()) {
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
            mc.getSoundManager().play(new TravellingSound(Level2Audio.PIPE_RUSH.get(), eye, 22.0, 26.0, 0.52, 0.85f));
        } else if (roll < 17) {
            tapRun(eye);
        } else {
            mc.getSoundManager().play(new TravellingSound(Level2Audio.PIPE_SHUDDER.get(), eye, 25.0, 26.0, 0.34, 0.8f));
        }
    }

    /** -1 or +1: ahead of you or behind, along the corridor. */
    private static double side() {
        return RANDOM.nextBoolean() ? 1.0 : -1.0;
    }

    /** A random spot round the bundle's cross-section (radians off its centre). */
    private static double round() {
        return (RANDOM.nextDouble() * 2.0 - 1.0) * BUNDLE_SPREAD;
    }

    /** The middle of the lane you stand in (the corridor is one block wide). */
    private static double laneZ(Vec3 eye) {
        return Mth.floor(eye.z) + 0.5;
    }

    /**
     * Puts {@link #bundleY}/{@link #bundleZ} on a pipe of the bundle at {@code x}: where the bundle
     * sits round the lane there ({@link Level2Twist#helix}: 0 = the +z wall, PI/2 = overhead), plus
     * {@code round} radians. Following x along the corridor, it corkscrews with the pipes.
     */
    private static void bundleAt(double x, double laneZ, double round) {
        double helix = Level2Spin.hasSeed()
                ? Level2Twist.helix(Level2Spin.seed(), Mth.floor(x), Mth.floor(laneZ)) : NO_SEED_HELIX;
        double angle = helix + round;
        bundleZ = laneZ + Math.cos(angle) * BUNDLE_ACROSS;
        bundleY = LANE_MID_Y + Math.sin(angle) * BUNDLE_UP;
    }

    /**
     * Water hammer: a slug of water slams through the pipes and every bracket it passes knocks,
     * so a row of 3-7 knocks runs along the corridor at 14-26 blocks a second, usually toward you
     * (sometimes past you), sometimes away, turning round you with the bundle.
     */
    private static void knockRun(Vec3 eye) {
        double from = side();
        double startX = eye.x + from * Mth.nextDouble(RANDOM, 10.0, 28.0);
        double travel = RANDOM.nextFloat() < 0.65f ? -from : from;
        double spacing = Mth.nextDouble(RANDOM, 2.0, 4.0);
        double speed = Mth.nextDouble(RANDOM, 14.0, 26.0);
        int count = 3 + RANDOM.nextInt(5);
        double lane = laneZ(eye);
        double round = round(); // one pipe of the bundle
        float pitch = 0.85f + RANDOM.nextFloat() * 0.3f;
        for (int i = 0; i < count; i++) {
            long delay = Math.round(spacing * i / speed * 20.0);
            double x = startX + travel * spacing * i;
            bundleAt(x, lane, round);
            // The first knock is the hardest; the slug loses a little each time.
            float volume = 0.85f * (1.0f - 0.07f * i);
            PENDING.add(new Pending(tick + delay, Level2Audio.PIPE_KNOCK.get(), SoundSource.AMBIENT,
                    x, bundleY, bundleZ, volume, pitch * (0.97f + RANDOM.nextFloat() * 0.06f), 0L));
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
        double lane = laneZ(eye);
        double round = round();
        int groups = 2 + RANDOM.nextInt(3);
        int taps = 2 + RANDOM.nextInt(3);
        float pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        long variant = RANDOM.nextLong() | 1L; // the same pipe every time
        long at = tick;
        for (int g = 0; g < groups; g++) {
            double x = eye.x + from * Math.max(10.0, distance - g * step);
            bundleAt(x, lane, round);
            for (int t = 0; t < taps; t++) {
                PENDING.add(new Pending(at, Level2Audio.PIPE_TAP.get(), SoundSource.AMBIENT, x, bundleY, bundleZ, 0.8f,
                        pitch * (0.99f + RANDOM.nextFloat() * 0.02f), variant));
                at += 7 + RANDOM.nextInt(4);
            }
            at += 40 + RANDOM.nextInt(60);
        }
    }

    /** A single heavy clank somewhere along the line, sometimes answered by a smaller knock. */
    private static void clank(Vec3 eye) {
        double x = eye.x + side() * Mth.nextDouble(RANDOM, 8.0, 30.0);
        double lane = laneZ(eye);
        double round = round();
        bundleAt(x, lane, round);
        PENDING.add(new Pending(tick, Level2Audio.PIPE_CLANK.get(), SoundSource.AMBIENT, x, bundleY, bundleZ,
                0.9f, 0.9f + RANDOM.nextFloat() * 0.2f, 0L));
        if (RANDOM.nextBoolean()) {
            double kx = x + side() * Mth.nextDouble(RANDOM, 1.0, 2.5);
            bundleAt(kx, lane, round);
            PENDING.add(new Pending(tick + 6 + RANDOM.nextInt(9), Level2Audio.PIPE_KNOCK.get(), SoundSource.AMBIENT,
                    kx, bundleY, bundleZ, 0.5f, 0.8f + RANDOM.nextFloat() * 0.15f, 0L));
        }
    }

    /** Hot metal stretching: a long creak from a pipe a little way off (closer during a twist). */
    private static void creak(Vec3 eye, boolean twist) {
        double x = eye.x + side() * (twist ? Mth.nextDouble(RANDOM, 3.0, 14.0) : Mth.nextDouble(RANDOM, 5.0, 20.0));
        bundleAt(x, laneZ(eye), round());
        PENDING.add(new Pending(tick, Level2Audio.PIPE_CREAK.get(), SoundSource.AMBIENT, x, bundleY, bundleZ,
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
     * at {@code speed} blocks per tick, following one pipe of the bundle as it corkscrews round the
     * lane (so it also turns round you as it passes). Water rushing through the bundle (44 blocks
     * in ~4.2 s, the length of the recording), or a shudder running along a pipe run.
     */
    private static final class TravellingSound extends AbstractTickableSoundInstance {
        private final double step;
        private final double endX;
        private final double lane;
        private final double round;

        TravellingSound(SoundEvent sound, Vec3 eye, double startDistance, double endDistance, double speed, float volume) {
            super(sound, SoundSource.AMBIENT, RandomSource.create(RANDOM.nextLong()));
            double from = side();
            this.lane = laneZ(eye);
            this.round = round();
            this.x = eye.x + from * startDistance;
            this.step = -from * speed;
            this.endX = eye.x - from * endDistance;
            follow();
            this.volume = volume;
            this.pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
            this.looping = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
        }

        private void follow() {
            bundleAt(x, lane, round);
            y = bundleY;
            z = bundleZ;
        }

        @Override
        public void tick() {
            x += step;
            follow();
            if ((step > 0 && x > endX) || (step < 0 && x < endX)) {
                stop();
            }
        }
    }

    private Level2Emitters() {}
}
