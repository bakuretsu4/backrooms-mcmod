package com.rickr.backrooms.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

/** Food found in wooden crates. The second number of effect() is the chance it triggers. */
public final class ModFoods {
    /** Old tin of something: 5 hunger, but 1 in 20 cans has gone bad (5 s of poison). */
    public static final FoodProperties CANNED_FOOD = new FoodProperties.Builder()
            .nutrition(5).saturationModifier(0.6f)
            .effect(() -> new MobEffectInstance(MobEffects.POISON, 100), 0.05f)
            .build();

    /** A pale mushroom growing in the damp carpet: 3 hunger, 10% chance to make you sick (nausea). */
    public static final FoodProperties BACKSHROOM = new FoodProperties.Builder()
            .nutrition(3).saturationModifier(0.3f)
            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200), 0.10f)
            .build();

    private ModFoods() {}
}
