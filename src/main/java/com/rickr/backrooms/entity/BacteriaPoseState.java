package com.rickr.backrooms.entity;

/**
 * Client-only, purely visual memory of one Bacteria's body between frames: where its head is
 * aimed, the "springs" that carry every joint smoothly from one frame to the next, which walls
 * and furniture its claws are holding on to, and whether there is a wall close to either side.
 * The model (client/BacteriaModel) reads and updates it every frame; the entity updates the
 * walls. It lives on the entity so each Bacteria has its own, made once with the entity: nothing
 * is created per frame. The server never touches it.
 */
public final class BacteriaPoseState {
    /** How many joint values the model smooths with springs (it numbers them itself). */
    public static final int SPRING_CHANNELS = 48;
    /** What a claw is doing (gripMode): hanging free in its pose, reaching for a hold, holding on. */
    public static final int GRIP_FREE = 0;
    public static final int GRIP_REACHING = 1;
    public static final int GRIP_HOLDING = 2;
    /** What a claw holds (gripKind): a flat face (palm flat), an upright edge or a top edge (claws hooked over), the floor. */
    public static final int HOLD_FACE = 1;
    public static final int HOLD_EDGE = 2;
    public static final int HOLD_TOP = 3;
    public static final int HOLD_FLOOR = 4;

    /** Ticks (ageInTicks) of the last frame, or NaN before the first one. */
    public float lastAge = Float.NaN;

    /** Where the head is aimed, as a world yaw/pitch in degrees (Minecraft's convention). */
    public float aimYaw;
    public float aimPitch;
    /** How fast the head's aim is turning (degrees per tick). */
    public float aimYawSpeed;
    public float aimPitchSpeed;
    /** What the head was trying to look at last frame (degrees), to tell how fast that moves. */
    public float aimYawWanted;
    public float aimPitchWanted;
    public boolean aimed;

    // Each smoothed joint value is a little spring: where it is shown now, how fast it is moving
    // (per tick), and the value it was chasing last frame (to tell how fast that moves).
    public final float[] springPos = new float[SPRING_CHANNELS];
    public final float[] springVel = new float[SPRING_CHANNELS];
    public final float[] springLastTarget = new float[SPRING_CHANNELS];
    /** False until the first frame has set every spring straight onto its pose. */
    public boolean springsReady;

    // Holding on (the model's grip controller), per arm (0 = right, 1 = left). Positions are in
    // the world, so a held claw stays exactly where it took hold while the body moves.
    public final int[] gripMode = new int[2];
    public final int[] gripKind = new int[2];
    /** Where the claw holds on, and which way the surface faces (flat, out of the surface). */
    public final double[] gripX = new double[2];
    public final double[] gripY = new double[2];
    public final double[] gripZ = new double[2];
    public final float[] gripNX = new float[2];
    public final float[] gripNZ = new float[2];
    /** Where the claw was when the reach began (it travels from there to the hold). */
    public final double[] fromX = new double[2];
    public final double[] fromY = new double[2];
    public final double[] fromZ = new double[2];
    /** The place the hold was looked for near (a new place means a new hold). */
    public final double[] wantX = new double[2];
    public final double[] wantY = new double[2];
    public final double[] wantZ = new double[2];
    /** When the reach began and how long it takes; when the hold began and how long it lasts (ticks). */
    public final float[] reachStart = new float[2];
    public final float[] reachLen = new float[2];
    public final float[] holdStart = new float[2];
    public final float[] holdLen = new float[2];
    /** After finding nothing to hold, a claw doesn't look again until this tick. */
    public final float[] senseAfter = new float[2];
    /** How many holds each claw has taken (seeds the next hold's timing). */
    public final int[] gripCount = new int[2];
    /** Each claw tip as shown last frame (world), so a reach starts from where the claw really is. */
    public final double[] clawX = new double[2];
    public final double[] clawY = new double[2];
    public final double[] clawZ = new double[2];
    public boolean clawsKnown;
    /** Counts the claws taking hold (the entity plays a quiet scrape for each). */
    public int gripLanded;

    // Running (the model's run): each arm keeps its own clock (where it is in its swing, 0-1), so
    // the two arms drift in and out of step instead of mirroring each other.
    public final float[] runArm = new float[2];
    public boolean runArmsReady;
    /**
     * A running claw on the floor stays exactly where it landed (world), while it is planted;
     * plantY is how high it was when it landed (it settles onto the floor from there) and
     * plantAt when (ageInTicks).
     */
    public final boolean[] plantLocked = new boolean[2];
    public final double[] plantX = new double[2];
    public final double[] plantY = new double[2];
    public final double[] plantZ = new double[2];
    public final float[] plantAt = new float[2];
    /** How firmly each planted claw is still held (1, falling if its spot gets out of reach). */
    public final float[] plantHold = new float[2];
    /** When (ageInTicks) it was last running, or NaN: stopping, it skids and its arms swing on. */
    public float runningAt = Float.NaN;

    /** How close a wall is to its right (0) and left (1) side: 0 = none, 1 = within Bacteria's NARROW_CHECK (2.4 blocks). */
    public final float[] narrow = new float[2];
    /** The same a tick earlier, so the model can ease between the two within a tick. */
    public final float[] narrowO = new float[2];

    /** A player near it that it can see (within 24 blocks), chosen a few times a second; -1 = none. */
    public int watchId = -1;
    /** The nearest player within 32 blocks, seen or not (for peeking and the test poses); -1 = none. */
    public int nearestId = -1;
}
