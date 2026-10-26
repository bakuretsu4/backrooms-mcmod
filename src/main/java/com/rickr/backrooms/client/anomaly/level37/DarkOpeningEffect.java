package com.rickr.backrooms.client.anomaly.level37;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The cold of a dark opening ({@code level_37/dark_opening}, Fandom Level 37.2's "dark opening"
 * back, https://backrooms.fandom.com/wiki/Level_37.2): the mirror of the Poolrooms' sunlit
 * archway. Near it the warm picture goes cold and dim, wisps of cold air spill out of the arch
 * along the floor, and a low draught breathes out of the dark. Walk into the darkness and the
 * picture fades to black; the server sends you back to the Poolrooms
 * ({@code world/anomaly/level37/DarkOpening}).
 */
final class DarkOpeningEffect implements AnomalyClientEffect {
    /** The cold reaches this far from the arch (blocks). */
    private static final double COLD_RANGE = 12.0;
    /** A faint dark veil right at the arch, as if the dark leaned out toward you. */
    private static final float VEIL_AT_ARCH = 0.18f;
    private static final float DRAFT_MAX = 0.75f;
    /** Cold, dim and colourless: blue shadows, grey-blue air close in, little glow. */
    private static final AtmosphereGrade.Mood COLD = new AtmosphereGrade.Mood(0.8f, 1.06f, 0.55f,
            0.86f, 0.94f, 1.12f, 0.94f, 0.98f, 1.06f,
            0.006f, 0.008f, 0.014f, 0.10f, 0.12f, 0.16f, 3.0f, 0.05f, 0.5f, 0.42f, 0.1f, 0.012f);

    private final Map<AnomalySite, SiteLoopSound> drafts = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        double d = Level37AnomalyPlan.distanceToOpening(site, player.getX(), player.getZ());
        float near = (float) Mth.clamp((COLD_RANGE - d) / (COLD_RANGE - 0.5), 0.0, 1.0);
        near = near * near * (3.0f - 2.0f * near);
        if (near <= 0.0f) {
            return;
        }
        boolean inDark = inDarkness(mc, player);
        Level37AnomalyOverlay.requestBlack(inDark ? 1.0f : near * near * VEIL_AT_ARCH);
        AtmosphereGrade.setAnomalyMood(COLD, inDark ? 1.0f : near * 0.85f);

        SiteLoopSound draft = drafts.get(site);
        if (draft == null || draft.isStopped()) {
            draft = new SiteLoopSound(ModSounds.L37_DARK_DRAFT.get(), Level37AnomalyPlan.faceCentre(site));
            drafts.put(site, draft);
            mc.getSoundManager().play(draft);
        }
        draft.feed(DRAFT_MAX * near, 1.0f);
        drafts.values().removeIf(SiteLoopSound::isStopped);

        if (!mc.isPaused() && random.nextInt(3) == 0) {
            coldAir(mc, site);
        }
    }

    /** A wisp of cold air sliding out of the arch low along the floor, into the room. */
    private void coldAir(Minecraft mc, AnomalySite site) {
        Vec3 face = Level37AnomalyPlan.faceCentre(site);
        double spread = (random.nextDouble() - 0.5) * (Level37AnomalyPlan.DARK_WIDTH - 0.8);
        // Into the room is the opposite of the way the opening faces.
        double ox = site.facing() == 1 ? 1.0 : site.facing() == 3 ? -1.0 : 0.0;
        double oz = site.facing() == 2 ? 1.0 : site.facing() == 0 ? -1.0 : 0.0;
        double x = face.x + ox * 0.15 + oz * spread;
        double z = face.z + oz * 0.15 + ox * spread;
        double y = Level37AnomalyPlan.GAP_BOTTOM_Y - 0.9 + random.nextDouble() * 0.6;
        double speed = 0.015 + random.nextDouble() * 0.02;
        mc.level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, ox * speed, -0.002, oz * speed);
    }

    /** Feet or head in the darkness block (same test as the server's). */
    private static boolean inDarkness(Minecraft mc, LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        return mc.level.getBlockState(feet).is(ModBlocks.L94_DARK_VOID.get())
                || mc.level.getBlockState(feet.above()).is(ModBlocks.L94_DARK_VOID.get());
    }
}
