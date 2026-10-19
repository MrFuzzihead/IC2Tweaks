package com.mrfuzzihead.ic2tweaks.enet.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pins the IC2 1.12.2 tier/power conversions and the effective-packet math. */
class PacketMathTest {

    @Test
    void powerFromTierMatchesIc2() {
        assertEquals(8.0D, PacketMath.powerFromTier(0), 1e-9);
        assertEquals(32.0D, PacketMath.powerFromTier(1), 1e-9);
        assertEquals(128.0D, PacketMath.powerFromTier(2), 1e-9);
        assertEquals(512.0D, PacketMath.powerFromTier(3), 1e-9);
        assertEquals(2048.0D, PacketMath.powerFromTier(4), 1e-9);
        assertEquals(8192.0D, PacketMath.powerFromTier(5), 1e-9);
        assertEquals(536870912.0D, PacketMath.powerFromTier(13), 1e-9);
        assertEquals(8.0D * Math.pow(4.0D, 14), PacketMath.powerFromTier(14), 1e-9);
        assertEquals(0.0D, PacketMath.powerFromTier(-1), 1e-9);
    }

    @Test
    void tierFromPowerRoundsUp() {
        assertEquals(0, PacketMath.tierFromPower(0));
        assertEquals(0, PacketMath.tierFromPower(-5));
        assertEquals(0, PacketMath.tierFromPower(Double.NaN));
        assertEquals(0, PacketMath.tierFromPower(8));
        assertEquals(1, PacketMath.tierFromPower(9));
        assertEquals(1, PacketMath.tierFromPower(32));
        assertEquals(2, PacketMath.tierFromPower(33));
        assertEquals(2, PacketMath.tierFromPower(128));
        assertEquals(3, PacketMath.tierFromPower(129));
        assertEquals(3, PacketMath.tierFromPower(512));
        assertEquals(Integer.MAX_VALUE, PacketMath.tierFromPower(Double.POSITIVE_INFINITY));
    }

    @Test
    void effectiveAmountAddsBackTheLoss() {
        // 31.8 delivered + 0.2 loss = 32 spent by the source
        assertEquals(32.0D, PacketMath.effectiveAmount(31.8D, 0.0D, 0.2D), 1e-9);
        // partial rejection leaves only the accepted amount + loss
        assertEquals(27.0D, PacketMath.effectiveAmount(31.8D, 5.0D, 0.2D), 1e-9);
        // full rejection has no effective packet (emit returns before this would be used)
        assertEquals(0.0D, PacketMath.effectiveAmount(10.0D, 100.0D, 0.2D), 1e-9);
        assertEquals(0.0D, PacketMath.effectiveAmount(10.0D, 10.0D, 0.2D), 1e-9);
        assertEquals(0.0D, PacketMath.effectiveAmount(0.0D, 0.0D, 0.2D), 1e-9);
        assertEquals(0.0D, PacketMath.effectiveAmount(-1.0D, 0.0D, 0.2D), 1e-9);
    }

    @Test
    void shockDamageMatchesIc2Formula() {
        assertEquals(0, PacketMath.shockDamageFromEnergy(0));
        assertEquals(0, PacketMath.shockDamageFromEnergy(-1));
        assertEquals(0, PacketMath.shockDamageFromEnergy(Double.NaN));
        assertEquals(1, PacketMath.shockDamageFromEnergy(64));
        assertEquals(2, PacketMath.shockDamageFromEnergy(65));
        // 59 energy from a 64 EU/t packet on a 5 EU/t absorption cable
        assertEquals(1, PacketMath.shockDamageFromEnergy(59));
        assertEquals(8, PacketMath.shockDamageFromEnergy(512 - 5));
    }

    @Test
    void usableTierRejectsDegenerateValues() {
        assertEquals(false, PacketMath.isUsableTier(0));
        assertEquals(false, PacketMath.isUsableTier(-1));
        assertEquals(false, PacketMath.isUsableTier(Integer.MAX_VALUE));
        assertEquals(true, PacketMath.isUsableTier(1));
    }

    @Test
    void capOfferMatches1212RunSyncStep() {
        // one tier-1 packet is 32 EU/t
        assertEquals(32.0D, PacketMath.capOffer(1000.0D, 1, 1), 1e-9D);
        assertEquals(64.0D, PacketMath.capOffer(1000.0D, 1, 2), 1e-9D);
        assertEquals(512.0D, PacketMath.capOffer(1000.0D, 3, 1), 1e-9D);
        // under the cap, the offer is untouched
        assertEquals(10.0D, PacketMath.capOffer(10.0D, 1, 1), 1e-9D);
        // degenerate inputs never produce energy
        assertEquals(0.0D, PacketMath.capOffer(0.0D, 1, 1), 1e-9D);
        assertEquals(0.0D, PacketMath.capOffer(-1.0D, 1, 1), 1e-9D);
        assertEquals(0.0D, PacketMath.capOffer(100.0D, 1, 0), 1e-9D);
        assertEquals(0.0D, PacketMath.capOffer(100.0D, -1, 1), 1e-9D);
        assertEquals(0.0D, PacketMath.capOffer(Double.NaN, 1, 1), 1e-9D);
    }

    @Test
    void nodeStatsVoltageSupportsRatedAndTierModes() {
        assertEquals(0.0D, PacketMath.nodeStatsVoltage(0, false), 1e-9D);
        assertEquals(0.0D, PacketMath.nodeStatsVoltage(-1, false), 1e-9D);
        // default: rated volts for 1.7.10 UI/addon compatibility
        assertEquals(32.0D, PacketMath.nodeStatsVoltage(1, false), 1e-9D);
        assertEquals(512.0D, PacketMath.nodeStatsVoltage(3, false), 1e-9D);
        // strict 1.12.2 mode: the tier index
        assertEquals(1.0D, PacketMath.nodeStatsVoltage(1, true), 1e-9D);
        assertEquals(3.0D, PacketMath.nodeStatsVoltage(3, true), 1e-9D);
    }
}
