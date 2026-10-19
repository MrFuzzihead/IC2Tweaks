package com.mrfuzzihead.ic2tweaks.enet.packet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the effects pass decided for one tick, in the form the adapter applies to the world.
 * <p>
 * Application order (mirroring {@code EnergyCalculatorLeg.applyCableEffects}): remove conductors, strip
 * insulation, explode sinks, then apply shock. {@link #sinksToExplode} is keyed by {@link
 * PacketGraph#tileId(int)} so multiple sink nodes on one tile collapse to one explosion.
 */
public final class PacketTickResult {

    public final Set<Integer> conductorsToRemove;
    public final Set<Integer> conductorsToStrip;
    public final Map<Integer, Double> sinksToExplode;
    public final List<ShockContribution> shockContributions;

    PacketTickResult(Set<Integer> conductorsToRemove, Set<Integer> conductorsToStrip,
        Map<Integer, Double> sinksToExplode, List<ShockContribution> shockContributions) {
        this.conductorsToRemove = conductorsToRemove;
        this.conductorsToStrip = conductorsToStrip;
        this.sinksToExplode = sinksToExplode;
        this.shockContributions = shockContributions;
    }

    public static PacketTickResult empty() {
        return new PacketTickResult(
            Collections.<Integer>emptySet(),
            Collections.<Integer>emptySet(),
            Collections.<Integer, Double>emptyMap(),
            Collections.<ShockContribution>emptyList());
    }

    public boolean isEmpty() {
        return conductorsToRemove.isEmpty() && conductorsToStrip.isEmpty()
            && sinksToExplode.isEmpty()
            && shockContributions.isEmpty();
    }

    /**
     * One conductor's shock contribution on one path. 1.12.2 takes the maximum per entity within a path
     * (over the conductors within reach of that entity) and sums across paths; the adapter has the
     * entity positions needed for that aggregation.
     */
    public static final class ShockContribution {

        public final PacketPath path;
        public final int conductorNode;
        public final int energy;

        ShockContribution(PacketPath path, int conductorNode, int energy) {
            this.path = path;
            this.conductorNode = conductorNode;
            this.energy = energy;
        }
    }
}
