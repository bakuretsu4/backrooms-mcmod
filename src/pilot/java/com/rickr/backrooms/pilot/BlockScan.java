package com.rickr.backrooms.pilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Read-only search of the blocks the client already has loaded (the same data the player sees),
 * so a test can find exit doors and such without any server command. Sections that cannot
 * contain the block are skipped via their palette, which keeps even large radii cheap.
 */
final class BlockScan {
    private BlockScan() {}

    static JsonObject scan(ClientLevel level, BlockPos center, int radius, String id, int limit) {
        Predicate<BlockState> match;
        if (id.startsWith("#")) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(id.substring(1)));
            match = s -> s.is(tag);
        } else {
            ResourceLocation rl = ResourceLocation.parse(id);
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(rl);
            if (block.isEmpty()) {
                return Pilot.error("unknown block id " + id);
            }
            Block b = block.get();
            match = s -> s.is(b);
        }
        radius = Math.max(1, Math.min(radius, 160));
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        long r2 = (long) radius * radius;
        LongArrayList hits = new LongArrayList();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection sec = sections[si];
                    int sy = level.getSectionYFromSectionIndex(si) << 4;
                    if (sy + 15 < minY || sy > maxY || sec.hasOnlyAir() || !sec.maybeHas(match)) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        int y = sy + ly;
                        if (y < minY || y > maxY) continue;
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                if (!match.test(sec.getBlockState(lx, ly, lz))) continue;
                                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                                long dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                hits.add(BlockPos.asLong(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        hits.sort((long a, long b) -> Long.compare(dist2(a, center), dist2(b, center)));
        JsonArray arr = new JsonArray();
        for (int i = 0; i < hits.size() && i < limit; i++) {
            long p = hits.getLong(i);
            JsonArray pos = new JsonArray();
            pos.add(BlockPos.getX(p));
            pos.add(BlockPos.getY(p));
            pos.add(BlockPos.getZ(p));
            arr.add(pos);
        }
        JsonObject res = Pilot.ok();
        res.addProperty("block", id);
        res.addProperty("radius", radius);
        res.addProperty("count", hits.size());
        res.add("positions", arr);
        return res;
    }

    private static long dist2(long p, BlockPos c) {
        long dx = BlockPos.getX(p) - c.getX(), dy = BlockPos.getY(p) - c.getY(), dz = BlockPos.getZ(p) - c.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
