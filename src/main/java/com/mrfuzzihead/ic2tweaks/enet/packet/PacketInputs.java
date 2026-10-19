package com.mrfuzzihead.ic2tweaks.enet.packet;

/**
 * Per-tick inputs for the packet model, indexed by node id. The adapter fills these from the live
 * tiles; tests fill them directly.
 * <p>
 * {@link #sourceOffer} is deliberately mutable: IC2 1.12.2 stores each source tile's remaining offer
 * on the tile itself, so a source that belongs to more than one grid shares one offer across them. The
 * adapter creates one instance per energy-net tick and passes the same instance to every grid.
 */
public final class PacketInputs {

    public final double[] sourceOffer;
    public final int[] sourceTier;
    public final int[] sourcePackets;
    public final double[] sinkDemand;
    public final int[] sinkTier;

    public PacketInputs(int nodeCount) {
        this.sourceOffer = new double[nodeCount];
        this.sourceTier = new int[nodeCount];
        this.sourcePackets = new int[nodeCount];
        this.sinkDemand = new double[nodeCount];
        this.sinkTier = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            this.sourcePackets[i] = 1;
        }
    }

    public void setSource(int node, double offer, int tier) {
        sourceOffer[node] = offer;
        sourceTier[node] = tier;
    }

    public void setSource(int node, double offer, int tier, int packets) {
        sourceOffer[node] = offer;
        sourceTier[node] = tier;
        sourcePackets[node] = packets;
    }

    public void setSink(int node, double demand, int tier) {
        sinkDemand[node] = demand;
        sinkTier[node] = tier;
    }
}
