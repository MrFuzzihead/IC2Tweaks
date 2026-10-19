package com.mrfuzzihead.ic2tweaks.enet.packet;

/**
 * Side effects the packet model needs from the live game, kept behind an interface so the calculator
 * stays free of Minecraft/IC2 types.
 * <p>
 * The 1.12.2 calculator calls {@code IEnergySink.injectEnergy} during distribution (the returned
 * rejection changes both the delivered amount and the sink's remaining demand), so this cannot be a
 * pure computation. The adapter also supplies the direction from the path (it owns the IC2 geometry).
 */
public interface PacketInjectionHandler {

    /**
     * @param path   the path whose target is being fed
     * @param amount the packet the sink is offered (already loss-adjusted and demand-capped)
     * @param tier   {@link PacketMath#tierFromPower(double)} of {@code amount}, as IC2 passes it
     * @return the amount the sink rejected; 0 means it accepted everything
     */
    double inject(PacketPath path, double amount, int tier);

    /** Called once per source whose offer was actually used. */
    void draw(int sourceNode, double amount);

    /** Accepts everything and records nothing; useful for tests and dry runs. */
    PacketInjectionHandler ACCEPT_ALL = new PacketInjectionHandler() {

        @Override
        public double inject(PacketPath path, double amount, int tier) {
            return 0.0D;
        }

        @Override
        public void draw(int sourceNode, double amount) {}
    };
}
