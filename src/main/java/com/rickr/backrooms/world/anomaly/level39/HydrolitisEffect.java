package com.rickr.backrooms.world.anomaly.level39;

import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * The Hydrolitis status effect. It does nothing by itself: {@link Hydrolitis} keeps it on an
 * infected player (its amplifier = the stage) so the game shows the icon and the client knows how
 * sick you are for the fever effects. The real symptoms come from {@link Hydrolitis}.
 */
public class HydrolitisEffect extends MobEffect {
    public HydrolitisEffect() {
        // A sickly turquoise-green, like the film on the stagnant water.
        super(MobEffectCategory.HARMFUL, 0x4FA38C);
    }

    /** Milk doesn't cure a plague: only almond water does (see {@link Hydrolitis}). */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
    }
}
