package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level227PaletteBlock;
import com.rickr.backrooms.registry.Level227Blocks;
import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.registry.Level227FloraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The two plant hazards of Level 227 that work by being near you (server side):
 *
 * <p><b>GammaPali gas.</b> Within {@link #GAS_RADIUS} blocks of a GammaPali you breathe its glow:
 * you are poisoned at once, your air runs out in about two seconds (the vanilla air bubbles show
 * it draining, the clearest warning there is), and then the gas suffocates you, a few seconds more
 * to death. Step out and the air comes back. The danger zone is the faint glowing haze the plant
 * shows round itself (Level227GammaPaliBlock), so it reads clearly and can be avoided.
 *
 * <p><b>The Palette.</b> When a player passes under a Palette (within {@link #PALETTE_REACH} blocks
 * sideways, up to {@link #PALETTE_HEIGHT} above), it wakes: creaks, then drops (Level227PaletteBlock).
 *
 * <p>Both are block scans round each player a few times a second, with one reused position
 * object: a few hundred block lookups per player, nothing allocated.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Hazards {
    /** How near a GammaPali (to the middle of its block) the gas reaches you. */
    public static final double GAS_RADIUS = 2.5;
    private static final int GAS_SCAN = 3;
    /** Air lost per check while in the gas (on top of vanilla's refill): a full 300 goes in ~2 s. */
    private static final int AIR_LOSS = 55;
    /** Suffocation damage per check (5 ticks) once the air is gone: four hearts a second, dead in under three seconds. */
    private static final float CHOKE_DAMAGE = 2.0f;

    /** Sideways reach and height of the space under a Palette that wakes it. */
    public static final double PALETTE_REACH = 3.0;
    public static final int PALETTE_HEIGHT = 10;

    private static final int CHECK_EVERY = 5;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % CHECK_EVERY != 0
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_227) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            if (inGas(level, player, cursor)) {
                breatheGas(level, player);
            }
            wakePalette(level, player, cursor);
        }
    }

    /** Is any GammaPali close enough to the player's body (feet to eyes)? */
    private static boolean inGas(ServerLevel level, ServerPlayer player, BlockPos.MutableBlockPos cursor) {
        int px = player.getBlockX();
        int py = player.getBlockY();
        int pz = player.getBlockZ();
        double feet = player.getY();
        double eyes = player.getEyeY();
        double r2 = GAS_RADIUS * GAS_RADIUS;
        for (int dy = -GAS_SCAN; dy <= GAS_SCAN + 1; dy++) {
            for (int dx = -GAS_SCAN; dx <= GAS_SCAN; dx++) {
                for (int dz = -GAS_SCAN; dz <= GAS_SCAN; dz++) {
                    BlockState state = level.getBlockState(cursor.set(px + dx, py + dy, pz + dz));
                    if (!state.is(Level227Blocks.GAMMAPALI.get())) {
                        continue;
                    }
                    double cx = px + dx + 0.5 - player.getX();
                    double cz = pz + dz + 0.5 - player.getZ();
                    // Height difference to the nearest point of the body between the feet and the eyes.
                    double cy = py + dy + 0.5;
                    double ey = cy < feet ? feet - cy : (cy > eyes ? cy - eyes : 0.0);
                    if (cx * cx + cz * cz + ey * ey <= r2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void breatheGas(ServerLevel level, ServerPlayer player) {
        // Poison II, kept topped up while you stay in it (it can't kill on its own, the choking does).
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, true));
        int air = player.getAirSupply();
        player.setAirSupply(Math.max(-20, air - AIR_LOSS));
        if (air <= 0) {
            // The air is gone: the gas chokes you (CHOKE_DAMAGE every check).
            DamageSource gas = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(Level227Flora.GAMMAPALI_DAMAGE));
            player.hurt(gas, CHOKE_DAMAGE);
        }
        if (level.getGameTime() % 30 == 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), Level227FloraSounds.GAMMAPALI_CHOKE.get(),
                    SoundSource.PLAYERS, 0.9f, 0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    /** Wakes the first sleeping Palette hanging over the player (one per check is plenty). */
    private static void wakePalette(ServerLevel level, ServerPlayer player, BlockPos.MutableBlockPos cursor) {
        int reach = (int) Math.ceil(PALETTE_REACH);
        int px = player.getBlockX();
        int py = player.getBlockY();
        int pz = player.getBlockZ();
        for (int dy = 2; dy <= PALETTE_HEIGHT; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockState state = level.getBlockState(cursor.set(px + dx, py + dy, pz + dz));
                    if (!Level227PaletteBlock.isWaiting(state)) {
                        continue;
                    }
                    double cx = px + dx + 0.5 - player.getX();
                    double cz = pz + dz + 0.5 - player.getZ();
                    // It can only drop if it hangs over open space.
                    if (cx * cx + cz * cz <= PALETTE_REACH * PALETTE_REACH
                            && level.getBlockState(cursor.set(px + dx, py + dy - 1, pz + dz)).getCollisionShape(level, cursor).isEmpty()) {
                        Level227PaletteBlock.wake(level, cursor.set(px + dx, py + dy, pz + dz).immutable());
                        return;
                    }
                }
            }
        }
    }

    private Level227Hazards() {}
}
