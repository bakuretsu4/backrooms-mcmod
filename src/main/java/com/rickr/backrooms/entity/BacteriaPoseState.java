package com.rickr.backrooms.entity;

/**
 * Client-only, purely visual memory of one Bacteria's body between frames: where its head is
 * aimed, how its dead-weight arms are swinging, and how it was moving last frame. The model
 * (client/BacteriaModel) reads and updates it every frame. It lives on the entity so each
 * Bacteria has its own, made once with the entity: nothing is created per frame. The server
 * never touches it.
 */
public final class BacteriaPoseState {
    /** Ticks (ageInTicks) of the last frame, or NaN before the first one. */
    public float lastAge = Float.NaN;

    /** Where the head is aimed, as a world yaw/pitch in degrees (Minecraft's convention). */
    public float aimYaw;
    public float aimPitch;
    public boolean aimed;

    // The arms as damped pendulums: position and speed of each arm's forward swing (raise),
    // sideways swing (out) and elbow (bend), in radians and radians per tick. 0 = right arm.
    public final float[] raise = new float[2];
    public final float[] raiseSpeed = new float[2];
    public final float[] out = new float[2];
    public final float[] outSpeed = new float[2];
    public final float[] bend = new float[2];
    public final float[] bendSpeed = new float[2];
    public boolean armsSet;

    // How the body moved last frame, to give the arms a shove when it speeds up, stops or turns.
    public double lastX;
    public double lastZ;
    public float lastForwardSpeed;
    public float lastYaw;
    public float lastYawRate;

    /** A player near it that it can see (within 24 blocks), chosen a few times a second; -1 = none. */
    public int watchId = -1;
    /** The nearest player within 32 blocks, seen or not (for peeking and the test poses); -1 = none. */
    public int nearestId = -1;
}
