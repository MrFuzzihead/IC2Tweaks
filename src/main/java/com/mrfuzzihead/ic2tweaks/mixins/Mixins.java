package com.mrfuzzihead.ic2tweaks.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;

/**
 * Mixin registry for the global IC2 energy-net overload feature.
 * <p>
 * Every entry is gated by the master {@code enableEnetOverload} config option, so when the feature is
 * off IC2 behaves exactly as before (including honoring its own {@code misc/useLinearTransferModel}
 * setting).
 */
public enum Mixins implements IMixins {

    /** Forces {@code EnergyNetLocal.useLinearTransferModel} to true, ignoring IC2.ini. */
    ENET_LINEAR_MODEL(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinEnergyNetLocalLinearModel")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled)),

    /** Captures {@code injectEnergy} calls and explodes over-tier machines. */
    ENET_OVERLOAD(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinEnergyNetLocalOverload")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled)),

    /** Adds the cable's energy loss to its item tooltip. */
    ITEM_CABLE_TOOLTIP(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinItemCable")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled)),

    /** Applies IC2 1.12.2's insulation-based shock absorption to cables. */
    CABLE_ABSORPTION(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinTileEntityCable")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
