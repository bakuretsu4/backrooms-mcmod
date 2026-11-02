package com.rickr.backrooms.client.water;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.audio.Acoustics;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What you hear with your head under Level 227's pond water.
 *
 * <p>Before this, vanilla's ocean ambience played (its "enter water" rush, the underwater loop
 * with its random clicks and rumbles, the "exit water" sound) and everything else stayed exactly
 * as loud and clear as on land. Now, in Level 227 only:
 * <ul>
 *   <li>vanilla's underwater sounds ({@code ambient.underwater.*}) are dropped;</li>
 *   <li>a quiet muffled bed plays instead ({@code level227.water.under_bed}: a low hush, the
 *       forest above reduced to a dull swell, a slow deep bubble now and then), fading in and out
 *       over about a second;</li>
 *   <li>every other sound is muffled by the same low-pass filter the walls use
 *       ({@code client/audio/Acoustics#setSubmerged}): highs mostly gone, half as loud, eased in
 *       as your head goes under and out as it comes up;</li>
 *   <li>a soft rush as your head goes under and water running off as it comes up. Bobbing at the
 *       surface flips between the two quickly, so a transition sound plays at most once every
 *       {@value #MIN_GAP_TICKS} ticks.</li>
 * </ul>
 * Nothing loud, nothing sudden. Works the same with or without a shader pack (it only listens to
 * where the camera is).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class UnderwaterAudio {
    /** The bed's volume (the file is made at -33 LUFS) and how fast it fades (volume per tick). */
    private static final float BED_VOLUME = 0.8f;
    private static final float BED_FADE = 0.06f;
    private static final float ENTER_VOLUME = 0.6f;
    private static final float EXIT_VOLUME = 0.5f;
    /** Fewest ticks between two transition sounds. */
    private static final int MIN_GAP_TICKS = 16;
    /** How fast the muffle closes in (share per tick) and lifts again. */
    private static final float MUFFLE_IN = 0.35f;
    private static final float MUFFLE_OUT = 0.2f;
    /** Vanilla's underwater sounds all live under this path. */
    private static final String VANILLA_UNDERWATER = "ambient.underwater.";

    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private static FadingLoopSound bed;
    private static boolean wasUnder;
    private static int sinceTransition = MIN_GAP_TICKS;
    private static float submerged;

    private UnderwaterAudio() {}

    private static boolean inLevel227() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_227;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        boolean active = mc.player != null && inLevel227();
        boolean under = active && mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
        if (active && under != wasUnder) {
            if (sinceTransition >= MIN_GAP_TICKS) {
                play(under ? ModSounds.L227_UNDER_ENTER.get() : ModSounds.L227_UNDER_EXIT.get(), under ? ENTER_VOLUME : EXIT_VOLUME);
            }
            sinceTransition = 0;
        }
        wasUnder = under;
        if (sinceTransition < MIN_GAP_TICKS) {
            sinceTransition++;
        }
        float target = under ? 1.0f : 0.0f;
        submerged += (target - submerged) * (under ? MUFFLE_IN : MUFFLE_OUT);
        if (submerged < 0.005f) {
            submerged = 0.0f;
        } else if (submerged > 0.995f) {
            submerged = 1.0f;
        }
        Acoustics.setSubmerged(submerged);
        if (under) {
            if (bed == null || bed.isStopped() || !mc.getSoundManager().isActive(bed)) {
                bed = new FadingLoopSound(ModSounds.L227_UNDER_BED.get(), true, 0.0, 0.0, 0.0, BED_FADE);
                mc.getSoundManager().play(bed);
            }
            bed.feed(BED_VOLUME);
        } // not fed: it fades out and stops by itself
    }

    /** A sound "in your head" (the same in both ears): it is your own head going in or out. */
    private static void play(SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT,
                volume, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, false, 0, SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true));
    }

    /** In Level 227 vanilla's ocean ambience never starts: our own bed and transitions replace it. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !inLevel227()) {
            return;
        }
        ResourceLocation id = sound.getLocation();
        if (id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) && id.getPath().startsWith(VANILLA_UNDERWATER)) {
            event.setSound(null);
        }
    }
}
