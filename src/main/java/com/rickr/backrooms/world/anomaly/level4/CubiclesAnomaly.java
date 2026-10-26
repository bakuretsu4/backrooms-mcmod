package com.rickr.backrooms.world.anomaly.level4;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.CubicleFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Level 4.3, "The Cubicles" (https://backrooms.fandom.com/wiki/Level_4.3): reached by walking down a
 * dim white series of hallways, an endless field of cubicles in constant white fog, with the
 * sounds of shuffling paper, pencils writing, unclear voices and the muffled rain and thunder of
 * Level 4. Its fridges hold Almond Water. The place makes people want to stay and work.
 *
 * <ul>
 *   <li><b>Endless:</b> the field repeats every {@link Level4AnomalyPlan#FIELD_PERIOD_U} x
 *       {@link Level4AnomalyPlan#FIELD_PERIOD_V} blocks; walk too far to the sides or deeper in and
 *       you are moved back one whole pattern ({@link Teleports#shift}), out of sight in the fog. The
 *       one direction it never does this is back toward the hallways: the way out always works.</li>
 *   <li><b>It wants you to stay:</b> after {@link #WORK_START} seconds in the field you slow down,
 *       a little more every {@link #WORK_STEP} seconds (Slowness I to IV; you can always still walk),
 *       and walking back toward the hallways is heavier still. Leaving, the slowness lingers a few
 *       seconds and then fades.</li>
 * </ul>
 * The fog, the sounds and the picture are client effects. No entities live here.
 */
final class CubiclesAnomaly implements AnomalyBehavior {
    private static final int WORK_START = 20;
    private static final int WORK_STEP = 40;
    private static final int MAX_SLOWNESS = 3;
    /** Lingering slowness after you leave (ticks). */
    private static final int AFTERGLOW = 200;
    /** Extra drag while walking back toward the way out (a fraction of your speed). */
    private static final double EXIT_DRAG = -0.25;
    private static final ResourceLocation EXIT_DRAG_ID = Backrooms.id("level4_cubicles_exit_drag");

    // Loop edges of the field (see Level4AnomalyPlan): the band [LOW_U, HIGH_U) x [.., DEEP_V) is home.
    private static final int LOW_U = 18;
    private static final int HIGH_U = 30;
    private static final int DEEP_V = 32;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> SLOWNESS = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        CubicleFrame f = Level4AnomalyPlan.cubicleFrame(site);
        int u = f.u(player.getBlockX(), player.getBlockZ());
        int v = f.v(player.getBlockX(), player.getBlockZ());
        boolean field = v >= Level4AnomalyPlan.FIELD_V0;
        UUID id = player.getUUID();
        if (field && loop(player, f, u, v)) {
            LAST_POS.remove(id); // the jump isn't walking
            return;
        }

        // Heavier on the way out: compare this tick's movement with the direction of the hallways (-v).
        double[] last = LAST_POS.get(id);
        LAST_POS.put(id, new double[] {player.getX(), player.getZ()});
        if (field && last != null) {
            double toward = -((player.getX() - last[0]) * Level4AnomalyPlan.stepX(f.plusV())
                    + (player.getZ() - last[1]) * Level4AnomalyPlan.stepZ(f.plusV()));
            boolean dragged = hasDrag(player);
            if (!dragged && toward > 0.03) {
                setDrag(player, true);
            } else if (dragged && toward < 0.01) {
                setDrag(player, false);
            }
        } else {
            setDrag(player, false);
        }

        // The longer you stay, the less you want to go.
        if (field && ticksInside % 20 == 0) {
            int seconds = ticksInside / 20;
            if (seconds >= WORK_START) {
                int amplifier = Math.min(MAX_SLOWNESS, (seconds - WORK_START) / WORK_STEP);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, amplifier, true, false, true));
                Integer before = SLOWNESS.put(id, amplifier);
                if (before == null || before < amplifier) {
                    // A thought that isn't quite yours, each time the pull gets stronger.
                    player.displayClientMessage(Component.translatable("message.backrooms.cubicles.work" + amplifier)
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                }
            }
        }
    }

    /** Moves the player back one pattern when they near the far or side edges. True if moved. */
    private static boolean loop(ServerPlayer player, CubicleFrame f, int u, int v) {
        int du = u >= HIGH_U ? -Level4AnomalyPlan.FIELD_PERIOD_U : u < LOW_U ? Level4AnomalyPlan.FIELD_PERIOD_U : 0;
        int dv = v >= DEEP_V ? -Level4AnomalyPlan.FIELD_PERIOD_V : 0;
        if (du == 0 && dv == 0) {
            return false;
        }
        int pu = f.plusU();
        int pv = f.plusV();
        Teleports.shift(player,
                du * Level4AnomalyPlan.stepX(pu) + dv * Level4AnomalyPlan.stepX(pv), 0,
                du * Level4AnomalyPlan.stepZ(pu) + dv * Level4AnomalyPlan.stepZ(pv));
        return true;
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        UUID id = player.getUUID();
        setDrag(player, false);
        LAST_POS.remove(id);
        Integer amplifier = SLOWNESS.remove(id);
        if (amplifier != null) {
            // It lets go of you slowly.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AFTERGLOW, amplifier, true, false, true));
        }
    }

    private static boolean hasDrag(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(EXIT_DRAG_ID);
    }

    private static void setDrag(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(EXIT_DRAG_ID) == on) {
            return;
        }
        if (on) {
            speed.addTransientModifier(new AttributeModifier(EXIT_DRAG_ID, EXIT_DRAG, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(EXIT_DRAG_ID);
        }
    }

    static void forget(UUID id) {
        LAST_POS.remove(id);
        SLOWNESS.remove(id);
    }
}
