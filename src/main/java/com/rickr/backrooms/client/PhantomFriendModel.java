package com.rickr.backrooms.client;

import com.rickr.backrooms.entity.PhantomFriend;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The normal player model, plus a friendly wave: right arm raised high, swaying side to side. */
public class PhantomFriendModel extends PlayerModel<PhantomFriend> {
    public PhantomFriendModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(PhantomFriend entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (entity.isWaving()) {
            // Positive zRot swings the right arm out and up; ~150 degrees is "hand above the head".
            rightArm.zRot = 2.6f + 0.35f * Mth.sin(ageInTicks * 0.45f);
            rightArm.xRot = -0.15f;
            rightArm.yRot = 0.0f;
            // The sleeve layer was copied from the arm inside super.setupAnim, so copy it again.
            rightSleeve.copyFrom(rightArm);
        }
    }
}
