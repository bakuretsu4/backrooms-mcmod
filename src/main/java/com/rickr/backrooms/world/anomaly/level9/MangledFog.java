package com.rickr.backrooms.world.anomaly.level9;

import com.rickr.backrooms.network.Level9PillarPayload;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.Level9Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Mangled's fog (Level 9's FOG_ROLL event, run from {@code LevelEvents}), after the canon of
 * Entity 63 (https://backrooms-wiki.wikidot.com/entity-63): "a sudden drop in temperature" comes
 * first, then a thick black smokescreen rolls over the suburb; a "massive black pillar" may appear
 * in it, "followed by thunder-like booms" (Level 9 "isn't known to have thunderstorms"); the
 * noises turn out to be human speech; when it is over the pillar is gone and "leaves a crater in
 * the street", and the ground quakes.
 *
 * <p>The server side of it, for every player in the suburb (not out in the Crimson Field):
 * <ol>
 *   <li>at the start: the cold (a crackle of frost, a few seconds of Slowness; the clients draw
 *       the frost and your breath);</li>
 *   <li>10 s in, when the fog is thick: the pillar rises on an open street 36-56 blocks from
 *       someone. It is drawn by the clients only (told by {@link Level9PillarPayload}); walk up to
 *       it and something shoves you back into the dark. Booms come from its direction every 7-12 s,
 *       each followed by a garbled voice;</li>
 *   <li>8 s before the event ends, as the fog lifts: the pillar is gone, and where it stood the
 *       road is blasted into a shallow crater (real blocks, only on road columns, never if anyone
 *       is standing there; every step is half a block so you can always walk out). A quake rolls
 *       out from it.</li>
 * </ol>
 * The lamps' hum cutting out, the darker fog and the pillar itself are client side
 * ({@code Level9Atmosphere}, {@code client/anomaly/level9}).
 */
public final class MangledFog {
    /** Slowness at the start: the cold gets into your legs. */
    private static final int CHILL_TICKS = 70;
    /** The pillar rises this long after the event starts (the fog is thick by then) ... */
    private static final int PILLAR_AT = 200;
    /** ... and is gone this long before the end (when the fog starts lifting). */
    private static final int CLEAR_BEFORE_END = 160;
    /** How far from the chosen player it may stand (blocks). */
    private static final double PILLAR_MIN = 36.0;
    private static final double PILLAR_MAX = 56.0;
    /** Nobody may be this close when it rises. */
    private static final double PILLAR_CLEAR = 10.0;
    /** Its blast radius: everything this close to the middle must be plain road. */
    private static final double CORE_RADIUS = 2.2;
    private static final double CRATER_RADIUS = 3.2;
    /** Walk closer than this and you are shoved back. */
    private static final double PUSH_RADIUS = 3.4;
    /** Booms and voices carry this far (blocks). */
    private static final double HEARING = 160.0;

    private static boolean tried;
    private static boolean standing;
    private static int pillarX;
    private static int pillarZ;
    private static long clearTick;
    private static long nextBoom;
    private static long nextVoice = -1;

    /** The fog roll just started. */
    public static void begin(ServerLevel level, long now, long endTick) {
        if (standing) {
            clear(level, true);
        }
        tried = false;
        clearTick = endTick - CLEAR_BEFORE_END;
        for (ServerPlayer player : suburbPlayers(level)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHILL_TICKS, 0, false, false, true));
            player.connection.send(new ClientboundSoundPacket(ModSounds.L9_FROST_ONSET, SoundSource.AMBIENT,
                    player.getX(), player.getEyeY(), player.getZ(), 0.9f, 1.0f, level.getRandom().nextLong()));
        }
    }

    /** Every tick while the fog roll runs. */
    public static void tick(ServerLevel level, long startTick, long now) {
        if (!standing && !tried && now - startTick >= PILLAR_AT && now < clearTick - 100) {
            tried = true;
            raise(level, now);
        }
        if (!standing) {
            return;
        }
        if (now >= clearTick) {
            clear(level, true);
            return;
        }
        RandomSource random = level.getRandom();
        if (now >= nextBoom) {
            for (ServerPlayer player : suburbPlayers(level)) {
                playFromPillar(player, ModSounds.L9_PILLAR_BOOM, 40.0, 4.5f, 0.8f + random.nextFloat() * 0.2f, random);
            }
            nextBoom = now + 140 + random.nextInt(100);
            nextVoice = now + 40 + random.nextInt(40);
        }
        if (nextVoice > 0 && now >= nextVoice) {
            // The noises "turn out to be human speech".
            for (ServerPlayer player : suburbPlayers(level)) {
                playFromPillar(player, ModSounds.L9_GARBLED_SPEECH, 28.0, 2.2f, 0.85f + random.nextFloat() * 0.25f, random);
            }
            nextVoice = -1;
        }
        // It is solid enough: whoever walks into it is thrown back into the dark.
        for (ServerPlayer player : suburbPlayers(level)) {
            double dx = player.getX() - (pillarX + 0.5);
            double dz = player.getZ() - (pillarZ + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < PUSH_RADIUS) {
                double nx = d < 1.0e-3 ? 1.0 : dx / d;
                double nz = d < 1.0e-3 ? 0.0 : dz / d;
                player.setDeltaMovement(nx * 0.7, 0.15, nz * 0.7);
                player.hurtMarked = true; // makes the server send the new motion to the client
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
            }
        }
    }

    /** The event ended (or was ended, or replaced): the pillar goes, leaving its crater. */
    public static void finish(ServerLevel level) {
        if (standing) {
            clear(level, true);
        }
        tried = false;
    }

    /** A player arrived on a floor: tell them whether a pillar is standing there. */
    public static void sendCurrent(ServerPlayer player) {
        boolean here = standing && BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_9;
        PacketDistributor.sendToPlayer(player, new Level9PillarPayload(here ? Level9PillarPayload.STANDING : Level9PillarPayload.NONE,
                pillarX, pillarZ));
    }

    /** Server stopping: forget everything. */
    public static void reset() {
        tried = false;
        standing = false;
        nextVoice = -1;
    }

    // ------------------------------------------------------------------------------------------

    private static void raise(ServerLevel level, long now) {
        List<ServerPlayer> players = suburbPlayers(level);
        if (players.isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        ServerPlayer who = players.get(random.nextInt(players.size()));
        Level9Layout layout = (Level9Layout) BackroomsLevel.LEVEL_9.layout();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_9);
        for (int attempt = 0; attempt < 48; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = PILLAR_MIN + random.nextDouble() * (PILLAR_MAX - PILLAR_MIN);
            int x = Mth.floor(who.getX() + Mth.cos(angle) * distance);
            int z = Mth.floor(who.getZ() + Mth.sin(angle) * distance);
            if (suitable(level, layout, seed, x, z, players)) {
                standing = true;
                pillarX = x;
                pillarZ = z;
                nextBoom = now + 30;
                nextVoice = -1;
                broadcast(level, Level9PillarPayload.STANDING);
                return;
            }
        }
    }

    /** Open street all round the middle, loaded, out of the field and nobody close. */
    private static boolean suitable(ServerLevel level, Level9Layout layout, long seed, int x, int z, List<ServerPlayer> players) {
        if (Level9Sites.inField(x + 8) || !level.isLoaded(new BlockPos(x, LevelLayout.FLOOR_Y, z))) {
            return false;
        }
        int r = (int) Math.ceil(CORE_RADIUS);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= CORE_RADIUS * CORE_RADIUS && !layout.isPlainRoad(seed, x + dx, z + dz)) {
                    return false;
                }
            }
        }
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(x + 0.5, player.getY(), z + 0.5) < PILLAR_CLEAR * PILLAR_CLEAR) {
                return false;
            }
        }
        return true;
    }

    private static void clear(ServerLevel level, boolean crater) {
        standing = false;
        nextVoice = -1;
        boolean dug = crater && carve(level);
        broadcast(level, dug ? Level9PillarPayload.CRATER : Level9PillarPayload.NONE);
        if (!dug) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : suburbPlayers(level)) {
            if (player.distanceToSqr(pillarX + 0.5, player.getY(), pillarZ + 0.5) < 110.0 * 110.0) {
                playFromPillar(player, ModSounds.L9_QUAKE, 24.0, 5.0f, 0.9f + random.nextFloat() * 0.15f, random);
            }
        }
    }

    /**
     * Blasts the crater where the pillar stood: the middle a full block down (y39 is its floor),
     * a ring of shattered asphalt half a block down, an outer ring of shattered asphalt at road
     * height. Only plain road columns, only loaded ones, and not at all while anyone stands within
     * reach of it. Doing it twice changes nothing. Returns true if it was carved.
     */
    private static boolean carve(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pillarX + 0.5, player.getY(), pillarZ + 0.5) < (CRATER_RADIUS + 1.3) * (CRATER_RADIUS + 1.3)) {
                return false; // never pull the ground from under someone's feet
            }
        }
        Level9Layout layout = (Level9Layout) BackroomsLevel.LEVEL_9.layout();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_9);
        BlockState soil = ModBlocks.L9_CRATER_SOIL.get().defaultBlockState();
        BlockState shattered = ModBlocks.L9_CRATER_ASPHALT.get().defaultBlockState();
        BlockState half = ModBlocks.L9_CRATER_ASPHALT_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        int floor = LevelLayout.FLOOR_Y;
        int r = (int) Math.ceil(CRATER_RADIUS);
        boolean any = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = pillarX + dx;
                int z = pillarZ + dz;
                BlockPos surface = new BlockPos(x, floor, z);
                if (d > CRATER_RADIUS || !layout.isPlainRoad(seed, x, z) || !level.isLoaded(surface)) {
                    continue;
                }
                BlockPos above = surface.above();
                BlockState onTop = level.getBlockState(above);
                if (onTop.is(ModBlocks.LEAF_LITTER.get()) || onTop.is(ModBlocks.DUST_PILE.get())) {
                    level.setBlock(above, air, Block.UPDATE_CLIENTS);
                }
                if (d <= 1.2) {
                    level.setBlock(surface.below(), soil, Block.UPDATE_CLIENTS);
                    level.setBlock(surface, air, Block.UPDATE_CLIENTS);
                } else if (d <= 2.2) {
                    level.setBlock(surface, half, Block.UPDATE_CLIENTS);
                } else {
                    level.setBlock(surface, shattered, Block.UPDATE_CLIENTS);
                }
                any = true;
            }
        }
        return any;
    }

    /**
     * Plays a sound to one player from the pillar's direction: at the pillar if it is close, else
     * {@code maxDistance} blocks toward it (loud enough to carry; see LevelEvents.playDistant).
     */
    private static void playFromPillar(ServerPlayer player, Holder<SoundEvent> sound, double maxDistance, float volume, float pitch,
                                       RandomSource random) {
        Vec3 eye = player.getEyePosition();
        double dx = pillarX + 0.5 - eye.x;
        double dz = pillarZ + 0.5 - eye.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > HEARING) {
            return;
        }
        double k = d <= maxDistance || d < 1.0e-3 ? 1.0 : maxDistance / d;
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.AMBIENT,
                eye.x + dx * k, eye.y + 2.0, eye.z + dz * k, volume, pitch, random.nextLong()));
    }

    private static List<ServerPlayer> suburbPlayers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && !Level9Sites.inField(player.getX())) {
                out.add(player);
            }
        }
        return out;
    }

    private static void broadcast(ServerLevel level, int phase) {
        Level9PillarPayload payload = new Level9PillarPayload(phase, pillarX, pillarZ);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private MangledFog() {}
}
