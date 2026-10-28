package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Level 227's one hard rule: falling off a branch is the end (canon: you drop into the void, lost
 * for good). The fall itself is left alone, a long drop through the black under the branches; only
 * once you are below {@link Level227Tree#VOID_DEATH_Y} does the void take you. There is no exit
 * below: the level's transferBelowY is never reached, so falling never "uses an exit".
 *
 * <p>Creative and spectator players are spared, so the tree can be explored from underneath.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Rules {
    /** Death in the void (data/backrooms/damage_type/l227_void.json): "... was lost to the void". */
    public static final ResourceKey<DamageType> VOID_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("l227_void"));

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.isCreative()
                || !player.isAlive() || player.getY() >= Level227Tree.VOID_DEATH_Y) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_227) {
            return;
        }
        DamageSource source = new DamageSource(player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(VOID_DAMAGE));
        player.hurt(source, Float.MAX_VALUE);
    }

    private Level227Rules() {}
}
