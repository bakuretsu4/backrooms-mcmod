package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.world.anomaly.AnomalyService;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * One chair of the Crimson Field chairlift, hanging from the cable by its hanger arm: an open bench
 * seat with a backrest, armrests, a safety bar and a crust of frost on everything. FACING is the
 * way you look when you sit in it (the way it travels).
 *
 * <p>The model hangs half a block to the RIGHT of its block (the up line runs between two block
 * columns) and reaches two blocks up to the grip on the cable. So only the half inside its own
 * block is solid/clickable, which is the half you walk up to.
 *
 * <p>Right-click the one at a lift station and you ride it ({@link GondolaChair#board}). The same
 * model (with {@link #LOWERED} bar) is what the seat entity and the client's passing chairs draw.
 */
public class GondolaChairBlock extends FurnitureBlock {
    /** The safety bar is down (someone is riding); the station chair always has it up. */
    public static final BooleanProperty LOWERED = BooleanProperty.create("lowered");

    public GondolaChairBlock(Properties properties) {
        // Facing north: the seat is offset east (x 8.5..23.5); inside its own block that is x 8.5..16.
        super(properties, Shapes.or(Block.box(8.5, 0, 2, 16, 3, 13), Block.box(8.5, 0, 12, 16, 14, 14)));
        registerDefaultState(defaultBlockState().setValue(LOWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOWERED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer rider) || rider.isPassenger()) {
            return InteractionResult.PASS;
        }
        // Only the chair waiting at a real lift station goes anywhere; a chair placed by a builder
        // somewhere else is just a chair.
        AnomalySite site = AnomalyService.siteAt(serverLevel, pos.getX(), pos.getZ());
        if (site == null || !site.kind().equals(Level9Sites.GONDOLA)) {
            return InteractionResult.PASS;
        }
        GondolaLine line = GondolaLine.of(site);
        if (!line.stationChairPos().equals(pos)) {
            return InteractionResult.PASS;
        }
        GondolaChair.board(serverLevel, rider, line, pos);
        return InteractionResult.CONSUME;
    }
}
