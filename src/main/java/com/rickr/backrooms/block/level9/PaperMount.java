package com.rickr.backrooms.block.level9;

import net.minecraft.util.StringRepresentable;

/**
 * What a pinned sheet of paper (a missing-person poster, a Neighborhood Watch flyer) is stuck to.
 * The paper block always stands in the open block next to the thing it is stuck to, but a lamp
 * pole or a fence post is thinner than a whole block, so the paper is drawn a few pixels further
 * back (into the pole's block) so it lies flat on the pole instead of floating in the air.
 */
public enum PaperMount implements StringRepresentable {
    /** A full block: a house wall, a garage door, the fridge. */
    WALL("wall", 0.0),
    /** A Level 9 streetlamp pole (5 px wide, so its face is 5.5 px into its block). */
    LAMP("lamp", 5.5),
    /** A picket-fence or porch post (4 px wide, so its face is 6 px into its block). */
    POST("post", 6.0);

    private final String name;
    /** How far (pixels) behind this block's back edge the surface it is stuck to lies. */
    private final double depth;

    PaperMount(String name, double depth) {
        this.name = name;
        this.depth = depth;
    }

    public double depth() {
        return depth;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
