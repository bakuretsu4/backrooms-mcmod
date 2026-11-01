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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
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
 * WANDER  --sees you-->           SPOTTED --8 ticks--> CHASE --3 s without seeing you--> SEARCH
 * WANDER  --hears a sprint / every 30-60 s a rough guess--> DRIFT --arrives--> WANDER
 * SEARCH  --sees or hears you--> CHASE      SEARCH --8 s, nothing--> WANDER ("lost" sigh)
 * </pre>
 * The mode is synced to clients so the model can pose and the camcorder can glitch.
 *
 * <p>Up close it never walks into you (your camera used to end up inside its body). Within
 * {@link #LUNGE_RANGE} it brakes to a stop, stares, then leaps to 1.5-2 blocks away and closes its long
 * arms around you like a cage; the hits come from the arms ({@link #closeIn()}). After a kill it folds over its
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
    public enum Action { NONE, LUNGE, WHIP, FEED, PEEK_LEFT, PEEK_RIGHT, PEEK_LEFT_HOLD, PEEK_RIGHT_HOLD, STARE, SWIPE }

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
     * Between spotting you and running at you: 8 ticks. No freeze: its head snaps onto you, it
     * bends over to look and swings round to face you, then it runs at once (as in the reference
     * footage). The model times the turn to this, so it is public.
     */
    public static final int SPOTTED_TICKS = 8;
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
    // Like the reference footage: it runs in, brakes to a stop in 2-3 ticks in a wide stance with
    // both claws planted out to the sides, stares at you for 3-6 ticks without moving, then
    // leaps at you in 3 ticks with its arms flung straight out, into the cage of arms.
    /** The stare before the leap, a random length between these (ticks). */
    private static final int STARE_MIN_TICKS = 3;
    private static final int STARE_MAX_TICKS = 6;
    /** The test command's stare before each leap (ticks). */
    public static final int POSE_STARE_TICKS = 5;
    /** The leap: ticks from leaving the floor to closing its arms round you. */
    public static final int LUNGE_TICKS = 3;
    /** The leap covers the gap in its last 2 ticks, at most this far per tick (blocks). */
    private static final double LEAP_STEP_MAX = 1.0;
    /** After a leap that missed, it waits this long before the next one. */
    private static final int LUNGE_COOLDOWN_TICKS = 20;
    /** Starting to run, it is kicked up to this speed (blocks per tick) over KICK_TICKS: full speed in about 3 ticks. */
    private static final double RUN_KICK = 0.27;
    /**
     * Its body stops this far from you (blocks, centre to centre) and its arms do the rest, so
     * your camera is never inside it. It keeps between the MIN and MAX distance. (Its arms are
     * shorter at the drawn size, BODY_SCALE, so it holds you from 1.5 blocks: from further away its
     * claws couldn't reach the floor beside you.)
     */
    private static final double STANDOFF = 1.5;
    private static final double STANDOFF_MIN = 1.4;
    private static final double STANDOFF_MAX = 1.75;
    /** How far its arms reach (centre to centre): the cage closes and the hits land from here. */
    private static final double CAGE_REACH = 1.9;
    /** ...and no more than this far above or below it. */
    private static final double CAGE_REACH_UP = 2.0;
    /** Get this far away and you have broken out of its arms. */
    private static final double CAGE_RELEASE = 2.6;
    /** If you walk into it, it eases back this fast (added to its speed each tick). */
    private static final double BACK_OFF = 0.08;
    /** While it holds you, an arm whips across your face at most every 40-60 ticks. */
    private static final int WHIP_GAP_MIN = 40;
    private static final int WHIP_GAP_MAX = 60;
    /** Reaching a noise it heard, it swipes at the spot if it gets this close (blocks). */
    private static final double NOISE_SWIPE_RANGE = 2.5;
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
    /**
     * How big it is drawn, compared with the model's own pixels: 0.75 makes it 2.5 blocks tall
     * standing straight (the model itself is 3.4), so its head clears the 3-block ceilings of
     * Level 0 even mid-stride. The hitbox (ModEntities) matches. Anything the animation measures
     * in the world (stride length, where its head and claws are) is scaled by this too.
     */
    public static final float BODY_SCALE = 0.75f;
    /** Test command with "loop": the one-shot moves replay this often (2 s). */
    public static final int POSE_LOOP_TICKS = 40;
    /** While its pose is forced it looks at the nearest player within this range. */
    private static final double POSE_LOOK_RANGE = 32.0;
    /** While calm, its head locks onto a player it can see within this range (client only). */
    private static final double WATCH_RANGE = 24.0;
    /** The client re-checks who it is watching this often (ticks): a ray check each time. */
    private static final int WATCH_CHECK_TICKS = 5;
    // The stride (pass 6: long, loping strides like the smooth reference animations). Its feet
    // travel this far per full stride (both feet): 2.0 blocks walking, 2.6 running, and in
    // between the stride length follows the speed smoothly, so there is no jump in cadence going
    // from a walk to a run. At its 2 blocks/s walk that is a 20-tick stride (1 second); at its
    // 6.5 blocks/s run an 8-tick one (the arms take two strides, 16 ticks, for one swing).
    public static final float WALK_STRIDE_BLOCKS = 2.0f;
    public static final float RUN_STRIDE_BLOCKS = 2.6f;
    /** Its walk and run speeds in blocks per tick (4.15 x 0.69^2 and 4.15 x 1.25^2 blocks/s, / 20). */
    public static final float WALK_SPEED_PER_TICK = 0.0988f;
    public static final float RUN_SPEED_PER_TICK = 0.3242f;
    /** Running, each step is up to this much longer or shorter (a fraction), so it isn't a metronome. */
    private static final float RUN_STEP_JITTER = 0.05f;
    /**
     * The client's speed reading is eased by this much of the difference each tick, so the
     * stride doesn't wobble with the position updates that come from the server in steps.
     */
    private static final float GAIT_SPEED_EASE = 0.3f;
    /**
     * Body turning: the drawn body follows the way it faces (yRot) like a damped spring with this
     * stiffness (per tick: it trails a turn by a couple of ticks and settles a 90-degree turn in
     * about 6, easing in and out),
     * never faster than BODY_TURN_MAX degrees a tick. Its own steering is capped at
     * MOVE_TURN_MAX degrees a tick (vanilla: 90).
     */
    private static final float BODY_TURN_STIFFNESS = 0.6f;
    private static final float BODY_TURN_MAX = 25.0f;
    private static final float MOVE_TURN_MAX = 30.0f;
    /** Turning on the spot to face you: this fraction of the way each tick, at most FACE_TURN_MAX degrees. */
    private static final float FACE_TURN_EASE = 0.5f;
    private static final float FACE_TURN_MAX = 25.0f;
    /** Stopping: each tick its speed is cut to this fraction, so it stops in 2-3 ticks instead of one. */
    private static final double BRAKE = 0.35;
    /** Starting to run, its kick toward you builds up over this many ticks. */
    private static final int KICK_TICKS = 3;
    // Quiet sounds the client plays itself (volume; they fade out within 12 blocks).
    private static final float CLICK_VOLUME = 0.32f;
    private static final float SCRAPE_VOLUME = 0.28f;
    /** Chance per footfall while walking that the claws scrape the floor. */
    private static final float SCRAPE_CHANCE = 0.3f;
    /**
     * The client checks this far (blocks) to each side for walls, so running arms plant ahead
     * instead: as far as the long spider-leg arms plant their claws (about 2.1-2.45 blocks out).
     */
    private static final double NARROW_CHECK = 2.4;

    // --- Speeds (multipliers of MOVEMENT_SPEED) ----------------------------------------------
    // How fast a mob goes: each tick it is pushed by (attribute x multiplier)^2 x 0.98, and the
    // floor's grip keeps 0.546 of its speed, so it settles at push / (1 - 0.546): for our 0.31
    // that is 4.15 x multiplier^2 blocks/s on a normal floor. Your Backrooms walk is 3.5 blocks/s
    // and your sprint 5.6: when it runs, you can't outrun it, only break its line of sight.
    private static final double CHASE_SPEED = 1.25;   // 6.5 blocks/s, the reference's run
    private static final double SEARCH_SPEED = 0.75;  // 2.3 blocks/s
    private static final double DRIFT_SPEED = 0.69;   // 2.0 blocks/s, the reference's walk
    private static final double WANDER_SPEED = 0.69;  // 2.0 blocks/s

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
    private int stareTicks = -1;
    private int lungeCooldown;
    private int nextWhipAt;
    private int feedTicks;
    private int peekTicks;
    private int peekCooldown = PEEK_COOLDOWN_MIN;
    @Nullable
    private Player peekAt;
    private int actionCount;
    // Starting to run: ticks of the kick still to come (server side).
    private int kickTicks;

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
    // Client-only, purely visual: the stride, counted in strides so far (same idea as the heart),
    // and the eased speed (blocks per tick) that drives it.
    private float gaitPhase;
    private float gaitPhaseO;
    private float gaitSpeed;
    private float gaitSpeedO;
    // Client-only: when (in this client's ticks) the latest move, mode and forced pose arrived,
    // so the model can time them.
    private Action shownAction = Action.NONE;
    private int actionTick = -1000;
    private Mode shownMode = Mode.WANDER;
    private int modeTick = -1000;
    private int forcedTick;
    // Client-only, purely visual: the per-frame pose memory (head aim, arm blend, walls nearby).
    public final BacteriaPoseState animState = new BacteriaPoseState();
    // Client-only: the footfall the scrape sound last checked.
    private int lastFootfall;
    private final BlockPos.MutableBlockPos wallCheck = new BlockPos.MutableBlockPos();

    public Bacteria(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        // Its own steering: like vanilla's, but it turns at most MOVE_TURN_MAX degrees a tick.
        this.moveControl = new SmoothMoveControl(this);
    }

    /**
     * Its own body turning (vanilla's snaps the body to the way it is going while it moves and
     * hardly turns it at all while it stands, which made the body jump round by up to 180
     * degrees when a run began). This one, on the server and on every client, eases the drawn
     * body toward the way the entity faces, moving or not, like a damped spring.
     */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new SmoothBodyControl(this);
    }

    /** See {@link #createBodyControl()}. Made by Mob's constructor, so it only keeps the mob. */
    private static final class SmoothBodyControl extends BodyRotationControl {
        private final Mob mob;
        // How fast the body is turning now (degrees per tick).
        private float turnSpeed;

        SmoothBodyControl(Mob mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public void clientTick() {
            // A critically damped spring: it starts turning gently, speeds up, and settles onto
            // the new facing without overshooting.
            float error = Mth.wrapDegrees(mob.getYRot() - mob.yBodyRot);
            float k = BODY_TURN_STIFFNESS;
            turnSpeed += k * k * error - 2.0f * k * turnSpeed;
            turnSpeed = Mth.clamp(turnSpeed, -BODY_TURN_MAX, BODY_TURN_MAX);
            mob.yBodyRot += turnSpeed;
            // Like vanilla, the head may not be turned further than its limit from the body.
            mob.yHeadRot = Mth.rotateIfNecessary(mob.yHeadRot, mob.yBodyRot, mob.getMaxHeadYRot());
        }
    }

    /** Vanilla steering, but turning at most {@link #MOVE_TURN_MAX} degrees a tick toward the path (vanilla: 90). */
    private static final class SmoothMoveControl extends MoveControl {
        SmoothMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        protected float rotlerp(float sourceAngle, float targetAngle, float maximumChange) {
            return super.rotlerp(sourceAngle, targetAngle, Math.min(maximumChange, MOVE_TURN_MAX));
        }
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

    /** Client: how fast it moves (blocks per tick), eased, for this frame. Drives the stride and the leg fade. */
    public float gaitSpeed(float partialTick) {
        return Mth.lerp(partialTick, gaitSpeedO, gaitSpeed);
    }

    /**
     * How far its feet travel per full stride (blocks) at {@code speed} blocks per tick: 2.0 at
     * its walk and below, 2.6 at its run and above, and in between in proportion, so the cadence
     * changes smoothly with the speed.
     */
    public static float strideBlocks(float speed) {
        float run = Mth.clamp((speed - WALK_SPEED_PER_TICK) / (RUN_SPEED_PER_TICK - WALK_SPEED_PER_TICK), 0.0f, 1.0f);
        return Mth.lerp(run, WALK_STRIDE_BLOCKS, RUN_STRIDE_BLOCKS);
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
            if (!firstTick && shownAction == Action.SWIPE) {
                playClick(1.0f); // the arm's joints crack as it whips round
            }
        } else if (MODE.equals(key)) {
            Mode now = mode();
            if (firstTick) {
                shownMode = now;
                modeTick = tickCount - 1000;
            } else if (now != shownMode) {
                shownMode = now;
                modeTick = tickCount;
                if (now == Mode.SPOTTED) {
                    // Its head snaps round onto you: one dry click of bone.
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
            senseWalls();
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
     * The stride clock. The client's reading of how fast it moves is eased (GAIT_SPEED_EASE), so
     * the stride doesn't wobble with the position updates that come from the server in steps, and
     * its feet travel {@link #strideBlocks} per stride, so the stride runs as fast as it really
     * moves: a 20-tick stride at its 2 blocks/s walk, an 8-tick one at its 6.5 blocks/s run (every
     * step a little longer or shorter, so it isn't a metronome). Stopping, the eased speed dies
     * away over a few ticks, so the legs finish their step and settle instead of freezing in the
     * air (the model fades them out with the same eased speed). On the test command's forced walk
     * and run it plays on the spot at those two speeds.
     */
    private void tickGait() {
        AnimPose forced = forcedPose();
        Mode current = mode();
        boolean running = forced == AnimPose.CHASE || forced == AnimPose.LUNGE
                || (forced == AnimPose.AUTO && current == Mode.CHASE);
        gaitPhaseO = gaitPhase;
        gaitSpeedO = gaitSpeed;
        float measured;
        if (forced == AnimPose.WALK) {
            measured = WALK_SPEED_PER_TICK;
        } else if (forced == AnimPose.CHASE || forced == AnimPose.LUNGE || forced == AnimPose.SPOTTED) {
            measured = RUN_SPEED_PER_TICK;
        } else if (forced != AnimPose.AUTO) {
            measured = 0.0f;
        } else {
            double dx = getX() - xo;
            double dz = getZ() - zo;
            measured = (float) Math.sqrt(dx * dx + dz * dz);
        }
        gaitSpeed += (measured - gaitSpeed) * GAIT_SPEED_EASE;
        float rate = gaitSpeed / strideBlocks(gaitSpeed);
        if (running) {
            int step = Mth.floor(gaitPhase * 2.0f);
            rate *= 1.0f + RUN_STEP_JITTER * BacteriaRhythm.hash(BacteriaRhythm.seed(getId()) + 301, step);
        }
        gaitPhase += rate;
        if (gaitPhase > 1000.0f) {
            gaitPhase -= 1000.0f;
            gaitPhaseO -= 1000.0f;
        }
        // Walking with its claws on the floor, now and then a footfall drags them with a scrape.
        int footfall = Mth.floor(gaitPhase * 2.0f);
        if (footfall != lastFootfall) {
            lastFootfall = footfall;
            boolean walking = forced == AnimPose.WALK || (forced == AnimPose.AUTO && !running && gaitSpeed > 0.02f);
            if (walking && random.nextFloat() < SCRAPE_CHANCE) {
                level().playLocalSound(getX(), getY(), getZ(), ModSounds.BACTERIA_SCRAPE.get(), SoundSource.HOSTILE,
                        SCRAPE_VOLUME, 0.85f + random.nextFloat() * 0.3f, false);
            }
        }
    }

    /**
     * Client: is there a wall within {@link #NARROW_CHECK} blocks to its left or right (at knee
     * and chest height)? Running down a corridor its claws then plant ahead instead of out to that
     * side (into the wall), like the reference in a narrow hall. Two block checks per side per
     * tick, eased over a few ticks.
     */
    private void senseWalls() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        // Its left, flat on the ground (its right is the opposite way).
        double lx = Mth.cos(yaw);
        double lz = Mth.sin(yaw);
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? -1.0 : 1.0; // 0 = its right, 1 = its left, like the arms
            boolean wall = false;
            for (int h = 0; h < 2 && !wall; h++) {
                wallCheck.set(getX() + lx * side * NARROW_CHECK, getY() + 0.5 + h, getZ() + lz * side * NARROW_CHECK);
                wall = !level().getBlockState(wallCheck).getCollisionShape(level(), wallCheck).isEmpty();
            }
            animState.narrowO[i] = animState.narrow[i];
            animState.narrow[i] += ((wall ? 1.0f : 0.0f) - animState.narrow[i]) * 0.35f;
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
        if ((forcedLoop() ? age % POSE_LOOP_TICKS : age) == POSE_STARE_TICKS + LUNGE_TICKS) {
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
     * frame. It is drawn 2.5 blocks tall ({@link #BODY_SCALE}), so it only has to stoop under
     * ceilings lower than that; the model uses this to stoop instead of poking through.
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
            if (getNavigation().isDone() && pathGoal != null && distanceToSqr(pathGoal) < NOISE_SWIPE_RANGE * NOISE_SWIPE_RANGE) {
                // It got to the noise it heard: it brakes to a stop and swipes at the spot.
                brake();
                sendAction(Action.SWIPE);
                setMode(Mode.WANDER);
            } else if (getNavigation().isDone() || modeTicks > DRIFT_MAX_TICKS) {
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

    /**
     * It has seen you: no freeze, it turns. Its head snaps onto you at once, its body swings round
     * to face you (30 degrees a tick, so even a full turn takes 6 ticks) while the model bends it
     * over to look, and after {@link #SPOTTED_TICKS} it runs, at full speed straight away.
     */
    private void tickSpotted() {
        if (!isHuntable(prey)) {
            giveUp(false);
            return;
        }
        getNavigation().stop();
        float face = faceToward(prey, 30.0f);
        yHeadRot = face;
        getLookControl().setLookAt(prey, 180.0f, 180.0f);
        if (modeTicks >= SPOTTED_TICKS) {
            setMode(Mode.CHASE);
            repathIn = 0;
            pathGoal = null;
            runKick();
        }
    }

    private void tickChase() {
        // The rest of the run's kick, if it has only just started running (see runKick).
        applyKick();
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
            if (lungeTicks >= 0 || stareTicks >= 0 || isCaging() || distanceToSqr(prey) < LUNGE_RANGE * LUNGE_RANGE) {
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
        stareTicks = -1;
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
     * Up close and in sight, like the reference footage: it brakes to a stop in 2-3 ticks, both claws
     * planted wide, and stares at you for 3-6 ticks without moving. Then the leap: its arms fling
     * straight out (1 tick) and it covers the gap to 1.75 blocks from you in the next 2, and
     * closes its arms round you: the cage. The body stays 1.5-2 blocks away while it holds you,
     * so your camera is never inside it; the hits come from the arms. If the leap misses, it
     * runs in and swipes at you with one arm until it can leap again. Damage and the
     * one-hit-a-second cooldown are the same as always.
     */
    private void closeIn() {
        getNavigation().stop();
        repathIn = 0;
        double dx = prey.getX() - getX();
        double dz = prey.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);

        if (lungeTicks >= 0) {
            // The leap: tick 1 the arms fling out, ticks 2-3 it covers the gap.
            faceToward(prey, 60.0f);
            lungeTicks++;
            double step = 0.0;
            if (lungeTicks >= 2 && d > 1.0e-3) {
                int ticksLeft = LUNGE_TICKS - lungeTicks + 1;
                step = Mth.clamp((d - STANDOFF) / ticksLeft, 0.0, LEAP_STEP_MAX);
                setDeltaMovement(dx / d * step, getDeltaMovement().y, dz / d * step);
            } else {
                brake();
            }
            if (lungeTicks >= LUNGE_TICKS) {
                lungeTicks = -1;
                lungeCooldown = LUNGE_COOLDOWN_TICKS;
                if (d - step <= CAGE_REACH) {
                    // The arms slam down around you. The loudest sound of the whole attack lands
                    // here, like in the reference video: its long inhale, deeper than when it spots you.
                    setCaged(true);
                    nextWhipAt = tickCount + WHIP_GAP_MIN + random.nextInt(WHIP_GAP_MAX - WHIP_GAP_MIN);
                    playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.75f + random.nextFloat() * 0.1f);
                }
            }
            return;
        }
        if (stareTicks >= 0) {
            // The stare: dead still, facing you.
            brake();
            faceToward(prey, 30.0f);
            if (--stareTicks < 0) {
                if (d <= LUNGE_RANGE + 1.5) {
                    lungeTicks = 0;
                    sendAction(Action.LUNGE);
                } else {
                    lungeCooldown = LUNGE_COOLDOWN_TICKS; // you got away during the stare
                }
            }
            return;
        }
        if (!isCaging() && lungeCooldown <= 0) {
            // It brakes to a stop and plants its claws: the stare begins.
            stareTicks = STARE_MIN_TICKS + random.nextInt(STARE_MAX_TICKS - STARE_MIN_TICKS + 1);
            brake();
            sendAction(Action.STARE);
            return;
        }

        if (d > STANDOFF_MAX) {
            // Not there: head for the spot STANDOFF short of you.
            double k = (d - STANDOFF) / d;
            getMoveControl().setWantedPosition(getX() + dx * k, prey.getY(), getZ() + dz * k, CHASE_SPEED);
        } else {
            // There: stand and face you with the whole body, so the arms close around you.
            faceToward(prey, 30.0f);
            if (d < STANDOFF_MIN && d > 1.0e-3) {
                // You walked into it: it eases back (without turning round) to keep its distance.
                setDeltaMovement(getDeltaMovement().add(-dx / d * BACK_OFF, 0.0, -dz / d * BACK_OFF));
            }
        }
        if (isCaging() && d > CAGE_RELEASE) {
            setCaged(false); // you broke free: it chases again (and may leap again)
            return;
        }
        if (attackCooldown <= 0 && d <= CAGE_REACH && Math.abs(prey.getY() - getY()) < CAGE_REACH_UP) {
            attackCooldown = ATTACK_COOLDOWN_TICKS;
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(prey);
            if (!isCaging()) {
                // Not holding you (the leap missed): one arm swipes across in front of it.
                sendAction(Action.SWIPE);
            } else if (tickCount >= nextWhipAt) {
                // Holding you: now and then (every 2-3 s, not every hit) an arm whips across your
                // face with the hit. The head's slow creep toward your face runs in the model.
                nextWhipAt = tickCount + WHIP_GAP_MIN + random.nextInt(WHIP_GAP_MAX - WHIP_GAP_MIN);
                sendAction(Action.WHIP);
            }
        }
    }

    /**
     * Turns its whole body toward {@code target}: part of the way each tick (FACE_TURN_EASE), so
     * the turn slows as it lines up, and at most {@code maxStep} (and FACE_TURN_MAX) degrees a
     * tick. The drawn body follows through the body control. Returns the facing it wants.
     */
    private float faceToward(Player target, float maxStep) {
        float face = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        float error = Mth.wrapDegrees(face - getYRot());
        float limit = Math.min(maxStep, FACE_TURN_MAX);
        float step = Math.abs(error) < 1.0f ? error : Mth.clamp(error * FACE_TURN_EASE, -limit, limit);
        setYRot(getYRot() + step);
        return face;
    }

    /** Brakes hard: its speed is cut to BRAKE each tick it is called, so it stops in 2-3 ticks without sliding on. */
    private void brake() {
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * BRAKE, v.y, v.z * BRAKE);
    }

    /** Starting to run: a kick toward you that builds up over KICK_TICKS ticks to most of its running speed. */
    private void runKick() {
        kickTicks = KICK_TICKS;
        applyKick();
    }

    /** One tick of the run's kick: a third, two thirds, then all of RUN_KICK (never slowing it down). */
    private void applyKick() {
        if (kickTicks <= 0 || prey == null) {
            kickTicks = 0;
            return;
        }
        double dx = prey.getX() - getX();
        double dz = prey.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double want = RUN_KICK * (KICK_TICKS - kickTicks + 1) / KICK_TICKS;
        kickTicks--;
        Vec3 v = getDeltaMovement();
        if (d > 1.0e-3 && v.x * v.x + v.z * v.z < want * want) {
            setDeltaMovement(dx / d * want, v.y, dz / d * want);
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
        // Its head snaps round onto you at once; the body swings round after it (tickSpotted).
        yHeadRot = (float) (Mth.atan2(player.getZ() - getZ(), player.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        getLookControl().setLookAt(player, 180.0f, 180.0f);
        playSound(ModSounds.BACTERIA_SCREECH.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
    }

    /**
     * How far its head may turn away from where its body faces: an owl's 150 degrees, not a
     * person's 75. Its head goes first; its body follows (and when it is still, vanilla's body
     * control turns the body after the head has held still for half a second).
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
        runKick();
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
            stareTicks = -1;
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
