package com.rickr.backrooms.block;

import net.minecraft.util.StringRepresentable;

/**
 * Which picture a Level 9 street sign shows (texture {@code street_sign_<kind>.png}).
 * Left and right are as seen by someone reading the sign. The arrows point towards the exit.
 */
public enum SignKind implements StringRepresentable {
    ARROW_LEFT("arrow_left"),
    ARROW_RIGHT("arrow_right"),
    ARROW_AHEAD("arrow_ahead"),
    /** "Neighborhood Watch" notice. */
    WATCH_NOTICE("watch_notice"),
    /** "Quiet zone" notice. */
    QUIET_NOTICE("quiet_notice"),
    /** Yellow diamond warning sign with an eye. */
    WARNING("warning"),
    /** A traffic sign gone wrong: scrambled words. */
    NONSENSE("nonsense");

    /** The lowercase name used in blockstate files ("kind=warning"). */
    private final String name;

    SignKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
