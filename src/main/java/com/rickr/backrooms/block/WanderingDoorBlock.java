package com.rickr.backrooms.block;

import com.rickr.backrooms.world.anomaly.level94.WanderingDoorBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The wandering door of Level 94's castle (anomaly {@code level_94/wandering_door}): an ordinary
 * red house door standing in its frame with no wall around it. It is never where you saw it last.
 * Open it and step through, and you come out in the castle's throne room, wherever the door stood.
 *
 * <p>Like the clay door, the block only notices the player; the trip itself happens on the
 * player's next tick in {@link WanderingDoorBehavior}.
 */
public class WanderingDoorBlock extends DoorBlock {
    public WanderingDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    /** Called for every block whose cell the entity's box overlaps, whatever the door's shape. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && state.getValue(OPEN) && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && entity instanceof ServerPlayer player && !player.isSpectator()) {
            WanderingDoorBehavior.steppedThrough(player);
        }
    }
}
