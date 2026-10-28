package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.registry.Level227FloraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lestron: a herb like purple sage that grows on the edges of the branches, where the soil thins
 * out over the drop (canon: it is used to create "[ACCESS DENIED]").
 *
 * <p>Right-click it to pick a few sprigs (the {@code backrooms:lestron} item). The plant stays,
 * cut back (HARVESTED), and grows back after several minutes, so a patch can be visited again.
 * Picking it means stepping right to the edge, which is the point.
 */
public class Level227LestronBlock extends Block {
    public static final BooleanProperty HARVESTED = BooleanProperty.create("harvested");

    private static final VoxelShape FULL = Block.box(2, 0, 2, 14, 12, 14);
    private static final VoxelShape CUT = Block.box(3, 0, 3, 13, 5, 13);

    public Level227LestronBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HARVESTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HARVESTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HARVESTED) ? CUT : FULL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(HARVESTED)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack sprigs = new ItemStack(Level227Flora.LESTRON.get(), 1 + level.random.nextInt(2));
            if (!player.getInventory().add(sprigs)) {
                player.drop(sprigs, false);
            }
            level.setBlock(pos, state.setValue(HARVESTED, true), Block.UPDATE_ALL);
            level.playSound(null, pos, Level227FloraSounds.RUSTLE.get(), SoundSource.BLOCKS, 0.6f, 1.25f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Only cut plants need the random ticks (to grow back). */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HARVESTED);
    }

    /**
     * A block gets a random tick about once a minute on average, so 1 in 8 means the herb is back
     * after roughly eight minutes.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) == 0) {
            level.setBlock(pos, state.setValue(HARVESTED, false), Block.UPDATE_ALL);
        }
    }
}
