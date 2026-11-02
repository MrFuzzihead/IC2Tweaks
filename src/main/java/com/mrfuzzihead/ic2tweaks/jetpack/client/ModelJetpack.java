package com.mrfuzzihead.ic2tweaks.jetpack.client;

import net.minecraft.client.model.ModelBiped;

/**
 * A minimal backpack model: the standard biped torso pushed slightly behind the player, textured with
 * IC2's existing {@code jetpack_1.png}. The front face sits inside the player's chest and is depth-hidden,
 * so a chestplate in front stays visible. Phase 3 can replace this with a dedicated tank/nozzle model.
 */
public final class ModelJetpack extends ModelBiped {

    public ModelJetpack() {
        super(0.0F, 0.0F, 64, 32);
        bipedHead.showModel = false;
        bipedHeadwear.showModel = false;
        bipedRightArm.showModel = false;
        bipedLeftArm.showModel = false;
        bipedRightLeg.showModel = false;
        bipedLeftLeg.showModel = false;
        bipedBody.setRotationPoint(0.0F, 0.0F, 1.5F);
    }
}
