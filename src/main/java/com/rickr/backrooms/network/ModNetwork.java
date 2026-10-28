package com.rickr.backrooms.network;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.anomaly.AnomalyClientState;
import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.player.EnergyClientState;
import com.rickr.backrooms.player.EnergyData;
import com.rickr.backrooms.player.HallucinationClientState;
import com.rickr.backrooms.player.SanityClientState;
import com.rickr.backrooms.player.SafeRoomClientState;
import com.rickr.backrooms.client.Level94ClientState;
import com.rickr.backrooms.world.Level94Clock;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Packet registration. Multiplayer rule of thumb: the server decides everything (battery,
 * flashlight, events, exits, monsters); clients only display it.
 */
public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToClient(CameraSyncPayload.TYPE, CameraSyncPayload.STREAM_CODEC, (payload, context) -> {
            CameraClientState.battery = payload.battery();
            CameraClientState.nightShot = payload.nightShot();
            CameraClientState.flashlight = payload.flashlight();
            CameraClientState.exitSignal = payload.exitSignal();
            CameraClientState.tapeTicks = payload.tapeTicks();
            CameraClientState.tapeSyncMs = System.currentTimeMillis();
        });

        registrar.playToClient(EscapePayload.TYPE, EscapePayload.STREAM_CODEC, (payload, context) -> {
            CameraClientState.escapeStartMs = System.currentTimeMillis();
        });

        // Every move between floors (freeze -> held -> mosh). Plain fields; client/intro/NoclipTransition reads them.
        registrar.playToClient(IntroCutscenePayload.TYPE, IntroCutscenePayload.STREAM_CODEC, (payload, context) -> {
            CameraClientState.introPhase = payload.phase();
            CameraClientState.introStartMs = System.currentTimeMillis();
        });

        registrar.playToClient(LevelEventPayload.TYPE, LevelEventPayload.STREAM_CODEC, (payload, context) -> {
            CameraClientState.eventKind = payload.kind();
            long nowMs = System.currentTimeMillis();
            CameraClientState.eventStartMs = nowMs - payload.elapsedTicks() * 50L;
            CameraClientState.eventEndMs = nowMs + payload.remainingTicks() * 50L;
        });

        // Level 0 local power outages (world/FixtureOutages): the client makes the lamps die and re-strike.
        registrar.playToClient(LocalOutagePayload.TYPE, LocalOutagePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.restore()) {
                com.rickr.backrooms.client.light.Level0Power.end(payload.id());
            } else {
                com.rickr.backrooms.client.light.Level0Power.start(payload.id(), payload.pattern(), payload.x(), payload.z(),
                        payload.radius(), payload.elapsedMs(), payload.durationMs(), payload.blocks(), payload.delaysMs());
            }
        }));

        // Sprint energy for the HUD stamina line and the client-side sprint release (S3).
        registrar.playToClient(EnergyPayload.TYPE, EnergyPayload.STREAM_CODEC, (payload, context) -> {
            EnergyClientState.energy = payload.energy() / (float) EnergyData.MAX;
            EnergyClientState.exhausted = payload.exhausted();
        });

        // Poolrooms hallucination strength for the dream shader and the hallucination loop.
        registrar.playToClient(HallucinationPayload.TYPE, HallucinationPayload.STREAM_CODEC,
                (payload, context) -> HallucinationClientState.dream = payload.dream());

        // Your sanity (0..100) for the HUD readout and the low-sanity hallucinations.
        registrar.playToClient(SanityPayload.TYPE, SanityPayload.STREAM_CODEC,
                (payload, context) -> SanityClientState.sanity = payload.sanity());

        // Who has their flashlight on, so we can draw other players' beams (S1).
        registrar.playToClient(FlashlightStatesPayload.TYPE, FlashlightStatesPayload.STREAM_CODEC,
                (payload, context) -> payload.applyOnClient());

        // Safe rooms: "SAFE ROOM" on the camcorder while you're inside one, and "CHECKPOINT SAVED".
        registrar.playToClient(SafeRoomPayload.TYPE, SafeRoomPayload.STREAM_CODEC,
                (payload, context) -> SafeRoomClientState.inSafeRoom = payload.inSafeRoom());
        registrar.playToClient(CheckpointSavedPayload.TYPE, CheckpointSavedPayload.STREAM_CODEC,
                (payload, context) -> SafeRoomClientState.checkpointSavedMs = System.currentTimeMillis());

        // Level 94 (Sky slice): when the day began and where your nearest castle is, for the
        // client's sky, light and the castle on the horizon.
        registrar.playToClient(Level94StatePayload.TYPE, Level94StatePayload.STREAM_CODEC, (payload, context) -> {
            Level94Clock.setClientStart(payload.clockStart());
            Level94ClientState.castleX = payload.castleX();
            Level94ClientState.castleZ = payload.castleZ();
            Level94ClientState.castleKnown = true;
        });

        // Exit hints: where your level's nearest real exit is (its faint sound, the NightShot marks).
        registrar.playToClient(ExitHintPayload.TYPE, ExitHintPayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.hint.ExitHints.receive(payload));

        // Anomalies: the site you're in and the ones nearby (sounds, picture mood, effects).
        registrar.playToClient(AnomalyPayload.TYPE, AnomalyPayload.STREAM_CODEC,
                (payload, context) -> AnomalyClientState.update(payload.current().orElse(null), payload.nearby()));

        // The real world: filming with a camcorder, reality wearing thin, walking into walls in the dark.
        registrar.playToClient(RealWorldPayload.TYPE, RealWorldPayload.STREAM_CODEC, (payload, context) -> {
            CameraClientState.realRecording = payload.recording();
            CameraClientState.realThin = payload.thin();
        });
        registrar.playToServer(WallPushPayload.TYPE, WallPushPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.rickr.backrooms.world.RealWorldEntries.onWallPush(player);
            }
        });

        // Hiding in furniture: who is hiding (everyone draws nothing there; the hider's view goes
        // inside), and the hider's jump (get out) and sneak (hold your breath) keys.
        registrar.playToClient(HidingPayload.TYPE, HidingPayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.player.HidingClientState.receive(payload.entries()));
        registrar.playToServer(HideInputPayload.TYPE, HideInputPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.rickr.backrooms.player.HidingService.onInput(player, payload.action());
            }
        });

        // Separate realities: the blocks the drifted player sees differently (their clicks on them do nothing).
        registrar.playToClient(DriftPayload.TYPE, DriftPayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.DriftClient.receive(payload.faked()));

        registrar.playToServer(CameraTogglePayload.TYPE, CameraTogglePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CameraService.toggle(player, payload.toggle());
            }
        });

        // Level 9 anomalies: where the Mangled fog's black pillar stands (the clients draw it).
        registrar.playToClient(Level9PillarPayload.TYPE, Level9PillarPayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.anomaly.level9.MangledPillar.receive(payload.phase(), payload.x(), payload.z()));

        // Level 39: the layout seed (so the client can work out tunnels, facade and holes itself),
        // and echo quakes (the client plays the echoes, rumble, shaking and dust).
        registrar.playToClient(Level39StatePayload.TYPE, Level39StatePayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.Level39ClientState.receiveSeed(payload.layoutSeed()));
        // Level 490: lightning strikes (the bolt, the glow in the clouds, the thunder), and the
        // layout seed (so the sky can place the far colossal chains).
        registrar.playToClient(Level490StrikePayload.TYPE, Level490StrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.rickr.backrooms.client.Level490Lightning.receive(payload)));
        registrar.playToClient(Level490StatePayload.TYPE, Level490StatePayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.Level490ClientState.receiveSeed(payload.layoutSeed()));
        registrar.playToClient(Level39QuakePayload.TYPE, Level39QuakePayload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.Level39QuakeEffects.receive(payload.x(), payload.y(), payload.z(), payload.strength()));

        // Level 227: the tree moving (a lurch, the shudder before it shakes you loose, the drag in
        // from another level's forest); the client plays the groans, the leaves and the shake.
        registrar.playToClient(Level227Payload.TYPE, Level227Payload.STREAM_CODEC,
                (payload, context) -> com.rickr.backrooms.client.Level227Audio.receive(payload));
    }

    private ModNetwork() {}
}
