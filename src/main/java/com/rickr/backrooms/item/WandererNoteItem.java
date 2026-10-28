package com.rickr.backrooms.item;

import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitHints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A torn note left in a supply container by someone who came through before you. Right-click to
 * read it: a few scrawled lines with a rough direction and distance to the level's exit, measured
 * from the container it was found in (so read it there). Some of them are wrong: the writer was
 * lost, or lying. A few also hint at the other ways to find the exit (listen for the sound that
 * stays in one place; NightShot shows painted marks). See {@code world/ExitHints}.
 *
 * <p>The note keeps which lines it has (NOTE), the compass direction (DIR, 0 = north, clockwise in
 * eighths) and how far (FAR, 0..2) in its custom data; the words are in the language file
 * ({@code note.backrooms.wanderer.N}, {@code note.backrooms.dir.N}, {@code note.backrooms.far.N}).
 */
public class WandererNoteItem extends Item {
    public static final int NOTES = 9;
    /** Share of notes that point the wrong way. */
    private static final float WRONG = 0.2f;
    private static final String NOTE = "note";
    private static final String DIR = "dir";
    private static final String FAR = "far";

    public WandererNoteItem(Properties properties) {
        super(properties);
    }

    /** A note written at {@code pos} of this level, about its nearest exit. */
    public static ItemStack create(ServerLevel level, BackroomsLevel floor, BlockPos pos, RandomSource random) {
        return create(level, floor, pos, random, false);
    }

    /**
     * The same, but {@code honest} notes always point the right way (Level 0's sealed hidden room
     * leaves one: its writer swears they checked).
     */
    public static ItemStack create(ServerLevel level, BackroomsLevel floor, BlockPos pos, RandomSource random, boolean honest) {
        int[] exit = ExitHints.nearestExit(level, floor, pos.getX(), pos.getZ());
        double dx = exit[0] - pos.getX();
        double dz = exit[2] - pos.getZ();
        // Minecraft's north is -z: angle 0 = north, 90 = east.
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int dir = Math.floorMod((int) Math.round(degrees / 45.0), 8);
        if (!honest && random.nextFloat() < WRONG) {
            dir = Math.floorMod(dir + Mth.nextInt(random, 3, 5), 8); // roughly the opposite way
        }
        double distance = Math.sqrt(dx * dx + dz * dz);
        int far = distance < 60 ? 0 : distance < 220 ? 1 : 2;
        CompoundTag tag = new CompoundTag();
        tag.putInt(NOTE, random.nextInt(NOTES));
        tag.putInt(DIR, dir);
        tag.putInt(FAR, far);
        ItemStack stack = new ItemStack(ModItems.WANDERER_NOTE.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            Component text = tag.contains(NOTE)
                    ? Component.translatable("note.backrooms.wanderer." + tag.getInt(NOTE),
                            Component.translatable("note.backrooms.dir." + tag.getInt(DIR)),
                            Component.translatable("note.backrooms.far." + tag.getInt(FAR)))
                    : Component.translatable("note.backrooms.wanderer.blank");
            player.sendSystemMessage(text.copy().withStyle(s -> s.withItalic(true)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
