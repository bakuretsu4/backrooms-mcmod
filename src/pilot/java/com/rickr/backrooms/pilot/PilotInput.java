package com.rickr.backrooms.pilot;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Holds keys the same way a person does: through the game's own KeyMapping state, so movement,
 * sprinting and sneaking go through the normal input code and the server sees nothing unusual.
 *
 * Held keys are re-asserted at the start of every client tick because the game legitimately
 * resets key states on its own (closing a screen re-reads the physical keyboard, opening one
 * releases everything). Keys we have let go of are left alone so a human can still play.
 */
final class PilotInput {
    static final String[] NAMES = {"forward", "back", "left", "right", "jump", "sneak", "sprint", "use", "attack"};
    static final int FORWARD = 0, BACK = 1, LEFT = 2, RIGHT = 3, JUMP_KEY = 4, SNEAK = 5, SPRINT = 6, USE = 7, ATTACK = 8;

    private final boolean[] held = new boolean[NAMES.length];

    static int indexOf(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static KeyMapping mapping(Options o, int i) {
        return switch (i) {
            case FORWARD -> o.keyUp;
            case BACK -> o.keyDown;
            case LEFT -> o.keyLeft;
            case RIGHT -> o.keyRight;
            case JUMP_KEY -> o.keyJump;
            case SNEAK -> o.keyShift;
            case SPRINT -> o.keySprint;
            case USE -> o.keyUse;
            default -> o.keyAttack;
        };
    }

    boolean isHeld(int i) {
        return held[i];
    }

    void set(int i, boolean down) {
        if (held[i] == down) {
            return;
        }
        held[i] = down;
        if (!down) {
            mapping(Minecraft.getInstance().options, i).setDown(false); // let go once, then hands off
        } else {
            apply();
        }
    }

    void releaseAll() {
        for (int i = 0; i < held.length; i++) {
            set(i, false);
        }
    }

    /** Called at the start of each client tick, before the game reads its keys. */
    void apply() {
        Options o = Minecraft.getInstance().options;
        for (int i = 0; i < held.length; i++) {
            if (held[i]) {
                KeyMapping km = mapping(o, i);
                // Only press when not already down: sneak/sprint may be in toggle mode, where every
                // setDown(true) flips the state instead of holding it.
                if (!km.isDown()) {
                    km.setDown(true);
                }
            }
        }
    }

    /** One press-and-release, exactly what a mouse button click queues up for the next tick. */
    static boolean click(int i) {
        KeyMapping km = mapping(Minecraft.getInstance().options, i);
        if (km.isUnbound()) {
            return false;
        }
        KeyMapping.click(km.getKey());
        return true;
    }

    /**
     * Point the view like mouse movement would. The previous-tick angles are set too so the camera
     * does not visibly swing through the interpolation between ticks.
     */
    static void look(LocalPlayer p, float yaw, float pitch) {
        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
    }

    static float yawTowards(double dx, double dz) {
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    static float pitchTowards(double dx, double dy, double dz) {
        return (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
    }

    void describe(com.google.gson.JsonObject out) {
        com.google.gson.JsonObject k = new com.google.gson.JsonObject();
        for (int i = 0; i < NAMES.length; i++) {
            k.addProperty(NAMES[i], held[i]);
        }
        out.add("heldKeys", k);
    }
}
