package com.mrfuzzihead.ic2tweaks;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Common-side proxy. Client-only event handlers live in {@code client/ClientProxy}, which FML loads only
 * on the physical client, so this class never references a client package.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}
}
