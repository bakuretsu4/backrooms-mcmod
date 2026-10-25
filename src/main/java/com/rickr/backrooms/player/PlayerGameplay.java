package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.EnergyPayload;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Travel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * How your body works inside the Backrooms: limited sprint energy, a slower walk, our own
 * footstep sounds and a nasty surprise when you die. This class is "common" code: it runs on
 * both the server and the client (for the local player's prediction), and touches no client classes.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class PlayerGameplay {
    /** Walking is 20% slower than vanilla in the Backrooms; it feels heavier, like the videos. */
    private static final ResourceLocation WALK_SLOWDOWN_ID = Backrooms.id("backrooms_walk_slowdown");
    private static final AttributeModifier WALK_SLOWDOWN =
            new AttributeModifier(WALK_SLOWDOWN_ID, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    // Footstep spacing in blocks walked: running takes longer strides.
    private static final double WALK_STRIDE = 1.7;
    private static final double RUN_STRIDE = 2.3;
    private static final float WALK_VOLUME = 0.15f;
    private static final float RUN_VOLUME = 1.0f;

    /**
     * Per-player footstep tracking: {lastX, lastZ, blocks walked since last step}. Separate maps for
     * the client and server threads because in singleplayer both run in the same JVM.
     */
    private static final Map<UUID, double[]> STEPS_SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> STEPS_CLIENT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        boolean inBackrooms = Travel.inBackrooms(player.level());
        if (player instanceof ServerPlayer serverPlayer) {
            tickEnergy(serverPlayer, inBackrooms);
        }
        updateWalkSpeed(player, inBackrooms);
        if (inBackrooms) {
            tickFootsteps(player);
        }
    }

    /** -1 per tick while sprinting in the Backrooms, +1 otherwise; empty bar = exhausted until > 200. */
    private static void tickEnergy(ServerPlayer player, boolean inBackrooms) {
        EnergyData data = player.getData(ModAttachments.ENERGY);
        boolean running = inBackrooms && player.isSprinting() && !player.isCreative() && !player.isSpectator();
        if (running) {
            data.energy = Math.max(0, data.energy - 1);
            if (data.energy == 0) {
                data.exhausted = true;
            }
        } else {
            data.energy = Math.min(EnergyData.MAX, data.energy + 1);
            if (data.exhausted && data.energy > EnergyData.RECOVER_AT) {
                data.exhausted = false;
            }
        }
        // Server is the boss: out of breath means no running. The client also lets go of sprint
        // on its own (client/gameplay) so the two sides agree and there is no rubber-banding.
        if (data.exhausted && inBackrooms && player.isSprinting()) {
            player.setSprinting(false);
        }
        sync(player, data, false);
    }

    /** Sends the energy bar to its owner when it changed (every 4 ticks is plenty for a HUD icon). */
    private static void sync(ServerPlayer player, EnergyData data, boolean force) {
        boolean changed = data.energy != data.lastSentEnergy || data.exhausted != data.lastSentExhausted;
        boolean important = force || data.exhausted != data.lastSentExhausted
                || data.energy == 0 || data.energy == EnergyData.MAX;
        if (changed && (important || player.tickCount % 4 == 0)) {
            data.lastSentEnergy = data.energy;
            data.lastSentExhausted = data.exhausted;
            PacketDistributor.sendToPlayer(player, new EnergyPayload(data.energy, data.exhausted));
        }
    }

    /** Forces a resend, e.g. after login/respawn when the client state was reset. */
    public static void forceSync(ServerPlayer player) {
        EnergyData data = player.getData(ModAttachments.ENERGY);
        data.lastSentEnergy = -1;
        sync(player, data, true);
    }

    /** Slow walk only while walking normally in the Backrooms; sprinting/sneaking use vanilla speeds. */
    private static void updateWalkSpeed(Player player, boolean inBackrooms) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean want = inBackrooms && !player.isSprinting() && !player.isCrouching();
        boolean has = speed.hasModifier(WALK_SLOWDOWN_ID);
        if (want && !has) {
            speed.addTransientModifier(WALK_SLOWDOWN);
        } else if (!want && has) {
            speed.removeModifier(WALK_SLOWDOWN_ID);
        }
    }

    /**
     * Our own footsteps: quiet shuffles when walking, loud slaps when running, carpet vs concrete.
     * {@code level.playSound(player, ...)} is the vanilla trick: on the server it plays for everyone
     * except {@code player}; on the client it plays only if {@code player} is you. So each person
     * hears their own steps instantly and everyone else's from the server.
     */
    private static void tickFootsteps(Player player) {
        Map<UUID, double[]> map = player.level().isClientSide ? STEPS_CLIENT : STEPS_SERVER;
        double[] track = map.computeIfAbsent(player.getUUID(), id -> new double[] {player.getX(), player.getZ(), 0});
        double dx = player.getX() - track[0];
        double dz = player.getZ() - track[1];
        track[0] = player.getX();
        track[1] = player.getZ();
        if (!player.onGround() || player.isSpectator() || player.isPassenger()) {
            return;
        }
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 2.0) {
            return; // teleported, not a step
        }
        track[2] += moved;
        boolean running = player.isSprinting();
        double stride = running ? RUN_STRIDE : WALK_STRIDE;
        if (track[2] < stride) {
            return;
        }
        track[2] = 0;
        SoundEvent sound = stepSound(player, running);
        float volume = running ? RUN_VOLUME : WALK_VOLUME;
        if (player.isCrouching()) {
            volume *= 0.5f;
        }
        float pitch = 0.92f + player.getRandom().nextFloat() * 0.16f;
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * Which footstep sound fits the ground: dead leaves you walk through, the lawn or the wooden
     * floors of the Suburbs, the Lobby's carpet, or (everywhere else) concrete.
     */
    private static SoundEvent stepSound(Player player, boolean running) {
        if (player.level().getBlockState(player.blockPosition()).is(ModBlocks.LEAF_LITTER.get())) {
            return ModSounds.STEP_LEAVES.get(); // the litter layer sits in the same block as your feet
        }
        BlockState on = player.getBlockStateOn();
        if (isCarpet(on)) {
            return running ? ModSounds.STEP_CARPET_RUN.get() : ModSounds.STEP_CARPET_WALK.get();
        }
        if (on.is(ModBlocks.LAWN_GRASS.get())) {
            return running ? ModSounds.STEP_GRASS_RUN.get() : ModSounds.STEP_GRASS_WALK.get();
        }
        if (on.is(ModBlocks.WOOD_FLOOR.get()) || on.is(ModBlocks.PORCH_PLANKS.get()) || on.is(Blocks.DARK_OAK_STAIRS)) {
            return running ? ModSounds.STEP_WOOD_RUN.get() : ModSounds.STEP_WOOD_WALK.get();
        }
        return running ? ModSounds.STEP_CONCRETE_RUN.get() : ModSounds.STEP_CONCRETE_WALK.get();
    }

    private static boolean isCarpet(BlockState state) {
        if (state.getSoundType() == SoundType.WOOL) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("carpet") || path.contains("rug");
    }

    /**
     * Silences vanilla block step sounds made by players in the Backrooms (all named "block.*.step"),
     * since we play our own. Fires on both sides, so it works for the local player and the server.
     */
    @SubscribeEvent
    public static void onPlaySound(PlayLevelSoundEvent.AtPosition event) {
        if (event.getSource() != SoundSource.PLAYERS || !Travel.inBackrooms(event.getLevel())) {
            return;
        }
        String path = event.getSound().value().getLocation().getPath();
        if (path.startsWith("block.") && path.endsWith(".step")) {
            event.setCanceled(true);
        }
    }

    /** Dying in the Backrooms: a sting and a short title only the victim sees. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Travel.inBackrooms(player.level())) {
            player.playNotifySound(ModSounds.DEATH_STING.get(), SoundSource.MASTER, 1.0f, 1.0f);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("title.backrooms.death").withStyle(ChatFormatting.DARK_RED)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STEPS_SERVER.remove(event.getEntity().getUUID());
    }

    private PlayerGameplay() {}
}
