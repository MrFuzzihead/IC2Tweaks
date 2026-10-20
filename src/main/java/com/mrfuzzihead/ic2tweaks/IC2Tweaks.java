package com.mrfuzzihead.ic2tweaks;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.ic2tweaks.command.CommandIc2Tweaks;
import com.mrfuzzihead.ic2tweaks.enet.EnetCableEffects;
import com.mrfuzzihead.ic2tweaks.enet.EnetConductorRegistry;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = IC2Tweaks.MODID,
    version = Tags.VERSION,
    name = IC2Tweaks.MODNAME,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = IC2Tweaks.DEPENDENCIES)
public class IC2Tweaks {

    public static final String MODID = "ic2tweaks";
    public static final String MODNAME = "IC2Tweaks";
    public static final String DEPENDENCIES = "required-after:IC2@2.2.828";
    public static final Logger LOG = LogManager.getLogger(MODID);

    private final EnetConductorRegistry conductorRegistry = new EnetConductorRegistry();
    private final EnetCableEffects cableEffects = new EnetCableEffects(conductorRegistry);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Ensure the energy-net options exist in ic2tweaks.cfg even if the late mixin loader did not
        // read the file first (it usually does).
        EnetOverloadConfig.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        LOG.info("Energy-net model: {}", EnetOverloadConfig.packetModel ? "packet (1.12.2-style)" : "legacy per-node");

        // Cable meltdown / shocks are applied from a per-world tick pass (see EnetCableEffects); the
        // registry keeps track of the conductors IC2 has loaded.
        MinecraftForge.EVENT_BUS.register(conductorRegistry);
        FMLCommonHandler.instance()
            .bus()
            .register(cableEffects);

        if (Boolean.getBoolean("ic2tweaks.canary")) {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.mrfuzzihead.ic2tweaks.dev.CanaryHarness());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandIc2Tweaks(conductorRegistry));
    }
}
