package com.mrfuzzihead.ic2tweaks.jetpack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.core.item.armor.ItemArmorJetpack;
import ic2.core.item.armor.ItemArmorQuantumSuit;

/**
 * Common item access for the stock IC2 jetpacks and the optionally integrated quantum chest. Keeping the
 * charge/drain calls here avoids casting the handler, HUD and tooltip to {@link ItemArmorJetpack} and lets
 * the quantum suit use IC2's electric-item manager.
 */
public final class JetpackItem {

    private JetpackItem() {}

    /** Jetpack items that the flight controller may tick. */
    public static boolean isSupported(ItemStack stack) {
        return JetpackWorn.isStockJetpack(stack) || JetpackConfig.tweakQuantumJetpack && isQuantumJetpack(stack);
    }

    /** Any chest jetpack, including ones whose controller we do not replace (used for the double-jetpack policy). */
    public static boolean isJetpackItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        return stack.getItem() instanceof ItemArmorJetpack || isQuantumJetpack(stack) || isGraviSuiteJetpack(stack);
    }

    /** @return whether this is the quantum chestplate (armour type 1). */
    public static boolean isQuantumJetpack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmorQuantumSuit)) {
            return false;
        }
        return ((ItemArmor) stack.getItem()).armorType == 1;
    }

    /**
     * GraviSuite is optional and is only used for the chest-wins policy here. Class-name matching avoids
     * loading its classes when the mod is absent; no reflection is involved.
     */
    public static boolean isGraviSuiteJetpack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        String name = stack.getItem()
            .getClass()
            .getName();
        return name.equals("gravisuite.ItemGraviChestPlate") || name.equals("gravisuite.ItemAdvancedJetPack")
            || name.equals("gravisuite.ItemAdvancedNanoChestPlate");
    }

    public static boolean isElectric(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IElectricItem;
    }

    public static double charge(ItemStack stack) {
        if (stack == null) {
            return 0.0D;
        }
        Item item = stack.getItem();
        if (item instanceof IElectricItem) {
            return ElectricItem.manager.getCharge(stack);
        }
        if (item instanceof ItemArmorJetpack) {
            return ((ItemArmorJetpack) item).getCharge(stack);
        }
        return 0.0D;
    }

    public static double maxCharge(ItemStack stack) {
        if (stack == null) {
            return 0.0D;
        }
        Item item = stack.getItem();
        if (item instanceof IElectricItem) {
            return ((IElectricItem) item).getMaxCharge(stack);
        }
        if (item instanceof ItemArmorJetpack) {
            return ((ItemArmorJetpack) item).getMaxCharge(stack);
        }
        return 0.0D;
    }

    /** @return whether the item paid the requested amount; gas items pre-check with {@link JetpackRules}. */
    public static boolean consume(EntityPlayer player, ItemStack stack, int amount) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof IElectricItem) {
            return ElectricItem.manager.use(stack, amount, player);
        }
        if (item instanceof ItemArmorJetpack) {
            ((ItemArmorJetpack) item).use(stack, amount);
            return true;
        }
        return false;
    }
}
