package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import ic2.core.item.armor.ItemArmorJetpack;

/**
 * Makes the stock jetpacks valid baubles on original 1.7.10 Baubles (which has no expanded slot API).
 * <p>
 * This is the legacy variant; {@link MixinItemArmorJetpackBaubleExpanded} is applied instead when
 * Baubles-Expanded is present, so the two are mutually exclusive and do not add duplicate methods.
 * The handler owns the actual ticking; these methods only satisfy the slot checks.
 */
@Mixin(value = ItemArmorJetpack.class, remap = false)
public abstract class MixinItemArmorJetpackBauble implements IBauble {

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.BELT;
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Unique
    public boolean ic2tweaks$baubleMixinApplied() {
        return true;
    }
}
