package com.mrfuzzihead.ic2tweaks.jetpack.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import travellersgear.TravellersGear;
import travellersgear.api.TravellersGearAPI;
import travellersgear.common.network.MessageNBTSync;

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

    @Override
    public void sync(EntityPlayer player, ItemStack stack) {
        ItemStack[] inventory = TravellersGearAPI.getExtendedInventory(player);
        if (inventory == null) {
            return;
        }
        TravellersGearAPI.setExtendedInventory(player, inventory);
        if (player instanceof EntityPlayerMP) {
            TravellersGear.packetHandler.sendTo(new MessageNBTSync(player), (EntityPlayerMP) player);
        }
    }
}
