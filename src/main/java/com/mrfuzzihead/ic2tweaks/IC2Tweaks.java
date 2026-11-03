package com.mrfuzzihead.ic2tweaks;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.ic2tweaks.command.CommandIc2Tweaks;
import com.mrfuzzihead.ic2tweaks.compat.waila.WailaIntegrationState;
import com.mrfuzzihead.ic2tweaks.enet.EnetCableEffects;
import com.mrfuzzihead.ic2tweaks.enet.EnetConductorRegistry;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackHandler;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInterModComms;
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

    @SidedProxy(
        clientSide = "com.mrfuzzihead.ic2tweaks.client.ClientProxy",
        serverSide = "com.mrfuzzihead.ic2tweaks.CommonProxy")
    public static CommonProxy proxy;

    private final EnetConductorRegistry conductorRegistry = new EnetConductorRegistry();
    private final EnetCableEffects cableEffects = new EnetCableEffects(conductorRegistry);
    private final JetpackHandler jetpackHandler = new JetpackHandler();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Ensure the options exist in ic2tweaks.cfg even if the late mixin loader did not read the
        // file first (it usually does).
        EnetOverloadConfig.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        WrenchConfig.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        JetpackConfig.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        LOG.info("Energy-net model: {}", EnetOverloadConfig.packetModel ? "packet (1.12.2-style)" : "legacy per-node");
        LOG.info("Lossless wrenching: {}", WrenchConfig.lossless);
        LOG.info(
            "Jetpack tweaks: {} (horizontal {} b/t, gas {}/{} mB/t, electric {}/{} EU/t)",
            JetpackConfig.enabled ? "enabled" : "disabled",
            JetpackConfig.horizontalSpeed,
            JetpackConfig.gasFuelPerTick,
            JetpackConfig.gasHoverFuelPerTick,
            JetpackConfig.electricEuPerTick,
            JetpackConfig.electricHoverEuPerTick);
        if (JetpackConfig.isBaubleSlotEnabled()) {
            LOG.info("Jetpack Baubles slots: {}", JetpackConfig.baubleSlotTypesCsv());
        }
        if (JetpackConfig.travellersGearSlot != JetpackConfig.TravellersGearSlot.OFF) {
            LOG.info("Jetpack TravellersGear slot: {}", JetpackConfig.travellersGearSlot);
        }
        if (WrenchConfig.lossless) {
            LOG.info(
                "Lossless wrench cost: {} durability / {} EU",
                WrenchConfig.manualDurabilityCost,
                WrenchConfig.electricEnergyCost);
        }

        // Optional slot integrations. The compat classes reference the optional mods' APIs, so only load
        // them when the mod is present; JetpackWorn itself stays free of those types.
        if (Loader.isModLoaded("Baubles")) {
            JetpackWorn.registerBaubles(new com.mrfuzzihead.ic2tweaks.jetpack.compat.BaublesCompat());
        }
        if (Loader.isModLoaded("TravellersGear")) {
            JetpackWorn.registerTravellersGear(new com.mrfuzzihead.ic2tweaks.jetpack.compat.TravellersGearCompat());
        }
        // The proxy is selected by FML: the client registers the renderer/HUD/tooltip handlers; the
        // dedicated server never loads those classes.
        proxy.preInit(event);

        // Cable meltdown / shocks are applied from a per-world tick pass (see EnetCableEffects); the
        // registry keeps track of the conductors IC2 has loaded.
        MinecraftForge.EVENT_BUS.register(conductorRegistry);
        FMLCommonHandler.instance()
            .bus()
            .register(cableEffects);

        // The jetpack handler owns the tick, fuel accounting and client motion/audio. Register it on both
        // buses: PlayerTickEvent lives on the FML bus, WorldEvent.Unload on the Forge bus.
        FMLCommonHandler.instance()
            .bus()
            .register(jetpackHandler);
        MinecraftForge.EVENT_BUS.register(jetpackHandler);

        if (Boolean.getBoolean("ic2tweaks.canary")) {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.mrfuzzihead.ic2tweaks.dev.CanaryHarness());
        }

        // Optional Waila integration: Waila reflects into the named method and passes its registrar.
        // The message is harmless when Waila is absent, and the compat class is only loaded by Waila.
        FMLInterModComms.sendMessage("Waila", "register", WailaIntegrationState.REGISTRATION_METHOD);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandIc2Tweaks(conductorRegistry));
    }
}
