package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.registry.Level227FloraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * The Palette (canon: "camouflaged against bark; drops on wanderers who come close and crushes
 * them"). It looks almost exactly like the bark round it (same grain, AXIS like bark) and clings
 * to the undersides of branches, overhangs and the bark above paths.
 *
 * <p>When a player passes under it (world/Level227Hazards spots that and calls {@link #wake}), it
 * creaks, and less than a second later lets go and drops as a falling block, aimed at where the
 * player is heading. The creak is the warning: run and it misses. If it lands on you, the crush
 * damage grows with the height of the fall (lethal from about five blocks).
 *
 * <p>Pieces of one Palette (touching Palette blocks) wake and fall together. Once it has fallen
 * it lies where it landed (FALLEN = true) and never wakes again.
 */
public class Level227PaletteBlock extends RotatedPillarBlock implements Fallable {
    /** A Palette that has already dropped (it lies on the ground now). */
    public static final BooleanProperty FALLEN = BooleanProperty.create("fallen");

    /** Ticks between the creak and the drop. */
    public static final int CREAK_TICKS = 16;
    /** Crush damage per block fallen, and the most it can do (vanilla anvils: 2 and 40). */
    private static final float DAMAGE_PER_BLOCK = 6.0f;
    private static final int DAMAGE_MAX = 30;
    /** Falling blocks: vanilla gravity and air drag per tick (FallingBlockEntity). */
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;
    /** How far (in blocks, sideways) it will lunge to land on its target. */
    private static final double MAX_LUNGE = 3.5;

    public Level227PaletteBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FALLEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FALLEN);
    }

    /**
     * Starts the drop: creaks now, falls {@link #CREAK_TICKS} later. Does nothing if it is already
     * waking or has fallen. The touching pieces of the same Palette follow when it drops ({@link #tick}).
     */
    public static void wake(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof Level227PaletteBlock block) || state.getValue(FALLEN)
                || level.getBlockTicks().hasScheduledTick(pos, block)) {
            return;
        }
        level.playSound(null, pos, Level227FloraSounds.PALETTE_CREAK.get(), SoundSource.BLOCKS,
                1.4f, 0.8f + level.random.nextFloat() * 0.25f);
        // Bits of bark trickle down: you might see it before you hear the drop.
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                10, 0.35, 0.05, 0.35, 0.0);
        level.scheduleTick(pos, block, CREAK_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(FALLEN)) {
            return;
        }
        // The touching pieces of the same Palette follow a moment later, so a big one peels off
        // rather than dropping as one slab (no extra creak: one is enough).
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState other = level.getBlockState(next);
            if (other.getBlock() == this && !other.getValue(FALLEN) && !level.getBlockTicks().hasScheduledTick(next, this)) {
                level.scheduleTick(next, this, 1 + random.nextInt(3));
            }
        }
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state.setValue(FALLEN, true));
        falling.setHurtsEntities(DAMAGE_PER_BLOCK, DAMAGE_MAX);
        falling.disableDrop();
        Player target = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 12.0, true);
        if (target != null && target.getY() < pos.getY()) {
            falling.setDeltaMovement(lunge(pos, target));
        }
    }

    /**
     * The sideways push that makes the falling block land where the target will be. A falling
     * block has no sideways speed of its own, so from straight above it would miss anyone a
     * step away. Counting the ticks it needs to fall that far (with vanilla gravity and drag),
     * the push covers the distance to the target's position by then (where it is now plus the
     * way it is moving), at most {@link #MAX_LUNGE} blocks.
     */
    private static Vec3 lunge(BlockPos pos, Player target) {
        double drop = pos.getY() - target.getY();
        int ticks = 0;
        double fallen = 0;
        double speed = 0;
        while (fallen < drop && ticks < 80) {
            speed = (speed + GRAVITY) * DRAG;
            fallen += speed;
            ticks++;
        }
        // Where the player will be: its movement over the last tick, carried on for the fall.
        Vec3 step = target.position().subtract(target.xo, target.yo, target.zo);
        double tx = target.getX() + step.x * ticks * 0.8;
        double tz = target.getZ() + step.z * ticks * 0.8;
        double dx = tx - (pos.getX() + 0.5);
        double dz = tz - (pos.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > MAX_LUNGE) {
            dx *= MAX_LUNGE / dist;
            dz *= MAX_LUNGE / dist;
        }
        // A sideways speed v covers v * (1 + 0.98 + 0.98^2 + ...) over the fall.
        double reach = (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
        return new Vec3(dx / reach, 0, dz / reach);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaceableState, FallingBlockEntity fallingBlock) {
        crash(level, pos, state);
    }

    /** It landed somewhere it can't lie (in tall grass, on a plant): it breaks up instead. */
    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        crash(level, pos, fallingBlock.getBlockState());
    }

    private static void crash(Level level, BlockPos pos, BlockState state) {
        level.playSound(null, pos, Level227FloraSounds.PALETTE_CRUSH.get(), SoundSource.BLOCKS, 1.6f,
                0.85f + level.random.nextFloat() * 0.2f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.2,
                    pos.getZ() + 0.5, 40, 0.6, 0.2, 0.6, 0.15);
        }
    }

    /** Death message "was crushed by a Palette" (data/backrooms/damage_type/palette.json). */
    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(Level227Flora.PALETTE_DAMAGE), entity);
    }

    /** True if a Palette at this spot is still waiting for someone. */
    public static boolean isWaiting(BlockState state) {
        return state.getBlock() instanceof Level227PaletteBlock && !state.getValue(FALLEN);
    }
}
