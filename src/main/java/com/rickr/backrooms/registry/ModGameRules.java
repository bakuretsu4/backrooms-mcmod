package com.rickr.backrooms.registry;

import net.minecraft.world.level.GameRules;

/**
 * Our game rules (the ones you change with {@code /gamerule}). Game rules are not a NeoForge
 * registry: Minecraft keeps them in one plain list, and a rule must be added to it before any
 * world loads. That's why {@link #bootstrap()} is called from the mod's constructor.
 */
public final class ModGameRules {
    /**
     * {@code /gamerule backroomsSharedCheckpoints true|false} (default true). When on, a checkpoint
     * saved at a radio is also shared with the saver's team (or with everyone, if they have no
     * team): see {@code player/SharedCheckpoints}. When off, every checkpoint is private again.
     */
    public static final GameRules.Key<GameRules.BooleanValue> SHARED_CHECKPOINTS = GameRules.register(
            "backroomsSharedCheckpoints", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));

    /** Does nothing by itself: calling it loads this class, which adds the rules above. */
    public static void bootstrap() {}

    private ModGameRules() {}
}
