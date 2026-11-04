package com.mrfuzzihead.ic2tweaks.jetpack;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** Tiny network channel used by the dedicated hover key. */
public final class JetpackNetwork {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("ic2tweaks");

    private JetpackNetwork() {}

    public static void init() {
        INSTANCE.registerMessage(ToggleHoverMessage.Handler.class, ToggleHoverMessage.class, 0, Side.SERVER);
        INSTANCE.registerMessage(TogglePowerMessage.Handler.class, TogglePowerMessage.class, 1, Side.SERVER);
    }
}
