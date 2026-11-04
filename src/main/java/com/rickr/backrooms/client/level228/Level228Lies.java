package com.rickr.backrooms.client.level228;

import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Reflections that lie (Level 228): YOUR personal hallucinations. Rarely (every one to three
 * minutes, more often the longer you stay), one of your own reflections close by stops being
 * quite yours:
 * <ul>
 *   <li>{@link #LOOK}: you have been standing still for a while; one reflection whose face is
 *       turned away from you slowly turns its head to look at you, holds, and slowly turns back.
 *       No sound, no snap. It turns back early (still slowly) if you move.</li>
 *   <li>{@link #LAG}: while you walk, it replays your movements most of a second late, then
 *       quietly catches up.</li>
 *   <li>{@link #WALK_ON}: you stop; it doesn't. It takes a few more steps in the direction you
 *       were going (inside its own space: it stops at walls and glass), then just stands there.
 *       The next time you look away for a moment it is back where it belongs.</li>
 *   <li>{@link #STOP}: you keep walking; it stops and stays standing where it was. Look away and
 *       back: it is gone, and that reflection stays empty for a while.</li>
 *   <li>{@link #VANISH}: one of your reflections is simply not there (the others are), picked
 *       out of sight so you only notice when you turn. It comes back while you aren't looking.</li>
 * </ul>
 * Rules: never a jump scare (nothing comes at you, no sound, no flash, nothing appears in front of
 * you); never on a puzzle mirror, never in the final chamber or the exit hallway (the caller
 * passes {@code allowed}); only your own reflections (teammates are never misrepresented to you);
 * client-only, so it can't touch anyone else's view or the shared puzzle. "Nonexistent room" and
 * "hidden passage" mirrors (PlanAgent's sealed images) attract the lies a little: a reflection that
 * walks on into a room that isn't there is the point of them.
 *
 * <p>An image is identified across frames by its mirror map: image = T + F * real, with F the
 * flips and T a translation that doesn't change as you move ({@code T = img - F * you}).
 * Everything is in static fields and fixed arrays: nothing allocates per frame or tick.
 */
public final class Level228Lies {
    public static final int NONE = 0;
    public static final int LOOK = 1;
    public static final int LAG = 2;
    public static final int WALK_ON = 3;
    public static final int STOP = 4;
    public static final int VANISH = 5;
    /** After STOP: that reflection shows nothing of you. */
    private static final int EMPTY = 6;

    /** A lie only starts on a reflection at most this far from the camera (blocks). */
    private static final double CLOSE = 12.0;
    /** Head-turn: a neck turns this far at most from the body (degrees), and it is only worth it from MIN_TURN on. */
    private static final float MAX_NECK = 75.0f;
    private static final float MIN_TURN = 35.0f;
    /** Head-turn timing (ticks): the slow turn, the hold, the slow return. */
    private static final int TURN_TICKS = 50;
    private static final int HOLD_MIN = 60;
    private static final int HOLD_MAX = 110;
    /** Standing still this long (ticks) before a reflection may turn its head. */
    private static final int STILL_BEFORE_LOOK = 60;
    /** Between lies (seconds), before escalation. */
    private static final float MIN_GAP = 60.0f;
    private static final float MAX_GAP = 180.0f;
    /** Full escalation after this long in the level (ticks): 30 minutes. */
    private static final float ESCALATE_TICKS = 20.0f * 60.0f * 30.0f;
    /** A pending lie that found no reflection to happen to in this long (ticks) is re-rolled. */
    private static final int PENDING_TIMEOUT = 20 * 60;
    /** Unseen ticks after which a stopped reflection is gone / an empty one quietly returns. */
    private static final int GONE_UNSEEN = 10;
    private static final int RETURN_UNSEEN = 30;
    /** The lie ends if you get this far from its image (blocks). */
    private static final double FAR = 28.0;

    private static final Random RANDOM = new Random();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();

    // --- Schedule -----------------------------------------------------------------------------
    private static int ticksInLevel;
    private static int nextLieTick = -1;
    private static int pending;
    private static int pendingAge;
    private static boolean forced;

    // --- You (tick) -----------------------------------------------------------------------------
    private static int stillTicks;
    private static int movingTicks;
    private static double lastX;
    private static double lastZ;
    private static boolean hasLast;
    /** Your last walking step (blocks per tick), remembered for WALK_ON. */
    private static double stepX;
    private static double stepZ;

    // --- The lie in progress --------------------------------------------------------------------
    private static int state;
    private static int age;
    private static int keyFx;
    private static int keyFz;
    private static int keyTx;
    private static int keyTz;
    private static double tX;
    private static double tZ;
    private static boolean seenFlag;
    private static int unseen;
    private static double imgX;
    private static double imgZ;

    // LOOK
    private static float lookW;
    private static float lookWO;
    private static int lookHold;
    private static boolean lookReturning;
    // LAG
    private static float lagDelay;
    private static int lagDuration;
    // WALK_ON / STOP: the reflection's own body, in image space.
    private static double fx;
    private static double fy;
    private static double fz;
    private static double fxO;
    private static double fzO;
    private static float fBody;
    private static float fHead;
    private static float fPitch;
    private static float fWalkPos;
    private static float fWalkPosO;
    private static float fWalkSpeed;
    private static float fWalkSpeedO;
    private static boolean fCrouch;
    private static double walkVx;
    private static double walkVz;
    private static double walkLeft;
    private static float fade;
    private static float fadeO;
    // VANISH
    private static int vanishFor;

    // --- This frame's best candidate ------------------------------------------------------------
    private static boolean candValid;
    private static double candScore;
    private static int cFx;
    private static int cFz;
    private static double cTx;
    private static double cTz;
    private static double cX;
    private static double cY;
    private static double cZ;
    private static float cBody;
    private static float cHead;
    private static float cPitch;
    private static float cWalkPos;
    private static float cWalkSpeed;
    private static boolean cCrouch;
    private static int imagesThisFrame;
    /** From the last tick: lies may happen where you are. */
    private static boolean allowedHere;

    private Level228Lies() {}

    // ==========================================================================================
    // Debug
    // ==========================================================================================

    /** {@code /backrooms mirror lie ...}: this lie next (NONE: end the current one and schedule normally). */
    static void force(int kind) {
        state = NONE;
        pending = kind;
        pendingAge = 0;
        forced = kind != NONE;
        if (kind == NONE) {
            nextLieTick = ticksInLevel + gap();
        }
    }

    /** One line for {@code /backrooms mirror info}. */
    static String describe() {
        return "lie: " + name(state) + (pending != NONE ? " (pending " + name(pending) + ")" : "")
                + ", still " + stillTicks + "t, next in " + Math.max(0, nextLieTick - ticksInLevel) / 20 + "s";
    }

    static String name(int kind) {
        return switch (kind) {
            case LOOK -> "look";
            case LAG -> "lag";
            case WALK_ON -> "walk";
            case STOP -> "stop";
            case VANISH -> "vanish";
            case EMPTY -> "empty";
            default -> "none";
        };
    }

    // ==========================================================================================
    // Ticks (client thread)
    // ==========================================================================================

    static void reset() {
        ticksInLevel = 0;
        nextLieTick = -1;
        pending = NONE;
        forced = false;
        state = NONE;
        stillTicks = 0;
        movingTicks = 0;
        hasLast = false;
        unseen = 0;
        seenFlag = false;
    }

    /**
     * Once a client tick in Level 228. {@code allowed}: lies may happen where you are (not the
     * final chamber or exit hallway); outside, any lie ends at once.
     */
    static void tick(Player player, ClientLevel level, boolean allowed) {
        ticksInLevel++;
        if (nextLieTick < 0) {
            nextLieTick = ticksInLevel + gap();
        }
        trackYou(player);
        if (seenFlag) {
            unseen = 0;
        } else {
            unseen++;
        }
        seenFlag = false;

        allowedHere = allowed;
        if (!allowed) {
            if (state != NONE) {
                finish();
            }
            return;
        }
        if (state == NONE) {
            if (pending == NONE && ticksInLevel >= nextLieTick) {
                pending = pickKind();
                pendingAge = 0;
            } else if (pending != NONE && !forced && ++pendingAge > PENDING_TIMEOUT) {
                pending = pickKind(); // it never found its moment: something else
                pendingAge = 0;
            }
            return;
        }
        age++;
        double dx = player.getX() - imgX;
        double dz = player.getZ() - imgZ;
        if (dx * dx + dz * dz > FAR * FAR) {
            finish();
            return;
        }
        switch (state) {
            case LOOK -> tickLook();
            case LAG -> {
                if (age > lagDuration + 20) {
                    finish();
                }
            }
            case WALK_ON -> tickWalkOn(level);
            case STOP -> {
                fadeO = fade;
                if (unseen >= GONE_UNSEEN) {
                    toEmpty(); // you looked away: next time you look, it's gone
                } else if (age > 20 * 20) {
                    fade = Math.max(0.0f, fade - 1.0f / 40.0f); // watched for 20 s: it fades into the dark
                    if (fade <= 0.0f) {
                        toEmpty();
                    }
                }
            }
            case EMPTY -> {
                if ((unseen >= RETURN_UNSEEN && age > 20 * 15) || age > 20 * 90) {
                    finish(); // back while you aren't looking
                }
            }
            case VANISH -> {
                if (age > vanishFor && unseen >= RETURN_UNSEEN) {
                    finish();
                }
            }
            default -> finish();
        }
    }

    private static void trackYou(Player p) {
        double x = p.getX();
        double z = p.getZ();
        if (hasLast) {
            double mx = x - lastX;
            double mz = z - lastZ;
            if (mx * mx + mz * mz < 0.01 * 0.01) {
                stillTicks++;
                movingTicks = 0;
            } else {
                movingTicks++;
                if (mx * mx + mz * mz > 0.04 * 0.04) {
                    stepX = mx;
                    stepZ = mz;
                }
                stillTicks = 0;
            }
        }
        lastX = x;
        lastZ = z;
        hasLast = true;
    }

    private static float escalation() {
        return Math.min(1.0f, ticksInLevel / ESCALATE_TICKS);
    }

    private static int gap() {
        float seconds = MIN_GAP + RANDOM.nextFloat() * (MAX_GAP - MIN_GAP);
        return (int) (seconds * 20.0f * (1.0f - 0.45f * escalation()));
    }

    /** The head-turn is the signature; the ones that leave you alone grow likelier with time. */
    private static int pickKind() {
        float e = escalation();
        float look = 4.0f;
        float lag = 2.0f;
        float walk = 2.0f;
        float stop = 1.0f + 1.5f * e;
        float vanish = 1.0f + 1.5f * e;
        float roll = RANDOM.nextFloat() * (look + lag + walk + stop + vanish);
        if ((roll -= look) < 0.0f) {
            return LOOK;
        }
        if ((roll -= lag) < 0.0f) {
            return LAG;
        }
        if ((roll -= walk) < 0.0f) {
            return WALK_ON;
        }
        return roll < stop ? STOP : VANISH;
    }

    private static void finish() {
        state = NONE;
        nextLieTick = ticksInLevel + gap();
    }

    private static void toEmpty() {
        state = EMPTY;
        age = 0;
        unseen = 0;
    }

    private static void tickLook() {
        lookWO = lookW;
        boolean youMoved = stillTicks == 0;
        if (!lookReturning && (youMoved || age > TURN_TICKS + lookHold)) {
            lookReturning = true;
        }
        if (lookReturning) {
            lookW = Math.max(0.0f, lookW - 1.0f / TURN_TICKS);
            if (lookW <= 0.0f && lookWO <= 0.0f) {
                finish();
            }
        } else {
            lookW = Math.min(1.0f, lookW + 1.0f / TURN_TICKS);
        }
    }

    private static void tickWalkOn(ClientLevel level) {
        fxO = fx;
        fzO = fz;
        fWalkPosO = fWalkPos;
        fWalkSpeedO = fWalkSpeed;
        fadeO = fade;
        if (walkLeft > 0.0) {
            // Eases out over the last block: a person slowing to a stop, not a puppet cut off.
            double v = Math.sqrt(walkVx * walkVx + walkVz * walkVz);
            double ease = Math.min(1.0, walkLeft / 1.0);
            double step = v * (0.35 + 0.65 * ease);
            double nx = fx + walkVx / v * step;
            double nz = fz + walkVz / v * step;
            if (standable(level, nx, fy, nz)) {
                fx = nx;
                fz = nz;
                walkLeft -= step;
                fWalkSpeed = (float) Math.min(1.0, step * 4.0);
            } else {
                walkLeft = 0.0; // a wall or a pane: it stops there
            }
        }
        if (walkLeft <= 0.0) {
            fWalkSpeed = Math.max(0.0f, fWalkSpeed - 0.12f);
        }
        fWalkPos += fWalkSpeed;
        if (walkLeft <= 0.0 && unseen >= RETURN_UNSEEN) {
            finish(); // back in step with you while you weren't looking
        } else if (age > 20 * 30) {
            fade = Math.max(0.0f, fade - 1.0f / 40.0f);
            if (fade <= 0.0f) {
                toEmpty();
            }
        }
    }

    /** A body could stand at these feet: two free blocks over a floor (the image's real blocks). */
    private static boolean standable(ClientLevel level, double x, double y, double z) {
        PROBE.set(Mth.floor(x), Mth.floor(y + 0.1), Mth.floor(z));
        if (!level.getBlockState(PROBE).getCollisionShape(level, PROBE).isEmpty()) {
            return false;
        }
        PROBE.move(0, 1, 0);
        if (!level.getBlockState(PROBE).getCollisionShape(level, PROBE).isEmpty()) {
            return false;
        }
        PROBE.move(0, -2, 0);
        return !level.getBlockState(PROBE).getCollisionShape(level, PROBE).isEmpty();
    }

    // ==========================================================================================
    // Frames (render thread)
    // ==========================================================================================

    static void beginFrame() {
        candValid = false;
        imagesThisFrame = 0;
    }

    private static int q(double v) {
        return (int) Math.round(v * 4.0);
    }

    /** True if the lie in progress (or the empty spot it left) is this image of you. */
    static boolean isLieImage(int flipX, int flipZ, double tx, double tz) {
        return state != NONE && flipX == keyFx && flipZ == keyFz && q(tx) == keyTx && q(tz) == keyTz;
    }

    /** The lie's image is on screen this frame (or would be, when empty). */
    static void noteSeen() {
        seenFlag = true;
    }

    /**
     * One image of you this frame (never a puzzle mirror's), with its live pose. {@code special}:
     * it is seen through a "nonexistent room"/"hidden passage" mirror. The best candidate of the
     * frame is where a pending lie starts at the end of the frame.
     */
    static void consider(Player you, float pt, int flipX, int flipZ, double tx, double tz, PuppetPose live, Vec3 cam,
            Vector3f look, boolean onScreen, boolean special) {
        imagesThisFrame++;
        if (!allowedHere) {
            return;
        }
        if (pending == NONE || state != NONE) {
            return;
        }
        double dx = live.x - cam.x;
        double dy = live.y + 1.5 - cam.y;
        double dz = live.z - cam.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > CLOSE || dist < 1.0) {
            return;
        }
        double cosView = (look.x() * dx + look.y() * dy + look.z() * dz) / dist;
        float body = Mth.rotLerp(pt, you.yBodyRotO, you.yBodyRot);
        float head = Mth.rotLerp(pt, you.yHeadRotO, you.yHeadRot);
        double score;
        switch (pending) {
            case LOOK -> {
                if (!onScreen || stillTicks < STILL_BEFORE_LOOK || cosView > 0.995) {
                    return; // dead centre is no good: it's the corner of your eye that sees it begin
                }
                float target = headTowards(flipX, flipZ, cam.x - live.x, cam.z - live.z);
                float turn = Math.abs(Mth.wrapDegrees(target - head));
                float neck = Math.abs(Mth.wrapDegrees(target - body));
                if (turn < MIN_TURN || neck > MAX_NECK) {
                    return;
                }
                score = 20.0 - dist + (special ? 4.0 : 0.0);
            }
            case LAG, STOP -> {
                if (!onScreen || movingTicks < (pending == STOP ? 20 : 10) || (pending == LAG && PoseTracks.history(you.getId()) < PoseTracks.RING)) {
                    return;
                }
                score = 20.0 - dist + (special ? 4.0 : 0.0);
            }
            case WALK_ON -> {
                if (!onScreen || stillTicks < 2 || stillTicks > 8) {
                    return; // the moment you stop
                }
                score = 20.0 - dist + (special ? 8.0 : 0.0);
            }
            case VANISH -> {
                if (onScreen && cosView > 0.86) {
                    return; // in plain view: it would vanish in front of you
                }
                score = (onScreen ? 10.0 : 20.0) - dist + (special ? 4.0 : 0.0);
            }
            default -> {
                return;
            }
        }
        if (candValid && score <= candScore) {
            return;
        }
        candValid = true;
        candScore = score;
        cFx = flipX;
        cFz = flipZ;
        cTx = tx;
        cTz = tz;
        cX = live.x;
        cY = live.y;
        cZ = live.z;
        cBody = body;
        cHead = head;
        cPitch = Mth.lerp(pt, you.xRotO, you.getXRot());
        cWalkPos = you.walkAnimation.position(pt);
        cWalkSpeed = you.walkAnimation.speed(pt);
        cCrouch = you.isCrouching();
    }

    /** End of the frame: start the pending lie on the frame's best candidate, if there was one. */
    static void endFrame() {
        if (!candValid || pending == NONE || state != NONE) {
            return;
        }
        if (pending == VANISH && imagesThisFrame < 2) {
            return; // "one image empty while others remain": not with a single reflection
        }
        state = pending;
        pending = NONE;
        forced = false;
        age = 0;
        unseen = 0;
        keyFx = cFx;
        keyFz = cFz;
        keyTx = q(cTx);
        keyTz = q(cTz);
        tX = cTx;
        tZ = cTz;
        imgX = cX;
        imgZ = cZ;
        float e = escalation();
        fx = fxO = cX;
        fy = cY;
        fz = fzO = cZ;
        fBody = cBody;
        fHead = cHead;
        fPitch = cPitch;
        fWalkPos = fWalkPosO = cWalkPos;
        fWalkSpeed = fWalkSpeedO = cWalkSpeed;
        fCrouch = cCrouch;
        fade = fadeO = 1.0f;
        switch (state) {
            case LOOK -> {
                lookW = 0.0f;
                lookWO = 0.0f;
                lookReturning = false;
                lookHold = HOLD_MIN + RANDOM.nextInt(HOLD_MAX - HOLD_MIN + 1);
            }
            case LAG -> {
                lagDelay = 12.0f + 6.0f * e + RANDOM.nextFloat() * 2.0f; // 0.6 .. 1.0 s late
                lagDuration = 60 + RANDOM.nextInt(50) + (int) (40 * e);
            }
            case WALK_ON -> {
                // Your last step, through the mirror map: the way the reflection was walking.
                double v = Math.sqrt(stepX * stepX + stepZ * stepZ);
                double speed = Mth.clamp(v, 0.1, 0.22);
                walkVx = v > 1e-4 ? cFx * stepX / v * speed : 0.0;
                walkVz = v > 1e-4 ? cFz * stepZ / v * speed : 0.0;
                walkLeft = v > 1e-4 ? 1.5 + RANDOM.nextDouble() * 2.0 : 0.0;
                fWalkSpeed = fWalkSpeedO = Math.max(cWalkSpeed, (float) Math.min(1.0, speed * 4.0));
            }
            case VANISH -> vanishFor = 200 + RANDOM.nextInt(300);
            default -> {
                // STOP needs nothing more than the frozen pose.
            }
        }
    }

    /**
     * Turns {@code pose} (the live reflection of {@code you} in the lie's image, plain) into the
     * lie. Returns false when nothing of you is to be drawn there.
     */
    static boolean apply(Player you, float pt, Vec3 cam, PuppetPose pose) {
        switch (state) {
            case LOOK -> {
                applyLook(you, pt, cam, pose);
                return true;
            }
            case LAG -> {
                applyLag(you, pt, pose);
                return true;
            }
            case WALK_ON -> {
                pose.x = Mth.lerp(pt, fxO, fx);
                pose.y = fy;
                pose.z = Mth.lerp(pt, fzO, fz);
                frozen(pose, Mth.lerp(pt, fWalkPosO, fWalkPos), Mth.lerp(pt, fWalkSpeedO, fWalkSpeed));
                pose.alpha = Mth.lerp(pt, fadeO, fade);
                return pose.alpha > 0.01f;
            }
            case STOP -> {
                pose.x = fx;
                pose.y = fy;
                pose.z = fz;
                frozen(pose, fWalkPos, 0.0f);
                pose.alpha = Mth.lerp(pt, fadeO, fade);
                return pose.alpha > 0.01f;
            }
            default -> {
                return false; // VANISH / EMPTY: nothing of you there
            }
        }
    }

    private static void frozen(PuppetPose pose, float walkPos, float walkSpeed) {
        pose.override = true;
        pose.body = fBody;
        pose.head = fHead;
        pose.pitch = fPitch;
        pose.walkPos = walkPos;
        pose.walkSpeed = walkSpeed;
        pose.attack = 0.0f;
        pose.crouch = fCrouch;
    }

    /** Your own movement, replayed late from your pose history (eased in and out). */
    private static void applyLag(Player you, float pt, PuppetPose pose) {
        float t = age + pt;
        float ramp;
        if (t < 10.0f) {
            ramp = smooth(t / 10.0f);
        } else if (t > lagDuration) {
            ramp = 1.0f - smooth(Math.min(1.0f, (t - lagDuration) / 20.0f));
        } else {
            ramp = 1.0f;
        }
        float delay = lagDelay * ramp;
        if (delay < 0.05f || !PoseTracks.sample(you.getId(), delay, pt)) {
            return;
        }
        pose.x = tX + keyFx * PoseTracks.sx;
        pose.y = PoseTracks.sy;
        pose.z = tZ + keyFz * PoseTracks.sz;
        pose.override = true;
        pose.body = PoseTracks.sBody;
        pose.head = PoseTracks.sHead;
        pose.pitch = PoseTracks.sPitch;
        pose.walkPos = PoseTracks.sWalkPos;
        pose.walkSpeed = PoseTracks.sWalkSpeed;
        pose.attack = PoseTracks.sAttack;
        pose.crouch = PoseTracks.sCrouch;
    }

    /** Your reflection, its head slowly turned to look at the camera (the body stays as yours). */
    private static void applyLook(Player you, float pt, Vec3 cam, PuppetPose pose) {
        float liveBody = Mth.rotLerp(pt, you.yBodyRotO, you.yBodyRot);
        float liveHead = Mth.rotLerp(pt, you.yHeadRotO, you.yHeadRot);
        float livePitch = Mth.lerp(pt, you.xRotO, you.getXRot());
        double ex = cam.x - pose.x;
        double ey = cam.y - (pose.y + you.getEyeHeight());
        double ez = cam.z - pose.z;
        float target = headTowards(keyFx, keyFz, ex, ez);
        // A neck, not an owl: the turn stops where a neck would.
        float rel = Mth.clamp(Mth.wrapDegrees(target - liveBody), -MAX_NECK, MAX_NECK);
        target = liveBody + rel;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
        float w = smooth(Mth.lerp(pt, lookWO, lookW));
        pose.override = true;
        pose.body = liveBody;
        pose.head = Mth.rotLerp(w, liveHead, target);
        pose.pitch = Mth.lerp(w, livePitch, targetPitch);
        pose.walkPos = you.walkAnimation.position(pt);
        pose.walkSpeed = you.walkAnimation.speed(pt);
        pose.attack = you.getAttackAnim(pt);
        pose.crouch = you.isCrouching();
    }

    /**
     * The head yaw, in the REAL body's frame, that makes the image look along (dx, dz) in image
     * space: the mirror map flips the direction, so flip it back (the map is its own inverse).
     */
    private static float headTowards(int flipX, int flipZ, double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx * flipX, dz * flipZ));
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
