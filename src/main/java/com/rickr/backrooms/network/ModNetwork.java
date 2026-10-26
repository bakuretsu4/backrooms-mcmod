package com.rickr.backrooms.network;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.player.EnergyClientState;
import com.rickr.backrooms.player.EnergyData;
import com.rickr.backrooms.player.HallucinationClientState;
import com.rickr.backrooms.player.SanityClientState;
import com.rickr.backrooms.player.SafeRoomClientState;
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

        registrar.playToClient(TransitionPayload.TYPE, TransitionPayload.STREAM_CODEC, (payload, context) -> {
            CameraClientState.transitionKind = payload.kind();
            CameraClientState.transitionStartMs = System.currentTimeMillis();
        });

        // The noclip intro (filming -> glitch -> fall). Plain fields; the client cutscene reads them.
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

        // Sprint energy for the HUD bolt and the client-side sprint release (S3).
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

        registrar.playToServer(CameraTogglePayload.TYPE, CameraTogglePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CameraService.toggle(player, payload.toggle());
            }
        });
    }

    private ModNetwork() {}
}
