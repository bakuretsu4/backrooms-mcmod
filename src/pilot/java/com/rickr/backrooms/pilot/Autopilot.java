package com.rickr.backrooms.pilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

import static com.rickr.backrooms.pilot.PathPlanner.CLIMB_DOWN;
import static com.rickr.backrooms.pilot.PathPlanner.CLIMB_UP;
import static com.rickr.backrooms.pilot.PathPlanner.DROP;
import static com.rickr.backrooms.pilot.PathPlanner.JUMP;
import static com.rickr.backrooms.pilot.PathPlanner.SNEAK_DOWN;
import static com.rickr.backrooms.pilot.PathPlanner.SWIM_DOWN;
import static com.rickr.backrooms.pilot.PathPlanner.SWIM_UP;
import static com.rickr.backrooms.pilot.PathPlanner.WALK;
import static com.rickr.backrooms.pilot.PilotInput.FORWARD;
import static com.rickr.backrooms.pilot.PilotInput.JUMP_KEY;
import static com.rickr.backrooms.pilot.PilotInput.SNEAK;
import static com.rickr.backrooms.pilot.PilotInput.SPRINT;

/**
 * Walks the player along planned paths using only what a person would do: turn to face the next
 * waypoint, hold forward, jump, sneak, and right-click closed doors. Plans are redone when the
 * player gets knocked off course, makes no progress, or (when only part of the way is known)
 * once more of the world has loaded.
 */
final class Autopilot {
    enum State { IDLE, PLANNING, MOVING, STUCK, ARRIVED, NO_PATH, ABORTED }

    private static final int PLAN_TICK_BUDGET = 60;     // give up searching further after ~3 s, use best partial
    private static final int NO_PROGRESS_JUMP = 25;
    private static final int NO_PROGRESS_REPLAN = 60;
    private static final int MAX_STUCK_REPLANS = 4;
    private static final int PARTIAL_REFRESH_TICKS = 100;

    private final PilotInput input;

    State state = State.IDLE;
    String reason = "";

    // Goal and settings for the current run.
    private double gx, gy, gz, radius;
    private boolean explore, ignoreY, sprint;
    private Boolean jumpOverride; // goto option "jump": null = follow the level's rule
    private double maxDrop = 3, maxDropSoft = 24;
    private int maxNodes = 60000;
    private long sliceNanos = 6_000_000L;

    // Planning.
    private PathPlanner planner;
    private ClientLevel planLevel;
    private int planTicks, waitStartTicks, deadEnds;
    private int lastPlanNodes, lastPlanExpanded;
    private String lastOutcome = "";

    // Following.
    private List<PathPlanner.Node> path;
    private int index;
    private boolean partial;
    private PathPlanner.Node partialEnd;
    private double bestDist = Double.POSITIVE_INFINITY;
    private int noProgress, stuckReplans, replans, ticksSincePlan, lastChunkCount, runTicks;

    // Door opening.
    private int doorPhase, doorTicks, doorTries;

    private final LongOpenHashSet stuckDoors = new LongOpenHashSet();
    private final Long2IntOpenHashMap visits = new Long2IntOpenHashMap();

    Autopilot(PilotInput input) {
        this.input = input;
    }

    boolean active() {
        return state == State.PLANNING || state == State.MOVING;
    }

    void start(double gx, double gy, double gz, double radius, boolean explore, boolean ignoreY, JsonObject opts) {
        this.gx = gx; this.gy = gy; this.gz = gz;
        this.radius = Math.max(0.5, radius);
        this.explore = explore;
        this.ignoreY = ignoreY;
        this.sprint = opts.has("sprint") && opts.get("sprint").getAsBoolean();
        this.maxDrop = opts.has("maxDrop") ? opts.get("maxDrop").getAsDouble() : 3.0;
        this.maxDropSoft = opts.has("maxDropWater") ? opts.get("maxDropWater").getAsDouble() : 24.0;
        this.maxNodes = opts.has("maxNodes") ? opts.get("maxNodes").getAsInt() : 60000;
        this.sliceNanos = (long) ((opts.has("sliceMs") ? opts.get("sliceMs").getAsDouble() : 6.0) * 1_000_000L);
        String jump = opts.has("jump") ? opts.get("jump").getAsString() : "auto";
        this.jumpOverride = jump.equalsIgnoreCase("auto") ? null : Boolean.valueOf(jump);
        stuckDoors.clear();
        visits.clear();
        replans = 0;
        stuckReplans = 0;
        deadEnds = 0;
        runTicks = 0;
        planLevel = Minecraft.getInstance().level;
        partialEnd = null;
        beginPlanning("start");
    }

    void stop(String why) {
        if (active()) {
            finish(State.IDLE, why);
        }
    }

    private void finish(State s, String why) {
        state = s;
        reason = why;
        planner = null;
        releaseMovement();
    }

    private void beginPlanning(String why) {
        state = State.PLANNING;
        reason = why;
        planner = null;
        path = null;
        index = 0;
        planTicks = 0;
        waitStartTicks = 0;
        doorPhase = 0;
        doorTries = 0;
        releaseMovement();
    }

    private void replan(String why) {
        replans++;
        beginPlanning(why);
    }

    private void releaseMovement() {
        for (int i = PilotInput.FORWARD; i <= PilotInput.SPRINT; i++) {
            input.set(i, false);
        }
    }

    void tick() {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            finish(State.ABORTED, "left the world");
            return;
        }
        if (mc.level != planLevel) {
            finish(State.ABORTED, "dimension or world changed");
            return;
        }
        if (p.isDeadOrDying()) {
            finish(State.STUCK, "player died");
            return;
        }
        if (mc.screen != null) {
            releaseMovement(); // keys do nothing while a screen is open; wait for it to close
            return;
        }
        runTicks++;
        ticksSincePlan++;
        if (playerAtGoal(p)) {
            finish(State.ARRIVED, "within radius of goal");
            return;
        }
        if (state == State.PLANNING) {
            plan(mc, p);
        } else {
            follow(mc, p);
        }
    }

    private boolean playerAtGoal(LocalPlayer p) {
        double dx = p.getX() - gx, dz = p.getZ() - gz, dy = ignoreY ? 0 : p.getY() - gy;
        boolean settled = p.onGround() || p.isInWater() || p.onClimbable();
        return settled && Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius + 0.25;
    }

    // ---------------------------------------------------------------- planning

    private void plan(Minecraft mc, LocalPlayer p) {
        if (planner == null) {
            WorldModel world = new WorldModel(mc.level, stuckDoors);
            int[] cell = new int[3];
            long startNode = findStart(world, p, cell);
            if (startNode == 0) {
                // Mid-air or wedged: wait for the player to settle before searching from here.
                if (++waitStartTicks > 100) {
                    finish(State.STUCK, "no standable cell at the player's position");
                }
                return;
            }
            planner = new PathPlanner(world, cell[0], cell[1], cell[2], startNode, gx, gy, gz, radius,
                    ignoreY, maxDrop, maxDropSoft, maxNodes, visits, jumpAllowed(mc.level));
        }
        planTicks++;
        boolean done = planner.step(System.nanoTime() + sliceNanos);
        if (!done && planTicks < PLAN_TICK_BUDGET) return;

        PathPlanner.Outcome outcome = done ? planner.outcome : PathPlanner.Outcome.LIMIT;
        lastOutcome = outcome.name().toLowerCase();
        lastPlanNodes = planner.nodeCount();
        lastPlanExpanded = planner.expanded;
        PathPlanner.Node best = planner.best;
        PathPlanner pl = planner;
        planner = null;
        ticksSincePlan = 0;
        lastChunkCount = mc.level.getChunkSource().getLoadedChunksCount();

        if (outcome == PathPlanner.Outcome.FOUND) {
            startFollowing(PathPlanner.trace(pl.goalNode), false, pl.goalNode, "path to goal");
            return;
        }
        partialEnd = best;
        boolean goalLoaded = mc.level.getChunkSource().hasChunk(Mth.floor(gx) >> 4, Mth.floor(gz) >> 4);
        if (outcome == PathPlanner.Outcome.EXHAUSTED && goalLoaded && !explore) {
            finish(State.NO_PATH, "goal is loaded but unreachable from here (" + lastPlanNodes + " cells searched)");
            return;
        }
        if (best == null || pl.startDistance(best) < 1.5) {
            // Nothing better nearby. While exploring, mark this spot as used up and look elsewhere.
            if (explore && ++deadEnds <= 5) {
                visits.addTo(PathPlanner.regionKey(Mth.floor(p.getX()), Mth.floor(p.getZ())), 3);
                replan("dead end, widening search");
                return;
            }
            finish(State.NO_PATH, outcome == PathPlanner.Outcome.EXHAUSTED
                    ? "no reachable cell gets closer to the goal" : "search limit reached without progress");
            return;
        }
        deadEnds = 0;
        startFollowing(PathPlanner.trace(best), true, best,
                goalLoaded ? "partial path (search limit), will re-plan" : "goal outside loaded chunks, heading to closest loaded point");
    }

    private void startFollowing(List<PathPlanner.Node> nodes, boolean isPartial, PathPlanner.Node end, String why) {
        path = nodes;
        index = 0;
        partial = isPartial;
        partialEnd = isPartial ? end : null;
        bestDist = Double.POSITIVE_INFINITY;
        noProgress = 0;
        doorPhase = 0;
        doorTries = 0;
        state = State.MOVING;
        reason = why;
    }

    /** The cell the player currently occupies, tolerating standing on a block edge. */
    private static long findStart(WorldModel world, LocalPlayer p, int[] out) {
        int by = Mth.floor(p.getY() + 0.001);
        double bestD = Double.POSITIVE_INFINITY;
        long bestNode = 0;
        for (int dy = 0; dy >= -1; dy--) {
            for (int i = 0; i < 4; i++) {
                int x = Mth.floor(p.getX() + ((i & 1) == 0 ? -0.29 : 0.29));
                int z = Mth.floor(p.getZ() + ((i & 2) == 0 ? -0.29 : 0.29));
                long nd = world.node(x, by + dy, z);
                if (nd == 0) continue;
                double ddx = x + 0.5 - p.getX(), ddz = z + 0.5 - p.getZ();
                double d = ddx * ddx + ddz * ddz + Math.abs(WorldModel.height(nd) - p.getY());
                if (d < bestD) {
                    bestD = d;
                    bestNode = nd;
                    out[0] = x; out[1] = by + dy; out[2] = z;
                }
            }
            if (bestNode != 0) break;
        }
        return bestNode;
    }

    // ---------------------------------------------------------------- following

    private void follow(Minecraft mc, LocalPlayer p) {
        // Skip ahead over any waypoint we are already at (also handles cutting a bend short).
        for (int k = Math.min(path.size() - 1, index + 3); k >= index; k--) {
            if (reached(p, path.get(k))) {
                index = k + 1;
                bestDist = Double.POSITIVE_INFINITY;
                noProgress = 0;
                stuckReplans = 0;
                doorPhase = 0;
                doorTries = 0;
                break;
            }
        }
        if (index >= path.size()) {
            if (partial) {
                if (partialEnd != null) visits.addTo(PathPlanner.regionKey(partialEnd.x, partialEnd.z), 1);
                replan("reached partial end, re-planning");
            } else {
                finish(State.ARRIVED, "end of path");
            }
            return;
        }
        if (partial && ticksSincePlan > PARTIAL_REFRESH_TICKS
                && mc.level.getChunkSource().getLoadedChunksCount() != lastChunkCount) {
            replan("new chunks loaded, re-planning");
            return;
        }

        PathPlanner.Node w = path.get(index);
        if (w.door != Long.MIN_VALUE && handleDoor(mc, p, w)) {
            return;
        }

        double dx = w.x + 0.5 - p.getX(), dz = w.z + 0.5 - p.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        double dy = w.h - p.getY();

        if (hd > 3.5 || dy < -3.5 && p.onGround()) {
            replan("knocked off the path");
            return;
        }

        double dist = hd + Math.abs(dy) * 0.5;
        if (dist < bestDist - 0.05) {
            bestDist = dist;
            noProgress = 0;
        } else if (++noProgress > NO_PROGRESS_REPLAN) {
            if (++stuckReplans > MAX_STUCK_REPLANS) {
                finish(State.STUCK, "no progress toward waypoint " + w.x + "," + w.y + "," + w.z
                        + " (" + PathPlanner.MOVE_NAMES[w.move] + ") after " + MAX_STUCK_REPLANS + " re-plans");
            } else {
                replan("no progress, re-planning");
            }
            return;
        }

        boolean vertical = w.move == CLIMB_UP || w.move == CLIMB_DOWN || w.move == SWIM_UP
                || w.move == SWIM_DOWN || w.move == SNEAK_DOWN;
        if (hd > 0.05) {
            PilotInput.look(p, PilotInput.yawTowards(dx, dz), 0.0F);
        }
        boolean forward = hd > (vertical ? 0.3 : 0.12);
        boolean inWater = p.isInWater();
        boolean jump = switch (w.move) {
            case JUMP -> hd < 1.6 && (p.onGround() || inWater || p.onClimbable());
            case CLIMB_UP, SWIM_UP -> true;
            default -> false;
        };
        // Sinking means letting go of jump and holding sneak; holding jump in water floats you at
        // the surface forever (the Level 37 pit drowning). Only press jump to surface or climb out.
        boolean descending = inWater && (w.move == SWIM_DOWN || dy < -0.3);
        if (descending && lowOnAir(p)) {
            // Air under a third: keep going only if the bottom is close, or if this is an explicit goto
            // whose goal lies below in the water (the caller asked us to get there). Otherwise surface.
            double depthLeft = p.getY() - (explore ? path.get(path.size() - 1).h : gy);
            if (depthLeft > 4.0 && !(!explore && gy < p.getY())) descending = false;
        }
        if (inWater && !descending && dy > -0.3) jump = true;   // stay afloat / swim up to the target
        if (inWater && !descending && w.move == SWIM_DOWN) jump = true; // surfacing for air
        if (p.onClimbable() && w.move != CLIMB_DOWN && w.move != SNEAK_DOWN && dy > -0.2) jump = true; // keep climbing to the ledge
        if (noProgress == NO_PROGRESS_JUMP || (p.horizontalCollision && p.onGround() && noProgress > 5 && w.move != DROP)) jump = true;
        if (descending) jump = false;
        boolean sneak = w.move == SNEAK_DOWN || descending;
        boolean run = sprint && !inWater && w.move == WALK && hd > 1.5 && straightAhead();

        input.set(FORWARD, forward);
        input.set(JUMP_KEY, jump);
        input.set(SNEAK, sneak);
        input.set(SPRINT, run);
    }

    private static boolean lowOnAir(net.minecraft.world.entity.player.Player p) {
        return p.getAirSupply() < p.getMaxAirSupply() / 3;
    }

    /** Sprint only on runs of plain walking, never into jumps, drops or doors. */
    private boolean straightAhead() {
        for (int k = index; k < Math.min(path.size(), index + 3); k++) {
            PathPlanner.Node n = path.get(k);
            if (n.move != WALK || n.door != Long.MIN_VALUE) return false;
        }
        return true;
    }

    private static boolean reached(LocalPlayer p, PathPlanner.Node n) {
        double dx = n.x + 0.5 - p.getX(), dz = n.z + 0.5 - p.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        double py = p.getY();
        return switch (n.move) {
            case CLIMB_UP, SWIM_UP -> hd < 0.6 && py >= n.h - 0.15;
            case CLIMB_DOWN, SWIM_DOWN, SNEAK_DOWN -> hd < 0.6 && py <= n.h + 0.3;
            default -> {
                double tol = n.kind == WorldModel.SWIM ? 1.2 : n.kind == WorldModel.CLIMBING ? 0.8 : 0.55;
                yield hd < (n.kind == WorldModel.SWIM ? 0.5 : 0.35) && Math.abs(py - n.h) < tol;
            }
        };
    }

    /**
     * Opens a closed door/gate/trapdoor in the way the way a player does: stop, look at it,
     * right-click, then wait to see it open. Returns true while it is busy with the door.
     */
    private boolean handleDoor(Minecraft mc, LocalPlayer p, PathPlanner.Node w) {
        BlockPos dp = BlockPos.of(w.door);
        BlockState s = mc.level.getBlockState(dp);
        if (!WorldModel.isOpenable(s) || WorldModel.isOpen(s)) {
            doorPhase = 0;
            return false;
        }
        releaseMovement();
        switch (doorPhase) {
            case 0 -> {
                aimAt(p, dp, s, doorTries);
                doorPhase = 1;
                doorTicks = 0;
            }
            case 1 -> {
                // The aim from last tick has been picked by now; only click if the crosshair is on it.
                HitResult hit = mc.hitResult;
                if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && sameDoor(mc, bh.getBlockPos(), w.door)) {
                    PilotInput.click(PilotInput.USE);
                    doorPhase = 2;
                    doorTicks = 0;
                    doorTries++;
                } else if (++doorTicks > 3) {
                    doorTries++;
                    doorPhase = 0;
                    if (doorTries >= 4) giveUpDoor(w.door, "could not get the crosshair on the door");
                }
            }
            default -> {
                if (++doorTicks > 12) {
                    doorPhase = 0;
                    if (doorTries >= 3) giveUpDoor(w.door, "door did not open after 3 clicks");
                }
            }
        }
        return true;
    }

    private void giveUpDoor(long key, String why) {
        stuckDoors.add(key);
        replan(why + " at " + BlockPos.of(key).toShortString() + ", routing around it");
    }

    private static boolean sameDoor(Minecraft mc, BlockPos hit, long key) {
        long h = hit.asLong();
        if (h == key) return true;
        BlockState s = mc.level.getBlockState(hit);
        return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && hit.below().asLong() == key;
    }

    /** Look at the middle of the block's outline (varying the point a little on retries). */
    private static void aimAt(LocalPlayer p, BlockPos pos, BlockState s, int attempt) {
        VoxelShape shape = s.getShape(p.level(), pos);
        Vec3 c = shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
        if (attempt % 2 == 1) c = c.add(0, 0.25, 0);
        Vec3 eye = p.getEyePosition();
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        PilotInput.look(p, PilotInput.yawTowards(dx, dz), PilotInput.pitchTowards(dx, dy, dz));
    }

    /** Whether jumping on land works here (the "jump" option overrides the level's rule). */
    boolean jumpAllowed(ClientLevel level) {
        return jumpOverride != null ? jumpOverride : levelAllowsJump(level);
    }

    /**
     * Mirrors MovementClientEvents: the mod switches the jump key off on land in every Backrooms
     * level except Level 490 and Level 227. Read from the dimension id so the pilot stays separate
     * from the mod's code.
     */
    static boolean levelAllowsJump(ClientLevel level) {
        if (level == null) return true;
        var id = level.dimension().location();
        if (!id.getNamespace().equals("backrooms")) return true;
        return id.getPath().equals("level_490") || id.getPath().equals("level_227");
    }

    // ---------------------------------------------------------------- reporting

    void describe(JsonObject out, LocalPlayer p) {
        JsonObject n = new JsonObject();
        n.addProperty("state", state.name().toLowerCase());
        n.addProperty("reason", reason);
        if (state != State.IDLE || path != null) {
            JsonArray g = new JsonArray();
            g.add(gx); g.add(gy); g.add(gz);
            n.add("goal", g);
            n.addProperty("radius", radius);
            n.addProperty("explore", explore);
            n.addProperty("jumpOverride", jumpOverride == null ? "auto" : jumpOverride.toString());
            if (p != null) {
                double dx = p.getX() - gx, dz = p.getZ() - gz, dy = ignoreY ? 0 : p.getY() - gy;
                n.addProperty("distance", Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        n.addProperty("partial", partial);
        if (path != null) {
            n.addProperty("pathLength", path.size());
            n.addProperty("index", index);
            if (index < path.size()) n.add("next", nodeJson(path.get(index)));
        }
        if (partialEnd != null) n.add("partialEnd", nodeJson(partialEnd));
        n.addProperty("lastSearch", lastOutcome);
        n.addProperty("searchedCells", lastPlanNodes);
        n.addProperty("expanded", lastPlanExpanded);
        if (planner != null) n.addProperty("searchingCells", planner.nodeCount());
        n.addProperty("replans", replans);
        n.addProperty("stuckReplans", stuckReplans);
        n.addProperty("ticks", runTicks);
        n.addProperty("doorPhase", doorPhase);
        n.addProperty("stuckDoors", stuckDoors.size());
        out.add("nav", n);
    }

    JsonArray pathJson(int limit) {
        JsonArray a = new JsonArray();
        if (path != null) {
            for (int i = index; i < path.size() && a.size() < limit; i++) a.add(nodeJson(path.get(i)));
        }
        return a;
    }

    private static JsonObject nodeJson(PathPlanner.Node n) {
        JsonObject o = new JsonObject();
        o.addProperty("x", n.x);
        o.addProperty("y", n.y);
        o.addProperty("z", n.z);
        o.addProperty("feetY", Math.round(n.h * 1000) / 1000.0);
        o.addProperty("move", PathPlanner.MOVE_NAMES[n.move]);
        o.addProperty("kind", n.kind == WorldModel.STAND ? "stand" : n.kind == WorldModel.CLIMBING ? "climb" : "swim");
        if (n.door != Long.MIN_VALUE) o.addProperty("door", BlockPos.of(n.door).toShortString());
        return o;
    }
}
