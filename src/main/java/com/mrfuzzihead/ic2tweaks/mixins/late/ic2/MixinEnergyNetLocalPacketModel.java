package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ic2.api.energy.NodeStats;
import ic2.core.energy.EnergyNetLocal;
import ic2.core.energy.Ic2TweaksPacketModel;

/**
 * Replaces the main-thread energy-net application step with the packet-model tick and serves stats from the
 * packet paths while packet mode is active.
 * <p>
 * {@code processChanges()} is the only energy-net phase with {@code locked == false}, i.e. the only place
 * where IC2 permits removing tiles; it is also where IC2 itself injects into sinks and draws from sources.
 * The legacy {@code Change} list is never populated in packet mode ({@code startCalculation} is cancelled),
 * so it and IC2's removed-tile bookkeeping are cleared here to keep the net from accumulating stale state.
 */
@Mixin(value = EnergyNetLocal.class, remap = false)
public abstract class MixinEnergyNetLocalPacketModel {

    @Shadow
    @Final
    private Map registeredTiles;

    @Shadow
    @Final
    private Set removedTiles;

    @Shadow
    private List changes;

    @Inject(method = "processChanges", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$packetTick(CallbackInfo ci) {
        if (Ic2TweaksPacketModel.isActive()) {
            Ic2TweaksPacketModel.tick(this);
            removedTiles.clear();
            changes.clear();
            ci.cancel();
        }
    }

    @Inject(method = "getNodeStats", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$packetNodeStats(TileEntity te, CallbackInfoReturnable<NodeStats> cir) {
        if (Ic2TweaksPacketModel.isActive()) {
            cir.setReturnValue(Ic2TweaksPacketModel.getNodeStats(this, registeredTiles, te));
        }
    }

    @Inject(method = "getTotalEnergyEmitted", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$packetTotalEmitted(TileEntity te, CallbackInfoReturnable<Double> cir) {
        if (Ic2TweaksPacketModel.isActive()) {
            cir.setReturnValue(Ic2TweaksPacketModel.getTotalEnergyEmitted(this, registeredTiles, te));
        }
    }

    @Inject(method = "getTotalEnergySunken", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$packetTotalSunken(TileEntity te, CallbackInfoReturnable<Double> cir) {
        if (Ic2TweaksPacketModel.isActive()) {
            cir.setReturnValue(Ic2TweaksPacketModel.getTotalEnergySunken(this, registeredTiles, te));
        }
    }
}
