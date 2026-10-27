package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.LightSupplies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A struck road flare lying on the floor. It burns red-orange for 75 s, sputters out over 3 s,
 * then lies there smoking for 30 s and disappears. It keeps burning underwater, like a marine
 * flare (bubbles instead of smoke).
 *
 * <p>Its light is client-only (Veil, see {@code client/light/ThrownLights}), so the monsters ask
 * {@link #litByFlare} / {@link #nearestBurning} instead: Smilers won't stay in its light, the
 * Bacteria spot you in it as if you stood under a lamp, and Level 9's Neighborhood Watch comes
 * over to look at it.
 */
public class FlareEntity extends ThrownLight {
    public static final int BURN_TICKS = 75 * 20;
    public static final int SPUTTER_TICKS = 3 * 20;
    public static final int SPENT_TICKS = 30 * 20;
    /**
     * How far from a burning flare it is "bright" for the monsters. The light reaches about 12
     * blocks, but its last few blocks are dim, about like the edge of a lamp's pool.
     */
    public static final double MONSTER_LIGHT_RADIUS = 9.0;

    public FlareEntity(EntityType<? extends FlareEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected int maxLife() {
        return BURN_TICKS + SPUTTER_TICKS + SPENT_TICKS;
    }

    /** Burning brightly (not yet sputtering). */
    public boolean burning() {
        return life() > SPUTTER_TICKS + SPENT_TICKS;
    }

    /** In its last 3 s of flame. */
    public boolean sputtering() {
        return !burning() && life() > SPENT_TICKS;
    }

    /** Burning or sputtering: it gives light. */
    public boolean lit() {
        return life() > SPENT_TICKS;
    }

    /** 1 at the start of the sputter, 0 when the flame is out (1 while burning normally). */
    public float sputterLeft(float partialTick) {
        if (burning()) {
            return 1.0f;
        }
        return Mth.clamp((life() - partialTick - SPENT_TICKS) / SPUTTER_TICKS, 0.0f, 1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (level().isClientSide) {
            particles();
        } else if (life() == SPENT_TICKS + SPUTTER_TICKS) {
            level().playSound(null, getX(), getY(), getZ(), LightSupplies.FLARE_SPUTTER.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    /** Only a few: a spark now and then and a thin thread of smoke (bubbles underwater). */
    private void particles() {
        RandomSource random = level().getRandom();
        Vec3 tip = tipPosition();
        boolean water = isInWater();
        if (lit()) {
            int smokeChance = sputtering() ? 3 : 5;
            if (random.nextInt(smokeChance) == 0) {
                if (water) {
                    level().addParticle(ParticleTypes.BUBBLE, tip.x, tip.y, tip.z, 0.0, 0.08, 0.0);
                } else {
                    level().addParticle(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0.0, 0.02, 0.0);
                }
            }
            if (!water && random.nextInt(sputtering() ? 3 : 7) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z,
                        (random.nextDouble() - 0.5) * 0.08, random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.08);
            }
        } else if (!water && random.nextInt(10) == 0) {
            // A spent flare keeps smoking a little.
            level().addParticle(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0.0, 0.01, 0.0);
        }
    }

    /** The burning end of the stick (the renderer draws the tip at the stick's +Z end). */
    public Vec3 tipPosition() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * 0.17, 0.05, Mth.cos(yaw) * 0.17);
    }

    /**
     * True when a burning flare lights {@code where}: within {@link #MONSTER_LIGHT_RADIUS} and with
     * nothing solid in between (its light doesn't go through walls either). Works in a blackout,
     * because the flare doesn't need the mains.
     */
    public static boolean litByFlare(Level level, Vec3 where) {
        for (FlareEntity flare : level.getEntitiesOfClass(FlareEntity.class,
                new AABB(where, where).inflate(MONSTER_LIGHT_RADIUS), FlareEntity::lit)) {
            if (flare.canLight(where, MONSTER_LIGHT_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /** The closest lit flare within {@code range} of {@code where} (no line of sight needed), or null. */
    @Nullable
    public static FlareEntity nearestBurning(Level level, Vec3 where, double range) {
        FlareEntity best = null;
        double bestSq = range * range;
        for (FlareEntity flare : level.getEntitiesOfClass(FlareEntity.class, new AABB(where, where).inflate(range), FlareEntity::lit)) {
            double d = flare.position().distanceToSqr(where);
            if (d <= bestSq) {
                best = flare;
                bestSq = d;
            }
        }
        return best;
    }

    private boolean canLight(Vec3 where, double radius) {
        Vec3 from = lightPosition(1.0f);
        if (from.distanceToSqr(where) > radius * radius) {
            return false;
        }
        return level().clip(new ClipContext(from, where, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
}
