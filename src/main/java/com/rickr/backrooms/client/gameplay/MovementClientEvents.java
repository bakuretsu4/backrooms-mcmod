package com.rickr.backrooms.client.gameplay;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.EnergyClientState;
import com.rickr.backrooms.world.Travel;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Client half of the Backrooms movement rules. The server enforces them too; doing them here as
 * well keeps your own movement prediction in line with the server, so you don't snap back.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class MovementClientEvents {
    /** Vanilla only starts (or keeps) a sprint when forward input is at least 0.8. */
    private static final float BELOW_SPRINT_IMPULSE = 0.79f;

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (!Travel.inBackrooms(player.level()) || player.isCreative() || player.isSpectator()) {
            return;
        }
        Input input = event.getInput();
        // No jumping in the Backrooms: you are a tired person with a camcorder, not a parkour pro.
        // Except in water: the jump key is how you swim up, and how you climb out of a pool onto
        // the raised tile walkways of the Poolrooms.
        if (!player.isInWater()) {
            input.jumping = false;
        }

        if (EnergyClientState.exhausted) {
            // Out of breath: let go of sprint. Capping forward input just under the sprint threshold
            // stops vanilla from re-starting a sprint (double-tap W, held or toggled sprint key).
            input.forwardImpulse = Math.min(input.forwardImpulse, BELOW_SPRINT_IMPULSE);
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }
    }

    private MovementClientEvents() {}
}
