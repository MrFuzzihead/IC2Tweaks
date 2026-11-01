package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mrfuzzihead.ic2tweaks.WrenchConfig;

import ic2.core.item.tool.ItemToolWrench;
import ic2.core.item.tool.ItemToolWrenchElectric;

/**
 * Makes wrenched machines lossless while keeping a meaningful cost.
 * <p>
 * IC2's wrench code has a native lossless path: when a machine's drop rate is below 100% and
 * {@code overrideWrenchSuccessRate()} returns true, it checks {@code canTakeDamage(200)} and then
 * charges {@code damage(200)} (200 durability for a normal wrench, 10,000 EU for an electric one).
 * <p>
 * We force that path on for every wrench, then replace the lossless charge with a configured cost:
 * normal wrenches pay {@link WrenchConfig#manualDurabilityCost} durability and electric wrenches pay
 * {@link WrenchConfig#electricEnergyCost} EU. Machines that already have a 100% drop rate never enter
 * the lossless path, so cables and blocks that were already safe keep their normal 10-damage cost.
 */
@Mixin(value = ItemToolWrench.class, remap = false)
public abstract class MixinItemToolWrench {

    /** Force IC2's native lossless branch for every wrench, not just an electric wrench in lossless mode. */
    @Redirect(
        method = "onItemUseFirst",
        at = @At(
            value = "INVOKE",
            target = "Lic2/core/item/tool/ItemToolWrench;overrideWrenchSuccessRate(Lnet/minecraft/item/ItemStack;)Z"),
        require = 1,
        remap = false)
    private boolean ic2tweaks$forceLossless(ItemToolWrench wrench, ItemStack stack) {
        return WrenchConfig.lossless || wrench.overrideWrenchSuccessRate(stack);
    }

    /** Check the configured EU cost instead of IC2's hard-coded 10,000 EU for electric wrenches. */
    @Redirect(
        method = "onItemUseFirst",
        at = @At(
            value = "INVOKE",
            target = "Lic2/core/item/tool/ItemToolWrench;canTakeDamage(Lnet/minecraft/item/ItemStack;I)Z",
            ordinal = 2),
        require = 1,
        remap = false)
    private boolean ic2tweaks$checkLosslessCost(ItemToolWrench wrench, ItemStack stack, int amount) {
        if (WrenchConfig.lossless && stack.getItem() instanceof ItemToolWrenchElectric) {
            return wrench.canTakeDamage(stack, WrenchConfig.electricDamageCost());
        }
        return wrench.canTakeDamage(stack, amount);
    }

    /** Charge the configured lossless cost instead of IC2's hard-coded 200 damage. */
    @Redirect(
        method = "onItemUseFirst",
        at = @At(
            value = "INVOKE",
            target = "Lic2/core/item/tool/ItemToolWrench;damage(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/player/EntityPlayer;)V",
            ordinal = 1),
        require = 1,
        remap = false)
    private void ic2tweaks$chargeLosslessCost(ItemToolWrench wrench, ItemStack stack, int amount, EntityPlayer player) {
        if (!WrenchConfig.lossless) {
            wrench.damage(stack, amount, player);
        } else if (stack.getItem() instanceof ItemToolWrenchElectric) {
            wrench.damage(stack, WrenchConfig.electricDamageCost(), player);
        } else {
            wrench.damage(stack, WrenchConfig.manualDurabilityCost, player);
        }
    }
}
