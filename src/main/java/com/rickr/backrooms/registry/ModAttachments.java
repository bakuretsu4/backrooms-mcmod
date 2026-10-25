package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraData;
import com.rickr.backrooms.player.EnergyData;
import com.rickr.backrooms.player.HallucinationData;
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

    private ModAttachments() {}
}
