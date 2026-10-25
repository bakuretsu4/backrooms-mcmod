package com.rickr.backrooms.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Builds Backrooms chunks straight from a {@link LevelLayout}: no noise, caves, ores or
 * structures, just the floor plan. Each 16x16 chunk asks the layout for its 256 columns.
 */
public final class BackroomsChunkGenerator extends ChunkGenerator {
    public static final MapCodec<BackroomsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            BackroomsLevel.CODEC.fieldOf("level").forGetter(g -> g.level)
    ).apply(i, BackroomsChunkGenerator::new));

    private static final int MIN_Y = 0;
    private static final int HEIGHT = 64;

    private final BackroomsLevel level;

    public BackroomsChunkGenerator(BiomeSource biomeSource, BackroomsLevel level) {
        super(biomeSource);
        this.level = level;
    }

    public BackroomsLevel level() {
        return level;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structures, ChunkAccess chunk) {
        long seed = BackroomsSeeds.layoutSeed(randomState, level);
        LevelLayout layout = level.layout();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkPos.getMinBlockX() + lx;
                int z = chunkPos.getMinBlockZ() + lz;
                layout.fillColumn(seed, x, z, (y, state) -> chunk.setBlockState(pos.set(x, y, z), state, false));
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState randomState) {
        BlockState[] column = column(x, z, randomState);
        for (int y = column.length - 1; y >= 0; y--) {
            if (type.isOpaque().test(column[y])) {
                return MIN_Y + y + 1;
            }
        }
        return MIN_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState randomState) {
        return new NoiseColumn(MIN_Y, column(x, z, randomState));
    }

    private BlockState[] column(int x, int z, RandomState randomState) {
        BlockState[] states = new BlockState[HEIGHT];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        level.layout().fillColumn(BackroomsSeeds.layoutSeed(randomState, level), x, z, (y, state) -> {
            if (y >= MIN_Y && y < MIN_Y + HEIGHT) {
                states[y - MIN_Y] = state;
            }
        });
        return states;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Backrooms: " + level.getSerializedName());
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }
}
