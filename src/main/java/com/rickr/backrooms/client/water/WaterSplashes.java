package com.rickr.backrooms.client.water;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModParticles;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Splashes and ripples on Level 227's ponds, client side.
 *
 * <p>Every tick we look at the entities near the camera (players and animals alike). The tick one
 * of them goes from "not touching water" to "touching water", it hit the surface: how fast it was
 * falling picks the splash. Each client does this for what it sees itself, so in multiplayer
 * everyone sees and hears every splash without anything sent over the network.
 * <ul>
 *   <li><b>Splash</b>, scaled by the speed and by the body's size: a crown of drops thrown up
 *       and out around the body (big ones also a column straight up), one to three rings
 *       spreading on the surface, foam, and bubbles dragged down with the body that then rise
 *       again. Our water renderer also gets the impact, so the reflection and the bottom seen
 *       through the water ripple outward ({@link WaterRipples}).</li>
 *   <li><b>Sound</b> in three sizes: a small plop (jumping in from the bank), a splash (a drop of
 *       a few blocks), a big belly-flop (a fall of about six blocks or more).</li>
 *   <li><b>Wading and swimming at the surface:</b> small rings around you as you move (faster =
 *       more often, with a few drops and a bit of foam), and vanilla's loud swimming stroke
 *       is swapped for a soft one (a soft wading step when you walk through the shallows).</li>
 * </ul>
 *
 * <p><b>Vanilla's splash:</b> its sound is replaced (cancelled here, ours plays instead), so it
 * never plays twice; its few splash and bubble particles are kept underneath ours (removing them
 * would need a mixin into the entity, for little gain). Only in Level 227.
 *
 * <p>Limits: at most {@value #MAX_DROPLETS} drops, {@value #MAX_BUBBLES} bubbles and
 * {@value #MAX_FOAM} foam patches per splash, at most {@value #MAX_SPLASHES_PER_TICK} splashes a
 * tick, and all of it shares a budget of {@value #BUDGET_PER_TICK} particles per tick (saved up
 * to {@value #BUDGET_BURST} for a burst). Per tick, nothing is allocated except a new entity's
 * small record (reused from a pool once it leaves).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class WaterSplashes {
    // --- How fast something hit the water (blocks per tick, falling). Free fall reaches 0.5 after
    // about 2 blocks and 0.85 after about 6; a jump in from the bank lands at about 0.3.
    /** Slower than this you stepped or slid in: only a ring. */
    static final double SPLASH_MIN = 0.12;
    /** From here a proper splash (sound and spray). */
    static final double SPLASH_MEDIUM = 0.5;
    /** From here a belly-flop. */
    static final double SPLASH_BIG = 0.85;

    // --- Caps.
    static final int MAX_DROPLETS = 40;
    static final int MAX_BUBBLES = 16;
    static final int MAX_FOAM = 6;
    static final int MAX_SPLASHES_PER_TICK = 6;
    static final int BUDGET_PER_TICK = 60;
    static final int BUDGET_BURST = 240;
    /** Entities further than this from the camera are not watched (blocks). */
    private static final double RANGE = 40.0;

    // --- Sound volumes (the files are made at -27 / -23 / -20 LUFS, the strokes at -31).
    private static final float SMALL_VOLUME = 0.6f;
    private static final float MEDIUM_VOLUME = 0.85f;
    private static final float BIG_VOLUME = 1.0f;
    /** A dropped item or an arrow: a much quieter plop. */
    private static final float OBJECT_VOLUME = 0.3f;

    /** What we remember about one entity between ticks. */
    private static final class Track {
        boolean inWater;
        double dyPrev;
        int seen;
        int rippleIn;
        boolean fresh;
    }

    private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();
    private static final ArrayDeque<Track> POOL = new ArrayDeque<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private static ClientLevel trackedLevel;
    private static int ticks;
    private static int budget;

    private WaterSplashes() {}

    /** The levels with these splashes. */
    static boolean active(Level level) {
        return BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_227;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !active(level)) {
            if (trackedLevel != null) {
                forgetAll();
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (level != trackedLevel) {
            forgetAll();
            trackedLevel = level;
        }
        ticks++;
        budget = Math.min(BUDGET_BURST, budget + BUDGET_PER_TICK);
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        int splashes = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isSpectator() || entity.isPassenger() || entity.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            Track track = TRACKS.get(entity.getId());
            if (track == null) {
                track = POOL.isEmpty() ? new Track() : POOL.pop();
                track.fresh = true;
                track.rippleIn = 0;
                TRACKS.put(entity.getId(), track);
            }
            track.seen = ticks;
            boolean inWater = entity.isInWater();
            double dy = entity.getY() - entity.yo;
            if (!track.fresh) {
                if (inWater && !track.inWater) {
                    // The speed it hit the water with: this tick's fall or the last one's (the
                    // water may already have braked it, and other players' motion is smoothed).
                    if (splashes < MAX_SPLASHES_PER_TICK && enter(level, entity, Math.max(-dy, -track.dyPrev))) {
                        splashes++;
                    }
                } else if (inWater && entity instanceof LivingEntity) {
                    surface(level, entity, track, dy);
                }
            }
            track.fresh = false;
            track.inWater = inWater;
            track.dyPrev = dy;
        }
        if (ticks % 20 == 0) {
            sweep();
        }
    }

    /** Something just hit the water at `speed` blocks/tick downward. False if no surface was found. */
    private static boolean enter(ClientLevel level, Entity entity, double speed) {
        double x = entity.getX();
        double z = entity.getZ();
        double top = surfaceY(level, x, entity.getY(), z);
        if (Double.isNaN(top)) {
            return false;
        }
        float width = entity.getBbWidth();
        boolean body = entity instanceof LivingEntity;
        // How much water it moves: a player 1, a bear about 2, a dropped item a third.
        float bulk = body ? Mth.clamp((float) Math.sqrt(width / 0.6f), 0.6f, 2.0f) : 0.35f;

        if (speed < SPLASH_MIN) {
            // Stepped or slid in: just a ring (vanilla's stroke sound is the sound).
            spawn(level, ModParticles.L227_WATER_RING.get(), x, top + 0.015, z, width * 0.5 + 0.15, 0.0, 0.0);
            WaterRipples.add(x, top, z, 0.15f * bulk);
            return true;
        }
        // 0 = a small plop ... 1 = a belly-flop (a little more for anything faster).
        float k = (float) Mth.clamp((speed - SPLASH_MIN) / (SPLASH_BIG - SPLASH_MIN), 0.0, 1.3);

        // The crown: drops thrown up and outward from the edge of the hole.
        int drops = Math.min(MAX_DROPLETS, Math.round((6 + 30 * k) * bulk));
        for (int i = 0; i < drops; i++) {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            float c = Mth.cos(angle);
            float s = Mth.sin(angle);
            double r = width * 0.5 * (0.7 + 0.5 * RANDOM.nextDouble());
            double out = (0.03 + 0.10 * k) * (0.6 + 0.8 * RANDOM.nextDouble());
            double up = (0.10 + 0.30 * k) * (0.5 + 0.7 * RANDOM.nextDouble());
            if (!spawn(level, ModParticles.L227_WATER_DROPLET.get(), x + c * r, top + 0.05, z + s * r, c * out, up, s * out)) {
                break;
            }
        }
        // A belly-flop also throws a column of water straight up out of the middle.
        if (speed >= SPLASH_BIG) {
            for (int i = 0; i < 5; i++) {
                spawn(level, ModParticles.L227_WATER_DROPLET.get(), x + (RANDOM.nextDouble() - 0.5) * width * 0.4, top + 0.1,
                        z + (RANDOM.nextDouble() - 0.5) * width * 0.4, (RANDOM.nextDouble() - 0.5) * 0.04,
                        0.45 + RANDOM.nextDouble() * 0.2, (RANDOM.nextDouble() - 0.5) * 0.04);
            }
        }
        // Rings on the surface: one for a plop, three for a belly-flop.
        int rings = speed >= SPLASH_BIG ? 3 : speed >= SPLASH_MEDIUM ? 2 : 1;
        for (int i = 0; i < rings; i++) {
            spawn(level, ModParticles.L227_WATER_RING.get(), x, top + 0.015, z, (width * 0.55 + 0.1) * (1.0 + 0.35 * i), 0.0, 0.0);
        }
        // Foam left churning where it went in.
        int foam = Math.min(MAX_FOAM, Math.round((speed >= SPLASH_BIG ? 6 : speed >= SPLASH_MEDIUM ? 3 : 0) * Math.min(bulk, 1.0f)));
        for (int i = 0; i < foam; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * width * 1.4;
            double oz = (RANDOM.nextDouble() - 0.5) * width * 1.4;
            spawn(level, ModParticles.L227_WATER_FOAM.get(), x + ox, top + 0.02, z + oz,
                    (0.25 + 0.3 * RANDOM.nextDouble()) * bulk, 0.0, 0.0);
        }
        // Air dragged down with the body: bubbles heading down, then rising back.
        if (body) {
            int bubbles = Math.min(MAX_BUBBLES, Math.round((3 + 13 * k) * bulk));
            double reach = Math.min(1.2, speed * 1.5);
            for (int i = 0; i < bubbles; i++) {
                double bx = x + (RANDOM.nextDouble() - 0.5) * width * 0.8;
                double bz = z + (RANDOM.nextDouble() - 0.5) * width * 0.8;
                double by = top - 0.2 - RANDOM.nextDouble() * reach;
                double down = -(0.08 + 0.45 * k) * (0.5 + 0.5 * RANDOM.nextDouble());
                if (!spawn(level, ModParticles.L227_WATER_BUBBLE.get(), bx, by, bz,
                        (RANDOM.nextDouble() - 0.5) * 0.04, down, (RANDOM.nextDouble() - 0.5) * 0.04)) {
                    break;
                }
            }
        }
        playSplash(level, entity, x, top, z, speed, body, bulk);
        WaterRipples.add(x, top, z, Mth.clamp(0.25f + 0.75f * k, 0.0f, 1.0f) * Math.min(1.0f, bulk));
        return true;
    }

    private static void playSplash(ClientLevel level, Entity entity, double x, double top, double z, double speed,
                                   boolean body, float bulk) {
        SoundEvent sound;
        float volume;
        if (!body) {
            sound = ModSounds.L227_SPLASH_SMALL.get();
            volume = OBJECT_VOLUME;
        } else if (speed >= SPLASH_BIG) {
            sound = ModSounds.L227_SPLASH_BIG.get();
            volume = BIG_VOLUME;
        } else if (speed >= SPLASH_MEDIUM) {
            sound = ModSounds.L227_SPLASH_MEDIUM.get();
            volume = MEDIUM_VOLUME;
        } else {
            sound = ModSounds.L227_SPLASH_SMALL.get();
            volume = SMALL_VOLUME + 0.4f * (float) ((speed - SPLASH_MIN) / (SPLASH_MEDIUM - SPLASH_MIN));
        }
        // Bigger bodies splash deeper; a little variety so two splashes never sound identical.
        float pitch = Mth.clamp((0.96f + RANDOM.nextFloat() * 0.08f) / (float) Math.sqrt(bulk), 0.7f, 1.3f);
        level.playLocalSound(x, top, z, sound, entity.getSoundSource(), volume, pitch, false);
    }

    /** Small rings (and at speed a few drops and some foam) around something moving at the surface. */
    private static void surface(ClientLevel level, Entity entity, Track track, double dy) {
        if (entity.isUnderWater() || --track.rippleIn > 0) {
            return;
        }
        double hx = entity.getX() - entity.xo;
        double hz = entity.getZ() - entity.zo;
        double h = Math.sqrt(hx * hx + hz * hz);
        if (h < 0.02 && Math.abs(dy) < 0.03) {
            track.rippleIn = 10; // still: check again in half a second
            return;
        }
        double top = surfaceY(level, entity.getX(), entity.getY(), entity.getZ());
        if (Double.isNaN(top)) {
            track.rippleIn = 10;
            return;
        }
        float width = entity.getBbWidth();
        spawn(level, ModParticles.L227_WATER_RING.get(), entity.getX(), top + 0.015, entity.getZ(), width * 0.45 + 0.1, 0.0, 0.0);
        if (h > 0.12) {
            // Pushing through the water: a drop or two flicked up ahead.
            double fx = entity.getX() + hx / h * width * 0.5;
            double fz = entity.getZ() + hz / h * width * 0.5;
            for (int i = 0; i < 2; i++) {
                spawn(level, ModParticles.L227_WATER_DROPLET.get(), fx, top + 0.05, fz,
                        hx * 0.4 + (RANDOM.nextDouble() - 0.5) * 0.04, 0.08 + RANDOM.nextDouble() * 0.08,
                        hz * 0.4 + (RANDOM.nextDouble() - 0.5) * 0.04);
            }
        }
        if (h > 0.18 && RANDOM.nextFloat() < 0.3f) {
            spawn(level, ModParticles.L227_WATER_FOAM.get(), entity.getX(), top + 0.02, entity.getZ(), 0.2 + width * 0.2, 0.0, 0.0);
        }
        // Walking slowly: a ring every 12 ticks; swimming or running: every 4.
        track.rippleIn = h < 0.02 ? 30 : Math.max(4, (int) (12 - h * 50));
    }

    /** Spends one particle of the budget; false (and nothing spawned) once it is used up. */
    private static boolean spawn(ClientLevel level, SimpleParticleType type, double x, double y, double z,
                                 double a, double b, double c) {
        if (budget <= 0) {
            return false;
        }
        budget--;
        level.addParticle(type, x, y, z, a, b, c);
        return true;
    }

    /**
     * World height of the water surface over something in the water at (x, y, z): climbs from
     * its feet (or the block under them) to the top water block. NaN if there is no water there.
     */
    static double surfaceY(ClientLevel level, double x, double y, double z) {
        CURSOR.set(x, y, z);
        if (!level.getFluidState(CURSOR).is(FluidTags.WATER)) {
            CURSOR.move(0, -1, 0);
            if (!level.getFluidState(CURSOR).is(FluidTags.WATER)) {
                return Double.NaN;
            }
        }
        for (int i = 0; i < 32; i++) {
            if (!level.getFluidState(CURSOR.move(0, 1, 0)).is(FluidTags.WATER)) {
                CURSOR.move(0, -1, 0);
                FluidState top = level.getFluidState(CURSOR);
                return CURSOR.getY() + top.getOwnHeight();
            }
        }
        return Double.NaN;
    }

    /** Forgets entities not seen for a second (gone, or out of range). */
    private static void sweep() {
        ObjectIterator<Int2ObjectMap.Entry<Track>> it = TRACKS.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<Track> entry = it.next();
            if (ticks - entry.getValue().seen > 20) {
                POOL.push(entry.getValue());
                it.remove();
            }
        }
    }

    private static void forgetAll() {
        for (Track track : TRACKS.values()) {
            POOL.push(track);
        }
        TRACKS.clear();
        trackedLevel = null;
    }

    // --- Vanilla's sounds ----------------------------------------------------------------------

    /**
     * Sounds placed in the world (other players' and the animals', sent by the server). Vanilla's
     * splash is dropped (ours plays from what this client saw); the swimming stroke becomes our
     * soft one, or a soft wading step for a player walking through the shallows.
     * Only on the client's own level (in single player the server's copy of the event comes
     * through here too and must be left alone).
     */
    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        Level level = event.getLevel();
        Holder<SoundEvent> sound = event.getSound();
        if (sound == null || !level.isClientSide() || !active(level)) {
            return;
        }
        if (isSplash(sound.value())) {
            event.setCanceled(true);
        } else if (isSwim(sound.value())) {
            Player wader = wadingPlayerAt(level, event.getPosition());
            softenStroke(event, wader != null, wader != null && wader.isSprinting());
        }
    }

    /** Sounds of your own player (they go through this event instead). */
    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Level level = event.getLevel();
        Holder<SoundEvent> sound = event.getSound();
        if (sound == null || !level.isClientSide() || !active(level)) {
            return;
        }
        if (isSplash(sound.value())) {
            event.setCanceled(true);
        } else if (isSwim(sound.value())) {
            Entity entity = event.getEntity();
            softenStroke(event, wading(entity), entity.isSprinting());
        }
    }

    private static void softenStroke(PlayLevelSoundEvent event, boolean wading, boolean running) {
        if (wading) {
            event.setSound(running ? ModSounds.STEP_WATER_WADE_RUN : ModSounds.STEP_WATER_WADE_WALK);
            event.setNewVolume(running ? 0.5f : 0.35f);
        } else {
            event.setSound(ModSounds.L227_WATER_PADDLE);
            // Vanilla's stroke volume follows the swimming speed (about 0.05 to 0.35): keep that, softly.
            event.setNewVolume(Mth.clamp(event.getOriginalVolume() * 2.5f, 0.15f, 0.6f));
        }
        event.setNewPitch(0.92f + RANDOM.nextFloat() * 0.16f);
    }

    /** Walking through water with its head out and feet on the bottom. */
    private static boolean wading(Entity entity) {
        return entity.onGround() && !entity.isUnderWater();
    }

    @Nullable
    private static Player wadingPlayerAt(Level level, Vec3 pos) {
        for (Player player : level.players()) {
            // The server places a player's sound at its feet; our copy of that player is smoothed, so allow half a block.
            if (player.position().distanceToSqr(pos) < 0.25) {
                return wading(player) ? player : null;
            }
        }
        return null;
    }

    private static boolean isSplash(SoundEvent sound) {
        return sound == SoundEvents.PLAYER_SPLASH || sound == SoundEvents.PLAYER_SPLASH_HIGH_SPEED
                || sound == SoundEvents.GENERIC_SPLASH || sound == SoundEvents.HOSTILE_SPLASH;
    }

    private static boolean isSwim(SoundEvent sound) {
        return sound == SoundEvents.PLAYER_SWIM || sound == SoundEvents.GENERIC_SWIM || sound == SoundEvents.HOSTILE_SWIM;
    }
}
