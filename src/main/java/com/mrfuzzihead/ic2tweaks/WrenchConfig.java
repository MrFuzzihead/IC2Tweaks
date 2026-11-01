package com.mrfuzzihead.ic2tweaks;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Configuration for wrench tweaks.
 * <p>
 * Like {@code EnetOverloadConfig}, this is read from the late-mixin loader (before
 * {@code FMLPreInitializationEvent}) because the mixin's {@code setApplyIf} condition needs the real
 * value from {@code ic2tweaks.cfg}.
 */
public final class WrenchConfig {

    /** Config category used for the wrench settings. Forge lower-cases category names. */
    public static final String CATEGORY = "wrench";

    /**
     * When on, IC2's per-machine wrench drop rate no longer matters: a wrenched machine always drops
     * itself instead of falling back to the machine casing. The operation pays the lossless cost below.
     */
    public static boolean lossless = true;

    /**
     * Durability charged for a forced-lossless wrench operation with a normal (non-electric) wrench.
     * IC2's normal wrench cost is 10 durability, so the default is a 2x premium.
     */
    public static int manualDurabilityCost = 20;

    /**
     * EU charged for a forced-lossless wrench operation with an electric wrench. Defaults to half of
     * IC2's own lossless-mode cost (100 damage x 50 EU). The electric wrench charges in 50 EU steps, so
     * this is rounded up to the next multiple of 50 when applied.
     */
    public static int electricEnergyCost = 5000;

    private WrenchConfig() {}

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        lossless = configuration.getBoolean(
            "losslessWrench",
            CATEGORY,
            true,
            "When true, machines removed with a wrench always drop themselves instead of breaking into a "
                + "machine casing. IC2's per-machine wrench drop rates (0.7-0.9 for most machines) no longer "
                + "cause a random casing drop. Normal wrenches pay losslessWrenchDurabilityCost; electric "
                + "wrenches pay losslessWrenchEnergyCost. Applies to machines whose drop rate is below 100%; "
                + "blocks that already drop 100% of the time keep their normal cost. Requires a restart.");

        manualDurabilityCost = configuration.getInt(
            "losslessWrenchDurabilityCost",
            CATEGORY,
            20,
            1,
            160,
            "Durability charged per forced-lossless wrench operation with a normal wrench. IC2's normal "
                + "wrench damage is 10, so 20 is a 2x premium. Capped at the wrench's 160 durability.");

        electricEnergyCost = configuration.getInt(
            "losslessWrenchEnergyCost",
            CATEGORY,
            5000,
            0,
            12000,
            "EU charged per forced-lossless wrench operation with an electric wrench. Default 5000 is half of "
                + "IC2's native lossless mode (100 damage x 50 EU). The electric wrench charges in 50 EU steps, "
                + "so this is rounded up to the next multiple of 50. Set 0 for free lossless wrenching.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /** Lossless cost in electric-wrench damage units (1 damage = 50 EU), rounded up. */
    public static int electricDamageCost() {
        return (electricEnergyCost + 49) / 50;
    }
}
