package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Sounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Level 39's echo quakes on the client: the show for one {@code Level39QuakePayload} (see
 * {@code world/Level39Quakes} for when they happen).
 *
 * <ol>
 *   <li><b>The boom</b> comes from the direction of the loud sound.</li>
 *   <li><b>Its echoes</b> come back {@link #ECHO_MS later}, each from another direction (off the
 *       facade behind you, then the ones to the sides), quieter and lower each time.</li>
 *   <li>If it was strong enough ({@link #QUAKE_THRESHOLD}), the echoes <b>build into a quake</b>:
 *       a deep rumble all around, the camera shaking harder and harder for a few seconds, every
 *       window in town rattling at the peak, dust falling from ceilings (or coal ash drifting
 *       down out in the open), then it settles. 6-10 s depending on the strength: magnitude 5,
 *       you can feel it but nothing falls down.</li>
 * </ol>
 * Everything is timed from when the message arrived, so the sounds and the shaking always line up.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39QuakeEffects {
    /** Must match {@code world/Level39Quakes.QUAKE_THRESHOLD}: from here on the echo becomes a quake. */
    private static final float QUAKE_THRESHOLD = 0.3f;
    /** When each echo arrives after the boom (ms), and how loud/low it is compared with the boom. */
    private static final long[] ECHO_MS = {0L, 950L, 2100L, 3500L};
    private static final float[] ECHO_VOLUME = {1.0f, 0.7f, 0.5f, 0.35f};
    private static final float[] ECHO_PITCH = {1.0f, 0.94f, 0.88f, 0.84f};
    /** Direction of each echo, as a turn (degrees) from the source direction. */
    private static final float[] ECHO_TURN = {0.0f, 180.0f, 90.0f, -90.0f};
    /** The booms are placed this far away; sounds.json gives them a 96-block range. */
    private static final double ECHO_DISTANCE = 40.0;
    /**
     * Mix levels. The boom, its echoes, the rumble and the rattling windows overlap, and the
     * town's huge reverb (client/audio/Acoustics) adds its tail on top; at full volume together
     * they clipped. These keep the loudest second about 8 dB above the town's own ambience and the
     * peaks under about -3 dBFS: the rumble carries the size (it is all low end), the rest sits under it.
     */
    private static final float ECHO_MIX = 0.32f;
    private static final float RUMBLE_MIX = 0.3f;

    /** The quake starts building this long after the boom (ms). */
    private static final long QUAKE_DELAY_MS = 1500L;
    /** Quake length: this much at threshold strength, plus up to this much more at full strength. */
    private static final float QUAKE_BASE_MS = 6000.0f;
    private static final float QUAKE_EXTRA_MS = 4000.0f;
    /** Shares of the quake: rising, holding at the peak, dying away (the rest). */
    private static final float RISE_SHARE = 0.4f;
    private static final float HOLD_SHARE = 0.2f;
    /** Peak shake at full strength (degrees). Level 9's far rumble is 0.6, its pillar quake ~1.8. */
    private static final float SHAKE_DEG = 2.4f;
    /** Most dust particles per tick at the peak. */
    private static final int MAX_DUST_PER_TICK = 14;
    /** Dust falls from a ceiling up to this far above your head; otherwise ash drifts down in the open. */
    private static final int CEILING_SEARCH = 8;
    private static final double DUST_RADIUS = 7.0;

    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Quake {
        final long startMs;
        final double x;
        final double y;
        final double z;
        final float strength;
        final boolean quakes;
        final float lengthMs;
        int echoesPlayed;
        boolean rumbled;
        boolean rattled;

        Quake(long startMs, double x, double y, double z, float strength) {
            this.startMs = startMs;
            this.x = x;
            this.y = y;
            this.z = z;
            this.strength = strength;
            this.quakes = strength >= QUAKE_THRESHOLD;
            this.lengthMs = QUAKE_BASE_MS + QUAKE_EXTRA_MS * Mth.clamp((strength - QUAKE_THRESHOLD) / (1.0f - QUAKE_THRESHOLD), 0.0f, 1.0f);
        }

        /** 0..1 how hard the ground shakes now (0 before the quake builds and after it ends). */
        float envelope(long nowMs) {
            if (!quakes) {
                return 0.0f;
            }
            float t = (nowMs - startMs - QUAKE_DELAY_MS) / lengthMs;
            if (t <= 0.0f || t >= 1.0f) {
                return 0.0f;
            }
            float e;
            if (t < RISE_SHARE) {
                e = t / RISE_SHARE;
                e = e * e; // builds up slowly, then faster: the echoes feeding it
            } else if (t < RISE_SHARE + HOLD_SHARE) {
                e = 1.0f;
            } else {
                e = 1.0f - (t - RISE_SHARE - HOLD_SHARE) / (1.0f - RISE_SHARE - HOLD_SHARE);
                e = e * e * (3.0f - 2.0f * e);
            }
            return e;
        }

        boolean over(long nowMs) {
            long end = quakes ? startMs + QUAKE_DELAY_MS + (long) lengthMs : startMs + ECHO_MS[ECHO_MS.length - 1];
            return nowMs > end + 500L;
        }
    }

    /** Usually one, briefly two when a wall knock's echo overlaps a quake. Client thread only. */
    private static final List<Quake> ACTIVE = new ArrayList<>();
    /** Network thread hands new quakes over here. */
    private static final List<Quake> INCOMING = new ArrayList<>();

    /** Network thread: a loud sound at (x, y, z) reached us with this strength (0..1). */
    public static void receive(int x, int y, int z, float strength) {
        synchronized (INCOMING) {
            INCOMING.add(new Quake(System.currentTimeMillis(), x + 0.5, y + 0.5, z + 0.5, strength));
        }
    }

    /** 0..1: how hard the ground is shaking right now (for other effects that want to join in). */
    public static float shaking() {
        long now = System.currentTimeMillis();
        float e = 0.0f;
        for (Quake q : ACTIVE) {
            e = Math.max(e, q.envelope(now) * q.strength);
        }
        return e;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        synchronized (INCOMING) {
            ACTIVE.addAll(INCOMING);
            INCOMING.clear();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !Level39ClientState.inLevel39()) {
            ACTIVE.clear();
            return;
        }
        long now = System.currentTimeMillis();
        float dust = 0.0f;
        Iterator<Quake> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Quake q = it.next();
            if (q.over(now)) {
                it.remove();
                continue;
            }
            playEchoes(mc, q, now);
            if (q.quakes) {
                long since = now - q.startMs;
                if (!q.rumbled && since >= QUAKE_DELAY_MS - 300L) {
                    q.rumbled = true;
                    // All around you, not from one place: the ground itself.
                    mc.getSoundManager().play(new SimpleSoundInstance(Level39Sounds.QUAKE_RUMBLE.get().getLocation(),
                            SoundSource.AMBIENT, RUMBLE_MIX * (0.45f + 0.55f * q.strength), 0.92f + RANDOM.nextFloat() * 0.1f, RANDOM,
                            false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
                }
                if (!q.rattled && since >= QUAKE_DELAY_MS + (long) (q.lengthMs * (RISE_SHARE - 0.08f))) {
                    q.rattled = true;
                    playAround(mc, Level39Sounds.WINDOW_RATTLE.get(), 10.0, ECHO_MIX * (0.6f + 0.4f * q.strength), 1.0f, 0.0f);
                }
            }
            dust = Math.max(dust, q.envelope(now) * q.strength);
        }
        if (dust > 0.05f && !mc.isPaused()) {
            spawnDust(mc, mc.level, dust);
        }
    }

    /** Plays every echo whose time has come. */
    private static void playEchoes(Minecraft mc, Quake q, long now) {
        Vec3 eye = mc.player.getEyePosition();
        float baseAngle = (float) Mth.atan2(q.z - eye.z, q.x - eye.x) * Mth.RAD_TO_DEG;
        while (q.echoesPlayed < ECHO_MS.length && now - q.startMs >= ECHO_MS[q.echoesPlayed]) {
            int i = q.echoesPlayed++;
            float volume = ECHO_MIX * ECHO_VOLUME[i] * (0.45f + 0.55f * q.strength);
            float pitch = ECHO_PITCH[i] * (0.97f + RANDOM.nextFloat() * 0.06f);
            float angle = (baseAngle + ECHO_TURN[i] + (RANDOM.nextFloat() - 0.5f) * 30.0f) * Mth.DEG_TO_RAD;
            // The first boom comes from up where it happened; the echoes from the facades around.
            double up = i == 0 ? Mth.clamp(q.y - eye.y, 0.0, 40.0) * 0.5 : 6.0 + RANDOM.nextDouble() * 10.0;
            mc.getSoundManager().play(new SimpleSoundInstance(Level39Sounds.ECHO_BOOM.get().getLocation(), SoundSource.AMBIENT,
                    volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR,
                    eye.x + Mth.cos(angle) * ECHO_DISTANCE, eye.y + up, eye.z + Mth.sin(angle) * ECHO_DISTANCE, false));
        }
    }

    /** One sound from a random direction {@code distance} blocks off. */
    private static void playAround(Minecraft mc, SoundEvent sound, double distance, float volume, float pitch, float up) {
        Vec3 eye = mc.player.getEyePosition();
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch, RANDOM,
                false, 0, SoundInstance.Attenuation.LINEAR, eye.x + Mth.cos(angle) * distance, eye.y + up,
                eye.z + Mth.sin(angle) * distance, false));
    }

    /**
     * Dust shaken loose: under a ceiling (a house, the tunnel vault) it trickles from the ceiling
     * blocks themselves; out in the open, flakes of coal ash drift down from the superstructure.
     */
    private static void spawnDust(Minecraft mc, ClientLevel level, float amount) {
        int count = Math.round(MAX_DUST_PER_TICK * amount);
        Vec3 eye = mc.player.getEyePosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            double x = eye.x + (RANDOM.nextDouble() * 2.0 - 1.0) * DUST_RADIUS;
            double z = eye.z + (RANDOM.nextDouble() * 2.0 - 1.0) * DUST_RADIUS;
            int baseY = Mth.floor(eye.y);
            BlockState ceiling = null;
            int ceilingY = 0;
            for (int dy = 1; dy <= CEILING_SEARCH; dy++) {
                cursor.set(Mth.floor(x), baseY + dy, Mth.floor(z));
                BlockState state = level.getBlockState(cursor);
                if (!state.isAir() && state.isSolidRender(level, cursor)) {
                    ceiling = state;
                    ceilingY = baseY + dy;
                    break;
                }
            }
            ParticleOptions particle;
            double y;
            if (ceiling != null) {
                particle = new BlockParticleOption(ParticleTypes.FALLING_DUST, ceiling);
                y = ceilingY - 0.05;
            } else {
                // Coal ash from the superstructure's windows (canon: they show coal traces).
                particle = RANDOM.nextInt(3) == 0
                        ? new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.COAL_BLOCK.defaultBlockState())
                        : ParticleTypes.ASH;
                y = eye.y + 3.0 + RANDOM.nextDouble() * 6.0;
            }
            level.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * The shake: rough, non-repeating, building with the quake. Runs after the handheld camera
     * (lowest priority) and only nudges the angles it already set, like Level 9's rumbles.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        float strength = shaking();
        if (strength <= 0.0f) {
            return;
        }
        float deg = SHAKE_DEG * strength;
        float t = (System.currentTimeMillis() % 600_000L) / 1000.0f;
        // A few unrelated sine waves = a rough tremble; the slow ones make the ground sway.
        float pitch = (Mth.sin(t * 59.0f) * 0.5f + Mth.sin(t * 23.0f) * 0.3f + Mth.sin(t * 4.1f) * 0.2f) * deg;
        float yaw = (Mth.sin(t * 47.0f + 1.3f) * 0.45f + Mth.sin(t * 9.0f) * 0.55f) * deg * 0.6f;
        float roll = (Mth.sin(t * 53.0f + 2.1f) * 0.5f + Mth.sin(t * 13.0f) * 0.3f + Mth.sin(t * 3.3f) * 0.2f) * deg;
        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
        event.setRoll(event.getRoll() + roll);
    }

    private Level39QuakeEffects() {}
}
