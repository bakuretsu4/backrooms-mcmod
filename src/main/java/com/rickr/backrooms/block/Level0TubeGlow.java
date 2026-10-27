package com.rickr.backrooms.block;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a Level 0 tube (the {@code fluorescent_light} troffer or a {@code l0_troffer_strip}) is
 * drawn glowing. Tubes normally render full-bright ({@code emissiveRendering}), but a faulty tube
 * goes dark for a moment when it stutters, and a dying tube only glows dimly, so its face must
 * stop glowing too or the ceiling would show a bright panel that gives no light. Which tubes are
 * faulty, and when, is only known on the client (it depends on the clock, see
 * client/light/FixturePower), so the client installs the answer here; everywhere else, and on
 * every other level, tubes always glow exactly as before.
 *
 * <p>Two things change for a dark tube: its faces stop being full-bright (they then take the
 * light of the room like the ceiling tiles around them), and its texture is tinted (the block
 * colour, through the model's tint index): grey for a stuttering tube that is off, so it reads
 * as switched off, and a lighter warm grey for a dying tube, so it reads as a weak old tube.
 */
public final class Level0TubeGlow {
    /** Set by the client at startup: is the tube block at this position dark right now? */
    public static volatile Predicate<BlockPos> dark = pos -> false;
    /** Set by the client at startup: is the tube block at this position a dying (dim, never fully lit) tube? */
    public static volatile Predicate<BlockPos> dying = pos -> false;

    /** Tint of a dark tube's lens: a dull grey, like the lens of a tube that's off. */
    private static final int DARK_TINT = 0x6A6A66;
    /**
     * Tint of a dying tube's lens: between lit white and the off grey, a little warm, so it reads
     * as a weak old tube that still glows faintly rather than a dead one.
     */
    private static final int DYING_TINT = 0xB8B49A;
    /** Tint of a lit tube: white, which leaves the texture exactly as it is. */
    private static final int LIT_TINT = 0xFFFFFF;

    /** For the block properties: {@code .emissiveRendering(Level0TubeGlow::emissive)}. */
    public static boolean emissive(BlockState state, BlockGetter level, BlockPos pos) {
        return !dark.test(pos);
    }

    /** The block colour of the tube's tinted faces (see the class comment). */
    public static int tint(@Nullable BlockPos pos) {
        if (pos == null || !dark.test(pos)) {
            return LIT_TINT;
        }
        return dying.test(pos) ? DYING_TINT : DARK_TINT;
    }

    private Level0TubeGlow() {}
}
