package com.rickr.backrooms.block;

import com.rickr.backrooms.world.ClayDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The clay door: a front door that looks moulded out of plasticine, lumpy and thumb-printed, like
 * something from a stop-motion film. It opens like any door. Step through the open doorway and you
 * are somewhere else: from a Level 9 house into Level 94, from a Level 94 or Level 39 house back
 * into Level 9's suburbs.
 *
 * <p>The block only notices the player; the actual trip happens on the next player tick in
 * {@link ClayDoors} (teleporting someone in the middle of their own movement is asking for trouble).
 */
public class ClayDoorBlock extends DoorBlock {
    public ClayDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    /** Called for every block whose cell the entity's box overlaps, whatever the door's shape. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && state.getValue(OPEN) && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && entity instanceof ServerPlayer player && !player.isSpectator()) {
            ClayDoors.steppedThrough(player);
        }
    }
}
