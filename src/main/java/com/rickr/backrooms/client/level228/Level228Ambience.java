package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Level228Footsteps;
import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What Level 228 v2 (the Hall of Mirrors, a dead carnival mirror maze) sounds like, client side.
 * A near-silent building; nothing here has a voice (the sounds are tools/level_assets/
 * level228_sound.py):
 * <ul>
 *   <li><b>The room tone</b> ({@code level228.ambience}, "in your head"): a big closed timber shed
 *       at night, almost nothing. Quieter in the Glass Labyrinth, nearly gone in the exit
 *       hallway, gone in the final chamber.</li>
 *   <li><b>The building</b> ({@code level228.creak}): now and then the timber frame settles
 *       somewhere in the dark, 10-26 blocks off, through the walls.</li>
 *   <li><b>The mirrors</b>: a real pane near you ticks as it settles in its frame, more rarely
 *       creaks against it (Level228Plan#mirrorsNear, so you can tell where).</li>
 *   <li><b>The organ</b> ({@code level228.organ}): every few minutes the carnival's band organ
 *       plays a phrase far away beyond the walls, warped and out of tune. Never in the chamber or
 *       the exit hallway (it fades out if you walk in while it plays).</li>
 *   <li><b>Your footsteps answered</b>: with a mirror beside you a faint copy of each of your
 *       steps comes back a moment later from behind the glass, where your reflection walks (the
 *       step reflected across the nearest pane), a little lower and duller. An acoustic
 *       reflection, not an anomaly: subtle, and the same for every mirror.</li>
 *   <li><b>The final chamber</b>: total silence. Every sound above stops; only the faint
 *       pressure of a small closed room ({@code level228.chamber_hush}) and your own steps (music
 *       stops too, client/BackroomsMusic).</li>
 *   <li><b>The exit hallway</b> ({@code level228.exit_wind}): the only place the outside gets in:
 *       wind at the door, leaves skittering, the loose door rattling.</li>
 * </ul>
 * The bulbs' buzz and a failing bulb's chatter are the fixtures' own (client/light/
 * FixtureSounds); the exit hint is client/hint/ExitHints; the floors are world/Level228Footsteps.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Ambience {
    /** Loop volumes per zone (Level228Plan.Zone ordinal): room tone, exit wind, chamber hush. */
    private static final float[] TONE = {0.42f, 0.40f, 0.40f, 0.38f, 0.30f, 0.0f, 0.10f, 0.45f, 0.40f};
    private static final float[] WIND = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.55f, 0.0f, 0.0f};
    private static final float[] HUSH = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.30f, 0.0f, 0.0f, 0.0f};
    /** Loop fade per tick: a zone change crossfades over about a second. */
    private static final float FADE = 0.02f;

    /** A pane ticks every this many ticks or so (12-35 s), creaks more rarely (30-80 s). */
    private static final int TICK_MIN = 240;
    private static final int TICK_MAX = 700;
    private static final int GLASS_CREAK_MIN = 600;
    private static final int GLASS_CREAK_MAX = 1600;
    /** The building settles every 25-70 s, somewhere 10-26 blocks off. */
    private static final int CREAK_MIN = 500;
    private static final int CREAK_MAX = 1400;
    private static final double CREAK_NEAR = 10.0;
    private static final double CREAK_FAR = 26.0;
    /** The organ: first after 1.5-4 minutes in the level, then every 3-7 minutes, 40-60 blocks away. */
    private static final int ORGAN_FIRST_MIN = 1800;
    private static final int ORGAN_FIRST_MAX = 4800;
    private static final int ORGAN_MIN = 3600;
    private static final int ORGAN_MAX = 8400;
    private static final double ORGAN_NEAR = 40.0;
    private static final double ORGAN_FAR = 60.0;
    /** The organ fades out this fast (per tick) when you walk into the chamber or the exit hallway. */
    private static final float ORGAN_FADE = 0.025f;

    /** Mirrors near you are looked up this far (blocks); a pane that sounds is at least NEAR away. */
    private static final double GLASS_REACH = 12.0;
    private static final double GLASS_NEAR = 3.0;

    /** Your step comes back off a pane at most this far to the side of you (blocks) ... */
    private static final double ECHO_REACH = 6.0;
    /**
     * ... this loud (a footstep plays at about 0.15; the event fires before the step is resolved,
     * so its own volume and pitch can't be read) and a little lower ...
     */
    private static final float ECHO_VOLUME = 0.05f;
    private static final float ECHO_PITCH = 0.93f;
    /** ... and this many ticks later, plus one per block to the glass (a far pane answers later). */
    private static final int ECHO_DELAY = 4;
    private static final int ECHO_DELAY_MAX = 10;
    /** A step sound counts as yours when it plays this close to your feet. */
    private static final double OWN_STEP = 1.5;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Level228Plan.Mirror[] MIRRORS = new Level228Plan.Mirror[32];

    static {
        for (int i = 0; i < MIRRORS.length; i++) {
            MIRRORS[i] = new Level228Plan.Mirror();
        }
    }

    @Nullable
    private static FadingLoopSound tone;
    @Nullable
    private static FadingLoopSound wind;
    @Nullable
    private static FadingLoopSound hush;
    @Nullable
    private static OrganSound organ;
    private static int nextTick = TICK_MIN;
    private static int nextGlassCreak = GLASS_CREAK_MIN;
    private static int nextCreak = CREAK_MIN;
    private static int nextOrgan = -1;

    private Level228Ambience() {}

    /** The chamber and the exit hallway are kept free of the maze's sounds. */
    private static boolean quietZone(Level228Plan.Zone zone) {
        return zone == Level228Plan.Zone.CHAMBER || zone == Level228Plan.Zone.EXIT_HALL;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean in = mc.player != null && mc.level != null && Level228Atmosphere.inLevel228();
        Level228Footsteps.setClientSeed(Level228ClientState.seed(), in && Level228ClientState.hasSeed());
        if (!in) {
            nextOrgan = -1; // a fresh wait next time you arrive
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Level228Plan.Zone zone = Level228Atmosphere.zone();
        int z = zone.ordinal();
        tone = keep(mc, tone, ModSounds.LEVEL228_AMBIENCE.get(), TONE[z]);
        wind = keep(mc, wind, ModSounds.LEVEL228_EXIT_WIND.get(), WIND[z]);
        hush = keep(mc, hush, ModSounds.LEVEL228_CHAMBER_HUSH.get(), HUSH[z]);
        boolean quiet = quietZone(zone);
        if (organ != null && (quiet || !mc.getSoundManager().isActive(organ))) {
            if (quiet) {
                organ.fadeOut();
            }
            if (!mc.getSoundManager().isActive(organ)) {
                organ = null;
            }
        }
        if (nextOrgan < 0) {
            nextOrgan = ORGAN_FIRST_MIN + RANDOM.nextInt(ORGAN_FIRST_MAX - ORGAN_FIRST_MIN);
        }
        if (quiet) {
            return; // the chamber is silent; the exit hallway has only its wind
        }
        if (--nextTick <= 0) {
            nextTick = TICK_MIN + RANDOM.nextInt(TICK_MAX - TICK_MIN);
            glass(mc.level, mc.player, ModSounds.LEVEL228_GLASS_TICK.get(), 0.30f, 0.25f);
        }
        if (--nextGlassCreak <= 0) {
            nextGlassCreak = GLASS_CREAK_MIN + RANDOM.nextInt(GLASS_CREAK_MAX - GLASS_CREAK_MIN);
            glass(mc.level, mc.player, ModSounds.LEVEL228_GLASS_CREAK.get(), 0.40f, 0.2f);
        }
        if (--nextCreak <= 0) {
            nextCreak = CREAK_MIN + RANDOM.nextInt(CREAK_MAX - CREAK_MIN);
            far(mc, ModSounds.LEVEL228_CREAK.get(), CREAK_NEAR, CREAK_FAR, Level228Plan.CEILING_Y,
                    0.55f + 0.35f * RANDOM.nextFloat(), 0.85f + 0.25f * RANDOM.nextFloat());
        }
        if (--nextOrgan <= 0 && organ == null) {
            nextOrgan = ORGAN_MIN + RANDOM.nextInt(ORGAN_MAX - ORGAN_MIN);
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double d = ORGAN_NEAR + RANDOM.nextDouble() * (ORGAN_FAR - ORGAN_NEAR);
            organ = new OrganSound(mc.player.getX() + Math.cos(angle) * d, Level228Plan.FEET_Y + 2.0,
                    mc.player.getZ() + Math.sin(angle) * d);
            mc.getSoundManager().play(organ);
        }
    }

    /** Keeps a loop playing at {@code volume} (starting it if it isn't); 0 lets it fade out and stop. */
    @Nullable
    private static FadingLoopSound keep(Minecraft mc, @Nullable FadingLoopSound loop, SoundEvent sound, float volume) {
        boolean active = loop != null && mc.getSoundManager().isActive(loop);
        if (volume <= 0.0f) {
            return active ? loop : null; // unfed, it fades and stops itself
        }
        if (!active) {
            loop = new FadingLoopSound(sound, true, 0, 0, 0, FADE);
            mc.getSoundManager().play(loop);
        }
        loop.feed(volume);
        return loop;
    }

    /** A sound from somewhere in the building, {@code near}..{@code far} blocks off in a random direction. */
    private static void far(Minecraft mc, SoundEvent sound, double near, double far, double y, float volume, float pitch) {
        LocalPlayer player = mc.player;
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double d = near + RANDOM.nextDouble() * (far - near);
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, RANDOM,
                player.getX() + Math.cos(angle) * d, y, player.getZ() + Math.sin(angle) * d));
    }

    /** A tick or a creak from a real mirror near you (nothing when none is near). */
    private static void glass(ClientLevel level, LocalPlayer player, SoundEvent sound, float base, float spread) {
        if (!Level228ClientState.hasSeed()) {
            return;
        }
        int n = Level228Plan.mirrorsNear(Level228ClientState.seed(), Level228ClientState.shift(), player.getX(), player.getZ(),
                GLASS_REACH, MIRRORS);
        n = Math.min(n, MIRRORS.length);
        if (n == 0) {
            return;
        }
        // A few tries for a pane that isn't right beside you (a tick in your ear reads as a jump scare).
        for (int i = 0; i < 6; i++) {
            Level228Plan.Mirror m = MIRRORS[RANDOM.nextInt(n)];
            double along = m.min + RANDOM.nextDouble() * (m.max - m.min);
            double x = m.axis == 0 ? m.plane : along;
            double z = m.axis == 0 ? along : m.plane;
            double dx = x - player.getX();
            double dz = z - player.getZ();
            if (dx * dx + dz * dz < GLASS_NEAR * GLASS_NEAR) {
                continue;
            }
            double y = m.y0 + RANDOM.nextDouble() * (m.y1 - m.y0);
            level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, base + spread * RANDOM.nextFloat(),
                    0.9f + 0.2f * RANDOM.nextFloat(), false);
            return;
        }
    }

    // --- The far organ ------------------------------------------------------------------------

    /** One phrase of the far organ: plays once at a fixed spot and can fade out early. */
    private static final class OrganSound extends AbstractTickableSoundInstance {
        private boolean fading;

        OrganSound(double x, double y, double z) {
            super(ModSounds.LEVEL228_ORGAN.get(), SoundSource.AMBIENT, RandomSource.create(RANDOM.nextLong()));
            this.x = x;
            this.y = y;
            this.z = z;
            this.looping = false;
            this.delay = 0;
            this.volume = 1.0f;
            this.pitch = 0.96f + 0.06f * RANDOM.nextFloat();
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public void tick() {
            if (fading) {
                volume -= ORGAN_FADE;
                if (volume <= 0.0f) {
                    volume = 0.0f;
                    stop();
                }
            }
        }
    }

    // --- The footstep echo ------------------------------------------------------------------

    /** Our own echo of a step (so the echo isn't echoed again). */
    private static final class Echo extends SimpleSoundInstance {
        Echo(ResourceLocation sound, float volume, float pitch, double x, double y, double z) {
            super(sound, SoundSource.PLAYERS, volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof Echo || sound.getSource() != SoundSource.PLAYERS || sound.isRelative()
                || !sound.getLocation().getPath().contains("step") || !Level228Atmosphere.inLevel228()
                || !Level228ClientState.hasSeed() || quietZone(Level228Atmosphere.zone())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.position().distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) > OWN_STEP * OWN_STEP) {
            return; // someone else's step (or a reflection's): only yours come back
        }
        // The nearest pane beside you whose span you stand in front of.
        int n = Math.min(MIRRORS.length, Level228Plan.mirrorsNear(Level228ClientState.seed(), Level228ClientState.shift(),
                player.getX(), player.getZ(), ECHO_REACH, MIRRORS));
        Level228Plan.Mirror best = null;
        double bestDist = ECHO_REACH;
        for (int i = 0; i < n; i++) {
            Level228Plan.Mirror m = MIRRORS[i];
            if (m.kind == Level228Plan.K_DISTORT || m.surface == Level228Plan.S_SHATTERED) {
                continue; // a bent or broken pane gives no clean echo
            }
            double along = m.axis == 0 ? player.getZ() : player.getX();
            if (along < m.min || along > m.max) {
                continue;
            }
            double d = Math.abs((m.axis == 0 ? player.getX() : player.getZ()) - m.plane);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        if (best == null) {
            return;
        }
        // Reflected across the pane: where your reflection's foot comes down.
        double x = sound.getX();
        double z = sound.getZ();
        if (best.axis == 0) {
            x = 2.0 * best.plane - x;
        } else {
            z = 2.0 * best.plane - z;
        }
        int delay = Math.min(ECHO_DELAY_MAX, ECHO_DELAY + (int) bestDist);
        float pitch = ECHO_PITCH + 0.04f * RANDOM.nextFloat();
        mc.getSoundManager().playDelayed(new Echo(sound.getLocation(), ECHO_VOLUME, pitch, x, sound.getY(), z), delay);
    }
}
