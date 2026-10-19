package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.ic2tweaks.IC2Tweaks;

import ic2.core.energy.EnergyNetLocal;

/**
 * Overrides IC2's {@code misc/useLinearTransferModel} config option.
 * <p>
 * 1.7.10's default ("energy distribution") model has no physical voltage/current data at all, so the
 * 1.12.2-style overload rules can only be implemented meaningfully on the linear transfer model. The
 * mixin is only applied when the mod's master {@code enableEnetOverload} option is on; it then hard-sets
 * the field to {@code true}, ignoring whatever IC2.ini says.
 */
@Mixin(value = EnergyNetLocal.class, remap = false)
public abstract class MixinEnergyNetLocalLinearModel {

    @Shadow
    @Final
    @Mutable
    public static boolean useLinearTransferModel;

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false)
    private static void ic2tweaks$forceLinearTransferModel(CallbackInfo ci) {
        useLinearTransferModel = true;
        IC2Tweaks.LOG.info("Energy-net overload enabled: forced IC2 useLinearTransferModel=true (IC2.ini ignored)");
    }
}
