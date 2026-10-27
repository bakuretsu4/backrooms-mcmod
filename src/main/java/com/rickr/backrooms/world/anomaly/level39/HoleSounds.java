package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.entity.Hound;
import com.rickr.backrooms.entity.Level39Spawner;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level39Layout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Canon: "if sounds come from the holes, run". Every so often a hole near someone starts making
 * noises: claws scraping far down, then heavy breathing, then a growl, louder each time, with dust
 * puffing up out of it. It is never a false alarm: within about twenty seconds of the first sound a
 * Hound climbs out of that hole.
 *
 * <p>The sounds and the dust are for everyone near; the Hound hunts as usual
 * ({@code entity/Hound}). Test with {@code /backrooms hole stir} next to a hole.
 */
final class HoleSounds implements AnomalyBehavior {
    /** Players within this many blocks (sideways, in town) can set a hole off... */
    private static final double LISTEN_RANGE = 40.0;
    /** ...with this chance per second (about once in two minutes of standing near one)... */
    private static final int STIR_ONE_IN = 120;
    /** ...but each hole rests this long (seconds) after it has let something out. */
    private static final int REST_S = 150;
    /** The sequence (seconds after the first sound): breathing, growl, and the climb (random in a window). */
    private static final int BREATH_AT = 4;
    private static final int GROWL_AT = 8;
    private static final int CLIMB_MIN = 12;
    private static final int CLIMB_SPREAD = 7;
    /** Never more Hounds than this already around the hole. */
    private static final int MAX_HOUNDS_NEAR = 2;

    /** A hole that is stirring: seconds since it started, and when the Hound comes out. */
    private static final class Stir {
        int seconds;
        final int climbAt;

        Stir(int climbAt) {
            this.climbAt = climbAt;
        }
    }

    private final Map<Long, Stir> stirring = new HashMap<>();
    private final Map<Long, Long> restUntil = new HashMap<>();

    private static long key(AnomalySite site) {
        return BlockPos.asLong(site.anchorX(), 0, site.anchorZ());
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        long key = key(site);
        Stir stir = stirring.get(key);
        if (stir != null) {
            advance(level, site, key, stir);
            return;
        }
        long now = level.getGameTime();
        if (level.getDifficulty() == Difficulty.PEACEFUL || now < restUntil.getOrDefault(key, 0L)) {
            return;
        }
        if (listener(level, site) != null && level.getRandom().nextInt(STIR_ONE_IN) == 0) {
            start(level, site);
        }
    }

    /** Starts the warning sequence at this hole (also the test command). */
    void start(ServerLevel level, AnomalySite site) {
        long key = key(site);
        if (!stirring.containsKey(key)) {
            Stir stir = new Stir(CLIMB_MIN + level.getRandom().nextInt(CLIMB_SPREAD));
            stirring.put(key, stir);
            emit(level, site, Level39AnomalyRegistry.HOLE_SCRAPE.get(), 1.2f, 0.4f);
        }
    }

    private void advance(ServerLevel level, AnomalySite site, long key, Stir stir) {
        stir.seconds++;
        if (stir.seconds == BREATH_AT) {
            emit(level, site, Level39AnomalyRegistry.HOLE_BREATH.get(), 1.5f, 0.6f);
        } else if (stir.seconds == GROWL_AT) {
            emit(level, site, Level39AnomalyRegistry.HOLE_GROWL.get(), 2.0f, 0.9f);
        } else if (stir.seconds > GROWL_AT && stir.seconds < stir.climbAt && stir.seconds % 3 == 0) {
            emit(level, site, Level39AnomalyRegistry.HOLE_SCRAPE.get(), 1.6f, 0.7f);
        }
        if (stir.seconds >= stir.climbAt) {
            stirring.remove(key);
            restUntil.put(key, level.getGameTime() + REST_S * 20L);
            climbOut(level, site);
        }
    }

    /** A sound from down the hole, and dust puffing up out of it. */
    private static void emit(ServerLevel level, AnomalySite site, SoundEvent sound, float volume, float dust) {
        double x = site.anchorX() + 0.5;
        double z = site.anchorZ() + 0.5;
        level.playSound(null, x, Level39Layout.GROUND_Y - 4, z, sound, SoundSource.HOSTILE, volume,
                0.92f + level.getRandom().nextFloat() * 0.12f);
        double r = Level39Sites.holeRadius(site);
        BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
        level.sendParticles(soil, x, Level39Layout.GROUND_Y + 0.5, z, (int) (12 * dust), r * 0.6, 0.3, r * 0.6, 0.02);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, Level39Layout.GROUND_Y + 1.0, z, (int) (30 * dust), r * 0.7, 1.2, r * 0.7, 0.01);
    }

    /** The Hound comes up over the rim, on the side facing whoever is nearest. */
    private static void climbOut(ServerLevel level, AnomalySite site) {
        double x = site.anchorX() + 0.5;
        double z = site.anchorZ() + 0.5;
        level.playSound(null, x, Level39Layout.GROUND_Y, z, Level39AnomalyRegistry.HOLE_CLIMB.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        emit(level, site, Level39Mobs.HOUND_GROWL.get(), 2.0f, 1.4f);
        if (level.getEntitiesOfClass(Hound.class, new AABB(x - 32, 20, z - 32, x + 32, 110, z + 32)).size() >= MAX_HOUNDS_NEAR) {
            return;
        }
        ServerPlayer target = listener(level, site);
        double angle = target == null ? level.getRandom().nextDouble() * Math.PI * 2.0
                : Math.atan2(target.getZ() - z, target.getX() - x);
        double reach = Level39Sites.holeRadius(site) + 2.0;
        for (int i = 0; i < 8; i++) {
            double a = angle + (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.6;
            int sx = (int) Math.floor(x + Math.cos(a) * reach);
            int sz = (int) Math.floor(z + Math.sin(a) * reach);
            BlockPos spot = Level39Spawner.standableNear(level, sx, Level39Layout.GROUND_Y + 1, sz, 3);
            if (spot != null) {
                Hound hound = Level39Mobs.HOUND.get().spawn(level, spot, MobSpawnType.EVENT);
                if (hound != null && target != null) {
                    hound.setTarget(target);
                }
                BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
                level.sendParticles(soil, spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, 30, 0.5, 0.4, 0.5, 0.1);
                return;
            }
        }
    }

    /** The nearest survival/adventure player in town within listening range, or null. */
    @Nullable
    private static ServerPlayer listener(ServerLevel level, AnomalySite site) {
        ServerPlayer best = null;
        double bestD = LISTEN_RANGE * LISTEN_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || p.getY() < Level39Layout.GROUND_Y - 12) {
                continue;
            }
            double dx = p.getX() - site.anchorX() - 0.5;
            double dz = p.getZ() - site.anchorZ() - 0.5;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }
}
