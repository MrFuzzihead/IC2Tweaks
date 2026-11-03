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

    /** Where a jetpack stack is currently worn. */
    public enum WornSlot {

        NONE,
        CHEST,
        BAUBLES,
        TRAVELLERS_GEAR
    }

    /** Supplies a jetpack from an optional slot mod without the common code referencing that mod. */
    public interface SlotProvider {

        ItemStack findWorn(EntityPlayer player);
    }

    private static SlotProvider baublesProvider;
    private static SlotProvider travellersGearProvider;

    private JetpackWorn() {}

    /** Registers the Baubles/Expanded resolver. Only call when Baubles is loaded. */
    public static void registerBaubles(SlotProvider provider) {
        baublesProvider = provider;
    }

    /** Registers the TravellersGear resolver. Only call when TravellersGear is loaded. */
    public static void registerTravellersGear(SlotProvider provider) {
        travellersGearProvider = provider;
    }

    /**
     * @return the worn jetpack stack, or {@code null}. The chest slot wins, matching stock IC2; the
     *         Baubles/Expanded and TravellersGear slots are fallbacks. Exactly one stack is returned per
     *         player, so the handler never ticks two jetpacks at once.
     */
    public static ItemStack get(EntityPlayer player) {
        ItemStack chest = player.inventory.armorInventory[CHEST_SLOT];
        if (isStockJetpack(chest)) {
            return chest;
        }
        // Chest wins over a bauble/TG jetpack. A quantum chest is only handed to the controller when
        // the optional quantum integration is enabled; otherwise stock IC2/GraviSuite keeps control.
        if (JetpackItem.isQuantumJetpack(chest)) {
            return JetpackConfig.tweakQuantumJetpack ? chest : null;
        }
        if (JetpackItem.isGraviSuiteJetpack(chest) || chest != null && chest.getItem() instanceof ItemArmorJetpack) {
            return null;
        }
        if (baublesProvider != null) {
            ItemStack found = baublesProvider.findWorn(player);
            if (isStockJetpack(found)) {
                return found;
            }
        }
        if (travellersGearProvider != null) {
            ItemStack found = travellersGearProvider.findWorn(player);
            if (isStockJetpack(found)) {
                return found;
            }
        }
        return null;
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

    /** @return the jetpack worn in the Baubles/Expanded slots, or {@code null}. */
    public static ItemStack getBaubles(EntityPlayer player) {
        return baublesProvider != null ? baublesProvider.findWorn(player) : null;
    }

    /** @return the jetpack worn in the TravellersGear slot, or {@code null}. */
    public static ItemStack getTravellersGear(EntityPlayer player) {
        return travellersGearProvider != null ? travellersGearProvider.findWorn(player) : null;
    }

    /** @return whether the stack is the one currently worn in the chest slot. */
    public static boolean isChestSlot(EntityPlayer player, ItemStack stack) {
        return player != null && stack != null && player.inventory.armorInventory[CHEST_SLOT] == stack;
    }

    /**
     * @return the slot {@code stack} is worn in, or {@link WornSlot#NONE} when it is not currently worn.
     *         The identity checks match {@link #get(EntityPlayer)}; they are only used for display.
     */
    public static WornSlot slotOf(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return WornSlot.NONE;
        }
        if (isChestSlot(player, stack)) {
            return WornSlot.CHEST;
        }
        if (baublesProvider != null && baublesProvider.findWorn(player) == stack) {
            return WornSlot.BAUBLES;
        }
        if (travellersGearProvider != null && travellersGearProvider.findWorn(player) == stack) {
            return WornSlot.TRAVELLERS_GEAR;
        }
        return WornSlot.NONE;
    }
}
