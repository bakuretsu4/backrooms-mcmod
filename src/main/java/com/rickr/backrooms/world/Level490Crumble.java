package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The last bridge before the spire gives way behind you (after the play-test: "no tension on the
 * walk"). Half way across it groans and cracks; a few steps on, a stretch of it behind you breaks
 * off and falls into the void, so there is no going back. A couple of minutes later it is whole
 * again (rebuilt from the level's layout), so the way is never shut for good; the save remembers a
 * gap that is due to come back, so it comes back after a restart too.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Crumble {
    /** How long the gap stays open (ticks). */
    private static final long GAP_TICKS = 2400;
    /** The falling pieces are taken away this long after they start to fall (before they could land anywhere). */
    private static final int FALL_TICKS = 60;
    private static final double WARN_AT = 0.4;
    private static final double BREAK_AT = 0.72;
    private static final double GAP_FROM = 0.25;
    private static final double GAP_TO = 0.48;

    /** Per bridge (keyed by its line and start): whether the warning has sounded on this crossing. */
    private static final Map<Long, Boolean> WARNED = new HashMap<>();
    private static final List<FallingBlockEntity> FALLING = new ArrayList<>();

    private Level490Crumble() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 2 != 0 || player.isSpectator()
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        int[] b = Level490Layout.lastBridge(seed, player.getBlockX(), player.getBlockZ());
        if (b == null) {
            return;
        }
        boolean alongX = b[0] == 1;
        double along = alongX ? player.getX() : player.getZ();
        double off = (alongX ? player.getZ() : player.getX()) - b[1] - 0.5;
        double p = (along - b[2]) / (double) (b[3] - b[2]);
        long key = ((long) b[1] << 32) ^ (b[2] & 0xffffffffL);
        if (Math.abs(off) > 4 || p < 0 || p > 1) {
            if (p < GAP_FROM || p > 1) {
                WARNED.remove(key);
            }
            return;
        }
        Data data = data(level);
        if (data.gaps.containsKey(key)) {
            return; // already broken; it comes back by itself
        }
        double gapMid = b[2] + (b[3] - b[2]) * (GAP_FROM + GAP_TO) / 2;
        double x = alongX ? gapMid : b[1] + 0.5;
        double z = alongX ? b[1] + 0.5 : gapMid;
        double y = (b[4] + b[5]) / 2.0;
        if (p >= WARN_AT && !WARNED.getOrDefault(key, false)) {
            WARNED.put(key, true);
            level.playSound(null, x, y, z, ModSounds.LEVEL490_CHAIN_CREAK.get(), SoundSource.BLOCKS, 1.6f, 0.7f);
            level.playSound(null, x, y, z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.4f, 0.5f);
        }
        if (p >= BREAK_AT) {
            collapse(level, b, x, y, z);
            data.gaps.put(key, new long[] {b[0], b[1], b[2], b[3], level.getGameTime() + GAP_TICKS});
            data.setDirty();
        }
    }

    /** The stretch from GAP_FROM to GAP_TO of the bridge falls away. */
    private static void collapse(ServerLevel level, int[] b, double x, double y, double z) {
        boolean alongX = b[0] == 1;
        int a0 = b[2] + (int) Math.round((b[3] - b[2]) * GAP_FROM);
        int a1 = b[2] + (int) Math.round((b[3] - b[2]) * GAP_TO);
        int yLo = Math.min(b[4], b[5]) - 3;
        int yHi = Math.max(b[4], b[5]) + 3;
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) {
            for (int o = -3; o <= 3; o++) {
                for (int yy = yLo; yy <= yHi; yy++) {
                    BlockPos pos = alongX ? new BlockPos(a, yy, b[1] + o) : new BlockPos(b[1] + o, yy, a);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) {
                        continue;
                    }
                    FallingBlockEntity f = FallingBlockEntity.fall(level, pos, st);
                    f.disableDrop();
                    f.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, -0.05 * level.random.nextDouble(),
                            (level.random.nextDouble() - 0.5) * 0.1);
                    FALLING.add(f);
                }
            }
        }
        level.playSound(null, x, y, z, ModSounds.LEVEL490_CHAIN_SNAP.get(), SoundSource.BLOCKS, 1.4f, 1.3f);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 0.55f);
        level.playSound(null, x, y, z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.6f, 0.6f);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_490) {
            return;
        }
        // Falling pieces gone before they can land on anything.
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
        boolean changed = data.gaps.values().removeIf(g -> {
            if (now < g[4]) {
                return false;
            }
            restore(level, g);
            return true;
        });
        if (changed) {
            data.setDirty();
        }
    }

    /** Builds the broken stretch again from the level's layout. */
    private static void restore(ServerLevel level, long[] g) {
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        LevelLayout layout = BackroomsLevel.LEVEL_490.layout();
        boolean alongX = g[0] == 1;
        int line = (int) g[1];
        int a0 = (int) (g[2] + Math.round((g[3] - g[2]) * GAP_FROM)) - 1;
        int a1 = (int) (g[2] + Math.round((g[3] - g[2]) * GAP_TO)) + 1;
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) {
            for (int o = -3; o <= 3; o++) {
                int x = alongX ? a : line + o;
                int z = alongX ? line + o : a;
                BlockState[] col = new BlockState[40];
                int base = 180;
                layout.fillColumn(seed, x, z, (y, state) -> {
                    if (y >= base && y < base + col.length) {
                        col[y - base] = state;
                    }
                });
                for (int k = 0; k < col.length; k++) {
                    BlockPos pos = new BlockPos(x, base + k, z);
                    BlockState want = col[k] == null ? Blocks.AIR.defaultBlockState() : col[k];
                    if (!level.getBlockState(pos).equals(want)) {
                        level.setBlock(pos, want, 2);
                    }
                }
            }
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "backrooms_level490_crumble");
    }

    private static final class Data extends SavedData {
        /** key -> {along x, line, from, to, due tick}. */
        final Map<Long, long[]> gaps = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (Tag t : tag.getList("gaps", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                data.gaps.put(c.getLong("key"), c.getLongArray("gap"));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<Long, long[]> e : gaps.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putLong("key", e.getKey());
                c.putLongArray("gap", e.getValue());
                list.add(c);
            }
            tag.put("gaps", list);
            return tag;
        }
    }
}
