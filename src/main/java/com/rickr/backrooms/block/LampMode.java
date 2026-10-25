package com.rickr.backrooms.block;

import net.minecraft.util.StringRepresentable;

/** What a Level 9 streetlamp is doing: burning steadily, flickering, or burnt out. */
public enum LampMode implements StringRepresentable {
    LIT("lit"),
    FLICKER("flicker"),
    DEAD("dead");

    /** The lowercase name used in blockstate files ("mode=dead"). */
    private final String name;

    LampMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
