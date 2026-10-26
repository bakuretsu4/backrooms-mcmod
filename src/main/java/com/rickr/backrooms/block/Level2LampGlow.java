package com.rickr.backrooms.block;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a Level 2 caged lamp's bulb is drawn glowing. The bulb normally renders full-bright
 * ({@code emissiveRendering}), but when its series circuit fails (client/light/FixturePower) the
 * whole circuit goes dark, so the bulb must look dead too. The answer is only known on the client
 * (it depends on the clock), so the client installs it here; everywhere else bulbs always glow.
 */
public final class Level2LampGlow {
    /** Set by the client at startup: is the bulb at this position lit right now? */
    public static volatile BiPredicate<BlockGetter, BlockPos> lit = (level, pos) -> true;

    /** For the block properties: {@code .emissiveRendering(Level2LampGlow::emissive)}. */
    public static boolean emissive(BlockState state, BlockGetter level, BlockPos pos) {
        return lit.test(level, pos);
    }

    private Level2LampGlow() {}
}
