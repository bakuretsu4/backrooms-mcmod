package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.level9.AnsweringMachineBlock;
import com.rickr.backrooms.block.level9.KidsDrawingBlock;
import com.rickr.backrooms.block.level9.MissingPosterBlock;
import com.rickr.backrooms.block.level9.PaperMount;
import com.rickr.backrooms.block.level9.PinnedPaperBlock;
import com.rickr.backrooms.block.level9.WatchFlyerBlock;
import com.rickr.backrooms.item.WatchFlyerItem;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The story things on Level 9's streets: what the people who lived here left behind
 * (block/level9). Like the rest of the suburb, every choice is a hash of the world seed and the
 * position, so each column can be built on its own (see Level9Layout).
 *
 * <p>Outdoors this class places them itself, through two small hooks in Level9Layout:
 * <ul>
 *   <li>{@link #sidewalk}: a missing-person poster on {@link #LAMP_POSTER} of the streetlamp
 *       poles (on the sidewalk side, at head height) and on {@link #FENCE_POSTER} of the front
 *       picket fence pieces (on the street side).</li>
 *   <li>{@link #lot}: a Neighborhood Watch flyer pinned to a porch post on {@link #PORCH_FLYER} of
 *       the houses ({@link #SAFE_PORCH_FLYER} of the safe houses: whoever runs them wants you to
 *       know the rules), a poster on the garage door of {@link #GARAGE_POSTER} of the houses with a
 *       garage, and one on the front corner of {@link #WALL_POSTER} of the houses.</li>
 * </ul>
 * Indoors the house interiors (Level9Houses) place the answering machines, the kids' drawings and
 * the odd poster on a fridge, with the state helpers below so the variants are picked the same way.
 * Mailboxes hand out flyers when opened (block/level9/MailboxBlock); supply crates have them in
 * their Level 9 loot tables.
 */
public final class Level9Story {
    /** Share of streetlamp poles with a missing-person poster on them. */
    static final double LAMP_POSTER = 0.22;
    /** Share of front picket-fence pieces with a poster on their post. */
    static final double FENCE_POSTER = 0.05;
    /** Share of houses with a garage that have a poster on the garage door. */
    static final double GARAGE_POSTER = 0.10;
    /** Share of houses with a poster on the front corner of the house. */
    static final double WALL_POSTER = 0.04;
    /** Share of houses with a Neighborhood Watch flyer pinned to a porch post. */
    static final double PORCH_FLYER = 0.18;
    /** The same for the safe houses. */
    static final double SAFE_PORCH_FLYER = 0.6;

    /** Posters on poles sit at head height: one block above the sidewalk's top. */
    private static final int POLE_POSTER_Y = 43;
    /** Posters on fence posts sit in the block beside the fence (lawn level). */
    private static final int FENCE_POSTER_Y = 42;
    /** Flyers and posters on porch posts, garage doors and house walls. */
    private static final int HOUSE_PAPER_Y = 43;

    private Level9Story() {}

    // ---- States (also used by the house interiors) ----------------------------------------------

    /** An answering machine with a waiting message (its light blinks); FACING = its front. */
    public static BlockState answeringMachine(long seed, int x, int z, Direction front) {
        return ModBlocks.ANSWERING_MACHINE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, front)
                .setValue(AnsweringMachineBlock.MESSAGE, Hash.range(Hash.of(seed, x, z, 9301), AnsweringMachineBlock.MESSAGES))
                .setValue(AnsweringMachineBlock.UNHEARD, true)
                .setValue(AnsweringMachineBlock.PLAYING, false);
    }

    /** A child's drawing taped to a wall; FACING = away from the wall. */
    public static BlockState kidsDrawing(long seed, int x, int z, Direction awayFromWall) {
        return ModBlocks.KIDS_DRAWING.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, awayFromWall)
                .setValue(KidsDrawingBlock.DRAWING, Hash.range(Hash.of(seed, x, z, 9302), KidsDrawingBlock.DRAWINGS));
    }

    /** A missing-person poster flat on a wall (or a fridge door); FACING = away from it. */
    public static BlockState missingPoster(long seed, int x, int z, Direction awayFromWall) {
        return poster(seed, x, z, awayFromWall, PaperMount.WALL);
    }

    static BlockState poster(long seed, int x, int z, Direction away, PaperMount mount) {
        return ModBlocks.MISSING_POSTER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, away)
                .setValue(PinnedPaperBlock.MOUNT, mount)
                .setValue(MissingPosterBlock.PERSON, Hash.range(Hash.of(seed, x, z, 9303), MissingPosterBlock.PEOPLE));
    }

    static BlockState flyer(long seed, int x, int z, Direction away, PaperMount mount) {
        return ModBlocks.WATCH_FLYER_PINNED.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, away)
                .setValue(PinnedPaperBlock.MOUNT, mount)
                .setValue(WatchFlyerBlock.FLYER, Hash.range(Hash.of(seed, x, z, 9304), WatchFlyerItem.FLYERS));
    }

    // ---- Hooks from Level9Layout ----------------------------------------------------------------

    /**
     * Called for every plain sidewalk column (not the curb). If a streetlamp pole stands right
     * next to it on the road side, or a front picket fence on the lawn side, this column may hold
     * the poster stapled to it. The cheap hash test comes first, so the layout lookups only run for
     * the few columns that could get one.
     */
    static void sidewalk(Level9Layout layout, long seed, int x, int z, LevelLayout.Column out) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX();
            int nz = z + d.getStepZ();
            // A pole whose arm points away from this column, over the road: we are behind it.
            if (Hash.unit(seed, nx, nz, 9305) < LAMP_POSTER && layout.poleAt(seed, nx, nz) == d) {
                out.set(POLE_POSTER_Y, poster(seed, nx, nz, d.getOpposite(), PaperMount.LAMP));
                return;
            }
            if (Hash.unit(seed, nx, nz, 9306) < FENCE_POSTER && layout.classify(seed, nx, nz) == Level9Layout.OTHER) {
                Level9Layout.House h = layout.houseAt(seed, nx, nz);
                // The front fence (v = 0) of a lot whose front faces this column; not across a path or driveway.
                if (h != null && h.clipA == null && h.v == 0 && h.front == d.getOpposite()
                        && Level9Layout.fenceKind(h, h.u, h.v) == 2) {
                    out.set(FENCE_POSTER_Y, poster(seed, nx, nz, d.getOpposite(), PaperMount.POST));
                    return;
                }
            }
        }
    }

    /**
     * Called for every column of an ordinary house lot, after the yard and the house are built.
     * {@code k} and {@code idx} name the lot (as in House.build), so per-house choices agree in
     * every column of the lot. Only writes in front of the house, never inside it.
     */
    static void lot(long seed, int k, int idx, Level9Layout.House h, int u, int v, LevelLayout.Column out) {
        int s = h.setback;
        // A house without a porch (porchU0 < 0, e.g. one still being built) has no posts and no trim.
        boolean finished = h.porchU0 >= 0;
        // The flyer on a porch post: in the lawn column right in front of the post, facing the street.
        if (finished && v == s - 3 && (u == h.porchU0 || u == h.porchU1)) {
            double chance = h.safe ? SAFE_PORCH_FLYER : PORCH_FLYER;
            boolean left = Hash.unit(seed, idx, k, 9307) < 0.5;
            if (Hash.unit(seed, idx, k, 9308) < chance && u == (left ? h.porchU0 : h.porchU1)) {
                out.set(HOUSE_PAPER_Y, flyer(seed, idx, k, h.front, PaperMount.POST));
            }
            return;
        }
        if (v != s - 1) {
            return;
        }
        // On the garage door: the driveway column in front of one of its three panels.
        if (h.garage && u >= h.gu0 + 1 && u <= h.gu0 + 3) {
            if (Hash.unit(seed, idx, k, 9309) < GARAGE_POSTER && u == h.gu0 + 1 + Hash.range(Hash.of(seed, idx, k, 9310), 3)) {
                out.set(HOUSE_PAPER_Y, poster(seed, idx, k + u, h.front, PaperMount.WALL));
            }
            return;
        }
        // On the front corner trim of the house (corners never have windows).
        if (finished && (u == h.hu0 || u == h.hu1) && !h.inPorch(u, v)) {
            boolean left = Hash.unit(seed, idx, k, 9311) < 0.5;
            if (Hash.unit(seed, idx, k, 9312) < WALL_POSTER && u == (left ? h.hu0 : h.hu1)) {
                out.set(HOUSE_PAPER_Y, poster(seed, idx, k - u, h.front, PaperMount.WALL));
            }
        }
    }
}
