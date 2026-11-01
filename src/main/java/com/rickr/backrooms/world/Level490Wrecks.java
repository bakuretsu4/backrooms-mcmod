package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level490Spire;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The creature wrecking the outside stair of the spire (world/Level490Hunt; after the play-test:
 * "more violent"): its hand tears a stretch of the stair out ({@link #tear}), its shaking of the
 * tower crumbles the stair's outer edge ({@link #crumble}). What breaks falls away into the void and
 * is built again from the layout a minute and a half later (remembered in the save, so a gap left
 * when you quit is mended too), as world/Level490Crumble does for the last bridge.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Wrecks {
    /** How long a wreck stays (ticks). */
    private static final long MENDS_AFTER = 1800;
    /** Falling pieces are taken away before they can land on the turn of the stair below (34 down). */
    private static final int FALL_TICKS = 30;
    private static final List<FallingBlockEntity> FALLING = new ArrayList<>();

    private Level490Wrecks() {}

    /**
     * Tears out the stair round (x, y, z): every block of it within {@code radius} (across) and from
     * 3 below to 1 above y, the whole width of the stair, leaving a gap to jump. Never the tower's body.
     */
    public static void tear(ServerLevel level, Level490Spire spire, double x, double y, double z, double radius) {
        int r = (int) Math.ceil(radius);
        int y0 = (int) Math.floor(y) - 3;
        int y1 = (int) Math.floor(y) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double bx = Math.floor(x) + dx + 0.5;
                double bz = Math.floor(z) + dz + 0.5;
                if (Math.hypot(bx - x, bz - z) > radius) {
                    continue;
                }
                for (int by = y0; by <= y1; by++) {
                    if (onStair(spire, bx, by, bz)) {
                        breakOff(level, new BlockPos((int) Math.floor(bx), by, (int) Math.floor(bz)));
                    }
                }
            }
        }
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.5f);
        level.playSound(null, x, y, z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
    }

    /** Crumbles up to {@code count} blocks of the stair's outer edge within 4 blocks of (x, y, z). Never the inner lane. */
    public static void crumble(ServerLevel level, Level490Spire spire, double x, double y, double z, int count) {
        List<BlockPos> edge = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos p = BlockPos.containing(x + dx, y + dy - 1, z + dz);
                    double bx = p.getX() + 0.5;
                    double bz = p.getZ() + 0.5;
                    if (!onStair(spire, bx, p.getY(), bz) || level.getBlockState(p).isAir()) {
                        continue;
                    }
                    // Outer lanes only: well out from the tower's surface at that height.
                    double[] s = spire.surface(bx, p.getY(), bz);
                    double cxz = Math.hypot(s[0] - spire.centreX() - 0.5, s[1] - spire.centreZ() - 0.5);
                    double r = Math.hypot(bx - spire.centreX() - 0.5, bz - spire.centreZ() - 0.5);
                    if (r > cxz + 2.3) {
                        edge.add(p);
                    }
                }
            }
        }
        java.util.Collections.shuffle(edge, new java.util.Random(level.getRandom().nextLong()));
        for (int i = 0; i < Math.min(count, edge.size()); i++) {
            breakOff(level, edge.get(i));
            // And the stair below it, so it is gone, not just a hole in a step.
            breakOff(level, edge.get(i).below());
        }
        if (!edge.isEmpty()) {
            level.playSound(null, x, y, z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
        }
    }

    /** True if a block at (x, y, z) is part of the outside stair (something you stand on it from above). */
    private static boolean onStair(Level490Spire spire, double x, int y, double z) {
        return spire.outsideProgress(x, y + 1.0, z) >= 0 || spire.outsideProgress(x, y + 1.5, z) >= 0;
    }

    private static void breakOff(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4,
                0.4, 0.1);
        FallingBlockEntity f = FallingBlockEntity.fall(level, pos, st);
        f.disableDrop();
        f.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.15, -0.1 * level.random.nextDouble(), (level.random.nextDouble() - 0.5) * 0.15);
        FALLING.add(f);
        Data data = data(level);
        data.wrecks.add(new long[] {pos.asLong(), level.getGameTime() + MENDS_AFTER});
        data.setDirty();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_490) {
            return;
        }
        FALLING.removeIf(f -> {
            if (f.isRemoved()) {
                return true;
            }
            if (f.time > FALL_TICKS) {
                f.discard();
                return true;
            }
            return false;
        });
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        Data data = data(level);
        long now = level.getGameTime();
        boolean changed = data.wrecks.removeIf(w -> {
            if (now < w[1]) {
                return false;
            }
            mend(level, BlockPos.of(w[0]));
            return true;
        });
        if (changed) {
            data.setDirty();
        }
    }

    /** Builds a broken block again from the level's layout (if nothing has been put there since). */
    private static void mend(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        LevelLayout layout = BackroomsLevel.LEVEL_490.layout();
        BlockState[] want = {Blocks.AIR.defaultBlockState()};
        layout.fillColumn(seed, pos.getX(), pos.getZ(), (y, state) -> {
            if (y == pos.getY()) {
                want[0] = state;
            }
        });
        if (!want[0].isAir()) {
            level.setBlock(pos, want[0], 2);
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "backrooms_level490_wrecks");
    }

    private static final class Data extends SavedData {
        /** {block pos, tick it mends}. */
        final List<long[]> wrecks = new ArrayList<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (Tag t : tag.getList("wrecks", Tag.TAG_COMPOUND)) {
                data.wrecks.add(((CompoundTag) t).getLongArray("w"));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (long[] w : wrecks) {
                CompoundTag c = new CompoundTag();
                c.putLongArray("w", w);
                list.add(c);
            }
            tag.put("wrecks", list);
            return tag;
        }
    }
}
