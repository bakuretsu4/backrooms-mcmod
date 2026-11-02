package com.rickr.backrooms.client.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.FixtureLight;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Level 9, client only: the streetlamps die behind you. Now and then, while you walk down a
 * street, the lamps you have just passed go out one by one behind your back, where you can't
 * see them (only the light on the road ahead of you changes). When you are far away, or once
 * you have turned round and looked back at them for a few seconds, they quietly come back.
 *
 * <p>Timings:
 * <ul>
 *   <li>A run can start once you have been on Level 9 for {@link #FIRST_RUN_TICKS} (90 s) and
 *       {@link #COOLDOWN_MIN_TICKS}-{@link #COOLDOWN_MAX_TICKS} (2-4 min) after the last run;
 *       then each second you walk outdoors there is a 1 in {@link #START_ONE_IN} chance (about
 *       45 s of walking on average). So a few runs per visit, never constant.</li>
 *   <li>In a run, every {@link #KILL_MIN_TICKS}-{@link #KILL_MAX_TICKS} (2-3.5 s) the nearest
 *       working lamp that is behind you, 10-40 blocks away and well outside your view (more than
 *       about 110 degrees from where you look) stutters for 0.7 s and goes out; 3-5 of them, then
 *       the run ends (also after 45 s, or 5 s of standing still).</li>
 *   <li>A dead lamp comes back (a short stutter, no sound) once you are 64 blocks from it, or one
 *       every 0.6-1.3 s after you have looked back at them for {@link #LOOK_BACK_TICKS} (3.5 s),
 *       and in any case after {@link #MAX_DEAD_MS} (2 min).</li>
 *   <li>Nothing starts during a level event (LAMP_WAVE, the Mangled's fog); when a lamp wave
 *       starts, every lamp that died here comes back at once (the wave takes over).</li>
 * </ul>
 * Broken (flicker) lamps, porch lights and safe-house lamps are never touched. The light code
 * asks {@link #lampOn} for every streetlamp each tick (client/light/BackroomsLights), and the
 * stutter itself is {@link FixturePower#behindLampOn}. {@code /backrooms event lampsbehind}
 * starts a run at once ({@link #forceRun}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class LampsBehind {
    private static final int MAX_DEAD = 8;
    private static final int FIRST_RUN_TICKS = 1800;
    private static final int COOLDOWN_MIN_TICKS = 2400;
    private static final int COOLDOWN_MAX_TICKS = 4800;
    private static final int START_ONE_IN = 45;
    private static final int KILL_MIN_TICKS = 40;
    private static final int KILL_MAX_TICKS = 70;
    private static final int RUN_MAX_TICKS = 900;
    private static final int STILL_MAX_TICKS = 100;
    private static final int LOOK_BACK_TICKS = 70;
    private static final long MAX_DEAD_MS = 120_000L;
    private static final double MIN_DISTANCE = 10.0;
    private static final double MAX_DISTANCE = 40.0;
    private static final double FAR_DISTANCE = 64.0;
    /** cos(110 degrees): a lamp further round than this from where you look is out of view. */
    private static final double BEHIND_COS = -0.34;
    /** cos(60 degrees): a lamp this close to where you look is being looked at. */
    private static final double SEEN_COS = 0.5;
    /** Walking: at least this many blocks per tick (sneaking is ~0.065, walking ~0.22). */
    private static final double WALK_SPEED = 0.08;
    private static final long NONE = Long.MIN_VALUE;

    private static final RandomSource RANDOM = RandomSource.create();

    // The dead lamps (fixed arrays: nothing allocated while it runs).
    private static final long[] KEY = new long[MAX_DEAD];
    private static final double[] X = new double[MAX_DEAD];
    private static final double[] Z = new double[MAX_DEAD];
    private static final long[] OUT_AT = new long[MAX_DEAD];
    private static final long[] BACK_AT = new long[MAX_DEAD];
    private static int dead;

    // Where you are, look and walk (updated each tick), and the best lamp the light code offered.
    private static double eyeX;
    private static double eyeZ;
    private static double lookX;
    private static double lookZ = 1.0;
    private static double moveX;
    private static double moveZ;
    private static double lastX = Double.NaN;
    private static double lastZ;
    private static boolean scanning;
    private static long bestKey = NONE;
    private static double bestX;
    private static double bestZ;
    private static double bestDistSq = Double.MAX_VALUE;

    private static boolean onLevel9;
    private static boolean running;
    private static int killsLeft;
    private static int nextKill;
    private static int runTicks;
    private static int stillTicks;
    private static int cooldown = FIRST_RUN_TICKS;
    private static int lookBack;
    private static int restoreGap;
    private static long tickCount;
    private static long waveStartSeen;

    private LampsBehind() {}

    /** Starts a run now ({@code /backrooms event lampsbehind}): the first lamp goes within a second. */
    public static void forceRun() {
        if (!onLevel9) {
            return;
        }
        startRun();
        killsLeft = 5;
        nextKill = 10;
    }

    /**
     * Called by the light code for every Level 9 streetlamp each tick, before it decides whether
     * the lamp is on: remembers the nearest lamp that may go out next.
     */
    public static void offer(FixtureLight light) {
        if (!scanning || light.broken || !light.on || light.level <= 0.0f || indexOf(light.key) >= 0) {
            return;
        }
        double dx = light.x - eyeX;
        double dz = light.z - eyeZ;
        double d2 = dx * dx + dz * dz;
        if (d2 < MIN_DISTANCE * MIN_DISTANCE || d2 > MAX_DISTANCE * MAX_DISTANCE || d2 >= bestDistSq) {
            return;
        }
        double d = Math.sqrt(d2);
        if ((dx * lookX + dz * lookZ) / d > BEHIND_COS || dx * moveX + dz * moveZ >= 0.0) {
            return; // in view, or not yet passed (it is not behind the way you are walking)
        }
        bestKey = light.key;
        bestX = light.x;
        bestZ = light.z;
        bestDistSq = d2;
    }

    /** False while this streetlamp is out (or stuttering) because it died behind you. */
    public static boolean lampOn(long key, long nowMs) {
        if (dead == 0) {
            return true;
        }
        int i = indexOf(key);
        return i < 0 || FixturePower.behindLampOn(key, OUT_AT[i], BACK_AT[i], nowMs);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean here = mc.level != null && mc.player != null
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9;
        if (here != onLevel9) {
            // Arrived or left: start clean.
            onLevel9 = here;
            dead = 0;
            running = false;
            scanning = false;
            cooldown = FIRST_RUN_TICKS;
            lastX = Double.NaN;
        }
        if (!here || mc.isPaused()) {
            return;
        }
        tickCount++;
        long now = System.currentTimeMillis();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        eyeX = eye.x;
        eyeZ = eye.z;
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        lookX = -Mth.sin(yaw);
        lookZ = Mth.cos(yaw);
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        if (!Double.isNaN(lastX)) {
            moveX = moveX * 0.85 + (px - lastX) * 0.15;
            moveZ = moveZ * 0.85 + (pz - lastZ) * 0.15;
        }
        lastX = px;
        lastZ = pz;
        boolean walking = Math.sqrt(moveX * moveX + moveZ * moveZ) > WALK_SPEED && Level9Soundscape.outdoors();
        int levelEvent = FixturePower.activeEvent(now);
        if (levelEvent == LevelEventPayload.LAMP_WAVE && com.rickr.backrooms.camera.CameraClientState.eventStartMs != waveStartSeen) {
            waveStartSeen = com.rickr.backrooms.camera.CameraClientState.eventStartMs;
            for (int i = 0; i < dead; i++) {
                BACK_AT[i] = Math.min(BACK_AT[i], now); // the wave takes over
            }
        }

        restore(now);

        long candidate = bestKey;
        double cx = bestX;
        double cz = bestZ;
        bestKey = NONE;
        bestDistSq = Double.MAX_VALUE;
        if (running) {
            runTicks++;
            stillTicks = walking ? 0 : stillTicks + 1;
            if (levelEvent != LevelEventPayload.NONE || killsLeft <= 0 || runTicks > RUN_MAX_TICKS || stillTicks > STILL_MAX_TICKS) {
                running = false;
                cooldown = Mth.nextInt(RANDOM, COOLDOWN_MIN_TICKS, COOLDOWN_MAX_TICKS);
            } else if (--nextKill <= 0 && candidate != NONE && dead < MAX_DEAD) {
                KEY[dead] = candidate;
                X[dead] = cx;
                Z[dead] = cz;
                OUT_AT[dead] = now;
                BACK_AT[dead] = Long.MAX_VALUE;
                dead++;
                killsLeft--;
                nextKill = Mth.nextInt(RANDOM, KILL_MIN_TICKS, KILL_MAX_TICKS);
            }
        } else if (cooldown > 0) {
            cooldown--;
        } else if (walking && levelEvent == LevelEventPayload.NONE && dead == 0 && tickCount % 20 == 0
                && RANDOM.nextInt(START_ONE_IN) == 0) {
            startRun();
        }
        scanning = running;
    }

    private static void startRun() {
        running = true;
        killsLeft = 3 + RANDOM.nextInt(3);
        nextKill = 30;
        runTicks = 0;
        stillTicks = 0;
        lookBack = 0;
    }

    /** Brings dead lamps back: far away, looked back at for a while, or simply dead too long. */
    private static void restore(long now) {
        boolean seen = false;
        for (int i = 0; i < dead; i++) {
            if (BACK_AT[i] != Long.MAX_VALUE) {
                if (now > BACK_AT[i] + FixturePower.behindLampSettleMs()) {
                    remove(i--);
                }
                continue;
            }
            double dx = X[i] - eyeX;
            double dz = Z[i] - eyeZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > FAR_DISTANCE || now - OUT_AT[i] > MAX_DEAD_MS) {
                BACK_AT[i] = now;
            } else if (d > 0.5 && (dx * lookX + dz * lookZ) / d > SEEN_COS) {
                seen = true;
            }
        }
        lookBack = seen ? lookBack + 1 : Math.max(0, lookBack - 2);
        if (lookBack < LOOK_BACK_TICKS || --restoreGap > 0) {
            return;
        }
        restoreGap = Mth.nextInt(RANDOM, 12, 26);
        for (int i = 0; i < dead; i++) {
            if (BACK_AT[i] == Long.MAX_VALUE) {
                BACK_AT[i] = now;
                return;
            }
        }
    }

    private static int indexOf(long key) {
        for (int i = 0; i < dead; i++) {
            if (KEY[i] == key) {
                return i;
            }
        }
        return -1;
    }

    private static void remove(int i) {
        dead--;
        KEY[i] = KEY[dead];
        X[i] = X[dead];
        Z[i] = Z[dead];
        OUT_AT[i] = OUT_AT[dead];
        BACK_AT[i] = BACK_AT[dead];
    }
}
