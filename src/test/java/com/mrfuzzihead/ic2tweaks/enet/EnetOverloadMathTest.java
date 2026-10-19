package com.mrfuzzihead.ic2tweaks.enet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure energy-net overload decisions. These pin down the strict-greater-than
 * behavior and the degenerate inputs that must never explode or throw.
 */
class EnetOverloadMathTest {

    @Test
    void tierPowerIsStrictlyGreaterThan() {
        // tier 1 -> 32 EU/t
        assertTrue(EnetOverloadMath.exceedsTierPower(33, 32));
        assertTrue(EnetOverloadMath.exceedsTierPower(128, 32));
        assertFalse(EnetOverloadMath.exceedsTierPower(32, 32));
        assertFalse(EnetOverloadMath.exceedsTierPower(31.9, 32));
    }

    @Test
    void tierPowerRejectsDegenerateInputs() {
        assertFalse(EnetOverloadMath.exceedsTierPower(0, 32));
        assertFalse(EnetOverloadMath.exceedsTierPower(-5, 32));
        assertFalse(EnetOverloadMath.exceedsTierPower(Double.NaN, 32));
        // tier Integer.MAX_VALUE -> getPowerFromTier is infinite; never explode.
        assertFalse(EnetOverloadMath.exceedsTierPower(1e9, Double.POSITIVE_INFINITY));
        assertFalse(EnetOverloadMath.exceedsTierPower(1e9, Double.NaN));
        assertFalse(EnetOverloadMath.exceedsTierPower(1e9, 0));
        assertFalse(EnetOverloadMath.exceedsTierPower(1e9, -1));
    }

    @Test
    void breakdownIsStrictlyGreaterThan() {
        // copper cable breakdown is 129
        assertTrue(EnetOverloadMath.exceedsBreakdown(130, 129));
        assertFalse(EnetOverloadMath.exceedsBreakdown(129, 129));
        assertFalse(EnetOverloadMath.exceedsBreakdown(0, 129));
        assertFalse(EnetOverloadMath.exceedsBreakdown(Double.NaN, 129));
        assertFalse(EnetOverloadMath.exceedsBreakdown(1e9, Double.POSITIVE_INFINITY));
    }

    @Test
    void shockDamageMatchesIc2Formula() {
        // (512 - 5) / 64 = 7.92 -> 8
        assertEquals(8, EnetOverloadMath.shockDamage(512, 5));
        // exactly one damage unit
        assertEquals(1, EnetOverloadMath.shockDamage(69, 5));
        assertEquals(0, EnetOverloadMath.shockDamage(64, 64));
        assertEquals(0, EnetOverloadMath.shockDamage(5, 5));
        assertEquals(0, EnetOverloadMath.shockDamage(0, 5));
        assertEquals(0, EnetOverloadMath.shockDamage(-10, 5));
        assertEquals(0, EnetOverloadMath.shockDamage(Double.NaN, 5));
    }

    @Test
    void shockEnergySubtractsAbsorption() {
        assertEquals(507.0D, EnetOverloadMath.shockEnergy(512, 5));
        assertEquals(0.0D, EnetOverloadMath.shockEnergy(5, 5));
        assertEquals(0.0D, EnetOverloadMath.shockEnergy(4, 5));
        // a negative absorption is treated as zero, matching the old single-conductor behaviour
        assertEquals(10.0D, EnetOverloadMath.shockEnergy(10, -5));
        assertEquals(0.0D, EnetOverloadMath.shockEnergy(10, Double.POSITIVE_INFINITY));
        assertEquals(0.0D, EnetOverloadMath.shockEnergy(0, 5));
        assertEquals(0.0D, EnetOverloadMath.shockEnergy(-10, 5));
        assertEquals(0.0D, EnetOverloadMath.shockEnergy(Double.NaN, 5));
    }

    @Test
    void accumulatedShockEnergyIsConvertedOnce() {
        assertEquals(8, EnetOverloadMath.shockDamageFromEnergy(507.0D));
        assertEquals(1, EnetOverloadMath.shockDamageFromEnergy(64.0D));
        assertEquals(2, EnetOverloadMath.shockDamageFromEnergy(64.0001D));
        assertEquals(0, EnetOverloadMath.shockDamageFromEnergy(0.0D));
        assertEquals(0, EnetOverloadMath.shockDamageFromEnergy(-1.0D));
        assertEquals(0, EnetOverloadMath.shockDamageFromEnergy(Double.NaN));
    }

    @Test
    void demandClampMirrors1122PacketSizing() {
        assertEquals(5.0D, EnetOverloadMath.clampToDemand(20, 5));
        assertEquals(20.0D, EnetOverloadMath.clampToDemand(20, 100));
        assertEquals(0.0D, EnetOverloadMath.clampToDemand(20, 0));
        assertEquals(0.0D, EnetOverloadMath.clampToDemand(20, -1));
        // a broken demand must never silently suppress delivery
        assertEquals(20.0D, EnetOverloadMath.clampToDemand(20, Double.NaN));
        assertEquals(20.0D, EnetOverloadMath.clampToDemand(20, Double.POSITIVE_INFINITY));
        assertEquals(0.0D, EnetOverloadMath.clampToDemand(-1, 5));
        assertEquals(0.0D, EnetOverloadMath.clampToDemand(Double.NaN, 5));
    }
}
