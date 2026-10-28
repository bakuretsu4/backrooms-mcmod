package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level227Atmos;
import com.rickr.backrooms.world.layout.Level227Tree;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The deadly sunbeams of Level 227 (server side: the rule; the shafts, glare and hum are drawn by
 * {@code client/Level227Sunbeams}). Canon: direct exposure to the Sun over the Void's light kills in
 * seconds, by disintegration.
 *
 * <p><b>The rule.</b> You are "in the light" when your eyes are inside a planned sunbeam
 * ({@link Level227Tree#beamAt}) AND nothing is between you and the sky: the sky light at your
 * eyes is the full 15, which in Minecraft only happens with an open column of air above you
 * (leaves, wood or a block you hold over your head all take it below 15). Then:
 * <ol>
 *   <li><b>{@value #WARN_TICKS} ticks (1 s) of warning:</b> the sun's hum climbs into a choir and the
 *       picture goes white (client). Step out now and nothing happens.</li>
 *   <li><b>Disintegration</b> over the next {@value #BURN_TICKS} ticks (2 s): white ash streams off
 *       you, {@value #BURN_DAMAGE} damage every {@value #BURN_EVERY} ticks (armour, shields and
 *       potions don't help), and at the end you are gone. Step out in time and you live, badly
 *       hurt.</li>
 * </ol>
 * Leaving the light lets the exposure fall back three times as fast as it built up. Creative and
 * spectator players see the light but are not hurt by it.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Sun {
    /** How long the light only warns you before it starts taking you apart (1 s). */
    public static final int WARN_TICKS = 20;
    /** How long the disintegration takes once it starts (2 s). */
    public static final int BURN_TICKS = 40;
    private static final int BURN_EVERY = 10;
    private static final float BURN_DAMAGE = 4.0f;
    /** Outside the light the exposure drains this many ticks per tick. */
    private static final int RECOVER_PER_TICK = 3;

    /** Ticks of exposure per player (only players who have some). */
    private static final Map<UUID, Integer> EXPOSURE = new HashMap<>();

    /**
     * Is this spot lit by a sunbeam with open sky above? Shared by the server rule and the client
     * visuals (the plan is the same on both sides; the sky light is the client's copy).
     */
    public static boolean inSunlight(Level level, double x, double eyeY, double z) {
        Level227Tree.Beam beam = Level227Tree.beamAt(x, z);
        if (beam == null || eyeY >= beam.topY()) {
            return false;
        }
        BlockPos eye = BlockPos.containing(x, eyeY, z);
        return level.getBrightness(LightLayer.SKY, eye) >= 15;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer had = EXPOSURE.get(player.getUUID());
        boolean here = BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_227;
        boolean lit = here && player.isAlive() && !player.isSpectator() && !player.isCreative()
                && inSunlight(player.level(), player.getX(), player.getEyeY(), player.getZ());
        if (!lit) {
            if (had != null) {
                int left = had - RECOVER_PER_TICK;
                if (left <= 0 || !here) {
                    EXPOSURE.remove(player.getUUID());
                } else {
                    EXPOSURE.put(player.getUUID(), left);
                }
            }
            return;
        }
        int exposure = (had == null ? 0 : had) + 1;
        EXPOSURE.put(player.getUUID(), exposure);
        ServerLevel level = player.serverLevel();
        if (exposure == 1) {
            // The light has found you: everyone nearby hears it climb.
            level.playSound(null, player.getX(), player.getY(), player.getZ(), Level227Atmos.SUN_BURN.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        if (exposure <= WARN_TICKS) {
            return;
        }
        int burning = exposure - WARN_TICKS;
        // White ash streaming off you, thicker as it goes (seen by everyone).
        int count = 2 + burning / 5;
        level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(),
                count * 3, 0.35, 0.6, 0.35, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                Math.max(1, count / 3), 0.3, 0.6, 0.3, 0.03);
        if (burning >= BURN_TICKS) {
            disintegrate(level, player);
            return;
        }
        if (burning % BURN_EVERY == 0) {
            player.hurt(damage(level), BURN_DAMAGE);
        }
    }

    /** The end: a burst of ash where you stood, and death that nothing can stop. */
    private static void disintegrate(ServerLevel level, Player player) {
        EXPOSURE.remove(player.getUUID());
        level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 0.9, player.getZ(), 160, 0.4, 0.8, 0.4, 0.08);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.9, player.getZ(), 24, 0.3, 0.7, 0.3, 0.12);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Level227Atmos.DISINTEGRATE.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        player.invulnerableTime = 0;
        player.hurt(damage(level), Float.MAX_VALUE);
    }

    private static DamageSource damage(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Level227Atmos.SUNLIGHT));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EXPOSURE.remove(event.getEntity().getUUID());
    }

    private Level227Sun() {}
}
