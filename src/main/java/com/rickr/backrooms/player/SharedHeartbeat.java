package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Travel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Shared panic: when your sanity is so low that you hear your own heartbeat (below
 * {@link SanityData#HEARTBEAT_BELOW}), friends within {@link #RANGE} blocks hear it too, faintly,
 * coming from you, at your heart rate.
 *
 * <p>The server decides, because only the server knows everyone's sanity (each client only knows
 * its own). It keeps a beat count per panicking player ({@link SanityData#heartPhase}) and on each
 * beat sends the existing heartbeat sound straight to the players in range: one sound packet each.
 *
 * <ul>
 *   <li>The panicking player is skipped: their own client already plays their heartbeat, louder
 *       and right in their head (client/SanityClientEffects).</li>
 *   <li>Spectators neither make nor hear it. Hidden players DO make it: a friend's heart pounding
 *       inside a locker is the point.</li>
 *   <li>It's a normal sound placed in the world, so walls muffle it like anything else
 *       (client/audio/Acoustics).</li>
 *   <li>Monsters can't hear it: it is sent as packets to players only, never played into the
 *       world on the server, so no monster (and no vibration sensor) ever learns about it.</li>
 *   <li>Nothing else happens to the listener: sound only, no sanity drain.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class SharedHeartbeat {
    /** Friends this close (blocks) hear your heart. */
    private static final double RANGE = 12.0;
    /**
     * Volume at sanity 30 and the extra at sanity 0. Your own heartbeat plays at 0.25..0.8 right in
     * your ears; a friend's is placed in the world and fades with distance on top of this, so it
     * always stays clearly quieter than your own (the README's "Mix" rules: quiet on purpose).
     */
    private static final float BASE_VOLUME = 0.12f;
    private static final float FEAR_VOLUME = 0.23f;

    /** Every tick: move the panicking player's heart on, and on each new beat let friends hear it. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SanityData data = player.getData(ModAttachments.SANITY);
        if (!panicking(player, data.sanity)) {
            data.heartPhase = 0.0; // calm again: the next panic starts a fresh rhythm
            return;
        }
        // Same rate the player sees on their HUD and hears in their own head (20 ticks a second).
        double before = data.heartPhase;
        data.heartPhase = before + SanityData.bpm(data.sanity) / 60.0 / 20.0;
        if (Math.floor(data.heartPhase) != Math.floor(before)) {
            beat(player, data.sanity);
        }
    }

    /** Hearing your own heartbeat right now: in the Backrooms, alive, not a spectator, sanity low. */
    private static boolean panicking(ServerPlayer player, float sanity) {
        return sanity < SanityData.HEARTBEAT_BELOW && player.isAlive() && !player.isSpectator()
                && Travel.inBackrooms(player.level());
    }

    /** One "lub-dub", sent to every other non-spectator player within range. */
    private static void beat(ServerPlayer player, float sanity) {
        float fear = 1.0f - sanity / SanityData.HEARTBEAT_BELOW; // 0 at 30 .. 1 at 0, as for your own
        float volume = BASE_VOLUME + FEAR_VOLUME * fear;
        // From the chest, not the eyes: it's their heart.
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.6;
        double z = player.getZ();
        ClientboundSoundPacket packet = null; // built only if someone is near enough to hear it
        for (ServerPlayer listener : player.serverLevel().players()) {
            if (listener == player || listener.isSpectator() || listener.distanceToSqr(x, y, z) > RANGE * RANGE) {
                continue;
            }
            if (packet == null) {
                packet = new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.SANITY_HEARTBEAT.get()),
                        SoundSource.PLAYERS, x, y, z, volume, 1.0f, player.getRandom().nextLong());
            }
            listener.connection.send(packet);
        }
    }

    private SharedHeartbeat() {}
}
