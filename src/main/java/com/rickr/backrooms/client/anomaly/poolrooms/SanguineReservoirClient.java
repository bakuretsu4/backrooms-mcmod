package com.rickr.backrooms.client.anomaly.poolrooms;

import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientState;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The Sanguine Reservoir on the client: the red copy of the Poolrooms you drift into from a dark
 * corner (PoolroomsLayout builds it, DarkCornerBehavior moves you there).
 *
 * <p>Canon: Wikidot Level 37.1, "The Sanguine Reservoir"
 * (https://backrooms-wiki.wikidot.com/level-37-1): an exact copy of Level 37 where the light is
 * gone but for "bright red sprouts" and "a red tint in the water"; the water turns viscous, "as if
 * not letting go"; "the faintest chorus of chants" hangs in the distance, with slithering sounds
 * and whimpers coming from nowhere.
 * <ul>
 *   <li>The picture is dim and red, darker still down in a stair pit.</li>
 *   <li>Water holds on to you: in it you move at about two thirds of your usual speed (the drain
 *       still works; that is the way out, down to Level 37).</li>
 *   <li>The chants never stop, faint, in your head; now and then something slithers or whimpers a
 *       few blocks away, out of sight.</li>
 * </ul>
 * The red water itself is {@code WaterStyle.SANGUINE}.
 *
 * <p>Runs after the anomaly effects (low priority) because it overrides their picture mood: the
 * Reservoir's own look wins there.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class SanguineReservoirClient {
    /** Dim, red and close: deep red shadows, the colour of the sprouts in the highlights, red haze. */
    private static final AtmosphereGrade.Mood RESERVOIR = new AtmosphereGrade.Mood(0.55f, 1.12f, 0.75f,
            1.15f, 0.80f, 0.80f, 1.08f, 0.90f, 0.88f,
            0.006f, 0.0f, 0.0f, 0.10f, 0.01f, 0.01f, 3.0f, 0.07f, 0.70f, 0.50f, 0.25f, 0.014f);
    /** Speed kept per tick while in the water (on top of the water's own drag): "not letting go". */
    private static final double VISCOSITY = 0.86;
    /** Rising is held back a little less, so you can always get your head back above water. */
    private static final double VISCOSITY_UP = 0.94;
    /** Volume of the chants hanging over everything. */
    private static final float CHANT_VOLUME = 0.2f;
    /** On average one slither every 25 s and one whimper every 40 s. */
    private static final int SLITHER_ODDS = 25 * 20;
    private static final int WHIMPER_ODDS = 40 * 20;

    private static final RandomSource RANDOM = RandomSource.create();
    private static FadingLoopSound chants;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.isPaused()
                || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.POOLROOMS
                || !PoolroomsLayout.inReservoir(player.getX())) {
            return; // the chants fade out by themselves
        }
        float depth = AnomalyClientState.in(PoolroomsAnomalyPlan.STAIR_PIT) ? StairPitEffect.depth(mc) : 0.0f;
        AtmosphereGrade.setAnomalyMood(RESERVOIR.lerp(DarkCornerEffect.FADED, depth * 0.8f), 1.0f);

        if (player.isInWater() && !player.isSpectator() && !player.getAbilities().flying) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x * VISCOSITY, v.y > 0.0 ? v.y * VISCOSITY_UP : v.y, v.z * VISCOSITY);
        }

        if (chants == null || !mc.getSoundManager().isActive(chants)) {
            chants = new FadingLoopSound(ModSounds.POOL_CHANT.get(), true, 0.0, 0.0, 0.0, 0.01f);
            mc.getSoundManager().play(chants);
        }
        // A slow swell, as if the chorus breathes.
        float swell = 0.75f + 0.25f * Mth.sin(player.tickCount * 0.02f);
        chants.feed(CHANT_VOLUME * swell);

        if (RANDOM.nextInt(SLITHER_ODDS) == 0) {
            somewhereNear(mc, player, ModSounds.POOL_SLITHER.get(), 0.6f);
        }
        if (RANDOM.nextInt(WHIMPER_ODDS) == 0) {
            somewhereNear(mc, player, ModSounds.POOL_WHIMPER.get(), 0.45f);
        }
    }

    /** Plays a sound 6-14 blocks away in a random direction: close, but never where you look first. */
    private static void somewhereNear(Minecraft mc, LocalPlayer player, SoundEvent sound, float volume) {
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double distance = 6.0 + RANDOM.nextDouble() * 8.0;
        double x = player.getX() + Mth.cos(angle) * distance;
        double z = player.getZ() + Mth.sin(angle) * distance;
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume,
                0.85f + RANDOM.nextFloat() * 0.3f, RANDOM, x, player.getY(), z));
    }

    private SanguineReservoirClient() {}
}
