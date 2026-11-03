package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackItem;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import ic2.core.block.wiring.TileEntityChargepadBlock;
import ic2.core.block.wiring.TileEntityElectricBlock;

/**
 * Extends the chargepad's normal armour/main-inventory pass to an electric jetpack worn in a
 * Baubles-Expanded or TravellersGear slot. The injection runs just before the stock {@code getItems} call
 * in {@code updateEntityServer}, while the player field is still set; the transfer limit mirrors the
 * chargepad's tier (32/128/512/2048 EU).
 */
@Mixin(value = TileEntityChargepadBlock.class, remap = false)
public abstract class MixinTileEntityChargepadBlock {

    @Shadow
    private EntityPlayer player;

    @Shadow
    protected abstract void chargeitems(ItemStack stack, int amount);

    @Inject(
        method = "updateEntityServer",
        at = @At(
            value = "INVOKE",
            target = "Lic2/core/block/wiring/TileEntityChargepadBlock;getItems(Lnet/minecraft/entity/player/EntityPlayer;)V"))
    private void ic2tweaks$chargeSlotJetpacks(CallbackInfo ci) {
        if (!JetpackConfig.enabled || !JetpackConfig.chargepadChargesBaubles || player == null) {
            return;
        }
        int tier = ((TileEntityElectricBlock) (Object) this).tier;
        int limit = 32 << (2 * Math.max(0, Math.min(3, tier - 1)));
        chargeJetpack(JetpackWorn.getBaubles(player), limit);
        chargeJetpack(JetpackWorn.getTravellersGear(player), limit);
    }

    private void chargeJetpack(ItemStack stack, int limit) {
        if (JetpackWorn.isStockJetpack(stack) && JetpackItem.isElectric(stack)) {
            chargeitems(stack, limit);
        }
    }

    /** Marker used by {@code /ic2tweaks jetpack} to confirm the mixin was applied. */
    @Unique
    public boolean ic2tweaks$chargepadMixinApplied() {
        return true;
    }
}
