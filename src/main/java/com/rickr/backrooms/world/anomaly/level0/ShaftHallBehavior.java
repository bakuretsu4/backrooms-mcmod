package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.registry.Level0SetPieceBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0SetPieces;
import com.rickr.backrooms.world.layout.Level0ShaftHall;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Shaft Hall on the server (floor plan: {@link Level0ShaftHall}). Every 2-5 minutes while
 * someone is in the hall, a ceiling tile works loose high up in one of the shafts in front of
 * them (5-16 blocks away): a dry crack from far above, a puff of grit, and the tile tumbles all
 * 18 blocks down and breaks on the carpet (block/FallenTileBlock plays the clatter when it lands
 * and stays there as a broken tile). In between, now and then, a thin trickle of dust sifts down
 * another shaft. Nothing ever falls on anyone: the shaft is chosen away from where people stand.
 */
public final class ShaftHallBehavior implements AnomalyBehavior {
    private static final int MIN_FALL_SECONDS = 120;
    private static final int MORE_FALL_SECONDS = 180;
    private static final int MIN_DUST_SECONDS = 25;
    private static final int MORE_DUST_SECONDS = 35;
    private static final double NEAR = 5.0;
    private static final double FAR = 16.0;

    private static final class Hall {
        final List<Level0ShaftHall.Shaft> shafts;
        long nextFall;
        long nextDust;

        Hall(List<Level0ShaftHall.Shaft> shafts, long now) {
            this.shafts = shafts;
            // The first fall never comes the moment you walk in.
            this.nextFall = now + 20L * 60;
            this.nextDust = now + 20L * 15;
        }
    }

    private final Map<AnomalySite, Hall> halls = new HashMap<>();

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        long now = level.getGameTime();
        if (halls.size() > 64 && !halls.containsKey(site)) {
            halls.clear();
        }
        Hall hall = halls.computeIfAbsent(site, s -> new Hall(Level0ShaftHall.shafts(s), now));
        boolean fall = now >= hall.nextFall;
        boolean dust = now >= hall.nextDust;
        if (!fall && !dust) {
            return;
        }
        ServerPlayer witness = null;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && Level0ShaftHall.inside(site, player.getBlockX(), player.getBlockZ())) {
                witness = player;
                break;
            }
        }
        if (witness == null) {
            return; // only when someone is there to notice
        }
        Level0ShaftHall.Shaft shaft = pickShaft(level, hall, witness);
        if (shaft == null) {
            return;
        }
        if (fall) {
            hall.nextFall = now + 20L * (MIN_FALL_SECONDS + level.random.nextInt(MORE_FALL_SECONDS + 1));
            hall.nextDust = Math.max(hall.nextDust, now + 20L * 10);
            dropTile(level, shaft);
        } else {
            hall.nextDust = now + 20L * (MIN_DUST_SECONDS + level.random.nextInt(MORE_DUST_SECONDS + 1));
            BlockParticleOption dustOf = new BlockParticleOption(ParticleTypes.FALLING_DUST, ModBlocks.CEILING_TILE.get().defaultBlockState());
            level.sendParticles(dustOf, shaft.centerX(), 46.0, shaft.centerZ(), 10, shaft.size() * 0.25, 1.5, shaft.size() * 0.25, 0.0);
        }
    }

    /** A shaft 5-16 blocks from the witness, preferably in front of them, with nobody under it. */
    private static Level0ShaftHall.Shaft pickShaft(ServerLevel level, Hall hall, ServerPlayer witness) {
        Vec3 look = witness.getLookAngle();
        Level0ShaftHall.Shaft fallback = null;
        int start = hall.shafts.isEmpty() ? 0 : level.random.nextInt(hall.shafts.size());
        for (int i = 0; i < hall.shafts.size(); i++) {
            Level0ShaftHall.Shaft shaft = hall.shafts.get((start + i) % hall.shafts.size());
            double dx = shaft.centerX() - witness.getX();
            double dz = shaft.centerZ() - witness.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < NEAR || d > FAR || occupied(level, shaft)) {
                continue;
            }
            if ((dx * look.x + dz * look.z) / d > 0.4) {
                return shaft;
            }
            if (fallback == null) {
                fallback = shaft;
            }
        }
        return fallback;
    }

    /** True if any player stands under the shaft (or right next to it). */
    private static boolean occupied(ServerLevel level, Level0ShaftHall.Shaft shaft) {
        for (ServerPlayer player : level.players()) {
            if (player.getX() > shaft.x() - 1 && player.getX() < shaft.x() + shaft.size() + 1
                    && player.getZ() > shaft.z() - 1 && player.getZ() < shaft.z() + shaft.size() + 1) {
                return true;
            }
        }
        return false;
    }

    /** The crack up top, a puff of grit, and the tile on its way down. */
    private static void dropTile(ServerLevel level, Level0ShaftHall.Shaft shaft) {
        int x = shaft.x() + level.random.nextInt(shaft.size());
        int z = shaft.z() + level.random.nextInt(shaft.size());
        // Two blocks under the roof: the block right under it may hold a dangling cable.
        BlockPos top = new BlockPos(x, Level0SetPieces.ROOF_Y - 2, z);
        if (!level.isLoaded(top) || !level.getBlockState(top).isAir()) {
            return;
        }
        level.playSound(null, top, ModSounds.L0_TILE_CRACK.get(), SoundSource.AMBIENT, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
        BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, ModBlocks.CEILING_TILE.get().defaultBlockState());
        level.sendParticles(grit, x + 0.5, top.getY() + 0.5, z + 0.5, 16, 0.4, 0.3, 0.4, 0.0);
        FallingBlockEntity.fall(level, top, Level0SetPieceBlocks.FALLEN_TILE.get().defaultBlockState());
    }
}
