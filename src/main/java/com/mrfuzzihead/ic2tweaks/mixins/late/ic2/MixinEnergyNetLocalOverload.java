package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.ic2tweaks.enet.EnetOverload;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadMath;

import ic2.api.energy.tile.IEnergySink;
import ic2.core.energy.EnergyNetLocal;

/**
 * Captures the {@code IEnergySink.injectEnergy} calls IC2's energy net performs in
 * {@code EnergyNetLocal.processChanges()} and applies the machine overload rule once the tick's energy
 * has been delivered.
 * <p>
 * {@code processChanges()} runs with {@code EnergyNetLocal.locked == false}, which is the only point in
 * the energy-net tick where removing a tile is legal. The captured {@code amount - returned} mirrors
 * 1.12.2's "effective" packet size, so a full machine that rejects the packet does not explode.
 */
@Mixin(value = EnergyNetLocal.class, remap = false)
public abstract class MixinEnergyNetLocalOverload {

    @Unique
    private List<EnetOverload.Injection> ic2tweaks$injections;

    @Redirect(
        method = "processChanges",
        at = @At(
            value = "INVOKE",
            target = "Lic2/api/energy/tile/IEnergySink;injectEnergy(Lnet/minecraftforge/common/util/ForgeDirection;DD)D"),
        require = 1,
        remap = false)
    private double ic2tweaks$captureInjection(IEnergySink sink, ForgeDirection dir, double amount, double voltage) {
        double injectAmount = amount;
        if (EnetOverloadConfig.demandCapped) {
            injectAmount = EnetOverloadMath.clampToDemand(amount, sink.getDemandedEnergy());
        }
        double returned = sink.injectEnergy(dir, injectAmount, voltage);
        if (ic2tweaks$injections == null) {
            ic2tweaks$injections = new ArrayList<>();
        }
        ic2tweaks$injections.add(new EnetOverload.Injection(sink, injectAmount - returned));
        // Hand any amount we withheld (demand cap) plus the sink's own rejection back to IC2.
        return amount - injectAmount + returned;
    }

    @Inject(method = "processChanges", at = @At("RETURN"), remap = false)
    private void ic2tweaks$applyOverloads(CallbackInfo ci) {
        if (ic2tweaks$injections != null && !ic2tweaks$injections.isEmpty()) {
            EnetOverload.applyMachineOverloads(ic2tweaks$injections);
            ic2tweaks$injections.clear();
        }
    }
}
