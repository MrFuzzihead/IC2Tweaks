package com.mrfuzzihead.ic2tweaks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the electric-wrench lossless cost conversion. IC2's electric wrench charges 50 EU per damage
 * point, so the configured EU value has to be rounded up to the next usable step.
 */
class WrenchConfigTest {

    @Test
    void electricCostRoundsUpToTheNext50EuStep() {
        int previous = WrenchConfig.electricEnergyCost;
        try {
            WrenchConfig.electricEnergyCost = 5000;
            assertEquals(100, WrenchConfig.electricDamageCost());

            WrenchConfig.electricEnergyCost = 5001;
            assertEquals(101, WrenchConfig.electricDamageCost());

            WrenchConfig.electricEnergyCost = 0;
            assertEquals(0, WrenchConfig.electricDamageCost());

            WrenchConfig.electricEnergyCost = 12000;
            assertEquals(240, WrenchConfig.electricDamageCost());
        } finally {
            WrenchConfig.electricEnergyCost = previous;
        }
    }
}
