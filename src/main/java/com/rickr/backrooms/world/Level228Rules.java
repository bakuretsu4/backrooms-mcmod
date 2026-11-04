package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level228StatePayload;
import com.rickr.backrooms.registry.Level228Blocks;
import com.rickr.backrooms.block.Level228MirrorBlock;
import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server rules of Level 228 ("Maze of Mirrors"), in and out:
 * <ul>
 *   <li><b>In:</b> through a dark-doored house in Level 94; you step out of a mirror
 *       ({@code Level228Layout#preferredArrival}). The client is told the layout seed
 *       ({@link Level228StatePayload}) on arrival, login and respawn, so it can put your
 *       reflections in the image volumes behind the glass.</li>
 *   <li><b>Out:</b> walk into the black mirror that shows no reflection ({@code l228_exit_mirror},
 *       it has no collision) and you come out in Level 369 ({@code BackroomsLevel.next}). It
 *       counts as soon as your body reaches its cell ({@link #touchesExit}), not only your middle:
 *       the exit is four blocks wide between frame posts, and someone walking at it off-centre is
 *       stopped by a post with half their body still outside.</li>
 * </ul>
 * The floor is flat and sealed: Level 228 is never left by falling.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level228Rules {
    /**
     * How far (blocks) in front of the exit mirror's cell your body may still be, walking at it,
     * and count as through. A post beside the exit can hold you back up to 0.3 (half a body).
     */
    private static final double REACH = 0.3;
    /** ... as long as you face it this squarely (cosine of the angle off its normal, ~60 degrees). */
    private static final double FACING = 0.5;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            return;
        }
        if (touchesExit(player)) {
            Travel.useExit(player, BackroomsLevel.LEVEL_228);
        }
    }

    /**
     * True when the player has walked into an exit mirror: their middle is in its cell, or their
     * body is in it or within {@link #REACH} in front of it while they face it. Walking past it
     * along the corridor (body near the cell, facing along the wall) does not count.
     */
    public static boolean touchesExit(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        if (player.level().getBlockState(feet).is(Level228Blocks.EXIT_MIRROR.get())
                || player.level().getBlockState(feet.above()).is(Level228Blocks.EXIT_MIRROR.get())) {
            return true;
        }
        AABB box = player.getBoundingBox();
        Vec3 look = player.getLookAngle();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int y0 = Math.max(Level228Plan.FEET_Y, (int) Math.floor(box.minY));
        int y1 = Math.min(Level228Plan.PANE_TOP_Y, (int) Math.floor(box.maxY));
        for (int x = (int) Math.floor(box.minX - REACH); x <= (int) Math.floor(box.maxX + REACH); x++) {
            for (int z = (int) Math.floor(box.minZ - REACH); z <= (int) Math.floor(box.maxZ + REACH); z++) {
                for (int y = y0; y <= y1; y++) {
                    BlockState state = player.level().getBlockState(at.set(x, y, z));
                    if (state.is(Level228Blocks.EXIT_MIRROR.get()) && reaches(box, look, state.getValue(Level228MirrorBlock.AXIS), x, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The body, stretched {@link #REACH} along the pane's normal only, overlaps cell (x, z), and the
     * player faces the cell along that normal. Sideways the body must really overlap the cell: a
     * neighbour a body-width off to the side is not walking into it.
     */
    private static boolean reaches(AABB box, Vec3 look, Direction.Axis normal, int x, int z) {
        boolean alongX = normal == Direction.Axis.X;
        double ex = alongX ? REACH : 0.0;
        double ez = alongX ? 0.0 : REACH;
        if (box.maxX + ex <= x || box.minX - ex >= x + 1 || box.maxZ + ez <= z || box.minZ - ez >= z + 1) {
            return false;
        }
        double centre = alongX ? (box.minX + box.maxX) * 0.5 : (box.minZ + box.maxZ) * 0.5;
        double toward = Math.signum((alongX ? x : z) + 0.5 - centre);
        return (alongX ? look.x : look.z) * toward >= FACING;
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTo() == BackroomsLevel.LEVEL_228.dimension()) {
            sendState(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInside(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInside(player);
        }
    }

    private static void sendIfInside(ServerPlayer player) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_228) {
            sendState(player);
        }
    }

    private static void sendState(ServerPlayer player) {
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_228);
        PacketDistributor.sendToPlayer(player, new Level228StatePayload(seed));
    }

    private Level228Rules() {}
}
