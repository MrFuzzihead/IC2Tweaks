package com.mrfuzzihead.ic2tweaks.enet;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Configuration for the global IC2 energy-net overload feature (IC2 Experimental 1.12.2 style voltage /
 * packet overload).
 * <p>
 * These values have to be readable before Forge's {@code FMLPreInitializationEvent} runs, because the
 * late-mixin loader uses {@link #enabled} to decide whether the IC2 energy-net mixins are applied at
 * all. {@link #synchronizeConfiguration(File)} is therefore called from the mixin loader (and again
 * from {@code IC2Tweaks.preInit}) rather than only from the normal config pass.
 */
public final class EnetOverloadConfig {

    /** Config category used for the energy-net overload settings. Forge lower-cases category names. */
    public static final String CATEGORY = "energynet";

    /**
     * Master switch. When {@code false} none of the energy-net mixins are applied and IC2's own
     * {@code misc/useLinearTransferModel} setting is left untouched.
     */
    public static boolean enabled = true;

    /**
     * Experimental 1.12.2-style packet calculator. Off by default: when off, the legacy per-node effects
     * path runs exactly as before. When on, the packet mixins replace the distribution tick and the legacy
     * effect passes are not applied.
     */
    public static boolean packetModel = false;

    /** Mirrors 1.12.2's {@code misc/roundEnetLoss}: floor each path's accumulated loss. */
    public static boolean packetModelRoundLossDown = false;

    /**
     * When on, {@code NodeStats.getVoltage()} reports 1.12.2's raw tier index instead of the rated tier
     * voltage (32/128/512/...). Rated volts are the default for 1.7.10 UI/addon compatibility.
     */
    public static boolean packetModelTierVoltage = false;

    /** Explode machines that accept a packet above their tier's voltage. */
    public static boolean explosions = true;

    /** Melt conductors that carry more than their breakdown energy. */
    public static boolean cableMeltdown = true;

    /** Apply electric shock damage near overloaded conductors. */
    public static boolean shocks = true;

    /**
     * When on, each injection is capped at the sink's remaining demand, matching IC2 Experimental
     * 1.12.2's packet sizing. Off by default because it changes how much energy every machine receives
     * and how much is returned to sources (a delivery change, not just an overload rule).
     */
    public static boolean demandCapped = false;

    private EnetOverloadConfig() {}

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        enabled = configuration.getBoolean(
            "enableEnetOverload",
            CATEGORY,
            true,
            "Master switch for the global IC2 energy-net overload system (IC2 Experimental 1.12.2 style). "
                + "When enabled, this mod applies over-voltage explosions, cable meltdown and shocks to every IC2 "
                + "machine and cable. Without enablePacketModel it forces IC2's misc/useLinearTransferModel to true "
                + "(ignoring IC2.ini); with enablePacketModel it replaces the distribution with the packet calculator "
                + "instead.");

        packetModel = configuration.getBoolean(
            "enablePacketModel",
            CATEGORY,
            false,
            "Experimental: replace IC2's per-node energy distribution with the 1.12.2 packet/path calculator. "
                + "Fault checks (melt/shock/explosion) then use each path's own packet instead of per-node aggregates, "
                + "so multiple sources no longer add up. When enabled, the legacy per-node effect passes and the "
                + "useLinearTransferModel override are disabled. Requires a restart.");

        packetModelRoundLossDown = configuration.getBoolean(
            "packetModelRoundLossDown",
            CATEGORY,
            false,
            "Packet model only: floor each energy path's accumulated loss, mirroring IC2 1.12.2's "
                + "misc/roundEnetLoss option.");

        packetModelTierVoltage = configuration.getBoolean(
            "packetModelTierVoltage",
            CATEGORY,
            false,
            "Packet model only: report 1.12.2's raw tier index (1, 2, 3, ...) from NodeStats.getVoltage() "
                + "instead of the rated tier voltage (32, 128, 512, ...). Rated volts are the default for 1.7.10 "
                + "UI and addon compatibility.");

        explosions = configuration.getBoolean(
            "enableEnetExplosions",
            CATEGORY,
            true,
            "Machines explode when they accept a packet above their tier's voltage (32 EU/t for tier 1).");

        cableMeltdown = configuration.getBoolean(
            "enableEnetCableMeltdown",
            CATEGORY,
            true,
            "Cables melt when the energy flowing through them exceeds their conductor breakdown energy.");

        shocks = configuration.getBoolean(
            "enableEnetShocks",
            CATEGORY,
            true,
            "Living entities near an overloaded cable take electric shock damage.");

        demandCapped = configuration.getBoolean(
            "enableDemandCappedPackets",
            CATEGORY,
            false,
            "Cap each injection at the sink's remaining demand (IC2 Experimental 1.12.2 packet sizing). "
                + "This changes how much energy every machine receives and how much is returned to sources, "
                + "so it is off by default.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
