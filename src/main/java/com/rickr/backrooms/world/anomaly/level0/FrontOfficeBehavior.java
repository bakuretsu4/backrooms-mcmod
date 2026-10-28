package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.Level0LightBlocks;
import com.rickr.backrooms.registry.Level0SetPieceBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level0AnomalyPlan;
import com.rickr.backrooms.world.layout.Level0FrontOffice;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The Front Office on the server (floor plan: {@link Level0FrontOffice}).
 *
 * <ul>
 *   <li><b>The PA calls you.</b> When Level 0's intercom event plays ({@code LevelEvents}), a
 *       player within {@value #CALL_RANGE} blocks of a front office often hears "...please come to
 *       the front office..." instead of the usual announcement ({@link #intercomLine}). They are
 *       now expected there for {@value #CALL_MINUTES} minutes.</li>
 *   <li><b>The door is sometimes real.</b> The first time in a visit that you come within
 *       {@value #NEAR_DOOR} blocks of the drawn door, the office decides: if you were called, the
 *       door is real; if not, it is real only during some quarter-hours of the world clock (about
 *       one in five). While it is real the beacon stops turning (and the client stops the alarm),
 *       a thin line of light shows under the door, and it stays real as long as someone stands
 *       near it, plus a little while.</li>
 *   <li><b>Walking into it</b> (standing right against it, facing it, for a third of a second)
 *       takes you through, into the hidden room above. In there, both drawn doors always work:
 *       the one you came by takes you back to the rug, the one under the EXIT sign to the lobby of
 *       the nearest exit stairwell. Sitting in the room calms you, faster than the Manila Room.</li>
 * </ul>
 * The blocks it changes (beacon lit/unlit, the light under the door) are re-checked every second
 * by {@link #siteTick}, so a restart or an unloaded chunk can never leave them in the wrong state.
 */
public final class FrontOfficeBehavior implements AnomalyBehavior {
    private static final int CALL_RANGE = 260;
    private static final double CALL_CHANCE = 0.55;
    private static final int CALL_MINUTES = 10;
    private static final long CALL_TICKS = CALL_MINUTES * 60L * 20L;
    /** How long the door stays real once it opens, and how long after the last person near it leaves. */
    private static final long OPEN_TICKS = 150L * 20L;
    private static final long LINGER_TICKS = 20L * 20L;
    private static final int NEAR_DOOR = 9;
    /** Uncalled visits: the door is real in this share of the world clock's quarter-hours. */
    private static final long WINDOW_TICKS = 15L * 60L * 20L;
    private static final double WINDOW_CHANCE = 0.2;
    /** Ticks of pressing against a drawn door before it lets you through. */
    private static final int PUSH_TICKS = 6;
    private static final float SANITY_PER_SECOND = 0.5f;

    /** Who the PA asked for, and until when (game time), per player. */
    private record Call(AnomalySite site, long until) {}

    private static final Map<UUID, Call> CALLS = new HashMap<>();
    /** The line each player heard at the start of the current announcement (its echo repeats it). */
    private static final Map<UUID, Holder<SoundEvent>> LAST_LINE = new HashMap<>();

    /** Game time until which each office's door is real. */
    private final Map<AnomalySite, Long> openUntil = new HashMap<>();
    /** Players whose visit has been judged already (cleared when they leave the site). */
    private final Set<UUID> judged = new HashSet<>();
    private final Map<UUID, Integer> pushing = new HashMap<>();

    // ==========================================================================================
    // The PA (called from LevelEvents)
    // ==========================================================================================

    /**
     * Which announcement {@code player} hears for this intercom event: often the front-office call
     * if one is within reach on Level 0 (and then the player is expected there), else the usual.
     */
    public static Holder<SoundEvent> intercomLine(ServerPlayer player, RandomSource random) {
        Holder<SoundEvent> line = ModSounds.INTERCOM;
        ServerLevel level = player.serverLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_0 && random.nextDouble() < CALL_CHANCE) {
            LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
            long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_0);
            AnomalySite office = layout.nearestAnomaly(seed, player.getBlockX(), player.getBlockZ(), Level0AnomalyPlan.FRONT_OFFICE);
            if (office != null && office.distanceTo(player.getX(), player.getZ()) <= CALL_RANGE) {
                CALLS.put(player.getUUID(), new Call(office, level.getGameTime() + CALL_TICKS));
                line = ModSounds.L0_INTERCOM_FRONT_OFFICE;
            }
        }
        LAST_LINE.put(player.getUUID(), line);
        return line;
    }

    /** The line for the announcement's echo: the same one the player heard first. */
    public static Holder<SoundEvent> echoLine(ServerPlayer player) {
        return LAST_LINE.getOrDefault(player.getUUID(), ModSounds.INTERCOM);
    }

    // ==========================================================================================
    // Players in the office
    // ==========================================================================================

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        ServerLevel level = player.serverLevel();
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        long now = level.getGameTime();
        if (Level0FrontOffice.inRoom(site, player.getX(), player.getY(), player.getZ())) {
            if (ticksInside % 20 == 19) {
                SanityService.restore(player, SANITY_PER_SECOND);
            }
            if (pushedThrough(player, Level0FrontOffice.roomBackDoor(site), Level0FrontOffice.roomBackDoorFacing(site))) {
                BlockPos front = Level0FrontOffice.doorFront(site, layout.floorY());
                goThrough(player, Level0FrontOffice.roomBackDoor(site), front, Level0FrontOffice.doorFacing(site));
            } else if (pushedThrough(player, Level0FrontOffice.roomExitDoor(site), Level0FrontOffice.roomExitDoorFacing(site))) {
                int[] lobby = Level0AnomalyPlan.exitLobby(layout, seed, player.getBlockX(), player.getBlockZ());
                BlockPos spot = Travel.searchWalkable(layout, seed, lobby[0], lobby[1], 3);
                BlockPos to = spot != null ? spot : new BlockPos(lobby[0], layout.standY(seed, lobby[0], lobby[1]), lobby[1]);
                goThrough(player, Level0FrontOffice.roomExitDoor(site), to, Direction.from2DDataValue(lobby[2]));
            }
            return;
        }

        BlockPos door = Level0FrontOffice.door(site, layout.floorY());
        double near = player.position().distanceTo(Vec3.atCenterOf(door));
        if (near <= NEAR_DOOR) {
            if (judged.add(player.getUUID()) && shouldOpen(player, site, now)) {
                openUntil.put(site, Math.max(openUntil.getOrDefault(site, 0L), now + OPEN_TICKS));
            }
            Long until = openUntil.get(site);
            if (until != null && until > now) {
                openUntil.put(site, Math.max(until, now + LINGER_TICKS)); // stays real while you're here
                if (pushedThrough(player, door, Level0FrontOffice.doorFacing(site))) {
                    BlockPos back = Level0FrontOffice.roomBackDoor(site);
                    Direction into = Level0FrontOffice.roomBackDoorFacing(site);
                    goThrough(player, door, back.relative(into), into);
                }
            }
        }
    }

    /** Called for, or the right time of day: the door is real for this visit. */
    private static boolean shouldOpen(ServerPlayer player, AnomalySite site, long now) {
        Call call = CALLS.get(player.getUUID());
        if (call != null && call.until() > now && call.site().equals(site)) {
            CALLS.remove(player.getUUID());
            return true;
        }
        return Hash.unit(Hash.of(site.salt(), (int) (now / WINDOW_TICKS), 0, 3711)) < WINDOW_CHANCE;
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        judged.remove(player.getUUID());
        pushing.remove(player.getUUID());
    }

    /**
     * True once the player has stood pressed against the drawn door at {@code door} (lower half,
     * facing {@code facing}) and looked at it for {@link #PUSH_TICKS} ticks in a row.
     */
    private boolean pushedThrough(ServerPlayer player, BlockPos door, Direction facing) {
        BlockPos front = door.relative(facing);
        boolean pressing = player.getBlockX() == front.getX() && player.getBlockZ() == front.getZ()
                && Math.abs(player.getY() - door.getY()) < 0.6 && gap(player, door, facing) < 0.45
                && -(player.getLookAngle().x * facing.getStepX() + player.getLookAngle().z * facing.getStepZ()) > 0.5;
        UUID id = player.getUUID();
        if (!pressing) {
            if (pushing.containsKey(id) && pushing.get(id) > 0 && frontOf(player, door, facing)) {
                pushing.put(id, 0);
            }
            return false;
        }
        int n = pushing.getOrDefault(id, 0) + 1;
        if (n < PUSH_TICKS) {
            pushing.put(id, n);
            return false;
        }
        pushing.put(id, 0);
        return true;
    }

    private static boolean frontOf(ServerPlayer player, BlockPos door, Direction facing) {
        BlockPos front = door.relative(facing);
        return player.getBlockX() == front.getX() && player.getBlockZ() == front.getZ();
    }

    /** How far (blocks) the player stands in front of the wall face the door is drawn on. */
    private static double gap(ServerPlayer player, BlockPos door, Direction facing) {
        if (facing.getAxis() == Direction.Axis.X) {
            double face = door.getX() + 0.5 + 0.5 * facing.getStepX();
            return (player.getX() - face) * facing.getStepX();
        }
        double face = door.getZ() + 0.5 + 0.5 * facing.getStepZ();
        return (player.getZ() - face) * facing.getStepZ();
    }

    /** Through a drawn door: a latch, a change of air, and you're on the other side. */
    private void goThrough(ServerPlayer player, BlockPos from, BlockPos to, Direction face) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, from, ModSounds.L0_DRAWN_DOOR.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        Teleports.to(player, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, face.toYRot(), 0.0f);
        level.playSound(null, to, ModSounds.L0_DRAWN_DOOR.get(), SoundSource.BLOCKS, 0.9f, 0.95f);
        pushing.remove(player.getUUID());
    }

    // ==========================================================================================
    // The office itself (once a second while someone is within range)
    // ==========================================================================================

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        long now = level.getGameTime();
        Long until = openUntil.get(site);
        boolean open = until != null && until > now;
        if (until != null && !open) {
            openUntil.remove(site);
        }
        if (openUntil.size() > 64) {
            openUntil.keySet().removeIf(s -> openUntil.get(s) <= now);
        }
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();

        // The beacon turns while the door is drawn, stops while it is real.
        BlockPos beacon = Level0FrontOffice.beacon(site, layout.ceilingY());
        if (level.isLoaded(beacon)) {
            BlockState state = level.getBlockState(beacon);
            if (state.is(Level0LightBlocks.BEACON.get()) && state.getValue(BlockStateProperties.LIT) == open) {
                level.setBlock(beacon, state.setValue(BlockStateProperties.LIT, !open), Block.UPDATE_ALL);
            }
        }

        // The line of light under the door.
        BlockPos front = Level0FrontOffice.doorFront(site, layout.floorY());
        if (level.isLoaded(front)) {
            BlockState here = level.getBlockState(front);
            boolean glowing = here.is(Level0SetPieceBlocks.DOOR_GLOW.get());
            if (open && here.isAir()) {
                level.setBlock(front, Level0SetPieceBlocks.DOOR_GLOW.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Level0FrontOffice.doorFacing(site)), Block.UPDATE_ALL);
                // The alarm stops (client) and a latch clicks somewhere behind the drawing.
                level.playSound(null, Level0FrontOffice.door(site, layout.floorY()), ModSounds.L0_DRAWN_DOOR.get(),
                        SoundSource.BLOCKS, 0.5f, 0.8f);
            } else if (!open && glowing) {
                level.setBlock(front, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (CALLS.size() > 256) {
            CALLS.values().removeIf(c -> c.until() <= now);
        }
    }
}
