package com.mrfuzzihead.ic2tweaks.api;

/**
 * Implemented by an {@code IEnergySink} that wants to veto or resize the default energy-net explosion.
 * Equivalent to IC2 Experimental 1.12.2's {@code ic2.api.energy.tile.IExplosionPowerOverride}.
 * <p>
 * Checked after {@link IOverloadHandler}; only TEs that opt into this interface are honoured.
 */
public interface IExplosionPowerOverride {

    /** @return {@code false} to skip the explosion entirely */
    boolean shouldExplode();

    /**
     * @param tier         the tier of the offending packet, {@code EnergyNet.getTierFromPower(amount)}
     * @param defaultPower the default blast power (2.5)
     * @return the blast power to use
     */
    float getExplosionPower(int tier, float defaultPower);
}
