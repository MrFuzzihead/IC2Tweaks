package com.mrfuzzihead.ic2tweaks.enet.packet;

/**
 * Pure math for the packet model. Mirrors the helpers IC2 Experimental 1.12.2 uses in
 * {@code EnergyCalculatorLeg} and {@code EnergyNet}, without any Minecraft/IC2 types so it can be unit
 * tested without a game.
 */
public final class PacketMath {

    /** Divisor IC2 Experimental 1.12.2 uses for shock damage. */
    public static final double SHOCK_DIVISOR = 64.0D;

    private PacketMath() {}

    /**
     * {@code EnergyNet.getPowerFromTier}: 8 / 32 / 128 / 512 / ... EU/t.
     * <p>
     * Matches IC2's implementation exactly: an int shift below tier 14, then a double power above it.
     */
    public static double powerFromTier(int tier) {
        if (tier < 0) {
            return 0.0D;
        }
        if (tier < 14) {
            return 8 << (2 * tier);
        }
        return 8.0D * Math.pow(4.0D, tier);
    }

    /**
     * {@code EnergyNet.getTierFromPower}: the inverse of {@link #powerFromTier(int)}, rounded up. A
     * 32 EU/t packet is tier 1, a 33 EU/t packet is tier 2, and a non-positive packet is tier 0.
     */
    public static int tierFromPower(double power) {
        if (!(power > 0.0D)) {
            return 0;
        }
        if (Double.isInfinite(power)) {
            return Integer.MAX_VALUE;
        }
        int tier = (int) Math.ceil(Math.log(power / 8.0D) / Math.log(4.0D));
        return Math.max(tier, 0);
    }

    /**
     * Port of {@code EnergyCalculatorLeg.emit}'s effective packet size:
     * {@code max(0, amount - rejected + loss)}. The loss is part of what the source spends. Full
     * rejection (`rejected >= amount`) has no effective packet, mirroring the early return in
     * {@code emit}.
     */
    public static double effectiveAmount(double amount, double rejected, double loss) {
        if (!(amount > 0.0D) || Double.isNaN(amount)) {
            return 0.0D;
        }
        double safeRejected = Math.max(0.0D, rejected);
        if (safeRejected >= amount) {
            return 0.0D;
        }
        return Math.max(0.0D, amount - safeRejected + Math.max(0.0D, loss));
    }

    /** {@code ceil(shockEnergy / 64)}, the damage IC2 1.12.2 applies once per entity. */
    public static int shockDamageFromEnergy(double shockEnergy) {
        if (!(shockEnergy > 0.0D) || Double.isNaN(shockEnergy)) {
            return 0;
        }
        return (int) Math.ceil(shockEnergy / SHOCK_DIVISOR);
    }

    /**
     * 1.12.2's {@code runSyncStep(enet)} cap: a source may offer at most one tier-power packet per
     * packet slot. Without this cap an over-offering single-packet 1.7.10 source could push a too-large
     * packet down one path and trip a threshold.
     */
    public static double capOffer(double offer, int tier, int packets) {
        if (!(offer > 0.0D) || packets <= 0) {
            return 0.0D;
        }
        double power = powerFromTier(tier);
        if (!(power > 0.0D)) {
            return 0.0D;
        }
        return Math.min(offer, power * packets);
    }

    /**
     * Maps a packet tier to the value exposed through {@code NodeStats.getVoltage()}. Rated volts are the
     * default (1.7.10 UI/addon compatibility); {@code rawTier} exposes 1.12.2's tier index instead.
     */
    public static double nodeStatsVoltage(int tier, boolean rawTier) {
        if (tier <= 0) {
            return 0.0D;
        }
        return rawTier ? tier : powerFromTier(tier);
    }

    /**
     * Thrown by {@link #tierFromPower} for an infinite packet: the model never treats it as a valid
     * explosion tier.
     */
    public static boolean isUsableTier(int tier) {
        return tier > 0 && tier != Integer.MAX_VALUE;
    }
}
