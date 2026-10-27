package com.rickr.backrooms.item;

import com.rickr.backrooms.block.ChalkMarkBlock;
import com.rickr.backrooms.block.ChalkMarkBlock.Symbol;
import com.rickr.backrooms.registry.ChalkRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A stick of white chalk (64 marks) to mark your own way through the Backrooms.
 *
 * <ul>
 *   <li>Right-click a solid wall, floor or ceiling: draws the current symbol there (a
 *       {@link ChalkMarkBlock}) and uses up one mark. Drawing on an old mark replaces it.</li>
 *   <li>Sneak + right-click anything (the air, a wall, a mark): switches the symbol, arrow ->
 *       cross -> circle -> arrow, and says which one in the action bar. Nothing is drawn.</li>
 * </ul>
 *
 * <p>Where an arrow points: on a wall it follows WHERE on the block face you click (left, right,
 * top or bottom quarter of the face, so you can point down a corridor or up a ladder); on the floor
 * and ceiling it points the way you are looking.
 *
 * <p>The current symbol is kept on the stack in its custom data (like the Torn Note's text), so
 * every piece of chalk remembers its own.
 */
public class ChalkItem extends Item {
    private static final String SYMBOL_TAG = "ChalkSymbol";

    public ChalkItem(Properties properties) {
        super(properties);
    }

    /** The symbol this piece of chalk draws right now (arrow for fresh chalk). */
    public static Symbol symbol(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Symbol.byName(tag.getString(SYMBOL_TAG));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // Sneaking switches the symbol wherever you click: in Level 0's tight corridors there is
        // nearly always a wall in reach, so "click the air" alone would be hard to find. Hiding
        // spots still win: HidingService cancels the RightClickBlock event for them, and then
        // Minecraft never calls this method at all.
        if (context.isSecondaryUseActive()) {
            if (context.getPlayer() != null) {
                cycle(level, context.getPlayer(), context.getItemInHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);

        // Clicking an old mark draws over it, on the same surface. Otherwise the mark goes into
        // the block in front of the face you clicked, flat against that face.
        BlockPos target;
        Direction facing;
        if (clickedState.is(ChalkRegistry.CHALK_MARK)) {
            target = clicked;
            facing = clickedState.getValue(ChalkMarkBlock.FACING);
        } else {
            target = clicked.relative(context.getClickedFace());
            facing = context.getClickedFace();
        }

        // Chalk needs something hard and flat to write on, and room in front of it (only air or
        // another chalk mark: we never wipe out water, tape or plants).
        BlockPos support = target.relative(facing.getOpposite());
        BlockState there = level.getBlockState(target);
        if (!level.getBlockState(support).isFaceSturdy(level, support, facing)
                || !(there.isAir() || there.is(ChalkRegistry.CHALK_MARK))) {
            // FAIL (not PASS) so the click stops here instead of falling through to use().
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        Symbol symbol = symbol(stack);
        int rotation = switch (facing) {
            case UP -> ChalkMarkBlock.rotationOnFloor(context.getHorizontalDirection());
            case DOWN -> ChalkMarkBlock.rotationOnCeiling(context.getHorizontalDirection());
            default -> wallRotation(context.getClickLocation(), support, facing);
        };

        if (!level.isClientSide) {
            BlockState mark = ChalkRegistry.CHALK_MARK.get().defaultBlockState()
                    .setValue(ChalkMarkBlock.FACING, facing)
                    .setValue(ChalkMarkBlock.SYMBOL, symbol)
                    .setValue(ChalkMarkBlock.ROTATION, rotation);
            level.setBlock(target, mark, Block.UPDATE_ALL_IMMEDIATE);
            Player player = context.getPlayer();
            level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
            level.playSound(null, target, ChalkRegistry.CHALK_DRAW.get(), SoundSource.PLAYERS,
                    1.0f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            // One mark of the 64 (creative players draw for free: hurtAndBreak skips them).
            if (player != null) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Arrow direction on a wall: which quarter of the face was clicked. The face is cut along its
     * two diagonals into a top, bottom, left and right triangle; the arrow points towards the
     * edge you clicked nearest to.
     */
    private static int wallRotation(Vec3 hit, BlockPos support, Direction facing) {
        Vec3 centre = Vec3.atCenterOf(support).relative(facing, 0.5);
        Vec3 d = hit.subtract(centre);
        // "Right" as seen by someone standing in front of the wall looking at it.
        Direction right = facing.getCounterClockWise();
        double u = d.x * right.getStepX() + d.z * right.getStepZ();
        double v = d.y;
        if (Math.abs(u) > Math.abs(v)) {
            return u > 0 ? 1 : 3;
        }
        return v > 0 ? 0 : 2;
    }

    /** Sneak + right-click the air: switch to the next symbol. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        cycle(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Moves this stick on to its next symbol (on the server; the stack syncs to the client). */
    private static void cycle(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Symbol next = symbol(stack).next();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(SYMBOL_TAG, next.getSerializedName()));
        // The vanilla action bar (the `true`): a short line above the hotbar that fades by itself.
        player.displayClientMessage(Component.translatable("message.backrooms.chalk.symbol",
                Component.translatable("chalk.backrooms.symbol." + next.getSerializedName())), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.chalk.tooltip",
                Component.translatable("chalk.backrooms.symbol." + symbol(stack).getSerializedName()))
                .withStyle(ChatFormatting.GRAY));
    }
}
