package com.rickr.backrooms.entity;

/**
 * Client-only, purely visual memory of one Bacteria's body between frames: where its head is
 * aimed, the "springs" that carry every joint smoothly from one frame to the next, and whether
 * there is a wall close to either side. The model (client/BacteriaModel) reads and updates it
 * every frame; the entity updates the walls. It lives on the entity so each Bacteria has its own,
 * made once with the entity: nothing is created per frame. The server never touches it.
 */
public final class BacteriaPoseState {
    /** How many joint values the model smooths with springs (it numbers them itself). */
    public static final int SPRING_CHANNELS = 48;

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

    /** How close a wall is to its right (0) and left (1) side: 0 = none, 1 = within Bacteria's NARROW_CHECK (2.4 blocks). */
    public final float[] narrow = new float[2];
    /** The same a tick earlier, so the model can ease between the two within a tick. */
    public final float[] narrowO = new float[2];

    /** A player near it that it can see (within 24 blocks), chosen a few times a second; -1 = none. */
    public int watchId = -1;
    /** The nearest player within 32 blocks, seen or not (for peeking and the test poses); -1 = none. */
    public int nearestId = -1;
}
