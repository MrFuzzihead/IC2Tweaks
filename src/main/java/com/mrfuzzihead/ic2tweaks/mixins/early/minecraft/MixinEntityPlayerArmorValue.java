package com.mrfuzzihead.ic2tweaks.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.ForgeHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;

/**
 * The vanilla armour bar sums {@code ItemArmor.damageReduceAmount}; IC2's jetpack is a diamond-material
 * chestplate with 0 {@code ISpecialArmor} absorption, so it displays 8 points that do nothing.
 * <p>
 * The Forge hook {@code ForgeHooks.getTotalArmorValue} already honours {@code ISpecialArmor.getArmorDisplay}
 * (0 for the jetpack, and the correct value for the quantum suit/lappack), but the vanilla HUD does not use
 * it. Route {@code EntityPlayer.getTotalArmorValue} through the hook when the option is enabled; this also
 * fixes the hazmat suit and CF pack.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayerArmorValue {

    @Inject(method = "getTotalArmorValue", at = @At("HEAD"), cancellable = true)
    private void ic2tweaks$useSpecialArmorDisplay(CallbackInfoReturnable<Integer> cir) {
        if (JetpackConfig.enabled && JetpackConfig.stripJetpackArmorDisplay) {
            cir.setReturnValue(ForgeHooks.getTotalArmorValue((EntityPlayer) (Object) this));
        }
    }

    /** Marker used by {@code /ic2tweaks jetpack} to confirm the mixin was applied. */
    @Unique
    public boolean ic2tweaks$armorHudMixinApplied() {
        return true;
    }
}
