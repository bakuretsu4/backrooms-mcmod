package com.rickr.backrooms.world.anomaly.level94;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan.DoorSpot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * {@code level_94/wandering_door}: in the M.E.G.'s log
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94) the team split up in
 * the castle and each of them found "a door that appears at random", each in a different place,
 * yet they all came out of it together inside the throne room.
 *
 * <p>Here: one plain house door in a free-standing white frame stands somewhere on the plateau
 * or in the castle (one of {@link Level94AnomalyPlan#doorSpots}). Once it has gone unwatched for
 * a while it moves to another spot that nobody is looking at either, with the soft clunk of a
 * door closing somewhere out of sight. Open it and step through ({@code WanderingDoorBlock}),
 * and you come out on the throne room carpet; everyone who uses it arrives at the same place.
 *
 * <p>The door's position is the world itself (nothing is saved): each second the spots are
 * checked for the door. A duplicate (say, a chunk generated late with the door in its first spot)
 * is cleared when unwatched; a missing door is put back. Spots must be empty, floored and free of
 * anyone before the door appears there, so it never shuts anybody in.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class WanderingDoorBehavior implements AnomalyBehavior {
    /** The door must have been out of everybody's sight this long before it moves... */
    private static final long UNSEEN_TICKS = 200;
    /** ...and must have stood in its spot at least this long. */
    private static final long STAY_TICKS = 600;
    private static final double WATCH_DISTANCE = 128.0;
    /** Nobody may stand this close to the old or the new spot when it moves. */
    private static final double KEEP_AWAY = 5.0;

    /** Players who walked into the open doorway this tick; they go on their next tick. */
    private static final Set<UUID> THROUGH = ConcurrentHashMap.newKeySet();

    /** Per door (by site salt): when somebody last saw it and when it last moved (game time). */
    private static final Map<Long, long[]> TIMES = new ConcurrentHashMap<>();

    /** Called by the door block when a player walks into its open doorway. */
    public static void steppedThrough(ServerPlayer player) {
        THROUGH.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !THROUGH.remove(player.getUUID())) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_94) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_94);
        double[] to = Level94AnomalyPlan.throneArrival(seed, player.getBlockX(), player.getBlockZ());
        Teleports.to(player, to[0], to[1], to[2], (float) to[3], 0.0f);
        level.playSound(null, to[0], to[1], to[2], SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.85f);
        player.displayClientMessage(Component.translatable("message.backrooms.l94.door_through").withStyle(s -> s.withItalic(true)), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        THROUGH.remove(event.getEntity().getUUID());
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        List<DoorSpot> spots = Level94AnomalyPlan.doorSpots(seed, site);
        for (DoorSpot spot : spots) {
            if (!level.isLoaded(spot.part(-1, 0)) || !level.isLoaded(spot.part(1, 0))) {
                return; // judge only with every spot in view of the server
            }
        }
        long now = level.getGameTime();
        long[] times = TIMES.computeIfAbsent(site.salt(), k -> new long[] {now, now});
        if (TIMES.size() > 256) {
            // A long session visits many doors: forget the ones nobody has been near for 5 minutes.
            // (A forgotten door just starts over as "seen now", so it waits a little before moving.)
            TIMES.values().removeIf(t -> now - Math.max(t[0], t[1]) > 6000);
        }
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            if (hasDoor(level, spots.get(i))) {
                found.add(i);
            }
        }
        RandomSource random = level.getRandom();
        if (found.isEmpty()) {
            // Gone (or never generated): it turns up again somewhere nobody is looking.
            DoorSpot first = spots.get(Level94AnomalyPlan.firstDoorSpot(site));
            if ((!watched(level, first) && tryPlace(level, first)) || placeSomewhere(level, spots, -1, random)) {
                times[1] = now;
            }
            return;
        }
        // Only ever one door: clear any extra that nobody is watching.
        for (int k = 1; k < found.size(); k++) {
            DoorSpot extra = spots.get(found.get(k));
            if (!watched(level, extra)) {
                remove(level, extra);
            }
        }
        int current = found.get(0);
        DoorSpot here = spots.get(current);
        if (watched(level, here)) {
            times[0] = now;
            return;
        }
        if (now - times[0] < UNSEEN_TICKS || now - times[1] < STAY_TICKS || random.nextInt(4) != 0) {
            return;
        }
        remove(level, here);
        if (placeSomewhere(level, spots, current, random)) {
            times[1] = now;
        } else {
            tryPlace(level, here); // nowhere else free right now: it stays
        }
    }

    /** Puts the door in a random free, unwatched spot other than {@code except}; true if it found one. */
    private static boolean placeSomewhere(ServerLevel level, List<DoorSpot> spots, int except, RandomSource random) {
        int start = random.nextInt(spots.size());
        for (int k = 0; k < spots.size(); k++) {
            int i = (start + k) % spots.size();
            if (i == except) {
                continue;
            }
            DoorSpot spot = spots.get(i);
            if (!watched(level, spot) && tryPlace(level, spot)) {
                // A door closing somewhere out of sight.
                level.playSound(null, spot.feet(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.35f, 0.8f);
                return true;
            }
        }
        return false;
    }

    private static boolean hasDoor(ServerLevel level, DoorSpot spot) {
        BlockState state = level.getBlockState(spot.feet());
        return state.is(ModBlocks.L94_WANDERING_DOOR.get()) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** Somebody could see the doorway, or is standing right by it. */
    private static boolean watched(ServerLevel level, DoorSpot spot) {
        Vec3 centre = spot.centre();
        if (Observation.seenByAnyone(level, centre, WATCH_DISTANCE)) {
            return true;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(centre) < KEEP_AWAY * KEEP_AWAY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the frame and the closed door at {@code spot} if every cell it needs is empty air on
     * a solid floor, the cells in front of and behind the doorway are open, and nobody is in the
     * way. Returns false (changing nothing) otherwise.
     */
    private static boolean tryPlace(ServerLevel level, DoorSpot spot) {
        for (int along = -1; along <= 1; along++) {
            BlockPos floor = spot.part(along, -1);
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                return false;
            }
            for (int up = 0; up <= 2; up++) {
                if (!level.getBlockState(spot.part(along, up)).isAir()) {
                    return false;
                }
            }
        }
        for (Direction side : new Direction[] {spot.facing(), spot.facing().getOpposite()}) {
            for (int up = 0; up <= 1; up++) {
                if (!level.getBlockState(spot.feet().relative(side).above(up)).isAir()) {
                    return false;
                }
            }
        }
        AABB frame = new AABB(spot.part(-1, 0).getCenter(), spot.part(1, 2).getCenter()).inflate(0.75);
        if (!level.getEntitiesOfClass(Entity.class, frame).isEmpty()) {
            return false;
        }
        // Lower door half before the upper one, so the pair joins up.
        for (int up = 0; up <= 2; up++) {
            for (int along = -1; along <= 1; along++) {
                BlockState state = Level94AnomalyPlan.doorPart(along, up, spot.facing());
                if (state != null) {
                    level.setBlock(spot.part(along, up), state, Block.UPDATE_ALL);
                }
            }
        }
        return true;
    }

    /** Takes the door and its frame away (only our own blocks: trim and the door). */
    private static void remove(ServerLevel level, DoorSpot spot) {
        for (int up = 2; up >= 0; up--) {
            for (int along = -1; along <= 1; along++) {
                BlockPos pos = spot.part(along, up);
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.L94_TRIM.get()) || state.is(ModBlocks.L94_WANDERING_DOOR.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
