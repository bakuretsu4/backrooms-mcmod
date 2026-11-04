package com.rickr.backrooms.client.level228;

/**
 * How one mirror puppet is drawn this frame: where it stands in the image volume, and (when a
 * reflection lies) the pose it is drawn in instead of the real player's. One instance, refilled
 * for every puppet, so drawing reflections allocates nothing.
 */
final class PuppetPose {
    /** Feet position in the image volume (world coordinates). */
    double x;
    double y;
    double z;
    /** False: draw the player exactly as they are right now (the angles below are unused). */
    boolean override;
    /** Body yaw, head yaw and pitch in the REAL player's frame (the mirror map is applied on top). */
    float body;
    float head;
    float pitch;
    /** Limb swing: walk cycle position and amount (vanilla's WalkAnimationState values). */
    float walkPos;
    float walkSpeed;
    /** Arm swing progress 0..1 (vanilla's attackAnim). */
    float attack;
    boolean crouch;
    /** Opacity 0..1 (a reflection walking off fades into the dark). */
    float alpha;
}
