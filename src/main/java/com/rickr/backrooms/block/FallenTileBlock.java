package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ceiling tile that came down and broke on the carpet (the Shaft Hall). It is only a thin layer
 * you walk straight through (the registry gives it no collision), and it has no item, so it can't
 * be picked up: it is evidence, not loot.
 *
 * <p>It is also what the server drops down a shaft when a tile "works loose" (world/anomaly/
 * level0/ShaftHallBehavior): a falling block of this kind tumbles the whole way down and, as
 * {@link Fallable}, tells us when it lands, so the clatter and the burst of tile grit happen at
 * the exact moment it hits the floor. If it lands on a tile that is already there, it just
 * shatters (same sound) and leaves nothing new.
 */
public class FallenTileBlock extends Block implements Fallable {
    /** 2 px tall: what you see outlined when you look at it. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public FallenTileBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Lies only on something solid. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaced, FallingBlockEntity entity) {
        shatter(level, pos);
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
        shatter(level, pos);
    }

    /** The hit: a dry clatter and a puff of broken tile. */
    private static void shatter(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + 0.5;
        server.playSound(null, x, y, z, ModSounds.L0_TILE_FALL.get(), SoundSource.AMBIENT, 1.0f,
                0.9f + server.random.nextFloat() * 0.2f);
        BlockParticleOption grit = new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.CEILING_TILE.get().defaultBlockState());
        server.sendParticles(grit, x, y, z, 24, 0.45, 0.05, 0.45, 0.15);
        server.sendParticles(ParticleTypes.POOF, x, y + 0.2, z, 4, 0.4, 0.1, 0.4, 0.01);
    }
}
