package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Bacteria (a.k.a. "the Lifeform") from Kane Pixels' Backrooms.
 *
 * <p>It is a hunter with real senses, not a homing missile. It only knows where you are if it
 * sees you (in the light from 24 blocks, in the dark only from 8) or hears you sprint. So the
 * way to survive is the same as in the footage: break line of sight, get somewhere dark, keep
 * still or creep, and it loses you.
 *
 * <p>The behaviour is a small state machine ({@link Mode}) run by the server in
 * {@link #customServerAiStep()}:
 * <pre>
 * WANDER  --sees you-->           SPOTTED --24 ticks--> CHASE --3 s without seeing you--> SEARCH
 * WANDER  --hears a sprint / every 30-60 s a rough guess--> DRIFT --arrives--> WANDER
 * SEARCH  --sees or hears you--> CHASE      SEARCH --8 s, nothing--> WANDER ("lost" sigh)
 * </pre>
 * The mode is synced to clients so the model can pose and the camcorder can glitch.
 *
 * <p>Up close it never walks into you (your camera used to end up inside its body). Within
 * {@link #LUNGE_RANGE} it lunges, stops 1.5-2 blocks away and closes its long arms around you
 * like a cage; the hits come from the arms ({@link #closeIn()}). After a kill it folds over its
 * victim for a few seconds. While wandering it sometimes leans out from behind cover to look at
 * you before it has properly seen you. Those moves are told to the clients as synced data
 * ({@link Action}, {@link #isCaging()}, {@link #preyId()}), so every player sees them at the
 * same moment.
 *
 * <p>Safe rooms ({@code world/SafeRooms}) are off limits: it never walks into one, and it can't
 * see or hear anyone inside. Reaching a safe room in a chase counts as escaping.
 *
 * <p>Nor can it see or hear anyone hiding in furniture ({@code player/HidingService}), unless it
 * saw them get in: then it comes over and drags them out, and the chase goes on. Hiding mid-chase
 * makes it search where it last saw you, then give up.
 */
public class Bacteria extends Monster implements HidingService.Seeker {
    /** What the Bacteria is doing right now. The order matters: it is synced as a byte. */
    public enum Mode { WANDER, DRIFT, SPOTTED, CHASE, SEARCH }

    /**
     * One-shot moves the server tells the clients to play right now (the model times them from
     * the moment the client hears about them). The order matters: it is synced as a number.
     * PEEK_*_HOLD is the rare peek that stays out for an uncomfortably long time.
     */
    public enum Action { NONE, LUNGE, WHIP, FEED, PEEK_LEFT, PEEK_RIGHT, PEEK_LEFT_HOLD, PEEK_RIGHT_HOLD }

    /**
     * Poses the test command {@code /backrooms bacteria pose} can force, so the lead can film
     * each one. AUTO = normal behaviour. The order matters: it is synced as a byte.
     */
    public enum AnimPose { AUTO, IDLE, WALK, CHASE, SPOTTED, LUNGE, CAGE, FEED, PEEK }

    private static final Mode[] MODES = Mode.values();
    private static final Action[] ACTIONS = Action.values();
    private static final AnimPose[] POSES = AnimPose.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.BYTE);
    /** Entity id of the player it is after (SPOTTED and CHASE), -1 for nobody. */
    private static final EntityDataAccessor<Integer> PREY_ID = SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.INT);
    /** True while its arms are closed around its prey. */
    private static final EntityDataAccessor<Boolean> CAGED = SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.BOOLEAN);
    /**
     * The latest one-shot move: {@code count << 4 | action}. The count goes up every time, so the
     * value always changes and the clients notice even the same move twice in a row.
     */
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.INT);
    /**
     * The pose forced by the test command: {@code count << 8 | loop bit | AnimPose number}. The
     * count goes up with every command, so running the same command again restarts the pose.
     */
    private static final EntityDataAccessor<Integer> FORCED = SynchedEntityData.defineId(Bacteria.class, EntityDataSerializers.INT);
    private static final int LOOP_BIT = 0x40;
    private static final int POSE_MASK = 0x3F;

    // --- Senses ------------------------------------------------------------------------------
    /** How far it can spot you when you stand in the light (lamps on, no blackout). */
    private static final double SIGHT_LIT = 24.0;
    /** How far it can spot you in the dark. Your flashlight does not change this. */
    private static final double SIGHT_DARK = 8.0;
    /** Crouching shrinks both sight ranges. */
    private static final double CROUCH_FACTOR = 0.6;
    /** Block light at your eyes needed to count as "in the light". Ceiling lamps give 12. */
    private static final int LIT_LEVEL = 7;
    /** Sprinting is loud: it hears it through walls from this far... */
    private static final double HEAR_SPRINT = 16.0;
    /** ...and a bit less far while it is busy searching. */
    private static final double HEAR_SPRINT_SEARCHING = 12.0;
    /** Nobody is checked beyond this, which keeps the sense loop cheap. */
    private static final double SENSE_RANGE = 48.0;
    /** The senses run every this many ticks, not every tick, to keep big servers light. */
    private static final int SENSE_INTERVAL = 5;

    // --- Timings (ticks; 20 ticks = 1 second) -----------------------------------------------
    /**
     * The freeze between spotting you and running at you: your warning. 24 ticks (1.2 s): its
     * head snaps onto you, then nothing moves but the head slowly tilting over, then it runs.
     * The model times its wind-up to this, so it is public.
     */
    public static final int SPOTTED_TICKS = 24;
    /** How long it keeps running to where it last saw you before it starts searching. */
    private static final int LOST_SIGHT_TICKS = 60;
    /** How long it searches before giving up. */
    private static final int SEARCH_TICKS = 160;
    /** How often the search sniffing plays. */
    private static final int SEARCH_SOUND_TICKS = 40;
    /** A drift that takes longer than this is abandoned. */
    private static final int DRIFT_MAX_TICKS = 600;
    /** Every 30-60 s while wandering it takes a rough guess at where someone is. */
    private static final int GUESS_MIN_TICKS = 600;
    private static final int GUESS_MAX_TICKS = 1200;
    private static final double GUESS_RANGE = 64.0;
    /** The guess is off by up to this many blocks, so it never walks straight to you. */
    private static final double GUESS_ERROR = 8.0;
    /** Path updates while chasing: at most twice a second. Pathfinding is the expensive part. */
    private static final int CHASE_REPATH_TICKS = 10;
    /** Same damage and rhythm as always: one hit a second at most. */
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    /** Chasing someone further than this makes it give up and search instead. */
    private static final double CHASE_GIVE_UP = 48.0;
    /** Within this many blocks (and in sight) it stops pathfinding, lunges and goes for you. */
    public static final double LUNGE_RANGE = 3.0;
    /** How many blocks up the headroom check looks; more than this counts as "plenty". */
    private static final float HEADROOM_CHECK = 5.0f;
    /** With nobody within {@link #LONELY_RANGE} for 3 minutes, it slips away when unseen. */
    private static final int LONELY_DESPAWN_TICKS = 3 * 60 * 20;
    private static final double LONELY_RANGE = 32.0;

    // --- Close combat (see closeIn) -----------------------------------------------------------
    /** The lunge: wind up, throw the arms wide, close them round you. Ticks from start to contact. */
    public static final int LUNGE_TICKS = 9;
    /** For those 9 ticks it bursts forward a bit faster than it runs (about 6.6 blocks/s). */
    private static final double LUNGE_SPEED = 1.25;
    /** After a lunge that missed, it waits this long before the next one. */
    private static final int LUNGE_COOLDOWN_TICKS = 20;
    /**
     * Its body stops this far from you (blocks, centre to centre) and its arms do the rest, so
     * your camera is never inside it. It keeps between the MIN and MAX distance.
     */
    private static final double STANDOFF = 1.75;
    private static final double STANDOFF_MIN = 1.5;
    private static final double STANDOFF_MAX = 2.0;
    /** How far its arms reach (centre to centre): the cage closes and the hits land from here. */
    private static final double CAGE_REACH = 2.4;
    /** ...and no more than this far above or below it. */
    private static final double CAGE_REACH_UP = 2.0;
    /** Get this far away and you have broken out of its arms. */
    private static final double CAGE_RELEASE = 3.2;
    /** If you walk into it, it eases back this fast (added to its speed each tick). */
    private static final double BACK_OFF = 0.08;
    /** While it holds you, an arm whips across your face at most every 40-60 ticks. */
    private static final int WHIP_GAP_MIN = 40;
    private static final int WHIP_GAP_MAX = 60;
    /** After a kill it folds over its victim this long (about 3 s), then goes back to wandering. */
    public static final int FEED_TICKS = 62;

    // --- Peeking out from cover (see tryPeek) ---------------------------------------------------
    /** How long one peek lasts: 25 ticks easing out, a hold, a 2-tick snap back in. */
    public static final int PEEK_TICKS = 70;
    /** One peek in four instead stays out this long, without moving. */
    public static final int PEEK_HOLD_TICKS = 170;
    private static final float PEEK_HOLD_CHANCE = 0.25f;
    /** It only peeks at someone this far away (blocks)... */
    private static final double PEEK_MIN = 8.0;
    private static final double PEEK_MAX = 24.0;
    /** ...if leaning about this far (blocks) to one side would let it see them. */
    private static final double PEEK_REACH = 1.0;
    /** After a peek it waits 20-40 s before the next; after a failed check, 1 s. */
    private static final int PEEK_COOLDOWN_MIN = 400;
    private static final int PEEK_COOLDOWN_MAX = 800;
    private static final int PEEK_RETRY_TICKS = 20;

    // --- Animation timing shared with the model (client) --------------------------------------
    /** Test command with "loop": the lunge replays this often (2 s). */
    public static final int POSE_LOOP_TICKS = 40;
    /** While its pose is forced it looks at the nearest player within this range. */
    private static final double POSE_LOOK_RANGE = 32.0;
    /** While calm, its head locks onto a player it can see within this range (client only). */
    private static final double WATCH_RANGE = 24.0;
    /** The client re-checks who it is watching this often (ticks): a ray check each time. */
    private static final int WATCH_CHECK_TICKS = 5;
    // The stride. Running: one step takes 4-6 ticks (5 +- 1, picked per step), so a full stride
    // is 9-11 ticks and the rhythm limps slightly. Stalking: a slow 32-tick stride.
    public static final float CHASE_STEP_TICKS = 5.0f;
    private static final float CHASE_STEP_JITTER = 1.0f;
    public static final float WALK_STRIDE_TICKS = 32.0f;
    private static final float SEARCH_STRIDE_TICKS = 16.0f;
    private static final float DRIFT_STRIDE_TICKS = 24.0f;
    /** Stalking, its bad leg's swing takes this much longer than the other's (a limp). */
    private static final float LIMP = 1.3f;
    /** Chance per stride that it stops mid-step with the bad foot in the air, for 8-20 ticks. */
    private static final float STEP_PAUSE_CHANCE = 0.25f;
    private static final int STEP_PAUSE_MIN = 8;
    private static final int STEP_PAUSE_MAX = 20;
    // Quiet sounds the client plays itself (volume; they fade out within 12 blocks).
    private static final float CLICK_VOLUME = 0.32f;
    private static final float SCRAPE_VOLUME = 0.28f;
    /** Chance per footfall while stalking that the claws scrape the floor. */
    private static final float SCRAPE_CHANCE = 0.3f;

    // --- Speeds (multipliers of MOVEMENT_SPEED) ----------------------------------------------
    // Mob speed works out to roughly (attribute x multiplier)^2, so 0.31 at 1.0 is about 4.2
    // blocks/s: faster than your Backrooms walk (3.5), slower than your sprint (5.6).
    private static final double CHASE_SPEED = 1.0;
    private static final double SEARCH_SPEED = 0.75;
    private static final double DRIFT_SPEED = 0.6;
    private static final double WANDER_SPEED = 0.45;

    // Server-side brain state. Not saved: after a reload it simply starts wandering again.
    private Mode mode = Mode.WANDER;
    private int modeTicks;
    @Nullable
    private Player prey;
    private Vec3 lastSeen = Vec3.ZERO;
    @Nullable
    private Vec3 pathGoal;
    private int unseenTicks;
    private int repathIn;
    private int attackCooldown;
    private int searchLegs;
    private int nextGuessIn = GUESS_MIN_TICKS;
    private int lonelyTicks;
    // Close combat and the other one-shot moves (server side).
    private int lungeTicks = -1;
    private int lungeCooldown;
    private int nextWhipAt;
    private int feedTicks;
    private int peekTicks;
    private int peekCooldown = PEEK_COOLDOWN_MIN;
    @Nullable
    private Player peekAt;
    private int actionCount;

    // Client-only, purely visual: free space above it (blocks), smoothed, for the model's stoop.
    private float headroom = HEADROOM_CHECK;
    private float headroomO = HEADROOM_CHECK;
    private boolean headroomMeasured;
    // Client-only, purely visual: heartbeat and breathing, counted in beats/breaths so far. Kept
    // here (not worked out from the clock) so the rhythm speeds up smoothly when the mode changes.
    private float heartPhase;
    private float heartPhaseO;
    private float breathPhase;
    private float breathPhaseO;
    // Client-only, purely visual: the stride, counted in strides so far (same idea as the heart).
    private float gaitPhase;
    private float gaitPhaseO;
    // Client-only: when (in this client's ticks) the latest move, mode and forced pose arrived,
    // so the model can time them.
    private Action shownAction = Action.NONE;
    private int actionTick = -1000;
    private Mode shownMode = Mode.WANDER;
    private int modeTick = -1000;
    private int forcedTick;
    // Client-only, purely visual: the per-frame pose memory (head aim, swinging arms).
    public final BacteriaPoseState animState = new BacteriaPoseState();
    // Client-only: the gait's limp pause, who it watches, and the clocks the sounds follow.
    private int gaitPause;
    private int lastGaitCycle;
    private int lastFootfall;
    private int lastGlitchSlot = Integer.MIN_VALUE;

    public Bacteria(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.WANDER.ordinal());
        builder.define(PREY_ID, -1);
        builder.define(CAGED, false);
        builder.define(ACTION, 0);
        builder.define(FORCED, 0);
    }

    /**
     * Only the "always on" basics are goals. Everything that decides where it goes lives in
     * {@link #customServerAiStep()}, because the modes hand over to each other and that is much
     * easier to follow as one state machine than as competing goals.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Current mode; works on both server and client (synced). */
    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.WANDER;
    }

    public boolean isChasing() {
        return mode() == Mode.CHASE;
    }

    /**
     * How strongly this Bacteria glitches the camcorder, before distance is applied. The freeze
     * before a chase counts as full danger: that spike is part of the warning.
     */
    public float dangerWeight() {
        return switch (mode()) {
            case SPOTTED, CHASE -> 1.0f;
            case SEARCH -> 0.75f;
            case WANDER, DRIFT -> 0.55f;
        };
    }

    // ------------------------------------------------------------------ animation data (synced)

    /** Entity id of the player it is after while SPOTTED or chasing, else -1. */
    public int preyId() {
        return entityData.get(PREY_ID);
    }

    /** True while its arms are closed around its prey (the "cage"). */
    public boolean isCaging() {
        return entityData.get(CAGED);
    }

    /** The pose forced by the test command, or AUTO. */
    public AnimPose forcedPose() {
        int v = entityData.get(FORCED) & POSE_MASK;
        return v < POSES.length ? POSES[v] : AnimPose.AUTO;
    }

    /** True if the forced pose should replay its one-shot moves over and over. */
    public boolean forcedLoop() {
        return (entityData.get(FORCED) & LOOP_BIT) != 0;
    }

    /**
     * Test command: force an animation pose ({@code AUTO} gives it back to the AI). While a pose
     * is forced it stands still and does nothing else (no senses, no attacks), so it can be
     * filmed; the walk and run play on the spot. Works on Bacteria summoned with NoAI too,
     * because the model only reads this synced value.
     */
    public void forcePose(AnimPose pose, boolean loop) {
        int count = ((entityData.get(FORCED) >>> 8) + 1) & 0xFFFFFF;
        entityData.set(FORCED, count << 8 | (loop ? LOOP_BIT : 0) | pose.ordinal());
        if (pose != AnimPose.AUTO) {
            getNavigation().stop();
        }
    }

    /** Client: the latest one-shot move. */
    public Action action() {
        return shownAction;
    }

    /** Client: ticks since the latest one-shot move started, for this frame. */
    public float actionAge(float partialTick) {
        return tickCount - actionTick + partialTick;
    }

    /** Client: the tick the latest move started; tells repeats of the same move apart. */
    public int actionStamp() {
        return actionTick;
    }

    /** Client: ticks since the mode last changed, for this frame. */
    public float modeAge(float partialTick) {
        return tickCount - modeTick + partialTick;
    }

    /** Client: the tick the mode last changed. */
    public int modeStamp() {
        return modeTick;
    }

    /** Client: ticks since the forced pose was set, for this frame. */
    public float forcedAge(float partialTick) {
        return tickCount - forcedTick + partialTick;
    }

    /** Strides so far, interpolated for this frame (only the fraction matters). */
    public float gaitPhase(float partialTick) {
        return Mth.lerp(partialTick, gaitPhaseO, gaitPhase);
    }

    /**
     * Client: note the moment a move, mode or forced pose arrives, so the model can play it from
     * there. Values that arrive with the entity itself (when it comes into view) are old news:
     * they are taken as having happened long ago, so a lunge doesn't replay when you walk up.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) {
            return;
        }
        if (ACTION.equals(key)) {
            int a = entityData.get(ACTION) & 0xF;
            shownAction = a < ACTIONS.length ? ACTIONS[a] : Action.NONE;
            actionTick = firstTick ? tickCount - 1000 : tickCount;
        } else if (MODE.equals(key)) {
            Mode now = mode();
            if (firstTick) {
                shownMode = now;
                modeTick = tickCount - 1000;
            } else if (now != shownMode) {
                shownMode = now;
                modeTick = tickCount;
                if (now == Mode.SPOTTED) {
                    // Its head snaps onto you: one dry click of bone.
                    playClick(1.0f);
                }
            }
        } else if (FORCED.equals(key)) {
            forcedTick = tickCount;
        }
    }

    // ------------------------------------------------------------------ headroom (client visual)

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            float measured = measureHeadroom();
            headroomO = headroomMeasured ? headroom : measured;
            // Ease toward the new value so it ducks smoothly rather than snapping.
            headroom = headroomMeasured ? headroom + (measured - headroom) * 0.3f : measured;
            headroomMeasured = true;
            tickOrgans();
            tickGait();
            if ((tickCount + getId()) % WATCH_CHECK_TICKS == 0) {
                pickWatchTarget();
            }
            tickGlitchSound();
            playForcedPlant();
        }
    }

    /**
     * The heart races once it has seen you (one beat every 7 ticks while frozen, 9 while
     * chasing), still runs fast while it searches, and slows to about one beat a second while it
     * wanders. Breathing follows the same mood. You can see this through the wires when you
     * shine a light on it.
     */
    private void tickOrgans() {
        Mode current = mode();
        float beatTicks = switch (current) {
            case SPOTTED -> 7.0f;
            case CHASE -> 9.0f;
            case SEARCH -> 12.0f;
            case WANDER, DRIFT -> 22.0f;
        };
        float breathTicks = switch (current) {
            case SPOTTED, CHASE -> 18.0f;
            case SEARCH -> 30.0f;
            case WANDER, DRIFT -> 115.0f; // one slow breath every ~6 s
        };
        heartPhaseO = heartPhase;
        breathPhaseO = breathPhase;
        heartPhase += 1.0f / beatTicks;
        breathPhase += 1.0f / breathTicks;
        // Only the fraction matters; keep the numbers small so floats stay precise.
        if (heartPhase > 1000.0f) {
            heartPhase -= 1000.0f;
            heartPhaseO -= 1000.0f;
        }
        if (breathPhase > 1000.0f) {
            breathPhase -= 1000.0f;
            breathPhaseO -= 1000.0f;
        }
    }

    /**
     * The stride runs on its own clock, so it has the cadence of the references whatever the exact
     * speed. Running: each step takes 4-6 ticks (picked per step), a slightly broken rhythm.
     * Stalking: a slow 32-tick stride with a limp: the bad leg (the same side as its low shoulder)
     * swings 30% slower, and about once every 8 steps it stops mid-step with that foot in the air
     * for 8-20 ticks before it finishes the step. The model only shows the stride as much as the
     * Bacteria is really moving, except when the test command forces a walk or run: then it plays
     * on the spot.
     */
    private void tickGait() {
        AnimPose forced = forcedPose();
        Mode current = mode();
        boolean running = forced == AnimPose.CHASE || forced == AnimPose.LUNGE
                || (forced == AnimPose.AUTO && (current == Mode.CHASE || current == Mode.SPOTTED));
        boolean stalking = forced == AnimPose.WALK || (forced == AnimPose.AUTO && current == Mode.WANDER);
        int seed = BacteriaRhythm.seed(getId());
        gaitPhaseO = gaitPhase;
        float rate;
        if (running) {
            int step = Mth.floor(gaitPhase * 2.0f);
            rate = 0.5f / (CHASE_STEP_TICKS + CHASE_STEP_JITTER * BacteriaRhythm.hash(seed + 301, step));
        } else if (stalking) {
            // The right leg swings forward in the middle half of each stride, the left leg in
            // the outer quarters (see the model); "mid-swing" is 0.5 for the right, 0 for the left.
            boolean lameRight = BacteriaRhythm.hash(seed, 1) > 0.0f;
            float mid = lameRight ? 0.5f : 0.0f;
            float frac = gaitPhase - Mth.floor(gaitPhase);
            float fromMid = Math.abs(frac - mid);
            boolean badSwing = Math.min(fromMid, 1.0f - fromMid) < 0.25f;
            if (gaitPause > 0) {
                gaitPause--;
                rate = 0.0f;
            } else {
                rate = (badSwing ? 1.0f / LIMP : 1.0f) / WALK_STRIDE_TICKS;
            }
            int cycle = Mth.floor(gaitPhase + rate - mid);
            if (cycle != lastGaitCycle && rate > 0.0f) {
                // About to pass mid-swing of the bad leg: sometimes it stops right there.
                lastGaitCycle = cycle;
                if (BacteriaRhythm.unit(seed + 302, cycle) < STEP_PAUSE_CHANCE) {
                    gaitPause = STEP_PAUSE_MIN + (int) (BacteriaRhythm.unit(seed + 303, cycle) * (STEP_PAUSE_MAX - STEP_PAUSE_MIN + 1));
                    rate = 0.0f;
                }
            }
        } else {
            rate = 1.0f / (current == Mode.SEARCH ? SEARCH_STRIDE_TICKS : DRIFT_STRIDE_TICKS);
        }
        gaitPhase += rate;
        if (gaitPhase > 1000.0f) {
            gaitPhase -= 1000.0f;
            gaitPhaseO -= 1000.0f;
            lastGaitCycle -= 1000;
        }
        // Stalking with its hands down, now and then a footfall drags the claws over the floor.
        int footfall = Mth.floor(gaitPhase * 2.0f);
        if (footfall != lastFootfall) {
            lastFootfall = footfall;
            double dx = getX() - xo;
            double dz = getZ() - zo;
            boolean moving = forced == AnimPose.WALK || dx * dx + dz * dz > 1.0e-4;
            if (stalking && moving && random.nextFloat() < SCRAPE_CHANCE) {
                level().playLocalSound(getX(), getY(), getZ(), ModSounds.BACTERIA_SCRAPE.get(), SoundSource.HOSTILE,
                        SCRAPE_VOLUME, 0.85f + random.nextFloat() * 0.3f, false);
            }
        }
    }

    /**
     * Client: who its head locks onto while it is calm: the nearest player it can see within 24
     * blocks (a ray check, a few times a second), and the nearest player within 32 seen or not
     * (for peeking round cover and for the test poses). Purely visual.
     */
    private void pickWatchTarget() {
        Vec3 eye = getEyePosition();
        double watchBest = WATCH_RANGE * WATCH_RANGE;
        double nearBest = POSE_LOOK_RANGE * POSE_LOOK_RANGE;
        int watch = -1;
        int nearest = -1;
        for (Player player : level().players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double distance = distanceToSqr(player);
            if (distance < nearBest) {
                nearBest = distance;
                nearest = player.getId();
            }
            if (distance < watchBest && clearLine(eye, player.getEyePosition())) {
                watchBest = distance;
                watch = player.getId();
            }
        }
        animState.watchId = watch;
        animState.nearestId = nearest;
    }

    /**
     * Client: a quiet dry click at the very tick one of the model's rare glitches pops (same
     * clock as the model, see BacteriaRhythm). None while it is frozen staring at you.
     */
    private void tickGlitchSound() {
        AnimPose forced = forcedPose();
        boolean chase;
        boolean cage;
        if (forced != AnimPose.AUTO) {
            chase = forced == AnimPose.CHASE;
            cage = forced == AnimPose.CAGE || forced == AnimPose.LUNGE;
            if (forced == AnimPose.SPOTTED) {
                lastGlitchSlot = Integer.MIN_VALUE;
                return;
            }
        } else {
            Mode current = mode();
            if (current == Mode.SPOTTED) {
                lastGlitchSlot = Integer.MIN_VALUE;
                return;
            }
            cage = current == Mode.CHASE && isCaging();
            chase = current == Mode.CHASE && !cage;
        }
        int seed = BacteriaRhythm.seed(getId()) + BacteriaRhythm.GLITCH_SEED;
        float period = chase ? BacteriaRhythm.CHASE_GLITCH_PERIOD : BacteriaRhythm.CALM_GLITCH_PERIOD;
        float jitter = chase ? BacteriaRhythm.CHASE_GLITCH_JITTER : BacteriaRhythm.CALM_GLITCH_JITTER;
        int slot = BacteriaRhythm.slot(seed, tickCount, period, jitter);
        if (lastGlitchSlot != Integer.MIN_VALUE && slot != lastGlitchSlot) {
            // Head pops and shoulder/spine jerks click; a hand clenching (and every cage re-grip)
            // only ticks softly.
            boolean soft = cage || (!chase && BacteriaRhythm.glitchChannel(seed, slot) == BacteriaRhythm.CLENCH);
            playClick(soft ? 0.5f : 1.0f);
        }
        lastGlitchSlot = slot;
    }

    private void playClick(float loudness) {
        level().playLocalSound(getX(), getY() + 2.6, getZ(), ModSounds.BACTERIA_CLICK.get(), SoundSource.HOSTILE,
                CLICK_VOLUME * loudness, 0.85f + random.nextFloat() * 0.3f, false);
    }

    /**
     * Test command only: with a forced lunge the server isn't running, so the client plays the
     * inhale itself at the moment the arms close (in normal play the server does that).
     */
    private void playForcedPlant() {
        if (forcedPose() != AnimPose.LUNGE) {
            return;
        }
        int age = tickCount - forcedTick;
        if ((forcedLoop() ? age % POSE_LOOP_TICKS : age) == LUNGE_TICKS) {
            level().playLocalSound(getX(), getY(), getZ(), ModSounds.BACTERIA_SCREECH.get(), SoundSource.HOSTILE,
                    1.5f, 0.8f, false);
        }
    }

    /** Heartbeats so far, interpolated for this frame (only the fraction matters). */
    public float heartPhase(float partialTick) {
        return Mth.lerp(partialTick, heartPhaseO, heartPhase);
    }

    /** Breaths so far, interpolated for this frame (only the fraction matters). */
    public float breathPhase(float partialTick) {
        return Mth.lerp(partialTick, breathPhaseO, breathPhase);
    }

    /**
     * Free height above its feet in blocks (up to {@link #HEADROOM_CHECK}), interpolated for this
     * frame. The model stands about 3.4 blocks tall but the hitbox is 2.9 so it fits through the
     * 3-block doorways; the model uses this to stoop under them instead of poking through.
     */
    public float headroom(float partialTick) {
        return Mth.lerp(partialTick, headroomO, headroom);
    }

    /**
     * Checks the column it stands in and the one about a block ahead, so it already ducks as it
     * walks up to a doorway, not only once its head is inside the lintel.
     */
    private float measureHeadroom() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        BlockPos feet = BlockPos.containing(getX(), getY() + 0.01, getZ());
        BlockPos ahead = BlockPos.containing(getX() - Mth.sin(yaw) * 1.2, getY() + 0.01, getZ() + Mth.cos(yaw) * 1.2);
        int free = Math.min(freeAbove(feet), freeAbove(ahead));
        return (float) (free - (getY() - feet.getY()));
    }

    private int freeAbove(BlockPos feet) {
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int i = 0; i < (int) HEADROOM_CHECK; i++) {
            pos.setY(feet.getY() + i);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return i;
            }
        }
        return (int) HEADROOM_CHECK;
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (forcedPose() != AnimPose.AUTO) {
            holdForPose();
            return;
        }
        modeTicks++;
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (lungeCooldown > 0) {
            lungeCooldown--;
        }
        if (peekCooldown > 0) {
            peekCooldown--;
        }
        if (keepOutOfSafeRooms()) {
            return;
        }
        // Spread the sense checks of many Bacteria over different ticks.
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case WANDER, DRIFT -> tickCalm(senseNow);
            case SPOTTED -> tickSpotted();
            case CHASE -> tickChase();
            case SEARCH -> tickSearch(senseNow);
        }
    }

    /**
     * Test command: the pose is forced. It stands where it is (nothing else runs: no senses, no
     * attacks, no despawning) and only turns its head to the nearest player, so it can be filmed.
     */
    private void holdForPose() {
        getNavigation().stop();
        Player near = level().getNearestPlayer(this, POSE_LOOK_RANGE);
        if (near != null) {
            getLookControl().setLookAt(near, 10.0f, 10.0f);
        }
    }

    /**
     * Safe rooms are off limits: if the next step of its path (or the spot it stands on) is
     * inside one, it stops at the door. A hunt ends there as if it lost you (the "lost" sigh
     * tells you you made it). True if it stopped this tick.
     */
    private boolean keepOutOfSafeRooms() {
        Path path = getNavigation().getPath();
        boolean headingIn = path != null && !path.isDone() && SafeRooms.isSafe(level(), path.getNextNodePos());
        boolean inside = SafeRooms.isSafe(this);
        if (!headingIn && !inside) {
            return false;
        }
        getNavigation().stop();
        if (mode == Mode.SPOTTED || mode == Mode.CHASE || mode == Mode.SEARCH) {
            giveUp(true);
        } else if (mode == Mode.DRIFT) {
            setMode(Mode.WANDER);
        }
        if (inside && !anyPlayerCanSee()) {
            discard(); // somehow got in (e.g. pushed): it slips away unseen
            return true;
        }
        return inside;
    }

    private void tickCalm(boolean senseNow) {
        if (senseNow) {
            Player seen = findVisible();
            if (seen != null) {
                spot(seen);
                return;
            }
            Player heard = findHeard(HEAR_SPRINT);
            if (heard != null) {
                drift(heard.position(), SEARCH_SPEED);
            }
            if (tickLonely()) {
                return;
            }
        }

        // Folded over a kill, or peeking out from cover: it stays put (the senses above still
        // run, so it still notices you, and anything else it does ends this).
        if (feedTicks > 0 || peekTicks > 0) {
            getNavigation().stop();
            if (peekTicks > 0) {
                peekTicks--;
                if (peekAt != null && peekAt.isAlive()) {
                    getLookControl().setLookAt(peekAt, 10.0f, 10.0f);
                }
            } else {
                feedTicks--;
            }
            return;
        }

        if (mode == Mode.DRIFT) {
            if (getNavigation().isDone() || modeTicks > DRIFT_MAX_TICKS) {
                setMode(Mode.WANDER);
            }
            return;
        }

        // Standing about and someone is 8-24 blocks off, out of its sight: lean out and look.
        if (senseNow && peekCooldown <= 0 && getNavigation().isDone() && tryPeek()) {
            return;
        }

        // WANDER: now and then a rough guess at where someone is, otherwise short strolls.
        if (--nextGuessIn <= 0) {
            nextGuessIn = GUESS_MIN_TICKS + random.nextInt(GUESS_MAX_TICKS - GUESS_MIN_TICKS);
            // (Never someone hiding in a safe room or in furniture: it doesn't know they're there.)
            Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), GUESS_RANGE,
                    EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(entity -> !SafeRooms.isSafe(entity)
                            && !HidingService.isHiddenFrom(entity, this)));
            if (nearest != null) {
                double ox = (random.nextDouble() * 2.0 - 1.0) * GUESS_ERROR;
                double oz = (random.nextDouble() * 2.0 - 1.0) * GUESS_ERROR;
                drift(nearest.position().add(ox, 0.0, oz), DRIFT_SPEED);
                return;
            }
        }
        if (getNavigation().isDone() && random.nextInt(120) == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 10, 3);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED);
            }
        }
    }

    private void tickSpotted() {
        if (!isHuntable(prey)) {
            giveUp(false);
            return;
        }
        // Frozen, staring: this pause (plus the start of its long inhale) is the player's warning.
        // Its head stays on you (the model aims it exactly); the body turns after it.
        getNavigation().stop();
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (modeTicks >= SPOTTED_TICKS) {
            setMode(Mode.CHASE);
            repathIn = 0;
            pathGoal = null;
        }
    }

    private void tickChase() {
        if (prey != null && prey.isDeadOrDying()) {
            // Got them. No "lost" sigh: that sound must only ever mean "you escaped".
            // If it was holding them, it folds over the body for a moment first.
            boolean holding = distanceToSqr(prey) < CAGE_RELEASE * CAGE_RELEASE;
            giveUp(false);
            if (holding) {
                startFeeding();
            }
            return;
        }
        if (prey != null && SafeRooms.isSafe(prey)) {
            // You made it into a safe room: it gives up at once ("lost" sigh, quiet minute).
            giveUp(true);
            return;
        }
        if (!isHuntable(prey) || distanceToSqr(prey) > CHASE_GIVE_UP * CHASE_GIVE_UP) {
            startSearch();
            return;
        }
        if (getSensing().hasLineOfSight(prey)) {
            if (unseenTicks > 0) {
                repathIn = 0; // just got eyes on you again: react now
            }
            unseenTicks = 0;
            lastSeen = prey.position();
            getLookControl().setLookAt(prey, 30.0f, 30.0f);
            if (lungeTicks >= 0 || isCaging() || distanceToSqr(prey) < LUNGE_RANGE * LUNGE_RANGE) {
                // Close and in plain view: no more paths (they stop a block short), it goes for you.
                closeIn();
            } else if (--repathIn <= 0) {
                // Re-path at most twice a second, and only if you actually moved.
                repathIn = CHASE_REPATH_TICKS;
                if (pathGoal == null || pathGoal.distanceToSqr(lastSeen) > 2.25 || getNavigation().isDone()) {
                    getNavigation().moveTo(prey, CHASE_SPEED);
                    pathGoal = lastSeen;
                }
            }
            return;
        }
        // Lost sight of you: whatever its arms were doing, they let go.
        lungeTicks = -1;
        setCaged(false);
        // Can't see you: run to where you were LAST seen (not where you really are now).
        if (unseenTicks++ == 0) {
            getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, CHASE_SPEED);
            pathGoal = lastSeen;
        }
        if (unseenTicks >= LOST_SIGHT_TICKS) {
            startSearch();
        }
    }

    /**
     * Up close and in sight. It used to walk right into you, so your camera ended up inside its
     * body. Now it lunges (9 ticks: wind up, arms thrown wide, arms closing), stops 1.5-2 blocks
     * away facing you, and holds you in a cage of its long arms; the hits come from the arms.
     * Damage and the one-hit-a-second cooldown are the same as before.
     */
    private void closeIn() {
        getNavigation().stop();
        repathIn = 0;
        double dx = prey.getX() - getX();
        double dz = prey.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (lungeTicks < 0 && !isCaging() && lungeCooldown <= 0) {
            lungeTicks = 0;
            sendAction(Action.LUNGE);
        }
        if (d > STANDOFF_MAX) {
            // Not there yet: head for the spot STANDOFF short of you (a short burst while lunging).
            double k = (d - STANDOFF) / d;
            getMoveControl().setWantedPosition(getX() + dx * k, prey.getY(), getZ() + dz * k,
                    lungeTicks >= 0 ? LUNGE_SPEED : CHASE_SPEED);
        } else {
            // There: stand and face you with the whole body, so the arms close around you.
            float face = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            setYRot(Mth.approachDegrees(getYRot(), face, 30.0f));
            yBodyRot = getYRot();
            if (d < STANDOFF_MIN && d > 1.0e-3) {
                // You walked into it: it eases back (without turning round) to keep its distance.
                setDeltaMovement(getDeltaMovement().add(-dx / d * BACK_OFF, 0.0, -dz / d * BACK_OFF));
            }
        }

        boolean planted = false;
        if (lungeTicks >= 0 && ++lungeTicks >= LUNGE_TICKS) {
            lungeTicks = -1;
            lungeCooldown = LUNGE_COOLDOWN_TICKS;
            if (d <= CAGE_REACH) {
                // The arms slam down around you. The loudest sound of the whole attack lands
                // here, like in the reference video: its long inhale, deeper than when it spots you.
                setCaged(true);
                planted = true;
                nextWhipAt = tickCount + WHIP_GAP_MIN + random.nextInt(WHIP_GAP_MAX - WHIP_GAP_MIN);
                playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.75f + random.nextFloat() * 0.1f);
            }
        }
        if (!isCaging()) {
            return;
        }
        if (d > CAGE_RELEASE) {
            setCaged(false); // you broke free: it chases again (and may lunge again)
            return;
        }
        if (attackCooldown <= 0 && d <= CAGE_REACH && Math.abs(prey.getY() - getY()) < CAGE_REACH_UP) {
            attackCooldown = ATTACK_COOLDOWN_TICKS;
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(prey);
            if (!planted && tickCount >= nextWhipAt) {
                // Now and then (every 2-3 s, not every hit) an arm whips across your face with
                // the hit. The head's slow creep toward your face runs on its own in the model.
                nextWhipAt = tickCount + WHIP_GAP_MIN + random.nextInt(WHIP_GAP_MAX - WHIP_GAP_MIN);
                sendAction(Action.WHIP);
            }
        }
    }

    private void tickSearch(boolean senseNow) {
        if (senseNow) {
            Player found = findVisible();
            if (found == null) {
                found = findHeard(HEAR_SPRINT_SEARCHING);
            }
            if (found != null) {
                reacquire(found);
                return;
            }
        }
        // Quiet sniffing while it looks for you: you can hear that it is close.
        if (modeTicks % SEARCH_SOUND_TICKS == 1) {
            playSound(ModSounds.BACTERIA_SEARCH.get(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
        }
        if (getNavigation().isDone() && searchLegs < 2) {
            // After reaching the last-seen spot, check two random spots close by.
            searchLegs++;
            Vec3 to = DefaultRandomPos.getPos(this, 6, 2);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, SEARCH_SPEED);
            }
        }
        if (modeTicks >= SEARCH_TICKS) {
            giveUp(true);
        }
    }

    // ------------------------------------------------------------------ transitions

    private void spot(Player player) {
        prey = player;
        lastSeen = player.position();
        unseenTicks = 0;
        setTarget(player);
        getNavigation().stop();
        setMode(Mode.SPOTTED);
        entityData.set(PREY_ID, player.getId());
        // Its head snaps round onto you at once; the body only turns after it, about half a
        // second later (the body follows the head on its own, see getMaxHeadYRot).
        yHeadRot = (float) (Mth.atan2(player.getZ() - getZ(), player.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        getLookControl().setLookAt(player, 180.0f, 180.0f);
        playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
    }

    /**
     * How far its head may turn away from where its body faces: an owl's 150 degrees, not a
     * person's 75. Its head goes first; the body only turns after the head has held still for
     * half a second (vanilla's body control), so it turns its head right round to look at you,
     * then its body follows.
     */
    @Override
    public int getMaxHeadYRot() {
        return 150;
    }

    /** Found you again while searching: no freeze this time, but it still breathes in (the tell). */
    private void reacquire(Player player) {
        prey = player;
        lastSeen = player.position();
        unseenTicks = 0;
        repathIn = 0;
        pathGoal = null;
        setTarget(player);
        setMode(Mode.CHASE);
        entityData.set(PREY_ID, player.getId());
        playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void startSearch() {
        setMode(Mode.SEARCH);
        searchLegs = 0;
        getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, SEARCH_SPEED);
    }

    /** Back to wandering. {@code escaped}: someone got away, so play the "lost" sigh and give them a quiet minute. */
    private void giveUp(boolean escaped) {
        prey = null;
        setTarget(null);
        getNavigation().stop();
        setMode(Mode.WANDER);
        // No guessing for at least 30 s after losing someone.
        nextGuessIn = GUESS_MIN_TICKS + random.nextInt(GUESS_MAX_TICKS - GUESS_MIN_TICKS);
        if (escaped) {
            // The "lost" sigh tells players they made it.
            playSound(ModSounds.BACTERIA_LOST.get(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
            if (level() instanceof ServerLevel serverLevel) {
                BacteriaSpawner.quietAround(serverLevel, position());
            }
        }
    }

    private void drift(Vec3 to, double speed) {
        // A new noise right next to the current goal: keep the current path (no re-pathing).
        if (mode == Mode.DRIFT && pathGoal != null && pathGoal.distanceToSqr(to) < 9.0) {
            return;
        }
        setMode(Mode.DRIFT);
        pathGoal = to;
        getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    private void setMode(Mode next) {
        if (next != Mode.WANDER && (feedTicks > 0 || peekTicks > 0)) {
            // Something came up: stop feeding / peeking, and tell the clients to stop showing it.
            feedTicks = 0;
            peekTicks = 0;
            peekAt = null;
            sendAction(Action.NONE);
        }
        if (next != Mode.CHASE) {
            lungeTicks = -1;
            setCaged(false);
        }
        if (next != Mode.SPOTTED && next != Mode.CHASE) {
            entityData.set(PREY_ID, -1);
        }
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private void setCaged(boolean caged) {
        entityData.set(CAGED, caged);
    }

    /** Tells every client to play a one-shot move now (see {@link Action}). */
    private void sendAction(Action action) {
        actionCount = (actionCount + 1) & 0x07FFFFFF;
        entityData.set(ACTION, actionCount << 4 | action.ordinal());
    }

    /** It killed the one it was holding: it folds over them for {@link #FEED_TICKS}. */
    private void startFeeding() {
        feedTicks = FEED_TICKS;
        getNavigation().stop();
        sendAction(Action.FEED);
    }

    /**
     * Peeking (Kane Pixels' doorway scene): someone is 8-24 blocks away and it can't see them, but
     * leaning about a block to one side would. Then it turns their way and leans out from behind
     * the pillar or door frame for a few seconds (the model does the leaning; its hitbox and its
     * senses stay where they are, so this changes nothing in the game, it is only seen). At most
     * a couple of ray checks once a second, and only when someone is in that range.
     */
    private boolean tryPeek() {
        peekCooldown = PEEK_RETRY_TICKS;
        Player target = null;
        double best = PEEK_MAX * PEEK_MAX;
        for (Player player : level().players()) {
            double distance = distanceToSqr(player);
            if (distance >= PEEK_MIN * PEEK_MIN && distance < best && isHuntable(player)) {
                target = player;
                best = distance;
            }
        }
        if (target == null || getSensing().hasLineOfSight(target)) {
            return false;
        }
        Vec3 eye = getEyePosition();
        Vec3 goal = target.getEyePosition();
        float face = (float) (Mth.atan2(goal.z - getZ(), goal.x - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        float rad = face * Mth.DEG_TO_RAD;
        int first = random.nextBoolean() ? 1 : -1;
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? first : -first;
            // Its left when facing them is (cos, 0, sin) of its yaw; side -1 is its right.
            Vec3 out = eye.add(Mth.cos(rad) * side * PEEK_REACH, 0.0, Mth.sin(rad) * side * PEEK_REACH);
            if (clearLine(eye, out) && clearLine(out, goal)) {
                setYRot(face);
                yBodyRot = face;
                yHeadRot = face;
                boolean hold = random.nextFloat() < PEEK_HOLD_CHANCE;
                peekTicks = hold ? PEEK_HOLD_TICKS : PEEK_TICKS;
                peekAt = target;
                peekCooldown = PEEK_COOLDOWN_MIN + random.nextInt(PEEK_COOLDOWN_MAX - PEEK_COOLDOWN_MIN);
                sendAction(side > 0 ? (hold ? Action.PEEK_LEFT_HOLD : Action.PEEK_LEFT)
                        : (hold ? Action.PEEK_RIGHT_HOLD : Action.PEEK_RIGHT));
                return true;
            }
        }
        return false;
    }

    private boolean clearLine(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** Ticks the "nobody around" timer. True if the Bacteria despawned. */
    private boolean tickLonely() {
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), LONELY_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        lonelyTicks = near == null ? lonelyTicks + SENSE_INTERVAL : 0;
        if (lonelyTicks >= LONELY_DESPAWN_TICKS && !anyPlayerCanSee()) {
            discard(); // never vanish in front of someone
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ senses

    /** Closest player it can actually spot right now, or null. */
    @Nullable
    private Player findVisible() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : level().players()) {
            if (!isHuntable(player)) {
                continue;
            }
            double distance = distanceToSqr(player);
            if (distance < bestDistance && distance < SENSE_RANGE * SENSE_RANGE && canSpot(player, distance)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Closest sprinting player within {@code range}, heard through walls. */
    @Nullable
    private Player findHeard(double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : level().players()) {
            if (isHuntable(player) && player.isSprinting()) {
                double distance = distanceToSqr(player);
                if (distance < bestDistance) {
                    best = player;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * Light decides how far it sees you, the flashlight does not: in a lit room you are exposed,
     * in the dark you are nearly invisible. The cheap distance test runs before the raycast.
     */
    private boolean canSpot(Player player, double distanceSqr) {
        double range = isLit(player) ? SIGHT_LIT : SIGHT_DARK;
        if (player.isCrouching()) {
            range *= CROUCH_FACTOR;
        }
        return distanceSqr <= range * range && getSensing().hasLineOfSight(player);
    }

    /**
     * "In the light" = the lamps light the spot where your head is. A blackout turns every lamp
     * off for this purpose, even though the light blocks themselves stay in the world.
     */
    private boolean isLit(Player player) {
        // Standing in a burning flare's light is like standing under a lamp (blackout or not).
        if (FlareEntity.litByFlare(level(), player.getEyePosition())) {
            return true;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level().dimension());
        if (floor != null && LevelEvents.isBlackout(floor)) {
            return false;
        }
        return level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) >= LIT_LEVEL;
    }

    /**
     * Someone it may notice and chase. Players inside a safe room are out of its reach: it can't
     * see or hear them. Nor someone hiding in furniture, unless it saw them get in.
     */
    private boolean isHuntable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player)
                && !HidingService.isHiddenFrom(player, this);
    }

    /** It dragged someone out of their hiding place: straight into a chase (it saw them get in). */
    @Override
    public void onPulledOut(ServerPlayer player) {
        reacquire(player);
    }

    private boolean anyPlayerCanSee() {
        for (Player player : level().players()) {
            if (!player.isSpectator() && player.distanceToSqr(this) < 64.0 * 64.0 && player.hasLineOfSight(this)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Hitting it is the surest way to get its attention. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof Player attacker && isHuntable(attacker)) {
            if (mode == Mode.SEARCH) {
                reacquire(attacker);
            } else if (mode == Mode.WANDER || mode == Mode.DRIFT) {
                spot(attacker);
            }
        }
        return hurt;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BACTERIA_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BACTERIA_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BACTERIA_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.BACTERIA_STEP.get(), isChasing() ? 0.9f : 0.4f, 0.9f + random.nextFloat() * 0.25f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
