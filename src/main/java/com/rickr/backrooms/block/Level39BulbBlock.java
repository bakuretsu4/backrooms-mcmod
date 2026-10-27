package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bare light bulb screwed into the popcorn ceiling of a Level 39 house, upside down, its socket
 * flush with the plaster (the reference photo of the level: dozens of them dotted over one
 * ceiling). It sits in the air block under the ceiling and hangs from the top of that block.
 *
 * <p>LIT: about a third still glow, warm and dim; the rest are dead. FLICKER: a few of the lit
 * ones have a loose filament and now and then stutter off and on. HANGING is false in the
 * upside-down houses, where the "ceiling" is the floor and the bulbs stick up out of it.
 * You walk through them.
 */
public class Level39BulbBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    public static final BooleanProperty FLICKER = BooleanProperty.create("flicker");
    /** Light level of a lit bulb: enough to read the wallpaper by, not enough to feel safe. */
    public static final int LIGHT = 8;

    // Matches the model: a 6 px socket ring flush with the ceiling and the 4 px bulb below it.
    private static final VoxelShape HANGING_SHAPE = Block.box(5, 8.5, 5, 11, 16, 11);
    private static final VoxelShape STANDING_SHAPE = Block.box(5, 0, 5, 11, 7.5, 11);

    public Level39BulbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(HANGING, true).setValue(FLICKER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, HANGING, FLICKER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? HANGING_SHAPE : STANDING_SHAPE;
    }

    /** Only the flickering bulbs need random ticks. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(FLICKER);
    }

    /** Every so often a loose bulb starts a stutter: off for a moment, then back (see {@link #tick}). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 2 + random.nextInt(5));
        }
    }

    /** One beat of a stutter: a dark bulb comes back on, and often goes again a moment later. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FLICKER)) {
            return;
        }
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
            if (random.nextFloat() < 0.6f) {
                level.scheduleTick(pos, this, 3 + random.nextInt(25));
            }
        } else {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 2 + random.nextInt(6));
        }
    }

    /** In creative: click the underside of a ceiling to hang it, the top of a floor to stand it up. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HANGING, context.getClickedFace() != Direction.UP);
    }
}
