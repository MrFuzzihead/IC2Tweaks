package com.mrfuzzihead.ic2tweaks.client;

import net.minecraftforge.common.MinecraftForge;

import com.mrfuzzihead.ic2tweaks.CommonProxy;
import com.mrfuzzihead.ic2tweaks.jetpack.client.JetpackClientTick;
import com.mrfuzzihead.ic2tweaks.jetpack.client.JetpackHudOverlay;
import com.mrfuzzihead.ic2tweaks.jetpack.client.JetpackKeyHandler;
import com.mrfuzzihead.ic2tweaks.jetpack.client.JetpackRenderer;
import com.mrfuzzihead.ic2tweaks.jetpack.client.JetpackTooltipHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Physical-client proxy. FML instantiates this class only on the client (see the {@code @SidedProxy} field
 * in {@link com.mrfuzzihead.ic2tweaks.IC2Tweaks}), so the client-only event handlers can be referenced
 * directly instead of being loaded reflectively.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new JetpackRenderer());
        MinecraftForge.EVENT_BUS.register(new JetpackHudOverlay());
        MinecraftForge.EVENT_BUS.register(new JetpackTooltipHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(new JetpackClientTick());
        FMLCommonHandler.instance()
            .bus()
            .register(new JetpackKeyHandler());
    }
}
