package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalyService;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level0RedRooms;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Red Rooms (https://backrooms-wiki.wikidot.com/red-rooms): a Class 3 sub-area you enter "after
 * turning a corner", whose entrance seals behind you and which has no exit, just a small sequence
 * of rooms that keeps repeating. Floor plan: {@link Level0RedRooms}.
 * <ul>
 *   <li><b>The loop.</b> Walk through the hole in the empty room's wall and along the crawlway, and
 *       halfway along it you are moved (seamlessly, {@link Teleports#shift}) into the identical
 *       crawlway that ends in the start room. Every time you go round, that counts as a loop.</li>
 *   <li><b>The seal.</b> As soon as someone is inside and nobody is looking at the doorway they came
 *       through, it is bricked up with crimson wallpaper. When everybody has left (or died) it
 *       opens again, out of sight if possible.</li>
 *   <li><b>Paranoia.</b> Sanity drains slowly while you're in the crimson part.</li>
 *   <li><b>It keeps count.</b> Each time round, the hum drags low and wrong for a moment (only the
 *       one who looped hears it), and out of sight a few more crimson walls in the rooms get tally
 *       marks, three per loop: the place is counting you. They go away again once it's empty.</li>
 *   <li><b>The way out.</b> Canon says there is none, so we borrow Level 0's own exit, the
 *       flickering wall (https://backrooms-wiki.wikidot.com/level-0): once someone has gone round
 *       the loop three times, or has been inside for four minutes (nobody is trapped for good), a
 *       stretch of the tally room's wall starts to flicker. Walking into it takes you to Level 1.</li>
 * </ul>
 */
public final class RedRoomsBehavior implements AnomalyBehavior {
    private static final int LOOPS_BEFORE_EXIT = 3;
    private static final int TICKS_BEFORE_EXIT = 4 * 60 * 20;
    private static final float SANITY_DRAIN_PER_SECOND = 0.15f;
    /** After everybody left, the doorway reopens even if someone is watching once this many ticks have passed. */
    private static final long REOPEN_ANYWAY_TICKS = 20 * 20;
    private static final double WATCH_DISTANCE = 48.0;
    /** New tally marks per loop round. */
    private static final int TALLIES_PER_LOOP = 3;

    /** How long a player has been in the crimson part and how often they've gone round. */
    private static final class Visitor {
        int loops;
        int ticks;
    }

    private final Map<UUID, Visitor> visitors = new HashMap<>();
    /** Game time each site was last left empty while still sealed or flickering. */
    private final Map<AnomalySite, Long> emptySince = new HashMap<>();
    /** Where each site can show tally marks (built once per site, see Level0RedRooms#tallySpots). */
    private final Map<AnomalySite, List<Level0RedRooms.Tally>> tallies = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        double[] shift = Level0RedRooms.loopShift(site, player.getX(), player.getZ());
        if (shift != null) {
            Teleports.shift(player, shift[0], 0.0, shift[1]);
            if (shift[2] > 0) {
                visitors.computeIfAbsent(player.getUUID(), id -> new Visitor()).loops++;
                // Only you hear it: the klaxon's tail dragging low, the moment the crawlway brings you round.
                player.playNotifySound(ModSounds.L0_RED_LOOP.get(), SoundSource.AMBIENT, 0.7f, 1.0f);
            }
            return;
        }
        if (Level0RedRooms.isInterior(site, player.getBlockX(), player.getBlockZ())) {
            Visitor visitor = visitors.computeIfAbsent(player.getUUID(), id -> new Visitor());
            visitor.ticks++;
            if (visitor.ticks % 20 == 0 && !player.isCreative()) {
                SanityService.drain(player, SANITY_DRAIN_PER_SECOND);
            }
        }
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        visitors.remove(player.getUUID());
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        int floor = BackroomsLevel.LEVEL_0.layout().floorY();
        BlockPos[] seal = Level0RedRooms.sealCells(site, floor);
        BlockPos[] flicker = Level0RedRooms.flickerCells(site, floor);
        if (!loaded(level, seal) || !loaded(level, flicker)) {
            return;
        }
        boolean someoneInside = false;
        boolean someoneInDoorway = false;
        boolean letGo = false;
        int mostLoops = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !site.equals(AnomalyService.currentSite(player))) {
                continue;
            }
            int x = player.getBlockX();
            int z = player.getBlockZ();
            someoneInDoorway |= Level0RedRooms.isDoorway(site, x, z);
            if (Level0RedRooms.isInterior(site, x, z)) {
                someoneInside = true;
                Visitor visitor = visitors.get(player.getUUID());
                letGo |= visitor != null && (visitor.loops >= LOOPS_BEFORE_EXIT || visitor.ticks >= TICKS_BEFORE_EXIT);
                mostLoops = Math.max(mostLoops, visitor == null ? 0 : visitor.loops);
            }
        }
        countLoops(level, site, floor, someoneInside ? mostLoops : 0);
        BlockState wall = ModBlocks.RED_WALLPAPER.get().defaultBlockState();
        boolean sealed = level.getBlockState(seal[0]).is(ModBlocks.RED_WALLPAPER.get());
        boolean flickering = level.getBlockState(flicker[0]).is(ModBlocks.FLICKERING_WALL.get());
        if (someoneInside) {
            emptySince.remove(site);
            if (!sealed && !someoneInDoorway && !seen(level, seal) && empty(level, seal)) {
                setAll(level, seal, wall); // the way you came in is gone
            }
            if (letGo && !flickering) {
                setAll(level, flicker, ModBlocks.FLICKERING_WALL.get().defaultBlockState());
                level.playSound(null, flicker[1], ModSounds.LIGHT_BLINK.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
            }
            return;
        }
        if (!sealed && !flickering) {
            emptySince.remove(site);
            return;
        }
        long now = level.getGameTime();
        boolean anyway = now - emptySince.computeIfAbsent(site, s -> now) >= REOPEN_ANYWAY_TICKS;
        if (sealed && (anyway || !seen(level, seal))) {
            setAll(level, seal, Blocks.AIR.defaultBlockState());
        }
        if (flickering && (anyway || !seen(level, flicker))) {
            setAll(level, flicker, wall);
        }
    }

    /**
     * Shows {@code loops} x TALLIES_PER_LOOP tally marks on the rooms' walls (the rest plain),
     * changing a wall only while nobody looks at it. Stateless: it just compares the blocks with
     * what they should be, so it also tidies up after a restart.
     */
    private void countLoops(ServerLevel level, AnomalySite site, int floor, int loops) {
        if (tallies.size() > 16 && !tallies.containsKey(site)) {
            tallies.clear();
        }
        List<Level0RedRooms.Tally> spots = tallies.computeIfAbsent(site, s -> Level0RedRooms.tallySpots(s, floor));
        int want = Math.min(spots.size(), loops * TALLIES_PER_LOOP);
        for (int k = 0; k < spots.size(); k++) {
            Level0RedRooms.Tally spot = spots.get(k);
            if (!level.isLoaded(spot.pos())) {
                continue;
            }
            boolean marked = level.getBlockState(spot.pos()).is(ModBlocks.RED_WALL_TALLY.get());
            if (marked == k < want || Observation.seenByAnyone(level, Vec3.atCenterOf(spot.pos()), WATCH_DISTANCE)) {
                continue;
            }
            level.setBlock(spot.pos(), k < want ? spot.marked() : spot.original(), Block.UPDATE_ALL);
        }
    }

    private static boolean loaded(ServerLevel level, BlockPos[] cells) {
        for (BlockPos pos : cells) {
            if (!level.isLoaded(pos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean seen(ServerLevel level, BlockPos[] cells) {
        for (BlockPos pos : cells) {
            if (Observation.seenByAnyone(level, Vec3.atCenterOf(pos), WATCH_DISTANCE)) {
                return true;
            }
        }
        return false;
    }

    /** True if no entity (player, monster, dropped item) is in the cells: we never brick anyone in. */
    private static boolean empty(ServerLevel level, BlockPos[] cells) {
        for (BlockPos pos : cells) {
            if (!level.getEntities((Entity) null, new AABB(pos)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void setAll(ServerLevel level, BlockPos[] cells, BlockState state) {
        for (BlockPos pos : cells) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }
}
