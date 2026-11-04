package com.mrfuzzihead.ic2tweaks.jetpack.client;

import net.minecraft.client.Minecraft;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackGuiState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Keeps {@link JetpackGuiState#guiOpen} in sync with the client screen for the {@code pauseInGui} option. */
public final class JetpackClientTick {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            JetpackGuiState.guiOpen = Minecraft.getMinecraft().currentScreen != null;
        }
    }
}
