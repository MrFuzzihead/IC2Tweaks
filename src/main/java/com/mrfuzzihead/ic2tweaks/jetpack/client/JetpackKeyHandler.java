package com.mrfuzzihead.ic2tweaks.jetpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Keyboard;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackItem;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackNetwork;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;
import com.mrfuzzihead.ic2tweaks.jetpack.ToggleHoverMessage;
import com.mrfuzzihead.ic2tweaks.jetpack.TogglePowerMessage;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import ic2.core.util.StackUtil;

/** Dedicated hover and power keys; both are configurable in Controls. */
public final class JetpackKeyHandler {

    private final KeyBinding hoverKey = new KeyBinding(
        "key.ic2tweaks.hover",
        Keyboard.KEY_H,
        "key.categories.ic2tweaks");
    private final KeyBinding powerKey = new KeyBinding(
        "key.ic2tweaks.power",
        Keyboard.KEY_J,
        "key.categories.ic2tweaks");

    public JetpackKeyHandler() {
        ClientRegistry.registerKeyBinding(hoverKey);
        ClientRegistry.registerKeyBinding(powerKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        boolean hoverPressed = JetpackConfig.dedicatedHoverKey && hoverKey.getIsKeyPressed();
        boolean powerPressed = JetpackConfig.dedicatedPowerKey && powerKey.getIsKeyPressed();
        if (!JetpackConfig.enabled || !hoverPressed && !powerPressed) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        ItemStack stack = JetpackWorn.get(player);
        if (!JetpackItem.isSupported(stack)) {
            return;
        }
        if (hoverPressed) {
            NBTTagCompound tag = StackUtil.getOrCreateNbtData(stack);
            tag.setBoolean("hoverMode", !tag.getBoolean("hoverMode"));
            tag.setByte("toggleTimer", (byte) 10);
            JetpackNetwork.INSTANCE.sendToServer(new ToggleHoverMessage());
        }
        if (powerPressed) {
            JetpackItem.setPowerOff(stack, !JetpackItem.isPowerOff(stack));
            JetpackNetwork.INSTANCE.sendToServer(new TogglePowerMessage());
        }
    }
}
