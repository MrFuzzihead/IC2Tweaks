package com.mrfuzzihead.ic2tweaks.jetpack.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import baubles.api.BaublesApi;

/**
 * Baubles/Expanded slot resolver. This class is only loaded when the Baubles mod is present (see
 * {@code IC2Tweaks.preInit}); {@code JetpackWorn} itself only knows the common {@link SlotProvider}.
 */
public final class BaublesCompat implements JetpackWorn.SlotProvider {

    @Override
    public ItemStack findWorn(EntityPlayer player) {
        IInventory inventory = BaublesApi.getBaubles(player);
        if (inventory == null) {
            return null;
        }
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (JetpackWorn.isStockJetpack(stack)) {
                return stack;
            }
        }
        return null;
    }
}
