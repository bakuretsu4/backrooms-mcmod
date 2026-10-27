package com.rickr.backrooms.mixin;

import com.rickr.backrooms.world.RealityDrift;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a drifted player's fake blocks ({@code world/RealityDrift}) in place when they click them.
 *
 * <p>After every right-click on a block, and after some left-clicks, vanilla re-sends the REAL
 * block (and its neighbour) to the clicking player, so their screen agrees with the server. For
 * someone looking at a fake door that would show the plain wall again: the illusion would break on
 * a click. Right after vanilla has done that (still inside the same packet, so the client gets
 * both updates together and never draws the real one), we send the fake again.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DriftPacketListenerMixin {
    /** The end of the right-click handling (its only return), after the two real-block updates. */
    @Inject(method = "handleUseItemOn", at = @At("TAIL"))
    private void backrooms$keepDriftAfterUse(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        RealityDrift.reassert(((ServerGamePacketListenerImpl) (Object) this).player, packet.getHitResult().getBlockPos());
    }

    /** Right after the left-click handling (start/abort/stop breaking), which can also re-send the real block. */
    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;handleBlockBreakAction(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V"))
    private void backrooms$keepDriftAfterAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        RealityDrift.reassert(((ServerGamePacketListenerImpl) (Object) this).player, packet.getPos());
    }
}
