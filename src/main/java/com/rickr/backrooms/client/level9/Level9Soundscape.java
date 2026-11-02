package com.rickr.backrooms.client.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level9Atmosphere;
import com.rickr.backrooms.client.audio.DuckedLoopSound;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Level 9's night bed ({@code ambient.level9_ambience}: crickets, far traffic that never comes
 * closer, faint wind chimes; made by tools/level9_assets/street.py) as an outdoor loop.
 *
 * <p>Minecraft plays it from the biome ("ambient_sound" in worldgen/biome/level_9.json) and offers
 * no volume knob, so, as Level 0 and Level 2 do, it is wrapped in a {@link DuckedLoopSound} when
 * it starts and its volume follows {@link #gain}:
 * <ul>
 *   <li>indoors (a solid block within {@link #ROOF_SEARCH} blocks over your head: a house's
 *       ceiling, the underpass roof) it fades to {@link #INDOOR_LEVEL} over about two seconds;</li>
 *   <li>when the dogs go quiet ({@link DogsGoQuiet}) the crickets stop at once, then come back
 *       over a few seconds;</li>
 *   <li>while the Mangled's fog is thick it goes with the lamps' hum
 *       ({@link Level9Atmosphere#lampHumScale}), and out in the Crimson Field it is silent.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level9Soundscape {
    private static final int ROOF_SEARCH = 14;
    private static final float INDOOR_LEVEL = 0.18f;
    /** Easing per tick: indoors/outdoors (~2 s), and the night coming back after the dogs (~3 s). */
    private static final float ROOF_EASE = 0.05f;
    private static final float HUSH_RECOVER_EASE = 0.025f;

    private static boolean roofOverhead;
    private static float roofGain = 1.0f;
    private static float hushGain = 1.0f;
    private static float gain = 1.0f;

    private Level9Soundscape() {}

    /** True when nothing solid is over your head (last check, every 5 ticks). */
    public static boolean outdoors() {
        return !roofOverhead;
    }

    private static double gain() {
        return gain;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_9
                || mc.isPaused()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (mc.level.getGameTime() % 5 == 0) {
            roofOverhead = hasRoof(mc, eye);
        }
        roofGain += ((roofOverhead ? INDOOR_LEVEL : 1.0f) - roofGain) * ROOF_EASE;
        float hushTarget = DogsGoQuiet.ambienceLevel();
        hushGain = hushTarget < hushGain ? hushTarget : hushGain + (hushTarget - hushGain) * HUSH_RECOVER_EASE;
        float field = Level9Atmosphere.inField() ? 0.0f : 1.0f;
        gain = Mth.clamp(roofGain * hushGain * field * Level9Atmosphere.lampHumScale(System.currentTimeMillis()), 0.0f, 1.0f);
    }

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    /** A solid block over the camera within {@link #ROOF_SEARCH} blocks (leaves and lamp heads don't count). */
    private static boolean hasRoof(Minecraft mc, Vec3 eye) {
        BlockPos.MutableBlockPos cursor = CURSOR;
        int x = Mth.floor(eye.x);
        int z = Mth.floor(eye.z);
        int y0 = Mth.floor(eye.y) + 1;
        for (int y = y0; y < y0 + ROOF_SEARCH; y++) {
            cursor.set(x, y, z);
            BlockState state = mc.level.getBlockState(cursor);
            if (!state.isAir() && state.isSolidRender(mc.level, cursor)) {
                return true;
            }
        }
        return false;
    }

    /** Wraps the biome's loop when it starts (checked on the dimension: the loop starts on the first tick). */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (sound instanceof BiomeAmbientSoundsHandler.LoopSoundInstance loop && mc.level != null
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9
                && sound.getLocation().equals(ModSounds.LEVEL9_AMBIENCE.getId())) {
            // Follow gain() exactly (ease 1): the easing is done above, with different speeds.
            event.setSound(new DuckedLoopSound(loop, Level9Soundscape::gain, 1.0f, gain));
        }
    }
}
