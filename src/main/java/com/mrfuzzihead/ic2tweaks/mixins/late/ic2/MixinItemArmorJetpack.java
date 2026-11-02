package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import ic2.core.item.armor.ItemArmorJetpack;

/**
 * Replaces IC2's {@code ItemArmorJetpack.onArmorTick} with {@link com.mrfuzzihead.ic2tweaks.jetpack.JetpackHandler}.
 * The method is cancelled only for the stock items (or addons when {@code tweakAddonJetpacks} is on), so
 * addon jetpacks keep their own behaviour. The runtime {@link JetpackConfig#enabled} check also makes a
 * config change without a restart fall back to the stock body.
 */
@Mixin(value = ItemArmorJetpack.class, remap = false)
public abstract class MixinItemArmorJetpack {

    @Inject(method = "onArmorTick", at = @At("HEAD"), cancellable = true)
    private void ic2tweaks$replaceStockTick(World world, EntityPlayer player, ItemStack stack, CallbackInfo ci) {
        if (JetpackConfig.enabled && JetpackWorn.isStockJetpack(stack)) {
            ci.cancel();
        }
    }

    /**
     * The stock gas {@code use()} calls {@code drainfromJetpack}, which parses the fluid NBT twice
     * (simulate + drain). IC2's {@code drain} already updates the item damage, so one drain is enough.
     * The electric jetpack overrides {@code use}, so this only affects the gas item.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void ic2tweaks$singleDrain(ItemStack stack, double amount, CallbackInfo ci) {
        if (!JetpackConfig.enabled) {
            return;
        }
        int milliBuckets = (int) amount;
        if (milliBuckets > 0) {
            ((ItemArmorJetpack) (Object) this).drain(stack, milliBuckets, true);
        }
        ci.cancel();
    }

    /** Marker used by {@code /ic2tweaks jetpack} to confirm the mixin was applied. */
    @Unique
    public boolean ic2tweaks$jetpackTickReplaced() {
        return true;
    }
}
