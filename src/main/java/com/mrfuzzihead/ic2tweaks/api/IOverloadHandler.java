package com.mrfuzzihead.ic2tweaks.api;

/**
 * Implemented by an {@code IEnergySink} that wants to handle an over-voltage packet itself instead of
 * being destroyed by the default energy-net explosion. Equivalent to IC2 Experimental 1.12.2's
 * {@code ic2.api.energy.tile.IOverloadHandler}.
 * <p>
 * The mod-local equivalent is necessary because 1.7.10 IC2 does not ship the 1.12.2 interfaces; only
 * TEs that opt into this interface are honoured.
 */
public interface IOverloadHandler {

    /**
     * Called before the default explosion. The tile stays intact when this returns {@code true}, so the
     * implementation is expected to react to the overload itself (shut down, vent, etc.).
     *
     * @param tier the tier of the offending packet, {@code EnergyNet.getTierFromPower(amount)}
     * @return {@code true} to cancel the default explosion
     */
    boolean onOverload(int tier);
}
