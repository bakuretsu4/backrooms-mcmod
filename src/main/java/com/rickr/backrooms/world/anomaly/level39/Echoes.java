package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level39Quakes;
import com.rickr.backrooms.world.layout.Level39Layout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Canon: Level 39 is heavily echoed, and the echoes can build into magnitude-5 quakes. The town is
 * one gigantic hall walled in by the superstructure, so every loud thing you do comes back off it.
 *
 * <ul>
 *   <li><b>Slams</b> (shutting a door, a trapdoor or a gate), a <b>bell</b>, a <b>hard landing</b>:
 *       each listener nearby hears the sound come back three or four times from the direction of
 *       the nearest wall of the superstructure, later and fainter each time.</li>
 *   <li>Each of those adds "noise" to the town, which ebbs away slowly. Make enough of it in a short
 *       time ({@value #QUAKE_NOISE}) and the echoes build into a quake ({@link Level39Quakes}, which
 *       also handles its own cool-down).</li>
 *   <li><b>Explosions</b> go straight to {@link Level39Quakes} as a strong boom.</li>
 * </ul>
 * Knocking a house wall down already calls {@link Level39Quakes} itself (Level39Houses).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Echoes {
    /** How many echoes come back, how much quieter each is, and the pitch drop per echo. */
    private static final int ECHOES = 4;
    private static final float ECHO_FADE = 0.55f;
    private static final float ECHO_PITCH = 0.94f;
    /** Listeners this close to the source hear its echoes. */
    private static final double HEAR_RANGE = 96.0;
    /** The echo is placed this far from each listener, toward the wall it bounces off. */
    private static final double ECHO_OFFSET = 9.0;
    /** Noise each kind of sound adds, how fast it ebbs (per second) and how much makes a quake. */
    private static final float SLAM_NOISE = 0.22f;
    private static final float BELL_NOISE = 0.4f;
    private static final float LANDING_NOISE = 0.15f;
    private static final float NOISE_EBB = 0.04f;
    private static final float QUAKE_NOISE = 1.0f;
    /** Strength of the quake that a pile-up of echoes sets off (0..1, see Level39Quakes). */
    private static final float NOISE_QUAKE_STRENGTH = 0.5f;
    /** Strength of an explosion's boom: at least this, more for big blasts. */
    private static final float EXPLOSION_MIN = 0.45f;
    /** A hard landing from this far makes a thud loud enough to echo. */
    private static final float LANDING_MIN_FALL = 5.0f;

    private record Pending(ServerLevel level, Holder<SoundEvent> sound, Vec3 source, float volume, float pitch, long at) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static float noise;
    private static long noiseAt;

    private Echoes() {}

    private static boolean inLevel39(ServerLevel level) {
        return BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_39;
    }

    // ==========================================================================================
    // What makes noise
    // ==========================================================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inLevel39(player.serverLevel())) {
            return;
        }
        BlockState state = player.serverLevel().getBlockState(event.getPos());
        Holder<SoundEvent> sound = null;
        float noiseAdd = SLAM_NOISE;
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            // It is open, so this click shuts it: a slam.
            if (state.getBlock() instanceof DoorBlock) {
                sound = holder(SoundEvents.WOODEN_DOOR_CLOSE);
            } else if (state.getBlock() instanceof TrapDoorBlock) {
                sound = holder(SoundEvents.WOODEN_TRAPDOOR_CLOSE);
            } else if (state.getBlock() instanceof FenceGateBlock) {
                sound = holder(SoundEvents.FENCE_GATE_CLOSE);
            }
        } else if (state.getBlock() instanceof BellBlock) {
            sound = holder(SoundEvents.BELL_BLOCK);
            noiseAdd = BELL_NOISE;
        }
        if (sound != null) {
            loud(player.serverLevel(), event.getPos(), sound, 1.0f, noiseAdd);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDistance() >= LANDING_MIN_FALL
                && inLevel39(player.serverLevel()) && player.getY() > Level39Layout.GROUND_Y) {
            loud(player.serverLevel(), player.blockPosition(), holder(SoundEvents.PLAYER_BIG_FALL), 1.0f, LANDING_NOISE);
        }
    }

    private static Holder<SoundEvent> holder(SoundEvent sound) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && inLevel39(level)) {
            Vec3 c = event.getExplosion().center();
            float strength = Math.min(1.0f, EXPLOSION_MIN + event.getExplosion().radius() * 0.08f);
            Level39Quakes.trigger(level, BlockPos.containing(c), strength);
        }
    }

    /**
     * A loud sound at {@code pos}: its echoes come back to everyone near, and it adds {@code noiseAdd}
     * to the town's noise (enough together makes a quake). Public so other Level 39 code can use it.
     */
    public static void loud(ServerLevel level, BlockPos pos, Holder<SoundEvent> sound, float volume, float noiseAdd) {
        long now = level.getGameTime();
        Vec3 source = Vec3.atCenterOf(pos);
        // Sound travels about 17 blocks a tick; the echo goes to the wall and back.
        int toWall = Math.max(16, -Level39Layout.facadeDistance(pos.getX(), pos.getZ()));
        long delay = Math.max(8, Math.min(60, toWall * 2L / 17L));
        float v = volume;
        float p = 1.0f;
        for (int i = 1; i <= ECHOES; i++) {
            v *= ECHO_FADE;
            p *= ECHO_PITCH;
            PENDING.add(new Pending(level, sound, source, v, p, now + delay * i));
        }
        noise = Math.max(0.0f, noise - NOISE_EBB * (now - noiseAt) / 20.0f) + noiseAdd;
        noiseAt = now;
        if (noise >= QUAKE_NOISE) {
            noise = 0.0f;
            Level39Quakes.trigger(level, pos, NOISE_QUAKE_STRENGTH);
        }
    }

    // ==========================================================================================
    // Playing the echoes
    // ==========================================================================================

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending e = it.next();
            if (e.level() != level) {
                if (e.level().getServer() != level.getServer() || now - e.at() > 200) {
                    it.remove(); // a level that is gone
                }
                continue;
            }
            if (now < e.at()) {
                continue;
            }
            it.remove();
            for (ServerPlayer listener : level.players()) {
                if (listener.position().distanceTo(e.source()) > HEAR_RANGE) {
                    continue;
                }
                Vec3 at = listener.getEyePosition().add(towardWall(listener).scale(ECHO_OFFSET));
                listener.connection.send(new ClientboundSoundPacket(e.sound(), SoundSource.AMBIENT, at.x, at.y, at.z,
                        e.volume(), e.pitch(), level.getRandom().nextLong()));
            }
        }
    }

    /** Unit direction from a listener toward the nearest facade (where the echo comes back from). */
    private static Vec3 towardWall(ServerPlayer p) {
        int lx = Level39Layout.local(p.getBlockX());
        int lz = Level39Layout.local(p.getBlockZ());
        if (Math.abs(lx) >= Math.abs(lz)) {
            return new Vec3(Math.signum(lx == 0 ? 1 : lx), 0.15, 0.0);
        }
        return new Vec3(0.0, 0.15, Math.signum(lz)).normalize();
    }
}
