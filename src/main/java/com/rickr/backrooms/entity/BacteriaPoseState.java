package com.rickr.backrooms.entity;

/**
 * Client-only, purely visual memory of one Bacteria's body between frames: where its head is
 * aimed, the arm pose it showed last frame (so a change of gait blends over a few ticks instead
 * of jumping), and whether there is a wall close to either side. The model
 * (client/BacteriaModel) reads and updates it every frame; the entity updates the walls. It lives
 * on the entity so each Bacteria has its own, made once with the entity: nothing is created per
 * frame. The server never touches it.
 */
public final class BacteriaPoseState {
    /** Ticks (ageInTicks) of the last frame, or NaN before the first one. */
    public float lastAge = Float.NaN;

    /** Where the head is aimed, as a world yaw/pitch in degrees (Minecraft's convention). */
    public float aimYaw;
    public float aimPitch;
    public boolean aimed;

    /** The pose (state) shown last frame, and when the current one started blending in. */
    public int lastState = -1;
    public float blendStart;
    // The arm angles at the moment the pose changed (blended away from), and those shown last
    // frame (radians; 0 = right arm, 1 = left arm).
    public final float[] fromRaise = new float[2];
    public final float[] fromOut = new float[2];
    public final float[] fromBend = new float[2];
    public final float[] fromSplay = new float[2];
    public final float[] fromForeOut = new float[2];
    public final float[] shownRaise = new float[2];
    public final float[] shownOut = new float[2];
    public final float[] shownBend = new float[2];
    public final float[] shownSplay = new float[2];
    public final float[] shownForeOut = new float[2];
    public boolean shown;

    /** How close a wall is to its right (0) and left (1) side: 0 = none, 1 = within 1.5 blocks. */
    public final float[] narrow = new float[2];

    /** A player near it that it can see (within 24 blocks), chosen a few times a second; -1 = none. */
    public int watchId = -1;
    /** The nearest player within 32 blocks, seen or not (for peeking and the test poses); -1 = none. */
    public int nearestId = -1;
}
