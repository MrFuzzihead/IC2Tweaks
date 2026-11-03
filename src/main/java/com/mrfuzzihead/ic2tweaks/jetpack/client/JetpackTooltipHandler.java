package com.mrfuzzihead.ic2tweaks.jetpack.client;

import java.util.Iterator;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fluids.FluidStack;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackItem;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackReadout;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ic2.api.item.IElectricItem;
import ic2.core.item.armor.ItemArmorJetpack;

/**
 * Adds localized fuel/charge, mode, slot and controls lines to the stock jetpack tooltip. IC2's gas
 * tooltip uses the raw fluid name and the electric jetpack has no addInformation at all, so the raw
 * {@code <fluid, amount mB>} line is replaced here.
 */
public final class JetpackTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!JetpackConfig.enabled || !JetpackConfig.tooltipsEnabled) {
            return;
        }
        ItemStack stack = event.itemStack;
        if (!JetpackItem.isSupported(stack)) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        boolean electric = JetpackItem.isElectric(stack);
        boolean quantum = JetpackItem.isQuantumJetpack(stack);
        long charge = Math.round(JetpackItem.charge(stack));
        long capacity = Math.round(JetpackItem.maxCharge(stack));
        int percent = JetpackReadout.percent(charge, capacity);

        // IC2's gas addInformation emits "< biogas, 1234 mB >"; replace it with the localized line below.
        for (Iterator<String> iterator = event.toolTip.iterator(); iterator.hasNext();) {
            String line = iterator.next();
            if (line != null && line.startsWith("<") && line.endsWith(">")) {
                iterator.remove();
            }
        }

        boolean hover = stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("hoverMode");
        if (quantum) {
            boolean enabled = stack.hasTagCompound() && stack.getTagCompound()
                .getBoolean("jetpack");
            event.toolTip.add(
                StatCollector.translateToLocalFormatted(
                    "ic2tweaks.jetpack.tooltip.enabled",
                    StatCollector.translateToLocal(
                        enabled ? "ic2tweaks.jetpack.tooltip.enabled.on" : "ic2tweaks.jetpack.tooltip.enabled.off")));
        }
        event.toolTip.add(
            StatCollector.translateToLocalFormatted(
                "ic2tweaks.jetpack.tooltip.mode",
                StatCollector.translateToLocal(
                    hover ? "ic2tweaks.jetpack.tooltip.mode.hover" : "ic2tweaks.jetpack.tooltip.mode.normal")));

        if (electric) {
            event.toolTip.add(
                StatCollector.translateToLocalFormatted(
                    "ic2tweaks.jetpack.tooltip.energy",
                    JetpackReadout.amount(charge),
                    JetpackReadout.amount(capacity),
                    percent));
            if (event.showAdvancedItemTooltips) {
                event.toolTip.add(
                    StatCollector.translateToLocalFormatted(
                        "ic2tweaks.jetpack.tooltip.tier",
                        ((IElectricItem) stack.getItem()).getTier(stack)));
            }
        } else {
            FluidStack fluid = ((ItemArmorJetpack) stack.getItem()).getFluid(stack);
            String fluidName = fluid != null ? fluid.getLocalizedName()
                : StatCollector.translateToLocal("ic2tweaks.jetpack.fluid.biogas");
            event.toolTip.add(
                StatCollector.translateToLocalFormatted(
                    "ic2tweaks.jetpack.tooltip.fuel",
                    fluidName,
                    JetpackReadout.amount(charge),
                    JetpackReadout.amount(capacity),
                    percent));
        }

        if (player != null) {
            JetpackWorn.WornSlot slot = JetpackWorn.slotOf(player, stack);
            if (slot != JetpackWorn.WornSlot.NONE) {
                event.toolTip.add(
                    StatCollector.translateToLocalFormatted(
                        "ic2tweaks.jetpack.tooltip.slot",
                        StatCollector.translateToLocal(
                            "ic2tweaks.jetpack.tooltip.slot." + slot.name()
                                .toLowerCase(Locale.ROOT))));
            }
        }

        event.toolTip.add(StatCollector.translateToLocal("ic2tweaks.jetpack.tooltip.controls.1"));
        event.toolTip.add(StatCollector.translateToLocal("ic2tweaks.jetpack.tooltip.controls.2"));
        if (quantum) {
            event.toolTip.add(StatCollector.translateToLocal("ic2tweaks.jetpack.tooltip.controls.quantum"));
        }
    }
}
