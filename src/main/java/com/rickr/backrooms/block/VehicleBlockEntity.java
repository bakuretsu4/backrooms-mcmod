package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A parked vehicle's anchor. It stores nothing (the block state says which vehicle and which way it
 * points); it exists only so the client can draw the vehicle with a block entity renderer.
 */
public class VehicleBlockEntity extends BlockEntity {
    public VehicleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.L94_VEHICLE.get(), pos, state);
    }
}
