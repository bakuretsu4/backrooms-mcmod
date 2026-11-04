package com.rickr.backrooms.client.audio;

import com.mojang.blaze3d.audio.Channel;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.mixin.client.ChannelAccessor;
import com.rickr.backrooms.mixin.client.SoundEngineAccessor;
import com.rickr.backrooms.mixin.client.SoundManagerAccessor;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import org.jetbrains.annotations.Nullable;

/**
 * How sound behaves in the Backrooms' spaces: walls muffle, rooms echo.
 *
 * <ul>
 *   <li><b>Muffling.</b> For every sound placed in the world we count the solid blocks on the
 *       straight line between your ears and the sound. Each wall takes away most of its highs
 *       and a little of its volume, so a lamp in the next corridor hums dull and low, and opens
 *       up as you round the corner. Re-checked a few times a second while the sound plays.</li>
 *   <li><b>Room reverb.</b> A few times a second we "feel out" the space around you with rays
 *       (how far are the walls? is there a ceiling?). Small rooms ring short and close, big ones
 *       long and wide, open air hardly at all. Each level adds its materials: Level 0's carpet
 *       and Level 4's offices soak up the highs (but under Level 0's tall Atrium and shafts the echo
 *       grows harder and longer, coming back from far above), Level 1's bare concrete garage rings, Level 2's
 *       painted pipe tunnels are tight and hard. Level 39's town rings like a colossal hall
 *       (the superstructure encloses it, so open air above does not weaken the echo there),
 *       while its marble tunnels below swallow sound: short and dry. Level 228's mirror maze sounds
 *       like each zone's materials: wood, tile, canvas, bare glass, a dead small chamber.</li>
 *   <li>The two pool levels (the Poolrooms and Level 37) get <b>no reverb</b>: in canon nothing
 *       reverberates properly there (see client/anomaly/poolrooms/PoolAcoustics, which also
 *       makes their sounds die out). They do get muffling.</li>
 *   <li><b>Under water</b> (Level 227's ponds, {@code client/water/UnderwaterAudio} sets
 *       {@link #setSubmerged}): every sound, the in-your-head ambience loops included, goes
 *       through the same filter much harder (highs mostly gone, half as loud), eased in as your
 *       head goes under. The underwater bed itself ({@code level227.water.under*}) stays clear.</li>
 * </ul>
 * Only in the Backrooms; the overworld sounds like vanilla. Music, UI and the camcorder's own
 * beeps (not placed in the world) are never touched. The OpenAL work is in {@link AcousticsEfx}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Acoustics {
    /** Room parameters for OpenAL's reverb (see AcousticsEfx#setRoom). */
    record Room(float wet, float decay, float decayHfRatio, float gainHf, float density, float diffusion,
                float reflections, float reflectionsDelay, float late, float lateDelay) {
        static final Room DRY = new Room(0.0f, 0.4f, 0.5f, 0.5f, 0.5f, 0.6f, 0.0f, 0.007f, 0.0f, 0.011f);

        Room lerp(Room to, float t) {
            return new Room(Mth.lerp(t, wet, to.wet), Mth.lerp(t, decay, to.decay), Mth.lerp(t, decayHfRatio, to.decayHfRatio),
                    Mth.lerp(t, gainHf, to.gainHf), Mth.lerp(t, density, to.density), Mth.lerp(t, diffusion, to.diffusion),
                    Mth.lerp(t, reflections, to.reflections), Mth.lerp(t, reflectionsDelay, to.reflectionsDelay),
                    Mth.lerp(t, late, to.late), Mth.lerp(t, lateDelay, to.lateDelay));
        }
    }

    /**
     * What a level's surfaces do to sound: how much reverb at most, how long it rings at full
     * size, how much of the highs the walls swallow (gainHf, decayHfRatio) and how "grainy" the
     * echo is (diffusion; hard narrow pipes are less smooth). {@code skyDamp} is how much open sky
     * above you weakens the echo (open air doesn't ring); {@code lateMul} scales the late echo's
     * loudness and how long it takes to come back (a colossal hall answers late and loud);
     * {@code earlyMul} scales the first slap off the nearest walls (soft surfaces swallow it).
     */
    private record Material(float wet, float decayMul, float gainHf, float decayHfRatio, float diffusion,
                            float skyDamp, float lateMul, float earlyMul) {
        Material(float wet, float decayMul, float gainHf, float decayHfRatio, float diffusion) {
            this(wet, decayMul, gainHf, decayHfRatio, diffusion, 0.85f, 1.0f);
        }

        Material(float wet, float decayMul, float gainHf, float decayHfRatio, float diffusion,
                 float skyDamp, float lateMul) {
            this(wet, decayMul, gainHf, decayHfRatio, diffusion, skyDamp, lateMul, 1.0f);
        }

        Material lerp(Material to, float t) {
            return new Material(Mth.lerp(t, wet, to.wet), Mth.lerp(t, decayMul, to.decayMul), Mth.lerp(t, gainHf, to.gainHf),
                    Mth.lerp(t, decayHfRatio, to.decayHfRatio), Mth.lerp(t, diffusion, to.diffusion),
                    Mth.lerp(t, skyDamp, to.skyDamp), Mth.lerp(t, lateMul, to.lateMul), Mth.lerp(t, earlyMul, to.earlyMul));
        }
    }

    // Level 0: a carpeted office under acoustic ceiling tiles. Carpet, tiles and papered plaster
    // swallow the highs and most of the first slap off the walls, so close up it sounds dead and
    // dry, like a real office. But the halls go on and on: what does come back is a soft, dark
    // tail that arrives a little late (lateMul), so you can't quite tell how big the space is.
    // A small room rings for about 0.3 s, the open fields for under 2 s.
    private static final Material LOBBY = new Material(0.5f, 0.55f, 0.35f, 0.35f, 1.0f, 0.85f, 1.25f, 0.55f);
    // Level 0's tall spaces (the Atrium's void, the shafts): above the carpet and the tiles there is
    // nothing soft, only bare shaft walls going up into the dark. The echo is wetter, rings longer
    // and keeps more of its highs, and its late part comes back from far above, late and loud,
    // so a footstep seems to climb away up the shaft. Blended in by how far the upward rays reach
    // (from TALL_FROM blocks on average, fully TALL_SPAN blocks further).
    private static final Material LOBBY_TALL = new Material(0.8f, 1.35f, 0.55f, 0.55f, 0.9f, 0.85f, 2.0f, 0.75f);
    private static final float TALL_FROM = 3.0f;
    private static final float TALL_SPAN = 8.0f;
    // Level 1: bare concrete parking garage: bright, long, a big space.
    private static final Material GARAGE = new Material(0.75f, 1.35f, 0.85f, 0.9f, 1.0f);
    // Level 2: one long, narrow concrete corridor lined with steel pipes: hard and a little
    // metallic (less diffusion = a grainier, fluttery echo between the close walls), ringing a bit
    // longer than its narrow width suggests, and a louder, later echo coming back from far down
    // the straight line (lateMul).
    private static final Material PIPES = new Material(0.85f, 1.0f, 0.92f, 1.1f, 0.45f, 0.85f, 1.3f);
    // Level 4: carpeted offices, ceiling tiles: dead and quiet.
    private static final Material OFFICE = new Material(0.45f, 0.7f, 0.4f, 0.4f, 0.9f);
    // Level 9 and Level 94: mostly open air; inside the houses, small furnished rooms.
    private static final Material OUTDOORS = new Material(0.45f, 0.75f, 0.55f, 0.6f, 0.8f);
    // Level 7: a vast concrete ceiling over open water: long, dark, smooth echoes (the groans roll on).
    private static final Material SEA = new Material(0.7f, 1.6f, 0.6f, 0.6f, 1.0f);
    // Level 39's town: the superstructure encloses it like one gigantic hall, so it is "outdoors"
    // that echoes enormously (canon: heavily echoed). Long decay (~9 s in the open), loud late
    // echoes coming back from the far facades, highs fading as they travel. The empty air above
    // does NOT weaken it (skyDamp 0): above you is the superstructure's ceiling, not sky.
    private static final Material MEGALOPHOBIA = new Material(0.95f, 2.6f, 0.6f, 0.55f, 1.0f, 0.0f, 2.2f);
    // Level 39's tunnels: canon says the marble dampens sound. Short, soft and dry-ish, the highs
    // swallowed, little late echo, even though the tunnels are long and hard-walled.
    private static final Material MARBLE_TUNNEL = new Material(0.4f, 0.35f, 0.35f, 0.35f, 1.0f, 0.85f, 0.6f);
    // Level 227's forest: no walls, but thousands of trunks, leaves and hanging moss scatter sound
    // into a soft, very diffuse tail; the leaves swallow the highs. Open sky over a gap weakens it
    // less than in a town (the trunks all round still answer), and the far echo comes back a
    // little late off the giant branches and the trunk.
    private static final Material FOREST = new Material(0.55f, 0.95f, 0.45f, 0.5f, 1.0f, 0.55f, 1.3f);
    // Level 369's rooms: plaster walls, but a lawn and flowers wall to wall and soft acoustic
    // ceilings. A short, soft, very diffuse room sound with the highs swallowed: close and calm.
    private static final Material GARDEN = new Material(0.5f, 0.6f, 0.45f, 0.45f, 1.0f);
    // Level 228, the carnival mirror maze: one material per zone (client/level228/
    // Level228Atmosphere#zone), eased by the room probe as you walk between them.
    // The Glass Labyrinth: glass and steel, nothing soft. A bright, short echo, fluttery between
    // the close parallel panes (low diffusion), its late part coming back down the mirrored runs.
    private static final Material GLASS = new Material(0.8f, 0.7f, 1.0f, 1.15f, 0.35f, 0.85f, 1.4f, 1.1f);
    // The foyer and the Crystal Maze: old floorboards, wooden frames and a coffered wooden
    // ceiling between the mirrors. Warmer and softer: the wood takes the top off, the slap off the
    // glass is still there, the tail is short and smooth.
    private static final Material MIRROR_WOOD = new Material(0.65f, 0.75f, 0.6f, 0.7f, 0.7f, 0.85f, 1.2f, 1.0f);
    // The Alhambra Hall: plaster, tile and glass: the brightest, longest ring of the maze.
    private static final Material MIRROR_TILE = new Material(0.8f, 0.95f, 0.85f, 1.0f, 0.55f, 0.85f, 1.35f, 1.05f);
    // The Funhouse: a sagging canvas ceiling and linoleum swallow most of it: close and dull.
    private static final Material CANVAS = new Material(0.5f, 0.55f, 0.4f, 0.45f, 0.9f, 0.85f, 1.0f, 0.8f);
    // The final chamber: a small closed room, still glass on three sides; a short, close echo
    // with almost no tail, so the silence there is "dead".
    private static final Material CHAMBER = new Material(0.55f, 0.45f, 0.8f, 0.9f, 0.5f, 0.85f, 0.6f, 1.0f);
    // The exit hallway: a long bare concrete corridor with leaves blown in: longer and plainer.
    private static final Material EXIT_HALL = new Material(0.7f, 1.1f, 0.75f, 0.8f, 0.9f, 0.85f, 1.3f, 1.0f);
    // Backstage: narrow plywood passages full of crates: dry, boxy, short.
    private static final Material BACKSTAGE = new Material(0.5f, 0.5f, 0.5f, 0.55f, 0.8f, 0.85f, 0.8f, 0.9f);

    private static Material level228() {
        return switch (com.rickr.backrooms.client.level228.Level228Atmosphere.zone()) {
            case FOYER, CRYSTAL, NONE -> MIRROR_WOOD;
            case ALHAMBRA -> MIRROR_TILE;
            case FUNHOUSE -> CANVAS;
            case GLASS -> GLASS;
            case CHAMBER -> CHAMBER;
            case EXIT_HALL -> EXIT_HALL;
            case BACKSTAGE -> BACKSTAGE;
        };
    }

    /** Level 39: the tunnels (and the holes/shafts leading down to them) lie below this height. */
    private static final double LEVEL39_TUNNEL_TOP = 60.0;

    @Nullable
    private static Material material(@Nullable BackroomsLevel level, Vec3 ear) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case LEVEL_0 -> LOBBY;
            case LEVEL_1 -> GARAGE;
            case LEVEL_2 -> PIPES;
            case LEVEL_4 -> OFFICE;
            case LEVEL_9, LEVEL_94, LEVEL_490 -> OUTDOORS;
            case LEVEL_39 -> ear.y < LEVEL39_TUNNEL_TOP ? MARBLE_TUNNEL : MEGALOPHOBIA;
            case LEVEL_7 -> SEA;
            case LEVEL_227 -> FOREST;
            case LEVEL_369 -> GARDEN;
            case LEVEL_228 -> level228();
            case POOLROOMS, LEVEL_37 -> null; // nothing reverberates there (canon), muffling only
        };
    }

    // --- Muffling ------------------------------------------------------------------------------
    /** Each solid block between you and a sound keeps this much of its highs ... */
    private static final float HF_PER_WALL = 0.28f;
    /** ... and this much of its volume. */
    private static final float GAIN_PER_WALL = 0.78f;
    /** Never quieter or duller than this, however many walls (sound still finds a way through). */
    private static final float MIN_GAIN = 0.35f;
    private static final float MIN_HF = 0.04f;
    /** At most this many walls counted; and a door, glass or a fixture counts as part of one. */
    private static final float MAX_WALLS = 4.0f;
    private static final float THIN_BLOCK = 0.4f;
    /** Sounds this close are never muffled (your own steps, a lamp right above you). */
    private static final double NEAR = 1.5;
    /** Muffling of playing sounds is re-checked every this many ticks ... */
    private static final int OCCLUSION_EVERY = 3;
    /** ... and eased toward the new value by this share each time (no clicks when you turn a corner). */
    private static final float OCCLUSION_EASE = 0.5f;
    /** At most this many sounds followed at once (the rest keep their muffling from the start). */
    private static final int MAX_TRACKED = 128;
    /** Head fully under water: every sound keeps this much of its volume ... */
    private static final float SUBMERGED_GAIN = 0.5f;
    /** ... and this much of its highs (on top of any walls). */
    private static final float SUBMERGED_HF = 0.1f;
    /** Our own underwater sounds (their path starts with this) are what you hear under water: never muffled. */
    private static final String UNDERWATER_OWN = "level227.water.under";
    /** 0..1 how far the listener's head is under water (client thread). */
    private static float submerged;

    // --- Room probe ----------------------------------------------------------------------------
    /** The room is felt out every this many ticks, with rays this long. */
    private static final int ROOM_EVERY = 8;
    private static final double RAY_LENGTH = 40.0;
    /** Rays: 8 around you level, 8 tilted up 40 degrees, straight up, 4 tilted down 25 degrees. */
    private static final Vec3[] RAYS = rays();
    /** The reverb eases toward the newly measured room by this share per probe. */
    private static final float ROOM_EASE = 0.35f;

    /** A sound we follow: written on the client thread, read on the sound thread. */
    private static final class Tracked {
        volatile float gain = 1.0f;
        volatile float gainHf = 1.0f;
        volatile boolean reverb;
        /** Played "in your head" (ambience): no walls, no room echo, only the underwater muffle. */
        boolean inHead;
    }

    /**
     * How far the listener's head is under water, 0..1 (from client/water/UnderwaterAudio, once
     * per tick). Playing sounds pick up the change within a few ticks.
     */
    public static void setSubmerged(float amount) {
        submerged = Mth.clamp(amount, 0.0f, 1.0f);
    }

    private static final Map<SoundInstance, Tracked> TRACKED = new ConcurrentHashMap<>();
    private static final AcousticsEfx EFX = new AcousticsEfx();

    private static Room room = Room.DRY;
    private static boolean roomDry = true;
    private static int ticks;

    // ==========================================================================================
    // Client thread
    // ==========================================================================================

    /** Just before a sound starts (client thread): decide its muffling, so it starts muffled. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (sound == null || !affects(sound) || mc.level == null || TRACKED.size() >= MAX_TRACKED) {
            return;
        }
        BackroomsLevel level = BackroomsLevel.fromDimension(mc.level.dimension());
        if (level == null) {
            return;
        }
        Tracked t = new Tracked();
        t.inHead = inYourHead(sound);
        float walls = t.inHead ? 0.0f : walls(mc.level, mc.gameRenderer.getMainCamera(), sound);
        t.gain = gainFor(walls);
        t.gainHf = hfFor(walls);
        t.reverb = !t.inHead && material(level, mc.gameRenderer.getMainCamera().getPosition()) != null;
        TRACKED.put(sound, t);
    }

    /**
     * World sounds (placed somewhere, fading with distance), and the in-your-head ambience loops
     * (only for the underwater muffle); never music, UI or the camcorder, nor our underwater bed.
     */
    private static boolean affects(SoundInstance sound) {
        SoundSource source = sound.getSource();
        if (source == SoundSource.MUSIC || source == SoundSource.MASTER || source == SoundSource.VOICE) {
            return false;
        }
        if (inYourHead(sound)) {
            return (source == SoundSource.AMBIENT || source == SoundSource.WEATHER)
                    && !sound.getLocation().getPath().startsWith(UNDERWATER_OWN);
        }
        return true;
    }

    private static boolean inYourHead(SoundInstance sound) {
        return sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        BackroomsLevel level = world == null ? null : BackroomsLevel.fromDimension(world.dimension());
        if (level == null) {
            TRACKED.clear();
            if (!roomDry) {
                pushRoom(Room.DRY);
                room = Room.DRY;
                roomDry = true;
            }
            return;
        }
        ticks++;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (ticks % ROOM_EVERY == 0) {
            Material material = material(level, camera.getPosition());
            Room target = material == null ? Room.DRY : measure(world, camera.getPosition(), material);
            room = room.lerp(target, ROOM_EASE);
            roomDry = material == null && room.wet() < 0.005f;
            pushRoom(room);
        }
        if (ticks % OCCLUSION_EVERY == 0) {
            updateTracked(mc, world, camera);
        }
    }

    /** Re-checks the walls between you and every sound still playing, and re-filters it. */
    private static void updateTracked(Minecraft mc, ClientLevel world, Camera camera) {
        SoundEngine engine = ((SoundManagerAccessor) mc.getSoundManager()).backrooms$soundEngine();
        Map<SoundInstance, ChannelAccess.ChannelHandle> playing = ((SoundEngineAccessor) engine).backrooms$instanceToChannel();
        Iterator<Map.Entry<SoundInstance, Tracked>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SoundInstance, Tracked> entry = it.next();
            SoundInstance sound = entry.getKey();
            ChannelAccess.ChannelHandle handle = playing.get(sound);
            if (handle == null) {
                // Not playing (finished, or never started): forget it. A brand-new sound may not
                // have its channel yet; it got its start values in onPlaySound anyway.
                if (!mc.getSoundManager().isActive(sound)) {
                    it.remove();
                }
                continue;
            }
            Tracked t = entry.getValue();
            float walls = t.inHead ? 0.0f : walls(world, camera, sound);
            float gain = Mth.lerp(OCCLUSION_EASE, t.gain, gainFor(walls));
            float gainHf = Mth.lerp(OCCLUSION_EASE, t.gainHf, hfFor(walls));
            if (Math.abs(gain - t.gain) < 0.01f && Math.abs(gainHf - t.gainHf) < 0.01f) {
                continue;
            }
            t.gain = gain;
            t.gainHf = gainHf;
            boolean reverb = t.reverb;
            handle.execute(channel -> EFX.applyToSource(sourceOf(channel), gain, gainHf, reverb));
        }
    }

    private static void pushRoom(Room r) {
        SoundEngine engine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).backrooms$soundEngine();
        ((SoundEngineAccessor) engine).backrooms$executor().execute(() -> EFX.setRoom(r));
    }

    private static float gainFor(float walls) {
        return Math.max(MIN_GAIN, (float) Math.pow(GAIN_PER_WALL, walls)) * Mth.lerp(submerged, 1.0f, SUBMERGED_GAIN);
    }

    private static float hfFor(float walls) {
        return Math.max(MIN_HF, (float) Math.pow(HF_PER_WALL, walls)) * Mth.lerp(submerged, 1.0f, SUBMERGED_HF);
    }

    /**
     * How many walls stand between the camera and the sound: walks the blocks on the straight
     * line between them (the sound's own block and yours don't count). A full solid block counts
     * 1; anything else with a shape (a door, glass, a fixture, a fence) a little less.
     */
    private static float walls(ClientLevel world, Camera camera, SoundInstance sound) {
        Vec3 ear = camera.getPosition();
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        double distance = ear.distanceTo(at);
        if (distance < NEAR || distance > 96.0) {
            return 0.0f;
        }
        BlockPos start = BlockPos.containing(ear);
        BlockPos end = BlockPos.containing(at);
        // Step along the line in quarter-block steps, counting each block once.
        int steps = (int) Math.ceil(distance * 4.0);
        float walls = 0.0f;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        for (int i = 1; i < steps && walls < MAX_WALLS; i++) {
            double t = (double) i / steps;
            pos.set(Mth.floor(Mth.lerp(t, ear.x, at.x)), Mth.floor(Mth.lerp(t, ear.y, at.y)), Mth.floor(Mth.lerp(t, ear.z, at.z)));
            long key = pos.asLong();
            if (key == last || pos.equals(start) || pos.equals(end)) {
                continue;
            }
            last = key;
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.isSolidRender(world, pos)) {
                walls += 1.0f;
            } else if (!state.getCollisionShape(world, pos).isEmpty()) {
                walls += THIN_BLOCK;
            }
        }
        return Math.min(walls, MAX_WALLS);
    }

    /**
     * Feels out the space around {@code ear}: the average distance to the walls sets the room's
     * size, and the share of upward rays that hit nothing (open sky) makes the echo weaker: open
     * air doesn't ring. (A long corridor sends level rays far but still has a ceiling: it rings.)
     */
    private static Room measure(ClientLevel world, Vec3 ear, Material m) {
        double sum = 0.0;
        double upSum = 0.0;
        int up = 0;
        int sky = 0;
        for (Vec3 dir : RAYS) {
            Vec3 to = ear.add(dir.scale(RAY_LENGTH));
            BlockHitResult hit = world.clip(new ClipContext(ear, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            boolean miss = hit.getType() == HitResult.Type.MISS;
            double distance = miss ? RAY_LENGTH : hit.getLocation().distanceTo(ear);
            sum += distance;
            if (dir.y > 0.1) {
                up++;
                upSum += distance;
                if (miss) {
                    sky++;
                }
            }
        }
        if (m == LOBBY) {
            // Level 0: under its usual 3-high ceiling the upward rays hit the tiles about 2 blocks
            // off; in the Atrium or a tall shaft they go 10 or more. Blend toward the tall preset.
            m = LOBBY.lerp(LOBBY_TALL, Mth.clamp((float) (upSum / up - TALL_FROM) / TALL_SPAN, 0.0f, 1.0f));
        }
        float mean = (float) (sum / RAYS.length);
        float skyShare = (float) sky / up;
        // 0 = a cupboard, 1 = a hall some 25 blocks across or more.
        float size = Mth.clamp((mean - 2.0f) / 22.0f, 0.0f, 1.0f);
        float decay = Mth.lerp(size, 0.5f, 3.4f) * m.decayMul();
        // Open sky above weakens the echo (by the material's skyDamp; Level 39's town has none).
        float open = skyShare * m.skyDamp();
        float wet = m.wet() * Mth.lerp(size, 0.7f, 1.0f) * (1.0f - open);
        float earlyOpen = Math.min(1.0f, skyShare * m.skyDamp() / 0.85f);
        return new Room(
                Mth.clamp(wet, 0.0f, 1.0f),
                Mth.clamp(decay, 0.1f, 20.0f),
                Mth.clamp(m.decayHfRatio(), 0.1f, 2.0f),
                Mth.clamp(m.gainHf(), 0.0f, 1.0f),
                Mth.lerp(size, 0.6f, 1.0f),
                m.diffusion(),
                // Small rooms: a strong early slap off the walls (less where soft surfaces absorb it).
                Mth.lerp(size, 1.2f, 0.5f) * (1.0f - earlyOpen) * m.earlyMul(),
                // OpenAL limits: reflections delay <= 0.3 s, late delay <= 0.1 s, late gain <= 10.
                Mth.clamp(Mth.lerp(size, 0.004f, 0.035f) * m.lateMul(), 0.0f, 0.3f),
                Mth.clamp(Mth.lerp(size, 1.2f, 1.8f) * m.lateMul(), 0.0f, 10.0f),
                Mth.clamp(Mth.lerp(size, 0.008f, 0.045f) * m.lateMul(), 0.0f, 0.1f));
    }

    private static Vec3[] rays() {
        Vec3[] out = new Vec3[21];
        int n = 0;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            out[n++] = new Vec3(Math.cos(a), 0.0, Math.sin(a));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 8;
            double up = Math.toRadians(40);
            out[n++] = new Vec3(Math.cos(a) * Math.cos(up), Math.sin(up), Math.sin(a) * Math.cos(up));
        }
        out[n++] = new Vec3(0.0, 1.0, 0.0);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4 + Math.PI / 4;
            double down = Math.toRadians(-25);
            out[n++] = new Vec3(Math.cos(a) * Math.cos(down), Math.sin(down), Math.sin(a) * Math.cos(down));
        }
        return out;
    }

    // ==========================================================================================
    // Sound thread
    // ==========================================================================================

    /** A sound's channel just started (sound thread): give it the muffling decided a moment ago. */
    @SubscribeEvent
    public static void onPlaySource(PlaySoundSourceEvent event) {
        start(event.getSound(), event.getChannel());
    }

    @SubscribeEvent
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        start(event.getSound(), event.getChannel());
    }

    private static void start(SoundInstance sound, Channel channel) {
        Tracked t = TRACKED.get(sound);
        if (t != null) {
            EFX.applyToSource(sourceOf(channel), t.gain, t.gainHf, t.reverb);
        }
    }

    private static int sourceOf(Channel channel) {
        return ((ChannelAccessor) channel).backrooms$source();
    }

    private Acoustics() {}
}
