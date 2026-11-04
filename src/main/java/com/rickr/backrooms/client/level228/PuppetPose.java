package com.rickr.backrooms.client.level228;

/**
 * How one mirror stand-in is drawn this frame: where it appears, how the mirror maps it, and what
 * (if anything) differs from the real body it reflects: a lie's own pose, a puzzle mirror's flaw,
 * a funhouse mirror's stretch. One instance, refilled for every stand-in, so drawing reflections
 * allocates nothing.
 */
final class PuppetPose {
    /** Feet where the stand-in appears (world coordinates, real space). */
    double x;
    double y;
    double z;
    /** The mirror map's handedness per axis (+1/-1), from the image. */
    int flipX;
    int flipZ;
    /** False: angles, swing and crouch are the real entity's at this partial tick. */
    boolean override;
    /** Body yaw, head yaw and pitch in the REAL body's frame (the mirror map is applied on top). */
    float body;
    float head;
    float pitch;
    /** Limb swing: walk cycle position and amount (vanilla's WalkAnimationState values). */
    float walkPos;
    float walkSpeed;
    /** Arm swing progress 0..1 (vanilla's attackAnim). */
    float attack;
    boolean crouch;
    /** Sideways head tilt (degrees, about the face's forward axis); applied even without override. */
    float headRoll;
    /** Extra head pitch (degrees, nodding) added to whatever pitch is drawn. */
    float nod;
    /** Opacity 0..1. */
    float alpha;
    /** Funhouse mirrors: height and width factors about the feet (1 = true). */
    float scaleY;
    float scaleXZ;
    /** Drawn as a rotated copy instead of a reflection: text and asymmetric skin read the wrong way. */
    boolean unmirrored;
    /** The main-hand item is carried on the other arm. */
    boolean wrongHand;
    /** Armour slot (Inventory.armor index 0 feet .. 3 head) drawn empty; -1: all as worn. */
    int dropArmour;
    /** The main-hand item is not drawn. */
    boolean dropHeld;
    /** The hat (outer head) skin layer is not drawn. */
    boolean dropHat;
    /** Eye glint 0..1 (the imitator's tell): two tiny full-bright quads on the face. */
    float glint;

    /** The plain reflection at (x, y, z): everything as the real body has it. */
    void reset(double px, double py, double pz, int fx, int fz) {
        x = px;
        y = py;
        z = pz;
        flipX = fx;
        flipZ = fz;
        override = false;
        crouch = false;
        headRoll = 0.0f;
        nod = 0.0f;
        alpha = 1.0f;
        scaleY = 1.0f;
        scaleXZ = 1.0f;
        unmirrored = false;
        wrongHand = false;
        dropArmour = -1;
        dropHeld = false;
        dropHat = false;
        glint = 0.0f;
    }
}
