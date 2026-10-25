package com.rickr.backrooms.client.light;

import com.rickr.backrooms.world.BackroomsLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side list of every light fixture block in the loaded chunks of the current Backrooms
 * dimension, grouped per chunk.
 *
 * <ul>
 *   <li>A chunk is scanned when it arrives from the server. Only the slice between the floor and
 *       the ceiling (plus one block either side) is read, and whole 16-block sections are skipped
 *       when their palette has no fixture at all, so a scan is cheap.</li>
 *   <li>Minecraft has no client event for single block changes, so we also re-scan the chunks
 *       right around the player, one per tick in a loop (each one about every second). That is
 *       how lamps switched on or off by the server get picked up.</li>
 *   <li>Everything is forgotten when the dimension changes or you log out.</li>
 * </ul>
 */
public final class LightIndex {
    /** Chunks around the player that get re-scanned in a loop (radius 2 = 5x5 chunks). */
    private static final int RESCAN_RADIUS = 2;

    @Nullable
    private ClientLevel level;
    private int minY;
    private int maxY;
    /** Block position (packed long) -> fixture block state. */
    private final Long2ObjectOpenHashMap<BlockState> fixtures = new Long2ObjectOpenHashMap<>();
    /** Chunk position (packed long) -> positions of the fixtures in that chunk. */
    private final Long2ObjectOpenHashMap<long[]> byChunk = new Long2ObjectOpenHashMap<>();
    private final LongArrayList scratch = new LongArrayList();
    /** Goes up by one whenever any fixture appears or disappears. */
    private int revision;
    private int rescanCursor;

    /** Forgets everything and binds to {@code newLevel} (null or a non-Backrooms level = inactive). */
    public void reset(@Nullable ClientLevel newLevel) {
        fixtures.clear();
        byChunk.clear();
        revision++;
        level = null;
        if (newLevel != null) {
            BackroomsLevel floor = BackroomsLevel.fromDimension(newLevel.dimension());
            if (floor != null) {
                level = newLevel;
                minY = floor.layout().floorY() - 1;
                maxY = floor.layout().highestCeilingY() + 1; // Poolrooms atriums are taller
            }
        }
    }

    @Nullable
    public ClientLevel level() {
        return level;
    }

    public int revision() {
        return revision;
    }

    /** The fixture block at a packed position, or null. */
    @Nullable
    public BlockState get(long pos) {
        return fixtures.get(pos);
    }

    /** Positions of the fixtures in a chunk, or null when it has none / isn't loaded. */
    @Nullable
    public long[] inChunk(int chunkX, int chunkZ) {
        return byChunk.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** A chunk arrived from the server (or was re-sent). */
    public void onChunkLoad(LevelChunk chunk) {
        if (level != null && chunk.getLevel() == level) {
            scan(chunk);
        }
    }

    public void onChunkUnload(ChunkPos pos) {
        if (level != null) {
            remove(pos.toLong());
        }
    }

    /** Scans the chunks around a freshly bound player (they may have loaded before we were ready). */
    public void scanAround(BlockPos center, int chunkRadius) {
        if (level == null) {
            return;
        }
        int cx = SectionPos.blockToSectionCoord(center.getX());
        int cz = SectionPos.blockToSectionCoord(center.getZ());
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk != null) {
                    scan(chunk);
                }
            }
        }
    }

    /** Re-scans one chunk near the player; cycles through the 5x5 around them. */
    public void tickRescan(BlockPos center) {
        if (level == null) {
            return;
        }
        int side = RESCAN_RADIUS * 2 + 1;
        rescanCursor = (rescanCursor + 1) % (side * side);
        int cx = SectionPos.blockToSectionCoord(center.getX()) + rescanCursor % side - RESCAN_RADIUS;
        int cz = SectionPos.blockToSectionCoord(center.getZ()) + rescanCursor / side - RESCAN_RADIUS;
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk != null) {
            scan(chunk);
        }
    }

    private void scan(LevelChunk chunk) {
        scratch.clear();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int checkedSection = Integer.MIN_VALUE;
        boolean sectionMayHave = false;
        for (int y = minY; y <= maxY; y++) {
            int index = chunk.getSectionIndex(y);
            if (index < 0 || index >= sections.length) {
                continue;
            }
            LevelChunkSection section = sections[index];
            if (index != checkedSection) {
                // Look at the section's palette once: no fixture in it means no need to read blocks.
                checkedSection = index;
                sectionMayHave = !section.hasOnlyAir() && section.maybeHas(FixtureType::isFixture);
            }
            if (!sectionMayHave) {
                continue;
            }
            int localY = y & 15;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, localY, z);
                    if (FixtureType.isFixture(state)) {
                        scratch.add(BlockPos.asLong(baseX + x, y, baseZ + z));
                    }
                }
            }
        }

        long chunkKey = chunk.getPos().toLong();
        long[] found = scratch.isEmpty() ? null : scratch.toLongArray();
        long[] old = byChunk.get(chunkKey);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean changed = !Arrays.equals(old, found);
        if (!changed && found != null) {
            // Same positions: still check the states (e.g. a lamp turned to face another way).
            for (long pos : found) {
                if (fixtures.get(pos) != chunk.getBlockState(cursor.set(pos))) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) {
            return;
        }
        remove(chunkKey);
        if (found != null) {
            byChunk.put(chunkKey, found);
            for (long pos : found) {
                fixtures.put(pos, chunk.getBlockState(cursor.set(pos)));
            }
        }
        revision++;
    }

    private void remove(long chunkKey) {
        long[] old = byChunk.remove(chunkKey);
        if (old != null) {
            for (long pos : old) {
                fixtures.remove(pos);
            }
            revision++;
        }
    }
}
