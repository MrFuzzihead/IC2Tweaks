package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mrfuzzihead.ic2tweaks.enet.EnetCableData;

import ic2.core.block.wiring.TileEntityCable;

/**
 * Replaces IC2 1.7.10's hard-coded insulation absorption values with IC2 Experimental 1.12.2's
 * insulation-level formula (tin is deliberately the most sensitive cable; cables that cannot be
 * insulated are immune). The shock pass reads the value from the conductor, so this is the single
 * place the rule lives.
 */
@Mixin(value = TileEntityCable.class, remap = false)
public abstract class MixinTileEntityCable {

    @Shadow
    public short cableType;

    @Inject(method = "getInsulationEnergyAbsorption", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$useNewAbsorption(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(EnetCableData.insulationEnergyAbsorption(this.cableType));
    }
}
