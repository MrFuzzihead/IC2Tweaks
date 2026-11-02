package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;
import ic2.core.item.armor.ItemArmorJetpack;

/**
 * Baubles-Expanded variant. It reports {@code baubleSlotTypes} (default cape/body/belt) through
 * {@link IBaubleExpanded}, while still honouring the legacy {@link BaubleType} for code paths that only
 * know the original API. The legacy mixin is not applied when this one is, so there are no duplicate
 * {@code IBauble} methods.
 */
@Mixin(value = ItemArmorJetpack.class, remap = false)
public abstract class MixinItemArmorJetpackBaubleExpanded implements IBaubleExpanded {

    @Override
    public String[] getBaubleTypes(ItemStack stack) {
        return JetpackConfig.baubleSlotTypes.clone();
    }

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
    public boolean ic2tweaks$baubleExpandedMixinApplied() {
        return true;
    }
}
