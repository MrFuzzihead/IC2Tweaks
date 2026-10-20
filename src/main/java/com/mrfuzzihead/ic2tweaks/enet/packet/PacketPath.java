package com.mrfuzzihead.ic2tweaks.enet.packet;

/**
 * One source-to-sink path, the pure equivalent of IC2 Experimental 1.12.2's {@code EnergyPath}.
 * <p>
 * The path precomputes everything the effects pass needs from its conductors: the minima of the three
 * thresholds (used to decide whether the packet is event-worthy and whether a conductor overloaded)
 * and the covered block volume (used by the adapter to find entities for shock damage).
 */
public final class PacketPath {

    public final int source;
    public final int target;
    public final int[] conductors;
    public final double loss;

    /** {@code min(minAbsorption, minInsulationBreakdown, minConductorBreakdown)}. */
    public final double minEffectEnergy;

    public final double minInsulationAbsorption;
    public final double minInsulationBreakdown;
    public final double minConductorBreakdown;

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    int lastCalcId = -1;
    double energySupplied;
    double maxPacketConducted;

    PacketPath(PacketGraph graph, int source, int target, int[] conductors, double loss) {
        this.source = source;
        this.target = target;
        this.conductors = conductors;
        this.loss = loss;

        double minAbsorption = Double.POSITIVE_INFINITY;
        double minInsulation = Double.POSITIVE_INFINITY;
        double minConductor = Double.POSITIVE_INFINITY;
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;

        for (int conductor : conductors) {
            minAbsorption = Math.min(minAbsorption, graph.insulationAbsorption(conductor));
            minInsulation = Math.min(minInsulation, graph.insulationBreakdown(conductor));
            minConductor = Math.min(minConductor, graph.conductorBreakdown(conductor));
            lowX = Math.min(lowX, graph.minX(conductor));
            lowY = Math.min(lowY, graph.minY(conductor));
            lowZ = Math.min(lowZ, graph.minZ(conductor));
            highX = Math.max(highX, graph.maxX(conductor));
            highY = Math.max(highY, graph.maxY(conductor));
            highZ = Math.max(highZ, graph.maxZ(conductor));
        }

        this.minInsulationAbsorption = minAbsorption;
        this.minInsulationBreakdown = minInsulation;
        this.minConductorBreakdown = minConductor;
        this.minEffectEnergy = Math.min(minAbsorption, Math.min(minInsulation, minConductor));
        this.minX = lowX;
        this.minY = lowY;
        this.minZ = lowZ;
        this.maxX = highX;
        this.maxY = highY;
        this.maxZ = highZ;
    }

    /** Last tick in which this path carried energy, mirroring {@code EnergyPath.lastCalcId}. */
    public int lastCalcId() {
        return lastCalcId;
    }

    /** Energy delivered to the target this tick (excludes path loss). */
    public double energySupplied() {
        return energySupplied;
    }

    /** Largest effective packet on this path this tick (includes path loss). */
    public double maxPacketConducted() {
        return maxPacketConducted;
    }

    public boolean hasConductor(int node) {
        for (int conductor : conductors) {
            if (conductor == node) {
                return true;
            }
        }
        return false;
    }
}
