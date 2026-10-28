package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The small things the maintenance crews left behind in Level 2's chambers (world/layout/
 * Level2Chambers): the shift logbook lying open on a bench, an enamel kettle, a chipped mug, a
 * work jacket on a hook. They stand on top of the furniture below them (their models start a
 * pixel low, so they rest on a workbench or table top), or hang on the wall (the jacket).
 * FACING points away from the wall / towards whoever reads it, like every wall-mounted block.
 *
 * <p>The logbook can be read: right-click and one page of the shift log appears in chat. Which
 * page depends on where the book lies (message.backrooms.l2.logbook.N in the language file), so
 * every chamber's book is a different page of the same crew's story, and some pages mention the
 * strange things elsewhere on the level.
 */
public class Level2ChamberPropBlock extends WallMountBlock {
    public enum Prop implements StringRepresentable {
        LOGBOOK, KETTLE, MUG, JACKET;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Prop> PROP = EnumProperty.create("prop", Prop.class);
    /** How many logbook pages there are in the language file. */
    public static final int LOG_PAGES = 8;

    // The outlines you see when you look at a prop (as it faces north; they are walk-through).
    private static final Map<Prop, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Prop.class);

    static {
        put(Prop.LOGBOOK, Block.box(3, 0, 4, 13, 2, 12));
        put(Prop.KETTLE, Block.box(4, 0, 5, 12, 8, 11));
        put(Prop.MUG, Block.box(6, 0, 6, 10, 5, 10));
        put(Prop.JACKET, Block.box(2, 0, 13, 14, 15, 16));
    }

    private static void put(Prop prop, VoxelShape north) {
        Map<Direction, VoxelShape> turned = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            turned.put(facing, turn(north, facing));
        }
        SHAPES.put(prop, turned);
    }

    public Level2ChamberPropBlock(Properties properties) {
        super(properties, 3, 0, 4, 13, 2, 12);
        registerDefaultState(defaultBlockState().setValue(PROP, Prop.LOGBOOK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PROP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PROP)).get(state.getValue(FACING));
    }

    /** The page of the log a book at {@code pos} lies open at (the same for everyone, always). */
    public static int logPage(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) Math.floorMod(h, (long) LOG_PAGES);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PROP) != Prop.LOGBOOK) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.sendSystemMessage(Component.translatable("message.backrooms.l2.logbook." + logPage(pos))
                    .withStyle(s -> s.withItalic(true)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
