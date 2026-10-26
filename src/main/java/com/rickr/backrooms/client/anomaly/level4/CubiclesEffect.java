package com.rickr.backrooms.client.anomaly.level4;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.CubicleFrame;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 4.3, "The Cubicles" (https://backrooms.fandom.com/wiki/Level_4.3), on the client: the
 * constant white fog, and the sounds of people working that you never see (shuffling paper,
 * pencils writing, unclear voices, Level 4's rain and thunder muffled far away).
 *
 * <p>It creeps in along the dim white hallways: the deeper you walk, the whiter and closer the fog
 * and the louder the work, until in the field you can see about ten blocks. The fog overrides Level
 * 4's own ({@code client/Level4Atmosphere}): our handlers run last.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class CubiclesEffect implements AnomalyClientEffect {
    /** Fog in the field: white from right in front of you, solid by this distance. */
    private static final float FIELD_FOG_END = 10.0f;
    private static final float FOG_R = 0.84f;
    private static final float FOG_G = 0.85f;
    private static final float FOG_B = 0.86f;
    /** Milky, washed out, low contrast: a bright white haze that swallows the far cubicles. */
    private static final AtmosphereGrade.Mood WHITE = new AtmosphereGrade.Mood(1.06f, 0.86f, 0.66f,
            1.0f, 1.0f, 1.01f, 1.0f, 1.0f, 1.0f,
            0.060f, 0.060f, 0.062f, 0.86f, 0.87f, 0.88f, 0.5f, 0.16f, 0.85f, 0.24f, 0.26f, 0.010f);
    private static final long WORK_MIN_GAP_MS = 1_800L;
    private static final long WORK_MAX_GAP_MS = 5_000L;

    /** 0..1 how deep into the fog the local player is, and when that was last set. */
    private static float fog;
    private static long fogSetMs;

    private final RandomSource random = RandomSource.create();
    @Nullable
    private Level4LoopSound ambience;
    private long nextWorkMs;

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (!inside) {
            return;
        }
        LocalPlayer player = mc.player;
        CubicleFrame f = Level4AnomalyPlan.cubicleFrame(site);
        int v = f.v(player.getBlockX(), player.getBlockZ());
        // 0 at the door from the office, 1 from the first row of the field on.
        float depth = Mth.clamp((v - 5) / (float) (Level4AnomalyPlan.FIELD_V0 - 5), 0.0f, 1.0f);
        fog = depth;
        fogSetMs = System.currentTimeMillis();
        AtmosphereGrade.setAnomalyMood(WHITE, 0.85f * depth);

        if (ambience == null || ambience.isStopped()) {
            ambience = new Level4LoopSound(ModSounds.L4_CUBICLES_AMBIENCE.get());
            mc.getSoundManager().play(ambience);
        }
        ambience.feed(0.25f + 0.55f * depth);

        long now = System.currentTimeMillis();
        if (depth >= 0.9f && now >= nextWorkMs && !mc.isPaused()) {
            nextWorkMs = now + WORK_MIN_GAP_MS + (long) (random.nextFloat() * (WORK_MAX_GAP_MS - WORK_MIN_GAP_MS));
            // Someone working a few cubicles away, always just out of sight in the fog.
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 5.0 + random.nextDouble() * 6.0;
            mc.level.playLocalSound(player.getX() + Math.cos(angle) * radius, LevelLayout.FLOOR_Y + 1.6,
                    player.getZ() + Math.sin(angle) * radius, ModSounds.L4_CUBICLES_WORK.get(), SoundSource.AMBIENT,
                    0.35f + random.nextFloat() * 0.35f, 0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    /** How much of the Cubicles' fog applies right now (0 = none). */
    private static float fogAmount() {
        return System.currentTimeMillis() - fogSetMs > 250L ? 0.0f : fog;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float amount = fogAmount();
        if (amount <= 0.0f || event.getType() != FogType.NONE) {
            return;
        }
        float far = Mth.lerp(amount, Math.min(48.0f, event.getFarPlaneDistance()), FIELD_FOG_END);
        event.setNearPlaneDistance(Mth.lerp(amount, Math.min(event.getNearPlaneDistance(), far * 0.5f), 0.0f));
        event.setFarPlaneDistance(far);
        event.setCanceled(true); // NeoForge only uses our distances when the event is cancelled
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float amount = fogAmount();
        if (amount <= 0.0f || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        event.setRed(Mth.lerp(amount, event.getRed(), FOG_R));
        event.setGreen(Mth.lerp(amount, event.getGreen(), FOG_G));
        event.setBlue(Mth.lerp(amount, event.getBlue(), FOG_B));
    }
}
