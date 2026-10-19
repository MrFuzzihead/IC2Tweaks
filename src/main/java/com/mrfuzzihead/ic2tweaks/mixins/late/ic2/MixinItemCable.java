package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.ic2tweaks.enet.EnetCableData;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;

import ic2.core.energy.EnergyNetLocal;
import ic2.core.item.block.ItemCable;

/**
 * Appends the cable's energy loss to its tooltip, next to IC2's existing packet-size line.
 * <p>
 * The loss only exists while the overload system forces IC2's linear transfer model, so the line is
 * only added when the master switch is on and the linear model is actually active (the mixin itself is
 * gated by the same master flag).
 */
@Mixin(value = ItemCable.class, remap = false)
public abstract class MixinItemCable {

    @Inject(method = "addInformation", at = @At("RETURN"), remap = false)
    private void ic2tweaks$addCableLoss(ItemStack itemStack, EntityPlayer player, List<String> info, boolean advanced,
        CallbackInfo ci) {
        if (!EnetOverloadConfig.enabled || !EnergyNetLocal.useLinearTransferModel) {
            return;
        }
        double loss = EnetCableData.conductionLoss(itemStack.getItemDamage());
        if (loss <= 0) {
            return;
        }
        info.add(StatCollector.translateToLocalFormatted("ic2tweaks.tooltip.cableLoss", loss));
    }
}
