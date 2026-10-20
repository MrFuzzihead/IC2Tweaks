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
     * 1.12.2-style packet calculator. On by default since Phase 4; set {@code false} for the legacy
     * per-node model as a one-release fallback. When on, the packet mixins replace the distribution tick
     * and the legacy effect passes are not applied.
     */
    public static boolean packetModel = true;

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
            true,
            "Use the 1.12.2 packet/path calculator (default). Fault checks (melt/shock/explosion) use each path's "
                + "own packet instead of per-node aggregates, so multiple sources no longer add up. Set this to false "
                + "to use the legacy per-node model as a one-release fallback (it also forces IC2's "
                + "useLinearTransferModel). Requires a restart.");

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
            "Legacy model only: cap each injection at the sink's remaining demand. The packet model always "
                + "does this as part of its distribution. This changes how much energy every machine receives "
                + "and how much is returned to sources, so it is off by default.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
