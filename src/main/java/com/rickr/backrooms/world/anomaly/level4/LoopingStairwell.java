package com.rickr.backrooms.world.anomaly.level4;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.CubicleFrame;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.StairFrame;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The looping stairwell: a grey stair core in an open-plan floor, its stairs going up and down,
 * the sign on every landing saying 4. However far you climb or descend, you only ever reach this
 * same floor again. Canon: "stairwells suggest many floors, but going up or down simply leads
 * back to the same floor" (https://backrooms.fandom.com/wiki/Level_4).
 *
 * <p>How: the shaft is built as identical storeys {@link Level4AnomalyPlan#STAIR_PERIOD} blocks
 * tall (see {@link Level4AnomalyPlan}). When you step onto the landing one storey up (or down), you
 * are moved back down (or up) by exactly one storey with {@link Teleports#shift}: same view, same
 * momentum, so it looks like you simply arrived at the next landing.
 *
 * <p>Rarely (1 trip in {@link #DETOUR_ODDS}) the stairs do lead somewhere else: the screen goes dark
 * and you come to in the nearest Level 4.3, "The Cubicles" (canon: in rare cases the stairs lead
 * into Level 4.3, which is not a reliable exit).
 *
 * <p>The stairwell door is a self-closing fire door, like real stairwell doors: it shuts once nobody
 * is next to it (so climbing past the landing above always shows a closed door there, like below).
 */
final class LoopingStairwell implements AnomalyBehavior {
    private static final int FLOOR = LevelLayout.FLOOR_Y;
    /** Feet on the landing one storey up (y46 + 1, a little margin for rounding). */
    private static final double UP_AT = FLOOR + Level4AnomalyPlan.STAIR_PERIOD + 0.9;
    /** Feet on the landing one storey down (y34 + 1). */
    private static final double DOWN_AT = FLOOR - Level4AnomalyPlan.STAIR_PERIOD + 1.1;
    /** One storey climbed or descended in this many lets you out in the Cubicles instead. */
    private static final int DETOUR_ODDS = 8;
    /** How long the screen stays dark before you come to elsewhere. */
    private static final int DETOUR_DELAY = 30;
    /** Nobody within this distance of the open door: it swings shut. */
    private static final double DOOR_CLEAR = 2.5;

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        StairFrame frame = Level4AnomalyPlan.stairFrame(site);
        int cell = Level4AnomalyPlan.stairCell(frame.lx(player.getBlockX(), player.getBlockZ()),
                frame.lz(player.getBlockX(), player.getBlockZ()));
        if (cell != Level4AnomalyPlan.S_LANDING && cell != Level4AnomalyPlan.S_HALF
                && cell != Level4AnomalyPlan.S_UP && cell != Level4AnomalyPlan.S_DOWN) {
            return;
        }
        double feet = player.getY();
        int dy;
        if (feet >= UP_AT && feet < Level4AnomalyPlan.STAIR_TOP) {
            dy = -Level4AnomalyPlan.STAIR_PERIOD;
        } else if (feet <= DOWN_AT && feet > Level4AnomalyPlan.STAIR_BOTTOM) {
            dy = Level4AnomalyPlan.STAIR_PERIOD;
        } else {
            return;
        }
        // Back to this floor, as if you had just arrived at the next one.
        Teleports.shift(player, 0, dy, 0);
        // (Not while a detour is already under way: the screen is dark for that one.)
        if (!player.hasEffect(MobEffects.BLINDNESS) && player.getRandom().nextInt(DETOUR_ODDS) == 0) {
            detour(player, seed);
        }
    }

    /** This time the stairs let you out somewhere else: into the nearest Level 4.3. */
    private static void detour(ServerPlayer player, long seed) {
        LevelLayout layout = BackroomsLevel.LEVEL_4.layout();
        AnomalySite target = layout.nearestAnomaly(seed, player.getBlockX(), player.getBlockZ(), Level4AnomalyPlan.CUBICLES);
        if (target == null) {
            return; // no Cubicles anywhere near: the loop carries on as usual
        }
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DETOUR_DELAY + 30, 0, false, false, false));
        // Footsteps going on without you, far down the shaft, as the light goes.
        player.playNotifySound(ModSounds.L4_STAIRWELL_ECHO.get(), SoundSource.AMBIENT, 0.9f, 0.7f);
        Level4Anomalies.later(player, DETOUR_DELAY, p -> {
            CubicleFrame f = Level4AnomalyPlan.cubicleFrame(target);
            int u = Level4AnomalyPlan.cubicleArrivalU();
            int v = Level4AnomalyPlan.cubicleArrivalV();
            float yaw = Direction.from2DDataValue(f.plusV()).toYRot(); // facing deeper in
            Teleports.to(p, f.worldX(u, v) + 0.5, FLOOR + 1, f.worldZ(u, v) + 0.5, yaw, 0.0f);
        });
    }

    /** Once a second: the door closes behind you once nobody is in the doorway. */
    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        BlockPos pos = new BlockPos(site.anchorX(), FLOOR + 1, site.anchorZ());
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock door) || !state.getValue(DoorBlock.OPEN)) {
            return;
        }
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (Player p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(cx, pos.getY(), cz) < DOOR_CLEAR * DOOR_CLEAR) {
                return;
            }
        }
        door.setOpen(null, level, state, pos, false);
    }
}
