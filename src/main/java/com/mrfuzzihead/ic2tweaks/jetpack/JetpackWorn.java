package com.mrfuzzihead.ic2tweaks.jetpack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import ic2.core.item.armor.ItemArmorJetpack;
import ic2.core.item.armor.ItemArmorJetpackElectric;

/**
 * Resolves which jetpack stack is currently worn and whether it is one of the stock IC2 items. Phase 1
 * only looks at the chest slot; the Baubles/TravellersGear slots are added in Phase 2.
 */
public final class JetpackWorn {

    /** Armour inventory index of the chestplate slot. */
    public static final int CHEST_SLOT = 2;

    private JetpackWorn() {}

    /**
     * @return the worn jetpack stack, or {@code null}. The chest slot wins, matching stock IC2; Phase 2 adds
     *         the Baubles and TravellersGear slots as fallbacks.
     */
    public static ItemStack get(EntityPlayer player) {
        ItemStack chest = player.inventory.armorInventory[CHEST_SLOT];
        return isStockJetpack(chest) ? chest : null;
    }

    /**
     * @return whether the stack is one of IC2's stock jetpacks. Addon subclasses only qualify when
     *         {@link JetpackConfig#tweakAddonJetpacks} is enabled, so an addon that overrides
     *         {@code useJetpack}/{@code onArmorTick} keeps its own behaviour by default.
     */
    public static boolean isStockJetpack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmorJetpack)) {
            return false;
        }
        if (JetpackConfig.tweakAddonJetpacks) {
            return true;
        }
        Class<?> itemClass = stack.getItem()
            .getClass();
        return itemClass == ItemArmorJetpack.class || itemClass == ItemArmorJetpackElectric.class;
    }

    /** @return whether the stack is the one currently worn in the chest slot. */
    public static boolean isChestSlot(EntityPlayer player, ItemStack stack) {
        return player.inventory.armorInventory[CHEST_SLOT] == stack;
    }
}
