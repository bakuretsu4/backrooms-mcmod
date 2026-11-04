package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.ClayDoors;
import com.rickr.backrooms.world.Level7Rules;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.layout.Level228Plan;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level39Tunnels;
import com.rickr.backrooms.world.layout.Level490Spire;
import com.rickr.backrooms.world.layout.Level9Layout;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The exit triggers themselves, in a real (throwaway, in-process) server with the mod loaded and
 * mock players: falling below transferBelowY, the Poolrooms' exit hall, Level 9's underpass, Level 7's sea door, Level 94's
 * dark door, Level 490's leap, Level 2's bulkhead taking the group within 6 blocks, and the side
 * ways back (clay doors, Level 39's sunlit dead ends). Each move must end on the next level at
 * exactly the spot the arrival code promises (so friends using the same exit land together), and
 * a dead or disconnected player must never hold anyone else up.
 *
 * <p>Player ticks are driven by posting the same PlayerTickEvent.Post the game posts every tick,
 * so every rule the mod hangs on it (ServerEvents, Level7Rules, Level9Rules, Level94Rules...) runs.
 */
class ExitTriggerServerTest {
    /** Ticks a move takes: the datamosh plus the tick that makes it. */
    private static final int MOVE_TICKS = Travel.MOSH_TICKS + 2;

    private final List<ServerPlayer> players = new ArrayList<>();
    private final MinecraftServer server = BackroomsTestServer.get();

    @AfterEach
    void logOutEveryone() throws Exception {
        onServer(() -> {
            for (ServerPlayer p : players) {
                if (server.getPlayerList().getPlayer(p.getUUID()) != null) {
                    server.getPlayerList().remove(p);
                }
            }
            return null;
        });
        players.clear();
    }

    // ------------------------------------------------------------------------------------------
    // Falling below transferBelowY: Levels 0, 1, 4 and 37
    // ------------------------------------------------------------------------------------------

    @Test
    void fallingBelowTheTriggerTakesYouOn() throws Exception {
        for (BackroomsLevel from : new BackroomsLevel[] {BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_4,
                BackroomsLevel.LEVEL_37}) {
            onServer(() -> {
                int[] exit = exitNear(from, 0, 0);
                ServerPlayer p = player("faller", from, exit[0] + 0.5, from.transferBelowY() - 1.0, exit[1] + 0.5);
                tick(p);
                assertTrue(Travel.isMoving(p), from + ": being below y" + from.transferBelowY() + " didn't start the exit");
                tickUntilMoved(p, from);
                assertArrivedAsPromised(p, from, from.next(), exit);
                return null;
            });
        }
    }

    @Test
    void standingAboveTheTriggerDoesNothing() throws Exception {
        onServer(() -> {
            int[] exit = exitNear(BackroomsLevel.LEVEL_0, 0, 0);
            ServerPlayer p = player("walker", BackroomsLevel.LEVEL_0, exit[0] + 0.5, BackroomsLevel.LEVEL_0.transferBelowY() + 0.5, exit[1] + 0.5);
            for (int i = 0; i < MOVE_TICKS; i++) {
                tick(p);
            }
            assertFalse(Travel.isMoving(p));
            assertEquals(BackroomsLevel.LEVEL_0.dimension(), p.level().dimension());
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------
    // The Poolrooms: the daylit niche at the top of the exit hall's stair
    // ------------------------------------------------------------------------------------------

    @Test
    void poolroomsHallNicheTakesYouOn() throws Exception {
        onServer(() -> {
            BackroomsLevel from = BackroomsLevel.POOLROOMS;
            long seed = BackroomsSeeds.layoutSeed(level(from), from);
            PoolroomsLayout layout = (PoolroomsLayout) from.layout();
            int[] niche = layout.exitPosition(seed, 0, 0);
            int feet = layout.exitY(seed, niche[0], niche[1]);
            assertTrue(layout.isHallExit(seed, niche[0], feet, niche[1]), "exitPosition/exitY is not the hall's way out");
            assertFalse(layout.isHallExit(seed, niche[0], feet - 1, niche[1]), "the niche's floor counts as the way out");
            ServerPlayer p = player("climber", from, niche[0] + 0.5, feet, niche[1] + 0.5);
            tick(p);
            assertTrue(Travel.isMoving(p), "stepping into the exit hall's daylit niche didn't start the exit");
            tickUntilMoved(p, from);
            assertArrivedAsPromised(p, from, from.next(), niche);
            return null;
        });
    }

    /** The hall's pool lies at the bottom of the world: swimming down in it takes you nowhere. */
    @Test
    void theExitHallsPoolIsNoWayOut() throws Exception {
        onServer(() -> {
            BackroomsLevel from = BackroomsLevel.POOLROOMS;
            long seed = BackroomsSeeds.layoutSeed(level(from), from);
            int[] centre = from.layout().exits().nearest(seed, 0, 0);
            ServerPlayer p = player("swimmer", from, centre[0] + 0.5, 3.0, centre[1] + 0.5);
            for (int i = 0; i < MOVE_TICKS; i++) {
                tick(p);
            }
            assertFalse(Travel.isMoving(p), "the bottom of the exit hall's pool started an exit");
            assertEquals(from.dimension(), p.level().dimension());
            return null;
        });
    }

    /** Two friends walking down the same stairwell come out at the same spot. */
    @Test
    void friendsUsingTheSameStairsLandTogether() throws Exception {
        onServer(() -> {
            int[] exit = exitNear(BackroomsLevel.LEVEL_0, 3000, -2000);
            ServerPlayer a = player("friend_a", BackroomsLevel.LEVEL_0, exit[0] + 2.5, 20.0, exit[1] - 2.5);
            ServerPlayer b = player("friend_b", BackroomsLevel.LEVEL_0, exit[0] - 2.5, 25.0, exit[1] + 3.5);
            for (int i = 0; i < MOVE_TICKS + 1; i++) {
                tick(a);
                tick(b);
            }
            assertEquals(BackroomsLevel.LEVEL_1.dimension(), a.level().dimension());
            assertEquals(BackroomsLevel.LEVEL_1.dimension(), b.level().dimension());
            assertEquals(a.position(), b.position(), "friends using the same exit landed apart");
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------
    // Level 9's underpass, Level 7's sea door, Level 94's dark door, Level 490's leap
    // ------------------------------------------------------------------------------------------

    @Test
    void level9UnderpassLeadsToThePoolrooms() throws Exception {
        onServer(() -> {
            ServerLevel level = level(BackroomsLevel.LEVEL_9);
            long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_9);
            Level9Layout layout = (Level9Layout) BackroomsLevel.LEVEL_9.layout();
            int[] exit = layout.exitPosition(seed, 0, 0);
            int[] zone = null;
            for (int r = 0; r <= 24 && zone == null; r++) {
                for (int dx = -r; dx <= r && zone == null; dx++) {
                    for (int dz = -r; dz <= r && zone == null; dz++) {
                        if (layout.isEscapeZone(seed, exit[0] + dx, exit[1] + dz)) {
                            zone = new int[] {exit[0] + dx, exit[1] + dz};
                        }
                    }
                }
            }
            assertNotNull(zone, "no underpass zone within 24 blocks of Level 9's exit position " + exit[0] + " " + exit[1]);
            double feet = layout.feetY(seed, zone[0], zone[1]);
            assertTrue(feet < 47, "the underpass road (feet y" + feet + ") is not below Level9Rules' y47 trigger");
            ServerPlayer p = player("underpass", BackroomsLevel.LEVEL_9, zone[0] + 0.5, feet, zone[1] + 0.5);
            tick(p);
            assertTrue(Travel.isMoving(p), "walking into the underpass didn't start the exit");
            tickUntilMoved(p, BackroomsLevel.LEVEL_9);
            assertArrivedAsPromised(p, BackroomsLevel.LEVEL_9, BackroomsLevel.POOLROOMS, zone);
            return null;
        });
    }

    @Test
    void level7SeaDoorLeadsToLevel9() throws Exception {
        onServer(() -> {
            int[] door = exitNear(BackroomsLevel.LEVEL_7, 0, 0);
            ServerPlayer p = player("diver", BackroomsLevel.LEVEL_7, door[0] + 0.5, 52.0, door[1] + 0.5);
            Level7Rules.throughExitDoor(p); // what SeaDoorBlock.entityInside does
            tick(p);
            assertTrue(Travel.isMoving(p), "the sea door didn't start the exit");
            tickUntilMoved(p, BackroomsLevel.LEVEL_7);
            assertArrivedAsPromised(p, BackroomsLevel.LEVEL_7, BackroomsLevel.LEVEL_9, door);
            return null;
        });
    }

    /** The dark door goes to next() = Level 228, stepping out of a mirror (Level228Layout.preferredArrival). */
    @Test
    void level94DarkDoorLeadsOn() throws Exception {
        onServer(() -> {
            ServerLevel level = level(BackroomsLevel.LEVEL_94);
            int[] spot = exitNear(BackroomsLevel.LEVEL_94, 0, 0);
            BlockPos feet = new BlockPos(spot[0], 120, spot[1]);
            level.setBlockAndUpdate(feet, ModBlocks.L94_DARK_VOID.get().defaultBlockState());
            ServerPlayer p = player("dark_door", BackroomsLevel.LEVEL_94, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            tick(p);
            assertTrue(Travel.isMoving(p), "standing in l94_dark_void didn't start the exit");
            tickUntilMoved(p, BackroomsLevel.LEVEL_94);
            assertArrivedAsPromised(p, BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_228, spot);
            return null;
        });
    }

    /**
     * Level 228's final chamber, as generated, walked into with real collision (ServerPlayer.move
     * against the generated panes, the way a player's own steps go): the accurate mirror lets you
     * out into the exit hallway (stepping out of its entry mirror), each wrong one quietly puts only
     * you back at the Glass Labyrinth's checkpoint, which is walkable and in that zone.
     */
    @Test
    void level228ChamberMirrorsSendYouOnOrBack() throws Exception {
        onServer(() -> {
            BackroomsLevel from = BackroomsLevel.LEVEL_228;
            ServerLevel level = level(from);
            long seed = BackroomsSeeds.layoutSeed(level, from);
            int answer = Level228Plan.correctExitMirror(seed);
            for (int m = 0; m < 3; m++) {
                double z = 42.0 + 3 * m; // the middle of mirror m (A, B, C from north to south)
                ServerPlayer p = player("chamber_" + m, from, 12.5, Level228Plan.FEET_Y, z);
                p.teleportTo(level, 12.5, Level228Plan.FEET_Y, z, -90f, 0f); // facing east, at the mirrors
                Vec3 step = new Vec3(0.2, 0, 0);
                for (int i = 0; i < 60 && p.getX() < 16; i++) {
                    p.move(MoverType.SELF, step);
                    tick(p);
                    if (Math.abs(p.getX() - 12.5) > 20 || Math.abs(p.getZ() - z) > 3) {
                        break; // moved by the mirror
                    }
                }
                double[] to = m == answer ? Level228Plan.hallEntry(seed) : Level228Plan.checkpoint(seed);
                assertEquals(from.dimension(), p.level().dimension(), "a chamber mirror left the level");
                assertTrue(p.position().distanceTo(new Vec3(to[0], to[1], to[2])) < 1e-6,
                        "mirror " + m + (m == answer ? " (accurate)" : " (wrong)") + " put you at " + p.position());
                assertFalse(Travel.isMoving(p), "a chamber mirror started a level exit");
            }
            return null;
        });
    }

    /**
     * Level 228's exit hallway door: someone alone who enters the vestibule goes on to Level 369
     * at once; with a teammate still in the maze the ready player waits, and both go together the
     * moment the teammate arrives too.
     */
    @Test
    void level228VestibuleTakesTheGroupOnTogether() throws Exception {
        onServer(() -> {
            BackroomsLevel from = BackroomsLevel.LEVEL_228;
            ServerLevel level = level(from);
            long seed = BackroomsSeeds.layoutSeed(level, from);
            int[] door = Level228Plan.exitDoor(seed);
            double vx = door[0] + 0.0;
            double vz = door[1] + 2.5;
            assertTrue(Level228Plan.inVestibule(seed, vx, vz));
            ServerPlayer solo = player("solo", from, vx, Level228Plan.FEET_Y, vz);
            holdTicks(level);
            assertTrue(Travel.isMoving(solo), "a solo player in the vestibule didn't go on");
            int[] used = {solo.getBlockX(), solo.getBlockZ()};
            tickUntilMoved(solo, from);
            assertArrivedAsPromised(solo, from, BackroomsLevel.LEVEL_369, used);

            double[] foyer = Level228Plan.arrival(seed);
            ServerPlayer first = player("first", from, vx, Level228Plan.FEET_Y, vz);
            ServerPlayer late = player("late", from, foyer[0], foyer[1], foyer[2]);
            for (int i = 0; i < 40; i++) {
                levelTick(level);
            }
            assertFalse(Travel.isMoving(first), "the ready player left without the teammate");
            late.teleportTo(level, vx - 1, Level228Plan.FEET_Y, vz, 0f, 0f);
            holdTicks(level);
            assertTrue(Travel.isMoving(first) && Travel.isMoving(late), "the group didn't go on together");
            return null;
        });
    }

    /**
     * The in-game sequence: A waits in the vestibule; B walks into the accurate chamber mirror and
     * comes out at the hallway entry, 34 blocks from the door: nobody moves (for two seconds of
     * ticks, players and level both ticking). B walks into the vestibule: both go in the same
     * tick. And with A waiting, B logging out lets A go.
     */
    @Test
    void level228GroupWaitsForTheTeammateWalkingTheHallway() throws Exception {
        onServer(() -> {
            BackroomsLevel from = BackroomsLevel.LEVEL_228;
            ServerLevel level = level(from);
            long seed = BackroomsSeeds.layoutSeed(level, from);
            int[] door = Level228Plan.exitDoor(seed);
            double vx = door[0];
            double vz = door[1] + 2.5;
            ServerPlayer a = player("a_ready", from, vx, Level228Plan.FEET_Y, vz);
            int answer = Level228Plan.correctExitMirror(seed);
            double mz = 42.0 + 3 * answer;
            ServerPlayer b = player("b_walking", from, 12.5, Level228Plan.FEET_Y, mz);
            b.teleportTo(level, 12.5, Level228Plan.FEET_Y, mz, -90f, 0f);
            for (int i = 0; i < 60 && b.getX() < 100; i++) {
                b.move(MoverType.SELF, new Vec3(0.2, 0, 0));
                tick(b);
                tick(a);
                levelTick(level);
            }
            double[] hall = Level228Plan.hallEntry(seed);
            assertTrue(b.position().distanceTo(new Vec3(hall[0], hall[1], hall[2])) < 1e-6, "B not at the hallway entry: " + b.position());
            for (int i = 0; i < 40; i++) {
                b.move(MoverType.SELF, new Vec3(0, 0, 0.1)); // strolling down the hallway
                tick(b);
                tick(a);
                levelTick(level);
                assertFalse(Travel.isMoving(a) || Travel.isMoving(b),
                        "the group left while B was still in the hallway at " + b.position() + " (tick " + i + ")");
            }
            b.teleportTo(level, vx - 1, Level228Plan.FEET_Y, vz, 0f, 0f);
            holdTicks(level);
            assertTrue(Travel.isMoving(a) && Travel.isMoving(b), "the group didn't go on together");

            // A waits, B disconnects in the maze: A goes.
            ServerPlayer c = player("c_ready", from, vx, Level228Plan.FEET_Y, vz);
            ServerPlayer d = player("d_leaves", from, 9.5, Level228Plan.FEET_Y, 45.0);
            for (int i = 0; i < 5; i++) {
                levelTick(level);
            }
            assertFalse(Travel.isMoving(c), "C left without D");
            server.getPlayerList().remove(d);
            players.remove(d);
            holdTicks(level);
            assertTrue(Travel.isMoving(c), "D logged out and C still waits");
            return null;
        });
    }

    /** The group exit holds until everyone has been ready this many level ticks. */
    private static void holdTicks(ServerLevel level) {
        for (int i = 0; i < com.rickr.backrooms.world.Level228Rules.GroupGate.READY_HOLD; i++) {
            levelTick(level);
        }
    }

    private static void levelTick(ServerLevel level) {
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, level));
    }

    /** Minecraft's yaw for facing {@code d} (0 = south, 90 = west). */
    private static float yawOf(Direction d) {
        return d.toYRot();
    }

    /** The leap off the top of Level 490's spire is the last exit: back to reality, at once (the tape stops). */
    @Test
    void level490LeapEscapesToReality() throws Exception {
        onServer(() -> {
            ServerPlayer p = player("leaper", BackroomsLevel.LEVEL_490, 0.5, Level490Spire.LEAP_Y, 0.5);
            tick(p); // the fall starts up at the collar
            p.teleportTo(level(BackroomsLevel.LEVEL_490), 0.5, Level490Spire.LEAP_Y - 5.0, 0.5, 0f, 0f);
            tick(p);
            assertEquals(Level.OVERWORLD, p.level().dimension(), "the leap didn't take you back to reality");
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------
    // The side ways back: clay doors (to Level 9) and Level 39's sunlit dead ends (to the Poolrooms)
    // ------------------------------------------------------------------------------------------

    @Test
    void clayDoorsLeadWhereTheyShould() throws Exception {
        BackroomsLevel[][] doors = {
                {BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_94},
                {BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_9},
                {BackroomsLevel.LEVEL_39, BackroomsLevel.LEVEL_9}};
        for (BackroomsLevel[] door : doors) {
            onServer(() -> {
                ServerPlayer p = player("clay_" + door[0].getSerializedName(), door[0], 300.5, 80.0, -200.5);
                ClayDoors.steppedThrough(p); // what ClayDoorBlock.entityInside does
                tick(p);
                assertTrue(Travel.isMoving(p), door[0] + ": stepping through a clay door didn't start the move");
                tickUntilMoved(p, door[0]);
                assertLandedAsPromised(p, door[0], door[1], 300, -201);
                return null;
            });
        }
    }

    @Test
    void level39SunlitDeadEndLeadsBackToThePoolrooms() throws Exception {
        onServer(() -> {
            long seed = BackroomsSeeds.layoutSeed(level(BackroomsLevel.LEVEL_39), BackroomsLevel.LEVEL_39);
            BlockPos arch = null;
            for (int k = 0; k < 9 && arch == null; k++) {
                arch = Level39Tunnels.nearestSunlitEnd(seed, (k % 3 - 1) * Level39Layout.CELL, (k / 3 - 1) * Level39Layout.CELL);
            }
            assertNotNull(arch, "no sunlit dead end in nine towns of Level 39");
            ServerPlayer p = player("sunlit", BackroomsLevel.LEVEL_39, arch.getX() + 0.5, arch.getY(), arch.getZ() + 0.5);
            tick(p);
            assertTrue(Travel.isMoving(p), "walking into the sunlit arch didn't start the move");
            tickUntilMoved(p, BackroomsLevel.LEVEL_39);
            assertLandedAsPromised(p, BackroomsLevel.LEVEL_39, BackroomsLevel.POOLROOMS, arch.getX(), arch.getZ());
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------
    // Level 2's bulkhead: the group within 6 blocks, never held up by the dead or the departed
    // ------------------------------------------------------------------------------------------

    @Test
    void level2BulkheadTakesTheGroupAndNoOneBlocksIt() throws Exception {
        onServer(() -> {
            ServerLevel level = level(BackroomsLevel.LEVEL_2);
            long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_2);
            int[] exit = BackroomsLevel.LEVEL_2.layout().exitPosition(seed, 0, 0);
            BlockPos bulkhead = new BlockPos(exit[0], 41, exit[1]);
            level.setBlockAndUpdate(bulkhead, Level2Blocks.EXIT_BULKHEAD.get().defaultBlockState());
            double y = 41.0;
            ServerPlayer opener = player("opener", BackroomsLevel.LEVEL_2, exit[0] + 1.5, y, exit[1] + 0.5);
            ServerPlayer near = player("near", BackroomsLevel.LEVEL_2, exit[0] + 4.5, y, exit[1] + 0.5);
            ServerPlayer far = player("far", BackroomsLevel.LEVEL_2, exit[0] + 12.5, y, exit[1] + 0.5);
            ServerPlayer dead = player("dead", BackroomsLevel.LEVEL_2, exit[0] + 2.5, y, exit[1] + 1.5);
            ServerPlayer leaver = player("leaver", BackroomsLevel.LEVEL_2, exit[0] + 2.5, y, exit[1] - 1.5);
            dead.setHealth(0.0f);
            assertFalse(dead.isAlive());

            BlockState state = level.getBlockState(bulkhead);
            state.useWithoutItem(level, opener, new BlockHitResult(Vec3.atCenterOf(bulkhead), Direction.EAST, bulkhead, false));
            assertTrue(Travel.isMoving(opener) && Travel.isMoving(near), "the bulkhead didn't start the group's exit");
            assertFalse(Travel.isMoving(far), "a player 12 blocks away was taken along");

            server.getPlayerList().remove(leaver); // disconnects mid-move
            assertFalse(Travel.isMoving(leaver), "a player who left mid-move still has a pending move");

            for (int i = 0; i < MOVE_TICKS; i++) {
                tick(opener);
                tick(near);
                tick(far);
                tick(dead);
            }
            assertEquals(BackroomsLevel.LEVEL_4.dimension(), opener.level().dimension(), "the opener didn't leave");
            assertEquals(BackroomsLevel.LEVEL_4.dimension(), near.level().dimension(), "a player within 6 blocks was left behind");
            assertEquals(opener.position(), near.position(), "the group landed apart");
            assertEquals(BackroomsLevel.LEVEL_2.dimension(), far.level().dimension());
            assertEquals(BackroomsLevel.LEVEL_2.dimension(), dead.level().dimension(), "a dead player was moved");
            assertFalse(Travel.isMoving(dead), "a dead player kept a pending move");
            // Where the opener stood when they used it (exits().nearest is taken from there).
            assertArrivedAsPromised(opener, BackroomsLevel.LEVEL_2, BackroomsLevel.LEVEL_4, new int[] {exit[0] + 1, exit[1]});
            return null;
        });
    }

    /** A pending move of someone who disconnected never stops anyone else using the same exit. */
    @Test
    void aDisconnectMidMoveDoesNotBlockTheExit() throws Exception {
        onServer(() -> {
            int[] exit = exitNear(BackroomsLevel.LEVEL_1, -4000, 4000);
            ServerPlayer gone = player("gone", BackroomsLevel.LEVEL_1, exit[0] + 0.5, 20.0, exit[1] + 0.5);
            tick(gone);
            assertTrue(Travel.isMoving(gone));
            server.getPlayerList().remove(gone);
            ServerPlayer next = player("next", BackroomsLevel.LEVEL_1, exit[0] + 0.5, 20.0, exit[1] + 0.5);
            tick(next);
            assertTrue(Travel.isMoving(next));
            tickUntilMoved(next, BackroomsLevel.LEVEL_1);
            assertArrivedAsPromised(next, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_2, exit);
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private ServerLevel level(BackroomsLevel level) {
        ServerLevel serverLevel = server.getLevel(level.dimension());
        assertNotNull(serverLevel, level + " isn't loaded in the test server");
        return serverLevel;
    }

    /** The exit centre of {@code level} nearest (x, z), with the server's real layout seed. */
    private int[] exitNear(BackroomsLevel level, int x, int z) {
        long seed = BackroomsSeeds.layoutSeed(level(level), level);
        return level.layout().exitPosition(seed, x, z);
    }

    /** A logged-in mock player (as the vanilla gametest helper makes them), standing at (x, y, z) in {@code level}. */
    private ServerPlayer player(String name, BackroomsLevel level, double x, double y, double z) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // Act like a NeoForge client with every channel, so the mod's payloads (datamosh, arrival card) can be sent.
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        players.add(p);
        p.teleportTo(level(level), x, y, z, 0f, 0f);
        return p;
    }

    private static void tick(ServerPlayer p) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private static void tickUntilMoved(ServerPlayer p, BackroomsLevel from) {
        for (int i = 0; i < MOVE_TICKS && p.level().dimension() == from.dimension(); i++) {
            tick(p);
        }
        assertFalse(p.level().dimension() == from.dimension(), from + ": the exit started but the player never moved");
    }

    /** The player is on {@code next}, exactly where Travel's arrival code says someone from {@code from}'s exit lands. */
    private void assertArrivedAsPromised(ServerPlayer p, BackroomsLevel from, @Nullable BackroomsLevel next, int[] usedExit) {
        assertNotNull(next);
        assertEquals(next.dimension(), p.level().dimension(), from + " led somewhere else than next()");
        long fromSeed = BackroomsSeeds.layoutSeed(level(from), from);
        int[] centre = from.layout().exits().nearest(fromSeed, usedExit[0], usedExit[1]);
        long seed = BackroomsSeeds.layoutSeed(level(next), next);
        Escape.Arrival arrival = Escape.arrive(next, seed, centre[0], centre[1], from);
        Vec3 expected = new Vec3(arrival.x() + 0.5, arrival.feet(), arrival.z() + 0.5);
        assertTrue(p.position().distanceTo(expected) < 1e-6,
                from + " -> " + next + ": landed at " + p.position() + ", the arrival code says " + expected);
    }

    /** The player is on {@code target}, where Travel's arrival code lands someone sent there from (x, z) of {@code from}. */
    private void assertLandedAsPromised(ServerPlayer p, BackroomsLevel from, BackroomsLevel target, int x, int z) {
        assertEquals(target.dimension(), p.level().dimension(), from + " led somewhere else than " + target);
        Escape.Arrival arrival = Escape.arrive(target, BackroomsSeeds.layoutSeed(level(target), target), x, z, from);
        Vec3 expected = new Vec3(arrival.x() + 0.5, arrival.feet(), arrival.z() + 0.5);
        assertTrue(p.position().distanceTo(expected) < 1e-6,
                from + " -> " + target + ": landed at " + p.position() + ", the arrival code says " + expected);
    }

    private <T> T onServer(Supplier<T> task) throws Exception {
        try {
            return server.submit(task).get(5, TimeUnit.MINUTES);
        } catch (java.util.concurrent.ExecutionException e) {
            // Show the assertion that failed on the server thread, not the wrapper.
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
