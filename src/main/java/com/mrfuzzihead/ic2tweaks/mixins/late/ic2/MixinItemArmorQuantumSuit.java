package com.mrfuzzihead.ic2tweaks.mixins.late.ic2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;

import ic2.core.item.armor.ItemArmorQuantumSuit;

/**
 * When {@code tweakQuantumJetpack} is on, the quantum chest is driven by {@code JetpackHandler}: its chest
 * {@code onArmorTick} (toggles/audio/drain/motion) is cancelled and {@code useJetpack} becomes a no-op.
 * The other quantum pieces keep their stock ticks; the helmet/leggings/boots behaviour is untouched.
 */
@Mixin(value = ItemArmorQuantumSuit.class, remap = false)
public abstract class MixinItemArmorQuantumSuit {

    @Inject(method = "onArmorTick", at = @At("HEAD"), cancellable = true)
    private void ic2tweaks$replaceQuantumChestTick(World world, EntityPlayer player, ItemStack stack, CallbackInfo ci) {
        if (JetpackConfig.enabled && JetpackConfig.tweakQuantumJetpack
            && stack != null
            && ((ItemArmor) stack.getItem()).armorType == 1) {
            ci.cancel();
        }
    }

    @Inject(method = "useJetpack", at = @At("HEAD"), cancellable = true)
    private void ic2tweaks$replaceQuantumJetpack(EntityPlayer player, boolean hover,
        CallbackInfoReturnable<Boolean> cir) {
        if (JetpackConfig.enabled && JetpackConfig.tweakQuantumJetpack) {
            cir.setReturnValue(false);
        }
    }

    /** Marker used by {@code /ic2tweaks jetpack} to confirm the mixin was applied. */
    @Unique
    public boolean ic2tweaks$quantumJetpackMixinApplied() {
        return true;
    }
}
