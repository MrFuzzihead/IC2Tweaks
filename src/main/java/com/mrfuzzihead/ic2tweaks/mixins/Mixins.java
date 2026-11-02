package com.mrfuzzihead.ic2tweaks.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.mrfuzzihead.ic2tweaks.WrenchConfig;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;

/**
 * Mixin registry for the IC2 core tweaks.
 * <p>
 * The energy-net entries are gated by the master {@code enableEnetOverload} config option, so when that
 * feature is off IC2 behaves exactly as before (including honoring its own
 * {@code misc/useLinearTransferModel} setting). The wrench tweak is gated independently by
 * {@code wrench/losslessWrench}.
 */
public enum Mixins implements IMixins {

    /** Treats every machine's wrench drop rate as 100%, so wrenched machines never break. */
    WRENCH_LOSSLESS(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinItemToolWrench")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> WrenchConfig.lossless)),

    /** Forces {@code EnergyNetLocal.useLinearTransferModel} to true, ignoring IC2.ini (legacy mode only). */
    ENET_LINEAR_MODEL(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinEnergyNetLocalLinearModel")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled && !EnetOverloadConfig.packetModel)),

    /** Captures {@code injectEnergy} calls and explodes over-tier machines (legacy mode only). */
    ENET_OVERLOAD(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinEnergyNetLocalOverload")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled && !EnetOverloadConfig.packetModel)),

    /** Attaches packet-model state to each IC2 grid and disables the legacy linear calculation. */
    ENET_PACKET_GRID(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinGridPacketModel")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled && EnetOverloadConfig.packetModel)),

    /** Replaces the main-thread energy application/stat methods while packet mode is active. */
    ENET_PACKET_LOCAL(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinEnergyNetLocalPacketModel")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled && EnetOverloadConfig.packetModel)),

    /** Adds the cable's energy loss to its item tooltip. */
    ITEM_CABLE_TOOLTIP(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinItemCable")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled)),

    /** Applies IC2 1.12.2's insulation-based shock absorption to cables. */
    CABLE_ABSORPTION(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinTileEntityCable")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> EnetOverloadConfig.enabled)),

    /** Replaces IC2's stock jetpack tick with the Phase 1 flight handler. */
    JETPACK(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.MixinItemArmorJetpack")
        .addRequiredMod(TargetMods.IC2)
        .setApplyIf(() -> JetpackConfig.enabled)),

    /** Makes the vanilla armour bar honour ISpecialArmor.getArmorDisplay (hides the jetpack's fake 8 points). */
    JETPACK_ARMOR_HUD(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("minecraft.MixinEntityPlayerArmorValue"));

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
