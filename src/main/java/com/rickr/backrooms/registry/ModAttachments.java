package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraData;
import com.rickr.backrooms.player.CheckpointData;
import com.rickr.backrooms.player.EnergyData;
import com.rickr.backrooms.player.HallucinationData;
import com.rickr.backrooms.player.SanityData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Backrooms.MOD_ID);

    public static final Supplier<AttachmentType<CameraData>> CAMERA = ATTACHMENTS.register("camera",
            () -> AttachmentType.builder(() -> new CameraData()).serialize(CameraData.CODEC).copyOnDeath().build());

    /** Sprint energy (not copied on death: you respawn rested). */
    public static final Supplier<AttachmentType<EnergyData>> ENERGY = ATTACHMENTS.register("energy",
            () -> AttachmentType.builder(() -> new EnergyData()).serialize(EnergyData.CODEC).build());

    /**
     * Poolrooms wet meter + phantom bookkeeping. Not serialized (transient): relogging or dying
     * simply wakes you from the hallucination.
     */
    public static final Supplier<AttachmentType<HallucinationData>> HALLUCINATION = ATTACHMENTS.register("hallucination",
            () -> AttachmentType.builder(() -> new HallucinationData()).build());

    /** Sanity 0..100 (saved, so it survives a relog; not copied on death: SanityService sets 70 on respawn). */
    public static final Supplier<AttachmentType<SanityData>> SANITY = ATTACHMENTS.register("sanity",
            () -> AttachmentType.builder(() -> new SanityData()).serialize(SanityData.CODEC).build());

    /** The last checkpoint radio you used: saved, and copied on death (that's how you respawn there). */
    public static final Supplier<AttachmentType<CheckpointData>> CHECKPOINT = ATTACHMENTS.register("checkpoint",
            () -> AttachmentType.builder(() -> new CheckpointData()).serialize(CheckpointData.CODEC).copyOnDeath().build());

    private ModAttachments() {}
}
