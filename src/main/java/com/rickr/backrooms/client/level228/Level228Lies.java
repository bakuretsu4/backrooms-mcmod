package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Reflections that lie (Level 228). Rarely (every one to three minutes, a little more often the
 * longer you stay), one of YOUR reflections close by (on screen, within {@value #CLOSE} blocks)
 * stops being quite yours:
 * <ul>
 *   <li>{@link #LAG}: it replays your movements most of a second late, for a few seconds, then
 *       catches up (a ring of your poses over the last few seconds, sampled each tick).</li>
 *   <li>{@link #LOOK}: its head stays turned to you while you look or turn away, and snaps back
 *       the moment you look straight at it.</li>
 *   <li>{@link #WALK}: it stops copying you, turns round and walks off down the reflected
 *       corridor (following the real maze's corridors, mirrored) until it fades into the dark;
 *       that reflection then stays empty while you remain in view of it.</li>
 *   <li>{@link #STILL}: it stays standing as it was when you walk off; the next time you look,
 *       it is simply gone, and that reflection stays empty for a while.</li>
 * </ul>
 * Never a jump scare: nothing sudden comes at you, no sound, no flash. The lie is something you
 * notice, or don't. Everything is preallocated (state in static fields, a fixed ring of poses),
 * so nothing here allocates per frame or tick.
 *
 * <p>The mod's sanity lives on the server and there is no client-to-server way to change it
 * (only server-side events drain it), so a lie costs no sanity: it works on the player, not the
 * bar.
 */
public final class Level228Lies {
    public static final int NONE = 0;
    public static final int LAG = 1;
    public static final int LOOK = 2;
    public static final int WALK = 3;
    public static final int STILL = 4;
    /** After WALK or STILL: that reflection shows nothing of you. */
    private static final int EMPTY = 5;

    /** A lie only starts on a reflection of you on screen and at most this far from the camera. */
    static final double CLOSE = 10.0;
    /** Poses remembered (ticks): the longest lag plus its interpolation, with room to spare. */
    private static final int RING = 64;
    /** Between lies (seconds), before escalation. */
    private static final float MIN_GAP = 60.0f;
    private static final float MAX_GAP = 180.0f;
    /** Full escalation after this long in the level (ticks): 30 minutes. */
    private static final float ESCALATE_TICKS = 20.0f * 60.0f * 30.0f;
    /** The walking reflection's pace (blocks per tick, about a slow 1.4 blocks a second) and limb swing. */
    private static final double WALK_STEP = 0.07;
    private static final float WALK_SWING = 0.3f;
    /** Ticks it stands turning round before it walks. */
    private static final int TURN_TICKS = 16;
    /** Unseen ticks after which a still reflection is gone / an empty one returns to normal. */
    private static final int GONE_UNSEEN = 10;
    private static final int EMPTY_UNSEEN = 120;
    /** The lie ends once you are this far from its image (blocks). */
    private static final double FAR = 32.0;

    private static final Random RANDOM = new Random();

    // --- Schedule -------------------------------------------------------------------------------
    private static int ticksInLevel;
    private static int nextLieTick = -1;
    /** The lie waiting for a reflection to happen to (0: none). */
    private static int pending;

    // --- The lie in progress ----------------------------------------------------------------------
    private static int state;
    private static int age;
    /** Which image it happens in (box, map, depth): images are refilled every frame, so matched by value. */
    private static final int[] KEY = new int[11];
    private static boolean seenFlag;
    private static int unseen;

    // LAG
    private static float lagDelay;
    private static int lagDuration;
    // LOOK
    private static float lookW;
    private static float lookWO;
    private static boolean lookArmed;
    private static int lookCap;
    // WALK / STILL: the pose it froze in (image position, real-frame angles).
    private static double fx;
    private static double fy;
    private static double fz;
    private static float fBody;
    private static float fHead;
    private static float fPitch;
    private static float fWalkPos;
    private static boolean fCrouch;
    // WALK: the walker.
    private static double wx;
    private static double wz;
    private static double wxO;
    private static double wzO;
    private static float wYaw;
    private static float wYawO;
    private static float wPos;
    private static float wPosO;
    private static float wAlpha;
    private static float wAlphaO;
    private static int cellX;
    private static int cellZ;
    private static int prevX;
    private static int prevZ;
    private static int targetX;
    private static int targetZ;
    private static boolean walkEnded;
    private static int walkedCells;
    private static long walkSeed;
    private static int wFlipX;
    private static int wFlipZ;
    private static int wOffX;
    private static int wOffZ;
    private static double camX;
    private static double camZ;

    // --- This frame's best candidate (a reflection of you a lie could start on) ------------------
    private static boolean candValid;
    private static double candDist;
    private static final int[] CAND_KEY = new int[11];
    private static double cX;
    private static double cY;
    private static double cZ;
    private static float cBody;
    private static float cHead;
    private static float cPitch;
    private static float cWalkPos;
    private static boolean cCrouch;

    // --- Your recent poses (real space), one per tick ---------------------------------------------
    private static final double[] RX = new double[RING];
    private static final double[] RY = new double[RING];
    private static final double[] RZ = new double[RING];
    private static final float[] R_BODY = new float[RING];
    private static final float[] R_HEAD = new float[RING];
    private static final float[] R_PITCH = new float[RING];
    private static final float[] R_WALK_POS = new float[RING];
    private static final float[] R_WALK_SPEED = new float[RING];
    private static final float[] R_ATTACK = new float[RING];
    private static final boolean[] R_CROUCH = new boolean[RING];
    private static int samples;

    private Level228Lies() {}

    // ==========================================================================================
    // Ticks (client thread)
    // ==========================================================================================

    /** Left the level (or nobody is playing): forget everything. */
    static void reset() {
        ticksInLevel = 0;
        nextLieTick = -1;
        pending = NONE;
        state = NONE;
        samples = 0;
        unseen = 0;
        seenFlag = false;
    }

    /** Debug ({@code /backrooms mirror lie ...}): this lie next, as soon as a reflection of you is close and on screen. */
    static void force(int kind) {
        state = NONE;
        pending = kind;
    }

    /** Once a client tick while in Level 228, after the player moved. */
    static void tick(Player player, long seed) {
        walkSeed = seed;
        ticksInLevel++;
        if (nextLieTick < 0) {
            nextLieTick = ticksInLevel + gap();
        }
        sample(player);

        if (seenFlag) {
            unseen = 0;
        } else {
            unseen++;
        }
        seenFlag = false;

        if (state == NONE) {
            if (pending == NONE && ticksInLevel >= nextLieTick) {
                pending = pickKind();
            }
            return;
        }
        age++;
        if (distanceToKey(player.getX(), player.getZ()) > FAR) {
            finish();
            return;
        }
        switch (state) {
            case LAG -> {
                if (age > lagDuration + 20) {
                    finish();
                }
            }
            case LOOK -> {
                lookWO = lookW;
                lookW = Math.min(1.0f, lookW + 1.0f / 24.0f);
                // Time's up: it turns back only while you aren't looking (only your gaze snaps it).
                if (age > lookCap && unseen > GONE_UNSEEN) {
                    finish();
                }
            }
            case WALK -> {
                stepWalker();
                if (wAlpha <= 0.0f && wAlphaO <= 0.0f) {
                    toEmpty();
                }
            }
            case STILL -> {
                if (unseen >= GONE_UNSEEN) {
                    toEmpty(); // you looked away: next time you look, it's gone
                }
            }
            case EMPTY -> {
                if (unseen >= EMPTY_UNSEEN || age > 20 * 90) {
                    finish();
                }
            }
            default -> finish();
        }
    }

    private static void sample(Player p) {
        int i = samples % RING;
        RX[i] = p.getX();
        RY[i] = p.getY();
        RZ[i] = p.getZ();
        R_BODY[i] = p.yBodyRot;
        R_HEAD[i] = p.yHeadRot;
        R_PITCH[i] = p.getXRot();
        R_WALK_POS[i] = p.walkAnimation.position();
        R_WALK_SPEED[i] = p.walkAnimation.speed();
        R_ATTACK[i] = p.attackAnim;
        R_CROUCH[i] = p.isCrouching();
        samples++;
    }

    /** 0 on arrival .. 1 after half an hour in the level. */
    private static float escalation() {
        return Math.min(1.0f, ticksInLevel / ESCALATE_TICKS);
    }

    /** Ticks to the next lie: one to three minutes, down to about half that with time. */
    private static int gap() {
        float seconds = MIN_GAP + RANDOM.nextFloat() * (MAX_GAP - MIN_GAP);
        return (int) (seconds * 20.0f * (1.0f - 0.45f * escalation()));
    }

    /** The subtle ones (late, watching) first; the ones that leave you alone grow likelier with time. */
    private static int pickKind() {
        float e = escalation();
        float lag = 3.0f;
        float look = 3.0f;
        float walk = 1.0f + 2.0f * e;
        float still = 1.0f + 2.0f * e;
        float roll = RANDOM.nextFloat() * (lag + look + walk + still);
        if (roll < lag) {
            return LAG;
        }
        if (roll < lag + look) {
            return LOOK;
        }
        return roll < lag + look + walk ? WALK : STILL;
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

    private static double distanceToKey(double x, double z) {
        double cx = (KEY[0] + KEY[1] + 1) * 0.5;
        double cz = (KEY[4] + KEY[5] + 1) * 0.5;
        double dx = Math.max(0.0, Math.abs(x - cx) - (KEY[1] - KEY[0] + 1) * 0.5);
        double dz = Math.max(0.0, Math.abs(z - cz) - (KEY[5] - KEY[4] + 1) * 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ==========================================================================================
    // The walker (WALK): cell to cell through the image, along the real maze's corridors mirrored
    // ==========================================================================================

    private static void stepWalker() {
        wxO = wx;
        wzO = wz;
        wYawO = wYaw;
        wPosO = wPos;
        wAlphaO = wAlpha;
        if (walkEnded) {
            wAlpha = Math.max(0.0f, wAlpha - 1.0f / 30.0f);
            return;
        }
        double tx = targetX + 0.5 - wx;
        double tz = targetZ + 0.5 - wz;
        float towards = realYaw(tx, tz);
        if (age <= TURN_TICKS) {
            wYaw = Mth.rotLerp(0.18f, wYaw, towards); // turning round, on the spot
            return;
        }
        wYaw = Mth.rotLerp(0.25f, wYaw, towards);
        wPos += WALK_SWING;
        double d = Math.sqrt(tx * tx + tz * tz);
        if (d <= WALK_STEP) {
            wx = targetX + 0.5;
            wz = targetZ + 0.5;
            prevX = cellX;
            prevZ = cellZ;
            cellX = targetX;
            cellZ = targetZ;
            walkedCells++;
            if (walkedCells > 40 || !chooseNext()) {
                walkEnded = true; // a dead end, the image's edge, or far enough: it fades where it stands
            }
        } else {
            wx += tx / d * WALK_STEP;
            wz += tz / d * WALK_STEP;
        }
        double cdx = wx - camX;
        double cdz = wz - camZ;
        if (cdx * cdx + cdz * cdz > 20.0 * 20.0) {
            walkEnded = true; // well into the dark already
        }
    }

    /**
     * The next cell: any corridor neighbour (asked of the REAL maze: image cell (x, z) is real cell
     * flip * (x, z) + off, and a step is mirrored the same way), not back where it came from and
     * still inside the image, preferring the one farthest from you: it walks AWAY.
     */
    private static boolean chooseNext() {
        int realX = wFlipX * cellX + wOffX;
        int realZ = wFlipZ * cellZ + wOffZ;
        double best = -1.0;
        int bx = 0;
        int bz = 0;
        for (int dir = 0; dir < 4; dir++) {
            int dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
            int dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
            int nx = cellX + dx;
            int nz = cellZ + dz;
            if ((nx == prevX && nz == prevZ) || nx < KEY[0] || nx > KEY[1] || nz < KEY[4] || nz > KEY[5]) {
                continue;
            }
            if (!Level228Plan.canStep(walkSeed, realX, realZ, wFlipX * dx, wFlipZ * dz)) {
                continue;
            }
            double ex = nx + 0.5 - camX;
            double ez = nz + 0.5 - camZ;
            double score = ex * ex + ez * ez;
            if (score > best) {
                best = score;
                bx = nx;
                bz = nz;
            }
        }
        if (best < 0.0) {
            return false;
        }
        targetX = bx;
        targetZ = bz;
        return true;
    }

    /**
     * The yaw, in the REAL player's frame, that the puppet must have to face along (dx, dz) in the
     * image: the mirror map flips the direction, so flip it back (the map is its own inverse).
     */
    private static float realYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx * wFlipX, dz * wFlipZ));
    }

    // ==========================================================================================
    // Frames (render thread)
    // ==========================================================================================

    static void beginFrame(Vec3 cam) {
        candValid = false;
        camX = cam.x;
        camZ = cam.z;
    }

    /** True if the lie in progress happens in this image. */
    static boolean isLieImage(Level228Plan.Image img) {
        return state != NONE && matches(KEY, img);
    }

    /** The lie's image is on screen this frame. */
    static void noteSeen() {
        seenFlag = true;
    }

    /**
     * A reflection of you is drawn in {@code img}, on screen, {@code dist} blocks away, at
     * {@code pose} (live). The nearest of the frame is where a pending lie starts.
     */
    static void offer(Level228Plan.Image img, Player p, float pt, PuppetPose pose, double dist) {
        if (pending == NONE || state != NONE || dist > CLOSE || (candValid && dist >= candDist)) {
            return;
        }
        if (pending == LAG && samples < RING) {
            return; // not enough of your past yet to replay
        }
        candValid = true;
        candDist = dist;
        fill(CAND_KEY, img);
        cX = pose.x;
        cY = pose.y;
        cZ = pose.z;
        cBody = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        cHead = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        cPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        cWalkPos = p.walkAnimation.position(pt);
        cCrouch = p.isCrouching();
    }

    /** End of the frame: start the pending lie on the nearest reflection of you, if there was one. */
    static void endFrame() {
        if (!candValid || pending == NONE) {
            return;
        }
        System.arraycopy(CAND_KEY, 0, KEY, 0, KEY.length);
        state = pending;
        pending = NONE;
        age = 0;
        unseen = 0;
        float e = escalation();
        fx = cX;
        fy = cY;
        fz = cZ;
        fBody = cBody;
        fHead = cHead;
        fPitch = cPitch;
        fWalkPos = cWalkPos;
        fCrouch = cCrouch;
        switch (state) {
            case LAG -> {
                lagDelay = 12.0f + 6.0f * e + RANDOM.nextFloat() * 2.0f; // 0.6 .. 1.0 s late
                lagDuration = 60 + RANDOM.nextInt(50) + (int) (40 * e);
            }
            case LOOK -> {
                lookW = 0.0f;
                lookWO = 0.0f;
                lookArmed = false;
                lookCap = 200 + (int) (200 * e);
            }
            case WALK -> {
                wFlipX = KEY[6];
                wFlipZ = KEY[7];
                wOffX = KEY[8];
                wOffZ = KEY[9];
                wx = wxO = fx;
                wz = wzO = fz;
                wYaw = wYawO = fBody;
                wPos = wPosO = fWalkPos;
                wAlpha = wAlphaO = 1.0f;
                cellX = prevX = Mth.floor(fx);
                cellZ = prevZ = Mth.floor(fz);
                walkedCells = 0;
                walkEnded = !chooseNext();
            }
            default -> {
                // STILL needs nothing more than the frozen pose.
            }
        }
    }

    /**
     * Turns {@code pose} (already the live reflection of {@code p} in the lie's image) into the
     * lie. Returns false when nothing of you is to be drawn there.
     */
    static boolean apply(Player p, float pt, Vec3 cam, Vector3f look, PuppetPose pose) {
        switch (state) {
            case LAG -> {
                return applyLag(p, pt, pose);
            }
            case LOOK -> {
                return applyLook(p, pt, cam, look, pose);
            }
            case WALK -> {
                float a = Mth.lerp(pt, wAlphaO, wAlpha);
                pose.x = Mth.lerp(pt, wxO, wx);
                pose.y = fy;
                pose.z = Mth.lerp(pt, wzO, wz);
                double dx = pose.x - cam.x;
                double dz = pose.z - cam.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                // Into the dark: fades out from 12 to 20 blocks (the fog takes it as it goes).
                a *= 1.0f - (float) Mth.clamp((dist - 12.0) / 8.0, 0.0, 1.0);
                if (a <= 0.01f) {
                    return false;
                }
                float yaw = Mth.rotLerp(pt, wYawO, wYaw);
                boolean walking = age > TURN_TICKS && !walkEnded;
                pose.override = true;
                pose.body = yaw;
                pose.head = yaw;
                pose.pitch = 8.0f; // head slightly down, walking off
                pose.walkPos = Mth.lerp(pt, wPosO, wPos);
                pose.walkSpeed = walking ? WALK_SWING : 0.0f;
                pose.attack = 0.0f;
                pose.crouch = false;
                pose.alpha = a;
                return true;
            }
            case STILL -> {
                pose.x = fx;
                pose.y = fy;
                pose.z = fz;
                pose.override = true;
                pose.body = fBody;
                pose.head = fHead;
                pose.pitch = fPitch;
                pose.walkPos = fWalkPos;
                pose.walkSpeed = 0.0f;
                pose.attack = 0.0f;
                pose.crouch = fCrouch;
                return true;
            }
            default -> {
                return false; // EMPTY: nothing of you there
            }
        }
    }

    /** Your own movement, replayed from the ring {@code delay} ticks late (eased in and out). */
    private static boolean applyLag(Player p, float pt, PuppetPose pose) {
        float t = age + pt;
        float ramp;
        if (t < 10.0f) {
            ramp = smooth(t / 10.0f);
        } else if (t > lagDuration) {
            ramp = 1.0f - smooth(Math.min(1.0f, (t - lagDuration) / 20.0f)); // catching up with you
        } else {
            ramp = 1.0f;
        }
        float delay = lagDelay * ramp;
        if (delay < 0.05f || samples < 3) {
            return true;
        }
        // The live pose is between samples (samples - 2) and (samples - 1) at pt; step back from there.
        double when = samples - 2 + pt - delay;
        double oldest = samples - RING + 1;
        if (when < oldest) {
            when = oldest;
        }
        int k0 = Mth.floor(when);
        float f = (float) (when - k0);
        int i0 = Math.floorMod(k0, RING);
        int i1 = Math.floorMod(k0 + 1, RING);
        double rx = Mth.lerp(f, RX[i0], RX[i1]);
        double ry = Mth.lerp(f, RY[i0], RY[i1]);
        double rz = Mth.lerp(f, RZ[i0], RZ[i1]);
        pose.x = KEY[6] * (rx - KEY[8] - 0.5) + 0.5;
        pose.y = ry;
        pose.z = KEY[7] * (rz - KEY[9] - 0.5) + 0.5;
        pose.override = true;
        pose.body = Mth.rotLerp(f, R_BODY[i0], R_BODY[i1]);
        pose.head = Mth.rotLerp(f, R_HEAD[i0], R_HEAD[i1]);
        pose.pitch = Mth.lerp(f, R_PITCH[i0], R_PITCH[i1]);
        pose.walkPos = Mth.lerp(f, R_WALK_POS[i0], R_WALK_POS[i1]);
        pose.walkSpeed = Mth.lerp(f, R_WALK_SPEED[i0], R_WALK_SPEED[i1]);
        pose.attack = Mth.lerp(f, R_ATTACK[i0], R_ATTACK[i1]);
        pose.crouch = f < 0.5f ? R_CROUCH[i0] : R_CROUCH[i1];
        return true;
    }

    /** Your reflection, but its head turned to the camera; it snaps back when you look straight at it. */
    private static boolean applyLook(Player p, float pt, Vec3 cam, Vector3f look, PuppetPose pose) {
        float liveBody = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float liveHead = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float livePitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        double ex = cam.x - pose.x;
        double ey = cam.y - (pose.y + p.getEyeHeight());
        double ez = cam.z - pose.z;
        double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (len > 1e-4) {
            // How straight you look at its face (camera's view vs. the line to its eyes).
            double cos = -(look.x() * ex + look.y() * ey + look.z() * ez) / len;
            if (cos < 0.906) {
                lookArmed = true; // more than ~25 degrees away: you have looked away from it
            } else if (lookArmed && cos > 0.9925) {
                finish(); // within ~7 degrees: caught it; it is just your reflection again, at once
                return true;
            }
        }
        float w = smooth(Mth.lerp(pt, lookWO, lookW));
        // The direction to the camera, taken back into the real player's frame (flip it back).
        double rx = ex * KEY[6];
        double rz = ez * KEY[7];
        float targetHead = (float) Math.toDegrees(Math.atan2(-rx, rz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(rx * rx + rz * rz)));
        float head = Mth.rotLerp(w, liveHead, targetHead);
        float rel = Mth.wrapDegrees(head - liveBody);
        // The body turns with it past 50 degrees: the whole reflection keeps facing you, not an owl's neck.
        float body = Math.abs(rel) > 50.0f ? head - Math.copySign(50.0f, rel) : liveBody;
        pose.override = true;
        pose.body = body;
        pose.head = head;
        pose.pitch = Mth.lerp(w, livePitch, targetPitch);
        pose.walkPos = p.walkAnimation.position(pt);
        pose.walkSpeed = p.walkAnimation.speed(pt);
        pose.attack = p.getAttackAnim(pt);
        pose.crouch = p.isCrouching();
        return true;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // ==========================================================================================
    // Image identity
    // ==========================================================================================

    private static void fill(int[] key, Level228Plan.Image img) {
        key[0] = img.minX;
        key[1] = img.maxX;
        key[2] = img.minY;
        key[3] = img.maxY;
        key[4] = img.minZ;
        key[5] = img.maxZ;
        key[6] = img.flipX;
        key[7] = img.flipZ;
        key[8] = img.offX;
        key[9] = img.offZ;
        key[10] = img.depth;
    }

    private static boolean matches(int[] key, Level228Plan.Image img) {
        return key[0] == img.minX && key[1] == img.maxX && key[2] == img.minY && key[3] == img.maxY
                && key[4] == img.minZ && key[5] == img.maxZ && key[6] == img.flipX && key[7] == img.flipZ
                && key[8] == img.offX && key[9] == img.offZ && key[10] == img.depth;
    }
}
