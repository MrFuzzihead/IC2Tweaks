package com.mrfuzzihead.ic2tweaks.enet;

/**
 * Pure, side-effect-free decisions for the energy-net overload system. Kept free of Minecraft/IC2
 * types so it can be unit tested without booting a game.
 */
public final class EnetOverloadMath {

    /** Divisor used by IC2 Experimental 1.12.2 for electric shock damage. */
    public static final double SHOCK_DIVISOR = 64.0D;

    private EnetOverloadMath() {}

    /**
     * @param accepted  energy actually accepted by the sink this tick (offered minus rejected)
     * @param tierPower {@code EnergyNet.getPowerFromTier(sinkTier)}
     * @return whether the accepted packet exceeds the sink's tier voltage
     */
    public static boolean exceedsTierPower(double accepted, double tierPower) {
        return accepted > 0 && isUsableLimit(tierPower) && accepted > tierPower;
    }

    /**
     * @param throughput energy flowing through a conductor this tick
     * @param breakdown  {@code IEnergyConductor.getConductorBreakdownEnergy()}
     * @return whether the conductor should melt
     */
    public static boolean exceedsBreakdown(double throughput, double breakdown) {
        return throughput > 0 && isUsableLimit(breakdown) && throughput > breakdown;
    }

    /**
     * @param throughput energy flowing through a conductor this tick
     * @param absorption {@code IEnergyConductor.getInsulationEnergyAbsorption()}
     * @return the energy the conductor contributes to shock damage this tick, i.e.
     *         {@code max(0, throughput - absorption)}; one conductor's contribution before the caller
     *         accumulates it per entity. The divisor is applied once, by
     *         {@link #shockDamageFromEnergy(double)}.
     */
    public static double shockEnergy(double throughput, double absorption) {
        if (throughput <= 0 || Double.isNaN(throughput)) {
            return 0D;
        }
        double excess = throughput - Math.max(0, absorption);
        return excess > 0 ? excess : 0D;
    }

    /**
     * @param shockEnergy accumulated shock energy for one entity this tick
     * @return integer shock damage, {@code ceil(shockEnergy / 64)}
     */
    public static int shockDamageFromEnergy(double shockEnergy) {
        if (shockEnergy <= 0 || Double.isNaN(shockEnergy)) {
            return 0;
        }
        return (int) Math.ceil(shockEnergy / SHOCK_DIVISOR);
    }

    /**
     * Convenience wrapper for a single conductor.
     *
     * @param throughput energy flowing through a conductor this tick
     * @param absorption {@code IEnergyConductor.getInsulationEnergyAbsorption()}
     * @return integer shock damage, {@code ceil((throughput - absorption) / 64)}
     */
    public static int shockDamage(double throughput, double absorption) {
        return shockDamageFromEnergy(shockEnergy(throughput, absorption));
    }

    /**
     * Shock threshold for a conductor that is being removed as overloaded this tick. Insulated cables
     * are shock-safe at their rated load, so their threshold only drops to the conductor breakdown (the
     * excess that is actually melting them); bare cables already have a lower absorption and are
     * unaffected.
     *
     * @param absorption {@code IEnergyConductor.getInsulationEnergyAbsorption()}
     * @param breakdown  {@code IEnergyConductor.getConductorBreakdownEnergy()}
     * @return the threshold to use
     */
    public static double overloadShockThreshold(double absorption, double breakdown) {
        return Math.min(absorption, breakdown);
    }

    /**
     * Caps an injection at the sink's remaining demand, mirroring IC2 Experimental 1.12.2's
     * {@code amount = min(offer - loss, demand)} packet sizing. Degenerate inputs are treated as
     * "no cap" so a broken {@code getDemandedEnergy()} can never suppress delivery silently.
     *
     * @param amount the amount the energy net wants to inject
     * @param demand the sink's remaining demand ({@code getDemandedEnergy()})
     * @return the amount to inject
     */
    public static double clampToDemand(double amount, double demand) {
        if (Double.isNaN(amount) || amount <= 0) {
            return 0D;
        }
        if (Double.isNaN(demand)) {
            return amount;
        }
        return Math.max(0D, Math.min(amount, demand));
    }

    private static boolean isUsableLimit(double limit) {
        return limit > 0 && !Double.isNaN(limit) && !Double.isInfinite(limit);
    }
}
