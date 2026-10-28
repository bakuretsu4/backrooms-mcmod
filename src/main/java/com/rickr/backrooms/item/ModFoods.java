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

    /** Chewy energy bar: 3 hunger, eaten quickly, and you can eat it even when full (it's for the sprint energy). */
    public static final FoodProperties ENERGY_BAR = new FoodProperties.Builder()
            .nutrition(3).saturationModifier(0.4f)
            .fast().alwaysEdible()
            .build();

    /**
     * Raw Level 227 boar meat: 3 hunger, and raw game from a tree that isn't a tree is a gamble:
     * often Hunger (30 s), sometimes nausea (10 s). Cook it first.
     */
    public static final FoodProperties BOAR_MEAT = new FoodProperties.Builder()
            .nutrition(3).saturationModifier(0.3f)
            .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 600), 0.6f)
            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200), 0.25f)
            .build();

    /** Cooked boar meat: safe (as the wiki says), and the biggest meal in the mod: 9 hunger. */
    public static final FoodProperties COOKED_BOAR_MEAT = new FoodProperties.Builder()
            .nutrition(9).saturationModifier(0.85f)
            .build();

    private ModFoods() {}
}
