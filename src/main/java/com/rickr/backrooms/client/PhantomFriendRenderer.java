package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.entity.PhantomFriend;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the phantom exactly like a player: the copied player's real skin (from the tab list's
 * {@link PlayerInfo}), the right arm shape (slim "Alex" or wide "Steve") and a name tag.
 *
 * <p>Extends {@link LivingEntityRenderer} rather than MobRenderer on purpose: the living-entity
 * renderer always shows a name tag (like for players), while mobs only show one when named.
 */
public class PhantomFriendRenderer extends LivingEntityRenderer<PhantomFriend, PlayerModel<PhantomFriend>> {
    /** Player models are drawn at 15/16 scale (same as vanilla's PlayerRenderer). */
    private static final float PLAYER_SCALE = 0.9375f;

    private final PhantomFriendModel wideModel;
    private final PhantomFriendModel slimModel;

    public PhantomFriendRenderer(EntityRendererProvider.Context context) {
        super(context, new PhantomFriendModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = (PhantomFriendModel) this.model;
        this.slimModel = new PhantomFriendModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(PhantomFriend entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (entity.isVanishing()) {
            return; // gone in an instant; the entity itself is removed a few ticks later
        }
        // Pick the arm shape that matches the copied skin before drawing.
        this.model = skinOf(entity).model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomFriend entity) {
        return skinOf(entity).texture();
    }

    @Override
    protected void scale(PhantomFriend entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
    }

    /**
     * The copied player's skin if they're online (the client knows every online player's skin from
     * the tab list), otherwise the default Steve/Alex skin for their UUID.
     */
    private static PlayerSkin skinOf(PhantomFriend entity) {
        UUID id = entity.mimicId().orElse(entity.getUUID());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(id) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }
}
