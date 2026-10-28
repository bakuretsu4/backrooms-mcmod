package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VoidBubble;
import com.rickr.backrooms.network.Level227Payload;
import com.rickr.backrooms.registry.Level227Atmos;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sound of Level 227 (client side), and what the tree's movements feel like.
 *
 * <ul>
 *   <li><b>The forest</b> (in your head, like a biome's ambience): wind through an ocean of leaves,
 *       insects, wrong birdsong far off. It thins out as you fall into the void.</li>
 *   <li><b>The void wind</b> (placed in the world): a hollow rush of air rising out of the void,
 *       played at the nearest spot where the branch ends, so with your eyes shut you still hear
 *       which way the drop is and how close. Falling, it is all around you.</li>
 *   <li><b>A sunbeam's hum</b> (placed at the nearest beam): it rises in pitch as you get close.</li>
 *   <li><b>The sun's voice</b> (in your head): a faint choir of whispers that swells near a beam
 *       and in the higher sections. Very rarely, every quarter to half an hour, it says something:
 *       one short line in the action bar, with a whisper.</li>
 *   <li><b>Fallen bubbles</b> hum where they float.</li>
 *   <li><b>The tree moving</b> ({@link Level227Payload}): a lurch (a vast groan, leaves raining down,
 *       a very slight sway of the camera), the shudder before it shakes you loose (growing harder),
 *       and, in another level's forest, the pull that drags you in.</li>
 * </ul>
 * The far-off groans, cracks and calls come from {@code client/audio/DistantAmbience}; the room
 * sound of the forest from {@code client/audio/Acoustics}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level227Audio {
    // --- Loop volumes (1 = the level the file was made at, see tools/anomaly_assets/level227_audio.py)
    private static final float FOREST_VOLUME = 1.0f;
    /** The forest sinks under the sun's white-out. */
    private static final float FOREST_IN_SUN = 0.35f;
    /** How far out to look for the drop (blocks), and how far below your eyes a column must fall to count. */
    private static final int EDGE_SEARCH = 36;
    private static final double EDGE_DROP = 40.0;
    /** The void wind hangs this far below your ears at the edge (it rises from below). */
    private static final double WIND_BELOW = 6.0;
    /** The sun's choir: loudest within a beam, fading out by this many blocks from one ... */
    private static final double VOICE_REACH = 48.0;
    /** ... and this loud anywhere in the higher sections. */
    private static final float VOICE_HIGH = 0.45f;
    /** The hum's pitch climbs from 1 to this as you reach the beam. */
    private static final float HUM_PITCH_RISE = 0.25f;
    private static final double HUM_REACH = 32.0;
    /** The sun speaks once every 15-35 minutes in the level ... */
    private static final int SPEAK_MIN_TICKS = 15 * 60 * 20;
    private static final int SPEAK_MAX_TICKS = 35 * 60 * 20;
    /** ... and only near a beam or high up, except for this share of the time. */
    private static final float SPEAK_ANYWHERE = 0.3f;
    private static final int SUN_LINES = 10;
    /** Fade speed of the loops (volume per tick). */
    private static final float FADE = 0.03f;

    // --- The tree moving
    /** Peak camera sway in a full lurch (degrees): very slight. */
    private static final float LURCH_DEG = 0.7f;
    /** Peak shake at the end of the warning before you're shaken loose, and of the drag. */
    private static final float UNSTABLE_DEG = 2.2f;
    private static final float DRAG_DEG = 1.2f;
    /** Leaves raining down around you at a full lurch (particles per tick). */
    private static final int LURCH_LEAVES = 6;

    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private static Level39LoopSound forest;
    @Nullable
    private static Level39LoopSound voice;
    @Nullable
    private static Level39LoopSound voidAround;
    @Nullable
    private static PlacedLoop voidEdge;
    @Nullable
    private static PlacedLoop hum;
    private static final PlacedLoop[] BUBBLE_HUMS = new PlacedLoop[2];
    private static final VoidBubble[] BUBBLES = new VoidBubble[2];
    /** Last found edge (where the void wind plays), or null when no drop is near. */
    @Nullable
    private static Vec3 edge;
    private static int ticks;
    private static int speakIn = -1;

    // What the tree is doing to you now (client ticks left / total, and how hard).
    private static int moveKind = -1;
    private static int moveLeft;
    private static int moveTotal;
    private static float moveStrength;
    @Nullable
    private static BlockState leafState;

    private Level227Audio() {}

    /** Network thread hands it over; the client thread picks it up next tick. */
    public static void receive(Level227Payload payload) {
        Minecraft.getInstance().execute(() -> {
            moveKind = payload.kind();
            moveLeft = moveTotal = Math.max(1, payload.ticks());
            moveStrength = payload.strength();
            if (payload.kind() == Level227Payload.LURCH) {
                // The groan of the whole tree: heard the same everywhere, it is the ground itself.
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(Level227Atmos.LURCH.get().getLocation(),
                        SoundSource.AMBIENT, 0.55f + 0.45f * payload.strength(), 0.9f + RANDOM.nextFloat() * 0.15f, RANDOM,
                        false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        tickMove(mc);
        if (!Level227Lighting.inLevel227()) {
            speakIn = -1;
            edge = null;
            return; // the loops starve and fade out by themselves
        }
        ticks++;
        ClientLevel level = mc.level;
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        float voidness = Level227Lighting.voidness();
        float glare = Level227Lighting.sunGlare();

        forest = feed(mc, forest, Level227Atmos.FOREST.get(),
                FOREST_VOLUME * (1.0f - 0.9f * voidness) * Mth.lerp(glare, 1.0f, FOREST_IN_SUN));
        voidAround = feed(mc, voidAround, Level227Atmos.VOID_WIND.get(), voidness);

        // The void wind at the nearest edge (searched twice a second; the sound glides to it).
        if (ticks % 10 == 0) {
            edge = findEdge(level, ear);
        }
        voidEdge = place(mc, voidEdge, Level227Atmos.VOID_WIND.get(), edge, edge == null ? 0.0f : 1.0f - voidness, 1.0f);

        // The nearest beam's hum, at the point of the shaft nearest you, rising as you approach.
        Level227Tree.Beam beam = Level227Lighting.nearestBeam();
        double beamDist = Level227Lighting.beamDistance();
        Vec3 humAt = null;
        float humPitch = 1.0f;
        if (beam != null && beamDist < HUM_REACH) {
            double dx = ear.x - beam.x();
            double dz = ear.z - beam.z();
            double d = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            double r = Math.min(d, beam.radius());
            int ground = Level227Tree.topSurfaceY(beam.x(), beam.z());
            double y = ground == Integer.MIN_VALUE ? ear.y : Mth.clamp(ear.y, ground, beam.topY());
            humAt = new Vec3(beam.x() + dx / d * r, y, beam.z() + dz / d * r);
            humPitch = 1.0f + HUM_PITCH_RISE * (float) (1.0 - beamDist / HUM_REACH);
        }
        hum = place(mc, hum, Level227Atmos.BEAM_HUM.get(), humAt, humAt == null ? 0.0f : 1.0f, humPitch);

        // The sun's voice: swelling near a beam and high up.
        float nearBeam = beamDist >= VOICE_REACH ? 0.0f : (float) Math.pow(1.0 - beamDist / VOICE_REACH, 1.5);
        voice = feed(mc, voice, Level227Atmos.SUN_VOICE.get(), Math.max(nearBeam, VOICE_HIGH * Level227Lighting.high()));
        tickSpeech(mc, nearBeam);

        // Fallen bubbles hum where they float.
        if (ticks % 10 == 0) {
            findBubbles(level, ear);
        }
        for (int i = 0; i < BUBBLES.length; i++) {
            VoidBubble b = BUBBLES[i];
            boolean alive = b != null && !b.isRemoved();
            BUBBLE_HUMS[i] = place(mc, BUBBLE_HUMS[i], Level227Atmos.BUBBLE_HUM.get(), alive ? b.centreAt(0.0f) : null,
                    alive ? 1.0f : 0.0f, 1.0f);
        }
    }

    /** The rare line from the sun: an action-bar line and a whisper, chosen at random. */
    private static void tickSpeech(Minecraft mc, float nearBeam) {
        if (speakIn < 0) {
            speakIn = Mth.nextInt(RANDOM, SPEAK_MIN_TICKS, SPEAK_MAX_TICKS);
        }
        if (--speakIn > 0) {
            return;
        }
        boolean heard = nearBeam > 0.05f || Level227Lighting.high() > 0.5f || RANDOM.nextFloat() < SPEAK_ANYWHERE;
        if (!heard) {
            speakIn = Mth.nextInt(RANDOM, 60 * 20, 3 * 60 * 20); // try again in a few minutes
            return;
        }
        speakIn = -1;
        mc.gui.setOverlayMessage(Component.translatable("message.backrooms.l227.sun." + RANDOM.nextInt(SUN_LINES))
                .withStyle(s -> s.withItalic(true).withColor(0xEDE6CC)), false);
        mc.getSoundManager().play(new SimpleSoundInstance(Level227Atmos.SUN_WHISPER.get().getLocation(), SoundSource.AMBIENT,
                0.8f, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    /**
     * The nearest place within {@value #EDGE_SEARCH} blocks where the ground falls away into the
     * void: 16 directions, stepping outward, using the loaded world's own height map (a column with
     * nothing in it, or whose top is far below your eyes). Returns a point a little below your ears
     * there, or null.
     */
    @Nullable
    private static Vec3 findEdge(ClientLevel level, Vec3 ear) {
        double best = Double.MAX_VALUE;
        double bx = 0.0;
        double bz = 0.0;
        int floor = level.getMinBuildHeight();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            for (int r = 3; r <= EDGE_SEARCH && r < best; r += 3) {
                int x = Mth.floor(ear.x + cos * r);
                int z = Mth.floor(ear.z + sin * r);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    break;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (top <= floor + 1 || top < ear.y - EDGE_DROP) {
                    best = r;
                    bx = x + 0.5;
                    bz = z + 0.5;
                    break;
                }
            }
        }
        return best == Double.MAX_VALUE ? null : new Vec3(bx, ear.y - WIND_BELOW, bz);
    }

    /** Keeps track of the (up to two) nearest fallen bubbles within 48 blocks. */
    private static void findBubbles(ClientLevel level, Vec3 ear) {
        BUBBLES[0] = null;
        BUBBLES[1] = null;
        double d0 = Double.MAX_VALUE;
        double d1 = Double.MAX_VALUE;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof VoidBubble b) {
                double d = b.distanceToSqr(ear);
                if (d > 48.0 * 48.0) {
                    continue;
                }
                if (d < d0) {
                    BUBBLES[1] = BUBBLES[0];
                    d1 = d0;
                    BUBBLES[0] = b;
                    d0 = d;
                } else if (d < d1) {
                    BUBBLES[1] = b;
                    d1 = d;
                }
            }
        }
    }

    /** Keeps one in-your-head loop playing (restarting it if the engine dropped it) and feeds its volume. */
    @Nullable
    private static Level39LoopSound feed(Minecraft mc, @Nullable Level39LoopSound loop, SoundEvent sound, float volume) {
        if (volume <= 0.001f && (loop == null || loop.isStopped())) {
            return loop;
        }
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
            loop = new Level39LoopSound(sound, FADE);
            mc.getSoundManager().play(loop);
        }
        loop.feed(volume);
        return loop;
    }

    /** Same for a loop placed in the world at {@code at} (null = nothing to play there now). */
    @Nullable
    private static PlacedLoop place(Minecraft mc, @Nullable PlacedLoop loop, SoundEvent sound, @Nullable Vec3 at,
                                    float volume, float pitch) {
        if (at == null || volume <= 0.001f) {
            if (loop != null) {
                loop.feed(null, 0.0f, pitch);
            }
            return loop == null || loop.isStopped() ? null : loop;
        }
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
            loop = new PlacedLoop(sound, at);
            mc.getSoundManager().play(loop);
        }
        loop.feed(at, volume, pitch);
        return loop;
    }

    /**
     * A looping sound out in the world that glides to wherever it is fed (so the void wind slides
     * along the edge as you walk instead of jumping), fades with distance (LINEAR, the range is in
     * sounds.json), and fades out and stops when nobody feeds it.
     */
    private static final class PlacedLoop extends AbstractTickableSoundInstance {
        private static final int STARVE_TICKS = 3;
        /** Share of the way to the new spot per tick (~1 s to settle). */
        private static final double GLIDE = 0.1;
        private double tx;
        private double ty;
        private double tz;
        private float target;
        private int unfed;

        PlacedLoop(SoundEvent sound, Vec3 at) {
            super(sound, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.0f;
            this.x = tx = at.x;
            this.y = ty = at.y;
            this.z = tz = at.z;
        }

        void feed(@Nullable Vec3 at, float volume, float pitch) {
            if (at != null) {
                tx = at.x;
                ty = at.y;
                tz = at.z;
            }
            target = volume;
            this.pitch = pitch;
            unfed = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (++unfed > STARVE_TICKS) {
                target = 0.0f;
            }
            x += (tx - x) * GLIDE;
            y += (ty - y) * GLIDE;
            z += (tz - z) * GLIDE;
            volume += Math.max(-FADE, Math.min(FADE, target - volume));
            if (target <= 0.0f && volume <= 0.001f) {
                stop();
            }
        }
    }

    // ==========================================================================================
    // The tree moving: leaves and the shake
    // ==========================================================================================

    private static void tickMove(Minecraft mc) {
        if (moveKind < 0) {
            return;
        }
        if (--moveLeft <= 0) {
            moveKind = -1;
            return;
        }
        float amount = moveAmount();
        int leaves = Math.round(LURCH_LEAVES * amount);
        if (leaves > 0) {
            spawnLeaves(mc, leaves);
        }
    }

    /** 0..1 how strongly the tree is moving you right now. */
    private static float moveAmount() {
        float t = 1.0f - moveLeft / (float) moveTotal; // 0 at the start, 1 at the end
        return switch (moveKind) {
            // A lurch heaves quickly, then dies away slowly.
            case Level227Payload.LURCH -> moveStrength * Math.min(1.0f, t / 0.08f) * (1.0f - t) * (1.0f - t);
            // The warning and the drag only build: they end in the noclip.
            case Level227Payload.UNSTABLE, Level227Payload.DRAG -> t * t;
            default -> 0.0f;
        };
    }

    /** Leaves shaken loose, drifting down around you (the canopy's own leaves where it has them). */
    private static void spawnLeaves(Minecraft mc, int count) {
        if (leafState == null) {
            leafState = BuiltInRegistries.BLOCK.getOptional(Backrooms.id("l227_leaves"))
                    .map(b -> b.defaultBlockState()).orElse(Blocks.JUNGLE_LEAVES.defaultBlockState());
        }
        BlockState state = Level227Lighting.inLevel227() ? leafState : Blocks.OAK_LEAVES.defaultBlockState();
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.FALLING_DUST, state);
        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < count; i++) {
            double x = eye.x + (RANDOM.nextDouble() - 0.5) * 24.0;
            double y = eye.y + 2.0 + RANDOM.nextDouble() * 14.0;
            double z = eye.z + (RANDOM.nextDouble() - 0.5) * 24.0;
            mc.level.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * The sway: slow and heavy for a lurch (the ground heaving, barely more than a degree), a
     * rough, growing tremble before you're shaken loose or dragged. Runs after the handheld camera
     * (lowest priority) and only nudges the angles it already set, like Level 39's quakes.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (moveKind < 0) {
            return;
        }
        float amount = moveAmount();
        if (amount <= 0.0f) {
            return;
        }
        float t = (System.currentTimeMillis() % 600_000L) / 1000.0f;
        float pitch;
        float yaw;
        float roll;
        if (moveKind == Level227Payload.LURCH) {
            float deg = LURCH_DEG * amount;
            pitch = (Mth.sin(t * 1.3f) * 0.7f + Mth.sin(t * 7.0f) * 0.15f) * deg;
            yaw = Mth.sin(t * 0.9f + 1.0f) * 0.4f * deg;
            roll = (Mth.sin(t * 1.1f + 2.0f) * 0.8f + Mth.sin(t * 9.0f) * 0.1f) * deg;
        } else {
            float deg = (moveKind == Level227Payload.UNSTABLE ? UNSTABLE_DEG : DRAG_DEG) * amount;
            pitch = (Mth.sin(t * 41.0f) * 0.5f + Mth.sin(t * 13.0f) * 0.3f + Mth.sin(t * 2.3f) * 0.2f) * deg;
            yaw = (Mth.sin(t * 37.0f + 1.3f) * 0.5f + Mth.sin(t * 7.0f) * 0.5f) * deg * 0.6f;
            roll = (Mth.sin(t * 47.0f + 2.1f) * 0.5f + Mth.sin(t * 3.1f) * 0.5f) * deg;
        }
        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
        event.setRoll(event.getRoll() + roll);
    }
}
