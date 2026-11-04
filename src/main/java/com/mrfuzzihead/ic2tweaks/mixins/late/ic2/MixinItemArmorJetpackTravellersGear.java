package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;

import ic2.core.item.armor.ItemArmorJetpack;
import travellersgear.api.ITravellersGear;

/**
 * Makes the stock jetpacks valid TravellersGear items in the configured slot (CLOAK or SHOULDERS).
 * The handler owns the actual ticking; these methods only satisfy TG's slot/render checks.
 */
@Mixin(value = ItemArmorJetpack.class, remap = false)
public abstract class MixinItemArmorJetpackTravellersGear implements ITravellersGear {

    @Override
    public int getSlot(ItemStack stack) {
        return JetpackConfig.travellersGearSlot.index;
    }

    @Override
    public void onTravelGearTick(EntityPlayer player, ItemStack stack) {}

    @Override
    public void onTravelGearEquip(EntityPlayer player, ItemStack stack) {}

    @Override
    public void onTravelGearUnequip(EntityPlayer player, ItemStack stack) {}

    @Unique
    public boolean ic2tweaks$travellersGearMixinApplied() {
        return true;
    }
}
