package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level369StatePayload;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.layout.Level369Plan;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Level 369's twist: the longer you stay, the more you forget (docs/level-369-comely.md). Nothing
 * for the first {@link #START_TICKS} (5 minutes); by {@link #FULL_TICKS} (25 minutes) it is total.
 * <ul>
 *   <li><b>The camcorder's readouts</b> fade one by one (client/CamcorderHud, from the amount sent
 *       in {@link Level369StatePayload} once a second).</li>
 *   <li><b>The way back:</b> a doorway you came through, once it is behind you and well out of
 *       sight, is walled over, and a new one opens somewhere else in the same wall. The two rooms
 *       stay joined (you can never be shut in); the way just isn't where you left it. Hallways'
 *       doorways are left alone (they line up with their corridors).</li>
 *   <li><b>Your things:</b> now and then a stack slips out of your inventory and lies in the grass
 *       a few blocks away in the same room, behind you; you can find it if you look. Never the
 *       camcorder or what's in your hand.</li>
 * </ul>
 * Everything comes back the moment you leave Level 369, by the arch of vines or any other way:
 * lost things still lying in the grass return to your inventory, and the doorways are put back as
 * the level made them. What a visit has done is saved with the world, so logging out doesn't undo
 * (or lose) it.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level369Forgetting {
    /** Nothing is forgotten before this (ticks spent in the level this visit) ... */
    public static final int START_TICKS = 5 * 60 * 20;
    /** ... and everything by this. */
    public static final int FULL_TICKS = 25 * 60 * 20;
    /** Seconds between two lost things, on average, once forgetting is total. */
    private static final float ITEM_SECONDS_AT_FULL = 75.0f;
    /** Chance per second that a remembered doorway behind you changes: base + this times the amount. */
    private static final float DOOR_CHANCE_BASE = 0.03f;
    private static final float DOOR_CHANCE_FULL = 0.12f;
    /** A doorway only changes this far from you (blocks) ... */
    private static final double DOOR_FAR = 9.0;
    /** ... and from everyone else. */
    private static final double DOOR_CLEAR = 6.0;
    /** Doorways crossed most recently that may still change; older ones are forgotten by us too. */
    private static final int REMEMBERED_DOORS = 6;
    /** A lost thing lands this far from you (blocks). */
    private static final double DROP_MIN = 3.0;
    private static final double DROP_MAX = 7.5;
    /** Marks a lost thing's item entity with its owner (persistent entity data). */
    private static final String OWNER_TAG = "backrooms_l369_owner";

    /** The cell each player was last in, to notice when they go through a doorway. */
    private static final Map<UUID, long[]> LAST_CELL = new ConcurrentHashMap<>();

    private Level369Forgetting() {}

    /** How much has been forgotten after {@code ticks} in the level: 0 .. 1. */
    public static float forgotten(int ticks) {
        return Mth.clamp((ticks - START_TICKS) / (float) (FULL_TICKS - START_TICKS), 0.0f, 1.0f);
    }

    // ------------------------------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.isCreative()
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_369) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Data data = data(level);
        Visit visit = data.visits.computeIfAbsent(player.getUUID(), id -> new Visit());
        visit.ticks++;
        data.setDirty();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_369);
        noticeDoorway(player, seed, visit);
        if (visit.ticks % 20 != 0) {
            return;
        }
        float amount = forgotten(visit.ticks);
        PacketDistributor.sendToPlayer(player, new Level369StatePayload(seed, amount));
        if (amount <= 0.0f) {
            return;
        }
        RandomSource random = player.getRandom();
        if (random.nextFloat() < DOOR_CHANCE_BASE + DOOR_CHANCE_FULL * amount) {
            moveADoorway(player, level, seed, visit, random);
        }
        if (random.nextFloat() < amount / ITEM_SECONDS_AT_FULL) {
            loseSomething(player, level, seed, visit, random);
        }
    }

    /** Going from one cell into a neighbour through a plain doorway: remember that doorway. */
    private static void noticeDoorway(ServerPlayer player, long seed, Visit visit) {
        int cx = Math.floorDiv(player.getBlockX(), Level369Plan.CELL);
        int cz = Math.floorDiv(player.getBlockZ(), Level369Plan.CELL);
        long[] last = LAST_CELL.put(player.getUUID(), new long[] {cx, cz});
        if (last == null || last[0] == cx && last[1] == cz || Math.abs(last[0] - cx) + Math.abs(last[1] - cz) != 1) {
            return;
        }
        // The edge between the two cells belongs to the east or south one of them.
        int ex = (int) Math.max(last[0], cx);
        int ez = (int) Math.max(last[1], cz);
        int side = last[0] != cx ? Level369Plan.WEST : Level369Plan.NORTH;
        int offset = Level369Plan.plainDoorway(seed, ex, ez, side);
        if (offset < 0 || visit.hasEdge(ex, ez, side)) {
            return;
        }
        visit.recent.removeIf(d -> d[0] == ex && d[1] == ez && d[2] == side);
        visit.recent.add(new int[] {ex, ez, side, offset});
        if (visit.recent.size() > REMEMBERED_DOORS) {
            visit.recent.remove(0);
        }
    }

    // ------------------------------------------------------------------------------------------
    // The way back
    // ------------------------------------------------------------------------------------------

    /** The block column of a doorway edge at {@code along} (cell-local, 1..19). */
    private static BlockPos column(int cx, int cz, int side, int along, int y) {
        int x = cx * Level369Plan.CELL + (side == Level369Plan.WEST ? 0 : along);
        int z = cz * Level369Plan.CELL + (side == Level369Plan.WEST ? along : 0);
        return new BlockPos(x, y, z);
    }

    /** The middle of a doorway at {@code offset}, at head height. */
    private static Vec3 doorwayCentre(int cx, int cz, int side, int offset) {
        return Vec3.atCenterOf(column(cx, cz, side, offset, Level369Plan.DOOR_TOP - 1));
    }

    /** True when nobody is near the doorway and it is well behind {@code player} (out of sight). */
    private static boolean unseen(ServerPlayer player, ServerLevel level, Vec3 centre) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = centre.subtract(eye);
        if (to.length() < DOOR_FAR || player.getLookAngle().dot(to.normalize()) > -0.1) {
            return false;
        }
        for (ServerPlayer other : level.players()) {
            if (other != player && other.position().distanceTo(centre) < DOOR_CLEAR + 4.0) {
                return false;
            }
        }
        return true;
    }

    private static void moveADoorway(ServerPlayer player, ServerLevel level, long seed, Visit visit, RandomSource random) {
        for (int i = 0; i < visit.recent.size(); i++) {
            int[] d = visit.recent.get(i);
            Vec3 old = doorwayCentre(d[0], d[1], d[2], d[3]);
            if (!unseen(player, level, old)) {
                continue;
            }
            int moved = newOffset(level, seed, d, random, player);
            if (moved < 0) {
                continue;
            }
            // Wall it over with the wall's own plaster (the lintel above it), then open the new one.
            for (int along = d[3] - 1; along <= d[3] + 1; along++) {
                BlockState plaster = level.getBlockState(column(d[0], d[1], d[2], along, Level369Plan.DOOR_TOP + 1));
                level.setBlock(column(d[0], d[1], d[2], along, LevelLayout.FLOOR_Y), Level369Blocks.GRASS.get().defaultBlockState(), 2);
                for (int y = LevelLayout.FLOOR_Y + 1; y <= Level369Plan.DOOR_TOP; y++) {
                    level.setBlock(column(d[0], d[1], d[2], along, y), plaster, 2);
                }
            }
            for (int along = moved - 1; along <= moved + 1; along++) {
                level.setBlock(column(d[0], d[1], d[2], along, LevelLayout.FLOOR_Y), Level369Blocks.BRICK.get().defaultBlockState(), 2);
                for (int y = LevelLayout.FLOOR_Y + 1; y <= Level369Plan.DOOR_TOP; y++) {
                    level.setBlock(column(d[0], d[1], d[2], along, y), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            visit.changed.add(new int[] {d[0], d[1], d[2], d[3], moved});
            visit.recent.remove(i);
            return;
        }
    }

    /**
     * Somewhere else along the same wall where a doorway can open: at least 4 blocks from the old
     * one, with room to walk on both sides (no pillar, bush or tree in the way), nobody near and out
     * of sight. -1 if there is no such place.
     */
    private static int newOffset(ServerLevel level, long seed, int[] d, RandomSource random, ServerPlayer player) {
        LevelLayout layout = BackroomsLevel.LEVEL_369.layout();
        int start = random.nextInt(13);
        for (int k = 0; k < 13; k++) {
            int offset = 4 + (start + k) % 13;
            if (Math.abs(offset - d[3]) < 4 || !unseen(player, level, doorwayCentre(d[0], d[1], d[2], offset))) {
                continue;
            }
            boolean clear = true;
            for (int along = offset - 1; along <= offset + 1 && clear; along++) {
                BlockPos wall = column(d[0], d[1], d[2], along, LevelLayout.FLOOR_Y + 1);
                for (int s = -1; s <= 1; s += 2) {
                    BlockPos side = d[2] == Level369Plan.WEST ? wall.offset(s, 0, 0) : wall.offset(0, 0, s);
                    clear &= layout.isWalkable(seed, side.getX(), side.getZ())
                            && !level.getBlockState(side).isSolid() && !level.getBlockState(side.above()).isSolid();
                }
            }
            if (clear) {
                return offset;
            }
        }
        return -1;
    }

    /** Builds a doorway edge's wall columns again exactly as the level made them. */
    private static void rebuild(ServerLevel level, long seed, int cx, int cz, int side) {
        LevelLayout layout = BackroomsLevel.LEVEL_369.layout();
        for (int along = 1; along < Level369Plan.CELL; along++) {
            BlockPos base = column(cx, cz, side, along, 0);
            level.getChunk(base.getX() >> 4, base.getZ() >> 4); // load it if it isn't
            BlockState[] want = new BlockState[Level369Plan.HIGHEST_Y + 1];
            layout.fillColumn(seed, base.getX(), base.getZ(), (y, state) -> {
                if (y < want.length) {
                    want[y] = state;
                }
            });
            for (int y = LevelLayout.FLOOR_Y; y <= Level369Plan.DOOR_TOP; y++) {
                level.setBlock(base.atY(y), want[y] == null ? Blocks.AIR.defaultBlockState() : want[y], 2);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Your things
    // ------------------------------------------------------------------------------------------

    private static void loseSomething(ServerPlayer player, ServerLevel level, long seed, Visit visit, RandomSource random) {
        Inventory inventory = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && slot != inventory.selected && !stack.is(ModItems.CAMCORDER.get())) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        BlockPos spot = dropSpot(player, level, seed, random);
        if (spot == null) {
            return;
        }
        int slot = slots.get(random.nextInt(slots.size()));
        ItemStack stack = inventory.removeItemNoUpdate(slot);
        ItemEntity item = new ItemEntity(level, spot.getX() + 0.5, spot.getY() + 0.05, spot.getZ() + 0.5, stack.copy(), 0, 0, 0);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(20);
        item.getPersistentData().putUUID(OWNER_TAG, player.getUUID());
        visit.lost.put(item.getUUID(), stack.copy()); // before it joins the level (see onJoin)
        level.addFreshEntity(item);
        level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.GRASS_STEP, SoundSource.PLAYERS, 0.15f, 1.3f);
        inventory.setChanged();
    }

    /** A spot of open lawn in the player's own room, a few blocks away, behind them; or null. */
    @Nullable
    private static BlockPos dropSpot(ServerPlayer player, ServerLevel level, long seed, RandomSource random) {
        LevelLayout layout = BackroomsLevel.LEVEL_369.layout();
        int cx = Math.floorDiv(player.getBlockX(), Level369Plan.CELL);
        int cz = Math.floorDiv(player.getBlockZ(), Level369Plan.CELL);
        double back = Math.toRadians(player.getYRot() + 180.0);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = back + (random.nextDouble() - 0.5) * Math.toRadians(200.0);
            double dist = DROP_MIN + random.nextDouble() * (DROP_MAX - DROP_MIN);
            int x = Mth.floor(player.getX() - Math.sin(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.cos(angle) * dist);
            BlockPos pos = new BlockPos(x, LevelLayout.FLOOR_Y + 1, z);
            if (Math.floorDiv(x, Level369Plan.CELL) == cx && Math.floorDiv(z, Level369Plan.CELL) == cz
                    && layout.isWalkable(seed, x, z) && !level.getBlockState(pos).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        ItemEntity item = event.getItemEntity();
        if (!item.getPersistentData().hasUUID(OWNER_TAG) || !event.getCurrentStack().isEmpty()
                || !(item.level() instanceof ServerLevel level)) {
            return;
        }
        Visit visit = data(level).visits.get(item.getPersistentData().getUUID(OWNER_TAG));
        if (visit != null && visit.lost.remove(item.getUUID()) != null) {
            data(level).setDirty(); // found again
        }
    }

    /** A lost thing whose owner already had it back (it lay in an unloaded chunk then): gone. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !item.getPersistentData().hasUUID(OWNER_TAG)
                || !(event.getLevel() instanceof ServerLevel level)
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_369) {
            return;
        }
        Visit visit = data(level).visits.get(item.getPersistentData().getUUID(OWNER_TAG));
        if (visit == null || !visit.lost.containsKey(item.getUUID())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Leaving: everything comes back
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getFrom() == BackroomsLevel.LEVEL_369.dimension()) {
            remember(player);
        } else if (event.getTo() == BackroomsLevel.LEVEL_369.dimension()) {
            sendState(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_369) {
                sendState(player);
            } else {
                remember(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_369) {
            sendState(player);
        }
    }

    private static void sendState(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Visit visit = data(level).visits.get(player.getUUID());
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_369);
        PacketDistributor.sendToPlayer(player, new Level369StatePayload(seed, visit == null ? 0.0f : forgotten(visit.ticks)));
    }

    /**
     * {@code player} has left Level 369: give back what they lost, put the doorways back as the level
     * made them, and start the next visit from nothing.
     */
    private static void remember(ServerPlayer player) {
        LAST_CELL.remove(player.getUUID());
        ServerLevel level = player.server.getLevel(BackroomsLevel.LEVEL_369.dimension());
        if (level == null) {
            return;
        }
        Data data = data(level);
        Visit visit = data.visits.remove(player.getUUID());
        if (visit == null) {
            return;
        }
        data.setDirty();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_369);
        for (int[] c : visit.changed) {
            rebuild(level, seed, c[0], c[1], c[2]);
        }
        for (Map.Entry<UUID, ItemStack> lost : visit.lost.entrySet()) {
            var entity = level.getEntity(lost.getKey());
            if (entity != null) {
                entity.discard();
            }
            // (If it lies in an unloaded chunk, onJoin removes it when that chunk loads.)
            ItemStack stack = lost.getValue().copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Saved state
    // ------------------------------------------------------------------------------------------

    /** One player's visit: time spent, doorways remembered and changed, things lost. */
    private static final class Visit {
        int ticks;
        /** {cell x, cell z, side, offset}: plain doorways crossed lately, oldest first. */
        final List<int[]> recent = new ArrayList<>();
        /** {cell x, cell z, side, old offset, new offset}: doorways moved. */
        final List<int[]> changed = new ArrayList<>();
        /** Lost things lying in the grass: item entity UUID -> what it held. */
        final Map<UUID, ItemStack> lost = new HashMap<>();

        boolean hasEdge(int cx, int cz, int side) {
            for (int[] c : changed) {
                if (c[0] == cx && c[1] == cz && c[2] == side) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "backrooms_level369_visits");
    }

    private static final class Data extends SavedData {
        final Map<UUID, Visit> visits = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (Tag t : tag.getList("visits", Tag.TAG_COMPOUND)) {
                CompoundTag v = (CompoundTag) t;
                Visit visit = new Visit();
                visit.ticks = v.getInt("ticks");
                for (Tag r : v.getList("recent", Tag.TAG_INT_ARRAY)) {
                    visit.recent.add(((net.minecraft.nbt.IntArrayTag) r).getAsIntArray());
                }
                for (Tag c : v.getList("changed", Tag.TAG_INT_ARRAY)) {
                    visit.changed.add(((net.minecraft.nbt.IntArrayTag) c).getAsIntArray());
                }
                for (Tag l : v.getList("lost", Tag.TAG_COMPOUND)) {
                    CompoundTag lost = (CompoundTag) l;
                    ItemStack stack = ItemStack.parseOptional(registries, lost.getCompound("stack"));
                    if (!stack.isEmpty()) {
                        visit.lost.put(lost.getUUID("entity"), stack);
                    }
                }
                data.visits.put(v.getUUID("player"), visit);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Visit> e : visits.entrySet()) {
                Visit visit = e.getValue();
                CompoundTag v = new CompoundTag();
                v.putUUID("player", e.getKey());
                v.putInt("ticks", visit.ticks);
                ListTag recent = new ListTag();
                visit.recent.forEach(r -> recent.add(new net.minecraft.nbt.IntArrayTag(r)));
                v.put("recent", recent);
                ListTag changed = new ListTag();
                visit.changed.forEach(c -> changed.add(new net.minecraft.nbt.IntArrayTag(c)));
                v.put("changed", changed);
                ListTag lost = new ListTag();
                for (Map.Entry<UUID, ItemStack> l : visit.lost.entrySet()) {
                    CompoundTag c = new CompoundTag();
                    c.putUUID("entity", l.getKey());
                    c.put("stack", l.getValue().save(registries));
                    lost.add(c);
                }
                v.put("lost", lost);
                list.add(v);
            }
            tag.put("visits", list);
            return tag;
        }
    }
}
