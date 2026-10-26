package com.rickr.backrooms.world.anomaly.level1;

import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.SupplyContainerBlockEntity;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Crate halls: storage halls where featureless wooden crates appear behind your back and vanish
 * again when nobody is looking. Based on the current Wikidot article ("Crates... usually appear
 * behind your back or after a Flickering event, and can disappear when nobody is looking at them";
 * the Gild sector has the most of them, <a href="https://backrooms-wiki.wikidot.com/level-1">level-1</a>)
 * and the archived one (when the lights fail "supplies are liable to vanish inexplicably"; crates
 * may hold car parts, crayons, used syringes, human hair,
 * <a href="https://backrooms-wiki.wikidot.com/archived:level-1">archived:level-1</a>).
 *
 * <p>Once a second (siteTick):
 * <ul>
 *   <li>while a blackout or flicker is on, every crate the hall made disappears, seen or not,
 *       opened or not; when the lights come back a few new ones stand somewhere out of sight;</li>
 *   <li>unopened crates that nobody can see may vanish;</li>
 *   <li>for each player in the hall, now and then a crate appears on a rack or on the floor
 *       BEHIND them (outside everyone's view), with a soft thud.</li>
 * </ul>
 * Crates only ever appear on the hall's crate spots ({@link Level1AnomalyPlan#crateSpots}), where
 * the layout builds nothing; so any crate standing on such a spot is one of ours, and only those
 * are ever removed. The crates are the ordinary Wooden Crate (same loot table); some also hold junk.
 */
public final class CrateHallBehavior implements AnomalyBehavior {
    /** How far away a player can notice a crate appearing or disappearing. */
    private static final double SEE_RANGE = 48.0;
    /** Chance per second that an unseen, unopened crate is gone. */
    private static final float VANISH_CHANCE = 0.04f;
    /** On average one new crate every this many seconds per player in the hall. */
    private static final int SPAWN_ONE_IN = 6;
    /** Share of new crates that also hold some of the odd things found in Level 1's crates. */
    private static final float JUNK_CHANCE = 0.35f;
    private static final Item[] CRAYONS = {Items.RED_DYE, Items.BLUE_DYE, Items.YELLOW_DYE, Items.GREEN_DYE};

    /** Per hall: were the lights out at its last tick? (To notice the moment they come back.) */
    private static final Map<AnomalySite, HallState> STATES = new HashMap<>();

    private static final class HallState {
        boolean dark;
        long lastTick;
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        long now = level.getGameTime();
        HallState state = STATES.computeIfAbsent(site, s -> new HallState());
        state.lastTick = now;
        if (STATES.size() > 64) {
            STATES.values().removeIf(s -> now - s.lastTick > 200); // halls nobody is near any more
        }
        RandomSource random = level.getRandom();
        List<BlockPos> spots = Level1AnomalyPlan.crateSpots(site);
        List<BlockPos> crates = new ArrayList<>();
        for (BlockPos pos : spots) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.SUPPLY_CRATE.get())) {
                crates.add(pos);
            }
        }

        if (LevelEvents.isLightsOut(BackroomsLevel.LEVEL_1)) {
            for (BlockPos pos : crates) {
                vanish(level, pos);
            }
            state.dark = true;
            return;
        }
        int cap = 8 + (int) (site.salt() & 7);
        if (state.dark) {
            // The lights are back: and there are crates where there were none a moment ago.
            state.dark = false;
            int burst = 2 + random.nextInt(3);
            for (int i = 0; i < 24 && burst > 0 && crates.size() < cap; i++) {
                BlockPos pos = spots.get(random.nextInt(spots.size()));
                if (free(level, pos) && !Observation.seenByAnyone(level, Vec3.atCenterOf(pos), SEE_RANGE)) {
                    place(level, pos, random, false);
                    crates.add(pos);
                    burst--;
                }
            }
            return;
        }

        for (Iterator<BlockPos> it = crates.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (random.nextFloat() < VANISH_CHANCE && !opened(level, pos)
                    && !Observation.seenByAnyone(level, Vec3.atCenterOf(pos), SEE_RANGE)) {
                vanish(level, pos);
                it.remove();
            }
        }

        for (ServerPlayer player : level.players()) {
            if (crates.size() >= cap) {
                break;
            }
            if (player.isSpectator() || site.distanceTo(player.getX(), player.getZ()) > 1.0 || random.nextInt(SPAWN_ONE_IN) != 0) {
                continue;
            }
            BlockPos pos = behind(level, spots, player, random);
            if (pos != null) {
                place(level, pos, random, true);
                crates.add(pos);
            }
        }
    }

    /** A free crate spot 3-14 blocks behind the player that nobody can see, or null. */
    @Nullable
    private static BlockPos behind(ServerLevel level, List<BlockPos> spots, ServerPlayer player, RandomSource random) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int i = 0; i < 20; i++) {
            BlockPos pos = spots.get(random.nextInt(spots.size()));
            Vec3 centre = Vec3.atCenterOf(pos);
            Vec3 to = centre.subtract(eye);
            double distance = to.length();
            if (distance < 3.0 || distance > 14.0 || look.dot(to) / distance > -0.2) {
                continue; // too close, too far or not behind them
            }
            if (free(level, pos) && !Observation.seenByAnyone(level, centre, SEE_RANGE)) {
                return pos;
            }
        }
        return null;
    }

    /** Empty spot, chunk loaded, and nobody (player or monster) standing in it. */
    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir()
                && level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty();
    }

    private static boolean opened(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SupplyContainerBlockEntity crate && crate.isLootFilled();
    }

    private static void place(ServerLevel level, BlockPos pos, RandomSource random, boolean thud) {
        level.setBlock(pos, ModBlocks.SUPPLY_CRATE.get().defaultBlockState()
                .setValue(SupplyContainerBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), Block.UPDATE_ALL);
        if (random.nextFloat() < JUNK_CHANCE && level.getBlockEntity(pos) instanceof SupplyContainerBlockEntity crate) {
            // The loot is still rolled when it's first opened; the junk just takes some of the slots.
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                crate.setItem(random.nextInt(crate.getContainerSize()), junk(random));
            }
        }
        if (thud) {
            // Something heavy settles somewhere behind you.
            level.playSound(null, pos, ModSounds.L1_CRATE_THUD.get(), SoundSource.BLOCKS, 0.45f, 0.85f + random.nextFloat() * 0.2f);
        }
    }

    /** Takes a crate away. Its contents go with it (nothing spills out: it simply isn't there). */
    private static void vanish(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SupplyContainerBlockEntity crate) {
            crate.clearContent();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * The odd things the archived article lists, plus yarn and "chemicals" from the current one.
     * The chemicals are a real hazard: an unlabelled bottle of poison.
     */
    private static ItemStack junk(RandomSource random) {
        return switch (random.nextInt(7)) {
            case 0 -> named(new ItemStack(Items.IRON_NUGGET, 1 + random.nextInt(3)), "car_part");
            case 1 -> named(new ItemStack(CRAYONS[random.nextInt(CRAYONS.length)], 1 + random.nextInt(4)), "crayon");
            case 2 -> named(new ItemStack(Items.GLASS_BOTTLE), "syringe");
            case 3 -> named(new ItemStack(Items.STRING, 1 + random.nextInt(2)), "human_hair");
            case 4 -> named(new ItemStack(Items.STRING, 2 + random.nextInt(4)), "yarn");
            case 5 -> named(new ItemStack(Items.GLASS_PANE, 1 + random.nextInt(3)), "glass_shards");
            default -> {
                // Unlabelled: the tooltip doesn't say what it does.
                ItemStack chemicals = named(PotionContents.createItemStack(Items.POTION, Potions.POISON), "chemicals");
                chemicals.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
                yield chemicals;
            }
        };
    }

    private static ItemStack named(ItemStack stack, String what) {
        // A custom name (not italic, so it doesn't read as renamed): potions ignore ITEM_NAME.
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.backrooms.l1_junk." + what).withStyle(s -> s.withItalic(false)));
        return stack;
    }
}
