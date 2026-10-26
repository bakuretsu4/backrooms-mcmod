package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A layer of plastic balls in the castle's ball pit (Level 94). Like powder snow: there is nothing
 * to stand on, so you sink to the bottom of the pit, and while you are in it every step is slow,
 * as if wading. Holding jump (or walking into the side of the pit) climbs you back out, the way a
 * ladder does, so nobody gets stuck two blocks down.
 */
public class BallPitBlock extends Block {
    /** How much of each tick's movement survives inside the balls (x/z = wading, y = sinking). */
    private static final Vec3 WADING = new Vec3(0.7, 1.2, 0.7);

    public BallPitBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.makeStuckInBlock(state, WADING);
    }

    /** Climbable like a ladder: the only way back up out of a two-deep pit without collision. */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }
}
