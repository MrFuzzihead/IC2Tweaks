package com.mrfuzzihead.ic2tweaks.enet.packet;

/**
 * Packet-model replacement for {@code ic2.api.energy.NodeStats}. Energy values are the delivered sums
 * from the current tick's paths (loss excluded); {@code maxPacket} is the largest effective packet that
 * touched the node.
 */
public final class PacketNodeStats {

    public final double energyIn;
    public final double energyOut;
    public final double maxPacket;
    public final int tier;

    PacketNodeStats(double energyIn, double energyOut, double maxPacket) {
        this.energyIn = energyIn;
        this.energyOut = energyOut;
        this.maxPacket = maxPacket;
        this.tier = PacketMath.tierFromPower(maxPacket);
    }
}
