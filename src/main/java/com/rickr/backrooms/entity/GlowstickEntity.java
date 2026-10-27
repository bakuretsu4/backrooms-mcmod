package com.rickr.backrooms.entity;

import com.rickr.backrooms.item.GlowstickItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A cracked glowstick lying on the floor: a soft steady green light for 8 minutes that fades over
 * its last 30 s. Players use them to mark a route (together with chalk). No effect on monsters.
 *
 * <p>Right-click it to pick it up again; the item remembers how much glow is left.
 */
public class GlowstickEntity extends ThrownLight {
    public static final int LIFE_TICKS = 8 * 60 * 20;
    public static final int FADE_TICKS = 30 * 20;

    public GlowstickEntity(EntityType<? extends GlowstickEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected int maxLife() {
        return LIFE_TICKS;
    }

    /** 1 for most of its life, easing down to 0 over the last 30 s. */
    public float glow(float partialTick) {
        return Mth.clamp((life() - partialTick) / FADE_TICKS, 0.0f, 1.0f);
    }

    /** Lets the crosshair target it, so it can be right-clicked. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /**
     * The stick itself is only 0.25 x 0.1 (it lies flat, and a bigger box would snag while it
     * rolls), which is a tiny target on the floor. Aiming counts this much extra around it.
     */
    @Override
    public float getPickRadius() {
        return 0.2f;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            if (!player.getInventory().add(GlowstickItem.withLife(life()))) {
                return InteractionResult.PASS; // inventory full: leave it lying
            }
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.4f);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}
