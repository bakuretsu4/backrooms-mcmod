package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What Level 228 sounds like (client side). It is a quiet place, and nothing here has a voice:
 * <ul>
 *   <li><b>The room tone</b> ({@code level228.ambience}, "in your head"): still air in narrow glass
 *       corridors and, very faint, the glass singing.</li>
 *   <li><b>The panels' hum</b> is the fixtures' own ({@code level228.light_hum}, see client/light:
 *       the nearest few lit panels, each heard only close under it), so the lit stretches hum and
 *       the dark between them is silent.</li>
 *   <li><b>The glass</b>: now and then a pane near you ticks as it settles, more rarely one creaks
 *       in its frame, each from a real pane within a dozen blocks (so you can tell where).</li>
 *   <li><b>Your footsteps answered</b>: when there is a mirror beside you, a faint copy of each
 *       of your steps comes back a moment later from behind the glass, where your reflection
 *       walks (the step reflected across the nearest pane), a little lower and duller. Subtle:
 *       you notice it only once you listen for it.</li>
 *   <li>The exit's cold draught is its exit-hint sound ({@code exit_hint.level_228}, client/hint).</li>
 * </ul>
 * Everything here goes quiet together when the thing in the mirrors holds its breath before a
 * catch ({@link #quiet}, from {@link Level228Other#hush}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Ambience {
    private static final float AMBIENCE_VOLUME = 0.45f;
    private static final float FADE = 0.02f;

    /** A pane ticks every this many ticks or so (12-35 s), creaks more rarely (30-80 s). */
    private static final int TICK_MIN = 240;
    private static final int TICK_MAX = 700;
    private static final int CREAK_MIN = 600;
    private static final int CREAK_MAX = 1600;
    /** The pane that sounds is looked for this far around you (blocks), at least NEAR away. */
    private static final int GLASS_REACH = 12;
    private static final double GLASS_NEAR = 3.0;
    private static final int GLASS_TRIES = 12;

    /** Your step comes back off a pane at most this far to the side of you (blocks) ... */
    private static final int ECHO_REACH = 6;
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
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    @Nullable
    private static FadingLoopSound ambience;
    private static int nextTick = TICK_MIN;
    private static int nextCreak = CREAK_MIN;
    /** The mirror pane block, looked up by id once (the level's blocks are registered elsewhere). */
    @Nullable
    private static Block mirror;

    private Level228Ambience() {}

    /** 0..1: how loud Level 228's own sounds may be (they hold their breath before a catch). */
    public static float quiet() {
        return 1.0f - 0.95f * Level228Other.hush();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || !Level228Atmosphere.inLevel228()) {
            return;
        }
        float quiet = quiet();
        ambience = keep(mc, ambience, AMBIENCE_VOLUME * quiet);
        if (Level228Other.hush() > 0.0f) {
            return; // the held breath: no glass either
        }
        if (--nextTick <= 0) {
            nextTick = TICK_MIN + RANDOM.nextInt(TICK_MAX - TICK_MIN);
            glass(mc.level, mc.player, true);
        }
        if (--nextCreak <= 0) {
            nextCreak = CREAK_MIN + RANDOM.nextInt(CREAK_MAX - CREAK_MIN);
            glass(mc.level, mc.player, false);
        }
    }

    /** Keeps the room tone playing at {@code volume} (starting it if it isn't). */
    private static FadingLoopSound keep(Minecraft mc, @Nullable FadingLoopSound loop, float volume) {
        if (loop == null || !mc.getSoundManager().isActive(loop)) {
            loop = new FadingLoopSound(ModSounds.LEVEL228_AMBIENCE.get(), true, 0, 0, 0, FADE);
            mc.getSoundManager().play(loop);
        }
        loop.feed(volume);
        return loop;
    }

    private static boolean isMirror(BlockState state) {
        if (mirror == null) {
            mirror = BuiltInRegistries.BLOCK.getOptional(Backrooms.id("l228_mirror")).orElse(Blocks.AIR);
        }
        return mirror != Blocks.AIR && state.is(mirror);
    }

    /** A tick or a creak from a real pane near you (nothing when none is found in a few tries). */
    private static void glass(ClientLevel level, LocalPlayer player, boolean tick) {
        int px = player.getBlockX();
        int py = player.getBlockY();
        int pz = player.getBlockZ();
        for (int i = 0; i < GLASS_TRIES; i++) {
            int dx = RANDOM.nextInt(2 * GLASS_REACH + 1) - GLASS_REACH;
            int dz = RANDOM.nextInt(2 * GLASS_REACH + 1) - GLASS_REACH;
            if (dx * dx + dz * dz < GLASS_NEAR * GLASS_NEAR) {
                continue;
            }
            CURSOR.set(px + dx, py + RANDOM.nextInt(2), pz + dz);
            if (!isMirror(level.getBlockState(CURSOR))) {
                continue;
            }
            float volume = (tick ? 0.35f + 0.25f * RANDOM.nextFloat() : 0.45f + 0.2f * RANDOM.nextFloat()) * quiet();
            float pitch = 0.9f + 0.2f * RANDOM.nextFloat();
            level.playLocalSound(CURSOR.getX() + 0.5, CURSOR.getY() + 0.5, CURSOR.getZ() + 0.5,
                    tick ? ModSounds.LEVEL228_GLASS_TICK.get() : ModSounds.LEVEL228_GLASS_CREAK.get(),
                    SoundSource.AMBIENT, volume, pitch, false);
            return;
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
                || !sound.getLocation().getPath().contains("step") || !Level228Atmosphere.inLevel228()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
                || player.position().distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) > OWN_STEP * OWN_STEP) {
            return; // someone else's step (or a reflection's): only yours come back
        }
        float quiet = quiet();
        if (quiet < 0.1f) {
            return;
        }
        // The nearest pane beside you, straight out to one of the four sides, at foot or head height.
        int px = player.getBlockX();
        int py = player.getBlockY();
        int pz = player.getBlockZ();
        Direction best = null;
        int bestDist = Integer.MAX_VALUE;
        double plane = 0.0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int d = 0; d <= ECHO_REACH && d < bestDist; d++) {
                CURSOR.set(px + side.getStepX() * d, py, pz + side.getStepZ() * d);
                BlockState state = mc.level.getBlockState(CURSOR);
                if (!isMirror(state)) {
                    CURSOR.move(Direction.UP);
                    state = mc.level.getBlockState(CURSOR);
                }
                if (isMirror(state) && facesAlong(state, side.getAxis())) {
                    best = side;
                    bestDist = d;
                    plane = (side.getAxis() == Direction.Axis.X ? CURSOR.getX() : CURSOR.getZ()) + 0.5;
                    break;
                }
                if (d > 0 && !state.isAir() && !state.getCollisionShape(mc.level, CURSOR).isEmpty()) {
                    break; // a wall or a panel: no mirror further this way
                }
            }
        }
        if (best == null) {
            return;
        }
        // Reflected across the pane: where your reflection's foot comes down.
        double x = sound.getX();
        double z = sound.getZ();
        if (best.getAxis() == Direction.Axis.X) {
            x = 2.0 * plane - x;
        } else {
            z = 2.0 * plane - z;
        }
        int delay = Math.min(ECHO_DELAY_MAX, ECHO_DELAY + bestDist);
        float pitch = ECHO_PITCH + 0.04f * RANDOM.nextFloat();
        mc.getSoundManager().playDelayed(new Echo(sound.getLocation(), ECHO_VOLUME * quiet, pitch, x, sound.getY(), z), delay);
    }

    /**
     * True when a pane reflects along {@code axis}: its {@code axis} property (read by name: the pane
     * lies in the plane axis = const) is that axis. A pane without one counts as facing every way.
     */
    private static boolean facesAlong(BlockState state, Direction.Axis axis) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("axis");
        if (property == null) {
            return true;
        }
        Object value = state.getValue(property);
        String name = value instanceof StringRepresentable named ? named.getSerializedName() : value.toString();
        return name.equalsIgnoreCase(axis.getName());
    }
}
