package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.registry.ModBlockEntities;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.rickr.backrooms.client.hud.StaminaBarLayer;
import com.rickr.backrooms.client.menu.SettingsScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

/** Client-only registration (mod-bus events, routed automatically). Never loaded on a dedicated server. */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping NIGHT_SHOT_KEY = new KeyMapping("key.backrooms.night_shot",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.backrooms");
    public static final KeyMapping FLASHLIGHT_KEY = new KeyMapping("key.backrooms.flashlight",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.backrooms");
    public static final KeyMapping OSD_KEY = new KeyMapping("key.backrooms.osd",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.backrooms");

    /** What each level's anomalous areas do on the client (sounds, picture mood): client/anomaly. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AnomalyClientEffects.bootstrap();
        // The "Config" button in the Mods list opens our SETTINGS screen.
        ModList.get().getModContainerById(Backrooms.MOD_ID).ifPresent(container -> container.registerExtensionPoint(
                IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent)));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BACTERIA.get(), BacteriaRenderer::new);
        // Uses vanilla's player model layers (PLAYER / PLAYER_SLIM), so no layer definition of its own.
        event.registerEntityRenderer(ModEntities.PHANTOM_FRIEND.get(), PhantomFriendRenderer::new);
        // Level 9: both are drawn from code-built geometry (no layer definitions, no textures).
        event.registerEntityRenderer(ModEntities.WATCHER.get(), WatcherRenderer::new);
        event.registerEntityRenderer(ModEntities.OBSERVER.get(), ObserverRenderer::new);
        // Level 490: the Penitents and the pilgrim, code-built figures.
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level490Mobs.PENITENT.get(), PenitentRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level490Mobs.PILGRIM.get(), PilgrimRenderer::new);
        // Blackouts on Levels 0-2: a glowing face drawn as a billboard.
        event.registerEntityRenderer(ModEntities.SMILER.get(), SmilerRenderer::new);
        // Level 39: Hounds (tunnels) and Facelings (town), code-built models.
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level39Mobs.HOUND.get(), HoundRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level39Mobs.FACELING.get(), FacelingRenderer::new);
        // Level 39 tunnels: Clumps, Skin-stealers and Wretches, code-built models.
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level39Mobs.CLUMP.get(), ClumpRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level39Mobs.SKIN_STEALER.get(), SkinStealerRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level39Mobs.WRETCH.get(), WretchRenderer::new);
        // Level 94: parked cars and milk vans, drawn whole by their anchor block's renderer.
        event.registerBlockEntityRenderer(ModBlockEntities.L94_VEHICLE.get(), VehicleRenderer::new);
        // Thrown flares and glowsticks: small code-built sticks with a full-bright tip/tube.
        event.registerEntityRenderer(com.rickr.backrooms.registry.LightSupplies.FLARE_ENTITY.get(), ThrownLightRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.LightSupplies.GLOWSTICK_ENTITY.get(), ThrownLightRenderer::new);
        // Level 227: a fallen void bubble, a code-built iridescent sphere mirroring another level.
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Atmos.VOID_BUBBLE.get(), VoidBubbleRenderer::new);
        // Level 227 predators: wolves, stags and squirrels, code-built models with a full-bright glow layer.
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Predators.WOLF.get(), L227WolfRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Predators.STAG.get(), L227StagRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Predators.SQUIRREL.get(), L227SquirrelRenderer::new);
        // Level 227 grazers: bears (with their eyeshine layer), does and boars, code-built models.
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Grazers.BEAR.get(), L227BearRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Grazers.DOE.get(), L227DoeRenderer::new);
        event.registerEntityRenderer(com.rickr.backrooms.registry.Level227Grazers.BOAR.get(), L227BoarRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BacteriaModel.LAYER, BacteriaModel::createBodyLayer);
        event.registerLayerDefinition(SmilerModel.LAYER, SmilerModel::createLayer);
        // Level 39 Hounds and Facelings.
        event.registerLayerDefinition(HoundModel.LAYER, HoundModel::createBodyLayer);
        event.registerLayerDefinition(FacelingModel.LAYER, FacelingModel::createBodyLayer);
        // Level 39 Clumps, Skin-stealers and Wretches.
        event.registerLayerDefinition(ClumpModel.LAYER, ClumpModel::createLayer);
        event.registerLayerDefinition(SkinStealerModel.LAYER, SkinStealerModel::createLayer);
        event.registerLayerDefinition(WretchModel.LAYER, WretchModel::createLayer);
        // Level 94 vehicles (Props slice).
        event.registerLayerDefinition(VehicleModel.CAR_LAYER, VehicleModel::createCarLayer);
        event.registerLayerDefinition(VehicleModel.VAN_LAYER, VehicleModel::createVanLayer);
        // Level 227 predators.
        event.registerLayerDefinition(L227WolfModel.LAYER, L227WolfModel::createBodyLayer);
        event.registerLayerDefinition(L227StagModel.LAYER, L227StagModel::createBodyLayer);
        event.registerLayerDefinition(L227SquirrelModel.LAYER, L227SquirrelModel::createBodyLayer);
        // Level 227 grazers.
        event.registerLayerDefinition(L227BearModel.LAYER, L227BearModel::createBodyLayer);
        event.registerLayerDefinition(L227DoeModel.LAYER, L227DoeModel::createBodyLayer);
        event.registerLayerDefinition(L227BoarModel.LAYER, L227BoarModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(NIGHT_SHOT_KEY);
        event.register(FLASHLIGHT_KEY);
        event.register(OSD_KEY);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Backrooms.id("camcorder_hud"), new CamcorderHud());
        // The camcorder arrival card ("LEVEL 2 / Something hums inside the pipes."), lower left.
        event.registerAboveAll(com.rickr.backrooms.client.hud.ArrivalCard.LAYER_ID, new com.rickr.backrooms.client.hud.ArrivalCard());
        // The real world wearing thin: a faint stale yellow (client/intro/RealWorldClient).
        event.registerAboveAll(com.rickr.backrooms.client.intro.RealWorldClient.LAYER_ID,
                new com.rickr.backrooms.client.intro.RealWorldClient());
        // Sprint stamina: a thin faint line where the (hidden) XP bar would be, only while it matters.
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, Backrooms.id("stamina_bar"), new StaminaBarLayer());
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        // Matches "effects": "backrooms:backrooms" in data/backrooms/dimension_type/backrooms.json.
        event.register(Backrooms.id("backrooms"), new BackroomsDimensionEffects());
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> CamcorderEffect.onResourcesReloaded());
    }

    private ClientSetup() {}
}
