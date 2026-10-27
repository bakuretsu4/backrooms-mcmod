package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.anomaly.AnomalyClientState;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sounds of Level 2's long pipe corridor, played on the client around you:
 * <ul>
 *   <li><b>Pressure in the pipes</b> every 7-18 seconds: a water-hammer knock running along the
 *       pipes (a row of knocks travelling toward you or away, along the corridor), a single heavy
 *       clank, a long creak of hot metal, or water rushing through the overhead bundle and past
 *       you. Always somewhere up or down the straight line, on one wall or overhead, so you hear
 *       which way it is. While the corridor TWISTs (the Level 2 event) the pipes creak far more
 *       often, as if the whole structure strains.</li>
 *   <li><b>Pump bays</b>: each pump hums and thumps (a loop on the pump).</li>
 *   <li><b>Leaks</b>: a leaking steam valve on the corridor wall hisses steadily, wisps steam and
 *       now and then blows a jet with a louder hiss; a vent on a pump does the same. (Vents on a
 *       hot zone's machines are left to Level2AnomalyEffects, which runs the hot zones' steam.)</li>
 *   <li><b>Drips</b>: every ceiling drip lets a drop fall on its own slow rhythm; the drop lands
 *       with a plink in the gutter (a metallic tick on a grate, a gloopy plop in black ooze).</li>
 * </ul>
 * The client has no layout seed for Level 2, so the pumps, leaks and drips are found by looking at
 * the blocks around you: a slab of the corridor 65 blocks long is swept a few slices per tick, and
 * the things found are used until the next sweep finishes (under a second later).
 *
 * <p>Nothing plays in the void below the floor or the loop hall above it, and Level2Sounds already
 * drops every sound while the void asks for silence. The loudness of each file is set in
 * tools/anomaly_assets/level2_audio.py (in line with the rest of the mix).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Emitters {
    private static final int FLOOR = Level2Sites.FLOOR_Y;

    // ---- The block sweep ---------------------------------------------------------------------
    /** Blocks swept either way along the corridor (X) ... */
    private static final int SCAN_ALONG = 32;
    /** ... and across it (Z): the corridor, its pump bays and the first blocks of side rooms. */
    private static final int SCAN_ACROSS = 12;
    /** Floor + 1 (things standing on the floor) up to the ceiling band. */
    private static final int SCAN_Y_MIN = FLOOR + 1;
    private static final int SCAN_Y_MAX = FLOOR + 5;
    /** Slices (one x each) swept per tick: 65 slices, so a full sweep takes 13 ticks. */
    private static final int SLICES_PER_TICK = 5;
    /** Pump blocks closer than this to one already found count as the same pump. */
    private static final int PUMP_MERGE = 3;

    // ---- Pumps, leaks, drips ---------------------------------------------------------------
    private static final double PUMP_RANGE = 26.0;
    private static final int MAX_PUMPS = 2;
    private static final float PUMP_VOLUME = 0.9f;
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

    /** What the last finished sweep found (packed block positions) ... */
    private static LongArrayList pumps = new LongArrayList();
    private static LongArrayList vents = new LongArrayList();
    private static LongArrayList drips = new LongArrayList();
    /** ... and what the sweep in progress has found so far. */
    private static LongArrayList nextPumps = new LongArrayList();
    private static LongArrayList nextVents = new LongArrayList();
    private static LongArrayList nextDrips = new LongArrayList();
    private static boolean sweeping;
    private static int sweepX;
    private static int sweepEndX;
    private static int sweepZ;

    /** The pump and leak loops playing, by the block they belong to. */
    private static final Long2ObjectOpenHashMap<MachineLoopSound> LOOPS = new Long2ObjectOpenHashMap<>();

    /** A one-off sound waiting for its moment (the next knock of a run, a drop landing). */
    private record Pending(long tick, SoundEvent sound, SoundSource source, double x, double y, double z, float volume, float pitch) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static long tick;
    private static int ticksToPressure = PRESSURE_MIN;
    private static boolean active;

    // Blocks, looked up once by id (some belong to the Level 2 block set, which may be missing in
    // an older world: then that kind of emitter simply never finds anything).
    private static boolean resolved;
    @Nullable
    private static Block pumpBlock;
    @Nullable
    private static Block dripBlock;
    @Nullable
    private static Block gratingBlock;
    private static Block ventBlock;
    private static Block machineBlock;
    private static Block oozeBlock;
    private static Block floorGrateBlock;

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
        // Only in the corridor itself: not down in the void, not up in the loop hall's office.
        if (eye.y > FLOOR && eye.y < FLOOR + Level2Sites.LOOP_RISE) {
            pumps(mc, eye);
            leaks(mc, level, eye);
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
        pumps.clear();
        vents.clear();
        drips.clear();
        PENDING.clear();
        LOOPS.clear();
        ticksToPressure = PRESSURE_MIN;
    }

    private static void resolveBlocks() {
        if (resolved) {
            return;
        }
        resolved = true;
        pumpBlock = byId("l2_pump");
        dripBlock = byId("l2_drip");
        gratingBlock = byId("l2_grating");
        ventBlock = ModBlocks.L2_STEAM_VENT.get();
        machineBlock = ModBlocks.L2_MACHINE.get();
        oozeBlock = ModBlocks.L2_BLACK_OOZE.get();
        floorGrateBlock = ModBlocks.FLOOR_GRATE.get();
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
            nextPumps.clear();
            nextVents.clear();
            nextDrips.clear();
        }
        for (int slice = 0; slice < SLICES_PER_TICK && sweepX <= sweepEndX; slice++, sweepX++) {
            for (int z = sweepZ - SCAN_ACROSS; z <= sweepZ + SCAN_ACROSS; z++) {
                for (int y = SCAN_Y_MIN; y <= SCAN_Y_MAX; y++) {
                    BlockState state = level.getBlockState(SCAN_POS.set(sweepX, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    Block block = state.getBlock();
                    if (block == pumpBlock) {
                        addPump(SCAN_POS.asLong());
                    } else if (block == ventBlock) {
                        if (!onMachine(level, state)) {
                            nextVents.add(SCAN_POS.asLong());
                        }
                    } else if (block == dripBlock) {
                        nextDrips.add(SCAN_POS.asLong());
                    }
                }
            }
        }
        if (sweepX > sweepEndX) {
            // Done: what we found replaces the last sweep's lists (the old ones are reused next time).
            LongArrayList swap = pumps;
            pumps = nextPumps;
            nextPumps = swap;
            swap = vents;
            vents = nextVents;
            nextVents = swap;
            swap = drips;
            drips = nextDrips;
            nextDrips = swap;
            sweeping = false;
        }
    }

    /** A pump is several blocks: keep one position per pump. */
    private static void addPump(long pos) {
        int x = BlockPos.getX(pos);
        int z = BlockPos.getZ(pos);
        for (int i = 0; i < nextPumps.size(); i++) {
            long other = nextPumps.getLong(i);
            if (Math.abs(BlockPos.getX(other) - x) <= PUMP_MERGE && Math.abs(BlockPos.getZ(other) - z) <= PUMP_MERGE) {
                return;
            }
        }
        nextPumps.add(pos);
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
    // Pumps, leaks and drips
    // ==========================================================================================

    /** Keeps a hum loop going on the nearest pumps. */
    private static void pumps(Minecraft mc, Vec3 eye) {
        int n = nearest(pumps, eye, PUMP_RANGE, MAX_PUMPS);
        for (int i = 0; i < n; i++) {
            long pos = NEAREST[i];
            // The sweep meets a pump at its lowest-x, lowest-z, lowest cell first (it runs up x,
            // then z, then y), and the pump is 2x2x2: its centre is the far corner of that cell.
            loop(mc, pos, Level2Audio.PUMP_HUM.get(), BlockPos.getX(pos) + 1.0, BlockPos.getY(pos) + 1.0,
                    BlockPos.getZ(pos) + 1.0, PUMP_VOLUME);
        }
    }

    /** Keeps the loop for {@code pos} playing (starting it if needed). */
    private static void loop(Minecraft mc, long pos, SoundEvent sound, double x, double y, double z, float volume) {
        MachineLoopSound loop = LOOPS.get(pos);
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
            loop = new MachineLoopSound(sound, x, y, z, FixturePower.mix(pos), volume, false);
            mc.getSoundManager().play(loop);
            LOOPS.put(pos, loop);
        }
        loop.keepAlive();
    }

    /** The nearest leaking valves: a steady hiss, wisps of steam, and now and then a jet. */
    private static void leaks(Minecraft mc, ClientLevel level, Vec3 eye) {
        int n = nearest(vents, eye, LEAK_RANGE, MAX_LEAKS);
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

    /** The nearest drips each let a drop fall now and then; the sound comes when it lands. */
    private static void drips(ClientLevel level, Vec3 eye) {
        int n = nearest(drips, eye, DRIP_RANGE, MAX_DRIPS);
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

    /** One drop: a falling water particle now, and the plink when it reaches the floor. */
    private static void drop(ClientLevel level, long drip) {
        int x = BlockPos.getX(drip);
        int y = BlockPos.getY(drip);
        int z = BlockPos.getZ(drip);
        if (level.getBlockState(PROBE.set(x, y, z)).getBlock() != dripBlock) {
            return; // gone since the sweep
        }
        double startY = y + 0.1;
        // Find what it lands on: the first block below with a top to hit (or black ooze).
        double landY = FLOOR + 1.0;
        Block landed = null;
        for (int by = y - 1; by >= FLOOR - 2; by--) {
            BlockState state = level.getBlockState(PROBE.set(x, by, z));
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, PROBE);
            if (state.getBlock() == oozeBlock || !shape.isEmpty() || !state.getFluidState().isEmpty()) {
                landY = by + (shape.isEmpty() ? 0.1 : shape.max(Direction.Axis.Y));
                landed = state.getBlock();
                break;
            }
        }
        double px = x + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2;
        double pz = z + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.2;
        level.addParticle(ParticleTypes.FALLING_WATER, px, startY, pz, 0.0, 0.0, 0.0);
        long lands = tick + fallTicks(startY - landY);
        if (landed == oozeBlock) {
            PENDING.add(new Pending(lands, ModSounds.L2_OOZE_DRIP.get(), SoundSource.BLOCKS, px, landY, pz,
                    0.35f, 0.9f + RANDOM.nextFloat() * 0.2f));
        } else if (landed != null && (landed == floorGrateBlock || landed == gratingBlock)) {
            // On a steel grate: higher and brighter, a little tick of metal.
            PENDING.add(new Pending(lands, Level2Audio.DRIP.get(), SoundSource.BLOCKS, px, landY, pz,
                    0.45f, 1.25f + RANDOM.nextFloat() * 0.15f));
        } else {
            PENDING.add(new Pending(lands, Level2Audio.DRIP.get(), SoundSource.BLOCKS, px, landY, pz,
                    0.5f, 0.88f + RANDOM.nextFloat() * 0.24f));
        }
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
        ticksToPressure = PRESSURE_MIN + RANDOM.nextInt(PRESSURE_SPREAD);
        int roll = RANDOM.nextInt(10);
        if (roll < 4) {
            knockRun(eye);
        } else if (roll < 6) {
            clank(eye);
        } else if (roll < 8) {
            creak(eye, false);
        } else {
            mc.getSoundManager().play(new RushSound(eye));
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
                    startX + travel * spacing * i, y, z, volume, pitch * (0.97f + RANDOM.nextFloat() * 0.06f)));
        }
    }

    /** A single heavy clank somewhere along the line, sometimes answered by a smaller knock. */
    private static void clank(Vec3 eye) {
        double x = eye.x + side() * Mth.nextDouble(RANDOM, 8.0, 30.0);
        double z = chaseZ(eye);
        PENDING.add(new Pending(tick, Level2Audio.PIPE_CLANK.get(), SoundSource.AMBIENT, x, CEILING_PIPES_Y, z,
                0.9f, 0.9f + RANDOM.nextFloat() * 0.2f));
        if (RANDOM.nextBoolean()) {
            PENDING.add(new Pending(tick + 6 + RANDOM.nextInt(9), Level2Audio.PIPE_KNOCK.get(), SoundSource.AMBIENT,
                    x + side() * Mth.nextDouble(RANDOM, 1.0, 2.5), RACK_Y, z, 0.5f, 0.8f + RANDOM.nextFloat() * 0.15f));
        }
    }

    /** Hot metal stretching: a long creak from a pipe a little way off (closer during a twist). */
    private static void creak(Vec3 eye, boolean twist) {
        double x = eye.x + side() * (twist ? Mth.nextDouble(RANDOM, 3.0, 14.0) : Mth.nextDouble(RANDOM, 5.0, 20.0));
        double y = RANDOM.nextBoolean() ? CEILING_PIPES_Y : RACK_Y;
        PENDING.add(new Pending(tick, Level2Audio.PIPE_CREAK.get(), SoundSource.AMBIENT, x, y, chaseZ(eye),
                twist ? 0.8f : 0.7f, (twist ? 0.75f : 0.85f) + RANDOM.nextFloat() * 0.3f));
    }

    private static void playPending(Minecraft mc) {
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (p.tick() > tick) {
                continue;
            }
            PENDING.remove(i);
            mc.getSoundManager().play(new SimpleSoundInstance(p.sound(), p.source(), p.volume(), p.pitch(), RANDOM,
                    p.x(), p.y(), p.z()));
        }
    }

    /**
     * Water rushing through the overhead bundle: a sound that really moves, from about 22 blocks
     * up the corridor, over your head, to 22 blocks down it (or the other way) in about 4 s, the
     * length of the recording.
     */
    private static final class RushSound extends AbstractTickableSoundInstance {
        /** Blocks per tick: 44 blocks in ~4.2 s. */
        private static final double SPEED = 0.52;
        private final double step;
        private final double endX;

        RushSound(Vec3 eye) {
            super(Level2Audio.PIPE_RUSH.get(), SoundSource.AMBIENT, RandomSource.create(RANDOM.nextLong()));
            double from = side();
            this.x = eye.x + from * 22.0;
            this.y = CEILING_PIPES_Y;
            this.z = eye.z;
            this.step = -from * SPEED;
            this.endX = eye.x - from * 26.0;
            this.volume = 0.85f;
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
