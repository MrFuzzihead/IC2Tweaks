package com.mrfuzzihead.ic2tweaks.jetpack.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import travellersgear.api.TravellersGearAPI;

/**
 * TravellersGear slot resolver. This class is only loaded when TravellersGear is present (see
 * {@code IC2Tweaks.preInit}).
 */
public final class TravellersGearCompat implements JetpackWorn.SlotProvider {

    @Override
    public ItemStack findWorn(EntityPlayer player) {
        int slot = JetpackConfig.travellersGearSlot.index;
        if (slot < 0) {
            return null;
        }
        ItemStack[] inventory = TravellersGearAPI.getExtendedInventory(player);
        if (inventory == null || slot >= inventory.length) {
            return null;
        }
        ItemStack stack = inventory[slot];
        return JetpackWorn.isStockJetpack(stack) ? stack : null;
    }
}
