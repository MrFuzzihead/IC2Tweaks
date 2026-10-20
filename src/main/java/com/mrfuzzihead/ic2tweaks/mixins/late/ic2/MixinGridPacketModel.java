package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mrfuzzihead.ic2tweaks.enet.packet.PacketGridStateHolder;

import ic2.core.energy.Ic2TweaksPacketModel;

/**
 * Attaches packet-model state to an IC2 {@code Grid} and disables the legacy linear calculation while the
 * packet model is active.
 * <p>
 * {@code Grid.nodes} is private and its value type ({@code Node}) is package-private, so the shadow is a
 * raw {@link Map}; the live adapter (which lives in {@code ic2.core.energy}) casts the values internally.
 * The state itself is a plain {@link Object} here for the same reason.
 */
@Mixin(targets = "ic2.core.energy.Grid", remap = false)
public abstract class MixinGridPacketModel implements PacketGridStateHolder {

    @Shadow
    @Final
    private Map nodes;

    @Unique
    private Object ic2tweaks$packetState;

    /** Any topology change drops the cached graph/path data; the next tick rebuilds it. */
    @Inject(method = "invalidate", at = @At("HEAD"), remap = false)
    private void ic2tweaks$invalidate(CallbackInfo ci) {
        if (Ic2TweaksPacketModel.isActive()) {
            ic2tweaks$packetState = null;
        }
    }

    /** Skip the async linear solve (and its upfront source draw); the packet tick replaces it. */
    @Inject(method = "startCalculation", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$skipStartCalculation(CallbackInfoReturnable<Runnable> cir) {
        if (Ic2TweaksPacketModel.isActive()) {
            cir.setReturnValue(null);
        }
    }

    /** Avoid a second set of getOfferedEnergy()/getDemandedEnergy() calls after the packet tick. */
    @Inject(method = "prepareCalculation", at = @At("HEAD"), cancellable = true, remap = false)
    private void ic2tweaks$skipPrepareCalculation(CallbackInfo ci) {
        if (Ic2TweaksPacketModel.isActive()) {
            ci.cancel();
        }
    }

    @Unique
    @Override
    public Object ic2tweaks$getPacketState() {
        return ic2tweaks$packetState;
    }

    @Unique
    @Override
    public void ic2tweaks$setPacketState(Object state) {
        ic2tweaks$packetState = state;
    }

    @Unique
    @Override
    public Map ic2tweaks$rawNodes() {
        return nodes;
    }
}
