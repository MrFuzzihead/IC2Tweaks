package com.mrfuzzihead.ic2tweaks.jetpack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ic2.core.Ic2Items;
import ic2.core.item.armor.ItemArmorJetpack;

/**
 * Right-click a biogas container (cell, can, etc.) to refill a worn gas jetpack. This is the QoL path for
 * packs that do not want to place a Canning Machine just to top up; electric and quantum jetpacks are not
 * affected. The action is server-authoritative and cancels the normal container use on success.
 */
public final class JetpackRefuelHandler {

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!JetpackConfig.enabled || !JetpackConfig.easyGasRefuel
            || event.entityPlayer == null
            || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || held.getItem() instanceof ItemArmorJetpack) {
            return;
        }
        boolean biogasCell = isBiogasCell(held);
        IFluidContainerItem container = held.getItem() instanceof IFluidContainerItem
            ? (IFluidContainerItem) held.getItem()
            : null;
        if (!biogasCell && container == null && !FluidContainerRegistry.isFilledContainer(held)) {
            return;
        }
        ItemStack jetpackStack = JetpackWorn.get(player);
        if (!(jetpackStack != null && jetpackStack.getItem() instanceof ItemArmorJetpack)
            || JetpackItem.isElectric(jetpackStack)) {
            return;
        }
        FluidStack fluid = biogasCell ? biogasFluid()
            : container != null ? container.getFluid(held) : FluidContainerRegistry.getFluidForFilledItem(held);
        if (fluid == null || !isBiogas(fluid)) {
            return;
        }
        ItemArmorJetpack jetpack = (ItemArmorJetpack) jetpackStack.getItem();
        int free = (int) Math.floor(jetpack.getMaxCharge(jetpackStack) - jetpack.getCharge(jetpackStack));
        if (free <= 0) {
            return;
        }
        int amount = Math.min(fluid.amount, free);
        int filled = jetpack.fill(jetpackStack, new FluidStack(fluid.getFluid(), amount), true);
        if (filled > 0) {
            if (biogasCell) {
                consumeHeld(player, held, Ic2Items.cell.copy());
            } else if (container != null) {
                container.drain(held, filled, true);
            } else {
                ItemStack empty = FluidContainerRegistry.drainFluidContainer(held);
                if (empty != null) {
                    consumeHeld(player, held, empty);
                }
            }
            event.setCanceled(true);
        }
    }

    private static FluidStack biogasFluid() {
        net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid("ic2biogas");
        if (fluid == null) {
            fluid = FluidRegistry.getFluid("biogas");
        }
        return fluid == null ? null : new FluidStack(fluid, 1000);
    }

    private static boolean isBiogas(FluidStack fluid) {
        String name = FluidRegistry.getFluidName(fluid);
        return "ic2biogas".equals(name) || "biogas".equals(name);
    }

    private static boolean isBiogasCell(ItemStack stack) {
        return stack.getItem() == Ic2Items.biogasCell.getItem()
            && stack.getItemDamage() == Ic2Items.biogasCell.getItemDamage();
    }

    private static void consumeHeld(EntityPlayer player, ItemStack held, ItemStack empty) {
        if (held.stackSize <= 1) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, empty);
        } else {
            --held.stackSize;
            if (!player.inventory.addItemStackToInventory(empty)) {
                player.dropPlayerItemWithRandomChoice(empty, false);
            }
        }
    }
}
